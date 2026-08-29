package net.dstone.common.messaging.saga;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.common.core.BaseObject;
import net.dstone.common.messaging.outbox.OutboxAppender;
import net.dstone.common.utils.ConvertUtil;

/**
 * <pre>
 * 오케스트레이션 방식 사가(Orchestration Saga) 엔진.
 *
 * [전체 흐름 요약]
 *    1) 이 클래스(오케스트레이터)가 사가의 "지휘자" 역할을 한다. 각 스텝은 SagaStepHandler 구현체가
 *       로컬(동일 JVM, 동일 DB 트랜잭션)에서 동기 실행한다 — 원격 서비스 호출이 아니라 인프로세스 호출이다.
 *    2) 한 스텝이 끝나면 그 결과를 "다음 스텝을 트리거하는 이벤트"로 만들어 발행해야 하는데, 이때
 *       Kafka로 직접 보내지 않고 반드시 OutboxAppender.append()를 거친다.
 *       이유: sagaStore.insertStepHistory()/updateStatus() 같은 DB 쓰기와 이벤트 발행을
 *       "하나의 로컬 트랜잭션"으로 묶어야 하기 때문(Transactional Outbox 패턴).
 *       → DB 커밋은 성공했는데 Kafka 발행에는 실패(또는 그 반대)하는 이중 쓰기(dual write) 문제를 제거한다.
 *    3) 실제 Kafka 전송은 별도 스레드(OutboxRelay, dstone-boot에서는 OutboxRelayScheduler가 주기 호출)가
 *       TB_OUTBOX_MESSAGE의 PENDING 레코드를 폴링해서 비동기적으로 수행한다.
 *    4) 다음 스텝으로의 "진행(proceed)"은 이 엔진이 스스로 하지 않는다. 
 *       Kafka Consumer(각 모듈의 @KafkaListener, 예: OrderSagaReplyListener)가 "{step}-reply" 토픽을 구독하고 있다가 메시지를
 *       수신하면 그 payload를 들고 proceed()를 호출해준다. 즉 스텝 간 연결은 Kafka 메시지가 매개한다.
 *    5) 스텝 실행 중 예외가 발생하면 compensate()가 자동 호출되어, 이미 성공했던 이전 스텝들을
 *       역순으로 되돌린다(SEC(Saga Execution Coordinator) 패턴의 보상 트랜잭션).
 *
 * [스텝/토픽 네이밍 규칙]
 *    스텝 이름이 "inventoryReserve"라면, 결과 이벤트는 관례상 토픽 "inventoryReserve-reply"로 발행된다.
 *    이 토픽을 구독하는 리스너가 다음 스텝 이름(예: "payment")을 알고 있다가 proceed()를 호출하는 식으로,
 *    "사가 정의(스텝 순서)"는 오케스트레이터가 아니라 리스너/컨트롤러 쪽 호출자가 갖는다.
 *    (엔진 자체는 SagaStepHandler 목록에서 이름만으로 찾아 실행할 뿐, 순서를 알지 못한다 — 그래야
 *    이 엔진 코드가 사가 종류(주문/배송/정산 등)에 무관하게 재사용 가능하다.)
 * </pre>
 */
public class SagaOrchestrator extends BaseObject {

	private final SagaStore sagaStore;
	private final OutboxAppender outboxAppender;
	private final List<SagaStepHandler> stepHandlers;
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 사가(Orchestration Saga) 엔진으로 Spring 컴퍼넌트에서 생성시킬 때 아래의 파라메터를 넘겨야 함.
	 * @param sagaStore - 사가 인스턴스 및 이력등을 저장 할 수 있도록 SagaStore 를 구현한 DAO컴퍼넌트.
	 * @param outboxAppender - 
	 * @param stepHandlers
	 */
	public SagaOrchestrator(SagaStore sagaStore, OutboxAppender outboxAppender, List<SagaStepHandler> stepHandlers) {
		this.sagaStore = sagaStore;
		this.outboxAppender = outboxAppender;
		this.stepHandlers = stepHandlers;
	}

	/**
	 * <pre>
	 * 사가를 시작하고 첫 스텝을 실행한다. (보통 Controller/Service 등 외부 트리거에서 호출)
	 *
	 * 여기서 생성되는 sagaId(UUID)는 이후 이 사가의 모든 스텝을 하나로 묶는 상관관계 키(correlation id)이며,
	 * runStep() 내부에서 outboxAppender.append(topic, sagaId, result)의 "key" 인자로 그대로 쓰인다.
	 * → Kafka 파티션 결정에 쓰이는 key가 되어, 같은 사가의 이벤트들이 항상 같은 파티션에 쌓이도록(=순서 보장) 강제한다.
	 * 동시에 result 맵 안에도 "SAGA_ID" 필드로 심어지므로(runStep 참고) Kafka 메시지 "value"(JSON body)에도
	 * 포함되어, 소비자(리스너) 측에서 payload.get("SAGA_ID")로 꺼내 쓸 수 있다.
	 * </pre>
	 * @param sagaType  사가의 업무적 종류를 나타내는 분류값(예: "ORDER"). Kafka로는 전송되지 않고
	 *                  TB_SAGA_INSTANCE.SAGA_TYPE 컬럼에만 저장되는 순수 내부 메타데이터다.
	 * @param firstStep 최초로 실행할 스텝 이름. SagaStepHandler.getStepName()과 매칭되는 식별자이며,
	 *                  동시에 결과 이벤트가 발행될 Kafka 토픽명의 접두어("{firstStep}-reply")를 결정한다.
	 * @param payload   첫 스텝 핸들러(SagaStepHandler.handle)에 그대로 전달될 입력 커맨드(Map).
	 *                  이 시점에는 아직 Kafka로 나가지 않는 순수 로컬 호출 인자이며, 첫 스텝이 끝난 뒤
	 *                  이 값을 재료로 만들어진 결과가 outbox를 거쳐 Kafka 메시지 "value"(JSON)로 발행된다.
	 * @return 생성된 sagaId(UUID 문자열). TB_SAGA_INSTANCE의 PK이자, 이후 모든 관련 Kafka 메시지의 key.
	 */
	public String start(String sagaType, String firstStep, Map<String, Object> payload) {
		
		this.info(signatureLog());
		
		String sagaId = UUID.randomUUID().toString();
		Map<String, Object> saga = new HashMap<String, Object>();
		saga.put("SAGA_ID", sagaId);
		saga.put("SAGA_TYPE", sagaType);
		saga.put("STATUS", SagaStatus.STARTED.name());
		saga.put("CURRENT_STEP", firstStep);
		
		// 사가 정보 DB저장
		sagaStore.insert(saga);
		// 첫스템 시작
		runStep(sagaId, firstStep, payload);
		
		return sagaId;
	}

	/**
	 * <pre>
	 * 이전 스텝의 응답을 받아 다음 스텝을 진행시키고 싶을 때 호출한다.
	 * 호출 시점: 이 메소드는 Kafka Consumer 콜백(@KafkaListener 메소드) 안에서 호출되는 것이 정상 사용 패턴이다.
	 * 즉 "이전 스텝의 응답(Kafka reply)"이란 실제로는 OutboxRelay가 이전 스텝 처리 후 발행한
	 * "{이전스텝}-reply" 토픽 메시지를 리스너가 poll/역직렬화하여 받은 것을 말한다(예: OrderSagaReplyListener).
	 * (다음 스텝 이름 결정은 사가 정의를 아는 모듈 쪽 호출자가 넘겨준다 — 엔진은 스텝 순서 자체를 모른다)
	 * </pre>
	 * @param sagaId  진행 중인 사가의 식별자. Kafka 메시지 value(JSON)에 "SAGA_ID" 필드로 담겨 온 값을
	 *                리스너가 꺼내 그대로 전달하는 것이 일반적이다(예: payload.get("SAGA_ID")).
	 * @param nextStep 다음에 실행할 스텝 이름. Kafka 토픽명 자체에는 없고(리스너가 구독한 토픽은 "이전 스텝"의
	 *                 reply 토픽이다), 사가 정의(순서)를 아는 리스너 코드가 하드코딩/설정으로 결정해서 넘긴다.
	 * @param command 다음 스텝 핸들러에 전달할 입력값. 대부분 "직전 Kafka 메시지의 value(역직렬화된 Map)"를
	 *                그대로 넘기므로, Kafka ConsumerRecord.value()가 사실상 이 파라메터의 원천이다.
	 */
	public void proceed(String sagaId, String nextStep, Map<String, Object> command) {
		runStep(sagaId, nextStep, command);
	}

	/**
	 * <pre>
	 * 사가 정의를 아는 호출자(리스너 등)가 마지막 스텝의 reply까지 받은 뒤 호출해서 사가를 종결시킨다.
	 * 엔진 자체는 스텝 순서를 모르므로 "이게 마지막 스텝이다"는 판단은 호출자 책임이다.
	 * 이 메소드는 상태만 갱신할 뿐 이벤트를 발행하지 않는다 — 사가가 끝났다는 사실을 Kafka로 알리고
	 * 싶다면 호출자가 별도로 outboxAppender.append()를 불러야 한다(현재 샘플은 그렇게 하지 않는다).
	 * </pre>
	 * @param sagaId  종결할 사가의 식별자(TB_SAGA_INSTANCE.SAGA_ID). 마지막 reply 이벤트의 SAGA_ID 필드에서 추출.
	 * @param lastStep 종결 시점의 마지막 스텝 이름. TB_SAGA_INSTANCE.CURRENT_STEP 갱신용으로만 쓰이고
	 *                 Kafka로는 나가지 않는다.
	 */
	public void complete(String sagaId, String lastStep) {
		sagaStore.updateStatus(sagaId, SagaStatus.COMPLETED.name(), lastStep);
	}

	/**
	 * <pre>
	 * 한 스텝을 실행하고, 그 결과를 다음 스텝 트리거용 이벤트로 아웃박스에 적재한다.
	 * 처리 순서: 
	 *   ①핸들러 동기 실행 
	 *   → ②SAGA_ID 결과에 주입 
	 *   → ③스텝 이력 DB 저장 
	 *   → ④사가 상태 갱신
	 *   → ⑤outboxAppender.append()로 TB_OUTBOX_MESSAGE에 PENDING 행 삽입.
	 * ②~⑤는 (배포 환경의 트랜잭션 경계 설정에 따라) 하나의 로컬 트랜잭션으로 묶여야
	 * 아웃박스 패턴의 원자성 보장이 성립한다 — DB 반영과 "발행 예약"이 함께 커밋되거나 함께 롤백되어야 한다.
	 * 실제 Kafka 전송(브로커로의 네트워크 I/O)은 여기서 일어나지 않고, 이후 OutboxRelay가 비동기로 수행한다.
	 * </pre>
	 * @param sagaId   사가 식별자. 결과 Map에 "SAGA_ID"로 주입되어 Kafka 메시지 value에 포함되며,
	 *                 동시에 outboxAppender.append()의 key 인자로 넘어가 Kafka 파티션 결정에도 쓰인다
	 *                 (= 같은 사가의 이벤트는 항상 같은 파티션 → 순서 보장).
	 * @param stepName 실행할 스텝 이름. findHandler()로 SagaStepHandler를 찾는 키이자,
	 *                 발행될 Kafka 토픽명 "{stepName}-reply"의 접두어.
	 * @param command  스텝 핸들러에 전달되는 입력값이자, 성공 시 TB_SAGA_STEP_HISTORY.PAYLOAD에 JSON으로
	 *                 저장되어 나중에 compensate()가 보상 호출 시 그대로 복원해서 재사용하는 값이다.
	 */
	private void runStep(String sagaId, String stepName, Map<String, Object> command) {
		this.info(signatureLog());
		SagaStepHandler handler = findHandler(stepName);
		try {
			Map<String, Object> result = handler.handle(command);
			if (result == null) {
				result = new HashMap<String, Object>();
			}
			// 보상 캐스케이딩과 리스너의 다음 스텝 트리거 모두 sagaId를 필요로 하므로, 핸들러 구현과 무관하게 항상 심어준다.
			result.put("SAGA_ID", sagaId);
			sagaStore.insertStepHistory(historyRow(sagaId, stepName, "SUCCESS", null, command));
			sagaStore.updateStatus(sagaId, SagaStatus.STEP_DONE.name(), stepName);
			// 다음 스텝 트리거용 이벤트. 토픽명은 관례상 "{stepName}-reply"를 기본으로 한다.
			// topic="{stepName}-reply"(Kafka 토픽), key=sagaId(Kafka 파티션 키), payload=result(Kafka 메시지 value, JSON 직렬화)
			outboxAppender.append(stepName + "-reply", sagaId, result);
		} catch (Exception e) {
			this.error("saga[" + sagaId + "] step[" + stepName + "] 실패: " + e.getMessage());
			sagaStore.insertStepHistory(historyRow(sagaId, stepName, "FAILED", e.getMessage(), command));
			this.compensate(sagaId, stepName);
		}
	}

	/**
	 * <pre>
	 * 실패한 스텝 이전에 이미 성공한 스텝들을 역순(최신 성공 스텝부터)으로 되돌린다.
	 * 실패한 스텝 자신은 성공한 적이 없으므로 보상 대상이 아니다.
	 * 중요: 이 보상 흐름은 Kafka/Outbox를 전혀 거치지 않는다 — sagaStore.findSuccessStepHistory()로
	 * DB에서 직접 성공 이력을 읽고, handler.compensate()를 같은 JVM/스레드에서 동기 호출하는
	 * 순수 로컬 처리다. 즉 보상은 이벤트 기반이 아니라 오케스트레이터가 직접 지휘하는 구조라서,
	 * "누가 무엇을 보상해야 하는지"에 대한 조율 로직이 이 클래스 하나에만 존재한다(SEC 방식의 특징).
	 * </pre>
	 * @param sagaId    보상 대상 사가의 식별자.
	 * @param failedStep 실패가 발생한 스텝 이름(상태 갱신 기록용, TB_SAGA_INSTANCE.CURRENT_STEP).
	 */
	private void compensate(String sagaId, String failedStep) {
		this.info(signatureLog());
		sagaStore.updateStatus(sagaId, SagaStatus.COMPENSATING.name(), failedStep);
		List<Map<String, Object>> succeededSteps = sagaStore.findSuccessStepHistory(sagaId);
		for (Map<String, Object> row : succeededSteps) {
			String stepName = (String) row.get("STEP_NAME");
			try {
				SagaStepHandler handler = findHandler(stepName);
				Map<String, Object> payload = parsePayload((String) row.get("PAYLOAD"));
				handler.compensate(payload);
			} catch (Exception e) {
				this.error("saga[" + sagaId + "] compensate[" + stepName + "] 중 오류: " + e.getMessage());
			}
		}
		sagaStore.updateStatus(sagaId, SagaStatus.FAILED.name(), failedStep);
	}

	private SagaStepHandler findHandler(String stepName) {
		this.info(signatureLog());
		for (SagaStepHandler handler : stepHandlers) {
			if (stepName.equals(handler.getStepName())) {
				return handler;
			}
		}
		throw new IllegalStateException("등록된 SagaStepHandler가 없습니다: " + stepName);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parsePayload(String payloadJson) {
		this.info(signatureLog());
		try {
			return objectMapper.readValue(payloadJson, Map.class);
		} catch (Exception e) {
			this.error("보상용 payload 파싱 실패: " + e.getMessage());
			return new HashMap<String, Object>();
		}
	}

	private Map<String, Object> historyRow(String sagaId, String stepName, String result, String errorMessage, Map<String, Object> command) {
		this.info(signatureLog());
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("SAGA_ID", sagaId);
		row.put("STEP_NAME", stepName);
		row.put("RESULT", result);
		row.put("ERROR_MSG", errorMessage);
		row.put("PAYLOAD", ConvertUtil.convertToJson(command));
		return row;
	}

}

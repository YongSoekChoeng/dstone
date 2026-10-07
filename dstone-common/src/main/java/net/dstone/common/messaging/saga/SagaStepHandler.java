package net.dstone.common.messaging.saga;

import java.util.Map;

/**
 * 사가의 한 스텝을 실제로 처리하는 핸들러 계약.
 * 각 모듈이 자기 도메인 로직으로 구현하여 @Component로 등록하면,
 * SagaOrchestrator가 Spring이 모아준 List<SagaStepHandler>에서 getStepName()으로 찾아서 실행한다.
 */
public interface SagaStepHandler {

	/**
	 * 이 핸들러가 담당하는 스텝 이름(사가 정의에서 참조하는 식별자).
	 * SagaOrchestrator.findHandler()가 이 값으로 핸들러를 찾고, 이 값 자체가
	 * 결과 이벤트의 Kafka 토픽명("{stepName}-reply")의 접두어로도 쓰인다.
	 */
	String getStepName();

	/**
	 * <pre>
	 * 정상 처리. 다음 스텝으로 전달할 결과를 반환한다.
	 * 이 반환값은 순수 로컬 리턴값이지만, SagaOrchestrator.runStep()이 여기에 "SAGA_ID" 필드를 주입한 뒤
	 * outboxAppender.append()를 거쳐 그대로 Kafka 메시지 value(JSON)로 발행되므로, 이 반환 Map의 각 필드는
	 * 곧 다음 스텝 리스너(@KafkaListener)가 payload.get(...)으로 읽게 될 필드와 동일하다.
	 * 여기서 예외를 던지면 오케스트레이터가 즉시 compensate 흐름으로 전환한다(Kafka 발행은 일어나지 않음).
	 *
	 * 멱등성 관련 주의: SagaOrchestrator.runStep()은 이 스텝이 이미 TB_SAGA_STEP_HISTORY에 SUCCESS로
	 * 기록돼 있으면 handle()을 다시 호출하지 않고 건너뛴다(Kafka at-least-once 재전달로 인한 중복 실행 방지,
	 * DB의 UX_SAGA_STEP(SAGA_ID, STEP_NAME) 유니크 제약이 최종 안전망). 다만 "이미 SUCCESS로 기록되기 전"에
	 * 두 번째 호출이 아주 근접한 시점에 들어오는 극히 좁은 동시성 구간에서는 handle()이 실제로 두 번 실행될
	 * 수 있다(중복 기록 자체는 막히지만, 그 전에 이미 side effect가 두 번 발생). 따라서 결제/재고차감처럼
	 * 실제 부수효과가 있는 구현체는 가능하면 자체적으로도 멱등하게(예: ORDER_ID 등 자연키로 중복 처리를
	 * 감지) 만드는 것이 안전하다.
	 * </pre>
	 * @param command 이전 스텝(또는 최초 호출자)이 넘긴 입력값. proceed() 경유 시에는 직전 Kafka 메시지의
	 *                value(역직렬화된 Map)가 그대로 들어온다.
	 * @return 다음 스텝으로 넘어갈 결과값. Kafka 메시지 value의 기반이 된다.
	 */
	Map<String, Object> handle(Map<String, Object> command) throws Exception;

	/**
	 * <pre>
	 * 보상 처리(실패 시 앞선 성공 스텝을 되돌림). 예외를 던지지 않는 것을 원칙으로 한다.
	 * Kafka/Outbox를 전혀 거치지 않고 SagaOrchestrator.compensate()에서 동기·로컬로 직접 호출된다.
	 * </pre>
	 * @param command TB_SAGA_STEP_HISTORY.PAYLOAD에 저장돼 있던, 해당 스텝을 "성공"시켰던 당시의 원본
	 *                command를 JSON에서 역직렬화해 복원한 값(Kafka 메시지가 아니라 DB에서 온 값).
	 */
	void compensate(Map<String, Object> command);

}

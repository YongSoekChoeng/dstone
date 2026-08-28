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
 * 오케스트레이션 방식 사가 엔진.
 * 각 스텝 처리는 로컬에서 SagaStepHandler로 즉시 실행하고, 그 결과(다음 스텝 트리거용 이벤트)는
 * 직접 Kafka로 보내지 않고 OutboxAppender를 거쳐 같은 트랜잭션/at-least-once 보장 위에서 발행한다.
 */
public class SagaOrchestrator extends BaseObject {

	private final SagaStore sagaStore;
	private final OutboxAppender outboxAppender;
	private final List<SagaStepHandler> stepHandlers;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public SagaOrchestrator(SagaStore sagaStore, OutboxAppender outboxAppender, List<SagaStepHandler> stepHandlers) {
		this.sagaStore = sagaStore;
		this.outboxAppender = outboxAppender;
		this.stepHandlers = stepHandlers;
	}

	/**
	 * 사가를 시작하고 첫 스텝을 실행한다.
	 * @param sagaType
	 * @param firstStep
	 * @param payload
	 * @return 생성된 sagaId
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
	 * 이전 스텝의 응답(Kafka reply 등)을 받아 다음 스텝을 진행시키고 싶을 때 호출.
	 * (다음 스텝 이름 결정은 사가 정의를 아는 모듈 쪽 호출자가 넘겨준다 — 엔진은 스텝 순서 자체를 모른다)
	 */
	public void proceed(String sagaId, String nextStep, Map<String, Object> command) {
		runStep(sagaId, nextStep, command);
	}

	/**
	 * 사가 정의를 아는 호출자(리스너 등)가 마지막 스텝의 reply까지 받은 뒤 호출해서 사가를 종결시킨다.
	 * 엔진 자체는 스텝 순서를 모르므로 "이게 마지막 스텝이다"는 판단은 호출자 책임이다.
	 */
	public void complete(String sagaId, String lastStep) {
		sagaStore.updateStatus(sagaId, SagaStatus.COMPLETED.name(), lastStep);
	}

	/**
	 * 사가의 첫 스텝을 실행한다.
	 * @param sagaId
	 * @param stepName
	 * @param command
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
			outboxAppender.append(stepName + "-reply", sagaId, result);
		} catch (Exception e) {
			this.error("saga[" + sagaId + "] step[" + stepName + "] 실패: " + e.getMessage());
			sagaStore.insertStepHistory(historyRow(sagaId, stepName, "FAILED", e.getMessage(), command));
			this.compensate(sagaId, stepName);
		}
	}

	/**
	 * 실패한 스텝 이전에 이미 성공한 스텝들을 역순(최신 성공 스텝부터)으로 되돌린다.
	 * 실패한 스텝 자신은 성공한 적이 없으므로 보상 대상이 아니다.
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

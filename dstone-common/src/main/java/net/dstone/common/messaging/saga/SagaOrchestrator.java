package net.dstone.common.messaging.saga;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.dstone.common.core.BaseObject;
import net.dstone.common.messaging.outbox.OutboxAppender;

/**
 * 오케스트레이션 방식 사가 엔진.
 * 각 스텝 처리는 로컬에서 SagaStepHandler로 즉시 실행하고, 그 결과(다음 스텝 트리거용 이벤트)는
 * 직접 Kafka로 보내지 않고 OutboxAppender를 거쳐 같은 트랜잭션/at-least-once 보장 위에서 발행한다.
 */
public class SagaOrchestrator extends BaseObject {

	private final SagaStore sagaStore;
	private final OutboxAppender outboxAppender;
	private final List<SagaStepHandler> stepHandlers;

	public SagaOrchestrator(SagaStore sagaStore, OutboxAppender outboxAppender, List<SagaStepHandler> stepHandlers) {
		this.sagaStore = sagaStore;
		this.outboxAppender = outboxAppender;
		this.stepHandlers = stepHandlers;
	}

	/**
	 * 사가를 시작하고 첫 스텝을 실행한다.
	 * @return 생성된 sagaId
	 */
	public String start(String sagaType, String firstStep, Map<String, Object> payload) {
		String sagaId = UUID.randomUUID().toString();

		Map<String, Object> saga = new HashMap<String, Object>();
		saga.put("SAGA_ID", sagaId);
		saga.put("SAGA_TYPE", sagaType);
		saga.put("STATUS", SagaStatus.STARTED.name());
		saga.put("CURRENT_STEP", firstStep);
		sagaStore.insert(saga);

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

	private void runStep(String sagaId, String stepName, Map<String, Object> command) {
		SagaStepHandler handler = findHandler(stepName);
		try {
			Map<String, Object> result = handler.handle(command);
			sagaStore.insertStepHistory(historyRow(sagaId, stepName, "SUCCESS", null));
			sagaStore.updateStatus(sagaId, SagaStatus.STEP_DONE.name(), stepName);
			// 다음 스텝 트리거용 이벤트. 토픽명은 관례상 "{stepName}-reply"를 기본으로 한다.
			outboxAppender.append(stepName + "-reply", sagaId, result);
		} catch (Exception e) {
			this.error("saga[" + sagaId + "] step[" + stepName + "] 실패: " + e.getMessage());
			sagaStore.insertStepHistory(historyRow(sagaId, stepName, "FAILED", e.getMessage()));
			compensate(sagaId, stepName, command);
		}
	}

	private void compensate(String sagaId, String failedStep, Map<String, Object> command) {
		sagaStore.updateStatus(sagaId, SagaStatus.COMPENSATING.name(), failedStep);
		try {
			SagaStepHandler handler = findHandler(failedStep);
			handler.compensate(command);
		} catch (Exception e) {
			this.error("saga[" + sagaId + "] compensate[" + failedStep + "] 중 오류: " + e.getMessage());
		}
		sagaStore.updateStatus(sagaId, SagaStatus.FAILED.name(), failedStep);
	}

	private SagaStepHandler findHandler(String stepName) {
		for (SagaStepHandler handler : stepHandlers) {
			if (stepName.equals(handler.getStepName())) {
				return handler;
			}
		}
		throw new IllegalStateException("등록된 SagaStepHandler가 없습니다: " + stepName);
	}

	private Map<String, Object> historyRow(String sagaId, String stepName, String result, String errorMessage) {
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("SAGA_ID", sagaId);
		row.put("STEP_NAME", stepName);
		row.put("RESULT", result);
		row.put("ERROR_MSG", errorMessage);
		return row;
	}

}

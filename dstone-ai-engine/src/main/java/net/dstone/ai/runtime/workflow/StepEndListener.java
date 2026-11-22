package net.dstone.ai.runtime.workflow;

/**
 * <pre>
 * Workflow step 하나가 끝날 때마다 불리는 bean입니다(WorkFlowExecutor).
 *
 * 성공, 실패, 예외, 승인 대기 가리지 않고 step이 끝나면 부릅니다.
 * step 안에서 Tool이 띄운 것(예: 임시 시험 서버 프로세스)을 그 step이 끝날 때 꼭 정리하려고 둡니다.
 * 엔진은 무엇을 정리하는지 모르고, 구현하는 쪽이 실행 id와 step id로 자기 것을 찾아 정리합니다.
 * 구현은 예외를 던지지 않아야 합니다. 던져도 엔진은 로그만 남기고 다음으로 갑니다.
 * </pre>
 */
public interface StepEndListener {

	/**
	 * @param executionId 끝난 step의 Workflow 실행 id
	 * @param stepId      끝난 step id
	 */
	void stepEnded(String executionId, String stepId);

}

package net.dstone.ai.runtime;

/**
 * WorkFlowExecutor 내부에서 "이 step 다음에 뭘 할지"를 표현하는 값이다.
 * 
 * @param status     step 처리 상태
 * @param nextStepId 다음에 이어갈 step id
 * @param message    성공/실패 시 남기는 메시지
 */
public record StepFlow(StepStatus status, String nextStepId, String message) {

	/**
	 * @param nextStepId 다음에 이어갈 step id
	 */
	public static StepFlow next(String nextStepId) {
		return new StepFlow(StepStatus.NEXT_STEP, nextStepId, null);
	}

	/**
	 * @param nextStepId 다시 돌아갈 step id
	 */
	public static StepFlow loop(String nextStepId) {
		return new StepFlow(StepStatus.LOOP, nextStepId, null);
	}

	/**
	 * @param message 성공 메시지
	 */
	public static StepFlow success(String message) {
		return new StepFlow(StepStatus.SUCCESS, null, message);
	}

	/**
	 * @param message 실패 사유 메시지
	 */
	public static StepFlow fail(String message) {
		return new StepFlow(StepStatus.FAIL, null, message);
	}

}

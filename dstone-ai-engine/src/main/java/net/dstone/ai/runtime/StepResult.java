package net.dstone.ai.runtime;

/** WorkflowExecutor 내부에서 "이 step 다음에 뭘 할지"를 표현하는 값이다. */
public record StepResult(StepStatus status, String nextStepId, String message) {

	public static StepResult next(String nextStepId) {
		return new StepResult(StepStatus.NEXT_STEP, nextStepId, null);
	}

	public static StepResult loop(String nextStepId) {
		return new StepResult(StepStatus.LOOP, nextStepId, null);
	}

	public static StepResult success(String message) {
		return new StepResult(StepStatus.SUCCESS, null, message);
	}

	public static StepResult fail(String message) {
		return new StepResult(StepStatus.FAIL, null, message);
	}

}

package net.dstone.ai.runtime.status;

/**
 * WorkFlowExecutor 내부에서 "이 스텝 다음에 뭘 할지"를 표현하는 값이다.
 *
 * @param status     스텝 처리 상태
 * @param nextStepId 다음에 이어갈 스텝 id(NEXT_STEP/LOOP일 때만 값이 있음)
 * @param message    성공/실패/에러 시 남기는 메시지
 */
public record StepFlow(StepStatus status, String nextStepId, String message) {

	/** @param nextStepId 다음에 이어갈 스텝 id */
	public static StepFlow next(String nextStepId) {
		return new StepFlow(StepStatus.NEXT_STEP, nextStepId, null);
	}

	/** @param nextStepId 다시 돌아갈 스텝 id */
	public static StepFlow loop(String nextStepId) {
		return new StepFlow(StepStatus.LOOP, nextStepId, null);
	}

	/** @param message 성공 메시지 */
	public static StepFlow success(String message) {
		return new StepFlow(StepStatus.SUCCESS, null, message);
	}

	/** @param message 실패 사유 메시지 */
	public static StepFlow fail(String message) {
		return new StepFlow(StepStatus.FAIL, null, message);
	}

	/** @param message 예외 메시지 */
	public static StepFlow error(String message) {
		return new StepFlow(StepStatus.ERROR, null, message);
	}

	/** 승인 대기로 실행을 멈출 때 - 다음 스텝id/메시지 둘 다 의미가 없다(같은 스텝을 재실행해서 재개하므로). */
	public static StepFlow waitingApproval() {
		return new StepFlow(StepStatus.WAITING_APPROVAL, null, null);
	}

}

package net.dstone.ai.runtime.status;

/**
 * WorkFlowExecutor가 스텝 하나를 실행한 뒤 "이 스텝 다음에 뭘 해야 하는지"를 담아서 돌려주는 값입니다.
 *
 * @param status     이번 스텝이 어떤 상태로 끝났는지입니다(성공/실패/다음 스텝으로/루프로 되돌아감 등).
 * @param nextStepId 다음에 이어서 실행할 스텝의 id입니다. status가 NEXT_STEP이거나 LOOP일 때만 값이 들어있습니다.
 * @param message    성공했을 때, 실패했을 때, 에러가 났을 때 남기는 설명 메시지입니다.
 */
public record StepFlow(StepStatus status, String nextStepId, String message) {

	/** 목록상 다음 스텝으로 그대로 넘어갈 때 씁니다. @param nextStepId 다음에 이어갈 스텝의 id입니다. */
	public static StepFlow next(String nextStepId) {
		return new StepFlow(StepStatus.NEXT_STEP, nextStepId, null);
	}

	/** 실패해서 앞쪽의 다른 스텝으로 되돌아갈 때 씁니다. @param nextStepId 다시 돌아갈 스텝의 id입니다. */
	public static StepFlow loop(String nextStepId) {
		return new StepFlow(StepStatus.LOOP, nextStepId, null);
	}

	/** Workflow 전체를 성공으로 끝낼 때 씁니다. @param message 남길 성공 메시지입니다. */
	public static StepFlow success(String message) {
		return new StepFlow(StepStatus.SUCCESS, null, message);
	}

	/** Workflow 전체를 실패로 끝낼 때 씁니다. @param message 실패한 이유를 담은 메시지입니다. */
	public static StepFlow fail(String message) {
		return new StepFlow(StepStatus.FAIL, null, message);
	}

	/** 실행 중 예외가 나서 Workflow를 끝낼 때 씁니다. @param message 예외 내용을 담은 메시지입니다. */
	public static StepFlow error(String message) {
		return new StepFlow(StepStatus.ERROR, null, message);
	}

	/** 사람의 승인을 기다리며 실행을 멈출 때 씁니다. 다음 스텝 id와 메시지는 둘 다 필요 없습니다 - 나중에 같은 스텝을 다시 실행해서 이어가기 때문입니다. */
	public static StepFlow waitingApproval() {
		return new StepFlow(StepStatus.WAITING_APPROVAL, null, null);
	}

}

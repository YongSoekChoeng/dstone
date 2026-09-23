package net.dstone.ai.runtime.workflow;

/**
 * WorkFlowExecutor가 스텝 하나를 실행하고 난 뒤, 바로 다음에 무엇을 해야 하는지를 나타내는 값입니다.
 *
 * NextStep은 목록상 다음에 있는 스텝으로 그대로 진행한다는 뜻이고, Loop는 같은 스텝이나 이전 스텝으로
 * 되돌아가서 다시 시도한다는 뜻입니다. Done은 Workflow 전체가 성공으로 끝났다는 뜻이고, Failed는 반대로
 * 실패로 끝났다는 뜻입니다.
 *
 * 예전에는 StepStatus라는 enum에 NEXT_STEP/LOOP/SUCCESS/FAIL 외에 ERROR/WAITING_APPROVAL까지 6가지
 * 값이 있었지만, 뒤의 두 값은 실제로 한 번도 만들어지는 일이 없었습니다 - 스텝 실행 중 예외가 나거나
 * 승인 대기 상태가 되는 경우는 WorkFlowExecutor.run()이 이 타입을 거치지 않고 직접 처리하기 때문입니다
 * (예외는 persistFailed로 바로, 승인 대기는 StepRunResult.pendingResult()로 바로 처리됩니다). 그래서
 * 이번에 정리하면서 실제로 쓰이는 4가지 경우만 남겼습니다.
 */
public sealed interface WorkflowTransition {

	/** 목록상 다음 스텝으로 그대로 넘어갈 때 씁니다. @param stepId 다음에 이어갈 스텝의 id입니다. */
	record NextStep(String stepId) implements WorkflowTransition {
	}

	/** 실패해서 앞쪽의 다른 스텝으로 되돌아갈 때 씁니다. @param stepId 다시 돌아갈 스텝의 id입니다. */
	record Loop(String stepId) implements WorkflowTransition {
	}

	/** Workflow 전체를 성공으로 끝낼 때 씁니다. @param message 남길 성공 메시지입니다. */
	record Done(String message) implements WorkflowTransition {
	}

	/** Workflow 전체를 실패로 끝낼 때 씁니다. @param message 실패한 이유를 담은 메시지입니다. */
	record Failed(String message) implements WorkflowTransition {
	}

	/** 목록상 다음 스텝으로 그대로 넘어갈 때 씁니다. @param nextStepId 다음에 이어갈 스텝의 id입니다. */
	static WorkflowTransition next(String nextStepId) {
		return new NextStep(nextStepId);
	}

	/** 실패해서 앞쪽의 다른 스텝으로 되돌아갈 때 씁니다. @param nextStepId 다시 돌아갈 스텝의 id입니다. */
	static WorkflowTransition loop(String nextStepId) {
		return new Loop(nextStepId);
	}

	/** Workflow 전체를 성공으로 끝낼 때 씁니다. @param message 남길 성공 메시지입니다. */
	static WorkflowTransition done(String message) {
		return new Done(message);
	}

	/** Workflow 전체를 실패로 끝낼 때 씁니다. @param message 실패한 이유를 담은 메시지입니다. */
	static WorkflowTransition failed(String message) {
		return new Failed(message);
	}

}

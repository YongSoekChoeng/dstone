package net.dstone.ai.runtime.workflow;

/**
 * WorkFlowExecutor가 스텝 하나를 실행하고 난 뒤, 바로 다음에 무엇을 해야 하는지를 나타내는 값입니다.
 *
 * NextStep은 목록상 다음에 있는 스텝으로 그대로 진행한다는 뜻이고, Loop는 같은 스텝이나 이전 스텝으로
 * 되돌아가서 다시 시도한다는 뜻입니다. Done은 Workflow 전체가 성공으로 끝났다는 뜻이고, Failed는 반대로
 * 실패로 끝났다는 뜻입니다.
 *
 * 스텝 실행 중 예외가 나거나 승인 대기 상태가 되는 경우는 이 타입으로 표현하지 않습니다.
 * WorkFlowExecutor.run()이 그 경우를 직접 처리합니다(예외는 persistFailed로, 승인 대기는
 * StepRunResult.pendingResult()로 바로 처리됩니다).
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

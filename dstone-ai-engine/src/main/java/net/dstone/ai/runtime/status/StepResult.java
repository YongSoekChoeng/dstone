package net.dstone.ai.runtime.status;

/**
 * StepRunner 하나가 스텝을 실행한 뒤 낼 수 있는 결과 3가지. 처리 불가능한 시스템 예외(ERROR)는 이 enum에 없다 - 그건 StepOutput으로
 * 정상 반환되는 값이 아니라 StepRunner가 그냥 예외를 던지는 것으로 표현되고, WorkFlowExecutor가 그 예외를 잡아 처리한다.
 */
public enum StepResult {

	/** 정상 성공. */
	SUCCESS,

	/** 비즈니스 로직상 실패(문법 오류, 감독 Agent의 반려 등) - StepOutput.failureReason에 사유가 담긴다. */
	FAILURE,

	/** 아직 끝나지 않음 - ApprovalStepRunner가 사람의 승인/반려 결정을 아직 못 받았을 때만 반환한다. */
	PENDING

}

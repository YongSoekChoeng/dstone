package net.dstone.ai.runtime.status;

/**
 * StepRunner가 스텝 하나를 실행하고 난 뒤에 낼 수 있는 결과 세 가지입니다. 여기에 처리할 수 없는
 * 시스템 예외(ERROR) 같은 항목은 없는데, 그런 경우는 StepOutput으로 정상적으로 값을 돌려주는 대신
 * StepRunner가 그냥 예외를 던지는 방식으로 표현되고, 그 예외는 WorkFlowExecutor가 잡아서 처리하기
 * 때문입니다.
 */
public enum StepResult {

	/** 스텝이 정상적으로 성공했습니다. */
	SUCCESS,

	/** 업무 로직상 실패했습니다(예: 문법 오류가 있었거나, 감독 역할의 Agent가 반려한 경우). 왜 실패했는지는 StepOutput.failureReason에 담겨 있습니다. */
	FAILURE,

	/** 아직 결과가 나지 않았습니다. ApprovalStepRunner가 사람의 승인/반려 결정을 아직 받지 못했을 때만 이 값을 돌려줍니다. */
	PENDING

}

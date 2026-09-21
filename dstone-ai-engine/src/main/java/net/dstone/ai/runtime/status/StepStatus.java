package net.dstone.ai.runtime.status;

/**
 * WorkFlowExecutor가 스텝 하나를 실행하고 난 뒤, 바로 다음에 무엇을 해야 하는지를 나타내는 값입니다.
 *
 * NEXT_STEP은 목록상 다음에 있는 스텝으로 그대로 진행한다는 뜻이고, LOOP는 같은 스텝이나 이전 스텝으로
 * 되돌아가서 다시 시도한다는 뜻입니다. SUCCESS는 Workflow 전체가 성공으로 끝났다는 뜻이고, FAIL은
 * 반대로 실패로 끝났다는 뜻입니다. ERROR는 스텝 실행 중 예외가 나서, onFailure로 정해둔 다른 경로 없이
 * 그 자리에서 바로 실패로 끝났다는 뜻입니다. WAITING_APPROVAL은 승인이 필요한 스텝이 아직 사람의
 * 결정을 받지 못해서 실행을 잠시 멈춰뒀다는 뜻입니다.
 */
public enum StepStatus {
	NEXT_STEP, LOOP, SUCCESS, FAIL, ERROR, WAITING_APPROVAL
}

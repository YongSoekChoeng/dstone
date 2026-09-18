package net.dstone.ai.runtime.status;

/**
 * WorkFlowExecutor가 스텝 하나를 실행한 뒤 다음에 무엇을 할지 나타낸다.
 *
 * NEXT_STEP → 앞으로 있는 다른 스텝으로 진행. LOOP → 같은/이전 스텝으로 되돌아감(재시도). SUCCESS → Workflow 종료(성공).
 * FAIL → Workflow 종료(실패). ERROR → 스텝이 예외를 던져 onFailure 분기 없이 즉시 실패로 종료. WAITING_APPROVAL → 승인
 * 스텝이 아직 결정을 못 받아 실행을 일시 중단.
 */
public enum StepStatus {
	NEXT_STEP, LOOP, SUCCESS, FAIL, ERROR, WAITING_APPROVAL
}

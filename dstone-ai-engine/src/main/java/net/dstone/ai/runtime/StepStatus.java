package net.dstone.ai.runtime;

/**
 * WorkflowExecutor가 한 step을 실행한 뒤 다음에 무엇을 할지 나타낸다. NEXT_STEP → 앞으로 있는 다른 step으로 진행. LOOP → 같은/이전 step으로 되돌아감(재시도).
 * SUCCESS → Workflow 종료(성공). FAIL → Workflow 종료(실패, 예외 발생).
 */
public enum StepStatus {
	NEXT_STEP, LOOP, SUCCESS, FAIL
}

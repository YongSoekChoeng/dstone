package net.dstone.ai.common.consts;

/**
 * <pre>
 * 하나의 Workflow 실행(runtime.workflow.execution.WorkFlowExecution)이 지금 어느 단계에 있는지 나타낸다.
 *
 * RUNNING → 진행 중(동기 실행 도중이거나 비동기 job이 처리 중). 
 * WAITING_APPROVAL → APPROVAL 스텝에서 멈춰 사람의 결정을 기다리는 중. 
 * DONE/FAILED → 정상/비정상 종료. 
 * CANCELLED → 사람이 명시적으로 취소(상태값만 있고, 취소하는 API는 없다).
 * </pre>
 */
public enum WorkFlowExecutionStatus {
	/**
	 * 진행 중(동기 실행 도중이거나 비동기 job이 처리 중)
	 */
	RUNNING, 
	
	/**
	 * APPROVAL 스텝에서 멈춰 사람의 결정을 기다리는 중
	 */
	WAITING_APPROVAL, 
	
	/**
	 * 정상 종료
	 */
	DONE, 
	
	/**
	 * 비정상 종료
	 */
	FAILED, 
	
	/**
	 * 사람이 명시적으로 취소(상태값만 있고, 취소하는 API는 없음)
	 */
	CANCELLED
}

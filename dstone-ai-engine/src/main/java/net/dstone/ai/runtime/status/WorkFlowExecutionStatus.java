package net.dstone.ai.runtime.status;

/**
 * 하나의 Workflow 실행(runtime.workflow.execution.WorkFlowExecution)이 지금 어느 단계에 있는지 나타낸다.
 *
 * RUNNING → 진행 중(동기 실행 도중이거나 비동기 job이 처리 중). WAITING_APPROVAL → APPROVAL 스텝에서 멈춰 사람의
 * 결정을 기다리는 중. DONE/FAILED → 정상/비정상 종료. CANCELLED → 사람이 명시적으로 취소(현재는 상태값만 정의되어
 * 있고, 취소를 트리거하는 API는 아직 없다 - 실제 필요가 나오면 추가한다).
 */
public enum WorkFlowExecutionStatus {
	RUNNING, WAITING_APPROVAL, DONE, FAILED, CANCELLED
}

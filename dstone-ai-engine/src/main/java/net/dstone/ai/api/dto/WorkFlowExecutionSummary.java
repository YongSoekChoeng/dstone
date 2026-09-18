package net.dstone.ai.api.dto;

import java.time.Instant;

import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * GET /api/ai/workflow/executions(목록 조회) 한 항목. 상세 화면으로 넘어가기 전 "지금 뭐가 승인 대기 중인지, 뭐가
 * 실패했는지"를 한눈에 훑어보는 용도라 history는 담지 않는다 - 필요하면 executionId로 상세(WorkFlowExecutionDetail)를 조회한다.
 *
 * @param executionId      실행 id
 * @param workflowId       실행된 Workflow id
 * @param caller           호출한 앱/서비스 식별자(tenant)
 * @param status           실행 상태
 * @param currentStepIndex 지금 실행 중이거나 막 끝낸 스텝의 순번
 * @param createdAt        생성 시각
 * @param updatedAt        마지막 상태 변경 시각
 */
public record WorkFlowExecutionSummary(String executionId, String workflowId, String caller, String status, int currentStepIndex, Instant createdAt, Instant updatedAt) {

	/** @param execution 요약으로 바꿀 실행 상태 */
	public static WorkFlowExecutionSummary from(WorkFlowExecution execution) {
		return new WorkFlowExecutionSummary(execution.executionId(), execution.workflowId(), execution.caller(), execution.status().name(), execution.currentStepIndex(), execution.createdAt(), execution.updatedAt());
	}

}

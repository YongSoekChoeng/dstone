package net.dstone.ai.api.dto;

import java.time.Instant;

import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * GET /api/ai/workflow/executions(실행 목록 조회) 응답에 담기는 항목 하나입니다.
 *
 * 상세 화면으로 들어가기 전에 "지금 무엇이 승인 대기 중인지, 무엇이 실패했는지"를 목록에서 한눈에
 * 훑어보는 용도라서, 스텝별 이력(history)까지는 담지 않습니다. 이력이 필요하면 executionId로
 * 상세 조회(WorkFlowExecutionDetail)를 따로 호출하면 됩니다.
 *
 * @param executionId      이 실행을 가리키는 식별자입니다.
 * @param workflowId       실행된 Workflow의 id입니다.
 * @param caller           이 Workflow를 호출한 앱이나 서비스를 가리키는 식별자(tenant)입니다.
 * @param status           지금 이 실행이 어떤 상태인지를 나타냅니다.
 * @param currentStepIndex 지금 실행 중이거나 방금 끝난 스텝이 몇 번째 순서인지를 나타냅니다.
 * @param createdAt        이 실행이 처음 생성된 시각입니다.
 * @param updatedAt        상태가 마지막으로 바뀐 시각입니다.
 */
public record WorkFlowExecutionSummary(String executionId, String workflowId, String caller, String status, int currentStepIndex, Instant createdAt, Instant updatedAt) {

	/**
	 * WorkFlowExecution(실행 상태)을 받아, 목록에 보여줄 요약 항목 하나로 바꿔줍니다.
	 *
	 * @param execution 요약으로 바꿔줄 실행 상태입니다.
	 */
	public static WorkFlowExecutionSummary from(WorkFlowExecution execution) {
		return new WorkFlowExecutionSummary(execution.executionId(), execution.workflowId(), execution.caller(), execution.status().name(), execution.currentStepIndex(), execution.createdAt(), execution.updatedAt());
	}

}

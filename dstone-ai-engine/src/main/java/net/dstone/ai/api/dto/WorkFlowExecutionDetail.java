package net.dstone.ai.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * GET /api/ai/workflow/executions/{executionId}(상세 조회) 응답. variables는 승인 대기 화면에서 "지금까지 각 스텝이
 * 뭘 넘겼는지" 디버깅할 때 쓰라고 그대로 노출한다. history의 각 필드는 common.config.ConfigCallLog가 로그로 남기는 것과 같은 값이라 로그와 항상 일치한다.
 *
 * @param executionId      실행 id
 * @param workflowId       실행된 Workflow id
 * @param caller           호출한 앱/서비스 식별자(tenant)
 * @param status           실행 상태
 * @param currentStepIndex 지금 실행 중이거나 막 끝낸 스텝의 순번
 * @param variables        Workflow 전역 변수(호출 시 입력값 + 각 스텝이 누적한 값)
 * @param resultText       최종 성공 결과(끝나기 전에는 null)
 * @param errorMessage     실패/에러 사유(끝나기 전이거나 성공했으면 null)
 * @param createdAt        생성 시각
 * @param updatedAt        마지막 상태 변경 시각
 * @param history          스텝별 실행 이력(오래된 순)
 */
public record WorkFlowExecutionDetail(String executionId, String workflowId, String caller, String status, int currentStepIndex, Map<String, Object> variables, String resultText, String errorMessage, Instant createdAt, Instant updatedAt,
	List<StepHistoryEntry> history) {

	/**
	 * @param execution 상세로 바꿀 실행 상태
	 * @param history   함께 담을 스텝별 실행 이력
	 */
	public static WorkFlowExecutionDetail from(WorkFlowExecution execution, List<StepHistoryEntry> history) {
		return new WorkFlowExecutionDetail(execution.executionId(), execution.workflowId(), execution.caller(), execution.status().name(), execution.currentStepIndex(), execution.variables(), execution.resultText(), execution.errorMessage(),
			execution.createdAt(), execution.updatedAt(), history);
	}

}

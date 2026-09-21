package net.dstone.ai.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * GET /api/ai/workflow/executions/{executionId}(실행 상세 조회) 요청에 대한 응답입니다.
 *
 * variables는 승인 대기 화면 같은 곳에서 "지금까지 각 스텝이 어떤 값을 주고받았는지" 확인하며 디버깅할
 * 수 있도록 그대로 노출해 둔 값입니다. history에 담긴 각 필드는 common.config.ConfigCallLog가 로그로
 * 남기는 값과 똑같기 때문에, 이 응답과 실제 로그 내용은 항상 서로 일치합니다.
 *
 * @param executionId      이 실행을 가리키는 식별자입니다.
 * @param workflowId       실행된 Workflow의 id입니다.
 * @param caller           이 Workflow를 호출한 앱이나 서비스를 가리키는 식별자(tenant)입니다.
 * @param status           지금 이 실행이 어떤 상태인지를 나타냅니다.
 * @param currentStepIndex 지금 실행 중이거나 방금 끝난 스텝이 몇 번째 순서인지를 나타냅니다.
 * @param variables        Workflow 전체에서 공유하는 변수 값입니다. 호출할 때 넘긴 입력값과, 그동안
 *                         각 스텝이 만들어낸 값이 함께 쌓여 있습니다.
 * @param resultText       Workflow가 성공적으로 끝났을 때의 최종 결과입니다. 아직 끝나지 않았다면 null입니다.
 * @param errorMessage     Workflow가 실패했을 때 그 사유입니다. 아직 끝나지 않았거나 성공했다면 null입니다.
 * @param createdAt        이 실행이 처음 생성된 시각입니다.
 * @param updatedAt        상태가 마지막으로 바뀐 시각입니다.
 * @param history          이 실행에서 각 스텝이 실행된 이력입니다. 오래된 순서대로 담겨 있습니다.
 */
public record WorkFlowExecutionDetail(String executionId, String workflowId, String caller, String status, int currentStepIndex, Map<String, Object> variables, String resultText, String errorMessage, Instant createdAt, Instant updatedAt,
	List<StepHistoryEntry> history) {

	/**
	 * WorkFlowExecution(실행 상태)과 스텝별 이력을 받아, 응답으로 내려줄 상세 정보 하나로 합쳐줍니다.
	 *
	 * @param execution 상세 응답으로 바꿔줄 실행 상태입니다.
	 * @param history   함께 담을 스텝별 실행 이력입니다.
	 */
	public static WorkFlowExecutionDetail from(WorkFlowExecution execution, List<StepHistoryEntry> history) {
		return new WorkFlowExecutionDetail(execution.executionId(), execution.workflowId(), execution.caller(), execution.status().name(), execution.currentStepIndex(), execution.variables(), execution.resultText(), execution.errorMessage(),
			execution.createdAt(), execution.updatedAt(), history);
	}

}

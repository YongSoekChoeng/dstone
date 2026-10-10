package net.dstone.ai.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.dstone.ai.api.service.ContextShortener;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * GET /api/ai/workflow/executions/{executionId}(실행 상세 조회) 요청에 대한 응답입니다.
 *
 * context는 승인 대기 화면 같은 곳에서 "지금까지 각 스텝이 어떤 값을 주고받았는지" 확인하며 디버깅할
 * 수 있도록 실행 컨텍스트 트리를 노출해 둔 값입니다(모양은 runtime.workflow.execution.WorkFlowContext 참고).
 * 다만 읽어 둔 파일 내용처럼 아주 긴 글자는 앞부분만 담습니다(api.service.ContextShortener, 전체는 full=true). history에 담긴 각 필드는 common.config.ConfigCallLog가 로그로
 * 남기는 값과 똑같기 때문에, 이 응답과 실제 로그 내용은 항상 서로 일치합니다.
 *
 * @param executionId      이 실행을 가리키는 식별자입니다.
 * @param workflowId       실행된 Workflow의 id입니다.
 * @param caller           이 Workflow를 호출한 앱이나 서비스를 가리키는 식별자(tenant)입니다.
 * @param status           지금 이 실행이 어떤 상태인지를 나타냅니다.
 * @param currentStepIndex 지금 실행 중이거나 방금 끝난 스텝이 몇 번째 순서인지를 나타냅니다.
 * @param context          실행 컨텍스트 트리입니다. 호출할 때 넘긴 입력값(input)과, 그동안 각 스텝이
 *                         저장한 결과(state)가 함께 쌓여 있습니다. 긴 글자는 앞부분만 담겨 있을 수 있습니다.
 * @param output           Workflow가 성공적으로 끝났을 때의 최종 결과입니다(글자 또는 객체). 아직 끝나지 않았다면 null입니다.
 * @param errorMessage     Workflow가 실패했을 때 그 사유입니다. 아직 끝나지 않았거나 성공했다면 null입니다.
 * @param createdAt        이 실행이 처음 생성된 시각입니다.
 * @param updatedAt        상태가 마지막으로 바뀐 시각입니다.
 * @param history          이 실행에서 각 스텝이 실행된 이력입니다. 오래된 순서대로 담겨 있습니다.
 * @param pendingApproval  승인 대기(WAITING_APPROVAL)일 때, 지금 어떤 결정을 기다리는지입니다(step id, 고를 수 있는 선택지). 승인 대기가 아니면 null입니다.
 */
public record WorkFlowExecutionDetail(String executionId, String workflowId, String caller, String status, int currentStepIndex, Map<String, Object> context, Object output, String errorMessage, Instant createdAt, Instant updatedAt,
	List<StepHistoryEntry> history, PendingApproval pendingApproval) {

	/**
	 * WorkFlowExecution(실행 상태)과 스텝별 이력을 받아, 응답으로 내려줄 상세 정보 하나로 합쳐줍니다.
	 *
	 * @param execution 상세 응답으로 바꿔줄 실행 상태입니다.
	 * @param history   함께 담을 스텝별 실행 이력입니다.
	 * @param pendingApproval 승인 대기일 때 기다리는 결정입니다(아니면 null).
	 * @param maxValueChars 컨텍스트 안의 글자 하나를 몇 자까지 그대로 담을지입니다. 0 이하면 줄이지 않고 전부 담습니다.
	 */
	public static WorkFlowExecutionDetail from(WorkFlowExecution execution, List<StepHistoryEntry> history, PendingApproval pendingApproval, int maxValueChars) {
		Map<String, Object> context = ContextShortener.shortenContext(execution.context(), maxValueChars);
		return new WorkFlowExecutionDetail(execution.executionId(), execution.workflowId(), execution.caller(), execution.status().name(), execution.currentStepIndex(), context, execution.output(), execution.errorMessage(),
			execution.createdAt(), execution.updatedAt(), history, pendingApproval);
	}

}

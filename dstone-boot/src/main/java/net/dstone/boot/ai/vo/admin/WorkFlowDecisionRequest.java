package net.dstone.boot.ai.vo.admin;

/**
 * /ai/admin/workflow/decision.do 요청 바디. dstone-ai-engine의 POST /api/ai/workflow/executions/{executionId}/decision을
 * 그대로 호출하기 위해 executionId까지 화면에서 함께 받는다.
 *
 * @param executionId 결정을 내릴 실행 id
 * @param approved    승인이면 true, 반려면 false
 * @param approver    결정한 사람/역할
 * @param comment     결정 사유/메모(선택)
 */
public record WorkFlowDecisionRequest(String executionId, boolean approved, String approver, String comment) {
}

package net.dstone.ai.api.dto;

/**
 * POST /api/ai/workflow/executions/{executionId}/decision 요청 본문. approver/comment는 서버가 실제 권한을
 * 검증하는 값이 아니라 감사/표시 목적으로만 기록된다(누가 실제로 승인 권한이 있는지는 dstone-ai-engine 밖의 시스템 책임).
 *
 * @param approved 승인이면 true, 반려면 false
 * @param approver 결정한 사람/역할
 * @param comment  결정 사유/메모(선택)
 */
public record WorkFlowDecisionRequest(boolean approved, String approver, String comment) {
}

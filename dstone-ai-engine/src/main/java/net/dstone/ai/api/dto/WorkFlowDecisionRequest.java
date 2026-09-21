package net.dstone.ai.api.dto;

/**
 * POST /api/ai/workflow/executions/{executionId}/decision 요청에 담을 내용입니다. APPROVAL step에서
 * 사람이 승인하거나 반려할 때 이 요청을 보냅니다.
 *
 * approver와 comment는 서버가 실제로 그 사람에게 승인 권한이 있는지 검증하는 데 쓰는 값이 아니라,
 * 누가 왜 그렇게 결정했는지 기록만 남겨두기 위한 값입니다. 실제로 승인 권한이 있는 사람인지 확인하는
 * 일은 dstone-ai-engine 바깥의 다른 시스템이 책임집니다.
 *
 * @param approved 승인이면 true, 반려면 false입니다.
 * @param approver 이 결정을 내린 사람이나 역할을 적습니다.
 * @param comment  결정한 이유나 메모입니다. 적지 않아도 됩니다.
 */
public record WorkFlowDecisionRequest(boolean approved, String approver, String comment) {
}

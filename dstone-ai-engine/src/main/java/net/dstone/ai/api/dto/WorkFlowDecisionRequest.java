package net.dstone.ai.api.dto;

/**
 * POST /api/ai/workflow/executions/{executionId}/decision 요청에 담을 내용입니다. APPROVAL step에서
 * 사람이 결정을 내릴 때 이 요청을 보냅니다.
 *
 * 멈춰 있는 APPROVAL step이 어떤 방식인지에 따라 보내는 값이 다릅니다.
 * - 승인/반려 방식(step에 routes가 없음): approved를 보냅니다. route는 보내지 않습니다.
 * - 선택지 방식(step에 routes가 있음): route에 선택지 이름 하나를 보냅니다. routes에 없는 이름이면 400입니다.
 *   approved는 쓰지 않습니다.
 * 어떤 방식인지, 고를 수 있는 이름이 무엇인지는 실행 상세 조회 응답의 pendingApproval에 들어 있습니다.
 *
 * approver와 comment는 서버가 실제로 그 사람에게 승인 권한이 있는지 검증하는 데 쓰는 값이 아니라,
 * 누가 왜 그렇게 결정했는지 기록만 남겨두기 위한 값입니다. 실제로 승인 권한이 있는 사람인지 확인하는
 * 일은 dstone-ai-engine 바깥의 다른 시스템이 책임집니다.
 *
 * @param approved 승인이면 true, 반려면 false입니다(승인/반려 방식에서만 씁니다). 보내지 않으면 반려로 봅니다.
 *                 선택지 방식에서는 이 값을 아예 보내지 않아도 되도록 boolean이 아니라 Boolean으로 받습니다
 *                 (boolean이면 값이 빠진 요청을 JSON으로 읽는 단계에서 400으로 거절합니다).
 * @param approver 이 결정을 내린 사람이나 역할을 적습니다.
 * @param comment  결정한 이유나 메모입니다. 적지 않아도 됩니다.
 * @param route    고른 선택지 이름입니다(선택지 방식에서만 씁니다).
 */
public record WorkFlowDecisionRequest(Boolean approved, String approver, String comment, String route) {
}

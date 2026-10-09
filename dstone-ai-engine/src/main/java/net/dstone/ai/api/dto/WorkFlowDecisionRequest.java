package net.dstone.ai.api.dto;

/**
 * <pre>
 * POST /api/ai/workflow/executions/{executionId}/decision 요청에 담을 내용입니다. APPROVAL step에서
 * 사람이 결정을 내릴 때 이 요청을 보냅니다.
 *
 * decision에 멈춰 있는 APPROVAL step의 routes 이름 중 하나를 보냅니다(예: APPROVED, REJECTED, CHANGES_REQUESTED).
 * routes에 없는 이름이면 400입니다. 고를 수 있는 이름은 실행 상세 조회 응답의 pendingApproval.routes에 들어 있습니다.
 *
 * 예전 화면과 맞추려고 두 가지를 더 받습니다.
 * - route: decision과 같은 뜻입니다(decision이 없을 때 씁니다).
 * - approved: decision과 route가 모두 없을 때만 봅니다. true는 APPROVED, false는 REJECTED입니다.
 *
 * approver와 comment는 서버가 실제로 그 사람에게 승인 권한이 있는지 검증하는 데 쓰는 값이 아니라,
 * 누가 왜 그렇게 결정했는지 기록만 남겨두기 위한 값입니다. 실제로 승인 권한이 있는 사람인지 확인하는
 * 일은 dstone-ai-engine 바깥의 다른 시스템이 책임집니다.
 * </pre>
 *
 * @param decision 고른 결정 이름입니다.
 * @param approved decision/route가 없을 때 쓰는 승인 여부입니다(없어도 됩니다).
 * @param approver 이 결정을 내린 사람이나 역할을 적습니다.
 * @param comment  결정한 이유나 메모입니다. step에 requireCommentOnReject를 적었으면 APPROVED가 아닌 결정에 꼭 있어야 합니다.
 * @param route    decision과 같은 뜻입니다(예전 이름).
 */
public record WorkFlowDecisionRequest(String decision, Boolean approved, String approver, String comment, String route) {

	/** 고른 결정 이름입니다. decision이 없으면 route를 씁니다. 둘 다 없으면 null입니다. */
	public String chosen() {
		if (this.decision != null && !this.decision.isBlank()) {
			return this.decision;
		}
		return this.route == null || this.route.isBlank() ? null : this.route;
	}

}

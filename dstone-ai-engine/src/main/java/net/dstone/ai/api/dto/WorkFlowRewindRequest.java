package net.dstone.ai.api.dto;

/**
 * POST /api/ai/workflow/executions/{executionId}/rewind 요청 본문입니다.
 *
 * @param fromStep   다시 시작할 step의 id입니다. 실행 기록에 있는 step만 고를 수 있습니다.
 * @param clearState true면 그 step부터 뒤에서 저장한 state 값을 지우고 시작합니다(없으면 false).
 */
public record WorkFlowRewindRequest(String fromStep, Boolean clearState) {

	/** clearState를 보내지 않았으면 false로 봅니다. */
	public boolean clear() {
		return Boolean.TRUE.equals(this.clearState);
	}

}

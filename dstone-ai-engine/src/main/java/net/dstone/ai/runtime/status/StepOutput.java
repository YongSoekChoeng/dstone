package net.dstone.ai.runtime.status;

import java.util.Map;

/**
 * StepRunner 하나를 실행한 결과. result로 성공/실패/대기를 가르고, primaryText는 다음 스텝의 {previous} 토큰에 그대로 이어지는
 * 주 결과다. data는 다음 스텝들이 {변수명}으로 참조할 수 있게 Workflow 전역 변수에 병합할 구조화 값인데, 대부분의 스텝은 텍스트 하나만
 * 돌려주면 충분하므로 비워도 된다(Map.of()).
 *
 * @param result        성공/실패/대기 여부
 * @param primaryText   다음 스텝의 {previous} 토큰에 바인딩될 주 결과 텍스트
 * @param data          Workflow 전역 변수에 병합할 구조화 결과(없으면 빈 Map)
 * @param failureReason FAILURE일 때 사유, 그 외에는 null
 */
public record StepOutput(StepResult result, String primaryText, Map<String, Object> data, String failureReason) {

	/** @param text 다음 스텝으로 이어질 성공 결과 텍스트 */
	public static StepOutput success(String text) {
		return new StepOutput(StepResult.SUCCESS, text, Map.of(), null);
	}

	/**
	 * @param text   다음 스텝(주로 onFailure로 되돌아가는 재작성 스텝)으로 이어질 텍스트
	 * @param reason 실패 사유
	 */
	public static StepOutput failure(String text, String reason) {
		return new StepOutput(StepResult.FAILURE, text, Map.of(), reason);
	}

	/** 아직 사람의 승인/반려 결정이 나지 않았을 때(ApprovalStepRunner 전용). */
	public static StepOutput pending() {
		return new StepOutput(StepResult.PENDING, null, Map.of(), null);
	}

}

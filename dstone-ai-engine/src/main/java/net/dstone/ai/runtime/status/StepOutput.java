package net.dstone.ai.runtime.status;

import java.util.Map;

/**
 * StepRunner 하나를 실행한 결과. result로 성공/실패/대기를 가르고, primaryText는 다음 스텝의 {previous} 토큰에 그대로 이어지는
 * 주 결과다. data는 다음 스텝들이 {stepId.키}로 참조할 수 있게 Workflow 전역 변수에 병합할 구조화 값인데(runtime.workflow.WorkFlowExecutor가
 * 이 step의 id를 접두사로 붙여 네임스페이싱한다 - 병렬 그룹의 형제 step끼리 같은 키를 반환해도 서로 덮어쓰지 않는다), 대부분의 스텝은 텍스트 하나만
 * 돌려주면 충분하므로 비워도 된다(Map.of()). {stepId.키} 토큰은 TOOL step의 inputTemplate(단순 문자열 치환)에서만 쓸 수 있다 - Agent의
 * prompt:는 Spring AI PromptTemplate(StringTemplate 기반)으로 렌더링되는데, 그 엔진은 속성 이름에 '.'을 허용하지 않으므로
 * runtime.agent.AgentExecutor가 prompt 렌더링 직전에 dot 섞인 키를 걸러낸다(promptSafeVariables() 참고).
 *
 * @param result        성공/실패/대기 여부
 * @param primaryText   다음 스텝의 {previous} 토큰에 바인딩될 주 결과 텍스트
 * @param data          Workflow 전역 변수에 {stepId.키}로 병합할 구조화 결과(없으면 빈 Map)
 * @param failureReason FAILURE일 때 사유, 그 외에는 null
 * @param route         StepType.ROUTER 전용 - LLM이 고른 route 키(runtime.status.RouteDecision.route()). ROUTER가 아니면 항상 null
 */
public record StepOutput(StepResult result, String primaryText, Map<String, Object> data, String failureReason, String route) {

	/** @param text 다음 스텝으로 이어질 성공 결과 텍스트 */
	public static StepOutput success(String text) {
		return new StepOutput(StepResult.SUCCESS, text, Map.of(), null, null);
	}

	/**
	 * @param text 다음 스텝으로 이어질 성공 결과 텍스트
	 * @param data Workflow 전역 변수에 {stepId.키}로 병합할 구조화 결과(예: StepDefinition.structuredOutput=true인 AGENT step의 StepPayload.data())
	 */
	public static StepOutput successWithData(String text, Map<String, Object> data) {
		return new StepOutput(StepResult.SUCCESS, text, data == null ? Map.of() : data, null, null);
	}

	/**
	 * @param text   다음 스텝(주로 onFailure로 되돌아가는 재작성 스텝)으로 이어질 텍스트
	 * @param reason 실패 사유
	 */
	public static StepOutput failure(String text, String reason) {
		return new StepOutput(StepResult.FAILURE, text, Map.of(), reason, null);
	}

	/** 아직 사람의 승인/반려 결정이 나지 않았을 때(ApprovalStepRunner 전용). */
	public static StepOutput pending() {
		return new StepOutput(StepResult.PENDING, null, Map.of(), null, null);
	}

	/**
	 * StepType.ROUTER 전용 - LLM이 고른 route를 실어 나른다(항상 SUCCESS로 취급되고, 그 route를 어느 step으로 이을지는
	 * runtime.workflow.WorkFlowExecutor가 StepDefinition.routes를 보고 정한다).
	 *
	 * @param text  다음 스텝으로 이어질 텍스트(보통 이 step의 입력을 그대로 전달)
	 * @param data  Workflow 전역 변수에 {stepId.키}로 병합할 구조화 결과(없으면 빈 Map)
	 * @param route StepDefinition.routes의 키 중 하나
	 */
	public static StepOutput routed(String text, Map<String, Object> data, String route) {
		return new StepOutput(StepResult.SUCCESS, text, data == null ? Map.of() : data, null, route);
	}

}

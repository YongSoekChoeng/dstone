package net.dstone.ai.runtime.status;

import java.util.Map;

/**
 * StepRunner가 스텝 하나를 실행하고 나서 돌려주는 결과값입니다. result 값으로 성공/실패/대기 중
 * 무엇인지 구분하고, primaryText는 다음 스텝의 {previous} 토큰 자리에 그대로 이어지는 주된 결과
 * 텍스트입니다. data는 뒤에 오는 다른 스텝들이 {stepId.키} 형태로 가져다 쓸 수 있도록 Workflow
 * 전역 변수에 합쳐 넣을 구조화된 값입니다(runtime.workflow.WorkFlowExecutor가 이 스텝의 id를
 * 앞에 붙여서 구분해 주기 때문에, 병렬로 실행되는 형제 스텝끼리 같은 키를 써도 서로 덮어쓰지
 * 않습니다). 대부분의 스텝은 텍스트 하나만 돌려줘도 충분하므로, data는 필요 없으면 비워 둬도
 * 됩니다(Map.of()). 다만 {stepId.키} 토큰은 TOOL step의 inputTemplate처럼 단순 문자열 치환을
 * 쓰는 곳에서만 쓸 수 있습니다 - Agent의 prompt는 Spring AI의 PromptTemplate(StringTemplate
 * 기반)으로 렌더링되는데, 이 엔진은 변수 이름에 '.' 문자를 쓸 수 없기 때문에, prompt를 렌더링하기
 * 직전에 runtime.agent.AgentExecutor가 '.'이 섞인 키들을 걸러내 줍니다(promptSafeVariables() 참고).
 *
 * @param result        이 스텝이 성공했는지, 실패했는지, 아니면 아직 결정을 기다리는 중인지를 나타냅니다.
 * @param primaryText   다음 스텝의 {previous} 토큰 자리에 그대로 들어갈 주된 결과 텍스트입니다.
 * @param data          Workflow 전역 변수에 {stepId.키} 형태로 합쳐 넣을 구조화된 결과입니다(없으면 빈 Map).
 * @param failureReason 실패했을 때(FAILURE)만 값이 들어있고, 그 외에는 null입니다.
 * @param route         StepType이 ROUTER인 스텝 전용입니다. LLM이 고른 route 이름(runtime.status.RouteDecision.route())이 들어가고, ROUTER가 아니면 항상 null입니다.
 */
public record StepOutput(StepResult result, String primaryText, Map<String, Object> data, String failureReason, String route) {

	/** 성공했고 별도의 구조화 데이터는 없을 때 씁니다. @param text 다음 스텝으로 이어질 성공 결과 텍스트입니다. */
	public static StepOutput success(String text) {
		return new StepOutput(StepResult.SUCCESS, text, Map.of(), null, null);
	}

	/**
	 * 성공했고 다음 스텝들이 참조할 구조화 데이터도 함께 있을 때 씁니다.
	 *
	 * @param text 다음 스텝으로 이어질 성공 결과 텍스트입니다.
	 * @param data Workflow 전역 변수에 {stepId.키} 형태로 합쳐 넣을 구조화 결과입니다(예: StepDefinition.structuredOutput이 true인 AGENT step이 돌려주는 StepPayload.data()).
	 */
	public static StepOutput successWithData(String text, Map<String, Object> data) {
		return new StepOutput(StepResult.SUCCESS, text, data == null ? Map.of() : data, null, null);
	}

	/**
	 * 스텝이 실패했을 때 씁니다.
	 *
	 * @param text   다음 스텝(보통은 onFailure로 지정된, 문제를 고치는 스텝)으로 이어질 텍스트입니다.
	 * @param reason 왜 실패했는지에 대한 설명입니다.
	 */
	public static StepOutput failure(String text, String reason) {
		return new StepOutput(StepResult.FAILURE, text, Map.of(), reason, null);
	}

	/** 사람의 승인/반려 결정이 아직 나지 않아서 대기 중일 때 씁니다(ApprovalStepRunner 전용). */
	public static StepOutput pending() {
		return new StepOutput(StepResult.PENDING, null, Map.of(), null, null);
	}

	/**
	 * StepType이 ROUTER인 스텝 전용입니다. LLM이 고른 route 이름을 실어 나릅니다. 이 결과는 항상
	 * SUCCESS로 취급되고, 그 route를 실제로 어느 스텝으로 이어줄지는 runtime.workflow.WorkFlowExecutor가
	 * StepDefinition.routes를 보고 정합니다.
	 *
	 * @param text  다음 스텝으로 이어질 텍스트입니다(보통 이 스텝에 들어온 입력을 그대로 전달합니다).
	 * @param data  Workflow 전역 변수에 {stepId.키} 형태로 합쳐 넣을 구조화 결과입니다(없으면 빈 Map).
	 * @param route StepDefinition.routes에 정의된 키 중 하나입니다.
	 */
	public static StepOutput routed(String text, Map<String, Object> data, String route) {
		return new StepOutput(StepResult.SUCCESS, text, data == null ? Map.of() : data, null, route);
	}

}

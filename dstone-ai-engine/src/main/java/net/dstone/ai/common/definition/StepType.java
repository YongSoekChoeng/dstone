package net.dstone.ai.common.definition;

/**
 * StepDefinition 하나가 실제로 무엇을 실행하는지 정한다. runtime.workflow.WorkFlowExecutor가 이 값으로 어떤
 * StepRunner(runtime.step 패키지)에 위임할지 분기한다.
 */
public enum StepType {

	/** 
	 * common.registry.AgentRegistry에서 ref로 찾은 AgentDefinition으로 LLM을 1회 호출한다. 
	 * 항상 성공으로 취급된다. 
	 */
	AGENT,

	/** 
	 * common.config.ConfigTool에 등록된 Tool 하나를 LLM 없이 직접 호출한다(ref=Tool 이름).
	 * 결정적 검증에 쓴다. 
	 */
	TOOL,

	/**
	 * AGENT와 똑같이 Agent를 호출하지만, 응답을 자유 텍스트가 아니라 구조화된 Verdict(pass/reason)로 받아서 성공/실패를 가른다
	 * (runtime.agent.AgentExecutor.callForVerdict, runtime.status.Verdict 참고). 
	 * 여러 step의 결과를 감독/재검토하는 역할인데, TOOL처럼 응답이 결정론적 자바 코드에서 나오지 않고 
	 * LLM이 만든 텍스트라서 "실패로 시작하는지" 같은 문자열 접두사 판정은 신뢰할 수 없다.
	 * 대신 Spring AI가 Verdict의 JSON 스키마를 프롬프트에 자동으로 삽입하고 응답을 그 스키마에 맞춰 파싱하게 한다
	 * (그래도 100% 확정적이진 않다 - 스키마 자체를 어기면 파싱 예외가 나는데, 그 경우도 실패로 처리된다).
	 */
	SUPERVISOR,

	/**
	 * 사람의 승인/반려를 기다리는 스텝이다(ref는 쓰지 않음). 
	 * 첫 실행에서는 아직 결정이 없으니 Workflow 실행 자체를 WAITING_APPROVAL 상태로 멈추고, 
	 * 외부에서 결정 API가 호출되면 같은 스텝을 다시 실행해 이번엔 결정에 따라 성공/실패로 진행한다(runtime.step.ApprovalStepRunner 참고). 
	 * parallelGroup 에서는 는 함께 쓸 수 없다(common.registry.WorkFlowRegistry가 기동 시 검증).
	 */
	APPROVAL

}

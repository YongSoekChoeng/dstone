package net.dstone.ai.common.definition;

/**
 * StepDefinition 하나가 실제로 무엇을 실행하는지 정한다. runtime.WorkflowExecutor가 이 값으로
 * 어떤 StepRunner(runtime.step 패키지)에 위임할지 분기한다.
 */
public enum StepType {

	/** common.registry.AgentRegistry에서 ref로 찾은 AgentDefinition으로 LLM을 1회 호출한다. 항상 성공으로 취급된다. */
	AGENT,

	/** rag.RagService.search()로 검색만 하고, 찾은 청크 텍스트를 다음 step으로 넘긴다(ref는 쓰지 않음). LLM을 부르지 않는다. */
	RAG,

	/** common.config.ConfigTool에 등록된 Tool 하나를 LLM 없이 직접 호출한다(ref=Tool 이름) - 결정적 검증에 쓴다. */
	TOOL,

	/**
	 * AGENT와 똑같이 Agent를 호출하지만, 응답 텍스트가 "실패"로 시작하는지로 성공/실패를 가른다(TOOL과 같은 판정 컨벤션). 
	 * 여러 step의 결과를 감독/재검토하는 역할이라, Agent의 프롬프트 자체가 "통과: .../실패: 이유" 형식으로 답하도록 작성돼 있어야 한다(강제 장치는 없다 - 프롬프트 설계 컨벤션이다).
	 */
	SUPERVISOR

}

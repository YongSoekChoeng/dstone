package net.dstone.ai.process;

/**
 * ProcessStep 하나가 실제로 무엇을 실행하는지 정한다. ProcessExecutor.runStep()이 이 값으로 분기한다.
 */
public enum ProcessStepType {

	/** agent.AgentRegistry에서 ref로 찾은 AgentDefinition으로 ChatService.runAgentStep()을 1회 호출한다. 항상 성공으로 취급된다. */
	AGENT,

	/** RagService.search()로 검색만 하고, 찾은 청크 텍스트를 이어붙여 다음 step으로 넘긴다(ref는 쓰지 않음). */
	RAG,

	/** ConfigTool에 등록된 Tool 하나를 LLM 없이 직접 호출한다(ref=Tool 이름) - Oracle→PostgreSQL의 SQL 검증처럼 결정적 검사에 쓴다. */
	TOOL,

	/**
	 * AGENT처럼 agent.AgentRegistry에서 ref로 찾은 AgentDefinition을 호출하지만(Phase 7, Agent Runtime),
	 * 그 응답 텍스트가 "실패"로 시작하는지로 성공/실패를 가른다(TOOL과 동일한 판정 컨벤션) - 여러 AGENT
	 * step의 결과를 모아 판단한 뒤 통과/재작업을 가르는 "감독자" 역할에 쓴다. 별도 Supervisor 실행기를
	 * 만들지 않고 기존 onSuccess/onFailure 분기를 그대로 재사용한다.
	 */
	SUPERVISOR

}

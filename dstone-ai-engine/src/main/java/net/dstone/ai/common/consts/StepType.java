package net.dstone.ai.common.consts;

/**
 * <pre>
 * Workflow step의 다섯 가지 종류입니다. YAML step의 type 값이며, 이 값에 따라 step이
 * common.definition.workflow.step 패키지의 record 하나로 읽히고 runtime.step의 StepExecutor 하나가 실행합니다.
 * 각 종류가 무엇을 하는지, 어떤 YAML 키를 쓰는지는 그 record의 설명에 있습니다.
 *
 *   값          record(YAML 키)             실행하는 곳
 *   AGENT       AgentStepDefinition         AgentStepExecutor       LLM에게 일을 한 번 시킴
 *   SUPERVISOR  SupervisorStepDefinition    SupervisorStepExecutor  LLM이 통과/불통과를 판정함
 *   ROUTER      RouterStepDefinition        RouterStepExecutor      LLM이 routes 중 갈 곳 하나를 고름
 *   TOOL        ToolStepDefinition          ToolStepExecutor        Tool 하나를 LLM 없이 직접 호출함
 *   APPROVAL    ApprovalStepDefinition      ApprovalStepExecutor    사람이 승인/반려할 때까지 기다림
 *
 * 실행 이력(runtime.workflow.execution.StepHistoryEntry)과 로그에도 이 값이 남습니다.
 * </pre>
 */
public enum StepType {

	/** LLM에게 일을 한 번 시킵니다(AgentStepDefinition). */
	AGENT,

	/** LLM이 {pass, reason}으로 통과/불통과를 판정합니다(SupervisorStepDefinition). */
	SUPERVISOR,

	/** LLM이 routes 중 갈 곳 하나를 {route, reason}으로 고릅니다(RouterStepDefinition). */
	ROUTER,

	/** Tool 하나를 LLM 없이 이름으로 직접 호출합니다(ToolStepDefinition). */
	TOOL,

	/** 사람이 승인/반려할 때까지 기다립니다(ApprovalStepDefinition). */
	APPROVAL

}

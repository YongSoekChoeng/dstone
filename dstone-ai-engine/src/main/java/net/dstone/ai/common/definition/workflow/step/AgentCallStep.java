package net.dstone.ai.common.definition.workflow.step;

/**
 * <pre>
 * Agent(LLM)를 부르는 step들(AGENT/SUPERVISOR/ROUTER)의 공통 약속입니다.
 * StepType.Kind.AGENT_CALL과 같은 묶음이며, runtime.step.AgentStepRunner가 이 타입을 받아서 처리합니다.
 *
 * input은 문자열입니다. 채워진 문자열이 그대로 LLM에게 보내는 사용자 메시지가 됩니다.
 * 비워두면 {{previous.text}}(직전 step의 결과 텍스트)를 씁니다.
 * </pre>
 */
public sealed interface AgentCallStep extends StepDefinition permits AgentStep, SupervisorStep, RouterStep {

	/** 부를 Agent의 id입니다(common.registry.AgentRegistry에서 찾습니다). */
	@Override
	String ref();

	/** LLM에게 보낼 사용자 메시지의 템플릿입니다. 비워두면 {{previous.text}}입니다. */
	String input();

}

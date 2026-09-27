package net.dstone.ai.common.definition.workflow.step;

/**
 * <pre>
 * 성공/실패 두 갈래로 다음 step을 정하는 step들의 공통 약속입니다. ROUTER를 뺀 네 종류(AGENT/SUPERVISOR/TOOL/APPROVAL)가 해당합니다.
 * ROUTER는 성공하면 onSuccess 대신 routes에서 갈 곳을 고르므로 이 interface가 없습니다(RouterStep 참고).
 * </pre>
 */
public sealed interface PassFailStep extends StepDefinition permits AgentStep, SupervisorStep, ToolStep, ApprovalStep {

	/** 성공했을 때 다음으로 갈 step의 id(또는 "SUCCESS" 예약어)입니다. 비워두면 목록의 바로 다음 step으로 갑니다. */
	String onSuccess();

}

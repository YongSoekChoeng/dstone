package net.dstone.ai.runtime.step;

import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.runtime.status.StepInput;
import net.dstone.ai.runtime.status.StepOutput;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * AGENT/TOOL/SUPERVISOR/APPROVAL 4개 StepType 러너(AgentStepRunner/ToolStepRunner/ApprovalStepRunner)가 전부 구현하는
 * 공통 인터페이스다. WorkFlowExecutor는 StepType으로 이 인터페이스의 구현체를 찾아 항상 똑같은 방식(run 한 번 호출)으로
 * 스텝을 실행하고, 반환된 StepOutput.result만 보고 다음 동작을 정한다 - StepType별로 분기해서 다르게 호출하지 않는다.
 *
 * 이렇게 시그니처를 하나로 통일해 둔 덕분에 common.config.ConfigCallLog가 이 메서드 하나만 겨냥하는 AOP advice로
 * 모든 스텝의 IN/OUT을 한 곳에서 균일하게 로깅할 수 있다.
 *
 * @param execution  실행 중인 Workflow 실행 상태(executionId/workflowId 등 - 로깅/영속화에 쓰인다)
 * @param definition 실행할 스텝 정의(type/ref/inputTemplate 등)
 * @param input      렌더링된 입력 텍스트와 전역 변수
 */
public interface StepRunner {

	StepOutput run(WorkFlowExecution execution, StepDefinition definition, StepInput input);

}

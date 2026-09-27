package net.dstone.ai.runtime.step;

import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * AGENT/SUPERVISOR/ROUTER/TOOL/APPROVAL, 이 다섯 가지 step을 실제로 처리하는 러너들(AgentStepRunner,
 * ToolStepRunner, ApprovalStepRunner)이 모두 구현하는 공통 인터페이스입니다. 러너마다 자기가 맡는 step
 * record 타입을 T로 정해서 받습니다(AgentStepRunner는 AgentCallStep, ToolStepRunner는 ToolStep,
 * ApprovalStepRunner는 ApprovalStep). 그래서 러너 안에서 step 종류를 다시 확인하거나 형변환할 필요가 없습니다.
 *
 * WorkFlowExecutor는 step의 record 타입을 보고 러너를 하나 골라서, 항상 똑같은 방식으로(run 메서드를 한 번
 * 호출해서) 스텝을 실행합니다. 그리고 그 결과로 돌아온 StepOutcome이 성공/실패/대기 중 어느 경우인지만
 * 보고 다음에 뭘 할지 정합니다.
 *
 * 이렇게 메서드 시그니처를 하나로 통일해 둔 덕분에, common.config.ConfigCallLog가 이 run 메서드
 * 하나만 겨냥하는 AOP advice 하나로도 모든 스텝의 입력/출력을 한곳에서 똑같은 형식으로 로깅할 수
 * 있습니다.
 *
 * @param execution  지금 실행 중인 Workflow의 실행 상태입니다(executionId, workflowId 등을 담고 있고, 로깅이나 상태 저장에 쓰입니다).
 * @param definition 실행할 스텝의 정의입니다(러너가 맡는 step record입니다).
 * @param <T>        이 러너가 맡는 step record 타입입니다.
 * @param input      이 스텝에 실제로 넘어갈, 템플릿이 이미 채워진 입력입니다(StepInput 참고).
 */
public interface StepRunner<T extends StepDefinition> {

	StepOutcome run(WorkFlowExecution execution, T definition, StepInput input);

}

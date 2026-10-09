package net.dstone.ai.runtime.step;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.workflow.step.ApprovalStepDefinition;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * <pre>
 * type: APPROVAL step(ApprovalStepDefinition)을 실행합니다. 사람이 결정할 때까지 기다리는 역할을 합니다. 다른
 * 실행기들과 마찬가지로 run() 메서드가 한 번 호출되는 것으로 끝이고, "지금 결정이 이미 나 있는가"만 확인합니다.
 *
 * - 아직 결정이 없으면 대기(StepOutcome.waitingApproval())를 돌려줍니다. WorkFlowExecutor는 이 값을 보고 Workflow 실행 전체를
 *   WAITING_APPROVAL 상태로 멈춰 둡니다.
 * - 나중에 api.controller.WorkFlowExecutionController의 decision API가 호출되면, 그 결정이 컨텍스트의
 *   approvals.{stepId}에 기록된 뒤 같은 스텝이 다시 한번 실행됩니다. 이번에는 결정이 있으니 그 결정대로 진행합니다.
 *
 * 결과(result)는 {decision, approver, comment}이고 항상 성공입니다. 고른 decision에 적힌 step으로 갑니다(step의 routes).
 *
 * 결정은 한 번 쓰고 지웁니다(WorkFlowContext.clearApproval()). 그래서 흐름이 되돌아와 이 step에 다시 오면
 * 사람에게 다시 묻습니다. 직전 결정은 step의 output으로 저장한 state에 남아 있어, 다음 step이 "${state.이름.comment:}"처럼 꺼내 씁니다.
 * </pre>
 */
@Component
public class ApprovalStepExecutor {

	/**
	 * 컨텍스트의 approvals.{stepId}에 사람의 결정이 들어 있는지 봅니다.
	 * 없으면 대기, 있으면 고른 결정을 route로 돌려줍니다.
	 *
	 * @param execution 지금 진행 중인 Workflow 실행 상태입니다.
	 * @param step      실행할 step의 정의입니다.
	 */
	public StepOutcome run(WorkFlowExecution execution, ApprovalStepDefinition step) {
		Map<String, Object> decision = WorkFlowContext.approval(execution.context(), step.id());
		if (decision == null) {
			return StepOutcome.waitingApproval();
		}
		// 이 결정은 지금 한 번만 씁니다. 지워 두어야 이 step에 다시 왔을 때 사람에게 다시 묻습니다.
		WorkFlowContext.clearApproval(execution.context(), step.id());

		Object approver = decision.get("approver");
		Object comment = decision.get("comment");
		// 사람이 고른 이름을 그대로 route로 넘깁니다. 어느 step으로 갈지는 WorkFlowExecutor가 routes에서 찾습니다.
		String chosen = decision.get("decision") == null ? null : decision.get("decision").toString();
		Map<String, Object> output = new LinkedHashMap<>();
		output.put("decision", chosen);
		output.put("approver", approver == null ? "" : approver);
		output.put("comment", comment == null ? "" : comment);
		return StepOutcome.routed(output, chosen);
	}

}

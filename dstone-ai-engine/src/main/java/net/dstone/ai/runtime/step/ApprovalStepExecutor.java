package net.dstone.ai.runtime.step;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.workflow.step.ApprovalStepDefinition;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * type: APPROVAL step(ApprovalStepDefinition)을 실행합니다. 사람이 승인하거나 반려할 때까지 기다리는 역할을 합니다. 다른
 * 실행기들과 마찬가지로 run() 메서드가 한 번 호출되는 것으로 끝이고, "지금 결정이 이미 나 있는가"만 확인합니다.
 *
 * - 아직 결정이 없으면 대기(StepOutcome.waitingApproval())를 돌려줍니다. WorkFlowExecutor는 이 값을 보고 Workflow 실행 전체를
 *   WAITING_APPROVAL 상태로 멈춰 둡니다.
 * - 나중에 api.controller.WorkFlowExecutionController의 decision API가 호출되면, 그 결정이 컨텍스트의
 *   approvals.{stepId}에 기록된 뒤 같은 스텝이 다시 한번 실행됩니다. 이번에는 결정이 있으니 그 결정을
 *   output에 {approved, approver, comment}로 남기고, 승인이면 성공을, 반려면 실패(error에 반려 사유)를 돌려줍니다.
 *   다음 step은 {{steps.id.output.comment}}처럼 꺼내 씁니다.
 * </pre>
 */
@Component
public class ApprovalStepExecutor {

	/**
	 * 컨텍스트의 approvals.{stepId}에 사람의 결정이 들어 있는지 봅니다.
	 * 없으면 대기, 승인이면 성공, 반려면 실패를 돌려줍니다.
	 *
	 * @param execution 지금 진행 중인 Workflow 실행 상태입니다.
	 * @param step      실행할 step의 정의입니다.
	 */
	public StepOutcome run(WorkFlowExecution execution, ApprovalStepDefinition step) {
		Map<String, Object> decision = WorkFlowContext.approval(execution.context(), step.id());
		if (decision == null) {
			return StepOutcome.waitingApproval();
		}

		boolean approved = Boolean.TRUE.equals(decision.get("approved"));
		Object approver = decision.get("approver");
		Object comment = decision.get("comment");

		Map<String, Object> output = new LinkedHashMap<>();
		output.put("approved", approved);
		output.put("approver", approver == null ? "" : approver);
		output.put("comment", comment == null ? "" : comment);
		if (approved) {
			return StepOutcome.success(output);
		}
		String reason = comment == null || StringUtil.isEmpty(comment.toString()) ? "(사유 없음)" : comment.toString();
		return StepOutcome.failure(output, reason);
	}

}

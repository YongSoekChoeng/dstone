package net.dstone.ai.runtime.step;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.utils.StringUtil;

/**
 * APPROVAL step을 처리하는 러너입니다. 사람이 승인하거나 반려할 때까지 기다리는 역할을 합니다. 다른
 * 러너들과 마찬가지로 run() 메서드가 한 번 호출되는 것으로 끝이고, "지금 결정이 이미 나 있는가"만 확인합니다.
 *
 * - 아직 결정이 없으면 PENDING을 돌려줍니다. WorkFlowExecutor는 이 값을 보고 Workflow 실행 전체를
 *   WAITING_APPROVAL 상태로 멈춰 둡니다.
 * - 나중에 api.controller.WorkFlowExecutionController의 decision API가 호출되면, 그 결정이 컨텍스트의
 *   approvals.{stepId}에 기록된 뒤 같은 스텝이 다시 한번 실행됩니다. 이번에는 결정이 있으니
 *   승인이면 성공을, 반려면 실패를 돌려줍니다.
 *
 * APPROVAL은 사람이 결정만 하는 관문이라서, 받은 input(직전 step의 결과 텍스트)을 결과 텍스트로 그대로 넘깁니다.
 * 결정 내용은 결과 텍스트에 덧붙이지 않고 따로 남깁니다.
 * - 승인: data에 {approved, approver, comment}를 남깁니다. 다음 step은 {{steps.id.data.comment}}처럼 꺼내 씁니다.
 * - 반려: 실패로 처리하고, 반려 사유(comment)를 error에 남깁니다.
 */
@Component
public class ApprovalStepRunner implements StepRunner {

	/**
	 * 컨텍스트의 approvals.{stepId}에 사람의 결정이 들어 있는지 봅니다.
	 * 없으면 대기(Pending), 승인이면 성공, 반려면 실패를 돌려줍니다.
	 */
	@Override
	public StepOutcome run(WorkFlowExecution execution, StepDefinition definition, StepInput input) {
		Map<String, Object> decision = WorkFlowContext.approval(execution.context(), definition.id());
		if (decision == null) {
			return StepOutcome.pending();
		}

		boolean approved = Boolean.TRUE.equals(decision.get("approved"));
		Object approver = decision.get("approver");
		Object comment = decision.get("comment");

		if (approved) {
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("approved", true);
			data.put("approver", approver == null ? "" : approver);
			data.put("comment", comment == null ? "" : comment);
			return StepOutcome.success(input.text(), data);
		}
		String reason = comment == null || StringUtil.isEmpty(comment.toString()) ? "(사유 없음)" : comment.toString();
		return StepOutcome.failure(input.text(), reason);
	}

}

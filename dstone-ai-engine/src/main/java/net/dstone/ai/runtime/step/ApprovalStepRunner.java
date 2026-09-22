package net.dstone.ai.runtime.step;

import java.util.Map;

import org.springframework.stereotype.Component;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.runtime.status.StepInput;
import net.dstone.ai.runtime.status.StepOutput;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * APPROVAL step을 처리하는 러너입니다. 사람이 승인하거나 반려할 때까지 기다리는 역할을 합니다. 다른
 * 러너들과 마찬가지로 이 클래스도 run() 메서드가 한 번 호출되는 것으로 끝이고, 특별한 코드 경로 없이
 * 그냥 "지금 결정이 이미 나 있는가"만 확인합니다.
 *
 * - 아직 아무 결정도 없으면 PENDING을 돌려줍니다. WorkFlowExecutor는 이 값을 보고 Workflow 실행 전체를
 *   WAITING_APPROVAL 상태로 멈춰 둡니다.
 * - 나중에 api.controller.WorkFlowExecutionController의 decision API가 호출되면, 그 결정 내용을
 *   variables.approvals.{stepId} 자리에 기록해 둔 뒤 같은 스텝을 다시 한번 실행시킵니다. 이번에는
 *   이미 결정이 있으니, 승인이면 SUCCESS를, 반려면 FAILURE를 돌려줍니다.
 *
 * 즉 이 클래스 입장에서는 "멈췄던 걸 다시 이어간다"는 개념이 따로 있는 게 아니라, 그냥 같은 스텝을
 * 한 번 더 실행하는 것뿐입니다.
 */
@Component
public class ApprovalStepRunner implements StepRunner {

	@SuppressWarnings("unchecked")
	@Override
	public StepOutput run(WorkFlowExecution execution, StepDefinition definition, StepInput input) {
		Map<String, Object> approvals = (Map<String, Object>) execution.variables().get(Constants.WorkFlow.APPROVALS_VARIABLE_KEY);
		Object decisionValue = approvals == null ? null : approvals.get(definition.id());
		if (!(decisionValue instanceof Map)) {
			return StepOutput.pending();
		}

		Map<String, Object> decision = (Map<String, Object>) decisionValue;
		boolean approved = Boolean.TRUE.equals(decision.get("approved"));
		String approver = String.valueOf(decision.get("approver"));
		String comment = String.valueOf(decision.get("comment"));

		if (approved) {
			return StepOutput.success(input.renderedText() + "\n\n[승인] " + approver + (comment == null || comment.isBlank() || "null".equals(comment) ? "" : " - " + comment));
		}else {
			String reason = comment == null || comment.isBlank() || "null".equals(comment) ? "(사유 없음)" : comment;
			return StepOutput.failure(input.renderedText() + "\n\n[반려] " + approver + " - " + reason, reason);
		}
	}

}

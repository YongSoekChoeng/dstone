package net.dstone.ai.runtime.step;

import java.util.Map;

import org.springframework.stereotype.Component;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.runtime.status.StepInput;
import net.dstone.ai.runtime.status.StepOutput;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * APPROVAL step - 사람의 승인/반려 결정을 기다린다. 다른 러너처럼 항상 똑같이 run() 한 번만 호출되고, 특별한 코드 경로 없이
 * "지금 결정이 있는가"만 확인한다:
 *
 * - 아직 결정이 없으면 PENDING을 반환한다 - WorkFlowExecutor가 이걸 보고 실행 전체를 WAITING_APPROVAL로 멈춘다.
 * - api.controller.WorkFlowExecutionController의 decision API가 호출되면 그 결정을
 *   variables.approvals.{stepId}에 기록한 뒤 같은 스텝을 다시 실행하는데, 이번엔 결정이 있으니
 *   승인이면 SUCCESS, 반려면 FAILURE를 반환한다.
 *
 * 즉 "재개"는 이 클래스 입장에서 특별한 게 아니라 그냥 같은 스텝의 재실행일 뿐이다.
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
		}
		String reason = comment == null || comment.isBlank() || "null".equals(comment) ? "(사유 없음)" : comment;
		return StepOutput.failure(input.renderedText() + "\n\n[반려] " + approver + " - " + reason, reason);
	}

}

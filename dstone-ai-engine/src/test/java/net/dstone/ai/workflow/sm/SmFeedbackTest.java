package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.step.AgentStepDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.common.expression.ContextResolver;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow에서 feedback을 받는 step이 뒤쪽 승인의 결정과 의견을 모두 받는지 확인합니다(LLM 없음).
 * 2026-11-19 실행 4c163653: step15(개발 승인)에서 [재설계]를 고르고 "UPLOADS import가 틀렸다"고 적었는데,
 * step09는 step10의 의견만 받게 되어 있어서 지난번 설계 승인 때 적은 "개발진행해."만 받고 고칠 게 없다고 끝냈습니다.
 * </pre>
 */
public class SmFeedbackTest extends WorkflowTestBase {

	@Test
	void 설계_step은_설계_승인과_개발_승인을_결정과_함께_받는다() {
		Map<String, Object> context = WorkFlowContext.create("D:/work", "sm-workflow", "test");
		Map<String, Object> state = WorkFlowContext.state(context);
		state.put("designApproval", approval("승인", "개발진행해."));
		state.put("developApproval", approval("재설계", "존재하지 않는 UPLOADS를 import한다"));

		String feedback = this.feedbackOf("step09", context);

		assertThat(feedback).contains("- 설계 승인(step10): 승인 / 개발진행해.")
			.contains("- 개발 승인(step15): 재설계 / 존재하지 않는 UPLOADS를 import한다");
	}

	@Test
	void 영향도_step은_설계_승인의_되돌리기도_받는다() {
		Map<String, Object> context = WorkFlowContext.create("D:/work", "sm-workflow", "test");
		WorkFlowContext.state(context).put("designApproval", approval("영향도 재분석", "참고 파일이 틀렸다"));

		String feedback = this.feedbackOf("step06", context);

		assertThat(feedback).contains("- 설계 승인(step10): 영향도 재분석 / 참고 파일이 틀렸다")
			.contains("- 영향도 분석 승인(step07):  / ")
			.contains("- 개발 승인(step15):  /");
	}

	private String feedbackOf(String stepId, Map<String, Object> context) {
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve("sm-workflow", null);
		for (StepDefinition step : workflow.steps()) {
			if (step.id().equals(stepId)) {
				Object resolved = ContextResolver.resolve(((AgentStepDefinition) step).input(), context, null);
				return String.valueOf(((Map<?, ?>) resolved).get("feedback"));
			}
		}
		throw new AssertionError("step이 없습니다: " + stepId);
	}

	private static Map<String, Object> approval(String decision, String comment) {
		Map<String, Object> value = new LinkedHashMap<String, Object>();
		value.put("decision", decision);
		value.put("approver", "local");
		value.put("comment", comment);
		return value;
	}

}

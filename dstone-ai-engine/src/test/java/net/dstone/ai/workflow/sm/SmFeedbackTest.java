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
 * 2026-11-19 실행 4c163653: 개발 승인에서 [재설계]를 고르고 "UPLOADS import가 틀렸다"고 적었는데,
 * 아키텍처 설계 step은 설계 승인의 의견만 받게 되어 있어서 지난번 설계 승인 때 적은 "개발진행해."만 받고 고칠 게 없다고 끝냈습니다.
 * </pre>
 */
public class SmFeedbackTest extends WorkflowTestBase {

	@Test
	void 설계_step은_설계_승인과_개발_승인을_결정과_함께_받는다() {
		Map<String, Object> context = WorkFlowContext.create("D:/work", "sm-workflow", "test");
		Map<String, Object> state = WorkFlowContext.state(context);
		state.put("designApproval", approval("승인", "개발진행해."));
		state.put("developApproval", approval("재설계", "존재하지 않는 UPLOADS를 import한다"));

		String feedback = this.feedbackOf("step10", context);

		assertThat(feedback).contains("- 설계 승인(step11): 승인 / 개발진행해.")
			.contains("- 개발 승인(step17): 재설계 / 존재하지 않는 UPLOADS를 import한다")
			.contains("- 컴파일 확인(step16):");
	}

	@Test
	void 영향도_step은_설계_승인의_되돌리기도_받는다() {
		Map<String, Object> context = WorkFlowContext.create("D:/work", "sm-workflow", "test");
		WorkFlowContext.state(context).put("designApproval", approval("영향도 재분석", "참고 파일이 틀렸다"));

		String feedback = this.feedbackOf("step07", context);

		assertThat(feedback).contains("- 설계 승인(step11): 영향도 재분석 / 참고 파일이 틀렸다")
			.contains("- 영향도 분석 승인(step08):  / ")
			.contains("- 개발 승인(step17):  /");
	}

	@Test
	void 개발_step은_컴파일_오류와_개발_승인을_받는다() {
		Map<String, Object> context = WorkFlowContext.create("D:/work", "sm-workflow", "test");
		WorkFlowContext.state(context).put("compileError", "실패: 종료 코드 1\n[ERROR] src/main/java/A.java:[3,29] cannot find symbol");

		String feedback = this.feedbackOf("step15", context);

		assertThat(feedback).contains("- 컴파일 확인(step16): 실패: 종료 코드 1").contains("A.java:[3,29] cannot find symbol")
			.contains("- 개발 승인(step17):  /");
	}

	/**
	 * <pre>
	 * 승인하고 앞으로 진행할 때 승인자가 남긴 의견이 다음 Agent step에 표시와 함께 가는지 봅니다(2026-11-20).
	 * 리뷰 불통과 처리(step06) → 영향도(step07), 영향도 승인(step08) → 설계(step10), 설계 승인(step11) → 개발(step15).
	 * </pre>
	 */
	@Test
	void 앞으로_진행시킨_승인의_의견은_다음_Agent_step이_표시와_함께_받는다() {
		Map<String, Object> context = WorkFlowContext.create("D:/work", "sm-workflow", "test");
		Map<String, Object> state = WorkFlowContext.state(context);
		state.put("reviewDecision", approval("그대로 진행", "TASK-03은 범위에서 뺀다"));
		state.put("impactApproval", approval("승인(설계진행)", "공통 코드 테이블도 같이 봐 주세요"));
		state.put("designApproval", approval("승인(개발진행)", "로그는 기존 Logger를 쓴다"));

		assertThat(this.feedbackOf("step07", context)).contains("- [이 단계로 보냄] 리뷰 불통과 처리 결정(step06): 그대로 진행 / TASK-03은 범위에서 뺀다");
		assertThat(this.feedbackOf("step10", context)).contains("- [이 단계로 보냄] 영향도 분석 승인(step08): 승인(설계진행) / 공통 코드 테이블도 같이 봐 주세요");
		assertThat(this.feedbackOf("step15", context)).contains("- [이 단계로 보냄] 설계 승인(step11): 승인(개발진행) / 로그는 기존 Logger를 쓴다");
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

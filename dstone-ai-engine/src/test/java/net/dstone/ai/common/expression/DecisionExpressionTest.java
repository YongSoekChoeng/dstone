package net.dstone.ai.common.expression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;

/**
 * <pre>
 * 승인·판정 결정 기록을 표현식으로 읽는지 확인합니다(서버 없음).
 *   ${arrival}    이 step으로 오게 만든 마지막 결정을 글로
 *   ${decisions}  지금까지의 결정 전체를 글로
 *   ${arrival.comment} 같은 필드는 값 그대로
 * </pre>
 */
class DecisionExpressionTest {

	@Test
	void 결정이_없으면_null이라_기본값을_쓸_수_있다() {
		Map<String, Object> context = WorkFlowContext.create("D:/work", "wf", "1.0.0");

		assertThat(ContextResolver.resolve("${arrival}", context, null)).isNull();
		assertThat(ContextResolver.resolve("${decisions:없음}", context, null)).isEqualTo("없음");
	}

	@Test
	void arrival은_마지막_결정을_사람이_읽는_글로_준다() {
		Map<String, Object> context = WorkFlowContext.create("D:/work", "wf", "1.0.0");
		WorkFlowContext.recordDecision(context, "step05", "요구사항 리뷰", "SUPERVISOR", "불통과", null, "TASK-02 완료 기준이 모호함", "step06");
		WorkFlowContext.recordDecision(context, "step06", "리뷰 불통과 처리 결정", "APPROVAL", "그대로 진행", "local", "TASK-03은 범위에서 뺀다", "step07");

		String arrival = String.valueOf(ContextResolver.resolve("${arrival}", context, null));
		assertThat(arrival).isEqualTo("리뷰 불통과 처리 결정(step06)의 결정: 그대로 진행 (승인자 local)\n의견:\nTASK-03은 범위에서 뺀다");
		assertThat(ContextResolver.resolve("${arrival.comment}", context, null)).isEqualTo("TASK-03은 범위에서 뺀다");

		String decisions = String.valueOf(ContextResolver.resolve("${decisions}", context, null));
		assertThat(decisions).startsWith("[1] 요구사항 리뷰(step05)의 판정: 불통과\n사유:\nTASK-02 완료 기준이 모호함")
			.contains("\n\n[2] 리뷰 불통과 처리 결정(step06)의 결정: 그대로 진행");
		assertThat(ContextResolver.resolve("${decisions[0].decision}", context, null)).isEqualTo("불통과");
	}

	@Test
	void arrival을_비우면_다음_결정까지_null이고_기록은_남는다() {
		Map<String, Object> context = WorkFlowContext.create("D:/work", "wf", "1.0.0");
		WorkFlowContext.recordDecision(context, "step08", "영향도 승인", "APPROVAL", "승인", "local", null, "step09");
		WorkFlowContext.clearArrival(context);

		assertThat(ContextResolver.resolve("${arrival:}", context, null)).isEqualTo("");
		assertThat(String.valueOf(ContextResolver.resolve("${decisions}", context, null))).isEqualTo("[1] 영향도 승인(step08)의 결정: 승인 (승인자 local)");
	}

	@Test
	void 검사는_arrival과_decisions를_읽을_수_있는_이름으로_본다() {
		for (ContextResolver.Reference reference : ContextResolver.references("${arrival.comment} ${decisions} ${nope}")) {
			String problem = ContextResolver.check(reference, java.util.List.of());
			if (reference.root().equals("nope")) {
				assertThat(problem).contains("arrival").contains("decisions");
			} else {
				assertThat(problem).isNull();
			}
		}
	}

}

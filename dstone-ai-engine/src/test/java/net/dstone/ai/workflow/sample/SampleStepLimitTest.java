package net.dstone.ai.workflow.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sample-step-limit: 판정 전용 step 없이 흐름을 적는 엔진 기능을 LLM 없이 확인합니다.
 *
 *   route(routeValue) → stamp(limit 2번, 자기 자신으로) → 한도를 넘으면 choose → 다시: stamp / 완료: END
 *
 * - limit: 2번까지 실행하고 세 번째는 건너뛰어 over로 간다. 건너뛴 것도 이력에 한 줄(성공) 남는다.
 * - limit.scope가 결정 기록이면 사람이 새로 결정한 뒤 다시 2번을 센다.
 * - settings.startTime: 첫 step 전에 시작 시각이 state에 들어간다.
 * - routeValue: 값 그대로 routes를 고르고, routes에 없는 값이면 onFailure로 간다.
 * </pre>
 */
public class SampleStepLimitTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-step-limit";

	@Test
	@SuppressWarnings("unchecked")
	void 한도까지만_실행하고_넘으면_건너뛰며_새_결정이면_다시_센다() {
		WorkFlowExecution waiting = this.run(WORKFLOW_ID, "again");
		assertThat(waiting.status()).isEqualTo(WorkFlowExecutionStatus.WAITING_APPROVAL);
		// stamp는 2번 실행되고, 세 번째는 한도로 건너뛴 줄이 남는다
		assertThat(this.stepIds(waiting)).containsExactly("route", "stamp", "stamp", "stamp");
		List<StepHistoryEntry> history = this.history(waiting);
		StepHistoryEntry skipped = history.get(3);
		assertThat(skipped.success()).isTrue();
		assertThat(skipped.durationMs()).isZero();
		assertThat(skipped.outputSummary()).contains("횟수 한도").contains("choose");
		assertThat(String.valueOf(this.state(waiting).get("startedAt"))).matches("\\d{8}-\\d{4}");

		WorkFlowExecution again = this.decide(waiting, "다시", null);
		assertThat(again.status()).isEqualTo(WorkFlowExecutionStatus.WAITING_APPROVAL);
		assertThat(this.stepIds(again)).containsExactly("route", "stamp", "stamp", "stamp", "choose", "stamp", "stamp", "stamp");
		// 한도로 건너뛰어 choose로 갔다는 것이 다음 줄의 온 길(via=limit)로 남는다
		assertThat(this.history(again).get(4).arrivedFrom()).isEqualTo("stamp");
		assertThat(this.history(again).get(4).arrivedVia()).isEqualTo("limit");

		WorkFlowExecution done = this.decide(again, "완료", null);
		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat((Map<String, Object>) done.output()).containsKey("startedAt");
	}

	@Test
	void routeValue는_값_그대로_갈_곳을_고르고_없는_값이면_실패로_간다() {
		WorkFlowExecution done = this.run(WORKFLOW_ID, "done");
		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.stepIds(done)).containsExactly("route");

		WorkFlowExecution failed = this.run(WORKFLOW_ID, "unknown");
		assertThat(failed.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
	}

}

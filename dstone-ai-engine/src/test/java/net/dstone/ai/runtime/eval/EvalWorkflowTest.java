package net.dstone.ai.runtime.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * Workflow 사례(workflow:)를 확인합니다. LLM을 부르지 않는 샘플 Workflow(sample-approval-routes-loop: stamp → choose 승인)로
 * 승인 규칙대로 고르기, 규칙 없는 승인에서 멈추고 실행을 CANCELLED로 닫기, snapshotAfter로 떠 두고 따로 채점하기,
 * answer* 채점이 state를 보는지, 시작할 때 틀린 승인 결정·모델 지정을 거절하는지 봅니다.
 * </pre>
 */
public class EvalWorkflowTest extends WorkflowTestBase {

	private static final String LOCATION = "dstone.ai.eval.location";

	@Autowired
	private EvalRunner runner;

	@Autowired
	private EvalRunStore store;

	@Autowired
	private WorkFlowExecutionStore executionStore;

	@TempDir
	Path dir;

	@BeforeEach
	void writeCases() throws Exception {
		Path root = this.dir.resolve("eval");
		write(root.resolve("flow/stop-at-choose.eval.yml"), String.join("\n",
			"id: stop-at-choose",
			"workflow: sample-approval-routes-loop",
			"input: 시험",
			"snapshotAfter: stamp",
			"checks:",
			"  - name: 시각 남김",
			"    answerNotEmpty: stampedAt",
			"  - name: 고르기 전",
			"    answerNotEmpty: choice",
			""));
		write(root.resolve("flow/choose-done.eval.yml"), String.join("\n",
			"id: choose-done",
			"workflow: sample-approval-routes-loop",
			"input: 시험",
			"approvals:",
			"  choose: 완료",
			"checks:",
			"  - name: 완료를 골랐다",
			"    answerContains: {field: choice, text: 완료}",
			""));
		write(root.resolve("flow/wrong-decision.eval.yml"), String.join("\n",
			"id: wrong-decision",
			"workflow: sample-approval-routes-loop",
			"input: 시험",
			"approvals:",
			"  choose: 없는결정",
			"checks:",
			"  - answerNotEmpty: choice",
			""));
		System.setProperty(LOCATION, root.toString());
	}

	@AfterEach
	void clearLocation() {
		System.clearProperty(LOCATION);
	}

	private static void write(Path file, String text) throws Exception {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	@Test
	@SuppressWarnings("unchecked")
	void 규칙이_없는_승인에서_멈추고_채점한_뒤_실행을_닫는다() throws Exception {
		Map<String, Object> run = this.startAndWait("stop-at-choose");

		assertThat(run.get("status")).isEqualTo("DONE");
		assertThat(run.get("agentId")).isEqualTo("sample-approval-routes-loop");
		// 멈춘 자리에서는 시각만 있고 결정은 아직 없다
		assertThat(run.get("passed")).isEqualTo(1);
		assertThat(run.get("total")).isEqualTo(2);
		Map<String, Object> flow = (Map<String, Object>) ((Map<String, Object>) run.get("answer")).get("workflowRun");
		assertThat(flow.get("end")).isEqualTo("STOPPED");
		assertThat(flow.get("stoppedAt")).isEqualTo("choose");
		// stamp 직후에 떠 둔 것도 같은 기준으로 따로 채점했다
		Map<String, Object> snapshot = (Map<String, Object>) flow.get("snapshot");
		assertThat(snapshot.get("after")).isEqualTo("stamp");
		assertThat(snapshot.get("passed")).isEqualTo(1);
		assertThat(Files.isDirectory(Path.of(String.valueOf(snapshot.get("dir"))))).isTrue();
		// 평가가 돌린 실행은 실행 저장소에 남고, 채점 뒤 CANCELLED로 닫힌다
		String executionId = String.valueOf(flow.get("executionId"));
		assertThat(this.executionStore.find(executionId).status()).isEqualTo(WorkFlowExecutionStatus.CANCELLED);
	}

	@Test
	@SuppressWarnings("unchecked")
	void 승인_규칙대로_골라_끝까지_돌린다() throws Exception {
		Map<String, Object> run = this.startAndWait("choose-done");

		assertThat(run.get("status")).isEqualTo("DONE");
		assertThat(run.get("passed")).isEqualTo(1);
		Map<String, Object> flow = (Map<String, Object>) ((Map<String, Object>) run.get("answer")).get("workflowRun");
		assertThat(flow.get("end")).isEqualTo("DONE");
		assertThat(String.valueOf(flow.get("decisions"))).contains("choose").contains("완료");
		assertThat(this.executionStore.find(String.valueOf(flow.get("executionId"))).status()).isEqualTo(WorkFlowExecutionStatus.DONE);
	}

	@Test
	void 틀린_승인_결정과_모델_지정은_시작할_때_거절한다() {
		String wrongDecision = null;
		try {
			this.runner.start("wrong-decision", null, null, 1, "tester");
		} catch (IllegalArgumentException e) {
			wrongDecision = e.getMessage();
		}
		String model = null;
		try {
			this.runner.start("choose-done", null, "some/model", 1, "tester");
		} catch (IllegalArgumentException e) {
			model = e.getMessage();
		}
		assertThat(wrongDecision).contains("없는결정").contains("완료");
		assertThat(model).contains("모델");
	}

	private Map<String, Object> startAndWait(String caseId) throws InterruptedException {
		Map<String, Object> started = this.runner.start(caseId, null, null, 1, "tester");
		String runId = ((List<?>) started.get("runIds")).get(0).toString();
		for (int i = 0; i < 600; i++) {
			Map<String, Object> run = this.store.find(runId);
			if (run != null && ("DONE".equals(run.get("status")) || "FAILED".equals(run.get("status")))) {
				return run;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("평가가 60초 안에 끝나지 않았습니다: " + runId);
	}

}

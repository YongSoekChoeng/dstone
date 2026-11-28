package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow의 자동 동작 검증 고리를 LLM 없이 확인합니다(2.4.0).
 * - 검증할 수 없으면 통과로 치지 않고 이유와 함께 사람에게 간다.
 * - 개발 결과 탓인 실패만 자동으로 고치고, 그것도 최대 2번이다.
 * - 사람이 새로 시키면 자동 수정 횟수가 다시 생긴다.
 * </pre>
 */
public class SmVerifyLoopTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	@TempDir
	Path workDir;

	@Test
	void 빌드된_리비전이_없으면_검증하지_않고_사람에게_간다() throws Exception {
		WorkflowStepper flow = this.atStep("step28");
		flow.put("projectRevision", Map.of("route", "none", "message", "빌드된 리비전이 없습니다."));
		flow.step("step28");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.currentStepId()).isEqualTo("step19");
		assertThat(this.lastHistory(flow, "step28").success()).isTrue();
	}

	@Test
	void 리비전이_있고_DDL이_없으면_설계서를_다시_읽으러_간다() throws Exception {
		WorkflowStepper flow = this.atStep("step28");
		flow.put("projectId", "myApp").put("projectRevision", Map.of("route", "ok", "revision", "r1"));
		flow.put("designApproval", Map.of("decision", "승인(개발진행, 서버 검증)"));
		flow.step("step28");
		assertThat(flow.currentStepId()).isEqualTo("step60");
		flow.step("step60");
		assertThat(String.valueOf(flow.state("ddlCheck"))).contains("route=none");
		flow.step("step43");
		flow.step("step64");

		assertThat(flow.currentStepId()).isEqualTo("step41");
	}

	/**
	 * <pre>
	 * 설계 승인에서 고른 검증 수준을 첫 검증에서 따르는지 봅니다(2.7.0).
	 * 기본 검증이면 서버를 띄우지 않고 사람에게 갑니다.
	 * 이 판정은 정상 결과라 이력에 실패로 남지 않습니다. 첫 개발은 step43을 거쳐 step64가 고르고,
	 * 첫 검증인지는 step43의 limit(max 1, scope 결정 기록)이 셉니다.
	 * </pre>
	 */
	@Test
	void 컴파일만으로_승인하면_첫_검증은_서버_없이_사람에게_간다() {
		WorkflowStepper flow = this.atStep("step43");
		flow.put("designApproval", Map.of("decision", "승인(개발진행, 기본 검증)"));
		flow.step("step43");
		flow.step("step64");

		assertThat(flow.currentStepId()).isEqualTo("step19");
		assertThat(this.lastHistory(flow, "step64").success()).isTrue();
	}

	@Test
	void 다시_돌_때는_검증을_생략하지_않고_화면_검증으로_간다() {
		WorkflowStepper flow = this.atStep("step43");
		flow.put("designApproval", Map.of("decision", "승인(개발진행, 기본 검증)"));
		flow.step("step43");
		flow.step("step64");
		assertThat(flow.currentStepId()).isEqualTo("step19");

		// 사람의 새 결정 없이 자동 수정을 거쳐 다시 컴파일 확인 뒤로 온 경우: 한도(1번)를 다 써서 step43을 건너뛰고 화면 검증으로 간다
		flow.jumpTo("step43");
		flow.step("step43");
		assertThat(flow.currentStepId()).isEqualTo("step41");
		assertThat(this.lastHistory(flow, "step43").success()).isTrue();
		assertThat(this.lastHistory(flow, "step43").outputSummary()).contains("횟수 한도");
	}

	@Test
	void 재개발할_때_고른_검증_수준이_설계_승인의_선택보다_앞선다() {
		String[][] cases = { { "재개발(서버 검증)", "step41" }, { "재개발(기본 검증)", "step19" } };
		for (String[] one : cases) {
			WorkflowStepper flow = this.atStep("step43");
			flow.put("designApproval", Map.of("decision", "승인(개발진행, 기본 검증)"));
			flow.put("developApproval", Map.of("decision", one[0]));
			flow.step("step43");

			assertThat(flow.currentStepId()).as(one[0]).isEqualTo(one[1]);
		}
	}

	@Test
	void 개발_승인에서_문서_수정을_고른_뒤에는_새_설계_승인의_검증_수준을_따른다() {
		WorkflowStepper flow = this.atStep("step43");
		flow.put("designApproval", Map.of("decision", "승인(개발진행, 기본 검증)"));
		flow.put("developApproval", Map.of("decision", "설계 수정"));
		flow.step("step43");
		flow.step("step64");

		assertThat(flow.currentStepId()).isEqualTo("step19");
	}

	@Test
	void 테스트_서버를_띄울_수_없으면_통과로_치지_않고_이유를_남긴다() {
		WorkflowStepper flow = this.atStep("step29");
		flow.put("projectId", "noSuchProject").put("projectRevision", Map.of("route", "ok", "revision", "r1"));
		flow.step("step29");

		assertThat(flow.currentStepId()).isEqualTo("step30");
		assertThat(String.valueOf(flow.state("serverCheck"))).contains("cause=environment");
		flow.step("step30");
		assertThat(flow.currentStepId()).isEqualTo("step19");
		assertThat(flow.state("serverError")).isNotNull();
		// 환경 탓이라 자동 수정하지 않는 것은 정상 판정이라 이력에 실패로 남지 않는다(2.6.1)
		assertThat(this.lastHistory(flow, "step30").success()).isTrue();
	}

	/**
	 * <pre>
	 * 자동 수정은 설계서도 고칩니다. 검증 Agent가 고치기 전 설계서를 받지 않도록
	 * 서버 기동 앞에서 설계서를 다시 읽는지 봅니다(2.6.0).
	 * </pre>
	 */
	@Test
	void 검증_전에_고친_설계서를_다시_읽는다() throws Exception {
		Path runDir = Files.createDirectories(this.workDir.resolve("20261122-2300"));
		Files.writeString(runDir.resolve("03-1.design.md"), "# 설계서\n## 수정 이력\n자동 수정: 고침", StandardCharsets.UTF_8);
		WorkflowStepper flow = this.atStep("step41");
		flow.put("docs", Map.of("design", "# 설계서(고치기 전)"));
		flow.step("step41");

		// 태스크아이템 목록이 없어도 예전처럼 설계서만 읽고 넘어간다(step41은 목록을 required로 두지 않음)
		assertThat(flow.currentStepId()).isEqualTo("step40");
		assertThat(String.valueOf(flow.stateMap("docs").get("design"))).contains("자동 수정: 고침");
	}

	@Test
	void 개발_결과_탓인_기동_실패는_자동_수정으로_간다() {
		WorkflowStepper flow = this.atStep("step30");
		flow.put("serverCheck", Map.of("success", Boolean.FALSE, "cause", "code", "message", "컴파일 오류"));
		flow.step("step30");

		assertThat(flow.currentStepId()).isEqualTo("step32");
	}

	@Test
	void FAIL이_있으면_자동_수정으로_간다() {
		WorkflowStepper flow = this.atStep("step36");
		flow.put("verificationAll", List.of(Map.of("id", "T1", "result", "PASS"), Map.of("id", "T2", "result", "FAIL")));
		flow.step("step36");

		assertThat(flow.currentStepId()).isEqualTo("step32");
	}

	@Test
	void FAIL이_없으면_사람에게_간다() {
		WorkflowStepper flow = this.atStep("step36");
		flow.put("verificationAll", List.of(Map.of("id", "T1", "result", "PASS"), Map.of("id", "T2", "result", "BLOCKED")));
		flow.step("step36");

		assertThat(flow.currentStepId()).isEqualTo("step19");
		// "FAIL 없음"은 좋은 결과라 이력에 실패로 남지 않는다(2.6.1)
		assertThat(this.lastHistory(flow, "step36").success()).isTrue();
	}

	/**
	 * <pre>
	 * 검증 Agent가 일부 기준만 답해도 지난 결과가 남는지 봅니다(2.14.0).
	 * 남은 항목은 carried로 표시하고, 개발 폴더를 새로 만들 때(step63)는 비웁니다.
	 * </pre>
	 */
	@Test
	void 이번에_답하지_않은_기준은_지난_결과가_남는다() {
		WorkflowStepper flow = this.atStep("step62");
		flow.put("verificationAll", List.of(Map.of("id", "TASK-01 ①", "result", "PASS"), Map.of("id", "TASK-02 ①", "result", "FAIL")));
		flow.put("verification", Map.of("scenarios", List.of(Map.of("id", "TASK-02①", "result", "PASS"))));
		flow.step("step62");

		assertThat(flow.currentStepId()).isEqualTo("step36");
		assertThat(flow.state("verificationAll")).isEqualTo(List.of(Map.of("id", "TASK-01 ①", "result", "PASS", "carried", true),
			Map.of("id", "TASK-02①", "result", "PASS", "carried", false)));
		flow.step("step36");
		assertThat(flow.currentStepId()).isEqualTo("step19");

		flow.jumpTo("step63");
		flow.step("step63");
		assertThat(flow.currentStepId()).isEqualTo("step65");
		assertThat(flow.state("verificationAll")).isEqualTo(List.of());
	}

	@Test
	void 자동_수정은_두_번까지고_사람이_새로_시키면_다시_센다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall());
		// 2.11.0부터 자동 수정 횟수는 step32의 limit(max 2, scope 결정 기록)이 센다
		Path runDir = Files.createDirectories(this.workDir.resolve("20261122-2300"));
		Files.writeString(runDir.resolve("03-1.design.md"), "# 설계서", StandardCharsets.UTF_8);
		Files.writeString(runDir.resolve("03-2.taskitems.md"), "# 목록", StandardCharsets.UTF_8);
		WorkflowStepper flow = this.atStep("step32");
		flow.step("step32");
		assertThat(flow.currentStepId()).isEqualTo("step34");
		flow.jumpTo("step32");
		flow.step("step32");
		assertThat(flow.currentStepId()).isEqualTo("step34");
		flow.jumpTo("step32");
		flow.step("step32");
		assertThat(flow.currentStepId()).isEqualTo("step19");
		// 한도를 다 쓴 것도 정상 판정이라 이력에 실패로 남지 않는다(2.6.1)
		assertThat(this.lastHistory(flow, "step32").success()).isTrue();

		flow.step("step19");
		flow.decide("재개발(서버 검증)", "다시 해 주세요");
		flow.jumpTo("step32");
		flow.step("step32");
		assertThat(flow.currentStepId()).isEqualTo("step34");
	}

	/** 그 step의 마지막 실행 이력입니다. */
	private StepHistoryEntry lastHistory(WorkflowStepper flow, String stepId) {
		StepHistoryEntry found = null;
		for (StepHistoryEntry entry : flow.history()) {
			if (stepId.equals(entry.stepId())) {
				found = entry;
			}
		}
		assertThat(found).as(stepId + " 이력").isNotNull();
		return found;
	}

	/** stepId부터 시작합니다. */
	private WorkflowStepper atStep(String stepId) {
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString().replace("\\", "/"));
		flow.put("now", "20261122-2300").put("request", "## 현업요청").put("sourceDir", "/app/myApp");
		return flow.jumpTo(stepId);
	}

}

package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.charset.StandardCharsets;
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
		flow.put("designApproval", Map.of("decision", "승인(개발진행, 전체 확인)"));
		flow.step("step28");
		assertThat(flow.currentStepId()).isEqualTo("step60");
		flow.step("step60");
		assertThat(String.valueOf(flow.state("ddlCheck"))).contains("route=none");
		flow.step("step42");
		flow.step("step43");
		flow.step("step44");

		assertThat(flow.currentStepId()).isEqualTo("step41");
	}

	/**
	 * <pre>
	 * 설계 승인에서 고른 검증 수준을 첫 검증에서 따르는지 봅니다(2.7.0).
	 * 최소 확인이면 서버를 띄우지 않고, 간이 확인이면 서버 기동만 하고 사람에게 갑니다.
	 * 이 판정들은 정상 결과라 이력에 실패로 남지 않습니다.
	 * </pre>
	 */
	@Test
	void 컴파일만으로_승인하면_첫_검증은_서버_없이_사람에게_간다() {
		WorkflowStepper flow = this.atStep("step42");
		flow.put("designApproval", Map.of("decision", "승인(개발진행, 최소 확인)"));
		flow.step("step42");
		assertThat(flow.currentStepId()).isEqualTo("step43");
		flow.step("step43");

		assertThat(flow.currentStepId()).isEqualTo("step19");
		assertThat(this.lastHistory(flow, "step43").success()).isTrue();
	}

	@Test
	void 기동만_확인으로_승인하면_첫_검증은_서버_기동만_한다() {
		WorkflowStepper flow = this.atStep("step42");
		flow.put("designApproval", Map.of("decision", "승인(개발진행, 간이 확인)"));
		flow.step("step42");
		flow.step("step43");
		assertThat(flow.currentStepId()).isEqualTo("step44");
		flow.step("step44");

		assertThat(flow.currentStepId()).isEqualTo("step45");
		assertThat(this.lastHistory(flow, "step44").success()).isTrue();
	}

	@Test
	void 기동만_확인에서_기동에_실패하면_기동_실패_판정으로_간다() {
		WorkflowStepper flow = this.atStep("step45");
		flow.put("projectId", "noSuchProject").put("sourceDir", this.workDir.toString());
		flow.step("step45");

		assertThat(flow.currentStepId()).isEqualTo("step30");
	}

	@Test
	void 다시_돌_때는_검증을_생략하지_않고_화면_검증으로_간다() {
		WorkflowStepper flow = this.atStep("step42");
		flow.put("designApproval", Map.of("decision", "승인(개발진행, 최소 확인)"));
		flow.step("step42");
		assertThat(flow.currentStepId()).isEqualTo("step43");

		// 재개발·전문가 해결·자동 수정을 거쳐 다시 컴파일 확인 뒤로 온 경우
		flow.jumpTo("step42");
		flow.step("step42");
		assertThat(flow.currentStepId()).isEqualTo("step41");
		assertThat(this.lastHistory(flow, "step42").success()).isTrue();
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
		flow.put("design", "# 설계서(고치기 전)");
		flow.step("step41");

		assertThat(flow.currentStepId()).isEqualTo("step40");
		assertThat(String.valueOf(flow.state("design"))).contains("자동 수정: 고침");
	}

	@Test
	void 개발_결과_탓인_기동_실패는_자동_수정으로_간다() {
		WorkflowStepper flow = this.atStep("step30");
		flow.put("serverCheck", Map.of("success", Boolean.FALSE, "cause", "code", "message", "컴파일 오류"));
		flow.step("step30");

		assertThat(flow.currentStepId()).isEqualTo("step31");
	}

	@Test
	void FAIL이_있으면_자동_수정으로_간다() {
		WorkflowStepper flow = this.atStep("step36");
		flow.put("verification", Map.of("scenarios", List.of(Map.of("result", "PASS"), Map.of("result", "FAIL"))));
		flow.step("step36");

		assertThat(flow.currentStepId()).isEqualTo("step31");
	}

	@Test
	void FAIL이_없으면_사람에게_간다() {
		WorkflowStepper flow = this.atStep("step36");
		flow.put("verification", Map.of("scenarios", List.of(Map.of("result", "PASS"), Map.of("result", "BLOCKED"))));
		flow.step("step36");

		assertThat(flow.currentStepId()).isEqualTo("step19");
		// "FAIL 없음"은 좋은 결과라 이력에 실패로 남지 않는다(2.6.1)
		assertThat(this.lastHistory(flow, "step36").success()).isTrue();
	}

	@Test
	void 자동_수정은_두_번까지고_사람이_새로_시키면_다시_센다() {
		assumeFalse(this.chatModel.isLlmCall());
		WorkflowStepper flow = this.atStep("step31");
		flow.step("step31");
		assertThat(flow.currentStepId()).isEqualTo("step32");
		flow.jumpTo("step31");
		flow.step("step31");
		assertThat(flow.currentStepId()).isEqualTo("step32");
		flow.jumpTo("step31");
		flow.step("step31");
		assertThat(flow.currentStepId()).isEqualTo("step19");
		// 한도를 다 쓴 것도 정상 판정이라 이력에 실패로 남지 않는다(2.6.1)
		assertThat(this.lastHistory(flow, "step31").success()).isTrue();

		flow.step("step19");
		flow.decide("재개발", "다시 해 주세요");
		flow.jumpTo("step31");
		flow.step("step31");
		assertThat(flow.currentStepId()).isEqualTo("step32");
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

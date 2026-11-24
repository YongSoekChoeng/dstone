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
	void 프로젝트ID가_없으면_검증하지_않고_사람에게_간다() throws Exception {
		Files.writeString(this.workDir.resolve("00.request.md"), "## 현업요청\n목록 화면", StandardCharsets.UTF_8);
		WorkflowStepper flow = this.atStep("step28");
		flow.step("step28");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.currentStepId()).isEqualTo("step19");
		assertThat(String.valueOf(flow.state("projectIdError"))).contains("프로젝트ID");
	}

	@Test
	void 프로젝트ID가_있으면_설계서를_다시_읽으러_간다() throws Exception {
		Files.writeString(this.workDir.resolve("00.request.md"), "## 현업요청\n목록 화면\n## 프로젝트ID\nmyApp", StandardCharsets.UTF_8);
		WorkflowStepper flow = this.atStep("step28");
		flow.step("step28");

		assertThat(flow.currentStepId()).isEqualTo("step41");
	}

	@Test
	void 격리할_수_없으면_통과로_치지_않고_이유를_남긴다() {
		WorkflowStepper flow = this.atStep("step29");
		flow.put("projectId", "noSuchProject").put("sourceDir", this.workDir.toString());
		flow.step("step29");

		assertThat(flow.currentStepId()).isEqualTo("step30");
		assertThat(String.valueOf(flow.state("sandboxCheck"))).contains("cause=environment");
		flow.step("step30");
		assertThat(flow.currentStepId()).isEqualTo("step19");
		assertThat(flow.state("sandboxError")).isNotNull();
	}

	/**
	 * <pre>
	 * 자동 수정·전문가 해결은 설계서도 고칩니다. 검증 Agent가 고치기 전 설계서를 받지 않도록
	 * 격리 기동 앞에서 설계서를 다시 읽는지 봅니다(2.6.0).
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
		flow.put("sandboxCheck", Map.of("success", Boolean.FALSE, "cause", "code", "message", "DDL 오류"));
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

		flow.step("step19");
		flow.decide("재개발", "다시 해 주세요");
		flow.jumpTo("step31");
		flow.step("step31");
		assertThat(flow.currentStepId()).isEqualTo("step32");
	}

	/** stepId부터 시작합니다. */
	private WorkflowStepper atStep(String stepId) {
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString().replace("\\", "/"));
		flow.put("now", "20261122-2300").put("request", "## 현업요청").put("sourceDir", "/app/myApp");
		return flow.jumpTo(stepId);
	}

}

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
		WorkflowStepper flow = this.atStep("step31");
		flow.step("step31");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.currentStepId()).isEqualTo("step17");
		assertThat(String.valueOf(flow.state("projectIdError"))).contains("프로젝트ID");
	}

	@Test
	void 격리할_수_없으면_통과로_치지_않고_이유를_남긴다() {
		WorkflowStepper flow = this.atStep("step32");
		flow.put("projectId", "noSuchProject").put("sourceDir", this.workDir.toString());
		flow.step("step32");

		assertThat(flow.currentStepId()).isEqualTo("step33");
		assertThat(String.valueOf(flow.state("sandboxCheck"))).contains("cause=environment");
		flow.step("step33");
		assertThat(flow.currentStepId()).isEqualTo("step17");
		assertThat(flow.state("sandboxError")).isNotNull();
	}

	@Test
	void 개발_결과_탓인_기동_실패는_자동_수정으로_간다() {
		WorkflowStepper flow = this.atStep("step33");
		flow.put("sandboxCheck", Map.of("success", Boolean.FALSE, "cause", "code", "message", "DDL 오류"));
		flow.step("step33");

		assertThat(flow.currentStepId()).isEqualTo("step36");
	}

	@Test
	void FAIL이_있으면_자동_수정으로_간다() {
		WorkflowStepper flow = this.atStep("step35");
		flow.put("verification", Map.of("scenarios", List.of(Map.of("result", "PASS"), Map.of("result", "FAIL"))));
		flow.step("step35");

		assertThat(flow.currentStepId()).isEqualTo("step36");
	}

	@Test
	void FAIL이_없으면_사람에게_간다() {
		WorkflowStepper flow = this.atStep("step35");
		flow.put("verification", Map.of("scenarios", List.of(Map.of("result", "PASS"), Map.of("result", "BLOCKED"))));
		flow.step("step35");

		assertThat(flow.currentStepId()).isEqualTo("step17");
	}

	@Test
	void 자동_수정은_두_번까지고_사람이_새로_시키면_다시_센다() {
		assumeFalse(this.chatModel.isLlmCall());
		WorkflowStepper flow = this.atStep("step36");
		flow.step("step36");
		assertThat(flow.currentStepId()).isEqualTo("step37");
		flow.jumpTo("step36");
		flow.step("step36");
		assertThat(flow.currentStepId()).isEqualTo("step37");
		flow.jumpTo("step36");
		flow.step("step36");
		assertThat(flow.currentStepId()).isEqualTo("step17");

		flow.step("step17");
		flow.decide("재개발", "다시 해 주세요");
		flow.jumpTo("step36");
		flow.step("step36");
		assertThat(flow.currentStepId()).isEqualTo("step37");
	}

	/** stepId부터 시작합니다. */
	private WorkflowStepper atStep(String stepId) {
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString().replace("\\", "/"));
		flow.put("now", "20261122-2300").put("request", "## 현업요청").put("sourceDir", "/app/myApp");
		return flow.jumpTo(stepId);
	}

}

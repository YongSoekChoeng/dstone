package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.support.ScriptedChatModel;
import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow가 앞에서 고친 것과 지적한 것을 잃어버리지 않는지 확인합니다(LLM 없음).
 * - 설계서가 있으면 영향도를 다시 승인해도 새로 쓰지 않고 고친다.
 * - 영향도분석서가 있으면 요구사항을 고쳐도 새로 쓰지 않고 고친다.
 * - 개발 폴더를 비우면 옛 컴파일 오류도 비운다.
 * - 리뷰어의 지적은 사람의 결정과 함께 요구사항 수정 Agent에게 간다.
 * - 문서가 상한보다 크면 잘린 채로 넘기지 않고 멈춘다.
 * </pre>
 */
public class SmKeepFixesTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	private static final String NOW = "20261122-2300";

	@TempDir
	Path workDir;

	@Test
	void 설계서가_있으면_영향도_승인_뒤에_새로_쓰지_않고_고친다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall());
		this.write("02.impact.md", "# 영향도 NEW_IMPACT");
		Path design = this.write("03-1.design.md", "# 설계\nFIX_FROM_REDEVELOP\n");
		this.chatModel.answerToolCalls("appendFile", this.appendArguments(design));
		this.chatModel.answer("고친 파일: 03-1.design.md");

		WorkflowStepper flow = this.atStep("step09");
		flow.step("step09");
		flow.decide("승인(설계진행)", "APPROVAL_COMMENT");
		flow.step("step10");
		flow.step("step11");
		assertThat(flow.currentStepId()).isEqualTo("step37");
		flow.step("step37");

		assertThat(flow.currentStepId()).isEqualTo("step13");
		assertThat(Files.readString(design, StandardCharsets.UTF_8)).contains("FIX_FROM_REDEVELOP");
		assertThat(ScriptedChatModel.textOf(this.chatModel.prompts().get(0))).contains("NEW_IMPACT", "APPROVAL_COMMENT");
	}

	@Test
	void 설계서가_없으면_처음부터_쓴다() throws IOException {
		this.write("02.impact.md", "# 영향도");
		WorkflowStepper flow = this.atStep("step10");
		flow.step("step10");
		flow.step("step11");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.currentStepId()).isEqualTo("step12");
	}

	@Test
	void 설계서가_상한보다_커도_있는_것으로_본다() throws IOException {
		this.write("03-1.design.md", "# 설계\n" + "x".repeat(70000));
		WorkflowStepper flow = this.atStep("step11");
		flow.step("step11");

		assertThat(flow.currentStepId()).isEqualTo("step37");
	}

	@Test
	void 설계를_처음부터_다시_쓰는_길은_사람이_고른다() {
		WorkflowStepper flow = this.atStep("step13");
		flow.step("step13");
		flow.decide("설계 다시 작성", "구조를 바꿔 주세요");

		assertThat(flow.currentStepId()).isEqualTo("step12");
	}

	@Test
	void 영향도분석서가_있으면_리뷰_뒤에_새로_쓰지_않고_고친다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall());
		Path impact = this.write("02.impact.md", "# 영향도\nIMPACT_FIX\n");
		this.chatModel.answerToolCalls("appendFile", this.appendArguments(impact));
		this.chatModel.answer("고친 파일: 02.impact.md");

		WorkflowStepper flow = this.atStep("step06");
		flow.put("requirement", "# 요구사항 NEW_REQUIREMENT");
		flow.step("step06");
		flow.decide("그대로 진행", "진행");
		flow.step("step07");
		assertThat(flow.currentStepId()).isEqualTo("step38");
		flow.step("step38");

		assertThat(flow.currentStepId()).isEqualTo("step09");
		assertThat(Files.readString(impact, StandardCharsets.UTF_8)).contains("IMPACT_FIX");
		assertThat(ScriptedChatModel.textOf(this.chatModel.prompts().get(0))).contains("NEW_REQUIREMENT");
	}

	@Test
	void 영향도분석서가_없으면_처음부터_쓴다() {
		WorkflowStepper flow = this.atStep("step07");
		flow.step("step07");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.currentStepId()).isEqualTo("step08");
	}

	@Test
	void 개발_폴더를_비우면_옛_컴파일_오류도_비운다() throws IOException {
		this.write("dev-resources/A.java", "class A {}");
		WorkflowStepper flow = this.atStep("step16");
		flow.put("compileError", "A.java:1 OLD_ERROR");
		flow.step("step16");

		assertThat(flow.currentStepId()).isEqualTo("step17");
		assertThat(flow.state("compileError")).isNull();
	}

	@Test
	void 리뷰어의_지적은_사람의_결정과_함께_수정_Agent에_간다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall());
		Path requirement = this.write("01.customer_requirement.md", "# 요구사항\n");
		this.chatModel.answerToolCalls("appendFile", this.appendArguments(requirement));
		this.chatModel.answer("고친 파일: 01.customer_requirement.md");

		Map<String, Object> review = new LinkedHashMap<String, Object>();
		review.put("pass", Boolean.FALSE);
		review.put("reason", "REVIEWER_REASON");
		WorkflowStepper flow = this.atStep("step06");
		flow.put("review", review);
		flow.step("step06");
		flow.decide("요구사항 수정", "HUMAN_COMMENT");
		flow.step("step39");

		assertThat(flow.currentStepId()).isEqualTo("step04");
		assertThat(ScriptedChatModel.textOf(this.chatModel.prompts().get(0))).contains("REVIEWER_REASON", "HUMAN_COMMENT");
	}

	@Test
	void 리뷰_답을_못_받았으면_다시_리뷰할_수_있다() {
		WorkflowStepper flow = this.atStep("step06");
		flow.step("step06");
		flow.decide("다시 리뷰", "판정을 받지 못함");

		assertThat(flow.currentStepId()).isEqualTo("step04");
	}

	@Test
	void 상한보다_큰_설계서는_잘린_채로_넘기지_않는다() throws IOException {
		this.write("03-1.design.md", "x".repeat(60001));
		WorkflowStepper flow = this.atStep("step14");
		flow.step("step14");

		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.execution().errorMessage()).contains("60001");
	}

	@Test
	void 되감아도_새로_쓰는_Agent가_승인_의견을_돌려받는다() {
		WorkflowStepper flow = this.atStep("step09");
		flow.step("step09");
		flow.decide("승인(설계진행)", "REWIND_COMMENT");
		// step10는 문서가 없어 실패하지만, 되감기는 그 앞의 결정 기록만 본다
		flow.step("step10"); // 결정이 어디로 갔는지 기록된 뒤의 실행

		for (String stepId : List.of("step12", "step37")) {
			Object restored = ReflectionTestUtils.invokeMethod(this.workFlowExecutionService, "arrivalFor",
				this.workFlowRegistry.resolve(WORKFLOW_ID, null), flow.execution().context(), stepId);
			assertThat(String.valueOf(restored)).as(stepId).contains("REWIND_COMMENT");
		}
	}

	/** 작업 폴더에 파일을 만듭니다. */
	private Path write(String name, String text) throws IOException {
		Path file = this.workDir.resolve(NOW).resolve(name);
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
		return file;
	}

	/** 수정 이력 한 줄을 붙이는 appendFile 인자입니다. */
	private String appendArguments(Path file) {
		return "{\"filePath\": \"" + file.toString().replace("\\", "/") + "\", \"fileContents\": \"\\n## 수정 이력\\n- 반영\"}";
	}

	/** 앞 step이 저장했을 값을 넣고 stepId부터 시작합니다. */
	private WorkflowStepper atStep(String stepId) {
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString().replace("\\", "/"));
		flow.put("now", NOW).put("request", "## 현업요청\n이미지 게시판").put("requirement", "# 요구사항").put("sourceDir", "/app/myApp");
		return flow.jumpTo(stepId);
	}

}

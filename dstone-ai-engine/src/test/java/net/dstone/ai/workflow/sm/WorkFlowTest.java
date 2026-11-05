package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow(어플리케이션 운영관리) 테스트입니다.
 *
 * 가짜 LLM은 파일을 쓰지 않으므로, Agent가 저장했어야 할 문서를 임시 폴더에 미리 만들어 둡니다.
 * </pre>
 */
public class WorkFlowTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	@TempDir
	Path workDir;

	/** 작업 폴더에 문서를 만듭니다. */
	private void writeDoc(String name, String text) throws IOException {
		Files.write(this.workDir.resolve(name), text.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void 작업_시각은_LLM_없이_정한다() {
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString());
		flow.step("step00");
		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(String.valueOf(flow.state("now"))).matches("\\d{8}-\\d{4}");
		assertThat(flow.currentStepId()).isEqualTo("step01");
		if (!this.chatModel.isLlmCall()) {
			assertThat(this.chatModel.prompts()).isEmpty();
		}
	}

	@Test
	void 설계_step이_태스크_목록을_저장하지_못하면_개발로_넘어가지_않는다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall());
		// 작업 폴더는 입력 폴더/실행 시각입니다. 설계서만 만들고 태스크아이템 목록은 만들지 않습니다.
		String now = "20261105-0900";
		Files.createDirectories(this.workDir.resolve(now));
		this.writeDoc(now + "/03-1.design.md", "설계서 내용");
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString().replace("\\", "/"));
		flow.put("now", now);
		flow.jumpTo("step11");
		flow.step("step11");
		assertThat(flow.state("design")).isEqualTo("설계서 내용");
		flow.step("step12");
		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(this.chatModel.prompts()).isEmpty();
	}

}

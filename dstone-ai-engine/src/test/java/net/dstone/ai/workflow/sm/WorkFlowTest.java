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
	void 작업_시각은_LLM_없이_정한다() throws IOException {
		// 2.11.0부터 시각을 구하는 step 없이 엔진이 settings.startTime으로 첫 step 전에 넣는다
		this.writeDoc("00.request.md", "## 현업요청\n이미지 게시판\n");
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString().replace("\\", "/"));
		flow.step("step01");
		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(String.valueOf(flow.state("now"))).matches("\\d{8}-\\d{4}");
		assertThat(flow.currentStepId()).isEqualTo("step02");
		if (!this.chatModel.isLlmCall()) {
			assertThat(this.chatModel.prompts()).isEmpty();
		}
	}

	@Test
	void 요청서의_프로젝트ID를_LLM_없이_state에_담고_리비전을_정하러_간다() throws IOException {
		this.writeDoc("00.request.md", "## 현업요청\n이미지 게시판\n\n## 프로젝트ID\n\tmyApp\n");
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString().replace("\\", "/"));
		flow.step("step01");
		flow.step("step02");
		assertThat(flow.state("projectId")).isEqualTo("myApp");
		assertThat(flow.currentStepId()).isEqualTo("step59");
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
		flow.jumpTo("step14");
		// 설계서와 목록을 한 step(readFiles)에서 읽고, 목록이 없으면(required) 그 자리에서 실패한다
		flow.step("step14");
		assertThat(flow.currentStepId()).isEqualTo("step13");
		assertThat(this.chatModel.prompts()).isEmpty();
	}

}

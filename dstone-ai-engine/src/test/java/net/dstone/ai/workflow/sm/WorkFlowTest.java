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
	void 설계_step이_태스크_목록을_저장하지_못하면_개발로_넘어가지_않는다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall());
		this.writeDoc("03-1.design.md", "설계서 내용");
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString());
		flow.jumpTo("step11");
		flow.step("step11");
		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
	}

}

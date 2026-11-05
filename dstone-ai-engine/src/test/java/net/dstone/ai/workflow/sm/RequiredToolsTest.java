package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.common.registry.DefinitionReloader;
import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * Agent의 execution.requiredTools(꼭 성공해야 하는 Tool)를 확인합니다.
 * sm-requirement-analyze-agent(sm-workflow step02)는 requiredTools: [writeFile], onInvalidOutput: RETRY, maxAttempts: 2입니다.
 * - writeFile 없이 "이제 작성합니다"라고만 답하면 한 번 더 부르고, 두 번 다 그러면 step이 실패합니다.
 * - 두 번째에 writeFile을 부르면 step은 성공합니다.
 * - tools.allowed에 없는 이름을 requiredTools에 적으면 [검사]에서 막힙니다.
 * </pre>
 */
public class RequiredToolsTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	private static final String STEP_ID = "step02";

	private static final String AGENT_ID = "sm-requirement-analyze-agent";

	@Autowired
	private DefinitionReloader reloader;
	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private YamlDefinitionLoader loader;

	@TempDir
	Path workDir;

	@Test
	void 저장하지_않고_끝나면_다시_부르고_그래도_안_하면_실패한다() {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		this.chatModel.answer("요청을 파악했습니다. 이제 산출물을 작성합니다.", "이제 작성하겠습니다.");

		WorkflowStepper flow = this.stepper(WORKFLOW_ID, STEP_ID, this.stepInput());
		flow.step(STEP_ID);

		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.errorMessage()).contains("2번 불렀지만").contains("[writeFile]").contains("requiredTools");
		assertThat(this.chatModel.prompts()).hasSize(2);
	}

	@Test
	void 다시_불렀을_때_저장하면_성공한다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		Path doc = this.workDir.resolve("01.customer_requirement.md");
		this.chatModel.answer("이제 산출물을 작성합니다.");
		this.chatModel.answerToolCalls("writeFile", "{\"filePath\": \"" + doc.toString().replace("\\", "/") + "\", \"fileContents\": \"# 고객 요구사항\"}");
		this.chatModel.answer(doc.toString());

		WorkflowStepper flow = this.stepper(WORKFLOW_ID, STEP_ID, this.stepInput());
		flow.step(STEP_ID);

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.currentStepId()).isEqualTo("step03");
		assertThat(Files.readString(doc)).isEqualTo("# 고객 요구사항");
		assertThat(this.chatModel.prompts()).hasSize(3);
	}

	@Test
	void tools_allowed에_없는_이름은_검사에서_막힌다() {
		String path = this.agentRegistry.pathOf(AGENT_ID);
		String text = this.source(path).replace("  requiredTools:\n    - writeFile", "  requiredTools:\n    - readFile");

		String message = this.checkFails(Map.of(path, text));

		assertThat(message).contains("execution.requiredTools").contains("readFile").contains("tools.allowed에 없습니다");
	}

	private Map<String, Object> stepInput() {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("base_dir", this.workDir.toString().replace("\\", "/"));
		input.put("request", "## 현업요청\n이미지 게시판을 만들어 주세요.");
		return input;
	}

	/** 바꾼 내용으로 검사만 하고(파일은 쓰지 않음), 실패 메시지를 돌려줍니다. 통과하면 테스트를 실패시킵니다. */
	private String checkFails(Map<String, String> changes) {
		try {
			this.reloader.apply(changes, false);
		} catch (IllegalStateException e) {
			return e.getMessage();
		}
		throw new AssertionError("검사를 통과하면 안 됩니다: " + changes.keySet());
	}

	/** 정의 폴더의 파일을 글자 그대로 읽습니다. */
	private String source(String path) {
		try {
			return Files.readString(this.loader.rootDir().resolve(path));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}

package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.common.registry.DefinitionReloader;
import net.dstone.ai.support.ScriptedChatModel;
import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * Agent의 execution.requiredTools(꼭 성공해야 하는 Tool)를 확인합니다.
 * sm-requirement-analyze-agent(sm-workflow step03)는 requiredTools: [writeFile], onInvalidOutput: RETRY, maxAttempts: 2입니다.
 * - writeFile 없이 "이제 작성합니다"라고만 답하면 한 번 더 부르고, 두 번 다 그러면 step이 실패합니다.
 * - 두 번째에 writeFile을 부르면 step은 성공합니다.
 * - tools.allowed에 없는 이름을 requiredTools에 적으면 [검사]에서 막힙니다.
 * </pre>
 */
public class RequiredToolsTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	private static final String STEP_ID = "step03";

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
		// 다시 부를 때는 지난 시도가 왜 실패했고 무엇을 답했는지 함께 보낸다(처음부터 다시 조사하지 않게).
		String retry = ScriptedChatModel.textOf(this.chatModel.prompts().get(1));
		assertThat(retry).contains("[지난 시도]").contains("requiredTools").contains("요청을 파악했습니다. 이제 산출물을 작성합니다.");
		assertThat(ScriptedChatModel.textOf(this.chatModel.prompts().get(0))).doesNotContain("[지난 시도]");
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
		assertThat(flow.currentStepId()).isEqualTo("step04");
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

	@Test
	void 둘_중_하나만_적은_항목은_어느_것이든_성공하면_된다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		// document-revise-agent(step25 설계서 수정)는 requiredTools: [editFileTexts|writeFile|appendFile]다. 고칠 때는 editFileTexts로만 끝난다.
		Path design = this.workDir.resolve("03-1.design.md");
		Files.writeString(design, "import com.example.system.file.UPLOADS;\n");
		this.chatModel.answerToolCalls("editFileTexts", "{\"filePath\": \"" + design.toString().replace("\\", "/")
			+ "\", \"edits\": [{\"before\": \"com.example.system.file.UPLOADS\", \"after\": \"com.example.config.Constants.UPLOADS\"}]}");
		this.chatModel.answer("지적된 import만 고쳤습니다.");

		Map<String, Object> input = new LinkedHashMap<>();
		input.put("documents", List.of(design.toString().replace("\\", "/")));
		input.put("request", "## 현업요청\n이미지 게시판");
		input.put("instruction", "개발 승인(step19)의 결정: 설계 수정\n의견:\nUPLOADS import가 틀렸다");
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, "step25", input);
		flow.step("step25");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(Files.readString(design)).contains("com.example.config.Constants.UPLOADS");
	}

	@Test
	void 주_문서를_고치지_않고_다른_문서만_고치면_실패한다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		// document-revise-agent는 execution.requiredWrites: [${input.documents[0]}]다. 두 번 다 태스크아이템 목록만 고치면 실패한다.
		Path design = this.workDir.resolve("03-1.design.md");
		Path taskitems = this.workDir.resolve("03-2.taskitems.md");
		Files.writeString(design, "# 설계\n최대 30MB\n");
		Files.writeString(taskitems, "| TASK-ITEM-01 | 대기 |\n");
		String editTaskitems = "{\"filePath\": \"" + taskitems.toString().replace("\\", "/") + "\", \"edits\": [{\"before\": \"대기\", \"after\": \"대기(수정)\"}]}";
		this.chatModel.answerToolCalls("editFileTexts", editTaskitems);
		this.chatModel.answer("고친 파일: 03-2.taskitems.md");
		this.chatModel.answerToolCalls("editFileTexts", editTaskitems.replace("대기(수정)", "대기(다시)").replace("\"대기\"", "\"대기(수정)\""));
		this.chatModel.answer("고친 파일: 03-2.taskitems.md");

		Map<String, Object> input = new LinkedHashMap<>();
		input.put("documents", List.of(design.toString().replace("\\", "/"), taskitems.toString().replace("\\", "/")));
		input.put("instruction", "설계 승인(step13)의 결정: 설계 수정\n의견:\n최대 50MB로");
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, "step25", input);
		flow.step("step25");

		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.errorMessage()).contains("requiredWrites").contains("03-1.design.md");
		// 다시 부를 때 지난 시도의 실패 이유(주 문서를 안 고쳤다)가 함께 간다.
		assertThat(ScriptedChatModel.textOf(this.chatModel.prompts().get(2))).contains("[지난 시도]").contains("03-1.design.md");
	}

	@Test
	void requiredWrites는_input을_가리키는_표현식이어야_한다() {
		String path = this.agentRegistry.pathOf("document-revise-agent");
		String text = this.source(path).replace("    - ${input.documents[0]}", "    - ${input.nope}");
		assertThat(this.checkFails(Map.of(path, text))).contains("execution.requiredWrites").contains("nope");

		String plain = this.source(path).replace("    - ${input.documents[0]}", "    - 03-1.design.md");
		assertThat(this.checkFails(Map.of(path, plain))).contains("execution.requiredWrites").contains("표현식");
	}

	@Test
	void 둘_중_하나_이름도_tools_allowed에_있어야_한다() {
		String path = this.agentRegistry.pathOf(AGENT_ID);
		String text = this.source(path).replace("  requiredTools:\n    - writeFile", "  requiredTools:\n    - writeFile|readFile");

		String message = this.checkFails(Map.of(path, text));

		assertThat(message).contains("execution.requiredTools").contains("'readFile'").contains("tools.allowed에 없습니다");
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

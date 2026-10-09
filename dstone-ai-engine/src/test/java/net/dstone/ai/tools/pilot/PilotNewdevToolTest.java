package net.dstone.ai.tools.pilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.tools.utils.FileUtil;

/**
 * <pre>
 * pilot 신규 개발 경로의 Tool을, Workflow의 TOOL step이 부르는 것과 같은 길(인자를 JSON으로 넘기고 답을 JSON으로 받음)로 불러 봅니다.
 * 검증과 문서의 내용은 PilotNewdevLogicTest가 확인하므로, 여기서는 파일을 실제로 읽고 쓰는지와 인자/답이 제대로 오가는지를 봅니다.
 * </pre>
 */
class PilotNewdevToolTest {

	@TempDir
	Path workDir;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private Map<String, ToolCallback> tools;

	@BeforeEach
	void setUp() {
		FileUtil fileUtil = new FileUtil();
		ReflectionTestUtils.setField(fileUtil, "environment", new MockEnvironment());
		PilotNewdevTool tool = new PilotNewdevTool();
		ReflectionTestUtils.setField(tool, "fileUtil", fileUtil);
		this.tools = new LinkedHashMap<>();
		for (ToolCallback callback : MethodToolCallbackProvider.builder().toolObjects(tool).build().getToolCallbacks()) {
			this.tools.put(callback.getToolDefinition().name(), callback);
		}
	}

	/** Tool을 이름으로 부르고 답(JSON)을 자바 값으로 읽어 돌려줍니다. */
	private Object call(String toolName, Map<String, Object> arguments) throws Exception {
		String result = this.tools.get(toolName).call(this.objectMapper.writeValueAsString(arguments));
		return this.objectMapper.readValue(result, Object.class);
	}

	private String path(String name) {
		return this.workDir.resolve(name).toString().replace('\\', '/');
	}

	private String read(String name) throws Exception {
		return Files.readString(this.workDir.resolve(name), StandardCharsets.UTF_8);
	}

	@Test
	void Tool이_11개_등록된다() {
		assertEquals(11, this.tools.size());
	}

	@Test
	@SuppressWarnings("unchecked")
	void 참고_구현_파일은_있는_것만_한_번씩_읽는다() throws Exception {
		Files.writeString(this.workDir.resolve("Board.java"), "class Board {}\n", StandardCharsets.UTF_8);
		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.put("files", List.of(Map.of("path", this.path("Board.java")), Map.of("path", this.path("Missing.java")), Map.of("path", this.path("Board.java"))));

		Map<String, Object> sources = (Map<String, Object>) this.call("pilotNewdevReadSources", arguments);

		// 공통 파일 읽기는 파일 끝의 줄바꿈을 떼고 돌려줍니다(readFile Tool과 같은 결과입니다).
		assertEquals(Map.of(this.path("Board.java"), "class Board {}"), sources.get("files"));
		assertEquals(List.of(this.path("Missing.java")), sources.get("missing"));
		assertEquals("===== 파일: " + this.path("Board.java") + " =====\nclass Board {}", sources.get("text"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void 검증_결과를_파일에_적고_다시_시킬_때_넘길_글자를_돌려준다() throws Exception {
		Files.writeString(this.workDir.resolve("Board.java"), "class Board {}\n", StandardCharsets.UTF_8);
		Map<String, Object> target = new LinkedHashMap<>();
		target.put("path", this.path("Faq.java"));
		target.put("changeType", "신규");
		target.put("referencePath", this.path("Board.java"));
		target.put("change", "새로 만든다");
		target.put("frs", List.of("FR-01"));
		Map<String, Object> analysis = new LinkedHashMap<>();
		analysis.put("scope", List.of(Map.of("item", "FAQ", "frs", List.of("FR-01"))));
		analysis.put("requirements", List.of(Map.of("id", "FR-01", "title", "목록", "then", "보인다")));
		analysis.put("targets", List.of(target));
		Map<String, Object> reference = Map.of("files", List.of(Map.of("path", this.path("Board.java"))));
		Map<String, Object> sources = Map.of("files", Map.of(this.path("Board.java"), "class Board {}\n"));

		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.put("workDir", this.path(""));
		arguments.put("analysis", analysis);
		arguments.put("reference", reference);
		arguments.put("sources", sources);
		Map<String, Object> check = (Map<String, Object>) this.call("pilotNewdevCheckAnalysis", arguments);

		assertEquals(true, check.get("ok"));
		assertEquals("문제 없음", check.get("text"));
		assertEquals("", check.get("feedback"));
		assertEquals("문제 없음", this.read("02-impact-check.md"));

		// 이미 있는 파일을 신규라고 하면 걸립니다. 걸린 내용이 그대로 feedback이 됩니다.
		Files.writeString(this.workDir.resolve("Faq.java"), "class Faq {}\n", StandardCharsets.UTF_8);
		check = (Map<String, Object>) this.call("pilotNewdevCheckAnalysis", arguments);
		assertEquals(false, check.get("ok"));
		assertEquals("- 신규라고 했는데 이미 있는 파일입니다: " + this.path("Faq.java"), check.get("feedback"));
		assertEquals(check.get("text"), this.read("02-impact-check.md"));

		Object saved = this.call("pilotNewdevWriteRequirements", Map.of("workDir", this.path(""), "analysis", analysis, "reference", reference));
		assertTrue(saved.toString().startsWith("저장했습니다"), saved.toString());
		assertTrue(this.read("01-requirements.md").contains("### FR-01 목록"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void 복사한_파일을_줄_번호와_함께_읽어_편집_Agent에_넣을_값을_만든다() throws Exception {
		Files.writeString(this.workDir.resolve("Faq.java"), "class Faq {\n  void update() {}\n}\n", StandardCharsets.UTF_8);
		Map<String, Object> design = new LinkedHashMap<>();
		design.put("path", this.path("Faq.java"));
		design.put("changeType", "신규");
		design.put("referencePath", this.path("Board.java"));
		design.put("summary", "FAQ");
		design.put("removes", List.of("update 메서드"));
		Map<String, Object> arguments = new LinkedHashMap<>();
		// 설계에 실패한 파일의 자리(null)가 섞여 있어도 됩니다.
		arguments.put("designs", java.util.Arrays.asList(design, null));
		arguments.put("copies", List.of(Map.of("sourcePath", this.path("Board.java"), "targetPath", this.path("Faq.java"), "replacements", List.of())));
		arguments.put("copyResults", List.of("복사했습니다. 파일은 전체 30자입니다: " + this.path("Faq.java")));

		List<Object> inputs = (List<Object>) this.call("pilotNewdevDevelopInputs", arguments);

		assertEquals(1, inputs.size());
		Map<String, Object> input = (Map<String, Object>) inputs.get(0);
		assertEquals(this.path("Faq.java"), ((Map<String, Object>) input.get("design")).get("path"));
		assertTrue(input.get("source").toString().contains("2:   void update() {}"), input.get("source").toString());
	}

	@Test
	void 결과가_하나도_없어도_태스크_목록을_만든다() throws Exception {
		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.put("workDir", this.path(""));
		arguments.put("designs", null);
		arguments.put("errors", java.util.Arrays.asList(null, "newCopy[0]: 실패"));

		Object saved = this.call("pilotNewdevWriteTasks", arguments);

		assertTrue(saved.toString().startsWith("저장했습니다"), saved.toString());
		String doc = this.read("04-tasks.md");
		assertTrue(doc.contains("## 실패한 호출\nnewCopy[0]: 실패"), doc);
		assertFalse(doc.contains("TASK-1"));
	}

}

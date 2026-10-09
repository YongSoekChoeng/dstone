package net.dstone.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import net.dstone.ai.tools.data.DataTool;
import net.dstone.ai.tools.file.FileSetTool;
import net.dstone.ai.tools.file.FileTool;
import net.dstone.ai.tools.template.TemplateTool;

/**
 * <pre>
 * 어느 Workflow에서나 TOOL step으로 부르는 범용 Tool(readFiles, readItemFiles, renderTemplate, filterList, checkData, editFileTexts)을, TOOL step이 부르는 것과 같은 길
 * (인자를 JSON으로 넘기고 답을 글자로 받음)로 불러 봅니다.
 * </pre>
 */
class StepToolsTest {

	@TempDir
	Path workDir;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private Map<String, ToolCallback> tools;

	@BeforeEach
	void setUp() {
		FileTool fileTool = new FileTool();
		ReflectionTestUtils.setField(fileTool, "environment", new MockEnvironment());
		FileSetTool fileSetTool = new FileSetTool();
		ReflectionTestUtils.setField(fileSetTool, "fileUtil", fileTool);
		TemplateTool templateTool = new TemplateTool();
		ReflectionTestUtils.setField(templateTool, "fileUtil", fileTool);
		DataTool dataTool = new DataTool();
		ReflectionTestUtils.setField(dataTool, "fileUtil", fileTool);
		this.tools = new LinkedHashMap<>();
		for (ToolCallback callback : MethodToolCallbackProvider.builder().toolObjects(fileTool, fileSetTool, templateTool, dataTool).build().getToolCallbacks()) {
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

	@Test
	@SuppressWarnings("unchecked")
	void readFiles는_있는_파일만_한_번씩_읽는다() throws Exception {
		Files.writeString(this.workDir.resolve("Board.java"), "class Board {}\n", StandardCharsets.UTF_8);
		Files.writeString(this.workDir.resolve("Faq.java"), "class Faq {}\n", StandardCharsets.UTF_8);
		Map<String, Object> arguments = new LinkedHashMap<>();
		// 경로 글자와 {path: 경로} 항목을 섞어 적어도 됩니다.
		arguments.put("paths", List.of(Map.of("path", this.path("Board.java")), Map.of("path", this.path("Missing.java")), this.path("Faq.java"), Map.of("path", this.path("Board.java"))));

		Map<String, Object> sources = (Map<String, Object>) this.call("readFiles", arguments);

		// 공통 파일 읽기는 파일 끝의 줄바꿈을 떼고 돌려줍니다(readFile Tool과 같은 결과입니다).
		assertEquals(Map.of(this.path("Board.java"), "class Board {}", this.path("Faq.java"), "class Faq {}"), sources.get("files"));
		assertEquals(List.of(this.path("Missing.java")), sources.get("missing"));
		assertEquals("===== 파일: " + this.path("Board.java") + " =====\nclass Board {}\n\n===== 파일: " + this.path("Faq.java") + " =====\nclass Faq {}", sources.get("text"));
	}

	@Test
	void renderTemplate은_틀에_데이터를_끼워_파일로_저장한다() throws Exception {
		Map<String, Object> analysis = new LinkedHashMap<>();
		analysis.put("background", "FAQ 메뉴");
		analysis.put("requirements", List.of(Map.of("id", "FR-01", "title", "목록", "then", "보인다")));
		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.put("template", "templates/pilot/newdev-requirements.md.mustache");
		arguments.put("data", Map.of("analysis", analysis));
		arguments.put("filePath", this.path("01-requirements.md"));

		String saved = this.tools.get("renderTemplate").call(this.objectMapper.writeValueAsString(arguments));

		assertTrue(saved.contains("저장했습니다"), saved);
		String doc = Files.readString(this.workDir.resolve("01-requirements.md"), StandardCharsets.UTF_8);
		assertTrue(doc.contains("### FR-01 목록\n- Given: -\n- When: -\n- Then: 보인다"), doc);
		assertTrue(doc.contains("- 같은 기능이 이미 있는지: 미확인"), doc);
	}

	@Test
	void renderTemplate은_없는_틀이나_templates_밖의_경로면_실패를_돌려준다() throws Exception {
		String missing = this.tools.get("renderTemplate").call(this.objectMapper.writeValueAsString(Map.of("template", "templates/none.mustache")));
		assertTrue(missing.contains("실패: 틀 파일이 없습니다"), missing);
		String outside = this.tools.get("renderTemplate").call(this.objectMapper.writeValueAsString(Map.of("template", "prompts/../application.yml")));
		assertTrue(outside.contains("실패: 틀 파일의 경로는 templates/ 로 시작해야 합니다"), outside);
	}

	@Test
	@SuppressWarnings("unchecked")
	void readItemFiles는_항목이_가리키는_파일을_읽어_붙인다() throws Exception {
		Files.writeString(this.workDir.resolve("Board.java"), "class Board {\n}\n", StandardCharsets.UTF_8);
		Map<String, Object> target = new LinkedHashMap<>();
		target.put("path", this.path("Faq.java"));
		target.put("referencePath", this.path("Board.java"));
		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.put("items", java.util.Arrays.asList(target, null));
		arguments.put("read", Map.of("referenceSource", "referencePath", "currentSource", "path"));

		List<Object> rows = (List<Object>) this.call("readItemFiles", arguments);

		assertEquals(1, rows.size());
		Map<String, Object> row = (Map<String, Object>) rows.get(0);
		assertEquals(target, row.get("item"));
		// 공통 파일 읽기가 돌려주는 줄바꿈 방식은 따지지 않습니다.
		assertTrue(row.get("referenceSource").toString().replace("\r", "").startsWith("class Board {\n}"), row.get("referenceSource").toString());
		// 아직 없는 파일(새로 만들 파일)의 내용은 빈 글자입니다.
		assertEquals("", row.get("currentSource"));

		arguments.put("lineNumbers", true);
		rows = (List<Object>) this.call("readItemFiles", arguments);
		assertTrue(((Map<String, Object>) rows.get(0)).get("referenceSource").toString().contains("1: class Board {"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void filterList와_checkData는_JSON_인자로_부를_수_있다() throws Exception {
		Files.writeString(this.workDir.resolve("menu.sql"), "x\n", StandardCharsets.UTF_8);
		List<Object> targets = List.of(Map.of("path", this.path("menu.sql"), "changeType", "수정"), Map.of("path", this.path("Faq.java"), "changeType", "수정"));

		Map<String, Object> filter = new LinkedHashMap<>();
		filter.put("list", targets);
		filter.put("where", Map.of("changeType", "수정"));
		filter.put("pick", "path");
		assertEquals(List.of(this.path("menu.sql"), this.path("Faq.java")), this.call("filterList", filter));

		Map<String, Object> rule = new LinkedHashMap<>();
		rule.put("rule", "filesExist");
		rule.put("list", targets);
		rule.put("where", Map.of("changeType", "수정"));
		rule.put("field", "path");
		rule.put("message", "없는 파일입니다");
		Map<String, Object> check = (Map<String, Object>) this.call("checkData", Map.of("rules", List.of(rule)));
		assertEquals(false, check.get("ok"));
		assertEquals("- 없는 파일입니다: " + this.path("Faq.java"), check.get("feedback"));

		Map<String, Object> wrong = (Map<String, Object>) this.call("checkData", Map.of("rules", List.of(Map.of("rule", "sameAs"))));
		assertEquals(false, wrong.get("success"));
	}

	@Test
	void editFileTexts는_모두_맞을_때만_한꺼번에_고친다() throws Exception {
		Path file = this.workDir.resolve("create.sql");
		Files.writeString(file, "CREATE TABLE A (ID INT);\r\nCREATE TABLE B (ID INT);\r\n", StandardCharsets.UTF_8);
		Map<String, Object> replace = Map.of("before", "CREATE TABLE A (ID INT);\nCREATE TABLE B", "after", "CREATE TABLE A (ID INT);\nCREATE TABLE FAQ");
		Map<String, Object> append = Map.of("append", true, "after", "-- end");
		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.put("filePath", this.path("create.sql"));

		// 하나라도 맞지 않으면 아무것도 바꾸지 않습니다.
		arguments.put("edits", List.of(replace, Map.of("before", "CREATE TABLE", "after", "x"), Map.of("before", "NOPE", "after", "y")));
		String failed = this.tools.get("editFileTexts").call(this.objectMapper.writeValueAsString(arguments));
		assertTrue(failed.contains("실패: 아무것도 바꾸지 않았습니다"), failed);
		assertTrue(failed.contains("2번째 변경: before가 파일에 2번") && failed.contains("3번째 변경: before가 파일에 0번"), failed);
		assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("CREATE TABLE B"));

		// dryRun이면 고칠 수 있다고만 답하고 파일은 그대로입니다.
		arguments.put("edits", List.of(replace, append));
		arguments.put("dryRun", true);
		String dryRun = this.tools.get("editFileTexts").call(this.objectMapper.writeValueAsString(arguments));
		assertTrue(dryRun.contains("고칠 수 있습니다(2곳)"), dryRun);
		assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("CREATE TABLE B"));

		// 줄바꿈 방식(CRLF)은 파일을 따릅니다.
		arguments.put("dryRun", false);
		String edited = this.tools.get("editFileTexts").call(this.objectMapper.writeValueAsString(arguments));
		assertTrue(edited.contains("고쳤습니다(2곳)"), edited);
		assertEquals("CREATE TABLE A (ID INT);\r\nCREATE TABLE FAQ (ID INT);\r\n\r\n-- end", Files.readString(file, StandardCharsets.UTF_8));

		// 바꿀 것이 없는 항목은 그냥 지나갑니다(forEach 목록에 섞여 있어도 실패가 아닙니다).
		arguments.put("edits", List.of());
		assertTrue(this.tools.get("editFileTexts").call(this.objectMapper.writeValueAsString(arguments)).contains("바꿀 것이 없습니다"));
	}

}

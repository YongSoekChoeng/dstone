package net.dstone.ai.tools.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * <pre>
 * readFiles가 여러 문서를 TOOL step 하나로 읽어 이름으로 꺼내게 해 주는지 봅니다.
 * readFile step 여러 개를 합칠 때 예전 동작(없으면 실패, maxChars를 넘으면 자르지 않고 실패)이 그대로여야 합니다.
 * </pre>
 */
public class FileSetToolReadFilesTest {

	@TempDir
	Path dir;

	private final FileSetTool tool = new FileSetTool();

	@BeforeEach
	void setUp() {
		FileTool fileTool = new FileTool();
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("dstone.ai.tool.file.max-read-chars", "10");
		ReflectionTestUtils.setField(fileTool, "environment", environment);
		ReflectionTestUtils.setField(this.tool, "fileTool", fileTool);
	}

	private Map<String, Object> item(String path, String name, Integer maxChars, boolean required) {
		Map<String, Object> item = new LinkedHashMap<String, Object>();
		item.put("path", path);
		item.put("name", name);
		if (maxChars != null) {
			item.put("maxChars", maxChars);
		}
		item.put("required", Boolean.valueOf(required));
		return item;
	}

	@Test
	void 이름을_주면_그_이름으로도_내용을_둔다() throws IOException {
		Path design = Files.writeString(this.dir.resolve("design.md"), "0123456789ABCDEFGHIJ", StandardCharsets.UTF_8);
		Path taskitems = Files.writeString(this.dir.resolve("taskitems.md"), "목록", StandardCharsets.UTF_8);

		Map<String, Object> result = this.tool.readFiles(List.<Object>of(
			this.item(design.toString(), "design", Integer.valueOf(60000), true),
			this.item(taskitems.toString(), "taskitems", Integer.valueOf(60000), true)));

		// maxChars를 주면 설정값(10자)에서 자르지 않고 통째로 읽는다
		assertThat(result.get("design")).isEqualTo("0123456789ABCDEFGHIJ");
		assertThat(result.get("taskitems")).isEqualTo("목록");
		assertThat((Map<?, ?>) result.get("files")).hasSize(2);
	}

	@Test
	void required인_파일이_없으면_실패하고_아니면_missing에_적는다() {
		String none = this.dir.resolve("none.md").toString();

		Map<String, Object> failed = this.tool.readFiles(List.<Object>of(this.item(none, "design", null, true)));
		assertThat(failed.get("success")).isEqualTo(Boolean.FALSE);
		assertThat(String.valueOf(failed.get("message"))).contains("꼭 있어야 하는 파일이 없습니다");

		Map<String, Object> skipped = this.tool.readFiles(List.<Object>of(this.item(none, "design", null, false)));
		assertThat(String.valueOf(skipped.get("missing"))).isEqualTo("[" + none + "]");
		assertThat(skipped).doesNotContainKey("design");
	}

	@Test
	void maxChars를_넘으면_자르지_않고_실패한다() throws IOException {
		Path design = Files.writeString(this.dir.resolve("design.md"), "0123456789ABCDEFGHIJ", StandardCharsets.UTF_8);

		Map<String, Object> result = this.tool.readFiles(List.<Object>of(this.item(design.toString(), "design", Integer.valueOf(5), true)));

		assertThat(result.get("success")).isEqualTo(Boolean.FALSE);
	}

	@Test
	void 결과에_이미_있는_키는_이름으로_쓸_수_없다() throws IOException {
		Path design = Files.writeString(this.dir.resolve("design.md"), "설계", StandardCharsets.UTF_8);

		Map<String, Object> result = this.tool.readFiles(List.<Object>of(this.item(design.toString(), "text", null, false)));

		assertThat(result.get("success")).isEqualTo(Boolean.FALSE);
	}

}

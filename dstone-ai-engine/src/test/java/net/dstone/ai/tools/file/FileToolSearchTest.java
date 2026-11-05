package net.dstone.ai.tools.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * <pre>
 * searchInFiles의 basePath에 폴더와 파일을 줬을 때를 서버 없이 확인합니다.
 * 파일 경로를 주면 그 파일 하나에서만 찾습니다(sm-workflow step06에서 파일 경로를 줬다가 "디렉토리를 찾을 수 없습니다"를 받고
 * 폴더로 다시 부르느라 LLM 호출이 한 번 더 든 일이 있었습니다).
 * </pre>
 */
class FileToolSearchTest {

	@TempDir
	Path dir;

	private final FileTool tool = new FileTool();

	@BeforeEach
	void setUp() throws Exception {
		ReflectionTestUtils.setField(this.tool, "environment", new MockEnvironment());
		Files.writeString(this.dir.resolve("FileService.java"), "class FileService {\n  void imageSave() {}\n}\n", StandardCharsets.UTF_8);
		Files.writeString(this.dir.resolve("Other.java"), "class Other {\n  void imageSave() {}\n}\n", StandardCharsets.UTF_8);
	}

	@Test
	void 파일_경로를_주면_그_파일에서만_찾는다() {
		Map<String, Object> result = this.tool.searchInFiles(this.dir.resolve("FileService.java").toString(), "imageSave", "*.xml");

		List<?> matches = (List<?>) result.get("matches");
		assertThat(matches).hasSize(1);
		assertThat(String.valueOf(matches.get(0))).contains("FileService.java:2:");
	}

	@Test
	void 폴더를_주면_아래의_파일을_모두_찾는다() {
		Map<String, Object> result = this.tool.searchInFiles(this.dir.toString(), "imageSave", null);

		assertThat((List<?>) result.get("matches")).hasSize(2);
	}

	@Test
	void 없는_경로는_안내한다() {
		Map<String, Object> result = this.tool.searchInFiles(this.dir.resolve("none").toString(), "imageSave", null);

		assertThat(String.valueOf(result.get("message"))).startsWith("디렉토리나 파일을 찾을 수 없습니다");
	}

}

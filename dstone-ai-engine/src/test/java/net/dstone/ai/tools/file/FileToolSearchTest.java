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
 * 키워드를 |로 여러 개 주면 그중 하나라도 있는 줄을 찾고, contextLines를 주면 앞뒤 줄을 함께 줍니다
 * (2026-11-20 실행 9294e45d: 같은 파일을 키워드마다 따로 검색하느라 Tool 호출을 다 썼습니다).
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
		Map<String, Object> result = this.tool.searchInFiles(this.dir.resolve("FileService.java").toString(), "imageSave", "*.xml", null);

		List<?> matches = (List<?>) result.get("matches");
		assertThat(matches).hasSize(1);
		assertThat(String.valueOf(matches.get(0))).contains("FileService.java:2:");
	}

	@Test
	void 폴더를_주면_아래의_파일을_모두_찾는다() {
		Map<String, Object> result = this.tool.searchInFiles(this.dir.toString(), "imageSave", null, null);

		assertThat((List<?>) result.get("matches")).hasSize(2);
	}

	@Test
	void 없는_경로는_안내한다() {
		Map<String, Object> result = this.tool.searchInFiles(this.dir.resolve("none").toString(), "imageSave", null, null);

		assertThat(String.valueOf(result.get("message"))).startsWith("디렉토리나 파일을 찾을 수 없습니다");
	}

	@Test
	void 키워드를_여러_개_주면_그중_하나라도_있는_줄을_찾는다() throws Exception {
		Files.writeString(this.dir.resolve("FileVO.java"), "class FileVO {\n  String thumbnail;\n  String path;\n}\n", StandardCharsets.UTF_8);

		Map<String, Object> result = this.tool.searchInFiles(this.dir.toString(), "imageSave | THUMBNAIL", "*.java", null);

		assertThat((List<?>) result.get("matches")).hasSize(3);
	}

	@Test
	void contextLines를_주면_찾은_줄의_앞뒤를_함께_준다() {
		Map<String, Object> result = this.tool.searchInFiles(this.dir.resolve("FileService.java").toString(), "imageSave", null, Integer.valueOf(1));

		String block = String.valueOf(((List<?>) result.get("matches")).get(0));
		assertThat(block).contains("FileService.java:2").contains("  1| class FileService {").contains("> 2|   void imageSave() {}").contains("  3| }");
	}

}

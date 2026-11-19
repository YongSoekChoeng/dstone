package net.dstone.ai.tools.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * <pre>
 * readFile의 maxChars를 서버 없이 확인합니다.
 * sm-workflow의 TOOL step이 34000자짜리 설계서를 기본 상한 30000자에서 잘라 개발 Agent에 넘겼고,
 * Agent가 없는 부분을 지어내려다 출력 한도를 다 쓴 일(2026-11-19)이 있어서 TOOL step이 상한을 정할 수 있게 했습니다.
 * </pre>
 */
class FileToolReadTest {

	@TempDir
	Path dir;

	private final FileTool tool = new FileTool();

	private Path file;

	@BeforeEach
	void setUp() throws Exception {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("dstone.ai.tool.file.max-read-chars", "10");
		ReflectionTestUtils.setField(this.tool, "environment", environment);
		this.file = this.dir.resolve("design.md");
		Files.writeString(this.file, "0123456789ABCDEFGHIJ", StandardCharsets.UTF_8);
	}

	@Test
	void maxChars를_비우면_설정값에서_자른다() {
		String result = this.tool.readFile(this.file.toString(), null);

		assertThat(result).startsWith("0123456789\n").contains("전체 20자 중 앞 10자만");
	}

	@Test
	void maxChars를_주면_그만큼_읽는다() {
		assertThat(this.tool.readFile(this.file.toString(), Integer.valueOf(100))).isEqualTo("0123456789ABCDEFGHIJ");
		assertThat(this.tool.readFile(this.file.toString(), Integer.valueOf(15))).startsWith("0123456789ABCDE\n").contains("앞 15자만");
	}

	@Test
	void 마크다운_제목_아래_내용만_꺼낸다() throws Exception {
		Path doc = this.dir.resolve("request.md");
		Files.writeString(doc, "# 요청서\n## 현업요청\n게시판\n### 상세\n목록\n## 소스경로\n\n\tD:/AppHome/testApp  \n\n## 끝\n", StandardCharsets.UTF_8);

		assertThat(this.tool.readMarkdownSection(doc.toString(), "소스경로")).isEqualTo("D:/AppHome/testApp");
		// 낮은 수준의 하위 제목은 내용에 들어가고, 같은 수준의 다음 제목에서 끝난다
		assertThat(this.tool.readMarkdownSection(doc.toString(), " 현업요청 ")).isEqualTo("게시판\n### 상세\n목록");
		assertThat(this.tool.readMarkdownSection(doc.toString(), "없는제목")).startsWith("실패:");
		assertThat(this.tool.readMarkdownSection(doc.toString(), "끝")).startsWith("실패:");
	}

	@Test
	void 인자_하나짜리는_설정값을_쓴다() {
		assertThat(this.tool.readFile(this.file.toString())).contains("앞 10자만");
	}

}

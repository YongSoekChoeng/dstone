package net.dstone.ai.runtime.workflow.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tool 이름과 인자만 보고 읽은 파일과 쓴 파일을 제대로 가려내는지 봅니다. 서버 없이 돌아갑니다.
 */
public class FileAccessRuleTest {

	@Test
	void 쓰기_Tool은_WRITE로_기록한다() {
		List<FileAccessRule.FileAccess> accesses = FileAccessRule.of("writeFile", "{\"filePath\": \" D:\\\\work\\\\01.md \", \"fileContents\": \"x\"}");
		assertThat(accesses).containsExactly(new FileAccessRule.FileAccess("D:/work/01.md", FileAccessRule.WRITE));
	}

	@Test
	void 읽기_Tool은_READ로_기록한다() {
		assertThat(FileAccessRule.of("readFileLines", "{\"fileFullPath\": \"/a/b.md\", \"startLine\": 3}"))
			.containsExactly(new FileAccessRule.FileAccess("/a/b.md", FileAccessRule.READ));
		assertThat(FileAccessRule.of("readFiles", "{\"paths\": [\"/a/1.md\", {\"path\": \"/a/2.md\"}, \"/a/1.md\"]}"))
			.containsExactly(new FileAccessRule.FileAccess("/a/1.md", FileAccessRule.READ), new FileAccessRule.FileAccess("/a/2.md", FileAccessRule.READ));
	}

	@Test
	void 복사는_원본_READ와_대상_WRITE를_함께_남긴다() {
		assertThat(FileAccessRule.of("copyFileWithReplace", "{\"sourcePath\": \"/s.java\", \"targetPath\": \"/t.java\"}"))
			.containsExactly(new FileAccessRule.FileAccess("/s.java", FileAccessRule.READ), new FileAccessRule.FileAccess("/t.java", FileAccessRule.WRITE));
	}

	@Test
	void dryRun은_파일을_바꾸지_않으므로_남기지_않는다() {
		assertThat(FileAccessRule.of("editFileTexts", "{\"filePath\": \"/a.md\", \"edits\": [], \"dryRun\": true}")).isEmpty();
		assertThat(FileAccessRule.of("editFileTexts", "{\"filePath\": \"/a.md\", \"edits\": []}")).hasSize(1);
	}

	@Test
	void 표에_없는_Tool이나_깨진_인자는_무시한다() {
		assertThat(FileAccessRule.of("getCurrentDateTime", "{}")).isEmpty();
		assertThat(FileAccessRule.of("writeFile", "{\"filePath\": \"/a.md\"")).isEmpty();
		assertThat(FileAccessRule.of("writeFile", "{\"filePath\": \"  \"}")).isEmpty();
	}

	@Test
	void 실패_답은_실패로_본다() {
		assertThat(FileAccessRule.isFailure("\"실패: 파일을 읽을 수 없습니다\"")).isTrue();
		assertThat(FileAccessRule.isFailure("실패: oldText가 없습니다")).isTrue();
		assertThat(FileAccessRule.isFailure("\"저장했습니다: /a.md\"")).isFalse();
		assertThat(FileAccessRule.isFailure(null)).isFalse();
	}

}

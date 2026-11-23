package net.dstone.ai.tools.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <pre>
 * fileChanges가 지난 스냅샷 뒤로 바뀐 파일(새 파일, 내용이 바뀐 파일, 지운 파일)만 알려 주는지 봅니다.
 * 내용이 같으면 다시 써도 바뀐 것으로 보지 않고, 뺄 폴더는 보지 않습니다.
 * </pre>
 */
public class FileSetToolChangesTest {

	@TempDir
	Path dir;

	@Test
	@SuppressWarnings("unchecked")
	void 지난_스냅샷_뒤로_바뀐_파일만_알려_준다() throws IOException {
		FileSetTool tool = new FileSetTool();
		Files.createDirectories(this.dir.resolve("dev/src"));
		Files.createDirectories(this.dir.resolve("compile-check"));
		Files.writeString(this.dir.resolve("dev/src/A.java"), "class A {}", StandardCharsets.UTF_8);
		Files.writeString(this.dir.resolve("dev/src/B.java"), "class B {}", StandardCharsets.UTF_8);
		Files.writeString(this.dir.resolve("compile-check/A.class"), "1", StandardCharsets.UTF_8);

		Map<String, Object> first = tool.fileChanges(this.dir.toString(), null, "compile-check");
		assertThat(first.get("first")).isEqualTo(Boolean.TRUE);
		assertThat((Map<String, String>) first.get("snapshot")).containsOnlyKeys("dev/src/A.java", "dev/src/B.java");

		Files.writeString(this.dir.resolve("dev/src/A.java"), "class A { int x; }", StandardCharsets.UTF_8);
		Files.writeString(this.dir.resolve("dev/src/B.java"), "class B {}", StandardCharsets.UTF_8); // 같은 내용으로 다시 씀
		Files.writeString(this.dir.resolve("dev/src/C.java"), "class C {}", StandardCharsets.UTF_8);
		Files.writeString(this.dir.resolve("compile-check/A.class"), "2", StandardCharsets.UTF_8);
		Files.delete(this.dir.resolve("dev/src/B.java"));
		Files.writeString(this.dir.resolve("dev/src/B2.java"), "class B2 {}", StandardCharsets.UTF_8);

		Map<String, Object> second = tool.fileChanges(this.dir.toString(), first.get("snapshot"), "compile-check");
		assertThat(second.get("first")).isEqualTo(Boolean.FALSE);
		assertThat((List<String>) second.get("changed")).containsExactlyInAnyOrder("dev/src/A.java", "dev/src/B2.java", "dev/src/C.java", "(삭제) dev/src/B.java");
		assertThat(second.get("summary").toString()).contains("바뀐 파일 4개");

		Map<String, Object> third = tool.fileChanges(this.dir.toString(), second.get("snapshot"), "compile-check");
		assertThat((List<String>) third.get("changed")).isEmpty();
		assertThat(third.get("summary").toString()).contains("바뀐 파일이 없습니다");
	}

	@Test
	void 없는_폴더는_실패다() {
		Map<String, Object> result = new FileSetTool().fileChanges(this.dir.resolve("none").toString(), null, null);
		assertThat(result.get("success")).isEqualTo(Boolean.FALSE);
	}

}

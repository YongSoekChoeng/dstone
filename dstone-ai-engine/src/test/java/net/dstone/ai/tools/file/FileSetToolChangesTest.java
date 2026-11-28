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
		assertThat(first.get("route")).isEqualTo("first");
		assertThat((Map<String, String>) first.get("snapshot")).containsOnlyKeys("dev/src/A.java", "dev/src/B.java");

		Files.writeString(this.dir.resolve("dev/src/A.java"), "class A { int x; }", StandardCharsets.UTF_8);
		Files.writeString(this.dir.resolve("dev/src/B.java"), "class B {}", StandardCharsets.UTF_8); // 같은 내용으로 다시 씀
		Files.writeString(this.dir.resolve("dev/src/C.java"), "class C {}", StandardCharsets.UTF_8);
		Files.writeString(this.dir.resolve("compile-check/A.class"), "2", StandardCharsets.UTF_8);
		Files.delete(this.dir.resolve("dev/src/B.java"));
		Files.writeString(this.dir.resolve("dev/src/B2.java"), "class B2 {}", StandardCharsets.UTF_8);

		Map<String, Object> second = tool.fileChanges(this.dir.toString(), first.get("snapshot"), "compile-check");
		assertThat(second.get("first")).isEqualTo(Boolean.FALSE);
		assertThat(second.get("route")).isEqualTo("changed");
		assertThat((List<String>) second.get("changed")).containsExactlyInAnyOrder("dev/src/A.java", "dev/src/B2.java", "dev/src/C.java", "(삭제) dev/src/B.java");
		assertThat(second.get("summary").toString()).contains("바뀐 파일 4개");

		Map<String, Object> third = tool.fileChanges(this.dir.toString(), second.get("snapshot"), "compile-check");
		assertThat((List<String>) third.get("changed")).isEmpty();
		assertThat(third.get("route")).isEqualTo("unchanged");
		assertThat(third.get("summary").toString()).contains("바뀐 파일이 없습니다");
	}

	@Test
	void copyDir을_주면_지난번과_달라진_줄을_diff로_알려_준다() throws IOException {
		FileSetTool tool = new FileSetTool();
		Path work = this.dir.resolve("work");
		Path copy = this.dir.resolve("copy");
		Files.createDirectories(work.resolve("dev"));
		StringBuilder jsp = new StringBuilder();
		for (int i = 1; i <= 20; i++) {
			jsp.append("line").append(i).append("\r\n");
		}
		String before = jsp.toString().replace("line10", "var rooms = roomRes[0].results;");
		Files.writeString(work.resolve("dev/meetingRoom.jsp"), before, StandardCharsets.UTF_8);
		Files.writeString(work.resolve("dev/Old.java"), "class Old {}", StandardCharsets.UTF_8);

		Map<String, Object> first = tool.fileChanges(work.toString(), null, null, copy.toString());
		assertThat(first).doesNotContainKey("diff");
		assertThat(copy.resolve("dev/meetingRoom.jsp")).exists();

		Files.writeString(work.resolve("dev/meetingRoom.jsp"), before.replace("roomRes[0].results", "roomRes.results"), StandardCharsets.UTF_8);
		Files.delete(work.resolve("dev/Old.java"));
		Files.writeString(work.resolve("dev/New.java"), "class New {}\n", StandardCharsets.UTF_8);

		Map<String, Object> second = tool.fileChanges(work.toString(), first.get("snapshot"), null, copy.toString());
		String diff = String.valueOf(second.get("diff"));
		assertThat(diff).contains("===== dev/meetingRoom.jsp\n@@ -8,5 +8,5 @@\n line8\n line9\n-var rooms = roomRes[0].results;\n+var rooms = roomRes.results;\n line11\n line12\n");
		assertThat(diff).doesNotContain("line5").doesNotContain("line14");
		assertThat(diff).contains("===== dev/Old.java\n(이 파일이 없어졌습니다)");
		assertThat(diff).contains("===== dev/New.java\n(새 파일이거나 지난 내용이 없습니다)\n@@ -1,0 +1,1 @@\n+class New {}");
		// 사본은 지금 내용으로 바뀌어, 다음 호출은 그 뒤의 변화만 본다
		Map<String, Object> third = tool.fileChanges(work.toString(), second.get("snapshot"), null, copy.toString());
		assertThat(String.valueOf(third.get("diff"))).isEmpty();
	}

	@Test
	void copyDir이_basePath_안에_있으면_스냅샷에서_뺀다() throws IOException {
		FileSetTool tool = new FileSetTool();
		Files.writeString(this.dir.resolve("A.txt"), "a", StandardCharsets.UTF_8);
		Map<String, Object> first = tool.fileChanges(this.dir.toString(), null, null, this.dir.resolve(".copy").toString());
		Map<String, Object> second = tool.fileChanges(this.dir.toString(), first.get("snapshot"), null, this.dir.resolve(".copy").toString());
		assertThat((List<String>) second.get("changed")).isEmpty();
	}

	@Test
	void 없는_폴더는_실패다() {
		Map<String, Object> result = new FileSetTool().fileChanges(this.dir.resolve("none").toString(), null, null);
		assertThat(result.get("success")).isEqualTo(Boolean.FALSE);
	}

}

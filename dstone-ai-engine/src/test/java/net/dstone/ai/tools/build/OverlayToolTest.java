package net.dstone.ai.tools.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.runtime.tool.ToolOutcome;

/**
 * <pre>
 * applyOverlay가 원본은 그대로 두고, 원본 + 개발 결과 - 삭제 목록인 시험용 사본을 만드는지 봅니다.
 * </pre>
 */
class OverlayToolTest {

	@TempDir
	Path dir;

	private final OverlayTool tool = new OverlayTool();

	private static void write(Path file, String text) throws Exception {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	@Test
	void 원본에_개발_결과를_얹고_삭제_목록을_지운다() throws Exception {
		Path source = this.dir.resolve("app");
		Path overlay = this.dir.resolve("work/dev-resources");
		Path target = this.dir.resolve("stage");
		write(source.resolve("src/A.java"), "원본 A");
		write(source.resolve("src/Old.java"), "지울 파일");
		write(source.resolve("target/classes/A.class"), "빌드 결과");
		write(overlay.resolve("src/A.java"), "고친 A");
		write(overlay.resolve("src/New.java"), "새 파일");
		write(overlay.resolve("_delete-files.txt"), "src/Old.java\n");

		ToolOutcome outcome = this.tool.applyOverlay(source.toString(), overlay.toString(), target.toString(), overlay.resolve("_delete-files.txt").toString(), null);

		assertThat(outcome.success()).as(outcome.message()).isTrue();
		assertThat(Files.readString(target.resolve("src/A.java"), StandardCharsets.UTF_8)).isEqualTo("고친 A");
		assertThat(target.resolve("src/New.java")).exists();
		assertThat(target.resolve("src/Old.java")).doesNotExist();
		// 삭제 목록 파일 자체와 빌드 결과 폴더는 사본에 들어가지 않습니다.
		assertThat(target.resolve("_delete-files.txt")).doesNotExist();
		assertThat(target.resolve("target")).doesNotExist();
		// 원본은 그대로입니다.
		assertThat(Files.readString(source.resolve("src/A.java"), StandardCharsets.UTF_8)).isEqualTo("원본 A");
		assertThat(source.resolve("src/Old.java")).exists();
	}

	@Test
	void 다시_부르면_개발_결과에서_빠진_파일을_사본에서도_지운다() throws Exception {
		Path source = this.dir.resolve("app");
		Path overlay = this.dir.resolve("dev");
		Path target = this.dir.resolve("stage");
		write(source.resolve("a.txt"), "a");
		write(overlay.resolve("b.txt"), "b");
		this.tool.applyOverlay(source.toString(), overlay.toString(), target.toString(), null, null);
		Files.delete(overlay.resolve("b.txt"));
		write(target.resolve("target/keep.class"), "사본에서 만든 빌드 결과");

		ToolOutcome outcome = this.tool.applyOverlay(source.toString(), overlay.toString(), target.toString(), null, List.of("target"));

		assertThat(outcome.success()).isTrue();
		assertThat(target.resolve("b.txt")).doesNotExist();
		assertThat(target.resolve("a.txt")).exists();
		assertThat(target.resolve("target/keep.class")).exists();
	}

	@Test
	void 사본_폴더가_원본과_겹치면_아무것도_하지_않는다() throws Exception {
		Path source = this.dir.resolve("app");
		write(source.resolve("a.txt"), "a");

		ToolOutcome inside = this.tool.applyOverlay(source.toString(), this.dir.resolve("dev").toString(), source.resolve("stage").toString(), null, null);
		ToolOutcome same = this.tool.applyOverlay(source.toString(), this.dir.resolve("dev").toString(), source.toString(), null, null);

		assertThat(inside.success()).isFalse();
		assertThat(same.success()).isFalse();
		assertThat(source.resolve("stage")).doesNotExist();
	}
}

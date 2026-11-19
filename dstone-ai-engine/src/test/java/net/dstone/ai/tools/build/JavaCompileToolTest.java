package net.dstone.ai.tools.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.runtime.tool.ToolOutcome;

/**
 * <pre>
 * compileJava가 빌드 도구 없이(Eclipse .classpath, WEB-INF 구조) 바뀐 파일만 원본 클래스를 참조해 컴파일하는지 확인합니다.
 * 원본은 테스트가 javac로 미리 컴파일해 둡니다(실제 프로젝트에서는 개발 도구나 빌드가 만들어 둔 클래스 폴더).
 * </pre>
 */
class JavaCompileToolTest {

	@TempDir
	Path dir;

	private final JavaCompileTool tool = new JavaCompileTool();

	private Path root;
	private Path overlay;
	private Path output;

	@BeforeEach
	void setUp() throws IOException {
		this.root = this.dir.resolve("app");
		this.overlay = this.dir.resolve("work/dev");
		this.output = this.dir.resolve("work/check");
		// Eclipse 웹 프로젝트 모양: 소스 src, 클래스 WebContent/WEB-INF/classes
		write(this.root.resolve("src/demo/Old.java"), "package demo;\npublic class Old { public String name() { return \"old\"; } }\n");
		write(this.root.resolve("src/demo/User.java"), "package demo;\npublic class User { String n = new Old().name(); }\n");
		write(this.root.resolve(".classpath"), "<classpath>\n<classpathentry kind=\"src\" path=\"src\"/>\n"
			+ "<classpathentry kind=\"output\" path=\"WebContent/WEB-INF/classes\"/>\n</classpath>\n");
		compileOriginal(this.root.resolve("WebContent/WEB-INF/classes"), this.root.resolve("src/demo/Old.java"), this.root.resolve("src/demo/User.java"));
	}

	@Test
	void 새_파일이_원본_클래스를_쓰면_통과한다() throws IOException {
		write(this.overlay.resolve("src/demo/New.java"), "package demo;\npublic class New { String n = new Old().name(); }\n");

		ToolOutcome outcome = this.compile(null);

		assertThat(outcome.success()).isTrue();
		assertThat(outcome.message()).contains("바뀐 파일 1개").contains("Eclipse");
	}

	@Test
	void 없는_클래스를_import하면_파일과_줄을_알려_준다() throws IOException {
		write(this.overlay.resolve("src/demo/New.java"), "package demo;\nimport demo.missing.UPLOADS;\npublic class New {}\n");

		ToolOutcome outcome = this.compile(null);

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.message()).contains("src/demo/New.java:2:").contains("demo.missing");
	}

	@Test
	void 고친_클래스를_쓰는_원본_파일도_함께_컴파일한다() throws IOException {
		// Old.name()을 없애면 원본 User가 깨져야 한다
		write(this.overlay.resolve("src/demo/Old.java"), "package demo;\npublic class Old { public String title() { return \"t\"; } }\n");

		ToolOutcome outcome = this.compile(null);

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.message()).contains("함께 확인한 원본 파일 1개").contains("src/demo/User.java (원본)");
	}

	@Test
	void 삭제_목록의_클래스를_아직_쓰면_알려_준다() throws IOException {
		write(this.overlay.resolve("src/demo/New.java"), "package demo;\npublic class New {}\n");
		Path deleteList = this.dir.resolve("work/delete.txt");
		write(deleteList, "# 지울 파일\nsrc/demo/Old.java\n");

		ToolOutcome outcome = this.compile(deleteList.toString());

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.message()).contains("src/demo/User.java: 삭제 목록의 클래스 demo.Old");
	}

	@Test
	void 원본_클래스가_없으면_빌드하라고_알린다() throws IOException {
		Path other = this.dir.resolve("other");
		write(other.resolve("src/main/java/a/A.java"), "package a; class A {}\n");
		write(this.overlay.resolve("src/main/java/a/B.java"), "package a; class B {}\n");

		ToolOutcome outcome = this.tool.compileJava(other.toString(), this.overlay.toString(), this.output.toString(), null, null, null, null, null, null);

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.message()).contains("클래스 파일").contains("classesDirs");
	}

	private ToolOutcome compile(String deleteList) {
		return this.tool.compileJava(this.root.toString(), this.overlay.toString(), this.output.toString(), deleteList, null, null, null, null, null);
	}

	private static void write(Path path, String text) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, text, StandardCharsets.UTF_8);
	}

	private static void compileOriginal(Path classes, Path... sources) throws IOException {
		Files.createDirectories(classes);
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		List<String> args = new ArrayList<String>();
		args.add("--release");
		args.add("8");
		args.add("-nowarn");
		args.add("-d");
		args.add(classes.toString());
		for (Path source : sources) {
			args.add(source.toString());
		}
		int code = compiler.run(null, null, null, args.toArray(new String[0]));
		assertThat(code).isZero();
		// 원본 소스가 클래스보다 새것이어도 원본 소스를 다시 읽지 않는지 보려고 시각을 맞춘다
		for (Path source : sources) {
			new File(source.toString()).setLastModified(System.currentTimeMillis() + 10000);
		}
	}

}

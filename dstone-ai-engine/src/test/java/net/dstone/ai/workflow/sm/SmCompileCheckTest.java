package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow의 컴파일 확인 step(step16, 범용 Tool compileJava)을 LLM 없이 돌려 봅니다.
 * 작은 Maven 프로젝트를 임시 폴더에 만들고(원본 클래스는 javac로 미리 만들어 둔다), 의존성 경로는 실제 mvn으로 구합니다.
 * mvn이 없는 곳에서는 건너뜁니다.
 * </pre>
 */
public class SmCompileCheckTest extends WorkflowTestBase {

	@TempDir
	Path root;

	private Path source;
	private Path workDir;

	@BeforeEach
	void setUp() throws IOException {
		assumeTrue(mavenAvailable(), "mvn이 없어 컴파일 확인을 건너뜁니다.");
		this.source = this.root.resolve("source");
		Files.createDirectories(this.source.resolve("src/main/java/demo"));
		Files.writeString(this.source.resolve("pom.xml"), String.join("\n",
			"<project xmlns=\"http://maven.apache.org/POM/4.0.0\">",
			"  <modelVersion>4.0.0</modelVersion>",
			"  <groupId>demo</groupId><artifactId>demo</artifactId><version>1</version>",
			"</project>"), StandardCharsets.UTF_8);
		Path old = this.source.resolve("src/main/java/demo/Old.java");
		Files.writeString(old, "package demo;\npublic class Old {}\n", StandardCharsets.UTF_8);
		Files.createDirectories(this.source.resolve("target/classes"));
		int code = ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "8", "-nowarn", "-d", this.source.resolve("target/classes").toString(), old.toString());
		assertThat(code).isZero();
		this.workDir = this.root.resolve("work");
		Files.createDirectories(this.workDir.resolve("dev-resources/src/main/java/demo"));
	}

	@Test
	void 통과하면_compileResult에_남고_다음은_개발_승인이다() throws IOException {
		this.devFile("New.java", "package demo;\npublic class New { Old old = new Old(); }\n");

		WorkflowStepper flow = this.stepper("sm-workflow", "step16", this.args());
		flow.step("step16");

		assertThat(flow.currentStepId()).isEqualTo("step17");
		assertThat(String.valueOf(flow.state("compileResult"))).contains("컴파일 통과").contains("Maven");
		assertThat(flow.state("compileError")).isNull();
	}

	@Test
	void 없는_클래스를_쓰면_오류가_compileError에_남고_개발_승인으로_간다() throws IOException {
		this.devFile("New.java", "package demo;\nimport demo.missing.UPLOADS;\npublic class New {}\n");

		WorkflowStepper flow = this.stepper("sm-workflow", "step16", this.args());
		flow.step("step16");

		assertThat(flow.currentStepId()).isEqualTo("step17");
		assertThat(String.valueOf(flow.state("compileError"))).contains("src/main/java/demo/New.java:2:").contains("demo.missing");
	}

	@Test
	void 삭제_목록의_클래스를_아직_쓰면_알린다() throws IOException {
		this.devFile("New.java", "package demo;\npublic class New { Old old; }\n");
		Files.writeString(this.workDir.resolve("dev-resources/_delete-files.txt"), "src/main/java/demo/Old.java\n", StandardCharsets.UTF_8);

		WorkflowStepper flow = this.stepper("sm-workflow", "step16", this.args());
		flow.step("step16");

		assertThat(String.valueOf(flow.state("compileError"))).contains("src/main/java/demo/New.java: 삭제 목록의 클래스 demo.Old");
		assertThat(this.source.resolve("src/main/java/demo/Old.java")).exists();
	}

	private void devFile(String name, String text) throws IOException {
		Files.writeString(this.workDir.resolve("dev-resources/src/main/java/demo/" + name), text, StandardCharsets.UTF_8);
	}

	/** sm-workflow step16의 with와 같은 값 */
	private Map<String, Object> args() {
		Map<String, Object> input = new LinkedHashMap<String, Object>();
		input.put("sourceRoot", slash(this.source));
		input.put("overlayDir", slash(this.workDir.resolve("dev-resources")));
		input.put("outputDir", slash(this.workDir.resolve("compile-check")));
		input.put("deleteList", slash(this.workDir.resolve("dev-resources/_delete-files.txt")));
		return input;
	}

	private static String slash(Path path) {
		return path.toString().replace("\\", "/");
	}

	private static boolean mavenAvailable() {
		try {
			boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
			Process process = new ProcessBuilder(windows ? List.of("cmd.exe", "/c", "mvn", "-v") : List.of("mvn", "-v")).redirectErrorStream(true).start();
			process.getInputStream().readAllBytes();
			return process.waitFor() == 0;
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}

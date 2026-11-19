package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow의 컴파일 확인 step(step16)을 실제 범용 스크립트(conf/scripts/build/overlay-compile)와 Maven으로 돌려 봅니다(LLM 없음).
 * 작은 Maven 프로젝트를 임시 폴더에 만들고, dev-resources에 넣은 파일로 통과·실패와 삭제 목록을 확인합니다.
 * mvn이 없는 곳에서는 건너뜁니다. 처음 한 번은 Maven 플러그인을 받느라 오래 걸릴 수 있습니다.
 * </pre>
 */
public class SmCompileCheckTest extends WorkflowTestBase {

	@TempDir
	Path root;

	private Path workDir;

	@BeforeEach
	void setUp() throws IOException {
		assumeTrue(mavenAvailable(), "mvn이 없어 컴파일 확인을 건너뜁니다.");
		Path source = this.root.resolve("source");
		Files.createDirectories(source.resolve("src/main/java/demo"));
		Files.writeString(source.resolve("pom.xml"), String.join("\n",
			"<project xmlns=\"http://maven.apache.org/POM/4.0.0\">",
			"  <modelVersion>4.0.0</modelVersion>",
			"  <groupId>demo</groupId><artifactId>demo</artifactId><version>1</version>",
			"  <properties><maven.compiler.release>17</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>",
			"</project>"), StandardCharsets.UTF_8);
		Files.writeString(source.resolve("src/main/java/demo/Old.java"), "package demo;\npublic class Old {}\n", StandardCharsets.UTF_8);
		this.workDir = this.root.resolve("work");
		Files.createDirectories(this.workDir.resolve("dev-resources/src/main/java/demo"));
	}

	@Test
	void 통과하면_compileResult에_남고_다음은_개발_승인이다() throws IOException {
		this.devFile("New.java", "package demo;\npublic class New { Old old = new Old(); }\n");

		WorkflowStepper flow = this.stepper("sm-workflow", "step16", this.args());
		flow.step("step16");

		assertThat(flow.currentStepId()).isEqualTo("step17");
		assertThat(String.valueOf(flow.state("compileResult"))).contains("compile OK");
		assertThat(flow.state("compileError")).isNull();
	}

	@Test
	void 없는_클래스를_쓰면_오류가_compileError에_남고_개발_승인으로_간다() throws IOException {
		this.devFile("New.java", "package demo;\nimport demo.missing.UPLOADS;\npublic class New {}\n");

		WorkflowStepper flow = this.stepper("sm-workflow", "step16", this.args());
		flow.step("step16");

		assertThat(flow.currentStepId()).isEqualTo("step17");
		assertThat(String.valueOf(flow.state("compileError"))).startsWith("실패:")
			.contains("src/main/java/demo/New.java").contains("demo.missing");
	}

	@Test
	void 삭제_목록의_파일은_사본에서_지운다() throws IOException {
		// Old를 지우면 Old를 쓰는 New는 컴파일되지 않아야 한다
		this.devFile("New.java", "package demo;\npublic class New { Old old; }\n");
		Files.writeString(this.workDir.resolve("dev-resources/_delete-files.txt"), "src/main/java/demo/Old.java\n", StandardCharsets.UTF_8);

		WorkflowStepper flow = this.stepper("sm-workflow", "step16", this.args());
		flow.step("step16");

		assertThat(String.valueOf(flow.state("compileError"))).contains("deleted in copy: src/main/java/demo/Old.java").contains("Old");
		assertThat(this.root.resolve("source/src/main/java/demo/Old.java")).exists();
	}

	private void devFile(String name, String text) throws IOException {
		Files.writeString(this.workDir.resolve("dev-resources/src/main/java/demo/" + name), text, StandardCharsets.UTF_8);
	}

	private Map<String, Object> args() {
		Map<String, Object> input = new LinkedHashMap<String, Object>();
		input.put("scriptName", "overlay-compile");
		List<String> args = new ArrayList<String>();
		// sm-workflow step16의 with와 같은 순서: 원본, 변경 폴더, 작업 폴더, 삭제 목록
		args.add(slash(this.root.resolve("source")));
		args.add(slash(this.workDir.resolve("dev-resources")));
		args.add(slash(this.workDir.resolve("compile-check")));
		args.add(slash(this.workDir.resolve("dev-resources/_delete-files.txt")));
		input.put("args", args);
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

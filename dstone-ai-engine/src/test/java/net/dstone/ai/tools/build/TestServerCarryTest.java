package net.dstone.ai.tools.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import net.dstone.ai.tools.build.sandbox.SandboxRegistry;

/**
 * <pre>
 * 임시 시험 서버를 다음 step에 넘겨주고 내리는 규칙을 봅니다(서버를 실제로 띄우지 않음).
 * - 띄운 step이 끝나면 다음 step 하나까지만 둔다
 * - 넘겨받은 step이 끝나면 내린다
 * - 실행이 루프를 벗어나면(끝남, 실패, 승인 대기, 일시정지) 모두 내린다
 * - 다시 쓸지는 입력과 개발 결과 파일 상태로 가린다
 * </pre>
 */
public class TestServerCarryTest {

	@TempDir
	Path dir;

	private TestServerTool tool() {
		TestServerTool tool = new TestServerTool();
		ReflectionTestUtils.setField(tool, "sandboxRegistry", new SandboxRegistry());
		return tool;
	}

	@Test
	void 띄운_step이_끝나도_다음_step_하나까지만_둔다() {
		TestServerTool tool = this.tool();
		tool.track("app", "exec1", "step29");

		tool.stepEnded("exec1", "step29");
		assertThat(tool.isRunning("app")).isTrue();

		tool.stepEnded("exec1", "step35");
		assertThat(tool.isRunning("app")).isFalse();
	}

	@Test
	void 다른_실행의_step이_끝나도_건드리지_않는다() {
		TestServerTool tool = this.tool();
		tool.track("app", "exec1", "step29");
		tool.stepEnded("exec1", "step29");

		tool.stepEnded("exec2", "step35");
		tool.executionLeft("exec2");

		assertThat(tool.isRunning("app")).isTrue();
	}

	@Test
	void 실행이_루프를_벗어나면_넘겨_둔_서버도_내린다() {
		TestServerTool tool = this.tool();
		tool.track("app", "exec1", "step29");
		tool.stepEnded("exec1", "step29");

		tool.executionLeft("exec1");

		assertThat(tool.isRunning("app")).isFalse();
	}

	@Test
	void 입력이나_개발_결과가_바뀌면_다른_값이_된다() throws IOException {
		Path overlay = Files.createDirectories(this.dir.resolve("dev-resources/src"));
		Path file = Files.writeString(overlay.resolve("A.java"), "class A {}", StandardCharsets.UTF_8);
		String base = TestServerTool.fingerprintOf("app", "D:/src", this.dir.resolve("dev-resources").toString(), null, null);

		// 경로 표기(역슬래시, 끝 슬래시, 공백)만 다른 것은 같은 입력이다
		assertThat(TestServerTool.fingerprintOf(" app ", "D:\\src\\", this.dir.resolve("dev-resources") + "/", null, null)).isEqualTo(base);
		assertThat(TestServerTool.fingerprintOf("app", "D:/other", this.dir.resolve("dev-resources").toString(), null, null)).isNotEqualTo(base);

		Files.writeString(file, "class A { int x; }", StandardCharsets.UTF_8);
		assertThat(TestServerTool.fingerprintOf("app", "D:/src", this.dir.resolve("dev-resources").toString(), null, null)).isNotEqualTo(base);
	}

}

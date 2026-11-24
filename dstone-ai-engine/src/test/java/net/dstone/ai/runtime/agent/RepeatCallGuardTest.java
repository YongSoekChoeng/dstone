package net.dstone.ai.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import net.dstone.ai.common.exception.AgentContractException;

/**
 * <pre>
 * 같은 Tool을 같은 인자로 연달아 불러 결과까지 같을 때 안내하고 끊는지 서버 없이 확인합니다(RepeatCallGuardCallback).
 * 기준: 3번째부터 결과 대신 안내, 5번째에 "반복 호출"로 중단. 결과가 바뀌거나 사이에 다른 Tool을 부르면 처음부터 다시 셉니다.
 * </pre>
 */
class RepeatCallGuardTest {

	private static final int NOTICE_AT = 3;
	private static final int FAIL_AT = 5;

	private final RepeatCallGuardCallback.Tracker tracker = new RepeatCallGuardCallback.Tracker();

	@Test
	void 같은_호출_같은_결과면_3번째에_안내하고_5번째에_중단한다() {
		FakeTool skill = new FakeTool("Skill", "본문");
		ToolCallback guarded = this.guarded(skill, false);

		assertThat(guarded.call("{\"command\":\"a\"}")).isEqualTo("본문");
		assertThat(guarded.call("{\"command\":\"a\"}")).isEqualTo("본문");
		assertThat(guarded.call("{\"command\":\"a\"}")).contains("3번 연달아").contains("반복 호출").doesNotContain("본문");
		assertThat(guarded.call("{\"command\":\"a\"}")).contains("4번 연달아");
		assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
			@Override
			public void call() {
				guarded.call("{\"command\":\"a\"}");
			}
		}).isInstanceOf(AgentContractException.class).hasMessageContaining("반복 호출").hasMessageContaining("[Skill]");
		// Tool은 매번 실제로 실행합니다(결과가 바뀌었는지 보려면 실행해야 합니다).
		assertThat(skill.calls).isEqualTo(5);
	}

	@Test
	void 결과가_바뀌면_반복으로_세지_않는다() {
		FakeTool log = new FakeTool("readFileLines", "1줄");
		ToolCallback guarded = this.guarded(log, false);

		for (int i = 0; i < 10; i++) {
			log.result = (i + 1) + "줄";
			assertThat(guarded.call("{\"filePath\":\"server.log\"}")).isEqualTo((i + 1) + "줄");
		}
	}

	@Test
	void 인자가_다르거나_사이에_다른_Tool을_부르면_처음부터_센다() {
		ToolCallback compile = this.guarded(new FakeTool("compileJava", "실패: 오류"), false);
		ToolCallback edit = this.guarded(new FakeTool("editFileTexts", "고쳤습니다"), false);

		for (int i = 0; i < 10; i++) {
			assertThat(compile.call("{}")).isEqualTo("실패: 오류");
			assertThat(edit.call("{\"n\":" + i + "}")).isEqualTo("고쳤습니다");
		}
	}

	@Test
	void 기다리는_용도의_Tool은_세지_않고_셈도_끊는다() {
		ToolCallback poll = this.guarded(new FakeTool("httpGet", "실패: 접속할 수 없습니다"), true);
		ToolCallback skill = this.guarded(new FakeTool("Skill", "본문"), false);

		for (int i = 0; i < 10; i++) {
			assertThat(poll.call("{\"url\":\"http://localhost:9090\"}")).isEqualTo("실패: 접속할 수 없습니다");
		}
		// 기다리는 Tool을 사이에 부르면 "연달아"가 끊깁니다.
		skill.call("{}");
		skill.call("{}");
		poll.call("{}");
		assertThat(skill.call("{}")).isEqualTo("본문");
		assertThat(skill.call("{}")).isEqualTo("본문");
	}

	@Test
	void 횟수가_0이면_끈다() {
		ToolCallback guarded = new RepeatCallGuardCallback(new FakeTool("Skill", "본문"), this.tracker, "test-agent", false, 0, 0);
		for (int i = 0; i < 10; i++) {
			assertThat(guarded.call("{}")).isEqualTo("본문");
		}
	}

	private ToolCallback guarded(ToolCallback tool, boolean exempt) {
		return new RepeatCallGuardCallback(tool, this.tracker, "test-agent", exempt, NOTICE_AT, FAIL_AT);
	}

	/** 부르면 정해 둔 결과를 돌려주고 몇 번 불렸는지 세는 가짜 Tool입니다. */
	private static class FakeTool implements ToolCallback {

		private final ToolDefinition definition;
		private String result;
		private int calls;

		FakeTool(String name, String result) {
			this.definition = ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
			this.result = result;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.definition;
		}

		@Override
		public String call(String toolInput) {
			this.calls++;
			return this.result;
		}
	}

}

package net.dstone.ai.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * <pre>
 * Tool을 부르는 순서(execution.toolPrerequisites)와 성공 기록을 확인합니다.
 * </pre>
 */
class ToolPrerequisiteTest {

	private final Set<String> succeeded = ConcurrentHashMap.newKeySet();
	private final AtomicInteger used = new AtomicInteger();

	@Test
	void 먼저_할_Tool이_성공하기_전에는_실행하지_않는다() {
		FixedTool click = new FixedTool("browser_click", "ok");
		FixedTool query = new FixedTool("dbQuery", "ok");

		assertThat(this.wrap(click).call("{}")).startsWith("실패:");
		assertThat(this.wrap(query).call("{}")).startsWith("실패:");
		this.wrap(new FixedTool("startServer", "{\"success\":false,\"cause\":\"environment\"}")).call("{}");
		assertThat(this.succeeded).doesNotContain("startServer");
		assertThat(this.wrap(click).call("{}")).startsWith("실패:");
		assertThat(click.calls + query.calls).isZero();

		this.wrap(new FixedTool("startServer", "{\"success\":true,\"cause\":\"\"}")).call("{}");
		assertThat(this.wrap(click).call("{}")).isEqualTo("ok");
		assertThat(this.wrap(query).call("{}")).isEqualTo("ok");
	}

	@Test
	void 목록에_없는_Tool은_막지_않는다() {
		assertThat(this.wrap(new FixedTool("readFileLines", "ok")).call("{}")).isEqualTo("ok");
	}

	private ToolCallback wrap(ToolCallback tool) {
		return new ToolCallBudgetCallback(tool, "test-agent", this.used, 100, this.succeeded)
			.prerequisites(Map.of("startServer", List.of("dbQuery", "browser_*")));
	}

	/** 정해 둔 답을 돌려주는 Tool */
	private static final class FixedTool implements ToolCallback {

		private final String name;
		private final String answer;
		private int calls;

		private FixedTool(String name, String answer) {
			this.name = name;
			this.answer = answer;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return ToolDefinition.builder().name(this.name).description("시험").inputSchema("{}").build();
		}

		@Override
		public String call(String toolInput) {
			this.calls++;
			return this.answer;
		}
	}

}

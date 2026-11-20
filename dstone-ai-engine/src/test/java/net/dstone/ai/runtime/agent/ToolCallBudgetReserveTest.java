package net.dstone.ai.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import net.dstone.ai.common.exception.AgentContractException;

/**
 * <pre>
 * 꼭 해야 하는 Tool(requiredTools)을 아직 못 했으면 Tool 호출 한도의 마지막 몇 번을 그 Tool 몫으로 남기는지 확인합니다(ToolCallBudgetCallback).
 * 2026-11-20 실행 9294e45d: 설계 Agent가 남은 횟수 안내를 받고도 한도 50번을 모두 조사에 쓰고 저장(writeFile|editFileTexts)을 하지 못했습니다.
 * </pre>
 */
class ToolCallBudgetReserveTest {

	private static final int MAX_CALLS = 10;

	private final AtomicInteger used = new AtomicInteger();
	private final AtomicInteger refusals = new AtomicInteger();
	private final Set<String> succeeded = ConcurrentHashMap.newKeySet();
	private final List<String> required = List.of("writeFile|editFileTexts");

	private ToolCallback search;
	private ToolCallback edit;

	@BeforeEach
	void setUp() {
		this.search = this.budgeted("searchInFiles");
		this.edit = this.budgeted("editFileTexts");
	}

	@Test
	void 남겨_둔_몫에_들어서면_다른_Tool은_실행하지_않고_저장하라고_한다() {
		int reserve = ToolCallBudgetCallback.reserveFor(MAX_CALLS, this.required);
		assertThat(reserve).isEqualTo(3);

		for (int i = 0; i < MAX_CALLS - reserve; i++) {
			assertThat(this.search.call("{}")).isEqualTo("ok:searchInFiles");
		}
		String refused = this.search.call("{}");

		assertThat(refused).startsWith("실패:").contains("writeFile|editFileTexts").contains("저장");
		assertThat(this.used.get()).isEqualTo(MAX_CALLS - reserve); // 거절한 호출은 횟수를 쓰지 않는다
		assertThat(this.edit.call("{}")).startsWith("ok:editFileTexts"); // 저장 Tool은 그대로 실행된다
	}

	@Test
	void 꼭_해야_하는_Tool을_성공하면_남겨_둔_몫이_풀린다() {
		this.edit.call("{}");
		for (int i = 0; i < MAX_CALLS - 1; i++) {
			assertThat(this.search.call("{}")).startsWith("ok:searchInFiles");
		}
		assertThat(this.used.get()).isEqualTo(MAX_CALLS);
	}

	@Test
	void 거절을_되풀이하면_그_호출을_실패로_끝낸다() {
		for (int i = 0; i < MAX_CALLS - 3; i++) {
			this.search.call("{}");
		}
		for (int i = 0; i < ToolCallBudgetCallback.MAX_REFUSALS; i++) {
			assertThat(this.search.call("{}")).startsWith("실패:");
		}
		final ToolCallback tool = this.search;
		assertThatThrownBy(new ThrowingCallable() {
			@Override
			public void call() {
				tool.call("{}");
			}
		}).isInstanceOf(AgentContractException.class).hasMessageContaining("꼭 해야 하는 Tool");
	}

	@Test
	void 꼭_해야_하는_Tool이_없으면_남겨_두지_않는다() {
		assertThat(ToolCallBudgetCallback.reserveFor(MAX_CALLS, List.of())).isZero();
		ToolCallback plain = new ToolCallBudgetCallback(new FakeTool("searchInFiles", this.succeeded), "test-agent", new AtomicInteger(), MAX_CALLS, this.succeeded);
		for (int i = 0; i < MAX_CALLS; i++) {
			assertThat(plain.call("{}")).startsWith("ok:searchInFiles");
		}
	}

	private ToolCallback budgeted(String name) {
		return new ToolCallBudgetCallback(new FakeTool(name, this.succeeded), "test-agent", this.used, MAX_CALLS, this.succeeded, this.required, this.refusals);
	}

	/** 부르면 "ok:이름"을 돌려주는 가짜 Tool입니다. */
	private static class FakeTool implements ToolCallback {

		private final ToolDefinition definition;

		FakeTool(String name, Set<String> succeeded) {
			this.definition = ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.definition;
		}

		@Override
		public String call(String toolInput) {
			return "ok:" + this.definition.name();
		}
	}

}

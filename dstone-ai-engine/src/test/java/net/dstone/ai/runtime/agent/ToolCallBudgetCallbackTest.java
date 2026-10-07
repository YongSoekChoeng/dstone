package net.dstone.ai.runtime.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import net.dstone.ai.common.exception.AgentContractException;

/**
 * <pre>
 * Tool 호출 횟수를 세고, 얼마 남지 않으면 알려 주고, 다 쓰면 막는지 확인합니다.
 * </pre>
 */
public class ToolCallBudgetCallbackTest {

	@Test
	public void 횟수가_넉넉하면_결과를_그대로_돌려준다() {
		CountingTool tool = new CountingTool("readFile");
		ToolCallback budgeted = new ToolCallBudgetCallback(tool, "test-agent", new AtomicInteger(), 10);

		assertEquals("결과1", budgeted.call("{}"));
		assertEquals(1, tool.calls);
	}

	@Test
	public void 얼마_남지_않으면_결과_끝에_남은_횟수를_알려_준다() {
		CountingTool tool = new CountingTool("readFile");
		ToolCallback budgeted = new ToolCallBudgetCallback(tool, "test-agent", new AtomicInteger(), 10);
		String result = null;
		for (int i = 0; i < 8; i++) {
			result = budgeted.call("{}");
		}

		// 한도 10번이면 2번 남았을 때부터 알린다.
		assertTrue(result.startsWith("결과8"));
		assertTrue(result.contains("2번만 더"));
	}

	@Test
	public void 마지막_호출은_실행하고_이제_부를_수_없다고_알린다() {
		CountingTool tool = new CountingTool("readFile");
		ToolCallback budgeted = new ToolCallBudgetCallback(tool, "test-agent", new AtomicInteger(), 3);
		budgeted.call("{}");
		budgeted.call("{}");

		String last = budgeted.call("{}");

		assertTrue(last.startsWith("결과3"));
		assertTrue(last.contains("모두 썼습니다"));
		assertEquals(3, tool.calls);
	}

	@Test
	public void 횟수를_다_쓰면_Tool을_부르지_않고_실패_안내를_돌려준다() {
		CountingTool tool = new CountingTool("readFile");
		ToolCallback budgeted = new ToolCallBudgetCallback(tool, "test-agent", new AtomicInteger(), 3);
		for (int i = 0; i < 3; i++) {
			budgeted.call("{}");
		}

		String refused = budgeted.call("{}");

		assertTrue(refused.startsWith("실패:"));
		assertEquals(3, tool.calls);
	}

	@Test
	public void 같은_Agent_호출에_붙은_Tool들은_횟수를_함께_쓴다() {
		AtomicInteger used = new AtomicInteger();
		CountingTool read = new CountingTool("readFile");
		CountingTool search = new CountingTool("searchInFiles");
		ToolCallback budgetedRead = new ToolCallBudgetCallback(read, "test-agent", used, 2);
		ToolCallback budgetedSearch = new ToolCallBudgetCallback(search, "test-agent", used, 2);
		budgetedRead.call("{}");
		budgetedSearch.call("{}");

		assertTrue(budgetedRead.call("{}").startsWith("실패:"));
		assertTrue(budgetedSearch.call("{}").startsWith("실패:"));
		assertEquals(1, read.calls);
		assertEquals(1, search.calls);
	}

	@Test
	public void 실패_안내를_세_번_받고도_또_부르면_예외로_끝낸다() {
		CountingTool tool = new CountingTool("readFile");
		final ToolCallback budgeted = new ToolCallBudgetCallback(tool, "test-agent", new AtomicInteger(), 2);
		for (int i = 0; i < 2 + ToolCallBudgetCallback.MAX_REFUSALS; i++) {
			budgeted.call("{}");
		}

		assertThrows(AgentContractException.class, new Executable() {
			@Override
			public void execute() {
				budgeted.call("{}");
			}
		});
		assertEquals(2, tool.calls);
	}

	/** 불린 횟수를 세고 "결과N"을 돌려주는 가짜 Tool입니다. */
	private static final class CountingTool implements ToolCallback {

		private final String name;
		private int calls = 0;

		private CountingTool(String name) {
			this.name = name;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return ToolDefinition.builder().name(this.name).description("테스트용 Tool").inputSchema("{\"type\":\"object\"}").build();
		}

		@Override
		public String call(String toolInput) {
			this.calls++;
			return "결과" + this.calls;
		}

	}

}

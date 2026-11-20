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
 * 같은 Tool을 같은 인자로 다시 불러 또 실패할 호출을 막는지 확인합니다(RepeatFailureGuardCallback, 서버 없음).
 * 2026-11-20 평가: 문서 수정 Agent가 editFileTexts를 같은 인자로 31번 불렀습니다.
 * 함께, 따옴표로 감싼 실패("실패: …")를 성공으로 세지 않는지도 봅니다(ToolCallBudgetCallback).
 * </pre>
 */
class RepeatFailureGuardTest {

	private final Map<String, String> failures = new ConcurrentHashMap<String, String>();

	@Test
	void 같은_인자로_다시_부르면_실행하지_않고_지난_실패를_알려_준다() {
		FakeTool edit = new FakeTool("editFileTexts", "\"실패: 아무것도 바꾸지 않았습니다. 3번째 변경: before가 파일에 0번 나옵니다\"");
		ToolCallback guarded = new RepeatFailureGuardCallback(edit, this.failures, "test-agent");
		String args = "{\"filePath\": \"/w/03-1.design.md\", \"edits\": []}";

		guarded.call(args);
		String second = guarded.call(args);

		assertThat(edit.calls).isEqualTo(1);
		assertThat(second).startsWith("실패: 같은 인자로 이미 실패한 editFileTexts").contains("3번째 변경");
	}

	@Test
	void 인자가_다르면_그대로_실행한다() {
		FakeTool edit = new FakeTool("editFileTexts", "\"실패: 없음\"");
		ToolCallback guarded = new RepeatFailureGuardCallback(edit, this.failures, "test-agent");

		guarded.call("{\"filePath\": \"/w/a.md\", \"edits\": [1]}");
		guarded.call("{\"filePath\": \"/w/a.md\", \"edits\": [2]}");

		assertThat(edit.calls).isEqualTo(2);
	}

	@Test
	void 파일_쓰기에_성공하면_기억을_비운다() {
		FakeTool edit = new FakeTool("editFileTexts", "\"실패: 없음\"");
		FakeTool write = new FakeTool("writeFile", "\"저장했습니다\"");
		ToolCallback guardedEdit = new RepeatFailureGuardCallback(edit, this.failures, "test-agent");
		ToolCallback guardedWrite = new RepeatFailureGuardCallback(write, this.failures, "test-agent");
		String args = "{\"filePath\": \"/w/a.md\", \"edits\": []}";

		guardedEdit.call(args);
		guardedWrite.call("{\"filePath\": \"/w/a.md\", \"fileContents\": \"x\"}");
		guardedEdit.call(args);

		assertThat(edit.calls).isEqualTo(2);
	}

	@Test
	void 따옴표로_감싼_실패는_성공으로_세지_않는다() {
		Set<String> succeeded = ConcurrentHashMap.newKeySet();
		Set<String> written = ConcurrentHashMap.newKeySet();
		ToolCallback budget = new ToolCallBudgetCallback(new FakeTool("editFileTexts", "\"실패: 아무것도 바꾸지 않았습니다\""), "test-agent",
			new AtomicInteger(), 10, succeeded, List.of("editFileTexts"), new AtomicInteger(), written);

		budget.call("{\"filePath\": \"/w/a.md\", \"edits\": []}");

		assertThat(succeeded).isEmpty();
		assertThat(written).isEmpty();
	}

	/** 정해 둔 답을 돌려주고 불린 횟수를 세는 가짜 Tool입니다. */
	private static class FakeTool implements ToolCallback {

		private final ToolDefinition definition;
		private final String answer;
		int calls;

		FakeTool(String name, String answer) {
			this.definition = ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
			this.answer = answer;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.definition;
		}

		@Override
		public String call(String toolInput) {
			this.calls++;
			return this.answer;
		}
	}

}

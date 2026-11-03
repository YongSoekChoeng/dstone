package net.dstone.ai.common.advisors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * <pre>
 * 회차별 줄과 합계 줄이 맞게 나오는지 봅니다.
 * </pre>
 */
public class AgentCallStatsAdvisorTest {

	private ChatResponse response(int prompt, int completion, String... toolNames) {
		List<AssistantMessage.ToolCall> calls = new java.util.ArrayList<AssistantMessage.ToolCall>();
		for (String name : toolNames) {
			calls.add(new AssistantMessage.ToolCall("id-" + name, "function", name, "{}"));
		}
		AssistantMessage message = AssistantMessage.builder().content("답").properties(Map.of()).toolCalls(calls).build();
		ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(new DefaultUsage(Integer.valueOf(prompt), Integer.valueOf(completion))).build();
		return new ChatResponse(List.of(new Generation(message)), metadata);
	}

	@Test
	void Tool을_요청한_회차는_한_줄이고_마지막_답에_합계가_붙는다() {
		AgentCallStatsAdvisor advisor = new AgentCallStatsAdvisor("a");

		String first = advisor.record(this.response(100, 10, "readFile", "readFile"), 5);
		assertThat(first).contains("1회차").contains("입력 100토큰").contains("[readFile, readFile]").doesNotContain("합계");

		String last = advisor.record(this.response(200, 20), 5);
		assertThat(last).contains("2회차").contains("Tool 요청 없음").contains("합계 - LLM 2회, Tool {readFile=2}, 입력 300토큰, 출력 30토큰");
	}

	@Test
	void 답이_없어도_죽지_않는다() {
		assertThat(new AgentCallStatsAdvisor("a").record(null, 1)).contains("합계 - LLM 1회");
	}

}

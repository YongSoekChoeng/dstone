package net.dstone.ai.common.advisors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * <pre>
 * 앞 라운드 답의 추론 글만 빠지고, 답 본문·Tool 호출·다른 metadata는 그대로 남는지 봅니다.
 * </pre>
 */
public class ReasoningStripAdvisorTest {

	private AssistantMessage assistant(Map<String, Object> metadata) {
		List<AssistantMessage.ToolCall> calls = new ArrayList<AssistantMessage.ToolCall>();
		calls.add(new AssistantMessage.ToolCall("call-1", "function", "readFileLines", "{\"fileFullPath\":\"A.java\"}"));
		return AssistantMessage.builder().content("파일을 읽겠습니다").properties(metadata).toolCalls(calls).build();
	}

	@Test
	void 추론_글만_빼고_나머지는_남긴다() {
		List<Message> messages = new ArrayList<Message>();
		messages.add(new SystemMessage("규칙"));
		messages.add(new UserMessage("질문"));
		messages.add(this.assistant(Map.<String, Object>of(ReasoningStripAdvisor.REASONING_CONTENT, "아주 긴 생각", "id", "r-1")));
		messages.add(ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "readFileLines", "1: class A"))).build());

		long[] removed = new long[1];
		List<Message> result = ReasoningStripAdvisor.strip(messages, removed);

		assertThat(result).isNotSameAs(messages).hasSize(4);
		AssistantMessage stripped = (AssistantMessage) result.get(2);
		assertThat(stripped.getMetadata()).doesNotContainKey(ReasoningStripAdvisor.REASONING_CONTENT).containsEntry("id", "r-1");
		assertThat(stripped.getText()).isEqualTo("파일을 읽겠습니다");
		assertThat(stripped.getToolCalls()).hasSize(1);
		assertThat(stripped.getToolCalls().get(0).name()).isEqualTo("readFileLines");
		assertThat(removed[0]).isEqualTo("아주 긴 생각".length());
		// 받은 목록과 메시지는 바꾸지 않습니다.
		assertThat(((AssistantMessage) messages.get(2)).getMetadata()).containsKey(ReasoningStripAdvisor.REASONING_CONTENT);
		assertThat(result.get(0)).isSameAs(messages.get(0));
		assertThat(result.get(3)).isSameAs(messages.get(3));
	}

	@Test
	void 뺄_것이_없으면_받은_목록을_그대로_돌려준다() {
		List<Message> messages = new ArrayList<Message>();
		messages.add(new UserMessage("질문"));
		messages.add(this.assistant(Map.<String, Object>of("id", "r-1")));

		assertThat(ReasoningStripAdvisor.strip(messages, null)).isSameAs(messages);
	}

}

package net.dstone.ai.common.advisors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * <pre>
 * 출력 한도에 걸려 답도 Tool 호출도 없이 끝난 라운드를 한 번만 다시 부르는지 봅니다.
 * 응답 모양은 2026-11-22 실행 f2cf12b3의 실패 응답(finishReason=LENGTH, 본문 없음, 추론 글만 있음)을 본떴습니다.
 * </pre>
 */
public class RunawayRetryAdvisorTest {

	/** 받은 요청을 적어 두고, 준비한 응답을 차례로 돌려주는 가짜 체인입니다. */
	private static final class FakeChain implements CallAdvisorChain {
		final List<ChatClientRequest> requests = new ArrayList<ChatClientRequest>();
		final List<ChatResponse> answers;
		FakeChain(List<ChatResponse> answers) {
			this.answers = answers;
		}
		@Override
		public ChatClientResponse nextCall(ChatClientRequest request) {
			this.requests.add(request);
			ChatResponse answer = this.answers.get(this.requests.size() - 1);
			return ChatClientResponse.builder().chatResponse(answer).build();
		}
		@Override
		public List<CallAdvisor> getCallAdvisors() {
			return List.of();
		}
		@Override
		public CallAdvisorChain copy(CallAdvisor after) {
			return this;
		}
	}

	private static ChatResponse runaway() {
		AssistantMessage message = AssistantMessage.builder().content("")
			.properties(Map.of(ReasoningStripAdvisor.REASONING_CONTENT, "세션 설정이 빠져 있다. 이것이 원인이다. 세션 설정이 빠져 있다. 이것이 원인이다."))
			.build();
		return new ChatResponse(List.of(new Generation(message, ChatGenerationMetadata.builder().finishReason("LENGTH").build())));
	}

	private static ChatResponse toolCall() {
		AssistantMessage message = AssistantMessage.builder().content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", "editFileTexts", "{}"))).build();
		return new ChatResponse(List.of(new Generation(message, ChatGenerationMetadata.builder().finishReason("TOOL_CALLS").build())));
	}

	private static ChatClientRequest request() {
		return ChatClientRequest.builder().prompt(new Prompt(List.<Message>of(new UserMessage("증상: 저장이 안 됨")))).build();
	}

	@Test
	void 되풀이하다_한도에_걸린_라운드는_안내를_붙여_한_번_다시_부른다() {
		FakeChain chain = new FakeChain(List.of(runaway(), toolCall()));
		ChatClientResponse response = new RunawayRetryAdvisor("a").adviseCall(request(), chain);

		assertThat(chain.requests).hasSize(2);
		assertThat(response.chatResponse().getResult().getOutput().getToolCalls()).hasSize(1);
		List<Message> retried = chain.requests.get(1).prompt().getInstructions();
		assertThat(retried).hasSize(2);
		assertThat(retried.get(1).getText()).startsWith("[엔진 안내]").contains("세션 설정이 빠져 있다");
		// 받은 요청은 바꾸지 않는다(Tool 고리가 들고 있는 대화는 그대로)
		assertThat(chain.requests.get(0).prompt().getInstructions()).hasSize(1);
	}

	@Test
	void 다시_불러도_같으면_그대로_넘기고_더_부르지_않는다() {
		RunawayRetryAdvisor advisor = new RunawayRetryAdvisor("a");
		FakeChain chain = new FakeChain(List.of(runaway(), runaway(), runaway()));
		ChatClientResponse response = advisor.adviseCall(request(), chain);
		assertThat(chain.requests).hasSize(2);
		assertThat(RunawayRetryAdvisor.isRunaway(response.chatResponse())).isTrue();

		// 같은 Agent 호출의 다음 라운드에서는 다시 부르지 않는다(호출 한 번에 한 번)
		advisor.adviseCall(request(), chain);
		assertThat(chain.requests).hasSize(3);
	}

	@Test
	void 정상_답과_Tool_호출과_답이_있는_LENGTH는_건드리지_않는다() {
		FakeChain chain = new FakeChain(List.of(toolCall()));
		new RunawayRetryAdvisor("a").adviseCall(request(), chain);
		assertThat(chain.requests).hasSize(1);

		AssistantMessage cut = AssistantMessage.builder().content("{\"causes\": [\"…").build();
		assertThat(RunawayRetryAdvisor.isRunaway(new ChatResponse(List.of(new Generation(cut, ChatGenerationMetadata.builder().finishReason("LENGTH").build())))))
			.isFalse();
		assertThat(RunawayRetryAdvisor.isRunaway(null)).isFalse();
	}

}

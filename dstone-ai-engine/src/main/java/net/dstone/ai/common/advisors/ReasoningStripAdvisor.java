package net.dstone.ai.common.advisors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * <pre>
 * LLM을 부르기 직전에, 앞 라운드 답에 붙어 있던 추론 글(reasoning)을 대화에서 빼 주는 Advisor입니다.
 *
 * 왜 필요한가:
 * Spring AI(2.0.1)의 OpenAiChatModel은 답에서 받은 추론 글을 AssistantMessage의 metadata("reasoningContent")에 담아 두고,
 * 다음 요청을 만들 때 그 메시지에 reasoning_content로 다시 실어 보냅니다. Tool 루프에서는 라운드마다 대화 전체를 다시 보내므로,
 * 앞에서 생각한 글이 뒤의 모든 라운드에 쌓여 갑니다.
 * sm-workflow 리뷰 step에서 1회차에 출력 31k토큰(대부분 추론)을 쓰자 2회차 입력이 2k → 33k토큰으로 늘었습니다.
 * 추론 글은 그 라운드의 답을 내는 데 쓰인 것이라, 다음 라운드에는 Tool 호출과 답 본문만 있으면 됩니다.
 *
 * 무엇을 빼는가:
 * AssistantMessage의 metadata에서 "reasoningContent" 하나만 뺍니다. 답 본문, Tool 호출, 그 밖의 metadata는 그대로 둡니다.
 * 이 키는 OpenAI 호환 provider(OpenRouter, vLLM 등)만 씁니다. Anthropic·Ollama의 메시지는 건드리지 않습니다.
 *
 * 어디서 도는가:
 * ToolResultCompactionAdvisor와 같이 ToolCallingAdvisor보다 안쪽에 있어서, Tool 루프의 매 라운드 LLM에 가기 직전에 돕니다.
 * 바꾸는 것은 "이번에 LLM에게 보내는 사본"뿐이고, 대화 기억(Redis)과 Tool 루프가 들고 있는 원래 대화는 그대로입니다.
 *
 * 설정 dstone.ai.agent.keep-reasoning이 true면 붙이지 않습니다(AgentExecutor).
 * Spring 빈으로 만들지 않는 이유는 ToolResultCompactionAdvisor와 같습니다.
 * </pre>
 */
public class ReasoningStripAdvisor implements CallAdvisor, StreamAdvisor {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ReasoningStripAdvisor.class);

	/** Spring AI OpenAiChatModel이 추론 글을 담는 metadata 키입니다. */
	public static final String REASONING_CONTENT = "reasoningContent";

	/**
	 * Advisor 순서입니다. ToolCallingAdvisor(가장 앞 + 300)보다 뒤여야 되풀이 안쪽에서 돌고,
	 * LLM 로그 Advisor(0)보다 앞이어야 로그에 "실제로 보낸 내용"이 남습니다.
	 */
	public static final int ORDER = -110;

	private final String agentId;

	/**
	 * @param agentId 이 호출의 Agent id입니다(로그용).
	 */
	public ReasoningStripAdvisor(String agentId) {
		this.agentId = agentId;
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		return callAdvisorChain.nextCall(this.stripped(chatClientRequest));
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
		return streamAdvisorChain.nextStream(this.stripped(chatClientRequest));
	}

	/** 추론 글을 뺀 요청을 돌려줍니다. 뺄 것이 없으면 받은 요청 그대로입니다. */
	private ChatClientRequest stripped(ChatClientRequest chatClientRequest) {
		Prompt prompt = chatClientRequest.prompt();
		List<Message> messages = prompt.getInstructions();
		long[] removedChars = new long[1];
		List<Message> result = strip(messages, removedChars);
		if (result == messages) {
			return chatClientRequest;
		}
		LOGGER.info("dstone-ai-engine agent: agent[" + this.agentId + "] 앞 라운드의 추론 글 " + removedChars[0] + "자를 빼고 보냈습니다");
		return chatClientRequest.mutate().prompt(new Prompt(result, prompt.getOptions())).build();
	}

	/**
	 * <pre>
	 * 대화에서 AssistantMessage의 추론 글(metadata "reasoningContent")을 뺀 새 목록을 돌려줍니다.
	 * 뺄 것이 없으면 받은 목록을 그대로 돌려줍니다. 받은 목록과 메시지는 바꾸지 않습니다.
	 * </pre>
	 *
	 * @param messages     LLM에게 보낼 대화
	 * @param removedChars 뺀 글자 수를 [0]에 더해 줍니다(로그용). null이면 세지 않습니다.
	 */
	public static List<Message> strip(List<Message> messages, long[] removedChars) {
		if (messages == null) {
			return null;
		}
		List<Message> result = null;
		for (int i = 0; i < messages.size(); i++) {
			Message message = messages.get(i);
			if (!(message instanceof AssistantMessage)) {
				continue;
			}
			AssistantMessage assistant = (AssistantMessage) message;
			Map<String, Object> metadata = assistant.getMetadata();
			if (metadata == null || !metadata.containsKey(REASONING_CONTENT)) {
				continue;
			}
			// 다른 provider가 AssistantMessage를 상속해 쓰는 경우는 건드리지 않습니다(정보를 잃을 수 있습니다).
			if (assistant.getClass() != AssistantMessage.class) {
				continue;
			}
			Object reasoning = metadata.get(REASONING_CONTENT);
			if (removedChars != null && reasoning != null) {
				removedChars[0] += String.valueOf(reasoning).length();
			}
			Map<String, Object> kept = new LinkedHashMap<String, Object>(metadata);
			kept.remove(REASONING_CONTENT);
			AssistantMessage rebuilt = AssistantMessage.builder()
				.content(assistant.getText())
				.properties(kept)
				.toolCalls(assistant.getToolCalls())
				.media(assistant.getMedia())
				.build();
			if (result == null) {
				result = new ArrayList<Message>(messages);
			}
			result.set(i, rebuilt);
		}
		return result == null ? messages : result;
	}

}

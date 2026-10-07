package net.dstone.ai.runtime.agent;

import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;

import net.dstone.common.utils.LogUtil;
import reactor.core.publisher.Flux;

/**
 * <pre>
 * LLM을 부르기 직전에, 대화에 쌓인 오래된 Tool 결과를 줄여 주는 Advisor입니다. 줄이는 규칙은 ToolResultCompactor에 있습니다.
 *
 * 어디서 도는가:
 * Spring AI(2.0.1)는 Tool 호출을 Advisor(ToolCallingAdvisor)가 되풀이합니다. "LLM 호출 → Tool 실행 → 결과를 붙여 다시 LLM 호출"을
 * Tool 요청이 없어질 때까지 돕니다. 이 Advisor는 그보다 안쪽(순서 값이 더 큼)에 있어서, 되풀이할 때마다 LLM에게 가기 직전에 한 번씩 돕니다.
 *
 * 줄이는 것은 "이번에 LLM에게 보내는 사본"뿐입니다. ToolCallingAdvisor가 들고 있는 원래 대화와 대화 기억(Redis)에는
 * 원래 결과가 그대로 남습니다. 그래서 Tool 호출 횟수를 세는 일이나 대화 저장에는 영향을 주지 않습니다.
 *
 * 이 클래스는 Spring 빈으로 만들지 않습니다. Advisor 빈이 하나라도 생기면 ConfigChatClient의 defaultAdvisors 목록 대신
 * 그 빈들만 주입되어, 거기서 넣은 LLM 로그 Advisor가 빠지기 때문입니다. AgentExecutor가 Tool을 붙일 때 함께 붙입니다.
 * </pre>
 */
public class ToolResultCompactionAdvisor implements CallAdvisor, StreamAdvisor {

	/**
	 * Advisor 순서입니다. ToolCallingAdvisor(가장 앞 + 300)보다 뒤여야 되풀이 안쪽에서 돌고,
	 * LLM 로그 Advisor(0)보다 앞이어야 로그에 "실제로 보낸 내용"이 남습니다.
	 */
	public static final int ORDER = -100;

	private final String agentId;
	private final int keepChars;

	/**
	 * @param agentId   이 호출의 Agent id입니다(로그용).
	 * @param keepChars 온전히 남겨 둘 Tool 결과의 글자 수 합입니다(dstone.ai.agent.tool.keep-result-chars).
	 */
	public ToolResultCompactionAdvisor(String agentId, int keepChars) {
		this.agentId = agentId;
		this.keepChars = keepChars;
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
		return callAdvisorChain.nextCall(this.compacted(chatClientRequest));
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
		return streamAdvisorChain.nextStream(this.compacted(chatClientRequest));
	}

	/** 오래된 Tool 결과를 줄인 요청을 돌려줍니다. 줄일 것이 없으면 받은 요청 그대로입니다. */
	private ChatClientRequest compacted(ChatClientRequest chatClientRequest) {
		Prompt prompt = chatClientRequest.prompt();
		List<Message> messages = prompt.getInstructions();
		List<Message> compacted = ToolResultCompactor.compact(messages, this.keepChars);
		if (compacted == messages) {
			return chatClientRequest;
		}
		LogUtil.sysout("dstone-ai-engine tool: agent[" + this.agentId + "]의 대화에 쌓인 Tool 결과가 " + ToolResultCompactor.toolResultChars(messages)
			+ "자라서 오래된 것부터 줄였습니다 - 줄인 뒤 " + ToolResultCompactor.toolResultChars(compacted) + "자(기준 " + this.keepChars + "자)");
		return chatClientRequest.mutate().prompt(new Prompt(compacted, prompt.getOptions())).build();
	}

}

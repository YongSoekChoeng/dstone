package net.dstone.ai.common.advisors;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * <pre>
 * Agent 호출 한 번이 LLM을 몇 번 불렀고 어떤 Tool을 몇 번 썼는지, 토큰을 얼마나 썼는지 로그에 남기는 Advisor입니다.
 *
 * 왜 필요한가:
 * LLM이 고른 Tool 호출은 ConfigCallLog에 잡히지 않습니다(TOOL step만 잡힙니다). 그래서 Agent가 왜 느린지 알 수 없었습니다.
 *
 * 어디서 도는가:
 * ToolCallingAdvisor보다 안쪽(순서 값이 더 큼)이라 LLM을 부를 때마다 한 번씩 돕니다. 그래서 회차마다 한 줄씩 남기고,
 * Tool 요청이 없는 답(마지막 답)이 오면 합계를 한 줄 더 남깁니다.
 * Tool 호출 한도 초과처럼 이 Advisor 바깥에서 끊기면 합계 줄은 남지 않지만 회차별 줄은 남아 있습니다.
 *
 * Tool 인자와 결과는 남기지 않습니다. 크기도 크고, 파일 내용이 그대로 로그에 쌓이기 때문입니다.
 * 스트리밍 호출은 세지 않습니다(CallAdvisor만 구현합니다).
 *
 * LogUtil.sysout을 쓰지 않습니다. 기동 스크립트가 stdout을 버리기 때문에 execution.log에 남도록 로거로 씁니다.
 *
 * 이 클래스는 Spring 빈이 아닙니다. Agent를 부를 때마다 AgentExecutor가 새로 만듭니다(숫자가 호출마다 따로이기 때문입니다).
 * </pre>
 */
public class AgentCallStatsAdvisor implements CallAdvisor {

	/** Advisor 순서입니다. ToolCallingAdvisor(가장 앞 + 300)보다 뒤여야 LLM을 부를 때마다 돕니다. */
	public static final int ORDER = -50;

	private static final Logger LOGGER = LoggerFactory.getLogger(AgentCallStatsAdvisor.class);

	private final String agentId;
	private final long startedAt = System.currentTimeMillis();
	private final Map<String, Integer> toolCounts = new TreeMap<String, Integer>();

	private int rounds = 0;
	private long promptTokens = 0;
	private long completionTokens = 0;

	/**
	 * @param agentId 이 호출의 Agent id입니다.
	 */
	public AgentCallStatsAdvisor(String agentId) {
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
		long roundStartedAt = System.currentTimeMillis();
		ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
		LOGGER.info(this.record(response == null ? null : response.chatResponse(), System.currentTimeMillis() - roundStartedAt));
		return response;
	}

	/**
	 * <pre>
	 * LLM 답 하나를 합계에 더하고, 로그에 남길 글을 돌려줍니다. Tool 요청이 없는 답이면 합계 줄도 함께 붙입니다.
	 * </pre>
	 *
	 * @param response  LLM 답입니다.
	 * @param elapsedMs 이번 회차에 걸린 시간입니다.
	 */
	String record(ChatResponse response, long elapsedMs) {
		this.rounds++;
		StringBuilder requested = new StringBuilder();
		if (response != null) {
			List<Generation> results = response.getResults();
			for (Generation generation : results == null ? List.<Generation>of() : results) {
				if (generation.getOutput() == null || generation.getOutput().getToolCalls() == null) {
					continue;
				}
				for (AssistantMessage.ToolCall toolCall : generation.getOutput().getToolCalls()) {
					Integer count = this.toolCounts.get(toolCall.name());
					this.toolCounts.put(toolCall.name(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
					requested.append(requested.length() == 0 ? "" : ", ").append(toolCall.name());
				}
			}
		}

		String roundTokens = "";
		if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
			Usage usage = response.getMetadata().getUsage();
			long prompt = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens().longValue();
			long completion = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens().longValue();
			this.promptTokens += prompt;
			this.completionTokens += completion;
			roundTokens = ", 입력 " + prompt + "토큰, 출력 " + completion + "토큰";
		}

		String line = "dstone-ai-engine agent: agent[" + this.agentId + "] " + this.rounds + "회차 " + elapsedMs + "ms" + roundTokens
			+ (requested.length() == 0 ? ", Tool 요청 없음" : ", Tool 요청 [" + requested + "]");
		if (requested.length() > 0) {
			return line;
		}
		return line + "\ndstone-ai-engine agent: agent[" + this.agentId + "] 합계 - LLM " + this.rounds + "회, Tool " + this.toolCounts
			+ ", 입력 " + this.promptTokens + "토큰, 출력 " + this.completionTokens + "토큰, " + (System.currentTimeMillis() - this.startedAt) + "ms";
	}

}

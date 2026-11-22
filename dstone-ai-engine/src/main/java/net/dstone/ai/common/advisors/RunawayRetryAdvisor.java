package net.dstone.ai.common.advisors;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * <pre>
 * 모델이 같은 생각을 되풀이하다 출력 한도에 걸려 아무것도 못 하고 끝난 라운드를, 그 자리에서 한 번 다시 부르는 Advisor입니다.
 *
 * 왜 필요한가:
 * 2026-11-22 실행 f2cf12b3에서 문제 해결 Agent가 원인 하나(세션 설정 누락)를 찾은 뒤 그 두 문장을 수백 번 되풀이했습니다.
 * 255초, 출력 60,000토큰을 쓰고 출력 한도(finishReason=LENGTH)에 걸려 답도 Tool 호출도 없이 끝났습니다.
 * 엔진은 그것을 step 실패로 처리했고, 찾은 내용과 그때까지 읽은 파일·DB 결과가 모두 버려졌습니다.
 * AgentExecutor.call()의 재시도는 대화를 처음부터 다시 시작하므로 같은 조사를 되풀이합니다.
 *
 * 무엇을 하는가:
 * Tool 고리(ToolCallingAdvisor) 안쪽에서 라운드마다 돕니다. 모델의 답이 "LENGTH + 답 글 없음 + Tool 호출 없음"이면
 * 그 답을 Tool 고리에 넘기지 않고 버린 뒤, 같은 대화(앞 라운드의 Tool 결과 그대로)에 엔진 안내 한 줄을 붙여 한 번 더 부릅니다.
 * 안내에는 버린 생각의 앞부분을 짧게 붙입니다. 거기에 이미 찾은 내용이 있는 경우가 많기 때문입니다.
 * 다시 부르는 것은 Agent 호출 한 번에 한 번뿐입니다(이 Advisor는 호출마다 새로 만듭니다). 다시 불러도 같으면 그 답을 그대로 넘기고,
 * 지금처럼 AgentExecutor가 실패로 처리합니다.
 * 안내 줄은 다시 부르는 이번 요청에만 붙습니다. Tool 고리가 들고 있는 대화와 대화 기억(Redis)은 바꾸지 않습니다.
 *
 * 어디서 도는가:
 * ReasoningStripAdvisor(-110)·ToolResultCompactionAdvisor(-100)보다 안쪽, AgentCallStatsAdvisor(-50)보다 바깥입니다.
 * 그래서 버린 라운드와 다시 부른 라운드가 회차 로그에 모두 남습니다.
 * 설정 dstone.ai.agent.runaway-retry가 false면 붙이지 않습니다(AgentExecutor).
 * Spring 빈으로 만들지 않는 이유는 ReasoningStripAdvisor와 같습니다.
 * </pre>
 */
public class RunawayRetryAdvisor implements CallAdvisor {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RunawayRetryAdvisor.class);

	/** Advisor 순서입니다(위 설명 참고). */
	public static final int ORDER = -60;

	/** 안내에 붙일 버린 생각의 최대 글자 수입니다. */
	static final int REASONING_HEAD_CHARS = 800;

	/** 다시 부를 때 붙이는 엔진 안내입니다. */
	static final String NOTE = "[엔진 안내] 바로 앞 응답은 같은 생각을 되풀이하다가 출력 한도에 걸려 버려졌습니다. "
		+ "이미 내린 결론을 다시 쓰지 마십시오. 지금 필요한 Tool을 부르거나(파일 수정, 조회 등), 할 일이 끝났으면 답을 쓰십시오. "
		+ "생각은 짧게 하십시오.";

	private final String agentId;

	/** 이 Agent 호출에서 이미 다시 불렀는가 */
	private boolean retried;

	/**
	 * @param agentId 이 호출의 Agent id입니다(로그용).
	 */
	public RunawayRetryAdvisor(String agentId) {
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
		ChatClientResponse response = callAdvisorChain.copy(this).nextCall(chatClientRequest);
		if (this.retried || !isRunaway(response == null ? null : response.chatResponse())) {
			return response;
		}
		this.retried = true;
		String head = reasoningHead(response.chatResponse());
		LOGGER.info("dstone-ai-engine agent: agent[" + this.agentId + "] 출력 한도에 걸려 답도 Tool 호출도 없이 끝난 라운드를 버리고 한 번 다시 부릅니다");
		return callAdvisorChain.copy(this).nextCall(withNote(chatClientRequest, head));
	}

	/**
	 * 출력 한도에 걸렸고(LENGTH), 답 글도 Tool 호출도 없는 응답인가.
	 *
	 * @param response 모델 응답
	 */
	static boolean isRunaway(ChatResponse response) {
		if (response == null || response.getResult() == null) {
			return false;
		}
		Generation result = response.getResult();
		String finishReason = result.getMetadata() == null ? null : result.getMetadata().getFinishReason();
		if (!"LENGTH".equalsIgnoreCase(finishReason)) {
			return false;
		}
		AssistantMessage output = result.getOutput();
		if (output == null) {
			return true;
		}
		boolean noText = output.getText() == null || output.getText().isBlank();
		boolean noToolCalls = output.getToolCalls() == null || output.getToolCalls().isEmpty();
		return noText && noToolCalls;
	}

	/** 버린 응답의 추론 글 앞부분입니다. 없으면 빈 글자입니다. */
	static String reasoningHead(ChatResponse response) {
		if (response == null || response.getResult() == null || response.getResult().getOutput() == null
			|| response.getResult().getOutput().getMetadata() == null) {
			return "";
		}
		Object reasoning = response.getResult().getOutput().getMetadata().get(ReasoningStripAdvisor.REASONING_CONTENT);
		if (reasoning == null) {
			return "";
		}
		String text = String.valueOf(reasoning).strip();
		return text.length() <= REASONING_HEAD_CHARS ? text : text.substring(0, REASONING_HEAD_CHARS) + " …";
	}

	/** 요청 대화 끝에 엔진 안내를 붙인 사본을 만듭니다. 받은 요청은 바꾸지 않습니다. */
	static ChatClientRequest withNote(ChatClientRequest request, String reasoningHead) {
		Prompt prompt = request.prompt();
		List<Message> messages = new ArrayList<Message>(prompt.getInstructions());
		String note = reasoningHead == null || reasoningHead.isEmpty() ? NOTE : NOTE + "\n버린 생각의 앞부분(참고):\n" + reasoningHead;
		messages.add(new UserMessage(note));
		return request.mutate().prompt(new Prompt(messages, prompt.getOptions())).build();
	}

}

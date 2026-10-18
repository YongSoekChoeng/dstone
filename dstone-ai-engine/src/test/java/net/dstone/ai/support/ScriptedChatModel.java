package net.dstone.ai.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationContext;

import reactor.core.publisher.Flux;

/**
 * <pre>
 * 테스트에서 엔진이 쓰는 ChatModel입니다(TestEngineConfig가 @Primary로 넣습니다). conf/application.yml의
 * dstone.ai.test.llmcall 값에 따라 답을 어디서 가져올지 정합니다.
 *
 *   llmcall   답                                         answer(...)
 *   false     테스트가 answer(...)로 넣어 둔 답(넣은 순서대로)   쓰임
 *   true      spring.ai.model.chat의 실제 provider 호출       무시됨
 *
 * - 어느 쪽이든 엔진은 이 ChatModel을 실제 provider처럼 씁니다. 프롬프트 조립, 답 검사(output 계약), state 저장은 운영과 같습니다.
 * - false인데 준비된 답이 없이 LLM이 불리면 IllegalStateException을 던집니다. LLM 없이 돌아야 할 Workflow가 LLM을 부르면
 *   그 실행이 FAILED로 끝나서 바로 드러납니다.
 * - 받은 프롬프트는 어느 쪽이든 모아 둡니다(prompts()). "이 step이 LLM에게 무엇을 보냈는지"를 검사할 수 있습니다.
 *
 * 테스트 컨텍스트 하나를 여러 테스트가 함께 쓰므로, WorkflowTestBase가 테스트마다 reset()을 부릅니다.
 * </pre>
 */
public class ScriptedChatModel implements ChatModel {

	/** true면 실제 provider를 부릅니다(dstone.ai.test.llmcall). */
	private final boolean llmCall;

	/** 실제 provider의 ChatModel을 찾을 곳입니다. */
	private final ApplicationContext context;

	/** 실제 provider의 ChatModel입니다. 처음 부를 때 찾아 둡니다. */
	private ChatModel realModel;

	/** 아직 돌려주지 않은 답입니다. 앞에서부터 하나씩 꺼냅니다(llmCall이 false일 때만). */
	private final List<String> answers = new ArrayList<String>();

	/** 지금까지 받은 프롬프트입니다(받은 순서). */
	private final List<Prompt> prompts = new ArrayList<Prompt>();

	/**
	 * @param llmCall true면 실제 provider를 부릅니다(dstone.ai.test.llmcall)
	 * @param context 실제 provider의 ChatModel 빈을 찾을 컨텍스트
	 */
	public ScriptedChatModel(boolean llmCall, ApplicationContext context) {
		this.llmCall = llmCall;
		this.context = context;
	}

	/** 실제 LLM을 부르는지 봅니다(dstone.ai.test.llmcall). 미리 넣은 답에 기대는 테스트는 이 값이 true면 건너뜁니다. */
	public boolean isLlmCall() {
		return this.llmCall;
	}

	/**
	 * LLM이 돌려줄 답을 차례로 넣어 둡니다. output을 선언한 Agent라면 그 모양의 JSON 글자를 넣습니다.
	 * 실제 LLM을 부를 때(dstone.ai.test.llmcall: true)는 무시합니다.
	 *
	 * @param texts 돌려줄 답들(불리는 순서대로)
	 */
	public synchronized void answer(String... texts) {
		if (this.llmCall) {
			return;
		}
		for (String text : texts) {
			this.answers.add(text);
		}
	}

	/** 넣어 둔 답과 받은 프롬프트를 모두 비웁니다. */
	public synchronized void reset() {
		this.answers.clear();
		this.prompts.clear();
	}

	/** 지금까지 받은 프롬프트의 복사본입니다. */
	public synchronized List<Prompt> prompts() {
		return new ArrayList<Prompt>(this.prompts);
	}

	/** 아직 쓰이지 않고 남은 답의 개수입니다. */
	public synchronized int remaining() {
		return this.answers.size();
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		synchronized (this) {
			this.prompts.add(prompt);
		}
		if (this.llmCall) {
			// 실제 LLM은 오래 걸릴 수 있어서 잠금 밖에서 부릅니다(forEach step이 동시에 부를 수 있습니다).
			return this.realModel().call(prompt);
		}
		synchronized (this) {
			if (this.answers.isEmpty()) {
				throw new IllegalStateException("ScriptedChatModel에 준비된 답이 없습니다. 테스트에서 answer(...)로 LLM의 답을 먼저 넣거나, "
					+ "실제 LLM을 부르려면 dstone.ai.test.llmcall을 true로 하십시오. 받은 프롬프트: " + shorten(prompt.getContents()));
			}
			String text = this.answers.remove(0);
			List<Generation> generations = new ArrayList<Generation>();
			generations.add(new Generation(new AssistantMessage(text)));
			return new ChatResponse(generations);
		}
	}

	/**
	 * <pre>
	 * 기본 옵션입니다. 실제 LLM을 부를 때는 실제 provider의 기본 옵션을 그대로 돌려줍니다.
	 * ChatClient는 이 값을 바탕으로 요청 옵션을 만들고, provider(예: OpenAiChatModel)는 그 옵션을 자기 옵션 클래스로 꺼내 씁니다.
	 * 일반 ChatOptions를 돌려주면 "DefaultChatOptions cannot be cast to OpenAiChatOptions"로 호출이 실패합니다.
	 * </pre>
	 */
	@Override
	public ChatOptions getDefaultOptions() {
		if (this.llmCall) {
			return this.realModel().getDefaultOptions();
		}
		return ChatModel.super.getDefaultOptions();
	}

	/** getDefaultOptions()와 같은 이유로, 실제 LLM을 부를 때는 실제 provider의 옵션을 돌려줍니다. */
	@Override
	public ChatOptions getOptions() {
		if (this.llmCall) {
			return this.realModel().getOptions();
		}
		return ChatModel.super.getOptions();
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		if (this.llmCall) {
			synchronized (this) {
				this.prompts.add(prompt);
			}
			return this.realModel().stream(prompt);
		}
		return Flux.just(this.call(prompt));
	}

	/**
	 * 컨텍스트에서 이 대역이 아닌 ChatModel(spring.ai.model.chat의 provider가 만든 것)을 찾습니다.
	 *
	 * @throws IllegalStateException 실제 provider의 ChatModel이 없을 때(spring.ai.model.chat이 none 등)
	 */
	private synchronized ChatModel realModel() {
		if (this.realModel == null) {
			for (Map.Entry<String, ChatModel> entry : this.context.getBeansOfType(ChatModel.class).entrySet()) {
				if (entry.getValue() != this) {
					this.realModel = entry.getValue();
					break;
				}
			}
			if (this.realModel == null) {
				throw new IllegalStateException("dstone.ai.test.llmcall이 true인데 실제 LLM의 ChatModel이 없습니다. conf/application.yml의 spring.ai.model.chat을 확인하십시오.");
			}
		}
		return this.realModel;
	}

	/** 오류 문장에 넣기에 너무 긴 글자는 앞부분만 남깁니다. */
	private static String shorten(String text) {
		if (text == null || text.length() <= 300) {
			return text;
		}
		return text.substring(0, 300) + "...";
	}

}

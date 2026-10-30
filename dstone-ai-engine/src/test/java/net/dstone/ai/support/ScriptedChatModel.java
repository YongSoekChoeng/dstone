package net.dstone.ai.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
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
 * 답은 글자뿐 아니라 Tool 호출도 넣을 수 있습니다(answerToolCalls()). 엔진의 Tool 실행 흐름(Spring AI의 ToolCallingAdvisor)이
 * 그대로 돌아서, Tool 결과를 받은 뒤 다음 답을 다시 꺼냅니다.
 * 여러 Agent가 동시에 불리는 경우(백그라운드 Sub Agent)를 위해, 프롬프트에 어떤 글자가 들어 있을 때만 꺼낼 답도 넣을 수 있습니다
 * (answerWhen(), answerToolCallsWhen()). 그런 답이 먼저이고, 맞는 것이 없으면 순서대로 넣은 답을 꺼냅니다.
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

	/** 아직 돌려주지 않은 답입니다(글자 또는 Tool 호출 목록). 앞에서부터 하나씩 꺼냅니다(llmCall이 false일 때만). */
	private final List<Object> answers = new ArrayList<Object>();

	/** 프롬프트에 그 글자(키)가 들어 있을 때만 꺼낼 답입니다. */
	private final Map<String, List<Object>> answersWhen = new LinkedHashMap<String, List<Object>>();

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

	/**
	 * <pre>
	 * LLM이 Tool을 부르는 답을 넣어 둡니다. 이름과 인자(JSON 글자)를 번갈아 적습니다. 둘 이상이면 한 답에서 여러 Tool을 부릅니다.
	 *   chatModel.answerToolCalls("Skill", "{\"command\": \"sql-review\"}");
	 * </pre>
	 *
	 * @param nameAndArguments Tool 이름, 인자, Tool 이름, 인자, ...
	 */
	public synchronized void answerToolCalls(String... nameAndArguments) {
		if (this.llmCall) {
			return;
		}
		this.answers.add(toolCalls(nameAndArguments));
	}

	/**
	 * 프롬프트에 marker가 들어 있을 때만 꺼낼 글자 답을 넣어 둡니다.
	 *
	 * @param marker 프롬프트(시스템 + 사용자 메시지)에 들어 있어야 할 글자
	 * @param texts  돌려줄 답들(불리는 순서대로)
	 */
	public synchronized void answerWhen(String marker, String... texts) {
		if (this.llmCall) {
			return;
		}
		List<Object> queue = this.queueOf(marker);
		for (String text : texts) {
			queue.add(text);
		}
	}

	/**
	 * 프롬프트에 marker가 들어 있을 때만 꺼낼 Tool 호출 답을 넣어 둡니다.
	 *
	 * @param marker           프롬프트(시스템 + 사용자 메시지)에 들어 있어야 할 글자
	 * @param nameAndArguments Tool 이름, 인자, Tool 이름, 인자, ...
	 */
	public synchronized void answerToolCallsWhen(String marker, String... nameAndArguments) {
		if (this.llmCall) {
			return;
		}
		this.queueOf(marker).add(toolCalls(nameAndArguments));
	}

	/** 넣어 둔 답과 받은 프롬프트를 모두 비웁니다. */
	public synchronized void reset() {
		this.answers.clear();
		this.answersWhen.clear();
		this.prompts.clear();
	}

	/** 지금까지 받은 프롬프트의 복사본입니다. */
	public synchronized List<Prompt> prompts() {
		return new ArrayList<Prompt>(this.prompts);
	}

	/**
	 * <pre>
	 * 프롬프트의 글자를 모두 이어 붙여 돌려줍니다. Prompt.getContents()와 달리 Tool 결과(ToolResponseMessage)도 넣습니다.
	 * "이 호출 때 LLM이 어떤 Tool 결과를 받았는지"를 검사할 때 씁니다.
	 * </pre>
	 *
	 * @param prompt 받은 프롬프트
	 */
	public static String textOf(Prompt prompt) {
		StringBuilder text = new StringBuilder();
		for (Message message : prompt.getInstructions()) {
			if (message instanceof ToolResponseMessage toolResponse) {
				for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
					text.append("\n[").append(response.name()).append("] ").append(response.responseData());
				}
			} else if (message.getText() != null) {
				text.append("\n").append(message.getText());
			}
		}
		return text.toString();
	}

	/** 아직 쓰이지 않고 남은 답의 개수입니다. */
	public synchronized int remaining() {
		int count = this.answers.size();
		for (List<Object> queue : this.answersWhen.values()) {
			count += queue.size();
		}
		return count;
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
			Object answer = this.next(prompt.getContents());
			if (answer == null) {
				throw new IllegalStateException("ScriptedChatModel에 준비된 답이 없습니다. 테스트에서 answer(...)로 LLM의 답을 먼저 넣거나, "
					+ "실제 LLM을 부르려면 dstone.ai.test.llmcall을 true로 하십시오. 받은 프롬프트: " + shorten(prompt.getContents()));
			}
			AssistantMessage message;
			if (answer instanceof String text) {
				message = new AssistantMessage(text);
			} else {
				@SuppressWarnings("unchecked")
				List<AssistantMessage.ToolCall> calls = (List<AssistantMessage.ToolCall>) answer;
				message = AssistantMessage.builder().content("").toolCalls(calls).build();
			}
			List<Generation> generations = new ArrayList<Generation>();
			generations.add(new Generation(message));
			return new ChatResponse(generations);
		}
	}

	/**
	 * <pre>
	 * 기본 옵션입니다. 실제 LLM을 부를 때는 실제 provider의 기본 옵션을 그대로 돌려줍니다.
	 * ChatClient는 이 값을 바탕으로 요청 옵션을 만들고, provider(예: OpenAiChatModel)는 그 옵션을 자기 옵션 클래스로 꺼내 씁니다.
	 * 일반 ChatOptions를 돌려주면 "DefaultChatOptions cannot be cast to OpenAiChatOptions"로 호출이 실패합니다.
	 * 미리 넣은 답을 쓸 때는 ToolCallingChatOptions를 돌려줍니다. ChatClient는 이 옵션일 때만 Agent의 Tool을 요청에 싣고,
	 * 그래야 answerToolCalls()로 넣은 Tool 호출을 엔진이 실제로 실행합니다.
	 * </pre>
	 */
	@Override
	public ChatOptions getDefaultOptions() {
		if (this.llmCall) {
			return this.realModel().getDefaultOptions();
		}
		return ToolCallingChatOptions.builder().build();
	}

	/** getDefaultOptions()와 같은 이유로, 실제 LLM을 부를 때는 실제 provider의 옵션을 돌려줍니다. */
	@Override
	public ChatOptions getOptions() {
		if (this.llmCall) {
			return this.realModel().getOptions();
		}
		return ToolCallingChatOptions.builder().build();
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

	/** 다음 답을 꺼냅니다. 프롬프트에 키가 들어 있는 답이 먼저이고, 없으면 순서대로 넣은 답입니다. 없으면 null입니다. */
	private Object next(String contents) {
		for (Map.Entry<String, List<Object>> entry : this.answersWhen.entrySet()) {
			if (!entry.getValue().isEmpty() && contents != null && contents.contains(entry.getKey())) {
				return entry.getValue().remove(0);
			}
		}
		return this.answers.isEmpty() ? null : this.answers.remove(0);
	}

	private List<Object> queueOf(String marker) {
		List<Object> queue = this.answersWhen.get(marker);
		if (queue == null) {
			queue = new ArrayList<Object>();
			this.answersWhen.put(marker, queue);
		}
		return queue;
	}

	/** Tool 이름과 인자를 번갈아 받은 것을 Tool 호출 목록으로 바꿉니다. */
	private static List<AssistantMessage.ToolCall> toolCalls(String... nameAndArguments) {
		if (nameAndArguments.length == 0 || nameAndArguments.length % 2 != 0) {
			throw new IllegalArgumentException("Tool 이름과 인자를 짝지어 적으십시오.");
		}
		List<AssistantMessage.ToolCall> calls = new ArrayList<AssistantMessage.ToolCall>();
		for (int i = 0; i < nameAndArguments.length; i += 2) {
			calls.add(new AssistantMessage.ToolCall("call-" + System.nanoTime() + "-" + i, "function", nameAndArguments[i], nameAndArguments[i + 1]));
		}
		return calls;
	}

	/** 오류 문장에 넣기에 너무 긴 글자는 앞부분만 남깁니다. */
	private static String shorten(String text) {
		if (text == null || text.length() <= 300) {
			return text;
		}
		return text.substring(0, 300) + "...";
	}

}

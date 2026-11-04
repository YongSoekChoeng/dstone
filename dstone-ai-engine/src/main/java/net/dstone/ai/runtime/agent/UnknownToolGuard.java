package net.dstone.ai.runtime.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import net.dstone.ai.common.exception.AgentContractException;

/**
 * <pre>
 * LLM이 받지 않은 Tool 이름으로 Tool을 부를 때, 실행 전체가 죽지 않고 "그런 Tool은 없다"는 답이 돌아가게 하는 포장지입니다.
 *
 * 왜 필요한가:
 * Spring AI(DefaultToolCallingManager)는 이 Agent에 붙은 Tool 중에 그 이름이 없으면
 * IllegalStateException("No ToolCallback found for tool name: ...")을 던집니다.
 * 이 예외는 시스템 오류라서 Workflow가 onFailure로 가지 못하고 실행 전체가 FAILED로 끝납니다.
 * 실제로 sm-workflow step13의 개발 Agent가 받지 않은 searchInFiles를 두 번이나 불러서 그렇게 끝났습니다(2026-11-04).
 *
 * 동작:
 *   모르는 이름이 없을 때        원래 ToolCallingManager에 그대로 넘깁니다.
 *   모르는 이름이 섞여 있을 때    그 이름에만 "실패: ... 쓸 수 있는 Tool: [...]"를 답하는 임시 Tool을 붙여서 넘깁니다.
 *                               같은 응답에 섞인 진짜 Tool 호출은 그대로 실행됩니다.
 *   그래도 계속 부를 때          그렇게 답한 횟수가 MAX_REFUSALS번에 이르면 AgentContractException을 던집니다
 *                               (Workflow에서는 step 실패가 되어 onFailure로 갑니다).
 *
 * 임시 Tool은 이번 한 번의 Tool 실행에만 쓰고 LLM에게 Tool 목록으로 보여 주지 않습니다.
 * 그래서 Agent가 쓸 수 있는 Tool이 늘어나지 않습니다(tools.allowed 화이트리스트는 그대로입니다).
 *
 * 몇 번 답했는지는 대화 기록에서 이 안내로 시작하는 Tool 결과를 세서 압니다.
 * 오래된 Tool 결과를 줄이는 ToolResultCompactionAdvisor도 앞부분 300자는 남기므로 이 안내는 지워지지 않습니다.
 *
 * Spring 빈으로 따로 만들지 않고, ConfigChatClient가 자동 설정의 ToolCallingManager 빈을 이것으로 감쌉니다.
 * </pre>
 */
public class UnknownToolGuard implements ToolCallingManager {

	/** bin/startApp.sh는 표준 출력을 버리므로 LogUtil.sysout 대신 로거로 남깁니다. */
	private static final Logger LOGGER = LoggerFactory.getLogger(UnknownToolGuard.class);

	/** 모르는 Tool 호출에 돌려주는 안내의 첫머리입니다. 대화 기록에서 몇 번 답했는지 셀 때도 씁니다. */
	static final String REFUSAL_PREFIX = "실패: 이번 작업에서 받은 Tool 중에 ";

	/** 모르는 Tool이라고 답하는 횟수입니다. 이만큼 알려 줘도 또 부르면 예외를 던집니다. */
	static final int MAX_REFUSALS = 3;

	private final ToolCallingManager delegate;

	/**
	 * @param delegate 실제 Tool 실행을 맡는 원래 ToolCallingManager입니다.
	 */
	public UnknownToolGuard(ToolCallingManager delegate) {
		this.delegate = delegate;
	}

	@Override
	public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
		return this.delegate.resolveToolDefinitions(chatOptions);
	}

	@Override
	public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
		ChatOptions options = prompt.getOptions();
		if (!(options instanceof ToolCallingChatOptions)) {
			return this.delegate.executeToolCalls(prompt, chatResponse);
		}
		ToolCallingChatOptions toolOptions = (ToolCallingChatOptions) options;
		List<String> knownNames = namesOf(toolOptions.getToolCallbacks());
		Set<String> unknownNames = unknownNames(chatResponse, knownNames);
		if (unknownNames.isEmpty()) {
			return this.delegate.executeToolCalls(prompt, chatResponse);
		}

		int refusals = countRefusals(prompt.getInstructions()) + 1;
		if (refusals > MAX_REFUSALS) {
			throw new AgentContractException("LLM이 받지 않은 Tool " + unknownNames + "을(를) 안내를 " + MAX_REFUSALS
				+ "번 받은 뒤에도 계속 불러서 중단했습니다. 쓸 수 있는 Tool: " + knownNames
				+ ". 이 Tool이 꼭 필요한 일이면 Agent의 tools.allowed에 넣고, 아니면 prompt에서 그 일을 빼거나 다른 Tool로 하라고 적으십시오.");
		}
		LOGGER.warn("dstone-ai-engine tool: LLM이 받지 않은 Tool " + unknownNames + "을(를) 불러서 실행하지 않고 안내를 돌려줍니다("
			+ refusals + "번째). 쓸 수 있는 Tool: " + knownNames);

		List<ToolCallback> callbacks = new ArrayList<>(toolOptions.getToolCallbacks());
		for (String name : unknownNames) {
			callbacks.add(new UnknownToolCallback(name, knownNames));
		}
		ChatOptions guarded = toolOptions.mutate().toolCallbacks(callbacks).build();
		return this.delegate.executeToolCalls(new Prompt(prompt.getInstructions(), guarded), chatResponse);
	}

	/** Tool 이름 목록을 만듭니다. */
	static List<String> namesOf(List<ToolCallback> callbacks) {
		List<String> names = new ArrayList<>();
		if (callbacks == null) {
			return names;
		}
		for (ToolCallback callback : callbacks) {
			names.add(callback.getToolDefinition().name());
		}
		return names;
	}

	/** LLM 응답의 Tool 호출 중에서 knownNames에 없는 이름만 골라 줍니다(순서 유지, 중복 없음). */
	static Set<String> unknownNames(ChatResponse chatResponse, List<String> knownNames) {
		Set<String> unknown = new LinkedHashSet<>();
		if (chatResponse == null) {
			return unknown;
		}
		for (Generation generation : chatResponse.getResults()) {
			AssistantMessage output = generation.getOutput();
			if (output == null || output.getToolCalls() == null) {
				continue;
			}
			for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
				if (!knownNames.contains(toolCall.name())) {
					unknown.add(toolCall.name());
				}
			}
		}
		return unknown;
	}

	/** 대화 기록에 이미 들어 있는 "모르는 Tool" 안내의 수를 셉니다. */
	static int countRefusals(List<Message> messages) {
		int count = 0;
		for (Message message : messages) {
			if (!(message instanceof ToolResponseMessage)) {
				continue;
			}
			for (ToolResponseMessage.ToolResponse response : ((ToolResponseMessage) message).getResponses()) {
				String data = response.responseData();
				if (data != null && data.startsWith(REFUSAL_PREFIX)) {
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * <pre>
	 * 모르는 Tool 이름 하나에 "그런 Tool은 없다"고만 답하는 임시 Tool입니다. 아무 일도 하지 않습니다.
	 * </pre>
	 */
	static class UnknownToolCallback implements ToolCallback {

		private final ToolDefinition definition;
		private final List<String> knownNames;

		UnknownToolCallback(String name, List<String> knownNames) {
			this.definition = ToolDefinition.builder()
				.name(name)
				.description("이 Agent가 받지 않은 Tool입니다.")
				.inputSchema("{\"type\":\"object\",\"properties\":{}}")
				.build();
			this.knownNames = knownNames;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.definition;
		}

		@Override
		public String call(String toolInput) {
			return REFUSAL_PREFIX + "'" + this.definition.name() + "'는 없습니다. 이 Tool은 실행되지 않았습니다. "
				+ "쓸 수 있는 Tool은 " + this.knownNames + "뿐입니다. 이 목록에 있는 이름만 부르십시오. "
				+ "목록의 Tool로 할 수 없는 일이면 하지 말고, 하지 못한 이유를 답에 적으십시오.";
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			return this.call(toolInput);
		}
	}

}

package net.dstone.ai.api.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceTool;

import net.dstone.ai.api.dto.ChatRequest;
import net.dstone.ai.common.consts.AiProvider;
import net.dstone.ai.config.ConfigTool;
import net.dstone.ai.governance.auth.CallerContext;
import net.dstone.ai.prompt.PromptTemplateRegistry;
import net.dstone.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

@Service
public class ChatService extends BaseService {

	@Autowired
	private ChatClient chatClient;

	@Autowired
	ConfigProperty configProperty;
	@Autowired
	private ConfigTool configTool;
	@Autowired
	private PromptTemplateRegistry promptTemplateRegistry;
	@Autowired
	private RagService ragService;
	@Autowired
	private AnthropicChatModel anthropicChatModel;
	@Autowired
	private ToolCallingManager toolCallingManager;

	public String chat(String sessionId, String caller, String providerId, ChatRequest request) {
		return this.buildSpec(sessionId, caller, providerId, request).call().content();
	}

	public Flux<String> chatStream(String sessionId, String caller, String providerId, ChatRequest request) {
		return this.buildSpec(sessionId, caller, providerId, request).stream().content();
	}

	private ChatClient.ChatClientRequestSpec buildSpec(String sessionId, String caller, String providerId, ChatRequest request){

		/************************************************************************
		<Spring AI chatClient의 기능 흐름>
		chatClient
		    │
		    ▼
			prompt() / prompt(String content) / prompt(Prompt prompt)
			    │
			    │  "이번 AI 요청을 구성하겠다"
			    ▼
			ChatClientRequestSpec
			    │
			    ├── system(...) => 시스템 프롬프트(AI의 역할/행동 방식/규칙을 정의)
			    ├── user(...) => 유저 프롬프트(실제 클라이언트가 요청한 내용)
			    │   user(u -> u
			    │       .text("고흐의 작품 중 {name}에 대해 알려줘.")
			    │       .param("name", "해바라기"))
			    ├── advisors(...) => Advisor는 AI 호출 전후에 개입해서 요청이나 응답을 보강
			    │       Chat Memory
			    │       RAG
			    │       Vector Search
			    │       Logging
			    │       Tool Calling
			    │       Security
			    │       Context Injection
			    ├── options(...)
			    ├── tools(...)
			    └── ...
			    │
			    ▼
			call() - 전체 응답을 받은 후 반환 / stream() - 응답이 시작되면 반환 시작
			    │
			    ▼
			ChatModel
			    │
			    ▼
			LLM
			    │
			    ▼
		ChatResponse
		    │
		    ▼
		content()
		************************************************************************/

		// 1. 요청 스펙 - 기본
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();

		// 2. 요청 스펙 - 세션 ID를 걸어서 지금까지의 대화 히스토리가 이어지도록 조치
		spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId));

		// 3. 요청 스펙 - 시스템 프롬프트로 적용
		// requiredTool 강제 1회 왕복(5번 스텝)에도 같은 시스템 프롬프트가 필요해서 렌더링 결과를 변수로
		// 남겨둔다 - PromptTemplateRegistry.render()를 두 번 호출하지 않기 위함이다.
		String systemPrompt = null;
		if (!StringUtil.isEmpty(request.promptName())) {
			// promptName이 있으면 그 템플릿(dstone.ai.prompt.version.{promptName}와 맵핑되는 src/main/resources/prompts/{promptName}/version.st 프롬프트)을 시스템 프롬프트로 적용한다.
			systemPrompt = this.promptTemplateRegistry.render(request.promptName(), request.variables());
			spec = spec.system(systemPrompt);
		}

		// 4. 요청 스펙 - RAG 적용
		if (Boolean.TRUE.equals(request.ragEnabled())) {
			// ragEnabled가 true로 온 요청에만 붙인다.
			spec = spec.advisors(this.ragService.getRagSpecAdvisor());
		}

		// 5. 요청 스펙 - Tool 적용 + 최종 user 메시지 결정
		String effectiveMessage = request.message();
		if (!StringUtil.isEmpty(request.requiredTool())) {
			AiProvider provider = AiProvider.fromPropertyValue(providerId);
			if (provider != AiProvider.ANTHROPIC) {
				throw new IllegalStateException("requiredTool(tool_choice 강제)은 provider=anthropic일 때만 지원합니다. 이번 요청의 provider=" + provider.propertyValue());
			}
			if (!this.configTool.toolNames().contains(request.requiredTool())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requiredTool[" + request.requiredTool() + "]이 등록된 Tool 목록에 없습니다: " + this.configTool.toolNames());
			}
			// requiredTool은 아래 forceToolOnce()가 별도의 1회 강제 왕복으로 먼저 처리하고, 그 결과를
			// 참고 문구로 붙인 메시지를 돌려준다 - 이유는 forceToolOnce() 주석 참고. 이후 본 대화에는
			// tool_choice를 강제하지 않고(auto) 필요하면 모델이 스스로 이 tool을 다시 불러 자기수정
			// 루프를 이어갈 수 있게 tool만 붙여준다.
			effectiveMessage = this.forceToolOnce(systemPrompt, request);
			spec = spec.toolCallbacks(configTool.toolCallbackProvider());
		} else if (Boolean.TRUE.equals(request.toolsEnabled())) {
			// 강제 호출 없이, 필요한지는 LLM이 알아서 판단하게 둔다(tool_choice=auto).
			spec = spec.toolCallbacks(configTool.toolCallbackProvider());
		}
		spec = spec.user(effectiveMessage);

		// 6. 요청 스펙 - 사용량 로깅 적용
		if (caller != null) {
			spec = spec.advisors(a -> a.param(CallerContext.ADVISOR_CONTEXT_KEY, caller));
		}

		return spec;
	}

	/**
	 * requiredTool(tool_choice 강제)을 ChatClient의 표준 tool 실행 루프(모델 호출 → tool 실행 → 결과를 붙여
	 * 재호출 → 반복)에 그대로 태우면 문제가 생긴다: Anthropic API는 매 요청마다 tool_choice를 독립적으로
	 * 평가하므로, 강제 옵션이 재호출 때도 그대로 남아있는 이 루프에서는 "결과를 돌려줘도 다음 라운드에 또
	 * 같은 tool을 강제로 부르는" 상태가 반복되다가 DefaultToolCallingManager의 tool당 최대 호출 수
	 * (기본 40)에 걸려 예외로 끝난다 - 실제로 재현된 증상이다. Spring AI 1.x에 있던
	 * internalToolExecutionEnabled(강제는 한 번만 걸고 이후는 auto로 풀어주는 옵션)가 2.x에서는 없어져서
	 * ChatOptions 레벨의 설정만으로는 고칠 수 없다.
	 *
	 * 그래서 requiredTool이 지정된 요청은 buildSpec()의 자동 루프를 타기 전에 이 메서드가 먼저 "1회 강제
	 * 왕복"만 별도로 처리한다: AnthropicChatModel.internalCall(...)로 루프를 돌지 않는 단일 API 호출을
	 * 보내 모델이 tool_use를 내놓게 만들고, ToolCallingManager로 그 tool을 정확히 한 번만 실제 실행한 뒤,
	 * 그 결과를 원래 메시지 뒤에 참고 문구로 붙인 문자열을 돌려준다. buildSpec()은 이 문자열을
	 * tool_choice를 강제하지 않은(auto) 상태로 user 메시지로 써서 이어간다. 즉 requiredTool의 원래
	 * 취지("최소 한 번은 반드시 호출")는 지키면서 무한 강제 반복만 없앤다.
	 *
	 * 이 1회 왕복은 ChatClient가 아니라 AnthropicChatModel을 직접 써서 ChatMemory/RAG 어드바이저를
	 * 거치지 않는다(세션 히스토리에는 이 뒤에 이어지는 진짜 응답부터 남는다) - requiredTool은 애초에
	 * provider=anthropic 한정의 검증/테스트용 기능이라 이 정도 트레이드오프는 감수한다.
	 */
	private String forceToolOnce(String systemPrompt, ChatRequest request) {
		List<Message> forcedMessages = new ArrayList<>();
		if (systemPrompt != null) {
			forcedMessages.add(new SystemMessage(systemPrompt));
		}
		forcedMessages.add(new UserMessage(request.message()));

		AnthropicChatOptions forcedOptions = AnthropicChatOptions.builder()
				.toolCallbacks(this.configTool.toolCallbackProvider().getToolCallbacks())
				.toolChoice(ToolChoice.ofTool(ToolChoiceTool.builder().name(request.requiredTool()).build()))
				.disableParallelToolUse(true)
				.build();

		Prompt forcedPrompt = new Prompt(forcedMessages, forcedOptions);
		ChatResponse forcedResponse = this.anthropicChatModel.internalCall(forcedPrompt, null);
		ToolExecutionResult toolResult = this.toolCallingManager.executeToolCalls(forcedPrompt, forcedResponse);

		String toolResultText = toolResult.conversationHistory().stream()
				.filter(ToolResponseMessage.class::isInstance)
				.map(ToolResponseMessage.class::cast)
				.flatMap(toolResponseMessage -> toolResponseMessage.getResponses().stream())
				.map(ToolResponseMessage.ToolResponse::responseData)
				.reduce((a, b) -> a + "\n" + b)
				.orElse("(도구 실행 결과 없음)");

		return request.message()
				+ "\n\n[참고: 위 요청을 " + request.requiredTool() + " 도구로 이미 한 번 검증했고 결과는 다음과 같습니다]\n" + toolResultText;
	}
}

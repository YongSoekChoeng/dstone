package net.dstone.ai.api.service;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
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
	
	public String chat(String sessionId, String caller, String providerId, ChatRequest request) {
		String answer = "";
		answer = this.getSpec(sessionId, caller, providerId, request).user(request.message()).call().content();
		return answer;
	}
	
	public Flux<String> chatStream(String sessionId, String caller, String providerId, ChatRequest request) {
		Flux<String> answer = null;
		answer = this.getSpec(sessionId, caller, providerId, request).user(request.message()).stream().content();
		return answer;
	}
	
	private ChatClient.ChatClientRequestSpec getSpec(String sessionId, String caller, String providerId, ChatRequest request){

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
		
		AiProvider provider = AiProvider.fromPropertyValue(providerId); 
		
		// 1. 요청 스펙 - 기본
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt(); 
		
		// 2. 요청 스펙 - 세션 ID를 걸어서 지금까지의 대화 히스토리가 이어지도록 조치
		spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId));

		// 3. 요청 스펙 - 시스템 프롬프트로 적용
		if (!StringUtil.isEmpty(request.promptName())) {
			// promptName이 있으면 그 템플릿(dstone.ai.prompt.version.{promptName}와 맵핑되는 src/main/resources/prompts/{promptName}/version.st 프롬프트)을 시스템 프롬프트로 적용한다.
			spec = spec.system(this.promptTemplateRegistry.render(request.promptName(), request.variables()));
		}

		// 4. 요청 스펙 - RAG 적용
		if (Boolean.TRUE.equals(request.ragEnabled())) {
			// ragEnabled가 true로 온 요청에만 붙인다.
			spec = spec.advisors(this.ragService.getRagSpecAdvisor());
		}

		// 5. 요청 스펙 - Tool 적용
		if (!StringUtil.isEmpty(request.requiredTool())) {
			if (provider != AiProvider.ANTHROPIC) {
				throw new IllegalStateException( "requiredTool(tool_choice 강제)은 provider=anthropic일 때만 지원합니다. 이번 요청의 provider=" + provider.propertyValue());
			}
			if (!configTool.toolNames().contains(request.requiredTool())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"requiredTool[" + request.requiredTool() + "]이 등록된 Tool 목록에 없습니다: " + configTool.toolNames());
			}
			spec = spec.toolCallbacks(configTool.toolCallbackProvider());
			spec = spec.options(
				AnthropicChatOptions.builder()
				.toolChoice(ToolChoice.ofTool(ToolChoiceTool.builder().name(request.requiredTool()).build()))
				.disableParallelToolUse(true)
			);
		}else if (Boolean.TRUE.equals(request.toolsEnabled())) {
			// 강제 호출 없이, 필요한지는 LLM이 알아서 판단하게 둔다(tool_choice=auto).
			spec = spec.toolCallbacks(configTool.toolCallbackProvider());
		}

		// 6. 요청 스펙 - 사용량 로깅 적용
		if (caller != null) {
			spec = spec.advisors(a -> a.param(CallerContext.ADVISOR_CONTEXT_KEY, caller));
		}
		
		return spec;
	}
}

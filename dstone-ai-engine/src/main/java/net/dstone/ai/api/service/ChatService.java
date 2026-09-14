package net.dstone.ai.api.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.ai.api.dto.ChatRequest;
import net.dstone.ai.capability.CapabilityDefinition;
import net.dstone.ai.capability.CapabilityRegistry;
import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.consts.AiProvider;
import net.dstone.ai.common.context.CallerContext;
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
	private CapabilityRegistry capabilityRegistry;
	
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
	
	/**
	 * Spring AI에서 AI 모델(예: ChatGPT, Claude 등)에게 보낼 요청(Request) 내용을 단계별로 조립하는 '명세서(Specification) 작성 도구' 메소드
	 * @param sessionId
	 * @param caller
	 * @param providerId
	 * @param request
	 * @return
	 */
	@SuppressWarnings("unused")
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
		
		// 1. 요청 스펙 - 기본. 프롬프트 및 메시지 구성 (Prompt & Messages)
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt(); 
		
		// 2. 요청 스펙 - 세션 ID를 걸어서 지금까지의 대화 히스토리가 이어지도록 조치
		spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId));

		// 2-1. capability가 왔으면 "미리 정해둔 기본 조합"을 가져온다 
		CapabilityDefinition capability = this.capabilityRegistry.resolve(request.capability());
		boolean ragEnabled = Boolean.valueOf(StringUtil.ifEmpty(request.ragEnabled(), "false"));
		boolean toolsEnabled = Boolean.valueOf(StringUtil.ifEmpty(request.toolsEnabled(), "true"));
		
		// 3. 요청 스펙 - 시스템 프롬프트로 적용
		if (!StringUtil.isEmpty(request.capability())) {
			// promptName이 있으면 그 템플릿(dstone.ai.prompt.version.{promptName}와 맵핑되는 src/main/resources/prompts/{promptName}/version.st 프롬프트)을 시스템 프롬프트로 적용한다.
			spec = spec.system(this.promptTemplateRegistry.render(request.capability(), request.variables()));
		}

		// 4. 요청 스펙 - RAG 적용
		if (ragEnabled) {
			spec = spec.advisors(this.ragService.getRagSpecAdvisor());
		}

		// 5. 요청 스펙 - Tool 적용
		if (toolsEnabled) {
			spec.tools(configTool.toolCallbackProvider());
		}

		// 6. 요청 스펙 - 사용량 로깅 적용
		if (caller != null) {
			spec = spec.advisors(a -> a.param(CallerContext.ADVISOR_CONTEXT_KEY, caller));
		}
		
		return spec;
	}
}

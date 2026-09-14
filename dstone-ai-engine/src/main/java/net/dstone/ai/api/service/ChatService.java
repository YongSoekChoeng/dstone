package net.dstone.ai.api.service;

import java.util.Map;
import java.util.function.Consumer;

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
import net.dstone.ai.process.ProcessExecutor;
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
	@Autowired
	private ProcessExecutor processExecutor;

	/**
	 * capability가 processName을 갖고 있으면(Phase 6, dstone.ai.process.definitions) 단일 호출 대신
	 * ProcessExecutor로 라우팅한다 - 나머지는 이전과 동일한 단일 호출 경로다.
	 */
	public String chat(String sessionId, String caller, String providerId, ChatRequest request) {
		CapabilityDefinition capability = this.capabilityRegistry.resolve(request.capability(), caller);
		if (!StringUtil.isEmpty(capability.processName())) {
			return this.processExecutor.run(capability.processName(), sessionId, caller, request.variables(),
				request.message());
		}
		return this.buildSpec(sessionId, caller, request).user(request.message()).call().content();
	}

	public Flux<String> chatStream(String sessionId, String caller, String providerId, ChatRequest request) {
		CapabilityDefinition capability = this.capabilityRegistry.resolve(request.capability(), caller);
		if (!StringUtil.isEmpty(capability.processName())) {
			throw new IllegalStateException("process 기반 capability[" + request.capability()
				+ "]는 아직 스트리밍(/chat/stream)을 지원하지 않습니다 - 일반 /chat을 사용하십시오.");
		}
		return this.buildSpec(sessionId, caller, request).user(request.message()).stream().content();
	}

	/**
	 * Process의 AGENT step(net.dstone.ai.process.ProcessExecutor)이 쓰는 단일 호출 진입점이다.
	 * ChatRequest/capability 없이 promptName을 직접 지정해 호출한다는 점만 buildSpec()과 다르다.
	 */
	public String runAgentStep(String sessionId, String caller, String promptName, Map<String, Object> variables,
			boolean toolsEnabled, boolean ragEnabled, String userMessage) {
		return this.buildSpec(sessionId, caller, promptName, variables, toolsEnabled, ragEnabled)
			.user(userMessage)
			.call()
			.content();
	}

	private ChatClient.ChatClientRequestSpec buildSpec(String sessionId, String caller, ChatRequest request) {
		boolean ragEnabled = Boolean.valueOf(StringUtil.ifEmpty(request.ragEnabled(), "false"));
		boolean toolsEnabled = Boolean.valueOf(StringUtil.ifEmpty(request.toolsEnabled(), "true"));
		return this.buildSpec(sessionId, caller, request.capability(), request.variables(), toolsEnabled, ragEnabled);
	}

	/**
	 * Spring AI에서 AI 모델(예: ChatGPT, Claude 등)에게 보낼 요청(Request) 내용을 단계별로 조립하는 '명세서(Specification) 작성 도구' 메소드
	 * @param sessionId
	 * @param caller
	 * @param promptName 시스템 프롬프트로 렌더링할 템플릿 이름(비우면 시스템 프롬프트 없이 진행)
	 * @param variables promptName 템플릿 렌더링용 변수
	 * @param toolsEnabled
	 * @param ragEnabled
	 * @return
	 */
	private ChatClient.ChatClientRequestSpec buildSpec(String sessionId, String caller, String promptName,
			Map<String, Object> variables, boolean toolsEnabled, boolean ragEnabled) {

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
		spec = spec.advisors(new Consumer<ChatClient.AdvisorSpec>() {
			@Override
			public void accept(ChatClient.AdvisorSpec a) {
				a.param(ChatMemory.CONVERSATION_ID, sessionId);
			}
		});

		// 3. 요청 스펙 - 시스템 프롬프트로 적용
		if (!StringUtil.isEmpty(promptName)) {
			// promptName이 있으면 그 템플릿(dstone.ai.prompt.version.{promptName}와 맵핑되는 src/main/resources/prompts/{promptName}/version.st 프롬프트)을 시스템 프롬프트로 적용한다.
			spec = spec.system(this.promptTemplateRegistry.render(promptName, variables));
		}

		// 4. 요청 스펙 - RAG 적용 (caller의 문서만 검색되도록 tenant 필터가 함께 걸린다)
		if (ragEnabled) {
			spec = spec.advisors(this.ragService.getRagSpecAdvisor(caller));
		}

		// 5. 요청 스펙 - Tool 적용 (caller의 Tool 화이트리스트를 통과한 것만 붙는다)
		if (toolsEnabled) {
			spec.tools(configTool.toolCallbackProvider(caller));
		}

		// 6. 요청 스펙 - 사용량 로깅 적용
		if (caller != null) {
			spec = spec.advisors(new Consumer<ChatClient.AdvisorSpec>() {
				@Override
				public void accept(ChatClient.AdvisorSpec a) {
					a.param(CallerContext.ADVISOR_CONTEXT_KEY, caller);
				}
			});
		}
		
		return spec;
	}
}

package net.dstone.ai.api;

import java.util.UUID;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceTool;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.dstone.ai.api.dto.ChatRequest;
import net.dstone.ai.api.dto.ChatResponse;
import net.dstone.ai.config.ConfigTool;
import net.dstone.ai.gateway.AiProvider;
import net.dstone.ai.gateway.GatewayProperties;
import net.dstone.ai.governance.auth.CallerContext;
import net.dstone.ai.prompt.PromptTemplateRegistry;
import net.dstone.ai.rag.retrieval.RetrievalService;
import net.dstone.common.biz.BaseController;
import net.dstone.common.utils.StringUtil;

/**
 * 이 엔진의 핵심 엔드포인트, POST /api/ai/chat을 처리한다. 옵션 하나 없이 message만 보내면 기본 채팅이
 * 되고, promptName/ragEnabled/toolsEnabled/requiredTool을 조합해서 시스템 프롬프트 적용, RAG-증강,
 * Tool 사용까지 한 요청 안에서 켜고 끌 수 있다. 각 옵션이 정확히 무엇을 하는지는 ChatRequest의
 * 필드별 설명을 참고하면 된다.
 */
@RestController
@RequestMapping("/api/ai/chat")
public class ChatController extends BaseController {

	private final ChatClient chatClient;
	private final GatewayProperties gatewayProperties;
	private final PromptTemplateRegistry promptTemplateRegistry;
	private final ObjectProvider<VectorStore> vectorStoreProvider;
	// RAG는 dstone.ai.rag.enabled=true일 때만 존재하는 빈이다. ChatController는 RAG를 안 쓰는 배포에서도
	// 항상 떠 있어야 하므로, 필수 의존성이 아니라 ObjectProvider로 있으면 쓰고 없으면 마는 식으로 받는다.
	private final ObjectProvider<RetrievalService> retrievalServiceProvider;
	// Tool은 RAG와 달리 외부 인프라 의존이 없어 항상 존재하는 빈이라 ObjectProvider가 필요 없다.
	private final ConfigTool configTool;

	public ChatController(ChatClient chatClient, GatewayProperties gatewayProperties,
			PromptTemplateRegistry promptTemplateRegistry, ObjectProvider<VectorStore> vectorStoreProvider,
			ObjectProvider<RetrievalService> retrievalServiceProvider, ConfigTool configTool) {
		this.chatClient = chatClient;
		this.gatewayProperties = gatewayProperties;
		this.promptTemplateRegistry = promptTemplateRegistry;
		this.vectorStoreProvider = vectorStoreProvider;
		this.retrievalServiceProvider = retrievalServiceProvider;
		this.configTool = configTool;
	}

	@PostMapping
	public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
		
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
		
		// message가 비어있으면 여기서 먼저 막는다.
		if (StringUtil.isEmpty(request.message())) {
			// 이대로 두면 Spring AI의 ChatClientRequestSpec.user()가 Assert.hasText()에서
			// IllegalArgumentException을 던지는데, 그보다 먼저 막아서 400과 함께 명확한 사유를 알려준다.
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}

		// 이번 요청에서 쓸 세션 ID를 정한다.
		HttpSession session = this.getSession(true);
		String sessionId = ( session.getAttribute(DEFAULT_SESSION_KEY) != null?session.getAttribute(DEFAULT_SESSION_KEY).toString() : ( StringUtil.isEmpty(request.sessionId())?UUID.randomUUID().toString() : request.sessionId() ) );

		// 세션 ID를 걸어서 지금까지의 대화 히스토리가 이어지도록 한 요청 스펙.
		ChatClient.ChatClientRequestSpec spec = this.chatClient.prompt().advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId));

		// governance.auth로 caller가 식별됐으면, observability.usage의 사용량 로깅이 쓸 수 있게 함께 넘겨준다.
		String caller = CallerContext.get(servletRequest);
		if (caller != null) {
			spec = spec.advisors(a -> a.param(CallerContext.ADVISOR_CONTEXT_KEY, caller));
		}

		// promptName이 있으면 그 템플릿을 시스템 프롬프트로 적용한다.
		if (!StringUtil.isEmpty(request.promptName())) {
			spec = spec.system(this.promptTemplateRegistry.render(request.promptName(), request.variables()));
		}

		// ragEnabled면 RAG 검색 결과를 컨텍스트에 끼워 넣는다.
		if (Boolean.TRUE.equals(request.ragEnabled())) {
			VectorStore vectorStore = this.vectorStoreProvider.getIfAvailable();
			if (vectorStore == null) {
				throw new IllegalStateException("RAG가 비활성화되어 있습니다(dstone.ai.rag.enabled=false 또는 미설정).");
			}
			RetrievalService retrievalService = this.retrievalServiceProvider.getObject();
			SearchRequest searchRequest = SearchRequest.builder()
				.topK(retrievalService.defaultTopK()) // 검색할 청크 수
				.similarityThreshold(retrievalService.defaultSimilarityThreshold()) // 실측 기반 조정값
				.build();
			spec = spec.advisors(QuestionAnswerAdvisor.builder(vectorStore).searchRequest(searchRequest).build());
		}

		// requiredTool이 있으면 그 Tool 하나를 반드시 호출하도록 강제한다.
		if (!StringUtil.isEmpty(request.requiredTool())) {
			// tool_choice=tool로 강제 호출하는 기능은 Anthropic Messages API 고유 기능이라 아직 gateway
			// abstraction을 타지 않는다 - provider가 바뀌면 여기서 바로 막아서, 모르는 새 auto로 조용히
			// 흘러가는 일이 없게 한다.
			if (this.gatewayProperties.activeProvider() != AiProvider.ANTHROPIC) {
				throw new IllegalStateException(
					"requiredTool(tool_choice 강제)은 spring.ai.model.chat=anthropic일 때만 지원합니다. 현재 provider="
						+ this.gatewayProperties.activeProvider().propertyValue());
			}
			if (!this.configTool.toolNames().contains(request.requiredTool())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"requiredTool[" + request.requiredTool() + "]이 등록된 Tool 목록에 없습니다: " + this.configTool.toolNames());
			}
			spec = spec.toolCallbacks(this.configTool.toolCallbackProvider())
				.options(AnthropicChatOptions.builder()
					.toolChoice(ToolChoice.ofTool(ToolChoiceTool.builder().name(request.requiredTool()).build()))
					.disableParallelToolUse(true));
		}else if (Boolean.TRUE.equals(request.toolsEnabled())) {
			// toolsEnabled만 켜져 있으면 강제 호출 없이, 필요한지는 LLM이 알아서 판단하게 둔다(tool_choice=auto).
			spec = spec.toolCallbacks(this.configTool.toolCallbackProvider());
		}

		String answer = spec.user(request.message()).call().content();
		return new ChatResponse(answer, this.gatewayProperties.activeProvider().propertyValue(), sessionId);
	}

}

package net.dstone.ai.api;

import java.util.UUID;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import reactor.core.publisher.Flux;

/**
 * 이 엔진의 핵심 엔드포인트, POST /api/ai/chat을 처리한다. 옵션 하나 없이 message만 보내면 기본 채팅이
 * 되고, promptName/ragEnabled/toolsEnabled/requiredTool을 조합해서 시스템 프롬프트 적용, RAG-증강,
 * Tool 사용까지 한 요청 안에서 켜고 끌 수 있다. 각 옵션이 정확히 무엇을 하는지는 ChatRequest의
 * 필드별 설명을 참고하면 된다.
 *
 * POST /api/ai/chat/stream은 같은 요청 계약에 응답만 text/event-stream(SSE)으로 토큰 단위 흘려보낸다.
 * Servlet 기반 Spring MVC 컨트롤러에서도 reactor-core가 클래스패스에 있으면(dstone-common이
 * spring-boot-starter-webflux를 물고 있어 항상 있음) Flux&lt;String&gt; 반환만으로 SSE가 동작한다 -
 * 별도로 WebFlux 런타임(Netty)으로 옮길 필요는 없다.
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
	// config.ConfigOllamaOverride가 dstone.ai.gateway.ollama-override.enabled=true일 때만 만드는 빈이라
	// ObjectProvider로 받는다 - 꺼져 있으면 provider=ollama 요청에 명확한 에러를 던지고, 켜져 있으면
	// 기본 chatClient(spring.ai.model.chat) 대신 이 빈으로 라우팅한다.
	private final ObjectProvider<ChatClient> ollamaChatClientProvider;

	public ChatController(ChatClient chatClient, GatewayProperties gatewayProperties,
			PromptTemplateRegistry promptTemplateRegistry, ObjectProvider<VectorStore> vectorStoreProvider,
			ObjectProvider<RetrievalService> retrievalServiceProvider, ConfigTool configTool,
			@Qualifier("ollamaChatClient") ObjectProvider<ChatClient> ollamaChatClientProvider) {
		this.chatClient = chatClient;
		this.gatewayProperties = gatewayProperties;
		this.promptTemplateRegistry = promptTemplateRegistry;
		this.vectorStoreProvider = vectorStoreProvider;
		this.retrievalServiceProvider = retrievalServiceProvider;
		this.configTool = configTool;
		this.ollamaChatClientProvider = ollamaChatClientProvider;
	}

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

	@PostMapping
	public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
		this.validateMessage(request);
		String sessionId = this.resolveSessionId(request);
		ResolvedChatClient resolved = this.resolveChatClient(request);
		String answer = this.buildRequestSpec(resolved, request, servletRequest, sessionId).call().content();
		return new ChatResponse(answer, resolved.provider().propertyValue(), sessionId);
	}

	/**
	 * chat()과 요청 계약은 동일하고, 응답만 LLM이 토큰을 생성하는 대로 text/event-stream으로 흘려보낸다.
	 * sessionId는 SSE 바디에 실어 보내지 않는다 - 호출자는 이미 자기가 보낸 sessionId(또는 서버가
	 * HttpSession에 들고 있는 값)를 알고 있어 되돌려줄 필요가 없다.
	 */
	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> chatStream(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
		this.validateMessage(request);
		String sessionId = this.resolveSessionId(request);
		ResolvedChatClient resolved = this.resolveChatClient(request);
		return this.buildRequestSpec(resolved, request, servletRequest, sessionId).stream().content();
	}

	private void validateMessage(ChatRequest request) {
		if (StringUtil.isEmpty(request.message())) {
			// 이대로 두면 Spring AI의 ChatClientRequestSpec.user()가 Assert.hasText()에서
			// IllegalArgumentException을 던지는데, 그보다 먼저 막아서 400과 함께 명확한 사유를 알려준다.
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}
	}

	private String resolveSessionId(ChatRequest request) {
		HttpSession session = this.getSession(true);
		return session.getAttribute(DEFAULT_SESSION_KEY) != null ? session.getAttribute(DEFAULT_SESSION_KEY).toString()
				: (StringUtil.isEmpty(request.sessionId()) ? UUID.randomUUID().toString() : request.sessionId());
	}

	private record ResolvedChatClient(ChatClient chatClient, AiProvider provider) {
	}

	/**
	 * 이번 요청이 실제로 어떤 ChatClient로 나가야 하는지 결정한다. 기본 provider(spring.ai.model.chat)
	 * 하나만 있던 예전과 달리, 지금은 요청마다 ChatRequest.provider로 다른 provider를 지정할 수 있어서
	 * "어느 빈을 쓸지"와 "그 결과로 최종 provider가 뭐라고 응답해야 하는지"를 한 번에 정리해서
	 * ResolvedChatClient로 묶어 돌려준다 - 호출하는 쪽(chat()/chatStream())이 이 두 값을 따로따로
	 * 판단할 필요가 없게 하기 위해서다.
	 *
	 * 아래 4단계를 순서대로(먼저 해당하는 조건에서 바로 return/throw) 검사한다:
	 *
	 * 1단계) provider가 아예 안 왔으면(null/빈 문자열) - 지금까지와 완전히 동일하게 동작해야 하므로
	 *        아무 판단도 하지 않고 기본 chatClient(spring.ai.model.chat으로 이미 결정된 빈)와 기본
	 *        provider를 그대로 돌려준다. 여기서 끝나면 2~4단계는 전혀 실행되지 않는다.
	 *
	 * 2단계) provider가 왔으면 먼저 문자열을 AiProvider enum으로 바꾼다(AiProvider.fromPropertyValue).
	 *        이 시점에 "anthropic/openai/ollama가 아닌 값"이 오면 그 메서드 안에서 바로
	 *        IllegalArgumentException이 터진다 - 오타 같은 걸 여기서 걸러주는 셈이다.
	 *
	 * 3단계) 변환된 requested가 현재 기본 provider(gatewayProperties.activeProvider())와 "우연히"
	 *        같다면, 굳이 별도 override 빈을 찾을 필요가 없다. 그냥 기본 chatClient를 그대로 쓰면
	 *        결과가 완전히 동일하기 때문이다(예: spring.ai.model.chat=ollama로 이미 떠 있는 상태에서
	 *        provider=ollama를 또 지정한 경우).
	 *
	 * 4단계) 3단계까지 안 걸렸다는 건 "기본 provider와는 다른 provider로 라우팅해야 한다"는 뜻이다.
	 *        지금 실제로 override 빈이 준비된 provider는 ollama뿐이라서:
	 *        - requested가 OLLAMA면 ollamaChatClientProvider(ConfigOllamaOverride가 조건부로 만든 빈,
	 *          dstone.ai.gateway.ollama-override.enabled=true일 때만 존재)를 ObjectProvider로 찾아본다.
	 *          존재하면 그 빈 + AiProvider.OLLAMA로 완성해서 돌려준다. 존재하지 않으면(설정이 꺼져
	 *          있으면) "그냥 기본으로 조용히 넘어가는" 대신 IllegalStateException으로 바로 실패시켜서,
	 *          호출한 쪽이 자기가 요청한 provider가 실제로는 적용되지 않았다는 걸 모르고 넘어가는
	 *          일이 없게 한다.
	 *        - requested가 OLLAMA도, 기본 provider도 아니면(예: 기본은 anthropic인데 openai를 요청한
	 *          경우) override 빈 자체가 없으므로 더 볼 것도 없이 400 Bad Request로 바로 막는다.
	 */
	private ResolvedChatClient resolveChatClient(ChatRequest request) {

		// 1단계: provider 미지정 -> 기존 동작과 100% 동일(기본 chatClient + 기본 provider).
		if (StringUtil.isEmpty(request.provider())) {
			return new ResolvedChatClient(this.chatClient, this.gatewayProperties.activeProvider());
		}

		// 2단계: 문자열 -> enum 변환. 지원하지 않는 값이면 여기서 바로 예외가 던져진다.
		AiProvider requested = AiProvider.fromPropertyValue(request.provider());

		// 3단계: 요청한 provider가 이미 기본 provider와 같다 -> override 빈을 찾을 필요 없이 기본 그대로.
		if (requested == this.gatewayProperties.activeProvider()) {
			return new ResolvedChatClient(this.chatClient, requested);
		}

		// 4단계: 기본과 다른 provider로 라우팅해야 한다. 지금은 ollama override만 실제로 존재한다.
		if (requested == AiProvider.OLLAMA) {
			ChatClient override = this.ollamaChatClientProvider.getIfAvailable();
			if (override == null) {
				// override 빈 자체가 없다(껐거나 설정 안 함) - 조용히 기본 provider로 넘기지 않고,
				// 요청한 provider가 실제로 적용되지 않는다는 걸 명확한 에러로 바로 알려준다.
				throw new IllegalStateException(
					"provider=ollama override가 비활성화되어 있습니다(dstone.ai.gateway.ollama-override.enabled=false 또는 미설정).");
			}
			return new ResolvedChatClient(override, AiProvider.OLLAMA);
		}

		// ollama도 아니고 기본 provider도 아닌 provider 요청 - override 빈이 없어 지원 불가.
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
			"provider[" + request.provider() + "]는 override를 지원하지 않습니다. 기본 provider("
				+ this.gatewayProperties.activeProvider().propertyValue() + ")와 같을 때만, 또는 ollama override가 켜져 있을 때만 지정할 수 있습니다.");
	}

	private ChatClient.ChatClientRequestSpec buildRequestSpec(ResolvedChatClient resolved, ChatRequest request,
			HttpServletRequest servletRequest, String sessionId) {

		// 세션 ID를 걸어서 지금까지의 대화 히스토리가 이어지도록 한 요청 스펙.
		ChatClient.ChatClientRequestSpec spec = resolved.chatClient().prompt().advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId));

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
			// abstraction을 타지 않는다 - provider가 바뀌면(기본 provider든 이번 요청의 override든) 여기서
			// 바로 막아서, 모르는 새 auto로 조용히 흘러가는 일이 없게 한다.
			if (resolved.provider() != AiProvider.ANTHROPIC) {
				throw new IllegalStateException(
					"requiredTool(tool_choice 강제)은 provider=anthropic일 때만 지원합니다. 이번 요청의 provider="
						+ resolved.provider().propertyValue());
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

		// ollamaModel이 있으면(provider=ollama일 때만 의미 있다) ollamaChatClient 빈에 고정된 기본 모델
		// (dstone.ai.gateway.ollama-override.model) 대신 이번 요청만 그 모델로 호출한다. 존재하지 않거나
		// 채팅을 지원하지 않는 모델명이면 여기서 막지 않고 Ollama가 반환하는 에러를 그대로 흘려보낸다.
		if (resolved.provider() == AiProvider.OLLAMA && !StringUtil.isEmpty(request.ollamaModel())) {
			spec = spec.options(ChatOptions.builder().model(request.ollamaModel()));
		}

		return spec.user(request.message());
	}

}

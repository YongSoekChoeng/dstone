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

import jakarta.servlet.http.HttpSession;
import net.dstone.ai.api.dto.ChatRequest;
import net.dstone.ai.api.dto.ChatResponse;
import net.dstone.ai.config.ConfigTool;
import net.dstone.ai.gateway.AiProvider;
import net.dstone.ai.gateway.GatewayProperties;
import net.dstone.ai.prompt.PromptTemplateRegistry;
import net.dstone.ai.rag.retrieval.RetrievalService;
import net.dstone.common.biz.BaseController;
import net.dstone.common.utils.StringUtil;

@RestController
@RequestMapping("/api/ai/chat")
public class ChatController extends BaseController {

	private final ChatClient chatClient;
	private final GatewayProperties gatewayProperties;
	private final PromptTemplateRegistry promptTemplateRegistry;
	private final ObjectProvider<VectorStore> vectorStoreProvider;
	// RAG 는 dstone.ai.rag.enabled=true일 때만 존재하는 빈이라, ChatController 컨트롤러는 항상 올라와 있어야 하므로 필수 의존성이 아니라 ObjectProvider로 선택 주입받는다.
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
	public ChatResponse chat(@RequestBody ChatRequest request) {
		
		// message 없이 호출했을 때 처리.
		if (StringUtil.isEmpty(request.message())) {
			// message 없이 호출하면 Spring AI의 ChatClientRequestSpec.user()가 Assert.hasText()에서
			// IllegalArgumentException을 던지는데, 여기서 먼저 막아 400과 함께 명확한 사유를 준다.
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}

		// 세션ID 생성
		HttpSession session = this.getSession(true);
		String sessionId = ( session.getAttribute(DEFAULT_SESSION_KEY) != null?session.getAttribute(DEFAULT_SESSION_KEY).toString() : ( StringUtil.isEmpty(request.sessionId())?UUID.randomUUID().toString() : request.sessionId() ) );

		// 세션ID 가 진행한 대화 누적치 가 적용된 요청스펙
		ChatClient.ChatClientRequestSpec spec = this.chatClient.prompt().advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId));

		// 현재 요청 프롬프트 가 적용된 요청스펙
		if (!StringUtil.isEmpty(request.promptName())) {
			spec = spec.system(this.promptTemplateRegistry.render(request.promptName(), request.variables()));
		}

		// RAG 가 적용된 요청스펙
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

		// Tool 이 적용된 요청스펙
		if (!StringUtil.isEmpty(request.requiredTool())) {
			// 강제옵션(Anthropic은 지원하지 않음)
			// tool_choice=tool 강제는 Anthropic Messages API 고유 기능이라 gateway abstraction을 아직 안 탄다 - provider가 바뀌면 여기서 바로 막아 조용히 auto로 흘러가는 걸 방지한다.
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
			spec = spec.toolCallbacks(this.configTool.toolCallbackProvider());
		}

		String answer = spec.user(request.message()).call().content();
		return new ChatResponse(answer, this.gatewayProperties.activeProvider().propertyValue(), sessionId);
	}

}

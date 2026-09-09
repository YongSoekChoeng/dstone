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
	// RAG(Phase 2)는 dstone.ai.rag.enabled=true일 때만 존재하는 빈이라, 이 컨트롤러는 항상 켜져 있어야 하므로
	// (Phase 0/1만 쓰는 배포에서도 기동돼야 함) 필수 의존성이 아니라 ObjectProvider로 선택 주입받는다.
	private final ObjectProvider<VectorStore> vectorStoreProvider;
	private final ObjectProvider<RetrievalService> retrievalServiceProvider;
	// Tool(Phase 3)은 RAG와 달리 외부 인프라 의존이 없어 항상 존재하는 빈이라 ObjectProvider가 필요 없다.
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
		if (StringUtil.isEmpty(request.message())) {
			// message 없이 호출하면 Spring AI의 ChatClientRequestSpec.user()가 Assert.hasText()에서
			// IllegalArgumentException을 던지는데, 이게 그대로 500으로 나가버려 원인을 알 수 없었다.
			// 여기서 먼저 막아 400과 함께 명확한 사유를 준다.
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}

		String sessionId = StringUtil.isEmpty(request.sessionId()) ? UUID.randomUUID().toString() : request.sessionId();

		ChatClient.ChatClientRequestSpec spec = this.chatClient.prompt()
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId));

		if (!StringUtil.isEmpty(request.promptName())) {
			spec = spec.system(this.promptTemplateRegistry.render(request.promptName(), request.variables()));
		}

		if (Boolean.TRUE.equals(request.ragEnabled())) {
			VectorStore vectorStore = this.vectorStoreProvider.getIfAvailable();
			if (vectorStore == null) {
				throw new IllegalStateException("RAG가 비활성화되어 있습니다(dstone.ai.rag.enabled=false 또는 미설정).");
			}
			RetrievalService retrievalService = this.retrievalServiceProvider.getObject();
			SearchRequest searchRequest = SearchRequest.builder()
				.topK(retrievalService.defaultTopK())
				.similarityThreshold(retrievalService.defaultSimilarityThreshold())
				.build();
			spec = spec.advisors(QuestionAnswerAdvisor.builder(vectorStore).searchRequest(searchRequest).build());
		}

		if (!StringUtil.isEmpty(request.requiredTool())) {
			// tool_choice=tool 강제는 Anthropic Messages API 고유 기능이라 gateway abstraction을
			// 아직 안 탄다 - provider가 바뀌면 여기서 바로 막아 조용히 auto로 흘러가는 걸 방지한다.
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
		}
		else if (Boolean.TRUE.equals(request.toolsEnabled())) {
			spec = spec.toolCallbacks(this.configTool.toolCallbackProvider());
		}

		String answer = spec.user(request.message()).call().content();
		return new ChatResponse(answer, this.gatewayProperties.activeProvider().propertyValue(), sessionId);
	}

}

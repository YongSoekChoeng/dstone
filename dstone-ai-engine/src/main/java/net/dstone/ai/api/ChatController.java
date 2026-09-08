package net.dstone.ai.api;

import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.ai.api.dto.ChatRequest;
import net.dstone.ai.api.dto.ChatResponse;
import net.dstone.ai.gateway.GatewayProperties;
import net.dstone.ai.prompt.PromptTemplateRegistry;
import net.dstone.common.biz.BaseController;
import net.dstone.common.utils.StringUtil;

@RestController
@RequestMapping("/api/ai/chat")
public class ChatController extends BaseController {

	private final ChatClient chatClient;
	private final GatewayProperties gatewayProperties;
	private final PromptTemplateRegistry promptTemplateRegistry;

	public ChatController(ChatClient chatClient, GatewayProperties gatewayProperties,
			PromptTemplateRegistry promptTemplateRegistry) {
		this.chatClient = chatClient;
		this.gatewayProperties = gatewayProperties;
		this.promptTemplateRegistry = promptTemplateRegistry;
	}

	@PostMapping
	public ChatResponse chat(@RequestBody ChatRequest request) {
		String sessionId = StringUtil.isEmpty(request.sessionId()) ? UUID.randomUUID().toString() : request.sessionId();

		ChatClient.ChatClientRequestSpec spec = this.chatClient.prompt()
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId));

		if (!StringUtil.isEmpty(request.promptName())) {
			spec = spec.system(this.promptTemplateRegistry.render(request.promptName(), request.variables()));
		}

		String answer = spec.user(request.message()).call().content();
		return new ChatResponse(answer, this.gatewayProperties.activeProvider().propertyValue(), sessionId);
	}

}

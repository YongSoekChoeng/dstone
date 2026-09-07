package net.dstone.ai.api;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.ai.api.dto.ChatRequest;
import net.dstone.ai.api.dto.ChatResponse;
import net.dstone.ai.gateway.GatewayProperties;
import net.dstone.common.biz.BaseController;

@RestController
@RequestMapping("/api/ai/chat")
public class ChatController extends BaseController {

	private final ChatClient chatClient;
	private final GatewayProperties gatewayProperties;

	public ChatController(ChatClient chatClient, GatewayProperties gatewayProperties) {
		this.chatClient = chatClient;
		this.gatewayProperties = gatewayProperties;
	}

	@PostMapping
	public ChatResponse chat(@RequestBody ChatRequest request) {
		String answer = chatClient.prompt().user(request.message()).call().content();
		return new ChatResponse(answer, this.gatewayProperties.activeProvider().propertyValue());
	}

}

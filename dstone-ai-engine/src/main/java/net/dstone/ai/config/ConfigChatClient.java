package net.dstone.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 1(gateway 패키지)에서 provider별 ChatModel을 주입받아 이 지점에서 교체하도록 확장한다.
 */
@Configuration
public class ConfigChatClient {

	@Bean
	public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
		return chatClientBuilder.build();
	}

}

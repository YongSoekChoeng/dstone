package net.dstone.ai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.ai.session.RedisChatMemoryRepository;
import net.dstone.common.core.BaseObject;

/**
 * session(Phase 1): Redis에 저장된 대화 히스토리를 최근 N개 메시지로 잘라(windowing)
 * 제공하는 ChatMemory 빈. ConfigChatClient가 이 빈을 MessageChatMemoryAdvisor로 감싸
 * ChatClient에 기본 advisor로 붙인다.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
public class ConfigChatMemory extends BaseObject {

	@Value("${dstone.ai.session.max-messages:20}")
	private int maxMessages;

	@Bean
	public ChatMemory chatMemory(RedisChatMemoryRepository redisChatMemoryRepository) {
		return MessageWindowChatMemory.builder()
			.chatMemoryRepository(redisChatMemoryRepository)
			.maxMessages(this.maxMessages)
			.build();
	}

}

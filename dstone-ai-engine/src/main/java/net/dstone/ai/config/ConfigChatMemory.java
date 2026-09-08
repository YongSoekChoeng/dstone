package net.dstone.ai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.ai.session.RedisChatMemoryRepository;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * session(Phase 1): Redis에 저장된 대화 히스토리를 최근 N개 메시지로 잘라(windowing)
 * 제공하는 ChatMemory 빈. ConfigChatClient가 이 빈을 MessageChatMemoryAdvisor로 감싸
 * ChatClient에 기본 advisor로 붙인다.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
public class ConfigChatMemory extends BaseObject {

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	@Bean
	public ChatMemory chatMemory(RedisChatMemoryRepository redisChatMemoryRepository) {
		String maxMessages = this.configProperty.getProperty("dstone.ai.session.max-messages");
		return MessageWindowChatMemory.builder()
			.chatMemoryRepository(redisChatMemoryRepository)
			.maxMessages(StringUtil.isEmpty(maxMessages) ? 20 : Integer.parseInt(maxMessages))
			.build();
	}

}

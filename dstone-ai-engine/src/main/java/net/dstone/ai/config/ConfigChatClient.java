package net.dstone.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.ai.governance.guardrail.PiiGuardrailAdvisor;
import net.dstone.ai.observability.usage.UsageLoggingAdvisor;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

@Configuration
public class ConfigChatClient {

	/**
	 * session.RedisChatMemorySession는 spring.data.redis.enabled=true일 때만 빈으로 등록된다.
	 * @ConditionalOnMissingBean 는 ChatMemoryRepository 가 null 일 경우에도(RedisChatMemorySession이 등록되지 않았더라도)
	 * Spring AI가 기본 제공하는 프로세스 메모리 구현체로 대체한다 
	 * 재시작/다중 인스턴스 간 공유는 안 되지만 로컬/단일 인스턴스 개발 환경에서는 그걸로 충분하다. 
	 * 이 덕분에 아래 chatMemory()는 ChatMemoryRepository가 "혹시 없을 수도 있다"는 걱정 없이 평범하게 주입받기만 하면 된다.
	 */
	@Bean
	@ConditionalOnMissingBean(ChatMemoryRepository.class)
	ChatMemoryRepository chatMemoryRepository() {
		return new InMemoryChatMemoryRepository();
	}

	/** dstone.ai.session.max-messages(기본 20)만큼만 최근 대화를 유지하는 슬라이딩 윈도우. */
	@Bean
	ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository, ConfigProperty configProperty) {
		String maxMessages = configProperty.getProperty("dstone.ai.session.max-messages");
		return MessageWindowChatMemory.builder()
			.chatMemoryRepository(chatMemoryRepository)
			.maxMessages(StringUtil.isEmpty(maxMessages) ? 20 : Integer.parseInt(maxMessages))
			.build();
	}

	/*************************************************************************************
	Spring AI에서 ChatClient는 기본 생성자가 존재하지 않으며, 단독으로 @Autowired를 통해 직접 주입받을 수 없음.
	Spring AI가 내장 자동 설정(Auto-configuration)으로 빈(Bean) 등록을 해주는 ChatClient.Builder를 주입받아 생성하는 것이 공식 표준 구현 방식.

	advisor 체인순서(advisor1, advisor2, advisor3, ...) : 가장 바깥쪽(advisor3)이 먼저 동작하고 순차적으로(advisor2, advisor1)이 동작.
	*************************************************************************************/
	@Bean
	ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, UsageLoggingAdvisor usageLoggingAdvisor, PiiGuardrailAdvisor piiGuardrailAdvisor) {
		return builder
			.defaultAdvisors(
				usageLoggingAdvisor // 사용량기록
				, piiGuardrailAdvisor // 개인정보보호
				, MessageChatMemoryAdvisor.builder(chatMemory).build() // 세션메모리
			).build();
	}
}

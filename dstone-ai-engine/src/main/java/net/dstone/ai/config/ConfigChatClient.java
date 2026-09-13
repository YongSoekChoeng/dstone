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
	 * session.RedisChatMemoryRepository는 spring.data.redis.enabled=true일 때만 빈으로 등록된다
	 * (@ConditionalOnProperty). Redis를 꺼둔 환경에서도 세션 기억 기능 자체가 기동 실패 없이 동작하도록,
	 * 그 빈이 없을 때만 Spring AI가 기본 제공하는 프로세스 메모리 구현체로 대체한다 - 재시작/다중 인스턴스
	 * 간 공유는 안 되지만 로컬/단일 인스턴스 개발 환경에서는 그걸로 충분하다. 이 덕분에 아래
	 * chatMemory()는 ChatMemoryRepository가 "혹시 없을 수도 있다"는 걱정 없이 평범하게 주입받기만 하면 된다.
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

	advisor 체인 순서(order 오름차순 = 바깥쪽→안쪽): UsageLoggingAdvisor(HIGHEST_PRECEDENCE) →
	PiiGuardrailAdvisor(HIGHEST_PRECEDENCE+1) → MessageChatMemoryAdvisor(기본값
	DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER). PII 마스킹이 먼저 끝나야 Redis 대화 히스토리에도 마스킹된
	텍스트가 저장되고, Usage 로깅이 가장 바깥쪽이어야 PII 처리/메모리 조회까지 포함한 전체 지연시간과
	guardrail REJECT 예외까지 잡아낼 수 있다 - 각 Advisor 클래스 자체의 javadoc 설계와 그대로 일치한다.
	*************************************************************************************/
	@Bean
	ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory,
			UsageLoggingAdvisor usageLoggingAdvisor, PiiGuardrailAdvisor piiGuardrailAdvisor) {
		return builder
			.defaultAdvisors(usageLoggingAdvisor, piiGuardrailAdvisor,
				MessageChatMemoryAdvisor.builder(chatMemory).build())
			.build();
	}
}

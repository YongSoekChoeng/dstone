package net.dstone.ai.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * 엔진 전체가 공유하는 단일 ChatClient(Provider는 dstone.ai.model.chat=anthropic 하나)를 조립한다.
 *
 * governance(PII/민감어 가드레일, 사용량 로깅·쿼터)는 이번 재설계 범위에서 뺐다 - 대신 List&lt;Advisor&gt;를
 * 그대로 주입받게 해서, 나중에 governance 모듈이 @Bean Advisor를 하나 추가하기만 하면(Spring이 다건
 * 빈을 자동으로 이 리스트에 모아준다) 이 클래스를 고치지 않고도 Advisor 체인에 끼워 넣을 수 있다.
 * 지금은 등록된 Advisor 빈이 없으므로 이 리스트는 항상 비어 있다.
 */
@Configuration
public class ConfigChatClient {

	/**
	 * session.RedisChatMemorySession은 spring.data.redis.enabled=true일 때만 빈으로 등록된다.
	 * @ConditionalOnMissingBean으로, Redis가 꺼져 있어도(ChatMemoryRepository 빈이 없어도) 프로세스
	 * 메모리 기반 기본 구현으로 대체해 chatMemory()가 항상 정상적으로 주입받을 수 있게 한다(재시작/다중
	 * 인스턴스 간 공유는 안 되지만 로컬 개발 환경에서는 충분하다).
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
	Spring AI에서 ChatClient는 기본 생성자가 없고 단독 @Autowired로 직접 주입받을 수 없다 - 자동설정이
	등록해주는 ChatClient.Builder를 주입받아 조립하는 게 공식 표준 방식이다.

	advisor 체인 순서(advisor1, advisor2, advisor3, ...): 가장 바깥쪽(리스트 마지막)이 먼저 동작하고
	순차적으로 안쪽으로 진행한다.
	*************************************************************************************/
	@Bean
	ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, List<Advisor> governanceAdvisors) {
		List<Advisor> advisors = new ArrayList<>(governanceAdvisors);
		advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build()); // 세션 메모리 - 항상 마지막(가장 안쪽)
		return builder.defaultAdvisors(advisors.toArray(new Advisor[0])).build();
	}
}

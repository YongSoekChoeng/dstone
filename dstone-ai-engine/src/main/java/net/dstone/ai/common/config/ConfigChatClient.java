package net.dstone.ai.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * 엔진 전체가 공유하는 단일 ChatClient(Provider는 dstone.ai.model.chat=anthropic 하나)를 조립한다.
 *
 * governance(PII/민감어 가드레일, 사용량 로깅·쿼터)는 이번 재설계 범위에서 뺐다 - 대신 List<Advisor>를 그대로 주입받게 해서, 나중에 governance 모듈이 @Bean
 * Advisor를 하나 추가하기만 하면(Spring이 다건 빈을 자동으로 이 리스트에 모아준다) 이 클래스를 고치지 않고도 Advisor 체인에 끼워 넣을 수 있다. 지금은 등록된 Advisor 빈이 없으므로 이
 * 리스트는 항상 비어 있다.
 */
@Configuration
public class ConfigChatClient {
	
	@Autowired
	ConfigProperty configProperty;
	

	/**
	 * <pre>
	 * session.RedisChatMemorySession(ChatMemoryRepository의 구현체)은 spring.data.redis.enabled=true일 때만 빈으로 등록된다. 그리고 파라메터로
	 * 사용되는 ChatMemoryRepository chatMemoryRepository 는 빈으로 등록된 RedisChatMemorySession 를 가리키므로 구동되는데 문제가 없다. 다만,
	 * spring.data.redis.enabled=false일 때(ChatMemoryRepository 가 등록되어있지 않을때) @ConditionalOnMissingBean 을 활용하여
	 * InMemoryChatMemoryRepository 를 등록함으로써 chatMemory()가 항상 정상적으로 주입받을 수 있게 한다.
	 * InMemoryChatMemoryRepository 는 재시작/다중 인스턴스 간 공유가 안된다는 점을 유의.
	 * </pre>
	 */
    @Bean
    public ChatMemoryRepository chatMemoryRepository(ObjectProvider<ChatMemoryRepository> repositoryProvider) {
        // getIfAvailable()은 빈이 있으면 가져오고, 없으면 null을 반환합니다.
        ChatMemoryRepository existingRepository = repositoryProvider.getIfAvailable();
        if (existingRepository != null) {
            // 이미 등록된 빈이 있으면 그것을 그대로 사용
            return existingRepository;
        } else {
            // 없으면 새로 생성
            return new InMemoryChatMemoryRepository();
        }
    }

	/**
	 * <pre>
	 * dstone.ai.session.max-messages(기본 20)만큼만 최근 대화를 유지하는 슬라이딩 윈도우.
	 * </pre>
	 *
	 * @param chatMemoryRepository 대화 내역을 저장할 저장소
	 * @param configProperty       설정값을 조회할 객체
	 */
	@Bean
	ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
		String maxMessages = configProperty.getProperty("dstone.ai.session.max-messages");
		int intMaxMessages = Integer.parseInt(StringUtil.ifEmpty(maxMessages, "20"));
		ChatMemory chatMemory = MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).maxMessages(intMaxMessages).build();
		return chatMemory;
	}

	/**
	 * <pre>
	 * 시스템 디폴트 Advisor를 등록하는 메소드.
	 * </pre>
	 *
	 * @param chatMemory 세션별 대화 내역을 담당할 메모리
	 * @param advisor    목록(현재는 비어 있음). 스프링에서 List<T> 타입은 단일 Bean과 다르게 빈 List로 주입 하므로 문제 없음.
	 */
	@Bean
	List<Advisor> defaultAdvisors(ChatMemory chatMemory, List<Advisor> advisors) {
		List<Advisor> advisorList = new ArrayList<>(advisors);
		
		// 1. 로깅하는 Advisor 등록
		SimpleLoggerAdvisor simpleLoggerAdvisor = SimpleLoggerAdvisor.builder().build();
		advisorList.add(simpleLoggerAdvisor);

		// 2. 세션별 대화 내역을 기억하게 저장하는 Advisor 등록
		MessageChatMemoryAdvisor mssageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		advisorList.add(mssageChatMemoryAdvisor);
		
		return advisorList;
	}

	/**
	 * <pre>
	 * ChatClient 생성 메소드.
	 * 
	 * - Spring AI에서 ChatClient는 기본 생성자가 없고 단독 @Autowired로 직접 주입받을 수 없다. 자동설정이 등록해주는 ChatClient.Builder를 주입받아 조립하는 게 공식 표준방식.
	 * - org.springframework.ai.chat.client.advisor.api.BaseAdvisor 를 구현하는 모든 Advisor들을 Spring이 자동으로 모아서 넘겨준다.
	 * - advisor 체인 순서(advisor1, advisor2, advisor3, ...): 가장 바깥쪽(리스트 마지막)이 먼저 동작하고 순차적으로 안쪽으로 진행한다.
	 * </pre>
	 * 
	 * @param builder    ChatClient를 조립할 빌더
	 */
	@Bean
	ChatClient chatClient(ChatClient.Builder builder, List<Advisor> defaultAdvisors) {
		return builder.defaultAdvisors(defaultAdvisors.toArray(new Advisor[0])).build();
	}
	
}

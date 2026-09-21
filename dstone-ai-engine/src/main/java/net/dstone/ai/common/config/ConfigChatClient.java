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
import org.springframework.core.Ordered;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * dstone-ai-engine 안의 모든 코드가 LLM을 부를 때 공통으로 쓰는 ChatClient 하나를 만들어주는 설정
 * 클래스입니다. ChatClient는 Spring AI에서 "LLM한테 말을 걸 때 쓰는 창구"라고 생각하면 됩니다.
 * 이 엔진은 provider(dstone.ai.model.chat 설정값 - 예: anthropic, ollama)를 하나만 정해서 쓰는데,
 * 그 provider로 가는 ChatClient를 여기서 딱 한 번 조립해서 스프링 빈으로 등록해 둡니다.
 *
 * PII 가리기, 금지어 검사, 사용량 로깅·쿼터 같은 "거버넌스" 기능은 이 클래스가 직접 처리하지
 * 않습니다. 대신 이 클래스는 List&lt;Advisor&gt;(LLM 요청/응답을 가로채서 처리할 수 있는 부가 기능
 * 목록)를 그대로 주입받는 구조로 되어 있습니다. 그래서 나중에 거버넌스 기능이 필요해지면, 새로운
 * 모듈에서 @Bean으로 Advisor 하나만 추가하면 됩니다(스프링이 등록된 Advisor 빈들을 자동으로 모아서
 * 이 리스트에 넣어주기 때문에, 이 클래스 코드를 고칠 필요가 없습니다). 지금은 그렇게 추가로 등록된
 * Advisor 빈이 하나도 없으므로, 이 리스트는 비어 있는 상태로 시작합니다.
 */
@Configuration
public class ConfigChatClient {
	
	@Autowired
	ConfigProperty configProperty;
	

	/**
	 * <pre>
	 * 대화 내용을 어디에 저장할지 결정하는 저장소(ChatMemoryRepository)를 준비해 줍니다.
	 *
	 * dstone.ai.session.redis.enabled 설정이 true이면 session.RedisChatMemorySession이라는
	 * 클래스가 이미 이 저장소 역할의 스프링 빈으로 등록되어 있습니다. 그 값이 true라면 이 메소드는
	 * 그 빈을 그대로 가져다 씁니다.
	 *
	 * 반대로 그 설정이 false여서 아무 빈도 등록되어 있지 않다면(즉 Redis를 안 쓰는 환경이라면), 이
	 * 메소드가 대신 InMemoryChatMemoryRepository(자바 메모리 위에만 대화 내용을 저장하는 간단한
	 * 저장소)를 새로 만들어서 씁니다. 이렇게 해두면 Redis가 있든 없든 chatMemory() 메소드는 항상
	 * 문제없이 저장소를 주입받을 수 있습니다.
	 *
	 * 다만 InMemoryChatMemoryRepository를 쓸 경우, 서버를 재시작하면 저장했던 대화 내용이
	 * 사라지고, 서버 인스턴스가 여러 대이면 인스턴스끼리 대화 내용을 공유하지 못한다는 점은
	 * 꼭 기억해 두세요.
	 * </pre>
	 */
    @Bean
    public ChatMemoryRepository chatMemoryRepository(ObjectProvider<ChatMemoryRepository> repositoryProvider) {
        // getIfAvailable()은 이미 등록된 빈이 있으면 그 빈을 가져오고, 없으면 null을 돌려줍니다.
        ChatMemoryRepository existingRepository = repositoryProvider.getIfAvailable();
        if (existingRepository != null) {
            // 이미 등록된 빈(예: RedisChatMemorySession)이 있으면 그것을 그대로 사용합니다.
            return existingRepository;
        } else {
            // 등록된 빈이 없으면 메모리 기반 저장소를 새로 만들어 사용합니다.
            return new InMemoryChatMemoryRepository();
        }
    }

	/**
	 * <pre>
	 * 대화가 아무리 길어져도 최근 메시지 일부만 기억하도록 관리해 주는 대화 메모리를 만듭니다.
	 *
	 * dstone.ai.session.max-messages 설정값(기본값 20)만큼 가장 최근 메시지만 남기고, 그보다
	 * 오래된 메시지는 잊어버립니다. 이렇게 하면 대화가 계속 이어져도 프롬프트가 끝없이 길어지지
	 * 않습니다.
	 * </pre>
	 *
	 * @param chatMemoryRepository 실제로 대화 내용을 저장할 저장소(위 chatMemoryRepository() 메소드가 만든 것)
	 * @param configProperty       dstone.ai.session.max-messages 같은 설정값을 읽어오는 데 쓰는 객체
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
		SimpleLoggerAdvisor simpleLoggerAdvisor = SimpleLoggerAdvisor.builder().order(Ordered.LOWEST_PRECEDENCE).build();
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

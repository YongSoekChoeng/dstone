package net.dstone.ai.config;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.ai.gateway.GatewayProperties;
import net.dstone.ai.governance.guardrail.PiiGuardrailAdvisor;
import net.dstone.ai.observability.usage.UsageLoggingAdvisor;
import net.dstone.common.core.BaseObject;

/**
 * anthropic/openai/ollama 중 어떤 provider가 켜져 있든, Spring AI가 spring.ai.model.chat 값을 보고
 * 딱 하나의 ChatModel만 자동으로 설정해준 다음 그 ChatModel로 ChatClient.Builder까지 만들어주기
 * 때문에, 여기서는 provider가 뭔지 신경 쓸 필요 없이 advisor만 붙여서 build()하면 된다.
 *
 * 다만 spring.ai.model.chat이 비어있거나 잘못된 값이면 provider 4개의 자동설정이 전부 꺼져버려서
 * chatClientBuilder 빈 자체가 생기지 않고, 그러면 Spring은 "ChatModel 빈이 없다"는 원인을 알기 어려운
 * 에러만 던진다. 그래서 GatewayProperties를 먼저 파라미터로 받아뒀다 - Bean 팩토리 메서드는 파라미터를
 * 선언한 순서대로 해석하므로, GatewayProperties의 검증(@PostConstruct)이 chatClientBuilder를 찾기 전에
 * 먼저 실행되어 문제가 있으면 훨씬 명확한 메시지로 알려준다.
 *
 * GatewayProperties는 net.dstone.common.config.ConfigProperty에 의존하는데, 이 모듈의
 * @ComponentScan은 net.dstone.ai만 훑어서 net.dstone.common은 포함하지 않는다. 그래서
 * dstone-boot/batch/batchadmin과 똑같이 Config 클래스에서 명시적으로 @Import 해준다.
 *
 * session(Phase 1): ChatMemory(ConfigChatMemory 참고)를 MessageChatMemoryAdvisor로 감싸서 기본
 * advisor로 붙여둔다. 호출하는 쪽은 ChatClient.Builder를 따로 건드릴 필요 없이
 * .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))만 넘기면 된다.
 *
 * governance.guardrail(PII 필터)과 observability.usage(사용량 로깅, 둘 다 Phase 4)도 여기서 기본
 * advisor로 함께 등록한다. 둘 다 켜져 있는지 여부를 스스로 체크하는 구조라서(ApiKeyAuthFilter나
 * RateLimitFilter의 shouldNotFilter와 같은 방식) 항상 빈으로 등록해둬도 아무 문제가 없다. 실행
 * 순서는 PiiGuardrailAdvisor/UsageLoggingAdvisor 각 클래스의 getOrder() 주석에 적어뒀다 - 아래
 * 리스트에 담는 순서는 의미가 없고, Spring AI가 advisor 체인을 만들 때 getOrder() 값을 보고 다시
 * 정렬해준다.
 */
@Configuration
public class ConfigChatClient extends BaseObject {

	@Bean
	public ChatClient chatClient(GatewayProperties gatewayProperties, ChatClient.Builder chatClientBuilder,
			ChatMemory chatMemory, PiiGuardrailAdvisor piiGuardrailAdvisor, UsageLoggingAdvisor usageLoggingAdvisor) {
		List<Advisor> advisors = List.of(usageLoggingAdvisor, piiGuardrailAdvisor,
				MessageChatMemoryAdvisor.builder(chatMemory).build());
		return chatClientBuilder.defaultAdvisors(advisors).build();
	}

}

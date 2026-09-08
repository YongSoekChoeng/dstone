package net.dstone.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.ai.gateway.GatewayProperties;
import net.dstone.common.core.BaseObject;

/**
 * 어떤 provider(anthropic/openai/azure-openai/ollama)가 활성화되어 있든, Spring AI가
 * spring.ai.model.chat 값에 따라 단 하나의 ChatModel만 자동설정하고 그 ChatModel로
 * ChatClient.Builder를 만들어주므로 이 지점은 provider와 무관하게 그대로 build()만 하면 된다.
 *
 * spring.ai.model.chat이 비어있거나 지원하지 않는 값이면 4개 provider 자동설정이 전부 꺼져
 * chatClientBuilder 빈 자체가 만들어지지 않는데, 그 경우 Spring이 던지는 에러는
 * "ChatModel 빈이 없다"는 원인불명 메시지뿐이다. GatewayProperties를 먼저 파라미터로 받아
 * (Bean 팩토리 메서드 파라미터는 선언 순서대로 해석된다) 그 검증(@PostConstruct)이
 * chatClientBuilder 해석보다 먼저 실행되게 해서 원인을 명확히 드러낸다.
 *
 * GatewayProperties가 net.dstone.common.config.ConfigProperty에 의존하는데, 이 모듈의
 * @ComponentScan은 net.dstone.ai만 훑으므로(net.dstone.common 미포함) {@link Config}에서
 * dstone-boot/batch/batchadmin과 동일하게 명시적으로 @Import 해준다.
 */
@Configuration
public class ConfigChatClient extends BaseObject {

	@Bean
	public ChatClient chatClient(GatewayProperties gatewayProperties, ChatClient.Builder chatClientBuilder) {
		return chatClientBuilder.build();
	}

}

package net.dstone.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigChatClient {
	
	/*************************************************************************************
	Spring AI에서 ChatClient는 기본 생성자가 존재하지 않으며, 단독으로 @Autowired를 통해 직접 주입받을 수 없음.
	Spring AI가 내장 자동 설정(Auto-configuration)으로 빈(Bean) 등록을 해주는 ChatClient.Builder를 주입받아 생성하는 것이 공식 표준 구현 방식.
	*************************************************************************************/
	
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}

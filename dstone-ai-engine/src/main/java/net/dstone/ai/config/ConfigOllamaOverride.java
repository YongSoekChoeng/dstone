package net.dstone.ai.config;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.ai.governance.guardrail.PiiGuardrailAdvisor;
import net.dstone.ai.observability.usage.UsageLoggingAdvisor;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * gateway.GatewayProperties/AiProvider는 spring.ai.model.chat 하나로 엔진 전체의 기본 ChatClient를
 * 스위칭하는 구조라, 요청 하나하나가 provider를 골라 쓸 방법이 없었다. 이 클래스는 spring.ai.model.chat
 * 값과 무관하게 항상 띄워둘 수 있는 "override용" Ollama ChatClient를 하나 더 만들어서 그 제약을 푼다 -
 * api.ChatController가 ChatRequest.provider="ollama"를 받으면 기본 chatClient(ConfigChatClient) 대신
 * 이 빈을 쓴다.
 *
 * 다른 provider(openai)는 이 환경에 API 키가 없어 무턱대고 빈을 만들면 기동이 실패하므로 override
 * 대상에 넣지 않았다 - Ollama만 base-url 하나로 항상 안전하게 띄울 수 있어서다. 나중에 다른 provider의
 * override가 필요해지면 이 클래스 옆에 같은 패턴으로 하나 더 추가하면 된다.
 *
 * dstone.ai.gateway.ollama-override.enabled=true일 때만 뜬다 - 이 엔진을 가져다 쓰는 모든 SI
 * 프로젝트가 다 필요로 하는 기능은 아니라서, 안 쓰면 Ollama 서버가 없어도(base-url이 안 맞아도)
 * 기동에 영향이 없게 기본은 꺼둔다.
 */
@Configuration
@ConditionalOnProperty(name = "dstone.ai.gateway.ollama-override.enabled", havingValue = "true")
public class ConfigOllamaOverride extends BaseObject {

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	@Bean(name = "ollamaChatClient")
	public ChatClient ollamaChatClient(ChatMemory chatMemory, PiiGuardrailAdvisor piiGuardrailAdvisor,
			UsageLoggingAdvisor usageLoggingAdvisor) {

		String baseUrl = this.configProperty.getProperty("spring.ai.ollama.base-url");
		if (StringUtil.isEmpty(baseUrl)) {
			throw new IllegalStateException(
				"dstone.ai.gateway.ollama-override.enabled=true인데 spring.ai.ollama.base-url 설정이 없습니다.");
		}
		String model = this.configProperty.getProperty("dstone.ai.gateway.ollama-override.model");
		if (StringUtil.isEmpty(model)) {
			throw new IllegalStateException(
				"dstone.ai.gateway.ollama-override.enabled=true인데 dstone.ai.gateway.ollama-override.model 설정이 없습니다.");
		}

		OllamaApi ollamaApi = OllamaApi.builder().baseUrl(baseUrl).build();
		OllamaChatOptions ollamaChatOptions = OllamaChatOptions.builder().model(model).build();
		OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
			.ollamaApi(ollamaApi)
			.options(ollamaChatOptions)
			.build();

		// 기본 chatClient(ConfigChatClient)와 동일한 advisor 구성 - 대화기록/PII필터/사용량로깅을
		// provider가 다르다고 빠뜨리면 안 되므로 그대로 맞춘다.
		List<Advisor> advisors = List.of(usageLoggingAdvisor, piiGuardrailAdvisor,
			MessageChatMemoryAdvisor.builder(chatMemory).build());
		return ChatClient.builder(ollamaChatModel).defaultAdvisors(advisors).build();
	}

}

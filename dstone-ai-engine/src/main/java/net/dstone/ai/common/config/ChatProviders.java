package net.dstone.ai.common.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaInitializationProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * Agent의 model.provider에 맞는 ChatClient를 내어 줍니다.
 *
 *   model.provider   쓰는 ChatClient
 *   (안 적음)        spring.ai.model.chat의 provider(ConfigChatClient가 만든 공용 ChatClient)
 *   기본과 같은 값   위와 같음
 *   기본과 다른 값   그 provider의 ChatClient(처음 부를 때 한 번 만들어 계속 씀)
 *
 * 접속 정보와 기본값은 모두 기존 spring.ai.{provider} 설정을 그대로 읽습니다.
 *
 * Spring AI 자동 설정은 spring.ai.model.chat에 적은 provider의 ChatModel만 만듭니다.
 * 나머지 provider는 자동 설정 클래스의 public 메소드를 이 클래스가 직접 불러 만듭니다.
 * 설정을 읽고 클라이언트를 조립하는 코드를 따로 두지 않으니, 기본 provider로 쓸 때와 똑같이 만들어집니다.
 * Spring AI를 올릴 때 이 메소드들의 인자가 바뀌면 여기서 컴파일 오류가 납니다.
 * </pre>
 */
@Component
public class ChatProviders {

	public static final String ANTHROPIC = "anthropic";
	public static final String OPENAI = "openai";
	public static final String OLLAMA = "ollama";

	/** model.provider에 적을 수 있는 값입니다. */
	public static final List<String> SUPPORTED = List.of(ANTHROPIC, OPENAI, OLLAMA);

	/** 등록된 ObservationRegistry가 없을 때 쓰는 값입니다. */
	private static final Supplier<ObservationRegistry> NOOP_REGISTRY = new Supplier<ObservationRegistry>() {
		@Override
		public ObservationRegistry get() {
			return ObservationRegistry.NOOP;
		}
	};

	@Autowired
	private ConfigProperty configProperty;
	@Autowired
	private Environment environment;
	@Autowired
	private ApplicationContext context;
	@Autowired
	private ChatClient defaultChatClient;
	/** ConfigChatClient.defaultAdvisors()입니다. provider가 달라도 같은 Advisor를 씁니다. */
	@Autowired
	private List<Advisor> defaultAdvisors;

	/** 기본 provider가 아닌 쪽의 ChatClient입니다(키 = provider). */
	private final Map<String, ChatClient> clients = new HashMap<String, ChatClient>();

	/** model.provider를 적지 않은 Agent가 쓰는 provider입니다(spring.ai.model.chat). */
	public String defaultProvider() {
		return this.configProperty.getProperty("spring.ai.model.chat");
	}

	/**
	 * 실제로 쓸 provider를 정합니다.
	 *
	 * @param provider Agent의 model.provider입니다. 비어 있으면 기본 provider입니다.
	 */
	public String resolve(String provider) {
		return StringUtil.isEmpty(provider) ? this.defaultProvider() : provider;
	}

	/**
	 * <pre>
	 * Agent가 적은 provider를 쓸 수 없으면 그 이유를 돌려줍니다. 쓸 수 있으면 null입니다.
	 * 호출할 때에야 알게 되지 않도록 기동과 디자이너의 [검사]에서 부릅니다(AgentRegistry).
	 * 접속이 되는지는 보지 않습니다. 서버가 잠시 내려가 있다고 엔진 기동까지 막지 않으려는 것입니다.
	 * </pre>
	 *
	 * @param provider Agent의 model.provider입니다.
	 */
	public String problemOf(String provider) {
		if (StringUtil.isEmpty(provider) || provider.equals(this.defaultProvider())) {
			return null;
		}
		if (!SUPPORTED.contains(provider)) {
			return "model.provider는 " + SUPPORTED + " 중 하나여야 합니다: " + provider;
		}
		if (!OLLAMA.equals(provider)
			&& StringUtil.isEmpty(this.configProperty.getProperty("spring.ai." + provider + ".api-key"))
			&& StringUtil.isEmpty(this.configProperty.getProperty("spring.ai." + provider + ".chat.api-key"))) {
			return "model.provider가 " + provider + "인데 spring.ai." + provider + ".api-key가 비어 있습니다.";
		}
		return null;
	}

	/**
	 * provider의 ChatClient를 돌려줍니다.
	 *
	 * @param provider resolve()로 정한 provider입니다.
	 */
	public synchronized ChatClient clientOf(String provider) {
		if (provider.equals(this.defaultProvider())) {
			return this.defaultChatClient;
		}
		ChatClient client = this.clients.get(provider);
		if (client == null) {
			client = this.buildClient(this.createModel(provider));
			this.clients.put(provider, client);
		}
		return client;
	}

	/**
	 * <pre>
	 * ChatModel 위에 ChatClient를 만듭니다. 공용 ChatClient와 같은 내용으로 조립해야 Tool 호출이 똑같이 돌아갑니다.
	 * 특히 ToolCallingAdvisor.Builder를 빠뜨리면 Tool을 부르는 되풀이 흐름의 설정(순서, ToolCallingManager)이 달라집니다.
	 * </pre>
	 */
	private ChatClient buildClient(ChatModel model) {
		ChatClient.Builder builder = ChatClient.builder(model
			, this.context.getBeanProvider(ObservationRegistry.class).getIfUnique(NOOP_REGISTRY)
			, this.context.getBeanProvider(ChatClientObservationConvention.class).getIfUnique()
			, this.context.getBeanProvider(AdvisorObservationConvention.class).getIfUnique()
			, this.context.getBean(ToolCallingAdvisor.Builder.class));
		return builder.defaultAdvisors(this.defaultAdvisors.toArray(new Advisor[0])).build();
	}

	/**
	 * <pre>
	 * 기본 provider가 아닌 provider의 ChatModel을 만듭니다.
	 * 처음 부를 때만 만들어서, 쓰지 않는 provider의 API 키가 비어 있어도 기동에는 영향이 없습니다.
	 * 테스트는 이 메소드를 덮어써서 실제 provider 대신 대역을 씁니다.
	 * </pre>
	 *
	 * @param provider anthropic, openai, ollama 중 하나입니다.
	 */
	protected ChatModel createModel(String provider) {
		if (OPENAI.equals(provider)) {
			return this.openAiModel();
		}
		if (ANTHROPIC.equals(provider)) {
			return this.anthropicModel();
		}
		if (OLLAMA.equals(provider)) {
			return this.ollamaModel();
		}
		throw new IllegalArgumentException("model.provider는 " + SUPPORTED + " 중 하나여야 합니다: " + provider);
	}

	private ChatModel openAiModel() {
		return new OpenAiChatAutoConfiguration().openAiChatModel(
			this.bind(OpenAiCommonProperties.CONFIG_PREFIX, new OpenAiCommonProperties())
			, this.bind(OpenAiChatProperties.CONFIG_PREFIX, new OpenAiChatProperties())
			, this.context.getBean(ToolCallingManager.class)
			, this.context.getBeanProvider(ObservationRegistry.class)
			, this.context.getBeanProvider(MeterRegistry.class)
			, this.context.getBeanProvider(ChatModelObservationConvention.class)
			, this.context.getBeanProvider(OpenAiHttpClientBuilderCustomizer.class));
	}

	private AnthropicChatModel anthropicModel() {
		return new AnthropicChatAutoConfiguration().anthropicChatModel(
			this.bind(AnthropicConnectionProperties.CONFIG_PREFIX, new AnthropicConnectionProperties())
			, this.bind(AnthropicChatProperties.CONFIG_PREFIX, new AnthropicChatProperties())
			, this.context.getBean(ToolCallingManager.class)
			, this.context.getBeanProvider(ObservationRegistry.class)
			, this.context.getBeanProvider(MeterRegistry.class)
			, this.context.getBeanProvider(ChatModelObservationConvention.class)
			, this.context.getBeanProvider(AnthropicHttpClientBuilderCustomizer.class));
	}

	private ChatModel ollamaModel() {
		final OllamaConnectionProperties connection = this.bind(OllamaConnectionProperties.CONFIG_PREFIX, new OllamaConnectionProperties());
		OllamaApi api = new OllamaApiAutoConfiguration().ollamaApi(
			new OllamaConnectionDetails() {
				@Override
				public String getBaseUrl() {
					return connection.getBaseUrl();
				}
			}
			, this.context.getBeanProvider(RestClient.Builder.class)
			, this.context.getBeanProvider(WebClient.Builder.class)
			, this.context.getBeanProvider(ResponseErrorHandler.class));
		return new OllamaChatAutoConfiguration().ollamaChatModel(api
			, this.bind(OllamaChatProperties.CONFIG_PREFIX, new OllamaChatProperties())
			, this.bind(OllamaInitializationProperties.CONFIG_PREFIX, new OllamaInitializationProperties())
			, this.context.getBean(ToolCallingManager.class)
			, this.context.getBeanProvider(ObservationRegistry.class)
			, this.context.getBeanProvider(ChatModelObservationConvention.class)
			, this.context.getBeanProvider(RetryTemplate.class));
	}

	/**
	 * <pre>
	 * 설정 값을 Spring AI의 Properties 클래스에 채웁니다.
	 * 자동 설정이 꺼진 provider의 Properties는 빈으로 만들어지지 않아서 직접 읽습니다.
	 * Environment를 거치므로 ENC(...) 값과 ${변수}도 똑같이 풀립니다.
	 * </pre>
	 *
	 * @param prefix   설정 위치입니다.
	 * @param defaults 설정이 하나도 없을 때 쓸 빈 객체입니다.
	 */
	private <T> T bind(String prefix, T defaults) {
		@SuppressWarnings("unchecked")
		Class<T> type = (Class<T>) defaults.getClass();
		return Binder.get(this.environment).bind(prefix, type).orElse(defaults);
	}

}

package net.dstone.ai.common.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * dstone-ai-engine 안의 모든 코드가 LLM을 부를 때 공통으로 쓰는 ChatClient 하나를 만들어주는 설정 클래스입니다. 
 * ChatClient는 Spring AI에서 "LLM한테 말을 걸 때 쓰는 창구"라고 생각하면 됩니다.
 * 이 엔진은 provider(dstone.ai.model.chat 설정값 - 예: anthropic, ollama)를 하나만 정해서 쓰는데,
 * 그 provider로 가는 ChatClient를 여기서 딱 한 번 조립해서 스프링 빈으로 등록해 둡니다.
 *
 * PII 가리기, 금지어 검사, 사용량 로깅·쿼터 같은 "거버넌스" 기능은 이 클래스가 직접 처리하지 않습니다. 
 * 대신 이 클래스는 List<Advisor>를 그대로 주입받는 구조로 되어 있습니다. 
 * 그래서 나중에 거버넌스 기능이 필요해지면, 새로운 모듈에서 @Bean으로 Advisor 하나만 추가하면 됩니다
 * (스프링이 등록된 Advisor 빈들을 자동으로 모아서 이 리스트에 넣어주기 때문에, 이 클래스 코드를 고칠 필요가 없습니다). 
 * 지금은 그렇게 추가로 등록된 Advisor 빈이 하나도 없으므로, 이 리스트는 비어 있는 상태로 시작합니다.
 * </pre>
 */
@Configuration
public class ConfigChatClient {
	
	@Autowired
	ConfigProperty configProperty;
	

	/**
	 * <pre>
	 * 대화 내용을 어디에 저장할지 결정하는 저장소(ChatMemoryRepository)를 준비해 줍니다.
	 *
	 * dstone.ai.session.redis.enabled 설정이 true 일 경우 session.RedisChatMemoryRepository 사용
	 *
	 * dstone.ai.session.redis.enabled 설정이 false 일 경우(즉 Redis를 안 쓰는 환경이라면) 이 메소드가 대신 
	 * InMemoryChatMemoryRepository(자바 메모리 위에만 대화 내용을 저장하는 간단한 저장소)를 새로 만들어서 사용
	 *
	 * InMemoryChatMemoryRepository를 쓸 경우, 서버를 재시작하면 저장했던 대화 내용이 사라지고, 서버 인스턴스가 여러 대이면 인스턴스끼리 대화 내용을 공유하지 못함.
	 * </pre>
	 */
    @Bean
    ChatMemoryRepository chatMemoryRepository(ObjectProvider<ChatMemoryRepository> repositoryProvider) {
    	/**************************************************************************
    	ChatMemoryRepository를 구현한 RedisChatMemoryRepository 가 반환된다.
    	그러나 spring.data.redis.enabled 가 false 일 경우 RedisChatMemoryRepository 가 
    	생성이 되어있지 않으므로 repositoryProvider.getIfAvailable()체크를 거친다.
    	**************************************************************************/
        ChatMemoryRepository existingRepository = repositoryProvider.getIfAvailable();
        if (existingRepository != null) {
            // 이미 등록된 빈(예: RedisChatMemoryRepository)이 있으면 그것을 그대로 사용합니다.
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
	 *
	 * 대화 기억 Advisor(MessageChatMemoryAdvisor)는 여기 두지 않습니다. 대화를 기억할지는 호출마다 다르기 때문에
	 * (채팅 API는 기억, Workflow step은 memory: true일 때만) runtime.agent.AgentExecutor가 필요할 때만 붙입니다.
	 * </pre>
	 *
	 * @param advisor    목록(현재는 비어 있음). 스프링에서 List<T> 타입은 단일 Bean과 다르게 빈 List로 주입 하므로 문제 없음.
	 */
	@Bean
	List<Advisor> defaultAdvisors(List<Advisor> advisors) {
		List<Advisor> advisorList = new ArrayList<>(advisors);
		
		// LLM 로깅하는 Advisor 등록
		if(net.dstone.ai.common.config.ConfigCallLog.IS_LLM_LOGGING_YN) {
			net.dstone.ai.common.config.ConfigCallLog.LlmLoggerAdvisor llmLoggerAdvisor = net.dstone.ai.common.config.ConfigCallLog.LlmLoggerAdvisor.builder().build();
			advisorList.add(llmLoggerAdvisor);
		}
		
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

	/**
	 * <pre>
	 * LLM 호출 한 번에 실어 보낼 옵션을 만들어 줍니다. 실어 보낼 것이 없으면 null입니다.
	 * runtime.agent.AgentExecutor가 호출마다 이 메소드로 옵션을 받아 붙입니다.
	 *
	 * provider가 openai일 때는 응답 대기 시간(spring.ai.openai.timeout)을 여기서 직접 넣어 줍니다.
	 * 이유: Spring AI(2.0.1)의 OpenAiChatModel은 호출할 때마다 "요청 옵션의 timeout"을 꺼내 그 요청의 대기 시간으로 씁니다.
	 * 그런데 이 값은 설정 파일로 바꿀 길이 없고 기본 60초로 굳어 있습니다. spring.ai.openai.timeout은 HTTP 클라이언트의
	 * 기본값으로만 들어가고, 요청마다 60초가 그 위를 덮어씁니다. 그래서 설정에 5m을 적어도 60초에 끊깁니다.
	 * 실제로 느린 모델이 긴 문서를 써 내다가 정확히 60초에 끊겼습니다
	 * ("Error reading response (원인: InterruptedIOException: timeout <- StreamResetException: stream was reset: CANCEL)").
	 *
	 * ChatClient의 기본 옵션(defaultOptions)에 넣지 않는 이유: 호출할 때 옵션을 따로 주면(Agent의 model 지정)
	 * 기본 옵션이 통째로 바뀌어서 timeout이 사라집니다. 그래서 호출마다 넣습니다.
	 * 여기서 넣지 않은 값(기본 모델, max-tokens, temperature 등)은 설정 파일의 값이 그대로 쓰입니다.
	 *
	 * 추론 세기(Agent의 reasoning)도 여기서 넣습니다. 보내는 방법이 provider마다 달라서 provider별로 옵션을 만듭니다.
	 *
	 *   reasoning   openai(OpenRouter 포함)      ollama            anthropic
	 *   none        reasoning_effort=none       think=false       thinking 끔
	 *   low         reasoning_effort=low        think=true        thinking 예산 1024 토큰
	 *   medium      reasoning_effort=medium     think=true        thinking 예산 2048 토큰
	 *   high        reasoning_effort=high       think=true        thinking 예산 4096 토큰
	 *
	 * - ollama는 모델 대부분이 켜기/끄기만 받아서 low/medium/high를 모두 "켜기"로 보냅니다.
	 * - anthropic의 thinking 예산은 max-tokens보다 작아야 합니다. max-tokens를 4096 이하로 쓰면 high는 오류가 납니다.
	 * - reasoning을 적지 않은 Agent에는 아무것도 넣지 않습니다(지금까지와 같습니다).
	 * </pre>
	 *
	 * @param model     이번 호출에서 쓸 모델명입니다. 비어 있으면 provider 공통 기본 모델을 씁니다.
	 * @param reasoning 이번 호출의 추론 세기입니다(none/low/medium/high). 비어 있으면 provider와 모델의 기본 동작을 씁니다.
	 * @param temperature 이번 호출의 temperature입니다. 비어 있으면 provider 설정값을 씁니다.
	 * @param timeoutSeconds 이번 호출이 답을 기다리는 시간(초)입니다. 비어 있으면 provider 설정값을 씁니다. 지금은 provider가 openai일 때만 적용됩니다.
	 */
	public ChatOptions.Builder<?> requestOptions(String model, String reasoning, Double temperature, Integer timeoutSeconds) {
		String provider = configProperty.getProperty("spring.ai.model.chat");
		ChatOptions.Builder<?> options = null;
		if ("openai".equals(provider)) {
			options = this.openAiOptions(reasoning, timeoutSeconds);
		} else if ("ollama".equals(provider)) {
			options = this.ollamaOptions(reasoning);
		} else if ("anthropic".equals(provider)) {
			options = this.anthropicOptions(reasoning);
		}
		if (!StringUtil.isEmpty(model)) {
			if (options == null) {
				options = ChatOptions.builder();
			}
			options = options.model(model);
		}
		if (temperature != null) {
			if (options == null) {
				options = ChatOptions.builder();
			}
			options = options.temperature(temperature);
		}
		return options;
	}

	/**
	 * openai용 옵션입니다. 응답 대기 시간과 추론 세기를 넣습니다. 넣을 것이 없으면 null입니다.
	 * 응답 대기 시간은 Agent가 적은 값(execution.timeoutSeconds)이 있으면 그 값을, 없으면 spring.ai.openai.timeout을 씁니다.
	 */
	private ChatOptions.Builder<?> openAiOptions(String reasoning, Integer timeoutSeconds) {
		String timeout = configProperty.getProperty("spring.ai.openai.timeout");
		if (StringUtil.isEmpty(timeout) && timeoutSeconds == null && StringUtil.isEmpty(reasoning)) {
			return null;
		}
		OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
		if (timeoutSeconds != null) {
			options.timeout(Duration.ofSeconds(timeoutSeconds.longValue()));
		} else if (!StringUtil.isEmpty(timeout)) {
			options.timeout(DurationStyle.detectAndParse(timeout.trim()));
		}
		if (!StringUtil.isEmpty(reasoning)) {
			options.reasoningEffort(reasoning);
		}
		return options;
	}

	/** ollama용 옵션입니다. 추론을 켜거나 끕니다. reasoning이 비어 있으면 null입니다. */
	private ChatOptions.Builder<?> ollamaOptions(String reasoning) {
		if (StringUtil.isEmpty(reasoning)) {
			return null;
		}
		OllamaChatOptions.Builder options = OllamaChatOptions.builder();
		if (AgentDefinition.REASONING_NONE.equals(reasoning)) {
			options.disableThinking();
		} else {
			options.enableThinking();
		}
		return options;
	}

	/** anthropic용 옵션입니다. thinking을 끄거나, 세기에 맞는 예산으로 켭니다. reasoning이 비어 있으면 null입니다. */
	private ChatOptions.Builder<?> anthropicOptions(String reasoning) {
		if (StringUtil.isEmpty(reasoning)) {
			return null;
		}
		AnthropicChatOptions.Builder options = AnthropicChatOptions.builder();
		if (AgentDefinition.REASONING_NONE.equals(reasoning)) {
			options.thinkingDisabled();
		} else if ("low".equals(reasoning)) {
			options.thinkingEnabled(1024L);
		} else if ("medium".equals(reasoning)) {
			options.thinkingEnabled(2048L);
		} else {
			options.thinkingEnabled(4096L);
		}
		return options;
	}

}

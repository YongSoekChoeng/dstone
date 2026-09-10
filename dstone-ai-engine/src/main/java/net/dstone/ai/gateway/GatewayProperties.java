package net.dstone.ai.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * 지금 켜져 있는 LLM provider(spring.ai.model.chat)를 읽어서 값이 맞는지 확인해두고, 다른 패키지
 * (observability/governance/api 등)가 "지금 어떤 provider가 떠 있지?"를 물어볼 때 답해주는 지점이다.
 *
 * anthropic/openai/ollama 3개 provider의 starter가 전부 클래스패스에 올라가 있는 상태라서,
 * spring.ai.model.chat을 비워두거나 오타를 내면 provider 자동설정이 전부 꺼지거나(값이 비어있을 때)
 * 반대로 전부 켜져버려서(값이 없을 때 각 provider의 matchIfMissing=true라서) 결국 어떤 ChatModel이
 * 뜨는지 알 수 없게 된다. 그래서 여기서 먼저 값을 확인해서, 문제가 있으면 명확한 에러 메시지와 함께
 * 기동을 바로 멈추게 한다.
 */
@Component
public class GatewayProperties extends BaseObject {

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean
	
	private AiProvider provider;

	@PostConstruct
	public void validate() {
		
		if( StringUtil.isEmpty(configProperty.getProperty("spring.ai.model.chat")) ) {
			throw new IllegalStateException("spring.ai.model.chat 설정이 없습니다. anthropic/openai/ollama 중 하나를 명시해야 합니다.");
		}
		this.provider = AiProvider.fromPropertyValue(configProperty.getProperty("spring.ai.model.chat"));
		LogUtil.sysout("dstone-ai-engine gateway: 활성 LLM provider = " + this.provider);
	}

	public AiProvider activeProvider() {
		return this.provider;
	}

}

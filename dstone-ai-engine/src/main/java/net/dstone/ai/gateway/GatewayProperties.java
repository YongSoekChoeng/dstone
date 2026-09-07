package net.dstone.ai.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;

/**
 * 현재 활성화된 LLM provider(spring.ai.model.chat)를 읽어 검증하고, 다른 패키지
 * (observability/governance/api 등)가 "지금 어떤 provider가 떠 있는지" 알아야 할 때 참조하는 지점이다.
 *
 * anthropic/openai/azure-openai/ollama 4개 provider의 starter가 모두 클래스패스에 있는 상태라,
 * spring.ai.model.chat이 비어있거나 오타가 나면 각 provider 자동설정이 전부 꺼지거나(비어있는 값)
 * 전부 켜져서(값이 없을 때 각 provider의 matchIfMissing=true) 어떤 ChatModel이 뜰지 알 수 없게 된다.
 * 여기서 먼저 값을 검증해 명확한 에러 메시지로 기동을 실패시킨다.
 */
@Component
public class GatewayProperties extends BaseObject {

	@Value("${spring.ai.model.chat:}")
	private String chatProviderValue;

	private AiProvider provider;

	@PostConstruct
	public void validate() {
		if (this.chatProviderValue == null || this.chatProviderValue.isBlank()) {
			throw new IllegalStateException(
					"spring.ai.model.chat 설정이 없습니다. anthropic/openai/azure-openai/ollama 중 하나를 명시해야 합니다.");
		}
		this.provider = AiProvider.fromPropertyValue(this.chatProviderValue);
		LogUtil.sysout("dstone-ai-engine gateway: 활성 LLM provider = " + this.provider);
	}

	public AiProvider activeProvider() {
		return this.provider;
	}

}

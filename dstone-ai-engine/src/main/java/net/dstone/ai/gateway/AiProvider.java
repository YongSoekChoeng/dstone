package net.dstone.ai.gateway;

import java.util.Arrays;

/**
 * spring.ai.model.chat 값(각 Spring AI provider starter의 자동설정이 참조하는 값)과
 * 1:1로 매핑되는 provider 목록. 이 값을 바꾸는 것만으로 실제 활성화되는 ChatModel/ChatClient가
 * 교체된다(개별 provider의 자동설정 클래스가 spring.ai.model.chat=&lt;propertyValue&gt; 조건으로
 * 켜지거나 꺼진다).
 *
 * azure-openai는 Spring AI 2.x에서 chat model provider로 완전히 제거되어(2.0.0-M4 이후
 * spring-ai-starter-model-azure-openai 미배포, Azure는 vector-store 용도만 남음) 목록에서 뺐다.
 *
 * anthropic/openai/ollama는 각각 spring-ai-starter-model-*의 자동설정이 이미 ChatModel 어댑터
 * 역할을 하고 있어 provider별 커스텀 어댑터 구현체를 따로 두지 않는다. local vLLM처럼 starter가
 * 없는 OpenAI 호환 서버는 provider는 OPENAI로 두고 spring.ai.openai.base-url만 vLLM 엔드포인트로
 * override해서 재사용한다. 향후 provider별 커스터마이징(요청/응답 인터셉터, 모델명 기본값 등)이
 * 실제로 필요해지면 net.dstone.ai.gateway.provider 패키지를 그 구현을 담는 자리로 새로 만든다.
 */
public enum AiProvider {

	ANTHROPIC("anthropic"),
	OPENAI("openai"),
	OLLAMA("ollama");

	private final String propertyValue;

	AiProvider(String propertyValue) {
		this.propertyValue = propertyValue;
	}

	public String propertyValue() {
		return this.propertyValue;
	}

	public static AiProvider fromPropertyValue(String propertyValue) {
		return Arrays.stream(values())
				.filter(provider -> provider.propertyValue.equals(propertyValue))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"지원하지 않는 spring.ai.model.chat 값입니다: [" + propertyValue + "], 지원값: " + Arrays.toString(values())));
	}

}

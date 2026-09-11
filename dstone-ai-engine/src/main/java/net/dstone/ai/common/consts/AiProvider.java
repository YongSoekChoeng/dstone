package net.dstone.ai.common.consts;

import java.util.Arrays;

/**
 * spring.ai.model.chat 값(각 Spring AI provider starter의 자동설정이 참조하는 값)과 하나씩 짝지어지는
 * provider 목록이다. 이 값을 바꾸는 것만으로 실제로 켜지는 ChatModel/ChatClient가 통째로 교체된다 -
 * provider별 자동설정 클래스가 spring.ai.model.chat=&lt;propertyValue&gt; 조건으로 켜지거나 꺼지기
 * 때문이다.
 *
 * azure-openai는 목록에 없다. Spring AI 2.x부터 chat model provider에서 완전히 빠졌기 때문인데
 * (2.0.0-M4 이후로 spring-ai-starter-model-azure-openai 자체가 더 이상 배포되지 않는다), Azure는
 * 이제 vector-store 용도로만 쓸 수 있다.
 *
 * anthropic/openai/ollama는 각각 spring-ai-starter-model-*의 자동설정이 이미 ChatModel 어댑터
 * 역할을 해주고 있어서, provider별로 커스텀 어댑터를 따로 만들 필요가 없다. local vLLM처럼 전용
 * starter가 없는 OpenAI 호환 서버를 붙이고 싶으면 provider는 그냥 OPENAI로 두고
 * spring.ai.openai.base-url만 vLLM 엔드포인트로 바꿔주면 그대로 재사용할 수 있다. 나중에 provider별
 * 커스터마이징(요청/응답 인터셉터, 모델명 기본값 등)이 실제로 필요해지면, 그때 gateway 패키지
 * 밑에 provider 패키지를 새로 만들어 그 구현을 담으면 된다.
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

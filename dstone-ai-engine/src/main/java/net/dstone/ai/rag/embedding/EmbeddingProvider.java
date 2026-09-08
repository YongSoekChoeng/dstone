package net.dstone.ai.rag.embedding;

import java.util.Arrays;

/**
 * spring.ai.model.embedding 값과 1:1로 매핑되는, 임베딩 모델을 제공하는 provider 목록.
 * gateway.AiProvider와 달리 anthropic은 없다 - Anthropic은 임베딩 생성 API 자체를 제공하지 않아서
 * spring-ai-starter-model-anthropic에는 ChatModel 구현체만 있고 EmbeddingModel 구현체가 없다.
 */
public enum EmbeddingProvider {

	OPENAI("openai"),
	OLLAMA("ollama");

	private final String propertyValue;

	EmbeddingProvider(String propertyValue) {
		this.propertyValue = propertyValue;
	}

	public String propertyValue() {
		return this.propertyValue;
	}

	public static EmbeddingProvider fromPropertyValue(String propertyValue) {
		return Arrays.stream(values())
				.filter(provider -> provider.propertyValue.equals(propertyValue))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"지원하지 않는 spring.ai.model.embedding 값입니다: [" + propertyValue + "], 지원값: "
								+ Arrays.toString(values()) + " (anthropic은 임베딩 모델을 제공하지 않아 목록에 없음)"));
	}

}

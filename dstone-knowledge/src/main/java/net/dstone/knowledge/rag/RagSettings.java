package net.dstone.knowledge.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;

/**
 * <pre>
 * RAG 관련 설정 값을 한곳에서 읽어 줍니다(conf/application.yml의 dstone.knowledge.rag.* 와 임베딩 모델 이름).
 *
 * 임베딩 모델 이름과 차원은 문서를 만드는 쪽(대기열에 올릴 때), 임베딩하는 쪽, 검색하는 쪽이 모두 같은 값을 써야 합니다.
 * 임베딩의 키가 (내용 해시, 모델)이라서, 한쪽만 다른 이름을 쓰면 서로의 결과를 찾지 못합니다.
 * </pre>
 */
@Component
public class RagSettings {

	@Autowired
	private ConfigProperty configProperty;

	/** 임베딩 모델 이름. 예: bge-m3:latest */
	public String embeddingModel() {
		String model = configProperty.getProperty("spring.ai.ollama.embedding.model");
		return model == null || model.trim().length() == 0 ? "bge-m3:latest" : model.trim();
	}

	/** 임베딩 벡터의 차원. rag_embedding.embedding 컬럼의 차원과 같아야 합니다. */
	public int embeddingDimensions() {
		return intProperty("dstone.knowledge.rag.embedding.dimensions", 1024);
	}

	public boolean embeddingEnabled() {
		return !"false".equalsIgnoreCase(configProperty.getProperty("dstone.knowledge.rag.embedding.enabled"));
	}

	public int embeddingBatchSize() {
		return Math.max(1, intProperty("dstone.knowledge.rag.embedding.batch-size", 16));
	}

	public int embeddingIdleSeconds() {
		return Math.max(1, intProperty("dstone.knowledge.rag.embedding.idle-seconds", 5));
	}

	public boolean skipAccessors() {
		return !"false".equalsIgnoreCase(configProperty.getProperty("dstone.knowledge.rag.skip-accessors"));
	}

	public int chunkMaxChars() {
		return Math.max(200, intProperty("dstone.knowledge.rag.chunk-max-chars", 1800));
	}

	private int intProperty(String key, int defaultValue) {
		String configured = configProperty.getProperty(key);
		if (configured == null || configured.trim().length() == 0) {
			return defaultValue;
		}
		return Integer.parseInt(configured.trim());
	}

}

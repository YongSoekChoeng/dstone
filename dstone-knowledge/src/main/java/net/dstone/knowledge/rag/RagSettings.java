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

	/** 올린 문서 하나에서 읽는 최대 글자 수. 넘는 부분은 버린다(끝없이 큰 파일이 서버를 붙잡지 않게) */
	public int uploadMaxChars() {
		return Math.max(1000, intProperty("dstone.knowledge.rag.upload.max-chars", 5000000));
	}

	/** 올린 문서의 청크 경계에서 겹쳐 넣는 문단의 최대 글자 수 */
	public int uploadOverlapChars() {
		return Math.max(0, intProperty("dstone.knowledge.rag.upload.overlap-chars", 200));
	}

	/** 올린 문서의 임베딩 우선순위. 코드(0)보다 커야 먼저 처리된다 */
	public int uploadEmbeddingPriority() {
		return intProperty("dstone.knowledge.rag.upload.embedding-priority", 10);
	}

	private int intProperty(String key, int defaultValue) {
		String configured = configProperty.getProperty(key);
		if (configured == null || configured.trim().length() == 0) {
			return defaultValue;
		}
		return Integer.parseInt(configured.trim());
	}

}

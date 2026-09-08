package net.dstone.ai.rag.retrieval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * VectorStore(pgvector) 유사도 검색(Phase 2). 기본 topK/similarityThreshold는
 * dstone.ai.rag.retrieval.*에서 가져오고, 요청별로 override할 수 있다.
 *
 * defaultTopK()/defaultSimilarityThreshold()는 net.dstone.ai.api.ChatController가
 * RAG-증강 채팅(QuestionAnswerAdvisor)을 구성할 때도 같은 기본값을 쓰도록 노출해둔 것이다.
 */
@Service
@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true")
public class RetrievalService extends BaseObject {

	private static final String SOURCE_ID_METADATA_KEY = "sourceId";

	private final VectorStore vectorStore;
	private final int defaultTopK;
	private final double defaultSimilarityThreshold;

	public RetrievalService(VectorStore vectorStore, ConfigProperty configProperty) {
		this.vectorStore = vectorStore;
		String topK = configProperty.getProperty("dstone.ai.rag.retrieval.top-k");
		this.defaultTopK = StringUtil.isEmpty(topK) ? 5 : Integer.parseInt(topK);
		String threshold = configProperty.getProperty("dstone.ai.rag.retrieval.similarity-threshold");
		this.defaultSimilarityThreshold = StringUtil.isEmpty(threshold) ? 0.5 : Double.parseDouble(threshold);
	}

	public List<Document> search(String query, Integer topK, Double similarityThreshold, String sourceId) {
		SearchRequest.Builder builder = SearchRequest.builder()
			.query(query)
			.topK(topK == null ? this.defaultTopK : topK)
			.similarityThreshold(similarityThreshold == null ? this.defaultSimilarityThreshold : similarityThreshold);
		if (!StringUtil.isEmpty(sourceId)) {
			builder.filterExpression(new FilterExpressionBuilder().eq(SOURCE_ID_METADATA_KEY, sourceId).build());
		}
		return this.vectorStore.similaritySearch(builder.build());
	}

	public int defaultTopK() {
		return this.defaultTopK;
	}

	public double defaultSimilarityThreshold() {
		return this.defaultSimilarityThreshold;
	}

}

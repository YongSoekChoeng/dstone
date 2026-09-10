package net.dstone.ai.rag.retrieval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import net.dstone.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * VectorStore(pgvector)에서 유사도 검색을 해주는 서비스다(Phase 2). 기본 topK/similarityThreshold는
 * dstone.ai.rag.retrieval.* 설정값을 쓰고, 요청마다 원하면 다른 값으로 override할 수 있다.
 *
 * defaultTopK()/defaultSimilarityThreshold()를 public으로 열어둔 이유는, ChatController가
 * RAG-증강 채팅(QuestionAnswerAdvisor)을 구성할 때도 여기와 같은 기본값을 쓰게 하기 위해서다.
 */
@Service
@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true")
public class RetrievalService extends BaseService {

	private static final String SOURCE_ID_METADATA_KEY = "sourceId";

	private final VectorStore vectorStore;
	private final int defaultTopK;
	private final double defaultSimilarityThreshold;

	public RetrievalService(VectorStore vectorStore, ConfigProperty configProperty) {
		this.vectorStore = vectorStore;
		String topK = configProperty.getProperty("dstone.ai.rag.retrieval.top-k");
		this.defaultTopK = StringUtil.isEmpty(topK) ? 5 : Integer.parseInt(topK);
		// bge-m3(Ollama, 이 환경 기본 임베딩 모델) 기준 실측: 실제로 관련 있는 문서/질의 쌍도
		// 코사인 유사도가 0.5를 넘지 못하는 경우가 흔해서(0.48 등) 0.5를 기본값으로 두면 데이터가
		// 있어도 검색 결과가 통째로 비어버린다 - 임베딩 모델/도메인마다 분포가 달라 튜닝이 필요한 값이다.
		String threshold = configProperty.getProperty("dstone.ai.rag.retrieval.similarity-threshold");
		this.defaultSimilarityThreshold = StringUtil.isEmpty(threshold) ? 0.35 : Double.parseDouble(threshold);
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

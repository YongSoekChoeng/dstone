package net.dstone.ai.common.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder.Op;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.api.dto.RagSearchRequest;
import net.dstone.ai.api.dto.RetrievedChunk;
import net.dstone.ai.common.consts.Constants;
import net.dstone.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * "이미 임베딩된 내용을 검색해서 LLM에 증강"하는, 진짜 의미의 RAG(Retrieval-Augmented Generation) 쪽이다. 문서를 임베딩으로 만들어
 * 벡터스토어에 넣고 빼는 일(api.service.EmbedService)과는 완전히 분리된 클래스다 - 이 클래스는 이미 있는 임베딩을 "읽기"만 한다.
 *
 * 검색 체인은 Spring AI의 RAG 모듈(spring-ai-rag)이 제공하는 조립 부품을 그대로 쓴다:
 * VectorStoreDocumentRetriever(검색) → ContextualQueryAugmenter(검색 결과를 프롬프트에 끼워넣기)를
 * RetrievalAugmentationAdvisor 하나로 묶는다. buildAdvisor()(AGENT의 ragEnabled 경로)와 search()(TOOL의
 * RagSearchTool 경로) 둘 다 같은 VectorStoreDocumentRetriever 생성 로직(buildRetriever())을 거치므로
 * "무엇을 검색 대상으로 볼지"(topK/threshold/tenant 필터)는 항상 한 곳에서만 정해진다.
 *
 * VectorStore(pgvector)는 dstone.ai.rag.enabled=true이고 spring.ai.model.embedding이 올바르게 설정돼 있을 때만 Spring AI가
 * 만들어주는 빈이라(아예 없을 수 있다), 이 빈의 존재 여부를 requireVectorStore() 한 곳에서만 흡수한다 - 그래서 이 클래스는 항상 등록되고,
 * "RAG를 실제로 쓸 수 있는가"는 메서드를 호출한 시점에만 판단된다.
 */
@Component
public class RagRetrievalChain extends BaseService {

	/**
	 * <pre>
	 * 검색 결과가 있을 때 프롬프트에 끼워넣는 템플릿이다. Spring AI의 ContextualQueryAugmenter 기본 템플릿은
	 * "컨텍스트에 없으면 모른다고 답하라"고 강제한다 - 지식 베이스 QA봇에는 맞는 동작이지만, 이 프로젝트의
	 * ragEnabled Agent(예: sql-conversion-agent)는 이미 자신의 system prompt에 해당 업무를 수행할 규칙/전문
	 * 지식을 전부 갖고 있고, RAG는 그 위에 참고 자료를 얹어주는 보조 수단일 뿐이다.
	 *
	 * {context} 는 검색된 문서, {query} 는 원래 질의어 - 둘 다 Spring AI의 ContextualQueryAugmenter가
	 * 자동으로 채워주는 플레이스홀더다.
	 * </pre>
	 */
	private static final PromptTemplate CONTEXT_PROMPT_TEMPLATE = new PromptTemplate("""
		{query}

		[참고자료]
		{context}
		""");

	@Autowired
	private ObjectProvider<VectorStore> vectorStoreProvider;
	@Autowired
	private ConfigProperty configProperty;

	/**
	 * <pre>
	 * 최상위원칙: dstone.ai.rag.enabled=true이고 VectorStore 빈이 실제로 떠 있을 때만 통과시킨다.
	 * </pre>
	 */
	private VectorStore requireVectorStore() {
		if (!Boolean.parseBoolean(this.configProperty.getProperty("dstone.ai.rag.enabled"))) {
			throw new IllegalStateException("RAG가 비활성화되어 있습니다(dstone.ai.rag.enabled=false 또는 미설정).");
		}
		VectorStore vectorStore = this.vectorStoreProvider.getIfAvailable();
		if (vectorStore == null) {
			throw new IllegalStateException("dstone.ai.rag.enabled=true인데 VectorStore 빈이 없습니다. spring.ai.model.embedding 설정을 확인하십시오.");
		}
		return vectorStore;
	}

	private int defaultTopK() {
		String topK = this.configProperty.getProperty("dstone.ai.rag.retrieval.top-k");
		return StringUtil.isEmpty(topK) ? 5 : Integer.parseInt(topK);
	}

	private double defaultSimilarityThreshold() {
		// bge-m3(로컬 임베딩) 기준 실측: 실제로 관련 있는 문서/질의 쌍도 코사인 유사도가 0.5를 못 넘는
		// 경우가 흔해서(0.48 등) 0.5를 기본값으로 두면 데이터가 있어도 검색 결과가 통째로 비어버린다.
		String threshold = this.configProperty.getProperty("dstone.ai.rag.retrieval.similarity-threshold");
		return StringUtil.isEmpty(threshold) ? 0.35 : Double.parseDouble(threshold);
	}

	/**
	 * <pre>
	 * caller(=tenant_id)와 sourceId 조건을 하나의 Filter.Expression으로 합쳐준다. 둘 다 없으면 null(=필터 없음)을 돌려준다 - caller가 없는
	 * 경우(security.auth가 꺼진 배포)는 기존과 동일하게 tenant 필터 없이 동작해야 하므로, 이 메서드가 유일하게 "격리를 켤지" 판단하는 지점이다.
	 * </pre>
	 *
	 * @param caller   호출한 앱/서비스 식별자(tenant)
	 * @param sourceId 문서 논리 식별자
	 */
	private Filter.Expression buildFilter(String caller, String sourceId) {
		FilterExpressionBuilder builder = new FilterExpressionBuilder();
		Op tenantOp = StringUtil.isEmpty(caller) ? null : builder.eq(Constants.Rag.TENANT_METADATA_KEY, caller);
		Op sourceOp = StringUtil.isEmpty(sourceId) ? null : builder.eq(Constants.Rag.SOURCE_ID_METADATA_KEY, sourceId);
		if (tenantOp != null && sourceOp != null) {
			return builder.and(tenantOp, sourceOp).build();
		}
		if (tenantOp != null) {
			return tenantOp.build();
		}
		if (sourceOp != null) {
			return sourceOp.build();
		}
		return null;
	}

	/**
	 * <pre>
	 * "무엇을 검색 대상으로 볼지"를 정하는 단 하나의 지점 - buildAdvisor()/search() 둘 다 이걸 거친다.
	 * </pre>
	 *
	 * @param topK                검색 결과 최대 개수(null이면 dstone.ai.rag.retrieval.top-k 기본값)
	 * @param similarityThreshold 검색 결과 유사도 임계값(null이면 dstone.ai.rag.retrieval.similarity-threshold 기본값)
	 * @param caller              호출한 앱/서비스 식별자(tenant)
	 * @param sourceId            검색 범위를 좁힐 문서 식별자(없으면 caller 범위 전체)
	 */
	private VectorStoreDocumentRetriever buildRetriever(Integer topK, Double similarityThreshold, String caller, String sourceId) {
		VectorStore vectorStore = this.requireVectorStore();
		VectorStoreDocumentRetriever.Builder builder = VectorStoreDocumentRetriever.builder()
			.vectorStore(vectorStore)
			.topK(topK == null ? this.defaultTopK() : topK)
			.similarityThreshold(similarityThreshold == null ? this.defaultSimilarityThreshold() : similarityThreshold);
		Filter.Expression filter = this.buildFilter(caller, sourceId);
		if (filter != null) {
			builder.filterExpression(filter);
		}
		return builder.build();
	}

	/**
	 * <pre>
	 * 선택적원칙: ragEnabled=true인 요청에만 이 Advisor를 붙인다 - caller의 문서만 검색되도록 tenant 필터를 강제한다.
	 * allowEmptyContext(true)로 둬서, 검색 결과가 하나도 없어도(또는 RAG 자체가 이 요청에 무관해도) 질의를
	 * "모른다고 답하라"는 문구로 바꿔치기하지 않고 원래 질의 그대로 진행시킨다 - Agent의 system prompt에 이미
	 * 있는 업무 지식만으로도 충분히 답할 수 있어야 하기 때문이다.
	 * </pre>
	 *
	 * @param caller 호출한 앱/서비스 식별자(tenant)
	 */
	public Advisor buildAdvisor(String caller) {
		VectorStoreDocumentRetriever retriever = this.buildRetriever(null, null, caller, null);
		ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
			.promptTemplate(CONTEXT_PROMPT_TEMPLATE)
			.allowEmptyContext(true)
			.build();
		return RetrievalAugmentationAdvisor.builder()
			.documentRetriever(retriever)
			.queryAugmenter(queryAugmenter)
			.build();
	}

	/**
	 * <pre>
	 * caller(=tenant_id)의 문서 범위로만 검색을 제한한다 - caller가 없으면(security.auth 꺼짐) 기존과 동일하게 전체 검색.
	 * </pre>
	 *
	 * @param request 검색 조건(질의어, topK 등)
	 * @param caller  호출한 앱/서비스 식별자(tenant)
	 */
	public List<RetrievedChunk> search(RagSearchRequest request, String caller) {
		VectorStoreDocumentRetriever retriever = this.buildRetriever(request.topK(), request.similarityThreshold(), caller, request.sourceId());
		List<Document> documents = retriever.retrieve(new Query(request.query()));
		List<RetrievedChunk> retrieved = new ArrayList<>(documents.size());
		for (Document doc : documents) {
			retrieved.add(new RetrievedChunk(doc.getText(), doc.getMetadata(), doc.getScore()));
		}
		return retrieved;
	}

}

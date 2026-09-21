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
 * "이미 임베딩해 둔 문서를 검색해서 LLM에게 참고 자료로 넘겨주는" 진짜 의미의 RAG
 * (Retrieval-Augmented Generation, 검색 증강 생성) 담당 클래스입니다. 문서를 임베딩으로 만들어
 * 벡터스토어에 넣거나 빼는 작업(api.service.EmbedService가 담당)과는 완전히 분리되어 있습니다 -
 * 이 클래스는 이미 저장되어 있는 임베딩을 "읽기"만 합니다.
 *
 * 검색 체인은 Spring AI의 RAG 모듈(spring-ai-rag)이 제공하는 부품을 그대로 조립해서 씁니다:
 * VectorStoreDocumentRetriever(실제 검색을 담당)와 ContextualQueryAugmenter(검색 결과를 프롬프트에
 * 끼워 넣는 역할)를 RetrievalAugmentationAdvisor 하나로 묶습니다. buildAdvisor()(AGENT step의
 * ragEnabled 경로에서 쓰임)와 search()(TOOL step의 RagSearchTool 경로에서 쓰임) 둘 다 결국 같은
 * VectorStoreDocumentRetriever 생성 로직(buildRetriever())을 거치기 때문에, "무엇을 검색 대상으로
 * 볼지"(topK, 유사도 임계값, tenant 필터)는 이 클래스 안 딱 한 곳에서만 정해집니다.
 *
 * VectorStore(pgvector) 빈은 dstone.ai.rag.enabled=true이고 spring.ai.model.embedding이 제대로
 * 설정되어 있을 때만 Spring AI가 만들어 줍니다. 즉 설정에 따라 이 빈이 아예 존재하지 않을 수도
 * 있는데, 이 사실을 확인하는 로직은 requireVectorStore() 한 곳에만 모아뒀습니다. 그래서 이
 * RagRetrievalChain 클래스 자체는 RAG 설정 여부와 무관하게 항상 등록되고, "지금 RAG를 실제로
 * 쓸 수 있는 상태인가"는 각 메소드가 실제로 호출되는 시점에만 판단됩니다.
 */
@Component
public class RagRetrievalChain extends BaseService {

	/**
	 * 검색 결과가 있을 때 프롬프트에 끼워 넣을 템플릿입니다.
	 *
	 * Spring AI의 ContextualQueryAugmenter가 기본으로 쓰는 템플릿은 "컨텍스트(참고 자료)에 없는
	 * 내용이면 모른다고 답하라"고 강제합니다. 이건 순수 지식베이스 QA봇에는 맞는 동작이지만, 이
	 * 프로젝트에서 ragEnabled를 켠 Agent(예: sql-conversion-agent)는 이미 자기 system prompt
	 * 안에 업무를 수행할 규칙과 전문 지식을 다 갖고 있고, RAG는 그 위에 참고 자료 하나를 더
	 * 얹어주는 보조 수단일 뿐입니다. 그래서 이 프로젝트에서는 그런 강제 문구 없이 참고자료만
	 * 덧붙이는 템플릿을 따로 씁니다.
	 *
	 * {context}는 검색된 문서 내용이, {query}는 원래 사용자 질의가 들어가는 자리인데, 둘 다
	 * Spring AI의 ContextualQueryAugmenter가 알아서 채워주는 플레이스홀더입니다.
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
	 * RAG를 실제로 쓸 수 있는 상태인지 확인하고, 쓸 수 있으면 VectorStore 빈을 돌려줍니다.
	 * dstone.ai.rag.enabled=true로 켜져 있고 VectorStore 빈이 실제로 떠 있을 때만 통과시키고,
	 * 둘 중 하나라도 아니면 예외를 던집니다.
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

	/** dstone.ai.rag.retrieval.top-k 설정값을 읽어 돌려줍니다. 설정이 없으면 5를 기본값으로 씁니다. */
	private int defaultTopK() {
		String topK = this.configProperty.getProperty("dstone.ai.rag.retrieval.top-k");
		return StringUtil.isEmpty(topK) ? 5 : Integer.parseInt(topK);
	}

	/** dstone.ai.rag.retrieval.similarity-threshold 설정값을 읽어 돌려줍니다. 설정이 없으면 0.35를 기본값으로 씁니다. */
	private double defaultSimilarityThreshold() {
		// bge-m3(로컬에서 쓰는 임베딩 모델) 기준으로 실제 측정해 보면, 정말 관련 있는 문서/질의
		// 쌍끼리도 코사인 유사도가 0.5를 넘지 못하는 경우가 흔합니다(0.48 정도가 흔함). 그래서
		// 기본값을 0.5로 두면 실제로는 관련 있는 문서가 있는데도 검색 결과가 통째로 비어버리는
		// 문제가 생겨서, 그보다 낮은 0.35를 기본값으로 씁니다.
		String threshold = this.configProperty.getProperty("dstone.ai.rag.retrieval.similarity-threshold");
		return StringUtil.isEmpty(threshold) ? 0.35 : Double.parseDouble(threshold);
	}

	/**
	 * caller(=tenant_id)와 sourceId 조건을 하나의 Filter.Expression으로 합쳐 줍니다. 둘 다 없으면
	 * null을 돌려주는데, 이건 "필터 없이 전체 검색"을 뜻합니다. caller가 없는 경우(예:
	 * security.auth가 꺼진 배포 환경)는 기존과 똑같이 tenant 필터 없이 동작해야 하므로, "여러
	 * 앱의 문서를 서로 격리할지 말지"를 판단하는 지점은 이 메소드 하나뿐입니다.
	 *
	 * @param caller   호출한 앱/서비스를 나타내는 식별자(tenant)
	 * @param sourceId 검색 범위를 좁힐 문서의 논리적 식별자
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
	 * "무엇을 검색 대상으로 삼을지"(개수 제한, 유사도 기준, tenant 필터)를 정하는 단 하나의
	 * 지점입니다. buildAdvisor()와 search() 둘 다 결국 이 메소드를 거쳐서 검색기를 만듭니다.
	 *
	 * @param topK                검색 결과 최대 개수(null이면 dstone.ai.rag.retrieval.top-k 기본값을 씁니다)
	 * @param similarityThreshold 검색 결과 유사도 임계값(null이면 dstone.ai.rag.retrieval.similarity-threshold 기본값을 씁니다)
	 * @param caller              호출한 앱/서비스를 나타내는 식별자(tenant)
	 * @param sourceId            검색 범위를 좁힐 문서 식별자(없으면 caller 범위 전체를 검색합니다)
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
	 * ragEnabled=true로 설정된 요청에만 붙이는 Advisor를 만들어 줍니다. 이 Advisor가 붙으면
	 * caller의 문서만 검색되도록 tenant 필터가 강제로 걸립니다.
	 *
	 * topK, similarityThreshold, allowEmptyContext를 전부 기본값(null이면 전역 설정값을 쓰고,
	 * allowEmptyContext는 true)으로 쓰는 간단한 버전입니다. Agent 정의에 개별 설정이 없을 때는
	 * buildAdvisor(caller, null, null, null)을 호출하는 것과 완전히 같습니다.
	 *
	 * @param caller 호출한 앱/서비스를 나타내는 식별자(tenant)
	 */
	public Advisor buildAdvisor(String caller) {
		return this.buildAdvisor(caller, null, null, null);
	}

	/**
	 * ragEnabled=true로 설정된 요청에만 붙이는 Advisor를 만들어 줍니다. 이 Advisor가 붙으면
	 * caller의 문서만 검색되도록 tenant 필터가 강제로 걸립니다.
	 *
	 * topK, similarityThreshold, allowEmptyContext는 전부 null로 두면 전역 기본값
	 * (dstone.ai.rag.retrieval.* 설정, allowEmptyContext는 true)을 쓰고, 값을 넣으면 이번
	 * 호출에만(보통은 AgentDefinition.ragTopK / ragSimilarityThreshold / ragAllowEmptyContext에서
	 * 넘어온 값) 그 값이 적용됩니다. RAG를 쓰는 Agent가 여러 개 늘어나더라도, 검색 범위나 개수,
	 * 그리고 "근거 자료가 없을 때 어떻게 답할지"를 Agent마다 다르게 가져갈 수 있도록 하기 위한
	 * 설계입니다.
	 *
	 * allowEmptyContext가 true(기본값)이면, 검색 결과가 하나도 없거나 RAG 자체가 이 요청과
	 * 무관하더라도 질의를 "모른다고 답하라"는 문구로 바꿔치기하지 않고 원래 질의 그대로
	 * 진행시킵니다. 이 프로젝트의 Agent들처럼 system prompt에 이미 필요한 업무 지식을 갖고
	 * 있고 RAG는 그저 보조 수단일 때 어울리는 동작입니다. false로 주면 Spring AI
	 * ContextualQueryAugmenter의 원래 동작(근거가 없으면 "모른다"고 답하도록 강제하는 동작)으로
	 * 돌아갑니다. "컨텍스트 밖의 답변은 절대 허용하면 안 되는" 순수 지식베이스 QA 같은 Agent에
	 * 쓰면 됩니다.
	 *
	 * @param caller              호출한 앱/서비스를 나타내는 식별자(tenant)
	 * @param topK                검색 결과 최대 개수(null이면 dstone.ai.rag.retrieval.top-k 기본값을 씁니다)
	 * @param similarityThreshold 검색 결과 유사도 임계값(null이면 dstone.ai.rag.retrieval.similarity-threshold 기본값을 씁니다)
	 * @param allowEmptyContext   검색 결과가 없을 때 원래 질의 그대로 진행할지 여부(null이면 true로 취급합니다)
	 */
	public Advisor buildAdvisor(String caller, Integer topK, Double similarityThreshold, Boolean allowEmptyContext) {
		VectorStoreDocumentRetriever retriever = this.buildRetriever(topK, similarityThreshold, caller, null);
		ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
			.promptTemplate(CONTEXT_PROMPT_TEMPLATE)
			.allowEmptyContext(allowEmptyContext == null ? true : allowEmptyContext)
			.build();
		return RetrievalAugmentationAdvisor.builder()
			.documentRetriever(retriever)
			.queryAugmenter(queryAugmenter)
			.build();
	}

	/**
	 * caller(=tenant_id)의 문서 범위로만 검색을 제한해서 실행합니다. caller가 없으면(예:
	 * security.auth가 꺼진 환경) 기존과 똑같이 전체 문서를 대상으로 검색합니다.
	 *
	 * @param request 검색 조건(질의어, topK 등)
	 * @param caller  호출한 앱/서비스를 나타내는 식별자(tenant)
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

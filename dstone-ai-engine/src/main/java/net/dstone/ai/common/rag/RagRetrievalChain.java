package net.dstone.ai.common.rag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import net.dstone.ai.api.dto.RagSearchRequest;
import net.dstone.ai.api.dto.RetrievedChunk;
import net.dstone.ai.common.knowledge.KnowledgeCallException;
import net.dstone.ai.common.knowledge.KnowledgeClient;
import net.dstone.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * RAG의 "검색" 쪽입니다. 올려 둔 문서에서 질문과 가까운 조각을 찾아 줍니다.
 *
 * 검색은 이 엔진이 직접 하지 않고 dstone-knowledge에 맡깁니다(POST /api/search, 올린 일반 문서만).
 * dstone-knowledge는 뜻으로 찾기(벡터)와 이름으로 찾기를 같이 합니다. 이 엔진에는 임베딩 모델도 벡터 저장소도 없습니다.
 *
 * 쓰는 곳은 둘이고, 둘 다 search() 하나를 거칩니다. 그래서 "무엇을 검색 대상으로 볼지"(개수, 유사도 기준, caller의 문서만)는
 * 이 클래스 안 한 곳에서만 정해집니다.
 *   - buildAdvisor()  Agent의 ragEnabled 경로. 찾은 조각을 질문 뒤에 [참고자료]로 붙여 주는 Advisor를 만든다
 *   - search()        Tool(tools.rag.RagSearchTool)과 검색 미리보기 API(api.controller.RagController)
 *
 * 이 클래스 자체는 RAG 설정과 무관하게 항상 등록되고, "지금 RAG를 쓸 수 있는 상태인가"는 검색하는 시점에 판단합니다(requireRag()).
 * </pre>
 */
@Component
public class RagRetrievalChain extends BaseService {

	private static final String SEARCH_PATH = "/api/search";

	/** dstone-knowledge가 한 번에 돌려주는 최대 건수 */
	private static final int MAX_CANDIDATES = 50;

	/**
	 * <pre>
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
	 * </pre>
	 */
	private static final PromptTemplate CONTEXT_PROMPT_TEMPLATE = new PromptTemplate("""
		{query}

		[참고자료]
		{context}
		""");

	@Autowired
	private KnowledgeClient knowledgeClient;
	@Autowired
	private ConfigProperty configProperty;

	/**
	 * <pre>
	 * RAG를 실제로 쓸 수 있는 상태인지 확인합니다.
	 * dstone.ai.rag.enabled=true로 켜져 있고 dstone-knowledge의 주소가 설정돼 있을 때만 통과시키고, 아니면 예외를 던집니다.
	 * </pre>
	 */
	private void requireRag() {
		if (!Boolean.parseBoolean(this.configProperty.getProperty("dstone.ai.rag.enabled"))) {
			throw new IllegalStateException("RAG가 비활성화되어 있습니다(dstone.ai.rag.enabled=false 또는 미설정).");
		}
		if (!this.knowledgeClient.isConfigured()) {
			throw new IllegalStateException("dstone.ai.rag.enabled=true인데 dstone-knowledge 주소가 없습니다. dstone.ai.tool.knowledge.base-url 설정을 확인하십시오.");
		}
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
	 * <pre>
	 * ragEnabled=true로 설정된 요청에만 붙이는 Advisor를 만들어 줍니다. 
	 * 이 Advisor가 붙으면 caller가 올린 문서에서만 찾습니다.
	 *
	 * topK, similarityThreshold, allowEmptyContext를 전부 기본값(null이면 전역 설정값을 쓰고,
	 * allowEmptyContext는 true)으로 쓰는 간단한 버전입니다. Agent 정의에 개별 설정이 없을 때는
	 * buildAdvisor(caller, null, null, null)을 호출하는 것과 완전히 같습니다.
	 * </pre>
	 *
	 * @param caller 호출한 앱/서비스를 나타내는 식별자(tenant)
	 */
	public Advisor buildAdvisor(String caller) {
		return this.buildAdvisor(caller, null, null, null);
	}

	/**
	 * <pre>
	 * ragEnabled=true로 설정된 요청에만 붙이는 Advisor를 만들어 줍니다. 이 Advisor가 붙으면
	 * caller가 올린 문서에서만 찾습니다.
	 *
	 * topK, similarityThreshold, allowEmptyContext는 전부 null로 두면 전역 기본값
	 * (dstone.ai.rag.retrieval.* 설정, allowEmptyContext는 true)을 쓰고, 값을 넣으면 이번
	 * 호출에만(보통은 AgentDefinition.ragTopK / ragSimilarityThreshold / ragAllowEmptyContext에서 넘어온 값) 그 값이 적용됩니다. 
	 * RAG를 쓰는 Agent가 여러 개 늘어나더라도, 검색 범위나 개수, 그리고 "근거 자료가 없을 때 어떻게 답할지"를 Agent마다 다르게 가져갈 수 있도록 하기 위한 설계입니다.
	 *
	 * allowEmptyContext가 true(기본값)이면, 검색 결과가 하나도 없거나 RAG 자체가 이 요청과 무관하더라도 
	 * 질의를 "모른다고 답하라"는 문구로 바꿔치기하지 않고 원래 질의 그대로 진행시킵니다. 
	 * 이 프로젝트의 Agent들처럼 system prompt에 이미 필요한 업무 지식을 갖고 있고 RAG는 그저 보조 수단일 때 어울리는 동작입니다. 
	 * false로 주면 Spring AI ContextualQueryAugmenter의 원래 동작(근거가 없으면 "모른다"고 답하도록 강제하는 동작)으로 돌아갑니다. 
	 * "컨텍스트 밖의 답변은 절대 허용하면 안 되는" 순수 지식베이스 QA 같은 Agent에 쓰면 됩니다.
	 * </pre>
	 *
	 * @param caller              호출한 앱/서비스를 나타내는 식별자(tenant)
	 * @param topK                검색 결과 최대 개수(null이면 dstone.ai.rag.retrieval.top-k 기본값을 씁니다)
	 * @param similarityThreshold 검색 결과 유사도 임계값(null이면 dstone.ai.rag.retrieval.similarity-threshold 기본값을 씁니다)
	 * @param allowEmptyContext   검색 결과가 없을 때 원래 질의 그대로 진행할지 여부(null이면 true로 취급합니다)
	 */
	public Advisor buildAdvisor(final String caller, final Integer topK, final Double similarityThreshold, Boolean allowEmptyContext) {
		// 설정이 잘못됐으면 LLM을 부르기 전에 여기서 알린다.
		this.requireRag();
		DocumentRetriever retriever = new DocumentRetriever() {
			@Override
			public List<Document> retrieve(Query query) {
				List<RetrievedChunk> chunks = RagRetrievalChain.this.search(new RagSearchRequest(query.text(), topK, similarityThreshold, null), caller);
				List<Document> documents = new ArrayList<Document>(chunks.size());
				for (int i = 0; i < chunks.size(); i++) {
					RetrievedChunk chunk = chunks.get(i);
					documents.add(Document.builder().text(chunk.text()).metadata(chunk.metadata()).score(chunk.score()).build());
				}
				return documents;
			}
		};
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
	 * <pre>
	 * 올려 둔 문서에서 질문과 가까운 조각을 찾습니다. caller가 있으면 그 caller가 올린 문서에서만 찾고,
	 * 없으면(인증을 꺼 둔 환경) 전체 문서에서 찾습니다.
	 *
	 * dstone-knowledge에서 넉넉히 받아 온 뒤 여기서 걸러 냅니다.
	 *   1. caller의 문서인가 (RagSourceId)
	 *   2. request.sourceId가 있으면 그 문서인가
	 *   3. 유사도가 기준 이상인가. 다만 질문에 든 이름(영문 낱말)이 글자 그대로 들어 있어서 찾힌 조각은 유사도와 상관없이 남긴다.
	 *      이름이 정확히 맞은 것은 뜻이 덜 가까워 보여도 찾던 것일 가능성이 높고, 방금 올려서 아직 임베딩이 안 된 문서는 유사도가 아예 없다.
	 * </pre>
	 *
	 * @param request 검색 조건(질의어, topK 등)
	 * @param caller  호출한 앱/서비스를 나타내는 식별자(tenant)
	 */
	public List<RetrievedChunk> search(RagSearchRequest request, String caller) {
		this.requireRag();
		int topK = request.topK() == null ? this.defaultTopK() : request.topK().intValue();
		double threshold = request.similarityThreshold() == null ? this.defaultSimilarityThreshold() : request.similarityThreshold().doubleValue();
		String storedSourceId = StringUtil.isEmpty(request.sourceId()) ? null : RagSourceId.stored(caller, request.sourceId());

		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("query", request.query());
		// 올린 일반 문서에서만 찾는다. 코드 분석 결과는 tools.knowledge.KnowledgeTool의 몫이다.
		body.put("sourceTypes", Arrays.asList("DOCUMENT"));
		// 걸러 내고도 topK개가 남도록 넉넉히 받는다.
		body.put("topK", Integer.valueOf(Math.min(MAX_CANDIDATES, Math.max(topK * 4, 20))));

		JsonNode hits;
		try {
			hits = this.knowledgeClient.post(SEARCH_PATH, body).path("hits");
		} catch (KnowledgeCallException e) {
			throw new IllegalStateException("문서를 검색하지 못했습니다: " + e.reason());
		}

		List<RetrievedChunk> retrieved = new ArrayList<RetrievedChunk>();
		for (int i = 0; i < hits.size() && retrieved.size() < topK; i++) {
			JsonNode hit = hits.get(i);
			String storedId = hit.path("documentId").asText(null);
			if (!RagSourceId.visibleTo(caller, storedId)) {
				continue;
			}
			if (storedSourceId != null && !storedSourceId.equals(storedId)) {
				continue;
			}
			Double score = hit.hasNonNull("score") ? Double.valueOf(hit.get("score").asDouble()) : null;
			boolean foundByName = hit.path("keywordScore").asInt(0) > 0;
			if (!foundByName && (score == null || score.doubleValue() < threshold)) {
				continue;
			}
			Map<String, Object> metadata = new LinkedHashMap<String, Object>();
			this.putIfPresent(metadata, "sourceId", RagSourceId.shown(caller, storedId));
			this.putIfPresent(metadata, "tenant", RagSourceId.tenantOf(storedId));
			this.putIfPresent(metadata, "title", hit.path("title").asText(null));
			this.putIfPresent(metadata, "fileName", hit.path("path").asText(null));
			if (hit.hasNonNull("chunkNo")) {
				metadata.put("chunkNo", Integer.valueOf(hit.get("chunkNo").asInt()));
			}
			retrieved.add(new RetrievedChunk(hit.path("content").asText(""), metadata, score));
		}
		return retrieved;
	}

	/** 값이 있을 때만 담습니다(Spring AI의 Document는 metadata에 null 값을 받지 않습니다). */
	private void putIfPresent(Map<String, Object> metadata, String key, String value) {
		if (!StringUtil.isEmpty(value)) {
			metadata.put(key, value);
		}
	}

}

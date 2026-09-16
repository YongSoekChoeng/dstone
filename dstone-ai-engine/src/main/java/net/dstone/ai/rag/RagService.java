package net.dstone.ai.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder.Op;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import net.dstone.ai.api.dto.IngestResponse;
import net.dstone.ai.api.dto.RagSearchRequest;
import net.dstone.ai.api.dto.RetrievedChunk;
import net.dstone.ai.common.consts.Constants;
import net.dstone.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * RAG(검색-증강 생성) 기능 - 문서 적재/삭제(api.controller.RagController)와 채팅 중 자동 검색 증강(runtime.agent.AgentExecutor), Workflow의 RAG
 * step(runtime.step.RagStepRunner)이 모두 이 클래스 하나를 통해 VectorStore를 다룬다.
 *
 * 실제 검색에 쓰이는 VectorStore(pgvector)는 dstone.ai.rag.enabled=true이고 spring.ai.model.embedding이 올바르게 설정돼 있을 때만 Spring AI가
 * 만들어주는 빈이라(VectorStore가 아예 없을 수 있다), 이 빈의 존재 여부를 requireVectorStore() 한 곳에서만 흡수한다 - 그래서 이 클래스는 항상 등록되고, "RAG를 실제로 쓸 수
 * 있는가"는 메서드를 호출한 시점에만 판단된다.
 */
@Service
public class RagService extends BaseService {

	@Autowired
	private ObjectProvider<VectorStore> vectorStoreProvider;
	@Autowired
	private ConfigProperty configProperty;

	/** 최상위원칙: dstone.ai.rag.enabled=true이고 VectorStore 빈이 실제로 떠 있을 때만 통과시킨다. */
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
	 * caller(=tenant_id)와 sourceId 조건을 하나의 Filter.Expression으로 합쳐준다. 둘 다 없으면 null(=필터 없음)을 돌려준다 - caller가 없는
	 * 경우(security.auth가 꺼진 배포)는 기존과 동일하게 tenant 필터 없이 동작해야 하므로, 이 메서드가 유일하게 "격리를 켤지" 판단하는 지점이다.
	 * 
	 * @param caller   호출한 앱/서비스 식별자
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
	 * 선택적원칙: ragEnabled=true인 요청에만 이 Advisor를 붙인다 - caller의 문서만 검색되도록 tenant 필터를 강제한다.
	 * 
	 * @param caller 호출한 앱/서비스 식별자
	 */
	public Advisor getRagSpecAdvisor(String caller) {
		VectorStore vectorStore = this.requireVectorStore();
		SearchRequest.Builder requestBuilder = SearchRequest.builder().topK(this.defaultTopK()).similarityThreshold(this.defaultSimilarityThreshold());
		Filter.Expression filter = this.buildFilter(caller, null);
		if (filter != null) {
			requestBuilder.filterExpression(filter);
		}
		return QuestionAnswerAdvisor.builder(vectorStore).searchRequest(requestBuilder.build()).build();
	}

	/**
	 * caller(=tenant_id)의 문서 범위로만 검색을 제한한다 - caller가 없으면(security.auth 꺼짐) 기존과 동일하게 전체 검색.
	 * 
	 * @param request 검색 조건(질의어, topK 등)
	 * @param caller  호출한 앱/서비스 식별자
	 */
	public List<RetrievedChunk> search(RagSearchRequest request, String caller) {
		VectorStore vectorStore = this.requireVectorStore();
		SearchRequest.Builder builder = SearchRequest.builder().query(request.query()).topK(request.topK() == null ? this.defaultTopK() : request.topK())
			.similarityThreshold(request.similarityThreshold() == null ? this.defaultSimilarityThreshold() : request.similarityThreshold());
		Filter.Expression filter = this.buildFilter(caller, request.sourceId());
		if (filter != null) {
			builder.filterExpression(filter);
		}
		List<Document> documents = vectorStore.similaritySearch(builder.build());
		List<RetrievedChunk> retrieved = new ArrayList<>(documents.size());
		for (Document doc : documents) {
			retrieved.add(new RetrievedChunk(doc.getText(), doc.getMetadata(), doc.getScore()));
		}
		return retrieved;
	}

	/**
	 * 원문을 Tika로 추출 → TokenTextSplitter로 청킹 → VectorStore(pgvector)에 저장한다. sourceId는 호출하는 쪽이 정하는 논리적 문서 식별자(파일명, 업무키 등)로, 같은
	 * sourceId로 다시 적재하면 upsert처럼 동작하도록 새 청크를 넣기 전에 그 sourceId로 색인돼 있던 기존 청크를 먼저 지운다.
	 *
	 * caller(=tenant_id)가 있으면 청크마다 tenant metadata를 함께 태깅해서, search()/getRagSpecAdvisor()가 같은 caller의 문서만 검색하도록 격리한다.
	 * 
	 * @param resource 적재할 원문 파일
	 * @param sourceId 문서 논리 식별자(재적재 시 upsert 기준 키)
	 * @param caller   호출한 앱/서비스 식별자
	 */
	public IngestResponse ingest(Resource resource, String sourceId, String caller) {
		if (StringUtil.isEmpty(sourceId)) {
			throw new IllegalArgumentException("sourceId는 필수입니다(재적재 시 upsert 기준 키로 쓰임).");
		}
		VectorStore vectorStore = this.requireVectorStore();

		String chunkSize = this.configProperty.getProperty("dstone.ai.rag.ingest.chunk-size");
		TokenTextSplitter textSplitter = TokenTextSplitter.builder().withChunkSize(StringUtil.isEmpty(chunkSize) ? 800 : Integer.parseInt(chunkSize)).build();

		List<Document> extracted = new TikaDocumentReader(resource).get();
		List<Document> chunks = textSplitter.apply(extracted);
		List<Document> tagged = new ArrayList<>(chunks.size());
		for (Document chunk : chunks) {
			var mutator = chunk.mutate().metadata(Constants.Rag.SOURCE_ID_METADATA_KEY, sourceId);
			if (!StringUtil.isEmpty(caller)) {
				mutator.metadata(Constants.Rag.TENANT_METADATA_KEY, caller);
			}
			tagged.add(mutator.build());
		}

		if (tagged.isEmpty()) {
			// 텍스트 추출/청킹 결과가 비어 있으면 아무 것도 하지 않는다 - 여기서도 기존 청크를 지워버리면
			// 손상된 파일을 잘못 재적재했을 때 기존에 정상 적재돼 있던 데이터까지 날아간다.
			return new IngestResponse(sourceId, 0);
		}

		this.deleteBySourceId(sourceId, caller);
		vectorStore.add(tagged);
		return new IngestResponse(sourceId, tagged.size());
	}

	/**
	 * sourceId와 caller(=tenant_id) 조건을 함께 걸어 삭제한다 - 다른 tenant가 같은 sourceId를 썼어도 서로의 문서를 지우지 못한다.
	 * 
	 * @param sourceId 문서 논리 식별자
	 * @param caller   호출한 앱/서비스 식별자
	 */
	public void deleteBySourceId(String sourceId, String caller) {
		VectorStore vectorStore = this.requireVectorStore();
		vectorStore.delete(this.buildFilter(caller, sourceId));
	}

}

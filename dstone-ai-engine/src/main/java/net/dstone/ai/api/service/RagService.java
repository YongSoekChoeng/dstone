package net.dstone.ai.api.service;

import java.util.List;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import net.dstone.ai.api.dto.IngestResponse;
import net.dstone.ai.api.dto.RagSearchRequest;
import net.dstone.ai.api.dto.RetrievedChunk;
import net.dstone.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * RAG(검색-증강 생성) 기능 - 문서 적재/삭제(net.dstone.ai.api.controller.RagController)와 채팅 중
 * 자동 검색 증강(net.dstone.ai.api.service.ChatService)이 모두 이 클래스 하나를 통해 VectorStore를
 * 다룬다.
 *
 * ChatController/ChatService/RagController는 dstone.ai.rag.enabled 여부와 무관하게 항상 떠 있어야
 * 하는 반면, 실제 검색에 쓰이는 VectorStore(pgvector)는 dstone.ai.rag.enabled=true이고
 * spring.ai.model.embedding이 올바르게 설정돼 있을 때만 Spring AI가 만들어주는 빈이다. 이 간극을
 * requireVectorStore() 한 곳에서만 흡수한다 - 그래서 이 클래스는 @ConditionalOnProperty 없이 항상
 * 등록되고, ChatService/RagController는 평범하게 @Autowired RagService로 받으면 된다("RAG를 실제로
 * 쓸 수 있는가"는 이 클래스의 메서드를 호출한 시점에만 판단되고, 호출하지 않으면 아무 영향이 없다).
 */
@Service
public class RagService extends BaseService {

	private static final String SOURCE_ID_METADATA_KEY = "sourceId";

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

	/** 선택적원칙: ChatService가 ragEnabled=true인 요청에만 이 Advisor를 붙인다. */
	public Advisor getRagSpecAdvisor() {
		VectorStore vectorStore = this.requireVectorStore();
		SearchRequest searchRequest = SearchRequest.builder()
			.topK(this.defaultTopK())
			.similarityThreshold(this.defaultSimilarityThreshold())
			.build();
		return QuestionAnswerAdvisor.builder(vectorStore).searchRequest(searchRequest).build();
	}

	public List<RetrievedChunk> search(RagSearchRequest request) {
		VectorStore vectorStore = this.requireVectorStore();
		SearchRequest.Builder builder = SearchRequest.builder()
			.query(request.query())
			.topK(request.topK() == null ? this.defaultTopK() : request.topK())
			.similarityThreshold(
				request.similarityThreshold() == null ? this.defaultSimilarityThreshold() : request.similarityThreshold());
		if (!StringUtil.isEmpty(request.sourceId())) {
			builder.filterExpression(new FilterExpressionBuilder().eq(SOURCE_ID_METADATA_KEY, request.sourceId()).build());
		}
		List<Document> documents = vectorStore.similaritySearch(builder.build());
		return documents.stream().map(doc -> new RetrievedChunk(doc.getText(), doc.getMetadata(), doc.getScore())).toList();
	}

	/**
	 * 원문을 Tika로 추출 → TokenTextSplitter로 청킹 → VectorStore(pgvector)에 저장한다. sourceId는
	 * 호출하는 쪽이 정하는 논리적 문서 식별자(파일명, 업무키 등)로, 같은 sourceId로 다시 적재하면
	 * upsert처럼 동작하도록 새 청크를 넣기 전에 그 sourceId로 색인돼 있던 기존 청크를 먼저 지운다.
	 */
	public IngestResponse ingest(Resource resource, String sourceId) {
		if (StringUtil.isEmpty(sourceId)) {
			throw new IllegalArgumentException("sourceId는 필수입니다(재적재 시 upsert 기준 키로 쓰임).");
		}
		VectorStore vectorStore = this.requireVectorStore();

		String chunkSize = this.configProperty.getProperty("dstone.ai.rag.ingest.chunk-size");
		TokenTextSplitter textSplitter = TokenTextSplitter.builder()
			.withChunkSize(StringUtil.isEmpty(chunkSize) ? 800 : Integer.parseInt(chunkSize))
			.build();

		List<Document> extracted = new TikaDocumentReader(resource).get();
		List<Document> chunks = textSplitter.apply(extracted);
		List<Document> tagged = chunks.stream()
			.map(chunk -> chunk.mutate().metadata(SOURCE_ID_METADATA_KEY, sourceId).build())
			.toList();

		if (tagged.isEmpty()) {
			// 텍스트 추출/청킹 결과가 비어 있으면 아무 것도 하지 않는다 - 여기서도 기존 청크를 지워버리면
			// 손상된 파일을 잘못 재적재했을 때 기존에 정상 적재돼 있던 데이터까지 날아간다.
			return new IngestResponse(sourceId, 0);
		}

		this.deleteBySourceId(sourceId);
		vectorStore.add(tagged);
		return new IngestResponse(sourceId, tagged.size());
	}

	public void deleteBySourceId(String sourceId) {
		VectorStore vectorStore = this.requireVectorStore();
		vectorStore.delete(new FilterExpressionBuilder().eq(SOURCE_ID_METADATA_KEY, sourceId).build());
	}

}

package net.dstone.ai.rag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

	/**
	 * <pre>
	 * QuestionAnswerAdvisor 기본 템플릿은 "컨텍스트에 없으면 모른다고 답하라"고 강제한다 - 지식 베이스 QA봇에는
	 * 맞는 동작이지만, 이 프로젝트의 ragEnabled Agent(예: sql-conversion-agent)는 이미 자신의 system prompt에
	 * 해당 업무를 수행할 규칙/전문 지식을 전부 갖고 있고, RAG는 그 위에 참고 자료를 얹어주는 보조 수단일 뿐이다.
	 *
	 * "컨텍스트를 어떻게 다뤄야 하는지"에 대한 지시문을 여기(user 턴)에 자연어로 섞어 넣었더니, 모델이 그
	 * 지시문 자체를 지켜야 할 명령이 아니라 언급해도 되는 대화 내용처럼 취급해서 최종 답변에 그대로 echo하는
	 * 사고가 실전에서 관찰됐다(예: SQL만 반환해야 하는 Agent가 "참고 자료가 도움이 되면 참고하고..." 문구를
	 * 답변에 끼워 넣음). system prompt(각 Agent의 promptName)는 상대적으로 훨씬 안정적으로 지켜지므로,
	 * 컨텍스트를 어떻게 취급할지는 이 템플릿이 아니라 각 Agent의 system prompt에 규칙으로 명시하게 하고,
	 * 여기서는 "참고자료를 보여주기만" 하는 중립적인 최소 형태로 유지한다.
	 * </pre>
	 */
	private static final PromptTemplate RAG_SPEC_PROMPT_TEMPLATE = new PromptTemplate("""
		{query}

		[참고자료]
		{question_answer_context}
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
	 * 선택적원칙: ragEnabled=true인 요청에만 이 Advisor를 붙인다 - caller의 문서만 검색되도록 tenant 필터를 강제한다.
	 * </pre>
	 *
	 * @param caller 호출한 앱/서비스 식별자(tenant)
	 */
	public Advisor getRagSpecAdvisor(String caller) {
		VectorStore vectorStore = this.requireVectorStore();
		SearchRequest.Builder requestBuilder = SearchRequest.builder().topK(this.defaultTopK()).similarityThreshold(this.defaultSimilarityThreshold());
		Filter.Expression filter = this.buildFilter(caller, null);
		if (filter != null) {
			requestBuilder.filterExpression(filter);
		}
		return QuestionAnswerAdvisor.builder(vectorStore).searchRequest(requestBuilder.build()).promptTemplate(RAG_SPEC_PROMPT_TEMPLATE).build();
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
	 * <pre>
	 * 원문을 Tika로 추출 → TokenTextSplitter로 청킹 → VectorStore(pgvector)에 저장한다. sourceId는 호출하는 쪽이 정하는 논리적 문서 식별자(파일명, 업무키 등)로, 같은
	 * sourceId로 다시 적재하면 upsert처럼 동작하도록 새 청크를 넣기 전에 그 sourceId로 색인돼 있던 기존 청크를 먼저 지운다.
	 *
	 * caller(=tenant_id)가 있으면 청크마다 tenant metadata를 함께 태깅해서, search()/getRagSpecAdvisor()가 같은 caller의 문서만 검색하도록 격리한다.
	 * </pre>
	 *
	 * @param resource 적재할 원문 파일
	 * @param sourceId 문서 논리 식별자(재적재 시 upsert 기준 키)
	 * @param caller   호출한 앱/서비스 식별자(tenant)
	 */
	public IngestResponse ingest(Resource resource, String sourceId, String caller) {
		if (StringUtil.isEmpty(sourceId)) {
			throw new IllegalArgumentException("sourceId는 필수입니다(재적재 시 upsert 기준 키로 쓰임).");
		}
		VectorStore vectorStore = this.requireVectorStore();

		List<Document> chunks = isJsonlSource(resource) ? readJsonlAsDocuments(resource) // JSONL: 한 줄 = 원자적 단위 -> 청킹 생략
			: splitGenericDocument(resource); // PDF/DOCX 등 비정형 문서: 기존 Tika + 청킹 경로

		List<Document> tagged = new ArrayList<>(chunks.size());
		for (Document chunk : chunks) {
			var mutator = chunk.mutate().metadata(Constants.Rag.SOURCE_ID_METADATA_KEY, sourceId);
			if (!StringUtil.isEmpty(caller)) {
				mutator.metadata(Constants.Rag.TENANT_METADATA_KEY, caller);
			}
			tagged.add(mutator.build());
		}

		vectorStore.add(tagged);

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
	 * <pre>
	 * sourceId와 caller(=tenant_id) 조건을 함께 걸어 삭제한다 - 다른 tenant가 같은 sourceId를 썼어도 서로의 문서를 지우지 못한다.
	 * </pre>
	 *
	 * @param sourceId 문서 논리 식별자
	 * @param caller   호출한 앱/서비스 식별자(tenant)
	 */
	public void deleteBySourceId(String sourceId, String caller) {
		VectorStore vectorStore = this.requireVectorStore();
		vectorStore.delete(this.buildFilter(caller, sourceId));
	}
	
	private boolean isJsonlSource(Resource resource) {
	    String filename = resource.getFilename();
	    return filename != null && (filename.endsWith(".jsonl") || filename.endsWith(".json"));
	}

	private List<Document> splitGenericDocument(Resource resource) {
		String chunkSize = this.configProperty.getProperty("dstone.ai.rag.ingest.chunk-size");
		TokenTextSplitter textSplitter = TokenTextSplitter.builder().withChunkSize(StringUtil.isEmpty(chunkSize) ? 800 : Integer.parseInt(chunkSize)).build();
		List<Document> extracted = new TikaDocumentReader(resource).get();
		return textSplitter.apply(extracted);
	}

	private List<Document> readJsonlAsDocuments(Resource resource) {
		List<Document> documents = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.strip();
				if (line.isEmpty())
					continue;

				JsonNode node = mapper.readTree(line);
				String instruction = textOf(node, "instruction");
				String input = textOf(node, "input");
				String output = textOf(node, "output");
				String notes = textOf(node, "notes");

				String content = """
					[오라클 쿼리]
					%s

					[PostgreSQL 변환 결과]
					%s

					[설명/주의사항]
					%s
					""".formatted(input, output, notes.isEmpty() ? "-" : notes);

				Map<String, Object> metadata = new HashMap<>();
				metadata.put("instruction", instruction);
				metadata.put("has_notes", !notes.isEmpty());

				documents.add(new Document(content, metadata));
			}
		} catch (IOException e) {
			throw new IllegalStateException("JSONL 파싱 실패: " + resource.getFilename(), e);
		}
		return documents;
	}

	private String textOf(JsonNode node, String field) {
		JsonNode v = node.get(field);
		return (v == null || v.isNull()) ? "" : v.asText();
	}
}

package net.dstone.ai.api.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
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
import net.dstone.ai.common.consts.Constants;
import net.dstone.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * 문서를 임베딩으로 만들어 벡터스토어(pgvector)에 넣고 빼는 일만 한다 - "RAG가 내부적으로 동작하기 위한 재료(임베딩)를 준비"하는
 * 데이터 파이프라인이지, RAG(검색-증강) 자체는 아니다. 검색은 common.rag.RagRetrievalChain의 책임이다.
 *
 * api.controller.EmbedController(문서 업로드/삭제 API, dstone-boot의 문서 관리 화면이 호출)와 api.controller.WorkFlowController
 * 같은 "외부에서 호출하는 관리 작업"의 진입점이라 rag 패키지가 아니라 api.service 패키지에 둔다.
 */
@Service
public class EmbedService extends BaseService {

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

	/**
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
	 * 원문을 Tika로 추출 → TokenTextSplitter로 청킹 → VectorStore(pgvector)에 저장한다. sourceId는 호출하는 쪽이 정하는 논리적 문서 식별자(파일명, 업무키 등)로, 같은
	 * sourceId로 다시 적재하면 upsert처럼 동작하도록 새 청크를 넣기 전에 그 sourceId로 색인돼 있던 기존 청크를 먼저 지운다.
	 *
	 * caller(=tenant_id)가 있으면 청크마다 tenant metadata를 함께 태깅해서, search()/buildAdvisor()가 같은 caller의 문서만 검색하도록 격리한다.
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

		List<Document> chunks = this.isJsonlSource(resource) ? this.readJsonlAsDocuments(resource) // JSONL: 한 줄 = 원자적 단위 -> 청킹 생략
			: this.splitGenericDocument(resource); // PDF/DOCX 등 비정형 문서: 기존 Tika + 청킹 경로

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
				String instruction = this.textOf(node, "instruction");
				String input = this.textOf(node, "input");
				String output = this.textOf(node, "output");
				String notes = this.textOf(node, "notes");

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

package net.dstone.ai.api.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.api.dto.DocumentSourceSummary;
import net.dstone.ai.api.dto.IngestResponse;
import net.dstone.ai.common.consts.Constants;
import net.dstone.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * 문서를 임베딩으로 만들어서 벡터스토어(pgvector)에 넣고 빼는 일을 담당합니다.
 *
 * 이 클래스가 하는 일은 "RAG가 나중에 검색할 수 있도록 재료(임베딩)를 미리 준비해두는 것"까지입니다.
 * 실제로 검색어를 받아 검색하는 RAG(검색-증강) 기능 자체는 common.rag.RagRetrievalChain이 따로 담당합니다.
 *
 * 이 클래스는 api.controller.EmbedController(문서 업로드/삭제 API, dstone-boot의 문서 관리 화면이
 * 호출합니다)나 api.controller.WorkFlowController처럼 "외부에서 호출하는 관리 작업"의 진입점 역할을
 * 하기 때문에, rag 패키지가 아니라 api.service 패키지에 두었습니다.
 */
@Service
public class EmbedService extends BaseService {

	@Autowired
	private ObjectProvider<VectorStore> vectorStoreProvider;
	@Autowired
	private ConfigProperty configProperty;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * RAG 관련 기능을 쓰기 전에 항상 거치는 관문입니다. dstone.ai.rag.enabled=true로 켜져 있고,
	 * VectorStore 빈이 실제로 떠 있을 때만 통과시키고, 그렇지 않으면 바로 예외를 던집니다.
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
	 * caller(tenant)와 sourceId 조건으로 벡터스토어를 걸러낼 필터 조건식을 만들어줍니다. 둘 다
	 * 비어 있으면 아무 조건 없이 전체를 대상으로 하겠다는 뜻으로 null을 돌려줍니다.
	 *
	 * @param caller   이 문서를 적재한 앱이나 서비스를 가리키는 식별자(tenant)입니다. 비워도 됩니다.
	 * @param sourceId 문서를 가리키는 논리적인 식별자입니다. 비워도 됩니다.
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
	 * 문서 하나를 벡터스토어에 적재합니다. 순서는 이렇습니다: 먼저 Tika로 원문 텍스트를 뽑아내고,
	 * TokenTextSplitter로 잘게 청크(조각)로 나눈 다음, 그 청크들을 VectorStore(pgvector)에 저장합니다.
	 *
	 * sourceId는 호출하는 쪽이 정해서 넘기는 논리적인 문서 식별자입니다(파일명이나 업무 키 등을 쓰면
	 * 됩니다). 같은 sourceId로 다시 적재하면 upsert처럼 동작하도록, 새 청크를 넣기 전에 그 sourceId로
	 * 이미 적재되어 있던 기존 청크를 먼저 지웁니다.
	 *
	 * caller(=tenant_id) 값이 있으면 각 청크에 tenant metadata를 함께 붙여둡니다. 이렇게 태깅해두면
	 * search()나 buildAdvisor()가 검색할 때 같은 caller의 문서끼리만 서로 보이도록 격리할 수 있습니다.
	 *
	 * @param resource 적재할 원문 파일입니다.
	 * @param sourceId 문서를 가리키는 논리적인 식별자입니다. 재적재할 때 upsert 여부를 판단하는 기준 키로도 쓰입니다.
	 * @param caller   이 문서를 적재하는 앱이나 서비스를 가리키는 식별자(tenant)입니다.
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
			// 텍스트 추출이나 청킹 결과가 비어 있으면 아무 것도 하지 않고 그대로 돌아갑니다. 여기서도
			// 기존 청크를 지워버리면, 손상된 파일을 실수로 재적재했을 때 기존에 멀쩡히 적재돼 있던
			// 데이터까지 함께 날아가 버립니다.
			return new IngestResponse(sourceId, 0);
		}

		this.deleteBySourceId(sourceId, caller);
		vectorStore.add(tagged);
		return new IngestResponse(sourceId, tagged.size());
	}

	/**
	 * sourceId와 caller(=tenant_id) 조건을 함께 걸어서 삭제합니다. 이렇게 두 조건을 같이 걸어두면,
	 * 다른 tenant가 우연히 같은 sourceId를 썼더라도 서로의 문서를 지울 수 없습니다.
	 *
	 * @param sourceId 지울 문서를 가리키는 논리적인 식별자입니다.
	 * @param caller   이 요청을 보낸 앱이나 서비스를 가리키는 식별자(tenant)입니다.
	 */
	public void deleteBySourceId(String sourceId, String caller) {
		VectorStore vectorStore = this.requireVectorStore();
		vectorStore.delete(this.buildFilter(caller, sourceId));
	}

	/**
	 * 지금 vector_store에 어떤 sourceId들이 적재되어 있는지, 각각 몇 개의 청크로 어떤 tenant에
	 * 태깅되어 있는지를 조회합니다.
	 *
	 * VectorStore 인터페이스 자체에는 "메타데이터 기준으로 집계해서 조회하는" 기능이 없기 때문에,
	 * pgvector 테이블을 JdbcTemplate으로 직접 조회합니다(runtime.workflow.execution.WorkFlowExecutionStore와
	 * 마찬가지로, 이 모듈은 MyBatis를 쓰지 않습니다). 테이블명은 코드에 직접 박아두지 않고
	 * spring.ai.vectorstore.pgvector.table-name 설정값에서 읽어옵니다. 다만 JdbcTemplate은 테이블명을
	 * 바인드 파라미터로 넘길 수 없어서 SQL 문자열에 그대로 이어붙여야 하는데, 이때 임의의 값이 SQL에
	 * 섞여 들어가지 않도록 validateTableName()으로 영숫자와 밑줄만 허용해 방어합니다.
	 *
	 * @param caller 이 tenant로 태깅된 문서만 걸러서 보고 싶을 때 지정합니다. 비워두면 전체를 돌려줍니다.
	 */
	public List<DocumentSourceSummary> listSources(String caller) {
		this.requireVectorStore();
		String table = this.validateTableName(this.vectorStoreTableName());
		String sql = "SELECT metadata->>'" + Constants.Rag.SOURCE_ID_METADATA_KEY + "' AS source_id, metadata->>'" + Constants.Rag.TENANT_METADATA_KEY + "' AS tenant, COUNT(*) AS chunk_count FROM "
			+ table + (StringUtil.isEmpty(caller) ? "" : " WHERE metadata->>'" + Constants.Rag.TENANT_METADATA_KEY + "' = ?") + " GROUP BY 1, 2 ORDER BY 1";
		RowMapper<DocumentSourceSummary> rowMapper = new RowMapper<DocumentSourceSummary>() {
			@Override
			public DocumentSourceSummary mapRow(ResultSet rs, int rowNum) throws SQLException {
				return new DocumentSourceSummary(rs.getString("source_id"), rs.getString("tenant"), rs.getLong("chunk_count"));
			}
		};
		return StringUtil.isEmpty(caller) ? this.jdbcTemplate.query(sql, rowMapper) : this.jdbcTemplate.query(sql, rowMapper, caller);
	}

	private String vectorStoreTableName() {
		String table = this.configProperty.getProperty("spring.ai.vectorstore.pgvector.table-name");
		return StringUtil.isEmpty(table) ? "vector_store" : table;
	}

	/**
	 * 테이블명을 SQL 문자열에 직접 이어붙이기 전에, 영숫자와 밑줄만으로 이루어져 있는지 확인합니다.
	 * 이 검사를 통과하지 못하면 설정값이 잘못된 것으로 보고 예외를 던집니다.
	 *
	 * @param table 검증할 테이블명입니다.
	 */
	private String validateTableName(String table) {
		if (!table.matches("[A-Za-z0-9_]+")) {
			throw new IllegalStateException("spring.ai.vectorstore.pgvector.table-name 설정값이 올바르지 않습니다: " + table);
		}
		return table;
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

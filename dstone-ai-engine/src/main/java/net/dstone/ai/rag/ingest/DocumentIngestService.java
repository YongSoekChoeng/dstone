package net.dstone.ai.rag.ingest;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import net.dstone.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * 문서 적재 파이프라인(Phase 2): Tika로 원문 추출(PDF/DOCX/PPTX/HTML/TXT 등 대부분의 포맷 커버) →
 * TokenTextSplitter로 토큰 단위 청킹 → VectorStore(pgvector)에 저장.
 *
 * sourceId는 호출 쪽이 부여하는 논리적 문서 식별자(파일명/업무키 등)다 - 같은 sourceId로 재적재하면
 * upsert처럼 동작하도록, 새 청크를 넣기 전에 그 sourceId로 색인된 기존 청크를 먼저 지운다
 * (그대로 두면 문서를 갱신할 때마다 오래된 청크가 검색 결과에 계속 섞여 나온다).
 */
@Service
@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true")
public class DocumentIngestService extends BaseService {

	private static final String SOURCE_ID_METADATA_KEY = "sourceId";

	private final VectorStore vectorStore;
	private final TokenTextSplitter textSplitter;

	public DocumentIngestService(VectorStore vectorStore, ConfigProperty configProperty) {
		this.vectorStore = vectorStore;
		String chunkSize = configProperty.getProperty("dstone.ai.rag.ingest.chunk-size");
		this.textSplitter = TokenTextSplitter.builder()
			.withChunkSize(StringUtil.isEmpty(chunkSize) ? 800 : Integer.parseInt(chunkSize))
			.build();
	}

	/**
	 * @param resource 원문 리소스(웹 계층에서 MultipartFile.getResource()로 넘겨받는 걸 가정)
	 * @param sourceId 논리적 문서 식별자 - 재적재 시 upsert 기준
	 * @param metadata 검색 결과에 함께 실어보낼 임의의 메타데이터(널 허용)
	 * @return 적재된 청크 수
	 */
	public int ingest(Resource resource, String sourceId, Map<String, Object> metadata) {
		if (StringUtil.isEmpty(sourceId)) {
			throw new IllegalArgumentException("sourceId는 필수입니다(재적재 시 upsert 기준 키로 쓰임).");
		}

		List<Document> extracted = new TikaDocumentReader(resource).get();
		List<Document> chunks = this.textSplitter.apply(extracted);

		Map<String, Object> extraMetadata = metadata == null ? Map.of() : metadata;
		List<Document> tagged = chunks.stream().map(chunk -> {
			Document.Builder builder = chunk.mutate();
			extraMetadata.forEach(builder::metadata);
			return builder.metadata(SOURCE_ID_METADATA_KEY, sourceId).build();
		}).toList();

		if (tagged.isEmpty()) {
			// 텍스트 추출/청킹 결과가 비어 있으면 아무 것도 하지 않는다 - 여기서도 기존 청크를 지워버리면
			// 손상된 파일을 잘못 재적재했을 때 기존에 정상 적재돼 있던 데이터까지 날아간다.
			return 0;
		}

		deleteBySourceId(sourceId);
		this.vectorStore.add(tagged);
		return tagged.size();
	}

	public void deleteBySourceId(String sourceId) {
		this.vectorStore.delete(new FilterExpressionBuilder().eq(SOURCE_ID_METADATA_KEY, sourceId).build());
	}

}

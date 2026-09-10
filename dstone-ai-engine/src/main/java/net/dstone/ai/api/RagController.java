package net.dstone.ai.api;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import net.dstone.ai.api.dto.IngestResponse;
import net.dstone.ai.api.dto.RagSearchRequest;
import net.dstone.ai.api.dto.RetrievedChunk;
import net.dstone.ai.rag.ingest.DocumentIngestService;
import net.dstone.ai.rag.retrieval.RetrievalService;
import net.dstone.common.biz.BaseController;

/**
 * dstone.ai.rag.enabled=true일 때만 등록되는 RAG 전용 API다. net.dstone.ai.api.ChatController의
 * ragEnabled 플래그가 "채팅 도중 자동으로 검색 결과를 끼워 넣는" 경로라면, 여기는 문서 적재나 검색
 * 자체를 직접 확인해보고 싶을 때 쓰는 관리·디버깅용 엔드포인트다.
 */
@RestController
@RequestMapping("/api/ai/rag")
@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true")
public class RagController extends BaseController {

	private final DocumentIngestService documentIngestService;
	private final RetrievalService retrievalService;

	public RagController(DocumentIngestService documentIngestService, RetrievalService retrievalService) {
		this.documentIngestService = documentIngestService;
		this.retrievalService = retrievalService;
	}

	@PostMapping("/documents")
	public IngestResponse ingest(@RequestParam("file") MultipartFile file, @RequestParam("sourceId") String sourceId) {
		int chunkCount = this.documentIngestService.ingest(file.getResource(), sourceId, null);
		return new IngestResponse(sourceId, chunkCount);
	}

	@DeleteMapping("/documents/{sourceId}")
	public void delete(@PathVariable String sourceId) {
		this.documentIngestService.deleteBySourceId(sourceId);
	}

	@PostMapping("/search")
	public List<RetrievedChunk> search(@RequestBody RagSearchRequest request) {
		List<Document> documents = this.retrievalService.search(request.query(), request.topK(),
				request.similarityThreshold(), request.sourceId());
		return documents.stream().map(doc -> new RetrievedChunk(doc.getText(), doc.getMetadata(), doc.getScore())).toList();
	}

}

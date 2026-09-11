package net.dstone.ai.api.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
import net.dstone.ai.api.service.RagService;
import net.dstone.common.biz.BaseController;

/**
 * RAG 문서 적재/삭제/검색 API다. net.dstone.ai.api.controller.ChatController의 ragEnabled
 * 옵션이 "채팅 도중 자동으로 검색 결과를 끼워 넣는" 경로라면, 여기는 문서 적재나 검색 자체를 직접
 * 확인해보고 싶을 때 쓰는 관리·디버깅용 엔드포인트다.
 *
 * ChatController와 마찬가지로 dstone.ai.rag.enabled 여부와 무관하게 항상 등록된다 - 실제로 RAG를
 * 쓸 수 있는지는 net.dstone.ai.api.service.RagService가 호출 시점에 판단해서, 꺼져 있으면 명확한
 * 에러 메시지로 알려준다.
 */
@RestController
@RequestMapping("/api/ai/rag")
public class RagController extends BaseController {

	@Autowired
	RagService ragService;

	@PostMapping("/documents")
	public IngestResponse ingest(@RequestParam("file") MultipartFile file, @RequestParam("sourceId") String sourceId) {
		return this.ragService.ingest(file.getResource(), sourceId);
	}

	@DeleteMapping("/documents/{sourceId}")
	public void delete(@PathVariable String sourceId) {
		this.ragService.deleteBySourceId(sourceId);
	}

	@PostMapping("/search")
	public List<RetrievedChunk> search(@RequestBody RagSearchRequest request) {
		return this.ragService.search(request);
	}

}

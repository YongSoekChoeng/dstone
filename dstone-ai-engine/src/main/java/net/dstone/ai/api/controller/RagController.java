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

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.api.dto.IngestResponse;
import net.dstone.ai.api.dto.RagSearchRequest;
import net.dstone.ai.api.dto.RetrievedChunk;
import net.dstone.ai.common.security.CallerContext;
import net.dstone.ai.rag.RagService;
import net.dstone.common.biz.BaseController;

/**
 * RAG 문서 적재/삭제/검색 API다. Agent의 ragEnabled 옵션이 "채팅 도중 자동으로 검색 결과를 끼워 넣는" 경로라면, 여기는 문서 적재나 검색 자체를 직접 확인해보고 싶을 때 쓰는
 * 관리·디버깅용 엔드포인트다.
 *
 * dstone.ai.rag.enabled 여부와 무관하게 항상 등록된다 - 실제로 RAG를 쓸 수 있는지는 rag.RagService가 호출 시점에 판단해서, 꺼져 있으면 명확한 에러 메시지로 알려준다.
 */
@RestController
@RequestMapping("/api/ai/rag")
public class RagController extends BaseController {

	@Autowired
	RagService ragService;

	/**
	 * @param file           적재할 문서 파일
	 * @param sourceId       문서 식별자
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@PostMapping("/documents")
	public IngestResponse ingest(@RequestParam("file") MultipartFile file, @RequestParam("sourceId") String sourceId, HttpServletRequest servletRequest) {
		return this.ragService.ingest(file.getResource(), sourceId, CallerContext.get(servletRequest));
	}

	/**
	 * @param sourceId       삭제할 문서 식별자
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@DeleteMapping("/documents/{sourceId}")
	public void delete(@PathVariable String sourceId, HttpServletRequest servletRequest) {
		this.ragService.deleteBySourceId(sourceId, CallerContext.get(servletRequest));
	}

	/**
	 * @param request        검색어/topK 등 검색 조건
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@PostMapping("/search")
	public List<RetrievedChunk> search(@RequestBody RagSearchRequest request, HttpServletRequest servletRequest) {
		return this.ragService.search(request, CallerContext.get(servletRequest));
	}

}

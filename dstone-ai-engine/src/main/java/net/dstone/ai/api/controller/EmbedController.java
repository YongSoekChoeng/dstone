package net.dstone.ai.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.api.dto.IngestResponse;
import net.dstone.ai.api.service.EmbedService;
import net.dstone.ai.common.security.CallerContext;
import net.dstone.common.biz.BaseController;

/**
 * 문서를 벡터스토어에 넣고 빼는 관리 API다 - "RAG"(검색-증강, common.rag.RagRetrievalChain)와는 다른 책임이라
 * 컨트롤러/서비스도 분리했다. 검색은 여기 없다 - AGENT의 ragEnabled(Advisor 자동 증강)나 tools.rag.RagSearchTool(TOOL
 * 스텝)로만 한다.
 *
 * dstone.ai.rag.enabled 여부와 무관하게 항상 등록된다 - 실제로 쓸 수 있는지는 api.service.EmbedService가 호출 시점에
 * 판단해서, 꺼져 있으면 명확한 에러 메시지로 알려준다.
 */
@RestController
@RequestMapping("/api/ai/embed")
public class EmbedController extends BaseController {

	@Autowired
	EmbedService embedService;

	/**
	 * @param file           적재할 문서 파일
	 * @param sourceId       문서 식별자
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@PostMapping("/documents")
	public IngestResponse ingest(@RequestParam("file") MultipartFile file, @RequestParam("sourceId") String sourceId, HttpServletRequest servletRequest) {
		return this.embedService.ingest(file.getResource(), sourceId, CallerContext.get(servletRequest));
	}

	/**
	 * @param sourceId       삭제할 문서 식별자
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@DeleteMapping("/documents/{sourceId}")
	public void delete(@PathVariable String sourceId, HttpServletRequest servletRequest) {
		this.embedService.deleteBySourceId(sourceId, CallerContext.get(servletRequest));
	}

}

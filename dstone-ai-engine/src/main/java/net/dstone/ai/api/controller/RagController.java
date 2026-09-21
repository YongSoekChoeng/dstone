package net.dstone.ai.api.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.api.dto.RagSearchRequest;
import net.dstone.ai.api.dto.RetrievedChunk;
import net.dstone.ai.common.rag.RagRetrievalChain;
import net.dstone.ai.common.security.CallerContext;
import net.dstone.common.biz.BaseController;
import net.dstone.common.utils.StringUtil;

/**
 * "이미 적재된 문서를 검색해 보는" 관리/운영 API다 - common.rag.RagRetrievalChain.search()를 그대로 노출한다.
 * 적재/삭제/목록 조회는 api.controller.EmbedController의 책임이라 여기 없다(클래스 분리 원칙은
 * EmbedController 주석 참고).
 *
 * AGENT의 ragEnabled(Advisor 자동 증강)나 tools.rag.RagSearchTool(TOOL 스텝)은 검색 결과가 LLM 호출
 * 안에 섞여 들어가므로, "이 문서를 넣으면 실제로 검색이 되는가"만 따로 바로 확인하고 싶을 때는 이
 * 엔드포인트로 검색 결과만 직접 조회한다.
 */
@RestController
@RequestMapping("/api/ai/rag")
public class RagController extends BaseController {

	@Autowired
	private RagRetrievalChain ragRetrievalChain;

	/**
	 * @param request        검색 조건(질의어, topK 등)
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@PostMapping("/search")
	public List<RetrievedChunk> search(@RequestBody RagSearchRequest request, HttpServletRequest servletRequest) {
		if (request == null || StringUtil.isEmpty(request.query())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query는 필수입니다.");
		}
		return this.ragRetrievalChain.search(request, CallerContext.get(servletRequest));
	}

}

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
 * 이미 벡터스토어에 적재되어 있는 문서를 검색만 해 보는 운영/관리용 API입니다. 내부적으로는
 * common.rag.RagRetrievalChain의 search() 메서드를 그대로 호출합니다.
 *
 * 문서를 적재하거나 삭제하거나 목록을 보는 기능은 여기에 없습니다. 그 일은 api.controller.EmbedController가
 * 맡고 있습니다(왜 두 컨트롤러로 나눴는지는 EmbedController의 클래스 설명을 참고하세요).
 *
 * AGENT의 ragEnabled 옵션이나 tools.rag.RagSearchTool을 쓰면 검색 결과가 LLM 호출 과정 안에 섞여
 * 들어가기 때문에, 검색이 실제로 잘 되는지만 따로 빠르게 확인하기는 어렵습니다. "이 문서를 넣었을 때
 * 정말 검색이 되는가?"만 빠르게 확인하고 싶을 때 이 엔드포인트를 쓰면, 검색 결과만 바로 볼 수 있습니다.
 */
@RestController
@RequestMapping("/api/ai/rag")
public class RagController extends BaseController {

	@Autowired
	private RagRetrievalChain ragRetrievalChain;

	/**
	 * 질의어로 벡터스토어를 검색해서, 관련성이 높은 문서 조각들을 돌려줍니다.
	 *
	 * @param request        검색 조건입니다. 검색할 질의어(query), 최대 몇 개까지 가져올지(topK) 등을 담고 있습니다.
	 * @param servletRequest 이 요청을 보낸 caller(호출 주체)를 식별하기 위해 쓰는 HTTP 요청 객체입니다.
	 */
	@PostMapping("/search")
	public List<RetrievedChunk> search(@RequestBody RagSearchRequest request, HttpServletRequest servletRequest) {
		if (request == null || StringUtil.isEmpty(request.query())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query는 필수입니다.");
		}
		return this.ragRetrievalChain.search(request, CallerContext.get(servletRequest));
	}

}

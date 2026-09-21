package net.dstone.ai.api.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.api.dto.DocumentSourceSummary;
import net.dstone.ai.api.dto.IngestResponse;
import net.dstone.ai.api.service.EmbedService;
import net.dstone.ai.common.security.CallerContext;
import net.dstone.common.biz.BaseController;

/**
 * 문서를 벡터스토어(문서를 숫자 벡터로 바꿔 저장해 두는 검색용 저장소)에 넣고 빼는 관리용 API입니다.
 *
 * "문서를 저장하는 일"과 "저장된 문서를 검색해서 답변에 활용하는 일(RAG, 검색-증강 생성)"은 서로 다른
 * 책임이라고 보고, 일부러 클래스를 나눴습니다. 검색 쪽 로직은 common.rag.RagRetrievalChain이 담당하고,
 * 실제로 검색을 실행하는 통로는 두 가지입니다: AGENT의 ragEnabled 옵션(Advisor가 자동으로 검색 결과를
 * 끼워 넣어 줌)이나, tools.rag.RagSearchTool(TOOL 스텝에서 직접 검색만 호출). 이 컨트롤러에는 검색
 * 기능이 없으니, 검색이 필요하면 위 둘 중 하나를 쓰면 됩니다.
 *
 * 이 컨트롤러는 dstone.ai.rag.enabled 설정값이 꺼져 있어도 항상 등록됩니다. 실제로 RAG 기능을 쓸 수
 * 있는지는 요청이 들어올 때마다 api.service.EmbedService가 판단합니다. 만약 꺼져 있는 상태에서 호출하면,
 * 이유를 알기 쉬운 에러 메시지로 알려줍니다.
 */
@RestController
@RequestMapping("/api/ai/embed")
public class EmbedController extends BaseController {

	@Autowired
	EmbedService embedService;

	/**
	 * 문서 파일 하나를 받아서 벡터스토어에 적재(임베딩으로 변환해서 저장)합니다.
	 *
	 * @param file           적재할 문서 파일입니다.
	 * @param sourceId       이 문서를 구분할 식별자입니다. 나중에 삭제하거나 목록에서 찾을 때 이 값을 씁니다.
	 * @param servletRequest 이 요청을 보낸 caller(호출 주체)를 식별하기 위해 쓰는 HTTP 요청 객체입니다.
	 */
	@PostMapping("/documents")
	public IngestResponse ingest(@RequestParam("file") MultipartFile file, @RequestParam("sourceId") String sourceId, HttpServletRequest servletRequest) {
		return this.embedService.ingest(file.getResource(), sourceId, CallerContext.get(servletRequest));
	}

	/**
	 * sourceId로 지정한 문서를 벡터스토어에서 삭제합니다.
	 *
	 * @param sourceId       삭제할 문서의 식별자입니다.
	 * @param servletRequest 이 요청을 보낸 caller(호출 주체)를 식별하기 위해 쓰는 HTTP 요청 객체입니다.
	 */
	@DeleteMapping("/documents/{sourceId}")
	public void delete(@PathVariable String sourceId, HttpServletRequest servletRequest) {
		this.embedService.deleteBySourceId(sourceId, CallerContext.get(servletRequest));
	}

	/**
	 * 지금 vector_store에 어떤 sourceId의 문서들이 적재되어 있는지, 각각 몇 개의 청크(문서를 잘게 나눈 조각)로
	 * 저장되어 있는지 목록으로 보여줍니다. 실제로 적재된 내용을 검색해서 확인해 보고 싶다면, 이 API가 아니라
	 * api.controller.RagController의 POST /api/ai/rag/search를 사용하면 됩니다.
	 *
	 * @param servletRequest 이 요청을 보낸 caller(호출 주체)를 식별하기 위해 쓰는 HTTP 요청 객체입니다.
	 */
	@GetMapping("/documents")
	public List<DocumentSourceSummary> list(HttpServletRequest servletRequest) {
		return this.embedService.listSources(CallerContext.get(servletRequest));
	}

}

package net.dstone.boot.knowledge.controller;

import java.io.File;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.boot.knowledge.service.KnowledgeProxyService;
import net.dstone.common.utils.RequestUtil;

/**
 * <pre>
 * "코드 분석(Knowledge)" 화면이 dstone-knowledge의 API를 부를 때 거치는 창구입니다.
 * 화면(views/knowledge/*.jsp)의 스크립트가 여기로 요청을 보내면 KnowledgeProxyService가 dstone-knowledge에 넘겨줍니다.
 * </pre>
 */
@RestController
@RequestMapping("/knowledge/api/*")
public class KnowledgeProxyController extends net.dstone.boot.common.biz.BaseController {

	@Autowired
	private KnowledgeProxyService knowledgeProxyService;

	/**
	 * <pre>
	 * dstone-knowledge의 API 하나를 부릅니다.
	 * 본문: {method, path, query?, body?}
	 *   - method: GET / POST / DELETE
	 *   - path: /api/로 시작하는 경로. 예: /api/revisions/12/impact
	 *   - query: 주소 뒤에 붙일 파라미터. 예: {"table": "TB_ORDER"}
	 *   - body: POST로 보낼 JSON
	 * </pre>
	 */
	@SuppressWarnings("unchecked")
	@PostMapping("/call.do")
	public ResponseEntity<String> call(@RequestBody Map<String, Object> request) {
		Object method = request.get("method");
		Object path = request.get("path");
		Object query = request.get("query");
		return this.knowledgeProxyService.call(method == null ? null : String.valueOf(method), path == null ? null : String.valueOf(path)
				, query instanceof Map ? (Map<String, Object>) query : null, request.get("body"));
	}

	/**
	 * <pre>
	 * 일반 문서 올리기(multipart/form-data): file(필수), sourceId, title, projectId.
	 *
	 * 이 프로젝트는 Spring의 멀티파트 처리를 꺼 두고(conf/application.yml의 spring.servlet.multipart.enabled=false)
	 * RequestUtil로 직접 받는다. 그래서 MultipartFile 파라미터가 아니라 RequestUtil에서 파일을 꺼낸다.
	 * 받은 파일은 dstone-knowledge로 넘긴 뒤 지운다(원본은 이쪽에 보관하지 않는다).
	 * </pre>
	 */
	@PostMapping("/upload.do")
	public ResponseEntity<String> upload(HttpServletRequest request, HttpServletResponse response) throws Exception {
		RequestUtil requestUtil = new RequestUtil(request, response);
		if (requestUtil.getFileCount() == 0) {
			return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body("{\"status\":400,\"message\":\"올릴 파일이 없습니다.\"}");
		}
		File savedFile = new File(requestUtil.getSavedPath(), requestUtil.getSavedFileName(0));
		try {
			return this.knowledgeProxyService.upload(savedFile, requestUtil.getOriginalFileName(0), requestUtil.getParameter("sourceId")
					, requestUtil.getParameter("title"), requestUtil.getParameter("projectId"));
		} finally {
			if (savedFile.exists() && !savedFile.delete()) {
				warn("올린 뒤 임시 파일을 지우지 못했습니다: " + savedFile.getAbsolutePath());
			}
		}
	}

}

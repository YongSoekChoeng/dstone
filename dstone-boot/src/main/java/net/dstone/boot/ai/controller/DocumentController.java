package net.dstone.boot.ai.controller;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.boot.ai.service.DocumentService;
import net.dstone.boot.ai.vo.DocumentVo;
import net.dstone.boot.common.security.vo.CustomUserDetails;
import net.dstone.boot.common.web.SessionListener;
import net.dstone.common.utils.RequestUtil;
import net.dstone.common.utils.StringUtil;

/**
 * RAG 문서 업로드/목록/삭제 전용 컨트롤러. 전부 AJAX라 ChatController와 같은 이유로 @RestController로 둔다.
 * 파일업로드는 이 코드베이스의 기존 관례대로 RequestUtil/FileUpUtil(FILE_UPLOAD_ROOT에 저장)을 그대로 쓴다.
 */
@RestController
@RequestMapping("/ai/document/*")
public class DocumentController extends net.dstone.boot.common.biz.BaseController {

	@Autowired
	private DocumentService documentService;

	@PostMapping(value = "/uploadDocument.do")
	public DocumentVo uploadDocument(HttpServletRequest request, HttpServletResponse response) throws Exception {

		RequestUtil requestUtil = new RequestUtil(request, response);
		String sourceId = requestUtil.getParameter("SOURCE_ID");

		if (StringUtil.isEmpty(sourceId) || requestUtil.getFileCount() == 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SOURCE_ID와 업로드 파일은 필수입니다.");
		}

		String originalFileName = requestUtil.getOriginalFileName(0);
		File savedFile = new File(requestUtil.getSavedPath(), requestUtil.getSavedFileName(0));

		return this.documentService.uploadDocument(sourceId, originalFileName, savedFile, this.getUploaderId(request));
	}

	@PostMapping(value = "/listDocument.do")
	public List<DocumentVo> listDocument() {
		return this.documentService.listDocument();
	}

	@PostMapping(value = "/deleteDocument.do")
	public Map<String, Object> deleteDocument(HttpServletRequest request, HttpServletResponse response) throws Exception {
		RequestUtil requestUtil = new RequestUtil(request, response);
		String sourceId = requestUtil.getParameter("SOURCE_ID");
		if (StringUtil.isEmpty(sourceId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SOURCE_ID는 필수입니다.");
		}
		this.documentService.deleteDocument(sourceId);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("SOURCE_ID", sourceId);
		return result;
	}

	private String getUploaderId(HttpServletRequest request) {
		CustomUserDetails userDetails = (CustomUserDetails) request.getSession(true).getAttribute(SessionListener.USER_LOGIN_SESSION_KEY);
		return userDetails.getUsername();
	}

}

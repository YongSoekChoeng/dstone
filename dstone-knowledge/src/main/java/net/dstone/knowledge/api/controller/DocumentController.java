package net.dstone.knowledge.api.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.knowledge.api.service.UploadDocumentService;
import net.dstone.knowledge.common.exception.ApiException;
import net.dstone.knowledge.common.security.CallerContext;

/**
 * <pre>
 * 일반 문서(PDF, Word, 텍스트 등) API입니다. 올린 문서는 /api/search에서 sourceTypes에 DOCUMENT를 넣어 찾습니다.
 * 문서는 호출자(API 키의 caller)별로 나뉩니다. 인증을 꺼 두면 한곳에 모입니다.
 * </pre>
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

	@Autowired
	private UploadDocumentService uploadDocumentService;

	/**
	 * <pre>
	 * 문서 올리기(multipart/form-data).
	 *   - file: 올릴 파일(필수)
	 *   - sourceId: 문서 이름. 같은 이름으로 다시 올리면 바꿔 넣습니다. 없으면 파일 이름
	 *   - title: 검색 결과에 보일 제목. 없으면 파일 이름
	 *   - projectId: 이 문서를 붙일 프로젝트. 없어도 됩니다
	 * </pre>
	 */
	@PostMapping
	public Map<String, Object> upload(@RequestParam("file") MultipartFile file
			, @RequestParam(name = "sourceId", required = false) String sourceId
			, @RequestParam(name = "title", required = false) String title
			, @RequestParam(name = "projectId", required = false) String projectId
			, HttpServletRequest request) {
		if (file.isEmpty()) {
			throw ApiException.badRequest("빈 파일입니다.");
		}
		InputStream input = null;
		try {
			input = file.getInputStream();
			return uploadDocumentService.upload(input, file.getOriginalFilename(), sourceId, title, projectId, CallerContext.get(request));
		} catch (IOException e) {
			throw ApiException.badRequest("올린 파일을 읽지 못했습니다: " + e.getMessage());
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException ignore) {
					// 닫다가 난 오류는 결과에 영향이 없다.
				}
			}
		}
	}

	/** 올린 문서 목록. 문서마다 청크 수와 그중 임베딩이 끝난 수가 같이 나옵니다. */
	@GetMapping
	public Map<String, Object> getDocumentList(@RequestParam(name = "projectId", required = false) String projectId
			, @RequestParam(name = "page", defaultValue = "1") int page
			, @RequestParam(name = "size", defaultValue = "50") int size
			, HttpServletRequest request) {
		return uploadDocumentService.getDocumentList(CallerContext.get(request), projectId, page, size);
	}

	/** 문서 지우기. 이름에 /나 .이 들어갈 수 있어서 경로가 아니라 파라미터로 받습니다. */
	@DeleteMapping
	public Map<String, Object> delete(@RequestParam("sourceId") String sourceId, HttpServletRequest request) {
		return uploadDocumentService.delete(CallerContext.get(request), sourceId);
	}

}

package net.dstone.knowledge.api.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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
 * 일반 문서(PDF, Word, 텍스트 등) API입니다. 올린 문서는 /api/search에서 프로젝트 없이 찾습니다(collections / documentIds로 범위를 좁힌다).
 * 문서는 묶음(collection)으로 묶을 수 있습니다. 예: nexacro, websquare.
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
	 *   - collection: 묶음 이름(쉼표 없이 100자 이내). 없어도 됩니다
	 * </pre>
	 */
	@PostMapping
	public Map<String, Object> upload(@RequestParam("file") MultipartFile file
			, @RequestParam(name = "sourceId", required = false) String sourceId
			, @RequestParam(name = "title", required = false) String title
			, @RequestParam(name = "collection", required = false) String collection
			, HttpServletRequest request) {
		if (file.isEmpty()) {
			throw ApiException.badRequest("빈 파일입니다.");
		}
		InputStream input = null;
		try {
			input = file.getInputStream();
			return uploadDocumentService.upload(input, file.getOriginalFilename(), sourceId, title, collection, CallerContext.get(request));
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

	/** 올린 문서 목록. 문서마다 묶음, 청크 수, 그중 임베딩이 끝난 수가 같이 나옵니다. collection을 주면 그 묶음만. */
	@GetMapping
	public Map<String, Object> getDocumentList(@RequestParam(name = "collection", required = false) String collection
			, @RequestParam(name = "page", defaultValue = "1") int page
			, @RequestParam(name = "size", defaultValue = "50") int size
			, HttpServletRequest request) {
		return uploadDocumentService.getDocumentList(CallerContext.get(request), collection, page, size);
	}

	/** 묶음 목록과 묶음마다의 문서 수. 묶음이 없는 문서는 collection이 비어 있는 한 줄로 나옵니다. */
	@GetMapping("/collections")
	public List<Map<String, Object>> getCollections(HttpServletRequest request) {
		return uploadDocumentService.getCollections(CallerContext.get(request));
	}

	/**
	 * <pre>
	 * 문서 하나의 청크를 순번 범위로(from ~ to, 0부터, 한 번에 10개까지). 검색으로 찾은 조각의 앞뒤를 이어 읽을 때 씁니다.
	 * 청크마다 순번, 제목 경로, PDF 쪽, 본문이 나옵니다.
	 * </pre>
	 */
	@GetMapping("/chunks")
	public Map<String, Object> getChunks(@RequestParam("sourceId") String sourceId
			, @RequestParam("from") int from
			, @RequestParam("to") int to
			, HttpServletRequest request) {
		return uploadDocumentService.getChunks(CallerContext.get(request), sourceId, from, to);
	}

	/** 문서 지우기. 이름에 /나 .이 들어갈 수 있어서 경로가 아니라 파라미터로 받습니다. */
	@DeleteMapping
	public Map<String, Object> delete(@RequestParam("sourceId") String sourceId, HttpServletRequest request) {
		return uploadDocumentService.delete(CallerContext.get(request), sourceId);
	}

}

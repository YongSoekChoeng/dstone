package net.dstone.knowledge.api.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.knowledge.api.service.RevisionService;

/**
 * 리비전 API입니다. 분석 결과를 리비전 단위로 조회하고 지웁니다.
 */
@RestController
@RequestMapping("/api/revisions")
public class RevisionController {

	@Autowired
	private RevisionService revisionService;

	/** 리비전 조회: 상태, 단계별 진행 상태, Job 목록, 스캔한 파일 요약 */
	@GetMapping("/{revisionId}")
	public Map<String, Object> getRevision(@PathVariable("revisionId") long revisionId) {
		return revisionService.getRevision(revisionId);
	}

	/** 스캔한 파일 목록. language/fileType/encoding은 일치 조건, path는 포함 조건입니다. */
	@GetMapping("/{revisionId}/files")
	public Map<String, Object> getFileList(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "language", required = false) String language
			, @RequestParam(name = "fileType", required = false) String fileType
			, @RequestParam(name = "encoding", required = false) String encoding
			, @RequestParam(name = "path", required = false) String path
			, @RequestParam(name = "page", defaultValue = "1") int page
			, @RequestParam(name = "size", defaultValue = "50") int size) {
		return revisionService.getFileList(revisionId, language, fileType, encoding, path, page, size);
	}

	/** 리비전과 거기에 딸린 분석 결과를 모두 지웁니다. 되돌릴 수 없습니다. */
	@DeleteMapping("/{revisionId}")
	public Map<String, Object> deleteRevision(@PathVariable("revisionId") long revisionId) {
		revisionService.deleteRevision(revisionId);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", revisionId);
		result.put("deleted", Boolean.TRUE);
		return result;
	}

}

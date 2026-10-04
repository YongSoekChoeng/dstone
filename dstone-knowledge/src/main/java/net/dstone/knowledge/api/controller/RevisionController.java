package net.dstone.knowledge.api.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.knowledge.api.service.RevisionService;
import net.dstone.knowledge.api.service.SymbolService;
import net.dstone.knowledge.symbol.TypeSolverTrial;

/**
 * 리비전 API입니다. 분석 결과를 리비전 단위로 조회하고 지웁니다.
 */
@RestController
@RequestMapping("/api/revisions")
public class RevisionController {

	@Autowired
	private RevisionService revisionService;

	@Autowired
	private SymbolService symbolService;

	@Autowired
	private TypeSolverTrial typeSolverTrial;

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

	/** 타입 목록. kind는 일치 조건, name은 전체 이름에 포함 조건입니다. */
	@GetMapping("/{revisionId}/symbols")
	public Map<String, Object> getTypeList(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "kind", required = false) String kind
			, @RequestParam(name = "name", required = false) String name
			, @RequestParam(name = "page", defaultValue = "1") int page
			, @RequestParam(name = "size", defaultValue = "50") int size) {
		return symbolService.getTypeList(revisionId, kind, name, page, size);
	}

	/** 타입 하나: 메소드, 필드, 애노테이션, 나가는 참조 */
	@GetMapping("/{revisionId}/symbols/{symbolId}")
	public Map<String, Object> getType(@PathVariable("revisionId") long revisionId, @PathVariable("symbolId") String symbolId) {
		return symbolService.getType(revisionId, symbolId);
	}

	/**
	 * (시험용) DB 색인 기반 타입 해석기를 돌려 보고 통계를 돌려줍니다. 결과를 저장하지 않습니다.
	 * 끝날 때까지 응답이 오지 않으므로(파일 수백 개에 몇 분) maxFiles로 범위를 정해서 씁니다.
	 * M2에서 RESOLVE 단계가 들어오면 없어집니다.
	 */
	@PostMapping("/{revisionId}/trials/type-solver")
	public Map<String, Object> runTypeSolverTrial(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "maxFiles", defaultValue = "200") int maxFiles
			, @RequestParam(name = "cacheSize", defaultValue = "100") int cacheSize
			, @RequestParam(name = "withJars", defaultValue = "true") boolean withJars) {
		return typeSolverTrial.run(revisionId, Math.max(1, Math.min(maxFiles, 5000)), Math.max(1, cacheSize), withJars);
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

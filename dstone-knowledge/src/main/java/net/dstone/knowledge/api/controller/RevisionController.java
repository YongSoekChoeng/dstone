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

import net.dstone.knowledge.common.exception.ApiException;
import net.dstone.knowledge.api.service.CallGraphService;
import net.dstone.knowledge.api.service.DiffService;
import net.dstone.knowledge.api.service.ImpactService;
import net.dstone.knowledge.api.service.RevisionService;
import net.dstone.knowledge.api.service.SymbolService;

/**
 * <pre>
 * 리비전 API입니다. 분석 결과를 리비전 단위로 조회하고 지웁니다.
 * </pre>
 */
@RestController
@RequestMapping("/api/revisions")
public class RevisionController {

	@Autowired
	private RevisionService revisionService;

	@Autowired
	private SymbolService symbolService;

	@Autowired
	private CallGraphService callGraphService;

	@Autowired
	private ImpactService impactService;

	@Autowired
	private DiffService diffService;

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

	/** 타입 목록. kind와 layer는 일치 조건, name은 전체 이름에 포함 조건입니다. */
	@GetMapping("/{revisionId}/symbols")
	public Map<String, Object> getTypeList(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "kind", required = false) String kind
			, @RequestParam(name = "layer", required = false) String layer
			, @RequestParam(name = "name", required = false) String name
			, @RequestParam(name = "page", defaultValue = "1") int page
			, @RequestParam(name = "size", defaultValue = "50") int size) {
		return symbolService.getTypeList(revisionId, kind, layer, name, page, size);
	}

	/** 타입 하나: 메소드, 필드, 애노테이션, 나가는 참조 */
	@GetMapping("/{revisionId}/symbols/{symbolId}")
	public Map<String, Object> getType(@PathVariable("revisionId") long revisionId, @PathVariable("symbolId") String symbolId) {
		return symbolService.getType(revisionId, symbolId);
	}

	/** 진입점 목록. type은 일치 조건(HTTP / SERVLET / MAIN / SCHEDULED / LISTENER / THREAD / JSP), path는 주소에 포함 조건입니다. */
	@GetMapping("/{revisionId}/endpoints")
	public Map<String, Object> getEndpointList(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "type", required = false) String type
			, @RequestParam(name = "path", required = false) String path
			, @RequestParam(name = "page", defaultValue = "1") int page
			, @RequestParam(name = "size", defaultValue = "50") int size) {
		return revisionService.getEndpointList(revisionId, type, path, page, size);
	}

	/** 메소드 찾기. owner는 타입 전체 이름에 포함 조건, name은 메소드 이름 일치 조건입니다. */
	@GetMapping("/{revisionId}/methods")
	public Map<String, Object> getMethodList(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "owner", required = false) String owner
			, @RequestParam(name = "name", required = false) String name
			, @RequestParam(name = "page", defaultValue = "1") int page
			, @RequestParam(name = "size", defaultValue = "50") int size) {
		return callGraphService.getMethodList(revisionId, owner, name, page, size);
	}

	/** 이 메소드를 부르는 쪽. depth만큼 거슬러 올라갑니다(최대 5). */
	@GetMapping("/{revisionId}/methods/{methodId}/callers")
	public Map<String, Object> getCallers(@PathVariable("revisionId") long revisionId, @PathVariable("methodId") String methodId
			, @RequestParam(name = "depth", defaultValue = "1") int depth
			, @RequestParam(name = "includePossible", defaultValue = "true") boolean includePossible) {
		return callGraphService.getCallers(revisionId, methodId, depth, includePossible);
	}

	/** 이 메소드가 부르는 쪽. depth만큼 따라 내려갑니다(최대 5). */
	@GetMapping("/{revisionId}/methods/{methodId}/callees")
	public Map<String, Object> getCallees(@PathVariable("revisionId") long revisionId, @PathVariable("methodId") String methodId
			, @RequestParam(name = "depth", defaultValue = "1") int depth
			, @RequestParam(name = "includePossible", defaultValue = "true") boolean includePossible
			, @RequestParam(name = "includeExternal", defaultValue = "false") boolean includeExternal) {
		return callGraphService.getCallees(revisionId, methodId, depth, includePossible, includeExternal);
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

	/**
	 * <pre>
	 * 리비전 비교: 기준(앞) 리비전과 견주어 달라진 파일, 타입, 메소드, 진입점, SQL statement, 테이블 사용, 호출을 돌려줍니다.
	 *   - base: 기준 리비전. 없으면 증분 분석의 기준 리비전, 그것도 없으면 같은 프로젝트의 바로 앞 리비전
	 *   - kinds: 볼 종류를 쉼표로(FILE,TYPE,METHOD,ENDPOINT,STATEMENT,TABLE_USE,CALL). 없으면 전부
	 *   - limit: 종류마다 돌려줄 최대 건수(기본 200, 최대 2000). 0이면 건수만
	 * </pre>
	 */
	@GetMapping("/{revisionId}/diff")
	public Map<String, Object> getDiff(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "base", required = false) Long base
			, @RequestParam(name = "kinds", required = false) String kinds
			, @RequestParam(name = "limit", required = false) Integer limit) {
		java.util.List<String> kindList = new java.util.ArrayList<String>();
		if (kinds != null && kinds.trim().length() > 0) {
			kindList.addAll(java.util.Arrays.asList(kinds.split(",")));
		}
		return diffService.getDiff(revisionId, base, kindList, limit);
	}

	/**
	 * <pre>
	 * 영향도 분석: 이것을 고치면 닿는 메소드, 진입점(주소), 화면을 한 번에 돌려줍니다.
	 * 대상은 넷 가운데 하나만 줍니다.
	 *   - table: 테이블 이름. access=READ / WRITE로 읽는 쪽이나 쓰는 쪽만 볼 수 있습니다(기본 ALL).
	 *   - statement: SQL statement 이름(네임스페이스.id 또는 id)
	 *   - type: 타입 전체 이름. 그 타입의 모든 메소드에서 출발합니다.
	 *   - methodId: 메소드 ID
	 * depth: 몇 단계까지 거슬러 올라갈지(기본 5, 최대 10)
	 * </pre>
	 */
	@GetMapping("/{revisionId}/impact")
	public Map<String, Object> getImpact(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "table", required = false) String table
			, @RequestParam(name = "statement", required = false) String statement
			, @RequestParam(name = "type", required = false) String type
			, @RequestParam(name = "methodId", required = false) String methodId
			, @RequestParam(name = "access", required = false) String access
			, @RequestParam(name = "depth", required = false) Integer depth
			, @RequestParam(name = "includePossible", defaultValue = "true") boolean includePossible) {
		String[] kinds = { "TABLE", "STATEMENT", "TYPE", "METHOD" };
		String[] values = { table, statement, type, methodId };
		String targetKind = null;
		String target = null;
		for (int i = 0; i < values.length; i++) {
			if (values[i] != null && values[i].trim().length() > 0) {
				if (targetKind != null) {
					throw ApiException.badRequest("대상은 table / statement / type / methodId 가운데 하나만 줍니다.");
				}
				targetKind = kinds[i];
				target = values[i];
			}
		}
		if (targetKind == null) {
			throw ApiException.badRequest("대상이 없습니다. table / statement / type / methodId 가운데 하나를 주세요.");
		}
		return impactService.getImpact(revisionId, targetKind, target, access, depth, includePossible);
	}

}

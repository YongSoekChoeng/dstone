package net.dstone.knowledge.api.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.knowledge.api.service.SearchQuery;
import net.dstone.knowledge.api.service.SearchService;
import net.dstone.knowledge.common.exception.ApiException;
import net.dstone.knowledge.common.security.CallerContext;

/**
 * <pre>
 * 검색 API입니다. 분석 결과로 만든 문서 조각과 올린 일반 문서를 뜻과 이름으로 찾습니다.
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class SearchController {

	@Autowired
	private SearchService searchService;

	/**
	 * <pre>
	 * 검색. 뜻으로 찾기(벡터)와 이름으로 찾기를 같이 합니다.
	 * 본문: {query, projectId?, revisionId?, sourceTypes?, docTypes?, layer?, mode?, topK?}
	 *   - 코드에서 찾으려면 projectId나 revisionId 가운데 하나가 있어야 합니다. projectId만 주면 분석이 끝난 가장 최근 리비전에서 찾습니다.
	 *   - sourceTypes: ["CODE", "DOCUMENT"] 가운데 찾을 곳. 없으면 프로젝트를 줬을 때 CODE, 안 줬을 때 DOCUMENT(올린 일반 문서)
	 *   - docTypes: ["METHOD", "TYPE", "FILE", "MAPPER", "VIEW", "UPLOAD"] 가운데 찾을 것. 없으면 전부
	 *   - layer: CONTROLLER / SERVICE / REPOSITORY ... 없으면 전부
	 *   - mode: HYBRID(기본) / VECTOR(뜻만) / KEYWORD(이름만)
	 *   - topK: 기본 10, 최대 50
	 * </pre>
	 */
	@PostMapping("/search")
	public Map<String, Object> search(@RequestBody Map<String, Object> request, HttpServletRequest servletRequest) {
		SearchQuery query = new SearchQuery();
		// 올린 일반 문서는 이 호출자의 것만 찾는다.
		query.setTenant(CallerContext.get(servletRequest));
		query.setQuery(textOf(request.get("query")));
		query.setProjectId(textOf(request.get("projectId")));
		query.setRevisionId(longOf(request.get("revisionId"), "revisionId"));
		query.setSourceTypes(stringsOf(request.get("sourceTypes")));
		query.setDocTypes(stringsOf(request.get("docTypes")));
		query.setLayer(textOf(request.get("layer")));
		query.setMode(textOf(request.get("mode")));
		if (request.get("topK") != null) {
			query.setTopK((int) longOf(request.get("topK"), "topK").longValue());
		}
		return searchService.search(query);
	}

	/** 리비전의 RAG 상태: 문서/청크 수, 임베딩 진행 상황, 임베딩 작업의 상태 */
	@GetMapping("/revisions/{revisionId}/rag")
	public Map<String, Object> getStatus(@PathVariable("revisionId") long revisionId) {
		return searchService.getStatus(revisionId);
	}

	private String textOf(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private Long longOf(Object value, String name) {
		if (value == null) {
			return null;
		}
		try {
			return Long.valueOf(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			throw ApiException.badRequest(name + "는 숫자여야 합니다: " + value);
		}
	}

	private List<String> stringsOf(Object value) {
		List<String> strings = new ArrayList<String>();
		if (value instanceof List) {
			List<?> list = (List<?>) value;
			for (int i = 0; i < list.size(); i++) {
				strings.add(String.valueOf(list.get(i)));
			}
		} else if (value != null) {
			strings.add(String.valueOf(value));
		}
		return strings;
	}

}

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

import net.dstone.knowledge.api.service.SearchService;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 검색 API입니다. 분석 결과로 만든 문서 조각을 뜻으로 찾습니다.
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class SearchController {

	@Autowired
	private SearchService searchService;

	/**
	 * <pre>
	 * 벡터 검색.
	 * 본문: {query, projectId?, revisionId?, docTypes?, layer?, topK?}
	 *   - projectId나 revisionId 가운데 하나는 있어야 합니다. projectId만 주면 분석이 끝난 가장 최근 리비전에서 찾습니다.
	 *   - docTypes: ["METHOD", "TYPE", "FILE", "MAPPER", "VIEW"] 가운데 찾을 것. 없으면 전부
	 *   - layer: CONTROLLER / SERVICE / REPOSITORY ... 없으면 전부
	 *   - topK: 기본 10, 최대 50
	 * </pre>
	 */
	@PostMapping("/search")
	public Map<String, Object> search(@RequestBody Map<String, Object> request) {
		Object query = request.get("query");
		Object projectId = request.get("projectId");
		Object layer = request.get("layer");
		return searchService.search(query == null ? null : String.valueOf(query), projectId == null ? null : String.valueOf(projectId)
				, longOf(request.get("revisionId"), "revisionId"), stringsOf(request.get("docTypes")), layer == null ? null : String.valueOf(layer)
				, (int) (request.get("topK") == null ? 10 : longOf(request.get("topK"), "topK").longValue()));
	}

	/** 리비전의 RAG 상태: 문서/청크 수, 임베딩 진행 상황, 임베딩 작업의 상태 */
	@GetMapping("/revisions/{revisionId}/rag")
	public Map<String, Object> getStatus(@PathVariable("revisionId") long revisionId) {
		return searchService.getStatus(revisionId);
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

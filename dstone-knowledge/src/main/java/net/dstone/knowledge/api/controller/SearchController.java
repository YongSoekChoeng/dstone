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
 * 검색 API입니다. 분석 결과로 만든 문서 조각이나 올린 일반 문서를 뜻과 이름으로 찾습니다. 한 번에 한쪽에서만 찾습니다.
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
	 * 본문: {query, projectId?, revisionId?, collections?, documentIds?, docTypes?, layer?, mode?, topK?, minScore?}
	 *   - 어디서 찾을지는 projectId / revisionId로 정합니다.
	 *     둘 가운데 하나가 있으면 코드(분석 결과)에서 찾습니다. projectId만 주면 분석이 끝난 가장 최근 리비전입니다.
	 *     둘 다 없으면 이 호출자가 올린 일반 문서에서 찾습니다.
	 *   - 코드를 찾을 때만: docTypes(["METHOD", "TYPE", "FILE", "MAPPER", "VIEW"] 가운데, 없으면 전부), layer(CONTROLLER / SERVICE ...)
	 *   - 올린 문서를 찾을 때만: collections(묶음 이름 목록), documentIds(문서 이름 목록). 없으면 전부
	 *     결과마다 documentId, chunkNo, headingPath(제목 경로)가 있어서 GET /api/documents/chunks로 앞뒤를 이어 읽을 수 있습니다.
	 *   - mode: HYBRID(기본) / VECTOR(뜻만) / KEYWORD(이름만)
	 *   - topK: 기본 10, 최대 50
	 *   - minScore: 뜻으로 찾은 결과를 남길 최소 유사도(0~1). 없으면 설정값(dstone.knowledge.rag.search.min-score.*), 0이면 거르지 않음
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
		query.setCollections(stringsOf(request.get("collections")));
		query.setDocumentIds(stringsOf(request.get("documentIds")));
		query.setDocTypes(stringsOf(request.get("docTypes")));
		query.setLayer(textOf(request.get("layer")));
		query.setMode(textOf(request.get("mode")));
		if (request.get("topK") != null) {
			query.setTopK((int) longOf(request.get("topK"), "topK").longValue());
		}
		query.setMinScore(doubleOf(request.get("minScore"), "minScore"));
		return searchService.search(query);
	}

	/** 리비전의 RAG 상태: 문서/청크 수, 임베딩 진행 상황, 임베딩 작업의 상태 */
	@GetMapping("/revisions/{revisionId}/rag")
	public Map<String, Object> getStatus(@PathVariable("revisionId") long revisionId) {
		return searchService.getStatus(revisionId);
	}

	/** null 이 아니면 글로 */
	private String textOf(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	/** 숫자로 읽습니다. null 이면 null, 숫자가 아니면 400 */
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

	/** 실수로 읽습니다. 비었으면 null, 숫자가 아니면 400 */
	private Double doubleOf(Object value, String name) {
		if (value == null || String.valueOf(value).trim().length() == 0) {
			return null;
		}
		try {
			return Double.valueOf(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			throw ApiException.badRequest(name + "는 숫자여야 합니다: " + value);
		}
	}

	/** 목록이면 각 값을 글로, 값 하나면 그것 하나짜리 목록으로 */
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

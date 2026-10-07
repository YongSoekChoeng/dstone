package net.dstone.knowledge.api.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.knowledge.api.service.GraphService;

/**
 * <pre>
 * 노드 맵 API입니다. 호출 구조를 그림으로 그릴 수 있게 노드와 선으로 돌려줍니다.
 * </pre>
 */
@RestController
@RequestMapping("/api/revisions/{revisionId}/graph")
public class GraphController {

	@Autowired
	private GraphService graphService;

	/**
	 * <pre>
	 * 전체 맵: 클래스 / 화면(JSP) / SQL 매퍼 파일 / 테이블이 노드, 그 사이의 관계를 합친 것이 선입니다.
	 *   - relations: 그릴 관계를 쉼표로. 없으면 호출 흐름(CALLS, CALLS_POSSIBLE_IMPLEMENTATION, EXECUTES_SQL, READS_TABLE, WRITES_TABLE, RENDERS, REQUESTS).
	 *     더 고를 수 있는 것: INCLUDES(화면 끼워 넣기), EXTENDS, IMPLEMENTS, INJECTS
	 * </pre>
	 */
	@GetMapping
	public Map<String, Object> getGraph(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "relations", required = false) String relations) {
		return graphService.getGraph(revisionId, relations);
	}

	/**
	 * <pre>
	 * 관계 트리: 노드 하나로 들어오는 길(부르는 쪽)과 거기서 나가는 길(불리는 쪽)을 메소드 단위로 돌려줍니다.
	 *   - id: 전체 맵의 노드 id, 또는 메소드 ID / SQL statement ID(S123)
	 *   - up / down: 각 방향으로 몇 단계까지(기본 3, 최대 10, 0이면 그 방향은 보지 않음)
	 *   - includeModel: VO / DTO의 메소드로 가는 호출도 볼지(기본 false)
	 * </pre>
	 */
	@GetMapping("/neighborhood")
	public Map<String, Object> getNeighborhood(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "id") String nodeId
			, @RequestParam(name = "up", required = false) Integer up
			, @RequestParam(name = "down", required = false) Integer down
			, @RequestParam(name = "includePossible", defaultValue = "true") boolean includePossible
			, @RequestParam(name = "includeModel", defaultValue = "false") boolean includeModel) {
		return graphService.getNeighborhood(revisionId, nodeId, up, down, includePossible, includeModel);
	}

	/** 노드 하나의 상세: 위치, 계층, 처리하는 진입점, 메소드 / SQL 목록, 분석이 만들어 둔 설명 문서 */
	@GetMapping("/node")
	public Map<String, Object> getNode(@PathVariable("revisionId") long revisionId, @RequestParam(name = "id") String nodeId) {
		return graphService.getNode(revisionId, nodeId);
	}

}

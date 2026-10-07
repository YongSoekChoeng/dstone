package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.DeclarationDao;
import net.dstone.knowledge.api.dao.GraphDao;
import net.dstone.knowledge.api.dao.RelationDao;
import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 노드 맵: 호출 구조를 그림으로 볼 수 있게 노드와 선으로 돌려줍니다.
 *
 * 세 가지를 줍니다.
 *   1. 전체 맵        클래스 / 화면(JSP) / SQL 매퍼 파일 / 테이블이 노드이고, 그 사이의 관계를 합친 것이 선입니다.
 *                     메소드 하나하나를 그리면 수만 개라 볼 수 없어서 클래스 단위로 묶습니다.
 *   2. 관계 트리      노드 하나를 골라, 거기로 들어오는 길(부르는 쪽)과 거기서 나가는 길(불리는 쪽)을 메소드 단위로 따라갑니다.
 *   3. 노드 상세      노드 하나의 설명(위치, 계층, 진입점, 메소드, SQL, 분석이 만들어 둔 설명 문서).
 *
 * 저장된 관계만 읽습니다. 분석이 잇지 못한 호출(리플렉션, 문자열로 조립한 주소 등)은 여기에도 나오지 않습니다.
 * </pre>
 */
@Service
public class GraphService extends BaseObject {

	/** 전체 맵에서 한 번에 돌려주는 최대 노드 / 선 수. 넘으면 truncated = true */
	private static final int MAX_NODES = 20000;
	private static final int MAX_EDGES = 100000;

	/** 관계 트리에서 한쪽 방향으로 돌려주는 최대 선 수 */
	private static final int MAX_TREE_EDGES = 3000;

	private static final int DEFAULT_DEPTH = 3;
	private static final int MAX_DEPTH = 10;

	/** 상세에 싣는 목록(진입점, 테이블을 쓰는 SQL)의 최대 행 수 */
	private static final int MAX_DETAIL_ROWS = 500;

	/** 전체 맵에 그릴 수 있는 관계 */
	private static final List<String> MAP_RELATIONS = Arrays.asList("CALLS", "CALLS_POSSIBLE_IMPLEMENTATION", "EXECUTES_SQL", "READS_TABLE", "WRITES_TABLE"
			, "RENDERS", "REQUESTS", "INCLUDES", "EXTENDS", "IMPLEMENTS", "INJECTS");

	/** 따로 고르지 않았을 때 그리는 관계: 화면 → 메소드 → SQL → 테이블로 이어지는 호출 흐름 */
	private static final List<String> DEFAULT_MAP_RELATIONS = Arrays.asList("CALLS", "CALLS_POSSIBLE_IMPLEMENTATION", "EXECUTES_SQL", "READS_TABLE", "WRITES_TABLE"
			, "RENDERS", "REQUESTS");

	@Autowired
	private GraphDao graphDao;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private DeclarationDao declarationDao;

	@Autowired
	private RelationDao relationDao;

	@Autowired
	private ResourceDao resourceDao;

	/* ======================= 1. 전체 맵 ======================= */

	/**
	 * @param relations 그릴 관계를 쉼표로. 없으면 호출 흐름(CALLS, EXECUTES_SQL, READS_TABLE, WRITES_TABLE, RENDERS, REQUESTS ...)
	 */
	public Map<String, Object> getGraph(long revisionId, String relations) {
		checkRevision(revisionId);
		List<String> relationTypes = mapRelations(relations);

		List<Map<String, Object>> nodes = graphDao.selectGraphNodes(revisionId, MAX_NODES + 1);
		List<Map<String, Object>> edges = graphDao.selectGraphEdges(revisionId, relationTypes, MAX_EDGES + 1);
		boolean truncated = nodes.size() > MAX_NODES || edges.size() > MAX_EDGES;
		if (nodes.size() > MAX_NODES) {
			nodes = new ArrayList<Map<String, Object>>(nodes.subList(0, MAX_NODES));
		}
		if (edges.size() > MAX_EDGES) {
			edges = new ArrayList<Map<String, Object>>(edges.subList(0, MAX_EDGES));
		}

		// 노드가 잘렸으면 한쪽 끝이 없는 선이 생긴다. 그릴 수 없으므로 뺀다.
		Set<String> nodeIds = new HashSet<String>();
		Map<String, Integer> kindCounts = new LinkedHashMap<String, Integer>();
		for (int i = 0; i < nodes.size(); i++) {
			nodeIds.add(String.valueOf(nodes.get(i).get("id")));
			String kind = String.valueOf(nodes.get(i).get("kind"));
			Integer count = kindCounts.get(kind);
			kindCounts.put(kind, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
		}
		List<Map<String, Object>> drawable = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < edges.size(); i++) {
			Map<String, Object> edge = edges.get(i);
			if (nodeIds.contains(String.valueOf(edge.get("from"))) && nodeIds.contains(String.valueOf(edge.get("to")))) {
				drawable.add(edge);
			}
		}

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", revisionId);
		result.put("relations", relationTypes);
		result.put("nodeCount", nodes.size());
		result.put("edgeCount", drawable.size());
		result.put("kinds", kindCounts);
		result.put("truncated", Boolean.valueOf(truncated));
		result.put("nodes", nodes);
		result.put("edges", drawable);
		return result;
	}

	private List<String> mapRelations(String relations) {
		if (relations == null || relations.trim().length() == 0) {
			return DEFAULT_MAP_RELATIONS;
		}
		List<String> picked = new ArrayList<String>();
		String[] names = relations.split(",");
		for (int i = 0; i < names.length; i++) {
			String name = names[i].trim().toUpperCase();
			if (name.length() == 0) {
				continue;
			}
			if (!MAP_RELATIONS.contains(name)) {
				throw ApiException.badRequest("그릴 수 없는 관계입니다: " + name + ". 쓸 수 있는 값: " + MAP_RELATIONS);
			}
			if (!picked.contains(name)) {
				picked.add(name);
			}
		}
		return picked.isEmpty() ? DEFAULT_MAP_RELATIONS : picked;
	}

	/* ======================= 2. 관계 트리 ======================= */

	/**
	 * <pre>
	 * 노드 하나에 이어진 길을 메소드 단위로 돌려줍니다.
	 * 노드의 level이 그 노드의 자리입니다: 0 = 고른 노드, -1, -2 ... = 부르는 쪽, 1, 2 ... = 불리는 쪽.
	 * </pre>
	 *
	 * @param nodeId 전체 맵의 노드 ID, 또는 메소드 ID / SQL statement ID(S123)
	 * @param up 부르는 쪽으로 몇 단계(0이면 보지 않음. 기본 3, 최대 10)
	 * @param down 불리는 쪽으로 몇 단계(0이면 보지 않음. 기본 3, 최대 10)
	 * @param includePossible 인터페이스를 통해 부르는 호출("가능한 구현")도 따라갈지
	 * @param includeModel VO / DTO(MODEL 계층)의 메소드로 가는 호출도 따라갈지. 끄면 getter / setter 호출이 빠져서 그림이 읽을 만해집니다
	 */
	public Map<String, Object> getNeighborhood(long revisionId, String nodeId, Integer up, Integer down, boolean includePossible, boolean includeModel) {
		checkRevision(revisionId);
		GraphNodeId id = parseId(nodeId);
		Map<String, Object> start = describe(revisionId, id);
		int upDepth = depthOf(up);
		int downDepth = depthOf(down);
		String startKind = String.valueOf(start.get("kind"));

		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", revisionId);
		condition.put("startId", id.getId());
		// 클래스, 화면, 매퍼 파일은 묶음이다. 그 안에 든 메소드와 statement 전부에서 출발한다.
		condition.put("startByMember", Boolean.valueOf("TYPE".equals(startKind) || "JSP".equals(startKind) || "MAPPER".equals(startKind)));
		condition.put("tableName", id.getTableName());
		condition.put("hideLayers", includeModel ? new ArrayList<String>() : Arrays.asList("MODEL"));
		condition.put("limit", Integer.valueOf(MAX_TREE_EDGES + 1));

		List<String> calls = new ArrayList<String>();
		calls.add("CALLS");
		if (includePossible) {
			calls.add("CALLS_POSSIBLE_IMPLEMENTATION");
		}
		calls.addAll(Arrays.asList("EXECUTES_SQL", "READS_TABLE", "WRITES_TABLE"));

		boolean truncated = false;
		List<Map<String, Object>> upEdges = new ArrayList<Map<String, Object>>();
		if (upDepth > 0) {
			// 화면이 그 주소를 요청하는 것(REQUESTS)도 "부르는 쪽"이다. 이 화면을 여는 메소드(RENDERS)는 고른 노드가 화면일 때만 뜻이 있어서 첫 걸음에만 넣는다.
			List<String> step = new ArrayList<String>(calls);
			step.add("REQUESTS");
			List<String> anchor = new ArrayList<String>(step);
			anchor.add("RENDERS");
			condition.put("direction", "UP");
			condition.put("depth", Integer.valueOf(upDepth));
			condition.put("stepTypes", step);
			condition.put("anchorTypes", anchor);
			upEdges = graphDao.selectNeighborEdges(condition);
			if (upEdges.size() > MAX_TREE_EDGES) {
				truncated = true;
				upEdges = new ArrayList<Map<String, Object>>(upEdges.subList(0, MAX_TREE_EDGES));
			}
		}
		List<Map<String, Object>> downEdges = new ArrayList<Map<String, Object>>();
		if (downDepth > 0 && !"TABLE".equals(startKind)) {
			// 메소드가 여는 화면(RENDERS)까지 간다. 화면이 요청하는 주소(REQUESTS)는 고른 노드가 화면일 때만 따라간다.
			// 중간에 만난 화면에서 또 따라가면 호출이 아니라 화면 이동을 따라가게 되어 앱 전체가 딸려 나온다.
			List<String> step = new ArrayList<String>(calls);
			step.add("RENDERS");
			List<String> anchor = new ArrayList<String>(step);
			anchor.add("REQUESTS");
			condition.put("direction", "DOWN");
			condition.put("depth", Integer.valueOf(downDepth));
			condition.put("stepTypes", step);
			condition.put("anchorTypes", anchor);
			downEdges = graphDao.selectNeighborEdges(condition);
			if (downEdges.size() > MAX_TREE_EDGES) {
				truncated = true;
				downEdges = new ArrayList<Map<String, Object>>(downEdges.subList(0, MAX_TREE_EDGES));
			}
		}

		// 노드의 자리(level)를 정한다. 고른 노드에 바로 붙은 선(depth 1)의 안쪽 끝이 고른 노드(또는 그 안의 메소드)다.
		Map<String, Integer> levels = new LinkedHashMap<String, Integer>();
		for (int i = 0; i < upEdges.size(); i++) {
			if (intOf(upEdges.get(i).get("depth")) == 1) {
				levels.put(String.valueOf(upEdges.get(i).get("to")), Integer.valueOf(0));
			}
		}
		for (int i = 0; i < downEdges.size(); i++) {
			if (intOf(downEdges.get(i).get("depth")) == 1) {
				levels.put(String.valueOf(downEdges.get(i).get("from")), Integer.valueOf(0));
			}
		}
		Set<String> startIds = new HashSet<String>(levels.keySet());
		List<Map<String, Object>> edges = new ArrayList<Map<String, Object>>();
		Set<String> edgeKeys = new HashSet<String>();
		collectEdges(upEdges, "UP", levels, edges, edgeKeys);
		collectEdges(downEdges, "DOWN", levels, edges, edgeKeys);

		List<Map<String, Object>> nodes = loadNodes(revisionId, new ArrayList<String>(levels.keySet()));
		for (int i = 0; i < nodes.size(); i++) {
			Map<String, Object> node = nodes.get(i);
			String key = String.valueOf(node.get("id"));
			node.put("level", levels.get(key));
			node.put("start", Boolean.valueOf(startIds.contains(key)));
		}

		Map<String, Object> depth = new LinkedHashMap<String, Object>();
		depth.put("up", Integer.valueOf(upDepth));
		depth.put("down", Integer.valueOf(downDepth));

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("start", start);
		result.put("depth", depth);
		result.put("nodeCount", nodes.size());
		result.put("edgeCount", edges.size());
		result.put("truncated", Boolean.valueOf(truncated));
		result.put("nodes", nodes);
		result.put("edges", edges);
		return result;
	}

	/**
	 * <pre>
	 * 한쪽 방향의 선을 결과에 담고, 바깥쪽 끝 노드의 자리를 정합니다.
	 * 이미 자리가 정해진 노드는 그대로 둡니다(얕은 것부터 오므로 먼저 정해진 자리가 더 가깝습니다).
	 * </pre>
	 */
	private void collectEdges(List<Map<String, Object>> found, String direction, Map<String, Integer> levels, List<Map<String, Object>> edges
			, Set<String> edgeKeys) {
		boolean up = "UP".equals(direction);
		for (int i = 0; i < found.size(); i++) {
			Map<String, Object> edge = found.get(i);
			String from = String.valueOf(edge.get("from"));
			String to = String.valueOf(edge.get("to"));
			int depth = intOf(edge.get("depth"));
			String outer = up ? from : to;
			if (!levels.containsKey(outer)) {
				levels.put(outer, Integer.valueOf(up ? -depth : depth));
			}
			// 고른 노드 안에서 서로 부르는 선은 양쪽 방향에서 다 나온다. 한 번만 담는다.
			if (edgeKeys.add(from + ">" + to + ">" + edge.get("type"))) {
				edge.put("direction", direction);
				edges.add(edge);
			}
		}
	}

	private List<Map<String, Object>> loadNodes(long revisionId, List<String> ids) {
		List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
		List<String> stored = new ArrayList<String>();
		for (int i = 0; i < ids.size(); i++) {
			String id = ids.get(i);
			if (id.startsWith(GraphNodeId.TABLE_PREFIX)) {
				nodes.add(tableNode(id));
			} else {
				stored.add(id);
			}
		}
		// IN 조건이 너무 길어지지 않게 나눠서 조회한다.
		int chunk = 1000;
		for (int from = 0; from < stored.size(); from += chunk) {
			List<Map<String, Object>> rows = graphDao.selectNodesByIds(revisionId, stored.subList(from, Math.min(from + chunk, stored.size())));
			for (int i = 0; i < rows.size(); i++) {
				Map<String, Object> row = rows.get(i);
				// 관계의 끝이 되는 파일은 화면(JSP)뿐이다. 전체 맵과 같은 이름으로 맞춘다.
				if ("FILE".equals(row.get("kind"))) {
					row.put("kind", "JSP");
					row.put("layer", "VIEW");
				} else if ("SQL".equals(row.get("kind"))) {
					row.put("layer", "MAPPER");
				}
				nodes.add(row);
			}
		}
		return nodes;
	}

	private Map<String, Object> tableNode(String id) {
		String name = id.substring(GraphNodeId.TABLE_PREFIX.length());
		Map<String, Object> node = new LinkedHashMap<String, Object>();
		node.put("id", id);
		node.put("kind", "TABLE");
		node.put("subType", "TABLE");
		node.put("name", name);
		node.put("fullName", name);
		node.put("layer", "TABLE");
		return node;
	}

	private int depthOf(Integer depth) {
		return depth == null ? DEFAULT_DEPTH : Math.max(0, Math.min(depth.intValue(), MAX_DEPTH));
	}

	private int intOf(Object value) {
		return value instanceof Number ? ((Number) value).intValue() : 0;
	}

	/* ======================= 3. 노드 상세 ======================= */

	/**
	 * <pre>
	 * 노드 하나의 설명입니다. 종류에 따라 들어 있는 항목이 다릅니다.
	 *   TYPE   endpoints(처리하는 진입점), methods, document
	 *   METHOD endpoints, document
	 *   JSP    methods(화면 안의 Java 코드), document
	 *   MAPPER statements(그 파일의 SQL statement)
	 *   SQL    document
	 *   TABLE  usage(이 테이블을 건드리는 SQL과 그것을 실행하는 메소드)
	 * document는 분석이 만들어 둔 설명 문서(검색에 쓰는 것과 같은 것)입니다. 문서가 없으면 null입니다.
	 * </pre>
	 */
	public Map<String, Object> getNode(long revisionId, String nodeId) {
		checkRevision(revisionId);
		GraphNodeId id = parseId(nodeId);
		Map<String, Object> node = describe(revisionId, id);
		String kind = String.valueOf(node.get("kind"));

		if ("TYPE".equals(kind)) {
			node.put("endpoints", graphDao.selectEndpointsByNode(revisionId, id.getId(), MAX_DETAIL_ROWS));
			node.put("methods", declarationDao.selectMethodListByOwner(revisionId, id.getId()));
			node.put("document", graphDao.selectDocumentText(revisionId, "TYPE", "TYPE", id.getId()));
		} else if ("METHOD".equals(kind)) {
			node.put("endpoints", graphDao.selectEndpointsByNode(revisionId, id.getId(), MAX_DETAIL_ROWS));
			node.put("document", graphDao.selectDocumentText(revisionId, "METHOD", "METHOD", id.getId()));
		} else if ("JSP".equals(kind)) {
			node.put("methods", graphDao.selectMethodsByFile(revisionId, id.getNumber()));
			node.put("document", graphDao.selectDocumentText(revisionId, "FILE", "VIEW", id.getId()));
		} else if ("MAPPER".equals(kind)) {
			node.put("statements", graphDao.selectStatementsByFile(revisionId, id.getNumber()));
		} else if ("SQL".equals(kind)) {
			node.put("document", graphDao.selectDocumentText(revisionId, "SQL", "MAPPER", id.getId()));
		} else if ("TABLE".equals(kind)) {
			node.put("usage", resourceDao.selectTableUsage(revisionId, id.getTableName(), MAX_DETAIL_ROWS));
		}
		return node;
	}

	/**
	 * <pre>
	 * 노드가 무엇인지(종류, 이름, 위치)를 찾습니다. 없는 노드면 404입니다.
	 * </pre>
	 *
	 * @return {id, kind(TYPE / METHOD / JSP / MAPPER / SQL / TABLE), name, fullName, ...}
	 */
	private Map<String, Object> describe(long revisionId, GraphNodeId id) {
		Map<String, Object> node = new LinkedHashMap<String, Object>();
		node.put("id", id.getId());

		if (GraphNodeId.TABLE.equals(id.getKind())) {
			node.putAll(tableNode(id.getId()));
			return node;
		}
		if (GraphNodeId.SQL.equals(id.getKind())) {
			Map<String, Object> statement = graphDao.selectStatement(revisionId, id.getNumber());
			if (statement == null) {
				throw notFound(revisionId, id);
			}
			node.put("kind", "SQL");
			node.put("name", statement.get("statement"));
			node.put("fullName", statement.get("statement"));
			node.put("layer", "MAPPER");
			node.put("subType", statement.get("statementType"));
			node.put("path", statement.get("path"));
			node.put("lineStart", statement.get("lineStart"));
			node.put("lineEnd", statement.get("lineEnd"));
			node.put("tables", statement.get("tables"));
			return node;
		}
		if (GraphNodeId.FILE.equals(id.getKind()) || GraphNodeId.MAPPER.equals(id.getKind())) {
			Map<String, Object> file = graphDao.selectFile(revisionId, id.getNumber());
			if (file == null) {
				throw notFound(revisionId, id);
			}
			boolean mapper = GraphNodeId.MAPPER.equals(id.getKind());
			String path = String.valueOf(file.get("path"));
			node.put("kind", mapper ? "MAPPER" : "JSP");
			node.put("name", path.substring(path.lastIndexOf('/') + 1));
			node.put("fullName", path);
			node.put("layer", mapper ? "MAPPER" : "VIEW");
			node.put("subType", file.get("fileType"));
			node.put("path", path);
			node.put("lineCount", file.get("lineCount"));
			return node;
		}

		// 타입인지 메소드인지는 ID만 봐서는 모른다. 타입부터 찾는다.
		Map<String, Object> type = declarationDao.selectType(revisionId, id.getId());
		if (type != null) {
			node.put("kind", "TYPE");
			node.put("name", type.get("simpleName"));
			node.put("fullName", type.get("fqn"));
			node.put("layer", type.get("layer"));
			node.put("layerConfidence", type.get("layerConfidence"));
			node.put("subType", type.get("kind"));
			node.put("path", type.get("path"));
			node.put("lineStart", type.get("lineStart"));
			node.put("lineEnd", type.get("lineEnd"));
			return node;
		}
		Map<String, Object> method = relationDao.selectMethod(revisionId, id.getId());
		if (method != null) {
			node.put("kind", "METHOD");
			node.put("name", method.get("signature"));
			node.put("fullName", method.get("ownerFqn") + "#" + method.get("signature"));
			node.put("returnType", method.get("returnType"));
			node.put("path", method.get("path"));
			node.put("lineStart", method.get("lineStart"));
			return node;
		}
		throw notFound(revisionId, id);
	}

	private GraphNodeId parseId(String nodeId) {
		GraphNodeId id = GraphNodeId.parse(nodeId);
		if (id == null) {
			throw ApiException.badRequest("노드 ID의 모양이 맞지 않습니다: " + nodeId + ". 전체 맵이 돌려준 id를 그대로 주세요.");
		}
		return id;
	}

	private ApiException notFound(long revisionId, GraphNodeId id) {
		return ApiException.notFound("없는 노드입니다: revisionId=" + revisionId + ", id=" + id.getId());
	}

	private void checkRevision(long revisionId) {
		if (revisionDao.selectRevision(revisionId) == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
	}

}

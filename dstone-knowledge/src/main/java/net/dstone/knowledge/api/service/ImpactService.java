package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.RelationDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.common.exception.ApiException;
import net.dstone.knowledge.common.util.TableName;

/**
 * <pre>
 * 영향도 분석: "이것을 고치면 어디까지 닿는가"를 한 번에 돌려줍니다.
 *
 * 대상을 직접 건드리는 메소드(출발점)에서 부르는 쪽으로 끝까지 거슬러 올라가며
 * 닿는 메소드, 진입점(주소, 스케줄, main), 화면을 모읍니다.
 *
 *   테이블 TB_ORDER ← SQL ← OrderDAO.cancel ← OrderService.cancel ← OrderController.cancel ← POST /order/cancel.do ← order/detail.jsp
 *
 * 대상 종류
 *   TABLE     테이블. access 로 읽는 SQL / 쓰는 SQL 만 볼 수 있다
 *   COLUMN    테이블의 컬럼 하나. 그 컬럼을 쓰는 SQL 에서 출발한다. 컬럼을 읽지 못한 SQL(SELECT * 등)은 LOW 로 함께 넣는다
 *   STATEMENT SQL statement
 *   TYPE      타입. 그 타입의 메소드 전부에서 출발한다
 *   METHOD    메소드
 *   CHANGES   앞 리비전과 견줘 바뀐 메소드와 SQL(이번 변경의 영향도)
 *
 * 결과마다 신뢰도가 붙습니다. 출발점에서 거기까지 가는 길에서 가장 약한 관계의 신뢰도이고, 길이 여럿이면 가장 믿을 만한 길의 값입니다.
 *   HIGH   확실한 관계만 따라왔다
 *   MEDIUM 중간에 덜 확실한 관계가 있다(타입만 알고 메소드를 이름으로 고른 호출, 화면 안의 주소 글자, 정규식으로 찾은 테이블)
 *   LOW    이름만 보고 짐작한 관계가 끼어 있다. 참고로만 본다
 * 진입점과 화면마다 그 신뢰도가 나온 길(근거 길, chain)을 함께 돌려줍니다. 화면의 [왜?]가 이것을 보여 줍니다.
 * 답을 지어내지 않고 저장된 관계만 따라가므로, 분석이 잇지 못한 호출(리플렉션, 문자열로 조립한 주소 등)은 여기에도 나오지 않습니다.
 * 그런 곳일 수 있는 것은 gaps(놓쳤을 수 있는 곳)로 따로 돌려줍니다.
 * </pre>
 */
@Service
public class ImpactService extends BaseObject {

	/** depth 를 줄 때의 최대값. 주지 않으면 끝까지 올라간다 */
	private static final int MAX_DEPTH = 50;

	/** 종류별로 돌려주는 최대 행 수 */
	private static final int MAX_ROWS = 2000;

	/** "놓쳤을 수 있는 것"의 예시로 돌려주는 최대 행 수(건수는 전체를 센다) */
	private static final int GAP_SAMPLES = 20;

	@Autowired
	private RelationDao relationDao;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private DiffService diffService;

	@Autowired
	private TableDictionaryService tableDictionaryService;

	/**
	 * <pre>
	 * 대상을 바꾸면 영향을 받는 메소드 · 진입점 · 화면을 호출 관계를 거슬러 올라가며 찾습니다.
	 * </pre>
	 *
	 * @param targetKind TABLE / COLUMN / STATEMENT / TYPE / METHOD / CHANGES
	 * @param target 테이블 이름 / 테이블.컬럼 / statement 이름(네임스페이스.id 또는 id) / 타입 전체 이름 / 메소드 ID / 기준 리비전 ID(비우거나 auto 면 앞 리비전)
	 * @param access 대상이 테이블 · 컬럼일 때만: ALL(기본) / READ(읽는 SQL만) / WRITE(쓰는 SQL만)
	 * @param depth 몇 단계까지 거슬러 올라갈지. 비우면 끝까지(최대 50)
	 * @param includePossible 인터페이스를 통해 부르는 호출("가능한 구현")도 따라갈지. 영향을 빠뜨리지 않으려면 켠다
	 */
	public Map<String, Object> getImpact(long revisionId, String targetKind, String target, String access, Integer depth, boolean includePossible) {
		Map<String, Object> revision = revisionDao.selectRevision(revisionId);
		if (revision == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		// 1) 조건 만들기
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", revisionId);
		condition.put("targetKind", targetKind);
		Map<String, Object> base = null;
		String name = target == null ? "" : target.trim();
		if ("TABLE".equals(targetKind)) {
			// 테이블 이름은 스키마를 떼고 대문자로 맞춰 저장한다(SqlTableExtractor).
			name = TableName.bare(name);
		} else if ("COLUMN".equals(targetKind)) {
			int dot = name.lastIndexOf('.');
			if (dot <= 0 || dot == name.length() - 1) {
				throw ApiException.badRequest("컬럼은 테이블.컬럼 으로 적습니다: " + target);
			}
			String table = TableName.bare(name.substring(0, dot));
			String column = name.substring(dot + 1).trim().toUpperCase();
			condition.put("table", table);
			condition.put("column", column);
			name = table + "." + column;
		} else if ("CHANGES".equals(targetKind)) {
			Long baseId = null;
			if (name.length() > 0 && !"AUTO".equalsIgnoreCase(name)) {
				try {
					baseId = Long.valueOf(name);
				} catch (NumberFormatException e) {
					throw ApiException.badRequest("changedSince 는 기준 리비전 번호이거나 auto 여야 합니다: " + target);
				}
			}
			base = diffService.resolveBaseRevision(revision, revisionId, baseId);
			condition.put("baseRevisionId", base.get("revisionId"));
			name = String.valueOf(base.get("revisionId"));
		}
		condition.put("target", name);
		condition.put("tableRelationTypes", tableRelationTypes(access));
		int depthLimit = depth == null ? -1 : Math.max(0, Math.min(depth.intValue(), MAX_DEPTH));

		// 거슬러 올라갈 관계: 호출, 화면의 요청, (켜면) 가능한 구현, 익명 클래스를 감싸는 바깥 메소드
		List<String> relationTypes = new ArrayList<String>();
		relationTypes.add("CALLS");
		// 화면이 그 메소드의 주소를 요청하는 것도 "부르는 쪽"이다.
		relationTypes.add("REQUESTS");
		if (includePossible) {
			relationTypes.add("CALLS_POSSIBLE_IMPLEMENTATION");
		}
		// new Runnable() { run() { 대상(); } } 처럼 익명 클래스 안에서 부르면, 익명 클래스 메소드를 감싸는 바깥 메소드로 올라간다.
		relationTypes.add("ENCLOSES_ANONYMOUS");
		condition.put("relationTypes", relationTypes);
		condition.put("limit", Integer.valueOf(MAX_ROWS));

		// 2) 출발점과 그 위로 닿는 관계 → 거리 · 신뢰도 · 근거 길
		List<ImpactWalker.Start> starts = new ArrayList<ImpactWalker.Start>();
		List<Map<String, Object>> startRows = relationDao.selectImpactStart(condition);
		for (int i = 0; i < startRows.size(); i++) {
			Map<String, Object> row = startRows.get(i);
			starts.add(new ImpactWalker.Start(String.valueOf(row.get("nodeId")), String.valueOf(row.get("nodeKind"))
					, ((Number) row.get("conf")).intValue(), startReason((String) row.get("reason"), (String) row.get("name"))));
		}
		List<Map<String, Object>> changedScreens = "CHANGES".equals(targetKind)
				? relationDao.selectImpactChangedScreens(condition) : new ArrayList<Map<String, Object>>();
		if (starts.isEmpty() && changedScreens.isEmpty()) {
			throw ApiException.notFound(notFoundMessage(targetKind, name));
		}
		List<ImpactWalker.Edge> edges = new ArrayList<ImpactWalker.Edge>();
		List<Map<String, Object>> edgeRows = starts.isEmpty() ? new ArrayList<Map<String, Object>>() : relationDao.selectImpactEdges(condition);
		for (int i = 0; i < edgeRows.size(); i++) {
			Map<String, Object> row = edgeRows.get(i);
			edges.add(new ImpactWalker.Edge(String.valueOf(row.get("fromId")), String.valueOf(row.get("fromKind")), String.valueOf(row.get("toId"))
					, String.valueOf(row.get("relationType")), ImpactWalker.rank(row.get("confidence"))));
		}
		Map<String, ImpactWalker.Reached> reached = new ImpactWalker("METHOD".equals(targetKind) ? name : null).walk(starts, edges, depthLimit);

		// 3) 닿은 노드에 붙은 것들: 메소드, 진입점, 화면
		boolean any = !starts.isEmpty();
		Map<String, Object> nodes = new LinkedHashMap<String, Object>();
		List<Map<String, Object>> methods = methodsOf(any ? relationDao.selectImpactMethods(condition) : null, reached, nodes);
		List<Map<String, Object>> screens = screensOf(any ? relationDao.selectImpactScreens(condition) : null, reached, nodes);
		addChangedScreens(screens, changedScreens);
		List<Map<String, Object>> endpoints = endpointsOf(any ? relationDao.selectImpactEndpoints(condition) : null, reached);
		// 진입점마다 그 진입점을 요청하거나 그 진입점이 여는 화면을 붙인다(요약에서 "화면 → 거래"를 한 줄로)
		attachScreens(endpoints, any ? relationDao.selectImpactScreenLinks(condition) : new ArrayList<Map<String, Object>>(), screens);

		// 4) 건드리는 데이터와 "놓쳤을 수 있는 것"
		List<String> downRelationTypes = new ArrayList<String>();
		downRelationTypes.add("CALLS");
		if (includePossible) {
			downRelationTypes.add("CALLS_POSSIBLE_IMPLEMENTATION");
		}
		downRelationTypes.add("ENCLOSES_ANONYMOUS");
		condition.put("downRelationTypes", downRelationTypes);
		condition.put("sampleLimit", Integer.valueOf(GAP_SAMPLES));
		List<Map<String, Object>> touches = any ? groupTouches(relationDao.selectImpactTouches(condition)) : new ArrayList<Map<String, Object>>();
		Map<String, Object> gaps = new LinkedHashMap<String, Object>();
		putGap(gaps, "missedCallers", any ? relationDao.selectImpactMissedCallers(condition) : new ArrayList<Map<String, Object>>());
		if (any && ("METHOD".equals(targetKind) || "TYPE".equals(targetKind) || "CHANGES".equals(targetKind))) {
			putGap(gaps, "unresolvedInside", relationDao.selectImpactUnresolvedInside(condition));
		}

		// 5) 대상 설명과 요약
		Map<String, Object> targetInfo = new LinkedHashMap<String, Object>();
		targetInfo.put("kind", targetKind);
		targetInfo.put("name", name);
		if ("TABLE".equals(targetKind) || "COLUMN".equals(targetKind)) {
			targetInfo.put("access", access == null || access.trim().length() == 0 ? "ALL" : access.trim().toUpperCase());
		}
		putTargetDetail(targetInfo, condition, String.valueOf(revision.get("projectId")));
		Map<String, Object> changes = null;
		if (base != null) {
			changes = changeSummary(base, startRows, changedScreens, relationDao.selectImpactRemovedMethodCount(condition));
		}

		int startMethods = 0;
		int maxDepth = 0;
		for (ImpactWalker.Reached node : reached.values()) {
			if (node.parentId == null) {
				startMethods++;
			}
			maxDepth = Math.max(maxDepth, node.depth);
		}
		boolean truncated = methods.size() > MAX_ROWS || endpoints.size() > MAX_ROWS || screens.size() > MAX_ROWS;

		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		// 대상을 직접 건드리는 메소드 수
		summary.put("startMethods", Integer.valueOf(startMethods));
		summary.put("methods", Integer.valueOf(methods.size()));
		summary.put("endpoints", Integer.valueOf(endpoints.size()));
		summary.put("screens", Integer.valueOf(screens.size()));
		summary.put("endpointsByConfidence", countBy(endpoints, "confidence"));
		summary.put("screensByConfidence", countBy(screens, "confidence"));
		summary.put("methodsByLayer", countBy(methods, "layer"));
		summary.put("tablesWritten", Integer.valueOf(countWritten(touches)));

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", revisionId);
		result.put("target", targetInfo);
		// 실제로 올라간 가장 먼 거리와, 준 제한(없으면 null = 끝까지)
		result.put("depth", Integer.valueOf(maxDepth));
		result.put("depthLimit", depthLimit < 0 ? null : Integer.valueOf(depthLimit));
		result.put("includePossible", Boolean.valueOf(includePossible));
		result.put("summary", summary);
		result.put("truncated", Boolean.valueOf(truncated));
		if (changes != null) {
			result.put("changes", changes);
		}
		result.put("endpoints", cut(endpoints));
		result.put("screens", cut(screens));
		result.put("methods", cut(methods));
		result.put("touches", touches);
		result.put("gaps", gaps);
		// 근거 길(chain)에 나오는 노드의 이름: {노드 ID: {kind, name}}
		result.put("nodes", chainNodes(result, nodes));
		return result;
	}

	/* ---------------- 닿은 노드에 거리 · 신뢰도 · 근거 길 붙이기 ---------------- */

	/**
	 * <pre>
	 * 닿는 메소드 행에 거리와 신뢰도를 붙입니다. 익명 클래스의 메소드는 목록에서 빼되(바깥 메소드가 같은 거리로 나온다)
	 * 근거 길에서 쓸 이름은 nodes 에 남깁니다. 대상 자신은 익명 클래스 메소드여도 남깁니다.
	 * </pre>
	 */
	private List<Map<String, Object>> methodsOf(List<Map<String, Object>> rows, Map<String, ImpactWalker.Reached> reached, Map<String, Object> nodes) {
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		if (rows == null) {
			return result;
		}
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String id = String.valueOf(row.get("methodId"));
			nodes.put(id, nodeName("METHOD", (String) row.get("method")));
			ImpactWalker.Reached node = reached.get(id);
			if (node == null) {
				continue;
			}
			Object anonymous = row.remove("anonymous");
			if (Boolean.TRUE.equals(anonymous) && node.parentId != null) {
				continue;
			}
			Map<String, Object> method = new LinkedHashMap<String, Object>();
			method.put("depth", Integer.valueOf(node.depth));
			method.put("confidence", ImpactWalker.name(node.conf));
			method.putAll(row);
			if (node.parentId == null && node.via != null) {
				method.put("startVia", node.via);
			}
			result.add(method);
		}
		sortRows(result, "method");
		return result;
	}

	/**
	 * <pre>
	 * 화면 행을 화면 하나에 한 행으로 모읍니다. 이어진 길이 여럿이면 가장 믿을 만한 것(같으면 가까운 것)을 씁니다.
	 * RENDERS 는 "닿은 메소드 → 화면을 연다" 관계의 신뢰도까지 넣어 계산합니다.
	 * </pre>
	 */
	private List<Map<String, Object>> screensOf(List<Map<String, Object>> rows, Map<String, ImpactWalker.Reached> reached, Map<String, Object> nodes) {
		Map<String, Map<String, Object>> byPath = new LinkedHashMap<String, Map<String, Object>>();
		if (rows == null) {
			return new ArrayList<Map<String, Object>>();
		}
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String nodeId = String.valueOf(row.get("nodeId"));
			String path = String.valueOf(row.get("path"));
			String via = String.valueOf(row.get("via"));
			if ("REQUESTS".equals(via)) {
				nodes.put(nodeId, nodeName("FILE", path));
			}
			ImpactWalker.Reached node = reached.get(nodeId);
			if (node == null) {
				continue;
			}
			int conf = node.conf;
			if (row.get("edgeConf") != null) {
				conf = Math.min(conf, ImpactWalker.rank(row.get("edgeConf")));
			}
			Map<String, Object> screen = byPath.get(path);
			if (screen == null) {
				screen = new LinkedHashMap<String, Object>();
				screen.put("path", path);
				screen.put("screenName", screenName((String) row.get("screenName"), (String) row.get("dictScreenName")));
				screen.put("vias", new ArrayList<String>());
				byPath.put(path, screen);
			}
			@SuppressWarnings("unchecked")
			List<String> vias = (List<String>) screen.get("vias");
			if (!vias.contains(via)) {
				vias.add(via);
			}
			Integer oldConf = (Integer) screen.get("conf");
			Integer oldDepth = (Integer) screen.get("depth");
			if (oldConf == null || conf > oldConf.intValue() || (conf == oldConf.intValue() && node.depth < oldDepth.intValue())) {
				screen.put("conf", Integer.valueOf(conf));
				screen.put("depth", Integer.valueOf(node.depth));
				screen.put("nodeId", nodeId);
				screen.put("chainVia", via);
			}
		}
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		Iterator<Map<String, Object>> it = byPath.values().iterator();
		while (it.hasNext()) {
			Map<String, Object> screen = it.next();
			@SuppressWarnings("unchecked")
			List<String> vias = (List<String>) screen.remove("vias");
			int conf = ((Integer) screen.remove("conf")).intValue();
			String nodeId = (String) screen.remove("nodeId");
			Map<String, Object> row = new LinkedHashMap<String, Object>();
			row.put("path", screen.get("path"));
			row.put("screenName", screen.get("screenName"));
			row.put("depth", screen.get("depth"));
			row.put("confidence", ImpactWalker.name(conf));
			row.put("via", join(vias));
			// 근거 길이 어떤 관계로 화면에 닿았는지(REQUESTS / CALLS / RENDERS)
			row.put("chainVia", screen.get("chainVia"));
			row.put("chain", chainOf(reached, nodeId));
			result.add(row);
		}
		sortRows(result, "path");
		return result;
	}

	/** 변경분 대상: 화면 파일 자체가 바뀐 것은 거리 0, HIGH 로 넣습니다(이미 있으면 이유만 더합니다) */
	private void addChangedScreens(List<Map<String, Object>> screens, List<Map<String, Object>> changed) {
		for (int i = 0; i < changed.size(); i++) {
			String path = String.valueOf(changed.get(i).get("path"));
			Map<String, Object> found = null;
			for (int s = 0; s < screens.size() && found == null; s++) {
				if (path.equals(screens.get(s).get("path"))) {
					found = screens.get(s);
				}
			}
			if (found != null) {
				found.put("depth", Integer.valueOf(0));
				found.put("confidence", "HIGH");
				found.put("via", "CHANGED," + found.get("via"));
				continue;
			}
			Map<String, Object> row = new LinkedHashMap<String, Object>();
			row.put("path", path);
			row.put("screenName", null);
			row.put("depth", Integer.valueOf(0));
			row.put("confidence", "HIGH");
			row.put("via", "CHANGED");
			row.put("chainVia", "CHANGED");
			row.put("chain", new ArrayList<Map<String, Object>>());
			screens.add(0, row);
		}
	}

	/**
	 * <pre>
	 * 진입점 행을 진입점 하나에 한 행으로 모읍니다. 타입에만 붙은 진입점(matchedBy = TYPE)은 그 타입의 닿은 메소드 가운데
	 * 가장 가까운 것으로 이어 주고, 어느 메소드가 불리는지 모르는 짐작이라 LOW 입니다.
	 * </pre>
	 */
	private List<Map<String, Object>> endpointsOf(List<Map<String, Object>> rows, Map<String, ImpactWalker.Reached> reached) {
		Map<String, Map<String, Object>> byKey = new LinkedHashMap<String, Map<String, Object>>();
		if (rows == null) {
			return new ArrayList<Map<String, Object>>();
		}
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String reachedId = String.valueOf(row.get("reachedId"));
			ImpactWalker.Reached node = reached.get(reachedId);
			if (node == null) {
				continue;
			}
			boolean byType = "TYPE".equals(row.get("matchedBy"));
			int conf = byType ? ImpactWalker.LOW : node.conf;
			String key = row.get("endpointType") + "|" + row.get("httpMethod") + "|" + row.get("path") + "|" + row.get("handler");
			Map<String, Object> old = byKey.get(key);
			if (old != null) {
				int oldConf = ImpactWalker.rank(old.get("confidence"));
				int oldDepth = ((Integer) old.get("depth")).intValue();
				if (conf < oldConf || (conf == oldConf && node.depth >= oldDepth)) {
					continue;
				}
			}
			Map<String, Object> endpoint = new LinkedHashMap<String, Object>();
			endpoint.put("endpointType", row.get("endpointType"));
			endpoint.put("httpMethod", row.get("httpMethod"));
			endpoint.put("path", row.get("path"));
			endpoint.put("handler", row.get("handler"));
			endpoint.put("methodId", row.get("methodId"));
			endpoint.put("depth", Integer.valueOf(node.depth));
			endpoint.put("confidence", ImpactWalker.name(conf));
			endpoint.put("matchedBy", row.get("matchedBy"));
			endpoint.put("chain", chainOf(reached, reachedId));
			byKey.put(key, endpoint);
		}
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(byKey.values());
		sortRows(result, "path");
		return result;
	}

	/**
	 * <pre>
	 * 근거 길: [{id, relation, confidence}, ...]. 첫 칸이 진입점 · 화면 쪽 노드, 마지막 칸이 출발점입니다.
	 * relation 과 confidence 는 그 노드에서 다음 칸(대상 쪽)으로 가는 관계입니다. 마지막 칸에는 via(출발점인 이유)가 붙습니다.
	 * </pre>
	 */
	private List<Map<String, Object>> chainOf(Map<String, ImpactWalker.Reached> reached, String id) {
		List<String> ids = ImpactWalker.chain(reached, id);
		List<Map<String, Object>> chain = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < ids.size(); i++) {
			ImpactWalker.Reached node = reached.get(ids.get(i));
			Map<String, Object> step = new LinkedHashMap<String, Object>();
			step.put("id", node.id);
			if (node.parentId != null) {
				step.put("relation", node.relation);
				step.put("confidence", ImpactWalker.name(node.edgeConf));
			} else if (node.via != null) {
				step.put("via", node.via);
			}
			chain.add(step);
		}
		return chain;
	}

	/** 응답에 실린 근거 길의 노드만 골라 이름을 붙입니다 */
	private Map<String, Object> chainNodes(Map<String, Object> result, Map<String, Object> names) {
		Map<String, Object> used = new LinkedHashMap<String, Object>();
		String[] keys = { "endpoints", "screens" };
		for (int k = 0; k < keys.length; k++) {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get(keys[k]);
			for (int i = 0; i < rows.size(); i++) {
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> chain = (List<Map<String, Object>>) rows.get(i).get("chain");
				for (int c = 0; chain != null && c < chain.size(); c++) {
					String id = String.valueOf(chain.get(c).get("id"));
					if (!used.containsKey(id)) {
						Object name = names.get(id);
						used.put(id, name == null ? nodeName("METHOD", id) : name);
					}
				}
			}
		}
		return used;
	}

	private Map<String, Object> nodeName(String kind, String name) {
		Map<String, Object> node = new LinkedHashMap<String, Object>();
		node.put("kind", kind);
		node.put("name", name);
		return node;
	}

	/**
	 * <pre>
	 * 진입점 행마다 screens: [{path, via, screenName}] 를 붙입니다. 진입점을 처리하는 메소드를 요청하는 화면(REQUESTS)과
	 * 그 메소드가 여는 화면(RENDERS)입니다. 메소드 없이 타입에만 붙은 진입점은 빈 목록입니다.
	 * </pre>
	 */
	private void attachScreens(List<Map<String, Object>> endpoints, List<Map<String, Object>> links, List<Map<String, Object>> screens) {
		Map<String, Object> screenNames = new HashMap<String, Object>();
		for (int i = 0; i < screens.size(); i++) {
			screenNames.put(String.valueOf(screens.get(i).get("path")), screens.get(i).get("screenName"));
		}
		Map<String, List<Map<String, Object>>> byMethod = new HashMap<String, List<Map<String, Object>>>();
		for (int i = 0; i < links.size(); i++) {
			Map<String, Object> link = links.get(i);
			String methodId = String.valueOf(link.get("methodId"));
			List<Map<String, Object>> list = byMethod.get(methodId);
			if (list == null) {
				list = new ArrayList<Map<String, Object>>();
				byMethod.put(methodId, list);
			}
			Map<String, Object> screen = new LinkedHashMap<String, Object>();
			screen.put("path", link.get("path"));
			screen.put("via", link.get("via"));
			screen.put("screenName", screenNames.get(String.valueOf(link.get("path"))));
			list.add(screen);
		}
		for (int i = 0; i < endpoints.size(); i++) {
			Map<String, Object> endpoint = endpoints.get(i);
			List<Map<String, Object>> list = endpoint.get("methodId") == null ? null : byMethod.get(String.valueOf(endpoint.get("methodId")));
			endpoint.put("screens", list == null ? new ArrayList<Map<String, Object>>() : list);
		}
	}

	/* ---------------- 출발점 · 변경분 ---------------- */

	/** 출발점인 이유를 사람이 읽는 글로 바꿉니다. 대상 자신이면 null */
	private String startReason(String reason, String name) {
		String what = name == null ? "" : name;
		if ("READS_TABLE".equals(reason)) {
			return what + " 실행 · 테이블 읽기";
		}
		if ("WRITES_TABLE".equals(reason)) {
			return what + " 실행 · 테이블 쓰기";
		}
		if ("COLUMN_UNKNOWN".equals(reason)) {
			return what + " 실행 · 컬럼을 읽지 못한 SQL(SELECT * 등)";
		}
		if ("SQL".equals(reason)) {
			return what + " 실행";
		}
		if ("ADDED".equals(reason)) {
			return "새로 생긴 메소드";
		}
		if ("BODY".equals(reason)) {
			return "내용이 바뀐 메소드";
		}
		if ("SIGNATURE".equals(reason)) {
			return "반환 타입이나 제한자가 바뀐 메소드";
		}
		if ("FILE".equals(reason)) {
			return "파일이 바뀐 메소드(메소드 단위로 견줄 정보가 없어 파일 전체)";
		}
		if ("SQL_ADDED".equals(reason)) {
			return "새 SQL " + what + " 실행";
		}
		if ("SQL_CHANGED".equals(reason)) {
			return "바뀐 SQL " + what + " 실행";
		}
		return null;
	}

	/** 변경분 대상의 요약: 기준 리비전, 이유별 메소드 수, 바뀐 SQL 수, 바뀐 화면 수, 없어진 메소드 수 */
	private Map<String, Object> changeSummary(Map<String, Object> base, List<Map<String, Object>> startRows, List<Map<String, Object>> changedScreens, int removed) {
		Map<String, Object> byReason = new LinkedHashMap<String, Object>();
		String[] reasons = { "ADDED", "BODY", "SIGNATURE", "FILE" };
		for (int r = 0; r < reasons.length; r++) {
			byReason.put(reasons[r], new java.util.HashSet<String>());
		}
		java.util.Set<String> statements = new java.util.HashSet<String>();
		for (int i = 0; i < startRows.size(); i++) {
			Map<String, Object> row = startRows.get(i);
			Object reason = row.get("reason");
			if ("SQL_ADDED".equals(reason) || "SQL_CHANGED".equals(reason)) {
				statements.add(String.valueOf(row.get("name")));
			} else if (byReason.containsKey(reason)) {
				@SuppressWarnings("unchecked")
				java.util.Set<String> ids = (java.util.Set<String>) byReason.get(reason);
				ids.add(String.valueOf(row.get("nodeId")));
			}
		}
		Map<String, Object> methods = new LinkedHashMap<String, Object>();
		for (int r = 0; r < reasons.length; r++) {
			methods.put(reasons[r], Integer.valueOf(((java.util.Set<?>) byReason.get(reasons[r])).size()));
		}
		Map<String, Object> baseInfo = new LinkedHashMap<String, Object>();
		baseInfo.put("revisionId", base.get("revisionId"));
		baseInfo.put("revisionLabel", base.get("revisionLabel"));
		baseInfo.put("createdAt", base.get("createdAt"));
		Map<String, Object> changes = new LinkedHashMap<String, Object>();
		changes.put("base", baseInfo);
		changes.put("methods", methods);
		changes.put("statements", Integer.valueOf(statements.size()));
		changes.put("screens", Integer.valueOf(changedScreens.size()));
		changes.put("removedMethods", Integer.valueOf(removed));
		return changes;
	}

	/* ---------------- 건드리는 데이터 · 놓쳤을 수 있는 것 ---------------- */

	/**
	 * <pre>
	 * (table, access, statement, columnKnown) 행을 테이블 하나에 한 행으로 모읍니다:
	 * {table, reads: [SQL 이름], writes: [SQL 이름], unsure: [SQL 이름]}.
	 * unsure 는 컬럼 대상에서 그 컬럼을 쓰는지 읽지 못한 SQL 입니다. 쓰는 테이블이 먼저, 그다음 이름 순입니다.
	 * </pre>
	 */
	private List<Map<String, Object>> groupTouches(List<Map<String, Object>> rows) {
		Map<String, Map<String, Object>> byTable = new LinkedHashMap<String, Map<String, Object>>();
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String table = String.valueOf(row.get("table"));
			Map<String, Object> touch = byTable.get(table);
			if (touch == null) {
				touch = new LinkedHashMap<String, Object>();
				touch.put("table", table);
				touch.put("reads", new ArrayList<String>());
				touch.put("writes", new ArrayList<String>());
				touch.put("unsure", new ArrayList<String>());
				byTable.put(table, touch);
			}
			String key = Boolean.FALSE.equals(row.get("columnKnown")) ? "unsure" : "WRITES_TABLE".equals(row.get("access")) ? "writes" : "reads";
			@SuppressWarnings("unchecked")
			List<String> list = (List<String>) touch.get(key);
			String statement = String.valueOf(row.get("statement"));
			if (!list.contains(statement)) {
				list.add(statement);
			}
		}
		List<Map<String, Object>> writing = new ArrayList<Map<String, Object>>();
		List<Map<String, Object>> readingOnly = new ArrayList<Map<String, Object>>();
		Iterator<Map<String, Object>> it = byTable.values().iterator();
		while (it.hasNext()) {
			Map<String, Object> touch = it.next();
			if (((List<?>) touch.get("writes")).isEmpty()) {
				readingOnly.add(touch);
			} else {
				writing.add(touch);
			}
		}
		writing.addAll(readingOnly);
		return writing;
	}

	private int countWritten(List<Map<String, Object>> touches) {
		int count = 0;
		for (int i = 0; i < touches.size(); i++) {
			if (!((List<?>) touches.get(i).get("writes")).isEmpty()) {
				count++;
			}
		}
		return count;
	}

	/** gaps 에 {count, samples} 를 넣습니다. 행마다 붙어 온 total 이 전체 건수입니다 */
	private void putGap(Map<String, Object> gaps, String key, List<Map<String, Object>> rows) {
		int total = 0;
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			Object value = row.remove("total");
			if (value instanceof Number) {
				total = ((Number) value).intValue();
			}
		}
		Map<String, Object> gap = new LinkedHashMap<String, Object>();
		gap.put("count", Integer.valueOf(total));
		gap.put("samples", rows);
		gaps.put(key, gap);
	}

	/** access(ALL / READ / WRITE)에 맞는 테이블 관계 종류. 그 밖의 값이면 400 */
	private List<String> tableRelationTypes(String access) {
		List<String> types = new ArrayList<String>();
		String upper = access == null || access.trim().length() == 0 ? "ALL" : access.trim().toUpperCase();
		if (!"ALL".equals(upper) && !"READ".equals(upper) && !"WRITE".equals(upper)) {
			throw ApiException.badRequest("access는 ALL / READ / WRITE 가운데 하나여야 합니다: " + access);
		}
		if (!"WRITE".equals(upper)) {
			types.add("READS_TABLE");
		}
		if (!"READ".equals(upper)) {
			types.add("WRITES_TABLE");
		}
		return types;
	}

	/**
	 * <pre>
	 * 화면이 "무엇에 대한 영향도인지" 보여 줄 수 있게 대상의 설명을 붙입니다. 종류마다 붙는 항목이 다릅니다.
	 *   METHOD    ownerFqn, signature, layer, path, lineStart, title(메소드 위 주석의 기능명)
	 *   TYPE      simpleName, typeKind, layer, path, lineStart
	 *   STATEMENT statement(네임스페이스.id), statementType, path, lineStart, matches(이름이 같은 statement 수)
	 *   TABLE     statements(건드리는 statement 수), koreanName / koreanNameSource(테이블 한글명과 출처)
	 *   COLUMN    statements(그 컬럼을 쓰는 statement 수), unknownStatements(컬럼을 읽지 못한 statement 수), koreanName(테이블)
	 * graphId 는 노드 맵에서 이 대상의 관계 트리를 여는 ID 입니다(변경분에는 없음).
	 * 설명을 못 읽어도 영향도 결과는 그대로 돌려줍니다.
	 * </pre>
	 */
	private void putTargetDetail(Map<String, Object> targetInfo, Map<String, Object> condition, String projectId) {
		String kind = (String) condition.get("targetKind");
		if ("CHANGES".equals(kind)) {
			return;
		}
		Map<String, Object> detail = relationDao.selectImpactTarget(condition);
		if (detail != null) {
			targetInfo.putAll(detail);
		}
		if ("TABLE".equals(kind) || "COLUMN".equals(kind)) {
			String table = "TABLE".equals(kind) ? (String) condition.get("target") : (String) condition.get("table");
			targetInfo.put("graphId", GraphNodeId.TABLE_PREFIX + table);
			try {
				Map<String, Object> dictionary = tableDictionaryService.describe(((Long) condition.get("revisionId")).longValue(), projectId, table);
				targetInfo.put("koreanName", dictionary.get("koreanName"));
				targetInfo.put("koreanNameSource", dictionary.get("koreanNameSource"));
				if ("COLUMN".equals(kind)) {
					putColumnName(targetInfo, dictionary, (String) condition.get("column"));
				}
			} catch (RuntimeException e) {
				// 한글명은 덤이다. 못 찾아도(대상 DB에 못 붙는 등) 영향도는 보여 준다.
				warn("테이블 한글명을 읽지 못했습니다: " + table + " - " + e.getMessage());
			}
		}
	}

	/** 컬럼 대상이면 그 컬럼의 한글명과 타입을 붙입니다 */
	private void putColumnName(Map<String, Object> targetInfo, Map<String, Object> dictionary, String column) {
		Object columns = dictionary.get("columns");
		if (!(columns instanceof List)) {
			return;
		}
		List<?> list = (List<?>) columns;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof Map) {
				Map<?, ?> row = (Map<?, ?>) list.get(i);
				if (column.equalsIgnoreCase(String.valueOf(row.get("column")))) {
					targetInfo.put("columnKoreanName", row.get("koreanName"));
					targetInfo.put("dataType", row.get("dataType"));
					return;
				}
			}
		}
	}

	/** 영향 받는 것이 없을 때의 안내(대상 종류마다 목록을 볼 수 있는 곳을 함께 알려 줌) */
	private String notFoundMessage(String targetKind, String name) {
		if ("TABLE".equals(targetKind)) {
			return "그 테이블을 건드리는 SQL을 실행하는 메소드가 없습니다: " + name + " (테이블 목록: /tables)";
		}
		if ("COLUMN".equals(targetKind)) {
			return "그 컬럼을 쓰는 SQL을 실행하는 메소드가 없습니다: " + name + " (테이블의 컬럼: /graph/node?id=T:테이블)";
		}
		if ("STATEMENT".equals(targetKind)) {
			return "그 SQL statement를 실행하는 메소드가 없습니다: " + name + " (statement 목록: /mappers)";
		}
		if ("TYPE".equals(targetKind)) {
			return "없는 타입이거나 메소드가 없는 타입입니다: " + name + " (타입 전체 이름으로 적습니다. 목록: /types)";
		}
		if ("CHANGES".equals(targetKind)) {
			return "리비전 " + name + " 과 견줘 바뀐 메소드 · SQL · 화면이 없습니다.";
		}
		return "없는 메소드입니다: " + name + " (메소드 ID로 적습니다. 찾기: /methods)";
	}

	/* ---------------- 작은 도우미 ---------------- */

	/** 화면명: 소스의 제목 · 메뉴명에 한글이 있으면 그것, 없으면 화면 사전의 한글명, 그것도 없으면 소스의 이름 */
	private String screenName(String fromSource, String fromDictionary) {
		if (fromSource != null && hasHangul(fromSource)) {
			return fromSource;
		}
		return fromDictionary != null ? fromDictionary : fromSource;
	}

	private boolean hasHangul(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= '가' && c <= '힣') {
				return true;
			}
		}
		return false;
	}

	private String join(List<String> values) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(values.get(i));
		}
		return sb.toString();
	}

	/** 거리 순, 같으면 key 의 이름 순 */
	private void sortRows(List<Map<String, Object>> rows, final String key) {
		Collections.sort(rows, new Comparator<Map<String, Object>>() {
			@Override
			public int compare(Map<String, Object> a, Map<String, Object> b) {
				int da = ((Number) a.get("depth")).intValue();
				int db = ((Number) b.get("depth")).intValue();
				if (da != db) {
					return da - db;
				}
				return String.valueOf(a.get(key)).compareTo(String.valueOf(b.get(key)));
			}
		});
	}

	/** 앞쪽 MAX_ROWS 건만 */
	private List<Map<String, Object>> cut(List<Map<String, Object>> rows) {
		return rows.size() > MAX_ROWS ? new ArrayList<Map<String, Object>>(rows.subList(0, MAX_ROWS)) : rows;
	}

	/** 그 키의 값별 건수. 값이 없는 행은 "NONE"으로 센다 */
	private Map<String, Object> countBy(List<Map<String, Object>> rows, String key) {
		Map<String, Object> counts = new LinkedHashMap<String, Object>();
		for (int i = 0; i < rows.size(); i++) {
			Object value = rows.get(i).get(key);
			String name = value == null ? "NONE" : String.valueOf(value);
			Number count = (Number) counts.get(name);
			counts.put(name, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
		}
		return counts;
	}

}

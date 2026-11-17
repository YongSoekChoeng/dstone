package net.dstone.knowledge.api.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.ProgramDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.api.dao.UnusedDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 프로그램 목록과 테이블 CRUD. 차세대 현행 분석에서 손으로 만들던 "AS-IS 프로그램 목록"과 "CRUD 매트릭스"를 분석 결과로 만듭니다.
 *
 * 프로그램 = 진입점(주소, main, 스케줄 ...) 하나. 진입점마다 그 메소드에서 출발해 부르는 쪽으로 끝까지 따라가며
 * 닿는 메소드 → SQL → 테이블을 모으고, 그 진입점을 요청하는 화면을 붙입니다.
 *   화면(REQUESTS) → 진입점 메소드 → (CALLS ...) → 메소드 → (EXECUTES_SQL) → SQL → (READS / WRITES_TABLE) → 테이블(C / R / U / D)
 * 따라가는 관계는 영향도와 같습니다(CALLS, CALLS_POSSIBLE_IMPLEMENTATION, ENCLOSES_ANONYMOUS, EXECUTES_SQL).
 *
 * 신뢰도: 테이블마다 "가장 믿을 만한 길"의 신뢰도를 붙입니다. 길의 신뢰도는 그 길에서 가장 약한 관계의 것입니다.
 *   HIGH 관계만으로 닿는 것을 먼저 모으고, 다음에 MEDIUM 까지, 마지막에 LOW 까지 넓혀 가며 처음 닿은 단계가 그 신뢰도입니다.
 *
 * 저장하지 않고 조회할 때 계산합니다(관계를 한 번 읽어 메모리에서 따라간다. H-Energy 진입점 867개 기준 1초 안쪽).
 * 계산한 결과는 리비전마다 들고 있다가, 그 리비전에 분석 Job 이 새로 돌면 다시 계산합니다(UnusedService 와 같은 방식).
 * 답을 지어내지 않으므로, 분석이 잇지 못한 호출(리플렉션, 문자열로 조립한 주소 등) 너머의 테이블은 나오지 않습니다.
 * </pre>
 */
@Service
public class ProgramService extends BaseObject {

	/** 신뢰도 이름 ↔ 순위(클수록 믿을 만하다) */
	private static final String[] LEVEL_NAMES = { "", "LOW", "MEDIUM", "HIGH" };

	/** 캐시에 둘 리비전 수. 넘으면 비운다 */
	private static final int MAX_CACHE = 10;

	/** 한 프로그램 줄에 이름을 보여 줄 화면 수(나머지는 수만) */
	private static final int SHOWN_SCREENS = 3;

	@Autowired
	private ProgramDao programDao;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private UnusedDao unusedDao;

	@Autowired
	private StructureService structureService;

	private final Map<Long, Computed> cache = new ConcurrentHashMap<Long, Computed>();

	/** 한 리비전의 계산 결과 */
	private static class Computed {
		final String stamp;
		/** 프로그램(진입점)마다 한 행. 주소 순 */
		final List<Map<String, Object>> programs = new ArrayList<Map<String, Object>>();
		/** 테이블마다 한 행. 쓰는 프로그램이 많은 순 */
		final List<Map<String, Object>> tables = new ArrayList<Map<String, Object>>();

		Computed(String stamp) {
			this.stamp = stamp;
		}
	}

	/** 관계 하나(메소드 → 메소드 / SQL, SQL → 테이블) */
	private static class Edge {
		final String to;
		final int rank;
		final String crud;

		Edge(String to, int rank, String crud) {
			this.to = to;
			this.rank = rank;
			this.crud = crud;
		}
	}

	/* ======================= 조회 ======================= */

	/**
	 * <pre>
	 * 프로그램 목록. q 는 주소 · 처리 메소드에 포함 조건(대소문자 무시), area 는 업무 영역 일치, table 은 그 테이블을 쓰는 프로그램만.
	 * 응답의 areas 는 q · table 로 거른 뒤(area 로 거르기 전)의 영역별 건수입니다.
	 * </pre>
	 */
	public Map<String, Object> getPrograms(long revisionId, String q, String area, String table, int page, int size) {
		Computed computed = compute(revisionId);
		String needle = blank(q) ? null : q.trim().toLowerCase(Locale.ROOT);
		String tableName = blank(table) ? null : table.trim().toUpperCase(Locale.ROOT);
		List<Map<String, Object>> matched = new ArrayList<Map<String, Object>>();
		Map<String, Integer> areaCounts = new HashMap<String, Integer>();
		for (int i = 0; i < computed.programs.size(); i++) {
			Map<String, Object> row = computed.programs.get(i);
			if (needle != null && !contains(row, needle, "path", "handler", "type")) {
				continue;
			}
			if (tableName != null && !usesTable(row, tableName)) {
				continue;
			}
			String rowArea = String.valueOf(row.get("area"));
			Integer count = areaCounts.get(rowArea);
			areaCounts.put(rowArea, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
			if (!blank(area) && !area.equals(rowArea)) {
				continue;
			}
			matched.add(row);
		}
		Map<String, Object> body = page(revisionId, matched, page, size);
		body.put("areas", areaList(areaCounts));
		return body;
	}

	/**
	 * <pre>
	 * 테이블 CRUD. 테이블마다 그 테이블을 만들고(C) 읽고(R) 고치고(U) 지우는(D) 프로그램 수.
	 * q 는 테이블 이름 포함 조건, crud 는 그 글자를 가진 테이블만(예: "D" = 지우는 프로그램이 있는 테이블).
	 * 응답의 totals 는 전체 테이블 수와, 어느 프로그램에서도 닿지 않는(SQL 에만 나오는) 테이블 수입니다.
	 * </pre>
	 */
	public Map<String, Object> getTables(long revisionId, String q, String crud, int page, int size) {
		Computed computed = compute(revisionId);
		String needle = blank(q) ? null : q.trim().toUpperCase(Locale.ROOT);
		String letter = blank(crud) ? null : crud.trim().toUpperCase(Locale.ROOT);
		List<Map<String, Object>> matched = new ArrayList<Map<String, Object>>();
		int unreached = 0;
		for (int i = 0; i < computed.tables.size(); i++) {
			Map<String, Object> row = computed.tables.get(i);
			if (((Number) row.get("programs")).intValue() == 0) {
				unreached++;
			}
			if (needle != null && String.valueOf(row.get("table")).indexOf(needle) < 0) {
				continue;
			}
			if (letter != null && (letter.length() != 1 || "CRUD".indexOf(letter) < 0 || ((Number) row.get(letter)).intValue() == 0)) {
				continue;
			}
			matched.add(row);
		}
		Map<String, Object> body = page(revisionId, matched, page, size);
		Map<String, Object> totals = new LinkedHashMap<String, Object>();
		totals.put("tables", Integer.valueOf(computed.tables.size()));
		totals.put("unreached", Integer.valueOf(unreached));
		totals.put("programs", Integer.valueOf(computed.programs.size()));
		body.put("totals", totals);
		return body;
	}

	private Map<String, Object> page(long revisionId, List<Map<String, Object>> matched, int page, int size) {
		int safePage = Math.max(1, page);
		int safeSize = Math.max(1, Math.min(size, 1000));
		int from = Math.min((safePage - 1) * safeSize, matched.size());
		int to = Math.min(from + safeSize, matched.size());
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("revisionId", Long.valueOf(revisionId));
		body.put("total", Integer.valueOf(matched.size()));
		body.put("page", Integer.valueOf(safePage));
		body.put("size", Integer.valueOf(safeSize));
		body.put("rows", new ArrayList<Map<String, Object>>(matched.subList(from, to)));
		return body;
	}

	/* ======================= 계산 ======================= */

	/** 캐시가 맞으면 그대로, 아니면 다시 계산 */
	private Computed compute(long revisionId) {
		Map<String, Object> revision = revisionDao.selectRevision(revisionId);
		if (revision == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		String stamp = unusedDao.selectStamp(revisionId) + "/" + revision.get("status");
		Long key = Long.valueOf(revisionId);
		Computed cached = cache.get(key);
		if (cached != null && cached.stamp.equals(stamp)) {
			return cached;
		}
		long started = System.currentTimeMillis();
		Computed computed = new Computed(stamp);

		// 1) 관계를 한 번씩 읽어 메모리에 둔다
		Map<String, List<Edge>> out = new HashMap<String, List<Edge>>();
		List<Map<String, Object>> edges = programDao.selectEdges(revisionId);
		for (int i = 0; i < edges.size(); i++) {
			Map<String, Object> row = edges.get(i);
			add(out, String.valueOf(row.get("from")), new Edge(String.valueOf(row.get("to")), rank(row.get("confidence")), null));
		}
		Map<String, List<Edge>> sqlTables = new HashMap<String, List<Edge>>();
		Map<String, Set<String>> sqlsByTable = new HashMap<String, Set<String>>();
		List<Map<String, Object>> tableEdges = programDao.selectTableEdges(revisionId);
		for (int i = 0; i < tableEdges.size(); i++) {
			Map<String, Object> row = tableEdges.get(i);
			String sql = String.valueOf(row.get("sql"));
			String table = String.valueOf(row.get("table")).toUpperCase(Locale.ROOT);
			add(sqlTables, sql, new Edge(table, rank(row.get("confidence")), String.valueOf(row.get("crud"))));
			Set<String> sqls = sqlsByTable.get(table);
			if (sqls == null) {
				sqls = new HashSet<String>();
				sqlsByTable.put(table, sqls);
			}
			sqls.add(sql);
		}
		Map<String, List<String>> screensByMethod = new HashMap<String, List<String>>();
		List<Map<String, Object>> requests = programDao.selectScreenRequests(revisionId);
		for (int i = 0; i < requests.size(); i++) {
			String method = String.valueOf(requests.get(i).get("method"));
			List<String> screens = screensByMethod.get(method);
			if (screens == null) {
				screens = new ArrayList<String>();
				screensByMethod.put(method, screens);
			}
			screens.add(String.valueOf(requests.get(i).get("screen")));
		}

		// 2) 진입점마다 따라간다
		StructureService.Areas areas = structureService.areas(revisionId);
		Map<String, TableAgg> byTable = new TreeMap<String, TableAgg>();
		List<Map<String, Object>> endpoints = programDao.selectEndpoints(revisionId);
		for (int i = 0; i < endpoints.size(); i++) {
			Map<String, Object> endpoint = endpoints.get(i);
			Map<String, Object> program = program(endpoint, out, sqlTables, screensByMethod, areas, byTable);
			computed.programs.add(program);
		}

		// 처리 메소드가 없는 진입점(web.xml 의 *.do 같은 서블릿 매핑)은 따라갈 것이 없어 뒤로 보낸다. 나머지는 주소 순 그대로
		Collections.sort(computed.programs, new Comparator<Map<String, Object>>() {
			@Override
			public int compare(Map<String, Object> a, Map<String, Object> b) {
				boolean aNone = a.get("methodId") == null;
				boolean bNone = b.get("methodId") == null;
				return aNone == bNone ? 0 : aNone ? 1 : -1;
			}
		});

		// 3) 테이블 행: 프로그램이 닿는 테이블 + SQL 에만 나오고 어느 프로그램에서도 닿지 않는 테이블
		for (String table : sqlsByTable.keySet()) {
			if (!byTable.containsKey(table)) {
				byTable.put(table, new TableAgg(table));
			}
		}
		for (Map.Entry<String, TableAgg> entry : byTable.entrySet()) {
			Set<String> sqls = sqlsByTable.get(entry.getKey());
			computed.tables.add(entry.getValue().row(sqls == null ? 0 : sqls.size()));
		}
		Collections.sort(computed.tables, new Comparator<Map<String, Object>>() {
			@Override
			public int compare(Map<String, Object> a, Map<String, Object> b) {
				int diff = ((Number) b.get("programs")).intValue() - ((Number) a.get("programs")).intValue();
				return diff != 0 ? diff : String.valueOf(a.get("table")).compareTo(String.valueOf(b.get("table")));
			}
		});

		info("프로그램 목록 · CRUD 계산 revisionId=" + revisionId + ", 진입점 " + endpoints.size() + "개, 관계 " + edges.size() + "개, "
				+ (System.currentTimeMillis() - started) + "ms");
		if (cache.size() >= MAX_CACHE) {
			cache.clear();
		}
		cache.put(key, computed);
		return computed;
	}

	/**
	 * <pre>
	 * 진입점 하나를 따라가 프로그램 한 행을 만들고, 닿은 테이블을 테이블 집계에 더합니다.
	 *
	 * 신뢰도별로 세 번 넓힌다: HIGH 관계만으로 닿는 노드를 모으고(단계 3), 지금까지 닿은 노드에서 MEDIUM 이상 관계로 더 닿는 노드(단계 2),
	 * 마지막으로 LOW 까지(단계 1). 노드의 단계 = 그 노드까지 가는 가장 믿을 만한 길의 신뢰도.
	 * </pre>
	 */
	private Map<String, Object> program(Map<String, Object> endpoint, Map<String, List<Edge>> out, Map<String, List<Edge>> sqlTables,
			Map<String, List<String>> screensByMethod, StructureService.Areas areas, Map<String, TableAgg> byTable) {
		String start = endpoint.get("methodId") == null ? null : String.valueOf(endpoint.get("methodId"));
		Map<String, Integer> level = new HashMap<String, Integer>();
		if (start != null) {
			level.put(start, Integer.valueOf(3));
			for (int min = 3; min >= 1; min--) {
				Deque<String> queue = new ArrayDeque<String>(level.keySet());
				while (!queue.isEmpty()) {
					String node = queue.poll();
					List<Edge> next = out.get(node);
					if (next == null) {
						continue;
					}
					for (int i = 0; i < next.size(); i++) {
						Edge edge = next.get(i);
						if (edge.rank >= min && !level.containsKey(edge.to)) {
							level.put(edge.to, Integer.valueOf(min));
							queue.add(edge.to);
						}
					}
				}
			}
		}

		// 닿은 SQL → 테이블. 테이블의 신뢰도 = (SQL 까지의 단계, SQL → 테이블 관계) 가운데 약한 것, 여러 길이면 가장 센 것
		int methods = 0;
		int sqls = 0;
		Map<String, Integer> tableLevel = new TreeMap<String, Integer>();
		Map<String, Set<Character>> tableCrud = new HashMap<String, Set<Character>>();
		for (Map.Entry<String, Integer> entry : level.entrySet()) {
			// SQL statement 의 ID 는 'S' + 번호, 메소드 ID 는 16진수 해시라 첫 글자로 나뉜다
			if (!entry.getKey().startsWith("S")) {
				methods++;
				continue;
			}
			sqls++;
			List<Edge> tables = sqlTables.get(entry.getKey());
			if (tables == null) {
				continue;
			}
			for (int i = 0; i < tables.size(); i++) {
				Edge edge = tables.get(i);
				int lvl = Math.min(entry.getValue().intValue(), edge.rank);
				Integer before = tableLevel.get(edge.to);
				if (before == null || before.intValue() < lvl) {
					tableLevel.put(edge.to, Integer.valueOf(lvl));
				}
				Set<Character> letters = tableCrud.get(edge.to);
				if (letters == null) {
					letters = new TreeSet<Character>(new Comparator<Character>() {
						@Override
						public int compare(Character a, Character b) {
							return "CRUD".indexOf(a.charValue()) - "CRUD".indexOf(b.charValue());
						}
					});
					tableCrud.put(edge.to, letters);
				}
				for (int c = 0; c < edge.crud.length(); c++) {
					if ("CRUD".indexOf(edge.crud.charAt(c)) >= 0) {
						letters.add(Character.valueOf(edge.crud.charAt(c)));
					}
				}
			}
		}

		String area = areas.ofPackage((String) endpoint.get("packageName"));
		List<Map<String, Object>> tables = new ArrayList<Map<String, Object>>();
		int weakest = 3;
		int[] crudCounts = new int[4];
		for (Map.Entry<String, Integer> entry : tableLevel.entrySet()) {
			String crud = letters(tableCrud.get(entry.getKey()));
			Map<String, Object> table = new LinkedHashMap<String, Object>();
			table.put("table", entry.getKey());
			table.put("crud", crud);
			table.put("confidence", LEVEL_NAMES[entry.getValue().intValue()]);
			tables.add(table);
			weakest = Math.min(weakest, entry.getValue().intValue());
			for (int c = 0; c < 4; c++) {
				if (crud.indexOf("CRUD".charAt(c)) >= 0) {
					crudCounts[c]++;
				}
			}
			TableAgg agg = byTable.get(entry.getKey());
			if (agg == null) {
				agg = new TableAgg(entry.getKey());
				byTable.put(entry.getKey(), agg);
			}
			agg.add(crud, area, entry.getValue().intValue());
		}

		List<String> screens = screensByMethod.get(start);
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("endpointId", endpoint.get("endpointId"));
		row.put("endpointType", endpoint.get("endpointType"));
		row.put("httpMethod", endpoint.get("httpMethod"));
		row.put("path", endpoint.get("path"));
		row.put("handler", handler(endpoint));
		row.put("type", endpoint.get("type"));
		row.put("methodId", start);
		row.put("area", area);
		row.put("file", endpoint.get("file"));
		row.put("line", endpoint.get("line"));
		row.put("screenCount", Integer.valueOf(screens == null ? 0 : screens.size()));
		row.put("screens", screens == null ? new ArrayList<String>() : new ArrayList<String>(screens.subList(0, Math.min(SHOWN_SCREENS, screens.size()))));
		row.put("methodCount", Integer.valueOf(methods));
		row.put("sqlCount", Integer.valueOf(sqls));
		row.put("tableCount", Integer.valueOf(tables.size()));
		row.put("C", Integer.valueOf(crudCounts[0]));
		row.put("R", Integer.valueOf(crudCounts[1]));
		row.put("U", Integer.valueOf(crudCounts[2]));
		row.put("D", Integer.valueOf(crudCounts[3]));
		// 테이블까지 가는 길 가운데 가장 약한 것. LOW 면 이름만 보고 이은 호출을 거쳐야 닿는 테이블이 있다는 뜻
		row.put("confidence", tables.isEmpty() ? null : LEVEL_NAMES[weakest]);
		row.put("tables", tables);
		return row;
	}

	/** 테이블 하나의 집계: 글자마다 프로그램 수, 쓰는 업무 영역, 가장 센 신뢰도 */
	private static class TableAgg {
		final String table;
		int programs;
		final int[] crud = new int[4];
		final Map<String, Integer> areas = new HashMap<String, Integer>();
		int best;

		TableAgg(String table) {
			this.table = table;
		}

		void add(String letters, String area, int level) {
			programs++;
			for (int c = 0; c < 4; c++) {
				if (letters.indexOf("CRUD".charAt(c)) >= 0) {
					crud[c]++;
				}
			}
			Integer count = areas.get(area);
			areas.put(area, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
			best = Math.max(best, level);
		}

		Map<String, Object> row(int sqlCount) {
			Map<String, Object> row = new LinkedHashMap<String, Object>();
			row.put("table", table);
			row.put("programs", Integer.valueOf(programs));
			row.put("C", Integer.valueOf(crud[0]));
			row.put("R", Integer.valueOf(crud[1]));
			row.put("U", Integer.valueOf(crud[2]));
			row.put("D", Integer.valueOf(crud[3]));
			row.put("sqlCount", Integer.valueOf(sqlCount));
			// 많이 쓰는 영역부터
			List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(areas.entrySet());
			Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
				@Override
				public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
					int diff = b.getValue().intValue() - a.getValue().intValue();
					return diff != 0 ? diff : a.getKey().compareTo(b.getKey());
				}
			});
			List<String> names = new ArrayList<String>();
			for (int i = 0; i < list.size(); i++) {
				names.add(list.get(i).getKey());
			}
			row.put("areas", names);
			row.put("confidence", best == 0 ? null : LEVEL_NAMES[best]);
			return row;
		}
	}

	/* ======================= 작은 도우미 ======================= */

	/** 처리 메소드: 클래스 단순 이름#메소드 이름 */
	private String handler(Map<String, Object> endpoint) {
		String type = (String) endpoint.get("type");
		String simple = type == null ? "" : type.substring(type.lastIndexOf('.') + 1);
		Object method = endpoint.get("methodName");
		if (method == null) {
			return simple;
		}
		return simple + "#" + method;
	}

	private String letters(Set<Character> set) {
		StringBuilder sb = new StringBuilder();
		if (set != null) {
			for (Character c : set) {
				sb.append(c.charValue());
			}
		}
		return sb.toString();
	}

	private int rank(Object confidence) {
		String name = confidence == null ? "" : String.valueOf(confidence);
		return "HIGH".equals(name) ? 3 : "MEDIUM".equals(name) ? 2 : 1;
	}

	private void add(Map<String, List<Edge>> map, String key, Edge edge) {
		List<Edge> list = map.get(key);
		if (list == null) {
			list = new ArrayList<Edge>();
			map.put(key, list);
		}
		list.add(edge);
	}

	private boolean usesTable(Map<String, Object> row, String table) {
		List<?> tables = (List<?>) row.get("tables");
		for (int i = 0; i < tables.size(); i++) {
			if (table.equals(((Map<?, ?>) tables.get(i)).get("table"))) {
				return true;
			}
		}
		return false;
	}

	private boolean contains(Map<String, Object> row, String needle, String... keys) {
		for (int i = 0; i < keys.length; i++) {
			Object value = row.get(keys[i]);
			if (value != null && String.valueOf(value).toLowerCase(Locale.ROOT).indexOf(needle) >= 0) {
				return true;
			}
		}
		return false;
	}

	/** 영역별 건수를 많은 것부터. ( 로 시작하는 것은 맨 뒤 */
	private List<Map<String, Object>> areaList(Map<String, Integer> counts) {
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("area", entry.getKey());
			item.put("count", entry.getValue());
			list.add(item);
		}
		Collections.sort(list, new Comparator<Map<String, Object>>() {
			@Override
			public int compare(Map<String, Object> a, Map<String, Object> b) {
				boolean aLast = String.valueOf(a.get("area")).startsWith("(");
				boolean bLast = String.valueOf(b.get("area")).startsWith("(");
				if (aLast != bLast) {
					return aLast ? 1 : -1;
				}
				int diff = ((Integer) b.get("count")).intValue() - ((Integer) a.get("count")).intValue();
				return diff != 0 ? diff : String.valueOf(a.get("area")).compareTo(String.valueOf(b.get("area")));
			}
		});
		return list;
	}

	private boolean blank(String value) {
		return value == null || value.trim().length() == 0;
	}

}

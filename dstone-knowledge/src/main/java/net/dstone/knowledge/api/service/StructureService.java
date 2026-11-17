package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.RelationDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.api.dao.StructureDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 리비전의 "현행 구조"를 돌려줍니다. 프로젝트 화면의 첫 탭이 이것 하나로 그려집니다.
 *   layers  계층별 개수(화면 → 컨트롤러 → 서비스 → DAO → SQL → 테이블). 노드 맵과 같은 기준
 *   areas   업무 영역별 규모
 *   trust   이 결과를 얼마나 믿을 수 있는지(호출 해석률, 짐작으로 이은 비율, 파싱 성공률, 가장 많이 못 푼 이유)
 *
 * 업무 영역은 이름을 짐작하지 않고 경로에서 기계적으로 뗍니다:
 *   패키지(com.frameq.hsl.sa.web) · 화면 폴더(src/main/hen/hsl/sa) · SQL 매퍼 폴더 각각에서
 *   대부분(80% 이상)이 공통으로 가진 앞부분(com.frameq / src/main/hen ...)을 떼고, 그다음 첫 마디(hsl)를 영역 이름으로 씁니다.
 *   이름이 같은 영역은 한 줄로 합칩니다. 그래서 패키지와 폴더를 같은 이름으로 나눈 프로젝트에서는 한 줄에 다 모입니다.
 *   그 첫 마디가 dao · mapper · service · web 같은 기술 이름이면 건너뛰고 다음 마디를 씁니다(hi/dao/jt/ji → jt).
 *   공통 앞부분 밖에 있는 것은 "(그 밖)", 앞부분 바로 아래에 있는 것은 "(최상위)" 입니다.
 * </pre>
 */
@Service
public class StructureService extends BaseObject {

	/** 영역 이름 대신 쓰는 말 */
	public static final String AREA_OTHER = "(그 밖)";
	public static final String AREA_TOP = "(최상위)";

	/** 영역 이름으로 쓰지 않는 기술 이름 마디(소문자로 비교). 이 마디 다음 것을 영역 이름으로 쓴다 */
	private static final Set<String> TECH_SEGMENTS = new HashSet<String>(Arrays.asList(
			"dao", "daos", "mapper", "mappers", "sql", "sqlmap", "sqlmaps", "mybatis", "ibatis", "query", "queries",
			"service", "services", "impl", "web", "controller", "controllers", "biz", "repository", "persistence", "model", "vo", "dto"));

	/** 공통 앞부분으로 볼 비율 */
	private static final double DOMINANT = 0.8;

	@Autowired
	private StructureDao structureDao;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private RelationDao relationDao;

	@Autowired
	private SemanticDao semanticDao;

	public Map<String, Object> getStructure(long revisionId) {
		if (revisionDao.selectRevision(revisionId) == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", Long.valueOf(revisionId));
		result.put("layers", structureDao.selectLayerCounts(revisionId));
		result.put("screenTypes", structureDao.selectScreenTypes(revisionId));
		result.put("endpointTypes", semanticDao.selectEndpointSummary(revisionId));
		result.put("trust", trust(revisionId));
		areas(revisionId, result);
		return result;
	}

	/* ======================= 업무 영역 ======================= */

	/** 영역 한 줄에 모으는 숫자 이름. 화면에 보이는 칸의 순서이기도 하다 */
	private static final List<String> AREA_KEYS = Arrays.asList("screens", "endpoints", "controllers", "services", "daos", "sqlStatements", "tables");

	private void areas(long revisionId, Map<String, Object> result) {
		List<Map<String, Object>> classes = structureDao.selectClassesByPackage(revisionId);
		List<Map<String, Object>> screens = structureDao.selectScreensByFolder(revisionId);
		List<Map<String, Object>> endpoints = structureDao.selectEndpointsByPackage(revisionId);
		List<Map<String, Object>> statements = structureDao.selectStatementsByFolder(revisionId);
		List<Map<String, Object>> tables = structureDao.selectTablesByFolder(revisionId);

		Areas resolver = new Areas(classes, endpoints, screens, statements);
		List<String> javaPrefix = resolver.javaPrefix;
		List<String> screenPrefix = resolver.screenPrefix;
		List<String> mapperPrefix = resolver.mapperPrefix;

		Map<String, Map<String, Object>> areas = new HashMap<String, Map<String, Object>>();
		for (int i = 0; i < classes.size(); i++) {
			Map<String, Object> row = classes.get(i);
			String layer = String.valueOf(row.get("layer"));
			String key = "SERVICE".equals(layer) ? "services" : "REPOSITORY".equals(layer) ? "daos" : "controllers";
			add(areas, areaOf((String) row.get("key"), "\\.", javaPrefix), key, number(row.get("count")));
		}
		for (int i = 0; i < endpoints.size(); i++) {
			add(areas, areaOf((String) endpoints.get(i).get("key"), "\\.", javaPrefix), "endpoints", number(endpoints.get(i).get("count")));
		}
		for (int i = 0; i < screens.size(); i++) {
			add(areas, areaOf((String) screens.get(i).get("key"), "/", screenPrefix), "screens", number(screens.get(i).get("count")));
		}
		for (int i = 0; i < statements.size(); i++) {
			add(areas, areaOf((String) statements.get(i).get("key"), "/", mapperPrefix), "sqlStatements", number(statements.get(i).get("count")));
		}
		// 테이블은 영역 안에서 겹치지 않게 센다(같은 테이블을 여러 매퍼가 써도 한 번)
		Map<String, Set<String>> tablesByArea = new HashMap<String, Set<String>>();
		for (int i = 0; i < tables.size(); i++) {
			String area = areaOf((String) tables.get(i).get("key"), "/", mapperPrefix);
			Set<String> set = tablesByArea.get(area);
			if (set == null) {
				set = new HashSet<String>();
				tablesByArea.put(area, set);
			}
			set.add(String.valueOf(tables.get(i).get("table")));
		}
		for (Map.Entry<String, Set<String>> entry : tablesByArea.entrySet()) {
			add(areas, entry.getKey(), "tables", entry.getValue().size());
		}

		// 규모가 큰 영역부터. (그 밖) · (최상위) 는 맨 뒤
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>(areas.values());
		Collections.sort(rows, new Comparator<Map<String, Object>>() {
			@Override
			public int compare(Map<String, Object> a, Map<String, Object> b) {
				boolean aSpecial = String.valueOf(a.get("area")).startsWith("(");
				boolean bSpecial = String.valueOf(b.get("area")).startsWith("(");
				if (aSpecial != bSpecial) {
					return aSpecial ? 1 : -1;
				}
				long diff = size(b) - size(a);
				if (diff != 0) {
					return diff > 0 ? 1 : -1;
				}
				return String.valueOf(a.get("area")).compareTo(String.valueOf(b.get("area")));
			}
		});
		result.put("areas", rows);

		Map<String, Object> prefixes = new LinkedHashMap<String, Object>();
		prefixes.put("java", join(javaPrefix, "."));
		prefixes.put("screen", join(screenPrefix, "/"));
		prefixes.put("mapper", join(mapperPrefix, "/"));
		result.put("areaPrefixes", prefixes);
	}

	/**
	 * <pre>
	 * 한 리비전의 업무 영역 판별기. 다른 화면(전환 제외 후보 등)이 행마다 영역을 붙일 때 씁니다.
	 * 공통 앞부분은 "현행 구조"와 같은 행으로 구하므로 두 화면의 영역 이름이 늘 같습니다.
	 * </pre>
	 */
	public Areas areas(long revisionId) {
		return new Areas(structureDao.selectClassesByPackage(revisionId), structureDao.selectEndpointsByPackage(revisionId),
				structureDao.selectScreensByFolder(revisionId), structureDao.selectStatementsByFolder(revisionId));
	}

	/**
	 * <pre>
	 * 업무 영역 판별기. 공통 앞부분은 종류마다 따로 구한다(패키지는 '.', 폴더는 '/').
	 * 진입점은 클래스와 같은 패키지 체계라 클래스의 앞부분을 같이 쓴다.
	 * </pre>
	 */
	public class Areas {
		final List<String> javaPrefix;
		final List<String> screenPrefix;
		final List<String> mapperPrefix;

		Areas(List<Map<String, Object>> classes, List<Map<String, Object>> endpoints, List<Map<String, Object>> screens, List<Map<String, Object>> statements) {
			this.javaPrefix = dominantPrefix(merge(classes, endpoints), "\\.");
			this.screenPrefix = dominantPrefix(screens, "/");
			this.mapperPrefix = dominantPrefix(statements, "/");
		}

		/** 패키지(com.frameq.hsl.sa.web)의 영역 */
		public String ofPackage(String packageName) {
			return areaOf(packageName, "\\.", javaPrefix);
		}

		/** 화면 파일 경로의 영역(파일 이름은 뗀다) */
		public String ofScreenFile(String path) {
			return areaOf(folderOf(path), "/", screenPrefix);
		}

		/** SQL 매퍼 파일 경로의 영역(파일 이름은 뗀다) */
		public String ofMapperFile(String path) {
			return areaOf(folderOf(path), "/", mapperPrefix);
		}

		/** 경로에서 폴더만. 폴더가 없으면 빈 글(= 루트) */
		private String folderOf(String path) {
			if (path == null) {
				return "";
			}
			int slash = path.lastIndexOf('/');
			return slash > 0 ? path.substring(0, slash) : "";
		}
	}

	/** 영역 하나의 규모(정렬용): 화면 + 진입점 + 클래스 + SQL 문 */
	private long size(Map<String, Object> row) {
		long sum = 0;
		for (int i = 0; i < AREA_KEYS.size(); i++) {
			if (!"tables".equals(AREA_KEYS.get(i))) {
				sum += number(row.get(AREA_KEYS.get(i)));
			}
		}
		return sum;
	}

	private void add(Map<String, Map<String, Object>> areas, String area, String key, long count) {
		Map<String, Object> row = areas.get(area);
		if (row == null) {
			row = new LinkedHashMap<String, Object>();
			row.put("area", area);
			for (int i = 0; i < AREA_KEYS.size(); i++) {
				row.put(AREA_KEYS.get(i), Long.valueOf(0));
			}
			areas.put(area, row);
		}
		row.put(key, Long.valueOf(number(row.get(key)) + count));
	}

	/** 두 목록의 {key, count} 를 한 목록으로(공통 앞부분을 같이 구할 때) */
	private List<Map<String, Object>> merge(List<Map<String, Object>> a, List<Map<String, Object>> b) {
		List<Map<String, Object>> all = new ArrayList<Map<String, Object>>(a);
		all.addAll(b);
		return all;
	}

	/**
	 * <pre>
	 * 대부분(80% 이상, 개수로 잰다)이 공통으로 가진 앞부분.
	 * 앞부분을 뗀 뒤에도 마디가 하나 이상 남는 것만 셉니다(영역 이름이 될 마디가 있어야 하므로).
	 * 예) com.frameq.hsl.sa.web, com.frameq.hhr.ra.web, com.frameq.common.web → [com, frameq]
	 * </pre>
	 */
	List<String> dominantPrefix(List<Map<String, Object>> rows, String separator) {
		long total = 0;
		List<String[]> keys = new ArrayList<String[]>();
		List<Long> weights = new ArrayList<Long>();
		for (int i = 0; i < rows.size(); i++) {
			long weight = number(rows.get(i).get("count"));
			total += weight;
			keys.add(segments((String) rows.get(i).get("key"), separator));
			weights.add(Long.valueOf(weight));
		}
		List<String> prefix = new ArrayList<String>();
		if (total == 0) {
			return prefix;
		}
		while (true) {
			Map<String, Long> next = new HashMap<String, Long>();
			for (int i = 0; i < keys.size(); i++) {
				String[] key = keys.get(i);
				if (key.length > prefix.size() + 1 && startsWith(key, prefix)) {
					String segment = key[prefix.size()];
					Long sum = next.get(segment);
					next.put(segment, Long.valueOf((sum == null ? 0 : sum.longValue()) + weights.get(i).longValue()));
				}
			}
			String best = null;
			long bestWeight = 0;
			for (Map.Entry<String, Long> entry : next.entrySet()) {
				if (entry.getValue().longValue() > bestWeight) {
					best = entry.getKey();
					bestWeight = entry.getValue().longValue();
				}
			}
			if (best == null || bestWeight < DOMINANT * total) {
				return prefix;
			}
			prefix.add(best);
		}
	}

	/** 공통 앞부분을 뗀 첫 마디 = 영역 이름 */
	String areaOf(String key, String separator, List<String> prefix) {
		String[] segments = segments(key, separator);
		if (!startsWith(segments, prefix)) {
			return AREA_OTHER;
		}
		int at = prefix.size();
		// 기술 이름 마디는 건너뛴다. 단, 마지막 마디면 그대로 쓴다(그 뒤에 쓸 것이 없으므로)
		while (at < segments.length - 1 && TECH_SEGMENTS.contains(segments[at].toLowerCase(java.util.Locale.ROOT))) {
			at++;
		}
		return segments.length > at ? segments[at] : AREA_TOP;
	}

	private String[] segments(String key, String separator) {
		if (key == null || key.length() == 0 || "(루트)".equals(key)) {
			return new String[0];
		}
		return key.split(separator);
	}

	private boolean startsWith(String[] key, List<String> prefix) {
		if (key.length < prefix.size()) {
			return false;
		}
		for (int i = 0; i < prefix.size(); i++) {
			if (!prefix.get(i).equals(key[i])) {
				return false;
			}
		}
		return true;
	}

	private String join(List<String> parts, String separator) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			sb.append(i == 0 ? "" : separator).append(parts.get(i));
		}
		return sb.toString();
	}

	/* ======================= 신뢰도 ======================= */

	/**
	 * <pre>
	 * 이 결과를 얼마나 믿을 수 있는지. 개발 리더가 가장 먼저 묻는 것이라 첫 화면에 한 줄로 보여 줍니다.
	 *   callResolutionRate  메소드 호출 가운데 어느 메소드를 부르는지 알아낸 비율
	 *   callHeuristicRate   그 가운데 이름만 보고 짐작해 이은 비율
	 *   parseSuccessRate    파싱에 성공한 파일 비율
	 *   unresolved          못 푼 참조 수(UNRESOLVED 만)와 가장 많은 이유
	 * </pre>
	 */
	private Map<String, Object> trust(long revisionId) {
		Map<String, Object> trust = new LinkedHashMap<String, Object>();
		List<Map<String, Object>> metrics = relationDao.selectMetricsByRevision(revisionId);
		for (int i = 0; i < metrics.size(); i++) {
			String name = String.valueOf(metrics.get(i).get("name"));
			if ("callResolutionRate".equals(name) || "callHeuristicRate".equals(name) || "parseSuccessRate".equals(name)) {
				trust.put(name, metrics.get(i).get("value"));
			}
		}
		List<Map<String, Object>> reasons = relationDao.selectUnresolvedReasons(revisionId);
		long unresolved = 0;
		Map<String, Object> top = null;
		for (int i = 0; i < reasons.size(); i++) {
			Map<String, Object> row = reasons.get(i);
			if (!"UNRESOLVED".equals(row.get("status"))) {
				continue;
			}
			unresolved += number(row.get("count"));
			if (top == null || number(row.get("count")) > number(top.get("count"))) {
				top = row;
			}
		}
		trust.put("unresolvedCount", Long.valueOf(unresolved));
		if (top != null) {
			Map<String, Object> reason = new LinkedHashMap<String, Object>();
			reason.put("refKind", top.get("refKind"));
			reason.put("reason", top.get("reason"));
			reason.put("count", top.get("count"));
			trust.put("topUnresolvedReason", reason);
		}
		return trust;
	}

	private long number(Object value) {
		return value instanceof Number ? ((Number) value).longValue() : 0L;
	}

}

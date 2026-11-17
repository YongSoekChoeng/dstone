package net.dstone.knowledge.api.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.analysis.pass08.semantic.dictionary.DbCommentReader;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.api.dao.UnusedDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 미사용 분석. 있지만 아무도 쓰지 않는 테이블 · SQL · 클래스 · 메소드 · 화면을 셉니다.
 *
 * 판정 기준은 "들어오는 참조가 하나도 없다" 입니다(진입점에서 닿는지는 보지 않는다).
 * 정적 분석이라 틀릴 수 있으므로 항목마다 등급을 붙입니다(UnusedJudge).
 *   확실(CERTAIN)  쓰일 수 있다는 흔적이 없다
 *   추정(LIKELY)   프레임워크가 이름으로 부르거나, 못 푼 참조가 이것을 가리킬 수 있다
 * 분석 결과(analysis_*)를 읽기만 하고 저장하지 않으므로, 예전 리비전에도 바로 쓸 수 있습니다.
 * 계산한 목록은 리비전마다 잠시 들고 있다가, 그 리비전에 분석 Job 이 새로 돌면 다시 계산합니다.
 * </pre>
 */
@Service
public class UnusedService extends BaseObject {

	public static final String TABLE = "TABLE";
	public static final String SQL = "SQL";
	public static final String TYPE = "TYPE";
	public static final String METHOD = "METHOD";
	public static final String SCREEN = "SCREEN";

	/** 화면에 보여 줄 순서 */
	private static final List<String> KINDS = Arrays.asList(TABLE, SQL, TYPE, METHOD, SCREEN);

	/** 이 값보다 호출 해석률이 낮으면 판정을 믿기 어렵다고 알린다 */
	private static final double LOW_RESOLUTION = 80.0;

	/** 설정이 없을 때 쓰는 "프레임워크가 직접 쓰는" 애노테이션 */
	private static final String DEFAULT_FRAMEWORK = "Configuration,SpringBootApplication,ControllerAdvice,RestControllerAdvice,Aspect,"
			+ "WebServlet,WebFilter,WebListener,Entity,Embeddable,MappedSuperclass,SpringBootTest,Test,ParameterizedTest,"
			+ "Bean,PostConstruct,PreDestroy,EventListener,ExceptionHandler,ModelAttribute,InitBinder,Scheduled,"
			+ "KafkaListener,RabbitListener,JmsListener,Around,Before,After,AfterReturning,AfterThrowing,Pointcut";

	/** 캐시에 둘 리비전 수. 넘으면 비운다 */
	private static final int MAX_CACHE = 20;

	@Autowired
	private UnusedDao unusedDao;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private DbCommentReader dbCommentReader;

	@Autowired
	private StructureService structureService;

	@Autowired
	private ConfigProperty configProperty;

	/** 후보 쿼리를 한 트랜잭션에서 돌린다(그 안에서만 JIT 을 끈다) */
	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	private final Map<Long, Computed> cache = new ConcurrentHashMap<Long, Computed>();

	/** 한 리비전의 계산 결과 */
	private static class Computed {
		final String stamp;
		/** 종류 → 판정한 행 */
		final Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<String, List<Map<String, Object>>>();
		/** 화면에 같이 보일 사실(호출 해석률, 테이블 목록의 출처 …) */
		final Map<String, Object> facts = new LinkedHashMap<String, Object>();

		Computed(String stamp) {
			this.stamp = stamp;
		}
	}

	/**
	 * <pre>
	 * 종류별 건수(확실 / 추정)와 판정을 읽을 때 알아야 할 사실
	 * </pre>
	 *
	 * @return {revisionId, callResolutionRate, warning, tableSources, menuKnown, kinds:[{kind, total, certain, likely}]}
	 */
	public Map<String, Object> getSummary(long revisionId) {
		Computed computed = compute(revisionId);
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("revisionId", Long.valueOf(revisionId));
		body.putAll(computed.facts);
		List<Map<String, Object>> kinds = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < KINDS.size(); i++) {
			String kind = KINDS.get(i);
			List<Map<String, Object>> rows = computed.rows.get(kind);
			int certain = 0;
			for (int j = 0; j < rows.size(); j++) {
				if (UnusedJudge.CERTAIN.equals(rows.get(j).get("grade"))) {
					certain++;
				}
			}
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("kind", kind);
			item.put("total", Integer.valueOf(rows.size()));
			item.put("certain", Integer.valueOf(certain));
			item.put("likely", Integer.valueOf(rows.size() - certain));
			kinds.add(item);
		}
		body.put("kinds", kinds);
		return body;
	}

	/** 접속 정보처럼 리비전 밖의 것이 바뀌어 판정이 달라질 수 있을 때 부릅니다. 기억해 둔 결과를 모두 지웁니다 */
	public void forget() {
		cache.clear();
	}

	/** 클래스 이름(com.a.B 또는 com.a.B.Inner)에서 패키지. 점이 없으면 빈 글 */
	private String packageOf(String className) {
		if (className == null) {
			return "";
		}
		int dot = className.lastIndexOf('.');
		return dot > 0 ? className.substring(0, dot) : "";
	}

	/**
	 * <pre>
	 * 한 종류의 목록. grade(CERTAIN / LIKELY)는 일치 조건, q 는 이름 · 경로 · 한글명에 포함 조건(대소문자 무시),
	 * area 는 업무 영역 일치 조건입니다.
	 * 응답의 areas 는 grade · q 로 거른 뒤(area 로 거르기 전)의 영역별 건수입니다. 화면이 영역 고르기 칸을 그릴 때 씁니다.
	 * </pre>
	 */
	public Map<String, Object> getList(long revisionId, String kind, String grade, String q, String area, int page, int size) {
		String upperKind = kind == null ? "" : kind.toUpperCase(Locale.ROOT);
		if (!KINDS.contains(upperKind)) {
			throw ApiException.badRequest("kind 는 " + KINDS + " 가운데 하나여야 합니다: " + kind);
		}
		int safePage = Math.max(1, page);
		int safeSize = Math.max(1, Math.min(size, 500));
		String needle = q == null || q.trim().length() == 0 ? null : q.trim().toLowerCase(Locale.ROOT);
		List<Map<String, Object>> all = compute(revisionId).rows.get(upperKind);
		List<Map<String, Object>> matched = new ArrayList<Map<String, Object>>();
		Map<String, Integer> areaCounts = new HashMap<String, Integer>();
		for (int i = 0; i < all.size(); i++) {
			Map<String, Object> row = all.get(i);
			if (grade != null && grade.length() > 0 && !grade.equalsIgnoreCase(String.valueOf(row.get("grade")))) {
				continue;
			}
			if (needle != null && !contains(row, needle)) {
				continue;
			}
			String rowArea = row.get("area") == null ? "" : String.valueOf(row.get("area"));
			Integer count = areaCounts.get(rowArea);
			areaCounts.put(rowArea, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
			if (area != null && area.length() > 0 && !area.equals(rowArea)) {
				continue;
			}
			matched.add(row);
		}
		int from = Math.min((safePage - 1) * safeSize, matched.size());
		int to = Math.min(from + safeSize, matched.size());
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("revisionId", Long.valueOf(revisionId));
		body.put("kind", upperKind);
		body.put("total", Integer.valueOf(matched.size()));
		body.put("page", Integer.valueOf(safePage));
		body.put("size", Integer.valueOf(safeSize));
		body.put("rows", new ArrayList<Map<String, Object>>(matched.subList(from, to)));
		body.put("areas", areaList(areaCounts));
		return body;
	}

	/** 영역별 건수를 많은 것부터. 이름이 ( 로 시작하는 것((그 밖) 등)과 빈 영역은 맨 뒤 */
	private List<Map<String, Object>> areaList(Map<String, Integer> counts) {
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("area", entry.getKey());
			item.put("count", entry.getValue());
			list.add(item);
		}
		java.util.Collections.sort(list, new java.util.Comparator<Map<String, Object>>() {
			@Override
			public int compare(Map<String, Object> a, Map<String, Object> b) {
				String aName = String.valueOf(a.get("area"));
				String bName = String.valueOf(b.get("area"));
				boolean aLast = aName.length() == 0 || aName.startsWith("(");
				boolean bLast = bName.length() == 0 || bName.startsWith("(");
				if (aLast != bLast) {
					return aLast ? 1 : -1;
				}
				int diff = ((Integer) b.get("count")).intValue() - ((Integer) a.get("count")).intValue();
				return diff != 0 ? diff : aName.compareTo(bName);
			}
		});
		return list;
	}

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
		Computed computed = new Computed(stamp);
		long started = System.currentTimeMillis();

		BigDecimal rate = unusedDao.selectCallResolutionRate(revisionId);
		computed.facts.put("callResolutionRate", rate);
		if (rate != null && rate.doubleValue() < LOW_RESOLUTION) {
			computed.facts.put("warning", "호출 해석률이 " + rate.stripTrailingZeros().toPlainString()
					+ "% 라서 못 푼 호출이 많습니다. 실제로는 쓰이는데 미사용으로 나온 것이 섞여 있을 수 있습니다. 라이브러리(jar) 위치를 등록하고 다시 분석하면 정확해집니다.");
		}

		final Map<String, List<Map<String, Object>>> candidates = loadCandidates(revisionId);

		// 1) 클래스: 메소드 판정이 "미사용 클래스 안의 메소드" 를 빼려면 먼저 알아야 한다.
		Set<String> framework = frameworkAnnotations();
		List<Map<String, Object>> typeCandidates = candidates.get(TYPE);
		UnusedJudge typeJudge = new UnusedJudge(framework, new HashSet<String>());
		List<Map<String, Object>> types = new ArrayList<Map<String, Object>>();
		Set<String> unusedTypeIds = new HashSet<String>();
		for (int i = 0; i < typeCandidates.size(); i++) {
			Map<String, Object> row = typeCandidates.get(i);
			if (add(types, row, typeJudge.type(row), String.valueOf(row.get("id")))) {
				unusedTypeIds.add(String.valueOf(row.get("id")));
			}
		}
		UnusedJudge judge = new UnusedJudge(framework, unusedTypeIds);

		// 2) 메소드
		List<Map<String, Object>> methodCandidates = candidates.get(METHOD);
		List<Map<String, Object>> methods = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < methodCandidates.size(); i++) {
			Map<String, Object> row = methodCandidates.get(i);
			add(methods, row, judge.method(row), String.valueOf(row.get("id")));
		}

		// 3) SQL statement
		List<Map<String, Object>> sqlCandidates = candidates.get(SQL);
		List<Map<String, Object>> sqls = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < sqlCandidates.size(); i++) {
			Map<String, Object> row = sqlCandidates.get(i);
			add(sqls, row, judge.sql(row), String.valueOf(row.get("id")));
		}

		// 4) 화면
		boolean menuKnown = unusedDao.countMenuScreens(revisionId) > 0;
		computed.facts.put("menuKnown", Boolean.valueOf(menuKnown));
		List<Map<String, Object>> screenCandidates = candidates.get(SCREEN);
		List<Map<String, Object>> screens = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < screenCandidates.size(); i++) {
			Map<String, Object> row = screenCandidates.get(i);
			add(screens, row, judge.screen(menuKnown), String.valueOf(row.get("id")));
		}

		// 5) 테이블
		List<Map<String, Object>> tables = tables(revisionId, String.valueOf(revision.get("projectId")), judge, computed.facts);

		// 업무 영역. 프로젝트 화면의 "현행 구조"와 같은 규칙(StructureService.Areas)이라 두 화면의 영역 이름이 같다.
		// 테이블은 쓰는 SQL 이 없어서(그래서 미사용이다) 영역을 정할 수 없다.
		StructureService.Areas areas = structureService.areas(revisionId);
		for (int i = 0; i < sqls.size(); i++) {
			sqls.get(i).put("area", areas.ofMapperFile((String) sqls.get(i).get("path")));
		}
		for (int i = 0; i < screens.size(); i++) {
			screens.get(i).put("area", areas.ofScreenFile((String) screens.get(i).get("path")));
		}
		for (int i = 0; i < types.size(); i++) {
			types.get(i).put("area", areas.ofPackage(packageOf((String) types.get(i).get("name"))));
		}
		for (int i = 0; i < methods.size(); i++) {
			String name = (String) methods.get(i).get("name");
			int hash = name == null ? -1 : name.indexOf('#');
			methods.get(i).put("area", areas.ofPackage(packageOf(hash > 0 ? name.substring(0, hash) : name)));
		}

		computed.rows.put(TABLE, tables);
		computed.rows.put(SQL, sqls);
		computed.rows.put(TYPE, types);
		computed.rows.put(METHOD, methods);
		computed.rows.put(SCREEN, screens);
		debug("미사용 분석 revisionId=" + revisionId + " " + (System.currentTimeMillis() - started) + "ms");

		if (cache.size() >= MAX_CACHE) {
			cache.clear();
		}
		cache.put(key, computed);
		return computed;
	}

	/**
	 * <pre>
	 * 클래스 · 메소드 · SQL · 화면의 후보를 한 읽기 트랜잭션에서 꺼냅니다.
	 * 비용 추정이 큰 쿼리라 PostgreSQL 이 JIT 을 켜는데, 그 컴파일에만 쿼리마다 1초 넘게 들어서 이 트랜잭션에서만 끕니다.
	 * </pre>
	 */
	private Map<String, List<Map<String, Object>>> loadCandidates(final long revisionId) {
		return txTemplateCommon.execute(new TransactionCallback<Map<String, List<Map<String, Object>>>>() {
			@Override
			public Map<String, List<Map<String, Object>>> doInTransaction(TransactionStatus status) {
				status.setRollbackOnly();
				unusedDao.disableJit();
				Map<String, List<Map<String, Object>>> candidates = new LinkedHashMap<String, List<Map<String, Object>>>();
				candidates.put(TYPE, unusedDao.selectTypeCandidates(revisionId));
				candidates.put(METHOD, unusedDao.selectMethodCandidates(revisionId));
				candidates.put(SQL, unusedDao.selectSqlCandidates(revisionId));
				candidates.put(SCREEN, unusedDao.selectScreenCandidates(revisionId));
				return candidates;
			}
		});
	}

	/**
	 * <pre>
	 * 테이블. "있다" 는 근거는 DB(접속 설정이 있을 때) · DDL 파일 · 테이블 사전 CSV 입니다.
	 * 셋 다 없으면 안 쓰는 테이블을 알 수 없으므로 빈 목록이고, tableSources 에 그 사실이 남습니다.
	 * </pre>
	 */
	private List<Map<String, Object>> tables(long revisionId, String projectId, UnusedJudge judge, Map<String, Object> facts) {
		Map<String, Map<String, Object>> known = new LinkedHashMap<String, Map<String, Object>>();
		int ddl = 0;
		int dict = 0;
		List<Map<String, Object>> declared = unusedDao.selectDeclaredTables(revisionId);
		for (int i = 0; i < declared.size(); i++) {
			Map<String, Object> row = declared.get(i);
			String name = String.valueOf(row.get("name"));
			String source = "DDL_TABLE".equals(row.get("source")) ? "DDL" : "DICT";
			if ("DDL".equals(source)) {
				ddl++;
			} else {
				dict++;
			}
			Map<String, Object> table = known.get(name);
			if (table == null) {
				table = new LinkedHashMap<String, Object>();
				table.put("id", "T:" + name);
				table.put("name", name);
				table.put("kind", source);
				table.put("title", row.get("title"));
				table.put("path", row.get("path"));
				table.put("line", row.get("line"));
				known.put(name, table);
			} else {
				table.put("kind", table.get("kind") + "," + source);
				if (table.get("title") == null) {
					table.put("title", row.get("title"));
				}
			}
		}
		DbCommentReader.TableList db = dbCommentReader.listTables(projectId);
		for (int i = 0; i < db.tables.size(); i++) {
			String name = db.tables.get(i);
			Map<String, Object> table = known.get(name);
			if (table == null) {
				table = new LinkedHashMap<String, Object>();
				table.put("id", "T:" + name);
				table.put("name", name);
				table.put("kind", "DB");
				known.put(name, table);
			} else {
				table.put("kind", "DB," + table.get("kind"));
			}
		}
		Map<String, Object> sources = new LinkedHashMap<String, Object>();
		sources.put("ddl", Integer.valueOf(ddl));
		sources.put("dict", Integer.valueOf(dict));
		sources.put("db", db.status);
		sources.put("dbTables", Integer.valueOf(db.tables.size()));
		if (db.message != null) {
			sources.put("dbMessage", db.message);
		}
		facts.put("tableSources", sources);

		Set<String> used = new HashSet<String>(unusedDao.selectUsedTables(revisionId));
		List<String> unused = new ArrayList<String>();
		for (String name : known.keySet()) {
			if (!used.contains(name)) {
				unused.add(name);
			}
		}
		Set<String> inSqlText = new HashSet<String>();
		// 이름 목록을 한 번에 너무 많이 보내지 않게 나눈다
		for (int from = 0; from < unused.size(); from += 500) {
			List<String> chunk = unused.subList(from, Math.min(from + 500, unused.size()));
			inSqlText.addAll(unusedDao.selectTablesInSqlText(revisionId, new ArrayList<String>(chunk)));
		}
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < unused.size(); i++) {
			Map<String, Object> table = known.get(unused.get(i));
			add(rows, table, judge.table(inSqlText.contains(unused.get(i))), String.valueOf(table.get("id")));
		}
		return rows;
	}

	/** 판정 결과를 붙여 목록에 넣는다. 판정하지 않는 것(null)이면 넣지 않고 false */
	private boolean add(List<Map<String, Object>> rows, Map<String, Object> row, UnusedJudge.Verdict verdict, String graphId) {
		if (verdict == null) {
			return false;
		}
		Map<String, Object> item = new LinkedHashMap<String, Object>();
		item.put("graphId", graphId);
		item.put("name", row.get("name"));
		item.put("title", row.get("title"));
		item.put("kind", row.get("kind"));
		item.put("layer", row.get("layer"));
		item.put("path", row.get("path"));
		item.put("line", row.get("line"));
		item.put("grade", verdict.grade);
		item.put("reason", verdict.reason);
		rows.add(item);
		return true;
	}

	private boolean contains(Map<String, Object> row, String needle) {
		String[] keys = { "name", "title", "path" };
		for (int i = 0; i < keys.length; i++) {
			Object value = row.get(keys[i]);
			if (value != null && String.valueOf(value).toLowerCase(Locale.ROOT).indexOf(needle) >= 0) {
				return true;
			}
		}
		return false;
	}

	/** dstone.knowledge.unused.framework-annotations (쉼표로 나눈 애노테이션 단순 이름). 없으면 기본 목록 */
	private Set<String> frameworkAnnotations() {
		String configured = configProperty.getProperty("dstone.knowledge.unused.framework-annotations");
		String text = configured == null || configured.trim().length() == 0 ? DEFAULT_FRAMEWORK : configured;
		return new HashSet<String>(UnusedJudge.split(text));
	}

}

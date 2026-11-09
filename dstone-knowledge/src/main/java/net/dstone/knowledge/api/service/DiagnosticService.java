package net.dstone.knowledge.api.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.DiagnosticDao;
import net.dstone.knowledge.api.dao.JavaSqlDao;
import net.dstone.knowledge.api.dao.RelationDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.common.exception.ApiException;
import net.dstone.knowledge.semantic.rule.SemanticRuleRegistry;

/**
 * <pre>
 * 적용 진단. 새 프로젝트를 처음 분석한 뒤 "어디가 안 이어졌는지" 와 "그것을 무엇으로 풀지" 를 한 번에 보여 줍니다.
 * 항목마다 해결 방법(solution)을 붙입니다.
 *   RULE      규칙 파일(conf/rules/{프로젝트ID}/*.yml)로 풀 수 있다. Java 를 고치지 않는다
 *   CLASSPATH 라이브러리(jar) 위치를 프로젝트에 적고 다시 분석한다
 *   SETTING   프로젝트 설정(Java 버전, 인코딩 …)을 고친다
 *   SOURCE    분석 대상 소스가 빠졌다(공통 모듈, 매퍼 XML …). 소스를 더한다
 *   DEVELOP   지원 범위 밖이다. 분석기를 개발해야 한다
 *   NONE      할 일이 없다
 * 분석 결과(analysis_*)를 읽기만 합니다. 지원하지 않는 언어의 파일은 결과에 없으므로 프로젝트 폴더를 지금 상태로 훑어 셉니다.
 * </pre>
 */
@Service
public class DiagnosticService extends BaseObject {

	private static final int SAMPLES = 10;
	/** 프로젝트 폴더를 훑을 때 볼 파일 수의 한도. 아주 큰 폴더에서 오래 걸리지 않게 한다 */
	private static final int MAX_VISIT = 300000;
	/** 분석하지 않는 소스 확장자 → 무엇인지 */
	private static final Map<String, String> UNSUPPORTED = new LinkedHashMap<String, String>();
	/** 훑지 않는 폴더(빌드 결과, 내려받은 라이브러리) */
	private static final List<String> SKIP_DIRS = Arrays.asList("node_modules", "target", "build", "dist", "bin", "out", ".git", ".svn", ".idea", ".gradle");

	static {
		UNSUPPORTED.put("kt", "Kotlin");
		UNSUPPORTED.put("kts", "Kotlin");
		UNSUPPORTED.put("groovy", "Groovy");
		UNSUPPORTED.put("scala", "Scala");
		UNSUPPORTED.put("vue", "Vue 화면");
		UNSUPPORTED.put("ts", "TypeScript");
		UNSUPPORTED.put("tsx", "TypeScript(React) 화면");
		UNSUPPORTED.put("jsx", "React 화면");
		UNSUPPORTED.put("py", "Python");
		UNSUPPORTED.put("cs", "C#");
		UNSUPPORTED.put("go", "Go");
		UNSUPPORTED.put("mrd", "Rexpert/MiPlatform 계열 화면");
	}

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private RelationDao relationDao;

	@Autowired
	private DiagnosticDao diagnosticDao;

	@Autowired
	private JavaSqlDao javaSqlDao;

	@Autowired
	private SemanticRuleRegistry ruleRegistry;

	/**
	 * @return {revisionId, projectId, summary: {RULE: n, ...}, checks: [{key, title, level, solution, count, total, message, samples}]}
	 */
	public Map<String, Object> diagnose(long revisionId) {
		Map<String, Object> revision = revisionDao.selectRevision(revisionId);
		if (revision == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		String projectId = (String) revision.get("projectId");
		Map<String, Object> project = projectService.getProject(projectId);

		List<Map<String, Object>> checks = new ArrayList<Map<String, Object>>();
		Map<String, Object> metrics = new LinkedHashMap<String, Object>();
		List<Map<String, Object>> metricRows = relationDao.selectMetricsByRevision(revisionId);
		for (int i = 0; i < metricRows.size(); i++) {
			metrics.put((String) metricRows.get(i).get("name"), metricRows.get(i).get("value"));
		}
		checkCalls(checks, metrics);
		checkParse(checks, metrics);
		checkUnsupportedFiles(checks, (String) project.get("localPath"));
		checkEndpoints(checks, revisionId);
		checkUnresolvedPaths(checks, revisionId);
		checkScreens(checks, revisionId);
		checkUnresolvedTargets(checks, revisionId);
		checkUnknownXml(checks, revisionId);
		checkJavaSql(checks, revisionId);
		checkLayers(checks, revisionId);
		checkRules(checks, revisionId, projectId);

		Map<String, Object> summary = new TreeMap<String, Object>();
		for (int i = 0; i < checks.size(); i++) {
			Map<String, Object> check = checks.get(i);
			String level = (String) check.get("level");
			if ("warn".equals(level) || "bad".equals(level)) {
				String solution = (String) check.get("solution");
				Object count = summary.get(solution);
				summary.put(solution, Integer.valueOf(count == null ? 1 : ((Integer) count).intValue() + 1));
			}
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", Long.valueOf(revisionId));
		result.put("projectId", projectId);
		result.put("summary", summary);
		result.put("checks", checks);
		return result;
	}

	private void checkCalls(List<Map<String, Object>> checks, Map<String, Object> metrics) {
		Number rate = (Number) metrics.get("callResolutionRate");
		if (rate == null) {
			return;
		}
		double value = rate.doubleValue();
		String level = value >= 95 ? "good" : value >= 80 ? "warn" : "bad";
		checks.add(check("calls", "호출 해석", level, level.equals("good") ? "NONE" : "CLASSPATH", toInt(metrics.get("callCount")), null
				, "메소드 호출의 " + value + "% 가 누구를 부르는지 풀렸습니다."
						+ (level.equals("good") ? "" : " 리비전 요약의 \"풀지 못한 참조 살펴보기\"에서 못 찾은 기호를 보고, 그 타입이 든 jar 위치를 프로젝트에 적은 뒤 다시 분석합니다."
								+ " 프로젝트 소스에 없는 공통 모듈의 클래스라면 그 소스나 jar 를 더합니다(SOURCE).")
				, null));
	}

	private void checkParse(List<Map<String, Object>> checks, Map<String, Object> metrics) {
		Number rate = (Number) metrics.get("parseSuccessRate");
		if (rate == null) {
			return;
		}
		boolean ok = rate.doubleValue() >= 100;
		checks.add(check("parse", "Java 파일 읽기", ok ? "good" : "warn", ok ? "NONE" : "SETTING", null, null
				, ok ? "Java 파일을 모두 읽었습니다." : "Java 파일의 " + rate + "% 만 읽었습니다. 분석 Job 의 오류 표에서 파일을 확인합니다. 대개 Java 버전이나 인코딩 설정 문제입니다."
				, null));
	}

	/** 분석하지 않는 언어의 소스가 프로젝트 폴더에 있는지 */
	private void checkUnsupportedFiles(List<Map<String, Object>> checks, String localPath) {
		if (localPath == null || !new File(localPath).isDirectory()) {
			checks.add(check("unsupportedFiles", "지원하지 않는 소스", "info", "NONE", null, null
					, "프로젝트 폴더를 이 서버에서 열 수 없어 확인하지 못했습니다: " + localPath, null));
			return;
		}
		final Map<String, int[]> counts = new TreeMap<String, int[]>();
		final Map<String, String> examples = new LinkedHashMap<String, String>();
		final int[] visited = { 0 };
		final Path root = Paths.get(localPath);
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					Path name = dir.getFileName();
					return name != null && !dir.equals(root) && SKIP_DIRS.contains(name.toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (++visited[0] > MAX_VISIT) {
						return FileVisitResult.TERMINATE;
					}
					String name = file.getFileName().toString();
					int dot = name.lastIndexOf('.');
					if (dot < 0 || name.endsWith(".d.ts")) {
						return FileVisitResult.CONTINUE;
					}
					String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
					if (UNSUPPORTED.containsKey(ext)) {
						int[] count = counts.get(ext);
						if (count == null) {
							counts.put(ext, new int[] { 1 });
							examples.put(ext, root.relativize(file).toString().replace('\\', '/'));
						} else {
							count[0]++;
						}
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			warn("프로젝트 폴더를 훑지 못했습니다: " + localPath + " - " + e.getMessage());
		}
		List<Map<String, Object>> samples = new ArrayList<Map<String, Object>>();
		int total = 0;
		for (Map.Entry<String, int[]> entry : counts.entrySet()) {
			total += entry.getValue()[0];
			Map<String, Object> sample = new LinkedHashMap<String, Object>();
			sample.put("name", "." + entry.getKey() + " (" + UNSUPPORTED.get(entry.getKey()) + ")");
			sample.put("count", Integer.valueOf(entry.getValue()[0]));
			sample.put("example", examples.get(entry.getKey()));
			samples.add(sample);
		}
		checks.add(check("unsupportedFiles", "지원하지 않는 소스", total == 0 ? "good" : "warn", total == 0 ? "NONE" : "DEVELOP", Integer.valueOf(total), null
				, total == 0 ? "분석하지 않는 언어의 소스가 없습니다."
						: "분석하지 않는 언어의 소스가 " + total + "개 있습니다. 이 파일들의 호출과 화면은 결과에 없습니다. 규칙으로는 풀 수 없고 분석기 개발이 필요합니다."
								+ (visited[0] > MAX_VISIT ? " (파일이 많아 앞의 " + MAX_VISIT + "개만 셌습니다)" : "")
				, samples));
	}

	private void checkEndpoints(List<Map<String, Object>> checks, long revisionId) {
		List<Map<String, Object>> rows = diagnosticDao.selectEndpointSummary(revisionId);
		int total = 0;
		int noMethod = 0;
		List<Map<String, Object>> byType = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String type = (String) row.get("endpointType");
			int count = toInt(row.get("count")).intValue();
			total += count;
			if (!"JSP".equals(type) && !"SERVLET".equals(type)) {
				noMethod += toInt(row.get("noMethod")).intValue();
			}
			Map<String, Object> sample = new LinkedHashMap<String, Object>();
			sample.put("name", type);
			sample.put("count", Integer.valueOf(count));
			byType.add(sample);
		}
		checks.add(check("endpoints", "진입점", total > 0 ? "good" : "bad", total > 0 ? "NONE" : "RULE", Integer.valueOf(total), null
				, total > 0 ? "진입점(주소 · 거래 · 스케줄 …) " + total + "개를 찾았습니다."
						: "진입점을 하나도 찾지 못했습니다. 주소를 애노테이션으로 적는 프레임워크면 endpoints.http / annotations 규칙, 설정 XML 에 적는 프레임워크면 endpoints.xml 규칙을 더합니다."
				, byType));
		if (noMethod > 0) {
			checks.add(check("endpointsWithoutMethod", "처리 메소드를 모르는 진입점", "warn", "RULE", Integer.valueOf(noMethod), Integer.valueOf(total)
					, "진입점 " + noMethod + "개는 처리하는 메소드를 정하지 못했습니다. 설정에 적힌 클래스가 프로젝트에 없거나(SOURCE), 메소드를 요청 파라미터로 고르는 방식(Struts DispatchAction 등, DEVELOP)입니다."
							+ " endpoints.xml 규칙이면 classAttribute / methodAttribute 를 확인합니다."
					, diagnosticDao.selectEndpointsWithoutMethod(revisionId, SAMPLES)));
		}
	}

	private void checkScreens(List<Map<String, Object>> checks, long revisionId) {
		Map<String, Object> screens = diagnosticDao.selectScreenSummary(revisionId);
		int total = toInt(screens.get("total")).intValue();
		if (total == 0) {
			return;
		}
		int noRequest = toInt(screens.get("noRequest")).intValue();
		boolean many = noRequest * 100 > total * 40;
		List<Map<String, Object>> samples = new ArrayList<Map<String, Object>>();
		List<String> paths = diagnosticDao.selectScreensWithoutRequest(revisionId, SAMPLES);
		for (int i = 0; i < paths.size(); i++) {
			Map<String, Object> sample = new LinkedHashMap<String, Object>();
			sample.put("name", paths.get(i));
			samples.add(sample);
		}
		checks.add(check("screens", "서버와 이어지지 않은 화면", noRequest == 0 ? "good" : many ? "warn" : "info", noRequest == 0 ? "NONE" : "RULE"
				, Integer.valueOf(noRequest), Integer.valueOf(total)
				, noRequest == 0 ? "모든 화면이 서버의 무엇인가를 부릅니다."
						: "화면 " + total + "개 가운데 " + noRequest + "개는 서버의 무엇도 부르지 않는 것으로 나왔습니다. 팝업 · 조각 화면이면 정상입니다."
								+ " 공통 함수에 거래 ID 를 넘겨 서버를 부르는 프로젝트면 screens.calls 규칙(필요하면 idPattern / idCase / idMap)을 더합니다."
								+ " 규칙 후보 API(/rules/candidates)의 screenRules 도 봅니다."
				, samples));
	}

	private void checkUnresolvedTargets(List<Map<String, Object>> checks, long revisionId) {
		int requests = diagnosticDao.countUnresolvedTargets(revisionId, "REQUESTS");
		if (requests > 0) {
			checks.add(check("unresolvedRequests", "화면이 부르는데 메소드가 없는 거래", "warn", "RULE", Integer.valueOf(requests), null
					, "화면 규칙으로 메소드 이름을 만들었는데 그런 메소드가 프로젝트에 없습니다. 거래 ID 와 메소드 이름의 관계가 틀에 그대로 들어맞지 않으면"
							+ " idPattern / idReplace / idCase 로 다듬거나, 대응표(idMap)를 씁니다. 서버 소스가 다른 저장소에 있으면 SOURCE 입니다."
					, diagnosticDao.selectUnresolvedTargets(revisionId, "REQUESTS", SAMPLES)));
		}
		int sql = diagnosticDao.countUnresolvedTargets(revisionId, "EXECUTES_SQL");
		if (sql > 0) {
			checks.add(check("unresolvedSql", "이름은 아는데 없는 SQL", "warn", "SOURCE", Integer.valueOf(sql), null
					, "코드가 부르는 SQL 이름의 statement 가 매퍼에 없습니다. 매퍼 XML 이 분석 대상 폴더 밖에 있으면 소스를 더하고(SOURCE),"
							+ " 이름 앞뒤에 프레임워크가 글자를 붙이면 callArguments 규칙의 template 을 고칩니다(RULE)."
					, diagnosticDao.selectUnresolvedTargets(revisionId, "EXECUTES_SQL", SAMPLES)));
		}
	}

	private void checkUnresolvedPaths(List<Map<String, Object>> checks, long revisionId) {
		int count = diagnosticDao.countUnresolvedPaths(revisionId);
		if (count == 0) {
			return;
		}
		checks.add(check("unresolvedPaths", "값을 모르는 주소 상수", "warn", "SOURCE", Integer.valueOf(count), null
				, "HTTP 진입점 " + count + "개는 주소를 상수로 적었는데 그 값을 계산하지 못해, 상수 이름이 주소에 그대로 들어 있습니다."
						+ " 상수가 분석 대상 밖(다른 jar · 모듈)에 있으면 그 소스를 더합니다(SOURCE). static import 한 상수는 아직 따라가지 못합니다(DEVELOP)."
				, diagnosticDao.selectUnresolvedPaths(revisionId, SAMPLES)));
	}

	/** Java 문자열 SQL 과 JPA 쿼리. 있으면 알려 주고, 테이블을 찾지 못한 것을 보여 준다 */
	private void checkJavaSql(List<Map<String, Object>> checks, long revisionId) {
		Map<String, Object> summary = javaSqlDao.selectSummary(revisionId);
		int java = toInt(summary.get("java")).intValue();
		int jpa = toInt(summary.get("jpa")).intValue();
		if (java + jpa == 0) {
			return;
		}
		int noTable = toInt(summary.get("javaNoTable")).intValue() + toInt(summary.get("jpaNoTable")).intValue();
		checks.add(check("javaSql", "Java 소스 안의 SQL", noTable == 0 ? "good" : "info", "NONE", Integer.valueOf(java + jpa), null
				, "Java 문자열 SQL " + java + "개(모르는 부분이 든 것 " + toInt(summary.get("javaDynamic")) + "), JPA 쿼리 " + jpa + "개를 SQL 매퍼처럼 메소드 · 테이블과 이었습니다."
						+ (noTable == 0 ? "" : " " + noTable + "개는 테이블을 찾지 못했습니다. 테이블 이름을 변수로 넘기거나(규칙으로 풀 수 없음),"
								+ " JPQL 의 엔티티가 분석 대상에 없거나(SOURCE), 엔티티 · 테이블 애노테이션 이름이 jpa 규칙과 다릅니다(RULE).")
				, diagnosticDao.selectJavaSqlWithoutTable(revisionId, SAMPLES)));
	}

	private void checkUnknownXml(List<Map<String, Object>> checks, long revisionId) {
		int count = diagnosticDao.countUnknownXml(revisionId);
		if (count == 0) {
			return;
		}
		checks.add(check("unknownXml", "종류를 모르는 XML", "info", "RULE", Integer.valueOf(count), null
				, "매퍼 · Spring · web.xml · 화면이 아닌 XML 이 " + count + "개 있습니다. 대부분 문제가 아닙니다."
						+ " 이 가운데 주소(거래 ID)와 처리 클래스 · 메소드를 적은 자체 프레임워크의 설정 파일이 있으면 endpoints.xml 규칙으로 진입점을 만듭니다."
				, diagnosticDao.selectUnknownXml(revisionId, SAMPLES)));
	}

	private void checkLayers(List<Map<String, Object>> checks, long revisionId) {
		Map<String, Object> layers = diagnosticDao.selectClassLayerSummary(revisionId);
		int total = toInt(layers.get("total")).intValue();
		if (total == 0) {
			return;
		}
		int noLayer = toInt(layers.get("noLayer")).intValue();
		int low = toInt(layers.get("low")).intValue();
		boolean many = noLayer * 100 > total * 30;
		checks.add(check("layers", "계층을 모르는 클래스", noLayer == 0 ? "good" : many ? "warn" : "info", noLayer == 0 ? "NONE" : "RULE"
				, Integer.valueOf(noLayer), Integer.valueOf(total)
				, "클래스 " + total + "개 가운데 " + noLayer + "개는 계층(Controller / Service …)을 정하지 못했고, " + low + "개는 이름 끝말로 짐작했습니다(LOW)."
						+ (noLayer == 0 ? "" : " 자체 프레임워크의 상위 타입이나 애노테이션이 있으면 layers.superTypes / layers.annotations 규칙을 더합니다. 유틸 · VO 처럼 계층이 없는 것이 맞는 클래스도 있습니다.")
				, diagnosticDao.selectNoLayerPackages(revisionId, SAMPLES)));
	}

	private void checkRules(List<Map<String, Object>> checks, long revisionId, String projectId) {
		List<String> applied = diagnosticDao.selectAppliedRuleSetNames(revisionId);
		boolean projectFolder = false;
		File[] dirs = ruleRegistry.rulesDir().listFiles();
		for (int i = 0; dirs != null && i < dirs.length; i++) {
			if (dirs[i].isDirectory() && dirs[i].getName().equalsIgnoreCase(projectId)) {
				projectFolder = true;
			}
		}
		List<Map<String, Object>> samples = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < applied.size(); i++) {
			Map<String, Object> sample = new LinkedHashMap<String, Object>();
			sample.put("name", applied.get(i));
			samples.add(sample);
		}
		checks.add(check("rules", "적용한 규칙", "info", "NONE", Integer.valueOf(applied.size()), null
				, (projectFolder ? "이 프로젝트 전용 규칙 폴더(conf/rules/" + projectId + "/)가 있습니다."
						: "이 프로젝트 전용 규칙 폴더가 없습니다. 이 프로젝트에만 쓸 관례는 conf/rules/" + projectId + "/ 아래에 적습니다(ruleSets 에 적지 않아도 저절로 쓴다).")
						+ " 규칙을 고친 뒤에는 SEMANTIC 부터 다시 분석합니다."
				, samples));
	}

	private Map<String, Object> check(String key, String title, String level, String solution, Integer count, Integer total, String message
			, List<Map<String, Object>> samples) {
		Map<String, Object> check = new LinkedHashMap<String, Object>();
		check.put("key", key);
		check.put("title", title);
		check.put("level", level);
		check.put("solution", solution);
		check.put("count", count);
		check.put("total", total);
		check.put("message", message);
		check.put("samples", samples == null ? new ArrayList<Map<String, Object>>() : samples);
		return check;
	}

	private static Integer toInt(Object value) {
		return value == null ? null : Integer.valueOf(((Number) value).intValue());
	}

}

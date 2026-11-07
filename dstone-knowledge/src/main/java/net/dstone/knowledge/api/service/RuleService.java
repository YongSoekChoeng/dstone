package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.AnalysisJobDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.api.dao.RuleDao;
import net.dstone.knowledge.common.exception.ApiException;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.job.AnalysisJobRunner;
import net.dstone.knowledge.semantic.SemanticPass;
import net.dstone.knowledge.semantic.rule.RuleException;
import net.dstone.knowledge.semantic.rule.RuleSet;
import net.dstone.knowledge.semantic.rule.SemanticRuleRegistry;
import net.dstone.knowledge.semantic.rule.SemanticRules;

/**
 * <pre>
 * 의미 분석 규칙(conf/rules/*.yml)을 다루는 API 의 업무 로직입니다.
 *
 *   규칙 묶음 목록        폴더에 있는 규칙 묶음과 그 해시
 *   리비전의 규칙         그 리비전을 분석할 때 쓴 묶음과 지금 파일의 비교(바뀌었으면 SEMANTIC 부터 다시 돌려야 반영된다)
 *   시험 적용(trial)      규칙 묶음 글을 받아 SEMANTIC 을 한 트랜잭션 안에서 돌려 보고, 전후 차이를 알려 준 뒤 되돌린다
 *   후보 찾기             규칙이 아직 모르는 관례의 흔적(SQL 이름 같은 글자를 받는 호출, 많이 붙은 애노테이션, 많이 상속한 상위 타입)
 *
 * 새 환경의 프로젝트를 분석할 때의 순서:
 *   1) 후보를 본다 → 2) 제안된 규칙 조각을 고쳐 시험 적용으로 결과를 본다 → 3) conf/rules 에 파일로 저장한다
 *   → 4) rerunFrom=SEMANTIC 으로 다시 돌린다
 * </pre>
 */
@Service
public class RuleService extends BaseObject {

	/** 후보로 보지 않을 애노테이션. 뜻이 언어나 도구(Lombok, 테스트)에 있는 것들이다 */
	private static final List<String> IGNORED_ANNOTATIONS = Arrays.asList(
			"Override", "Deprecated", "SuppressWarnings", "FunctionalInterface", "SafeVarargs"
			, "Getter", "Setter", "Data", "Value", "Builder", "ToString", "EqualsAndHashCode", "NoArgsConstructor", "AllArgsConstructor"
			, "RequiredArgsConstructor", "Slf4j", "Log4j", "Log4j2", "Log", "CommonsLog", "NonNull", "Nullable", "NotNull"
			, "Test", "BeforeEach", "AfterEach", "BeforeAll", "AfterAll", "Before", "After", "DisplayName", "ParameterizedTest"
			, "JsonProperty", "JsonIgnore", "JsonFormat", "Column", "Id", "Table", "GeneratedValue", "Transient");

	private static final int MAX_SAMPLES = 200;

	@Autowired
	private SemanticRuleRegistry ruleRegistry;

	@Autowired
	private SemanticPass semanticPass;

	@Autowired
	private AnalysisJobRunner analysisJobRunner;

	@Autowired
	private RuleDao ruleDao;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private AnalysisJobDao analysisJobDao;

	@Autowired
	private ProjectService projectService;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	/** 폴더에 있는 규칙 묶음 목록(order 순서) */
	public Map<String, Object> getRuleSets() {
		List<RuleSet> sets = loadAll();
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < sets.size(); i++) {
			list.add(describe(sets.get(i)));
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("rulesDir", ruleRegistry.rulesDir().getPath());
		result.put("ruleSets", list);
		return result;
	}

	/**
	 * <pre>
	 * 리비전을 분석할 때 쓴 규칙 묶음과, 지금 이 프로젝트에 쓰일 묶음을 견줍니다.
	 * status: SAME(같음) / CHANGED(파일 내용이 바뀜) / REMOVED(지금은 쓰지 않음) / ADDED(지금은 쓰는데 그때는 없었음)
	 * 하나라도 SAME 이 아니면 upToDate 가 false 이고, SEMANTIC 부터 다시 돌려야 지금 규칙이 반영됩니다.
	 * </pre>
	 */
	public Map<String, Object> getRevisionRules(long revisionId) {
		Map<String, Object> revision = revisionOf(revisionId);
		Map<String, Object> project = projectService.getProject((String) revision.get("projectId"));
		List<Map<String, Object>> applied = ruleDao.selectAppliedRuleSets(revisionId);
		List<RuleSet> current = rulesFor(project, null).getSets();

		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		Set<String> seen = new LinkedHashSet<String>();
		boolean upToDate = !applied.isEmpty();
		for (int i = 0; i < applied.size(); i++) {
			Map<String, Object> a = applied.get(i);
			String name = (String) a.get("name");
			seen.add(name);
			RuleSet now = find(current, name);
			Map<String, Object> row = new LinkedHashMap<String, Object>();
			row.put("name", name);
			row.put("source", a.get("source"));
			row.put("appliedHash", a.get("hash"));
			row.put("currentHash", now == null ? null : now.hash);
			String status = now == null ? "REMOVED" : now.hash.equals(a.get("hash")) ? "SAME" : "CHANGED";
			row.put("status", status);
			upToDate = upToDate && "SAME".equals(status);
			rows.add(row);
		}
		for (int i = 0; i < current.size(); i++) {
			RuleSet now = current.get(i);
			if (!seen.contains(now.name)) {
				Map<String, Object> row = new LinkedHashMap<String, Object>();
				row.put("name", now.name);
				row.put("source", now.source);
				row.put("appliedHash", null);
				row.put("currentHash", now.hash);
				row.put("status", "ADDED");
				upToDate = false;
				rows.add(row);
			}
		}

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", Long.valueOf(revisionId));
		result.put("projectId", revision.get("projectId"));
		result.put("projectRuleSets", project.get("ruleSets"));
		result.put("upToDate", Boolean.valueOf(upToDate));
		if (applied.isEmpty()) {
			result.put("note", "규칙 기록이 없는 리비전입니다(규칙 파일을 쓰기 전에 분석했거나 SEMANTIC 을 돌리지 않았습니다).");
		} else if (!upToDate) {
			result.put("note", "분석할 때와 지금의 규칙이 다릅니다. rerunFrom=SEMANTIC 으로 다시 돌리면 지금 규칙이 반영됩니다.");
		}
		result.put("ruleSets", rows);
		return result;
	}

	/**
	 * <pre>
	 * 규칙을 시험 적용합니다. SEMANTIC 단계를 한 트랜잭션 안에서 실제로 돌려 보고, 전후를 견준 뒤 모두 되돌립니다.
	 * DB 에 남는 것은 없습니다. 큰 프로젝트는 SEMANTIC 한 번만큼(수십 초~몇 분) 걸립니다.
	 *
	 * ruleSetText 가 있으면 그 묶음을 더해 봅니다(같은 이름의 파일이 있으면 그 대신). 없으면 지금 파일 그대로 돌려,
	 * "규칙 파일을 고쳤는데 다시 돌리면 무엇이 바뀌나" 를 봅니다.
	 *
	 * 그 프로젝트에 분석이 돌고 있으면 409 입니다. 서버 전체에서 한 번에 하나만 돕니다(synchronized).
	 * </pre>
	 */
	public synchronized Map<String, Object> trial(long revisionId, String ruleSetText, int sampleLimit) {
		long started = System.currentTimeMillis();
		Map<String, Object> revision = revisionOf(revisionId);
		String projectId = (String) revision.get("projectId");
		Map<String, Object> project = projectService.getProject(projectId);
		checkReadyForSemantic(revisionId);
		if (analysisJobDao.countActiveJobByProject(projectId) > 0) {
			throw ApiException.conflict("이 프로젝트는 분석이 돌고 있습니다. 끝난 뒤에 시험하세요: " + projectId);
		}

		RuleSet trialSet = null;
		if (ruleSetText != null && ruleSetText.trim().length() > 0) {
			try {
				trialSet = ruleRegistry.parse(ruleSetText);
			} catch (RuleException e) {
				throw ApiException.badRequest(e.getMessage());
			}
		}
		final SemanticRules rules = rulesFor(project, trialSet);
		final AnalysisJobContext context = new AnalysisJobContext("TRIAL", projectId, revisionId, project);
		context.setSemanticRules(rules);
		final long id = revisionId;
		final int limit = Math.max(1, Math.min(sampleLimit, MAX_SAMPLES));

		Map<String, Object> result = txTemplateCommon.execute(new TransactionCallback<Map<String, Object>>() {
			@Override
			public Map<String, Object> doInTransaction(TransactionStatus status) {
				// 무엇이 있어도 마지막에 되돌린다. 시험 적용은 아무것도 남기지 않는다.
				status.setRollbackOnly();
				ruleDao.createBeforeFacts(id);
				semanticPass.runPlugins(context);
				ruleDao.createAfterFacts(id);
				Map<String, Object> body = new LinkedHashMap<String, Object>();
				body.put("summary", changedOnly(ruleDao.selectTrialSummary()));
				body.put("added", ruleDao.selectTrialSamples("ADDED", limit));
				body.put("removed", ruleDao.selectTrialSamples("REMOVED", limit));
				return body;
			}
		});

		Map<String, Object> response = new LinkedHashMap<String, Object>();
		response.put("revisionId", Long.valueOf(revisionId));
		response.put("projectId", projectId);
		response.put("trialRuleSet", trialSet == null ? null : trialSet.name);
		response.put("ruleSets", names(rules.getSets()));
		response.putAll(result);
		response.put("elapsedMs", Long.valueOf(System.currentTimeMillis() - started));
		response.put("note", "시험 결과는 저장하지 않았습니다. 반영하려면 규칙 파일을 " + ruleRegistry.rulesDir().getPath()
				+ " 에 저장하고 rerunFrom=SEMANTIC 으로 다시 돌리세요.");
		return response;
	}

	/**
	 * <pre>
	 * 규칙 후보를 찾습니다. 지금 이 프로젝트에 쓰이는 규칙이 이미 보는 것은 뺍니다.
	 *   callArguments  따옴표 글자를 인자로 받는 호출 가운데, 그 글자가 이 프로젝트의 SQL 이름이나 JSP 화면 이름과 같은 것
	 *   annotations    규칙이 모르는 애노테이션 가운데 많이 붙은 것(unexplained: 계층 없는 타입, 진입점 아닌 메소드에 붙은 수)
	 *   superTypes     규칙이 모르는 상위 타입 가운데, 계층이 없거나 LOW 인 하위 타입이 많은 것
	 *   screenRules    화면 규칙(screens.calls / opens)이 보는 자리의 인자가 글자가 아니어서 건너뛴 호출 가운데,
	 *                  다른 자리에 따옴표 글자가 있던 것. 예: fnOpenPage(param, "JIMC1000G") → argIndex: 1 규칙 제안
	 *                  (SEMANTIC 이 남긴 집계를 읽으므로, 이 기능 전에 분석한 리비전은 SEMANTIC 을 다시 돌려야 나온다)
	 * 후보마다 규칙 파일에 붙여 넣을 조각(suggestion)을 함께 줍니다. 계층이나 진입점 종류는 짐작이라 꼭 고쳐서 시험해 보세요.
	 * </pre>
	 */
	public Map<String, Object> getCandidates(long revisionId, int limit) {
		Map<String, Object> revision = revisionOf(revisionId);
		Map<String, Object> project = projectService.getProject((String) revision.get("projectId"));
		SemanticRules rules = rulesFor(project, null);
		int size = Math.max(1, Math.min(limit, 100));

		List<Map<String, Object>> covered = new ArrayList<Map<String, Object>>();
		addCovered(covered, rules.getSqlArguments());
		addCovered(covered, rules.getViewArguments());
		List<Map<String, Object>> calls = ruleDao.selectCallArgumentCandidates(revisionId, covered, size);
		for (int i = 0; i < calls.size(); i++) {
			calls.get(i).put("suggestion", callArgumentSuggestion(calls.get(i)));
		}

		List<Map<String, Object>> annotations = ruleDao.selectAnnotationCandidates(revisionId, knownAnnotations(rules), IGNORED_ANNOTATIONS, size);
		for (int i = 0; i < annotations.size(); i++) {
			annotations.get(i).put("suggestion", annotationSuggestion(annotations.get(i), rules));
		}

		List<Map<String, Object>> superTypes = ruleDao.selectSuperTypeCandidates(revisionId, knownSuperTypes(rules), size);
		for (int i = 0; i < superTypes.size(); i++) {
			superTypes.get(i).put("suggestion", superTypeSuggestion(superTypes.get(i), rules));
		}

		List<Map<String, Object>> screenRules = screenRuleCandidates(revisionId);

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", Long.valueOf(revisionId));
		result.put("projectId", revision.get("projectId"));
		result.put("ruleSets", names(rules.getSets()));
		result.put("summary", ruleDao.selectCandidateSummary(revisionId));
		result.put("screenRules", screenRules);
		result.put("callArguments", calls);
		result.put("annotations", annotations);
		result.put("superTypes", superTypes);
		result.put("howTo", "suggestion 을 규칙 묶음(name 포함)으로 엮어 POST /api/revisions/" + revisionId
				+ "/rules/trial 로 시험한 뒤, " + ruleRegistry.rulesDir().getPath() + " 에 저장하고 rerunFrom=SEMANTIC 으로 다시 돌리세요.");
		return result;
	}

	/** SEMANTIC 이 남긴 화면 규칙 집계에서, 다른 자리에 글자가 있던 것에 규칙 조각을 붙입니다. 글자가 가장 많던 자리를 제안합니다. */
	private List<Map<String, Object>> screenRuleCandidates(long revisionId) {
		List<Map<String, Object>> candidates = ruleDao.selectScreenRuleCandidates(revisionId);
		for (int i = 0; i < candidates.size(); i++) {
			Map<String, Object> c = candidates.get(i);
			boolean open = "OPEN".equals(c.get("kind"));
			c.put("suggestion", "screens:\n  " + (open ? "opens" : "calls") + ":\n    - { function: " + c.get("function")
					+ ", " + (open ? "screen" : "method") + ": \"" + c.get("template") + "\", argIndex: " + c.get("literalAt") + " }\n");
		}
		return candidates;
	}

	/* ============================== 도우미 ============================== */

	private List<RuleSet> loadAll() {
		try {
			return ruleRegistry.loadAll();
		} catch (RuleException e) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "규칙 파일이 잘못 적혀 있습니다. " + e.getMessage());
		}
	}

	private SemanticRules rulesFor(Map<String, Object> project, RuleSet trial) {
		try {
			return ruleRegistry.rulesFor(project, trial);
		} catch (RuleException e) {
			throw ApiException.badRequest(e.getMessage());
		}
	}

	private Map<String, Object> revisionOf(long revisionId) {
		Map<String, Object> revision = revisionDao.selectRevision(revisionId);
		if (revision == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		return revision;
	}

	/** SEMANTIC 앞의 단계가 모두 끝난 리비전이어야 시험할 수 있습니다. */
	private void checkReadyForSemantic(long revisionId) {
		List<String> all = analysisJobRunner.passNames();
		List<String> fromSemantic = analysisJobRunner.passNamesFrom(SemanticPass.NAME);
		for (int i = 0; i < all.size(); i++) {
			String pass = all.get(i);
			if (fromSemantic.contains(pass)) {
				continue;
			}
			String status = revisionDao.selectRevisionPassStatus(revisionId, pass);
			if (!"DONE".equals(status)) {
				throw ApiException.conflict("이 리비전은 " + pass + " 단계가 끝나지 않아(" + status + ") 의미 분석을 시험할 수 없습니다.");
			}
		}
	}

	private Map<String, Object> describe(RuleSet set) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("name", set.name);
		row.put("description", set.description);
		row.put("order", Integer.valueOf(set.order));
		row.put("autoApply", Boolean.valueOf(set.autoApply));
		row.put("source", set.source);
		row.put("hash", set.hash);
		row.put("rules", Integer.valueOf(set.ruleCount()));
		return row;
	}

	private RuleSet find(List<RuleSet> sets, String name) {
		for (int i = 0; i < sets.size(); i++) {
			if (sets.get(i).name.equals(name)) {
				return sets.get(i);
			}
		}
		return null;
	}

	private List<String> names(List<RuleSet> sets) {
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < sets.size(); i++) {
			names.add(sets.get(i).name);
		}
		return names;
	}

	/** 전후가 같은 줄은 빼고 바뀐 묶음만 남깁니다. 아무것도 안 바뀌었으면 빈 목록입니다. */
	private List<Map<String, Object>> changedOnly(List<Map<String, Object>> summary) {
		List<Map<String, Object>> changed = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < summary.size(); i++) {
			Map<String, Object> row = summary.get(i);
			if (((Number) row.get("added")).longValue() > 0 || ((Number) row.get("removed")).longValue() > 0) {
				changed.add(row);
			}
		}
		return changed;
	}

	private void addCovered(List<Map<String, Object>> covered, List<RuleSet.CallArgumentRule> rules) {
		for (int i = 0; i < rules.size(); i++) {
			RuleSet.CallArgumentRule rule = rules.get(i);
			for (int m = 0; m < rule.methods.size(); m++) {
				covered.add(coveredRow("CALL", rule.methods.get(m), rule.argIndex));
			}
			for (int c = 0; c < rule.constructors.size(); c++) {
				covered.add(coveredRow("CREATE", rule.constructors.get(c), rule.argIndex));
			}
		}
	}

	private Map<String, Object> coveredRow(String kind, String name, int argIndex) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("kind", kind);
		row.put("name", name);
		row.put("argIndex", Integer.valueOf(argIndex));
		return row;
	}

	private List<String> knownAnnotations(SemanticRules rules) {
		Set<String> known = new LinkedHashSet<String>();
		List<RuleSet.HttpMappingRule> http = rules.getHttpMappings();
		for (int i = 0; i < http.size(); i++) {
			known.addAll(http.get(i).classAnnotations);
			for (int j = 0; j < http.get(i).methodAnnotations.size(); j++) {
				known.add(http.get(i).methodAnnotations.get(j).annotation);
			}
		}
		for (int i = 0; i < rules.getAnnotatedEndpoints().size(); i++) {
			known.addAll(rules.getAnnotatedEndpoints().get(i).annotations);
		}
		for (int i = 0; i < rules.getLayerAnnotations().size(); i++) {
			known.addAll(rules.getLayerAnnotations().get(i).annotations);
		}
		known.addAll(rules.getInjectAnnotations());
		known.addAll(rules.getProxyAnnotations());
		return new ArrayList<String>(known);
	}

	private List<String> knownSuperTypes(SemanticRules rules) {
		Set<String> known = new LinkedHashSet<String>();
		for (int i = 0; i < rules.getLayerSuperTypes().size(); i++) {
			known.addAll(rules.getLayerSuperTypes().get(i).types);
		}
		for (int i = 0; i < rules.getSuperTypeEndpoints().size(); i++) {
			known.addAll(rules.getSuperTypeEndpoints().get(i).superTypes);
		}
		return new ArrayList<String>(known);
	}

	private String callArgumentSuggestion(Map<String, Object> c) {
		boolean sql = ((Number) c.get("sqlMatches")).longValue() >= ((Number) c.get("viewMatches")).longValue();
		StringBuilder sb = new StringBuilder();
		sb.append("callArguments:\n");
		sb.append("  - target: ").append(sql ? "SQL" : "VIEW").append('\n');
		sb.append("    ").append("CREATE".equals(c.get("kind")) ? "constructors" : "methods").append(": [").append(c.get("name")).append("]\n");
		int argIndex = ((Number) c.get("argIndex")).intValue();
		if (argIndex != 0) {
			sb.append("    argIndex: ").append(argIndex).append('\n');
		}
		if ("CALL".equals(c.get("kind")) && c.get("receiverType") != null) {
			sb.append("    receiverTypes: [").append(c.get("receiverType")).append("]   # 이름이 흔한 메소드면 대상 타입으로 좁힌다\n");
		}
		return sb.toString();
	}

	private String annotationSuggestion(Map<String, Object> a, SemanticRules rules) {
		String name = (String) a.get("annotation");
		if ("TYPE".equals(a.get("targetKind"))) {
			return "layers:\n  annotations:\n    - { layer: " + guessLayer(name, rules) + ", annotations: [" + name + "] }\n";
		}
		return "endpoints:\n  annotations:\n    - endpointType: HANDLER   # 진입점 종류를 정해 적는다(영문 대문자)\n      annotations: [" + name + "]\n";
	}

	private String superTypeSuggestion(Map<String, Object> s, SemanticRules rules) {
		String superType = (String) s.get("superType");
		String simple = superType.substring(superType.lastIndexOf('.') + 1);
		return "layers:\n  superTypes:\n    - { layer: " + guessLayer(simple, rules) + ", types: [" + superType + "] }\n";
	}

	/** 이름 규칙(layers.names)으로 계층을 짐작합니다. 맞는 것이 없으면 고를 자리를 남깁니다(그대로 쓰면 검사에서 걸린다). */
	private String guessLayer(String name, SemanticRules rules) {
		List<RuleSet.LayerNameRule> names = rules.getLayerNames();
		for (int i = 0; i < names.size(); i++) {
			try {
				if (Pattern.compile(names.get(i).pattern).matcher(name).find()) {
					return names.get(i).layer;
				}
			} catch (RuntimeException e) {
				// PostgreSQL 에만 맞는 정규식이면 짐작하지 않는다.
			}
		}
		return "<계층을 고르세요>";
	}

}

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
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.job.AnalysisJobRunner;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticPass;
import net.dstone.knowledge.analysis.pass08.semantic.rule.ProjectRules;
import net.dstone.knowledge.analysis.pass08.semantic.rule.Rule;
import net.dstone.knowledge.analysis.pass08.semantic.rule.RuleException;
import net.dstone.knowledge.analysis.pass08.semantic.rule.RuleFile;
import net.dstone.knowledge.analysis.pass08.semantic.rule.RuleFiles;

/**
 * <pre>
 * 의미 분석 규칙(conf/rules)을 다루는 API 의 업무 로직입니다.
 *
 *   규칙 파일 목록        폴더에 있는 규칙 파일과 그 해시
 *   리비전의 규칙         그 리비전을 분석할 때 쓴 파일과 지금 파일의 비교(바뀌었으면 SEMANTIC 부터 다시 돌려야 반영된다)
 *   시험 적용(trial)      규칙 파일 글을 받아 SEMANTIC 을 한 트랜잭션 안에서 돌려 보고, 전후 차이를 알려 준 뒤 되돌린다
 *   후보 찾기             규칙이 아직 모르는 관례의 흔적(SQL 이름 같은 글자를 받는 호출, 많이 붙은 애노테이션, 많이 상속한 상위 타입)
 *
 * 새 환경의 프로젝트를 분석할 때의 순서:
 *   1) 후보를 본다 → 2) 제안된 규칙 조각을 고쳐 시험 적용으로 결과를 본다 → 3) conf/rules/projects/{프로젝트ID}/ 에 파일로 저장한다
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
	private RuleFiles ruleFiles;

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

	/** 폴더에 있는 규칙 파일 목록(우선순위 순서: projects → common → guess) */
	public Map<String, Object> getRuleSets() {
		List<RuleFile> sets = loadAll();
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < sets.size(); i++) {
			list.add(describe(sets.get(i)));
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("rulesDir", ruleFiles.rulesDir().getPath());
		result.put("ruleSets", list);
		return result;
	}

	/**
	 * <pre>
	 * 리비전을 분석할 때 쓴 규칙 파일과, 지금 이 프로젝트에 쓰일 파일을 견줍니다.
	 * status: SAME(같음) / CHANGED(파일 내용이 바뀜) / REMOVED(지금은 쓰지 않음) / ADDED(지금은 쓰는데 그때는 없었음)
	 * 하나라도 SAME 이 아니면 upToDate 가 false 이고, SEMANTIC 부터 다시 돌려야 지금 규칙이 반영됩니다.
	 * </pre>
	 */
	public Map<String, Object> getRevisionRules(long revisionId) {
		Map<String, Object> revision = revisionOf(revisionId);
		Map<String, Object> project = projectService.getProject((String) revision.get("projectId"));
		List<Map<String, Object>> applied = ruleDao.selectAppliedRuleSets(revisionId);
		List<RuleFile> current = rulesFor(project, null).getFiles();

		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		Set<String> seen = new LinkedHashSet<String>();
		boolean upToDate = !applied.isEmpty();
		for (int i = 0; i < applied.size(); i++) {
			Map<String, Object> a = applied.get(i);
			String name = (String) a.get("name");
			seen.add(name);
			RuleFile now = find(current, name);
			Map<String, Object> row = new LinkedHashMap<String, Object>();
			row.put("name", name);
			row.put("source", a.get("source"));
			row.put("appliedHash", a.get("hash"));
			row.put("currentHash", now == null ? null : now.getHash());
			String status = now == null ? "REMOVED" : now.getHash().equals(a.get("hash")) ? "SAME" : "CHANGED";
			row.put("status", status);
			upToDate = upToDate && "SAME".equals(status);
			rows.add(row);
		}
		for (int i = 0; i < current.size(); i++) {
			RuleFile now = current.get(i);
			if (!seen.contains(now.name)) {
				Map<String, Object> row = new LinkedHashMap<String, Object>();
				row.put("name", now.name);
				row.put("source", now.path);
				row.put("appliedHash", null);
				row.put("currentHash", now.getHash());
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
	 * ruleText 가 있으면 그 글을 path(규칙 폴더 안의 경로) 에 있는 파일처럼 더해 봅니다. 그 경로의 파일이 있으면 그 대신 씁니다.
	 * 없으면 지금 파일 그대로 돌려, "규칙 파일을 고쳤는데 다시 돌리면 무엇이 바뀌나" 를 봅니다.
	 *
	 * 그 프로젝트에 분석이 돌고 있으면 409 입니다. 서버 전체에서 한 번에 하나만 돕니다(synchronized).
	 * </pre>
	 */
	public synchronized Map<String, Object> trial(long revisionId, String path, String ruleText, int sampleLimit) {
		long started = System.currentTimeMillis();
		Map<String, Object> revision = revisionOf(revisionId);
		String projectId = (String) revision.get("projectId");
		Map<String, Object> project = projectService.getProject(projectId);
		checkReadyForSemantic(revisionId);
		if (analysisJobDao.countActiveJobByProject(projectId) > 0) {
			throw ApiException.conflict("이 프로젝트는 분석이 돌고 있습니다. 끝난 뒤에 시험하세요: " + projectId);
		}

		RuleFile trialFile = null;
		if (ruleText != null && ruleText.trim().length() > 0) {
			if (path == null || path.trim().length() == 0) {
				throw ApiException.badRequest("시험할 규칙 파일의 경로(path)를 적어야 합니다. 예: \"projects/" + projectId.toLowerCase() + "/backend.yml\"."
						+ " 그 경로의 파일이 있으면 시험하는 동안 그 대신 씁니다.");
			}
			try {
				trialFile = ruleFiles.parse(ruleText, path);
			} catch (RuleException e) {
				throw ApiException.badRequest(e.getMessage());
			}
		}
		final ProjectRules rules = rulesFor(project, trialFile);
		final AnalysisJobContext context = new AnalysisJobContext("TRIAL", projectId, revisionId, project);
		context.setProjectRules(rules);
		final long id = revisionId;
		final int limit = Math.max(1, Math.min(sampleLimit, MAX_SAMPLES));

		Map<String, Object> result = txTemplateCommon.execute(new TransactionCallback<Map<String, Object>>() {
			@Override
			public Map<String, Object> doInTransaction(TransactionStatus status) {
				// 무엇이 있어도 마지막에 되돌린다. 시험 적용은 아무것도 남기지 않는다.
				status.setRollbackOnly();
				ruleDao.createBeforeFacts(id);
				semanticPass.runSteps(context);
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
		response.put("trialRuleSet", trialFile == null ? null : trialFile.name);
		response.put("ruleSets", names(rules.getFiles()));
		response.putAll(result);
		response.put("elapsedMs", Long.valueOf(System.currentTimeMillis() - started));
		response.put("note", "시험 결과는 저장하지 않았습니다. 반영하려면 규칙 파일을 " + ruleFiles.rulesDir().getPath()
				+ " 에 저장하고 rerunFrom=SEMANTIC 으로 다시 돌리세요.");
		return response;
	}

	/**
	 * <pre>
	 * 규칙 후보를 찾습니다. 지금 이 프로젝트에 쓰이는 규칙이 이미 보는 것은 뺍니다.
	 *   callArguments  따옴표 글자를 인자로 받는 호출 가운데, 그 글자가 이 프로젝트의 SQL 이름이나 JSP 화면 이름과 같은 것(sql: / view: 규칙)
	 *   annotations    규칙이 모르는 애노테이션 가운데 많이 붙은 것(unexplained: 계층 없는 타입, 진입점 아닌 메소드에 붙은 수)
	 *   superTypes     규칙이 모르는 상위 타입 가운데, 계층이 없거나 LOW 인 하위 타입이 많은 것
	 *   screenRules    화면 규칙(screen: 의 call / open)이 보는 자리의 인자가 글자가 아니어서 건너뛴 호출 가운데,
	 *                  다른 자리에 따옴표 글자가 있던 것. 예: fnOpenPage(param, "JIMC1000G") → open: fnOpenPage(_, ID) 규칙 제안
	 *                  (SEMANTIC 이 남긴 집계를 읽으므로, 이 기능 전에 분석한 리비전은 SEMANTIC 을 다시 돌려야 나온다)
	 * 후보마다 규칙 파일에 붙여 넣을 조각(suggestion)을 함께 줍니다. 계층이나 진입점 종류는 짐작이라 꼭 고쳐서 시험해 보세요.
	 * </pre>
	 */
	public Map<String, Object> getCandidates(long revisionId, int limit) {
		Map<String, Object> revision = revisionOf(revisionId);
		Map<String, Object> project = projectService.getProject((String) revision.get("projectId"));
		ProjectRules rules = rulesFor(project, null);
		int size = Math.max(1, Math.min(limit, 100));

		List<Map<String, Object>> covered = new ArrayList<Map<String, Object>>();
		addCovered(covered, rules.sqlCalls());
		addCovered(covered, rules.viewCalls());
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
		String file = "conf/rules/projects/" + String.valueOf(revision.get("projectId")).toLowerCase() + "/";

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", Long.valueOf(revisionId));
		result.put("projectId", revision.get("projectId"));
		result.put("ruleSets", names(rules.getFiles()));
		result.put("summary", ruleDao.selectCandidateSummary(revisionId));
		result.put("screenRules", screenRules);
		result.put("callArguments", calls);
		result.put("annotations", annotations);
		result.put("superTypes", superTypes);
		result.put("howTo", "suggestion 을 규칙 파일 하나로 엮어 POST /api/revisions/" + revisionId
				+ "/rules/trial 에 {\"path\": \"projects/" + String.valueOf(revision.get("projectId")).toLowerCase() + "/backend.yml\", \"rules\": \"...\"} 로 시험한 뒤, "
				+ file + " 에 저장하고 rerunFrom=SEMANTIC 으로 다시 돌리세요.");
		return result;
	}

	/** SEMANTIC 이 남긴 화면 규칙 집계에서, 다른 자리에 글자가 있던 것에 규칙 조각을 붙입니다. 글자가 가장 많던 자리를 제안합니다. */
	private List<Map<String, Object>> screenRuleCandidates(long revisionId) {
		List<Map<String, Object>> candidates = ruleDao.selectScreenRuleCandidates(revisionId);
		for (int i = 0; i < candidates.size(); i++) {
			Map<String, Object> c = candidates.get(i);
			boolean open = "OPEN".equals(c.get("kind"));
			c.put("suggestion", "screen:\n  - " + (open ? "open: " : "call: ") + c.get("function") + "(" + argumentsWithIdAt(((Number) c.get("literalAt")).intValue())
					+ ")\n    to: \"" + String.valueOf(c.get("template")).replace("{id}", "{ID}") + "\"\n");
		}
		return candidates;
	}

	/* ============================== 도우미 ============================== */

	/** ID 가 n번째 인자인 호출의 인자 모양. 2 → "_, _, ID" */
	private static String argumentsWithIdAt(int index) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < index; i++) {
			sb.append("_, ");
		}
		return sb.append("ID").toString();
	}

	private List<RuleFile> loadAll() {
		try {
			return ruleFiles.loadAll();
		} catch (RuleException e) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "규칙 파일이 잘못 적혀 있습니다. " + e.getMessage());
		}
	}

	private ProjectRules rulesFor(Map<String, Object> project, RuleFile trial) {
		try {
			return ruleFiles.rulesFor((String) project.get("projectId"), project.get("ruleSets"), trial);
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

	private Map<String, Object> describe(RuleFile file) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("name", file.name);
		row.put("description", file.description);
		row.put("tier", file.tier.name());
		row.put("project", file.projectFolder);
		row.put("source", file.path);
		row.put("hash", file.getHash());
		row.put("rules", Integer.valueOf(file.ruleCount()));
		return row;
	}

	private RuleFile find(List<RuleFile> sets, String name) {
		for (int i = 0; i < sets.size(); i++) {
			if (sets.get(i).name.equals(name)) {
				return sets.get(i);
			}
		}
		return null;
	}

	private List<String> names(List<RuleFile> sets) {
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < sets.size(); i++) {
			names.add(sets.get(i).name);
		}
		return names;
	}

	/** 전후가 같은 줄은 빼고 바뀐 것만 남깁니다. 아무것도 안 바뀌었으면 빈 목록입니다. */
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

	private void addCovered(List<Map<String, Object>> covered, List<Rule.CallArgument> rules) {
		for (int i = 0; i < rules.size(); i++) {
			Rule.CallArgument rule = rules.get(i);
			covered.add(coveredRow(rule.refKind(), rule.callName(), rule.argIndex));
		}
	}

	private Map<String, Object> coveredRow(String kind, String name, int argIndex) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("kind", kind);
		row.put("name", name);
		row.put("argIndex", Integer.valueOf(argIndex));
		return row;
	}

	private List<String> knownAnnotations(ProjectRules rules) {
		Set<String> known = new LinkedHashSet<String>();
		List<Rule.HttpEntry> http = rules.httpEntries();
		for (int i = 0; i < http.size(); i++) {
			known.addAll(http.get(i).annotationNames());
		}
		for (int i = 0; i < rules.annotationEntries().size(); i++) {
			known.add(rules.annotationEntries().get(i).annotation);
		}
		List<Rule.Layer> layers = rules.layers();
		for (int i = 0; i < layers.size(); i++) {
			if (Rule.Layer.BY_ANNOTATION.equals(layers.get(i).by)) {
				known.add(layers.get(i).value);
			}
		}
		for (int i = 0; i < rules.injects().size(); i++) {
			known.add(rules.injects().get(i).annotation);
		}
		for (int i = 0; i < rules.proxies().size(); i++) {
			known.add(rules.proxies().get(i).annotation);
		}
		return new ArrayList<String>(known);
	}

	private List<String> knownSuperTypes(ProjectRules rules) {
		Set<String> known = new LinkedHashSet<String>();
		List<Rule.Layer> layers = rules.layers();
		for (int i = 0; i < layers.size(); i++) {
			if (Rule.Layer.BY_EXTENDS.equals(layers.get(i).by)) {
				known.add(layers.get(i).value);
			}
		}
		for (int i = 0; i < rules.extendsEntries().size(); i++) {
			known.add(rules.extendsEntries().get(i).superType);
		}
		return new ArrayList<String>(known);
	}

	/** 후보 호출 하나를 sql: / view: 규칙 한 줄로 */
	private String callArgumentSuggestion(Map<String, Object> c) {
		boolean sql = ((Number) c.get("sqlMatches")).longValue() >= ((Number) c.get("viewMatches")).longValue();
		String slot = sql ? "SQL" : "VIEW";
		int argIndex = ((Number) c.get("argIndex")).intValue();
		StringBuilder args = new StringBuilder();
		for (int i = 0; i < argIndex; i++) {
			args.append("_, ");
		}
		args.append(slot);
		String call;
		if ("CREATE".equals(c.get("kind"))) {
			call = "new " + c.get("name") + "(" + args + ")";
		} else if (c.get("receiverType") != null) {
			call = c.get("receiverType") + "#" + c.get("name") + "(" + args + ")";
		} else {
			call = "_." + c.get("name") + "(" + args + ")";
		}
		return (sql ? "sql" : "view") + ":\n  - " + call
				+ ("CALL".equals(c.get("kind")) && c.get("receiverType") != null ? "   # 이름이 흔한 메소드라 대상 타입으로 좁혔다. 아무 객체면 _." + c.get("name") + "(...)" : "")
				+ "\n";
	}

	private String annotationSuggestion(Map<String, Object> a, ProjectRules rules) {
		String name = (String) a.get("annotation");
		if ("TYPE".equals(a.get("targetKind"))) {
			return "layer:\n  " + guessLayer(name, rules) + ": \"@" + name + "\"\n";
		}
		return "entry:\n  HANDLER: \"@" + name + "\"   # 진입점 종류(HANDLER)를 정해 적는다(영문 대문자)\n";
	}

	private String superTypeSuggestion(Map<String, Object> s, ProjectRules rules) {
		String superType = (String) s.get("superType");
		String simple = superType.substring(superType.lastIndexOf('.') + 1);
		return "layer:\n  " + guessLayer(simple, rules) + ": extends " + superType + "\n";
	}

	/** 이름 규칙(layer 의 name ...)으로 계층을 짐작합니다. 맞는 것이 없으면 고를 자리를 남깁니다(그대로 쓰면 검사에서 걸린다). */
	private String guessLayer(String name, ProjectRules rules) {
		List<Rule.Layer> layers = rules.layers();
		for (int i = 0; i < layers.size(); i++) {
			Rule.Layer rule = layers.get(i);
			if (Rule.Layer.BY_NAME.equals(rule.by) && Pattern.compile(rule.nameRegex).matcher(name).matches()) {
				return rule.layer;
			}
		}
		return "<계층을 고르세요>";
	}

}

package net.dstone.ai.runtime.eval;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.api.dto.EvalCaseDraftRequest;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.project.ProjectStore;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.ai.runtime.workflow.file.WorkFlowFileEntry;
import net.dstone.ai.runtime.workflow.file.WorkFlowFileStore;
import net.dstone.ai.runtime.workflow.snapshot.StepSnapshotRecorder;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 실행 기록으로 평가 사례(conf/eval/{프로젝트ID}/{id}.eval.yml + 자료 폴더)를 만듭니다.
 * 화면: 실행 관리 > 진행 기록 > [평가 사례로 만들기](Agent 사례), 실행 머리의 [Workflow 평가 사례로 만들기].
 *
 * Agent 사례(step 스냅숏 한 회차로 만듦)
 *   - input       : 스냅숏의 input.json. 작업 폴더 경로는 ${workDir}, 프로젝트 소스 폴더는 ${param.sourceDir},
 *                   프로젝트 ID는 ${param.projectId}로 바꿉니다. 길거나 여러 줄인 값은 자료 폴더 input/{이름}.txt로 빼고
 *                   ${fixture:input/{이름}.txt}로 읽게 합니다.
 *   - work/       : 고른 입력 폴더를 step이 시작할 때 복사해 둔 것(스냅숏 inputs/{이름}/)
 *   - 미리 채점    : 작업 폴더에 step이 끝났을 때 쓴 파일(files/after)을 덮고, 스냅숏의 답·Tool 횟수로 checks를 돌립니다.
 *
 * Workflow 사례(실행 하나로 만듦)
 *   - input       : Workflow input. 요청 폴더 경로는 ${requestDir}로 바꿉니다
 *   - work/       : 실행을 시작할 때 남긴 요청 폴더(스냅숏 _start). 스냅숏이 없는 지난 실행이면 지금 요청 폴더에서
 *                   이 실행이 새로 만든 파일을 뺀 것으로 대신하고 경고를 붙입니다(원래 있던 파일을 고친 것은 되돌리지 못함)
 *   - approvals   : 실행 기록의 사람 결정(같은 step을 여러 번 정했으면 마지막 것)
 *   - 미리 채점    : 지금 요청 폴더와 지금 state로 checks를 돌립니다(Tool 횟수는 보지 않음)
 *
 * http·dbQuery는 격리 시험 서버가 있어야 해서 미리 채점하지 않습니다(ok = null).
 * 엔진은 어떤 Workflow·Agent·대상 앱인지 모릅니다. 입력 값 가운데 경로처럼 보이는 것과 프로젝트 관리의 값만 봅니다.
 * </pre>
 */
@Service
public class EvalCaseBuilder extends BaseObject {

	/** 이 글자 수를 넘거나 여러 줄인 입력 값은 자료 파일로 뺍니다. */
	private static final int INLINE_MAX = 300;

	/** 화면에 미리 보일 답의 최대 글자 수 */
	private static final int ANSWER_PREVIEW_CHARS = 20000;

	/** 사례 id 규칙(EvalCaseLoader와 같음) */
	private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private StepSnapshotRecorder snapshots;

	@Autowired
	private WorkFlowExecutionStore executionStore;

	@Autowired
	private WorkFlowFileStore fileStore;

	@Autowired
	private EvalCaseLoader loader;

	@Autowired
	private AgentRegistry agentRegistry;

	@Autowired
	private ProjectStore projectStore;

	@Autowired
	private Environment environment;

	/* ---------------------------------------------------------------------------------------------
	 * 초안(화면이 대화상자를 채울 값)
	 * ------------------------------------------------------------------------------------------- */

	/**
	 * 실행 하나의 step 스냅숏 목록입니다(진행 기록의 줄과 짝을 맞출 때 씁니다).
	 *
	 * @param executionId 실행 id
	 */
	public List<Map<String, Object>> snapshots(String executionId) {
		WorkFlowExecution execution = this.execution(executionId);
		return this.snapshots.list(execution.workflowId(), executionId);
	}

	/**
	 * Agent 사례의 초안입니다.
	 *
	 * @param executionId 실행 id
	 * @param snapshot    스냅숏 이름(예: step30-1)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> stepDraft(String executionId, String snapshot) throws IOException {
		WorkFlowExecution execution = this.execution(executionId);
		Path dir = this.stepSnapshot(execution, snapshot);
		Map<String, Object> manifest = readMap(dir.resolve(StepSnapshotRecorder.MANIFEST_FILE));
		String agentId = String.valueOf(manifest.get("agent"));
		String stepId = String.valueOf(manifest.get("stepId"));

		Map<String, Object> draft = new LinkedHashMap<String, Object>();
		draft.put("kind", "agent");
		draft.put("executionId", executionId);
		draft.put("workflowId", execution.workflowId());
		draft.put("snapshot", snapshot);
		draft.put("stepId", stepId);
		draft.put("round", manifest.get("round"));
		draft.put("agent", agentId);
		draft.put("success", manifest.get("success"));
		draft.put("error", manifest.get("error"));
		draft.put("startedAt", manifest.get("startedAt"));
		draft.put("endedAt", manifest.get("endedAt"));
		draft.put("truncated", manifest.get("truncated"));
		draft.put("suggestedId", suggestId(agentId.replaceAll("-agent$", "") + "-" + stepId));
		draft.put("input", readJson(dir.resolve(StepSnapshotRecorder.INPUT_FILE)));
		draft.put("inputs", manifest.get("inputs"));
		draft.put("workKey", firstCopiedDir((List<Map<String, Object>>) manifest.get("inputs")));
		draft.put("files", manifest.get("files"));
		Object output = readJson(dir.resolve(StepSnapshotRecorder.OUTPUT_FILE));
		draft.put("answer", shorten(output));
		draft.put("outputFields", this.outputFields(agentId));
		Map<String, Object> tools = readMap(dir.resolve(StepSnapshotRecorder.TOOLS_FILE));
		draft.put("tools", tools.get("counts"));
		Map<String, Object> decision = nextDecision(execution, String.valueOf(manifest.get("endedAt")));
		draft.put("nextDecision", decision);
		draft.put("suggestedDescription", decision == null || decision.get("comment") == null ? "" : String.valueOf(decision.get("comment")).strip());
		return draft;
	}

	/**
	 * Workflow 사례의 초안입니다.
	 *
	 * @param executionId 실행 id
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> executionDraft(String executionId) throws IOException {
		WorkFlowExecution execution = this.execution(executionId);
		WorkSource source = this.workflowSource(execution);
		Map<String, Object> draft = new LinkedHashMap<String, Object>();
		draft.put("kind", "workflow");
		draft.put("executionId", executionId);
		draft.put("workflowId", execution.workflowId());
		draft.put("suggestedId", suggestId(execution.workflowId().replaceAll("-workflow$", "") + "-flow"));
		draft.put("input", execution.context().get("input"));
		draft.put("requestDir", source.originalPath);
		draft.put("startSnapshot", Boolean.valueOf(source.fromSnapshot));
		draft.put("warnings", source.warnings);
		draft.put("approvals", approvalsOf(execution));
		List<Map<String, Object>> decisions = new ArrayList<Map<String, Object>>();
		for (Map<String, Object> decision : WorkFlowContext.decisions(execution.context())) {
			decisions.add(decision);
		}
		draft.put("decisions", decisions);
		String lastComment = "";
		for (Map<String, Object> decision : decisions) {
			if ("APPROVAL".equals(decision.get("kind")) && decision.get("comment") != null && !String.valueOf(decision.get("comment")).isBlank()) {
				lastComment = String.valueOf(decision.get("comment")).strip();
			}
		}
		draft.put("suggestedDescription", lastComment);
		draft.put("answer", shorten(WorkFlowContext.state(execution.context())));
		Map<String, Object> state = WorkFlowContext.state(execution.context());
		draft.put("outputFields", new ArrayList<String>(state.keySet()));
		draft.put("files", this.currentFiles(source.originalPath));
		return draft;
	}

	/* ---------------------------------------------------------------------------------------------
	 * 미리 채점, 저장
	 * ------------------------------------------------------------------------------------------- */

	/**
	 * <pre>
	 * 사례를 만들어 보고(저장하지 않음) 그때 결과로 채점합니다. 돌려주는 값: {yaml, checks:[{name, kind, ok, detail}], warnings}
	 * ok가 null이면 미리 채점할 수 없는 종류입니다.
	 * </pre>
	 *
	 * @param request 화면이 보낸 값
	 */
	public Map<String, Object> preview(EvalCaseDraftRequest request) throws IOException {
		Plan plan = this.plan(request);
		EvalCase evalCase = this.loader.parse(plan.casePath, plan.yaml);
		Path previewDir = this.previewRoot().resolve(UUID.randomUUID().toString());
		try {
			Path fixtureDir = previewDir.resolve("fixture");
			this.writeFixture(plan, fixtureDir);
			EvalWorkspace workspace = new EvalWorkspace(evalCase, fixtureDir, previewDir.resolve("request").resolve(evalCase.id()), plan.params);
			workspace.prepare();
			this.overlayResult(plan, workspace.workDir());
			List<Map<String, Object>> results = workspace.check(plan.answer, plan.toolCounts, null);
			for (Map<String, Object> result : results) {
				String kind = String.valueOf(result.get("kind"));
				if (EvalCase.Check.HTTP.equals(kind) || EvalCase.Check.DB_QUERY.equals(kind)) {
					result.put("ok", null);
					result.put("detail", "격리 시험 서버가 있어야 해서 미리 채점하지 않습니다. 저장한 뒤 Agent 평가에서 돌려 보세요");
				} else if (plan.workflow && "toolUsed".equals(kind)) {
					result.put("ok", null);
					result.put("detail", "Workflow 사례는 Tool 횟수를 미리 채점하지 않습니다");
				}
			}
			Map<String, Object> answer = new LinkedHashMap<String, Object>();
			answer.put("yaml", plan.yaml);
			answer.put("casePath", plan.casePath);
			answer.put("checks", results);
			answer.put("warnings", plan.warnings);
			return answer;
		} finally {
			deleteQuietly(previewDir);
		}
	}

	/**
	 * <pre>
	 * 사례 파일과 자료 폴더를 conf/eval/{프로젝트ID}/ 아래에 씁니다. 같은 id가 있으면 쓰지 않습니다.
	 * 돌려주는 값: {casePath, fixture, yaml, warnings}
	 * </pre>
	 *
	 * @param request 화면이 보낸 값
	 */
	public Map<String, Object> save(EvalCaseDraftRequest request) throws IOException {
		Plan plan = this.plan(request);
		this.loader.parse(plan.casePath, plan.yaml);
		for (EvalCaseLoader.Loaded loaded : this.loader.loadAll()) {
			if (loaded.evalCase() != null && plan.id.equals(loaded.evalCase().id())) {
				throw new IllegalArgumentException("같은 id의 사례가 이미 있습니다(" + loaded.path() + "). 다른 id를 쓰세요.");
			}
		}
		Path root = this.loader.root();
		Path caseFile = root.resolve(plan.casePath).normalize();
		Path fixtureDir = root.resolve(plan.fixture).normalize();
		if (!caseFile.startsWith(root) || !fixtureDir.startsWith(root)) {
			throw new IllegalArgumentException("사례 경로가 사례 폴더 밖을 가리킵니다.");
		}
		if (Files.exists(caseFile) || Files.exists(fixtureDir)) {
			throw new IllegalArgumentException("이미 있는 파일입니다(" + plan.casePath + " 또는 " + plan.fixture + "/). 다른 id를 쓰세요.");
		}
		this.writeFixture(plan, fixtureDir);
		Files.createDirectories(caseFile.getParent());
		Files.writeString(caseFile, plan.yaml, StandardCharsets.UTF_8);
		Map<String, Object> answer = new LinkedHashMap<String, Object>();
		answer.put("casePath", plan.casePath);
		answer.put("fixture", plan.fixture);
		answer.put("yaml", plan.yaml);
		answer.put("warnings", plan.warnings);
		return answer;
	}

	/* ---------------------------------------------------------------------------------------------
	 * 사례 만들기
	 * ------------------------------------------------------------------------------------------- */

	/** 만들 사례의 모든 값입니다. */
	private static final class Plan {
		String id;
		String casePath;
		String fixture;
		boolean workflow;
		String yaml;
		Map<String, String> params = new LinkedHashMap<String, String>();
		/** 자료 폴더에 쓸 글자 파일(자료 폴더 기준 경로 → 내용) */
		Map<String, String> textFiles = new LinkedHashMap<String, String>();
		/** work/로 복사할 원본(없으면 null) */
		Path workSource;
		/** work/로 복사하지 않을 파일(workSource 기준 상대 경로) */
		Set<String> workExclude = new HashSet<String>();
		/** 그때의 작업 폴더 경로(글자 파일 안의 이 경로를 자리로 바꿉니다) */
		String workPath;
		/** 작업 폴더 자리 이름: ${workDir}(Agent) 또는 ${requestDir}(Workflow) */
		String workToken;
		/** 미리 채점용: 그때의 답, Tool 횟수 */
		Object answer;
		Map<String, Integer> toolCounts = new LinkedHashMap<String, Integer>();
		/** 미리 채점용: 작업 폴더 기준 경로 → 끝난 뒤 내용 파일(null이면 지워짐) */
		Map<String, Path> after = new LinkedHashMap<String, Path>();
		/** 미리 채점용(Workflow): 작업 폴더 위에 통째로 덮을 지금 폴더 */
		Path afterTree;
		List<String> warnings = new ArrayList<String>();
	}

	@SuppressWarnings("unchecked")
	private Plan plan(EvalCaseDraftRequest request) throws IOException {
		if (request == null || isBlank(request.executionId())) {
			throw new IllegalArgumentException("executionId가 없습니다.");
		}
		String id = request.id() == null ? "" : request.id().strip();
		if (!ID.matcher(id).matches()) {
			throw new IllegalArgumentException("사례 id '" + id + "'는 영문 소문자·숫자·하이픈만 씁니다(예: verifier-step35-20261124).");
		}
		String projectId = request.projectId() == null ? "" : request.projectId().strip();
		if (!projectId.matches("[A-Za-z0-9_-]+")) {
			throw new IllegalArgumentException("프로젝트를 고르세요(사례는 conf/eval/{프로젝트ID}/ 아래에 둡니다).");
		}
		if (request.checks() == null || request.checks().isEmpty()) {
			throw new IllegalArgumentException("채점 기준을 하나 이상 넣으세요.");
		}
		WorkFlowExecution execution = this.execution(request.executionId());
		Map<String, Object> project = this.projectStore.find(projectId);
		String sourceDir = project == null || project.get("sourceDir") == null ? null : String.valueOf(project.get("sourceDir"));

		Plan plan = new Plan();
		plan.id = id;
		plan.casePath = projectId + "/" + id + EvalCaseLoader.SUFFIX;
		plan.fixture = projectId + "/" + id;
		plan.workflow = isBlank(request.snapshot());
		if (project == null) {
			plan.warnings.add("프로젝트 관리에 '" + projectId + "'가 없어 소스 폴더를 ${param.sourceDir}로 바꾸지 않았습니다.");
		}

		Map<String, Object> root = new LinkedHashMap<String, Object>();
		root.put("id", id);
		root.put("description", request.description() == null ? "" : request.description().strip());
		Object input;
		String header;
		if (plan.workflow) {
			plan.workToken = "${requestDir}";
			WorkSource source = this.workflowSource(execution);
			plan.workSource = source.dir;
			plan.workExclude = source.exclude;
			plan.workPath = source.originalPath;
			plan.warnings.addAll(source.warnings);
			if (source.originalPath != null) {
				plan.afterTree = Path.of(source.originalPath);
			}
			root.put("workflow", execution.workflowId());
			input = execution.context().get("input");
			plan.answer = WorkFlowContext.state(execution.context());
			header = "# 실행 " + execution.executionId() + " 의 Workflow " + execution.workflowId() + " 을 처음부터 다시 돌리는 사례입니다(실행 관리 > [Workflow 평가 사례로 만들기]).\n";
		} else {
			plan.workToken = "${workDir}";
			Path dir = this.stepSnapshot(execution, request.snapshot());
			Map<String, Object> manifest = readMap(dir.resolve(StepSnapshotRecorder.MANIFEST_FILE));
			root.put("agent", String.valueOf(manifest.get("agent")));
			input = readJson(dir.resolve(StepSnapshotRecorder.INPUT_FILE));
			plan.answer = readJson(dir.resolve(StepSnapshotRecorder.OUTPUT_FILE));
			Map<String, Object> tools = readMap(dir.resolve(StepSnapshotRecorder.TOOLS_FILE));
			if (tools.get("counts") instanceof Map<?, ?> counts) {
				for (Map.Entry<String, Object> entry : ((Map<String, Object>) counts).entrySet()) {
					plan.toolCounts.put(entry.getKey(), Integer.valueOf(String.valueOf(entry.getValue())));
				}
			}
			if (!isBlank(request.workKey())) {
				Map<String, Object> picked = null;
				for (Map<String, Object> item : (List<Map<String, Object>>) manifest.get("inputs")) {
					if (request.workKey().equals(item.get("key"))) {
						picked = item;
					}
				}
				if (picked == null || !"dir".equals(picked.get("type"))) {
					throw new IllegalArgumentException("입력 '" + request.workKey() + "'는 폴더가 아니어서 작업 폴더로 쓸 수 없습니다.");
				}
				if (!Boolean.TRUE.equals(picked.get("copied"))) {
					throw new IllegalArgumentException("입력 '" + request.workKey() + "' 폴더는 스냅숏에 복사되지 않았습니다 - " + picked.get("reason"));
				}
				plan.workSource = dir.resolve(String.valueOf(picked.get("stored")));
				plan.workPath = String.valueOf(picked.get("path"));
				for (Map<String, Object> file : (List<Map<String, Object>>) manifest.get("files")) {
					String rel = relativeTo(plan.workPath, String.valueOf(file.get("path")));
					if (rel == null || !Boolean.TRUE.equals(file.get("write"))) {
						continue;
					}
					plan.after.put(rel, file.get("after") == null ? null : dir.resolve(String.valueOf(file.get("after"))));
				}
			} else {
				plan.warnings.add("작업 폴더를 고르지 않아 work/ 없이 만듭니다. 파일 채점(file…)은 작업 폴더가 있어야 의미가 있습니다.");
			}
			if (Boolean.TRUE.equals(manifest.get("truncated"))) {
				plan.warnings.add("이 스냅숏은 용량 상한에 걸려 일부 파일 내용이 빠져 있습니다(manifest.json의 skipped).");
			}
			header = "# 실행 " + execution.executionId() + " " + manifest.get("stepId") + "(" + manifest.get("round") + "회차, " + shortTime(manifest.get("startedAt"))
				+ ")의 " + manifest.get("agent") + " 입력을 뜬 사례입니다(실행 관리 > 진행 기록 > [평가 사례로 만들기]).\n";
		}

		root.put("fixture", plan.fixture);
		Object rewritten = this.rewriteInput(plan, input, sourceDir, projectId);
		if (!plan.params.isEmpty()) {
			root.put("params", plan.params);
		}
		root.put("input", rewritten);
		if (plan.workflow) {
			Map<String, String> approvals = request.approvals() != null && !request.approvals().isEmpty() ? request.approvals() : approvalsOf(execution);
			if (!approvals.isEmpty()) {
				root.put("approvals", approvals);
			}
		}
		List<Map<String, Object>> checks = new ArrayList<Map<String, Object>>();
		for (Map<String, Object> raw : request.checks()) {
			String kind = raw.get("kind") == null ? "" : String.valueOf(raw.get("kind"));
			if (kind.isBlank()) {
				throw new IllegalArgumentException("채점 기준에 종류(kind)가 없습니다: " + raw);
			}
			Map<String, Object> check = new LinkedHashMap<String, Object>();
			check.put("name", raw.get("name") == null || String.valueOf(raw.get("name")).isBlank() ? kind : String.valueOf(raw.get("name")).strip());
			check.put(kind, raw.get("value"));
			checks.add(check);
		}
		root.put("checks", checks);

		StringBuilder yaml = new StringBuilder(header);
		yaml.append("# 만든 날: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append('\n');
		if (!isBlank(request.note())) {
			for (String line : request.note().strip().split("\\r?\\n")) {
				yaml.append("# ").append(line).append('\n');
			}
		}
		yaml.append(dump(root));
		plan.yaml = yaml.toString();
		return plan;
	}

	/**
	 * <pre>
	 * 입력 값의 환경 경로를 자리로 바꿉니다. 맵이면 맨 위 칸마다, 글자 하나면 그 글자를 봅니다.
	 *   작업 폴더(요청 폴더) 경로 → ${workDir}(${requestDir})
	 *   프로젝트 소스 폴더       → ${param.sourceDir}
	 *   프로젝트 ID와 같은 값    → ${param.projectId}
	 * 길거나 여러 줄인 글자는 자료 폴더 input/{이름}.txt로 빼고 ${fixture:…}로 읽습니다.
	 * </pre>
	 */
	@SuppressWarnings("unchecked")
	private Object rewriteInput(Plan plan, Object input, String sourceDir, String projectId) {
		if (input instanceof Map<?, ?> map) {
			Map<String, Object> result = new LinkedHashMap<String, Object>();
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
				result.put(entry.getKey(), this.rewriteValue(plan, entry.getKey(), entry.getValue(), sourceDir, projectId));
			}
			return result;
		}
		return this.rewriteValue(plan, "input", input, sourceDir, projectId);
	}

	private Object rewriteValue(Plan plan, String key, Object value, String sourceDir, String projectId) {
		if (!(value instanceof String text)) {
			return value;
		}
		if (text.equals(projectId)) {
			plan.params.put("projectId", projectId);
			return "${param.projectId}";
		}
		String result = this.replacePaths(plan, text, sourceDir);
		if (result.length() > INLINE_MAX || result.indexOf('\n') >= 0) {
			String file = "input/" + key.replaceAll("[^A-Za-z0-9._-]", "_") + ".txt";
			plan.textFiles.put(file, result);
			return "${fixture:" + file + "}";
		}
		return result;
	}

	/** 글자 안의 작업 폴더 경로와 소스 폴더 경로를 자리로 바꿉니다(긴 경로부터, 슬래시·역슬래시 모양 모두). */
	private String replacePaths(Plan plan, String text, String sourceDir) {
		String result = text;
		boolean workFirst = sourceDir == null || plan.workPath == null || plan.workPath.length() >= sourceDir.length();
		if (workFirst) {
			result = replacePath(result, plan.workPath, plan.workToken);
		}
		if (sourceDir != null && !sourceDir.isBlank()) {
			String replaced = replacePath(result, sourceDir, "${param.sourceDir}");
			if (!replaced.equals(result)) {
				plan.params.put("sourceDir", sourceDir.replace('\\', '/'));
				result = replaced;
			}
		}
		if (!workFirst) {
			result = replacePath(result, plan.workPath, plan.workToken);
		}
		return result;
	}

	/** path(끝의 / 없이)를 token으로 바꿉니다. 드라이브 문자가 있는 경로는 대소문자를 가리지 않습니다. */
	static String replacePath(String text, String path, String token) {
		if (text == null || path == null || path.isBlank()) {
			return text;
		}
		String slash = path.strip().replace('\\', '/');
		while (slash.endsWith("/") && slash.length() > 1) {
			slash = slash.substring(0, slash.length() - 1);
		}
		String back = slash.replace('/', '\\');
		int flags = slash.matches("^[A-Za-z]:.*") ? Pattern.CASE_INSENSITIVE : 0;
		String result = text;
		// 경로 바로 뒤가 글자·숫자면(다른 폴더 이름의 앞부분) 바꾸지 않는다
		result = Pattern.compile(Pattern.quote(slash) + "(?![A-Za-z0-9_.-])", flags).matcher(result).replaceAll(Matcher.quoteReplacement(token));
		if (!back.equals(slash)) {
			result = Pattern.compile(Pattern.quote(back) + "(?![A-Za-z0-9_.-])", flags).matcher(result).replaceAll(Matcher.quoteReplacement(token));
		}
		return result;
	}

	/** 자료 폴더를 씁니다: input/*.txt, work/(글자 파일 안의 작업 폴더 경로는 자리로 바꿈). */
	private void writeFixture(final Plan plan, Path fixtureDir) throws IOException {
		Files.createDirectories(fixtureDir);
		for (Map.Entry<String, String> entry : plan.textFiles.entrySet()) {
			Path file = fixtureDir.resolve(entry.getKey());
			Files.createDirectories(file.getParent());
			Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
		}
		if (plan.workSource == null || !Files.isDirectory(plan.workSource)) {
			return;
		}
		final Path from = plan.workSource;
		final Path to = fixtureDir.resolve("work");
		Files.createDirectories(to);
		Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String rel = from.relativize(file).toString().replace('\\', '/');
				if (plan.workExclude.contains(rel)) {
					return FileVisitResult.CONTINUE;
				}
				Path target = to.resolve(rel);
				Files.createDirectories(target.getParent());
				byte[] bytes = Files.readAllBytes(file);
				String text = utf8OrNull(bytes);
				if (text == null || plan.workPath == null) {
					Files.write(target, bytes);
				} else {
					Files.writeString(target, replacePath(text, plan.workPath, plan.workToken), StandardCharsets.UTF_8);
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** 미리 채점용: 작업 폴더에 "그때 step이 끝났을 때"의 파일을 덮습니다. */
	private void overlayResult(Plan plan, final Path workDir) throws IOException {
		for (Map.Entry<String, Path> entry : plan.after.entrySet()) {
			Path target = workDir.resolve(entry.getKey());
			if (entry.getValue() == null) {
				Files.deleteIfExists(target);
				continue;
			}
			Files.createDirectories(target.getParent());
			Files.copy(entry.getValue(), target, StandardCopyOption.REPLACE_EXISTING);
		}
		if (plan.afterTree != null && Files.isDirectory(plan.afterTree)) {
			final Path from = plan.afterTree;
			Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Path target = workDir.resolve(from.relativize(file).toString());
					Files.createDirectories(target.getParent());
					Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}

	/* ---------------------------------------------------------------------------------------------
	 * Workflow 사례의 요청 폴더
	 * ------------------------------------------------------------------------------------------- */

	/** Workflow 사례의 work/ 원본입니다. */
	private static final class WorkSource {
		Path dir;
		Set<String> exclude = new HashSet<String>();
		String originalPath;
		boolean fromSnapshot;
		List<String> warnings = new ArrayList<String>();
	}

	@SuppressWarnings("unchecked")
	private WorkSource workflowSource(WorkFlowExecution execution) throws IOException {
		WorkSource source = new WorkSource();
		Object input = execution.context().get("input");
		Path start = this.snapshots.find(execution.workflowId(), execution.executionId(), StepSnapshotRecorder.START);
		if (start != null) {
			Map<String, Object> manifest = readMap(start.resolve(StepSnapshotRecorder.MANIFEST_FILE));
			for (Map<String, Object> item : (List<Map<String, Object>>) manifest.get("inputs")) {
				if (!"dir".equals(item.get("type"))) {
					continue;
				}
				source.originalPath = String.valueOf(item.get("path"));
				if (Boolean.TRUE.equals(item.get("copied"))) {
					source.dir = start.resolve(String.valueOf(item.get("stored")));
					source.fromSnapshot = true;
				} else {
					source.warnings.add("실행 시작 때 요청 폴더가 너무 커서 복사하지 않았습니다 - " + item.get("reason"));
				}
				break;
			}
		}
		if (source.dir == null) {
			String path = input instanceof String text ? text.strip() : null;
			if (path == null && input instanceof Map<?, ?> map) {
				for (Object value : ((Map<String, Object>) map).values()) {
					if (value instanceof String text && new File(text.strip()).isDirectory()) {
						path = text.strip();
						break;
					}
				}
			}
			if (path == null || !new File(path).isDirectory()) {
				source.warnings.add("Workflow input에서 요청 폴더를 찾지 못해 work/ 없이 만듭니다.");
				return source;
			}
			source.originalPath = path.replace('\\', '/');
			source.dir = Path.of(path);
			List<String> modified = new ArrayList<String>();
			for (WorkFlowFileEntry entry : this.fileStore.list(execution.executionId())) {
				String rel = relativeTo(source.originalPath, entry.path());
				if (rel == null || !"WRITE".equals(entry.action())) {
					continue;
				}
				if (Boolean.TRUE.equals(entry.created())) {
					source.exclude.add(rel);
				} else if (!modified.contains(rel)) {
					modified.add(rel);
				}
			}
			source.warnings.add("이 실행에는 시작 스냅숏이 없어(스냅숏 기능 이전 실행) 지금 요청 폴더에서 이 실행이 새로 만든 파일 " + source.exclude.size()
				+ "개를 빼고 만듭니다. 파일 Tool을 거치지 않고 생긴 파일(빌드 결과 등)은 빠지지 않을 수 있습니다.");
			if (!modified.isEmpty()) {
				source.warnings.add("원래 있던 파일을 이 실행이 고쳤습니다. 고친 뒤의 내용이 들어갑니다: " + modified);
			}
		}
		return source;
	}

	/** 실행 기록의 사람 결정으로 approvals를 만듭니다(같은 step은 마지막 결정). */
	private static Map<String, String> approvalsOf(WorkFlowExecution execution) {
		Map<String, String> approvals = new LinkedHashMap<String, String>();
		for (Map<String, Object> decision : WorkFlowContext.decisions(execution.context())) {
			if ("APPROVAL".equals(decision.get("kind")) && decision.get("step") != null && decision.get("decision") != null) {
				approvals.put(String.valueOf(decision.get("step")), String.valueOf(decision.get("decision")));
			}
		}
		return approvals;
	}

	/** 지금 요청 폴더의 파일 목록(요청 폴더 기준 경로)입니다. 너무 많으면 앞의 500개만입니다. */
	private List<String> currentFiles(String dir) throws IOException {
		final List<String> result = new ArrayList<String>();
		if (dir == null || !new File(dir).isDirectory()) {
			return result;
		}
		final Path from = Path.of(dir);
		Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				result.add(from.relativize(file).toString().replace('\\', '/'));
				return result.size() >= 500 ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
			}
		});
		return result;
	}

	/* ---------------------------------------------------------------------------------------------
	 * 도우미
	 * ------------------------------------------------------------------------------------------- */

	private WorkFlowExecution execution(String executionId) {
		WorkFlowExecution execution = this.executionStore.find(executionId);
		if (execution == null) {
			throw new IllegalArgumentException("실행이 없습니다: " + executionId);
		}
		return execution;
	}

	private Path stepSnapshot(WorkFlowExecution execution, String snapshot) {
		Path dir = this.snapshots.find(execution.workflowId(), execution.executionId(), snapshot);
		if (dir == null || StepSnapshotRecorder.START.equals(snapshot)) {
			throw new IllegalArgumentException("step 스냅숏이 없습니다: " + snapshot + " (스냅숏 기능을 넣기 전 실행이거나, forEach·TOOL step이거나, 보관 기간이 지났습니다)");
		}
		return dir;
	}

	/** Agent output 정의의 칸 이름들입니다(글자 답이면 빈 목록). */
	@SuppressWarnings("unchecked")
	private List<String> outputFields(String agentId) {
		List<String> fields = new ArrayList<String>();
		AgentDefinition agent = this.agentRegistry.find(agentId);
		if (agent == null || agent.output() == null || agent.output().schema() == null) {
			return fields;
		}
		Object properties = agent.output().schema().get("properties");
		if (properties instanceof Map<?, ?> map) {
			for (Object key : ((Map<Object, Object>) map).keySet()) {
				fields.add(String.valueOf(key));
			}
		}
		return fields;
	}

	/** after(스냅숏이 끝난 시각) 뒤에 처음 내려진 결정입니다. 없으면 null입니다. */
	private static Map<String, Object> nextDecision(WorkFlowExecution execution, String after) {
		Instant end;
		try {
			end = Instant.parse(after);
		} catch (RuntimeException e) {
			return null;
		}
		for (Map<String, Object> decision : WorkFlowContext.decisions(execution.context())) {
			try {
				Instant at = OffsetDateTime.parse(String.valueOf(decision.get("at"))).toInstant();
				if (!at.isBefore(end)) {
					return decision;
				}
			} catch (RuntimeException e) {
				// 시각이 없는 옛 기록은 건너뛴다
			}
		}
		return null;
	}

	/** 처음으로 통째 복사된 입력 폴더의 이름입니다(작업 폴더 후보). 없으면 null입니다. */
	private static String firstCopiedDir(List<Map<String, Object>> inputs) {
		if (inputs == null) {
			return null;
		}
		for (Map<String, Object> item : inputs) {
			if ("dir".equals(item.get("type")) && Boolean.TRUE.equals(item.get("copied"))) {
				return String.valueOf(item.get("key"));
			}
		}
		return null;
	}

	/** base 폴더 안의 path면 base 기준 상대 경로, 아니면 null입니다(드라이브 문자가 있으면 대소문자 무시). */
	static String relativeTo(String base, String path) {
		if (base == null || path == null) {
			return null;
		}
		String b = base.replace('\\', '/');
		while (b.endsWith("/")) {
			b = b.substring(0, b.length() - 1);
		}
		String p = path.replace('\\', '/');
		boolean ignoreCase = b.matches("^[A-Za-z]:.*");
		String prefix = b + "/";
		if (ignoreCase ? p.toLowerCase().startsWith(prefix.toLowerCase()) : p.startsWith(prefix)) {
			return p.substring(prefix.length());
		}
		return null;
	}

	private static String suggestId(String base) {
		String id = (base + "-" + new SimpleDateFormat("yyyyMMdd").format(new Date())).toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
		return id.isEmpty() ? "case" : id;
	}

	private static String shortTime(Object iso) {
		if (iso == null) {
			return "";
		}
		String text = String.valueOf(iso);
		return text.length() >= 16 ? text.substring(0, 16).replace('T', ' ') : text;
	}

	private static String dump(Map<String, Object> root) {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setAllowUnicode(true);
		options.setIndent(2);
		options.setIndicatorIndent(0);
		options.setWidth(4096);
		options.setSplitLines(false);
		return new Yaml(options).dump(root);
	}

	private Path previewRoot() {
		String root = this.environment.getProperty("dstone.ai.eval.work-root");
		if (root == null || root.isBlank()) {
			root = System.getProperty("APP_HOME", ".") + "/LOGS/dstone-ai-engine/eval";
		}
		return Path.of(root).toAbsolutePath().normalize().resolve("_preview");
	}

	private static Object readJson(Path file) throws IOException {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		return JSON.readValue(file.toFile(), Object.class);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> readMap(Path file) throws IOException {
		Object value = readJson(file);
		return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
	}

	private static Object shorten(Object value) {
		if (value == null) {
			return null;
		}
		String text = value instanceof String s ? s : String.valueOf(value);
		if (text.length() <= ANSWER_PREVIEW_CHARS) {
			return value;
		}
		return text.substring(0, ANSWER_PREVIEW_CHARS) + "… (잘림)";
	}

	private static String utf8OrNull(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			return null;
		}
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

	private static void deleteQuietly(Path dir) {
		if (dir == null || !Files.exists(dir)) {
			return;
		}
		try {
			Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
					Files.delete(path);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			// 미리 채점 폴더는 남아도 된다(평가 작업 폴더 아래 _preview)
		}
	}

}

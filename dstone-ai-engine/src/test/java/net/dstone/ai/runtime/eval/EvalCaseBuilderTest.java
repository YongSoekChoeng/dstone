package net.dstone.ai.runtime.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.api.dto.EvalCaseDraftRequest;
import net.dstone.ai.common.project.ProjectStore;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.tool.StepRef;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.ai.runtime.workflow.file.WorkFlowFileEntry;
import net.dstone.ai.runtime.workflow.file.WorkFlowFileStore;
import net.dstone.ai.runtime.workflow.snapshot.StepSnapshotRecorder;

/**
 * <pre>
 * Step 스냅숏(StepSnapshotRecorder)과 실행 기록으로 평가 사례를 만드는 일(EvalCaseBuilder)을 Spring 없이 확인합니다.
 * 실행 저장소·프로젝트·Agent 레지스트리는 대역(Mockito)이고, 스냅숏·사례 폴더는 임시 폴더입니다.
 * 장면: Agent가 소스 파일을 읽고 작업 폴더의 설계서를 고쳤는데, 답에 "확인하지 않은 것"을 PASS로 냈다.
 * </pre>
 */
public class EvalCaseBuilderTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@TempDir
	Path dir;

	private Path work;
	private Path source;
	private StepSnapshotRecorder recorder;
	private EvalCaseLoader loader;
	private EvalCaseBuilder builder;
	private WorkFlowExecutionStore executionStore;
	private WorkFlowFileStore fileStore;
	private Map<String, Object> context;

	@BeforeEach
	void setUp() throws Exception {
		this.work = Files.createDirectories(this.dir.resolve("req/20261124"));
		this.source = Files.createDirectories(this.dir.resolve("app/src"));
		Files.writeString(this.work.resolve("design.md"), "처음 설계", StandardCharsets.UTF_8);
		// 대상 앱 화면의 EL. 사례의 params로 오해받으면 안 된다
		Files.writeString(this.work.resolve("list.jsp"), "<a href=\"view?id=${param.id}\">보기</a>", StandardCharsets.UTF_8);
		Files.writeString(this.source.resolve("Main.java"), "class Main {}", StandardCharsets.UTF_8);

		MockEnvironment env = new MockEnvironment();
		env.setProperty("dstone.ai.workflow.snapshot.root", this.dir.resolve("snapshot").toString());
		env.setProperty("dstone.ai.eval.location", this.dir.resolve("eval").toString());
		env.setProperty("dstone.ai.eval.work-root", this.dir.resolve("evalwork").toString());
		this.recorder = new StepSnapshotRecorder();
		ReflectionTestUtils.setField(this.recorder, "environment", env);
		this.loader = new EvalCaseLoader();
		ReflectionTestUtils.setField(this.loader, "environment", env);

		this.context = WorkFlowContext.create(slash(this.dir.resolve("req")), "demo-workflow", "1.0");
		this.executionStore = mock(WorkFlowExecutionStore.class);
		when(this.executionStore.find("exec-1")).thenReturn(WorkFlowExecution.start("exec-1", "demo-workflow", null, null, this.context));
		this.fileStore = mock(WorkFlowFileStore.class);
		when(this.fileStore.list("exec-1")).thenReturn(new ArrayList<WorkFlowFileEntry>());
		ProjectStore projectStore = mock(ProjectStore.class);
		Map<String, Object> project = new LinkedHashMap<String, Object>();
		project.put("projectId", "demoApp");
		project.put("sourceDir", slash(this.dir.resolve("app")));
		when(projectStore.find("demoApp")).thenReturn(project);

		this.builder = new EvalCaseBuilder();
		ReflectionTestUtils.setField(this.builder, "snapshots", this.recorder);
		ReflectionTestUtils.setField(this.builder, "executionStore", this.executionStore);
		ReflectionTestUtils.setField(this.builder, "fileStore", this.fileStore);
		ReflectionTestUtils.setField(this.builder, "loader", this.loader);
		ReflectionTestUtils.setField(this.builder, "agentRegistry", mock(AgentRegistry.class));
		ReflectionTestUtils.setField(this.builder, "projectStore", projectStore);
		ReflectionTestUtils.setField(this.builder, "environment", env);
	}

	/** step 하나를 돈 것처럼 스냅숏을 남깁니다: 소스를 읽고, 설계서를 고치고, 답을 냅니다. */
	private void runStep() throws Exception {
		this.recorder.beginExecution("demo-workflow", "exec-1", this.context.get("input"));
		Map<String, Object> input = new LinkedHashMap<String, Object>();
		input.put("base_dir", slash(this.work));
		input.put("source_dir", slash(this.source));
		input.put("project_id", "demoApp");
		input.put("history", "지난 기록: " + slash(this.work) + "/design.md 를 고쳤다\n두 번째 줄");
		input.put("round", Integer.valueOf(2));
		this.recorder.beginStep("demo-workflow", "exec-1", "step5", "AGENT", "demo-agent", input);
		StepRef ref = new StepRef("exec-1", "step5");
		String read = "{\"fileFullPath\":\"" + slash(this.source) + "/Main.java\"}";
		this.recorder.beforeTool(ref, "readFile", read);
		this.recorder.afterTool(ref, "readFile", read, "class Main {}");
		String write = "{\"filePath\":\"" + slash(this.work) + "/design.md\",\"content\":\"x\"}";
		this.recorder.beforeTool(ref, "writeFile", write);
		Files.writeString(this.work.resolve("design.md"), "기준1 PASS - 미확인", StandardCharsets.UTF_8);
		this.recorder.afterTool(ref, "writeFile", write, "저장했습니다");
		Map<String, Object> answer = new LinkedHashMap<String, Object>();
		answer.put("result", "PASS");
		this.recorder.endStep("exec-1", "step5", true, answer, null);
		// 사람이 다음 승인에서 남긴 의견(설명 초안이 된다)
		WorkFlowContext.recordDecision(this.context, "step6", "검토", "APPROVAL", "반려", "tester", "확인하지 않은 기준을 PASS로 냈음", "step5");
	}

	@Test
	@SuppressWarnings("unchecked")
	void snapshotKeepsInputFilesBeforeAfterAndTools() throws Exception {
		this.runStep();
		Path step = this.recorder.find("demo-workflow", "exec-1", "step5-1");
		assertThat(step).isNotNull();
		Map<String, Object> manifest = JSON.readValue(step.resolve("manifest.json").toFile(), Map.class);
		assertThat(manifest.get("success")).isEqualTo(Boolean.TRUE);
		// 입력 폴더는 시작할 때 통째로, 고치기 전 내용으로 남는다
		assertThat(Files.readString(step.resolve("inputs/base_dir/design.md"))).isEqualTo("처음 설계");
		// 입력 폴더 밖(소스)에서 읽은 파일은 처음 건드릴 때의 내용
		List<Map<String, Object>> files = (List<Map<String, Object>>) manifest.get("files");
		assertThat(files).hasSize(2);
		Map<String, Object> design = files.get(1);
		assertThat(design.get("write")).isEqualTo(Boolean.TRUE);
		assertThat(Files.readString(step.resolve(String.valueOf(design.get("after"))))).isEqualTo("기준1 PASS - 미확인");
		assertThat(Files.exists(step.resolve(String.valueOf(files.get(0).get("before"))))).isTrue();
		Map<String, Object> tools = JSON.readValue(step.resolve("tools.json").toFile(), Map.class);
		assertThat((Map<String, Object>) tools.get("counts")).containsEntry("readFile", 1).containsEntry("writeFile", 1);
		// 다시 돌면 다음 회차
		this.recorder.beginStep("demo-workflow", "exec-1", "step5", "AGENT", "demo-agent", "x");
		this.recorder.endStep("exec-1", "step5", false, null, "실패");
		assertThat(this.recorder.find("demo-workflow", "exec-1", "step5-2")).isNotNull();
	}

	@Test
	@SuppressWarnings("unchecked")
	void stepDraftPreviewAndSave() throws Exception {
		this.runStep();
		Map<String, Object> draft = this.builder.stepDraft("exec-1", "step5-1");
		assertThat(draft.get("workKey")).isEqualTo("base_dir");
		assertThat(draft.get("suggestedDescription")).isEqualTo("확인하지 않은 기준을 PASS로 냈음");

		List<Map<String, Object>> checks = new ArrayList<Map<String, Object>>();
		checks.add(check("설계서 있음", "fileExists", "design.md"));
		checks.add(check("미확인을 PASS로 적지 않음", "fileNotMatches", Map.of("file", "design.md", "pattern", "PASS[^\\n]*?미확인")));
		checks.add(check("소스를 읽음", "toolUsed", Map.of("name", "readFile", "min", 1)));
		checks.add(check("답이 PASS가 아님", "answerNotMatches", Map.of("field", "result", "pattern", "PASS")));
		EvalCaseDraftRequest request = new EvalCaseDraftRequest("exec-1", "step5-1", "demo-step5-case", "demoApp", "미확인 PASS", "그때 일", "base_dir", null, checks);

		Map<String, Object> preview = this.builder.preview(request);
		List<Map<String, Object>> results = (List<Map<String, Object>>) preview.get("checks");
		// 기본은 ✓, 핵심은 ✗(그때 실수를 잡음)
		assertThat(results.get(0).get("ok")).isEqualTo(Boolean.TRUE);
		assertThat(results.get(1).get("ok")).isEqualTo(Boolean.FALSE);
		assertThat(results.get(2).get("ok")).isEqualTo(Boolean.TRUE);
		assertThat(results.get(3).get("ok")).isEqualTo(Boolean.FALSE);
		String yaml = String.valueOf(preview.get("yaml"));
		assertThat(yaml).contains("base_dir: ${workDir}").contains("${param.sourceDir}/src").contains("${param.projectId}")
			.contains("${fixture:input/history.txt}").contains("round: 2");
		// 환경 경로는 params의 기본값에만 남고 input에는 남지 않는다
		assertThat(yaml.substring(yaml.indexOf("\ninput:"))).doesNotContain(slash(this.dir));

		Map<String, Object> saved = this.builder.save(request);
		Path root = this.dir.resolve("eval");
		assertThat(saved.get("casePath")).isEqualTo("demoApp/demo-step5-case.eval.yml");
		assertThat(Files.readString(root.resolve("demoApp/demo-step5-case/input/history.txt"))).contains("${workDir}/design.md");
		// work/는 step이 시작할 때의 내용이고, 대상 앱 EL은 그대로다
		assertThat(Files.readString(root.resolve("demoApp/demo-step5-case/work/design.md"))).isEqualTo("처음 설계");
		assertThat(Files.readString(root.resolve("demoApp/demo-step5-case/work/list.jsp"))).contains("${param.id}");
		EvalCaseLoader.Loaded loaded = this.loader.loadAll().get(0);
		assertThat(loaded.problem()).isNull();

		// 저장한 사례로 작업 공간을 만들면 EL이 있어도 실패하지 않는다
		EvalWorkspace workspace = new EvalWorkspace(loaded.evalCase(), root.resolve("demoApp/demo-step5-case"), this.dir.resolve("run/case"), loaded.evalCase().params());
		workspace.prepare();
		Map<String, Object> input = (Map<String, Object>) workspace.input();
		assertThat(String.valueOf(input.get("history"))).contains(slash(workspace.workDir()) + "/design.md");

		// 같은 id로 다시 저장하면 거절한다
		boolean refused = false;
		try {
			this.builder.save(request);
		} catch (IllegalArgumentException e) {
			refused = true;
		}
		assertThat(refused).isTrue();
	}

	@Test
	@SuppressWarnings("unchecked")
	void workflowDraftUsesStartSnapshotAndDecisions() throws Exception {
		this.runStep();
		Map<String, Object> draft = this.builder.executionDraft("exec-1");
		assertThat(draft.get("startSnapshot")).isEqualTo(Boolean.TRUE);
		assertThat((Map<String, Object>) draft.get("approvals")).containsEntry("step6", "반려");

		List<Map<String, Object>> checks = new ArrayList<Map<String, Object>>();
		checks.add(check("설계서에 미확인 없음", "fileNotMatches", Map.of("file", "20261124/design.md", "pattern", "미확인")));
		EvalCaseDraftRequest request = new EvalCaseDraftRequest("exec-1", null, "demo-flow-case", "demoApp", "흐름", null, null, null, checks);
		Map<String, Object> preview = this.builder.preview(request);
		assertThat(String.valueOf(preview.get("yaml"))).contains("workflow: demo-workflow").contains("input: ${requestDir}").contains("step6: 반려");
		// 지금 요청 폴더(그때 결과)에는 미확인이 있으므로 실패 = 실수를 잡음
		assertThat(((List<Map<String, Object>>) preview.get("checks")).get(0).get("ok")).isEqualTo(Boolean.FALSE);
		this.builder.save(request);
		// 시작 스냅숏은 고치기 전 내용이다
		assertThat(Files.readString(this.dir.resolve("eval/demoApp/demo-flow-case/work/20261124/design.md"))).isEqualTo("처음 설계");
	}

	@Test
	void replacePathHandlesSlashesCaseAndPrefix() {
		assertThat(EvalCaseBuilder.replacePath("D:\\Work\\req\\a.md, d:/work/req/b.md, D:/Work/request/c.md", "D:/Work/req", "${workDir}"))
			.isEqualTo("${workDir}\\a.md, ${workDir}/b.md, D:/Work/request/c.md");
		assertThat(EvalCaseBuilder.relativeTo("/app/req/", "/app/req/x/y.md")).isEqualTo("x/y.md");
		assertThat(EvalCaseBuilder.relativeTo("/app/req", "/app/request/y.md")).isNull();
		assertThat(StepSnapshotRecorder.storedPath("D:\\App\\a.md")).isEqualTo("D/App/a.md");
	}

	private static Map<String, Object> check(String name, String kind, Object value) {
		Map<String, Object> check = new LinkedHashMap<String, Object>();
		check.put("name", name);
		check.put("kind", kind);
		check.put("value", value);
		return check;
	}

	private static String slash(Path path) {
		return path.toAbsolutePath().toString().replace('\\', '/');
	}
}

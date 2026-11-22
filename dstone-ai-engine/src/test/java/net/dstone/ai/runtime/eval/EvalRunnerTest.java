package net.dstone.ai.runtime.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * 평가 기능(conf/eval 사례 → 작업 폴더 → Agent 호출 → 채점 → AI_EVAL_RUN)을 미리 넣은 LLM 답으로 확인합니다.
 * 실제 LLM으로 재는 일은 엔진의 평가 화면(/ai/admin/eval/eval.html)이나 API(/api/ai/eval)로 합니다.
 * </pre>
 */
public class EvalRunnerTest extends WorkflowTestBase {

	@Autowired
	private EvalCaseLoader loader;

	@Autowired
	private EvalRunner runner;

	@Autowired
	private EvalRunStore store;

	@Test
	void conf_eval의_사례는_모두_읽힌다() {
		List<EvalCaseLoader.Loaded> all = this.loader.loadAll();
		assertThat(all).isNotEmpty();
		for (EvalCaseLoader.Loaded loaded : all) {
			assertThat(loaded.problem()).as(loaded.path()).isNull();
		}
	}

	@Test
	void 사례를_풀게_하고_채점해서_남긴다() throws Exception {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		EvalCase evalCase = this.loader.find("requirement-write");
		// Agent는 저장(writeFile)하지 않고 말로만 답합니다. 이 Agent는 requiredTools: [writeFile], 재시도 2번이라
		// 두 번 다 실패로 끝나고, 채점은 그래도 끝까지 해서 남깁니다.
		this.chatModel.answer("저장했습니다.", "저장했습니다.");
		assertThat(evalCase.agent()).isEqualTo("sm-requirement-analyze-agent");

		Map<String, Object> started = this.runner.start("requirement-write", null, null, 1, "tester");
		String runId = ((List<?>) started.get("runIds")).get(0).toString();
		Map<String, Object> run = this.waitDone(runId);

		assertThat(run.get("status")).isEqualTo("FAILED");
		assertThat(String.valueOf(run.get("failure"))).contains("writeFile");
		assertThat(run.get("passed")).isEqualTo(0);
		assertThat(run.get("total")).isEqualTo(3);
		assertThat(run.get("caseId")).isEqualTo("requirement-write");
		assertThat(run.get("agentId")).isEqualTo("sm-requirement-analyze-agent");
		assertThat(((Number) run.get("rounds")).intValue()).isEqualTo(2);
		Path workDir = Path.of(String.valueOf(run.get("workDir")));
		// 요청서는 작업 폴더 위 요청 폴더에 자리를 채워 남습니다.
		String request = Files.readString(workDir.getParent().resolve("00.request.md"), StandardCharsets.UTF_8);
		assertThat(request).contains("/app/testApp").doesNotContain("${param.sourceDir}");
		// copy: []라 작업 폴더에는 아무것도 복사되지 않습니다.
		assertThat(workDir.resolve("02.impact.md")).doesNotExist();
	}

	@Test
	void 작업_폴더를_준비하고_채점한다() throws Exception {
		EvalCase evalCase = this.loader.find("troubleshoot-delete-db");
		Path requestDir = Files.createTempDirectory("eval-test").resolve("troubleshoot-delete-db");
		Map<String, String> params = new java.util.LinkedHashMap<String, String>(evalCase.params());
		EvalWorkspace workspace = new EvalWorkspace(evalCase, this.loader.root().resolve(evalCase.fixture()), requestDir, params);
		workspace.prepare();

		@SuppressWarnings("unchecked")
		Map<String, Object> input = (Map<String, Object>) workspace.input();
		assertThat(String.valueOf(input.get("base_dir"))).isEqualTo(workspace.workDir().toString().replace('\\', '/'));
		assertThat(String.valueOf(input.get("request"))).contains("## 시험환경").contains("- DB연결: testApp");
		assertThat(String.valueOf(input.get("instruction"))).contains("삭제가 안되고 있어");
		assertThat(String.valueOf(input.get("design"))).startsWith("# 설계서");
		// JSP의 ${...}(EL)는 자리 채우기에서 건드리지 않습니다.
		Path jsp = workspace.workDir().resolve("dev-resources/src/main/webapp/WEB-INF/views/test/imageboard/imageBoard.jsp");
		assertThat(Files.readString(jsp, StandardCharsets.UTF_8)).contains("${_csrf.parameterName}");

		// Agent가 Service에 mb_id를 넣고 설계서를 고쳤다고 치고 채점합니다.
		Path service = workspace.workDir().resolve("dev-resources/src/main/java/kr/co/gnx/test/imageboard/ImageBoardService.java");
		Files.writeString(service, Files.readString(service, StandardCharsets.UTF_8) + "\n// vo.setMb_id(mbId);\n", StandardCharsets.UTF_8);
		Files.writeString(workspace.workDir().resolve("03-1.design.md"), "고침", StandardCharsets.UTF_8);
		Map<String, Object> answer = Map.of("causes", List.of("삭제 WHERE mb_id 값이 비어 있음"), "compiled", Boolean.TRUE, "verification", List.of("관측: dbQuery"));
		List<Map<String, Object>> checks = workspace.check(answer, Map.of("dbQuery", Integer.valueOf(1)));

		assertThat(checks).hasSize(6);
		for (Map<String, Object> check : checks) {
			assertThat(check.get("ok")).as(String.valueOf(check)).isEqualTo(Boolean.TRUE);
		}
		// 아무것도 안 했으면 떨어집니다.
		List<Map<String, Object>> none = new EvalWorkspace(evalCase, this.loader.root().resolve(evalCase.fixture()), requestDir.resolveSibling("again"), params)
			.check(null, Map.of());
		assertThat(none.get(0).get("ok")).isEqualTo(Boolean.FALSE);
	}

	/** 평가가 끝날 때까지(최대 60초) 기다립니다. */
	private Map<String, Object> waitDone(String runId) throws InterruptedException {
		for (int i = 0; i < 600; i++) {
			Map<String, Object> run = this.store.find(runId);
			if (run != null && ("DONE".equals(run.get("status")) || "FAILED".equals(run.get("status")))) {
				return run;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("평가가 60초 안에 끝나지 않았습니다: " + runId);
	}
}

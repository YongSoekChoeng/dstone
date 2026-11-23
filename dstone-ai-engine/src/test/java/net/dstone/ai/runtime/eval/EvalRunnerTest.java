package net.dstone.ai.runtime.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * 평가 기능(사례 → 작업 폴더 → Agent 호출 → 채점 → 저장)을 미리 넣은 LLM 답으로 확인합니다.
 * 저장소의 conf/eval 사례에 기대지 않도록 테스트가 임시 폴더에 사례를 만들어
 * dstone.ai.eval.location으로 가리킵니다. 사례의 Agent는 샘플 Agent(sample-basic-echo-agent)입니다.
 * </pre>
 */
public class EvalRunnerTest extends WorkflowTestBase {

	private static final String LOCATION = "dstone.ai.eval.location";

	@Autowired
	private EvalCaseLoader loader;

	@Autowired
	private EvalRunner runner;

	@Autowired
	private EvalRunStore store;

	@TempDir
	Path dir;

	@BeforeEach
	void writeCase() throws Exception {
		Path root = this.dir.resolve("eval");
		write(root.resolve("demo/echo-check.eval.yml"), String.join("\n",
			"id: echo-check",
			"description: 시험용 사례",
			"agent: sample-basic-echo-agent",
			"fixture: demo/data",
			"params:",
			"  name: 기본값",
			"request:",
			"  file: request.md",
			"  append: \"- 덧붙임: ${param.name}\"",
			"input: \"${request}${file:note.md}\"",
			"checks:",
			"  - name: 인사함",
			"    answerContains: 안녕",
			"  - name: 메모 그대로",
			"    fileChanged: note.md",
			"  - name: Tool 안 씀",
			"    toolUsed: {name: dbQuery, min: 1}",
			"  - name: 답 모양",
			"    answerMatches: {pattern: '안녕.*요'}",
			"  - name: 답에 없어야 할 말",
			"    answerNotMatches: {pattern: '실패'}",
			"  - name: 메모에 EL 남음",
			"    fileMatches: {file: note.md, pattern: 'EL \\$\\{_csrf'}",
			"  - name: 메모에 없어야 할 모양",
			"    fileNotMatches: {file: note.md, pattern: 'zzz'}",
			""));
		write(root.resolve("demo/data/request.md"), "# 요청 ${param.name}\n");
		write(root.resolve("demo/data/work/note.md"), "폴더 ${workDir} / EL ${_csrf.token}\n");
		write(root.resolve("demo/bad.eval.yml"), "id: other-name\nagent: x\ninput: a\nchecks: [{fileExists: a}]\n");
		System.setProperty(LOCATION, root.toString());
	}

	@AfterEach
	void clearLocation() {
		System.clearProperty(LOCATION);
	}

	private static void write(Path file, String text) throws Exception {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	@Test
	void 틀린_사례는_사유와_함께_목록에_남는다() {
		List<EvalCaseLoader.Loaded> all = this.loader.loadAll();
		assertThat(all).hasSize(2);
		EvalCaseLoader.Loaded bad = all.get(0);
		assertThat(bad.path()).isEqualTo("demo/bad.eval.yml");
		assertThat(bad.problem()).contains("파일 이름");
		assertThat(all.get(1).evalCase().id()).isEqualTo("echo-check");
	}

	@Test
	void 정규식이_틀린_채점은_읽을_때_알려_준다() throws Exception {
		write(this.dir.resolve("eval/demo/regex-bad.eval.yml"), "id: regex-bad\nagent: x\ninput: a\nchecks: [{fileMatches: {file: a, pattern: '(a'}}]\n");
		String problem = null;
		for (EvalCaseLoader.Loaded loaded : this.loader.loadAll()) {
			if ("demo/regex-bad.eval.yml".equals(loaded.path())) {
				problem = loaded.problem();
			}
		}
		assertThat(problem).contains("정규식");
	}

	@Test
	void 사례를_풀게_하고_채점해서_남긴다() throws Exception {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		this.chatModel.answer("안녕하세요.");

		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("name", "바꾼값");
		Map<String, Object> started = this.runner.start("echo-check", params, null, 1, "tester");
		String runId = ((List<?>) started.get("runIds")).get(0).toString();
		Map<String, Object> run = this.waitDone(runId);

		assertThat(run.get("status")).isEqualTo("DONE");
		assertThat(run.get("answer")).isEqualTo("안녕하세요.");
		assertThat(run.get("total")).isEqualTo(7);
		assertThat(run.get("passed")).isEqualTo(5);
		assertThat(((Number) run.get("rounds")).intValue()).isEqualTo(1);
		// Agent에 들어간 글: 요청서(자리를 채움 + 덧붙임) + 작업 폴더 파일 내용
		String sent = net.dstone.ai.support.ScriptedChatModel.textOf(this.chatModel.prompts().get(0));
		assertThat(sent).contains("# 요청 바꾼값").contains("- 덧붙임: 바꾼값").contains("EL ${_csrf.token}");
		Path workDir = Path.of(String.valueOf(run.get("workDir")));
		assertThat(Files.readString(workDir.resolve("note.md"), StandardCharsets.UTF_8)).contains(workDir.toString().replace('\\', '/'));
		assertThat(Files.readString(workDir.getParent().resolve("00.request.md"), StandardCharsets.UTF_8)).contains("바꾼값");
	}

	@Test
	void 없는_사례나_없는_params는_거절한다() {
		String noCase = null;
		try {
			this.runner.start("nothing", null, null, 1, "tester");
		} catch (IllegalArgumentException e) {
			noCase = e.getMessage();
		}
		String noParam = null;
		try {
			this.runner.start("echo-check", Map.of("zzz", "1"), null, 1, "tester");
		} catch (IllegalArgumentException e) {
			noParam = e.getMessage();
		}
		assertThat(noCase).contains("없습니다");
		assertThat(noParam).contains("zzz");
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

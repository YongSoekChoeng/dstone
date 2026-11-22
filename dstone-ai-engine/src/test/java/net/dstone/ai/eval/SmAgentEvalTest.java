package net.dstone.ai.eval;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.expression.DecisionText;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow Agent들의 행동을 실제 LLM으로 재는 평가 세트입니다.
 * 배관(값이 제대로 전달되는가)이 아니라 Agent가 일을 해내는지를 봅니다. 저장했는가, Tool을 몇 번 불렀는가, 지시를 반영했는가.
 *
 * 프롬프트·모델·엔진을 바꾸면 이 세트를 다시 돌려 기준선과 비교합니다(.claude/docs/ai-engine-agent-principles.md).
 * 사례는 실제 실행(9294e45d, 2026-11-20)의 문서와 승인 의견입니다(src/test/resources/eval/sm/work002).
 * 문서 속 경로는 {{SOURCE_DIR}} 같은 자리로 바꿔 두었고, 돌릴 때 이 PC의 경로로 채웁니다.
 *
 * 돌리는 법(실제 LLM 호출 비용이 듭니다. 사례마다 수 분):
 *   mvn test -DskipTests=false -Ddstone.ai.test.llmcall=true -Ddstone.ai.eval=true -Dtest=SmAgentEvalTest
 *   -Ddstone.ai.eval.source-dir=/app/testApp   (testApp 소스 루트. 기본값 /app/testApp)
 *   -Dtest=SmAgentEvalTest#설계_수정            (사례 하나만)
 *
 * 결과는 target/eval/report.md에 한 줄씩 쌓이고, 사례마다 만든 문서는 target/eval/{실행시각}/{사례}/ 에 남습니다.
 * 판정(checks)은 문서에 꼭 들어가야 할 낱말을 보는 정도입니다. 품질 전체는 남은 문서를 사람이 읽어 확인합니다.
 * </pre>
 */
public class SmAgentEvalTest extends WorkflowTestBase {

	private static final Path FIXTURE = Paths.get("src/test/resources/eval/sm/work002");

	/**
	 * 전문가 해결 사례(실행 f2cf12b3, 2026-11-22). 다섯 번 맡겨도 "게시물 삭제가 안 된다"를 풀지 못한 시점의 설계서·개발 결과·결정 기록입니다.
	 * 진짜 원인은 삭제 SQL의 WHERE 조건값(mb_id)을 Controller·Service 어디에서도 채우지 않은 것입니다(오류 없이 0건 삭제).
	 */
	private static final Path TROUBLESHOOT_FIXTURE = Paths.get("src/test/resources/eval/sm/troubleshoot-delete");

	private static final Path OUT_ROOT = Paths.get("target/eval");

	private static final String RUN_ID = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());

	@Autowired
	private AgentExecutor agentExecutor;
	@Autowired
	private AgentRegistry agentRegistry;

	@BeforeEach
	void onlyWhenAsked() {
		assumeTrue(this.chatModel.isLlmCall() && Boolean.getBoolean("dstone.ai.eval"), "실제 LLM 평가는 -Ddstone.ai.test.llmcall=true -Ddstone.ai.eval=true 일 때만 돕니다.");
	}

	@Test
	void 요구사항_작성() throws Exception {
		Case c = this.newCase("요구사항_작성");
		Map<String, Object> input = new LinkedHashMap<String, Object>();
		input.put("base_dir", c.workDir.toString());
		input.put("request", c.request);
		c.run("sm-requirement-analyze-agent", input);
		c.check("01 저장", c.exists("01.customer_requirement.md"));
		c.check("목록화면", c.contains("01.customer_requirement.md", "목록"));
		c.check("썸네일", c.contains("01.customer_requirement.md", "썸네일"));
		c.report();
	}

	@Test
	void 영향도_작성() throws Exception {
		Case c = this.newCase("영향도_작성");
		c.copyDoc("01.customer_requirement.md");
		Map<String, Object> input = new LinkedHashMap<String, Object>();
		input.put("base_dir", c.workDir.toString());
		input.put("request", c.request);
		input.put("requirement", c.doc("01.customer_requirement.md"));
		c.run("sm-analyze-impact-agent", input);
		c.check("02 저장", c.exists("02.impact.md"));
		c.check("TASK 표", c.contains("02.impact.md", "TASK-01"));
		c.check("소스경로 기준", c.contains("02.impact.md", c.sourceDir));
		// 요청서에 프로젝트ID가 있으므로 조사는 knowledge Tool로 시작해야 한다(Skill source-investigation).
		c.check("knowledge Tool 사용", c.toolCalls("knowledge") > 0);
		c.report();
	}

	@Test
	void 설계_작성() throws Exception {
		Case c = this.newCase("설계_작성");
		c.copyDoc("01.customer_requirement.md");
		c.copyDoc("02.impact.md");
		Map<String, Object> input = new LinkedHashMap<String, Object>();
		input.put("base_dir", c.workDir.toString());
		input.put("request", c.request);
		input.put("requirement", c.doc("01.customer_requirement.md"));
		input.put("impact", c.doc("02.impact.md"));
		input.put("instruction", c.decisionText("step08", "영향도 분석 승인", "impactApproval", null));
		input.put("history", "[1] " + c.decisionText("step08", "영향도 분석 승인", "impactApproval", null));
		c.run("sm-architect-agent", input);
		c.check("03-1 저장", c.exists("03-1.design.md"));
		c.check("03-2 저장", c.exists("03-2.taskitems.md"));
		c.check("승인 의견 반영(TS_300)", c.contains("03-1.design.md", "TS_300"));
		c.report();
	}

	@Test
	void 설계_수정() throws Exception {
		Case c = this.newCase("설계_수정");
		c.copyDoc("01.customer_requirement.md");
		c.copyDoc("02.impact.md");
		c.copyDoc("03-1.design.md");
		c.copyDoc("03-2.taskitems.md");
		c.copyDoc("03-3.ddl(데이터베이스).md");
		// sm-workflow 2.0.0: [설계 수정]을 고르면 문서 수정 Agent가 설계서 세 개를 지시대로 고친다(step20).
		String instruction = c.decisionText("step11", "설계 승인", "designApproval", "설계 수정");
		Map<String, Object> input = new LinkedHashMap<String, Object>();
		input.put("documents", List.of(c.path("03-1.design.md"), c.path("03-2.taskitems.md"), c.path("03-3.ddl(데이터베이스).md")));
		input.put("request", c.request);
		input.put("instruction", instruction);
		input.put("history", "[1] " + c.decisionText("step08", "영향도 분석 승인", "impactApproval", null) + "\n\n[2] " + instruction);
		c.run("document-revise-agent", input);
		c.check("설계서 고침", c.changed("03-1.design.md"));
		c.check("imageBoard.go", c.contains("03-1.design.md", "imageBoard.go"));
		c.check("최대 50MB", c.contains("03-1.design.md", "50"));
		c.check("gif 허용", c.contains("03-1.design.md", "gif"));
		c.check("썸네일 파일 생성", c.contains("03-1.design.md", "ImageUtil"));
		c.report();
	}

	@Test
	void 전문가_해결_삭제_증거없음() throws Exception {
		this.troubleshootDelete("전문가_해결_삭제_증거없음", "troubleshoot-none", null);
	}

	@Test
	void 전문가_해결_삭제_시험환경() throws Exception {
		this.troubleshootDelete("전문가_해결_삭제_시험환경", "troubleshoot-db", "- DB연결: " + System.getProperty("dstone.ai.eval.db-connection", "testApp") + "\n");
	}

	/**
	 * <pre>
	 * 시험 서버에 올려 브라우저로 재현까지 하는 사례입니다. 이 PC에 시험 서버가 준비되어 있어야 합니다.
	 * 기본값은 WSL의 testApp 시험 사본(docs/software/16.playwright-mcp.md 4절)이고, 값은 시스템 프로퍼티로 바꿀 수 있습니다.
	 *   -Ddstone.ai.eval.stage-dir=/app/testApp-stage   -Ddstone.ai.eval.url=http://localhost:7080
	 *   -Ddstone.ai.eval.account="admin / test1234!"     -Ddstone.ai.eval.server-log=/opt/tomcat9/logs/catalina.out
	 * 반영 스크립트는 conf/application.yml의 runShellScript 등록 이름 test-server-deploy(env의 TEST_SERVER_DEPLOY_SCRIPT)입니다.
	 * </pre>
	 */
	@Test
	void 전문가_해결_삭제_재현() throws Exception {
		String environment = "- DB연결: " + System.getProperty("dstone.ai.eval.db-connection", "testApp") + "\n"
			+ "- 서버로그: " + System.getProperty("dstone.ai.eval.server-log", "/opt/tomcat9/logs/catalina.out") + "\n"
			+ "- 반영폴더: " + System.getProperty("dstone.ai.eval.stage-dir", "/app/testApp-stage") + "\n"
			+ "- 반영명령: test-server-deploy\n"
			+ "- 접속주소: " + System.getProperty("dstone.ai.eval.url", "http://localhost:7080") + "\n"
			+ "- 시험계정: " + System.getProperty("dstone.ai.eval.account", "admin / test1234!") + "\n";
		this.troubleshootDelete("전문가_해결_삭제_재현", "troubleshoot-reproduce", environment);
	}

	/**
	 * <pre>
	 * 전문가 해결(sm-troubleshooter-agent)이 "여전히 삭제가 안 된다"만 받고 진짜 원인을 찾는지 봅니다.
	 * environment가 있으면 요청서에 '## 시험환경' 절로 붙이고, 적힌 수단을 실제로 썼는지도 봅니다.
	 * DB연결 별칭은 -Ddstone.ai.eval.db-connection(기본 testApp)으로 바꿀 수 있습니다.
	 * </pre>
	 *
	 * 작업 폴더 이름은 영문(folder)으로 둡니다. 2026-11-22 첫 측정에서 한글 폴더 이름(…/20261122-111421/전문가_해결_…)을
	 * 모델이 '-'로 이어 옮겨 적어 개발 결과를 찾지 못하고 Tool 한도를 다 쓴 일이 있었습니다.
	 *
	 * @param folder      작업 폴더 이름(영문)
	 * @param environment '## 시험환경' 아래에 붙일 줄들. 없으면 null
	 */
	private void troubleshootDelete(String name, String folder, String environment) throws Exception {
		Case c = this.newCase(name, folder, TROUBLESHOOT_FIXTURE);
		if (environment != null) {
			c.request = c.request.stripTrailing() + "\n\n## 시험환경\n" + environment;
			Files.writeString(c.requestDir.resolve("00.request.md"), c.request, StandardCharsets.UTF_8);
		}
		c.copyDoc("03-1.design.md");
		c.copyDoc("03-2.taskitems.md");
		c.copyDoc("03-3.ddl(데이터베이스).md");
		c.copyDevResources();
		Map<String, Object> input = new LinkedHashMap<String, Object>();
		input.put("base_dir", c.workDir.toString().replace('\\', '/'));
		input.put("source_dir", c.sourceDir);
		input.put("request", c.request);
		input.put("design", c.doc("03-1.design.md"));
		input.put("taskitems", c.doc("03-2.taskitems.md"));
		input.put("instruction", c.inputOf("instruction"));
		input.put("history", c.inputOf("history"));
		input.put("compile_error", "");
		c.run("sm-troubleshooter-agent", input);

		String controller = "dev-resources/src/main/java/kr/co/gnx/test/imageboard/ImageBoardController.java";
		String service = "dev-resources/src/main/java/kr/co/gnx/test/imageboard/ImageBoardService.java";
		int before = c.countOriginal(controller, "setMb_id") + c.countOriginal(service, "setMb_id");
		int after = c.count(controller, "setMb_id") + c.count(service, "setMb_id");
		c.check("원인에 조건값(mb_id) 누락", c.answerOf("causes").contains("mb_id"));
		c.check("삭제 경로에 mb_id 채움", after > before);
		c.check("설계서 반영", c.changed("03-1.design.md"));
		c.check("컴파일 통과", "true".equals(c.answerOf("compiled")));
		c.check("확인 방법 기록", !c.answerOf("verification").isEmpty() && !"[]".equals(c.answerOf("verification")));
		if (environment != null && environment.contains("DB연결")) {
			c.check("dbQuery로 관측", c.toolCalls("dbQuery") > 0);
		}
		if (environment != null && environment.contains("반영명령")) {
			c.check("시험 서버에 반영", c.toolCalls("applyOverlay") > 0 && c.toolCalls("runShellScript") > 0);
			c.check("브라우저로 재현", c.toolCalls("browser_") > 0);
			// 브라우저로 누르면 체크박스가 체크된 채로 남지 않는 문제도 있습니다(2026-11-22 확인). 찾았는지 참고로 봅니다.
			c.check("(참고) 체크 유지 문제", c.answerOf("causes").contains("체크"));
		}
		c.report();
	}

	/**
	 * 사례 하나의 작업 폴더를 만듭니다. 요청서는 폴더 위(요청 폴더)에 두고, 작업 폴더는 그 아래 실행시각 폴더입니다.
	 */
	private Case newCase(String name) throws IOException {
		return this.newCase(name, name, FIXTURE);
	}

	/**
	 * 사례 하나의 작업 폴더를 만듭니다. folder는 작업 폴더 이름, fixture는 00.request.md와 docs/가 들어 있는 사례 폴더입니다.
	 */
	private Case newCase(String name, String folder, Path fixture) throws IOException {
		Path requestDir = OUT_ROOT.resolve(RUN_ID).resolve(folder).toAbsolutePath();
		Path workDir = requestDir.resolve("work");
		Files.createDirectories(workDir);
		Case c = new Case(name, fixture, requestDir, workDir, System.getProperty("dstone.ai.eval.source-dir", "/app/testApp"));
		c.request = c.fill(Files.readString(fixture.resolve("00.request.md"), StandardCharsets.UTF_8));
		Files.writeString(requestDir.resolve("00.request.md"), c.request, StandardCharsets.UTF_8);
		this.chatModel.reset();
		return c;
	}

	/** 사례 하나의 자료와 결과를 들고 있습니다. */
	private class Case {

		final String name;
		final Path fixture;
		final Path requestDir;
		final Path workDir;
		final String sourceDir;
		final Map<String, String> original = new LinkedHashMap<String, String>();
		final List<String> checks = new ArrayList<String>();
		String request;
		String agentId;
		Object answer;
		boolean success;
		String failure;
		long seconds;
		int passed;

		Case(String name, Path fixture, Path requestDir, Path workDir, String sourceDir) {
			this.name = name;
			this.fixture = fixture;
			this.requestDir = requestDir;
			this.workDir = workDir;
			this.sourceDir = sourceDir;
		}

		String fill(String text) {
			return text.replace("{{SOURCE_DIR}}", this.sourceDir).replace("{{WORK_DIR}}", this.workDir.toString().replace('\\', '/'))
				.replace("{{REQUEST_DIR}}", this.requestDir.toString().replace('\\', '/'));
		}

		void copyDoc(String file) throws IOException {
			String text = this.fill(Files.readString(this.fixture.resolve("docs").resolve(file), StandardCharsets.UTF_8));
			Files.writeString(this.workDir.resolve(file), text, StandardCharsets.UTF_8);
			this.original.put(file, text);
		}

		/** 사례 폴더의 dev-resources를 작업 폴더로 옮깁니다(경로 자리는 채웁니다). 옮긴 파일의 처음 내용은 original에 둡니다. */
		void copyDevResources() throws IOException {
			final Path from = this.fixture.resolve("dev-resources");
			List<Path> files = new ArrayList<Path>();
			try (java.util.stream.Stream<Path> walk = Files.walk(from)) {
				java.util.Iterator<Path> it = walk.iterator();
				while (it.hasNext()) {
					Path p = it.next();
					if (Files.isRegularFile(p)) {
						files.add(p);
					}
				}
			}
			for (Path file : files) {
				String rel = "dev-resources/" + from.relativize(file).toString().replace('\\', '/');
				Path to = this.workDir.resolve(rel);
				Files.createDirectories(to.getParent());
				String text = this.fill(Files.readString(file, StandardCharsets.UTF_8));
				Files.writeString(to, text, StandardCharsets.UTF_8);
				this.original.put(rel, text);
			}
		}

		/** 사례 폴더의 input.json에서 값 하나를 꺼냅니다(경로 자리는 채웁니다). */
		@SuppressWarnings("unchecked")
		String inputOf(String key) throws IOException {
			Map<String, Object> all = new ObjectMapper().readValue(this.fixture.resolve("input.json").toFile(), Map.class);
			return this.fill(String.valueOf(all.get(key)));
		}

		/** Agent 답(맵)의 한 항목을 글자로 돌려줍니다. 없으면 빈 글자입니다. */
		String answerOf(String key) {
			if (this.answer instanceof Map<?, ?> map && map.get(key) != null) {
				return String.valueOf(map.get(key));
			}
			return "";
		}

		/** 파일에서 text가 몇 번 나오는지 셉니다. 파일이 없으면 0입니다. */
		int count(String file, String text) {
			try {
				if (!this.exists(file)) {
					return 0;
				}
				String doc = this.doc(file);
				int n = 0;
				int at = doc.indexOf(text);
				while (at >= 0) {
					n++;
					at = doc.indexOf(text, at + text.length());
				}
				return n;
			} catch (IOException e) {
				return 0;
			}
		}

		/** 처음 내용(original)에서 text가 몇 번 나왔는지 셉니다. */
		int countOriginal(String file, String text) {
			String doc = this.original.get(file);
			if (doc == null) {
				return 0;
			}
			int n = 0;
			int at = doc.indexOf(text);
			while (at >= 0) {
				n++;
				at = doc.indexOf(text, at + text.length());
			}
			return n;
		}

		String doc(String file) throws IOException {
			return Files.readString(this.workDir.resolve(file), StandardCharsets.UTF_8);
		}

		String path(String file) {
			return this.workDir.resolve(file).toString().replace('\\', '/');
		}

		/**
		 * 실제 실행의 승인 기록을 엔진이 ${arrival}로 넘기는 글 모양 그대로 만듭니다(DecisionText).
		 *
		 * @param decisionName 결정 이름을 바꿔 쓸 때(route 이름이 바뀐 경우). 그대로면 null
		 */
		@SuppressWarnings("unchecked")
		String decisionText(String stepId, String stepName, String stateName, String decisionName) throws IOException {
			Map<String, Object> all = new ObjectMapper().readValue(this.fixture.resolve("decisions.json").toFile(), Map.class);
			Map<String, Object> one = (Map<String, Object>) all.get(stateName);
			Map<String, Object> record = new LinkedHashMap<String, Object>();
			record.put("step", stepId);
			record.put("name", stepName);
			record.put("kind", "APPROVAL");
			record.put("decision", decisionName == null ? one.get("decision") : decisionName);
			record.put("approver", one.get("approver"));
			record.put("comment", this.fill(String.valueOf(one.get("comment"))));
			return DecisionText.of(record);
		}

		void run(String agentId, Object input) {
			this.agentId = agentId;
			AgentDefinition agent = SmAgentEvalTest.this.agentRegistry.find(agentId);
			long start = System.currentTimeMillis();
			try {
				this.answer = SmAgentEvalTest.this.agentExecutor.call(agent, null, null, input, null, null, null, null);
				this.success = true;
			} catch (RuntimeException e) {
				this.success = false;
				this.failure = e.getMessage();
			}
			this.seconds = (System.currentTimeMillis() - start) / 1000;
		}

		/** 이번 사례에서 LLM이 요청한 Tool 호출 가운데 이름이 prefix로 시작하는 것의 수입니다. */
		int toolCalls(String prefix) {
			int count = 0;
			for (ChatResponse response : SmAgentEvalTest.this.chatModel.responses()) {
				if (response.getResult() == null || response.getResult().getOutput() == null) {
					continue;
				}
				for (AssistantMessage.ToolCall call : response.getResult().getOutput().getToolCalls()) {
					if (call.name().startsWith(prefix)) {
						count++;
					}
				}
			}
			return count;
		}

		boolean exists(String file) {
			return Files.exists(this.workDir.resolve(file));
		}

		boolean contains(String file, String text) {
			try {
				return this.exists(file) && this.doc(file).contains(text);
			} catch (IOException e) {
				return false;
			}
		}

		boolean changed(String file) {
			try {
				return this.exists(file) && !this.doc(file).equals(this.original.get(file));
			} catch (IOException e) {
				return false;
			}
		}

		void check(String label, boolean ok) {
			this.checks.add((ok ? "O " : "X ") + label);
			if (ok) {
				this.passed++;
			}
		}

		void report() throws IOException {
			int rounds = 0;
			int toolCalls = 0;
			long promptTokens = 0;
			Map<String, Integer> byTool = new TreeMap<String, Integer>();
			for (ChatResponse response : SmAgentEvalTest.this.chatModel.responses()) {
				rounds++;
				if (response.getMetadata() != null && response.getMetadata().getUsage() != null && response.getMetadata().getUsage().getPromptTokens() != null) {
					promptTokens += response.getMetadata().getUsage().getPromptTokens().longValue();
				}
				if (response.getResult() == null || response.getResult().getOutput() == null) {
					continue;
				}
				for (AssistantMessage.ToolCall call : response.getResult().getOutput().getToolCalls()) {
					toolCalls++;
					Integer count = byTool.get(call.name());
					byTool.put(call.name(), count == null ? 1 : count + 1);
				}
			}
			String line = "| " + RUN_ID + " | " + this.name + " | " + this.agentId + " | " + (this.success ? "성공" : "실패") + " | " + this.seconds + "s | " + rounds + " | " + toolCalls
				+ " | " + promptTokens + " | " + this.passed + "/" + this.checks.size() + " | " + String.join(", ", this.checks) + " | " + byTool
				+ (this.failure == null ? "" : " | " + this.failure.replace('\n', ' ').substring(0, Math.min(200, this.failure.length()))) + " |\n";
			Path report = OUT_ROOT.resolve("report.md");
			if (!Files.exists(report)) {
				Files.createDirectories(OUT_ROOT);
				Files.writeString(report, "| 실행 | 사례 | Agent | 결과 | 시간 | LLM 회차 | Tool 호출 | 입력 토큰 합 | 판정 | 판정 내용 | Tool별 | 실패 사유 |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n", StandardCharsets.UTF_8);
			}
			Files.writeString(report, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
			System.out.println("[eval] " + line);
		}
	}

}

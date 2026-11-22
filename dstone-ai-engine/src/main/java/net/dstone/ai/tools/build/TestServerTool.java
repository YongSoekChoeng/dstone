package net.dstone.ai.tools.build;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.PreDestroy;
import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.project.ProjectStore;
import net.dstone.ai.runtime.tool.StepRef;
import net.dstone.ai.runtime.tool.ToolOutcome;
import net.dstone.ai.runtime.workflow.StepEndListener;
import net.dstone.ai.tools.build.sandbox.SandboxException;
import net.dstone.ai.tools.build.sandbox.SandboxRegistry;
import net.dstone.ai.tools.build.sandbox.TestSandbox;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 원본 소스 + 개발 결과로 임시 시험 서버를 띄우고 내리는 Tool입니다.
 * 문제 해결 Agent가 고치기 전에 증상을 재현하고, 고친 뒤 증상이 사라졌는지 브라우저로 확인하는 데 씁니다.
 * 사람이 쓰는 시험 서버는 건드리지 않습니다.
 *
 * ## 폴더
 * 프로젝트마다 폴더 하나를 캐시처럼 다시 씁니다. 처음 한 번만 원본 전체를 복사하고, 다음부터는 바뀐 파일만 복사합니다.
 *   {작업 루트}/{프로젝트키}-server/
 *     app/         시험용 사본(applyOverlay가 원본 + 개발 결과로 맞춤. 빌드 결과(target)는 띄울 때마다 clean으로 새로 만듦)
 *     tomcat/      WAR 방식일 때의 전용 CATALINA_BASE(conf, webapps, logs, work, temp)
 *     server.log   서버 출력(readFileTail로 읽음)
 *     server.pid   띄운 프로세스 번호(엔진이 죽어 남은 프로세스를 다음에 정리하려고)
 *     server.lock  같은 프로젝트를 두 실행이 동시에 쓰지 않게 잡는 잠금
 *     source.txt   사본을 만든 원본 경로(다른 원본이면 사본을 새로 만듦)
 * 작업 루트는 dstone.ai.tool.test-server.work-root(env의 TEST_SERVER_WORK_ROOT), 비우면 {사용자 홈}/.dstone-ai-engine 입니다.
 * 프로젝트키는 프로젝트ID, 없으면 원본 폴더 이름입니다.
 *
 * ## 띄우는 방법
 * Maven 프로젝트만 지원합니다. 포트는 비어 있는 것을 골라 씁니다. 사람이 쓰는 서버와 겹치지 않게 하려는 것입니다.
 * 1) packaging이 war이고 설정 tomcat-home(env의 TEST_SERVER_TOMCAT_HOME)이 있으면, 실제 배포와 같은 방식으로 띄웁니다.
 *    mvn clean package로 WAR를 만들고(clean: 지난 실행의 클래스가 남지 않게), 설치된 Tomcat을 함께 쓰되 사본 폴더 안에 전용 CATALINA_BASE(tomcat/)를 만들어 거기에 올립니다.
 *    설치된 Tomcat의 conf를 복사해 HTTP 포트만 바꾸고 shutdown 포트와 AJP는 끕니다. 사람이 쓰는 Tomcat은 건드리지 않습니다.
 *    context path는 pom의 tomcat 플러그인 <path>, 없으면 / 입니다.
 * 2) 아니면 원본 pom.xml의 플러그인으로 띄웁니다.
 *   tomcat7/tomcat6-maven-plugin   mvn tomcat7:run  (사본 pom의 그 플러그인 <port>만 빈 포트로 바꿉니다)
 *   spring-boot-maven-plugin       mvn spring-boot:run  (--server.port 인자)
 *   jetty-maven-plugin             mvn jetty:run  (-Djetty.http.port)
 * 플러그인 방식은 앱 jar와 플러그인의 서블릿 API가 부딪쳐 못 뜨는 앱이 있습니다(WAR에 servlet-api가 딸려 들어간 경우). 그런 앱은 1)로 띄웁니다.
 *
 * ## JDK
 * 프로젝트 관리의 Java 버전(ai_management_project.JAVA_VERSION) → 원본 pom.xml의 컴파일 버전 → 원본 클래스 파일 순서로 버전을 정하고,
 * 그 버전의 JDK 경로를 설정 java-home-{버전}(env의 JAVA_HOME_{버전})에서 찾습니다. 경로는 서버마다 달라서 DB에 두지 않습니다.
 * 못 찾으면 엔진이 쓰는 JDK로 띄우고 결과에 그렇다고 적습니다.
 *
 * ## 내리기
 * 접속 판정: HTTP 응답이 오면 뜬 것으로 보되, WAR 방식에서 앱 경로가 404면 앱이 시작에 실패한 것으로 보고 실패를 돌려줍니다.
 *
 * Agent가 stopTestServer를 부르지 않아도, 띄운 step이 끝나면(StepEndListener) 내립니다. 엔진이 내려갈 때도 내립니다.
 * 사본 폴더는 지우지 않습니다(다음에 다시 씀). 지우고 싶으면 폴더를 직접 지우면 됩니다.
 *
 * ## 두 가지 모드
 * - startTestServer: DB를 따로 만들지 않습니다. 사본은 원본 설정 그대로 원래 DB에 붙습니다. 개발·시험 DB에서만 쓰십시오.
 * - startSandboxServer: 띄울 때마다 일회용 DB에 복제하고 DDL을 거기에만 반영합니다. 앱의 접속과 원본 폴더 경로도 사본 쪽으로 돌립니다(tools.build.sandbox.TestSandbox).
 *   격리를 보장할 수 없으면 원래 DB로 대신 띄우지 않고 실패하며, 실패의 원인을 cause로 알려 줍니다.
 *     code         개발 결과가 원인(DDL 오류, 빌드 실패, 앱 기동 실패). 고치면 된다
 *     environment  환경이 원인(지원하지 않는 구성, 설치·설정 없음, 외부 의존). 코드를 고쳐도 소용없다
 *   빌드한 파일만 띄웁니다: Spring Boot는 java -jar, 그 밖의 WAR는 설치된 Tomcat. Maven 플러그인으로 띄우는 방식은 격리 옵션을 확인할 수 없어 쓰지 않습니다.
 * </pre>
 */
@AiTool
public class TestServerTool extends BaseObject implements StepEndListener {

	/** 기본 작업 루트(사용자 홈 아래)입니다. */
	private static final String DEFAULT_ROOT_NAME = ".dstone-ai-engine";

	/** 사본 폴더 등 하위 이름 */
	private static final String APP_DIR = "app";
	private static final String LOG_FILE = "server.log";
	private static final String PID_FILE = "server.pid";
	private static final String LOCK_FILE = "server.lock";
	private static final String SOURCE_FILE = "source.txt";
	private static final String TOMCAT_BASE_DIR = "tomcat";

	/** 띄운 프로세스의 명령줄에 넣어 두는 표시입니다. 엔진이 죽어 남은 프로세스를 정리할 때 우리가 띄운 것인지 확인합니다. */
	private static final String MARKER = "-Ddstone.ai.test-server=";

	/** 실패할 때 결과에 붙일 로그 끝줄 수 */
	private static final int LOG_TAIL_LINES = 40;

	@Autowired
	private ConfigProperty configProperty;

	@Autowired
	private OverlayTool overlayTool;

	@Autowired
	private ProjectStore projectStore;

	@Autowired
	private SandboxRegistry sandboxRegistry;

	/** 이 엔진이 띄운 서버들(키: 프로젝트키) */
	private final Map<String, RunningServer> running = new ConcurrentHashMap<String, RunningServer>();

	/** 띄운 서버 하나의 정보입니다. */
	private static final class RunningServer {
		String key;
		String executionId;
		String stepId;
		Process process;
		RandomAccessFile lockFile;
		FileLock lock;
		String url;
		File log;
		TestSandbox sandbox;
	}

	/** startSandboxServer 한 번의 요청과, 실패했을 때의 원인 */
	private static final class SandboxRequest {
		String ddlFile;
		String cause = CAUSE_ENVIRONMENT;
	}

	/** 실패 원인: 개발 결과. 고치면 된다 */
	private static final String CAUSE_CODE = "code";

	/** 실패 원인: 환경. 코드를 고쳐도 소용없다 */
	private static final String CAUSE_ENVIRONMENT = "environment";

	/** 앱이 DB 로그인에 실패한 흔적. 프로젝트 관리의 계정이 앱의 계정과 다른 환경 문제다 */
	private static final Pattern LOGIN_FAILED = Pattern.compile("Access denied for user|password authentication failed for user");

	/** 띄우는 방법 하나(플러그인 종류에 따라 정함) */
	private static final class Launch {
		String kind;
		List<String> goals = new ArrayList<String>();
		String contextPath = "/";
	}

	@Tool(description = "원본 소스에 개발 결과를 얹은 사본으로 임시 시험 서버를 띄우고, 접속 주소와 서버 로그 파일 경로를 돌려준다. "
		+ "사람이 쓰는 서버는 건드리지 않는다. 이미 이 실행이 띄운 서버가 있으면 내리고 지금 개발 결과로 다시 띄운다(고친 뒤 확인할 때 다시 부른다). "
		+ "빌드와 기동을 기다리므로 몇 분 걸릴 수 있다. 다 쓰면 stopTestServer를 부른다(부르지 않아도 step이 끝나면 내려간다). "
		+ "Maven 프로젝트(tomcat, spring-boot, jetty 플러그인)만 띄울 수 있다. 사본은 원래 DB에 붙는다.")
	public ToolOutcome startTestServer(
			@ToolParam(required = false, description = "프로젝트ID(요청서의 프로젝트ID). Java 버전을 찾고 사본 폴더 이름에 쓴다. 없으면 비운다") String projectId,
			@ToolParam(description = "원본 소스 루트의 절대경로(읽기만 한다)") String sourceRoot,
			@ToolParam(description = "원본 루트 기준 같은 경로로 바뀐 파일·새 파일이 들어 있는 폴더(개발 결과)의 절대경로") String overlayDir,
			@ToolParam(required = false, description = "삭제 목록 파일의 절대경로. 원본 루트 기준 상대경로가 한 줄에 하나") String deleteList,
			ToolContext toolContext) {
		try {
			return this.start(projectId, sourceRoot, overlayDir, deleteList, StepRef.from(toolContext), null);
		} catch (IOException | RuntimeException e) {
			return ToolOutcome.fail("임시 시험 서버를 띄우지 못했습니다: " + e.getMessage());
		}
	}

	@Tool(description = "격리된 시험 서버를 띄운다. 프로젝트 DB를 일회용 DB로 복제하고 ddlFile의 DDL을 거기에만 반영하므로, 화면에서 등록·수정·삭제를 해도 원래 DB와 파일은 바뀌지 않는다. "
		+ "접속주소, 서버로그, dbQuery에 쓸 복제본 별칭(sandbox-...)을 돌려준다. 실패하면 cause가 code(개발 결과가 원인)나 environment(환경이 원인, 고쳐도 소용없음)다. "
		+ "다시 부르면 복제본부터 새로 만든다. 다 쓰면 stopTestServer를 부른다(부르지 않아도 step이 끝나면 내려간다).")
	public Map<String, Object> startSandboxServer(
			@ToolParam(description = "프로젝트ID. 복제할 DB를 프로젝트 관리에서 찾는다") String projectId,
			@ToolParam(description = "원본 소스 루트의 절대경로(읽기만 한다)") String sourceRoot,
			@ToolParam(description = "원본 루트 기준 같은 경로로 바뀐 파일·새 파일이 들어 있는 폴더(개발 결과)의 절대경로") String overlayDir,
			@ToolParam(required = false, description = "복제본에 반영할 DDL 파일(.sql, 또는 ```sql 블록이 든 .md)의 절대경로. 없으면 비운다") String ddlFile,
			@ToolParam(required = false, description = "삭제 목록 파일의 절대경로. 원본 루트 기준 상대경로가 한 줄에 하나") String deleteList,
			ToolContext toolContext) {
		SandboxRequest request = new SandboxRequest();
		request.ddlFile = ddlFile;
		ToolOutcome outcome;
		try {
			outcome = isBlank(projectId)
				? ToolOutcome.fail("프로젝트ID가 없어 복제할 DB를 알 수 없습니다. 자동 검증을 하지 못합니다.")
				: this.start(projectId, sourceRoot, overlayDir, deleteList, StepRef.from(toolContext), request);
		} catch (IOException | RuntimeException e) {
			request.cause = CAUSE_ENVIRONMENT;
			outcome = ToolOutcome.fail("격리 시험 서버를 띄우지 못했습니다: " + e.getMessage());
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("success", outcome.success());
		result.put("cause", Boolean.TRUE.equals(outcome.success()) ? "" : request.cause);
		result.put("message", outcome.message());
		return result;
	}

	@Tool(description = "startTestServer나 startSandboxServer로 띄운 임시 시험 서버를 내린다. 사본 폴더는 다음에 다시 쓰려고 남긴다.")
	public ToolOutcome stopTestServer(
			@ToolParam(required = false, description = "startTestServer에 준 프로젝트ID. 없으면 비운다") String projectId,
			@ToolParam(description = "startTestServer에 준 원본 소스 루트의 절대경로") String sourceRoot) {
		String key = keyOf(projectId, sourceRoot);
		RunningServer server = this.running.get(key);
		if (server == null) {
			return ToolOutcome.pass("떠 있는 임시 시험 서버가 없습니다: " + key);
		}
		this.stop(server);
		return ToolOutcome.pass("임시 시험 서버를 내렸습니다: " + key);
	}

	@Tool(description = "프로젝트 관리에 등록된 시험계정(화면 로그인용)을 알려 준다. 비밀번호는 알려 주지 않고, 비밀번호 칸에 글자 그대로 넣을 자리표시자를 준다. "
		+ "startTestServer 결과에도 같은 내용이 있다. 사람이 쓰는 시험 서버에 로그인할 때 쓴다.")
	public ToolOutcome getTestAccount(@ToolParam(description = "프로젝트ID(요청서의 프로젝트ID)") String projectId) {
		return ToolOutcome.pass(this.accountLine(projectId));
	}

	/** step이 끝나면 그 실행·step이 띄운 서버를 내립니다. */
	@Override
	public void stepEnded(String executionId, String stepId) {
		if (executionId != null && stepId != null) {
			this.sandboxRegistry.stepEnded(new StepRef(executionId, stepId));
		}
		Iterator<RunningServer> it = new ArrayList<RunningServer>(this.running.values()).iterator();
		while (it.hasNext()) {
			RunningServer server = it.next();
			if (executionId != null && executionId.equals(server.executionId) && stepId != null && stepId.equals(server.stepId)) {
				this.info("dstone-ai-engine tool: step[" + stepId + "]이 끝나 임시 시험 서버를 내립니다: " + server.key);
				this.stop(server);
			}
		}
	}

	/** 엔진이 내려갈 때 띄운 서버를 모두 내립니다. */
	@PreDestroy
	public void stopAll() {
		Iterator<RunningServer> it = new ArrayList<RunningServer>(this.running.values()).iterator();
		while (it.hasNext()) {
			this.stop(it.next());
		}
	}

	private ToolOutcome start(String projectId, String sourceRootText, String overlayText, String deleteList, StepRef stepRef, SandboxRequest request) throws IOException {
		if (isBlank(sourceRootText) || isBlank(overlayText)) {
			return ToolOutcome.fail("sourceRoot, overlayDir는 모두 필요합니다.");
		}
		File sourceRoot = new File(sourceRootText.trim()).getAbsoluteFile();
		File pom = new File(sourceRoot, "pom.xml");
		if (!pom.isFile()) {
			return ToolOutcome.fail("원본에 pom.xml이 없습니다. 임시 시험 서버는 Maven 프로젝트만 띄울 수 있습니다: " + sourceRoot);
		}
		String key = keyOf(projectId, sourceRootText);
		String executionId = stepRef == null ? null : stepRef.executionId();
		File base = new File(this.workRoot(), key + "-server");
		Files.createDirectories(base.toPath());

		// 1) 같은 실행이 띄운 서버가 있으면 내린다(고친 뒤 다시 띄우는 경우). 다른 실행이 쓰는 중이면 기다린다.
		RunningServer previous = this.running.get(key);
		if (previous != null && sameExecution(previous.executionId, executionId)) {
			this.stop(previous);
		}
		RunningServer server = new RunningServer();
		server.key = key;
		server.executionId = executionId;
		server.stepId = stepRef == null ? null : stepRef.stepId();
		String lockProblem = this.acquireLock(server, base);
		if (lockProblem != null) {
			return ToolOutcome.fail(lockProblem);
		}
		boolean started = false;
		try {
			// 2) 엔진이 죽어서 남아 있던 프로세스가 있으면 정리한다(잠금을 얻었으니 주인은 없다).
			this.killLeftover(base, key);

			// 3) 사본을 맞춘다. 다른 원본으로 만든 사본이면 새로 만든다.
			File app = new File(base, APP_DIR);
			File sourceFile = new File(base, SOURCE_FILE);
			String recorded = sourceFile.isFile() ? Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8).trim() : "";
			if (!recorded.equals(sourceRoot.getPath()) && app.exists()) {
				deleteTree(app.toPath());
			}
			ToolOutcome overlay = this.overlayTool.applyOverlay(sourceRoot.getPath(), overlayText.trim(), app.getPath(), deleteList, null);
			if (!Boolean.TRUE.equals(overlay.success())) {
				return ToolOutcome.fail("사본을 맞추지 못했습니다: " + overlay.message());
			}
			Files.writeString(sourceFile.toPath(), sourceRoot.getPath(), StandardCharsets.UTF_8);

			if (request != null) {
				String problem = this.createSandbox(server, projectId.trim(), base, sourceRoot, app, request);
				if (problem != null) {
					return ToolOutcome.fail(problem);
				}
			}

			// 4) 포트와 JDK를 정한다.
			File appPom = new File(app, "pom.xml");
			String pomText = Files.readString(appPom.toPath(), StandardCharsets.UTF_8);
			int port = freePort();
			List<String> notes = new ArrayList<String>();
			File javaHome = this.javaHome(projectId, pomText, sourceRoot, notes);
			File log = new File(base, LOG_FILE);
			Files.deleteIfExists(log.toPath());
			server.log = log;

			// 5) 띄운다. 출력은 server.log로 보낸다.
			File tomcatHome = this.tomcatHome();
			String kind;
			String contextPath;
			Process process;
			long runLogFrom = 0L;
			boolean bootArchive = request != null && pluginBlock(pomText, "spring-boot-maven-plugin") != null;
			if (request != null && !bootArchive && !(isWar(pomText) && tomcatHome != null)) {
				return ToolOutcome.fail("격리 시험 서버로 띄울 수 없는 구성입니다(되는 것: Spring Boot 단일 모듈, 또는 WAR + 설정 tomcat-home). 자동 검증을 하지 못합니다.");
			}
			if (bootArchive) {
				// Boot는 실제 배포처럼 java -jar로 띄운다(Boot 3 이상은 설치된 Tomcat 9에 올릴 수 없기도 하다).
				String buildProblem = this.mavenPackage(app, javaHome, log, key, server.sandbox);
				if (buildProblem != null) {
					request.cause = CAUSE_CODE;
					return ToolOutcome.fail("빌드하지 못했습니다(" + buildProblem + "). 서버 로그: " + log + "\n" + tail(log, LOG_TAIL_LINES));
				}
				File archive = findBootArchive(new File(app, "target"));
				if (archive == null) {
					return ToolOutcome.fail("mvn package 뒤에 실행할 jar나 WAR가 없습니다: " + app);
				}
				String problem = prepareRun(server.sandbox, archive, port, forProject(this.config("sandbox-trusted-libs", ""), projectId));
				if (problem != null) {
					return ToolOutcome.fail(problem);
				}
				kind = "java -jar " + archive.getName();
				contextPath = "/";
				runLogFrom = log.length();
				process = this.startArchive(app, archive, javaHome, log, key, port, server.sandbox);
			} else if (isWar(pomText) && tomcatHome != null) {
				String tomcatPath = tomcatPathOf(pomText);
				contextPath = tomcatPath == null ? "/" : tomcatPath;
				kind = "WAR + Tomcat(" + tomcatHome.getPath() + ", 전용 CATALINA_BASE)";
				String buildProblem = this.mavenPackage(app, javaHome, log, key, server.sandbox);
				if (buildProblem != null) {
					if (request != null) {
						request.cause = CAUSE_CODE;
					}
					return ToolOutcome.fail("WAR를 만들지 못했습니다(" + buildProblem + "). 서버 로그: " + log + "\n" + tail(log, LOG_TAIL_LINES));
				}
				File war = findWar(new File(app, "target"));
				if (war == null) {
					return ToolOutcome.fail("mvn package 뒤에 target 아래 WAR 파일이 없습니다: " + app);
				}
				File catalinaBase = new File(base, TOMCAT_BASE_DIR);
				if (request != null) {
					String problem = prepareRun(server.sandbox, war, port, forProject(this.config("sandbox-trusted-libs", ""), projectId));
					if (problem != null) {
						return ToolOutcome.fail(problem);
					}
				}
				prepareCatalinaBase(tomcatHome, catalinaBase, port, war, contextPath);
				runLogFrom = log.length();
				process = this.startCatalina(tomcatHome, catalinaBase, javaHome, log, key, server.sandbox);
			} else {
				Launch launch = launchOf(pomText, port);
				if (launch == null) {
					return ToolOutcome.fail("pom.xml에서 서버를 띄울 방법을 찾지 못했습니다. WAR 프로젝트는 설정 tomcat-home(env의 TEST_SERVER_TOMCAT_HOME)을 채우고, "
						+ "아니면 tomcat7/tomcat6, spring-boot, jetty 플러그인이 있어야 합니다. 이 프로젝트는 요청서 시험환경의 반영폴더·반영명령으로 시험 서버에 올려야 합니다.");
				}
				if (launch.kind.startsWith("tomcat")) {
					// tomcat 플러그인은 pom에 적힌 <port>가 명령줄 값보다 우선이라, 사본의 pom에서만 바꾼다(원본은 그대로).
					Files.writeString(appPom.toPath(), patchTomcatPort(pomText, port), StandardCharsets.UTF_8);
				}
				kind = "mvn " + launch.goals.get(0);
				contextPath = launch.contextPath;
				List<String> command = this.mavenCommand(key);
				command.addAll(launch.goals);
				ProcessBuilder builder = new ProcessBuilder(command).directory(app).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log));
				utf8Maven(builder);
				if (javaHome != null) {
					builder.environment().put("JAVA_HOME", javaHome.getPath());
				}
				process = builder.start();
			}
			Files.writeString(new File(base, PID_FILE).toPath(), String.valueOf(process.pid()), StandardCharsets.UTF_8);
			server.process = process;
			server.url = "http://localhost:" + port + contextPath;
			this.running.put(key, server);

			// 6) 접속될 때까지 기다린다.
			int timeout = Integer.parseInt(this.config("start-timeout-seconds", "600"));
			String waitProblem = waitUntilUp(process, port, contextPath, timeout);
			if (waitProblem != null) {
				if (request != null) {
					request.cause = startupCause(server.sandbox, log, runLogFrom);
					waitProblem += deniedNote(server.sandbox);
				}
				return ToolOutcome.fail("임시 시험 서버가 뜨지 않았습니다(" + waitProblem + "). 서버 로그: " + log + "\n" + tail(log, LOG_TAIL_LINES));
			}
			// Tomcat에 올린 앱이 시작에 실패해도 Tomcat 자체는 떠서 응답(404)을 준다. 그 경우는 실패로 본다.
			if (kind.startsWith("WAR") && httpStatus(port, contextPath) == HttpURLConnection.HTTP_NOT_FOUND) {
				if (request != null) {
					request.cause = startupCause(server.sandbox, log, runLogFrom);
				}
				return ToolOutcome.fail("Tomcat은 떴지만 앱(" + contextPath + ")이 시작되지 않았습니다(404). 서버 로그에서 앱 시작 오류를 보십시오: " + log + "\n"
					+ tail(log, LOG_TAIL_LINES) + deniedNote(server.sandbox));
			}
			// 격리 옵션을 JVM이 정말 받았는지 본다. JVM은 JAVA_TOOL_OPTIONS를 받으면 그렇다고 로그에 찍는다.
			if (server.sandbox != null && !textFrom(log, runLogFrom).contains("-DsocksProxyHost=127.0.0.1")) {
				return ToolOutcome.fail("앱 JVM에 격리 옵션이 들어갔는지 확인할 수 없어 내렸습니다(원래 DB에 쓸 수 있어서).");
			}
			started = true;
			StringBuilder message = new StringBuilder();
			message.append("임시 시험 서버를 띄웠습니다.\n");
			message.append("접속주소: ").append(server.url).append('\n');
			message.append("서버로그: ").append(log.getPath()).append('\n');
			message.append("방식: ").append(kind).append(", JDK: ").append(javaHome == null ? "엔진과 같은 JDK(" + System.getProperty("java.version") + ")" : javaHome.getPath()).append('\n');
			message.append("사본: ").append(app.getPath()).append(" (").append(overlay.message()).append(")");
			message.append('\n').append(this.accountLine(projectId));
			if (server.sandbox != null) {
				Map<String, Map<String, String>> clones = server.sandbox.readConnections();
				this.sandboxRegistry.opened(stepRef, server.url, clones);
				message.append("\n복제본 DB 별칭(dbQuery의 alias): ").append(String.join(", ", clones.keySet()));
				for (String line : server.sandbox.report()) {
					message.append('\n').append(line);
				}
				message.append(deniedNote(server.sandbox));
				String sample = this.uploadSample();
				if (sample != null) {
					message.append("\n업로드 시험용 파일(browser_file_upload의 paths): ").append(sample);
				}
			}
			for (String note : notes) {
				message.append('\n').append(note);
			}
			return ToolOutcome.pass(message.toString());
		} finally {
			if (!started) {
				this.stop(server);
			}
		}
	}

	/** 격리 환경을 만들어 server에 답니다. 실패면 이유. */
	private String createSandbox(RunningServer server, String projectId, File base, File sourceRoot, File app, SandboxRequest request) {
		Map<String, Map<String, String>> connections = new LinkedHashMap<String, Map<String, String>>();
		for (Map.Entry<String, Map<String, String>> entry : this.projectStore.connections().entrySet()) {
			if (entry.getKey().startsWith(projectId + ".")) {
				connections.put(entry.getKey(), entry.getValue());
			}
		}
		Map<String, String> homes = new LinkedHashMap<String, String>();
		for (String kind : TestSandbox.kinds()) {
			homes.put(kind, this.config("sandbox-" + kind + "-home", ""));
		}
		try {
			server.sandbox = TestSandbox.create(base, sourceRoot, app, connections, request.ddlFile, homes,
				Integer.parseInt(this.config("sandbox-max-db-mb", "2048")), forProject(this.config("sandbox-allow-hosts", ""), projectId));
			return null;
		} catch (SandboxException e) {
			request.cause = e.code() ? CAUSE_CODE : CAUSE_ENVIRONMENT;
			return e.getMessage();
		}
	}

	/** 빌드 산출물이 격리를 지킬 수 있는지 보고, 앱이 자기 포트를 부를 수 있게 엽니다. 실패면 이유. */
	private static String prepareRun(TestSandbox sandbox, File archive, int port, String trustedLibs) {
		try {
			sandbox.checkArtifact(archive, trustedLibs);
			sandbox.allowSelf(port);
			return null;
		} catch (SandboxException e) {
			return e.getMessage();
		}
	}

	/** 기동 실패의 원인. 막은 외부 접속이나 DB 로그인 실패가 있으면 환경, 아니면 개발 결과 */
	private static String startupCause(TestSandbox sandbox, File log, long from) {
		return !sandbox.denied().isEmpty() || LOGIN_FAILED.matcher(textFrom(log, from)).find() ? CAUSE_ENVIRONMENT : CAUSE_CODE;
	}

	/**
	 * <pre>
	 * "프로젝트ID=값, 프로젝트ID=값" 모양의 설정에서 그 프로젝트의 값만 쉼표로 이어 돌려줍니다.
	 * 격리를 푸는 예외라 엔진 전체에 걸지 않습니다. 한 프로젝트 때문에 푼 것이 다른 프로젝트까지 풀면 안 됩니다.
	 * </pre>
	 */
	static String forProject(String setting, String projectId) {
		List<String> values = new ArrayList<String>();
		if (!isBlank(setting) && !isBlank(projectId)) {
			for (String item : setting.split(",")) {
				int equals = item.indexOf('=');
				if (equals > 0 && item.substring(0, equals).trim().equals(projectId.trim())) {
					values.add(item.substring(equals + 1).trim());
				}
			}
		}
		return String.join(",", values);
	}

	private static String deniedNote(TestSandbox sandbox) {
		if (sandbox == null || sandbox.denied().isEmpty()) {
			return "";
		}
		return "\n막은 외부 접속: " + String.join(", ", sandbox.denied()) + " (앱에 꼭 필요하면 관리자가 설정 sandbox-allow-hosts에 '프로젝트ID=host:port'로 더한다)";
	}

	private static String textFrom(File log, long from) {
		try (RandomAccessFile file = new RandomAccessFile(log, "r")) {
			file.seek(Math.min(from, file.length()));
			byte[] bytes = new byte[(int) (file.length() - file.getFilePointer())];
			file.readFully(bytes);
			return new String(bytes, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "";
		}
	}

	/** 업로드 화면을 시험할 그림 파일. 브라우저 MCP가 읽어도 되는 폴더(설정 upload-sample-dir)에 둡니다. */
	private String uploadSample() {
		String dir = this.config("upload-sample-dir", "");
		// env의 MCP 폴더가 비면 "/output"만 남는다. 그때는 만들지 않는다
		if (dir.isEmpty() || dir.equals("/output") || !new File(dir).getParentFile().isDirectory()) {
			return null;
		}
		File sample = new File(dir, "dstone-upload-sample.png");
		try {
			if (!sample.isFile()) {
				Files.createDirectories(sample.getParentFile().toPath());
				ImageIO.write(new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB), "png", sample);
			}
			return sample.getAbsolutePath();
		} catch (IOException e) {
			this.warn("dstone-ai-engine tool: 업로드 시험용 파일을 만들지 못했습니다: " + e.getMessage());
			return null;
		}
	}

	/** Spring Boot가 만든 실행 파일(jar 또는 WAR)을 찾습니다. */
	private static File findBootArchive(File target) {
		File[] files = target.listFiles();
		if (files == null) {
			return null;
		}
		for (File file : files) {
			String name = file.getName();
			if (file.isFile() && (name.endsWith(".war") || (name.endsWith(".jar")
				&& !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar") && !name.endsWith("-plain.jar")))) {
				return file;
			}
		}
		return null;
	}

	/** Spring Boot 실행 파일을 격리 옵션과 함께 띄웁니다. */
	private Process startArchive(File app, File archive, File javaHome, File log, String key, int port, TestSandbox sandbox) throws IOException {
		File home = javaHome != null ? javaHome : new File(System.getProperty("java.home"));
		List<String> command = new ArrayList<String>();
		command.add(new File(home, isWindows() ? "bin/java.exe" : "bin/java").getPath());
		command.add(MARKER + key);
		command.addAll(Arrays.asList(UTF8_OUTPUT.split(" ")));
		command.add("-jar");
		command.add(archive.getPath());
		command.add("--server.port=" + port);
		command.add("--server.servlet.context-path=/");
		ProcessBuilder builder = new ProcessBuilder(command).directory(app).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log));
		isolate(builder, sandbox.appOptions());
		return builder.start();
	}

	/**
	 * JVM에 격리 옵션을 넣습니다. JAVA_TOOL_OPTIONS는 스크립트가 JAVA_OPTS를 덮어써도, 자식 JVM을 새로 띄워도 들어갑니다.
	 * 이것을 덮어쓸 수 있는 _JAVA_OPTIONS는 뺍니다.
	 */
	private static void isolate(ProcessBuilder builder, String options) {
		Map<String, String> env = builder.environment();
		String current = env.get("JAVA_TOOL_OPTIONS");
		env.put("JAVA_TOOL_OPTIONS", isBlank(current) ? options : current + " " + options);
		env.remove("_JAVA_OPTIONS");
	}

	/**
	 * <pre>
	 * 화면에 로그인할 시험계정 안내 한 줄입니다(프로젝트 관리의 시험계정).
	 * 비밀번호는 알려 주지 않고 자리표시자만 줍니다. 엔진(ToolCallGate)이 브라우저 입력 Tool에 넘길 때만 실제 값으로 바꿉니다.
	 * </pre>
	 */
	private String accountLine(String projectId) {
		if (isBlank(projectId)) {
			return "시험계정: 없음(프로젝트ID가 없음). 로그인이 필요 없는 화면만 볼 수 있습니다.";
		}
		try {
			Map<String, Object> project = this.projectStore.find(projectId.trim());
			Object userId = project == null ? null : project.get("testUserId");
			if (userId == null || isBlank(userId.toString())) {
				return "시험계정: 없음(프로젝트 관리에 시험계정이 없음). 로그인이 필요 없는 화면만 볼 수 있습니다.";
			}
			if (!"Y".equals(project.get("hasTestPassword"))) {
				return "시험계정: ID " + userId + " (비밀번호는 프로젝트 관리에 없음)";
			}
			return "시험계정: ID " + userId + ", 비밀번호 칸에는 ${secret:" + projectId.trim() + "." + ProjectStore.SECRET_TEST_PASSWORD
				+ "}를 글자 그대로 넣는다(browser_type, browser_fill_form에서만 엔진이 실제 값으로 바꿔 넣는다)";
		} catch (RuntimeException e) {
			return "시험계정: 읽지 못했습니다(" + e.getMessage() + ")";
		}
	}

	/**
	 * <pre>
	 * 프로젝트 잠금을 잡습니다. 다른 실행(이 엔진이든 다른 엔진이든)이 쓰고 있으면 lock-wait-seconds 동안 기다립니다.
	 * 잡으면 null, 못 잡으면 그 이유를 돌려줍니다.
	 * </pre>
	 */
	private String acquireLock(RunningServer server, File base) throws IOException {
		long waitMillis = Long.parseLong(this.config("lock-wait-seconds", "60")) * 1000L;
		long deadline = System.currentTimeMillis() + waitMillis;
		RandomAccessFile file = new RandomAccessFile(new File(base, LOCK_FILE), "rw");
		FileChannel channel = file.getChannel();
		while (true) {
			FileLock lock = null;
			try {
				lock = channel.tryLock();
			} catch (OverlappingFileLockException e) {
				lock = null; // 이 엔진의 다른 실행이 잡고 있다
			}
			if (lock != null) {
				server.lockFile = file;
				server.lock = lock;
				return null;
			}
			if (System.currentTimeMillis() >= deadline) {
				file.close();
				RunningServer holder = this.running.get(server.key);
				return "다른 실행이 이 프로젝트의 임시 시험 서버를 쓰고 있습니다" + (holder == null || holder.executionId == null ? "" : "(실행 " + holder.executionId + ")")
					+ ". 그 실행이 끝난 뒤 다시 부르십시오.";
			}
			try {
				Thread.sleep(2000L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				file.close();
				return "기다리는 중에 중단되었습니다.";
			}
		}
	}

	/** 서버 하나를 내리고 잠금을 풉니다. 여러 번 불러도 됩니다. */
	private void stop(RunningServer server) {
		if (server.process != null) {
			killTree(server.process.toHandle());
		}
		this.running.remove(server.key, server);
		if (server.sandbox != null) {
			this.sandboxRegistry.closed(server.sandbox.readConnections().keySet());
			server.sandbox.close();
		}
		try {
			if (server.lock != null && server.lock.isValid()) {
				server.lock.release();
			}
			if (server.lockFile != null) {
				server.lockFile.close();
			}
		} catch (IOException e) {
			this.warn("dstone-ai-engine tool: 임시 시험 서버 잠금을 풀지 못했습니다(" + server.key + "): " + e.getMessage());
		}
	}

	/** 엔진이 죽어 남은 프로세스를 정리합니다. 명령줄에 우리 표시가 있을 때만 내립니다(번호가 다른 프로세스에 다시 쓰였을 수 있어서). */
	private void killLeftover(File base, String key) {
		File pidFile = new File(base, PID_FILE);
		if (!pidFile.isFile()) {
			return;
		}
		try {
			long pid = Long.parseLong(Files.readString(pidFile.toPath(), StandardCharsets.UTF_8).trim());
			Optional<ProcessHandle> handle = ProcessHandle.of(pid);
			if (handle.isPresent() && handle.get().isAlive()) {
				Optional<String> commandLine = handle.get().info().commandLine();
				if (commandLine.isPresent() && commandLine.get().contains(MARKER + key)) {
					this.info("dstone-ai-engine tool: 남아 있던 임시 시험 서버 프로세스를 내립니다: " + key + " pid=" + pid);
					killTree(handle.get());
				}
			}
			Files.deleteIfExists(pidFile.toPath());
		} catch (IOException | RuntimeException e) {
			this.warn("dstone-ai-engine tool: 남은 임시 시험 서버를 확인하지 못했습니다(" + key + "): " + e.getMessage());
		}
	}

	/** 프로세스와 그 자식들을 내립니다. 먼저 정상 종료를 요청하고, 10초 안에 안 끝나면 강제로 내립니다. */
	private static void killTree(ProcessHandle root) {
		List<ProcessHandle> all = new ArrayList<ProcessHandle>();
		Iterator<ProcessHandle> descendants = root.descendants().iterator();
		while (descendants.hasNext()) {
			all.add(descendants.next());
		}
		all.add(root);
		for (ProcessHandle handle : all) {
			handle.destroy();
		}
		long deadline = System.currentTimeMillis() + 10000L;
		for (ProcessHandle handle : all) {
			while (handle.isAlive() && System.currentTimeMillis() < deadline) {
				try {
					Thread.sleep(200L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			if (handle.isAlive()) {
				handle.destroyForcibly();
			}
		}
	}

	/**
	 * <pre>
	 * 쓸 JDK를 정합니다. 버전 순서: 프로젝트 관리의 Java 버전 → 원본 pom.xml → 원본 클래스 파일.
	 * 경로는 설정 java-home-{버전}. 못 찾으면 null(엔진과 같은 JDK)이고 notes에 이유를 남깁니다.
	 * </pre>
	 */
	private File javaHome(String projectId, String pomText, File sourceRoot, List<String> notes) {
		String version = null;
		String from = null;
		if (!isBlank(projectId)) {
			try {
				Map<String, Object> project = this.projectStore.find(projectId.trim());
				Object value = project == null ? null : project.get("javaVersion");
				if (value != null && !isBlank(value.toString())) {
					version = value.toString().trim();
					from = "프로젝트 관리";
				}
			} catch (RuntimeException e) {
				notes.add("프로젝트 관리에서 Java 버전을 읽지 못했습니다: " + e.getMessage());
			}
		}
		if (version == null) {
			version = javaVersionOfPom(pomText);
			from = version == null ? null : "pom.xml";
		}
		if (version == null) {
			version = javaVersionOfClasses(new File(sourceRoot, "target/classes"));
			from = version == null ? null : "원본 클래스 파일";
		}
		if (version == null) {
			notes.add("참고: Java 버전을 알 수 없어 엔진과 같은 JDK로 띄웠습니다. 프로젝트 관리에 Java 버전을 넣으면 그 JDK를 씁니다.");
			return null;
		}
		String path = this.config("java-home-" + version, "");
		if (!isBlank(path) && new File(path.trim()).isDirectory()) {
			return new File(path.trim());
		}
		if (version.equals(String.valueOf(Runtime.version().feature()))) {
			return null; // 엔진과 같은 버전이면 그대로 쓴다
		}
		notes.add("참고: Java " + version + "(" + from + ")용 JDK 경로가 설정(env의 JAVA_HOME_" + version + ")에 없어 엔진과 같은 JDK(Java "
			+ Runtime.version().feature() + ")로 띄웠습니다. 서버가 이상하게 동작하면 이것부터 의심하십시오.");
		return null;
	}

	/** pom.xml에 적힌 컴파일 버전을 읽습니다(1.8 → 8). 못 찾으면 null. */
	static String javaVersionOfPom(String pomText) {
		String[] tags = { "maven.compiler.release", "maven.compiler.source", "java.version", "java-version", "release", "source" };
		for (String tag : tags) {
			Matcher m = Pattern.compile("<" + Pattern.quote(tag) + ">\\s*([0-9.]+)\\s*</" + Pattern.quote(tag) + ">").matcher(pomText);
			if (m.find()) {
				String v = m.group(1);
				return v.startsWith("1.") ? v.substring(2) : v;
			}
		}
		return null;
	}

	/** 원본 클래스 파일 하나의 major 버전으로 Java 버전을 정합니다(52 → 8). 못 찾으면 null. */
	private static String javaVersionOfClasses(File classesDir) {
		if (!classesDir.isDirectory()) {
			return null;
		}
		final File[] found = new File[1];
		try {
			Files.walkFileTree(classesDir.toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
					if (path.toString().endsWith(".class")) {
						found[0] = path.toFile();
						return FileVisitResult.TERMINATE;
					}
					return FileVisitResult.CONTINUE;
				}
			});
			if (found[0] == null) {
				return null;
			}
			byte[] head = Files.readAllBytes(found[0].toPath());
			if (head.length >= 8) {
				int major = ((head[6] & 0xff) << 8) | (head[7] & 0xff);
				return String.valueOf(major - 44);
			}
		} catch (IOException e) {
			// 읽지 못하면 모르는 것으로
		}
		return null;
	}

	/**
	 * <pre>
	 * pom.xml의 플러그인을 보고 띄우는 방법을 정합니다. 못 정하면 null.
	 * tomcat은 tomcat7:run 기본 경로가 /artifactId 라서, 플러그인에 <path>가 없으면 그것을 씁니다.
	 * </pre>
	 */
	static Launch launchOf(String pomText, int port) {
		Launch launch = new Launch();
		String tomcat = pluginBlock(pomText, "tomcat7-maven-plugin");
		String tomcatGoal = "tomcat7:run";
		if (tomcat == null) {
			tomcat = pluginBlock(pomText, "tomcat6-maven-plugin");
			tomcatGoal = "tomcat6:run";
		}
		if (tomcat != null) {
			launch.kind = tomcatGoal.substring(0, tomcatGoal.indexOf(':'));
			launch.goals.add("clean"); // 지난 실행의 클래스가 남지 않게(mavenPackage 설명 참고)
			launch.goals.add(tomcatGoal);
			launch.goals.add("-Dmaven.tomcat.port=" + port);
			Matcher path = Pattern.compile("<path>\\s*([^<$]*)\\s*</path>").matcher(tomcat);
			if (path.find()) {
				launch.contextPath = normalizePath(path.group(1));
			} else {
				String artifactId = projectArtifactId(pomText);
				launch.contextPath = artifactId == null ? "/" : "/" + artifactId;
			}
			return launch;
		}
		if (pluginBlock(pomText, "spring-boot-maven-plugin") != null) {
			launch.kind = "spring-boot";
			launch.goals.add("clean");
			launch.goals.add("spring-boot:run");
			launch.goals.add("-Dspring-boot.run.arguments=--server.port=" + port); // Boot 2 이상
			launch.goals.add("-Drun.arguments=--server.port=" + port); // Boot 1
			return launch;
		}
		if (pluginBlock(pomText, "jetty-maven-plugin") != null || pluginBlock(pomText, "maven-jetty-plugin") != null) {
			launch.kind = "jetty";
			launch.goals.add("clean");
			launch.goals.add("jetty:run");
			launch.goals.add("-Djetty.http.port=" + port);
			launch.goals.add("-Djetty.port=" + port);
			return launch;
		}
		return null;
	}

	/** tomcat 플러그인 블록 안의 <port> 값만 바꿉니다. 블록에 <port>가 없으면 그대로 둡니다(-Dmaven.tomcat.port가 쓰임). */
	static String patchTomcatPort(String pomText, int port) {
		Matcher m = PLUGIN.matcher(pomText);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (m.find()) {
			String block = m.group();
			if (block.contains("tomcat7-maven-plugin") || block.contains("tomcat6-maven-plugin")) {
				String patched = block.replaceFirst("<port>[^<]*</port>", Matcher.quoteReplacement("<port>" + port + "</port>"));
				out.append(pomText, last, m.start()).append(patched);
				last = m.end();
			}
		}
		out.append(pomText.substring(last));
		return out.toString();
	}

	/** <plugin>…</plugin> 하나(중첩 없음) */
	private static final Pattern PLUGIN = Pattern.compile("<plugin>(?:(?!</plugin>).)*</plugin>", Pattern.DOTALL);

	private static String pluginBlock(String pomText, String artifactId) {
		Matcher m = PLUGIN.matcher(pomText);
		while (m.find()) {
			if (m.group().contains("<artifactId>" + artifactId + "</artifactId>")) {
				return m.group();
			}
		}
		return null;
	}

	/** 프로젝트 자신의 artifactId(parent, 의존성, 플러그인 것 제외)입니다. */
	private static String projectArtifactId(String pomText) {
		String text = pomText.replaceAll("(?s)<parent>.*?</parent>", "")
			.replaceAll("(?s)<dependencies>.*?</dependencies>", "")
			.replaceAll("(?s)<dependencyManagement>.*?</dependencyManagement>", "")
			.replaceAll("(?s)<build>.*?</build>", "")
			.replaceAll("(?s)<profiles>.*?</profiles>", "");
		Matcher m = Pattern.compile("<artifactId>\\s*([^<]+?)\\s*</artifactId>").matcher(text);
		return m.find() ? m.group(1) : null;
	}

	private static String normalizePath(String path) {
		String p = path.trim();
		if (p.isEmpty()) {
			return "/";
		}
		return p.startsWith("/") ? p : "/" + p;
	}

	/**
	 * <pre>
	 * 접속될 때까지 기다립니다. HTTP 응답이 오면(상태 코드는 무엇이든) 뜬 것으로 봅니다.
	 * 프로세스가 먼저 끝나거나 시간이 지나면 그 이유를 돌려줍니다. 뜨면 null.
	 * </pre>
	 */
	private static String waitUntilUp(Process process, int port, String contextPath, int timeoutSeconds) {
		long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
		while (System.currentTimeMillis() < deadline) {
			if (!process.isAlive()) {
				return "프로세스가 끝났습니다. 종료 코드 " + process.exitValue();
			}
			try {
				HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + port + contextPath).toURL().openConnection();
				connection.setConnectTimeout(2000);
				connection.setReadTimeout(10000);
				connection.setInstanceFollowRedirects(false);
				int status = connection.getResponseCode();
				connection.disconnect();
				if (status > 0) {
					return null;
				}
			} catch (IOException e) {
				// 아직 안 떴다
			}
			try {
				process.waitFor(3, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return "기다리는 중에 중단되었습니다";
			}
		}
		return timeoutSeconds + "초 안에 접속되지 않았습니다";
	}

	/** 그 경로에 GET을 보내 응답 코드를 돌려줍니다. 응답이 없으면 -1. */
	private static int httpStatus(int port, String path) {
		try {
			HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
			connection.setConnectTimeout(2000);
			connection.setReadTimeout(30000);
			connection.setInstanceFollowRedirects(false);
			int status = connection.getResponseCode();
			connection.disconnect();
			return status;
		} catch (IOException e) {
			return -1;
		}
	}

	private static int freePort() throws IOException {
		ServerSocket socket = new ServerSocket(0);
		try {
			return socket.getLocalPort();
		} finally {
			socket.close();
		}
	}

	private static String tail(File log, int lines) {
		try {
			// UTF-8이 아닌 글자가 섞여 있어도 실패하지 않게, 깨진 글자만 대체해서 읽는다.
			String text = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
			List<String> all = Arrays.asList(text.split("\\r?\\n", -1));
			int from = Math.max(0, all.size() - lines);
			return String.join("\n", all.subList(from, all.size()));
		} catch (IOException | RuntimeException e) {
			return "(로그를 읽지 못했습니다: " + e.getMessage() + ")";
		}
	}

	/** 띄운 JVM의 콘솔 출력을 UTF-8로 맞추는 옵션입니다(server.log를 읽을 수 있게). */
	private static final String UTF8_OUTPUT = "-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8";

	/** Maven 출력도 UTF-8로 맞춥니다. 이미 있는 MAVEN_OPTS 뒤에 덧붙입니다. */
	private static void utf8Maven(ProcessBuilder builder) {
		Map<String, String> env = builder.environment();
		String current = env.get("MAVEN_OPTS");
		env.put("MAVEN_OPTS", current == null || current.isBlank() ? UTF8_OUTPUT : current + " " + UTF8_OUTPUT);
	}

	/** mvn 명령 앞부분(Windows는 cmd.exe /c, 우리 표시 포함)입니다. */
	private List<String> mavenCommand(String key) {
		List<String> command = new ArrayList<String>();
		if (isWindows()) {
			command.add("cmd.exe");
			command.add("/c");
		}
		command.add(this.config("maven-command", "mvn"));
		command.add("-B");
		command.add("-DskipTests");
		command.add(MARKER + key);
		return command;
	}

	/** 사본에서 mvn package를 돌려 WAR를 만듭니다. 성공하면 null, 실패하면 이유. 출력은 log에 덧붙입니다. */
	private String mavenPackage(File app, File javaHome, File log, String key, TestSandbox sandbox) throws IOException {
		List<String> command = this.mavenCommand(key);
		// clean을 함께 한다. 사본은 다음 실행에서도 다시 쓰는데, Maven 증분 빌드는 소스가 없어진 클래스를 지우지 않아
		// 지난 실행의 클래스가 WAR에 남는다(2026-11-22 실행 86b3660b: 지난 실행의 DAO와 bean 이름이 부딪쳐 앱이 뜨지 않음).
		command.add("clean");
		command.add("package");
		ProcessBuilder builder = new ProcessBuilder(command).directory(app).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log));
		utf8Maven(builder);
		if (javaHome != null) {
			builder.environment().put("JAVA_HOME", javaHome.getPath());
		}
		if (sandbox != null) {
			isolate(builder, sandbox.buildOptions()); // 빌드 중에도 원래 DB로는 가지 못하게
		}
		Process process = builder.start();
		try {
			int timeout = Integer.parseInt(this.config("start-timeout-seconds", "600"));
			if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
				killTree(process.toHandle());
				return timeout + "초 안에 끝나지 않았습니다";
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			killTree(process.toHandle());
			return "기다리는 중에 중단되었습니다";
		}
		return process.exitValue() == 0 ? null : "mvn package 종료 코드 " + process.exitValue();
	}

	/** 전용 CATALINA_BASE로 Tomcat을 띄웁니다(catalina run, 앞에서 돌아감). */
	private Process startCatalina(File tomcatHome, File catalinaBase, File javaHome, File log, String key, TestSandbox sandbox) throws IOException {
		List<String> command = new ArrayList<String>();
		if (isWindows()) {
			command.add("cmd.exe");
			command.add("/c");
			command.add(new File(tomcatHome, "bin/catalina.bat").getPath());
		} else {
			command.add("sh");
			command.add(new File(tomcatHome, "bin/catalina.sh").getPath());
		}
		command.add("run");
		ProcessBuilder builder = new ProcessBuilder(command).directory(catalinaBase).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log));
		Map<String, String> env = builder.environment();
		env.put("CATALINA_HOME", tomcatHome.getPath());
		env.put("CATALINA_BASE", catalinaBase.getPath());
		env.put("CATALINA_PID", ""); // 사람이 쓰는 Tomcat의 PID 파일을 건드리지 않게
		env.put("CATALINA_OPTS", MARKER + key + " " + UTF8_OUTPUT);
		if (javaHome != null) {
			env.put("JAVA_HOME", javaHome.getPath());
			env.remove("JRE_HOME");
		}
		if (sandbox != null) {
			isolate(builder, sandbox.appOptions());
		}
		return builder.start();
	}

	/**
	 * <pre>
	 * 전용 CATALINA_BASE를 만듭니다. 매번 conf를 설치된 Tomcat에서 다시 복사하고, webapps·work·temp는 비웁니다.
	 * server.xml은 shutdown 포트를 끄고(-1), 주석이 아닌 첫 HTTP Connector의 포트만 바꾸고, 나머지 Connector(AJP, HTTPS)는 지웁니다.
	 * 사람이 그 Tomcat에 등록해 둔 다른 앱(server.xml의 <Context>, conf/Catalina 아래 파일)은 뺍니다. 그 앱의 경로가 없으면 Host 기동이 실패합니다.
	 * </pre>
	 */
	static void prepareCatalinaBase(File tomcatHome, File catalinaBase, int port, File war, String contextPath) throws IOException {
		Files.createDirectories(catalinaBase.toPath());
		String[] clean = { "conf", "webapps", "work", "temp" };
		for (String name : clean) {
			Path dir = new File(catalinaBase, name).toPath();
			if (Files.exists(dir)) {
				deleteTree(dir);
			}
			Files.createDirectories(dir);
		}
		Files.createDirectories(new File(catalinaBase, "logs").toPath());
		copyTree(new File(tomcatHome, "conf").toPath(), new File(catalinaBase, "conf").toPath());
		// 사람이 그 Tomcat에 등록해 둔 다른 앱(conf/Catalina/{host}/*.xml)은 가져오지 않는다. 경로가 없으면 기동이 실패한다.
		Path perAppContexts = new File(catalinaBase, "conf/Catalina").toPath();
		if (Files.exists(perAppContexts)) {
			deleteTree(perAppContexts);
		}
		// 콘솔 출력(server.log)을 UTF-8로 맞춘다. Windows는 콘솔 인코딩(MS949)으로 나와 읽지 못한다.
		File logging = new File(catalinaBase, "conf/logging.properties");
		if (logging.isFile()) {
			String props = new String(Files.readAllBytes(logging.toPath()), StandardCharsets.ISO_8859_1);
			Files.write(logging.toPath(), patchConsoleEncoding(props).getBytes(StandardCharsets.ISO_8859_1));
		}
		File serverXml = new File(catalinaBase, "conf/server.xml");
		String xml = Files.readString(serverXml.toPath(), StandardCharsets.UTF_8);
		Files.writeString(serverXml.toPath(), patchServerXml(xml, port), StandardCharsets.UTF_8);
		String name = "/".equals(contextPath) ? "ROOT" : contextPath.substring(1).replace('/', '#');
		Files.copy(war.toPath(), new File(catalinaBase, "webapps/" + name + ".war").toPath());
	}

	/** server.xml의 포트를 바꾸고 다른 앱의 Context를 뺍니다(prepareCatalinaBase 설명 참고). */
	static String patchServerXml(String xml, int port) {
		String text = xml.replaceFirst("<Server\\s+port=\"[^\"]*\"", Matcher.quoteReplacement("<Server port=\"-1\""));
		text = removeContexts(text);
		Matcher m = Pattern.compile("<!--.*?-->|<Connector\\b[^>]*?(?:/>|>.*?</Connector>)", Pattern.DOTALL).matcher(text);
		StringBuilder out = new StringBuilder();
		int last = 0;
		boolean httpDone = false;
		while (m.find()) {
			String part = m.group();
			if (part.startsWith("<!--")) {
				continue;
			}
			String replaced;
			boolean http = !part.contains("AJP") && !part.contains("SSLEnabled=\"true\"");
			if (http && !httpDone) {
				replaced = part.replaceFirst("port=\"[^\"]*\"", Matcher.quoteReplacement("port=\"" + port + "\""));
				httpDone = true;
			} else {
				replaced = "<!-- dstone 임시 시험 서버에서 끔 -->";
			}
			out.append(text, last, m.start()).append(replaced);
			last = m.end();
		}
		out.append(text.substring(last));
		return out.toString();
	}

	/** 주석이 아닌 <Context …/> 또는 <Context …>…</Context>를 주석 한 줄로 바꿉니다. */
	static String removeContexts(String xml) {
		Matcher m = Pattern.compile("<!--.*?-->|<Context\\b[^>]*?(?:/>|>.*?</Context>)", Pattern.DOTALL).matcher(xml);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (m.find()) {
			if (m.group().startsWith("<!--")) {
				continue;
			}
			out.append(xml, last, m.start()).append("<!-- dstone 임시 시험 서버에서 뺌(사람이 등록한 다른 앱) -->");
			last = m.end();
		}
		out.append(xml.substring(last));
		return out.toString();
	}

	/** logging.properties의 콘솔 인코딩을 UTF-8로 바꾸거나, 없으면 덧붙입니다. */
	static String patchConsoleEncoding(String props) {
		String key = "java.util.logging.ConsoleHandler.encoding";
		Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=.*$").matcher(props);
		if (m.find()) {
			return m.replaceAll(Matcher.quoteReplacement(key + " = UTF-8"));
		}
		return props + (props.endsWith("\n") ? "" : "\n") + key + " = UTF-8\n";
	}

	private static File findWar(File target) {
		File[] files = target.listFiles();
		if (files == null) {
			return null;
		}
		for (File file : files) {
			if (file.isFile() && file.getName().endsWith(".war")) {
				return file;
			}
		}
		return null;
	}

	static boolean isWar(String pomText) {
		return Pattern.compile("<packaging>\\s*war\\s*</packaging>").matcher(pomText).find();
	}

	/** pom의 tomcat 플러그인에 적힌 <path>입니다. 없으면 null. */
	static String tomcatPathOf(String pomText) {
		String block = pluginBlock(pomText, "tomcat7-maven-plugin");
		if (block == null) {
			block = pluginBlock(pomText, "tomcat6-maven-plugin");
		}
		if (block == null) {
			return null;
		}
		Matcher path = Pattern.compile("<path>\\s*([^<$]*)\\s*</path>").matcher(block);
		return path.find() ? normalizePath(path.group(1)) : null;
	}

	/** 설정 tomcat-home입니다. 비었거나 bin/catalina 스크립트가 없으면 null. */
	private File tomcatHome() {
		String path = this.config("tomcat-home", "");
		if (isBlank(path)) {
			return null;
		}
		File home = new File(path.trim());
		File script = new File(home, isWindows() ? "bin/catalina.bat" : "bin/catalina.sh");
		return script.isFile() ? home : null;
	}

	private static void copyTree(final Path from, final Path to) throws IOException {
		Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(to.resolve(from.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, to.resolve(from.relativize(file).toString()));
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void deleteTree(Path root) throws IOException {
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** 작업 루트(설정 work-root, 비우면 {사용자 홈}/.dstone-ai-engine)입니다. */
	private File workRoot() {
		String configured = this.config("work-root", "");
		if (!isBlank(configured)) {
			return new File(configured.trim());
		}
		return new File(System.getProperty("user.home"), DEFAULT_ROOT_NAME);
	}

	/** 사본 폴더 이름에 쓸 키(프로젝트ID, 없으면 원본 폴더 이름). 경로에 못 쓰는 글자는 _로 바꿉니다. */
	static String keyOf(String projectId, String sourceRoot) {
		String raw = projectId;
		if (isBlank(raw)) {
			String path = sourceRoot == null ? "" : sourceRoot.trim().replace('\\', '/');
			while (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			raw = path.substring(path.lastIndexOf('/') + 1);
		}
		String key = raw.trim().replaceAll("[^A-Za-z0-9_-]", "_");
		return key.isEmpty() ? "project" : key;
	}

	private static boolean sameExecution(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}

	private String config(String name, String defaultValue) {
		String value = this.configProperty.getProperty(Constants.Tool.TestServer.PREFIX + "." + name);
		return isBlank(value) ? defaultValue : value.trim();
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

}

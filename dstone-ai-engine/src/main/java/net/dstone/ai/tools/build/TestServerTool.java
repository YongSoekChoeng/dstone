package net.dstone.ai.tools.build;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.project.ProjectBuildService;
import net.dstone.ai.common.project.ProjectStore;
import net.dstone.ai.runtime.tool.StepRef;
import net.dstone.ai.runtime.tool.ToolOutcome;
import net.dstone.ai.runtime.workflow.StepEndListener;
import net.dstone.ai.tools.build.sandbox.SandboxArtifact;
import net.dstone.ai.tools.build.sandbox.SandboxGuard;
import net.dstone.ai.tools.build.testserver.RunLayer;
import net.dstone.ai.tools.build.testserver.TestDriverClient;
import net.dstone.ai.tools.build.testserver.TestDriverJar;
import net.dstone.ai.tools.build.testserver.TestServerRegistry;
import net.dstone.ai.tools.build.testserver.TomcatBase;
import net.dstone.ai.tools.build.testserver.WasCheck;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 테스트 서버 Tool입니다. 프로젝트의 리비전(프로젝트 관리의 [Build] 결과) + 개발 결과로 서버를 띄우고, 고객사 개발 DB를 쓰되 끝나면 원복합니다.
 * 설계: docs/temp/dstone-ai-테스트관련재설계/01.설계.md 5·6장.
 *
 * ## 폴더({작업 루트}/{프로젝트ID}/runs/{실행ID})
 *   webapp/   실행 전용 docBase(개발 결과의 웹 파일, 바꾼 설정)       classes/  컴파일 결과와 바꾼 리소스(먼저 읽힘)
 *   data/     소스에 박힌 원본 절대경로를 이리로 바꿈                  tomcat/   실행 전용 CATALINA_BASE
 *   stage/    다음 반영을 만드는 곳                                     server.log, server.pid, driver-control.txt(테스트 드라이버 창구)
 * 리비전 폴더와 원본 소스 디렉토리는 읽기만 합니다.
 *
 * ## 띄우기
 * WAR는 설치된 Tomcat(javax 앱은 tomcat-home-9, jakarta 앱은 tomcat-home-10·11)에 Resources로 실행 층과 리비전을 겹쳐 올리고,
 * Spring Boot는 리비전의 클래스·lib로 java -cp 실행합니다(jar를 다시 만들지 않음). EJB·벤더 API를 쓰는 앱은 띄우지 않습니다(WasCheck).
 *
 * ## 다시 부르면(같은 실행, 같은 리비전)
 * 바뀐 파일만 반영합니다. JSP·정적 파일은 복사만(즉시), Java·XML·설정은 context reload(Context 파일의 수정 시각을 바꿈), Boot는 JVM 재시작.
 *
 * ## DB
 * 앱의 JDBC URL과 드라이버를 테스트 드라이버로 바꿔(RunLayer), 개발 DB를 커밋하지 않는 연결 하나로 씁니다. 실행이 승인 대기·일시정지로
 * 루프를 벗어나면 원복(rollback)하고, 끝나거나 서버를 내릴 때도 원복합니다. 연결이 끊기면 DB가 원복합니다.
 * 테스트 DB 별칭(test-DB이름)으로 dbQuery하면 테스트 서버 안의 같은 연결에서 조회합니다(커밋하지 않은 데이터가 보임).
 *
 * ## 수명
 * 실행마다 서버 하나. step이 끝나도 두고, 실행이 끝나면 내립니다. 승인 대기·일시정지 중에는 유휴 idle-minutes가 지나면 내립니다.
 * DB 연결을 db-hold-minutes보다 오래 잡으면 원복하고 내립니다. 동시 서버는 max-servers까지(넘으면 기다림).
 * </pre>
 */
@AiTool
public class TestServerTool extends BaseObject implements StepEndListener {

	/** 띄운 프로세스의 명령줄 표시(엔진이 죽어 남은 프로세스를 정리할 때 씀) */
	private static final String MARKER = "-Ddstone.ai.test-server=";

	/** 띄운 JVM의 콘솔 출력을 UTF-8로 */
	private static final String UTF8_OUTPUT = "-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8";

	private static final int LOG_TAIL_LINES = 40;
	private static final String CAUSE_CODE = "code";
	private static final String CAUSE_ENVIRONMENT = "environment";
	private static final String CONTROL_FILE = "driver-control.txt";
	private static final String DEFAULT_VENDOR_PREFIXES = "weblogic.,com.bea.,com.ibm.websphere.,com.ibm.ws.,jeus.,tmax.";

	@Autowired
	private ConfigProperty configProperty;

	@Autowired
	private ProjectStore projectStore;

	@Autowired
	private ProjectBuildService projectBuildService;

	@Autowired
	private JavaCompileTool javaCompileTool;

	@Autowired
	private TestServerRegistry registry;

	/** 실행별 서버(키: 실행ID, Workflow 밖이면 adhoc-프로젝트ID) */
	private final Map<String, Run> runs = new ConcurrentHashMap<String, Run>();

	private ScheduledExecutorService reaper;

	/** 테스트 서버 하나 */
	private static final class Run {
		String key;
		String projectId;
		String executionId;
		String revision;
		Map<String, Object> resolved;
		File dir;
		File log;
		File descriptor;
		Process process;
		int port;
		String url;
		String contextPath;
		boolean boot;
		File javaHome;
		File tomcatHome;
		SandboxGuard guard;
		TestDriverClient driver;
		Map<String, Map<String, String>> aliases = new LinkedHashMap<String, Map<String, String>>();
		List<String> differences = new ArrayList<String>();
		List<String> notes = new ArrayList<String>();
		boolean bootDatasource;
		/** 앱 설정에서 테스트 드라이버로 바꾼 원래 드라이버들. 테스트 드라이버가 실제 연결에 먼저 씁니다 */
		Set<String> originalDrivers = new LinkedHashSet<String>();
		File driverJar;
		/** 실행이 승인 대기 등으로 루프를 벗어난 때(유휴 시작). 루프 안이면 0 */
		volatile long idleSince;
		/** DB 연결을 잡기 시작한 때(띄운 때, 마지막 원복 때) */
		volatile long dbSince;
	}

	/** 한 번의 요청 결과 */
	private static final class Answer {
		boolean success;
		String cause = "";
		String message;
		Map<String, Object> timings = new LinkedHashMap<String, Object>();
	}

	@PostConstruct
	public void init() {
		this.reaper = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable runnable) {
				Thread thread = new Thread(runnable, "dstone-test-server-reaper");
				thread.setDaemon(true);
				return thread;
			}
		});
		this.reaper.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				TestServerTool.this.reap();
			}
		}, 60, 60, TimeUnit.SECONDS);
		this.killLeftovers();
	}

	@Tool(description = "테스트 서버를 띄운다. 프로젝트의 현재 리비전(프로젝트 관리 [Build] 결과)에 개발 결과를 얹어 띄우고, 고객사 개발 DB를 쓰되 실행이 멈추거나 끝나면 원복한다. "
		+ "이미 이 실행의 서버가 떠 있으면 바뀐 파일만 반영한다(JSP·정적 파일은 바로, Java·XML·설정은 context reload. reload하면 세션이 끊기니 다시 로그인한다). "
		+ "결과: {success, cause(code: 개발 결과 탓 | environment: 환경 탓), message, url, serverLog, dbAliases(test-…: 테스트 서버와 같은 연결로 조회, 커밋 안 한 데이터가 보임)}. "
		+ "테스트 중 DDL은 실행되지 않는다(DDL은 사람이 개발 DB에 반영). 다 쓰면 stopTestServer를 불러도 되고, 부르지 않아도 엔진이 내린다.")
	public Map<String, Object> startTestServer(
			@ToolParam(description = "프로젝트ID") String projectId,
			@ToolParam(required = false, description = "쓸 리비전(Workflow가 시작할 때 받은 값). 비우면 프로젝트의 현재 리비전") String revision,
			@ToolParam(required = false, description = "개발 결과 폴더(원본 소스 루트 기준 같은 경로로 바뀐 파일·새 파일)의 절대경로. 없으면 비운다") String overlayDir,
			@ToolParam(required = false, description = "삭제 목록 파일의 절대경로. 원본 루트 기준 상대경로가 한 줄에 하나") String deleteList,
			ToolContext toolContext) {
		StepRef stepRef = StepRef.from(toolContext);
		Answer answer = new Answer();
		Run run = null;
		try {
			if (isBlank(projectId) || this.projectStore.settings(projectId.trim()) == null) {
				answer.message = "프로젝트 관리에 없는 프로젝트ID라 테스트 서버를 띄우지 못합니다: " + projectId;
				answer.cause = CAUSE_ENVIRONMENT;
			} else {
				run = this.start(projectId.trim(), revision, overlayDir, deleteList, stepRef, answer);
			}
		} catch (IOException | RuntimeException e) {
			this.error("dstone-ai-engine tool: 테스트 서버를 띄우다 오류: " + e);
			answer.success = false;
			answer.cause = CAUSE_ENVIRONMENT;
			answer.message = "테스트 서버를 띄우지 못했습니다: " + e.getMessage();
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("success", Boolean.valueOf(answer.success));
		result.put("cause", answer.success ? "" : answer.cause);
		result.put("message", answer.message);
		if (answer.success && run != null) {
			result.put("url", run.url);
			result.put("serverLog", run.log.getAbsolutePath());
			result.put("dbAliases", new ArrayList<String>(run.aliases.keySet()));
			result.put("revision", run.revision);
		}
		result.put("timings", answer.timings);
		return result;
	}

	@Tool(description = "이 실행의 테스트 서버를 내리고 개발 DB의 테스트 데이터를 원복한다.")
	public ToolOutcome stopTestServer(@ToolParam(description = "startTestServer에 준 프로젝트ID") String projectId, ToolContext toolContext) {
		StepRef stepRef = StepRef.from(toolContext);
		Run run = this.runs.get(keyOf(stepRef, projectId));
		if (run == null) {
			return ToolOutcome.pass("떠 있는 테스트 서버가 없습니다.");
		}
		String problem = this.stop(run);
		return ToolOutcome.pass("테스트 서버를 내렸습니다." + (problem == null ? " 개발 DB의 테스트 데이터를 원복했습니다." : " 원복 확인 필요: " + problem));
	}

	@Tool(description = "프로젝트 관리에 등록된 테스트계정(화면 로그인용)을 알려 준다. 비밀번호는 알려 주지 않고, 비밀번호 칸에 글자 그대로 넣을 자리표시자를 준다.")
	public ToolOutcome getTestAccount(@ToolParam(description = "프로젝트ID(요청서의 프로젝트ID)") String projectId) {
		return ToolOutcome.pass(this.accountLine(projectId));
	}

	/* ---------------------------------------------------------------- StepEndListener */

	@Override
	public void stepEnded(String executionId, String stepId) {
		if (executionId != null && stepId != null) {
			this.registry.stepEnded(new StepRef(executionId, stepId));
		}
	}

	@Override
	public void executionLeft(String executionId) {
		this.executionLeft(executionId, false);
	}

	/** 승인 대기·일시정지면 원복만 하고 서버는 둡니다(유휴 시간이 지나면 내림). 끝남·실패면 내립니다. */
	@Override
	public void executionLeft(String executionId, boolean resumable) {
		if (executionId == null) {
			return;
		}
		Run run = this.runs.get(executionId);
		if (run == null) {
			return;
		}
		if (!resumable) {
			this.info("dstone-ai-engine tool: 실행[" + executionId + "]이 끝나 테스트 서버를 내리고 DB를 원복합니다.");
			this.stop(run);
			return;
		}
		String problem = run.driver.rollback();
		run.dbSince = System.currentTimeMillis();
		run.idleSince = System.currentTimeMillis();
		if (problem != null) {
			this.warn("dstone-ai-engine tool: 실행[" + executionId + "]의 개발 DB 원복이 실패해 서버를 내립니다(연결이 끊기면 DB가 되돌림): " + problem);
			this.stop(run);
		} else {
			this.info("dstone-ai-engine tool: 실행[" + executionId + "]이 기다리는 동안 개발 DB를 원복했습니다. 서버는 유휴 시간까지 둡니다.");
		}
	}

	@PreDestroy
	public void stopAll() {
		if (this.reaper != null) {
			this.reaper.shutdownNow();
		}
		Iterator<Run> it = new ArrayList<Run>(this.runs.values()).iterator();
		while (it.hasNext()) {
			this.stop(it.next());
		}
	}

	/* ---------------------------------------------------------------- 띄우기와 반영 */

	private Run start(String projectId, String revision, String overlayText, String deleteText, StepRef stepRef, Answer answer) throws IOException {
		String key = keyOf(stepRef, projectId);
		Map<String, Object> resolved = this.projectBuildService.resolve(projectId, isBlank(revision) ? null : revision.trim());
		if (resolved == null) {
			answer.cause = CAUSE_ENVIRONMENT;
			answer.message = "빌드된 리비전이 없어 테스트 서버를 띄우지 못합니다" + (isBlank(revision) ? "" : "(리비전 " + revision + ")")
				+ ". 프로젝트 관리에서 [Build]를 누르면 다음부터 테스트할 수 있습니다.";
			return null;
		}
		File overlay = isBlank(overlayText) ? null : new File(overlayText.trim());
		File deleteList = isBlank(deleteText) ? null : new File(deleteText.trim());
		synchronized (key.intern()) {
			Run run = this.runs.get(key);
			if (run != null && run.process != null && run.process.isAlive() && run.revision.equals(resolved.get("revision"))) {
				run.idleSince = 0L;
				this.apply(run, overlay, deleteList, answer);
				if (answer.success) {
					this.registry.opened(stepRef, run.url, run.aliases);
				}
				return run;
			}
			if (run != null) {
				this.stop(run);
			}
			if (!this.waitForSlot(answer)) {
				return null;
			}
			run = this.launch(key, projectId, stepRef, resolved, overlay, deleteList, answer);
			if (run != null && answer.success) {
				this.registry.opened(stepRef, run.url, run.aliases);
			}
			return run;
		}
	}

	private Run launch(String key, String projectId, StepRef stepRef, Map<String, Object> resolved, File overlay, File deleteList, Answer answer) throws IOException {
		long t0 = System.currentTimeMillis();
		Map<String, Object> project = this.projectStore.settings(projectId);
		Run run = new Run();
		run.key = key;
		run.projectId = projectId;
		run.executionId = stepRef == null ? null : stepRef.executionId();
		run.revision = String.valueOf(resolved.get("revision"));
		run.resolved = resolved;
		run.boot = ProjectBuildService.KIND_BOOT.equals(resolved.get("kind"));
		run.dir = new File(new File(this.projectBuildService.projectDir(projectId), "runs"), safe(key));
		RunLayer.deleteTreeQuietly(run.dir);
		Files.createDirectories(run.dir.toPath());
		Files.writeString(new File(run.dir, ProjectBuildService.RUN_REVISION_FILE).toPath(), run.revision, StandardCharsets.UTF_8);
		run.log = new File(run.dir, "server.log");
		File revDir = new File(String.valueOf(resolved.get("revisionDir")));
		File revSrc = new File(String.valueOf(resolved.get("sourceRoot")));
		File webRoot = fileOrNull(resolved.get("webRoot"));
		File classesDir = fileOrNull(resolved.get("classesDir"));
		File libDir = fileOrNull(resolved.get("libDir"));
		boolean started = false;
		try {
			// 1) Tomcat으로 띄울 수 있는 앱인가
			WasCheck.Result was = WasCheck.check(revDir, revSrc, webRoot, libDir, splitList(this.config("vendor-api-prefixes", DEFAULT_VENDOR_PREFIXES)));
			if (was.blocker != null) {
				answer.cause = CAUSE_ENVIRONMENT;
				answer.message = was.blocker + ". 이 프로젝트는 서버 기동 테스트를 하지 않고 정적 확인까지만 합니다.";
				return null;
			}
			List<String> unguarded = SandboxArtifact.unguardedJars(libDir, splitList(forProject(this.config("trusted-libs", ""), projectId)));
			if (!unguarded.isEmpty()) {
				answer.cause = CAUSE_ENVIRONMENT;
				answer.message = "외부 접속을 막을 수 없는 라이브러리(NIO)가 있어 테스트 서버를 띄우지 않습니다: " + String.join(", ", unguarded)
					+ ". 쓰지 않는 것이면 관리자가 설정 trusted-libs에 '" + projectId + "=jar 이름 앞부분'으로 더합니다.";
				return null;
			}

			// 2) 실행 층
			List<Map<String, String>> dbs = this.projectDbs(projectId);
			RunLayer.Spec spec = this.spec(run, project, resolved, revDir, revSrc, webRoot, classesDir, libDir, overlay, deleteList, dbs);
			RunLayer.Result layer = RunLayer.build(spec, this.javaCompileTool);
			answer.timings.put("layerMs", Long.valueOf(System.currentTimeMillis() - t0));
			if (layer.codeProblem != null) {
				answer.cause = CAUSE_CODE;
				answer.message = "개발 결과를 컴파일하지 못해 테스트 서버를 띄우지 않았습니다:\n" + layer.codeProblem;
				return null;
			}
			RunLayer.sync(new File(spec.stageDir, "webapp"), new File(run.dir, "webapp"));
			RunLayer.sync(new File(spec.stageDir, "classes"), new File(run.dir, "classes"));
			run.notes.addAll(layer.notes);
			run.bootDatasource = layer.bootDatasource;
			run.originalDrivers.addAll(layer.driversFound);
			List<TomcatBase.JndiResource> jndi = new ArrayList<TomcatBase.JndiResource>();
			for (Map<String, String> db : dbs) {
				if (!isBlank(db.get("jndiName"))) {
					TomcatBase.JndiResource resource = new TomcatBase.JndiResource();
					resource.name = stripEnv(db.get("jndiName"));
					resource.url = db.get("url");
					resource.username = db.get("username");
					resource.password = db.get("password");
					jndi.add(resource);
				}
			}
			if (!dbs.isEmpty() && layer.urlsFound.isEmpty() && jndi.isEmpty()) {
				answer.cause = CAUSE_ENVIRONMENT;
				answer.message = "앱의 설정·소스에서 프로젝트 관리에 등록한 DB의 JDBC URL을 찾지 못했습니다(URL을 조각으로 조립하는 앱일 수 있음). "
					+ "테스트 데이터를 원복할 수 없어 띄우지 않습니다. DB가 JNDI로 연결되면 프로젝트 관리의 DB에 JNDI 이름을 넣으십시오.";
				return null;
			}
			for (Map<String, String> db : dbs) {
				if (!layer.urlsFound.contains(db.get("url")) && isBlank(db.get("jndiName"))) {
					run.differences.add("프로젝트 DB '" + db.get("dbName") + "'의 URL은 앱 설정에서 찾지 못했습니다. 앱이 그 DB에 직접 연결하면 감지해 서버를 내립니다.");
				}
			}
			if (layer.pathFiles > 0) {
				run.differences.add("소스에 박힌 원본 폴더 경로(" + project.get("sourceDir") + ")를 테스트 서버 전용 폴더(" + new File(run.dir, "data")
					+ ")로 바꿨습니다(" + layer.pathFiles + "개 파일). 그 폴더는 비어서 시작하므로 기존 첨부 파일은 없습니다.");
			}

			// 3) 문(외부 접속 차단, DB 연결 세기)과 드라이버
			run.driverJar = TestDriverJar.ensure(this.projectBuildService.workRoot());
			run.guard = new SandboxGuard(new ArrayList<SandboxGuard.Endpoint>(), 0, false);
			for (Map<String, String> db : dbs) {
				for (SandboxGuard.Endpoint endpoint : endpointsOf(db.get("url"))) {
					run.guard.watch(endpoint);
				}
			}
			for (String host : splitList(forProject(this.config("allow-hosts", ""), projectId))) {
				int colon = host.lastIndexOf(':');
				run.guard.allow(colon > 0 ? new SandboxGuard.Endpoint(host.substring(0, colon), Integer.parseInt(host.substring(colon + 1))) : new SandboxGuard.Endpoint(host, 0));
			}
			run.driver = new TestDriverClient(new File(run.dir, CONTROL_FILE));
			run.port = freePort();
			run.guard.allow(new SandboxGuard.Endpoint("localhost", run.port));
			run.javaHome = this.javaHome(project, run.notes);

			// 4) 띄우기
			long t1 = System.currentTimeMillis();
			if (run.boot) {
				run.contextPath = "/";
				run.differences.add(staticPathsNotApplied(project));
			} else {
				run.tomcatHome = this.tomcatHome(was.jakarta);
				if (run.tomcatHome == null) {
					answer.cause = CAUSE_ENVIRONMENT;
					answer.message = (was.jakarta ? "jakarta 앱이라 Tomcat 10 이상(설정 tomcat-home-10 또는 tomcat-home-11)" : "javax 앱이라 Tomcat 9(설정 tomcat-home-9)")
						+ "이 필요한데 이 서버에 설정되지 않았습니다.";
					return null;
				}
				int major = TomcatBase.majorVersion(run.tomcatHome);
				if (major > 0 && major < 9) {
					run.notes.add("참고: Tomcat " + major + "입니다. 테스트 서버는 Tomcat 8.5 이상에서만 확인했습니다.");
				}
				run.contextPath = contextPathOf(project, was);
				List<String[]> staticPaths = this.staticPaths(project, run);
				run.descriptor = TomcatBase.prepare(run.tomcatHome, new File(run.dir, "tomcat"), run.port, run.contextPath, new File(run.dir, "webapp"),
					new File(run.dir, "classes"), webRoot, classesDir, libDir, jndi, run.driverJar, staticPaths, run.notes);
				run.differences.add("설치된 Tomcat에 사람이 등록해 둔 다른 앱(Context)은 띄우지 않았습니다.");
			}
			String problem = this.startProcess(run);
			answer.timings.put("startMs", Long.valueOf(System.currentTimeMillis() - t1));
			if (problem != null) {
				answer.cause = this.startupCause(run);
				answer.message = "테스트 서버가 뜨지 않았습니다(" + problem + "). 서버 로그: " + run.log + "\n" + tail(run.log, LOG_TAIL_LINES) + this.deniedNote(run);
				return null;
			}
			String bypass = this.bypassCheck(run);
			if (bypass != null) {
				answer.cause = CAUSE_ENVIRONMENT;
				answer.message = bypass;
				return null;
			}
			this.runs.put(key, run);
			run.dbSince = System.currentTimeMillis();
			for (Map<String, String> db : dbs) {
				Map<String, String> setting = new LinkedHashMap<String, String>(db);
				setting.put("control", new File(run.dir, CONTROL_FILE).getAbsolutePath());
				setting.put("description", "테스트 서버의 DB(" + db.get("dbName") + "). 테스트 서버와 같은 연결로 조회해 커밋하지 않은 테스트 데이터가 보입니다");
				run.aliases.put(TestServerRegistry.ALIAS_PREFIX + db.get("dbName"), setting);
			}
			started = true;
			answer.success = true;
			answer.timings.put("totalMs", Long.valueOf(System.currentTimeMillis() - t0));
			answer.message = this.describe(run, "테스트 서버를 띄웠습니다.");
			return run;
		} finally {
			if (!started) {
				this.stop(run);
			}
		}
	}

	/** 떠 있는 서버에 바뀐 것만 반영합니다. */
	private void apply(Run run, File overlay, File deleteList, Answer answer) throws IOException {
		long t0 = System.currentTimeMillis();
		Map<String, Object> project = this.projectStore.settings(run.projectId);
		RunLayer.Spec spec = this.spec(run, project, run.resolved, new File(String.valueOf(run.resolved.get("revisionDir"))), new File(String.valueOf(run.resolved.get("sourceRoot"))),
			fileOrNull(run.resolved.get("webRoot")), fileOrNull(run.resolved.get("classesDir")), fileOrNull(run.resolved.get("libDir")), overlay, deleteList, this.projectDbs(run.projectId));
		RunLayer.Result layer = RunLayer.build(spec, this.javaCompileTool);
		answer.timings.put("layerMs", Long.valueOf(System.currentTimeMillis() - t0));
		if (layer.codeProblem != null) {
			answer.cause = CAUSE_CODE;
			answer.message = "개발 결과를 컴파일하지 못했습니다. 테스트 서버는 지난번 반영 상태 그대로입니다:\n" + layer.codeProblem;
			return;
		}
		List<String> webChanged = RunLayer.sync(new File(spec.stageDir, "webapp"), new File(run.dir, "webapp"));
		List<String> classChanged = RunLayer.sync(new File(spec.stageDir, "classes"), new File(run.dir, "classes"));
		boolean needReload = !classChanged.isEmpty();
		for (String rel : webChanged) {
			if (rel.startsWith("WEB-INF/")) {
				needReload = true;
			}
		}
		String how;
		long t1 = System.currentTimeMillis();
		if (webChanged.isEmpty() && classChanged.isEmpty()) {
			how = "지난번과 바뀐 것이 없어 그대로 씁니다.";
		} else if (!needReload && !run.boot) {
			how = "바뀐 화면 파일 " + webChanged.size() + "개를 바로 반영했습니다(서버를 다시 올리지 않음).";
		} else {
			String problem = run.boot ? "Spring Boot" : this.reload(run);
			if (problem != null) {
				// reload가 안 되면(또는 Boot면) JVM을 다시 띄운다. 연결이 끊기므로 DB의 테스트 데이터도 원복된다
				run.driver.rollback();
				killTree(run.process.toHandle());
				Files.deleteIfExists(new File(run.dir, CONTROL_FILE).toPath());
				String startProblem = this.startProcess(run);
				if (startProblem != null) {
					answer.cause = this.startupCause(run);
					answer.message = "바뀐 것을 반영하려고 서버를 다시 띄웠지만 뜨지 않았습니다(" + startProblem + "). 서버 로그: " + run.log + "\n" + tail(run.log, LOG_TAIL_LINES);
					this.stop(run);
					return;
				}
				run.dbSince = System.currentTimeMillis();
				how = "바뀐 파일(" + (webChanged.size() + classChanged.size()) + "개)을 반영하려고 JVM을 다시 띄웠습니다(" + problem + "). 세션이 끊겼으니 다시 로그인하십시오. 개발 DB의 테스트 데이터도 원복되었습니다.";
			} else {
				how = "바뀐 파일(" + (webChanged.size() + classChanged.size()) + "개)을 반영하려고 앱을 다시 올렸습니다(context reload). 세션이 끊겼으니 다시 로그인하십시오. DB의 테스트 데이터는 남아 있습니다.";
			}
		}
		answer.timings.put("applyMs", Long.valueOf(System.currentTimeMillis() - t1));
		String bypass = this.bypassCheck(run);
		if (bypass != null) {
			answer.cause = CAUSE_ENVIRONMENT;
			answer.message = bypass;
			this.stop(run);
			return;
		}
		run.notes.clear();
		run.notes.addAll(layer.notes);
		answer.success = true;
		answer.message = this.describe(run, "이 실행의 테스트 서버가 떠 있어 다시 띄우지 않았습니다. " + how);
	}

	private RunLayer.Spec spec(Run run, Map<String, Object> project, Map<String, Object> resolved, File revDir, File revSrc, File webRoot, File classesDir, File libDir,
		File overlay, File deleteList, List<Map<String, String>> dbs) {
		RunLayer.Spec spec = new RunLayer.Spec();
		spec.revDir = revDir;
		spec.revSrc = revSrc;
		spec.webRoot = webRoot;
		spec.classesDir = classesDir;
		spec.libDir = libDir;
		spec.originalSource = text(project, "sourceDir");
		spec.dataDir = new File(run.dir, "data");
		spec.overlayDir = overlay;
		spec.deleteList = deleteList;
		spec.stageDir = new File(run.dir, "stage");
		for (Map<String, String> db : dbs) {
			spec.dbUrls.add(db.get("url"));
			if (!isBlank(db.get("jndiName"))) {
				spec.jndiNames.add(stripEnv(db.get("jndiName")));
			}
		}
		spec.sourceDirs.addAll(splitList(text(project, "buildSourceDirs")));
		String javaVersion = text(project, "javaVersion");
		spec.release = javaVersion == null ? 0 : Integer.parseInt(javaVersion);
		spec.boot = ProjectBuildService.KIND_BOOT.equals(resolved.get("kind"));
		return spec;
	}

	/** 프로세스를 띄우고 접속될 때까지 기다립니다. 뜨면 null, 아니면 이유. */
	private String startProcess(Run run) throws IOException {
		List<String> command = new ArrayList<String>();
		String driverProps = "-Ddstone.testdriver.control=" + new File(run.dir, CONTROL_FILE).getAbsolutePath()
			+ " -Ddstone.testdriver.lockWaitSeconds=" + this.config("db-lock-wait-seconds", "30");
		if (!run.originalDrivers.isEmpty()) {
			driverProps += " -Ddstone.testdriver.drivers=" + String.join(",", run.originalDrivers);
		}
		ProcessBuilder builder;
		long logFrom = run.log.isFile() ? run.log.length() : 0L;
		if (run.boot) {
			File home = run.javaHome != null ? run.javaHome : new File(System.getProperty("java.home"));
			command.add(new File(home, isWindows() ? "bin/java.exe" : "bin/java").getPath());
			command.add(MARKER + run.key);
			command.addAll(Arrays.asList(UTF8_OUTPUT.split(" ")));
			command.addAll(Arrays.asList(driverProps.split(" ")));
			if (run.bootDatasource) {
				command.add("-Dspring.datasource.driver-class-name=" + TestDriverJar.DRIVER_CLASS);
			}
			command.add("-cp");
			command.add(new File(run.dir, "classes").getAbsolutePath() + File.pathSeparator + run.resolved.get("classesDir") + File.pathSeparator
				+ run.resolved.get("libDir") + File.separator + "*" + File.pathSeparator + run.driverJar.getAbsolutePath());
			command.add(String.valueOf(run.resolved.get("mainClass")));
			command.add("--server.port=" + run.port);
			command.add("--server.servlet.context-path=/");
			builder = new ProcessBuilder(command).directory(run.dir);
		} else {
			command.addAll(TomcatBase.command(run.tomcatHome));
			File catalinaBase = new File(run.dir, "tomcat");
			builder = new ProcessBuilder(command).directory(catalinaBase);
			Map<String, String> env = builder.environment();
			env.put("CATALINA_HOME", run.tomcatHome.getPath());
			env.put("CATALINA_BASE", catalinaBase.getPath());
			env.put("CATALINA_PID", "");
			env.put("CATALINA_OPTS", MARKER + run.key + " " + UTF8_OUTPUT + " " + driverProps);
			if (run.javaHome != null) {
				env.put("JAVA_HOME", run.javaHome.getPath());
				env.remove("JRE_HOME");
			}
		}
		builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(run.log));
		Map<String, String> env = builder.environment();
		String current = env.get("JAVA_TOOL_OPTIONS");
		env.put("JAVA_TOOL_OPTIONS", isBlank(current) ? run.guard.jvmOptions() : current + " " + run.guard.jvmOptions());
		env.remove("_JAVA_OPTIONS");
		run.process = builder.start();
		Files.writeString(new File(run.dir, "server.pid").toPath(), String.valueOf(run.process.pid()), StandardCharsets.UTF_8);
		run.url = "http://localhost:" + run.port + ("/".equals(run.contextPath) ? "/" : run.contextPath + "/");
		int timeout = Integer.parseInt(this.config("start-timeout-seconds", "600"));
		String problem = waitUntilUp(run.process, run.port, run.contextPath, timeout);
		if (problem != null) {
			return problem;
		}
		if (!run.boot && httpStatus(run.port, run.contextPath) == HttpURLConnection.HTTP_NOT_FOUND) {
			return "Tomcat은 떴지만 앱(" + run.contextPath + ")이 시작되지 않았습니다(404)";
		}
		if (!textFrom(run.log, logFrom).contains("-DsocksProxyHost=127.0.0.1")) {
			return "앱 JVM에 외부 접속 차단 옵션이 들어갔는지 확인할 수 없습니다";
		}
		return null;
	}

	/**
	 * <pre>
	 * Context 파일의 수정 시각을 바꿔 Tomcat이 앱을 다시 올리게 하고(HostConfig, 최대 10초 간격으로 봄), 끝날 때까지 기다립니다.
	 * Tomcat은 다시 올릴 때 Context 파일 경로를 로그에 두 번(시작, 끝) 남기므로 그것을 봅니다. 되면 null, 아니면 이유.
	 * </pre>
	 */
	private String reload(Run run) throws IOException {
		long from = run.log.length();
		String path = run.descriptor.getAbsolutePath();
		if (!run.descriptor.setLastModified(System.currentTimeMillis() + 1000L)) {
			Files.writeString(run.descriptor.toPath(), Files.readString(run.descriptor.toPath(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
		}
		long deadline = System.currentTimeMillis() + Long.parseLong(this.config("start-timeout-seconds", "600")) * 1000L;
		while (System.currentTimeMillis() < deadline) {
			if (!run.process.isAlive()) {
				return "Tomcat이 끝났습니다";
			}
			String text = textFrom(run.log, from);
			int count = 0;
			int at = text.indexOf(path);
			while (at >= 0) {
				count++;
				at = text.indexOf(path, at + path.length());
			}
			if (count >= 2) {
				int status = httpStatus(run.port, run.contextPath);
				return status == HttpURLConnection.HTTP_NOT_FOUND || status < 0 ? "다시 올린 앱이 응답하지 않습니다(" + status + ")" : null;
			}
			if (count == 0 && System.currentTimeMillis() - deadline + Long.parseLong(this.config("start-timeout-seconds", "600")) * 1000L > 30000L) {
				return "Tomcat이 30초 안에 다시 올리기를 시작하지 않았습니다";
			}
			sleep(1000L);
		}
		return "다시 올리기가 끝나지 않았습니다";
	}

	/** 테스트 드라이버를 거치지 않은 DB 연결이 있는지 봅니다. 있으면 이유. */
	private String bypassCheck(Run run) {
		int watched = run.guard.watchedCount();
		int physical = run.driver.physicalConnections();
		if (physical >= 0 && watched > physical) {
			this.stop(run);
			return "앱이 테스트 드라이버를 거치지 않고 개발 DB에 직접 연결했습니다(연결 " + watched + "개, 드라이버 " + physical + "개). "
				+ "테스트 데이터를 원복할 수 없어 서버를 내렸습니다. URL을 코드에서 조립하거나 다른 설정 파일에서 읽는 앱일 수 있습니다.";
		}
		return null;
	}

	/** 서버를 내리고 DB를 원복합니다. 원복 문제가 있으면 그 이유(연결이 끊기면 DB가 되돌림). */
	private String stop(Run run) {
		if (run == null) {
			return null;
		}
		String problem = null;
		if (run.driver != null) {
			problem = run.driver.rollback();
		}
		if (run.process != null) {
			killTree(run.process.toHandle());
		}
		if (run.guard != null) {
			run.guard.close();
		}
		this.registry.closed(run.aliases.keySet());
		this.runs.remove(run.key, run);
		// 비밀번호가 든 설정과 실행 층은 지우고 로그만 남긴다
		if (run.dir != null) {
			for (String name : new String[] { "tomcat", "stage", "webapp", "classes", "data", CONTROL_FILE, ProjectBuildService.RUN_REVISION_FILE, "server.pid" }) {
				RunLayer.deleteTreeQuietly(new File(run.dir, name));
			}
		}
		return problem;
	}

	/** 유휴·DB 점유 상한을 넘은 서버를 내립니다(1분마다). */
	private void reap() {
		long now = System.currentTimeMillis();
		long idle = Long.parseLong(this.config("idle-minutes", "30")) * 60000L;
		long hold = Long.parseLong(this.config("db-hold-minutes", "60")) * 60000L;
		Iterator<Run> it = new ArrayList<Run>(this.runs.values()).iterator();
		while (it.hasNext()) {
			Run run = it.next();
			try {
				if (run.process == null || !run.process.isAlive()) {
					this.info("dstone-ai-engine tool: 테스트 서버 프로세스가 끝나 있어 정리합니다: " + run.key);
					this.stop(run);
				} else if (run.idleSince > 0 && now - run.idleSince > idle) {
					this.info("dstone-ai-engine tool: 유휴 시간이 지나 테스트 서버를 내립니다: " + run.key);
					this.stop(run);
				} else if (run.idleSince == 0 && now - run.dbSince > hold) {
					this.warn("dstone-ai-engine tool: 개발 DB 연결을 " + (hold / 60000L) + "분 넘게 잡아 원복하고 테스트 서버를 내립니다: " + run.key);
					this.stop(run);
				}
			} catch (RuntimeException e) {
				this.warn("dstone-ai-engine tool: 테스트 서버 정리 실패(" + run.key + "): " + e.getMessage());
			}
		}
	}

	/** 동시 서버 수가 상한이면 lock-wait-seconds까지 기다립니다. */
	private boolean waitForSlot(Answer answer) {
		int max = Integer.parseInt(this.config("max-servers", "3"));
		long deadline = System.currentTimeMillis() + Long.parseLong(this.config("lock-wait-seconds", "60")) * 1000L;
		while (this.runs.size() >= max) {
			if (System.currentTimeMillis() >= deadline) {
				answer.cause = CAUSE_ENVIRONMENT;
				answer.message = "동시에 띄울 수 있는 테스트 서버(" + max + "개)가 모두 쓰이는 중입니다. 다른 실행이 끝난 뒤 다시 부르십시오.";
				return false;
			}
			sleep(2000L);
		}
		return true;
	}

	/* ---------------------------------------------------------------- 결과 글 */

	private String describe(Run run, String head) {
		StringBuilder message = new StringBuilder(head).append('\n');
		message.append("접속주소: ").append(run.url).append('\n');
		message.append("서버로그: ").append(run.log.getPath()).append('\n');
		message.append("리비전: ").append(run.revision).append(", 방식: ").append(run.boot ? "Spring Boot(java -cp)" : "Tomcat(" + run.tomcatHome + ")")
			.append(", JDK: ").append(run.javaHome == null ? "엔진과 같은 JDK(" + System.getProperty("java.version") + ")" : run.javaHome.getPath()).append('\n');
		message.append("테스트 DB 별칭(dbQuery의 alias, 커밋하지 않은 테스트 데이터가 보임): ").append(String.join(", ", run.aliases.keySet())).append('\n');
		message.append("DB: 고객사 개발 DB를 쓰되 커밋하지 않습니다. 실행이 멈추거나 끝나면 원복합니다. 테스트 중 DDL은 실행되지 않습니다.\n");
		message.append(this.accountLine(run.projectId));
		String sample = this.uploadSample();
		if (sample != null) {
			message.append("\n업로드 테스트용 파일(browser_file_upload의 paths): ").append(sample);
		}
		for (String note : run.notes) {
			message.append('\n').append(note);
		}
		message.append(this.deniedNote(run));
		message.append("\n원래 환경과 다른 점(이것 때문에 생긴 실패는 개발 결과 탓이 아닙니다):");
		for (String difference : run.differences) {
			message.append("\n- ").append(difference);
		}
		message.append("\n- 테스트 DB는 한 연결을 차례로 씁니다. 다른 요청이 오래 쓰면 'dstone 테스트 DB … 잠금 대기' 오류가 날 수 있습니다.");
		return message.toString();
	}

	private String deniedNote(Run run) {
		if (run.guard == null || run.guard.denied().isEmpty()) {
			return "";
		}
		return "\n막은 외부 접속: " + String.join(", ", run.guard.denied()) + " (앱에 꼭 필요하면 관리자가 설정 allow-hosts에 '프로젝트ID=host:port'로 더한다)";
	}

	/** 기동 실패의 원인. 막은 외부 접속, DB 로그인 실패, 드라이버 문제면 환경, 아니면 개발 결과 */
	private String startupCause(Run run) {
		String text = tail(run.log, 400);
		boolean environment = (run.guard != null && !run.guard.denied().isEmpty())
			|| Pattern.compile("Access denied for user|password authentication failed|ORA-01017|No suitable driver|dstone 테스트 DB").matcher(text).find();
		return environment ? CAUSE_ENVIRONMENT : CAUSE_CODE;
	}

	/** 테스트계정 안내 한 줄입니다. 비밀번호는 자리표시자만 줍니다. */
	private String accountLine(String projectId) {
		if (isBlank(projectId)) {
			return "테스트계정: 없음(프로젝트ID가 없음). 로그인이 필요 없는 화면만 볼 수 있습니다.";
		}
		try {
			Map<String, Object> project = this.projectStore.find(projectId.trim());
			Object userId = project == null ? null : project.get("testUserId");
			if (userId == null || isBlank(userId.toString())) {
				return "테스트계정: 없음(프로젝트 관리에 테스트계정이 없음). 로그인이 필요 없는 화면만 볼 수 있습니다.";
			}
			if (!"Y".equals(project.get("hasTestPassword"))) {
				return "테스트계정: ID " + userId + " (비밀번호는 프로젝트 관리에 없음)";
			}
			return "테스트계정: ID " + userId + ", 비밀번호 칸에는 ${secret:" + projectId.trim() + "." + ProjectStore.SECRET_TEST_PASSWORD
				+ "}를 글자 그대로 넣는다(browser_type, browser_fill_form에서만 엔진이 실제 값으로 바꿔 넣는다)";
		} catch (RuntimeException e) {
			return "테스트계정: 읽지 못했습니다(" + e.getMessage() + ")";
		}
	}

	/** 업로드 화면을 테스트할 그림 파일(설정 upload-sample-dir). */
	private String uploadSample() {
		String dir = this.config("upload-sample-dir", "");
		if (dir.isEmpty() || dir.equals("/output") || new File(dir).getParentFile() == null || !new File(dir).getParentFile().isDirectory()) {
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
			return null;
		}
	}

	/* ---------------------------------------------------------------- 설정과 도우미 */

	/** 프로젝트의 DB들(쓰는 프로젝트만): {dbName, url, username, password, schema, jndiName} */
	private List<Map<String, String>> projectDbs(String projectId) {
		List<Map<String, String>> result = new ArrayList<Map<String, String>>();
		for (Map.Entry<String, Map<String, String>> entry : this.projectStore.connections().entrySet()) {
			if (entry.getKey().startsWith(projectId + ".")) {
				Map<String, String> db = new LinkedHashMap<String, String>(entry.getValue());
				db.put("dbName", entry.getKey().substring(projectId.length() + 1));
				result.add(db);
			}
		}
		return result;
	}

	/** 테스트서버 정적 경로. 원본 소스 안의 폴더는 data 쪽으로 바꿔 엽니다. */
	private List<String[]> staticPaths(Map<String, Object> project, Run run) {
		List<String[]> result = new ArrayList<String[]>();
		List<String[]> configured;
		try {
			configured = ProjectStore.parseStaticPaths(text(project, "testStaticPaths"));
		} catch (IllegalArgumentException e) {
			run.notes.add("테스트서버 정적 경로를 읽지 못했습니다: " + e.getMessage());
			return result;
		}
		String source = text(project, "sourceDir");
		String sourcePrefix = source == null ? null : source.replace('\\', '/').replaceAll("/+$", "");
		List<String> opened = new ArrayList<String>();
		for (String[] path : configured) {
			String folder = path[1];
			if (sourcePrefix != null && folder.toLowerCase(Locale.ROOT).startsWith(sourcePrefix.toLowerCase(Locale.ROOT) + "/")) {
				File data = new File(new File(run.dir, "data"), folder.substring(sourcePrefix.length() + 1));
				data.mkdirs();
				folder = data.getAbsolutePath();
			}
			if (!new File(folder).isDirectory()) {
				run.notes.add("테스트서버 정적 경로 " + path[0] + "의 폴더가 없어 열지 않았습니다: " + folder);
				continue;
			}
			result.add(new String[] { path[0], folder });
			opened.add(path[0] + " → " + folder);
		}
		run.differences.add("웹앱 밖 폴더를 여는 주소는 프로젝트 관리의 '테스트서버 정적 경로'에 적힌 것만 엽니다. 그 밖의 주소가 404면 원래 서버의 웹서버 설정 차이일 수 있습니다. 지금 연 것: "
			+ (opened.isEmpty() ? "없음" : String.join(", ", opened)));
		return result;
	}

	private static String staticPathsNotApplied(Map<String, Object> project) {
		return isBlank(text(project, "testStaticPaths")) ? "웹앱 밖 폴더를 여는 주소(원래 서버의 웹서버 설정)는 없습니다. 그런 주소가 404면 환경 차이일 수 있습니다"
			: "프로젝트 관리의 '테스트서버 정적 경로'는 Tomcat 방식에서만 엽니다. Spring Boot에서는 열지 않았습니다";
	}

	private static String contextPathOf(Map<String, Object> project, WasCheck.Result was) {
		String path = text(project, "contextPath");
		if (path == null) {
			path = was.contextPath;
		}
		if (path == null || path.isEmpty()) {
			return "/";
		}
		path = path.replaceAll("/+$", "");
		return path.isEmpty() ? "/" : path.startsWith("/") ? path : "/" + path;
	}

	/** 쓸 JDK(프로젝트 Java 버전 → 리비전 클래스 파일). 못 찾으면 null(엔진 JDK)이고 notes에 남깁니다. */
	private File javaHome(Map<String, Object> project, List<String> notes) {
		String version = text(project, "javaVersion");
		if (version == null) {
			return null;
		}
		File home = this.projectBuildService.javaHome(version);
		if (home == null && !version.equals(String.valueOf(Runtime.version().feature()))) {
			notes.add("참고: Java " + version + "용 JDK 경로가 설정(env의 JAVA_HOME_" + version + ")에 없어 엔진과 같은 JDK(Java " + Runtime.version().feature()
				+ ")로 띄웠습니다. 서버가 이상하게 동작하면 이것부터 의심하십시오.");
		}
		return home;
	}

	/** javax 앱은 tomcat-home-9, jakarta 앱은 tomcat-home-10, 11. 없으면 예전 설정 tomcat-home. */
	private File tomcatHome(boolean jakarta) {
		String[] keys = jakarta ? new String[] { "tomcat-home-10", "tomcat-home-11" } : new String[] { "tomcat-home-9", "tomcat-home" };
		for (String key : keys) {
			String path = this.config(key, "");
			if (!isBlank(path)) {
				File home = new File(path.trim());
				if (new File(home, isWindows() ? "bin/catalina.bat" : "bin/catalina.sh").isFile()) {
					return home;
				}
			}
		}
		return null;
	}

	/** 엔진이 죽어 남은 테스트 서버 프로세스를 정리합니다(명령줄 표시가 있을 때만). */
	private void killLeftovers() {
		File[] projects = this.projectBuildService.workRoot().listFiles();
		if (projects == null) {
			return;
		}
		for (File project : projects) {
			File[] runDirs = new File(project, "runs").listFiles();
			if (runDirs == null) {
				continue;
			}
			for (File runDir : runDirs) {
				File pidFile = new File(runDir, "server.pid");
				if (!pidFile.isFile()) {
					continue;
				}
				try {
					long pid = Long.parseLong(Files.readString(pidFile.toPath(), StandardCharsets.UTF_8).trim());
					Optional<ProcessHandle> handle = ProcessHandle.of(pid);
					if (handle.isPresent() && handle.get().isAlive()) {
						Optional<String> commandLine = handle.get().info().commandLine();
						if (commandLine.isPresent() && commandLine.get().contains(MARKER)) {
							this.info("dstone-ai-engine tool: 남아 있던 테스트 서버 프로세스를 내립니다: " + runDir + " pid=" + pid);
							killTree(handle.get());
						}
					}
					Files.deleteIfExists(pidFile.toPath());
				} catch (IOException | RuntimeException e) {
					this.warn("dstone-ai-engine tool: 남은 테스트 서버를 확인하지 못했습니다(" + runDir + "): " + e.getMessage());
				}
			}
		}
	}

	/** JDBC URL의 DB 주소들입니다(MySQL·MariaDB·PostgreSQL은 //host:port, Oracle은 @host:port 또는 HOST=·PORT=). */
	static List<SandboxGuard.Endpoint> endpointsOf(String url) {
		List<SandboxGuard.Endpoint> result = new ArrayList<SandboxGuard.Endpoint>();
		if (url == null) {
			return result;
		}
		String text = url.replaceFirst("^jdbc:(log4jdbc:)?", "");
		int defaultPort = text.startsWith("postgresql") ? 5432 : text.startsWith("oracle") ? 1521 : text.startsWith("sqlserver") ? 1433 : 3306;
		Matcher description = Pattern.compile("(?i)\\(HOST\\s*=\\s*([^)\\s]+)\\)\\s*\\(PORT\\s*=\\s*(\\d+)\\)").matcher(text);
		while (description.find()) {
			result.add(new SandboxGuard.Endpoint(description.group(1), Integer.parseInt(description.group(2))));
		}
		if (!result.isEmpty()) {
			return result;
		}
		Matcher m = Pattern.compile("(?://|@//|@)([^/?;:@]+(?::\\d+)?(?:,[^/?;:@]+(?::\\d+)?)*)").matcher(text);
		if (m.find()) {
			for (String part : m.group(1).split(",")) {
				int colon = part.lastIndexOf(':');
				String host = colon > 0 ? part.substring(0, colon) : part;
				int port = colon > 0 ? Integer.parseInt(part.substring(colon + 1)) : defaultPort;
				result.add(new SandboxGuard.Endpoint(host.replace("[", "").replace("]", ""), port));
			}
		}
		return result;
	}

	/** "프로젝트ID=값, 프로젝트ID=값" 모양의 설정에서 그 프로젝트의 값만 쉼표로 이어 돌려줍니다. */
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

	private static String keyOf(StepRef stepRef, String projectId) {
		return stepRef != null && stepRef.executionId() != null ? stepRef.executionId() : "adhoc-" + (projectId == null ? "" : projectId.trim());
	}

	private static String stripEnv(String jndiName) {
		String name = jndiName.trim();
		return name.startsWith("java:comp/env/") ? name.substring("java:comp/env/".length()) : name;
	}

	private static String safe(String key) {
		return key.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static File fileOrNull(Object path) {
		return path == null || isBlank(String.valueOf(path)) ? null : new File(String.valueOf(path));
	}

	private static List<String> splitList(String text) {
		List<String> result = new ArrayList<String>();
		if (!isBlank(text)) {
			for (String part : text.split("[,\\n]")) {
				if (!part.trim().isEmpty()) {
					result.add(part.trim());
				}
			}
		}
		return result;
	}

	private static String text(Map<String, Object> map, String key) {
		Object value = map == null ? null : map.get(key);
		return value == null || String.valueOf(value).trim().isEmpty() ? null : String.valueOf(value).trim();
	}

	private String config(String name, String defaultValue) {
		String value = this.configProperty.getProperty(Constants.Tool.TestServer.PREFIX + "." + name);
		return isBlank(value) ? defaultValue : value.trim();
	}

	/** 접속될 때까지 기다립니다(HTTP 응답이 오면 뜬 것). 뜨면 null. */
	private static String waitUntilUp(Process process, int port, String contextPath, int timeoutSeconds) {
		long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
		while (System.currentTimeMillis() < deadline) {
			if (!process.isAlive()) {
				return "프로세스가 끝났습니다. 종료 코드 " + process.exitValue();
			}
			if (httpStatus(port, contextPath) > 0) {
				return null;
			}
			try {
				process.waitFor(2, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return "기다리는 중에 중단되었습니다";
			}
		}
		return timeoutSeconds + "초 안에 접속되지 않았습니다";
	}

	private static int httpStatus(int port, String path) {
		try {
			HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + port + ("/".equals(path) ? "/" : path + "/")).toURL().openConnection();
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

	/** 프로세스와 자식들을 내립니다. 10초 안에 안 끝나면 강제로. */
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
				sleep(200L);
			}
			if (handle.isAlive()) {
				handle.destroyForcibly();
			}
		}
	}

	private static String tail(File log, int lines) {
		try {
			String text = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
			List<String> all = Arrays.asList(text.split("\\r?\\n", -1));
			return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
		} catch (IOException | RuntimeException e) {
			return "(로그를 읽지 못했습니다: " + e.getMessage() + ")";
		}
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

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

}

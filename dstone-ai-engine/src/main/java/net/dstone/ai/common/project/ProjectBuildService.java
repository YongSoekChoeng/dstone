package net.dstone.ai.common.project;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.ai.tools.build.JavaCompileTool;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 프로젝트 관리의 [Build]를 처리합니다(리비전 빌드). 설계: docs/temp/dstone-ai-테스트관련재설계/01.설계.md 4장.
 * [Build]는 백그라운드 스레드로(startBuild), Workflow의 리비전 갱신은 끝날 때까지 기다려(refresh) 같은 순서를 밟습니다.
 *
 * ## 순서(같은 프로젝트는 한 번에 하나)
 * 1. 형상관리가 있으면 소스 디렉토리를 갱신한다(git pull --ff-only, svn update). 폴더가 비었으면 받는다(clone, checkout).
 * 2. 리비전 이름: 형상관리 리비전 앞 12자, 없으면 빌드 시각(yyyyMMdd-HHmmss).
 *    같은 리비전이 이미 OK면 다시 빌드하지 않고 현재 리비전만 바꾼다. INPUT(구조 입력 필요)이면 빌드 없이 구조만 다시 찾는다.
 * 3. 소스를 {작업 루트}/{프로젝트ID}/rev/{리비전}/src 로 복사한다(.git·.svn, 루트의 target·build 제외).
 *    소스 디렉토리가 나중에 바뀌어도 진행 중인 실행에 영향이 없게 하려는 것이다.
 * 4. 종류별로 정해진 명령을 실행한다. 화면에서 받는 것은 인자뿐이다(셸 해석 글자는 거절).
 *      MAVEN   {maven-command} -B -DskipTests package {인자}
 *      GRADLE  gradlew(있으면) 또는 gradle: build -x test {인자}
 *      ANT     ant {인자}
 *      SHELL   dstone.ai.tool.shell.allowed-commands 에 등록된 스크립트(인자 = 등록 이름), 스크립트 인자로 리비전 src 경로
 *      NONE    엔진이 소스 전체를 javac(JavaCompileTool.compileAll) → rev/{리비전}/classes
 * 5. 산출물을 찾는다(detectOutputs). 경로는 리비전 폴더 기준 상대경로로 남긴다.
 * 6. 성공(OK)하면 현재 리비전을 바꾸고, 오래된 리비전을 지운다(keep-revisions, 실행이 쓰는 것과 현재 것은 남김).
 *
 * ## 리비전 정리
 * 빌드에 성공하면 오래된 리비전을 지웁니다. 현재 리비전, 테스트 서버가 쓰는 리비전, 끝나지 않은 실행이 받아 간 리비전
 * ({작업 루트}/{프로젝트ID}/pins/{실행ID})은 keep-revisions를 넘어도 지우지 않습니다.
 *
 * 형상관리 비밀번호는 명령줄에 싣지 않는다(git은 GIT_ASKPASS 스크립트가 환경변수에서 읽고, svn은 --password-from-stdin).
 * 리비전 폴더는 빌드가 끝나면 읽기만 한다. 고객의 소스 디렉토리 값은 바꾸지 않는다.
 * </pre>
 */
@Service
public class ProjectBuildService extends BaseObject {

	/** 기본 작업 루트(사용자 홈 아래) */
	public static final String DEFAULT_ROOT_NAME = ".dstone-ai-engine";

	/** 리비전 상태 */
	public static final String STATUS_BUILDING = "BUILDING";
	public static final String STATUS_OK = "OK";
	public static final String STATUS_FAILED = "FAILED";
	public static final String STATUS_INPUT = "INPUT";

	/** 산출물 종류 */
	public static final String KIND_WAR = "WAR";
	public static final String KIND_BOOT = "BOOT";

	/** 실행 폴더가 쓰는 리비전을 적어 두는 파일 이름(runs/{실행ID}/revision.txt). 정리할 때 이 리비전은 지우지 않습니다. */
	public static final String RUN_REVISION_FILE = "revision.txt";

	/** 실행이 받아 간 리비전을 적어 두는 폴더(pins/{실행ID}) */
	private static final String PIN_DIR = "pins";

	/** 리비전 폴더의 산출물 위치 안내 파일(JavaCompileTool이 읽음) */
	public static final String LAYOUT_FILE = "build-layout.properties";

	/** 화면의 Java 버전 목록 */
	public static final List<String> JAVA_VERSIONS = List.of("6", "7", "8", "11", "17", "21", "25");

	/** 빌드 인자에 쓸 수 없는 글자(Windows는 cmd.exe가 해석하므로) */
	private static final String FORBIDDEN_ARG_CHARS = ";&|`$<>^%\"'\r\n";

	/** 복사하지 않는 폴더(어디에 있든) */
	private static final Set<String> SKIP_ANYWHERE = Set.of(".git", ".svn", ".gradle", "node_modules", ".idea");

	/** 복사하지 않는 폴더(루트 바로 아래만. 지난 빌드 결과) */
	private static final Set<String> SKIP_AT_ROOT = Set.of("target", "build");

	/** 결과에 붙일 로그 끝 줄 수 */
	private static final int LOG_TAIL_LINES = 40;

	@Autowired
	private ProjectStore projectStore;

	@Autowired
	private ConfigProperty configProperty;

	@Autowired
	private JavaCompileTool javaCompileTool;

	@Autowired
	private WorkFlowExecutionStore executionStore;

	/** 프로젝트별 빌드 차례(한 번에 하나, 먼저 온 순서대로) */
	private final Map<String, Semaphore> buildTurns = new ConcurrentHashMap<String, Semaphore>();

	/** 프로젝트별로 리비전을 받아 가는 일과 지우는 일이 겹치지 않게 하는 잠금 */
	private final Map<String, Object> pinLocks = new ConcurrentHashMap<String, Object>();

	/** 프로젝트별 마지막 빌드 실패. 기다리던 실행이 같은 리비전을 다시 빌드하지 않게 합니다 */
	private final Map<String, BuildOutcome> lastFailures = new ConcurrentHashMap<String, BuildOutcome>();

	/** 프로젝트별 마지막 빌드 안내(화면이 읽음) */
	private final Map<String, String> lastMessage = new ConcurrentHashMap<String, String>();

	/** 엔진이 다시 뜨면 빌드 중이던 기록은 끝나지 못한 것입니다. */
	@PostConstruct
	public void init() {
		try {
			this.projectStore.abandonBuildingRevisions();
		} catch (RuntimeException e) {
			this.warn("dstone-ai-engine project: 빌드 중이던 리비전 기록을 정리하지 못했습니다: " + e.getMessage());
		}
	}

	/**
	 * 빌드를 백그라운드로 시작합니다.
	 *
	 * @return 안내
	 * @throws IllegalArgumentException 프로젝트 설정이 모자람
	 * @throws IllegalStateException    이미 빌드 중
	 */
	public String startBuild(final String projectId, final String by) {
		final Map<String, Object> project = this.projectStore.settings(projectId);
		if (project == null) {
			throw new IllegalArgumentException("프로젝트가 없습니다: " + projectId);
		}
		if (isBlank(text(project, "sourceDir"))) {
			throw new IllegalArgumentException("소스 디렉토리를 먼저 저장하세요.");
		}
		String buildType = text(project, "buildType");
		if (isBlank(buildType)) {
			throw new IllegalArgumentException("빌드 종류를 먼저 저장하세요.");
		}
		checkArgs(text(project, "buildArgs"));
		final Semaphore turn = this.buildTurn(projectId);
		if (!turn.tryAcquire()) {
			throw new IllegalStateException("이 프로젝트는 이미 빌드 중입니다.");
		}
		this.lastMessage.put(projectId, "빌드 중입니다.");
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					BuildOutcome outcome = ProjectBuildService.this.build(projectId, project, by, 0L);
					ProjectBuildService.this.lastMessage.put(projectId, outcome.message());
				} catch (Exception e) {
					ProjectBuildService.this.error("dstone-ai-engine project: 빌드 중 오류 " + projectId + ": " + e);
					ProjectBuildService.this.lastMessage.put(projectId, "빌드 중 오류가 났습니다: " + e.getMessage());
				} finally {
					turn.release();
				}
			}
		}, "dstone-project-build-" + projectId);
		thread.setDaemon(true);
		thread.start();
		return "빌드를 시작했습니다. 몇 분 걸릴 수 있습니다.";
	}

	/**
	 * <pre>
	 * 형상관리에서 새 소스를 받아 리비전을 만들고 끝날 때까지 기다립니다([Build]와 같은 순서).
	 * 새 커밋이 없으면 빌드하지 않고 지난 리비전을 그대로 씁니다. 같은 프로젝트를 빌드 중이면 차례를 기다립니다.
	 * 형상관리가 없는 프로젝트(리비전 이름이 빌드 시각이라 매번 다시 빌드하게 됨)와 빌드 설정이 없는 프로젝트는 갱신하지 않습니다.
	 * </pre>
	 *
	 * @return 갱신 결과. 갱신 대상이 아니면 null
	 * @throws IllegalArgumentException 빌드 인자가 잘못됨
	 */
	public BuildOutcome refresh(String projectId, String by) throws IOException, InterruptedException {
		if (!isRefreshable(this.projectStore.settings(projectId))) {
			return null;
		}
		long asked = System.currentTimeMillis();
		Semaphore turn = this.buildTurn(projectId);
		turn.acquire();
		try {
			// 기다리는 동안 설정이 바뀌었을 수 있어 다시 읽는다
			Map<String, Object> project = this.projectStore.settings(projectId);
			if (!isRefreshable(project)) {
				return null;
			}
			checkArgs(text(project, "buildArgs"));
			this.lastMessage.put(projectId, "빌드 중입니다.");
			BuildOutcome outcome = this.build(projectId, project, by, asked);
			this.lastMessage.put(projectId, outcome.message());
			return outcome;
		} catch (IOException e) {
			this.lastMessage.put(projectId, "빌드 중 오류가 났습니다: " + e.getMessage());
			throw e;
		} finally {
			turn.release();
		}
	}

	/** Workflow가 갱신할 수 있는 프로젝트인지(형상관리, 소스 디렉토리, 빌드 종류가 있는지) 봅니다. */
	static boolean isRefreshable(Map<String, Object> project) {
		if (project == null) {
			return false;
		}
		String scmType = text(project, "scmType");
		return ("GIT".equals(scmType) || "SVN".equals(scmType)) && !isBlank(text(project, "sourceDir")) && !isBlank(text(project, "buildType"));
	}

	private Semaphore buildTurn(String projectId) {
		Semaphore turn = this.buildTurns.get(projectId);
		if (turn == null) {
			this.buildTurns.putIfAbsent(projectId, new Semaphore(1, true));
			turn = this.buildTurns.get(projectId);
		}
		return turn;
	}

	private Object pinLock(String projectId) {
		Object lock = this.pinLocks.get(projectId);
		if (lock == null) {
			this.pinLocks.putIfAbsent(projectId, new Object());
			lock = this.pinLocks.get(projectId);
		}
		return lock;
	}

	/**
	 * <pre>
	 * resolve와 같지만, 실행이 이 리비전을 쓴다고 적어 두어 실행이 끝나기 전에는 지워지지 않게 합니다.
	 * executionId가 없으면(Workflow 밖) 적지 않습니다.
	 * </pre>
	 */
	public Map<String, Object> resolveAndPin(String projectId, String revision, String executionId) throws IOException {
		synchronized (this.pinLock(projectId)) {
			Map<String, Object> resolved = this.resolve(projectId, revision);
			if (resolved != null && isSafeName(executionId)) {
				File dir = new File(this.projectDir(projectId), PIN_DIR);
				Files.createDirectories(dir.toPath());
				Files.writeString(new File(dir, executionId).toPath(), String.valueOf(resolved.get("revision")), StandardCharsets.UTF_8);
			}
			return resolved;
		}
	}

	/** 빌드 중인지와 마지막 안내입니다. */
	public Map<String, Object> status(String projectId) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("running", Boolean.valueOf(this.buildTurn(projectId).availablePermits() == 0));
		result.put("message", this.lastMessage.get(projectId));
		return result;
	}

	/** 리비전 빌드 로그의 끝부분입니다. 없으면 빈 글. */
	public String logTail(String projectId, String revision, int lines) {
		if (!isSafeName(revision)) {
			return "";
		}
		return tail(new File(this.revisionDir(projectId, revision), "build.log"), lines);
	}

	/**
	 * <pre>
	 * 리비전의 산출물 위치(절대경로)입니다. revision이 비면 현재 리비전. 빌드된(OK) 리비전이 없으면 null입니다.
	 * 결과: {projectId, revision, revisionDir, sourceRoot, kind, webRoot, classesDir, libDir, mainClass}
	 * </pre>
	 */
	public Map<String, Object> resolve(String projectId, String revision) {
		Map<String, Object> project = this.projectStore.settings(projectId);
		if (project == null) {
			return null;
		}
		String name = isBlank(revision) ? text(project, "currentRevision") : revision.trim();
		if (isBlank(name)) {
			return null;
		}
		Map<String, Object> row = this.projectStore.revision(projectId, name);
		File dir = this.revisionDir(projectId, name);
		if (row == null || !STATUS_OK.equals(row.get("status")) || !dir.isDirectory()) {
			return null;
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("projectId", projectId);
		result.put("revision", name);
		result.put("revisionDir", dir.getAbsolutePath());
		result.put("sourceRoot", new File(dir, "src").getAbsolutePath());
		result.put("kind", row.get("kind"));
		result.put("webRoot", absolute(dir, (String) row.get("webRoot")));
		result.put("classesDir", absolute(dir, (String) row.get("classesDir")));
		result.put("libDir", absolute(dir, (String) row.get("libDir")));
		result.put("mainClass", row.get("mainClass"));
		return result;
	}

	/** 화면의 Java 버전 목록. {version, available(이 서버에 JDK가 있는지)} */
	public List<Map<String, Object>> javaVersions() {
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		for (String version : JAVA_VERSIONS) {
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("version", version);
			item.put("available", Boolean.valueOf(this.javaHome(version) != null || version.equals(String.valueOf(Runtime.version().feature()))));
			result.add(item);
		}
		return result;
	}

	/** SHELL 빌드에 고를 수 있는 스크립트 이름(dstone.ai.tool.shell.allowed-commands)입니다. */
	@SuppressWarnings("rawtypes")
	public List<String> shellScripts() {
		List<String> names = new ArrayList<String>();
		List allowed = this.configProperty.getListProperty(Constants.Tool.Shell.PREFIX + ".allowed-commands");
		if (allowed != null) {
			for (Object entry : allowed) {
				if (entry instanceof Map) {
					names.add(String.valueOf(((Map) entry).get("name")));
				}
			}
		}
		return names;
	}

	/** 작업 루트(dstone.ai.tool.test-server.work-root, 비우면 {홈}/.dstone-ai-engine)입니다. */
	public File workRoot() {
		String configured = this.config("work-root", "");
		return isBlank(configured) ? new File(System.getProperty("user.home"), DEFAULT_ROOT_NAME) : new File(configured.trim());
	}

	/** 프로젝트 폴더({작업 루트}/{프로젝트ID})입니다. */
	public File projectDir(String projectId) {
		return new File(this.workRoot(), projectId);
	}

	/** 리비전 폴더입니다. */
	public File revisionDir(String projectId, String revision) {
		return new File(new File(this.projectDir(projectId), "rev"), revision);
	}

	/** 버전의 JDK 폴더(설정 java-home-{버전})입니다. 없으면 null. */
	public File javaHome(String version) {
		if (isBlank(version)) {
			return null;
		}
		String path = this.config("java-home-" + version.trim(), "");
		File dir = isBlank(path) ? null : new File(path.trim());
		return dir != null && dir.isDirectory() ? dir : null;
	}

	/* ---------------------------------------------------------------- 빌드 */

	/** @param notBefore 이 시각 뒤에 같은 리비전이 이미 실패했으면 다시 빌드하지 않습니다(0이면 늘 빌드) */
	private BuildOutcome build(String projectId, Map<String, Object> project, String by, long notBefore) throws IOException, InterruptedException {
		File projectDir = this.projectDir(projectId);
		Files.createDirectories(new File(projectDir, "rev").toPath());
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		File log = new File(projectDir, "build-" + stamp + ".log");
		File source = new File(text(project, "sourceDir"));
		Map<String, String> env = this.buildEnv(project);

		// 1) 형상관리
		String scmRevision = null;
		String scmType = text(project, "scmType");
		if ("GIT".equals(scmType) || "SVN".equals(scmType)) {
			String[] scm = this.updateSource(projectId, project, source, log, env);
			if (scm[1] != null) {
				return BuildOutcome.failure(stamp, this.failEarly(projectId, stamp, by, log, "형상관리에서 소스를 받지 못했습니다: " + scm[1]));
			}
			scmRevision = scm[0];
		}
		if (!source.isDirectory()) {
			return BuildOutcome.failure(stamp, this.failEarly(projectId, stamp, by, log, "소스 디렉토리가 없습니다: " + source));
		}

		// 2) 리비전 이름
		String revision = revisionName(scmRevision, stamp);
		File revDir = this.revisionDir(projectId, revision);
		Map<String, Object> previous = this.projectStore.revision(projectId, revision);
		if (previous != null && STATUS_OK.equals(previous.get("status")) && revDir.isDirectory()) {
			this.projectStore.setCurrentRevision(projectId, revision);
			Files.deleteIfExists(log.toPath());
			return BuildOutcome.success(revision, "이미 빌드한 리비전이라 다시 빌드하지 않고 현재 리비전으로 정했습니다: " + revision);
		}
		BuildOutcome failed = this.lastFailures.get(projectId);
		if (notBefore > 0 && failed != null && failed.revision().equals(revision) && failed.finishedAt() >= notBefore) {
			Files.deleteIfExists(log.toPath());
			return failed;
		}
		boolean onlyDetect = previous != null && STATUS_INPUT.equals(previous.get("status")) && new File(revDir, "src").isDirectory();
		this.projectStore.startRevision(projectId, revision, scmRevision, by);
		Files.createDirectories(revDir.toPath());
		File revLog = new File(revDir, "build.log");
		if (!onlyDetect) {
			Files.deleteIfExists(revLog.toPath());
		}
		if (log.isFile()) {
			Files.write(revLog.toPath(), Files.readAllBytes(log.toPath()), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
			Files.delete(log.toPath());
		}

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		File src = new File(revDir, "src");
		String buildType = text(project, "buildType");
		long since;
		if (onlyDetect) {
			append(revLog, "\n== 구조만 다시 찾습니다(빌드는 하지 않음)\n");
			since = 0L;
		} else {
			// 3) 복사
			deleteTree(src.toPath());
			deleteTree(new File(revDir, "webapp").toPath());
			deleteTree(new File(revDir, "classes").toPath());
			deleteTree(new File(revDir, "lib").toPath());
			append(revLog, "\n== 소스 복사: " + source + " → " + src + "\n");
			copySource(source.toPath(), src.toPath());
			since = System.currentTimeMillis() - 2000L;

			// 4) 빌드
			append(revLog, "== 빌드(" + buildType + ")\n");
			String problem = this.runBuild(project, revDir, src, revLog, env);
			if (problem != null) {
				result.put("status", STATUS_FAILED);
				result.put("message", problem + "\n" + tail(revLog, LOG_TAIL_LINES));
				this.projectStore.finishRevision(projectId, revision, result);
				return this.remember(projectId, BuildOutcome.failure(revision, "빌드에 실패했습니다(" + problem + "). 현재 리비전은 바꾸지 않았습니다. 로그: " + revLog));
			}
		}

		// 5) 산출물 찾기
		Map<String, Object> outputs = detectOutputs(revDir, since, project);
		result.putAll(outputs);
		this.projectStore.finishRevision(projectId, revision, result);
		if (!STATUS_OK.equals(outputs.get("status"))) {
			return this.remember(projectId, BuildOutcome.failure(revision, String.valueOf(outputs.get("message"))));
		}
		writeLayout(revDir, outputs);
		this.projectStore.setCurrentRevision(projectId, revision);
		this.lastFailures.remove(projectId);
		this.cleanup(projectId, revision);
		return BuildOutcome.success(revision, "빌드했습니다. 현재 리비전: " + revision + " (" + outputs.get("kind") + ")");
	}

	private BuildOutcome remember(String projectId, BuildOutcome failure) {
		this.lastFailures.put(projectId, failure);
		return failure;
	}

	/** 리비전을 정하기 전에 실패한 것도 기록에 남깁니다(리비전 이름은 빌드 시각). */
	private String failEarly(String projectId, String stamp, String by, File log, String message) throws IOException {
		this.projectStore.startRevision(projectId, stamp, null, by);
		File revDir = this.revisionDir(projectId, stamp);
		Files.createDirectories(revDir.toPath());
		if (log.isFile()) {
			Files.move(log.toPath(), new File(revDir, "build.log").toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("status", STATUS_FAILED);
		result.put("message", message);
		this.projectStore.finishRevision(projectId, stamp, result);
		return message;
	}

	/**
	 * <pre>
	 * 소스 디렉토리를 형상관리에서 갱신합니다. 폴더가 없거나 비었으면 받습니다.
	 * 돌려주는 값: [리비전, 문제(없으면 null)]
	 * </pre>
	 */
	private String[] updateSource(String projectId, Map<String, Object> project, File source, File log, Map<String, String> env) throws IOException, InterruptedException {
		String type = text(project, "scmType");
		String url = text(project, "scmUrl");
		String user = text(project, "scmUsername");
		String password = user == null ? null : this.projectStore.scmPassword(projectId);
		boolean empty = !source.isDirectory() || source.list() == null || source.list().length == 0;
		long timeout = this.buildTimeoutSeconds();
		if ("GIT".equals(type)) {
			Map<String, String> gitEnv = new LinkedHashMap<String, String>(env);
			gitEnv.put("GIT_TERMINAL_PROMPT", "0");
			File askPass = null;
			if (user != null) {
				askPass = writeAskPass(this.projectDir(projectId));
				gitEnv.put("GIT_ASKPASS", askPass.getAbsolutePath());
				gitEnv.put("DSTONE_SCM_USER", user);
				gitEnv.put("DSTONE_SCM_PASS", password == null ? "" : password);
			}
			try {
				int code;
				if (empty) {
					if (isBlank(url)) {
						return new String[] { null, "소스 디렉토리가 비어 있는데 형상관리 URL이 없습니다." };
					}
					Files.createDirectories(source.toPath());
					code = run(List.of("git", "clone", url, source.getAbsolutePath()), source.getParentFile(), gitEnv, log, timeout, null).code;
				} else if (!new File(source, ".git").exists()) {
					return new String[] { null, "소스 디렉토리가 git 작업 폴더가 아닙니다: " + source };
				} else {
					code = run(List.of("git", "-C", source.getAbsolutePath(), "pull", "--ff-only"), source, gitEnv, log, timeout, null).code;
				}
				if (code != 0) {
					return new String[] { null, "git 명령이 실패했습니다(종료 코드 " + code + "). 로컬 변경이 있거나 계정이 틀렸을 수 있습니다." };
				}
				Result head = run(List.of("git", "-C", source.getAbsolutePath(), "rev-parse", "HEAD"), source, gitEnv, log, 60, null);
				return head.code == 0 ? new String[] { head.output.trim(), null } : new String[] { null, "git 리비전을 읽지 못했습니다." };
			} finally {
				if (askPass != null) {
					Files.deleteIfExists(askPass.toPath());
				}
			}
		}
		// SVN
		List<String> auth = new ArrayList<String>();
		auth.add("--non-interactive");
		if (user != null) {
			auth.add("--username");
			auth.add(user);
			auth.add("--password-from-stdin");
		}
		String stdin = user == null ? null : (password == null ? "" : password) + "\n";
		List<String> command = new ArrayList<String>();
		command.add("svn");
		if (empty) {
			if (isBlank(url)) {
				return new String[] { null, "소스 디렉토리가 비어 있는데 형상관리 URL이 없습니다." };
			}
			Files.createDirectories(source.toPath());
			command.add("checkout");
			command.addAll(auth);
			command.add(url);
			command.add(source.getAbsolutePath());
		} else {
			command.add("update");
			command.addAll(auth);
			command.add(source.getAbsolutePath());
		}
		int code = run(command, source.getParentFile(), env, log, timeout, stdin).code;
		if (code != 0) {
			return new String[] { null, "svn 명령이 실패했습니다(종료 코드 " + code + "). 계정이나 충돌을 확인하십시오(svn 1.10 이상 필요)." };
		}
		Result info = run(List.of("svn", "info", "--show-item", "revision", source.getAbsolutePath()), source, env, log, 60, null);
		return info.code == 0 ? new String[] { "r" + info.output.trim(), null } : new String[] { null, "svn 리비전을 읽지 못했습니다." };
	}

	/** 종류별로 정해진 명령을 실행합니다. 실패면 이유, 성공이면 null. */
	private String runBuild(Map<String, Object> project, File revDir, File src, File log, Map<String, String> env) throws IOException, InterruptedException {
		String type = text(project, "buildType");
		List<String> args = splitArgs(text(project, "buildArgs"));
		List<String> command = new ArrayList<String>();
		boolean windows = isWindows();
		if ("MAVEN".equals(type)) {
			command.addAll(Arrays.asList(this.config("maven-command", "mvn").split("\\s+")));
			command.add("-B");
			command.add("-DskipTests");
			command.add("package");
			command.addAll(args);
		} else if ("GRADLE".equals(type)) {
			File wrapper = new File(src, windows ? "gradlew.bat" : "gradlew");
			if (wrapper.isFile()) {
				wrapper.setExecutable(true);
				command.add(wrapper.getAbsolutePath());
			} else {
				command.add("gradle");
			}
			command.add("build");
			command.add("-x");
			command.add("test");
			command.addAll(args);
		} else if ("ANT".equals(type)) {
			command.add("ant");
			command.addAll(args);
		} else if ("SHELL".equals(type)) {
			String name = args.isEmpty() ? null : args.get(0);
			String path = name == null ? null : this.shellScriptPath(name);
			if (path == null) {
				return "등록된 빌드 스크립트가 아닙니다(dstone.ai.tool.shell.allowed-commands): " + name;
			}
			command.add(path);
			command.add(src.getAbsolutePath());
		} else if ("NONE".equals(type)) {
			List<File> sourceDirs = new ArrayList<File>();
			for (String dir : splitList(text(project, "buildSourceDirs"))) {
				sourceDirs.add(new File(src, dir));
			}
			List<File> libs = new ArrayList<File>();
			if (!isBlank(text(project, "buildLibDir"))) {
				libs.add(new File(src, text(project, "buildLibDir")));
			}
			String javaVersion = text(project, "javaVersion");
			int release = javaVersion == null ? 0 : Integer.parseInt(javaVersion);
			String problem = this.javaCompileTool.compileAll(src, sourceDirs, libs, new File(revDir, "classes"), release, null);
			append(log, problem == null ? "전체 컴파일 통과\n" : problem + "\n");
			return problem == null ? null : "전체 컴파일에 실패했습니다";
		} else {
			return "알 수 없는 빌드 종류입니다: " + type;
		}
		if (windows) {
			// mvn·gradle·ant는 Windows에서 .cmd/.bat라 명령 해석기를 거쳐야 한다(인자의 특수 글자는 checkArgs가 막는다)
			command.add(0, "/c");
			command.add(0, "cmd.exe");
		}
		append(log, "명령: " + String.join(" ", command) + "\n");
		Result result = run(command, src, env, log, this.buildTimeoutSeconds(), null);
		if (result.timedOut) {
			return "빌드 시간이 " + this.config("build-timeout-minutes", "30") + "분을 넘었습니다";
		}
		return result.code == 0 ? null : "빌드 명령이 실패했습니다(종료 코드 " + result.code + ")";
	}

	/**
	 * <pre>
	 * 빌드 산출물을 찾습니다. 경로는 리비전 폴더 기준 상대경로입니다.
	 * 순서: 프로젝트 관리에 직접 넣은 웹 루트 → Spring Boot jar(BOOT-INF) → 풀린 WAR 폴더(WEB-INF가 있는 빌드 결과) → .war 파일(풀어 둠)
	 *       → "빌드 없음"이면 소스 안의 WEB-INF/web.xml 폴더.
	 * 빌드 뒤에 생긴(since 이후) 것만 봅니다. 저장소에 들어 있던 옛 산출물을 잘못 고르지 않게 하려는 것입니다.
	 * 결과: {status: OK | INPUT, kind, webRoot, classesDir, libDir, mainClass, message}
	 * </pre>
	 */
	static Map<String, Object> detectOutputs(File revDir, long since, Map<String, Object> project) throws IOException {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		File src = new File(revDir, "src");
		boolean none = "NONE".equals(text(project, "buildType"));
		String webRoot = text(project, "buildWebRoot");
		if (webRoot != null) {
			String web = "src/" + webRoot;
			if (!new File(revDir, web).isDirectory()) {
				return input(result, "프로젝트 관리의 웹 루트가 없습니다: " + web);
			}
			result.put("kind", KIND_WAR);
			result.put("webRoot", web);
			String classes = text(project, "buildClassesDir");
			result.put("classesDir", none ? "classes" : classes != null ? "src/" + classes : web + "/WEB-INF/classes");
			String lib = text(project, "buildLibDir");
			result.put("libDir", lib != null ? "src/" + lib : web + "/WEB-INF/lib");
			return ok(result, revDir);
		}
		if (!none) {
			File bootJar = newestArchive(src, since, ".jar", "BOOT-INF/");
			if (bootJar != null) {
				File classes = new File(revDir, "classes");
				File lib = new File(revDir, "lib");
				deleteTree(classes.toPath());
				deleteTree(lib.toPath());
				unzip(bootJar, "BOOT-INF/classes/", classes);
				unzip(bootJar, "BOOT-INF/lib/", lib);
				String mainClass;
				JarFile jar = new JarFile(bootJar);
				try {
					Manifest manifest = jar.getManifest();
					mainClass = manifest == null ? null : manifest.getMainAttributes().getValue("Start-Class");
				} finally {
					jar.close();
				}
				if (mainClass == null) {
					return input(result, "Spring Boot jar에 Start-Class가 없습니다: " + bootJar);
				}
				result.put("kind", KIND_BOOT);
				result.put("classesDir", "classes");
				result.put("libDir", "lib");
				result.put("mainClass", mainClass);
				return ok(result, revDir);
			}
			File exploded = explodedWar(src, since);
			if (exploded != null) {
				String web = relative(revDir, exploded);
				result.put("kind", KIND_WAR);
				result.put("webRoot", web);
				result.put("classesDir", web + "/WEB-INF/classes");
				result.put("libDir", web + "/WEB-INF/lib");
				return ok(result, revDir);
			}
			File war = newestArchive(src, since, ".war", null);
			if (war != null) {
				File webapp = new File(revDir, "webapp");
				deleteTree(webapp.toPath());
				unzip(war, "", webapp);
				result.put("kind", KIND_WAR);
				result.put("webRoot", "webapp");
				result.put("classesDir", "webapp/WEB-INF/classes");
				result.put("libDir", "webapp/WEB-INF/lib");
				return ok(result, revDir);
			}
			return input(result, "빌드는 됐지만 산출물(.war, 풀린 WAR 폴더, Spring Boot jar)을 찾지 못했습니다. 프로젝트 관리에 웹 루트·클래스·lib 폴더를 넣고 다시 [Build]를 누르세요.");
		}
		File webInf = findWebXmlDir(src);
		if (webInf == null) {
			return input(result, "소스에서 WEB-INF/web.xml을 찾지 못했습니다. 프로젝트 관리에 웹 루트를 넣고 다시 [Build]를 누르세요.");
		}
		String web = relative(revDir, webInf.getParentFile());
		result.put("kind", KIND_WAR);
		result.put("webRoot", web);
		result.put("classesDir", "classes");
		String lib = text(project, "buildLibDir");
		result.put("libDir", lib != null ? "src/" + lib : web + "/WEB-INF/lib");
		return ok(result, revDir);
	}

	private static Map<String, Object> ok(Map<String, Object> result, File revDir) {
		String classes = (String) result.get("classesDir");
		if (classes != null && !new File(revDir, classes).isDirectory()) {
			return input(result, "클래스 폴더가 없습니다: " + classes);
		}
		result.put("status", STATUS_OK);
		result.put("message", "산출물: " + result.get("kind") + (result.get("webRoot") == null ? "" : ", 웹 루트 " + result.get("webRoot"))
			+ ", 클래스 " + classes + (result.get("libDir") == null ? "" : ", lib " + result.get("libDir")));
		return result;
	}

	private static Map<String, Object> input(Map<String, Object> result, String message) {
		result.put("status", STATUS_INPUT);
		result.put("message", message);
		return result;
	}

	/** 산출물 위치를 리비전 폴더에 남깁니다. compileJava가 리비전 소스를 받으면 이것으로 클래스·lib를 찾습니다. */
	static void writeLayout(File revDir, Map<String, Object> outputs) throws IOException {
		java.util.Properties props = new java.util.Properties();
		for (String key : new String[] { "classesDir", "libDir", "webRoot" }) {
			Object value = outputs.get(key);
			if (value != null) {
				props.setProperty(key, new File(revDir, String.valueOf(value)).getAbsolutePath());
			}
		}
		try (java.io.Writer writer = Files.newBufferedWriter(new File(revDir, LAYOUT_FILE).toPath(), StandardCharsets.UTF_8)) {
			props.store(writer, "dstone revision build layout");
		}
	}

	/* ---------------------------------------------------------------- 정리 */

	/** 현재 리비전, 실행이 쓰는 리비전, 최근 keep-revisions개만 남기고 지웁니다. */
	private void cleanup(String projectId, String current) {
		synchronized (this.pinLock(projectId)) {
			this.deleteOldRevisions(projectId, current);
		}
	}

	private void deleteOldRevisions(String projectId, String current) {
		int keep = Integer.parseInt(this.config("keep-revisions", "3"));
		Set<String> inUse = this.revisionsInUse(projectId);
		List<Map<String, Object>> rows = this.projectStore.revisions(projectId);
		int kept = 0;
		for (Map<String, Object> row : rows) {
			String revision = String.valueOf(row.get("revision"));
			if (STATUS_BUILDING.equals(row.get("status"))) {
				continue;
			}
			if (revision.equals(current) || inUse.contains(revision) || kept < keep) {
				kept++;
				continue;
			}
			try {
				deleteTree(this.revisionDir(projectId, revision).toPath());
				this.projectStore.deleteRevision(projectId, revision);
				this.info("dstone-ai-engine project: 오래된 리비전을 지웠습니다: " + projectId + "/" + revision);
			} catch (IOException e) {
				this.warn("dstone-ai-engine project: 리비전 폴더를 지우지 못했습니다: " + projectId + "/" + revision + ": " + e.getMessage());
			}
		}
	}

	/** 실행이 받아 간 리비전(pins/{실행ID})과 테스트 서버가 쓰는 리비전(runs/{실행ID}/revision.txt)입니다. */
	private Set<String> revisionsInUse(String projectId) {
		Set<String> result = ConcurrentHashMap.newKeySet();
		this.addPinnedRevisions(projectId, result);
		File[] runs = new File(this.projectDir(projectId), "runs").listFiles();
		if (runs == null) {
			return result;
		}
		for (File run : runs) {
			File file = new File(run, RUN_REVISION_FILE);
			if (file.isFile()) {
				try {
					result.add(Files.readString(file.toPath(), StandardCharsets.UTF_8).trim());
				} catch (IOException e) {
					// 읽지 못하면 건너뛴다
				}
			}
		}
		return result;
	}

	/** 아직 이어질 수 있는 실행의 리비전을 모으고, 더는 필요 없는 표시는 지웁니다. */
	private void addPinnedRevisions(String projectId, Set<String> result) {
		File[] pins = new File(this.projectDir(projectId), PIN_DIR).listFiles();
		if (pins == null) {
			return;
		}
		int failedDays = Integer.parseInt(this.config("keep-failed-days", "7"));
		for (File pin : pins) {
			try {
				boolean needed;
				try {
					WorkFlowExecution execution = this.executionStore.find(pin.getName());
					needed = needsRevision(execution.status(), execution.updatedAt(), Instant.now(), failedDays);
				} catch (IllegalArgumentException e) {
					needed = false; // 없는 실행
				} catch (RuntimeException e) {
					this.warn("dstone-ai-engine project: 실행 상태를 읽지 못해 리비전을 남겨 둡니다: " + pin.getName() + ": " + e.getMessage());
					needed = true;
				}
				if (needed) {
					result.add(Files.readString(pin.toPath(), StandardCharsets.UTF_8).trim());
				} else {
					Files.deleteIfExists(pin.toPath());
				}
			} catch (IOException e) {
				this.warn("dstone-ai-engine project: 리비전 표시를 처리하지 못했습니다: " + pin + ": " + e.getMessage());
			}
		}
	}

	/** 대기·진행·승인 대기·일시정지 중인 실행과, 실패한 지 failedDays일이 안 된 실행(다시 돌릴 수 있음)은 리비전이 필요합니다. */
	static boolean needsRevision(WorkFlowExecutionStatus status, Instant updatedAt, Instant now, int failedDays) {
		if (status == WorkFlowExecutionStatus.DONE || status == WorkFlowExecutionStatus.CANCELLED) {
			return false;
		}
		if (status == WorkFlowExecutionStatus.FAILED) {
			return updatedAt != null && updatedAt.plus(Duration.ofDays(failedDays)).isAfter(now);
		}
		return true;
	}

	/** 빌드 한 번의 결과입니다. finishedAt은 끝난 시각(밀리초)입니다. */
	public record BuildOutcome(boolean ok, String revision, String message, long finishedAt) {

		static BuildOutcome success(String revision, String message) {
			return new BuildOutcome(true, revision, message, System.currentTimeMillis());
		}

		static BuildOutcome failure(String revision, String message) {
			return new BuildOutcome(false, revision, message, System.currentTimeMillis());
		}
	}

	/* ---------------------------------------------------------------- 도우미 */

	/** 리비전 이름: 형상관리 리비전 앞 12자(경로에 못 쓰는 글자는 _), 없으면 빌드 시각입니다. */
	static String revisionName(String scmRevision, String stamp) {
		if (isBlank(scmRevision)) {
			return stamp;
		}
		String name = scmRevision.trim().replaceAll("[^A-Za-z0-9._-]", "_");
		return name.length() > 12 ? name.substring(0, 12) : name;
	}

	/** 빌드 인자에 셸이 해석하는 글자가 있으면 거절합니다. */
	static void checkArgs(String args) {
		if (args == null) {
			return;
		}
		for (int i = 0; i < args.length(); i++) {
			if (FORBIDDEN_ARG_CHARS.indexOf(args.charAt(i)) >= 0) {
				throw new IllegalArgumentException("빌드 인자에 쓸 수 없는 글자가 있습니다(" + args.charAt(i) + "). 정해진 빌드 명령 뒤에 붙는 인자만 넣으세요.");
			}
		}
	}

	static List<String> splitArgs(String args) {
		List<String> result = new ArrayList<String>();
		if (!isBlank(args)) {
			result.addAll(Arrays.asList(args.trim().split("\\s+")));
		}
		return result;
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

	/** 이름에 경로 구분이나 ..가 없는지 봅니다. */
	static boolean isSafeName(String name) {
		return name != null && name.matches("[A-Za-z0-9._-]+") && !name.contains("..");
	}

	private Map<String, String> buildEnv(Map<String, Object> project) {
		Map<String, String> env = new LinkedHashMap<String, String>();
		File javaHome = this.javaHome(text(project, "javaVersion"));
		if (javaHome != null) {
			env.put("JAVA_HOME", javaHome.getAbsolutePath());
			env.put("PATH", new File(javaHome, "bin").getAbsolutePath() + File.pathSeparator + System.getenv().getOrDefault("PATH", ""));
		}
		return env;
	}

	@SuppressWarnings("rawtypes")
	private String shellScriptPath(String name) {
		List allowed = this.configProperty.getListProperty(Constants.Tool.Shell.PREFIX + ".allowed-commands");
		if (allowed != null) {
			for (Object entry : allowed) {
				if (entry instanceof Map && name.equals(String.valueOf(((Map) entry).get("name")))) {
					Object path = ((Map) entry).get("path");
					return path == null || isBlank(String.valueOf(path)) ? null : String.valueOf(path);
				}
			}
		}
		return null;
	}

	private long buildTimeoutSeconds() {
		return Long.parseLong(this.config("build-timeout-minutes", "30")) * 60L;
	}

	private String config(String name, String defaultValue) {
		String value = this.configProperty.getProperty(Constants.Tool.TestServer.PREFIX + "." + name);
		return isBlank(value) ? defaultValue : value.trim();
	}

	/** GIT_ASKPASS 로 쓸 스크립트를 만듭니다. 값은 환경변수에서 읽으므로 파일에 비밀번호가 남지 않습니다. */
	private static File writeAskPass(File dir) throws IOException {
		Files.createDirectories(dir.toPath());
		File file;
		if (isWindows()) {
			file = File.createTempFile("askpass", ".bat", dir);
			Files.writeString(file.toPath(), "@echo off\r\necho %~1| findstr /b /c:\"Username\" >nul && (echo %DSTONE_SCM_USER%) || (echo %DSTONE_SCM_PASS%)\r\n", StandardCharsets.US_ASCII);
		} else {
			file = File.createTempFile("askpass", ".sh", dir);
			Files.writeString(file.toPath(), "#!/bin/sh\ncase \"$1\" in\n  Username*) printf '%s\\n' \"$DSTONE_SCM_USER\" ;;\n  *) printf '%s\\n' \"$DSTONE_SCM_PASS\" ;;\nesac\n", StandardCharsets.US_ASCII);
			file.setExecutable(true, true);
		}
		return file;
	}

	/** 명령 실행 결과 */
	private static final class Result {
		int code;
		boolean timedOut;
		String output = "";
	}

	/** 명령을 실행하고 출력을 로그에 덧붙입니다. stdin이 있으면 넣고 닫습니다. */
	private static Result run(List<String> command, File dir, Map<String, String> env, File log, long timeoutSeconds, String stdin) throws IOException, InterruptedException {
		File out = File.createTempFile("dstone-build", ".out");
		Result result = new Result();
		try {
			ProcessBuilder builder = new ProcessBuilder(command).directory(dir).redirectErrorStream(true).redirectOutput(out);
			builder.environment().putAll(env);
			Process process = builder.start();
			OutputStream in = process.getOutputStream();
			if (stdin != null) {
				in.write(stdin.getBytes(StandardCharsets.UTF_8));
			}
			in.close();
			if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				result.timedOut = true;
				result.code = -1;
			} else {
				result.code = process.exitValue();
			}
			result.output = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
			append(log, result.output);
			return result;
		} catch (IOException e) {
			append(log, "명령을 실행하지 못했습니다(" + command.get(0) + "): " + e.getMessage() + "\n");
			result.code = -1;
			return result;
		} finally {
			Files.deleteIfExists(out.toPath());
		}
	}

	private static void append(File log, String text) throws IOException {
		Files.createDirectories(log.getParentFile().toPath());
		Files.writeString(log.toPath(), text, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
	}

	/** 소스를 복사합니다(.git 등과 루트의 target·build는 빼고). */
	static void copySource(final Path from, final Path to) throws IOException {
		Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
				if (SKIP_ANYWHERE.contains(name) || (from.equals(dir.getParent()) && SKIP_AT_ROOT.contains(name))) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				Files.createDirectories(to.resolve(from.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** since 이후에 생긴 것 가운데 가장 새 압축 파일(필요하면 안에 marker로 시작하는 항목이 있는 것)입니다. */
	private static File newestArchive(File root, final long since, final String extension, final String marker) throws IOException {
		final File[] found = new File[1];
		walk(root, 5, new FileFilter() {
			@Override
			public void file(File file) {
				if (!file.getName().toLowerCase(Locale.ROOT).endsWith(extension) || file.lastModified() < since) {
					return;
				}
				if (marker != null && !hasEntry(file, marker)) {
					return;
				}
				if (found[0] == null || file.lastModified() > found[0].lastModified()) {
					found[0] = file;
				}
			}
		});
		return found[0];
	}

	/** 빌드 결과 폴더(target, build 아래) 가운데 since 이후에 생긴 WEB-INF가 있는 폴더입니다. */
	private static File explodedWar(File src, long since) {
		String[] outputs = { "target", "build", "dist" };
		for (String output : outputs) {
			File[] children = new File(src, output).listFiles();
			if (children == null) {
				continue;
			}
			for (File child : children) {
				File webInf = new File(child, "WEB-INF");
				if (child.isDirectory() && webInf.isDirectory() && webInf.lastModified() >= since) {
					return child;
				}
			}
		}
		return null;
	}

	/** 소스 안에서 WEB-INF/web.xml이 있는 WEB-INF 폴더를 찾습니다. */
	private static File findWebXmlDir(File src) throws IOException {
		final File[] found = new File[1];
		walk(src, 5, new FileFilter() {
			@Override
			public void file(File file) {
				if (found[0] == null && "web.xml".equals(file.getName()) && "WEB-INF".equals(file.getParentFile().getName())) {
					found[0] = file.getParentFile();
				}
			}
		});
		return found[0];
	}

	private interface FileFilter {
		void file(File file);
	}

	private static void walk(File root, final int maxDepth, final FileFilter filter) throws IOException {
		if (!root.isDirectory()) {
			return;
		}
		final Path base = root.toPath();
		Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
				if (SKIP_ANYWHERE.contains(name) || base.relativize(dir).getNameCount() > maxDepth) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				filter.file(file.toFile());
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static boolean hasEntry(File archive, String prefix) {
		try {
			ZipFile zip = new ZipFile(archive);
			try {
				Enumeration<? extends ZipEntry> entries = zip.entries();
				while (entries.hasMoreElements()) {
					if (entries.nextElement().getName().startsWith(prefix)) {
						return true;
					}
				}
			} finally {
				zip.close();
			}
		} catch (IOException e) {
			return false;
		}
		return false;
	}

	/** 압축 파일에서 prefix 아래 항목을 to 아래에 풉니다(prefix는 떼고). 폴더 밖으로 나가는 항목은 건너뜁니다. */
	static void unzip(File archive, String prefix, File to) throws IOException {
		Files.createDirectories(to.toPath());
		Path base = to.toPath().toAbsolutePath().normalize();
		ZipFile zip = new ZipFile(archive);
		try {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (!entry.getName().startsWith(prefix) || entry.getName().length() == prefix.length()) {
					continue;
				}
				Path target = base.resolve(entry.getName().substring(prefix.length())).normalize();
				if (!target.startsWith(base)) {
					continue;
				}
				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}
				Files.createDirectories(target.getParent());
				InputStream in = zip.getInputStream(entry);
				try {
					OutputStream os = new FileOutputStream(target.toFile());
					try {
						in.transferTo(os);
					} finally {
						os.close();
					}
				} finally {
					in.close();
				}
			}
		} finally {
			zip.close();
		}
	}

	static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
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

	private static String tail(File file, int lines) {
		if (!file.isFile()) {
			return "";
		}
		try {
			List<String> all = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
			return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
		} catch (IOException e) {
			return "";
		}
	}

	private static String relative(File base, File file) {
		return base.toPath().toAbsolutePath().normalize().relativize(file.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
	}

	private static String absolute(File base, String relative) {
		return relative == null ? null : new File(base, relative).getAbsolutePath();
	}

	private static String text(Map<String, Object> map, String key) {
		Object value = map == null ? null : map.get(key);
		return value == null || String.valueOf(value).trim().isEmpty() ? null : String.valueOf(value).trim();
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

}

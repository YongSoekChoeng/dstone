package net.dstone.ai.runtime.workflow.snapshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import net.dstone.ai.runtime.tool.StepRef;
import net.dstone.ai.runtime.workflow.file.FileAccessRule;

/**
 * <pre>
 * Agent step이 실제로 마주한 장면을 실행 기록 옆에 남깁니다(Step 스냅숏). 평가 사례를 만들 때 이것을 씁니다.
 *
 *   {root}/{workflowId}/{executionId}/
 *     _start/                 ← 실행을 처음 시작할 때 Workflow input(폴더면 내용까지)
 *     {stepId}-{회차}/
 *       input.json            ← 그 step의 Agent가 받은 입력(표현식을 채운 값)
 *       output.json           ← Agent의 답(실패면 null)
 *       tools.json            ← 부른 Tool 목록과 이름별 횟수
 *       manifest.json         ← 원래 경로 → 보관 경로, 시각, 성공 여부, 생략한 것
 *       inputs/{입력 이름}/    ← 입력 값이 폴더면 step을 시작할 때의 내용 전체(상한 안일 때만)
 *       files/before/…        ← 위 폴더 밖의 파일을 처음 건드리는 순간의 내용
 *       files/after/…         ← 이 step이 쓴 파일의 step이 끝났을 때 내용
 *
 * 무엇을 남길지는 엔진이 정하지 않습니다. 입력 값 가운데 실제로 있는 폴더·파일과, 파일 Tool이 건드린 파일만 봅니다.
 * 그래서 어느 Workflow, 어느 대상 앱에나 같게 동작합니다.
 *
 * 남기지 않는 것:
 * - forEach step(반복들이 같은 step id로 동시에 돌아 서로의 Tool 호출을 가를 수 없음). 입력만 남기지도 않습니다.
 * - TOOL·ROUTER·APPROVAL step(Agent가 판단하는 step이 아님).
 *
 * 스냅숏은 부가 기능입니다. 복사가 실패해도 step은 그대로 진행하고 경고 로그만 남깁니다.
 * 보관 기간(retention-days)이 지난 실행 폴더는 실행을 새로 시작할 때 하루 한 번 지웁니다.
 * </pre>
 */
@Component
public class StepSnapshotRecorder {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(StepSnapshotRecorder.class);

	/** 실행 시작 스냅숏 폴더 이름입니다. */
	public static final String START = "_start";

	public static final String INPUT_FILE = "input.json";
	public static final String OUTPUT_FILE = "output.json";
	public static final String TOOLS_FILE = "tools.json";
	public static final String MANIFEST_FILE = "manifest.json";
	public static final String INPUTS_DIR = "inputs";
	public static final String BEFORE_DIR = "files/before";
	public static final String AFTER_DIR = "files/after";

	/** Tool 호출 기록에 남길 인자의 최대 글자 수 */
	private static final int MAX_ARGS_CHARS = 300;

	/** 이름 없는 입력(글자 하나)을 부를 이름 */
	private static final String PLAIN_INPUT_KEY = "input";

	private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	@Autowired
	private Environment environment;

	/** 지금 도는 step의 스냅숏. 키 = 실행 id + "|" + step id */
	private final Map<String, Active> actives = new ConcurrentHashMap<String, Active>();

	/** 마지막으로 보관 기간을 정리한 시각(밀리초) */
	private final AtomicLong lastSweep = new AtomicLong(0L);

	/** 켜져 있는지입니다(dstone.ai.workflow.snapshot.enabled, 기본 true). */
	public boolean enabled() {
		return Boolean.parseBoolean(this.environment.getProperty("dstone.ai.workflow.snapshot.enabled", "true"));
	}

	/** 스냅숏 최상위 폴더입니다(dstone.ai.workflow.snapshot.root, 비면 {APP_HOME}/LOGS/dstone-ai-engine/snapshot). */
	public Path root() {
		String root = this.environment.getProperty("dstone.ai.workflow.snapshot.root");
		if (root == null || root.isBlank()) {
			root = System.getProperty("APP_HOME", ".") + "/LOGS/dstone-ai-engine/snapshot";
		}
		return Path.of(root).toAbsolutePath().normalize();
	}

	/** 실행 하나의 스냅숏 폴더입니다. */
	public Path executionDir(String workflowId, String executionId) {
		return this.root().resolve(safeName(workflowId)).resolve(safeName(executionId));
	}

	/* ---------------------------------------------------------------------------------------------
	 * 실행 시작
	 * ------------------------------------------------------------------------------------------- */

	/**
	 * <pre>
	 * 실행을 처음 시작할 때 Workflow input을 남깁니다(Workflow 평가 사례의 요청 폴더가 됩니다).
	 * 이미 남겼으면(되감아 처음부터 다시 도는 경우) 그대로 둡니다. 그때의 input은 처음 것이 맞기 때문입니다.
	 * </pre>
	 *
	 * @param workflowId  Workflow id
	 * @param executionId 실행 id
	 * @param input       Workflow input
	 */
	public void beginExecution(String workflowId, String executionId, Object input) {
		if (!this.enabled()) {
			return;
		}
		this.sweepIfDue();
		Path dir = this.executionDir(workflowId, executionId).resolve(START);
		if (Files.exists(dir)) {
			return;
		}
		try {
			Files.createDirectories(dir);
			Budget budget = new Budget(this.maxStepBytes());
			Map<String, Object> manifest = new LinkedHashMap<String, Object>();
			manifest.put("kind", "execution");
			manifest.put("workflowId", workflowId);
			manifest.put("executionId", executionId);
			manifest.put("startedAt", Instant.now().toString());
			manifest.put("inputs", this.copyInputs(dir, input, budget));
			JSON.writeValue(dir.resolve(INPUT_FILE).toFile(), input);
			JSON.writeValue(dir.resolve(MANIFEST_FILE).toFile(), manifest);
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("dstone-ai-engine snapshot: [" + executionId + "] 실행 시작 스냅숏을 남기지 못했습니다. 실행은 계속합니다 - " + e.getMessage());
		}
	}

	/* ---------------------------------------------------------------------------------------------
	 * step 시작·끝
	 * ------------------------------------------------------------------------------------------- */

	/**
	 * Agent step을 시작할 때 부릅니다. 입력을 남기고, 입력 값이 가리키는 폴더를 복사해 둡니다.
	 *
	 * @param workflowId  Workflow id
	 * @param executionId 실행 id
	 * @param stepId      step id
	 * @param stepType    step 종류(AGENT, SUPERVISOR)
	 * @param agentId     부를 Agent id
	 * @param input       표현식을 채운 입력
	 */
	public void beginStep(String workflowId, String executionId, String stepId, String stepType, String agentId, Object input) {
		if (!this.enabled()) {
			return;
		}
		try {
			Path executionDir = this.executionDir(workflowId, executionId);
			Files.createDirectories(executionDir);
			Path dir = this.nextRoundDir(executionDir, stepId);
			Files.createDirectories(dir);
			Active active = new Active(dir, new Budget(this.maxStepBytes()));
			active.manifest.put("kind", "step");
			active.manifest.put("workflowId", workflowId);
			active.manifest.put("executionId", executionId);
			active.manifest.put("stepId", stepId);
			active.manifest.put("round", Integer.valueOf(roundOf(dir.getFileName().toString(), stepId)));
			active.manifest.put("stepType", stepType);
			active.manifest.put("agent", agentId);
			active.manifest.put("startedAt", Instant.now().toString());
			List<Map<String, Object>> inputs = this.copyInputs(dir, input, active.budget);
			active.manifest.put("inputs", inputs);
			for (Map<String, Object> item : inputs) {
				if (Boolean.TRUE.equals(item.get("copied")) && "dir".equals(item.get("type"))) {
					active.copiedDirs.put(slash(String.valueOf(item.get("path"))), String.valueOf(item.get("stored")));
				}
			}
			JSON.writeValue(dir.resolve(INPUT_FILE).toFile(), input);
			this.actives.put(key(executionId, stepId), active);
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("dstone-ai-engine snapshot: [" + executionId + "/" + stepId + "] step 스냅숏을 시작하지 못했습니다. step은 계속합니다 - " + e.getMessage());
		}
	}

	/**
	 * Agent step이 끝날 때 부릅니다(성공·실패·예외 모두). 쓴 파일의 끝 내용, 답, Tool 목록, 목록표를 씁니다.
	 *
	 * @param executionId 실행 id
	 * @param stepId      step id
	 * @param success     성공했는지
	 * @param output      Agent의 답(실패면 null)
	 * @param error       실패 사유(성공이면 null)
	 */
	public void endStep(String executionId, String stepId, boolean success, Object output, String error) {
		Active active = this.actives.remove(key(executionId, stepId));
		if (active == null) {
			return;
		}
		try {
			synchronized (active) {
				for (String path : active.written) {
					Map<String, Object> entry = active.files.get(path);
					File file = new File(path);
					if (!file.isFile()) {
						entry.put("after", null);
						entry.put("deleted", Boolean.TRUE);
						continue;
					}
					String stored = AFTER_DIR + "/" + storedPath(path);
					if (active.budget.take(file.length())) {
						copyFile(file.toPath(), active.dir.resolve(stored));
						entry.put("after", stored);
					} else {
						entry.put("after", null);
						entry.put("skipped", "용량 상한(max-step-bytes)을 넘어 끝 내용을 남기지 않았습니다");
					}
				}
				active.manifest.put("endedAt", Instant.now().toString());
				active.manifest.put("success", Boolean.valueOf(success));
				active.manifest.put("error", error);
				active.manifest.put("files", new ArrayList<Map<String, Object>>(active.files.values()));
				active.manifest.put("bytes", Long.valueOf(active.budget.used));
				active.manifest.put("truncated", Boolean.valueOf(active.budget.over));
				Map<String, Object> tools = new LinkedHashMap<String, Object>();
				tools.put("counts", active.toolCounts);
				tools.put("calls", active.toolCalls);
				JSON.writeValue(active.dir.resolve(OUTPUT_FILE).toFile(), output);
				JSON.writeValue(active.dir.resolve(TOOLS_FILE).toFile(), tools);
				JSON.writeValue(active.dir.resolve(MANIFEST_FILE).toFile(), active.manifest);
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("dstone-ai-engine snapshot: [" + executionId + "/" + stepId + "] step 스냅숏을 마치지 못했습니다 - " + e.getMessage());
		}
	}

	/* ---------------------------------------------------------------------------------------------
	 * Tool 호출(ConfigTool의 포장지가 부름)
	 * ------------------------------------------------------------------------------------------- */

	/**
	 * <pre>
	 * Tool을 부르기 직전에 불립니다. 이 호출이 건드릴 파일을 처음 건드리는 것이면 지금 내용을 복사해 둡니다.
	 * step 시작 때 통째로 복사한 폴더 안의 파일은 그 사본이 곧 "처음 내용"이라 다시 복사하지 않습니다.
	 * </pre>
	 *
	 * @param stepRef   부른 step(Workflow 밖이면 null)
	 * @param toolName  Tool 이름
	 * @param toolInput Tool 인자(JSON 글자)
	 */
	public void beforeTool(StepRef stepRef, String toolName, String toolInput) {
		Active active = this.activeOf(stepRef);
		if (active == null) {
			return;
		}
		try {
			synchronized (active) {
				for (FileAccessRule.FileAccess access : FileAccessRule.of(toolName, toolInput)) {
					String path = slash(access.path());
					if (active.files.containsKey(path)) {
						continue;
					}
					Map<String, Object> entry = new LinkedHashMap<String, Object>();
					entry.put("path", path);
					File file = new File(path);
					entry.put("existed", Boolean.valueOf(file.isFile()));
					String inCopied = active.storedInCopiedDir(path);
					if (inCopied != null) {
						entry.put("before", file.isFile() ? inCopied : null);
					} else if (file.isFile()) {
						String stored = BEFORE_DIR + "/" + storedPath(path);
						if (active.budget.take(file.length())) {
							copyFile(file.toPath(), active.dir.resolve(stored));
							entry.put("before", stored);
						} else {
							entry.put("before", null);
							entry.put("skipped", "용량 상한(max-step-bytes)을 넘어 처음 내용을 남기지 않았습니다");
						}
					} else {
						entry.put("before", null);
					}
					entry.put("read", Boolean.FALSE);
					entry.put("write", Boolean.FALSE);
					active.files.put(path, entry);
				}
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("dstone-ai-engine snapshot: [" + stepRef.executionId() + "/" + stepRef.stepId() + "] 파일 처음 내용을 남기지 못했습니다 - " + e.getMessage());
		}
	}

	/**
	 * Tool 호출이 끝난 뒤 불립니다. 호출 기록과, 성공한 호출이 읽고 쓴 파일을 적습니다.
	 *
	 * @param stepRef   부른 step(Workflow 밖이면 null)
	 * @param toolName  Tool 이름
	 * @param toolInput Tool 인자(JSON 글자)
	 * @param result    Tool의 답
	 */
	public void afterTool(StepRef stepRef, String toolName, String toolInput, String result) {
		Active active = this.activeOf(stepRef);
		if (active == null) {
			return;
		}
		synchronized (active) {
			boolean ok = !FileAccessRule.isFailure(result);
			Integer count = active.toolCounts.get(toolName);
			active.toolCounts.put(toolName, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
			Map<String, Object> call = new LinkedHashMap<String, Object>();
			call.put("name", toolName);
			call.put("ok", Boolean.valueOf(ok));
			call.put("at", Instant.now().toString());
			call.put("args", toolInput == null ? null : toolInput.length() > MAX_ARGS_CHARS ? toolInput.substring(0, MAX_ARGS_CHARS) + "…" : toolInput);
			active.toolCalls.add(call);
			if (!ok) {
				return;
			}
			for (FileAccessRule.FileAccess access : FileAccessRule.of(toolName, toolInput)) {
				String path = slash(access.path());
				Map<String, Object> entry = active.files.get(path);
				if (entry == null) {
					continue;
				}
				if (FileAccessRule.WRITE.equals(access.action())) {
					entry.put("write", Boolean.TRUE);
					active.written.add(path);
				} else {
					entry.put("read", Boolean.TRUE);
				}
			}
		}
	}

	/* ---------------------------------------------------------------------------------------------
	 * 읽기(평가 사례 만들기)
	 * ------------------------------------------------------------------------------------------- */

	/**
	 * 실행 하나의 스냅숏 목록(manifest 내용)을 시작 순으로 돌려줍니다. 폴더가 없으면 빈 목록입니다.
	 *
	 * @param workflowId  Workflow id
	 * @param executionId 실행 id
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> list(String workflowId, String executionId) {
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		Path dir = this.executionDir(workflowId, executionId);
		File[] children = dir.toFile().listFiles();
		if (children == null) {
			return result;
		}
		TreeMap<String, Map<String, Object>> sorted = new TreeMap<String, Map<String, Object>>();
		for (File child : children) {
			File manifest = new File(child, MANIFEST_FILE);
			if (!manifest.isFile()) {
				continue;
			}
			try {
				Map<String, Object> map = JSON.readValue(manifest, Map.class);
				map.put("name", child.getName());
				String at = map.get("startedAt") == null ? "" : String.valueOf(map.get("startedAt"));
				sorted.put(at + "|" + child.getName(), map);
			} catch (IOException e) {
				LOGGER.warn("dstone-ai-engine snapshot: 목록표를 읽지 못했습니다(" + manifest + ") - " + e.getMessage());
			}
		}
		result.addAll(sorted.values());
		return result;
	}

	/**
	 * 스냅숏 폴더 하나를 돌려줍니다. 이름이 이상하거나 없으면 null입니다.
	 *
	 * @param workflowId  Workflow id
	 * @param executionId 실행 id
	 * @param name        스냅숏 이름(예: step30-1, _start)
	 */
	public Path find(String workflowId, String executionId, String name) {
		if (name == null || !name.equals(safeName(name))) {
			return null;
		}
		Path dir = this.executionDir(workflowId, executionId).resolve(name);
		return Files.isRegularFile(dir.resolve(MANIFEST_FILE)) ? dir : null;
	}

	/**
	 * <pre>
	 * 원래 경로를 스냅숏 안의 상대 경로로 바꿉니다. 드라이브 문자의 ':'와 앞의 '/'를 뺍니다.
	 *   D:/App/a.md → D/App/a.md      /app/a.md → app/a.md
	 * </pre>
	 */
	public static String storedPath(String path) {
		String result = slash(path).replace(":", "");
		while (result.startsWith("/")) {
			result = result.substring(1);
		}
		return result;
	}

	/* ---------------------------------------------------------------------------------------------
	 * 안쪽
	 * ------------------------------------------------------------------------------------------- */

	private Active activeOf(StepRef stepRef) {
		if (stepRef == null || this.actives.isEmpty()) {
			return null;
		}
		return this.actives.get(key(stepRef.executionId(), stepRef.stepId()));
	}

	/**
	 * <pre>
	 * 입력 값 가운데 실제로 있는 폴더·파일을 복사합니다. 맵이면 맨 위 칸마다, 글자 하나면 그 글자를 봅니다.
	 * 폴더는 상한(max-dir-bytes, max-dir-files) 안일 때만 통째로 복사합니다. 넘으면 경로만 적습니다
	 * (대상 앱 소스 폴더처럼 큰 폴더는 파일 Tool이 건드린 파일만 files/before에 남습니다).
	 * </pre>
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> copyInputs(Path dir, Object input, Budget budget) {
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		Map<String, Object> values = new LinkedHashMap<String, Object>();
		if (input instanceof Map<?, ?> map) {
			values.putAll((Map<String, Object>) map);
		} else if (input instanceof String) {
			values.put(PLAIN_INPUT_KEY, input);
		}
		Set<String> seen = new HashSet<String>();
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			if (!(entry.getValue() instanceof String text) || !looksLikePath(text)) {
				continue;
			}
			File file = new File(text.strip());
			if (!file.exists()) {
				continue;
			}
			String path = slash(file.getAbsolutePath());
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("key", entry.getKey());
			item.put("path", path);
			item.put("type", file.isDirectory() ? "dir" : "file");
			String stored = INPUTS_DIR + "/" + safeName(entry.getKey());
			if (!seen.add(path)) {
				item.put("copied", Boolean.FALSE);
				item.put("reason", "같은 경로를 앞 입력에서 이미 복사했습니다");
				result.add(item);
				continue;
			}
			try {
				if (file.isFile()) {
					if (budget.take(file.length())) {
						copyFile(file.toPath(), dir.resolve(stored).resolve(file.getName()));
						item.put("copied", Boolean.TRUE);
						item.put("stored", stored + "/" + file.getName());
					} else {
						item.put("copied", Boolean.FALSE);
						item.put("reason", "용량 상한을 넘었습니다");
					}
				} else {
					long[] size = this.measure(file.toPath());
					if (size == null) {
						item.put("copied", Boolean.FALSE);
						item.put("reason", "폴더가 상한(max-dir-bytes " + this.maxDirBytes() + ", max-dir-files " + this.maxDirFiles() + ")보다 커서 건드린 파일만 남깁니다");
					} else if (!budget.take(size[0])) {
						item.put("copied", Boolean.FALSE);
						item.put("reason", "step 용량 상한(max-step-bytes)을 넘었습니다");
					} else {
						this.copyTree(file.toPath(), dir.resolve(stored));
						item.put("copied", Boolean.TRUE);
						item.put("stored", stored);
						item.put("bytes", Long.valueOf(size[0]));
						item.put("fileCount", Long.valueOf(size[1]));
					}
				}
			} catch (IOException e) {
				item.put("copied", Boolean.FALSE);
				item.put("reason", "복사하지 못했습니다 - " + e.getMessage());
			}
			result.add(item);
		}
		return result;
	}

	/** 절대경로처럼 보이는 한 줄 글자인지 봅니다(D:/…, /…, \\…). */
	private static boolean looksLikePath(String text) {
		String value = text.strip();
		if (value.isEmpty() || value.length() > 1000 || value.indexOf('\n') >= 0) {
			return false;
		}
		return value.startsWith("/") || value.startsWith("\\") || value.matches("^[A-Za-z]:[\\\\/].*");
	}

	/** 폴더의 [바이트, 파일 수]를 잽니다. 상한을 넘으면 거기서 멈추고 null입니다. 빼는 폴더(exclude-dirs)는 세지 않습니다. */
	private long[] measure(Path dir) throws IOException {
		final long maxBytes = this.maxDirBytes();
		final long maxFiles = this.maxDirFiles();
		final Set<String> excluded = this.excludedDirs();
		final long[] size = new long[] { 0L, 0L };
		final boolean[] over = new boolean[] { false };
		Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
				return !path.equals(dir) && excluded.contains(path.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
				size[0] += attrs.size();
				size[1]++;
				if (size[0] > maxBytes || size[1] > maxFiles) {
					over[0] = true;
					return FileVisitResult.TERMINATE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path path, IOException e) {
				return FileVisitResult.CONTINUE;
			}
		});
		return over[0] ? null : size;
	}

	private void copyTree(final Path from, final Path to) throws IOException {
		final Set<String> excluded = this.excludedDirs();
		Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if (!dir.equals(from) && excluded.contains(dir.getFileName().toString())) {
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

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException e) {
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void copyFile(Path from, Path to) throws IOException {
		Files.createDirectories(to.getParent());
		Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
	}

	/** {stepId}-{다음 회차} 폴더를 돌려줍니다. */
	private Path nextRoundDir(Path executionDir, String stepId) {
		String prefix = safeName(stepId) + "-";
		int max = 0;
		File[] children = executionDir.toFile().listFiles();
		if (children != null) {
			for (File child : children) {
				int round = roundOf(child.getName(), stepId);
				if (round > max) {
					max = round;
				}
			}
		}
		return executionDir.resolve(prefix + (max + 1));
	}

	/** 폴더 이름에서 회차를 읽습니다. 그 step의 폴더가 아니면 0입니다. */
	private static int roundOf(String name, String stepId) {
		String prefix = safeName(stepId) + "-";
		if (!name.startsWith(prefix)) {
			return 0;
		}
		try {
			return Integer.parseInt(name.substring(prefix.length()));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * <pre>
	 * 보관 기간(retention-days, 기본 30일, 0 이하면 지우지 않음)이 지난 실행 폴더를 지웁니다.
	 * 실행을 새로 시작할 때 하루에 한 번만 보고, 지우는 일은 따로 도는 스레드가 합니다(실행을 늦추지 않게).
	 * 폴더의 마지막 수정 시각으로 봅니다.
	 * </pre>
	 */
	private void sweepIfDue() {
		final int days = Integer.parseInt(this.environment.getProperty("dstone.ai.workflow.snapshot.retention-days", "30"));
		long now = System.currentTimeMillis();
		long last = this.lastSweep.get();
		if (days <= 0 || now - last < 24L * 60 * 60 * 1000 || !this.lastSweep.compareAndSet(last, now)) {
			return;
		}
		final Path root = this.root();
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				StepSnapshotRecorder.this.sweep(root, Instant.now().minus(days, ChronoUnit.DAYS));
			}
		}, "dstone-ai-snapshot-sweep");
		thread.setDaemon(true);
		thread.start();
	}

	/** root/{workflowId}/{executionId} 가운데 before보다 오래된 것을 지웁니다. */
	void sweep(Path root, Instant before) {
		File[] workflows = root.toFile().listFiles();
		if (workflows == null) {
			return;
		}
		for (File workflow : workflows) {
			File[] executions = workflow.listFiles();
			if (executions == null) {
				continue;
			}
			for (File execution : executions) {
				try {
					FileTime modified = Files.getLastModifiedTime(execution.toPath());
					if (execution.isDirectory() && modified.toInstant().isBefore(before)) {
						deleteTree(execution.toPath());
					}
				} catch (IOException | RuntimeException e) {
					LOGGER.warn("dstone-ai-engine snapshot: 보관 기간이 지난 폴더를 지우지 못했습니다(" + execution + ") - " + e.getMessage());
				}
			}
		}
	}

	private static void deleteTree(Path dir) throws IOException {
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
	}

	private long maxStepBytes() {
		return Long.parseLong(this.environment.getProperty("dstone.ai.workflow.snapshot.max-step-bytes", "52428800"));
	}

	private long maxDirBytes() {
		return Long.parseLong(this.environment.getProperty("dstone.ai.workflow.snapshot.max-dir-bytes", "20971520"));
	}

	private long maxDirFiles() {
		return Long.parseLong(this.environment.getProperty("dstone.ai.workflow.snapshot.max-dir-files", "3000"));
	}

	private Set<String> excludedDirs() {
		Set<String> result = new LinkedHashSet<String>();
		String value = this.environment.getProperty("dstone.ai.tool.file.exclude-dirs", ".git,.svn,target,node_modules");
		for (String name : value.split(",")) {
			if (!name.isBlank()) {
				result.add(name.strip());
			}
		}
		return result;
	}

	private static String key(String executionId, String stepId) {
		return executionId + "|" + stepId;
	}

	private static String slash(String path) {
		return path == null ? null : path.strip().replace('\\', '/');
	}

	/** 폴더 이름으로 쓸 수 있게 글자·숫자·.-_[] 밖의 글자를 _로 바꿉니다. */
	static String safeName(String name) {
		return name == null ? "_" : name.replaceAll("[^A-Za-z0-9._\\-\\[\\]]", "_").replace("..", "_");
	}

	/** step 하나의 용량 상한입니다. */
	private static final class Budget {
		private final long max;
		private long used;
		private boolean over;

		private Budget(long max) {
			this.max = max;
		}

		/** bytes만큼 쓸 수 있으면 더하고 true, 넘으면 false입니다. */
		private boolean take(long bytes) {
			if (this.used + bytes > this.max) {
				this.over = true;
				return false;
			}
			this.used += bytes;
			return true;
		}
	}

	/** 지금 도는 step 하나의 스냅숏입니다. */
	private static final class Active {
		private final Path dir;
		private final Budget budget;
		private final Map<String, Object> manifest = new LinkedHashMap<String, Object>();
		/** 원래 경로 → {path, existed, before, after, read, write} */
		private final Map<String, Map<String, Object>> files = new LinkedHashMap<String, Map<String, Object>>();
		/** step 시작 때 통째로 복사한 폴더: 원래 경로 → 스냅숏 안 경로 */
		private final Map<String, String> copiedDirs = new LinkedHashMap<String, String>();
		private final Set<String> written = new LinkedHashSet<String>();
		private final Map<String, Integer> toolCounts = new TreeMap<String, Integer>();
		private final List<Map<String, Object>> toolCalls = new ArrayList<Map<String, Object>>();

		private Active(Path dir, Budget budget) {
			this.dir = dir;
			this.budget = budget;
		}

		/** path가 통째로 복사한 폴더 안이면 그 사본 안의 경로, 아니면 null입니다. */
		private String storedInCopiedDir(String path) {
			for (Map.Entry<String, String> entry : this.copiedDirs.entrySet()) {
				String base = entry.getKey().endsWith("/") ? entry.getKey() : entry.getKey() + "/";
				// Windows는 경로의 대소문자를 가리지 않는다(D:/App 와 d:/app 은 같은 폴더)
				boolean inside = File.separatorChar == '\\' ? path.toLowerCase().startsWith(base.toLowerCase()) : path.startsWith(base);
				if (inside) {
					return entry.getValue() + "/" + path.substring(base.length());
				}
			}
			return null;
		}
	}

}

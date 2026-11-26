package net.dstone.ai.runtime.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * conf/eval 아래의 평가 사례(*.eval.yml)를 읽습니다. 형식은 EvalCase의 설명을 보십시오.
 *
 * 사례는 평가를 돌릴 때마다 파일에서 새로 읽습니다(엔진을 다시 띄우지 않고 사례를 더하거나 고칠 수 있게).
 * 틀린 사례 파일은 목록에서 빼지 않고 problem에 사유를 담아 돌려줍니다. 화면이 무엇이 틀렸는지 보여 줄 수 있게 하려는 것입니다.
 *
 * 사례 폴더 위치는 dstone.ai.eval.location(기본: {APP_CONF_DIR}/eval)입니다.
 * 평가 사례는 테스트 코드(src/test)가 아니라 정의 파일 옆에 둡니다. src/test는 언제든 지울 수 있는 자원이고,
 * 사례는 실패한 실행의 그 시점 자료라 잃으면 다시 만들 수 없기 때문입니다(2026-11-22 사용자 결정).
 * </pre>
 */
@Component
public class EvalCaseLoader extends BaseObject {

	/** 사례 파일 이름 꼬리입니다. 자료 폴더 안의 다른 yml(대상 앱의 설정 등)과 섞이지 않게 따로 둡니다. */
	public static final String SUFFIX = ".eval.yml";

	private static final Set<String> CASE_KEYS = Set.of("id", "description", "agent", "workflow", "approvals", "snapshotAfter", "server", "fixture", "copy", "params", "request", "input", "checks");

	private static final Set<String> CHECK_KINDS = Set.of("answerContains", "answerNotEmpty", "answerEquals", "fileExists", "fileChanged", "fileContains", "textIncreased", "toolUsed",
		"fileMatches", "fileNotMatches", "answerMatches", "answerNotMatches", EvalCase.Check.HTTP, EvalCase.Check.DB_QUERY);

	/**
	 * server에 쓸 수 있는 이름(TestServerTool.startTestServer의 인자)과 꼭 있어야 하는 것.
	 * sourceRoot, ddlFile은 예전(복제본 시험 서버) 사례를 읽으려고 받기만 하고 쓰지 않습니다(소스는 프로젝트의 리비전, DDL은 사람이 반영).
	 */
	private static final Set<String> SERVER_KEYS = Set.of("projectId", "revision", "overlayDir", "deleteList", "sourceRoot", "ddlFile");
	private static final Set<String> SERVER_REQUIRED = Set.of("projectId", "overlayDir");

	@Autowired
	private Environment environment;

	/**
	 * 읽은 사례 하나입니다. 틀린 파일이면 evalCase가 null이고 problem에 사유가 있습니다.
	 *
	 * @param path     사례 파일 경로(사례 폴더 기준)
	 * @param evalCase 읽은 사례
	 * @param problem  읽지 못한 사유
	 */
	public record Loaded(String path, EvalCase evalCase, String problem) {
	}

	/** 사례 폴더의 절대경로입니다. */
	public Path root() {
		String location = this.environment.getProperty("dstone.ai.eval.location");
		if (location == null || location.isBlank()) {
			location = System.getProperty("APP_CONF_DIR", "conf") + "/eval";
		}
		return Path.of(location).toAbsolutePath().normalize();
	}

	/** 사례를 전부 읽어 경로 순으로 돌려줍니다. 사례 폴더가 없으면 빈 목록입니다. */
	public List<Loaded> loadAll() {
		final Path root = this.root();
		final List<Path> files = new ArrayList<Path>();
		if (Files.isDirectory(root)) {
			try {
				Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
						if (file.getFileName().toString().endsWith(SUFFIX)) {
							files.add(file);
						}
						return FileVisitResult.CONTINUE;
					}
				});
			} catch (IOException e) {
				throw new IllegalStateException("평가 사례 폴더를 읽지 못했습니다: " + root + " - " + e.getMessage(), e);
			}
		}
		files.sort(Comparator.naturalOrder());
		List<Loaded> result = new ArrayList<Loaded>();
		for (Path file : files) {
			String rel = root.relativize(file).toString().replace('\\', '/');
			try {
				result.add(new Loaded(rel, this.parse(rel, Files.readString(file, StandardCharsets.UTF_8)), null));
			} catch (IOException | RuntimeException e) {
				result.add(new Loaded(rel, null, e.getMessage()));
			}
		}
		return result;
	}

	/**
	 * id로 사례 하나를 찾습니다.
	 *
	 * @param id 사례 id
	 * @throws IllegalArgumentException 없거나 틀린 사례일 때
	 */
	public EvalCase find(String id) {
		for (Loaded loaded : this.loadAll()) {
			if (loaded.evalCase() != null && loaded.evalCase().id().equals(id)) {
				return loaded.evalCase();
			}
			if (loaded.evalCase() == null && loaded.path().endsWith("/" + id + SUFFIX)) {
				throw new IllegalArgumentException("평가 사례 '" + id + "' 파일이 틀렸습니다(" + loaded.path() + "): " + loaded.problem());
			}
		}
		throw new IllegalArgumentException("평가 사례 '" + id + "'가 없습니다(" + this.root() + " 아래 *" + SUFFIX + ").");
	}

	/**
	 * 사례 파일 글자를 읽어 EvalCase로 만듭니다. 틀리면 IllegalStateException입니다.
	 *
	 * @param path 사례 파일 경로(오류 문구용)
	 * @param text 파일 내용
	 */
	@SuppressWarnings("unchecked")
	EvalCase parse(String path, String text) {
		Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
		if (!(loaded instanceof Map)) {
			throw new IllegalStateException("맨 위가 키: 값 모양이어야 합니다.");
		}
		Map<String, Object> root = (Map<String, Object>) loaded;
		for (String key : root.keySet()) {
			if (!CASE_KEYS.contains(key)) {
				throw new IllegalStateException("'" + key + "'는 쓸 수 없는 키입니다(쓸 수 있는 키 = " + CASE_KEYS + ").");
			}
		}
		String id = this.text(root, "id", true);
		String fileName = path.substring(path.lastIndexOf('/') + 1);
		if (!fileName.equals(id + SUFFIX)) {
			throw new IllegalStateException("id '" + id + "'와 파일 이름 '" + fileName + "'이 다릅니다. 파일 이름을 id" + SUFFIX + "로 맞추십시오.");
		}
		if (!id.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
			throw new IllegalStateException("id '" + id + "'는 영문 소문자·숫자·하이픈만 씁니다(작업 폴더 이름으로도 쓰입니다).");
		}
		Map<String, String> params = new LinkedHashMap<String, String>();
		Object rawParams = root.get("params");
		if (rawParams != null) {
			if (!(rawParams instanceof Map)) {
				throw new IllegalStateException("params는 이름: 기본값 모양이어야 합니다.");
			}
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) rawParams).entrySet()) {
				params.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
			}
		}
		String requestFile = null;
		String requestAppend = null;
		Object rawRequest = root.get("request");
		if (rawRequest != null) {
			if (!(rawRequest instanceof Map)) {
				throw new IllegalStateException("request는 {file, append} 모양이어야 합니다.");
			}
			Map<String, Object> request = (Map<String, Object>) rawRequest;
			requestFile = request.get("file") == null ? null : String.valueOf(request.get("file"));
			requestAppend = request.get("append") == null ? null : String.valueOf(request.get("append"));
		}
		Object rawInput = root.get("input");
		if (!(rawInput instanceof Map) && !(rawInput instanceof String)) {
			throw new IllegalStateException("input이 없습니다. Agent input 모양대로 이름: 값(맵)이나 글자로 적으십시오.");
		}
		Object input = rawInput instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>) rawInput) : rawInput;
		List<EvalCase.Check> checks = new ArrayList<EvalCase.Check>();
		Object rawChecks = root.get("checks");
		if (!(rawChecks instanceof List) || ((List<Object>) rawChecks).isEmpty()) {
			throw new IllegalStateException("checks가 없습니다. 채점 기준을 하나 이상 적으십시오.");
		}
		for (Object rawCheck : (List<Object>) rawChecks) {
			checks.add(this.check(rawCheck));
		}
		List<String> copy = null;
		Object rawCopy = root.get("copy");
		if (rawCopy != null) {
			if (!(rawCopy instanceof List)) {
				throw new IllegalStateException("copy는 work/ 기준 경로 목록이어야 합니다.");
			}
			copy = new ArrayList<String>();
			for (Object item : (List<Object>) rawCopy) {
				copy.add(String.valueOf(item));
			}
		}
		String agent = this.text(root, "agent", false);
		String workflow = this.text(root, "workflow", false);
		if ((agent == null) == (workflow == null)) {
			throw new IllegalStateException("agent와 workflow 가운데 하나만 적습니다(Agent 하나를 잴 때 agent, 흐름 전체를 잴 때 workflow).");
		}
		Map<String, String> approvals = this.textMap(root, "approvals");
		String snapshotAfter = this.text(root, "snapshotAfter", false);
		if (workflow == null && (!approvals.isEmpty() || snapshotAfter != null)) {
			throw new IllegalStateException("approvals와 snapshotAfter는 workflow 사례에만 씁니다.");
		}
		Map<String, String> server = this.textMap(root, "server");
		for (String key : server.keySet()) {
			if (!SERVER_KEYS.contains(key)) {
				throw new IllegalStateException("server의 '" + key + "'는 쓸 수 없는 이름입니다(쓸 수 있는 이름 = " + SERVER_KEYS + ").");
			}
		}
		EvalCase evalCase = new EvalCase(id, this.text(root, "description", false), agent, workflow, approvals, snapshotAfter, server,
			this.text(root, "fixture", false), copy, params, requestFile, requestAppend, input, checks, path);
		if (evalCase.needsServer()) {
			for (String key : SERVER_REQUIRED) {
				if (server.get(key) == null || server.get(key).isBlank()) {
					throw new IllegalStateException("http/dbQuery 채점이 있으면 server에 " + SERVER_REQUIRED + "를 적어야 합니다(없는 것: " + key + ").");
				}
			}
		}
		return evalCase;
	}

	@SuppressWarnings("unchecked")
	private EvalCase.Check check(Object raw) {
		if (!(raw instanceof Map)) {
			throw new IllegalStateException("checks의 항목은 {name, 종류: 값} 모양이어야 합니다: " + raw);
		}
		Map<String, Object> map = (Map<String, Object>) raw;
		String name = map.get("name") == null ? null : String.valueOf(map.get("name"));
		String kind = null;
		Object value = null;
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			if ("name".equals(entry.getKey())) {
				continue;
			}
			if (!CHECK_KINDS.contains(entry.getKey())) {
				throw new IllegalStateException("채점 종류 '" + entry.getKey() + "'는 없습니다(쓸 수 있는 종류 = " + CHECK_KINDS + ").");
			}
			if (kind != null) {
				throw new IllegalStateException("채점 항목 하나에는 종류를 하나만 적습니다: " + map.keySet());
			}
			kind = entry.getKey();
			value = entry.getValue();
		}
		if (kind == null) {
			throw new IllegalStateException("채점 항목에 종류가 없습니다: " + map);
		}
		if (EvalCase.Check.HTTP.equals(kind)) {
			if (!(value instanceof Map) || ((Map<String, Object>) value).get("path") == null) {
				throw new IllegalStateException("http 채점은 {method, path, body, status, json, contains, save} 모양이고 path가 있어야 합니다: " + map);
			}
		}
		if (EvalCase.Check.DB_QUERY.equals(kind)) {
			Map<String, Object> query = value instanceof Map ? (Map<String, Object>) value : null;
			if (query == null || query.get("sql") == null || (query.get("equals") == null && query.get("rows") == null)) {
				throw new IllegalStateException("dbQuery 채점은 {sql, equals} 또는 {sql, rows} 모양이어야 합니다: " + map);
			}
		}
		if (kind.endsWith("Matches")) {
			// 정규식이 틀리면 돌려 볼 때가 아니라 읽을 때 알려 준다
			Map<String, Object> spec = value instanceof Map ? (Map<String, Object>) value : null;
			String target = kind.startsWith("file") ? "file" : "field";
			if (spec == null || spec.get("pattern") == null || (kind.startsWith("file") && spec.get(target) == null)) {
				throw new IllegalStateException(kind + " 채점은 {" + target + ", pattern} 모양이어야 합니다: " + map);
			}
			try {
				Pattern.compile(String.valueOf(spec.get("pattern")), Pattern.DOTALL);
			} catch (PatternSyntaxException e) {
				throw new IllegalStateException(kind + " 채점의 pattern이 정규식으로 맞지 않습니다: " + e.getDescription() + " - " + spec.get("pattern"));
			}
		}
		return new EvalCase.Check(name == null ? kind : name, kind, value);
	}

	/** 이름: 값 모양의 키를 글자 맵으로 읽습니다(적은 순서 유지). 없으면 빈 맵입니다. */
	@SuppressWarnings("unchecked")
	private Map<String, String> textMap(Map<String, Object> root, String key) {
		Map<String, String> result = new LinkedHashMap<String, String>();
		Object raw = root.get(key);
		if (raw == null) {
			return result;
		}
		if (!(raw instanceof Map)) {
			throw new IllegalStateException(key + "는 이름: 값 모양이어야 합니다.");
		}
		for (Map.Entry<String, Object> entry : ((Map<String, Object>) raw).entrySet()) {
			result.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
		}
		return result;
	}

	private String text(Map<String, Object> root, String key, boolean required) {
		Object value = root.get(key);
		if (value == null || String.valueOf(value).isBlank()) {
			if (required) {
				throw new IllegalStateException(key + "가 없습니다.");
			}
			return null;
		}
		return String.valueOf(value).strip();
	}

}

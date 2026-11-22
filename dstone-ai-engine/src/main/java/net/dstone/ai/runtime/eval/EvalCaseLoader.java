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

	private static final Set<String> CASE_KEYS = Set.of("id", "description", "agent", "fixture", "copy", "params", "request", "input", "checks");

	private static final Set<String> CHECK_KINDS = Set.of("answerContains", "answerNotEmpty", "answerEquals", "fileExists", "fileChanged", "fileContains", "textIncreased", "toolUsed");

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
		if (!(rawInput instanceof Map)) {
			throw new IllegalStateException("input이 없습니다. Agent에 넣을 값을 이름: 값 모양으로 적으십시오.");
		}
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
		return new EvalCase(id, this.text(root, "description", false), this.text(root, "agent", true), this.text(root, "fixture", false), copy, params,
			requestFile, requestAppend, new LinkedHashMap<String, Object>((Map<String, Object>) rawInput), checks, path);
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
		return new EvalCase.Check(name == null ? kind : name, kind, value);
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

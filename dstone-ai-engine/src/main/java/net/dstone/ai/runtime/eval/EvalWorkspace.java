package net.dstone.ai.runtime.eval;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <pre>
 * 평가 실행 한 건의 작업 공간입니다. 자료 폴더를 복사하고, 값의 자리를 채우고, 끝난 뒤 채점합니다.
 *
 *   {요청 폴더}/00.request.md       ← request(선택)
 *   {요청 폴더}/work/               ← 작업 폴더. 자료 폴더의 work/ 아래를 그대로 복사(글자 파일은 자리를 채움)
 *
 * 자료 폴더의 work/ 밖의 파일(요청서, 지시 글 등)은 복사하지 않고 ${fixture:경로}와 request.file로 읽습니다.
 * Workflow 사례는 작업 폴더가 요청 폴더 자체입니다(Workflow가 그 아래에 결과를 씁니다).
 * 자리 채우기는 정해진 이름만 바꿉니다. 다른 ${…}(JSP EL 등)는 그대로 둡니다.
 * </pre>
 */
public class EvalWorkspace {

	/** 바꾸는 자리: ${workDir} ${requestDir} ${param.이름} ${request} ${file:경로} ${fixture:경로} ${state.이름} ${saved.이름} */
	private static final Pattern TOKEN = Pattern.compile("\\$\\{(workDir|requestDir|request|param\\.[A-Za-z0-9_-]+|state\\.[A-Za-z0-9_.-]+|saved\\.[A-Za-z0-9_-]+|file:[^}]+|fixture:[^}]+)\\}");

	/** http 채점 한 번의 응답 대기 상한 */
	private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(60);

	/** dbQuery 채점에서 읽는 최대 행 수 */
	private static final int MAX_DB_ROWS = 1000;

	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * <pre>
	 * http·dbQuery 채점이 쓰는 격리 시험 서버입니다. EvalRunner가 띄우고 넘깁니다.
	 * </pre>
	 */
	public interface Probe {

		/** 서버 접속주소(끝의 / 없이) */
		String baseUrl();

		/**
		 * 복제본 DB에서 SELECT 하나를 실행합니다.
		 *
		 * @param sql     SELECT 문장
		 * @param maxRows 최대 행 수
		 */
		List<Map<String, Object>> rows(String sql, int maxRows);
	}

	private final EvalCase evalCase;
	private final Path fixtureDir;
	private final Path requestDir;
	private final Path workDir;
	private final Map<String, String> params;
	private final Map<String, String> original = new LinkedHashMap<String, String>();
	private String request = "";
	/** Workflow가 끝난(멈춘) 뒤의 state. ${state.이름}이 읽습니다 */
	private Map<String, Object> state = new LinkedHashMap<String, Object>();
	/** http 채점이 save로 남긴 값. ${saved.이름}이 읽습니다 */
	private final Map<String, String> saved = new LinkedHashMap<String, String>();

	/**
	 * @param evalCase   사례
	 * @param fixtureDir 자료 폴더(없으면 null)
	 * @param requestDir 이 실행의 요청 폴더(새로 만듭니다)
	 * @param params     기본값에 실행 값을 덮은 params
	 */
	public EvalWorkspace(EvalCase evalCase, Path fixtureDir, Path requestDir, Map<String, String> params) {
		this.evalCase = evalCase;
		this.fixtureDir = fixtureDir;
		this.requestDir = requestDir;
		this.workDir = evalCase.isWorkflow() ? requestDir : requestDir.resolve("work");
		this.params = params;
	}

	/** 요청 폴더입니다. */
	public Path requestDir() {
		return this.requestDir;
	}

	/**
	 * Workflow가 끝난(멈춘) 뒤의 state를 넣습니다. ${state.이름}이 이 값을 읽습니다.
	 *
	 * @param state Workflow 컨텍스트의 state
	 */
	public void setState(Map<String, Object> state) {
		this.state = state == null ? new LinkedHashMap<String, Object>() : state;
	}

	/**
	 * <pre>
	 * 요청 폴더를 copyTo로 통째로 복사하고, 그 사본을 가리키는 작업 공간을 돌려줍니다(snapshotAfter).
	 * 사본의 ${requestDir}·${workDir}는 사본 쪽이고 params, 요청서, 처음 복사한 내용은 같습니다.
	 * </pre>
	 *
	 * @param copyTo 사본을 둘 새 폴더
	 */
	public EvalWorkspace snapshot(Path copyTo) throws IOException {
		copyTree(this.requestDir, copyTo);
		EvalWorkspace copy = new EvalWorkspace(this.evalCase, this.fixtureDir, copyTo, this.params);
		copy.request = this.request;
		copy.original.putAll(this.original);
		return copy;
	}

	/** 폴더를 통째로 복사합니다. */
	private static void copyTree(final Path from, final Path to) throws IOException {
		Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
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

	/**
	 * server 값의 자리를 채워 돌려줍니다(TestServerTool.startSandboxServer에 넘길 인자).
	 */
	public Map<String, String> serverArgs() throws IOException {
		Map<String, String> result = new LinkedHashMap<String, String>();
		for (Map.Entry<String, String> entry : this.evalCase.server().entrySet()) {
			result.put(entry.getKey(), this.fill(entry.getValue(), false));
		}
		return result;
	}

	/** 작업 폴더입니다. */
	public Path workDir() {
		return this.workDir;
	}

	/** 폴더를 만들고 자료를 복사한 뒤 요청서를 씁니다. */
	public void prepare() throws IOException {
		Files.createDirectories(this.workDir);
		if (this.fixtureDir != null && Files.isDirectory(this.fixtureDir.resolve("work"))) {
			this.copyWork(this.fixtureDir.resolve("work"));
		}
		if (this.evalCase.requestFile() != null || this.evalCase.requestAppend() != null) {
			StringBuilder text = new StringBuilder();
			if (this.evalCase.requestFile() != null) {
				text.append(this.fill(this.readFixture(this.evalCase.requestFile()), false).stripTrailing());
			}
			if (this.evalCase.requestAppend() != null) {
				text.append(text.length() == 0 ? "" : "\n\n").append(this.fill(this.evalCase.requestAppend(), false).strip());
			}
			this.request = text.append('\n').toString();
			Files.writeString(this.requestDir.resolve("00.request.md"), this.request, StandardCharsets.UTF_8);
		}
	}

	/** 사례의 input에서 자리를 채운 값을 돌려줍니다(글자·목록·맵 안의 글자 모두). */
	public Object input() throws IOException {
		return this.fillValue(this.evalCase.input());
	}

	@SuppressWarnings("unchecked")
	private Object fillValue(Object value) throws IOException {
		if (value instanceof String text) {
			return this.fill(text, true);
		}
		if (value instanceof List<?> list) {
			List<Object> result = new ArrayList<Object>();
			for (Object item : list) {
				result.add(this.fillValue(item));
			}
			return result;
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> result = new LinkedHashMap<String, Object>();
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
				result.put(entry.getKey(), this.fillValue(entry.getValue()));
			}
			return result;
		}
		return value;
	}

	/**
	 * 글자의 자리를 채웁니다.
	 *
	 * @param text      채울 글자
	 * @param withFiles true면 ${request} ${file:} ${fixture:}도 채웁니다(input에서만)
	 */
	String fill(String text, boolean withFiles) throws IOException {
		if (text == null || text.indexOf("${") < 0) {
			return text;
		}
		Matcher matcher = TOKEN.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String name = matcher.group(1);
			String value = null;
			if ("workDir".equals(name)) {
				value = slash(this.workDir);
			} else if ("requestDir".equals(name)) {
				value = slash(this.requestDir);
			} else if (name.startsWith("param.")) {
				String key = name.substring("param.".length());
				if (!this.params.containsKey(key)) {
					throw new IllegalStateException("사례 '" + this.evalCase.id() + "'의 params에 '" + key + "'가 없습니다: " + matcher.group());
				}
				value = this.params.get(key);
			} else if (name.startsWith("state.")) {
				Object found = stateValue(this.state, name.substring("state.".length()));
				if (found == null) {
					throw new IllegalStateException("Workflow state에 '" + name.substring("state.".length()) + "'가 없습니다: " + matcher.group());
				}
				value = String.valueOf(found);
			} else if (name.startsWith("saved.")) {
				String key = name.substring("saved.".length());
				if (!this.saved.containsKey(key)) {
					throw new IllegalStateException("앞 http 채점이 save로 남긴 '" + key + "'가 없습니다: " + matcher.group());
				}
				value = this.saved.get(key);
			} else if (withFiles && "request".equals(name)) {
				value = this.request;
			} else if (withFiles && name.startsWith("file:")) {
				value = Files.readString(this.workDir.resolve(name.substring("file:".length())), StandardCharsets.UTF_8);
			} else if (withFiles && name.startsWith("fixture:")) {
				value = this.fill(this.readFixture(name.substring("fixture:".length())), false);
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? matcher.group() : value));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/** state 안의 값을 점으로 이은 이름으로 찾습니다(예: compileResult.success). */
	private static Object stateValue(Map<String, Object> state, String dotted) {
		Object node = state;
		for (String part : dotted.split("\\.")) {
			if (!(node instanceof Map<?, ?> map)) {
				return null;
			}
			node = map.get(part);
		}
		return node;
	}

	private String readFixture(String rel) throws IOException {
		if (this.fixtureDir == null) {
			throw new IllegalStateException("사례 '" + this.evalCase.id() + "'에 fixture가 없는데 자료 파일을 읽으려 했습니다: " + rel);
		}
		return Files.readString(this.fixtureDir.resolve(rel), StandardCharsets.UTF_8);
	}

	/** 자료 폴더의 work/를 작업 폴더로 복사합니다. UTF-8 글자 파일은 자리를 채우고 처음 내용을 기억합니다. */
	private void copyWork(final Path from) throws IOException {
		final List<Path> files = new ArrayList<Path>();
		Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				files.add(file);
				return FileVisitResult.CONTINUE;
			}
		});
		for (Path file : files) {
			String rel = from.relativize(file).toString().replace('\\', '/');
			if (!this.selected(rel)) {
				continue;
			}
			Path to = this.workDir.resolve(rel);
			Files.createDirectories(to.getParent());
			byte[] bytes = Files.readAllBytes(file);
			String text = utf8OrNull(bytes);
			if (text == null) {
				Files.write(to, bytes);
				continue;
			}
			String filled = this.fill(text, false);
			Files.writeString(to, filled, StandardCharsets.UTF_8);
			this.original.put(rel, filled);
		}
	}

	/** 사례의 copy 목록에 걸리는 경로인지 봅니다(목록이 없으면 전부). 폴더를 적으면 그 아래 전부입니다. */
	private boolean selected(String rel) {
		if (this.evalCase.copy() == null) {
			return true;
		}
		for (String item : this.evalCase.copy()) {
			String path = item.replace('\\', '/');
			while (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			if (rel.equals(path) || rel.startsWith(path + "/")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 끝난 뒤 채점합니다.
	 *
	 * @param answer     Agent 답(실패했으면 null)
	 * @param toolCounts Tool 이름별 요청 수
	 * @return [{name, kind, ok, detail}]
	 */
	public List<Map<String, Object>> check(Object answer, Map<String, Integer> toolCounts) {
		return this.check(answer, toolCounts, null);
	}

	/**
	 * 끝난 뒤 채점합니다. http·dbQuery 채점은 probe(격리 시험 서버)가 있어야 합니다.
	 *
	 * @param answer     Agent 답(실패했으면 null)
	 * @param toolCounts Tool 이름별 요청 수
	 * @param probe      격리 시험 서버(띄우지 못했거나 필요 없으면 null)
	 * @return [{name, kind, ok, detail}]
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> check(Object answer, Map<String, Integer> toolCounts, Probe probe) {
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
		for (EvalCase.Check check : this.evalCase.checks()) {
			boolean ok;
			String detail = "";
			try {
				Object value = check.value();
				Map<String, Object> map = value instanceof Map ? (Map<String, Object>) value : Map.of();
				switch (check.kind()) {
					case "answerContains": {
						String text = this.fill(String.valueOf(value instanceof Map ? map.get("text") : value), false);
						ok = answerText(answer, value instanceof Map ? (String) map.get("field") : null).contains(text);
						break;
					}
					case "answerNotEmpty": {
						String text = answerText(answer, String.valueOf(value)).strip();
						ok = !text.isEmpty() && !"[]".equals(text) && !"{}".equals(text);
						break;
					}
					case "answerEquals": {
						ok = answerText(answer, (String) map.get("field")).strip().equals(String.valueOf(map.get("value")));
						break;
					}
					case "fileExists": {
						ok = Files.exists(this.workDir.resolve(this.fill(String.valueOf(value), false)));
						break;
					}
					case "fileChanged": {
						String rel = this.fill(String.valueOf(value), false);
						String now = this.readWork(rel);
						ok = now != null && !now.equals(this.original.get(rel));
						break;
					}
					case "fileContains": {
						String now = this.readWork(this.fill(String.valueOf(map.get("file")), false));
						ok = now != null && now.contains(this.fill(String.valueOf(map.get("text")), false));
						break;
					}
					case "fileMatches":
					case "fileNotMatches": {
						String now = this.readWork(this.fill(String.valueOf(map.get("file")), false));
						boolean found = now != null && find(now, String.valueOf(map.get("pattern")));
						ok = "fileMatches".equals(check.kind()) ? found : now != null && !found;
						detail = now == null ? "파일 없음" : found ? "찾음" : "못 찾음";
						break;
					}
					case "answerMatches":
					case "answerNotMatches": {
						String text = answerText(answer, (String) map.get("field"));
						boolean found = find(text, String.valueOf(map.get("pattern")));
						ok = "answerMatches".equals(check.kind()) ? found : answer != null && !found;
						detail = answer == null ? "답 없음" : found ? "찾음" : "못 찾음";
						break;
					}
					case "textIncreased": {
						String text = String.valueOf(map.get("text"));
						int before = 0;
						int after = 0;
						for (Object file : (List<Object>) map.get("files")) {
							before += count(this.original.get(String.valueOf(file)), text);
							after += count(this.readWork(String.valueOf(file)), text);
						}
						ok = after > before;
						detail = before + " → " + after;
						break;
					}
					case "toolUsed": {
						String name = String.valueOf(value instanceof Map ? map.get("name") : value);
						int min = value instanceof Map && map.get("min") != null ? Integer.parseInt(String.valueOf(map.get("min"))) : 1;
						int used = 0;
						for (Map.Entry<String, Integer> entry : toolCounts.entrySet()) {
							if (entry.getKey().startsWith(name)) {
								used += entry.getValue().intValue();
							}
						}
						ok = used >= min;
						detail = used + "번";
						break;
					}
					case EvalCase.Check.HTTP: {
						if (probe == null) {
							throw new IllegalStateException("격리 시험 서버가 없어 채점하지 못했습니다");
						}
						detail = this.checkHttp(probe, map);
						ok = detail.startsWith("ok");
						break;
					}
					case EvalCase.Check.DB_QUERY: {
						if (probe == null) {
							throw new IllegalStateException("격리 시험 서버가 없어 채점하지 못했습니다");
						}
						List<Map<String, Object>> rows = probe.rows(this.fill(String.valueOf(map.get("sql")), false), MAX_DB_ROWS);
						if (map.get("rows") != null) {
							int expected = Integer.parseInt(String.valueOf(map.get("rows")));
							ok = rows.size() == expected;
							detail = rows.size() + "행";
						} else {
							String expected = this.fill(String.valueOf(map.get("equals")), false);
							Object first = rows.isEmpty() || rows.get(0).isEmpty() ? null : rows.get(0).values().iterator().next();
							ok = first != null && expected.equals(String.valueOf(first));
							detail = "값 " + (rows.isEmpty() ? "(행 없음)" : String.valueOf(first));
						}
						break;
					}
					default:
						throw new IllegalStateException("모르는 채점 종류입니다: " + check.kind());
				}
			} catch (IOException | RuntimeException e) {
				ok = false;
				detail = "채점 실패: " + e.getMessage();
			}
			Map<String, Object> result = new LinkedHashMap<String, Object>();
			result.put("name", check.name());
			result.put("kind", check.kind());
			result.put("ok", Boolean.valueOf(ok));
			result.put("detail", detail);
			results.add(result);
		}
		return results;
	}

	/**
	 * <pre>
	 * http 채점 하나를 합니다. 적은 것만 봅니다: status, json(경로 → 값), contains(글자 목록).
	 * 맞으면 "ok ..."로 시작하는 글자를, 틀리면 무엇이 달랐는지를 돌려줍니다. save는 맞든 틀리든 찾은 값을 남깁니다.
	 * </pre>
	 */
	@SuppressWarnings("unchecked")
	private String checkHttp(Probe probe, Map<String, Object> spec) throws IOException {
		String method = spec.get("method") == null ? "GET" : String.valueOf(spec.get("method")).toUpperCase(java.util.Locale.ROOT);
		String path = this.fill(String.valueOf(spec.get("path")), false);
		String url = probe.baseUrl() + (path.startsWith("/") ? path : "/" + path);
		Object rawBody = spec.get("body");
		String body = null;
		if (rawBody instanceof String text) {
			body = this.fill(text, false);
		} else if (rawBody != null) {
			body = JSON.writeValueAsString(this.fillPlain(rawBody));
		}
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).header("Content-Type", "application/json");
		builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		HttpResponse<String> response;
		try {
			response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("요청을 기다리다 중단되었습니다", e);
		}
		String text = response.body() == null ? "" : response.body();
		Object json = null;
		try {
			json = text.isBlank() ? null : JSON.readValue(text, Object.class);
		} catch (IOException e) {
			// JSON이 아닌 응답(HTML 등)이면 json 검사는 실패하고 contains는 글자로 봅니다.
		}
		Object rawSave = spec.get("save");
		if (rawSave instanceof Map) {
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) rawSave).entrySet()) {
				String picked = JsonPick.pick(json, String.valueOf(entry.getValue()));
				if (picked != null) {
					this.saved.put(entry.getKey(), picked);
				}
			}
		}
		List<String> wrong = new ArrayList<String>();
		if (spec.get("status") != null && !String.valueOf(spec.get("status")).equals(String.valueOf(response.statusCode()))) {
			wrong.add("status " + response.statusCode() + "(기대 " + spec.get("status") + ")");
		}
		Object rawJson = spec.get("json");
		if (rawJson instanceof Map) {
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) rawJson).entrySet()) {
				String expected = this.fill(String.valueOf(entry.getValue()), false);
				String actual = JsonPick.pick(json, entry.getKey());
				if (!expected.equals(actual)) {
					wrong.add(entry.getKey() + "=" + actual + "(기대 " + expected + ")");
				}
			}
		}
		Object rawContains = spec.get("contains");
		if (rawContains != null) {
			List<Object> parts = rawContains instanceof List ? (List<Object>) rawContains : List.of(rawContains);
			for (Object part : parts) {
				String expected = this.fill(String.valueOf(part), false);
				if (!text.contains(expected)) {
					wrong.add("본문에 '" + expected + "' 없음");
				}
			}
		}
		String head = method + " " + path + " → " + response.statusCode();
		if (wrong.isEmpty()) {
			return "ok " + head;
		}
		String snippet = text.replaceAll("\\s+", " ");
		return head + ": " + String.join(", ", wrong) + " | 응답: " + (snippet.length() > 300 ? snippet.substring(0, 300) + "…" : snippet);
	}

	/** 글자·목록·맵 안의 글자 자리를 채웁니다(요청서·파일 자리는 채우지 않음). */
	@SuppressWarnings("unchecked")
	private Object fillPlain(Object value) throws IOException {
		if (value instanceof String text) {
			return this.fill(text, false);
		}
		if (value instanceof List<?> list) {
			List<Object> result = new ArrayList<Object>();
			for (Object item : list) {
				result.add(this.fillPlain(item));
			}
			return result;
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> result = new LinkedHashMap<String, Object>();
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
				result.put(entry.getKey(), this.fillPlain(entry.getValue()));
			}
			return result;
		}
		return value;
	}

	private String readWork(String rel) throws IOException {
		Path file = this.workDir.resolve(rel);
		return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
	}

	/** 답의 한 칸(없으면 답 전체)을 글자로 돌려줍니다. 답이 없으면 빈 글자입니다. */
	private static String answerText(Object answer, String field) {
		if (answer == null) {
			return "";
		}
		if (field == null || field.isBlank()) {
			return String.valueOf(answer);
		}
		if (answer instanceof Map<?, ?> map) {
			Object value = map.get(field);
			return value == null ? "" : String.valueOf(value);
		}
		return "";
	}

	/**
	 * <pre>
	 * 글에 정규식과 맞는 곳이 있는지 봅니다. 줄을 넘어 맞출 수 있게 DOTALL로 컴파일합니다.
	 * "있으면 안 되는 모양"(예: 함수 호출 방식과 본문이 어긋난 코드)을 채점할 때 씁니다.
	 * </pre>
	 */
	private static boolean find(String text, String pattern) {
		return Pattern.compile(pattern, Pattern.DOTALL).matcher(text).find();
	}

	private static int count(String text, String part) {
		if (text == null || part.isEmpty()) {
			return 0;
		}
		int n = 0;
		int at = text.indexOf(part);
		while (at >= 0) {
			n++;
			at = text.indexOf(part, at + part.length());
		}
		return n;
	}

	private static String utf8OrNull(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			return null;
		}
	}

	private static String slash(Path path) {
		return path.toString().replace('\\', '/');
	}

}

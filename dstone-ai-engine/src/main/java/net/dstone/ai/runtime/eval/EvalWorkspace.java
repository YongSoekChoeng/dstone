package net.dstone.ai.runtime.eval;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 평가 실행 한 건의 작업 공간입니다. 자료 폴더를 복사하고, 값의 자리를 채우고, 끝난 뒤 채점합니다.
 *
 *   {요청 폴더}/00.request.md       ← request(선택)
 *   {요청 폴더}/work/               ← 작업 폴더. 자료 폴더의 work/ 아래를 그대로 복사(글자 파일은 자리를 채움)
 *
 * 자료 폴더의 work/ 밖의 파일(요청서, 지시 글 등)은 복사하지 않고 ${fixture:경로}와 request.file로 읽습니다.
 * 자리 채우기는 정해진 이름만 바꿉니다. 다른 ${…}(JSP EL 등)는 그대로 둡니다.
 * </pre>
 */
public class EvalWorkspace {

	/** 바꾸는 자리: ${workDir} ${requestDir} ${param.이름} ${request} ${file:경로} ${fixture:경로} */
	private static final Pattern TOKEN = Pattern.compile("\\$\\{(workDir|requestDir|request|param\\.[A-Za-z0-9_-]+|file:[^}]+|fixture:[^}]+)\\}");

	private final EvalCase evalCase;
	private final Path fixtureDir;
	private final Path requestDir;
	private final Path workDir;
	private final Map<String, String> params;
	private final Map<String, String> original = new LinkedHashMap<String, String>();
	private String request = "";

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
		this.workDir = requestDir.resolve("work");
		this.params = params;
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
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> check(Object answer, Map<String, Integer> toolCounts) {
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
						ok = Files.exists(this.workDir.resolve(String.valueOf(value)));
						break;
					}
					case "fileChanged": {
						String rel = String.valueOf(value);
						String now = this.readWork(rel);
						ok = now != null && !now.equals(this.original.get(rel));
						break;
					}
					case "fileContains": {
						String now = this.readWork(String.valueOf(map.get("file")));
						ok = now != null && now.contains(this.fill(String.valueOf(map.get("text")), false));
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

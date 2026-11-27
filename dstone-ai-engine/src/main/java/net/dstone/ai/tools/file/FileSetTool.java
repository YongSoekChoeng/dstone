package net.dstone.ai.tools.file;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;

/**
 * <pre>
 * 파일 여러 개를 한 번에 다루는 Tool입니다. 어느 Workflow에서나 TOOL step으로 부릅니다.
 *
 * Agent가 "이 파일들을 봐야 한다"고 고른 뒤에 그 내용을 LLM 없이 한 번만 읽어 두고(state에 저장),
 * 뒤의 Agent들에게 input으로 넘기는 데 씁니다. Agent마다 같은 파일을 Tool로 다시 읽는 일이 없어집니다.
 *
 * stepOnly = true라서 LLM에게는 보이지 않고, 결과 길이 상한도 걸리지 않습니다(파일 내용처럼 큰 값을 그대로 돌려줍니다).
 * 파일 하나를 읽는 일은 readFile Tool(FileTool)을 그대로 거칩니다. 그래서 읽기 상한(dstone.ai.tool.file.max-read-chars)과
 * 큰 파일에 붙는 "잘렸다"는 안내가 똑같습니다.
 * </pre>
 */
@AiTool(stepOnly = true)
public class FileSetTool {

	/** 목록의 항목이 맵일 때 경로가 들어 있는 키입니다. */
	private static final String PATH_KEY = "path";

	@Autowired
	private FileTool fileTool;

	/**
	 * <pre>
	 * 파일들을 읽어 둡니다. 있는 파일만, 같은 경로는 한 번만 읽습니다.
	 * 돌려주는 값: {files: {경로: 내용}, missing: [없는 파일의 경로], text: 전부 이은 글자 하나, (이름: 내용)...}
	 * text는 파일마다 "===== 파일: 경로 =====" 줄 아래에 내용을 둔 것으로, Agent의 input에 그대로 넣기 좋습니다.
	 * 있는 파일인데 읽지 못하면 실패입니다({success: false, message}).
	 *
	 * 항목을 맵으로 주면 파일마다 아래를 정할 수 있습니다. 여러 문서를 TOOL step 하나로 읽어 Agent마다 넘길 때 씁니다.
	 *   name:     결과에 이 이름으로도 내용을 둡니다(${state.저장이름.name}으로 읽음). files·missing·text는 쓸 수 없습니다.
	 *   maxChars: 통째로 받아야 하는 파일. 이보다 크면 자르지 않고 실패합니다(readFile의 maxChars와 같음).
	 *   required: true면 파일이 없을 때 실패합니다. 문서가 없는 채로 다음 Agent에 넘어가지 않게 합니다.
	 * YAML 예: 설계서와 태스크아이템 목록을 한 번에
	 *   tool: readFiles
	 *   with:
	 *     paths:
	 *       - { path: "${input}/03-1.design.md", name: design, maxChars: 60000, required: true }
	 *       - { path: "${input}/03-2.taskitems.md", name: taskitems, maxChars: 60000, required: true }
	 *   save: docs        → ${state.docs.design}, ${state.docs.taskitems}
	 * </pre>
	 *
	 * @param paths 읽을 파일들. 경로 글자의 목록이어도 되고, {path, name, maxChars, required}가 든 항목의 목록이어도 됩니다.
	 */
	@Tool(description = "파일 여러 개를 읽어 {files: {경로: 내용}, missing: [없는 경로], text: 전부 이은 글자}로 돌려준다. 있는 파일만, 같은 경로는 한 번만 읽는다. "
		+ "항목을 {path, name, maxChars, required}로 주면 name으로도 내용을 두고, maxChars를 넘거나 required인데 없으면 실패한다.")
	public Map<String, Object> readFiles(@ToolParam(description = "읽을 파일의 절대경로 목록. 경로 글자이거나 {path, name, maxChars, required}가 든 항목") List<Object> paths) {
		Map<String, String> contents = new LinkedHashMap<>();
		Map<String, String> named = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (Object item : paths == null ? List.of() : paths) {
			String path = this.pathOf(item);
			Map<?, ?> options = item instanceof Map ? (Map<?, ?>) item : Map.of();
			String name = options.get("name") == null ? null : options.get("name").toString().trim();
			if (name != null && (name.isEmpty() || RESERVED_NAMES.contains(name) || named.containsKey(name))) {
				return this.fail("readFiles의 name '" + name + "'는 쓸 수 없습니다(비었거나, files·missing·text이거나, 이미 쓴 이름).");
			}
			if (path == null || !this.fileTool.isFileExist(path)) {
				if (Boolean.parseBoolean(String.valueOf(options.get("required")))) {
					return this.fail("꼭 있어야 하는 파일이 없습니다: " + path);
				}
				missing.add(String.valueOf(path));
				continue;
			}
			String content = contents.get(path);
			if (content == null) {
				content = this.fileTool.readFile(path, maxCharsOf(options.get("maxChars")));
				if (content == null || content.startsWith(Constants.Outcome.FAIL_PREFIX)) {
					return this.fail(content == null ? "파일을 읽지 못했습니다: " + path : content);
				}
				contents.put(path, content);
			}
			if (name != null) {
				named.put(name, content);
			}
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("files", contents);
		result.put("missing", missing);
		result.put("text", joined(contents));
		result.putAll(named);
		return result;
	}

	/** readFiles 결과에 이미 있는 키라 name으로 쓸 수 없는 것들입니다. */
	private static final java.util.Set<String> RESERVED_NAMES = java.util.Set.of("files", "missing", "text");

	/** 항목의 maxChars 값을 숫자로 바꿉니다. 없거나 숫자가 아니면 null(설정값까지 읽고 넘으면 자름)입니다. */
	private static Integer maxCharsOf(Object value) {
		if (value instanceof Number number) {
			return Integer.valueOf(number.intValue());
		}
		try {
			return value == null ? null : Integer.valueOf(value.toString().trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * <pre>
	 * 목록의 항목마다, 항목이 가리키는 파일을 읽어서 붙여 줍니다. forEach로 Agent를 부르기 전에 "항목 + 그 파일의 내용"을 준비할 때 씁니다.
	 *
	 * 예) items: [{path: "/a/Faq.java", referencePath: "/a/Board.java"}], read: {current: path, reference: referencePath}
	 *   → [{item: {path: ..., referencePath: ...}, current: "(Faq.java의 내용)", reference: "(Board.java의 내용)"}]
	 *
	 * 경로가 비어 있거나 파일이 없거나 읽지 못하면 그 내용은 빈 글자입니다(새로 만들 파일처럼 아직 없는 것이 정상일 수 있어서 실패로 보지 않습니다).
	 * lineNumbers가 true면 "줄번호: 내용" 모양으로 읽습니다(readFileLines Tool과 같습니다).
	 * </pre>
	 *
	 * @param items       항목 목록(맵). 빈 자리(null)는 건너뜁니다.
	 * @param read        붙일 이름 → 경로가 들어 있는 필드 이름
	 * @param lineNumbers true면 줄 번호를 붙여 읽습니다.
	 */
	@Tool(description = "목록의 항목마다 항목의 필드가 가리키는 파일을 읽어 [{item: 원래 항목, 이름: 파일 내용}]으로 돌려준다. read에 '붙일 이름: 경로가 든 필드 이름'을 적는다. 없는 파일의 내용은 빈 글자다.")
	public List<Map<String, Object>> readItemFiles(
			@ToolParam(description = "항목 목록") List<Object> items,
			@ToolParam(description = "붙일 이름 → 경로가 들어 있는 필드 이름. 예: {source: path}") Map<String, String> read,
			@ToolParam(required = false, description = "true면 '줄번호: 내용' 모양으로 읽는다") Boolean lineNumbers) {
		List<Map<String, Object>> result = new ArrayList<>();
		for (Object item : items == null ? List.of() : items) {
			if (item == null) {
				continue;
			}
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("item", item);
			for (Map.Entry<String, String> entry : (read == null ? Map.<String, String>of() : read).entrySet()) {
				Object path = item instanceof Map ? ((Map<?, ?>) item).get(entry.getValue()) : null;
				row.put(entry.getKey(), this.contentOf(path == null ? "" : path.toString(), Boolean.TRUE.equals(lineNumbers)));
			}
			result.add(row);
		}
		return result;
	}

	/** 파일 내용을 돌려줍니다. 경로가 비었거나 파일이 없거나 읽지 못하면 빈 글자입니다. */
	private String contentOf(String path, boolean lineNumbers) {
		if (path.isBlank() || !this.fileTool.isFileExist(path)) {
			return "";
		}
		String content = lineNumbers ? this.fileTool.readNumberedLines(path, null, null) : this.fileTool.readFile(path);
		return content == null || content.startsWith(Constants.Outcome.FAIL_PREFIX) ? "" : content;
	}

	/**
	 * 읽어 둔 파일들을 글자 하나로 잇습니다. 파일마다 "===== 파일: 경로 =====" 줄 아래에 내용을 둡니다.
	 *
	 * @param contents 경로 → 내용
	 */
	static String joined(Map<String, String> contents) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, String> entry : contents.entrySet()) {
			parts.add("===== 파일: " + entry.getKey() + " =====\n" + entry.getValue());
		}
		return String.join("\n\n", parts);
	}

	/** 목록의 항목에서 경로를 꺼냅니다. 글자면 그대로, 맵이면 path 값입니다. 없으면 null입니다. */
	private String pathOf(Object item) {
		Object path = item instanceof Map ? ((Map<?, ?>) item).get(PATH_KEY) : item;
		return path == null ? null : path.toString();
	}

	/** TOOL step이 실패로 알아보는 모양({success: false, message})을 만듭니다. */
	private Map<String, Object> fail(String message) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", false);
		result.put("message", message);
		return result;
	}


	/** 이 크기보다 큰 파일은 내용 대신 크기와 수정 시각으로 비교합니다(읽는 데 오래 걸리지 않게). */
	private static final long HASH_LIMIT_BYTES = 5L * 1024 * 1024;

	/** 바뀐 파일을 안내 글에 이 개수까지만 적습니다. */
	private static final int MAX_LISTED_CHANGES = 100;

	/** diff를 만들 때 이보다 큰 파일은 사본을 두지 않고 diff도 만들지 않습니다(글 파일이 아닐 가능성이 큼). */
	private static final long DIFF_FILE_LIMIT_BYTES = 512L * 1024;

	/** diff 글 전체의 상한(글자 수)입니다. 넘으면 잘라 내고 안내를 붙입니다. Agent 입력을 지나치게 키우지 않으려고. */
	private static final int MAX_DIFF_CHARS = 30000;

	/**
	 * <pre>
	 * 폴더 아래 파일들의 지금 상태(스냅샷)를 만들고, 지난번 스냅샷과 비교해 바뀐 파일을 알려 줍니다.
	 * 돌려주는 값: {first, changed: [상대경로], snapshot: {상대경로: 내용 해시}, summary: 사람·Agent가 읽을 한 덩어리 글}
	 * - first: previous가 비어 비교할 것이 없으면 true(changed는 빈 목록)
	 * - changed: 새로 생기거나, 내용이 바뀌거나, 없어진 파일(없어진 것은 "(삭제) 경로")
	 * 결과를 save해 두었다가 다음에 그 snapshot을 previous로 넘기면 "그 사이에 바뀐 것"이 됩니다.
	 * 비교는 내용 해시로 합니다(다시 써도 내용이 같으면 바뀌지 않은 것). 아주 큰 파일만 크기와 수정 시각으로 봅니다.
 *
 * copyDir을 주면 무엇이 바뀌었는지(diff)도 만듭니다. 결과에 diff(파일마다 "===== 경로" 아래 unified diff)가 더해집니다.
 * 이 폴더에 지난번 내용의 사본을 두었다가 다음 호출 때 비교하고, 비교가 끝나면 지금 내용으로 바꿔 둡니다.
 * Agent의 자기 설명이 아니라 실제로 바뀐 줄을 다음 Agent에게 넘기려는 것입니다(지난 수정이 무엇을 되돌렸는지 보임).
 * copyDir이 basePath 안에 있으면 스냅샷에서 뺍니다. Agent가 작업 폴더를 뒤지다 옛 사본을 읽지 않게 basePath 밖에 두기를 권합니다.
	 *
	 * YAML 예: 지난 검증 뒤로 작업 폴더에서 바뀐 파일
	 *   tool: fileChanges
	 *   with: { basePath: "${input}/work", previous: "${state.workChanges.snapshot:}", excludeDirs: compile-check }
	 *   save: workChanges
	 * </pre>
	 *
	 * @param basePath    볼 폴더의 절대경로
	 * @param previous    지난번 결과의 snapshot. 처음이면 비움
	 * @param excludeDirs 보지 않을 폴더 이름(쉼표로 여럿). 빌드 결과처럼 매번 바뀌는 폴더를 뺀다
	 */
	public Map<String, Object> fileChanges(String basePath, Object previous, String excludeDirs) {
		return this.fileChanges(basePath, previous, excludeDirs, null);
	}

	@Tool(description = "폴더 아래 파일의 스냅샷을 만들고 지난 스냅샷(previous)과 비교해 바뀐 파일을 {first, changed, snapshot, summary, diff}로 돌려준다. diff는 copyDir을 줄 때만.")
	public Map<String, Object> fileChanges(
			@ToolParam(description = "볼 폴더의 절대경로") String basePath,
			@ToolParam(required = false, description = "지난번 결과의 snapshot({상대경로: 해시}). 처음이면 비움") Object previous,
			@ToolParam(required = false, description = "보지 않을 폴더 이름. 쉼표로 여럿") String excludeDirs,
			@ToolParam(required = false, description = "지난 내용의 사본을 둘 폴더의 절대경로. 주면 바뀐 파일의 diff를 만든다") String copyDir) {
		if (basePath == null || basePath.isBlank() || !new File(basePath.trim()).isDirectory()) {
			return this.fail("폴더가 없습니다: " + basePath);
		}
		List<String> excluded = new ArrayList<>();
		if (excludeDirs != null) {
			for (String name : excludeDirs.split(",")) {
				if (!name.isBlank()) {
					excluded.add(name.trim());
				}
			}
		}
		File root = new File(basePath.trim());
		File copyRoot = copyDir == null || copyDir.isBlank() ? null : new File(copyDir.trim());
		Map<String, String> snapshot = new TreeMap<>();
		try {
			collect(root, root, excluded, copyRoot, snapshot);
		} catch (IOException e) {
			return this.fail("파일을 읽지 못했습니다: " + e.getMessage());
		}
		boolean first = !(previous instanceof Map) || ((Map<?, ?>) previous).isEmpty();
		List<String> changed = new ArrayList<>();
		if (!first) {
			Map<?, ?> before = (Map<?, ?>) previous;
			for (Map.Entry<String, String> entry : snapshot.entrySet()) {
				Object old = before.get(entry.getKey());
				if (old == null || !old.toString().equals(entry.getValue())) {
					changed.add(entry.getKey());
				}
			}
			for (Object key : before.keySet()) {
				if (!snapshot.containsKey(String.valueOf(key))) {
					changed.add("(삭제) " + key);
				}
			}
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("first", first);
		result.put("changed", changed);
		result.put("snapshot", snapshot);
		result.put("summary", summary(first, changed));
		if (copyRoot != null) {
			try {
				if (!first) {
					result.put("diff", diffText(root, copyRoot, changed));
				}
				refreshCopy(root, copyRoot, snapshot.keySet());
			} catch (IOException e) {
				// diff는 덧붙이는 정보라, 만들지 못해도 바뀐 파일 목록은 그대로 돌려준다
				result.put("diff", "diff를 만들지 못했습니다: " + e.getMessage());
			}
		}
		return result;
	}

	/** 바뀐 파일마다 사본(지난 내용)과 지금 내용의 diff를 이어 붙입니다. 전체가 MAX_DIFF_CHARS를 넘으면 자릅니다. */
	private static String diffText(File root, File copyRoot, List<String> changed) throws IOException {
		StringBuilder text = new StringBuilder();
		for (String path : changed) {
			String body;
			if (path.startsWith("(삭제) ")) {
				body = "(이 파일이 없어졌습니다)\n";
				path = path.substring("(삭제) ".length());
			} else {
				File now = new File(root, path);
				File old = new File(copyRoot, path);
				if (now.length() > DIFF_FILE_LIMIT_BYTES || isBinary(now)) {
					body = "(크거나 글 파일이 아니라 diff를 만들지 않습니다)\n";
				} else {
					String before = old.isFile() ? Files.readString(old.toPath(), StandardCharsets.UTF_8) : "";
					body = (old.isFile() ? "" : "(새 파일이거나 지난 내용이 없습니다)\n") + LineDiff.diff(before, Files.readString(now.toPath(), StandardCharsets.UTF_8));
				}
			}
			text.append("===== ").append(path).append('\n').append(body);
			if (text.length() > MAX_DIFF_CHARS) {
				text.setLength(MAX_DIFF_CHARS);
				text.append("\n...(diff가 길어 ").append(MAX_DIFF_CHARS).append("자에서 잘랐습니다. 나머지 파일은 직접 읽어 보십시오)\n");
				break;
			}
		}
		return text.toString();
	}

	/** 사본 폴더를 비우고 지금 글 파일들을 복사해 둡니다(다음 호출의 "지난 내용"). */
	private static void refreshCopy(File root, File copyRoot, java.util.Set<String> paths) throws IOException {
		if (copyRoot.exists()) {
			org.springframework.util.FileSystemUtils.deleteRecursively(copyRoot);
		}
		for (String path : paths) {
			File source = new File(root, path);
			if (source.length() > DIFF_FILE_LIMIT_BYTES || isBinary(source)) {
				continue;
			}
			File target = new File(copyRoot, path);
			target.getParentFile().mkdirs();
			Files.copy(source.toPath(), target.toPath());
		}
	}

	/** 앞부분에 NUL 바이트가 있으면 글 파일이 아닌 것으로 봅니다. */
	private static boolean isBinary(File file) throws IOException {
		byte[] head = new byte[8192];
		int read;
		try (java.io.InputStream in = Files.newInputStream(file.toPath())) {
			read = in.read(head);
		}
		for (int i = 0; i < read; i++) {
			if (head[i] == 0) {
				return true;
			}
		}
		return false;
	}

	/** 바뀐 파일 목록을 읽기 좋은 글로 만듭니다. */
	private static String summary(boolean first, List<String> changed) {
		if (first) {
			return "처음 확인이라 비교할 지난 스냅샷이 없습니다.";
		}
		if (changed.isEmpty()) {
			return "지난 스냅샷 뒤로 바뀐 파일이 없습니다.";
		}
		StringBuilder text = new StringBuilder("지난 스냅샷 뒤로 바뀐 파일 " + changed.size() + "개:");
		for (int i = 0; i < changed.size() && i < MAX_LISTED_CHANGES; i++) {
			text.append("\n- ").append(changed.get(i));
		}
		if (changed.size() > MAX_LISTED_CHANGES) {
			text.append("\n- ...외 ").append(changed.size() - MAX_LISTED_CHANGES).append("개");
		}
		return text.toString();
	}

	/** 폴더 아래 파일을 "상대경로 → 해시"로 모읍니다. 뺄 폴더는 들어가지 않습니다. */
	private static void collect(File root, File dir, List<String> excluded, File copyRoot, Map<String, String> snapshot) throws IOException {
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		Arrays.sort(children);
		for (File child : children) {
			if (child.isDirectory()) {
				if (!excluded.contains(child.getName()) && !(copyRoot != null && sameFile(child, copyRoot))) {
					collect(root, child, excluded, copyRoot, snapshot);
				}
			} else if (child.isFile()) {
				String relative = root.toPath().relativize(child.toPath()).toString().replace('\\', '/');
				snapshot.put(relative, fingerprint(child));
			}
		}
	}

	/** 파일 하나의 해시입니다. 아주 큰 파일은 크기와 수정 시각입니다. */
	/** 두 경로가 같은 폴더인지 봅니다(상대경로·대소문자 표기가 달라도). */
	private static boolean sameFile(File a, File b) throws IOException {
		return a.getCanonicalFile().equals(b.getCanonicalFile());
	}

	private static String fingerprint(File file) throws IOException {
		if (file.length() > HASH_LIMIT_BYTES) {
			return "size:" + file.length() + ",modified:" + file.lastModified();
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file.toPath()))).substring(0, 16);
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}
}

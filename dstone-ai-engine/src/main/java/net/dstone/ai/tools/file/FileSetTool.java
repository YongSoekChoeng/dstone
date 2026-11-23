package net.dstone.ai.tools.file;

import java.io.File;
import java.io.IOException;
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
	 * 돌려주는 값: {files: {경로: 내용}, missing: [없는 파일의 경로], text: 전부 이은 글자 하나}
	 * text는 파일마다 "===== 파일: 경로 =====" 줄 아래에 내용을 둔 것으로, Agent의 input에 그대로 넣기 좋습니다.
	 * 있는 파일인데 읽지 못하면 실패입니다({success: false, message}).
	 * </pre>
	 *
	 * @param paths 읽을 파일들. 경로 글자의 목록이어도 되고, {path: 경로}가 든 항목의 목록이어도 됩니다.
	 */
	@Tool(description = "파일 여러 개를 읽어 {files: {경로: 내용}, missing: [없는 경로], text: 전부 이은 글자}로 돌려준다. 있는 파일만, 같은 경로는 한 번만 읽는다.")
	public Map<String, Object> readFiles(@ToolParam(description = "읽을 파일의 절대경로 목록. 경로 글자이거나 {path: 경로}가 든 항목") List<Object> paths) {
		Map<String, String> contents = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (Object item : paths == null ? List.of() : paths) {
			String path = this.pathOf(item);
			if (path == null || !this.fileTool.isFileExist(path)) {
				missing.add(String.valueOf(path));
				continue;
			}
			if (contents.containsKey(path)) {
				continue;
			}
			String content = this.fileTool.readFile(path);
			if (content == null || content.startsWith(Constants.Outcome.FAIL_PREFIX)) {
				return this.fail(content == null ? "파일을 읽지 못했습니다: " + path : content);
			}
			contents.put(path, content);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("files", contents);
		result.put("missing", missing);
		result.put("text", joined(contents));
		return result;
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

	/**
	 * <pre>
	 * 폴더 아래 파일들의 지금 상태(스냅샷)를 만들고, 지난번 스냅샷과 비교해 바뀐 파일을 알려 줍니다.
	 * 돌려주는 값: {first, changed: [상대경로], snapshot: {상대경로: 내용 해시}, summary: 사람·Agent가 읽을 한 덩어리 글}
	 * - first: previous가 비어 비교할 것이 없으면 true(changed는 빈 목록)
	 * - changed: 새로 생기거나, 내용이 바뀌거나, 없어진 파일(없어진 것은 "(삭제) 경로")
	 * 결과를 save해 두었다가 다음에 그 snapshot을 previous로 넘기면 "그 사이에 바뀐 것"이 됩니다.
	 * 비교는 내용 해시로 합니다(다시 써도 내용이 같으면 바뀌지 않은 것). 아주 큰 파일만 크기와 수정 시각으로 봅니다.
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
	@Tool(description = "폴더 아래 파일의 스냅샷을 만들고 지난 스냅샷(previous)과 비교해 바뀐 파일을 {first, changed, snapshot, summary}로 돌려준다.")
	public Map<String, Object> fileChanges(
			@ToolParam(description = "볼 폴더의 절대경로") String basePath,
			@ToolParam(required = false, description = "지난번 결과의 snapshot({상대경로: 해시}). 처음이면 비움") Object previous,
			@ToolParam(required = false, description = "보지 않을 폴더 이름. 쉼표로 여럿") String excludeDirs) {
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
		Map<String, String> snapshot = new TreeMap<>();
		try {
			collect(root, root, excluded, snapshot);
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
		return result;
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
	private static void collect(File root, File dir, List<String> excluded, Map<String, String> snapshot) throws IOException {
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		Arrays.sort(children);
		for (File child : children) {
			if (child.isDirectory()) {
				if (!excluded.contains(child.getName())) {
					collect(root, child, excluded, snapshot);
				}
			} else if (child.isFile()) {
				String relative = root.toPath().relativize(child.toPath()).toString().replace('\\', '/');
				snapshot.put(relative, fingerprint(child));
			}
		}
	}

	/** 파일 하나의 해시입니다. 아주 큰 파일은 크기와 수정 시각입니다. */
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

package net.dstone.ai.tools.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * 파일 하나를 읽는 일은 readFile Tool(FileUtil)을 그대로 거칩니다. 그래서 읽기 상한(dstone.ai.tool.file.max-read-chars)과
 * 큰 파일에 붙는 "잘렸다"는 안내가 똑같습니다.
 * </pre>
 */
@AiTool(stepOnly = true)
public class FileSetTool {

	/** 목록의 항목이 맵일 때 경로가 들어 있는 키입니다. */
	private static final String PATH_KEY = "path";

	@Autowired
	private FileUtil fileUtil;

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
			if (path == null || !this.fileUtil.isFileExist(path)) {
				missing.add(String.valueOf(path));
				continue;
			}
			if (contents.containsKey(path)) {
				continue;
			}
			String content = this.fileUtil.readFile(path);
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
		if (path.isBlank() || !this.fileUtil.isFileExist(path)) {
			return "";
		}
		String content = lineNumbers ? this.fileUtil.readFileLines(path, null, null) : this.fileUtil.readFile(path);
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

}

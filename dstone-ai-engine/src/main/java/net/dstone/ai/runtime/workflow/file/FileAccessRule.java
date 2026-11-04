package net.dstone.ai.runtime.workflow.file;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <pre>
 * Tool 호출 하나가 어떤 파일을 읽었는지, 썼는지를 Tool 이름과 인자만 보고 알아냅니다.
 * 파일을 건드리지 않고 DB에도 접근하지 않는 순수한 규칙이라 단위 테스트로 확인할 수 있습니다.
 *
 * 알아보는 Tool은 아래 표에 적힌 것뿐입니다. 표에 없는 Tool은 파일을 건드리지 않는 것으로 봅니다.
 *
 *   Tool                                       경로 인자                  기록
 *   readFile / readFileLines / readFileTail    fileFullPath               READ
 *   readFiles                                  paths(글자 또는 {path})    READ
 *   writeFile / appendFile / replaceInFile     filePath                   WRITE
 *   editFileLines                              filePath                   WRITE
 *   editFileTexts                              filePath                   WRITE(dryRun이 true면 기록하지 않음)
 *   copyFileWithReplace                        sourcePath / targetPath    READ / WRITE
 *   MCP read_text_file / read_file             path                       READ
 *   MCP read_multiple_files                    paths                      READ
 *   MCP write_file / edit_file                 path                       WRITE(edit_file의 dryRun이 true면 기록하지 않음)
 *
 * 경로는 앞뒤 공백을 지우고 역슬래시(\)를 슬래시(/)로 바꿔서 기록합니다. 같은 파일이 "D:\a\b.md"와 "D:/a/b.md"로
 * 두 줄이 되지 않게 하려는 것입니다. Java의 File은 Windows에서도 슬래시 경로를 그대로 엽니다.
 * </pre>
 */
public final class FileAccessRule {

	/** 파일을 읽었다는 표시입니다. */
	public static final String READ = "READ";
	/** 파일을 만들거나 고쳤다는 표시입니다. */
	public static final String WRITE = "WRITE";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private FileAccessRule() {
	}

	/**
	 * Tool 호출 하나에서 건드린 파일 한 개입니다.
	 *
	 * @param path   파일 경로
	 * @param action READ 또는 WRITE
	 */
	public record FileAccess(String path, String action) {
	}

	/**
	 * <pre>
	 * Tool 이름과 인자(JSON 글자)를 보고 건드린 파일 목록을 돌려줍니다.
	 * 표에 없는 Tool이거나 인자를 읽을 수 없으면 빈 목록입니다.
	 * </pre>
	 *
	 * @param toolName  Tool 이름
	 * @param toolInput Tool에 넘긴 인자(JSON 글자)
	 */
	public static List<FileAccess> of(String toolName, String toolInput) {
		List<FileAccess> result = new ArrayList<FileAccess>();
		if (toolName == null) {
			return result;
		}
		Map<String, Object> args = argsOf(toolInput);
		if (args == null) {
			return result;
		}
		switch (toolName) {
			case "readFile":
			case "readFileLines":
			case "readFileTail":
				add(result, args.get("fileFullPath"), READ);
				break;
			case "readFiles":
			case "read_multiple_files":
				addAll(result, args.get("paths"), READ);
				break;
			case "writeFile":
			case "appendFile":
			case "replaceInFile":
			case "editFileLines":
				add(result, args.get("filePath"), WRITE);
				break;
			case "editFileTexts":
				if (!Boolean.TRUE.equals(args.get("dryRun"))) {
					add(result, args.get("filePath"), WRITE);
				}
				break;
			case "copyFileWithReplace":
				add(result, args.get("sourcePath"), READ);
				add(result, args.get("targetPath"), WRITE);
				break;
			case "read_text_file":
			case "read_file":
				add(result, args.get("path"), READ);
				break;
			case "write_file":
				add(result, args.get("path"), WRITE);
				break;
			case "edit_file":
				if (!Boolean.TRUE.equals(args.get("dryRun"))) {
					add(result, args.get("path"), WRITE);
				}
				break;
			default:
				break;
		}
		return result;
	}

	/**
	 * <pre>
	 * Tool의 답이 실패를 뜻하는지 봅니다. 실패한 호출은 파일을 건드리지 않았으므로 기록하지 않습니다.
	 * 이 엔진의 파일 Tool은 실패하면 "실패: ..."로 답합니다. 글자를 돌려주는 Tool의 답은 JSON 글자("...")로 한 번 감싸여 오므로 따옴표도 함께 봅니다.
	 * </pre>
	 *
	 * @param result Tool이 돌려준 답
	 */
	public static boolean isFailure(String result) {
		if (result == null) {
			return false;
		}
		String text = result.trim();
		if (text.startsWith("\"")) {
			text = text.substring(1);
		}
		return text.startsWith("실패");
	}

	/** 경로를 기록할 모양으로 다듬습니다. 비어 있으면 null입니다. */
	public static String normalize(String path) {
		if (path == null) {
			return null;
		}
		String trimmed = path.trim().replace('\\', '/');
		return trimmed.isEmpty() ? null : trimmed;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> argsOf(String toolInput) {
		if (toolInput == null || toolInput.isBlank()) {
			return null;
		}
		try {
			Object value = OBJECT_MAPPER.readValue(toolInput, Object.class);
			return value instanceof Map ? (Map<String, Object>) value : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static void add(List<FileAccess> result, Object path, String action) {
		if (!(path instanceof String)) {
			return;
		}
		String normalized = normalize((String) path);
		if (normalized == null) {
			return;
		}
		for (FileAccess access : result) {
			if (access.path().equals(normalized) && access.action().equals(action)) {
				return;
			}
		}
		result.add(new FileAccess(normalized, action));
	}

	/** 경로 목록을 기록합니다. 항목은 경로 글자이거나 {path: 경로}입니다. */
	@SuppressWarnings("unchecked")
	private static void addAll(List<FileAccess> result, Object paths, String action) {
		if (!(paths instanceof List)) {
			return;
		}
		for (Object item : (List<Object>) paths) {
			if (item instanceof Map) {
				add(result, ((Map<String, Object>) item).get("path"), action);
			} else {
				add(result, item, action);
			}
		}
	}

}

package net.dstone.ai.runtime.agent;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.runtime.workflow.file.FileAccessRule;
import net.dstone.ai.tools.file.FileTool;

/**
 * <pre>
 * Agent 호출 한 번 안에서, 이미 읽은 파일 범위를 또 읽으려 하면 Tool을 실행하지 않고 짧은 안내만 돌려주는 포장지입니다.
 *
 * 왜 필요한가:
 * Tool 결과는 대화에 쌓이고 그 뒤의 LLM 호출마다 다시 실려 갑니다. 같은 범위를 또 읽으면 같은 내용이 대화에 두 번 들어가서,
 * 그 뒤의 모든 호출이 그만큼 무거워집니다. 프롬프트에 "같은 범위를 두 번 읽지 않는다"고 적어도 지켜지지 않을 때가 있습니다
 * (sm-workflow 개발 step에서 한 번 호출에 35,000자를 다시 읽었습니다).
 *
 * 무엇을 막는가:
 *   readFileLines   요청한 줄 범위가 이번 호출에서 이미 받은 범위 안에 모두 들어 있을 때
 *   readFile        같은 파일을 이미 끝까지 받았을 때(앞부분만 잘려서 받았으면 막지 않음)
 * 아래일 때는 막지 않고 그대로 읽습니다.
 *   - 그 뒤로 파일이 바뀌었을 때(고친 시각이나 크기가 다름). 이 Agent가 Tool로 고친 파일은 기록을 바로 지웁니다.
 *   - 일부만 겹칠 때
 *   - 대화가 길어져 오래된 Tool 결과를 줄인 뒤(ToolResultCompactionAdvisor가 장부를 비웁니다)
 *
 * Agent 호출마다 AgentExecutor가 장부(ReadHistory) 하나를 만들어, 그 호출에 붙는 모든 Tool이 이 포장지로 함께 씁니다.
 * 파일을 고치는 Tool도 감싸는 이유는, 고친 파일의 기록을 지우기 위해서입니다.
 * 막았을 때도 Tool 호출 횟수(ToolCallBudgetCallback)는 하나로 셉니다.
 * </pre>
 */
public class RereadGuardCallback implements ToolCallback {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RereadGuardCallback.class);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** readFileLines 결과에서 "줄번호: 내용" 줄을 찾는 패턴입니다. */
	private static final Pattern NUMBERED_LINE = Pattern.compile("(?m)^(\\d+): ");

	private static final String READ_FILE = "readFile";
	private static final String READ_FILE_LINES = "readFileLines";

	private final ToolCallback delegate;
	private final ReadHistory history;
	private final String agentId;

	/**
	 * @param delegate 실제 Tool 호출을 맡는 원래 ToolCallback입니다.
	 * @param history  이 Agent 호출의 읽은 범위 장부입니다. 같은 호출에 붙는 Tool들이 함께 씁니다.
	 * @param agentId  이 Tool을 쓰는 Agent의 id입니다(로그용).
	 */
	public RereadGuardCallback(ToolCallback delegate, ReadHistory history, String agentId) {
		this.delegate = delegate;
		this.history = history;
		this.agentId = agentId;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return this.delegate.getToolDefinition();
	}

	@Override
	public ToolMetadata getToolMetadata() {
		return this.delegate.getToolMetadata();
	}

	@Override
	public String call(String toolInput) {
		return this.guarded(toolInput, null, false);
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		return this.guarded(toolInput, toolContext, true);
	}

	private String guarded(String toolInput, ToolContext toolContext, boolean withContext) {
		String name = this.delegate.getToolDefinition().name();
		if (READ_FILE_LINES.equals(name) || READ_FILE.equals(name)) {
			return this.read(name, toolInput, toolContext, withContext);
		}
		String result = this.callDelegate(toolInput, toolContext, withContext);
		// 파일을 고친 Tool이면 그 파일의 기록을 지웁니다(실패했어도 지워서 손해 볼 것이 없습니다).
		List<FileAccessRule.FileAccess> accesses = FileAccessRule.of(name, toolInput);
		for (FileAccessRule.FileAccess access : accesses) {
			if (FileAccessRule.WRITE.equals(access.action())) {
				this.history.forgetPath(access.path());
			}
		}
		return result;
	}

	/** 읽기 Tool을 부릅니다. 이미 읽은 범위면 부르지 않고 안내를 돌려줍니다. */
	private String read(String name, String toolInput, ToolContext toolContext, boolean withContext) {
		Map<String, Object> args = argsOf(toolInput);
		String path = args == null ? null : FileAccessRule.normalize(args.get("fileFullPath") == null ? null : String.valueOf(args.get("fileFullPath")));
		if (path == null) {
			return this.callDelegate(toolInput, toolContext, withContext);
		}
		String key = ReadHistory.keyOf(name, path);
		Integer startLine = READ_FILE_LINES.equals(name) ? FileTool.lineNumberOf(args.get("startLine")) : null;
		Integer endLine = READ_FILE_LINES.equals(name) ? FileTool.lineNumberOf(args.get("endLine")) : null;
		int from = startLine == null || startLine.intValue() < 1 ? 1 : startLine.intValue();
		int to = endLine == null ? ReadHistory.END : endLine.intValue();

		String stamp = stampOf(path);
		if (this.history.covers(key, stamp, from, to)) {
			LOGGER.info("dstone-ai-engine tool: agent[" + this.agentId + "]가 이미 읽은 범위를 다시 읽으려 해서 [" + name + "]를 실행하지 않았습니다 - "
				+ path + " " + from + "~" + (to == ReadHistory.END ? "끝" : String.valueOf(to)) + "줄");
			return alreadyReadNotice(name, from, to);
		}

		String result = this.callDelegate(toolInput, toolContext, withContext);
		if (stamp != null && !FileAccessRule.isFailure(result)) {
			int[] range = READ_FILE_LINES.equals(name) ? readRangeOf(textOf(result), endLine) : wholeFileRangeOf(textOf(result));
			if (range != null) {
				this.history.record(key, stamp, range[0], range[1]);
			}
		}
		return result;
	}

	private String callDelegate(String toolInput, ToolContext toolContext, boolean withContext) {
		return withContext ? this.delegate.call(toolInput, toolContext) : this.delegate.call(toolInput);
	}

	/** 이미 읽은 범위를 또 읽으려 할 때 돌려주는 안내입니다. */
	static String alreadyReadNotice(String name, int from, int to) {
		String range = READ_FILE_LINES.equals(name) ? from + "~" + (to == ReadHistory.END ? "끝" : String.valueOf(to)) + "줄은" : "내용은";
		return "(안내: 이 파일의 " + range + " 이번 작업에서 이미 " + name + "로 읽었고, 그 뒤로 파일이 바뀌지 않았습니다. "
			+ "Tool을 다시 실행하지 않았습니다. 앞에서 받은 결과를 그대로 보십시오. 아직 읽지 않은 줄이 필요하면 그 구간만 startLine/endLine으로 지정해 읽으십시오.)";
	}

	/**
	 * <pre>
	 * readFileLines 결과에서 실제로 받은 줄 범위를 알아냅니다. 줄 번호 줄이 없으면 null입니다.
	 * - 잘렸다는 안내가 있으면 결과에 들어 있는 첫 줄~마지막 줄만 받은 것입니다.
	 * - 잘리지 않았고 끝 줄을 정하지 않았거나 파일보다 큰 끝 줄을 정했으면 파일 끝까지 받은 것입니다(END).
	 * </pre>
	 *
	 * @param text    Tool 결과(JSON 따옴표를 벗긴 글자)
	 * @param endLine 요청한 끝 줄. 없으면 null
	 */
	static int[] readRangeOf(String text, Integer endLine) {
		if (text == null) {
			return null;
		}
		Matcher matcher = NUMBERED_LINE.matcher(text);
		int first = -1;
		int last = -1;
		while (matcher.find()) {
			int lineNo;
			try {
				lineNo = Integer.parseInt(matcher.group(1));
			} catch (NumberFormatException e) {
				continue;
			}
			if (first < 0) {
				first = lineNo;
			}
			last = lineNo;
		}
		if (first < 0) {
			return null;
		}
		boolean cut = text.contains("...(구간이 커서") || text.contains("(안내: startLine과 endLine 없이");
		if (!cut && (endLine == null || endLine.intValue() > last)) {
			return new int[] { first, ReadHistory.END };
		}
		return new int[] { first, last };
	}

	/** readFile 결과가 잘리지 않았으면 파일 전체(1~END)를 받은 것입니다. 잘렸으면 null입니다. */
	static int[] wholeFileRangeOf(String text) {
		if (text == null || text.contains("...(파일이 커서")) {
			return null;
		}
		return new int[] { 1, ReadHistory.END };
	}

	/** Tool 결과는 JSON 글자("...")로 한 번 감싸여 올 수 있습니다. 감싸여 있으면 벗깁니다. */
	static String textOf(String result) {
		if (result == null) {
			return null;
		}
		String trimmed = result.trim();
		if (trimmed.startsWith("\"")) {
			try {
				return OBJECT_MAPPER.readValue(trimmed, String.class);
			} catch (Exception e) {
				return result;
			}
		}
		return result;
	}

	/** 파일의 지금 상태(고친 시각과 크기)입니다. 파일이 없으면 null이고, 그때는 막지도 기록하지도 않습니다. */
	static String stampOf(String path) {
		File file = new File(path);
		if (!file.isFile()) {
			return null;
		}
		return file.lastModified() + ":" + file.length();
	}

	private static Map<String, Object> argsOf(String toolInput) {
		if (toolInput == null || toolInput.isBlank()) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readValue(toolInput, new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception e) {
			return null;
		}
	}

}

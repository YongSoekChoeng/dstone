package net.dstone.ai.runtime.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import net.dstone.ai.common.exception.AgentContractException;
import net.dstone.common.utils.LogUtil;

/**
 * <pre>
 * Agent 호출 한 번 안에서 같은 Tool을 같은 인자로 연달아 불러 결과까지 똑같으면, 반복에 빠진 것으로 보고 끊는 포장지입니다.
 *
 * 왜 필요한가:
 * 2026-11-24 실행 9f14f909에서 sm-developer-agent가 일을 다 마친 뒤 같은 Skill을 30번 연달아 불렀습니다.
 * 호출 횟수 한도(120번)에는 걸리지 않았고, 그동안 입력이 63k에서 117k 토큰까지 늘다가 provider의 크레딧 부족(402)으로 끝났습니다.
 * 반복 때문에 멈췄다는 사실도 오류에 남지 않았습니다.
 *
 * 어떻게 세나:
 *   같은 Tool + 같은 인자 + 같은 결과        연달아 몇 번째인지 셉니다
 *   인자나 결과가 하나라도 다름               처음부터 다시 셉니다(결과가 바뀌었으면 무언가 진행된 것입니다)
 *   다른 Tool을 사이에 부름                   처음부터 다시 셉니다(고치고 다시 컴파일하는 것은 반복이 아닙니다)
 *   기다리는 용도의 Tool(@RepeatableTool, MCP의 repeatableTools)   세지 않고, 셈도 처음부터 다시 합니다
 *
 *   noticeAt번째(기본 3)부터     결과를 다시 싣지 않고 "같은 결과가 이미 위에 있다"는 안내를 줍니다
 *   failAt번째(기본 5)           AgentContractException을 던져 이 Agent 호출을 "반복 호출"로 끝냅니다
 *
 * Tool은 언제나 실제로 실행합니다. 결과가 바뀌었는지는 실행해 봐야 알 수 있기 때문입니다.
 * 안쪽 포장지(RepeatFailureGuardCallback, RereadGuardCallback, SkillTool)가 돌려준 같은 안내가 되풀이되는 것도 똑같이 셉니다.
 *
 * 이 클래스는 Spring 빈이 아닙니다. Agent를 부를 때마다 AgentExecutor가 새로 만들고, 같은 호출의 Tool들이 Tracker 하나를 함께 씁니다.
 * </pre>
 */
public class RepeatCallGuardCallback implements ToolCallback {

	/**
	 * <pre>
	 * 한 Agent 호출 안에서 "바로 앞의 호출"과 그것이 연달아 몇 번째인지 들고 있는 장부입니다.
	 * 그 호출의 Tool들이 함께 씁니다.
	 * </pre>
	 */
	public static class Tracker {

		private String lastKey;
		private String lastResult;
		private int count;

		/** 이번 호출을 적고, 같은 호출·같은 결과가 연달아 몇 번째인지 돌려줍니다. */
		synchronized int record(String key, String result) {
			if (key.equals(this.lastKey) && result.equals(this.lastResult)) {
				this.count++;
			} else {
				this.lastKey = key;
				this.lastResult = result;
				this.count = 1;
			}
			return this.count;
		}

		/**
		 * <pre>
		 * 이 호출이 "바로 앞 호출과 같은 Tool, 같은 인자"일 때, 그 호출이 같은 결과로 연달아 몇 번 끝났는지 돌려줍니다. 아니면 0입니다.
		 * 모델이 응답으로 요청한 Tool 호출을 아직 실행하기 전에, 그것이 또 같은 호출인지 미리 볼 때 씁니다(RedundantSkillCallAdvisor).
		 * </pre>
		 *
		 * @param toolName  Tool 이름
		 * @param arguments 모델이 준 인자(JSON 글자)
		 */
		public synchronized int identicalRuns(String toolName, String arguments) {
			return keyOf(toolName, arguments).equals(this.lastKey) ? this.count : 0;
		}

		/** 셈을 처음부터 다시 합니다. 세지 않는 Tool을 불렀을 때 씁니다. */
		synchronized void reset() {
			this.lastKey = null;
			this.lastResult = null;
			this.count = 0;
		}
	}

	private final ToolCallback delegate;
	private final Tracker tracker;
	private final String agentId;
	private final boolean exempt;
	private final int noticeAt;
	private final int failAt;
	private Set<String> succeededTools;
	private Set<String> writtenPaths;

	/**
	 * @param delegate 실제 Tool 호출을 맡는 ToolCallback
	 * @param tracker  같은 Agent 호출의 Tool들이 함께 쓰는 장부
	 * @param agentId  이 Tool을 쓰는 Agent의 id(로그와 오류 문구용)
	 * @param exempt   true면 세지 않습니다(기다리는 용도의 Tool, Sub Agent). 부르면 셈을 처음부터 다시 합니다
	 * @param noticeAt 몇 번째부터 결과 대신 안내를 줄지. 0이면 안내하지 않습니다
	 * @param failAt   몇 번째에 중단할지. 0이면 중단하지 않습니다
	 */
	public RepeatCallGuardCallback(ToolCallback delegate, Tracker tracker, String agentId, boolean exempt, int noticeAt, int failAt) {
		this.delegate = delegate;
		this.tracker = tracker;
		this.agentId = agentId;
		this.exempt = exempt;
		this.noticeAt = noticeAt;
		this.failAt = failAt;
	}

	/**
	 * 중단할 때 오류 문구에 "그때까지 한 일"을 붙이려고, 이 호출의 진행 기록을 알려 줍니다(없으면 null).
	 * 일은 다 해 놓고 마무리만 못 한 경우와 아무것도 못 한 경우를 오류만 보고 가르기 위한 것입니다.
	 *
	 * @param succeededTools 성공한 Tool 이름들
	 * @param writtenPaths   성공한 Tool이 쓴 파일 경로들
	 */
	public RepeatCallGuardCallback progress(Set<String> succeededTools, Set<String> writtenPaths) {
		this.succeededTools = succeededTools;
		this.writtenPaths = writtenPaths;
		return this;
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
		return this.checked(toolInput, this.delegate.call(toolInput));
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		return this.checked(toolInput, this.delegate.call(toolInput, toolContext));
	}

	/**
	 * 실행한 결과를 장부에 적고, 반복이면 안내로 바꾸거나 중단합니다. 반복이 아니면 결과를 그대로 돌려줍니다.
	 *
	 * @throws AgentContractException 같은 호출·같은 결과가 failAt번 연달아 나왔을 때
	 */
	private String checked(String toolInput, String result) {
		if (this.exempt) {
			this.tracker.reset();
			return result;
		}
		String name = this.delegate.getToolDefinition().name();
		String key = keyOf(name, toolInput);
		int count = this.tracker.record(key, result == null ? "" : result);
		if (this.failAt > 0 && count >= this.failAt) {
			throw new AgentContractException("반복 호출: agent[" + this.agentId + "]가 [" + name + "]를 같은 인자로 " + count
				+ "번 연달아 불렀고 결과도 매번 같아서 중단했습니다. 인자: " + abbreviate(toolInput) + this.progressText());
		}
		if (this.noticeAt > 0 && count >= this.noticeAt) {
			LogUtil.sysout("dstone-ai-engine tool: agent[" + this.agentId + "]가 [" + name + "]를 같은 인자로 " + count
				+ "번 연달아 불렀고 결과도 같아서 결과 대신 안내를 줬습니다.");
			return notice(name, count, this.failAt);
		}
		return result;
	}

	/** 중단 오류에 덧붙일, 그때까지 한 일입니다. 알려 준 기록이 없으면 빈 글자입니다. */
	private String progressText() {
		StringBuilder text = new StringBuilder();
		try {
			if (this.succeededTools != null) {
				text.append(" / 성공한 Tool: ").append(this.succeededTools.isEmpty() ? "없음" : new ArrayList<String>(this.succeededTools));
			}
			if (this.writtenPaths != null) {
				List<String> paths = new ArrayList<String>(this.writtenPaths);
				text.append(" / 쓴 파일 ").append(paths.size()).append("개").append(paths.isEmpty() ? "" : ": " + paths);
			}
		} catch (RuntimeException e) {
			// 다른 스레드가 기록을 바꾸는 중이면 덧붙이지 않습니다. 오류 원인이 가려지면 안 됩니다.
			return "";
		}
		return text.toString();
	}

	/** 장부에 적는 "같은 호출" 키입니다. Tool 이름과 인자(앞뒤 공백 뺀 것)가 같으면 같은 호출입니다. */
	static String keyOf(String toolName, String toolInput) {
		return toolName + "\u0000" + (toolInput == null ? "" : toolInput.strip());
	}

	/** 결과 대신 돌려주는 안내입니다. */
	private static String notice(String name, int count, int failAt) {
		StringBuilder text = new StringBuilder();
		text.append("(안내: ").append(name).append("를 같은 인자로 ").append(count).append("번 연달아 불렀고 결과도 앞과 똑같아서 다시 싣지 않았습니다. ")
			.append("결과는 위 대화에 그대로 있습니다. 같은 호출은 그만하고 다음 일로 넘어가십시오. 할 일을 다 했으면 최종 답을 내십시오.");
		if (failAt > 0) {
			text.append(" 같은 호출을 ").append(failAt - count).append("번 더 하면 이 작업은 '반복 호출'로 중단됩니다.");
		}
		return text.append(")").toString();
	}

	/** 오류 문구에 넣을 인자를 짧게 줄입니다. */
	private static String abbreviate(String toolInput) {
		if (toolInput == null) {
			return "";
		}
		String trimmed = toolInput.strip();
		return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
	}

}

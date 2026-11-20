package net.dstone.ai.runtime.agent;

import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import net.dstone.ai.runtime.workflow.file.FileAccessRule;
import net.dstone.common.utils.LogUtil;

/**
 * <pre>
 * Agent 호출 한 번 안에서, 똑같은 Tool을 똑같은 인자로 다시 불러 또 실패할 호출을 막는 포장지입니다.
 *
 * 왜 필요한가:
 * 2026-11-20 평가에서 문서 수정 Agent가 editFileTexts를 같은 인자로 31번 불렀습니다. Tool은 매번 "그 글자는 파일에 없다,
 * 이미 끝난 변경이면 넘어가라"고 정확히 알려 줬지만 모델은 같은 묶음을 그대로 다시 보냈고, 그동안 LLM 호출과 토큰만 늘었습니다.
 * 인자가 같으면 결과도 같으므로, 두 번째부터는 Tool을 실행하지 않고 그 사실과 지난 실패 사유를 바로 돌려줍니다.
 *
 * 언제 잊나:
 * 어떤 Tool이든 파일 쓰기에 성공하면(FileAccessRule의 WRITE) 기억을 모두 비웁니다. 파일이 바뀌었으면 같은 인자도 결과가 달라질 수 있어서입니다.
 * 막은 호출도 Tool 호출 횟수(ToolCallBudgetCallback)에 한 번으로 셉니다. 그래야 끝없이 되풀이하는 모델이 한도에서 멈춥니다.
 *
 * 이 클래스는 Spring 빈이 아닙니다. Agent를 부를 때마다 AgentExecutor가 새로 만들고, 같은 호출의 Tool들이 failures 하나를 함께 씁니다.
 * </pre>
 */
public class RepeatFailureGuardCallback implements ToolCallback {

	/** 지난 실패 사유를 안내에 붙일 때의 최대 글자 수입니다. */
	private static final int REASON_MAX_CHARS = 400;

	private final ToolCallback delegate;
	private final Map<String, String> failures;
	private final String agentId;

	/**
	 * @param delegate 실제 Tool 호출을 맡는 ToolCallback입니다.
	 * @param failures "Tool 이름 + 인자" → 실패 결과입니다. 같은 Agent 호출에 붙는 Tool들이 함께 씁니다(동시에 쓸 수 있는 맵).
	 * @param agentId  이 Tool을 쓰는 Agent의 id입니다(로그용).
	 */
	public RepeatFailureGuardCallback(ToolCallback delegate, Map<String, String> failures, String agentId) {
		this.delegate = delegate;
		this.failures = failures;
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
		String refused = this.refusedIfRepeated(toolInput);
		return refused != null ? refused : this.remember(toolInput, this.delegate.call(toolInput));
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		String refused = this.refusedIfRepeated(toolInput);
		return refused != null ? refused : this.remember(toolInput, this.delegate.call(toolInput, toolContext));
	}

	/** 같은 인자로 이미 실패했으면 안내를 돌려줍니다. 처음이면 null입니다. */
	private String refusedIfRepeated(String toolInput) {
		String previous = this.failures.get(this.keyOf(toolInput));
		if (previous == null) {
			return null;
		}
		String name = this.delegate.getToolDefinition().name();
		LogUtil.sysout("dstone-ai-engine tool: agent[" + this.agentId + "]가 같은 인자로 이미 실패한 [" + name + "]를 다시 불러서 실행하지 않았습니다.");
		String reason = previous.length() > REASON_MAX_CHARS ? previous.substring(0, REASON_MAX_CHARS) + "..." : previous;
		return "실패: 같은 인자로 이미 실패한 " + name + " 호출입니다. 인자를 바꾸지 않으면 결과도 같아서 실행하지 않았습니다. "
			+ "지난 실패 사유를 보고 인자를 고치거나(파일에 지금 있는 글자로), 이미 끝난 일이면 그 부분을 빼고 다음 일로 넘어가십시오.\n지난 실패: " + reason;
	}

	/** 실패면 기억해 두고, 파일 쓰기에 성공했으면 기억을 비웁니다. 결과는 그대로 돌려줍니다. */
	private String remember(String toolInput, String result) {
		if (FileAccessRule.isFailure(result)) {
			this.failures.put(this.keyOf(toolInput), result);
			return result;
		}
		for (FileAccessRule.FileAccess access : FileAccessRule.of(this.delegate.getToolDefinition().name(), toolInput)) {
			if (FileAccessRule.WRITE.equals(access.action())) {
				this.failures.clear();
				break;
			}
		}
		return result;
	}

	private String keyOf(String toolInput) {
		return this.delegate.getToolDefinition().name() + "\u0000" + (toolInput == null ? "" : toolInput.strip());
	}

}

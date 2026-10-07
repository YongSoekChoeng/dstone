package net.dstone.ai.runtime.agent;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import net.dstone.ai.common.exception.AgentContractException;
import net.dstone.common.utils.LogUtil;

/**
 * <pre>
 * Agent 호출 한 번 안에서 Tool을 부를 수 있는 횟수를 세고, 다 쓰면 막는 포장지입니다.
 *
 * 왜 엔진이 세는가:
 * prompt에 "조회는 25번 이내로 끝내라"고 적어도 모델이 지키지 않을 때가 있습니다
 * (실제로 searchInFiles를 40번 되풀이하거나, 영향도 분석 한 번에 Tool을 50번 부른 적이 있습니다).
 * Tool 결과는 대화에 쌓여서 그 뒤의 LLM 호출마다 다시 보내지므로, 횟수가 늘면 시간과 비용이 함께 늡니다.
 *
 * 동작:
 *   남은 횟수가 넉넉할 때     Tool을 그대로 부릅니다.
 *   남은 횟수가 얼마 없을 때   Tool을 부르고, 결과 끝에 "앞으로 N번 남았다"는 안내를 붙입니다.
 *   횟수를 다 썼을 때         Tool을 부르지 않고 "실패: ..." 안내를 돌려줍니다.
 *   그래도 계속 부를 때        안내를 3번 돌려준 뒤에는 예외(AgentContractException)를 던져 그 Agent 호출을 실패로 끝냅니다.
 *
 * 안내를 미리 붙이는 이유: 한도에서 갑자기 막으면 모델이 결과 파일을 저장(writeFile)하지 못하고 끝납니다.
 * 몇 번 남았는지 알려 주면 조사를 멈추고 저장부터 합니다.
 *
 * 끝내 예외를 던지는 이유: 안내만 돌려주면 말을 듣지 않는 모델은 실행되지도 않는 Tool을 끝없이 부릅니다.
 * 부를 때마다 LLM 호출이 한 번씩 나가므로 시간만 씁니다. Workflow에서는 step 실패가 되어 onFailure로 갑니다.
 * Spring AI에도 자체 한도(spring.ai.tools.limits)가 있지만 Agent마다 다르게 줄 수 없어서, 그쪽은 넉넉히 두고 여기서 먼저 막습니다.
 *
 * 이 클래스는 Spring 빈이 아닙니다. Agent를 부를 때마다 AgentExecutor가 새로 만듭니다(횟수가 호출마다 따로이기 때문입니다).
 * 같은 Agent 호출에 붙는 Tool들은 used 하나를 함께 씁니다. Sub Agent 호출은 세지 않습니다(그쪽은 자기 한도가 따로 있습니다).
 * </pre>
 */
public class ToolCallBudgetCallback implements ToolCallback {

	/** 한도를 넘긴 호출에 "실패" 안내를 돌려주는 횟수입니다. 이만큼 알려 줘도 계속 부르면 예외를 던집니다. */
	static final int MAX_REFUSALS = 3;

	private final ToolCallback delegate;
	private final String agentId;
	private final AtomicInteger used;
	private final int maxCalls;

	/**
	 * @param delegate 실제 Tool 호출을 맡는 원래 ToolCallback입니다.
	 * @param agentId  이 Tool을 쓰는 Agent의 id입니다(로그용).
	 * @param used     지금까지 부른 횟수입니다. 같은 Agent 호출에 붙는 Tool들이 함께 씁니다.
	 * @param maxCalls 부를 수 있는 최대 횟수입니다.
	 */
	public ToolCallBudgetCallback(ToolCallback delegate, String agentId, AtomicInteger used, int maxCalls) {
		this.delegate = delegate;
		this.agentId = agentId;
		this.used = used;
		this.maxCalls = maxCalls;
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
		int count = this.used.incrementAndGet();
		if (count > this.maxCalls) {
			return this.refused(count);
		}
		return this.withNotice(this.delegate.call(toolInput), count);
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		int count = this.used.incrementAndGet();
		if (count > this.maxCalls) {
			return this.refused(count);
		}
		return this.withNotice(this.delegate.call(toolInput, toolContext), count);
	}

	/**
	 * 횟수를 다 쓴 뒤의 호출에 돌려주는 안내입니다. Tool은 부르지 않습니다.
	 *
	 * @throws AgentContractException 안내를 MAX_REFUSALS번 돌려준 뒤에도 또 불렀을 때
	 */
	private String refused(int count) {
		if (count > this.maxCalls + MAX_REFUSALS) {
			throw new AgentContractException("agent[" + this.agentId + "]가 Tool 호출 한도(" + this.maxCalls + "번)를 다 쓴 뒤에도 Tool을 계속 불러서 중단했습니다. "
				+ "같은 조회를 되풀이하지 않도록 prompt를 고치거나, 정말 많이 불러야 하는 일이면 Agent의 maxToolCalls를 올리십시오.");
		}
		LogUtil.sysout("dstone-ai-engine tool: agent[" + this.agentId + "]가 Tool 호출 한도(" + this.maxCalls + "번)를 넘겨 ["
			+ this.delegate.getToolDefinition().name() + "]를 실행하지 않았습니다(" + count + "번째 호출).");
		return "실패: 이번 작업에서 쓸 수 있는 Tool 호출 횟수(" + this.maxCalls + "번)를 다 썼습니다. 이 Tool은 실행되지 않았습니다. "
			+ "Tool을 더 부르지 마십시오. 지금까지 알아낸 것으로 답을 마무리하고, 확인하지 못한 것은 확인하지 못했다고 적으십시오.";
	}

	/** 남은 횟수가 얼마 없으면 결과 끝에 안내를 붙입니다. */
	private String withNotice(String result, int count) {
		int remaining = this.maxCalls - count;
		if (remaining > noticeFrom(this.maxCalls)) {
			return result;
		}
		String notice;
		if (remaining == 0) {
			notice = "(안내: Tool 호출 횟수 " + this.maxCalls + "번을 모두 썼습니다. 이제 Tool을 부를 수 없습니다. 지금까지 알아낸 것으로 답을 마무리하십시오.)";
		} else {
			notice = "(안내: Tool을 앞으로 " + remaining + "번만 더 부를 수 있습니다(한도 " + this.maxCalls + "번). 조사를 멈추고, 저장할 결과가 있으면 지금 저장하십시오.)";
		}
		return (result == null ? "" : result) + "\n\n" + notice;
	}

	/**
	 * 남은 횟수가 이 값 이하가 되면 안내를 붙이기 시작합니다. 한도의 5분의 1이고, 적어도 2번입니다.
	 * (한도 40번이면 8번 남았을 때부터, 한도 5번이면 2번 남았을 때부터)
	 */
	static int noticeFrom(int maxCalls) {
		return Math.max(2, maxCalls / 5);
	}

}

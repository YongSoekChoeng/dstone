package net.dstone.ai.runtime.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 엔진에 등록된 Tool이 실제로 실행될 때 모두 지나가는 문입니다.
 *
 * Tool이 불리는 길은 두 가지입니다.
 * - TOOL step: ToolExecutor가 이름으로 찾아서 직접 부릅니다.
 * - AGENT step과 /chat: LLM이 고르면 Spring AI가 ToolCallback을 바로 부릅니다.
 * 두 길 모두 ConfigTool.LimitedToolCallback을 거치고, LimitedToolCallback은 이 bean을 거쳐 Tool을 실행합니다.
 * 로컬 @AiTool, MCP Tool, Skill Tool, Sub Agent도 모두 같습니다.
 *
 * LimitedToolCallback은 Spring bean이 아니라서 AOP가 걸리지 않습니다.
 * 그래서 실행 자체를 이 bean으로 옮겨 두고, 호출 로그(ConfigCallLog.TOOL_POINTCUT)는 이 클래스에 겁니다.
 * 나중에 Tool 호출을 감사하거나 막는 기능을 넣을 때도 이곳이 자리입니다.
 *
 * 주의: 이 클래스는 ConfigTool에 의존하면 안 됩니다. ConfigTool이 이 bean을 주입받기 때문에 순환 참조가 생깁니다.
 * 예산 초과·같은 파일 다시 읽기·모르는 Tool처럼 앞 단계에서 막힌 호출은 Tool을 실행하지 않으므로 여기까지 오지 않습니다.
 * </pre>
 */
@Component
public class ToolCallGate extends BaseObject {

	/**
	 * <pre>
	 * Tool 하나를 실행하고 원래 결과를 그대로 돌려줍니다. 결과 자르기는 부른 쪽(LimitedToolCallback)이 합니다.
	 * </pre>
	 *
	 * @param toolName    실행할 Tool 이름입니다(로그에 씁니다).
	 * @param toolInput   Tool에 넘길 인자 JSON입니다.
	 * @param toolContext caller·실행 id·step id를 담은 ToolContext입니다. 없으면 null입니다.
	 * @param delegate    실제로 Tool을 실행할 ToolCallback입니다.
	 * @return Tool이 돌려준 원래 결과
	 */
	public String invoke(String toolName, String toolInput, ToolContext toolContext, ToolCallback delegate) {
		if (toolContext == null) {
			return delegate.call(toolInput);
		}
		return delegate.call(toolInput, toolContext);
	}
}

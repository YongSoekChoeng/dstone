package net.dstone.ai.runtime.tool;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;

import net.dstone.ai.common.consts.Constants;

/**
 * <pre>
 * "어느 Workflow 실행의 어느 step이 부른 것인지"를 담는 값입니다.
 *
 * step 실행기가 Agent나 Tool을 부를 때 함께 넘기면, 이 값이 ToolContext에 실려 Tool 호출까지 따라갑니다.
 * Sub Agent의 Tool 호출도 부모와 같은 값을 씁니다. 그래서 파일 Tool이 어떤 파일을 읽고 썼는지
 * step별로 기록할 수 있습니다(runtime.workflow.file.WorkFlowFileRecorder).
 *
 * 채팅 API처럼 Workflow 밖에서 부를 때는 null을 넘깁니다. 그때는 기록하지 않습니다.
 * </pre>
 *
 * @param executionId Workflow 실행 id
 * @param stepId      step id
 */
public record StepRef(String executionId, String stepId) {

	/**
	 * <pre>
	 * Tool에 넘길 ToolContext 값을 만듭니다.
	 * caller 키는 값이 없어도 늘 넣습니다. Spring AI는 ToolContext를 받는 @Tool 메서드에 빈 맵을 넘기면 오류를 내기 때문입니다.
	 * </pre>
	 *
	 * @param caller  이 호출을 보낸 앱/서비스의 식별자. 없으면 빈 글자로 넣습니다
	 * @param stepRef 부른 step. Workflow 밖이면 null
	 */
	public static Map<String, Object> toolContextOf(String caller, StepRef stepRef) {
		Map<String, Object> context = new LinkedHashMap<String, Object>();
		context.put(Constants.Security.Caller.ADVISOR_CONTEXT_KEY, caller == null ? "" : caller);
		if (stepRef != null) {
			context.put(Constants.Tool.CONTEXT_EXECUTION_ID, stepRef.executionId());
			context.put(Constants.Tool.CONTEXT_STEP_ID, stepRef.stepId());
		}
		return context;
	}

	/**
	 * ToolContext에 실린 값을 다시 꺼냅니다. Workflow 밖에서 부른 호출이면 null입니다.
	 *
	 * @param toolContext Tool이 받은 ToolContext
	 */
	public static StepRef from(ToolContext toolContext) {
		if (toolContext == null || toolContext.getContext() == null) {
			return null;
		}
		Object executionId = toolContext.getContext().get(Constants.Tool.CONTEXT_EXECUTION_ID);
		Object stepId = toolContext.getContext().get(Constants.Tool.CONTEXT_STEP_ID);
		if (executionId == null || stepId == null) {
			return null;
		}
		return new StepRef(executionId.toString(), stepId.toString());
	}

}

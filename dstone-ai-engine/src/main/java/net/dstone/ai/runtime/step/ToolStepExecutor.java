package net.dstone.ai.runtime.step;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.workflow.step.ToolStepDefinition;
import net.dstone.ai.runtime.tool.ToolExecutor;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * <pre>
 * type: TOOL step(ToolStepDefinition)을 실행합니다. LLM을 거치지 않고, caller가 쓸 수 있는 Tool 하나를 코드로 직접 호출합니다.
 *
 * 1) 인자 만들기: runtime.workflow.WorkFlowExecutor가 step의 input 맵을 이미 채워서 넘겨주므로(arguments),
 *    그 맵을 JSON으로 바꾸기만 하면 Tool 인자가 됩니다.
 * 2) 호출하기: runtime.tool.ToolExecutor로 Tool을 부릅니다(caller별 Tool 화이트리스트 검사도 여기서 함께 이뤄집니다).
 * 3) output 만들기: Tool 응답이 JSON이면 그 값(객체, 배열, 숫자, true/false)을, 아니면 응답 글자를 그대로 output으로 씁니다.
 *    모양은 Tool이 정하므로 step에서 따로 선언하지 않습니다.
 * 4) 성공/실패 판정: output이 runtime.tool.ToolOutcome 모양({"success":..., "message":...})이면 success 값으로,
 *    글자면 그 글자가 "실패"(Constants.Outcome.FAIL_PREFIX)로 시작하는지로 판정합니다. 그 밖의 모양은 성공입니다.
 *    실패면 ToolOutcome의 message(없으면 응답 글자)를 실패 사유(error)로 남깁니다.
 *    onFailure로 이동한 step은 {{steps.id.error}}로 실패 이유를, {{steps.id.input.인자명}}으로 실패한 입력값을 읽을 수 있습니다.
 * </pre>
 */
@Component
public class ToolStepExecutor {

	/** 응답 전체가 JSON 하나일 때만 JSON으로 봅니다("123 개"처럼 JSON 뒤에 글자가 더 붙으면 글자로 봅니다). */
	private final ObjectMapper objectMapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	@Autowired
	private ToolExecutor toolExecutor;

	/**
	 * TOOL step 하나를 실행합니다. 인자를 JSON으로 바꿔 Tool을 부르고, 응답으로 output을 만든 뒤 성공/실패를 판정합니다
	 * (순서는 클래스 설명의 1~4 참고).
	 *
	 * @param execution 지금 진행 중인 Workflow 실행 상태입니다.
	 * @param step      실행할 step의 정의입니다.
	 * @param arguments 템플릿이 채워진 Tool 인자입니다.
	 */
	@SuppressWarnings("unchecked")
	public StepOutcome run(WorkFlowExecution execution, ToolStepDefinition step, Map<String, Object> arguments) {
		String toolResult = this.toolExecutor.call(execution.caller(), step.ref(), this.toJson(arguments));
		Object output = this.parse(toolResult);

		if (output instanceof Map && ((Map<String, Object>) output).get("success") instanceof Boolean success) {
			if (success) {
				return StepOutcome.success(output);
			}
			Object message = ((Map<String, Object>) output).get("message");
			return StepOutcome.failure(output, message == null ? toolResult : message.toString());
		}
		if (output instanceof String text && text.startsWith(Constants.Outcome.FAIL_PREFIX)) {
			return StepOutcome.failure(output, text);
		}
		return StepOutcome.success(output);
	}

	/**
	 * Tool 응답을 output 값으로 바꿉니다. JSON으로 읽히면 그 값을, 아니면(또는 JSON null이면) 응답 글자를 그대로 돌려줍니다.
	 *
	 * @param toolResult Tool 응답 원문입니다.
	 */
	private Object parse(String toolResult) {
		try {
			Object parsed = this.objectMapper.readValue(toolResult, Object.class);
			return parsed == null ? toolResult : parsed;
		} catch (JsonProcessingException e) {
			return toolResult;
		}
	}

	/**
	 * 채워진 Tool 인자 맵을 JSON 글자로 바꿉니다. 인자가 없으면 빈 객체({})입니다.
	 *
	 * @param arguments 채워진 Tool 인자입니다.
	 */
	private String toJson(Map<String, Object> arguments) {
		try {
			return this.objectMapper.writeValueAsString(arguments == null ? Map.of() : arguments);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Tool 인자를 JSON으로 바꾸지 못했습니다: " + arguments, e);
		}
	}

}

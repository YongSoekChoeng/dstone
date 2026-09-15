package net.dstone.ai.runtime.step;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.runtime.StepOutcome;
import net.dstone.ai.runtime.tool.ToolExecutor;

/**
 * TOOL step - LLM 없이 caller가 쓸 수 있는 Tool 하나를 직접 호출한다(예: SqlSyntaxTools.validateSqlSyntax로
 * 결정적 검증). 성공하면 Tool의 응답 문구가 아니라 검증받은 원본 값(input)을 그대로 다음 step에 넘긴다 -
 * "통과했다"는 메시지 자체는 다음 step에 새로운 정보가 아니기 때문이다. 실패하면 원본 값 뒤에 Tool이
 * 알려준 이유를 덧붙인다 - onFailure로 되돌아간 step이 "무엇을, 왜 고쳐야 하는지" 둘 다 볼 수 있어야 한다.
 */
@Component
public class ToolStepRunner {

	private static final String FAILURE_PREFIX = "실패";

	@Autowired
	private ToolExecutor toolExecutor;

	public StepOutcome run(StepDefinition step, String caller, Map<String, Object> variables, String input) {
		String jsonInput = this.renderToolInput(step.inputTemplate(), input, variables);
		String toolResult = this.toolExecutor.call(caller, step.ref(), jsonInput);
		if (toolResult.startsWith(FAILURE_PREFIX)) {
			return new StepOutcome(false, input + "\n\n[검증 결과] " + toolResult);
		}
		return new StepOutcome(true, input);
	}

	/** {previous}/{변수명} 토큰을 실제 값으로 바꿔 Tool 호출용 JSON 인자를 만든다 - 별도 템플릿 엔진 없이 단순 치환이면 충분하다. */
	private String renderToolInput(String inputTemplate, String input, Map<String, Object> variables) {
		String rendered = inputTemplate.replace("{previous}", this.jsonEscape(input));
		if (variables != null) {
			for (Map.Entry<String, Object> entry : variables.entrySet()) {
				rendered = rendered.replace("{" + entry.getKey() + "}", this.jsonEscape(String.valueOf(entry.getValue())));
			}
		}
		return rendered;
	}

	private String jsonEscape(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

}

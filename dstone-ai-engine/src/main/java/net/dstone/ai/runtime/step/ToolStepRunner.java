package net.dstone.ai.runtime.step;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.runtime.StepOutcome;
import net.dstone.ai.runtime.tool.ToolExecutor;
import net.dstone.common.utils.StringUtil;

/**
 * TOOL step - LLM 없이 caller가 쓸 수 있는 Tool 하나를 직접 호출한다(예: SqlSyntaxTools.validateSqlSyntax로 결정적 검증). 성공하면 Tool의 응답 문구가
 * 아니라 검증받은 원본 값(input)을 그대로 다음 step에 넘긴다 - "통과했다"는 메시지 자체는 다음 step에 새로운 정보가 아니기 때문이다. 실패하면 원본 값 뒤에 Tool이 알려준 이유를 덧붙인다 -
 * onFailure로 되돌아간 step이 "무엇을, 왜 고쳐야 하는지" 둘 다 볼 수 있어야 한다.
 */
@Component
public class ToolStepRunner {

	@Autowired
	private ToolExecutor toolExecutor;

	/**
	 * @param step      실행할 TOOL step 정의
	 * @param caller    호출 주체 식별자(tenant)
	 * @param variables Workflow 호출 시 넘겨받은 변수 맵
	 * @param input     이전 step 결과(또는 최초 입력) 텍스트
	 */
	public StepOutcome run(StepDefinition step, String caller, Map<String, Object> variables, String input) {
		String jsonInput = this.renderToolInput(step.inputTemplate(), input, variables);
		String toolResult = this.toolExecutor.call(caller, step.ref(), jsonInput);
		if (toolResult.startsWith(Constants.Outcome.FAIL_PREFIX)) {
			return new StepOutcome(false, input + "\n\n[검증 결과] " + toolResult);
		}
		return new StepOutcome(true, input);
	}

	/**
	 * <pre>
	 * {previous}/{변수명} 토큰을 실제 값으로 바꿔 Tool 호출용 JSON 인자를 만든다 - 별도 템플릿 엔진 없이 단순 치환이면 충분하다.
	 * </pre>
	 *
	 * @param inputTemplate {previous}/{변수명} 토큰을 담고 있는 입력 템플릿
	 * @param input         {previous} 토큰 자리에 채워 넣을 이전 step 결과
	 * @param variables     {변수명} 토큰 자리에 채워 넣을 변수 맵
	 */
	private String renderToolInput(String inputTemplate, String input, Map<String, Object> variables) {
		String rendered = inputTemplate.replace("{previous}", this.jsonEscape(input));
		if (variables != null) {
			for (Map.Entry<String, Object> entry : variables.entrySet()) {
				rendered = rendered.replace("{" + entry.getKey() + "}", this.jsonEscape(String.valueOf(entry.getValue())));
			}
		}
		return rendered;
	}

	/**
	 * <pre>
	 * \, ", \n만 직접 치환하면 LLM 출력에 섞인 \r(CRLF 줄바꿈) 같은 다른 제어문자가 이스케이프되지 않은
	 * 채 JSON 문자열에 그대로 남아 무효한 JSON이 된다(예: MethodToolCallback의 JSON->Map 변환 실패).
	 * Jackson의 JsonStringEncoder는 JSON 문자열 리터럴에 필요한 모든 제어문자 이스케이프를 처리해준다.
	 * </pre>
	 *
	 * @param value JSON 문자열 안에 안전하게 넣을 원본 값
	 */
	private String jsonEscape(String value) {
		return value == null ? "" : new String(JsonStringEncoder.getInstance().quoteAsString(value));
	}

}

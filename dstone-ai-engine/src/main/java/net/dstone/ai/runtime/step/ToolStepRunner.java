package net.dstone.ai.runtime.step;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.runtime.status.StepInput;
import net.dstone.ai.runtime.status.StepOutput;
import net.dstone.ai.runtime.status.ToolOutput;
import net.dstone.ai.runtime.tool.ToolExecutor;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * TOOL step - LLM 없이 caller가 쓸 수 있는 Tool 하나를 직접 호출한다(예: SqlSyntaxTool.validateSqlSyntax로 결정적 검증). 성공하면 Tool의 응답 문구가
 * 아니라 검증받은 원본 값(input)을 그대로 다음 step에 넘긴다 - "통과했다"는 메시지 자체는 다음 step에 새로운 정보가 아니기 때문이다. 실패하면 원본 값 뒤에 Tool이 알려준 이유를 덧붙인다 -
 * onFailure로 되돌아간 step이 "무엇을, 왜 고쳐야 하는지" 둘 다 볼 수 있어야 한다.
 */
@Component
public class ToolStepRunner implements StepRunner {

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * <pre>
	 * 앞 step(AGENT)이 "설명/레이블/마크다운 코드펜스를 절대 출력하지 말라"는 prompt 지시를 지키지 않고
	 * 대괄호 레이블 한 줄([변환된 SQL], [변경된 SQL] 등 - 매번 문구가 바뀐다)이나 코드펜스로 실제 값을
	 * 감싸서 반환하는 경우가 실전에서 반복적으로 관찰됐다. prompt를 아무리 일반화해도 LLM이 새 표현을
	 * 계속 만들어내므로, TOOL step은 "이전 step 출력 = 다음 step이 쓸 순수 데이터"라는 계약이 흔들리지
	 * 않도록 이 흔한 포장만 벗겨내고 방어적으로 정규화한다. Tool 자체의 검증 로직(SqlSyntaxTool 등)은
	 * 이 정규화 이후의 텍스트에 대해 그대로 엄격하게 동작하므로, 실제 문법 오류를 가려주지는 않는다.
	 * </pre>
	 */
	private static final Pattern CODE_FENCE_WRAPPER = Pattern.compile("^```[a-zA-Z]*\\s*\\n?([\\s\\S]*?)\\n?```$");
	private static final Pattern LEADING_LABEL = Pattern.compile("^\\[[^\\[\\]\\n]{1,60}\\]\\s*\\n*");

	@Autowired
	private ToolExecutor toolExecutor;

	@Override
	public StepOutput run(WorkFlowExecution execution, StepDefinition definition, StepInput input) {
		String normalized = this.stripLlmArtifacts(input.renderedText());
		String jsonInput = this.renderToolInput(definition.inputTemplate(), normalized, input.variables());
		String toolResult = this.toolExecutor.call(execution.caller(), definition.ref(), jsonInput);

		ToolOutput outcome = this.tryParseOutcome(toolResult);
		boolean failed = outcome != null ? Boolean.FALSE.equals(outcome.success()) : toolResult.startsWith(Constants.Outcome.FAIL_PREFIX);
		if (!failed) {
			return StepOutput.success(normalized);
		}
		String reasonText = outcome != null && outcome.message() != null ? outcome.message() : toolResult;
		return StepOutput.failure(normalized + "\n\n[검증 결과] " + reasonText, reasonText);
	}

	/**
	 * <pre>
	 * Tool이 runtime.status.ToolOutput(success/message)을 반환했으면(권장) 그걸 그대로 쓰고, 아니면 null을 돌려줘서
	 * run()이 문자열 접두사 컨벤션(Constants.Outcome.FAIL_PREFIX)으로 판정하게 한다. "실패로 해석되지 않는 JSON이지만
	 * 우연히 success 필드를 가진 무관한 객체"까지 성공/실패로 오판하지 않도록, success 필드가 아예 없는 경우(null)도
	 * "ToolOutcome이 아니다"로 취급한다.
	 * </pre>
	 *
	 * @param toolResult Tool 호출 원본 응답(runtime.tool.ToolExecutor.unwrap()을 거친 텍스트)
	 */
	private ToolOutput tryParseOutcome(String toolResult) {
		try {
			ToolOutput outcome = this.objectMapper.readValue(toolResult, ToolOutput.class);
			return outcome.success() == null ? null : outcome;
		} catch (JsonProcessingException e) {
			return null;
		}
	}

	/**
	 * @param text 정규화할 이전 step 출력 텍스트
	 */
	private String stripLlmArtifacts(String text) {
		if (text == null) {
			return "";
		}
		String result = text.strip();
		String previous;
		do {
			previous = result;
			Matcher fence = CODE_FENCE_WRAPPER.matcher(result);
			if (fence.matches()) {
				result = fence.group(1).strip();
			}
			Matcher label = LEADING_LABEL.matcher(result);
			if (label.lookingAt()) {
				result = result.substring(label.end()).strip();
			}
		} while (!result.equals(previous));
		return result;
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

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
import net.dstone.ai.runtime.tool.ToolExecutor;
import net.dstone.ai.runtime.tool.ToolOutcome;
import net.dstone.ai.runtime.tool.ToolPayload;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.utils.StringUtil;

/**
 * TOOL step을 처리하는 러너입니다. LLM을 거치지 않고, caller가 쓸 수 있는 Tool 하나를 코드로 직접
 * 호출합니다(예: SqlSyntaxTool.validateSqlSyntax로 SQL 문법을 결정적으로 검증하는 경우). 성공했을 때
 * 다음 step에 무엇을 넘길지는 StepDefinition.structuredOutput 값에 따라 둘로 갈립니다.
 *
 * - structuredOutput이 false(기본값)면 Tool이 돌려준 응답 문구가 아니라, 검증받은 원본 값(input)을
 *   그대로 다음 step에 넘깁니다 - "통과했다"는 메시지 자체는 다음 step 입장에서 딱히 새로운 정보가
 *   아니기 때문입니다(SqlSyntaxTool처럼 "이 값이 맞는지"만 확인하는 검증형 Tool을 염두에 둔
 *   기본값입니다).
 * - structuredOutput이 true면 반대로, Tool이 실제로 돌려준 응답을 다음 step에 넘깁니다 -
 *   list_directory/read_text_file처럼 "새 데이터를 가져오는" Tool은 그 응답 자체가 다음 step이
 *   원하는 데이터이기 때문입니다. Tool이 runtime.tool.ToolPayload(primaryText, data) 모양으로
 *   응답했다면 그 data까지 그대로 살려서 넘기고, 그게 아니라면(MCP Tool을 포함해 대부분의 Tool이
 *   이 경우입니다) 응답 텍스트 전체를 primaryText로 씁니다 - AGENT의 structuredOutput과 달리, 이
 *   모양을 지키지 않았다고 실패로 처리하지는 않습니다(자세한 이유는 runStructuredOutput() 참고).
 *
 * 실패하면(structuredOutput 값과 무관하게) 원본 값 뒤에 Tool이 알려준 실패 이유를 덧붙여서
 * 넘깁니다 - onFailure로 되돌아간 step이 "무엇을 고쳐야 하는지"와 "왜 고쳐야 하는지"를 둘 다 볼 수
 * 있어야 하기 때문입니다.
 */
@Component
public class ToolStepRunner implements StepRunner {

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 앞에 있는 AGENT step이 "설명이나 레이블, 마크다운 코드펜스를 절대 붙이지 말라"는 prompt 지시를
	 * 지키지 않고, 대괄호 레이블 한 줄([변환된 SQL], [변경된 SQL] 등 - 문구가 매번 조금씩 다르게
	 * 나옵니다)이나 코드펜스로 실제 값을 감싸서 돌려주는 경우가 실제로 계속 관찰됐습니다. prompt를
	 * 아무리 꼼꼼하게 다듬어도 LLM이 계속 새로운 표현을 만들어낼 수 있기 때문에, TOOL step에서는
	 * "이전 step의 출력은 곧 다음 step이 쓸 순수한 데이터다"라는 약속이 깨지지 않도록, 자주 나오는
	 * 이런 포장들을 여기서 미리 벗겨내며 방어적으로 정리해 둡니다. 다만 Tool 자체의 검증 로직
	 * (SqlSyntaxTool 등)은 이렇게 정리된 텍스트에 대해 여전히 엄격하게 동작하므로, 이 정리 작업이
	 * 실제 문법 오류까지 가려서 숨겨주지는 않습니다.
	 */
	private static final Pattern CODE_FENCE_WRAPPER = Pattern.compile("^```[a-zA-Z]*\\s*\\n?([\\s\\S]*?)\\n?```$");
	private static final Pattern LEADING_LABEL = Pattern.compile("^\\[[^\\[\\]\\n]{1,60}\\]\\s*\\n*");

	@Autowired
	private ToolExecutor toolExecutor;

	@Override
	public StepOutcome run(WorkFlowExecution execution, StepDefinition definition, StepInput input) {
		String normalized = this.stripLlmArtifacts(input.renderedText());
		String jsonInput = this.renderToolInput(definition.inputTemplate(), normalized, input.variables());
		String toolResult = this.toolExecutor.call(execution.caller(), definition.ref(), jsonInput);

		ToolOutcome outcome = this.tryParseOutcome(toolResult);
		boolean failed = outcome != null ? Boolean.FALSE.equals(outcome.success()) : toolResult.startsWith(Constants.Outcome.FAIL_PREFIX);
		if (failed) {
			String reasonText = outcome != null && outcome.message() != null ? outcome.message() : toolResult;
			return StepOutcome.failure(normalized + "\n\n[검증 결과] " + reasonText, reasonText);
		}
		return Boolean.TRUE.equals(definition.structuredOutput()) ? this.runStructuredOutput(toolResult) : StepOutcome.success(normalized);
	}

	/**
	 * <pre>
	 * structuredOutput=true인 TOOL step의 성공 처리입니다. Tool이 runtime.tool.ToolPayload(primaryText,
	 * data) 모양으로 응답했다면 그 값을 그대로 옮겨 담고, 그렇지 않다면 응답 텍스트 전체를 primaryText로
	 * 씁니다(data는 빈 Map).
	 *
	 * AGENT의 structuredOutput=true(runtime.step.AgentStepRunner.runStructuredAgent)는 이 모양을
	 * 지키지 않으면 실패로 처리합니다 - LLM에게 이 스키마를 지키라고 프롬프트로 직접 지시했는데
	 * 지키지 않았다면, 그 자체가 신뢰할 수 없는 응답이라는 신호이기 때문입니다(fail-closed). TOOL은
	 * 정반대로 갑니다 - 애초에 어떤 Tool에게도 "이 스키마를 지켜라"라고 요구한 적이 없고, 오히려
	 * list_directory/read_text_file처럼 이 모양을 전혀 모르는 Tool(특히 MCP Tool은 전부 여기
	 * 해당합니다)을 그대로 쓰는 것이 정상적인 다수 사례입니다. 그래서 파싱 실패를 에러가 아니라
	 * "이 Tool은 구조화된 데이터가 없다"는 흔한 정상 경우로 보고, 응답 텍스트를 그대로 살려서
	 * 성공으로 돌려줍니다(graceful fallback).
	 * </pre>
	 *
	 * @param toolResult Tool을 호출한 원본 응답 텍스트입니다(runtime.tool.ToolExecutor.call()을 거친, 이미 풀린 결과입니다).
	 */
	private StepOutcome runStructuredOutput(String toolResult) {
		ToolPayload payload = this.tryParsePayload(toolResult);
		if (payload != null && !StringUtil.isEmpty(payload.primaryText())) {
			return StepOutcome.successWithData(payload.primaryText(), payload.data());
		}
		return StepOutcome.success(toolResult);
	}

	/**
	 * Tool이 runtime.tool.ToolPayload 형식(primaryText/data 필드)으로 응답했다면 그 값을 그대로
	 * 씁니다. 그게 아니라면(대부분의 Tool이 여기 해당합니다) null을 돌려줘서, runStructuredOutput()이
	 * 대신 응답 텍스트 전체를 primaryText로 쓰게 합니다. tryParseOutcome()과 같은 이유로, primaryText
	 * 필드 자체가 아예 없는 경우(null인 경우)도 "이건 ToolPayload가 아니다"로 취급합니다.
	 *
	 * @param toolResult Tool을 호출한 원본 응답 텍스트입니다(runtime.tool.ToolExecutor.call()을 거친, 이미 풀린 결과입니다).
	 */
	private ToolPayload tryParsePayload(String toolResult) {
		try {
			return this.objectMapper.readValue(toolResult, ToolPayload.class);
		} catch (JsonProcessingException e) {
			return null;
		}
	}

	/**
	 * Tool이 runtime.tool.ToolOutcome 형식(success/message 필드)으로 응답했다면(이게 권장하는
	 * 방식입니다) 그 값을 그대로 씁니다. 그게 아니라면 null을 돌려줘서, run() 메서드가 대신 문자열
	 * 접두사 방식(Constants.Outcome.FAIL_PREFIX)으로 성공/실패를 판정하게 합니다. 이때 "success
	 * 필드가 우연히 들어있긴 하지만 사실은 전혀 다른 목적의 JSON 객체"를 잘못 성공/실패로 오해하지
	 * 않도록, success 필드 자체가 아예 없는 경우(null인 경우)도 "이건 ToolOutcome이 아니다"로
	 * 취급합니다.
	 *
	 * @param toolResult Tool을 호출한 원본 응답 텍스트입니다(runtime.tool.ToolExecutor.unwrap()을 거친 결과입니다).
	 */
	private ToolOutcome tryParseOutcome(String toolResult) {
		try {
			ToolOutcome outcome = this.objectMapper.readValue(toolResult, ToolOutcome.class);
			return outcome.success() == null ? null : outcome;
		} catch (JsonProcessingException e) {
			return null;
		}
	}

	/**
	 * 이전 step이 남긴 출력 텍스트에서 코드펜스나 대괄호 레이블 같은 불필요한 포장을 벗겨내어 깔끔한
	 * 텍스트로 정리합니다.
	 *
	 * @param text 정리할 대상인, 이전 step의 원본 출력 텍스트입니다.
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
	 * inputTemplate에 있는 {previous}나 {변수명} 같은 토큰들을 실제 값으로 바꿔서, Tool을 호출할 때
	 * 넘길 JSON 인자를 만들어 줍니다. 별도의 템플릿 엔진을 쓰지 않고 단순 문자열 치환만으로 충분합니다.
	 *
	 * @param inputTemplate {previous}나 {변수명} 같은 토큰이 들어있는 입력 템플릿입니다.
	 * @param input         {previous} 토큰 자리에 채워 넣을, 이전 step의 결과 텍스트입니다.
	 * @param variables     {변수명} 토큰 자리에 채워 넣을 변수들을 담은 맵입니다.
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
	 * \, ", \n 같은 몇 개 문자만 직접 하나씩 치환하는 방식으로는, LLM 출력에 섞여 들어올 수 있는
	 * \r(CRLF 줄바꿈) 같은 다른 제어 문자가 이스케이프되지 않은 채 그대로 남아서 잘못된 JSON이 되어
	 * 버립니다(예: MethodToolCallback이 JSON을 Map으로 변환하다가 실패하는 경우). 그래서 직접
	 * 치환하는 대신, JSON 문자열 리터럴에 필요한 모든 제어 문자 이스케이프를 알아서 처리해 주는
	 * Jackson의 JsonStringEncoder를 씁니다.
	 *
	 * @param value JSON 문자열 안에 안전하게 넣고 싶은 원본 값입니다.
	 */
	private String jsonEscape(String value) {
		return value == null ? "" : new String(JsonStringEncoder.getInstance().quoteAsString(value));
	}

}

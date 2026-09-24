package net.dstone.ai.runtime.step;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.consts.ToolParse;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.common.definition.StepOutputDefinition;
import net.dstone.ai.runtime.tool.ToolExecutor;
import net.dstone.ai.runtime.tool.ToolOutcome;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * <pre>
 * TOOL step을 처리하는 러너입니다. LLM을 거치지 않고, caller가 쓸 수 있는 Tool 하나를 코드로 직접 호출합니다.
 *
 * 1) 인자 만들기: runtime.workflow.WorkFlowExecutor가 step의 input 맵을 이미 채워서 넘겨주므로(StepInput.arguments),
 *    그 맵을 JSON으로 바꾸기만 하면 Tool 인자가 됩니다.
 * 2) 호출하기: runtime.tool.ToolExecutor로 Tool을 부릅니다(caller별 Tool 화이트리스트 검사도 여기서 함께 이뤄집니다).
 * 3) 성공/실패 판정: Tool이 runtime.tool.ToolOutcome({"success":..., "message":...})으로 답하면 success 값으로,
 *    평범한 문자열로 답하면 그 문자열이 "실패"(Constants.Outcome.FAIL_PREFIX)로 시작하는지로 판정합니다.
 * 4) 결과 남기기: 성공이든 실패든 결과 텍스트에는 Tool 응답 원문을 그대로 남깁니다.
 *    - 성공: step의 output.parse에 따라 응답을 data로 정리합니다(common.consts.ToolParse 참고).
 *    - 실패: ToolOutcome의 message(없으면 응답 원문)를 실패 사유(error)로 남깁니다.
 *      onFailure로 이동한 step은 {{steps.id.error}}로 실패 이유를, {{steps.id.input.인자명}}으로 실패한 입력값을 읽을 수 있습니다.
 * </pre>
 */
@Component
public class ToolStepRunner implements StepRunner {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private ToolExecutor toolExecutor;

	/**
	 * TOOL step 하나를 실행합니다. 인자를 JSON으로 바꿔 Tool을 부르고, 성공/실패를 판정한 뒤,
	 * 성공이면 output.parse에 따라 data까지 만들어서 돌려줍니다(순서는 클래스 설명의 1~4 참고).
	 */
	@Override
	public StepOutcome run(WorkFlowExecution execution, StepDefinition definition, StepInput input) {
		String toolResult = this.toolExecutor.call(execution.caller(), definition.ref(), this.toJson(input.arguments()));

		ToolOutcome outcome = this.tryParseOutcome(toolResult);
		boolean failed = outcome != null ? Boolean.FALSE.equals(outcome.success()) : toolResult.startsWith(Constants.Outcome.FAIL_PREFIX);
		if (failed) {
			String reason = outcome != null && outcome.message() != null ? outcome.message() : toolResult;
			return StepOutcome.failure(toolResult, reason);
		}
		return this.parse(definition.output(), toolResult);
	}

	/**
	 * <pre>
	 * 성공한 Tool 응답을 step의 output.parse에 따라 data로 정리합니다. 결과 텍스트는 항상 응답 원문입니다.
	 * - text(기본): data 없음
	 * - json : 응답 JSON 객체를 그대로 data로, JSON 배열이면 {items: [...]}로 씁니다. JSON이 아니면 실패입니다.
	 * - lines: 응답을 줄로 나눠 {lines: [...]}로 씁니다(빈 줄은 버림). pattern이 있으면 맞는 줄만 남기고,
	 *          pattern에 괄호 그룹이 있으면 첫 번째 그룹에 잡힌 부분만 씁니다.
	 * </pre>
	 *
	 * @param output     step의 output 선언입니다(없으면 null).
	 * @param toolResult Tool 응답 원문입니다.
	 */
	private StepOutcome parse(StepOutputDefinition output, String toolResult) {
		ToolParse parse = output == null || output.parse() == null ? ToolParse.TEXT : output.parse();
		return switch (parse) {
			case TEXT -> StepOutcome.success(toolResult);
			case JSON -> this.parseJson(toolResult);
			case LINES -> StepOutcome.success(toolResult, Map.of("lines", this.parseLines(toolResult, output.pattern())));
		};
	}

	/**
	 * Tool 응답을 JSON으로 읽어 data를 만듭니다. JSON 객체면 그대로, JSON 배열이면 {items: [...]}로 감쌉니다.
	 * JSON 객체나 배열이 아니면 실패로 처리합니다.
	 *
	 * @param toolResult Tool 응답 원문입니다.
	 */
	private StepOutcome parseJson(String toolResult) {
		try {
			Object parsed = this.objectMapper.readValue(toolResult, new TypeReference<Object>() {
			});
			if (parsed instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> data = (Map<String, Object>) parsed;
				return StepOutcome.success(toolResult, data);
			}
			if (parsed instanceof List) {
				return StepOutcome.success(toolResult, Map.of("items", parsed));
			}
		} catch (JsonProcessingException e) {
			// 아래에서 실패로 처리합니다.
		}
		return StepOutcome.failure(toolResult, "output.parse=json인데 Tool 응답이 JSON 객체나 배열이 아닙니다.");
	}

	/**
	 * Tool 응답을 줄 단위 리스트로 나눕니다. 빈 줄은 버리고, pattern이 있으면 맞는 줄만 남깁니다.
	 *
	 * @param toolResult Tool 응답 원문입니다.
	 * @param pattern    남길 줄을 고르는 정규식입니다(없으면 null). 괄호 그룹이 있으면 첫 번째 그룹만 씁니다.
	 */
	private List<String> parseLines(String toolResult, String pattern) {
		Pattern compiled = pattern == null ? null : Pattern.compile(pattern);
		List<String> lines = new ArrayList<>();
		for (String line : toolResult.split("\\R")) {
			String stripped = line.strip();
			if (stripped.isEmpty()) {
				continue;
			}
			if (compiled == null) {
				lines.add(stripped);
				continue;
			}
			Matcher matcher = compiled.matcher(stripped);
			if (matcher.find()) {
				lines.add(matcher.groupCount() >= 1 ? matcher.group(1) : stripped);
			}
		}
		return lines;
	}

	/**
	 * Tool이 runtime.tool.ToolOutcome 모양({"success":..., "message":...})으로 답했다면 그 값을 돌려주고,
	 * 아니면 null을 돌려줍니다. 우연히 다른 목적의 JSON을 ToolOutcome으로 오해하지 않도록, success 필드가
	 * 없는 경우도 ToolOutcome이 아닌 것으로 봅니다.
	 *
	 * @param toolResult Tool 응답 원문입니다.
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

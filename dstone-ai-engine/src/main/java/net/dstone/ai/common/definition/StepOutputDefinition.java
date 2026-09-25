package net.dstone.ai.common.definition;

import java.util.Map;

import net.dstone.ai.common.consts.ToolParse;

/**
 * step 하나가 "자기 결과를 어떤 모양으로 내놓을지" 선언합니다. YAML의 step 아래 output: 자리에 적습니다.
 * 결과를 정리하는 책임은 결과를 만드는 step 자신에게 있습니다. 그래서 다음 step은 받은 값을 따로
 * 다듬을 필요 없이 {{steps.id.output.키}}로 바로 꺼내 쓰면 됩니다.
 *
 * step 종류에 따라 쓸 수 있는 필드가 다릅니다(잘못 섞어 쓰면 엔진이 켜질 때 막힙니다).
 * <pre>
 * - AGENT : schema만 씁니다. LLM이 이 모양의 JSON으로 답하도록 강제하고, 그 JSON이 output이 됩니다.
 *           모양을 지키지 않은 답이 오면 step이 실패합니다.
 *           output:
 *             schema:
 *               sql: string
 *               tables: list<string>
 * - TOOL  : parse(+ pattern)만 씁니다. Tool 응답 텍스트를 output으로 정리하는 방법입니다(common.consts.ToolParse 참고).
 *           output:
 *             parse: lines
 *             pattern: '^\[FILE\] (.+)$'
 * - SUPERVISOR / ROUTER / APPROVAL : output을 쓰지 않습니다. output 모양이 정해져 있습니다
 *           (SUPERVISOR {pass, reason}, ROUTER {route, reason}, APPROVAL {approved, approver, comment}).
 * </pre>
 *
 * @param schema  AGENT 전용. 필드 이름 → 필드 모양입니다. 선언한 필드는 모두 필수입니다.
 * @param parse   TOOL 전용. Tool 응답을 output으로 정리하는 방법입니다. 비워두면 TEXT(output 없음)입니다.
 * @param pattern TOOL + parse: lines 전용. 이 정규식에 맞는 줄만 남깁니다(괄호 그룹이 있으면 첫 번째 그룹만 씁니다).
 */
public record StepOutputDefinition(
	Map<String, FieldDefinition> schema
	, ToolParse parse
	, String pattern
	) {
}

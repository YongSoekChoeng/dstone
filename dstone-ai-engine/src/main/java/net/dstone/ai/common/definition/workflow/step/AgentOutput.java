package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

import net.dstone.ai.common.definition.FieldDefinition;

/**
 * <pre>
 * AGENT step이 "자기 결과를 어떤 모양으로 내놓을지" 선언합니다. 
 * YAML의 AGENT step 아래 output: 자리에 적습니다.
 * 결과를 정리하는 책임은 결과를 만드는 step 자신에게 있습니다. 그래서 다음 step은 받은 값을 따로 다듬을 필요 없이 {{steps.id.output.키}}로 바로 꺼내 쓰면 됩니다.
 *
 * schema를 적으면 LLM이 이 모양의 JSON으로 답하도록 강제하고, 그 JSON이 output이 됩니다.
 * 모양을 지키지 않은 답이 오면 step이 실패합니다(runtime.agent.SchemaOutputConverter 참고).
 *
 *   output:
 *     schema:
 *       sql: string
 *       tables: list<string>
 * </pre>
 *
 * @param schema (필수)필드 이름 → 필드 모양입니다. 선언한 필드는 모두 필수이고, 최소 1개 있어야 합니다.
 */
public record AgentOutput(
	Map<String, FieldDefinition> schema
	) {
}

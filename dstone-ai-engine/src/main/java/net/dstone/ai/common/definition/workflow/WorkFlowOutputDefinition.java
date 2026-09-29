package net.dstone.ai.common.definition.workflow;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.dstone.ai.common.schema.JsonSchemaUtil;

/**
 * <pre>
 * workflows/*.yml 의 workflow.output입니다. Workflow가 성공으로 끝났을 때 "무엇을 최종 결과로 돌려줄지"를 정합니다.
 *
 *   output:
 *     value: "${ .steps.convert.output }"  # (필수) 무엇을 돌려줄지. step input과 같은 "${ }" 표현식
 *     schema: { type: string }            # (옵셔널) 결과가 이 모양인지 마지막에 한 번 더 검사
 *
 * value는 문자열뿐 아니라 맵/리스트로도 적을 수 있습니다. 그러면 여러 step의 값을 모아 새 모양으로 돌려줍니다.
 *   value:
 *     sql: "${ .steps.convert.output.sql }"
 *     approvedBy: "${ .steps.review.output.approver }"
 *
 * 표현식의 결과는 타입 그대로(객체, 리스트 등) 돌려줍니다(common.schema.JqExpEvalUtil 참고).
 * schema를 적었는데 결과가 그 모양이 아니면 Workflow는 FAILED로 끝납니다.
 * </pre>
 *
 * @param value  (필수)최종 결과입니다(표현식, 리터럴, 또는 그것들을 담은 맵/리스트).
 * @param schema (옵셔널)최종 결과의 모양입니다. 비워두면 검사하지 않습니다.
 */
public record WorkFlowOutputDefinition(Object value, Map<String, Object> schema) {

	/**
	 * <pre>
	 * YAML의 output 맵을 읽을 때 쓰입니다. schema는 축약형도 받습니다(예: schema: string).
	 * </pre>
	 *
	 * @param value  최종 결과입니다.
	 * @param schema 최종 결과의 모양입니다(없으면 null).
	 */
	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public static WorkFlowOutputDefinition of(@JsonProperty("value") Object value, @JsonProperty("schema") Object schema) {
		return new WorkFlowOutputDefinition(value, schema == null ? null : JsonSchemaUtil.normalize(schema));
	}

}

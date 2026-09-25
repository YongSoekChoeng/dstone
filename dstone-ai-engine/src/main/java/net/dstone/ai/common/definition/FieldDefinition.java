package net.dstone.ai.common.definition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * <pre>
 * 값 하나의 모양(타입)을 선언합니다. 두 곳에서 씁니다.
 * - Workflow의 inputs: 이 Workflow를 실행할 때 요청에 꼭 들어 있어야 하는 입력값
 * - AGENT step의 output.schema: LLM이 반드시 이 모양의 JSON으로 답해야 하는 필드
 *
 * YAML에는 두 가지 방법으로 적을 수 있습니다.
 *   sql: string                          # 축약형: 타입만 적습니다
 *   sql:                                 # 확장형: 설명까지 적습니다(LLM에게 그대로 전달됩니다)
 *     type: string
 *     description: 변환된 PostgreSQL SQL
 * 
 * 쓸 수 있는 타입은 string / number / integer / boolean / object / list<타입> 입니다
 * (예: list<string>, list<object>). 타입 이름이 올바른지는 엔진이 켜질 때
 * common.registry.WorkFlowRegistry가 검사합니다(검사 규칙은 common.schema.FieldTypes 참고).
 * </pre>
 * 
 * @param type        값의 타입입니다.
 * @param description 이 값이 무엇인지 설명하는 문구입니다(없어도 됩니다).
 */
public record FieldDefinition(
	String type
	, String description
	) {

	/**
	 * 확장형(type/description을 따로 적은 형태)을 읽을 때 쓰입니다.
	 *
	 * @param type        값의 타입입니다.
	 * @param description 이 값에 대한 설명입니다.
	 */
	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public FieldDefinition(@JsonProperty("type") String type, @JsonProperty("description") String description) {
		this.type = type;
		this.description = description;
	}

	/**
	 * 축약형(sql: string처럼 타입만 적은 형태)을 읽을 때 쓰입니다.
	 *
	 * @param type 값의 타입입니다.
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static FieldDefinition of(String type) {
		return new FieldDefinition(type, null);
	}

}

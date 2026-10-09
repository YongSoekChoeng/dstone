package net.dstone.ai.common.definition;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.dstone.ai.common.schema.JsonSchemaUtil;

/**
 * <pre>
 * "이 자리에 어떤 모양의 값이 오가는가"를 선언한 것입니다(입출력 계약). YAML 두 곳에서 씁니다.
 * - agents/*.yml 의 agent.input: 이 Agent가 받는 값의 모양
 * - workflows/*.yml 의 workflow.input: 이 Workflow를 실행할 때 요청에 담아야 하는 값의 모양
 *
 * schema에는 JSON Schema 파일의 경로를 적습니다(definitions 폴더 기준, schemas/ 아래).
 * 파일은 common.loader.YamlDefinitionLoader가 읽어서 맵으로 바꿔 넣어 줍니다.
 *
 *   input:
 *     schema: schemas/requirement-input.schema.json
 *
 * 타입만 필요한 간단한 경우에는 파일 없이 축약형으로 적어도 됩니다(규칙은 common.schema.JsonSchemaUtil 참고).
 *
 *   input: string                           # = {schema: {type: string}}
 *   input: list&lt;string&gt;
 *
 * 축약형은 표준 모양으로 펼쳐지므로, schema()는 항상 표준 JSON Schema 맵입니다.
 * 스키마 자체가 올바른지(예: type 오타)는 엔진이 켜질 때 레지스트리가 검사합니다.
 * </pre>
 *
 * @param schema 표준 JSON Schema 맵입니다.
 */
public record SchemaDefinition(Map<String, Object> schema) {

	/**
	 * 확장형(schema: 아래에 스키마를 적은 형태)을 읽을 때 쓰입니다.
	 *
	 * @param schema YAML에 적힌 스키마입니다(맵 또는 타입 이름).
	 */
	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public static SchemaDefinition of(@JsonProperty("schema") Object schema) {
		if (schema == null) {
			throw new IllegalArgumentException("schema가 비어 있습니다(예: schema: {type: string}).");
		}
		return new SchemaDefinition(JsonSchemaUtil.normalize(schema));
	}

	/**
	 * 축약형(output: string처럼 타입 이름만 적은 형태)을 읽을 때 쓰입니다.
	 *
	 * @param typeName 타입 이름입니다(예: string, object, list&lt;string&gt;).
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static SchemaDefinition shorthand(String typeName) {
		return new SchemaDefinition(JsonSchemaUtil.normalize(typeName));
	}

	/** 아무것도 선언하지 않았을 때의 계약({type: string})을 돌려줍니다. */
	public static SchemaDefinition string() {
		return new SchemaDefinition(JsonSchemaUtil.string());
	}

}

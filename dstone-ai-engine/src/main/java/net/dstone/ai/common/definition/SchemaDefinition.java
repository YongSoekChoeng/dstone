package net.dstone.ai.common.definition;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;

import net.dstone.ai.common.schema.JsonSchemaUtil;
import net.dstone.ai.common.schema.Picoschema;

/**
 * <pre>
 * "이 자리에 어떤 모양의 값이 오가는가"를 선언한 것입니다(입출력 계약). 세 곳에서 씁니다.
 * - agents/**.md 머리말의 input: 이 Agent가 받는 값의 모양
 * - agents/**.md 머리말의 output: 이 Agent가 돌려주는 값의 모양
 * - workflows/**.yml 의 input: 이 Workflow를 실행할 때 요청에 담아야 하는 값의 모양
 *
 * 짧은 타입 표기(Picoschema)로 그 자리에 바로 적습니다(규칙은 common.schema.Picoschema 참고).
 *
 *   input: string, 질문 한 줄
 *
 *   output:
 *     path: string, 파일의 절대경로
 *     frs?(array, FR 번호): string
 *
 * 읽을 때 표준 JSON Schema로 바뀌므로, schema()는 항상 표준 JSON Schema 맵입니다.
 * 스키마 자체가 올바른지는 엔진이 켜질 때 레지스트리가 검사합니다.
 * </pre>
 *
 * @param schema 표준 JSON Schema 맵입니다.
 */
public record SchemaDefinition(Map<String, Object> schema) {

	/**
	 * YAML에 적은 값(글자 또는 필드 맵)을 읽을 때 쓰입니다.
	 *
	 * @param raw YAML에 적힌 Picoschema 값입니다.
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static SchemaDefinition of(Object raw) {
		return new SchemaDefinition(Picoschema.compile(raw));
	}

	/** 아무것도 선언하지 않았을 때의 계약({type: string})을 돌려줍니다. */
	public static SchemaDefinition string() {
		return new SchemaDefinition(JsonSchemaUtil.string());
	}

}

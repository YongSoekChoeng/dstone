package net.dstone.ai.common.consts;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * TOOL step이 Tool의 응답 텍스트를 어떤 모양의 output으로 정리할지 정하는 값입니다.
 * YAML의 step output.parse 자리에 적습니다(대소문자 구분 없음. 예: parse: lines).
 *
 * 어떤 값을 고르든 step의 text에는 항상 Tool 응답 원문이 그대로 남습니다. 이 값은 "다음 step이
 * {{steps.id.output.키}}로 꺼내 쓸 output을 어떻게 만들지"만 정합니다.
 */
public enum ToolParse {

	/** output을 만들지 않습니다(빈 맵). 아무것도 적지 않았을 때의 기본값입니다. */
	TEXT,

	/**
	 * 응답을 JSON으로 읽어서 output으로 씁니다. JSON 객체면 그대로 output이 되고, JSON 배열이면
	 * {items: [...]} 모양으로 감싸서 output이 됩니다. JSON이 아니면 step이 실패합니다.
	 */
	JSON,

	/**
	 * 응답을 줄 단위로 나눠서 {lines: [...]} 모양의 output으로 씁니다. 빈 줄은 버립니다.
	 * output.pattern(정규식)을 함께 적으면 그 정규식에 맞는 줄만 남기고, 정규식에 괄호 그룹이 있으면
	 * 줄 전체 대신 첫 번째 그룹에 잡힌 부분만 값으로 씁니다.
	 */
	LINES;

	/**
	 * YAML에 소문자(lines)로 적어도, 대문자(LINES)로 적어도 똑같이 읽히게 해줍니다.
	 *
	 * @param value YAML에 적힌 문자열입니다.
	 */
	@JsonCreator
	public static ToolParse from(String value) {
		for (ToolParse parse : values()) {
			if (parse.name().equalsIgnoreCase(value)) {
				return parse;
			}
		}
		throw new IllegalArgumentException("output.parse에는 text/json/lines 중 하나만 쓸 수 있습니다: " + value);
	}

}

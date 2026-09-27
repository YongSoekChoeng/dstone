package net.dstone.ai.common.definition.workflow.step;

import net.dstone.ai.common.consts.ToolParse;

/**
 * <pre>
 * TOOL step이 "Tool 응답 텍스트를 어떤 모양의 output으로 정리할지" 선언합니다. YAML의 TOOL step 아래 output: 자리에 적습니다.
 * 정리 방법은 common.consts.ToolParse를 참고하세요. 결과 텍스트(text)는 정리 방법과 상관없이 항상 Tool 응답 원문입니다.
 *
 *   output:
 *     parse: lines
 *     pattern: '^\[FILE\] (.+)$'
 * </pre>
 *
 * @param parse   (옵셔널)Tool 응답을 output으로 정리하는 방법입니다. 비워두면 TEXT(output 없음)입니다.
 * @param pattern (옵셔널)parse: lines 전용. 이 정규식에 맞는 줄만 남깁니다(괄호 그룹이 있으면 첫 번째 그룹만 씁니다).
 */
public record ToolOutput(
	ToolParse parse
	, String pattern
	) {

	/** 실제로 쓸 정리 방법을 돌려줍니다. parse가 비어 있으면 TEXT입니다. */
	public ToolParse parseOrText() {
		return this.parse == null ? ToolParse.TEXT : this.parse;
	}

}

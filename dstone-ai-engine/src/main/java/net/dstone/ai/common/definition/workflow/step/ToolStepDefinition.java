package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

import net.dstone.ai.common.consts.StepType;
import net.dstone.ai.common.consts.ToolParse;

/**
 * <pre>
 * workflows/*.yml 의 type: TOOL step입니다. 등록된 Tool(@AiTool로 만든 자바 기능 또는 MCP 서버의 Tool) 하나를
 * LLM을 거치지 않고 직접 호출합니다. 값을 검증하거나 외부 시스템에서 데이터를 가져오는 것처럼 결과가 코드로
 * 정해지는 작업에 씁니다(runtime.step.ToolStepExecutor).
 *
 * input은 맵입니다. 채워진 맵이 JSON으로 바뀌어 Tool의 인자가 됩니다. 비워두면 빈 인자({})로 호출합니다.
 * 값 전체가 {{ ... }} 하나뿐이면 원래 타입(리스트, 숫자 등)을 그대로 유지해서 넘깁니다.
 *
 * output은 Tool의 답(글자)을 steps.이id.output으로 정리하는 방법입니다(common.consts.ToolParse).
 * text에는 어떤 방법이든 Tool 응답 원문이 그대로 남습니다.
 *   text(기본) : output 없음
 *   json       : 응답 JSON 객체가 그대로 output(배열이면 {items: [...]})
 *   lines      : 응답을 줄로 나눠 {lines: [...]}. pattern을 함께 적으면 맞는 줄만 남김
 *
 *   - id: list
 *     type: TOOL
 *     ref: list_directory
 *     input:
 *       path: "{{inputs.message}}"
 *     output: lines
 *     pattern: '^\[FILE\] (.+)$'          # "[FILE] "로 시작하는 줄만 남기고, 괄호 부분(파일명)만 꺼냄
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다.
 * @param ref          (필수)부를 Tool의 이름입니다(@Tool 메서드 이름 또는 MCP Tool 이름).
 * @param input        (옵셔널)Tool 인자 이름 → 값 템플릿입니다. 비워두면 빈 인자입니다.
 * @param output       (옵셔널)Tool 응답을 output으로 정리하는 방법입니다(text/json/lines, 대소문자 무관). 비워두면 text입니다.
 * @param pattern      (옵셔널)output: lines 전용. 이 정규식에 맞는 줄만 남깁니다(괄호 그룹이 있으면 첫 번째 그룹만 씁니다).
 * @param onSuccess    (옵셔널)성공했을 때 갈 step의 id(또는 "SUCCESS")입니다.
 * @param onFailure    (옵셔널)실패했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param forEach      (옵셔널)이 step을 항목마다 동시에 실행할 리스트의 참조 경로입니다.
 * @param itemVariable (옵셔널)forEach 반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"입니다.
 */
public record ToolStepDefinition(
	String id
	, String ref
	, Map<String, Object> input
	, ToolParse output
	, String pattern
	, String onSuccess
	, String onFailure
	, String forEach
	, String itemVariable
	) implements StepDefinition {

	@Override
	public StepType type() {
		return StepType.TOOL;
	}

}

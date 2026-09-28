package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/*.yml 의 type: TOOL step입니다. 등록된 Tool(@AiTool로 만든 자바 기능 또는 MCP 서버의 Tool) 하나를
 * LLM을 거치지 않고 직접 호출합니다. 값을 검증하거나 외부 시스템에서 데이터를 가져오는 것처럼 결과가 코드로
 * 정해지는 작업에 씁니다(runtime.step.ToolStepExecutor).
 *
 * 무엇을 받고 무엇을 돌려주는지는 Tool이 정합니다.
 * - input: Tool 인자 이름 → 값 템플릿인 맵입니다. 채워진 맵이 JSON으로 바뀌어 Tool 인자가 됩니다.
 *   인자 이름은 엔진이 켜질 때 Tool의 인자 스키마(@Tool 메서드 파라미터 또는 MCP Tool의 inputSchema)와 대조합니다.
 *   값 전체가 {{ ... }} 하나뿐이면 원래 타입(리스트, 숫자 등)을 그대로 유지해서 넘깁니다.
 *   Tool에 인자가 없으면 input을 비워둡니다(빈 인자 {}로 호출).
 * - output: 따로 적지 않습니다. Tool 응답이 JSON이면 객체/배열/숫자 그대로, 아니면 글자 그대로 steps.이id.output에 들어갑니다.
 *
 *   - id: tree
 *     type: TOOL
 *     ref: directory_tree
 *     input:
 *       path: "{{input}}"
 * </pre>
 * @param id           (필수)이 step의 이름입니다.
 * @param ref          (필수)부를 Tool의 이름입니다(@Tool 메서드 이름 또는 MCP Tool 이름).
 * @param input        (옵셔널)Tool 인자 이름 → 값 템플릿입니다. 비워두면 빈 인자입니다.
 * @param onSuccess    (옵셔널)성공했을 때 갈 step의 id(또는 "SUCCESS")입니다.
 * @param onFailure    (옵셔널)실패했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param forEach      (옵셔널)이 step을 항목마다 동시에 실행할 리스트의 참조 경로입니다.
 * @param itemVariable (옵셔널)forEach 반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"입니다.
 */
public record ToolStepDefinition(
	String id
	, String ref
	, Map<String, Object> input
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

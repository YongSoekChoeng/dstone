package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * type: TOOL - 등록된 Tool(@AiTool로 만든 자바 기능 또는 MCP 서버의 Tool) 하나를 LLM을 거치지 않고 직접 호출하는 step입니다.
 * 값을 검증하거나, 파일/문서/외부 시스템에서 데이터를 가져오는 것처럼 결과가 코드로 정해지는 작업에 씁니다
 * (runtime.step.ToolStepRunner 참고).
 *
 * input은 맵입니다. 채워진 맵이 JSON으로 바뀌어 Tool의 인자가 됩니다. 비워두면 빈 인자({})로 호출합니다.
 * 값 전체가 {{ ... }} 하나뿐이면 원래 타입(리스트, 숫자 등)을 그대로 유지해서 넘깁니다.
 *
 *   - id: validate
 *     type: TOOL
 *     ref: validateSqlSyntax
 *     input:
 *       sql: "{{steps.convert.output.sql}}"
 *     output:
 *       parse: json
 *     onFailure: convert
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다.
 * @param ref          (필수)부를 Tool의 이름입니다(common.config.ConfigTool에 등록된 이름).
 * @param input        (옵셔널)Tool 인자 이름 → 값 템플릿입니다. 비워두면 빈 인자입니다.
 * @param output       (옵셔널)Tool 응답을 어떤 모양의 output으로 정리할지 선언합니다(ToolOutput 참고).
 * @param onSuccess    (옵셔널)성공했을 때 갈 step의 id(또는 "SUCCESS")입니다.
 * @param onFailure    (옵셔널)실패했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param forEach      (옵셔널)반복 실행할 리스트의 참조 경로입니다(ForEachStep 참고).
 * @param itemVariable (옵셔널)반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"입니다.
 */
public record ToolStep(
	String id
	, String ref
	, Map<String, Object> input
	, ToolOutput output
	, String onSuccess
	, String onFailure
	, String forEach
	, String itemVariable
	) implements ForEachStep, PassFailStep {

	@Override
	public StepType type() {
		return StepType.TOOL;
	}

}

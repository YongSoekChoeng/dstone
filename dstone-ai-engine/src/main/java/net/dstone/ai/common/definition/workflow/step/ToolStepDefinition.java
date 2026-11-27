package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/**.yml 의 type: TOOL step입니다. 등록된 Tool(@AiTool로 만든 자바 기능 또는 MCP 서버의 Tool) 하나를
 * LLM을 거치지 않고 직접 호출합니다. 값을 검증하거나, 목록을 거르거나, 파일을 읽거나, 외부 시스템에서 데이터를 가져오는 것처럼
 * 결과가 코드로 정해지는 작업에 씁니다(runtime.step.ToolStepExecutor).
 *
 * 표현식은 값을 읽어 오기만 하므로, 계산이나 조건이 필요한 일은 YAML이 아니라 Tool(자바 코드)에 둡니다.
 *
 * 무엇을 받고 무엇을 돌려주는지는 Tool이 정합니다.
 * - with: Tool 인자 이름 → 값(표현식 또는 리터럴)인 맵입니다. 계산된 맵이 JSON으로 바뀌어 Tool 인자가 됩니다.
 *   인자 이름은 엔진이 켜질 때 Tool의 인자 스키마(@Tool 메서드 파라미터 또는 MCP Tool의 inputSchema)와 대조합니다.
 *   Tool에 인자가 없으면 with를 비워둡니다(빈 인자 {}로 호출).
 * - result: Tool 응답이 JSON이면 객체/배열/숫자 그대로, 아니면 글자 그대로입니다.
 *
 *   - id: tree
 *     type: TOOL
 *     tool: directory_tree
 *     with:
 *       path: ${input}
 *     save: tree
 *
 * ## 결과 값으로 갈 곳 고르기(routes)
 * Tool이 돌려준 값이 글자·숫자·true/false 하나면, 그 값을 글자로 바꿔 같은 routes 이름으로 갑니다. LLM도 사람도 고르지 않고
 * 코드가 정한 값으로 나뉘는 분기입니다(예: 파일이 있으면 고치러, 없으면 새로 쓰러).
 * "없음" 같은 정상 결과를 실패로 만들어 onFailure로 나누지 않게 하려는 것입니다. 실패는 진짜 오류일 때만 남습니다.
 * routes를 적으면 next는 적지 않고, forEach와 함께 쓸 수 없습니다(결과가 반복마다 하나씩이라 값 하나로 고를 수 없음).
 * 값이 routes 이름에 없으면 실패로 보고 onFailure로 갑니다.
 *
 *   - id: step07
 *     type: TOOL
 *     tool: isFileExist                  # 있으면 true, 없으면 false
 *     with:
 *       filePath: ${input}/02.impact.md
 *     routes:
 *       "true": step38
 *       "false": step08
 *     onFailure: FAIL
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다.
 * @param name         (옵셔널)사람이 읽는 step 이름입니다(예: 요구사항 분석). 디자이너 화면에 id와 함께 보이고, 실행에는 쓰이지 않습니다.
 * @param tool         (필수)부를 Tool의 이름입니다(@Tool 메서드 이름 또는 MCP Tool 이름).
 * @param input        (옵셔널)Tool 인자 이름 → 값(표현식 또는 리터럴)입니다. YAML 키는 with입니다. 비워두면 빈 인자입니다.
 * @param save         (옵셔널)이 step이 돌려준 값을 저장할 state 이름입니다(예: analysis → ${state.analysis}).
 * @param saveError    (옵셔널)실패 사유를 저장할 state 이름입니다(성공이면 null이 저장됩니다).
 * @param saveInput    (옵셔널)이 step이 실제로 받은 입력을 저장할 state 이름입니다.
 * @param saveEach     (옵셔널)forEach step만: 반복마다 {item, input, result, error, success} 한 건씩 담은 목록을 저장할 state 이름입니다.
 * @param next         (옵셔널)성공했을 때 갈 step의 id(또는 "END")입니다. routes와 함께 쓰지 않습니다.
 * @param routes       (옵셔널)Tool이 돌려준 값 → 갈 step의 id(또는 "END"/"FAIL")입니다. 적으면 next 대신 이것으로 갈 곳을 정합니다.
 * @param onFailure    (옵셔널)실패했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param forEach      (옵셔널)이 step을 항목마다 동시에 실행할 리스트의 표현식입니다(예: ${state.tree}).
 * @param itemVariable (옵셔널)forEach 반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"이고 표현식에서 ${item}으로 씁니다.
 * @param limit        (옵셔널)이 step을 몇 번까지 실행할지(StepLimit). 넘으면 실행하지 않고 limit.over로 갑니다.
 */
public record ToolStepDefinition(
	String id
	, String name
	, String tool
	, @JsonProperty("with") Map<String, Object> input
	, String save
	, String saveError
	, String saveInput
	, String saveEach
	, String next
	, Map<String, String> routes
	, String onFailure
	, String forEach
	, String itemVariable
	, StepLimit limit
	) implements StepDefinition {

	@Override
	public StepType type() {
		return StepType.TOOL;
	}

}

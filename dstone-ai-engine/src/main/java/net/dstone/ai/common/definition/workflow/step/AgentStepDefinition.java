package net.dstone.ai.common.definition.workflow.step;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/**.yml 의 type: AGENT step입니다. LLM에게 일을 한 번 시킵니다. agent에 적은 Agent를 한 번 부르고,
 * 그 답을 save에 적은 state 이름으로 저장합니다(runtime.step.AgentStepExecutor).
 *
 * 무엇을 받고 무엇을 돌려주는지는 Agent가 정합니다(agents/**.md 의 input/output). step은 "무엇을 넣고(with), 답을 어디에 둘지(save)"만 적습니다.
 * - Agent input이 string이면 with를 값 하나로 적습니다(표현식 또는 글자).
 * - Agent input이 object면 with를 맵으로 적습니다(필드마다 표현식 또는 리터럴). 엔진이 계산한 맵을 Agent input 스키마로 검사합니다.
 * - 답이 Agent output 모양이 아니면(또는 input이 Agent input 모양이 아니면) 이 step은 실패입니다.
 *
 *   - id: step01
 *     name: 요구사항 분석
 *     type: AGENT
 *     agent: pilot-requirment-analyzer-agent
 *     with:
 *       requirement: ${input.requirement}
 *     save: requirementAnalysis
 *     next: step02
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다.
 * @param name         (옵셔널)사람이 읽는 step 이름입니다(예: 요구사항 분석). 디자이너 화면에 id와 함께 보이고, 실행에는 쓰이지 않습니다.
 * @param agent        (필수)부를 Agent의 id입니다.
 * @param input        (필수)Agent에게 넣을 값입니다. YAML 키는 with입니다(Agent input 모양. 표현식/리터럴, 또는 그것들의 맵).
 * @param save         (옵셔널)이 step이 돌려준 값을 저장할 state 이름입니다(예: analysis → ${state.analysis}).
 * @param saveError    (옵셔널)실패 사유를 저장할 state 이름입니다(성공이면 null이 저장됩니다).
 * @param saveInput    (옵셔널)이 step이 실제로 받은 입력을 저장할 state 이름입니다.
 * @param saveEach     (옵셔널)forEach step만: 반복마다 {item, input, result, error, success} 한 건씩 담은 목록을 저장할 state 이름입니다.
 * @param next         (옵셔널)성공했을 때 갈 step의 id(또는 "END")입니다. 비워두면 목록의 다음 step입니다.
 * @param onFailure    (옵셔널)실패했을 때 갈 step의 id(또는 "FAIL")입니다. 비워두면 Workflow가 실패로 끝납니다.
 * @param forEach      (옵셔널)이 step을 항목마다 동시에 실행할 리스트의 표현식입니다(예: ${state.files}).
 * @param itemVariable (옵셔널)forEach 반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"이고 표현식에서 ${item}으로 씁니다.
 * @param memory       (옵셔널)true면 이 step이 자기 대화방에서 이전에 나눈 대화를 기억합니다(재작성 루프 등).
 */
public record AgentStepDefinition(
	String id
	, String name
	, String agent
	, @JsonProperty("with") Object input
	, String save
	, String saveError
	, String saveInput
	, String saveEach
	, String next
	, String onFailure
	, String forEach
	, String itemVariable
	, Boolean memory
	) implements StepDefinition {

	@Override
	public StepType type() {
		return StepType.AGENT;
	}

}

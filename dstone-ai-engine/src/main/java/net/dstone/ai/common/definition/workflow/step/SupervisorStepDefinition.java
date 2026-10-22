package net.dstone.ai.common.definition.workflow.step;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/**.yml 의 type: SUPERVISOR step입니다. LLM이 앞 step의 결과를 보고 통과/불통과를 판정합니다
 * (runtime.step.SupervisorStepExecutor).
 *
 * 답의 모양은 엔진이 {pass, reason}으로 정해 두었습니다. 그래서 부르는 Agent는 output을 선언하지 않습니다.
 * - 통과(pass: true)면 성공(next), 불통과면 실패(onFailure)입니다. 불통과 사유는 error에도 남습니다.
 * - 불통과여도 결과({pass: false, reason})는 save에 그대로 저장되므로, 되돌아간 step이 사유를 읽을 수 있습니다.
 *
 *   - id: review
 *     type: SUPERVISOR
 *     agent: sample-verdict-judge-agent
 *     with: ${state.draft}
 *     save: review                       # ${state.review.pass}, ${state.review.reason}
 *     next: publish
 *     onFailure: rewrite
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다.
 * @param name         (옵셔널)사람이 읽는 step 이름입니다(예: 요구사항 분석). 디자이너 화면에 id와 함께 보이고, 실행에는 쓰이지 않습니다.
 * @param agent        (필수)판정을 맡길 Agent의 id입니다.
 * @param input        (필수)Agent에게 넣을 값입니다. YAML 키는 with입니다(Agent input 모양).
 * @param save         (옵셔널)판정 결과({pass, reason})를 저장할 state 이름입니다.
 * @param saveError    (옵셔널)실패(불통과) 사유를 저장할 state 이름입니다.
 * @param saveInput    (옵셔널)이 step이 실제로 받은 입력을 저장할 state 이름입니다.
 * @param saveEach     (옵셔널)forEach step만: 반복마다 {item, input, result, error, success} 한 건씩 담은 목록을 저장할 state 이름입니다.
 * @param next         (옵셔널)통과했을 때 갈 step의 id(또는 "END")입니다.
 * @param onFailure    (옵셔널)불통과일 때 갈 step의 id(또는 "FAIL")입니다.
 * @param forEach      (옵셔널)이 step을 항목마다 동시에 실행할 리스트의 표현식입니다.
 * @param itemVariable (옵셔널)forEach 반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"입니다.
 * @param memory       (옵셔널)true면 이 step이 자기 대화방에서 이전에 나눈 대화를 기억합니다.
 */
public record SupervisorStepDefinition(
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
		return StepType.SUPERVISOR;
	}

}

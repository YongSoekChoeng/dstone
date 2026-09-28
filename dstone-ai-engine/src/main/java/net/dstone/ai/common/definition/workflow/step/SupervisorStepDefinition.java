package net.dstone.ai.common.definition.workflow.step;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/*.yml 의 type: SUPERVISOR step입니다. LLM에게 "통과인가 아닌가, 그리고 왜 그런가"를 판정받습니다.
 * 앞선 step들의 결과가 괜찮은지 감독하고 다시 검토하는 역할에 씁니다(runtime.step.SupervisorStepExecutor).
 *
 * - 통과: 성공이고, output에 {pass: true, reason}을 남깁니다.
 * - 불통과(또는 답의 모양이 깨짐): 실패이고, output에 {pass: false, reason}을, error에 reason을 남깁니다.
 *
 * output 모양은 엔진이 {pass, reason}으로 정해 두었으므로(common.schema.StepOutputSchemas), ref의 Agent는 output을 선언하지 않습니다.
 * input은 AGENT step과 같은 규칙입니다(Agent input 모양에 맞춰 적음).
 *
 *   - id: review
 *     type: SUPERVISOR
 *     ref: sample-verdict-judge-agent
 *     input: "{{steps.convert.output.sql}}"
 *     onFailure: convert
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다.
 * @param ref          (필수)부를 Agent의 id입니다.
 * @param input        (필수)Agent에게 넣을 값(판정할 대상)의 템플릿입니다.
 * @param onSuccess    (옵셔널)통과했을 때 갈 step의 id(또는 "SUCCESS")입니다.
 * @param onFailure    (옵셔널)불통과했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param forEach      (옵셔널)이 step을 항목마다 동시에 실행할 리스트의 참조 경로입니다.
 * @param itemVariable (옵셔널)forEach 반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"입니다.
 * @param memory       (옵셔널)true면 이 step이 이전에 자기가 나눈 대화를 기억합니다(대화방 = sessionId:stepId). 비워두면 false입니다.
 *                     재작성 루프처럼 같은 step이 다시 불릴 때 이전 시도를 기억하게 할 때 씁니다. 다른 step의 대화는 섞이지 않습니다.
 *                     forEach와 함께 쓸 수 없습니다(동시에 도는 반복들이 한 대화방에 섞여 쓰이기 때문입니다).
 */
public record SupervisorStepDefinition(
	String id
	, String ref
	, Object input
	, String onSuccess
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

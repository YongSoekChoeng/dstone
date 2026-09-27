package net.dstone.ai.common.definition.workflow.step;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/*.yml 의 type: SUPERVISOR step입니다. LLM에게 "통과인가 아닌가, 그리고 왜 그런가"를 판정받습니다.
 * 앞선 step들의 결과가 괜찮은지 감독하고 다시 검토하는 역할에 씁니다(runtime.step.SupervisorStepExecutor).
 *
 * - 통과: 받은 input을 결과 텍스트로 그대로 넘기고, output에 {pass, reason}을 남깁니다.
 * - 불통과(또는 답의 모양이 깨짐): 실패로 처리하고, reason을 error에 남깁니다.
 *
 * output 모양이 {pass, reason}으로 정해져 있어서 output 키는 없습니다.
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
 * @param input        (옵셔널)LLM에게 보낼 사용자 메시지의 템플릿입니다. 비워두면 {{previous.text}}입니다.
 * @param onSuccess    (옵셔널)통과했을 때 갈 step의 id(또는 "SUCCESS")입니다.
 * @param onFailure    (옵셔널)불통과했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param forEach      (옵셔널)이 step을 항목마다 동시에 실행할 리스트의 참조 경로입니다.
 * @param itemVariable (옵셔널)forEach 반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"입니다.
 */
public record SupervisorStepDefinition(
	String id
	, String ref
	, String input
	, String onSuccess
	, String onFailure
	, String forEach
	, String itemVariable
	) implements StepDefinition {

	@Override
	public StepType type() {
		return StepType.SUPERVISOR;
	}

}

package net.dstone.ai.common.definition.workflow.step;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/*.yml 의 type: AGENT step입니다. LLM에게 일을 한 번 시킵니다. ref에 적은 Agent를 한 번 부르고,
 * 그 답을 steps.이id.output에 남깁니다(runtime.step.AgentStepExecutor).
 *
 * 무엇을 받고 무엇을 돌려주는지는 Agent가 정합니다(agents/*.yml 의 input/output). step은 "무엇을 넣을지"만 적습니다.
 * - Agent input이 string이면 input을 값 하나로 적습니다(표현식 또는 글자).
 * - Agent input이 object면 input을 맵으로 적습니다(필드마다 표현식 또는 리터럴). 엔진이 계산한 맵을 Agent input 스키마로 검사합니다.
 *   값 전체를 표현식 하나로 적어도 됩니다(예: "${ .steps.analyze.output }"). 그러면 계산한 값을 그대로 검사합니다.
 * - 답은 Agent output 모양 그대로 steps.이id.output에 들어갑니다. output이 object면 "${ .steps.이id.output.필드 }"로,
 *   string이면 "${ .steps.이id.output }"으로 꺼냅니다.
 * - 답이 Agent output 모양이 아니면(또는 input이 Agent input 모양이 아니면) 이 step은 실패입니다.
 *
 *   - id: extract
 *     type: AGENT
 *     ref: sample-structured-extract-agent      # output: {sql: string}을 선언한 Agent
 *     input: "${ .input }"
 *     onSuccess: validate
 *     onFailure: FAIL
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다.
 * @param ref          (필수)부를 Agent의 id입니다(agents/*.yml 의 agent.id).
 * @param input        (필수)Agent에게 넣을 값입니다. Agent input이 string이면 값 하나, object면 맵입니다.
 * @param onSuccess    (옵셔널)성공했을 때 갈 step의 id(또는 "SUCCESS")입니다. 비워두면 목록의 다음 step입니다.
 * @param onFailure    (옵셔널)실패했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param forEach      (옵셔널)이 step을 항목마다 동시에 실행할 리스트의 표현식입니다(예: "${ .input.sqlList }").
 * @param itemVariable (옵셔널)forEach 반복 중 항목을 담을 jq 변수 이름입니다. 비워두면 "item"이고 표현식에서 $item으로 씁니다.
 * @param memory       (옵셔널)true면 이 step이 이전에 자기가 나눈 대화를 기억합니다(대화방 = sessionId:stepId). 비워두면 false입니다.
 *                     재작성 루프처럼 같은 step이 다시 불릴 때 이전 시도를 기억하게 할 때 씁니다. 다른 step의 대화는 섞이지 않습니다.
 *                     forEach와 함께 쓸 수 없습니다(동시에 도는 반복들이 한 대화방에 섞여 쓰이기 때문입니다).
 */
public record AgentStepDefinition(
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
		return StepType.AGENT;
	}

}

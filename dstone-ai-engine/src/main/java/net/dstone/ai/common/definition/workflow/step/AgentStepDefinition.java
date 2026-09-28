package net.dstone.ai.common.definition.workflow.step;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/*.yml 의 type: AGENT step입니다. LLM에게 일을 한 번 시킵니다. ref에 적은 Agent를 한 번 부르고,
 * 그 답을 steps.이id.output에 남깁니다(runtime.step.AgentStepExecutor).
 *
 * 무엇을 받고 무엇을 돌려주는지는 Agent가 정합니다(agents/*.yml 의 input/output). step은 "무엇을 넣을지"만 적습니다.
 * - Agent input이 string이면 input을 글자(템플릿)로 적습니다.
 * - Agent input이 object면 input을 맵으로 적습니다(값마다 템플릿). 엔진이 채운 맵을 Agent input 스키마로 검사합니다.
 * - 답은 Agent output 모양 그대로 steps.이id.output에 들어갑니다. output이 object면 {{steps.이id.output.필드}}로,
 *   string이면 {{steps.이id.output}}으로 꺼냅니다.
 * - 답이 Agent output 모양이 아니면(또는 input이 Agent input 모양이 아니면) 이 step은 실패입니다.
 *
 *   - id: extract
 *     type: AGENT
 *     ref: sample-structured-extract-agent      # output: {sql: string}을 선언한 Agent
 *     input: "{{input}}"
 *     onSuccess: validate
 *     onFailure: FAIL
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다.
 * @param ref          (필수)부를 Agent의 id입니다(agents/*.yml 의 agent.id).
 * @param input        (필수)Agent에게 넣을 값의 템플릿입니다. Agent input이 string이면 글자, object면 맵입니다.
 * @param onSuccess    (옵셔널)성공했을 때 갈 step의 id(또는 "SUCCESS")입니다. 비워두면 목록의 다음 step입니다.
 * @param onFailure    (옵셔널)실패했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param forEach      (옵셔널)이 step을 항목마다 동시에 실행할 리스트의 참조 경로입니다(예: input.sqlList).
 * @param itemVariable (옵셔널)forEach 반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"입니다.
 */
public record AgentStepDefinition(
	String id
	, String ref
	, Object input
	, String onSuccess
	, String onFailure
	, String forEach
	, String itemVariable
	) implements StepDefinition {

	@Override
	public StepType type() {
		return StepType.AGENT;
	}

}

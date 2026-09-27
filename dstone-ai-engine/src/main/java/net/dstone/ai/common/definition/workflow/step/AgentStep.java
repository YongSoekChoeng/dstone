package net.dstone.ai.common.definition.workflow.step;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * type: AGENT - LLM에게 일을 한 번 시키는 step입니다. ref에 적은 Agent를 한 번 부르고, 답을 결과로 남깁니다
 * (runtime.step.AgentStepRunner 참고).
 *
 * output.schema를 적지 않으면 LLM의 답 원문이 text가 되고 항상 성공입니다.
 * output.schema를 적으면 LLM이 그 모양의 JSON으로 답하도록 강제하고, 그 JSON이 output이 됩니다.
 * 모양을 지키지 않은 답이 오면 실패입니다.
 *
 *   - id: analyze
 *     type: AGENT
 *     ref: sql-analyzer
 *     input: "{{inputs.message}}"
 *     output:
 *       schema:
 *         tables: list<string>
 *     onFailure: FAIL
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다.
 * @param ref          (필수)부를 Agent의 id입니다.
 * @param input        (옵셔널)LLM에게 보낼 사용자 메시지의 템플릿입니다. 비워두면 {{previous.text}}입니다.
 * @param output       (옵셔널)LLM 답을 어떤 모양의 output으로 받을지 선언합니다(AgentOutput 참고).
 * @param onSuccess    (옵셔널)성공했을 때 갈 step의 id(또는 "SUCCESS")입니다.
 * @param onFailure    (옵셔널)실패했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param forEach      (옵셔널)반복 실행할 리스트의 참조 경로입니다(ForEachStep 참고).
 * @param itemVariable (옵셔널)반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"입니다.
 */
public record AgentStep(
	String id
	, String ref
	, String input
	, AgentOutput output
	, String onSuccess
	, String onFailure
	, String forEach
	, String itemVariable
	) implements AgentCallStep, ForEachStep, PassFailStep {

	@Override
	public StepType type() {
		return StepType.AGENT;
	}

}

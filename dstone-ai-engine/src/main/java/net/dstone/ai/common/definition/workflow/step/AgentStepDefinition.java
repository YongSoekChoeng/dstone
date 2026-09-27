package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

import net.dstone.ai.common.consts.StepType;
import net.dstone.ai.common.definition.workflow.FieldDefinition;

/**
 * <pre>
 * workflows/*.yml 의 type: AGENT step입니다. LLM에게 일을 한 번 시킵니다. ref에 적은 Agent를 한 번 부르고,
 * 답을 결과로 남깁니다(runtime.step.AgentStepExecutor).
 *
 * output을 적지 않으면 LLM의 답 원문이 text가 되고 항상 성공입니다.
 * output을 적으면 LLM이 그 모양의 JSON으로 답하도록 강제하고, 그 JSON이 steps.이id.output이 됩니다.
 * YAML의 output 아래 모양과 {{steps.이id.output.키}}로 꺼내는 모양이 똑같습니다.
 * 모양을 지키지 않은 답이 오면 실패입니다.
 *
 *   - id: extract
 *     type: AGENT
 *     ref: sample-structured-extract-agent
 *     input: "{{inputs.message}}"
 *     output:
 *       sql: string                       # 축약형: 타입만
 *       tables:                           # 확장형: 설명까지(LLM에게 그대로 전달됨)
 *         type: list<string>
 *         description: SQL이 읽는 테이블 이름들
 *     onFailure: FAIL
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다.
 * @param ref          (필수)부를 Agent의 id입니다(agents/*.yml 의 agent.id).
 * @param input        (옵셔널)LLM에게 보낼 사용자 메시지의 템플릿입니다. 비워두면 {{previous.text}}입니다.
 * @param output       (옵셔널)LLM 답을 받을 모양입니다. 필드 이름 → 필드 모양이고, 적은 필드는 모두 필수입니다.
 * @param onSuccess    (옵셔널)성공했을 때 갈 step의 id(또는 "SUCCESS")입니다. 비워두면 목록의 다음 step입니다.
 * @param onFailure    (옵셔널)실패했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param forEach      (옵셔널)이 step을 항목마다 동시에 실행할 리스트의 참조 경로입니다(예: inputs.sqlList).
 * @param itemVariable (옵셔널)forEach 반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"입니다.
 */
public record AgentStepDefinition(
	String id
	, String ref
	, String input
	, Map<String, FieldDefinition> output
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

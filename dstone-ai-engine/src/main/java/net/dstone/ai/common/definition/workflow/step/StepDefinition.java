package net.dstone.ai.common.definition.workflow.step;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * Workflow를 이루는 여러 단계(step) 중 하나입니다. WorkFlowDefinition의 steps 목록에 담기는 항목이 바로 이 타입입니다.
 *
 * ## step 종류마다 record가 따로 있습니다
 * YAML의 type 값을 보고 아래 다섯 record 중 하나로 읽습니다(common.loader.YamlDefinitionLoader).
 *
 *   type        record           하는 일
 *   AGENT       AgentStep        LLM에게 일을 한 번 시킴
 *   SUPERVISOR  SupervisorStep   LLM이 통과/불통과를 판정함
 *   ROUTER      RouterStep       LLM이 routes 중 갈 곳 하나를 고름
 *   TOOL        ToolStep         Tool 하나를 LLM 없이 직접 호출함
 *   APPROVAL    ApprovalStep     사람이 승인/반려할 때까지 기다림
 *
 * 각 record에는 그 종류가 실제로 쓰는 키만 있습니다. 그래서 다른 종류의 키를 적으면
 * (예: TOOL step에 routes, APPROVAL step에 input) 엔진이 켜질 때 "쓸 수 없는 키"로 바로 막힙니다.
 *
 * ## 공통 부분은 interface로 묶습니다
 * record는 다른 클래스를 상속(extends)할 수 없어서, 필드 선언은 record마다 따로 적습니다.
 * 대신 "이런 값을 꺼낼 수 있다"는 약속을 interface로 묶어서, 엔진 코드는 interface만 보고 다룹니다.
 *
 *   interface        담긴 값              해당 record
 *   StepDefinition   id, type, onFailure  전부
 *   AgentCallStep    ref, input(문자열)   AgentStep, SupervisorStep, RouterStep
 *   ForEachStep      forEach, itemVariable AgentStep, SupervisorStep, ToolStep
 *   PassFailStep     onSuccess            ROUTER를 뺀 나머지 전부
 *
 * sealed interface라서 위 다섯 record 말고는 step이 될 수 없습니다. 그래서 switch로 step 종류를
 * 나눌 때 빠뜨린 종류가 있으면 컴파일러가 알려줍니다.
 *
 * ## step은 무엇을 받는가 (input)
 * input에는 이 step에 넣어줄 값을 템플릿으로 적습니다. 템플릿 안의 {{ ... }} 자리는 step이 실행되기
 * 직전에 엔진(runtime.workflow.WorkFlowExecutor)이 실제 값으로 채웁니다(문법은 common.template.Template 참고).
 *
 *   {{inputs.message}}                Workflow를 실행할 때 넘긴 값
 *   {{steps.analyze.output.tables}}   analyze step이 남긴 결과
 *   {{previous.text}}                 바로 직전에 실행된 step의 결과
 *   {{item}}                          forEach로 반복 중일 때 이번 반복이 맡은 항목
 *   {{a.b ?? c.d}}                    왼쪽 값이 없으면 오른쪽 값을 씀
 *
 * input의 모양은 step 종류마다 다릅니다(AgentCallStep, ToolStep, ApprovalStep 참고).
 *
 * ## step은 무엇을 내놓는가 (output)
 * 모든 step은 실행이 끝나면 자기 id 아래에 {input, output, text, error}를 남깁니다(forEach면 items도 남깁니다).
 * 다음 step들은 이 값을 {{steps.이id.text}}, {{steps.이id.output.키}}처럼 이름으로 가져다 씁니다.
 * output의 모양은 AGENT는 AgentOutput, TOOL은 ToolOutput에 선언하고, 나머지는 모양이 정해져 있습니다
 * (SUPERVISOR {pass, reason}, ROUTER {route, reason}, APPROVAL {approved, approver, comment}).
 *
 * ## 다음 step으로 어떻게 넘어가는가 (onSuccess / onFailure)
 * onSuccess와 onFailure를 둘 다 비워두면 가장 단순한 동작이 됩니다: 성공하면 목록에서 바로 다음 step으로 넘어가고,
 * 실패하면 그 자리에서 Workflow 전체가 실패로 끝납니다.
 * onFailure에 앞쪽에 있는 다른 step의 id를 적어두면, 실패했을 때 그 step으로 되돌아가는 "재시도 루프"가 만들어집니다.
 * 이런 루프가 끝없이 돌지 않도록 WorkFlowDefinition.maxIterations가 전체 실행 횟수를 제한합니다.
 * onSuccess나 onFailure에 "SUCCESS" 또는 "FAIL"이라는 예약어를 적으면, 그 자리에서 바로 Workflow 전체를 성공/실패로 끝냅니다.
 * ROUTER는 onSuccess가 없고 대신 routes로 갈 곳을 정합니다(RouterStep 참고).
 *
 * ## 성공/실패는 어떻게 판정하는가
 * - AGENT: output.schema가 없으면 항상 성공입니다. schema가 있으면 LLM이 그 모양을 지키지 않았을 때 실패입니다.
 * - TOOL: Tool이 runtime.tool.ToolOutcome으로 답하면 그 success 값으로, 평범한 문자열로 답하면 "실패"로 시작하는지로 판정합니다.
 *   output.parse: json인데 응답이 JSON이 아니어도 실패입니다.
 * - SUPERVISOR: LLM의 판정(runtime.agent.Verdict의 pass)으로 판정합니다.
 * - ROUTER: LLM이 route를 고르지 못하면 실패입니다.
 * - APPROVAL: 사람이 승인했는지 반려했는지로 판정합니다.
 * - 공통: input 템플릿이 가리키는 값을 찾지 못하면(아직 실행 안 된 step을 참조하는 등) 그 step은 실패입니다.
 * 실패 사유는 그 step의 error에 남으므로, onFailure로 이동한 step이 {{steps.id.error}}로 읽을 수 있습니다.
 *
 * YAML이 이 규칙들을 지켰는지는 엔진이 켜질 때 common.registry.WorkFlowRegistry가 모두 검사합니다.
 * </pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = AgentStep.class, name = "AGENT")
	, @JsonSubTypes.Type(value = SupervisorStep.class, name = "SUPERVISOR")
	, @JsonSubTypes.Type(value = RouterStep.class, name = "ROUTER")
	, @JsonSubTypes.Type(value = ToolStep.class, name = "TOOL")
	, @JsonSubTypes.Type(value = ApprovalStep.class, name = "APPROVAL")
})
public sealed interface StepDefinition permits AgentCallStep, ForEachStep, PassFailStep {

	/** 이 step을 가리키는 이름입니다. onSuccess/onFailure/routes와 {{steps.id...}} 참조가 이 이름을 씁니다. */
	String id();

	/** 이 step의 종류입니다. YAML의 type 값이며, record마다 정해져 있습니다. */
	StepType type();

	/** 실패했을 때 다음으로 갈 step의 id(또는 "FAIL" 예약어)입니다. 비워두면 Workflow 전체가 실패로 끝납니다. */
	String onFailure();

	/**
	 * 이 step이 부르는 대상의 이름입니다. AGENT/SUPERVISOR/ROUTER는 Agent id, TOOL은 Tool 이름입니다.
	 * APPROVAL은 부르는 대상이 없어서 null입니다. 실행 이력과 로그에 "무엇을 불렀는지" 남길 때 씁니다.
	 */
	default String ref() {
		return null;
	}

}

package net.dstone.ai.common.definition.workflow.step;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.consts.StepType;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * workflows/*.yml 의 steps: 항목 하나입니다. WorkFlowDefinition.steps 목록에 담깁니다.
 *
 * ## type 값마다 record가 하나씩 있습니다
 * YAML의 type 값을 보고 아래 다섯 record 중 하나로 읽습니다(common.loader.YamlDefinitionLoader).
 * record의 필드가 곧 그 종류의 step에 적을 수 있는 YAML 키입니다.
 *
 *   type        record                     하는 일                              실행하는 곳(runtime.step)
 *   AGENT       AgentStepDefinition        LLM에게 일을 한 번 시킴               AgentStepExecutor
 *   SUPERVISOR  SupervisorStepDefinition   LLM이 통과/불통과를 판정함            SupervisorStepExecutor
 *   ROUTER      RouterStepDefinition       LLM이 routes 중 갈 곳 하나를 고름      RouterStepExecutor
 *   TOOL        ToolStepDefinition         Tool 하나를 LLM 없이 직접 호출함       ToolStepExecutor
 *   APPROVAL    ApprovalStepDefinition     사람이 승인/반려할 때까지 기다림       ApprovalStepExecutor
 *
 * record에 없는 키를 적으면(예: TOOL step에 routes, APPROVAL step에 input) 엔진이 켜질 때
 * "쓸 수 없는 키"로 바로 막힙니다.
 *
 * ## 이 interface가 하는 일
 * steps 목록에 다섯 종류가 섞여 들어오므로, 그 목록의 공통 타입으로만 씁니다. 모든 종류에 있는 값(id, type, onFailure)만
 * 여기에 두고, 나머지는 각 record에서 꺼냅니다. sealed interface라서 다섯 record 말고는 step이 될 수 없고,
 * switch로 step 종류를 나눌 때 빠뜨린 종류가 있으면 컴파일러가 알려줍니다.
 * 종류마다 있기도 하고 없기도 한 값(ref, forEach)을 꺼내는 일은 아래 static 메서드가 한곳에서 맡습니다.
 *
 * ## 공통 규칙
 * - input: 이 step에 넣어줄 값의 템플릿입니다. {{ ... }} 자리는 step이 실행되기 직전에 엔진이 채웁니다
 *   (문법은 common.template.Template 참고). 모양은 종류마다 다릅니다(AGENT류는 문자열, TOOL은 맵, APPROVAL은 없음).
 *
 *     {{inputs.message}}                Workflow를 실행할 때 넘긴 값
 *     {{steps.analyze.output.tables}}   analyze step이 남긴 결과
 *     {{previous.text}}                 바로 직전에 실행된 step의 결과
 *     {{item}}                          forEach로 반복 중일 때 이번 반복이 맡은 항목
 *     {{a.b ?? c.d}}                    왼쪽 값이 없으면 오른쪽 값을 씀
 *
 * - 결과: 모든 step은 끝나면 자기 id 아래에 {input, output, text, error}를 남깁니다(forEach면 items도).
 *   다음 step은 {{steps.이id.text}}, {{steps.이id.output.키}}처럼 꺼내 씁니다. output의 모양은 AGENT와 TOOL은
 *   YAML의 output에 선언하고, 나머지는 정해져 있습니다(SUPERVISOR {pass, reason}, ROUTER {route, reason},
 *   APPROVAL {approved, approver, comment}).
 *
 * - 다음 step: onSuccess와 onFailure를 둘 다 비워두면 성공 시 목록의 다음 step으로, 실패 시 Workflow 전체 실패로 끝납니다.
 *   onFailure에 앞쪽 step의 id를 적으면 재시도 루프가 되고, 무한 반복은 WorkFlowDefinition.maxIterations가 막습니다.
 *   "SUCCESS"/"FAIL" 예약어를 적으면 그 자리에서 Workflow 전체를 성공/실패로 끝냅니다.
 *   ROUTER는 onSuccess 대신 routes로 갈 곳을 정합니다.
 *
 * - 실패 사유는 그 step의 error에 남으므로, onFailure로 이동한 step이 {{steps.id.error}}로 읽을 수 있습니다.
 *   input 템플릿이 가리키는 값을 찾지 못해도 그 step은 실패입니다.
 *
 * YAML이 이 규칙들을 지켰는지는 엔진이 켜질 때 common.registry.WorkFlowRegistry가 모두 검사합니다.
 * </pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = AgentStepDefinition.class, name = "AGENT")
	, @JsonSubTypes.Type(value = SupervisorStepDefinition.class, name = "SUPERVISOR")
	, @JsonSubTypes.Type(value = RouterStepDefinition.class, name = "ROUTER")
	, @JsonSubTypes.Type(value = ToolStepDefinition.class, name = "TOOL")
	, @JsonSubTypes.Type(value = ApprovalStepDefinition.class, name = "APPROVAL")
})
public sealed interface StepDefinition
	permits AgentStepDefinition, SupervisorStepDefinition, RouterStepDefinition, ToolStepDefinition, ApprovalStepDefinition {

	/** 이 step을 가리키는 이름입니다. onSuccess/onFailure/routes와 {{steps.id...}} 참조가 이 이름을 씁니다. */
	String id();

	/** 이 step의 종류입니다. YAML의 type 값이며, record마다 정해져 있습니다. */
	StepType type();

	/** 실패했을 때 다음으로 갈 step의 id(또는 "FAIL" 예약어)입니다. 비워두면 Workflow 전체가 실패로 끝납니다. */
	String onFailure();

	/**
	 * step이 부르는 대상의 이름(ref)을 돌려줍니다. AGENT/SUPERVISOR/ROUTER는 Agent id, TOOL은 Tool 이름이고,
	 * APPROVAL은 부르는 대상이 없어서 null입니다. 실행 이력과 로그에 "무엇을 불렀는지" 남길 때 씁니다.
	 *
	 * @param step ref를 꺼낼 step
	 */
	static String refOf(StepDefinition step) {
		switch (step) {
			case AgentStepDefinition agent:
				return agent.ref();
			case SupervisorStepDefinition supervisor:
				return supervisor.ref();
			case RouterStepDefinition router:
				return router.ref();
			case ToolStepDefinition tool:
				return tool.ref();
			case ApprovalStepDefinition approval:
				return null;
		}
	}

	/**
	 * step의 forEach 경로를 돌려줍니다. 비워뒀거나 forEach를 쓸 수 없는 종류(ROUTER/APPROVAL)면 null입니다.
	 *
	 * @param step forEach를 꺼낼 step
	 */
	static String forEachOf(StepDefinition step) {
		String forEach;
		switch (step) {
			case AgentStepDefinition agent:
				forEach = agent.forEach();
				break;
			case SupervisorStepDefinition supervisor:
				forEach = supervisor.forEach();
				break;
			case ToolStepDefinition tool:
				forEach = tool.forEach();
				break;
			case RouterStepDefinition router:
				forEach = null;
				break;
			case ApprovalStepDefinition approval:
				forEach = null;
				break;
		}
		return StringUtil.isEmpty(forEach) ? null : forEach;
	}

	/**
	 * forEach 반복 중 항목을 담을 변수 이름을 돌려줍니다. itemVariable을 비워뒀으면 "item"입니다.
	 *
	 * @param step 변수 이름을 꺼낼 step
	 */
	static String itemKeyOf(StepDefinition step) {
		String itemVariable;
		switch (step) {
			case AgentStepDefinition agent:
				itemVariable = agent.itemVariable();
				break;
			case SupervisorStepDefinition supervisor:
				itemVariable = supervisor.itemVariable();
				break;
			case ToolStepDefinition tool:
				itemVariable = tool.itemVariable();
				break;
			case RouterStepDefinition router:
				itemVariable = null;
				break;
			case ApprovalStepDefinition approval:
				itemVariable = null;
				break;
		}
		return StringUtil.isEmpty(itemVariable) ? Constants.WorkFlow.DEFAULT_ITEM_VARIABLE_KEY : itemVariable;
	}

}

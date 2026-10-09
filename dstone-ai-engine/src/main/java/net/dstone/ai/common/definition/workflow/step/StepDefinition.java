package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

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
 *   APPROVAL    ApprovalStepDefinition     사람이 routes 중 하나를 고를 때까지 기다림   ApprovalStepExecutor
 *
 * record에 없는 키를 적으면(예: TOOL step에 routes, APPROVAL step에 input) 엔진이 켜질 때
 * "쓸 수 없는 키"로 바로 막힙니다.
 *
 * ## 이 interface가 하는 일
 * steps 목록에 다섯 종류가 섞여 들어오므로, 그 목록의 공통 타입으로만 씁니다. 모든 종류에 있는 값(id, type, output, onFailure)만
 * 여기에 두고, 나머지는 각 record에서 꺼냅니다. sealed interface라서 다섯 record 말고는 step이 될 수 없고,
 * switch로 step 종류를 나눌 때 빠뜨린 종류가 있으면 컴파일러가 알려줍니다.
 * 종류마다 있기도 하고 없기도 한 값(부르는 대상, next, forEach, memory)을 꺼내는 일은 아래 static 메서드가 한곳에서 맡습니다.
 *
 * ## 공통 규칙
 * - 계약은 부르는 대상이 정합니다: 무엇을 받고 무엇을 돌려주는지는 Agent(agents/*.yml 의 input/output)나
 *   Tool(인자 스키마)이 정하고, step은 "무엇을 넣을지(input)", "결과를 어디에 둘지(output)", "다음에 어디로 갈지"만 적습니다.
 *
 * - id: 영문, 숫자, 밑줄(_)만 씁니다(예: validateEach). END/FAIL은 예약어라 id로 쓸 수 없습니다.
 *
 * - input: 이 step에 넣어줄 값입니다. "${ ... }"는 step이 실행되기 직전에 엔진이 읽어 와서 채우고,
 *   그 밖의 값은 적힌 그대로 넘깁니다(규칙은 common.expression.ContextResolver 참고).
 *
 *     "${input}"                        Workflow를 실행할 때 넘긴 값(object면 ${input.필드})
 *     "${state.analysis}"               앞의 step이 state에 저장해 둔 값(object면 ${state.analysis.필드})
 *     "${item}"                         forEach로 반복 중일 때 이번 반복이 맡은 항목
 *     "${state.review.comment:}"        값이 없으면 콜론 뒤의 글자(여기서는 빈 글자)
 *     "${input}/01-requirements.md"     글자 사이에 끼워 넣기
 *
 * - output: 이 step의 결과를 state 어디에 저장할지입니다. 저장하지 않은 결과는 다음 step이 읽을 수 없습니다.
 *
 *     output:
 *       result: "state.analysis"        이 step이 돌려준 값 전체
 *       result.sql: "state.sql"         돌려준 값 안의 필드 하나
 *       input: "state.sentSql"          이 step이 실제로 받은 입력
 *       error: "state.analysisError"    실패 사유(성공이면 null)
 *
 *   result의 모양은 AGENT는 Agent output, TOOL은 Tool 응답(JSON이면 그 값, 아니면 글자)이고, 나머지는 엔진이
 *   정해 두었습니다(SUPERVISOR {pass, reason}, ROUTER {route, reason}, APPROVAL {decision, approver, comment}).
 *   forEach step은 input과 result가 반복별 값의 리스트입니다(순서는 forEach 리스트와 같습니다).
 *   step이 끝날 때마다(성공이든 실패든) 적힌 자리를 모두 새 값으로 덮어씁니다.
 *
 * - 다음 step: next와 onFailure를 둘 다 비워두면 성공 시 목록의 다음 step으로, 실패 시 Workflow 전체 실패로 끝납니다.
 *   onFailure에 앞쪽 step의 id를 적으면 재시도 루프가 되고, 무한 반복은 workflow.settings.maxIterations가 막습니다.
 *   "END"/"FAIL" 예약어를 적으면 그 자리에서 Workflow 전체를 성공/실패로 끝냅니다.
 *   ROUTER와 APPROVAL은 next 대신 routes로 갈 곳을 정합니다.
 *
 * - 대화 기억: step끼리 넘기는 값은 state뿐입니다. LLM을 부르는 step은 기본적으로 이전 대화를 보지 않고
 *   input만 보고 답합니다. memory: true를 적은 step만 자기 대화방(sessionId:stepId)에서 이전에 자기가 나눈 대화를
 *   기억합니다(재작성 루프 등). 다른 step의 대화는 섞이지 않습니다.
 *
 * - forEach: 이 step을 항목마다 동시에 실행할 리스트를 표현식으로 적습니다(예: "${input.sqlList}").
 *   각 반복의 input 안에서 이번 항목은 ${item}(또는 itemVariable에 적은 이름)입니다.
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

	/** 이 step을 가리키는 이름입니다. next/onFailure/routes가 이 이름을 씁니다. */
	String id();

	/** 이 step의 종류입니다. YAML의 type 값이며, record마다 정해져 있습니다. */
	StepType type();

	/** 이 step의 결과를 state 어디에 저장할지입니다(무엇 → state.이름). 비워뒀으면 null입니다. */
	Map<String, String> output();

	/** 실패했을 때 다음으로 갈 step의 id(또는 "FAIL" 예약어)입니다. 비워두면 Workflow 전체가 실패로 끝납니다. */
	String onFailure();

	/**
	 * step이 부르는 대상의 이름을 돌려줍니다. AGENT/SUPERVISOR/ROUTER는 Agent id, TOOL은 Tool 이름이고,
	 * APPROVAL은 부르는 대상이 없어서 null입니다. 실행 이력과 로그에 "무엇을 불렀는지" 남길 때 씁니다.
	 *
	 * @param step 부르는 대상을 꺼낼 step
	 */
	static String refOf(StepDefinition step) {
		switch (step) {
			case AgentStepDefinition agent:
				return agent.agent();
			case SupervisorStepDefinition supervisor:
				return supervisor.agent();
			case RouterStepDefinition router:
				return router.agent();
			case ToolStepDefinition tool:
				return tool.tool();
			case ApprovalStepDefinition approval:
				return null;
		}
	}

	/**
	 * 성공했을 때 갈 곳(next)을 돌려줍니다. 비워뒀으면 null이고, ROUTER와 APPROVAL은 routes로 갈 곳을 정하므로 항상 null입니다.
	 *
	 * @param step next를 꺼낼 step
	 */
	static String nextOf(StepDefinition step) {
		switch (step) {
			case AgentStepDefinition agent:
				return agent.next();
			case SupervisorStepDefinition supervisor:
				return supervisor.next();
			case ToolStepDefinition tool:
				return tool.next();
			case RouterStepDefinition router:
				return null;
			case ApprovalStepDefinition approval:
				return null;
		}
	}

	/**
	 * <pre>
	 * 이름을 골라서 갈 곳을 정하는 step이면 그 routes(이름 → 갈 곳)를 돌려줍니다. 아니면 null입니다.
	 * - ROUTER: LLM이 이름을 고릅니다.
	 * - APPROVAL: 사람이 이름을 고릅니다.
	 * </pre>
	 *
	 * @param step routes를 꺼낼 step
	 */
	static Map<String, String> routesOf(StepDefinition step) {
		if (step instanceof RouterStepDefinition router) {
			return router.routes() == null ? Map.of() : router.routes();
		}
		if (step instanceof ApprovalStepDefinition approval) {
			return approval.decisionRoutes();
		}
		return null;
	}

	/**
	 * step의 forEach 표현식을 돌려줍니다. 비워뒀거나 forEach를 쓸 수 없는 종류(ROUTER/APPROVAL)면 null입니다.
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
	 * <pre>
	 * forEach 반복 중 항목을 담을 변수 이름을 돌려줍니다.
	 * itemVariable을 비워뒀으면 "item"입니다.
	 * </pre>
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

	/**
	 * <pre>
	 * step이 이전 대화를 기억하는지(memory: true) 돌려줍니다.
	 * 비워뒀거나 LLM을 부르지 않는 종류(TOOL/APPROVAL)면 false입니다.
	 * </pre>
	 *
	 * @param step memory 값을 꺼낼 step
	 */
	static boolean memoryOf(StepDefinition step) {
		Boolean memory;
		switch (step) {
			case AgentStepDefinition agent:
				memory = agent.memory();
				break;
			case SupervisorStepDefinition supervisor:
				memory = supervisor.memory();
				break;
			case RouterStepDefinition router:
				memory = router.memory();
				break;
			case ToolStepDefinition tool:
				memory = null;
				break;
			case ApprovalStepDefinition approval:
				memory = null;
				break;
		}
		return Boolean.TRUE.equals(memory);
	}

}

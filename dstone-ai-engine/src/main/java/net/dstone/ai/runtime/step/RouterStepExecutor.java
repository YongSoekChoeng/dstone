package net.dstone.ai.runtime.step;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.FieldDefinition;
import net.dstone.ai.common.definition.workflow.step.RouterStepDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * type: ROUTER step(RouterStepDefinition)을 실행합니다. LLM에게 "어디로 갈지"를 {route, reason} 모양의 JSON으로
 * 답하게 합니다. 답의 모양은 AGENT step의 output과 똑같은 방식(AgentExecutor.callForSchema)으로 강제하고 검사합니다.
 * route 설명에 routes의 이름 목록을 넣어서 LLM이 그중에서 고르도록 알려줍니다(Agent prompt에도 적어 두는 것이 안전합니다).
 *
 * 받은 input은 결과 텍스트로 그대로 넘기고, 고른 경로는 output에 {route, reason}으로 남깁니다.
 * 그 route가 routes에 실제로 있는지 확인하고 다음 step을 정하는 일은 runtime.workflow.WorkFlowExecutor가 이어받습니다.
 *
 * 답을 {route, reason} 모양으로 읽지 못하거나 route가 비어 있으면 실패로 처리합니다(onFailure를 따릅니다).
 * 갈 곳을 고르지 못한 채로 계속 진행할 수는 없기 때문입니다.
 * </pre>
 */
@Component
public class RouterStepExecutor {

	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private AgentExecutor agentExecutor;

	/**
	 * ROUTER step 하나를 실행합니다(규칙은 클래스 설명 참고).
	 *
	 * @param execution      지금 진행 중인 Workflow 실행 상태입니다.
	 * @param step           실행할 step의 정의입니다.
	 * @param input          템플릿이 채워진 사용자 메시지(분류할 대상)입니다.
	 * @param workflowInputs Workflow를 실행할 때 넘긴 값(컨텍스트의 inputs)입니다. Agent system prompt의 {변수}를 채웁니다.
	 */
	public StepOutcome run(WorkFlowExecution execution, RouterStepDefinition step, String input, Map<String, Object> workflowInputs) {
		AgentDefinition agent = this.agentRegistry.resolve(step.ref(), execution.caller());
		Map<String, Object> answer;
		try {
			answer = this.agentExecutor.callForSchema(agent, execution.sessionId(), execution.caller(), workflowInputs, input, this.decisionShape(step));
		} catch (Exception e) {
			return StepOutcome.failure(input, "라우팅 Agent 응답을 {route, reason} 모양으로 읽지 못했습니다 - " + e.getMessage());
		}
		Object routeValue = answer.get("route");
		if (routeValue == null || StringUtil.isEmpty(routeValue.toString())) {
			return StepOutcome.failure(input, "라우팅 Agent가 route를 고르지 않았습니다.");
		}
		Map<String, Object> output = new LinkedHashMap<>();
		output.put("route", routeValue.toString());
		output.put("reason", answer.get("reason"));
		return StepOutcome.routed(input, output, routeValue.toString());
	}

	/**
	 * LLM이 지켜야 할 답의 모양({route: string, reason: string})을 만듭니다. route 설명에 고를 수 있는 이름 목록을 넣습니다.
	 *
	 * @param step 실행할 ROUTER step의 정의입니다.
	 */
	private Map<String, FieldDefinition> decisionShape(RouterStepDefinition step) {
		Map<String, FieldDefinition> shape = new LinkedHashMap<>();
		shape.put("route", new FieldDefinition("string", "다음 중 정확히 하나: " + String.join(", ", step.routes().keySet())));
		shape.put("reason", new FieldDefinition("string", "그 경로를 고른 이유"));
		return shape;
	}

}

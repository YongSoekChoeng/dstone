package net.dstone.ai.runtime.step;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.step.RouterStepDefinition;
import net.dstone.ai.common.exception.AgentContractException;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.common.schema.JsonSchemaUtil;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.prompt.EnginePrompt;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * <pre>
 * type: ROUTER step(RouterStepDefinition)을 실행합니다. LLM에게 "어디로 갈지"를 {route, reason} 모양의 JSON으로
 * 답하게 합니다. 답의 모양은 엔진이 정한 스키마(common.schema.StepOutputSchemas.routeDecision())로 강제하고 검사하며,
 * route는 routes 이름 중 하나만 허용합니다(스키마의 enum). 그래서 routes에 없는 이름을 고른 답은 모양이 틀린 답으로 봅니다.
 *
 * 고른 경로는 output에 {route, reason}으로 남기고, 그 route로 다음 step을 정하는 일은
 * runtime.workflow.WorkFlowExecutor가 이어받습니다.
 *
 * LLM에게는 "경로만 고르고 질문에 답하지 말라"는 엔진 규칙(runtime.prompt.EnginePrompt.ROUTER)을 함께 보냅니다.
 *
 * 답을 {route, reason} 모양으로 받지 못하면(또는 input이 Agent input 모양이 아니면) 실패로 처리합니다(onFailure를 따릅니다).
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
	 * @param execution       지금 진행 중인 Workflow 실행 상태입니다.
	 * @param step            실행할 step의 정의입니다.
	 * @param input           표현식을 계산해 채운 input(분류할 대상)입니다.
	 */
	@SuppressWarnings("unchecked")
	public StepOutcome run(WorkFlowExecution execution, RouterStepDefinition step, Object input) {
		AgentDefinition agent = this.agentRegistry.resolve(step.agent(), execution.caller());
		Map<String, Object> answer = null;
		try {
			answer = (Map<String, Object>) this.agentExecutor.callForSchema(agent, execution.conversationIdOf(step), execution.caller(), input, JsonSchemaUtil.routeDecision(step.routes().keySet()), EnginePrompt.ROUTER);
		} catch (AgentContractException e) {
			// 왜 못 받았는지(답이 비었는지, JSON이 아닌지, 출력 한도에 걸렸는지)는 예외 메시지에 들어 있습니다.
			return StepOutcome.failure(null, "라우팅 Agent 응답을 {route, reason} 모양으로 받지 못했습니다: " + e.getMessage());
		}
		String route = String.valueOf(answer.get("route"));
		Map<String, Object> output = new LinkedHashMap<>();
		output.put("route", route);
		output.put("reason", answer.get("reason"));
		return StepOutcome.routed(output, route);
	}

}

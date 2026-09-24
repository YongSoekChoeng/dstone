package net.dstone.ai.runtime.step;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.consts.StepType;
import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.definition.FieldDefinition;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.agent.RouteDecision;
import net.dstone.ai.runtime.agent.Verdict;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * Workflow의 세 가지 step 종류(AGENT, SUPERVISOR, ROUTER)를 처리하는 클래스입니다. 셋 다 StepDefinition.ref()에
 * 적힌 Agent를 부르고, 채워진 input 텍스트(StepInput.text)를 사용자 메시지로 보낸다는 점은 같습니다.
 * 응답을 어떤 모양으로 받고 무엇을 결과로 남기는지가 다릅니다.
 *
 *   종류                    text(결과 텍스트)        data(구조화된 결과)         실패하는 경우
 *   AGENT (schema 없음)     LLM 답변 원문            없음                       없음(항상 성공)
 *   AGENT (schema 있음)     data를 JSON 글자로       output.schema대로 읽은 값  LLM이 schema를 지키지 않음
 *   SUPERVISOR              받은 input 그대로        {pass, reason}             pass=false, 또는 응답 모양이 깨짐
 *   ROUTER                  받은 input 그대로        {route, reason}            route를 고르지 못함, 또는 응답 모양이 깨짐
 *
 * SUPERVISOR와 ROUTER는 "판정"과 "선택"만 하는 관문이라서, 받은 input을 결과 텍스트로 그대로 넘깁니다.
 * 판정 사유는 결과 텍스트에 덧붙이지 않고 data(또는 실패 시 error)에만 담으므로, 다음 step이 필요할 때
 * {{steps.id.data.reason}}이나 {{steps.id.error}}로 따로 꺼내 씁니다.
 *
 * Workflow의 step에서 Agent를 부를 때는 요청마다 RAG/Tool/모델을 바꾸는 기능(ragOverride 등)을 쓰지 않고
 * 항상 Agent 정의값을 그대로 씁니다. 그 기능은 api.controller.ChatController처럼 Agent 하나를 직접 부르는
 * 화면에서만 씁니다.
 * </pre>
 */
@Component
public class AgentStepRunner implements StepRunner {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private AgentExecutor agentExecutor;

	@Override
	public StepOutcome run(WorkFlowExecution execution, StepDefinition definition, StepInput input) {
		AgentDefinition agent = this.agentRegistry.resolve(definition.ref(), execution.caller());
		if (definition.type() == StepType.SUPERVISOR) {
			return this.runSupervisor(execution, agent, input);
		}
		if (definition.type() == StepType.ROUTER) {
			return this.runRouter(execution, agent, input);
		}
		if (definition.output() != null && definition.output().schema() != null) {
			return this.runSchemaAgent(execution, agent, input, definition.output().schema());
		}
		return this.runAgent(execution, agent, input);
	}

	/**
	 * output.schema가 없는 AGENT step을 처리합니다. LLM이 답한 글을 그대로 결과 텍스트로 남기고, 항상 성공으로 봅니다.
	 *
	 * @param execution 지금 진행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent의 정의
	 * @param input     이 step에 들어온 입력
	 */
	private StepOutcome runAgent(WorkFlowExecution execution, AgentDefinition agent, StepInput input) {
		String answer = this.agentExecutor.call(agent, execution.sessionId(), execution.caller(), input.workflowInput(), input.text(), null, null, null);
		return StepOutcome.success(answer);
	}

	/**
	 * output.schema가 있는 AGENT step을 처리합니다. LLM이 schema 모양의 JSON으로 답하게 하고, 그 JSON을 data로 남깁니다.
	 * 결과 텍스트에는 같은 data를 JSON 글자로 담습니다.
	 *
	 * LLM이 schema를 지키지 않으면(필드가 빠졌거나 타입이 다르면) 실패로 처리합니다. schema를 선언했다는 것은
	 * 다음 step이 그 data를 믿고 그대로 쓰겠다는 뜻이므로, 모양이 깨진 답을 성공으로 넘기지 않습니다.
	 *
	 * @param execution 지금 진행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent의 정의
	 * @param input     이 step에 들어온 입력
	 * @param schema    LLM이 지켜야 할 응답 필드 목록(step의 output.schema)
	 */
	private StepOutcome runSchemaAgent(WorkFlowExecution execution, AgentDefinition agent, StepInput input, Map<String, FieldDefinition> schema) {
		Map<String, Object> data;
		try {
			data = this.agentExecutor.callForSchema(agent, execution.sessionId(), execution.caller(), input.workflowInput(), input.text(), schema);
		} catch (Exception e) {
			return StepOutcome.failure(null, "Agent 응답을 output.schema 모양으로 읽지 못했습니다 - " + e.getMessage());
		}
		return StepOutcome.success(this.toJson(data), data);
	}

	/**
	 * <pre>
	 * ROUTER step을 처리합니다. LLM에게 "어디로 갈지"를 담은 RouteDecision(route, reason)으로 답하게 합니다.
	 * 받은 input은 결과 텍스트로 그대로 넘기고, 고른 경로는 data에 {route, reason}으로 남깁니다.
	 * 그 route가 StepDefinition.routes에 실제로 있는지 확인하고 다음 step을 정하는 일은
	 * runtime.workflow.WorkFlowExecutor가 이어받습니다.
	 *
	 * 응답을 RouteDecision 모양으로 읽지 못하거나 route가 비어 있으면 실패로 처리합니다. 갈 곳을 고르지
	 * 못한 채로 계속 진행할 수는 없기 때문입니다.
	 * </pre>
	 *
	 * @param execution 지금 진행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent의 정의
	 * @param input     이 step에 들어온 입력
	 */
	private StepOutcome runRouter(WorkFlowExecution execution, AgentDefinition agent, StepInput input) {
		RouteDecision decision;
		try {
			decision = this.agentExecutor.callForEntity(agent, execution.sessionId(), execution.caller(), input.workflowInput(), input.text(), RouteDecision.class);
		} catch (Exception e) {
			return StepOutcome.failure(input.text(), "라우팅 Agent 응답을 구조화된 형식(route/reason)으로 해석하지 못했습니다 - " + e.getMessage());
		}
		if (decision == null || StringUtil.isEmpty(decision.route())) {
			return StepOutcome.failure(input.text(), "라우팅 Agent가 route를 고르지 않았습니다.");
		}
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("route", decision.route());
		data.put("reason", decision.reason());
		return StepOutcome.routed(input.text(), data, decision.route());
	}

	/**
	 * <pre>
	 * SUPERVISOR step을 처리합니다. LLM에게 "통과했는지 아닌지"를 담은 Verdict(pass, reason)로 답하게 해서,
	 * 그 값으로 이 step의 성공/실패를 정합니다(Spring AI가 Verdict의 JSON 모양을 프롬프트에 알려주고, 답을 그
	 * 모양으로 읽어줍니다 - runtime.agent.AgentExecutor.callForVerdict 참고).
	 *
	 * - 통과: 받은 input을 결과 텍스트로 그대로 넘기고, data에 {pass, reason}을 남깁니다.
	 * - 불통과: 실패로 처리하고, reason을 실패 사유(error)로 남깁니다.
	 * - 응답 모양이 깨짐: 판정을 믿을 수 없으므로 안전하게 실패로 처리합니다.
	 * </pre>
	 *
	 * @param execution 지금 진행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent의 정의
	 * @param input     이 step에 들어온 입력
	 */
	private StepOutcome runSupervisor(WorkFlowExecution execution, AgentDefinition agent, StepInput input) {
		Verdict verdict;
		try {
			verdict = this.agentExecutor.callForVerdict(agent, execution.sessionId(), execution.caller(), input.workflowInput(), input.text());
		} catch (Exception e) {
			return StepOutcome.failure(input.text(), "감독 Agent 응답을 구조화된 형식(pass/reason)으로 해석하지 못했습니다 - " + e.getMessage());
		}
		String reason = verdict == null || StringUtil.isEmpty(verdict.reason()) ? "(사유 없음)" : verdict.reason();
		if (verdict != null && verdict.pass()) {
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("pass", true);
			data.put("reason", reason);
			return StepOutcome.success(input.text(), data);
		}
		return StepOutcome.failure(input.text(), reason);
	}

	/**
	 * data를 결과 텍스트로 남길 JSON 글자로 바꿉니다.
	 *
	 * @param data JSON 글자로 바꿀 값입니다.
	 */
	private String toJson(Map<String, Object> data) {
		try {
			return this.objectMapper.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Agent 응답 data를 JSON으로 바꾸지 못했습니다: " + data, e);
		}
	}

}

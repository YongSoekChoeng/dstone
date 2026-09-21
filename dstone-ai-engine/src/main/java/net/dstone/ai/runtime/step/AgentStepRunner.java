package net.dstone.ai.runtime.step;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.common.definition.StepType;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.status.RouteDecision;
import net.dstone.ai.runtime.status.StepInput;
import net.dstone.ai.runtime.status.StepOutput;
import net.dstone.ai.runtime.status.StepPayload;
import net.dstone.ai.runtime.status.Verdict;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.utils.StringUtil;

/**
 * AGENT/SUPERVISOR/ROUTER step - ref로 지정된 Agent를 호출한다. SUPERVISOR는 구조화된 Verdict(pass/reason)로 성공/실패를,
 * ROUTER는 구조화된 RouteDecision(route/reason)으로 다음 갈 곳을 가른다. AGENT는 StepDefinition.structuredOutput이 true일 때만
 * 구조화된 StepPayload(primaryText/data)로 응답을 받고, 그 외에는(기본값) 자유 텍스트로 받는다.
 */
@Component
public class AgentStepRunner implements StepRunner {

	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private AgentExecutor agentExecutor;

	@Override
	public StepOutput run(WorkFlowExecution execution, StepDefinition definition, StepInput input) {
		AgentDefinition agent = this.agentRegistry.resolve(definition.ref(), execution.caller());
		if (definition.type() == StepType.SUPERVISOR) {
			return this.runSupervisor(execution, agent, input);
		}
		if (definition.type() == StepType.ROUTER) {
			return this.runRouter(execution, agent, input);
		}
		if (Boolean.TRUE.equals(definition.structuredOutput())) {
			return this.runStructuredAgent(execution, agent, input);
		}
		return this.runAgent(execution, agent, input);
	}

	/**
	 * <pre>
	 * AGENT step(structuredOutput=false/null, 기본값) - 항상 성공으로 취급된다.
	 * </pre>
	 *
	 * @param execution 실행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent 정의
	 * @param input     렌더링된 입력과 전역 변수
	 */
	private StepOutput runAgent(WorkFlowExecution execution, AgentDefinition agent, StepInput input) {
		// Workflow step은 항상 Agent 정의값 그대로 쓴다(null, null, null) - 요청별 ragOverride/toolsOverride/modelOverride는
		// api.controller.ChatController(단일 Agent 직접 호출)에만 있는 기능이다.
		String answer = this.agentExecutor.call(agent, execution.sessionId(), execution.caller(), input.variables(), input.renderedText(), null, null, null);
		return StepOutput.success(answer);
	}

	/**
	 * <pre>
	 * AGENT step(structuredOutput=true) - 자유 텍스트 대신 구조화된 StepPayload(primaryText, data)로 응답을 받는다.
	 * data는 그대로 StepOutput.data()가 되어 runtime.workflow.WorkFlowExecutor가 {stepId.키}로 variables에 병합해준다.
	 *
	 * 응답을 StepPayload 스키마로 못 읽으면(모델이 스키마 자체를 어김) 이 step은 예외적으로 실패로 처리된다 - AGENT는 원래
	 * "항상 성공"이지만, structuredOutput=true를 켰다는 건 다음 step이 data를 구조적으로 신뢰하고 쓰겠다는 뜻이므로,
	 * 그 계약이 깨졌는데도 성공으로 흘려보내면 다음 step이 엉뚱한 값을 받게 된다(SUPERVISOR의 fail-closed와 같은 이유).
	 * </pre>
	 *
	 * @param execution 실행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent 정의
	 * @param input     렌더링된 입력과 전역 변수
	 */
	private StepOutput runStructuredAgent(WorkFlowExecution execution, AgentDefinition agent, StepInput input) {
		StepPayload payload;
		try {
			payload = this.agentExecutor.callForEntity(agent, execution.sessionId(), execution.caller(), input.variables(), input.renderedText(), StepPayload.class);
		} catch (Exception e) {
			String reason = "Agent 응답을 구조화된 형식(primaryText/data)으로 해석하지 못했습니다 - " + e.getMessage();
			return StepOutput.failure(input.renderedText(), reason);
		}
		if (payload == null || StringUtil.isEmpty(payload.primaryText())) {
			return StepOutput.failure(input.renderedText(), "Agent가 primaryText 없는 구조화 응답을 반환했습니다.");
		}
		return StepOutput.successWithData(payload.primaryText(), payload.data());
	}

	/**
	 * <pre>
	 * ROUTER step - 자유 텍스트/Verdict 대신 구조화된 RouteDecision(route, reason)으로 응답을 받는다. 이 step 자체는 항상
	 * SUCCESS로 취급되고(라우팅은 판정이 아니라 선택이므로), 고른 route가 StepDefinition.routes에 실제로 있는지 + 그 route를
	 * 따라 어디로 갈지는 runtime.workflow.WorkFlowExecutor.decideTransition이 이어서 처리한다.
	 *
	 * 응답을 RouteDecision 스키마로 못 읽으면(모델이 스키마를 어김) 이 step은 실패로 처리된다 - route를 하나도 못 골랐는데
	 * 진행할 수는 없으므로 runStructuredAgent와 같은 이유로 fail-closed다.
	 * </pre>
	 *
	 * @param execution 실행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent 정의
	 * @param input     렌더링된 입력과 전역 변수
	 */
	private StepOutput runRouter(WorkFlowExecution execution, AgentDefinition agent, StepInput input) {
		RouteDecision decision;
		try {
			decision = this.agentExecutor.callForEntity(agent, execution.sessionId(), execution.caller(), input.variables(), input.renderedText(), RouteDecision.class);
		} catch (Exception e) {
			String reason = "라우팅 Agent 응답을 구조화된 형식(route/reason)으로 해석하지 못했습니다 - " + e.getMessage();
			return StepOutput.failure(input.renderedText(), reason);
		}
		if (decision == null || StringUtil.isEmpty(decision.route())) {
			return StepOutput.failure(input.renderedText(), "라우팅 Agent가 route를 고르지 않았습니다.");
		}
		return StepOutput.routed(input.renderedText(), Map.of(), decision.route());
	}

	/**
	 * <pre>
	 * SUPERVISOR step - AGENT와 똑같이 Agent를 호출하지만, 응답을 자유 텍스트가 아니라 구조화된 Verdict(pass/reason)로 받아서 그걸로 성공/실패를 가른다.
	 * (runtime.agent.AgentExecutor.callForVerdict 참고. Spring AI가 Verdict의 JSON 스키마를 프롬프트에 자동으로 삽입하고 응답을 그 스키마에 맞춰 파싱해준다).
	 *
	 * 그래도 100% 확정적인 건 아니다 - 모델이 스키마 자체를 어기면 entity() 파싱이 예외를 던지는데,
	 * 그런 경우와 verdict.pass()가 명시적으로 false인 경우를 구분하지 않고 둘 다 실패로 묶는다
	 * (fail-closed - 판정을 신뢰할 수 없으면 안전한 쪽인 실패로 처리한다).
	 * </pre>
	 *
	 * @param execution 실행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent 정의
	 * @param input     렌더링된 입력과 전역 변수
	 */
	private StepOutput runSupervisor(WorkFlowExecution execution, AgentDefinition agent, StepInput input) {
		Verdict verdict;
		try {
			verdict = this.agentExecutor.callForVerdict(agent, execution.sessionId(), execution.caller(), input.variables(), input.renderedText());
		} catch (Exception e) {
			String reason = "감독 Agent 응답을 구조화된 형식(pass/reason)으로 해석하지 못했습니다 - " + e.getMessage();
			return StepOutput.failure(input.renderedText() + "\n\n[검토 결과] 실패: " + reason, reason);
		}
		if (verdict != null && verdict.pass()) {
			return StepOutput.success(input.renderedText());
		}
		String reason = verdict == null || StringUtil.isEmpty(verdict.reason()) ? "(사유 없음)" : verdict.reason();
		return StepOutput.failure(input.renderedText() + "\n\n[검토 결과] 실패: " + reason, reason);
	}

}

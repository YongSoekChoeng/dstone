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
 * Workflow의 세 가지 step 종류(AGENT, SUPERVISOR, ROUTER)를 처리하는 클래스입니다. 셋 다 공통적으로
 * StepDefinition.ref()에 적힌 이름의 Agent를 호출한다는 점은 같지만, 응답을 어떤 형태로 받고 그걸로
 * 무엇을 판단하는지가 다릅니다.
 *
 * - AGENT: 기본값(structuredOutput=false)이면 LLM이 자유롭게 쓴 텍스트를 그대로 받습니다.
 *   structuredOutput=true로 설정하면 자유 텍스트 대신 정해진 구조(StepPayload)로 응답을 받습니다.
 * - SUPERVISOR: 자유 텍스트가 아니라 "통과했는지 아닌지"를 담은 구조화된 응답(Verdict의 pass/reason)을
 *   받아서, 그 값으로 이 step의 성공/실패를 결정합니다.
 * - ROUTER: 역시 자유 텍스트가 아니라 "다음에 어디로 갈지"를 담은 구조화된 응답(RouteDecision의
 *   route/reason)을 받아서, 그 값으로 Workflow가 다음에 어느 step으로 이동할지를 정합니다.
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
	 * AGENT step을 처리합니다(structuredOutput이 false이거나 아예 설정되지 않은, 가장 기본적인 경우입니다).
	 * LLM이 답한 자유 텍스트를 그대로 다음 step으로 넘기고, 이 step 자체는 항상 성공으로 취급합니다.
	 * </pre>
	 *
	 * @param execution 지금 진행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent의 정의
	 * @param input     이 step에 들어온 입력 텍스트와 전역 변수
	 */
	private StepOutput runAgent(WorkFlowExecution execution, AgentDefinition agent, StepInput input) {
		// Workflow의 step에서 Agent를 부를 때는 항상 (null, null, null)을 넘겨서 Agent 정의값을 그대로 씁니다.
		// 요청마다 RAG/Tool/모델을 바꿔서 호출하는 기능(ragOverride/toolsOverride/modelOverride)은
		// api.controller.ChatController처럼 Agent 하나를 직접 호출하는 화면에서만 쓰는 기능입니다.
		String answer = this.agentExecutor.call(agent, execution.sessionId(), execution.caller(), input.variables(), input.renderedText(), null, null, null);
		return StepOutput.success(answer);
	}

	/**
	 * <pre>
	 * structuredOutput=true로 설정된 AGENT step을 처리합니다. 자유 텍스트 대신, 정해진 구조를 가진
	 * StepPayload(primaryText와 data 두 필드)로 응답을 받습니다. 여기서 받은 data는 그대로
	 * StepOutput.data()에 담기고, runtime.workflow.WorkFlowExecutor가 이 값을 {stepId.키}라는 이름으로
	 * variables에 합쳐 넣어줍니다 - 그래서 다음 step이 이 데이터를 참조할 수 있게 됩니다.
	 *
	 * 만약 LLM 응답을 StepPayload 구조로 해석하지 못하면(모델이 정해진 형식을 지키지 않은 경우) 이
	 * step은 실패로 처리됩니다. 원래 AGENT step은 "항상 성공"으로 취급하는 게 기본 원칙이지만, 여기서는
	 * 예외를 둡니다. structuredOutput=true로 설정했다는 것은 "다음 step이 이 data 값을 믿고 그대로
	 * 쓰겠다"는 뜻인데, 만약 형식이 깨진 응답을 그냥 성공으로 흘려보내면 다음 step이 엉뚱한 값을
	 * 받게 되기 때문입니다(SUPERVISOR step에서 판정이 불확실할 때 안전하게 실패로 처리하는 것과
	 * 같은 이유입니다).
	 * </pre>
	 *
	 * @param execution 지금 진행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent의 정의
	 * @param input     이 step에 들어온 입력 텍스트와 전역 변수
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
	 * ROUTER step을 처리합니다. 자유 텍스트나 Verdict가 아니라, "어디로 갈지"를 담은 구조화된
	 * RouteDecision(route와 reason 두 필드)으로 응답을 받습니다. 이 step 자체는 라우팅이 판정이 아니라
	 * 선택이기 때문에 항상 SUCCESS(성공)로 취급됩니다. LLM이 고른 route 값이 StepDefinition.routes에
	 * 실제로 정의되어 있는지 확인하고, 그 route에 맞춰 다음에 어느 step으로 이동할지 정하는 일은
	 * runtime.workflow.WorkFlowExecutor.decideTransition이 이어받아 처리합니다.
	 *
	 * 만약 LLM 응답을 RouteDecision 구조로 해석하지 못하면(모델이 정해진 형식을 지키지 않은 경우) 이
	 * step은 실패로 처리됩니다. 어디로 가야 할지 하나도 고르지 못한 상태로 계속 진행할 수는 없기
	 * 때문입니다 - runStructuredAgent와 같은 이유로, 판단이 불확실하면 안전하게 실패로 처리합니다.
	 * </pre>
	 *
	 * @param execution 지금 진행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent의 정의
	 * @param input     이 step에 들어온 입력 텍스트와 전역 변수
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
	 * SUPERVISOR step을 처리합니다. AGENT step과 마찬가지로 Agent를 호출하지만, 응답을 자유 텍스트가
	 * 아니라 "통과했는지 아닌지"를 담은 구조화된 Verdict(pass와 reason 두 필드)로 받아서, 그 값으로
	 * 이 step의 성공/실패를 결정합니다(자세한 호출 방식은 runtime.agent.AgentExecutor.callForVerdict를
	 * 참고하세요 - Spring AI가 Verdict의 JSON 형태를 자동으로 프롬프트에 알려주고, LLM의 답변을 그
	 * 형태에 맞게 파싱해 줍니다).
	 *
	 * 다만 이 방식도 100% 완벽하지는 않습니다. LLM이 정해진 형식을 지키지 않으면 파싱 과정에서
	 * 예외가 발생하는데, 이 경우와 "verdict.pass()가 명확히 false로 나온 경우"를 굳이 구분하지 않고
	 * 둘 다 똑같이 실패로 처리합니다. 판정 결과를 믿을 수 없는 상황이라면, 안전한 쪽인 "실패"로
	 * 처리하는 것이 맞기 때문입니다.
	 * </pre>
	 *
	 * @param execution 지금 진행 중인 Workflow 실행 상태
	 * @param agent     호출할 Agent의 정의
	 * @param input     이 step에 들어온 입력 텍스트와 전역 변수
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

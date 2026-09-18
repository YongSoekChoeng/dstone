package net.dstone.ai.runtime.step;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.common.definition.StepType;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.status.StepInput;
import net.dstone.ai.runtime.status.StepOutput;
import net.dstone.ai.runtime.status.Verdict;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.utils.StringUtil;

/** AGENT/SUPERVISOR step - ref로 지정된 Agent를 호출한다. SUPERVISOR만 구조화된 Verdict(pass/reason)로 성공/실패를 가른다. */
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
		return this.runAgent(execution, agent, input);
	}

	/**
	 * <pre>
	 * AGENT step - 항상 성공으로 취급된다.
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

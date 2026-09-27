package net.dstone.ai.runtime.step;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.FieldDefinition;
import net.dstone.ai.common.definition.workflow.step.SupervisorStepDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * type: SUPERVISOR step(SupervisorStepDefinition)을 실행합니다. LLM에게 "통과했는지 아닌지, 그리고 왜 그런지"를
 * {pass, reason} 모양의 JSON으로 답하게 해서, 그 값으로 이 step의 성공/실패를 정합니다.
 * 답의 모양은 AGENT step의 output과 똑같은 방식(AgentExecutor.callForSchema)으로 강제하고 검사합니다.
 *
 * - 통과: 받은 input을 결과 텍스트로 그대로 넘기고, output에 {pass, reason}을 남깁니다.
 * - 불통과: 실패로 처리하고, reason을 실패 사유(error)로 남깁니다.
 * - 답의 모양이 깨짐: 판정을 믿을 수 없으므로 안전하게 실패로 처리합니다.
 *
 * SUPERVISOR는 "판정"만 하는 관문이라서 받은 input을 결과 텍스트로 그대로 넘깁니다. 판정 사유는
 * 다음 step이 필요할 때 {{steps.id.output.reason}}이나 {{steps.id.error}}로 따로 꺼내 씁니다.
 * </pre>
 */
@Component
public class SupervisorStepExecutor {

	/** LLM이 지켜야 할 답의 모양입니다: {pass: boolean, reason: string} */
	private static final Map<String, FieldDefinition> VERDICT = new LinkedHashMap<>();
	static {
		VERDICT.put("pass", new FieldDefinition("boolean", "통과면 true, 통과하지 못했으면 false"));
		VERDICT.put("reason", new FieldDefinition("string", "그렇게 판정한 이유"));
	}

	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private AgentExecutor agentExecutor;

	/**
	 * SUPERVISOR step 하나를 실행합니다(판정 규칙은 클래스 설명 참고).
	 *
	 * @param execution      지금 진행 중인 Workflow 실행 상태입니다.
	 * @param step           실행할 step의 정의입니다.
	 * @param input          템플릿이 채워진 사용자 메시지(판정할 대상)입니다.
	 * @param workflowInputs Workflow를 실행할 때 넘긴 값(컨텍스트의 inputs)입니다. Agent system prompt의 {변수}를 채웁니다.
	 */
	public StepOutcome run(WorkFlowExecution execution, SupervisorStepDefinition step, String input, Map<String, Object> workflowInputs) {
		AgentDefinition agent = this.agentRegistry.resolve(step.ref(), execution.caller());
		Map<String, Object> answer;
		try {
			answer = this.agentExecutor.callForSchema(agent, execution.sessionId(), execution.caller(), workflowInputs, input, VERDICT);
		} catch (Exception e) {
			return StepOutcome.failure(input, "감독 Agent 응답을 {pass, reason} 모양으로 읽지 못했습니다 - " + e.getMessage());
		}
		Object reasonValue = answer.get("reason");
		String reason = reasonValue == null || StringUtil.isEmpty(reasonValue.toString()) ? "(사유 없음)" : reasonValue.toString();
		if (Boolean.TRUE.equals(answer.get("pass"))) {
			Map<String, Object> output = new LinkedHashMap<>();
			output.put("pass", true);
			output.put("reason", reason);
			return StepOutcome.success(input, output);
		}
		return StepOutcome.failure(input, reason);
	}

}

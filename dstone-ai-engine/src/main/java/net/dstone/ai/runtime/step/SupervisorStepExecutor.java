package net.dstone.ai.runtime.step;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.step.SupervisorStepDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.common.schema.StepOutputSchemas;
import net.dstone.ai.runtime.agent.AgentContractException;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * type: SUPERVISOR step(SupervisorStepDefinition)을 실행합니다. LLM에게 "통과했는지 아닌지, 그리고 왜 그런지"를
 * {pass, reason} 모양의 JSON으로 답하게 해서, 그 값으로 이 step의 성공/실패를 정합니다.
 * 답의 모양은 엔진이 정한 스키마(common.schema.StepOutputSchemas.verdict())로 강제하고 검사합니다.
 *
 * - 통과: 성공이고, output에 {pass: true, reason}을 남깁니다.
 * - 불통과: 실패이고, output에 {pass: false, reason}을, error에 reason을 남깁니다.
 * - 답의 모양이 깨짐(또는 input이 Agent input 모양이 아님): 판정을 믿을 수 없으므로 안전하게 실패로 처리합니다.
 *
 * 판정 사유는 다음 step이 필요할 때 {{steps.id.output.reason}}이나 {{steps.id.error}}로 꺼내 씁니다.
 * </pre>
 */
@Component
public class SupervisorStepExecutor {

	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private AgentExecutor agentExecutor;

	/**
	 * SUPERVISOR step 하나를 실행합니다(판정 규칙은 클래스 설명 참고).
	 *
	 * @param execution       지금 진행 중인 Workflow 실행 상태입니다.
	 * @param step            실행할 step의 정의입니다.
	 * @param input           템플릿이 채워진 input(판정할 대상)입니다.
	 * @param promptVariables Agent system prompt의 {변수}를 채울 값입니다(Workflow input이 object면 그 필드들).
	 */
	@SuppressWarnings("unchecked")
	public StepOutcome run(WorkFlowExecution execution, SupervisorStepDefinition step, Object input, Map<String, Object> promptVariables) {
		AgentDefinition agent = this.agentRegistry.resolve(step.ref(), execution.caller());
		Map<String, Object> answer;
		try {
			answer = (Map<String, Object>) this.agentExecutor.callForSchema(agent, execution.sessionId(), execution.caller(), promptVariables, input, StepOutputSchemas.verdict());
		} catch (AgentContractException e) {
			return StepOutcome.failure(null, "감독 Agent 응답을 {pass, reason} 모양으로 받지 못했습니다 - " + e.getMessage());
		}
		Object reasonValue = answer.get("reason");
		String reason = reasonValue == null || StringUtil.isEmpty(reasonValue.toString()) ? "(사유 없음)" : reasonValue.toString();
		boolean pass = Boolean.TRUE.equals(answer.get("pass"));
		Map<String, Object> output = new LinkedHashMap<>();
		output.put("pass", pass);
		output.put("reason", reason);
		return pass ? StepOutcome.success(output) : StepOutcome.failure(output, reason);
	}

}

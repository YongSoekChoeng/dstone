package net.dstone.ai.runtime.step;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.step.AgentStepDefinition;
import net.dstone.ai.common.exception.AgentContractException;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * <pre>
 * type: AGENT step(AgentStepDefinition)을 실행합니다. ref에 적힌 Agent를 한 번 부르고, 채워진 input을 넣습니다.
 * 받는 값과 돌려주는 값의 모양은 Agent가 정합니다(agents/*.yml 의 input/output).
 *
 *   Agent output         steps.id.output에 남는 값         실패하는 경우
 *   string(기본)         LLM 답변 원문(글자)               input이 Agent input 모양이 아님
 *   object 등            output 모양대로 읽은 값(맵 등)      위 경우 + LLM이 output 모양을 지키지 않음
 *
 * Workflow의 step에서 Agent를 부를 때는 요청마다 RAG/Tool/모델을 바꾸는 기능(ragOverride 등)을 쓰지 않고
 * 항상 Agent 정의값을 그대로 씁니다. 그 기능은 api.controller.ChatController처럼 Agent 하나를 직접 부르는
 * 화면에서만 씁니다.
 * </pre>
 */
@Component
public class AgentStepExecutor {

	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private AgentExecutor agentExecutor;

	/**
	 * <pre>
	 * AGENT step 하나를 실행합니다. ref의 Agent를 찾고(caller가 쓸 수 있는 Agent인지도 함께 검사합니다),
	 * Agent의 답을 output으로 남깁니다.
	 *
	 * 넣은 값이나 LLM의 답이 Agent 계약과 맞지 않으면 실패로 처리합니다(onFailure를 따릅니다).
	 * 계약을 선언했다는 것은 다음 step이 그 값을 믿고 그대로 쓰겠다는 뜻이므로, 모양이 깨진 답을 성공으로 넘기지 않습니다.
	 * </pre>
	 *
	 * @param execution       지금 진행 중인 Workflow 실행 상태입니다.
	 * @param step            실행할 step의 정의입니다.
	 * @param input           템플릿이 채워진 input입니다(Agent input 모양).
	 * @param promptVariables Agent system prompt의 {변수}를 채울 값입니다(Workflow input이 object면 그 필드들).
	 */
	public StepOutcome run(WorkFlowExecution execution, AgentStepDefinition step, Object input, Map<String, Object> promptVariables) {
		AgentDefinition agent = this.agentRegistry.resolve(step.ref(), execution.caller());
		try {
			return StepOutcome.success(this.agentExecutor.call(agent, execution.sessionId(), execution.caller(), promptVariables, input, null, null, null));
		} catch (AgentContractException e) {
			return StepOutcome.failure(null, e.getMessage());
		}
	}

}

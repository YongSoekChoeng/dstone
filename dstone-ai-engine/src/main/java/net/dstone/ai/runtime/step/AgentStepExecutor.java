package net.dstone.ai.runtime.step;

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
	 * @param input           표현식을 계산해 채운 input입니다(Agent input 모양).
	 */
	public StepOutcome run(WorkFlowExecution execution, AgentStepDefinition step, Object input) {
		AgentDefinition agent = this.agentRegistry.resolve(step.ref(), execution.caller());
		try {
			
			return StepOutcome.success(
				this.agentExecutor.call(
					agent								// agent. 호출할 Agent의 정의(프롬프트, 입출력 계약, Tool/RAG 사용 여부 등)
					, execution.conversationIdOf(step)	// conversationId. 대화방 id. 이 대화방의 이전 대화를 기억해서 이어 갑니다. null이면 대화 기억 없이 부릅니다
					, execution.caller()				// caller. 이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
					, null								// variables. 프롬프트의 {변수명}에 input 필드 말고 더 채울 값들(채팅 API 전용, 없으면 null)
					, input								// input. Agent에게 넣을 값(Agent input 모양)
					, null								// ragOverride 이번 호출에서만 RAG 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
					, null								// toolsOverride 이번 호출에서만 Tool 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
					, null								// modelOverride 이번 호출에서만 쓸 모델명을 강제로 지정하고 싶을 때 씀(null이면 agent.model()을 쓰고, 그것도 없으면 provider 공통 기본 모델을 씀)
				)
			);
		} catch (AgentContractException e) {
			return StepOutcome.failure(null, e.getMessage());
		}
	}

}

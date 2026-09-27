package net.dstone.ai.runtime.step;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.step.AgentStepDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * <pre>
 * type: AGENT step(AgentStepDefinition)을 실행합니다. ref에 적힌 Agent를 한 번 부르고, 채워진 input을 사용자 메시지로 보냅니다.
 *
 *   YAML                    text(결과 텍스트)        output(구조화된 결과)   실패하는 경우
 *   output 없음             LLM 답변 원문            없음                    없음(항상 성공)
 *   output 있음             output을 JSON 글자로     output대로 읽은 값      LLM이 output 모양을 지키지 않음
 *
 * Workflow의 step에서 Agent를 부를 때는 요청마다 RAG/Tool/모델을 바꾸는 기능(ragOverride 등)을 쓰지 않고
 * 항상 Agent 정의값을 그대로 씁니다. 그 기능은 api.controller.ChatController처럼 Agent 하나를 직접 부르는
 * 화면에서만 씁니다.
 * </pre>
 */
@Component
public class AgentStepExecutor {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private AgentExecutor agentExecutor;

	/**
	 * <pre>
	 * AGENT step 하나를 실행합니다. ref의 Agent를 찾고(caller가 쓸 수 있는 Agent인지도 함께 검사합니다),
	 * output이 없으면 답 원문을, 있으면 그 모양으로 읽은 JSON을 결과로 남깁니다.
	 *
	 * output을 선언했는데 LLM이 그 모양을 지키지 않으면(필드가 빠졌거나 타입이 다르면) 실패로 처리합니다.
	 * output을 선언했다는 것은 다음 step이 그 값을 믿고 그대로 쓰겠다는 뜻이므로, 모양이 깨진 답을 성공으로 넘기지 않습니다.
	 * </pre>
	 *
	 * @param execution      지금 진행 중인 Workflow 실행 상태입니다.
	 * @param step           실행할 step의 정의입니다.
	 * @param input          템플릿이 채워진 사용자 메시지입니다.
	 * @param workflowInputs Workflow를 실행할 때 넘긴 값(컨텍스트의 inputs)입니다. Agent system prompt의 {변수}를 채웁니다.
	 */
	public StepOutcome run(WorkFlowExecution execution, AgentStepDefinition step, String input, Map<String, Object> workflowInputs) {
		AgentDefinition agent = this.agentRegistry.resolve(step.ref(), execution.caller());
		if (step.output() == null) {
			String answer = this.agentExecutor.call(agent, execution.sessionId(), execution.caller(), workflowInputs, input, null, null, null);
			return StepOutcome.success(answer);
		}
		Map<String, Object> output;
		try {
			output = this.agentExecutor.callForSchema(agent, execution.sessionId(), execution.caller(), workflowInputs, input, step.output());
		} catch (Exception e) {
			return StepOutcome.failure(null, "Agent 응답을 output 모양으로 읽지 못했습니다 - " + e.getMessage());
		}
		return StepOutcome.success(this.toJson(output), output);
	}

	/**
	 * output을 결과 텍스트로 남길 JSON 글자로 바꿉니다.
	 *
	 * @param output JSON 글자로 바꿀 값입니다.
	 */
	private String toJson(Map<String, Object> output) {
		try {
			return this.objectMapper.writeValueAsString(output);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Agent 응답 output를 JSON으로 바꾸지 못했습니다: " + output, e);
		}
	}

}

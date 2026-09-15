package net.dstone.ai.runtime.step;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.StepOutcome;
import net.dstone.ai.runtime.agent.AgentExecutor;

/** AGENT/SUPERVISOR step - ref로 지정된 Agent를 호출한다. SUPERVISOR만 "실패" 접두사로 성공/실패를 가른다. */
@Component
public class AgentStepRunner {

	private static final String FAILURE_PREFIX = "실패";

	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private AgentExecutor agentExecutor;

	/**
	 * AGENT step - 항상 성공으로 취급된다.
	 * @param ref 호출할 Agent 이름
	 * @param sessionId 대화 세션 식별자
	 * @param caller 호출한 앱/서비스 식별자
	 * @param variables 프롬프트 템플릿에 바인딩할 변수 맵
	 * @param input 사용자 입력 텍스트
	 */
	public StepOutcome runAgent(String ref, String sessionId, String caller, Map<String, Object> variables, String input) {
		return new StepOutcome(true, this.call(ref, sessionId, caller, variables, input));
	}

	/**
	 * SUPERVISOR step - AGENT와 똑같이 Agent를 호출하지만, 그 프롬프트가 "통과: .../실패: 이유" 형식으로
	 * 답하도록 작성돼 있다고 가정하고 그 텍스트로 성공/실패를 가른다(TOOL과 같은 판정 컨벤션). 여러 step의
	 * 결과를 감독/재검토하는 역할이라, 강제 장치는 없고 프롬프트 설계로 지켜야 하는 관례다.
	 * @param ref 호출할 Agent 이름
	 * @param sessionId 대화 세션 식별자
	 * @param caller 호출한 앱/서비스 식별자
	 * @param variables 프롬프트 템플릿에 바인딩할 변수 맵
	 * @param input 사용자 입력 텍스트
	 */
	public StepOutcome runSupervisor(String ref, String sessionId, String caller, Map<String, Object> variables, String input) {
		String result = this.call(ref, sessionId, caller, variables, input);
		if (result.startsWith(FAILURE_PREFIX)) {
			return new StepOutcome(false, input + "\n\n[검토 결과] " + result);
		}
		return new StepOutcome(true, input);
	}

	/**
	 * @param ref 호출할 Agent 이름
	 * @param sessionId 대화 세션 식별자
	 * @param caller 호출한 앱/서비스 식별자
	 * @param variables 프롬프트 템플릿에 바인딩할 변수 맵
	 * @param input 사용자 입력 텍스트
	 */
	private String call(String ref, String sessionId, String caller, Map<String, Object> variables, String input) {
		AgentDefinition agent = this.agentRegistry.resolve(ref, caller);
		// Workflow step은 항상 Agent 정의값 그대로 쓴다(null, null) - 요청별 RAG/Tool 오버라이드는
		// api.controller.ChatController(단일 Agent 직접 호출)에만 있는 기능이다.
		return this.agentExecutor.call(sessionId, caller, agent, variables, input, null, null);
	}

}

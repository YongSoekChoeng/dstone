package net.dstone.ai.runtime.step;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.StepOutcome;
import net.dstone.ai.runtime.agent.AgentExecutor;

/** AGENT/SUPERVISOR step - ref로 지정된 Agent를 호출한다. SUPERVISOR만 "통과"/"실패" 접두사로 성공/실패를 가른다. */
@Component
public class AgentStepRunner {

	private static final String PASS_PREFIX = "통과";

	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private AgentExecutor agentExecutor;

	/**
	 * AGENT step - 항상 성공으로 취급된다.
	 * 
	 * @param ref       호출할 Agent 이름
	 * @param sessionId 대화 세션 식별자
	 * @param caller    호출한 앱/서비스 식별자
	 * @param variables 프롬프트 템플릿에 바인딩할 변수 맵
	 * @param input     사용자 입력 텍스트
	 */
	public StepOutcome runAgent(String ref, String sessionId, String caller, Map<String, Object> variables, String input) {
		return new StepOutcome(true, this.call(ref, sessionId, caller, variables, input));
	}

	/**
	 * SUPERVISOR step - AGENT와 똑같이 Agent를 호출하지만, 그 프롬프트가 "통과: .../실패: 이유" 형식으로 답하도록 작성돼 있다고 가정하고 그 텍스트로 성공/실패를 가른다(TOOL과 같은 판정 컨벤션).
	 * 여러 step의 결과를 감독/재검토하는 역할이라, 강제 장치는 없고 프롬프트 설계로 지켜야 하는 관례다.
	 * LLM이 그 컨벤션을 안 지킬 수 있다는 전제로, "통과"로 시작하는 경우에만 성공으로 인정하고(코드펜스나 다른 표현으로 답하는 등) 그 외에는 전부 실패로 처리한다(fail-closed). 
	 * TOOL step은 응답이 결정론적인 자바 코드(SqlSyntaxTools 등)에서 나오므로 fail-open("실패"로 시작할 때만 실패)이어도 안전하지만, 
	 * SUPERVISOR는 LLM이 만든 텍스트라 같은 방식이면 컨벤션을 벗어난 응답을 조용히 성공으로 흘려보낼 위험이 있다.
	 *
	 * @param ref       호출할 Agent 이름
	 * @param sessionId 대화 세션 식별자
	 * @param caller    호출한 앱/서비스 식별자
	 * @param variables 프롬프트 템플릿에 바인딩할 변수 맵
	 * @param input     사용자 입력 텍스트
	 */
	public StepOutcome runSupervisor(String ref, String sessionId, String caller, Map<String, Object> variables, String input) {
		String result = this.sanitizeForJudgment(this.call(ref, sessionId, caller, variables, input));
		if (result.startsWith(PASS_PREFIX)) {
			return new StepOutcome(true, input);
		}
		return new StepOutcome(false, input + "\n\n[검토 결과] " + result);
	}

	/**
	 * "통과"/"실패" 판정 전에 선행/후행 공백과 마크다운 코드펜스(```)를 벗겨낸다.
	 * LLM이 컨벤션 자체는 지키면서도 앞뒤에 공백이나 코드펜스를 붙이는 바람에 접두사 판정이 깨지는 것을 막는 최소한의 방어 코드다
	 * (코드펜스를 완전히 무시하고 아예 다른 형식으로 답하는 경우까지는 못 막지만, 그런 경우는 fail-closed 판정으로 실패 처리된다).
	 * @param text 판정에 쓸 원본 응답
	 */
	private String sanitizeForJudgment(String text) {
		String trimmed = text == null ? "" : text.trim();
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			trimmed = firstNewline < 0 ? "" : trimmed.substring(firstNewline + 1).trim();
		}
		if (trimmed.endsWith("```")) {
			trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
		}
		return trimmed;
	}

	/**
	 * @param ref       호출할 Agent 이름
	 * @param sessionId 대화 세션 식별자
	 * @param caller    호출한 앱/서비스 식별자
	 * @param variables 프롬프트 템플릿에 바인딩할 변수 맵
	 * @param input     사용자 입력 텍스트
	 */
	private String call(String ref, String sessionId, String caller, Map<String, Object> variables, String input) {
		AgentDefinition agent = this.agentRegistry.resolve(ref, caller);
		// Workflow step은 항상 Agent 정의값 그대로 쓴다(null, null) - 요청별 ragOverride/toolsOverride 는
		// api.controller.ChatController(단일 Agent 직접 호출)에만 있는 기능이다.
		return this.agentExecutor.call(sessionId, caller, agent, variables, input, null, null);
	}

}

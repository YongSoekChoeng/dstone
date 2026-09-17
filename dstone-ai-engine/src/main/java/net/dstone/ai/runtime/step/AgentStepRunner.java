package net.dstone.ai.runtime.step;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.StepOutcome;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.agent.Verdict;
import net.dstone.common.utils.StringUtil;

/** AGENT/SUPERVISOR step - ref로 지정된 Agent를 호출한다. SUPERVISOR만 구조화된 Verdict(pass/reason)로 성공/실패를 가른다. */
@Component
public class AgentStepRunner {

	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private AgentExecutor agentExecutor;

	/**
	 * <pre>
	 * AGENT step - 항상 성공으로 취급된다.
	 * </pre>
	 *
	 * @param ref       호출할 Agent 이름
	 * @param sessionId 대화 세션 식별자
	 * @param caller    호출한 앱/서비스 식별자
	 * @param variables 프롬프트 템플릿에 바인딩할 변수 맵
	 * @param input     사용자 입력 텍스트
	 */
	public StepOutcome runAgent(String ref, String sessionId, String caller, Map<String, Object> variables, String input) {
		String answer = this.call(ref, sessionId, caller, variables, input);
		return new StepOutcome(true, answer);
	}

	/**
	 * <pre>
	 * SUPERVISOR step - AGENT와 똑같이 Agent를 호출하지만, 응답을 자유 텍스트가 아니라 구조화된 Verdict(pass/reason)로 받아서 그걸로 성공/실패를 가른다.
	 * (runtime.agent.AgentExecutor.callForVerdict 참고.
	 * Spring AI가 Verdict의 JSON 스키마를 프롬프트에 자동으로 삽입하고 응답을 그 스키마에 맞춰 파싱해준다).
	 *
	 * 그래도 100% 확정적인 건 아니다 - 모델이 스키마 자체를 어기면 entity() 파싱이 예외를 던지는데,
	 * 그런 경우와 verdict.pass()가 명시적으로 false인 경우를 구분하지 않고 둘 다 실패로 묶는다
	 * (fail-closed - 판정을 신뢰할 수 없으면 안전한 쪽인 실패로 처리한다).
	 * </pre>
	 *
	 * @param ref       호출할 Agent 이름
	 * @param sessionId 대화 세션 식별자
	 * @param caller    호출한 앱/서비스 식별자
	 * @param variables 프롬프트 템플릿에 바인딩할 변수 맵
	 * @param input     사용자 입력 텍스트
	 */
	public StepOutcome runSupervisor(String ref, String sessionId, String caller, Map<String, Object> variables, String input) {
		Verdict verdict;
		try {
			verdict = this.callForVerdict(ref, sessionId, caller, variables, input);
		} catch (Exception e) {
			return new StepOutcome(false, input + "\n\n[검토 결과] 실패: 감독 Agent 응답을 구조화된 형식(pass/reason)으로 해석하지 못했습니다 - " + e.getMessage());
		}
		if (verdict != null && verdict.pass()) {
			return new StepOutcome(true, input);
		}
		String reason = verdict == null || StringUtil.isEmpty(verdict.reason()) ? "(사유 없음)" : verdict.reason();
		return new StepOutcome(false, input + "\n\n[검토 결과] 실패: " + reason);
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

	/**
	 * @param ref       호출할 Agent 이름
	 * @param sessionId 대화 세션 식별자
	 * @param caller    호출한 앱/서비스 식별자
	 * @param variables 프롬프트 템플릿에 바인딩할 변수 맵
	 * @param input     사용자 입력 텍스트
	 */
	private Verdict callForVerdict(String ref, String sessionId, String caller, Map<String, Object> variables, String input) {
		AgentDefinition agent = this.agentRegistry.resolve(ref, caller);
		return this.agentExecutor.callForVerdict(sessionId, caller, agent, variables, input);
	}

}

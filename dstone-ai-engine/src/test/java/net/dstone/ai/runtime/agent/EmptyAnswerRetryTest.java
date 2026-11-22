package net.dstone.ai.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.exception.EmptyAnswerException;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * 모델이 답도 Tool 호출도 없이 끝났을 때(빈 답) 엔진이 어떻게 하는지 봅니다.
 * 2026-11-22 실행 f2cf12b3에서 gpt-oss-120b가 Tool 호출을 추론 글에 쓰고 빈 답으로 멈춘 일이 두 번 있었습니다.
 * - onInvalidOutput을 적지 않은 Agent(한 번만 부름)도 빈 답이면 한 번 더 부릅니다.
 * - 두 번 다 비면 "빈 답"이라는 사유로 실패합니다("JSON이 아닙니다: "로 남지 않습니다).
 * </pre>
 */
public class EmptyAnswerRetryTest extends WorkflowTestBase {

	/** onInvalidOutput이 없는(maxAttempts 1) 가장 단순한 Agent입니다. */
	private static final String AGENT_ID = "sample-basic-echo-agent";

	@Autowired
	private AgentRegistry agentRegistry;

	@Autowired
	private AgentExecutor agentExecutor;

	@BeforeEach
	void skipRealLlm() {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
	}

	@Test
	void 빈_답이면_설정과_상관없이_한_번_더_부른다() {
		AgentDefinition agent = this.agentRegistry.find(AGENT_ID);
		assertThat(agent.maxAttempts()).isEqualTo(1);
		this.chatModel.answer("", "안녕하세요.");

		Object answer = this.agentExecutor.call(agent, null, null, "인사해 줘", null, null, null, null);

		assertThat(answer).isEqualTo("안녕하세요.");
		assertThat(this.chatModel.prompts()).hasSize(2);
	}

	@Test
	void 두_번_다_비면_빈_답_사유로_실패한다() {
		AgentDefinition agent = this.agentRegistry.find(AGENT_ID);
		this.chatModel.answer("", "  ");

		String message = null;
		try {
			this.agentExecutor.call(agent, null, null, "인사해 줘", null, null, null, null);
		} catch (net.dstone.ai.common.exception.AgentContractException e) {
			message = e.getMessage();
		}
		assertThat(message).contains("빈 답");
		assertThat(this.chatModel.prompts()).hasSize(2);
	}

	@Test
	void 빈_답_예외는_계약_위반의_한_종류다() {
		assertThat(new EmptyAnswerException("x")).isInstanceOf(net.dstone.ai.common.exception.AgentContractException.class);
	}
}

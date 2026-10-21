package net.dstone.ai.workflow.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sample-supervisor-verdict-gate: AGENT(sample-role-reply-agent)가 답을 만들고 SUPERVISOR가 판정합니다.
 * draft step의 Agent 프롬프트에 있는 ${input.role} 자리가 step의 with로 넣은 role 값으로 채워지는지도 봅니다.
 * </pre>
 */
public class SampleSupervisorVerdictGateTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-supervisor-verdict-gate";

	@Test
	void 프롬프트의_input_자리는_Agent가_받은_값으로_채워진다() {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 판정 흐름을 확인하는 테스트라 실제 LLM을 부를 때(dstone.ai.test.llmcall)는 건너뜁니다.");
		this.chatModel.answer("안녕하세요, 무엇을 도와드릴까요?", "{\"pass\": true, \"reason\": \"안녕이 들어 있음\"}");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, Map.of("role", "도서관 사서", "message", "인사해 줘"));

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.stepIds(execution)).containsExactly("draft", "judge");
		String draftPrompt = this.chatModel.prompts().get(0).getContents();
		assertThat(draftPrompt).contains("당신은 도서관 사서 역할을 맡은 AI 어시스턴트입니다.").doesNotContain("${input.role}");
	}

}

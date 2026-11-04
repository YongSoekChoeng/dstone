package net.dstone.ai.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.junit.jupiter.api.Test;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.ScriptedChatModel;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * LLM이 받지 않은 Tool을 불렀을 때 실행 전체가 죽지 않고 "실패:" 안내가 돌아가는지 봅니다(UnknownToolGuard).
 * sample-agent-skill의 Agent는 Skill과 validateSqlSyntax만 받으므로, searchInFiles는 이 Agent가 모르는 Tool입니다.
 * </pre>
 */
public class UnknownToolGuardTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-agent-skill";

	@Test
	void 받지_않은_Tool을_부르면_실패_안내가_돌아가고_진짜_Tool은_실행된다() {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 Tool 흐름을 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		// 한 응답에 모르는 Tool과 진짜 Tool을 함께 부릅니다.
		this.chatModel.answerToolCalls("searchInFiles", "{\"keyword\": \"FileService\"}", "validateSqlSyntax", "{\"sql\": \"SELECT 1\"}");
		this.chatModel.answer("결과: 통과");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, "SELECT 1");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.state(execution).get("answer")).isEqualTo("결과: 통과");
		String second = ScriptedChatModel.textOf(this.chatModel.prompts().get(1));
		assertThat(second).contains(UnknownToolGuard.REFUSAL_PREFIX + "'searchInFiles'는 없습니다").contains("validateSqlSyntax");
		// 진짜 Tool의 결과도 함께 들어 있어야 합니다(안내만 있고 Tool이 실행되지 않으면 안 됩니다).
		assertThat(second.split(UnknownToolGuard.REFUSAL_PREFIX, -1)).hasSize(2);
	}

	@Test
	void 안내를_받고도_계속_부르면_step_실패로_끝난다() {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 Tool 흐름을 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		for (int i = 0; i <= UnknownToolGuard.MAX_REFUSALS; i++) {
			this.chatModel.answerToolCalls("searchInFiles", "{\"keyword\": \"FileService\"}");
		}

		WorkFlowExecution execution = this.run(WORKFLOW_ID, "SELECT 1");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(execution.errorMessage()).contains("계속 불러서 중단했습니다").doesNotContain("No ToolCallback found");
		assertThat(this.chatModel.prompts()).hasSize(UnknownToolGuard.MAX_REFUSALS + 1);
	}

}

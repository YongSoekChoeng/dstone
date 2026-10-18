package net.dstone.ai.workflow.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sample-loop-retry-until-valid: TOOL 검증이 실패하면 AGENT(sample-fix-agent)가 SQL을 고치고 다시 검증하는 onFailure 루프입니다.
 *
 * AGENT step의 LLM은 ScriptedChatModel이 대신합니다. 그래서 LLM 없이도 "Agent 계약(output: {sql}) 검사 → save → 다음 step이 읽기"까지
 * 운영과 같은 길을 탑니다. 고친 SQL을 미리 답으로 넣어 두고, 그 값이 재검증을 거쳐 최종 결과가 되는지 봅니다.
 * </pre>
 */
public class SampleLoopRetryUntilValidTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-loop-retry-until-valid";

	@Test
	@SuppressWarnings("unchecked")
	void 틀린_SQL은_Agent가_고친_뒤_재검증을_통과한다() {
		this.chatModel.answer("{\"sql\": \"SELECT * FROM member WHERE id = 1\"}");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, "SELECT * FORM member WHERE id = 1");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(execution.output()).isEqualTo("SELECT * FROM member WHERE id = 1");
		assertThat(this.stepIds(execution)).containsExactly("validate", "fix", "validate");
		assertThat((Map<String, Object>) this.state(execution).get("fixed")).containsEntry("sql", "SELECT * FROM member WHERE id = 1");
		// fix step은 검증에 실패한 SQL과 그 사유를 Agent에게 넘깁니다.
		assertThat(this.chatModel.prompts()).hasSize(1);
		assertThat(this.chatModel.prompts().get(0).getContents()).contains("FORM");
	}

	@Test
	void 올바른_SQL이면_LLM을_부르지_않는다() {
		WorkFlowExecution execution = this.run(WORKFLOW_ID, "SELECT 1 FROM dual");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.stepIds(execution)).containsExactly("validate");
		assertThat(this.chatModel.prompts()).isEmpty();
	}

	@Test
	void Agent가_output_모양을_지키지_않으면_실패로_끝난다() {
		this.chatModel.answer("고친 SQL은 SELECT * FROM member WHERE id = 1 입니다.");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, "SELECT * FORM member WHERE id = 1");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(this.stepIds(execution)).containsExactly("validate", "fix");
	}

}

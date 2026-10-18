package net.dstone.ai.workflow.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sample-tool-chain-basic: TOOL step 두 개(validateSqlSyntax → getCurrentDateTime)를 잇는 Workflow입니다. LLM을 부르지 않습니다.
 * TOOL step의 결과가 save로 state에 저장되고, return의 맵이 그 값들로 채워지는지 봅니다.
 * </pre>
 */
public class SampleToolChainBasicTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-tool-chain-basic";

	@Test
	@SuppressWarnings("unchecked")
	void 올바른_SQL이면_두_step을_거쳐_성공한다() {
		WorkFlowExecution execution = this.run(WORKFLOW_ID, "SELECT 1 FROM dual");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.stepIds(execution)).containsExactly("validate", "now");
		Map<String, Object> output = (Map<String, Object>) execution.output();
		assertThat(output).containsKeys("validation", "checkedAt");
		assertThat((Map<String, Object>) output.get("validation")).containsEntry("success", Boolean.TRUE);
		assertThat(this.chatModel.prompts()).isEmpty();
	}

	@Test
	void 문법이_틀린_SQL이면_첫_step에서_실패로_끝난다() {
		WorkFlowExecution execution = this.run(WORKFLOW_ID, "SELEC 1 FROM dual");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(execution.errorMessage()).contains("문법 오류");
		assertThat(this.stepIds(execution)).containsExactly("validate");
	}

}

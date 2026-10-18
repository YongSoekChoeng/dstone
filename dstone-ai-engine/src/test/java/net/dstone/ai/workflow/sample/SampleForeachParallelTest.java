package net.dstone.ai.workflow.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sample-foreach-parallel: TOOL step 하나를 input.sqlList 항목 수만큼 동시에 실행하는 Workflow입니다. LLM을 부르지 않습니다.
 * forEach의 결과가 항목 순서대로 모이는지, 하나라도 실패하면 step이 실패하는지, input 모양 검사가 실행 전에 막는지 봅니다.
 * </pre>
 */
public class SampleForeachParallelTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-foreach-parallel";

	@Test
	void 모든_SQL이_올바르면_항목_순서대로_결과가_모인다() {
		WorkFlowExecution execution = this.run(WORKFLOW_ID,
			this.json("{\"sqlList\": [\"SELECT 1 FROM dual\", \"SELECT name FROM member WHERE id = 1\"]}"));

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat((List<?>) execution.output()).hasSize(2);
		assertThat(this.stepIds(execution)).containsExactly("validateEach[0]", "validateEach[1]");
	}

	@Test
	void 하나라도_문법이_틀리면_Workflow가_실패한다() {
		WorkFlowExecution execution = this.run(WORKFLOW_ID,
			this.json("{\"sqlList\": [\"SELECT 1 FROM dual\", \"SELEC 1 FROM dual\"]}"));

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(execution.errorMessage()).contains("SELEC");
	}

	@Test
	void input_모양이_다르면_실행하기_전에_거절한다() {
		assertThrows(IllegalArgumentException.class, new Executable() {
			@Override
			public void execute() {
				SampleForeachParallelTest.this.run(WORKFLOW_ID, SampleForeachParallelTest.this.json("{\"sqlList\": \"SELECT 1 FROM dual\"}"));
			}
		});
	}

}

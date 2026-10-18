package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.WorkflowTestBase;

public class WorkFlowTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	@Test
	@SuppressWarnings("unchecked")
	void testSmWorkflow() {
		WorkFlowExecution execution = this.run(WORKFLOW_ID, " D:/AppHome/testApp/workshop/changes/work001[log-error-fix]");

		//assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat( this.stepIds(execution) ).containsExactly("신규개발", "일반");
		
	}

}

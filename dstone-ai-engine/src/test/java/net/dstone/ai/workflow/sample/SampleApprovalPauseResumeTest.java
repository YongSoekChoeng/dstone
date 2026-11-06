package net.dstone.ai.workflow.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sample-approval-pause-resume: APPROVAL step 하나(APPROVED → END, REJECTED → FAIL)로 된 Workflow입니다. LLM을 부르지 않습니다.
 * approval.requireCommentOnReject: true라서 의견 없는 반려는 거절되어야 합니다.
 * </pre>
 */
public class SampleApprovalPauseResumeTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-approval-pause-resume";

	@Test
	@SuppressWarnings("unchecked")
	void 승인하면_성공하고_결정이_최종_결과가_된다() {
		WorkFlowExecution waiting = this.run(WORKFLOW_ID, "승인 테스트");
		assertThat(waiting.status()).isEqualTo(WorkFlowExecutionStatus.WAITING_APPROVAL);
		assertThat(this.pendingApproval(waiting).artifact()).isEqualTo("sample-document");

		WorkFlowExecution done = this.decide(waiting, "APPROVED", null);
		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat((Map<String, Object>) done.output()).containsEntry("decision", "APPROVED").containsEntry("approver", "tester");
	}

	@Test
	void 의견_없는_반려는_거절하고_의견이_있으면_실패로_끝난다() {
		final WorkFlowExecution waiting = this.run(WORKFLOW_ID, "승인 테스트");

		assertThrows(ResponseStatusException.class, new Executable() {
			@Override
			public void execute() {
				SampleApprovalPauseResumeTest.this.decide(waiting, "REJECTED", null);
			}
		});

		WorkFlowExecution rejected = this.decide(waiting, "REJECTED", "내용이 부족합니다");
		assertThat(rejected.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
	}

	@Test
	void 같은_승인_대기에_두_번_결정하면_한_번만_받고_나중_것은_409로_거절한다() {
		final WorkFlowExecution waiting = this.run(WORKFLOW_ID, "승인 테스트");

		this.workFlowExecutionService.decide(waiting.executionId(), "APPROVED", null, "dev1", null);
		ResponseStatusException second = assertThrows(ResponseStatusException.class, new Executable() {
			@Override
			public void execute() {
				SampleApprovalPauseResumeTest.this.workFlowExecutionService.decide(waiting.executionId(), "APPROVED", null, "dev2", null);
			}
		});

		assertThat(second.getStatusCode().value()).isEqualTo(409);
		WorkFlowExecution done = this.awaitFinished(waiting.executionId());
		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.stepIds(done)).doesNotHaveDuplicates();
	}

	@Test
	void 예전_화면처럼_approved만_보내도_결정으로_받는다() {
		WorkFlowExecution waiting = this.run(WORKFLOW_ID, "승인 테스트");

		WorkFlowExecution queued = this.workFlowExecutionService.decide(waiting.executionId(), null, Boolean.TRUE, "tester", null);
		assertThat(queued.status()).isEqualTo(WorkFlowExecutionStatus.QUEUED);
		WorkFlowExecution done = this.awaitFinished(waiting.executionId());
		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
	}

}

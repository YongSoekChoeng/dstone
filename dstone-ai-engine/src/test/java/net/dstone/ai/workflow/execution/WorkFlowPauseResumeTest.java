package net.dstone.ai.workflow.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.ai.api.dto.ResumePoint;
import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionRecovery;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * 일시정지, 재개, 실패 지점부터 재실행, 재시작 복구, 목록 검색을 확인합니다.
 * step 사이에 요청을 끼워 넣으려고 WorkFlowExecutor.run(workflow, execution, 1)로 한 step씩 돌립니다.
 * </pre>
 */
public class WorkFlowPauseResumeTest extends WorkflowTestBase {

	private static final String TOOL_WORKFLOW = "sample-tool-chain-basic";
	private static final String AGENT_WORKFLOW = "sample-supervisor-verdict-gate";

	@Autowired
	private WorkFlowExecutionRecovery recovery;

	/** 새 실행을 저장만 하고 돌리지는 않습니다. */
	private WorkFlowExecution newExecution(String workflowId, Object input) {
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(workflowId, null);
		WorkFlowExecution execution = WorkFlowExecution.start(UUID.randomUUID().toString(), workflowId, null, "test-pause", WorkFlowContext.create(input, workflowId, workflow.version()));
		this.workFlowExecutionStore.insert(execution);
		return execution;
	}

	/** 비동기로 이어 간 실행이 RUNNING을 벗어날 때까지 기다립니다. */
	private WorkFlowExecution await(String executionId) throws InterruptedException {
		for (int i = 0; i < 300; i++) {
			WorkFlowExecution execution = this.workFlowExecutionStore.find(executionId);
			if (execution.status() != WorkFlowExecutionStatus.RUNNING) {
				return execution;
			}
			Thread.sleep(100L);
		}
		throw new AssertionError("실행이 30초 안에 끝나지 않았습니다: " + executionId);
	}

	@Test
	void 일시정지하면_다음_step_전에_멈추고_재개하면_그_step부터_이어_간다() throws Exception {
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(TOOL_WORKFLOW, null);
		WorkFlowExecution first = this.workFlowExecutor.run(workflow, this.newExecution(TOOL_WORKFLOW, "SELECT 1 FROM dual"), 1);
		assertThat(first.status()).isEqualTo(WorkFlowExecutionStatus.RUNNING);

		this.workFlowExecutionService.pause(first.executionId());
		assertThat(this.workFlowExecutionService.isPauseRequested(first.executionId())).isTrue();
		WorkFlowExecution paused = this.workFlowExecutor.run(workflow, first);

		assertThat(paused.status()).isEqualTo(WorkFlowExecutionStatus.PAUSED);
		assertThat(this.stepIds(paused)).containsExactly("validate");
		assertThat(this.workFlowExecutionService.isPauseRequested(first.executionId())).isFalse();
		ResumePoint point = this.workFlowExecutionService.resumePoint(paused);
		assertThat(point.stepId()).isEqualTo("now");
		assertThat(point.warnings()).isEmpty();

		this.workFlowExecutionService.resume(paused.executionId());
		WorkFlowExecution done = this.await(paused.executionId());

		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.stepIds(done)).containsExactly("validate", "now");
	}

	@Test
	void LLM_장애로_실패한_step은_답이_돌아오면_그_step부터_다시_돈다() throws Exception {
		assumeFalse(this.chatModel.isLlmCall(), "LLM 실패를 흉내 내는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		// 답을 넣어 두지 않으면 LLM 대역이 예외를 던집니다.
		WorkFlowExecution failed = this.run(AGENT_WORKFLOW, Map.of("role", "사서", "message", "인사해 줘"));
		assertThat(failed.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(this.workFlowExecutionService.resumePoint(failed).stepId()).isEqualTo("draft");

		this.chatModel.answer("안녕하세요", "{\"pass\": true, \"reason\": \"안녕이 들어 있음\"}");
		this.workFlowExecutionService.resume(failed.executionId());
		WorkFlowExecution done = this.await(failed.executionId());

		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(done.errorMessage()).isNull();
		// 실패한 첫 호출 + 다시 돈 draft + judge
		assertThat(this.chatModel.prompts()).hasSize(3);
	}

	@Test
	void 실패한_TOOL_step을_이어_가면_일부가_끝났을_수_있다고_알린다() {
		WorkFlowExecution failed = this.run(TOOL_WORKFLOW, "SELEC 1 FROM dual");

		ResumePoint point = this.workFlowExecutionService.resumePoint(failed);

		assertThat(point.resumable()).isTrue();
		assertThat(point.warnings()).anyMatch(new java.util.function.Predicate<String>() {
			@Override
			public boolean test(String warning) {
				return warning.contains("일부가 이미 끝났을");
			}
		});
	}

	@Test
	void 멈췄던_step이_정의에서_사라졌으면_이어_가지_않는다() {
		WorkFlowExecution failed = this.run(TOOL_WORKFLOW, "SELEC 1 FROM dual");
		WorkFlowContext.setCurrentStep(failed.context(), "지워진step");

		assertThat(this.workFlowExecutionService.resumePoint(failed).resumable()).isFalse();
		assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
			@Override
			public void call() {
				WorkFlowPauseResumeTest.this.workFlowExecutionService.resume(failed.executionId());
			}
		}).isInstanceOf(ResponseStatusException.class).hasMessageContaining("지워진step");
		assertThat(this.workFlowExecutionStore.find(failed.executionId()).status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
	}

	@Test
	void 끝난_실행은_일시정지하거나_재개할_수_없다() {
		WorkFlowExecution done = this.run(TOOL_WORKFLOW, "SELECT 1 FROM dual");
		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);

		assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
			@Override
			public void call() {
				WorkFlowPauseResumeTest.this.workFlowExecutionService.pause(done.executionId());
			}
		}).isInstanceOf(ResponseStatusException.class);
		assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
			@Override
			public void call() {
				WorkFlowPauseResumeTest.this.workFlowExecutionService.resume(done.executionId());
			}
		}).isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void 상태가_그사이_바뀌면_재개는_한_번만_성공한다() {
		WorkFlowExecution failed = this.run(TOOL_WORKFLOW, "SELEC 1 FROM dual");

		assertThat(this.workFlowExecutionStore.markResumed(failed, 0)).isTrue();
		assertThat(this.workFlowExecutionStore.markResumed(failed, 0)).isFalse();
		this.workFlowExecutionStore.update(failed);
	}

	@Test
	void 재시작_복구는_RUNNING으로_남은_실행을_FAILED로_바꾸고_다시_실행할_수_있게_한다() {
		WorkFlowExecution stuck = this.newExecution(TOOL_WORKFLOW, "SELECT 1 FROM dual");

		this.recovery.recover();

		WorkFlowExecution failed = this.workFlowExecutionStore.find(stuck.executionId());
		assertThat(failed.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(failed.errorMessage()).contains("재시작");
		assertThat(this.workFlowExecutionService.resumePoint(failed).resumable()).isTrue();
	}

	@Test
	void 목록은_id_일부로_찾고_페이지로_나눈다() {
		WorkFlowExecution first = this.run(TOOL_WORKFLOW, "SELECT 1 FROM dual");
		this.run(TOOL_WORKFLOW, "SELECT 2 FROM dual");

		List<WorkFlowExecution> found = this.workFlowExecutionService.search(null, null, first.executionId().substring(0, 13).toUpperCase(), null, 0, 10);
		assertThat(found).extracting("executionId").contains(first.executionId());

		long total = this.workFlowExecutionService.count(null, "tool-chain", null, null);
		assertThat(total).isGreaterThanOrEqualTo(2L);
		assertThat(this.workFlowExecutionService.search(null, "tool-chain", null, null, 0, 1)).hasSize(1);
	}

}

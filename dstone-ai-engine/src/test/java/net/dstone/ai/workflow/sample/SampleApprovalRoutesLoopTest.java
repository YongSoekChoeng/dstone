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
 * sample-approval-routes-loop: 결정 이름을 routes로 정한 APPROVAL과, 같은 APPROVAL step으로 되돌아오는 흐름입니다. LLM을 부르지 않습니다.
 *
 *   stamp(시각 찍기) → choose(사람이 고름)   완료 → END / 다시 → stamp / 중단 → FAIL
 *
 * 승인 대기에서 멈추는지, "다시"를 고르면 같은 step에서 또 묻는지(결정을 한 번 쓰고 지우는지),
 * routes에 없는 이름은 거절되고 실행이 그대로 대기에 남는지 봅니다.
 * </pre>
 */
public class SampleApprovalRoutesLoopTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-approval-routes-loop";

	@Test
	@SuppressWarnings("unchecked")
	void 다시를_고르면_같은_step에서_또_묻고_완료를_고르면_끝난다() {
		WorkFlowExecution waiting = this.run(WORKFLOW_ID, "선택지 테스트");
		assertThat(waiting.status()).isEqualTo(WorkFlowExecutionStatus.WAITING_APPROVAL);
		assertThat(this.pendingApproval(waiting).stepId()).isEqualTo("choose");
		assertThat(this.pendingApproval(waiting).routes()).containsExactlyInAnyOrder("완료", "다시", "중단");

		WorkFlowExecution again = this.decide(waiting, "다시", "한 번 더");
		assertThat(again.status()).isEqualTo(WorkFlowExecutionStatus.WAITING_APPROVAL);
		assertThat(this.stepIds(again)).containsExactly("stamp", "choose", "stamp");
		assertThat((Map<String, Object>) this.state(again).get("choice")).containsEntry("comment", "한 번 더");

		WorkFlowExecution done = this.decide(again, "완료", null);
		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		Map<String, Object> output = (Map<String, Object>) done.output();
		assertThat((Map<String, Object>) output.get("decision")).containsEntry("decision", "완료");
	}

	@Test
	void routes에_없는_결정은_거절하고_대기_상태를_유지한다() {
		final WorkFlowExecution waiting = this.run(WORKFLOW_ID, "선택지 테스트");

		assertThrows(ResponseStatusException.class, new Executable() {
			@Override
			public void execute() {
				SampleApprovalRoutesLoopTest.this.decide(waiting, "모름", null);
			}
		});
		WorkFlowExecution after = this.workFlowExecutionService.find(waiting.executionId());
		assertThat(after.status()).isEqualTo(WorkFlowExecutionStatus.WAITING_APPROVAL);
	}

}

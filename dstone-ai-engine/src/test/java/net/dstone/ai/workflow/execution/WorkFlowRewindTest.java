package net.dstone.ai.workflow.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.ai.api.dto.RewindPoint;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.file.WorkFlowFileEntry;
import net.dstone.ai.runtime.workflow.file.WorkFlowFileStore;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * 실행을 지난 step으로 되돌려 다시 돌리는 기능(rewind)을 확인합니다.
 * LLM 없이 도는 sample-tool-chain-basic(validate → now)을 씁니다.
 * </pre>
 */
public class WorkFlowRewindTest extends WorkflowTestBase {

	private static final String TOOL_WORKFLOW = "sample-tool-chain-basic";
	/** 원래 있던 파일을 고치고(apply) 보고서를 새로 만드는(report) Workflow입니다. */
	private static final String EDIT_REPORT = "sample-tool-edit-report";

	@Autowired
	private WorkFlowFileStore workFlowFileStore;

	@TempDir
	Path workDir;

	/** 비동기로 다시 돈 실행이 RUNNING을 벗어날 때까지 기다립니다. */
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

	/** 저장된 실행의 input을 바꿔 둡니다. 다시 돌 때 경로가 달라지게 하려고 씁니다. */
	private void changeInput(WorkFlowExecution execution, Object input) {
		WorkFlowExecution stored = this.workFlowExecutionStore.find(execution.executionId());
		stored.context().put(Constants.WorkFlow.Context.INPUT, input);
		this.workFlowExecutionStore.update(stored);
	}

	@Test
	void 끝난_실행도_지난_step부터_다시_돌린다() throws Exception {
		WorkFlowExecution done = this.run(TOOL_WORKFLOW, "SELECT 1 FROM dual");
		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);

		RewindPoint point = this.workFlowExecutionService.rewindPoint(done, "validate");
		assertThat(point.rewindable()).isTrue();
		assertThat(point.steps()).containsExactly("validate", "now");
		assertThat(point.savedNames()).containsExactly("validation", "checkedAt");
		assertThat(point.warnings()).anyMatch(new java.util.function.Predicate<String>() {
			@Override
			public boolean test(String warning) {
				return warning.contains("validation, checkedAt");
			}
		});
		assertThat(this.workFlowExecutionService.rewindPoint(done, "now").savedNames()).containsExactly("checkedAt");

		WorkFlowExecution rewound = this.workFlowExecutionService.rewind(done.executionId(), "now", false);
		assertThat(rewound.status()).isEqualTo(WorkFlowExecutionStatus.RUNNING);
		assertThat(rewound.output()).isNull();
		WorkFlowExecution again = this.await(done.executionId());

		assertThat(again.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.stepIds(again)).containsExactly("validate", "now", "now");
		assertThat(again.output()).isNotNull();
	}

	@Test
	void clearState면_그_구간에서_저장한_값을_지우고_시작한다() throws Exception {
		WorkFlowExecution done = this.run(TOOL_WORKFLOW, "SELECT 1 FROM dual");
		// 이번에는 validate에서 실패해 now까지 가지 않게 합니다.
		this.changeInput(done, "SELEC 1 FROM dual");

		this.workFlowExecutionService.rewind(done.executionId(), "validate", true);
		WorkFlowExecution failed = this.await(done.executionId());

		assertThat(failed.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(WorkFlowContext.state(failed.context())).containsKey("validation").doesNotContainKey("checkedAt");
	}

	@Test
	void clearState가_아니면_예전_값이_남는다() throws Exception {
		WorkFlowExecution done = this.run(TOOL_WORKFLOW, "SELECT 1 FROM dual");
		this.changeInput(done, "SELEC 1 FROM dual");

		this.workFlowExecutionService.rewind(done.executionId(), "validate", false);
		WorkFlowExecution failed = this.await(done.executionId());

		assertThat(failed.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(WorkFlowContext.state(failed.context())).containsKey("checkedAt");
	}

	@Test
	void 기록에_없는_step이나_없는_step으로는_되돌리지_않는다() {
		WorkFlowExecution failed = this.run(TOOL_WORKFLOW, "SELEC 1 FROM dual");

		RewindPoint notRun = this.workFlowExecutionService.rewindPoint(failed, "now");
		assertThat(notRun.rewindable()).isFalse();
		assertThat(notRun.problem()).contains("기록에 없습니다");
		assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
			@Override
			public void call() {
				WorkFlowRewindTest.this.workFlowExecutionService.rewind(failed.executionId(), "now", true);
			}
		}).isInstanceOf(ResponseStatusException.class);
		assertThat(this.workFlowExecutionStore.find(failed.executionId()).status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
	}

	@Test
	void 돌고_있는_실행은_되돌리지_않는다() {
		WorkFlowExecution first = this.workFlowExecutor.run(this.workFlowRegistry.resolve(TOOL_WORKFLOW, null), this.startOnly(), 1);
		assertThat(first.status()).isEqualTo(WorkFlowExecutionStatus.RUNNING);

		assertThat(this.workFlowExecutionService.rewindPoint(first, "validate").problem()).contains("돌고 있는");
	}

	@Test
	void 되돌리면_구간의_step이_새로_만든_파일과_빈_폴더를_지우고_고친_파일은_남긴다() throws Exception {
		File source = this.workDir.resolve("a.txt").toFile();
		Files.write(source.toPath(), "hello world\n".getBytes(StandardCharsets.UTF_8));
		File reportDir = this.workDir.resolve("run1/out").toFile();
		File report = new File(reportDir, "report.md");
		File report2 = this.workDir.resolve("run2/report.md").toFile();

		WorkFlowExecution done = this.run(EDIT_REPORT, this.json(this.editReportInput(source, report)));
		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(report).isFile();

		RewindPoint point = this.workFlowExecutionService.rewindPoint(done, "apply");
		assertThat(point.deleteFiles()).containsExactly(path(report));
		assertThat(point.keptFiles()).containsExactly(path(source));

		// 다시 돌 때는 다른 곳에 보고서를 쓰게 해서, 지운 파일이 다시 생기지 않게 합니다.
		this.changeInput(done, this.json(this.editReportInput(source, report2)));
		this.workFlowExecutionService.rewind(done.executionId(), "apply", false);
		WorkFlowExecution again = this.await(done.executionId());

		assertThat(again.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(report).doesNotExist();
		assertThat(this.workDir.resolve("run1").toFile()).doesNotExist();
		assertThat(source).isFile();
		assertThat(report2).isFile();
		// 되돌린 구간의 예전 기록은 지워지고, 다시 돈 step의 기록만 남습니다.
		List<String> paths = new ArrayList<>();
		for (WorkFlowFileEntry entry : this.workFlowFileStore.list(done.executionId())) {
			paths.add(entry.path());
		}
		assertThat(paths).contains(path(report2)).doesNotContain(path(report));
	}

	private String editReportInput(File source, File report) {
		return "{\"reportPath\": \"" + path(report) + "\", \"changes\": [{\"path\": \"" + path(source)
			+ "\", \"edits\": [{\"append\": true, \"after\": \"끝\"}]}]}";
	}

	private static String path(File file) {
		return file.getAbsolutePath().replace('\\', '/');
	}

	/** 새 실행을 저장만 하고 돌리지는 않습니다. */
	private WorkFlowExecution startOnly() {
		String version = this.workFlowRegistry.resolve(TOOL_WORKFLOW, null).version();
		WorkFlowExecution execution = WorkFlowExecution.start(java.util.UUID.randomUUID().toString(), TOOL_WORKFLOW, null, "test-rewind",
			WorkFlowContext.create("SELECT 1 FROM dual", TOOL_WORKFLOW, version));
		this.workFlowExecutionStore.insert(execution);
		return execution;
	}

}

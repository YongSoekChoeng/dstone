package net.dstone.ai.workflow.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.ai.api.dto.WorkFlowFileContent;
import net.dstone.ai.api.dto.WorkFlowFileInfo;
import net.dstone.ai.api.dto.WorkFlowFileList;
import net.dstone.ai.api.service.WorkFlowFileService;
import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * step이 파일 Tool로 읽고 쓴 파일이 step별로 기록되고, 실행 관리 화면용 API(WorkFlowFileService)로 보고 고칠 수 있는지 봅니다.
 *
 * - TOOL step: sample-tool-edit-report(LLM 없음)로 확인합니다. 고치지 못한 파일(실패: ...)은 기록되지 않습니다.
 * - AGENT step: sample-agent-skill의 LLM이 readFile을 부르게 해서 확인합니다(미리 넣은 답).
 * - 저장: 원래 줄바꿈(CRLF)을 지키는지, 연 뒤에 바뀐 파일은 덮어쓰지 않는지, 실행 중에는 거절하는지 봅니다.
 *
 * conf/application.yml에서 dstone.ai.workflow.file.edit-enabled가 true여야 저장 테스트가 통과합니다.
 * </pre>
 */
public class WorkFlowFileTest extends WorkflowTestBase {

	private static final String EDIT_REPORT = "sample-tool-edit-report";

	@Autowired
	private WorkFlowFileService fileService;

	@TempDir
	Path workDir;

	@Test
	void TOOL_step이_쓴_파일이_step별로_기록된다() throws Exception {
		File a = this.write("a.txt", "hello world\n");
		File b = this.write("b.txt", "abc\n");
		File report = this.workDir.resolve("report.md").toFile();

		WorkFlowExecution execution = this.run(EDIT_REPORT, this.json(this.editReportInput(a, b, report)));
		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);

		WorkFlowFileList list = this.fileService.list(execution.executionId());
		assertThat(this.paths(list)).containsExactly(path(a), path(report));
		WorkFlowFileInfo reportInfo = this.info(list, path(report));
		assertThat(reportInfo.written()).isTrue();
		assertThat(reportInfo.editableType()).isTrue();
		assertThat(reportInfo.exists()).isTrue();
		assertThat(reportInfo.steps()).hasSize(1);
		assertThat(reportInfo.steps().get(0).stepId()).isEqualTo("report");
		assertThat(reportInfo.steps().get(0).action()).isEqualTo("WRITE");
		assertThat(this.info(list, path(a)).steps().get(0).stepId()).isEqualTo("apply");
		assertThat(this.info(list, path(a)).editableType()).isFalse();
		assertThat(list.readOnlyReason()).isNull();
	}

	@Test
	void AGENT_step의_LLM이_읽은_파일도_기록된다() throws Exception {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 Tool 흐름을 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		File doc = this.write("guide.md", "# 안내\n");
		this.chatModel.answerToolCalls("readFile", "{\"fileFullPath\": \"" + path(doc) + "\"}");
		this.chatModel.answer("읽었습니다.");

		WorkFlowExecution execution = this.run("sample-agent-skill", "SELECT 1");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		WorkFlowFileList list = this.fileService.list(execution.executionId());
		assertThat(this.paths(list)).containsExactly(path(doc));
		assertThat(list.files().get(0).steps().get(0).stepId()).isEqualTo("step01");
		assertThat(list.files().get(0).steps().get(0).action()).isEqualTo("READ");
		assertThat(list.files().get(0).written()).isFalse();
	}

	@Test
	void md_파일을_열고_고쳐_저장하면_원래_줄바꿈을_지킨다() throws Exception {
		File a = this.write("a.txt", "hello world\n");
		File report = this.workDir.resolve("report.md").toFile();
		WorkFlowExecution execution = this.run(EDIT_REPORT, this.json(this.editReportInput(a, null, report)));
		// Agent가 Windows에서 쓴 문서처럼 CRLF로 바꿔 둡니다.
		Files.write(report.toPath(), "# 결과\r\n\r\n내용\r\n".getBytes(StandardCharsets.UTF_8));

		WorkFlowFileContent opened = this.fileService.read(execution.executionId(), path(report));
		assertThat(opened.text()).isEqualTo("# 결과\n\n내용\n");
		assertThat(opened.lineBreak()).isEqualTo("CRLF");
		assertThat(opened.editable()).isTrue();

		WorkFlowFileContent saved = this.fileService.save(execution.executionId(), path(report), "# 결과\n\n고친 내용\n", opened.hash());

		assertThat(read(report)).isEqualTo("# 결과\r\n\r\n고친 내용\r\n");
		assertThat(saved.hash()).isNotEqualTo(opened.hash());
		assertThat(saved.text()).isEqualTo("# 결과\n\n고친 내용\n");
	}

	@Test
	void 연_뒤에_파일이_바뀌었으면_덮어쓰지_않는다() throws Exception {
		File a = this.write("a.txt", "hello world\n");
		File report = this.workDir.resolve("report.md").toFile();
		WorkFlowExecution execution = this.run(EDIT_REPORT, this.json(this.editReportInput(a, null, report)));
		WorkFlowFileContent opened = this.fileService.read(execution.executionId(), path(report));
		Files.write(report.toPath(), "Agent가 다시 쓴 내용\n".getBytes(StandardCharsets.UTF_8));

		assertThat(this.saveStatus(execution.executionId(), path(report), "화면에서 고친 내용\n", opened.hash())).isEqualTo(HttpStatus.CONFLICT);
		assertThat(read(report)).isEqualTo("Agent가 다시 쓴 내용\n");
	}

	@Test
	void 기록에_없는_파일이나_md가_아닌_파일은_저장하지_못한다() throws Exception {
		File a = this.write("a.txt", "hello world\n");
		File other = this.write("other.md", "남의 문서\n");
		File report = this.workDir.resolve("report.md").toFile();
		WorkFlowExecution execution = this.run(EDIT_REPORT, this.json(this.editReportInput(a, null, report)));

		assertThat(this.readStatus(execution.executionId(), path(other))).isEqualTo(HttpStatus.BAD_REQUEST);

		WorkFlowFileContent text = this.fileService.read(execution.executionId(), path(a));
		assertThat(text.editable()).isFalse();
		String before = read(a);
		assertThat(this.saveStatus(execution.executionId(), path(a), "x\n", text.hash())).isEqualTo(HttpStatus.CONFLICT);
		assertThat(read(a)).isEqualTo(before);
	}

	@Test
	void 실행_중에는_보기만_된다() throws Exception {
		File a = this.write("a.txt", "hello world\n");
		File report = this.workDir.resolve("report.md").toFile();
		Files.write(report.toPath(), "# 이전 보고서\n".getBytes(StandardCharsets.UTF_8));
		WorkflowStepper flow = this.stepper(EDIT_REPORT, this.json(this.editReportInput(a, null, report)));
		flow.step("check").step("apply");
		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.RUNNING);
		String executionId = flow.execution().executionId();

		WorkFlowFileList list = this.fileService.list(executionId);
		assertThat(this.paths(list)).containsExactly(path(a));
		assertThat(list.readOnlyReason()).contains("실행 중");
	}

	/** 저장을 시도하고, 거절되면 그 HTTP 상태를 돌려줍니다. 저장되면 null입니다. */
	private HttpStatus saveStatus(String executionId, String path, String text, String baseHash) {
		try {
			this.fileService.save(executionId, path, text, baseHash);
			return null;
		} catch (ResponseStatusException e) {
			return HttpStatus.valueOf(e.getStatusCode().value());
		}
	}

	/** 읽기를 시도하고, 거절되면 그 HTTP 상태를 돌려줍니다. 읽히면 null입니다. */
	private HttpStatus readStatus(String executionId, String path) {
		try {
			this.fileService.read(executionId, path);
			return null;
		} catch (ResponseStatusException e) {
			return HttpStatus.valueOf(e.getStatusCode().value());
		}
	}

	/** sample-tool-edit-report의 input입니다. b가 null이면 a만 고칩니다. */
	private String editReportInput(File a, File b, File report) {
		String changes = "{\"path\": \"" + path(a) + "\", \"edits\": [{\"before\": \"hello\", \"after\": \"안녕\"}, {\"append\": true, \"after\": \"끝\"}]}";
		if (b != null) {
			changes = changes + ", {\"path\": \"" + path(b) + "\", \"edits\": [{\"before\": \"없는 글자\", \"after\": \"x\"}]}";
		}
		return "{\"reportPath\": \"" + path(report) + "\", \"changes\": [" + changes + "]}";
	}

	private List<String> paths(WorkFlowFileList list) {
		List<String> paths = new ArrayList<String>();
		for (WorkFlowFileInfo info : list.files()) {
			paths.add(info.path());
		}
		return paths;
	}

	private WorkFlowFileInfo info(WorkFlowFileList list, String path) {
		for (WorkFlowFileInfo info : list.files()) {
			if (info.path().equals(path)) {
				return info;
			}
		}
		throw new AssertionError("목록에 없는 파일입니다: " + path);
	}

	private File write(String name, String text) throws Exception {
		Path file = this.workDir.resolve(name);
		Files.write(file, text.getBytes(StandardCharsets.UTF_8));
		return file.toFile();
	}

	private static String read(File file) throws Exception {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	/** JSON에 넣을 경로입니다(Windows의 '\'는 '/'로 바꿉니다). */
	private static String path(File file) {
		return file.getAbsolutePath().replace('\\', '/');
	}

}

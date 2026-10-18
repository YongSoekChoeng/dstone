package net.dstone.ai.workflow.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sample-tool-edit-report: 범용 Tool만으로 "확인 → 파일마다 고치기 → 실패한 것 고르기 → 보고서 저장"을 하는 Workflow입니다. LLM을 부르지 않습니다.
 *
 * 실제 파일을 고치므로, 테스트마다 JUnit이 만들어 주는 임시 폴더(@TempDir)에 파일을 두고 그 경로를 input으로 넘깁니다.
 * 고칠 수 있는 파일(a.txt)은 바뀌고, 고칠 수 없는 파일(b.txt)은 그대로 남아 failedPaths에 잡히는지 봅니다.
 * </pre>
 */
public class SampleToolEditReportTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-tool-edit-report";

	@TempDir
	Path workDir;

	@Test
	@SuppressWarnings("unchecked")
	void 고칠_수_있는_파일만_바뀌고_나머지는_보고서에_실패로_남는다() throws Exception {
		File a = this.write("a.txt", "hello world\n");
		File b = this.write("b.txt", "abc\n");
		File report = this.workDir.resolve("report.md").toFile();

		String input = "{"
			+ "\"reportPath\": \"" + path(report) + "\","
			+ "\"changes\": ["
			+ "  {\"path\": \"" + path(a) + "\", \"edits\": [{\"before\": \"hello\", \"after\": \"안녕\"}, {\"append\": true, \"after\": \"끝\"}]},"
			+ "  {\"path\": \"" + path(b) + "\", \"edits\": [{\"before\": \"없는 글자\", \"after\": \"x\"}]}"
			+ "]}";
		WorkFlowExecution execution = this.run(WORKFLOW_ID, this.json(input));

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(read(a)).contains("안녕 world").contains("끝");
		assertThat(read(b)).isEqualTo("abc\n");
		Map<String, Object> output = (Map<String, Object>) execution.output();
		assertThat((List<Object>) output.get("failedPaths")).containsExactly(path(b));
		assertThat(report).exists();
		assertThat(read(report)).contains("고치지 못한 파일");
	}

	/** 임시 폴더에 UTF-8 파일을 만듭니다. */
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

package net.dstone.ai.workflow.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sample-mcp-filesystem-list: 외부 MCP 서버(sample-filesystem)의 Tool을 TOOL step이 LLM 없이 부르는 Workflow입니다.
 * directory_tree로 폴더를 보고, 파일마다(forEach) read_text_file로 읽습니다.
 *
 * MCP 서버는 npx로 띄우므로 Node.js가 있어야 하고, 처음 한 번은 패키지를 내려받느라 인터넷이 필요합니다.
 * 서버를 띄우지 못한 환경에서는 실패가 아니라 건너뜀(skipped)으로 남깁니다.
 * </pre>
 */
public class SampleMcpFilesystemListTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-mcp-filesystem-list";

	@Test
	void MCP_서버가_노출한_폴더의_파일을_모두_읽는다() {
		assumeTrue(this.hasTool("directory_tree") && this.hasTool("read_text_file"), "MCP 서버(sample-filesystem)에 접속하지 못해 건너뜁니다.");
		File folder = new File(MODULE_DIR, "mcp/server-filesystem/sample");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, folder.getAbsolutePath().replace('\\', '/'));

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat((List<?>) execution.output()).hasSize(folder.list().length);
	}

}

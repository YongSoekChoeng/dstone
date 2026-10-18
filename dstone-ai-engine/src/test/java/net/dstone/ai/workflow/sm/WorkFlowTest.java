package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow(어플리케이션 운영관리) 테스트입니다. 지금 정의된 흐름은 아래 두 step입니다.
 *
 *   step00_01_readReq       TOOL    요청서 폴더의 00-request.md를 읽어 state.request에 저장(LLM 없음)
 *   step00_02_decideJobKind ROUTER  sm-decide-job-kind-agent가 요청서를 보고 신규개발 / 일반 중 하나를 고름 → state.classify
 *
 * 요청서는 테스트가 임시 폴더(@TempDir)에 직접 만듭니다. 폴더 이름과 내용은 testApp의 샘플 요청서
 * (workshop/changes/work001[log-error-fix], work002[add-bbs])와 같은 모양입니다. 실행 환경의 D:/ 같은 경로에 기대지 않습니다.
 *
 * ROUTER의 LLM은 ScriptedChatModel이 대신합니다. 그래서 이 테스트가 확인하는 것은 "LLM이 무엇을 고를지"가 아니라
 * "요청서가 Agent에게 전달되고, 고른 route가 state와 return에 남고, 그 route의 다음 step으로 가는지"입니다.
 *
 * 지금 정의에서는 두 route가 모두 FAIL로 가므로(뒤의 step을 아직 만들지 않음) 실행은 FAILED로 끝납니다.
 * routes에 다음 step을 이으면 assertRouted()의 상태 확인과 기대 step 목록을 함께 고칩니다.
 * </pre>
 */
public class WorkFlowTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	@TempDir
	Path workshop;

	/** 오류 수정 요청서(work001과 같은 모양)는 일반 경로로 간다. */
	@Test
	void testSmWorkflow() throws Exception {
		File requestDir = this.request("work001[log-error-fix]",
			"## 현업요청\n\n"
			+ "아래의 메뉴에서 에러가 발생합니다. 신속한 수정 바랍니다.\n"
			+ "-테스트>로그조회>로그인이력\n"
			+ "-테스트>로그조회>액션로그\n"
			+ "-테스트>로그조회>에러로그\n\n"
			+ "## 프로젝트ID\ntestApp\n\n"
			+ "## 소스경로\nD:/AppHome/testApp\n\n"
			+ "## 에러로그파일\n(현재 파일과 동일한 디렉토리)\nerror.log\n");
		this.chatModel.answer("{\"route\": \"일반\", \"reason\": \"요청서에 '에러가 발생합니다'라고 적혀 있어 기존 기능의 오류 수정이다.\"}");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, path(requestDir));

		this.assertRouted(execution, "일반");
		// ROUTER Agent가 받은 요청서 내용입니다(step00_01_readReq가 읽은 그대로).
		assertThat(this.chatModel.prompts().get(0).getContents()).contains("-테스트>로그조회>로그인이력");
	}

	/** 없는 기능을 새로 만드는 요청서(work002와 같은 모양)는 신규개발 경로로 간다. */
	@Test
	void testSmWorkflowNewDevelopment() throws Exception {
		File requestDir = this.request("work002[add-bbs]",
			"## 현업요청\n\n"
			+ "신규로 FAQ기능을 개발하라.\n\n"
			+ "<기능>\n자주 물어볼 수 있는 질문들을 모범대답과 함께 화면에 보여준다.\n"
			+ "테이블설계가 필요. 샘플게시판을 참고하되 게시판이 아닌 FAQ임을 감안하여 설계한다.\n\n"
			+ "<메뉴위치>\n-테스트>FAQ\n\n"
			+ "## 프로젝트ID\ntestApp\n\n"
			+ "## 소스경로\nD:/AppHome/testApp\n");
		this.chatModel.answer("{\"route\": \"신규개발\", \"reason\": \"요청서가 '신규로 FAQ기능을 개발하라'고 하여 없는 기능을 새로 만드는 일이다.\"}");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, path(requestDir));

		this.assertRouted(execution, "신규개발");
	}

	/** 요청서 폴더에 00-request.md가 없으면 LLM을 부르기 전에 첫 step에서 실패한다. */
	@Test
	void testSmWorkflowWithoutRequestFile() {
		File emptyDir = this.workshop.resolve("work999[empty]").toFile();
		emptyDir.mkdirs();

		WorkFlowExecution execution = this.run(WORKFLOW_ID, path(emptyDir));

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(this.stepIds(execution)).containsExactly("step00_01_readReq");
		assertThat(this.chatModel.prompts()).isEmpty();
	}

	/**
	 * ROUTER가 route를 골라 state.classify와 return에 남겼는지 봅니다.
	 *
	 * @param execution 끝난 실행
	 * @param route     골랐어야 하는 route
	 */
	@SuppressWarnings("unchecked")
	private void assertRouted(WorkFlowExecution execution, String route) {
		assertThat(this.stepIds(execution)).containsExactly("step00_01_readReq", "step00_02_decideJobKind");
		assertThat((Map<String, Object>) this.state(execution).get("classify")).containsEntry("route", route);
		// 지금 정의는 두 route가 모두 FAIL로 갑니다. 다음 step을 이으면 이 줄을 고칩니다.
		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(this.chatModel.remaining()).isZero();
	}

	/**
	 * 임시 폴더 아래에 요청서 폴더를 만들고 00-request.md를 씁니다.
	 *
	 * @param folderName 요청서 폴더 이름(예: work001[log-error-fix])
	 * @param text       00-request.md 내용
	 */
	private File request(String folderName, String text) throws Exception {
		Path dir = this.workshop.resolve(folderName);
		Files.createDirectories(dir);
		Files.write(dir.resolve("00-request.md"), text.getBytes(StandardCharsets.UTF_8));
		return dir.toFile();
	}

	/** Workflow input으로 넘길 경로입니다(Windows의 '\'는 '/'로 바꿉니다). */
	private static String path(File dir) {
		return dir.getAbsolutePath().replace('\\', '/');
	}

}

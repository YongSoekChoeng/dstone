package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow(어플리케이션 운영관리) 테스트입니다. step 하나씩 끊어서 실행합니다(WorkflowStepper).
 *
 * 쓰는 법
 * - step마다 아래에 "step id와 같은 이름의 메서드"를 하나 둡니다. 그 메서드가 그 step을 실행하고(flow.step(id)) 결과를 확인합니다.
 *   LLM을 부르는 step이면 실행 전에 this.chatModel.answer(...)로 LLM의 답을 넣어 둡니다.
 * - testSmWorkflow()는 그 메서드들을 순서대로 부릅니다. 앞 step까지만 보고 싶으면 뒤의 줄을 주석으로 막습니다.
 *   막힌 step은 실행되지 않고, 실행은 다음 step을 가리킨 채(RUNNING) 끝납니다.
 * - 흐름이 예상과 다르게 갈라지면(onFailure, routes) flow.step(id)가 "지금 차례는 step[...]"이라는 AssertionError로 알려 줍니다.
 * - 중간 step부터 시작하는 예는 testSmWorkflowFromDecideJobKind()에 있습니다.
 *
 * 요청서(00-request.md)는 테스트가 임시 폴더(@TempDir)에 직접 만듭니다. 내용은 testApp의 샘플 요청서
 * (workshop/changes/work001[log-error-fix], work002[add-bbs])와 같은 모양입니다.
 * </pre>
 */
public class WorkFlowTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	/** 오류 수정 요청서(work001과 같은 모양) */
	private static final String REQUEST_ERROR_FIX = "## 현업요청\n\n"
		+ "아래의 메뉴에서 에러가 발생합니다. 신속한 수정 바랍니다.\n"
		+ "-테스트>로그조회>로그인이력\n"
		+ "-테스트>로그조회>액션로그\n"
		+ "-테스트>로그조회>에러로그\n\n"
		+ "## 프로젝트ID\ntestApp\n\n"
		+ "## 소스경로\nD:/AppHome/testApp\n\n"
		+ "## 에러로그파일\n(현재 파일과 동일한 디렉토리)\nerror.log\n";

	/** 신규 개발 요청서(work002와 같은 모양) */
	private static final String REQUEST_NEW_DEV = "## 현업요청\n\n"
		+ "신규로 FAQ기능을 개발하라.\n\n"
		+ "<기능>\n자주 물어볼 수 있는 질문들을 모범대답과 함께 화면에 보여준다.\n"
		+ "테이블설계가 필요. 샘플게시판을 참고하되 게시판이 아닌 FAQ임을 감안하여 설계한다.\n\n"
		+ "<메뉴위치>\n-테스트>FAQ\n\n"
		+ "## 프로젝트ID\ntestApp\n\n"
		+ "## 소스경로\nD:/AppHome/testApp\n";

	@TempDir
	Path workshop;

	/** 오류 수정 요청서로 step을 차례로 실행합니다. 뒤의 줄을 주석으로 막으면 그 앞 step까지만 실행합니다. */
	@Test
	void testSmWorkflow() throws Exception {
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.request("work001[log-error-fix]", REQUEST_ERROR_FIX));

		this.step00_01_readReq(flow, "-테스트>로그조회>로그인이력");
		this.step00_02_decideJobKind(flow, "일반", "-테스트>로그조회>로그인이력");
		/*
		this.step01_01_xxx(flow);
		this.step01_02_xxx(flow);
		*/
	}

	/** 신규 개발 요청서는 step00_02에서 신규개발을 고른다. */
	@Test
	void testSmWorkflowNewDevelopment() throws Exception {
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.request("work002[add-bbs]", REQUEST_NEW_DEV));

		this.step00_01_readReq(flow, "신규로 FAQ기능을 개발하라.");
		this.step00_02_decideJobKind(flow, "신규개발", "신규로 FAQ기능을 개발하라.");
	}

	/** 앞 step을 건너뛰고 step00_02부터 시작합니다. 건너뛴 step이 저장했을 값(state.request)을 직접 넣습니다. */
	@Test
	void testSmWorkflowFromDecideJobKind() {
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, "(건너뜀)");
		flow.put("request", REQUEST_ERROR_FIX).jumpTo("step00_02_decideJobKind");

		this.step00_02_decideJobKind(flow, "일반", "-테스트>로그조회>로그인이력");
	}

	/** 요청서 폴더에 00-request.md가 없으면 첫 step에서 실패하고, 뒤 step(LLM)은 실행되지 않는다. */
	@Test
	void testSmWorkflowWithoutRequestFile() {
		File emptyDir = this.workshop.resolve("work999[empty]").toFile();
		emptyDir.mkdirs();
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, path(emptyDir));

		flow.step("step00_01_readReq");

		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(this.chatModel.prompts()).isEmpty();
	}

	// =====================================================================================
	// step별 테스트. 메서드 이름 = step id
	// =====================================================================================

	/**
	 * step00_01_readReq(TOOL readFile): 요청서 폴더의 00-request.md를 읽어 state.request에 저장합니다. LLM은 부르지 않습니다.
	 *
	 * @param flow     실행 중인 Workflow
	 * @param expected 읽은 요청서에 들어 있어야 하는 글자
	 */
	private void step00_01_readReq(WorkflowStepper flow, String expected) {
		flow.step("step00_01_readReq");

		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.RUNNING);
		assertThat((String) flow.state("request")).contains(expected);
		assertThat(this.chatModel.prompts()).isEmpty();
	}

	/**
	 * <pre>
	 * step00_02_decideJobKind(ROUTER sm-decide-job-kind-agent): 요청서를 보고 신규개발 / 일반 중 하나를 골라 state.classify에 저장합니다.
	 * LLM의 답은 ScriptedChatModel로 넣습니다. 그래서 여기서 확인하는 것은 "요청서가 Agent에게 갔는지"와
	 * "고른 route가 state에 남는지"입니다.
	 * 지금 정의는 두 route가 모두 FAIL로 가서 이 step 뒤에 실행이 FAILED로 끝납니다. routes에 다음 step을 이으면 상태 확인을 고칩니다.
	 * </pre>
	 *
	 * @param flow        실행 중인 Workflow
	 * @param route       LLM이 고를 route(신규개발 / 일반)
	 * @param requestLine Agent에게 간 프롬프트에 들어 있어야 하는 요청서의 한 줄
	 */
	private void step00_02_decideJobKind(WorkflowStepper flow, String route, String requestLine) {
		this.chatModel.answer("{\"route\": \"" + route + "\", \"reason\": \"요청서의 내용으로 판단했습니다.\"}");

		flow.step("step00_02_decideJobKind");

		assertThat(flow.stateMap("classify")).containsEntry("route", route);
		assertThat(this.chatModel.prompts().get(this.chatModel.prompts().size() - 1).getContents()).contains(requestLine);
		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.FAILED); // 지금 정의는 두 route 모두 FAIL
	}

	// =====================================================================================
	// 도우미
	// =====================================================================================

	/**
	 * 임시 폴더 아래에 요청서 폴더를 만들고 00-request.md를 씁니다. Workflow input으로 넘길 폴더 경로를 돌려줍니다.
	 *
	 * @param folderName 요청서 폴더 이름(예: work001[log-error-fix])
	 * @param text       00-request.md 내용
	 */
	private String request(String folderName, String text) throws Exception {
		Path dir = this.workshop.resolve(folderName);
		Files.createDirectories(dir);
		Files.write(dir.resolve("00-request.md"), text.getBytes(StandardCharsets.UTF_8));
		return path(dir.toFile());
	}

	/** Workflow input으로 넘길 경로입니다(Windows의 '\'는 '/'로 바꿉니다). */
	private static String path(File dir) {
		return dir.getAbsolutePath().replace('\\', '/');
	}

}

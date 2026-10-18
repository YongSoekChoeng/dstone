package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow(어플리케이션 운영관리) 테스트입니다. step마다 따로 떼어 확인합니다.
 *
 * 쓰는 법
 * - step 하나에 블록 하나입니다. stepper(WORKFLOW_ID, step id, 그 step에 넣을 값)으로 그 step에서 시작하는 실행을 만들고,
 *   step(id)로 그 step 하나만 실행합니다. 넣을 값은 그 step의 with를 계산한 결과와 같은 모양입니다
 *   (TOOL step은 Tool 인자 이름 → 값의 맵, AGENT/ROUTER/SUPERVISOR step은 Agent input 모양의 값).
 * - 앞 step의 결과가 필요하면 그 결과를 꺼내 다음 step에 넣을 값으로 씁니다(아래 step2가 step1의 state.request를 씀).
 * - 뒤 step을 아직 만들지 않았거나 빼고 싶으면 그 블록을 주석으로 막습니다.
 * - LLM을 부르는 step은 실행 전에 this.chatModel.answer(...)로 LLM의 답을 넣어 둡니다(테스트에서는 실제 LLM을 부르지 않습니다).
 *
 * 요청서는 testApp의 샘플(workshop/changes/work001[log-error-fix]/00-request.md)을 그대로 읽습니다.
 * 그 폴더는 Windows에서는 D:/AppHome/testApp, 그 밖(WSL)에서는 /app/testApp에 있습니다(TEST_APP_HOME).
 * </pre>
 */
public class WorkFlowTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	/** testApp 폴더입니다. 다른 위치에 두었으면 -DTEST_APP_HOME=경로 로 바꿉니다. */
	private static final String TEST_APP_HOME = System.getProperty("TEST_APP_HOME",
		System.getProperty("os.name", "").toLowerCase().startsWith("windows") ? "D:/AppHome/testApp" : "/app/testApp");

	@Test
	@SuppressWarnings("unchecked")
	void testSmWorkflow() {

		// step1 테스트: step00_01_readReq(TOOL readFile) - 요청서를 읽어 state.request에 저장
		HashMap<String, Object> step1Param = new HashMap<String, Object>();
		step1Param.put("fileFullPath", TEST_APP_HOME + "/workshop/changes/work001[log-error-fix]/00-request.md");
		WorkflowStepper step1 = this.stepper(WORKFLOW_ID, "step00_01_readReq", step1Param);
		step1.step("step00_01_readReq");
		assertThat(step1.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat((String) step1.state("request")).contains("## 현업요청");

		// step2 테스트: step00_02_decideJobKind(ROUTER sm-decide-job-kind-agent) - 요청서를 보고 신규개발 / 일반 중 하나를 고름
		String step2Param = (String) step1.state("request"); // with: ${state.request}
		this.chatModel.answer("{\"route\": \"일반\", \"reason\": \"요청서에 '에러가 발생합니다'라고 적혀 있어 기존 기능의 오류 수정이다.\"}");
		WorkflowStepper step2 = this.stepper(WORKFLOW_ID, "step00_02_decideJobKind", step2Param);
		step2.step("step00_02_decideJobKind");
		assertThat(step2.stateMap("classify")).containsEntry("route", "일반");
		// 지금 정의는 두 route(신규개발, 일반)가 모두 FAIL로 가서 이 step 뒤에 실행이 FAILED로 끝납니다.
		// routes에 다음 step을 이으면 아래 줄을 isNotEqualTo(WorkFlowExecutionStatus.FAILED)로 바꿉니다.
		assertThat(step2.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);

		/*
		// step3 테스트
		HashMap<String, Object> step3Param = new HashMap<String, Object>();
		step3Param.put("...", ...);
		WorkflowStepper step3 = this.stepper(WORKFLOW_ID, "step01_01_xxx", step3Param);
		step3.step("step01_01_xxx");
		assertThat(step3.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);

		// step4 테스트
		// step5 테스트
		*/
	}

}

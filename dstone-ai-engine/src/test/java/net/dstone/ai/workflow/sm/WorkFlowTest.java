package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.ScriptedChatModel;
import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow(어플리케이션 운영관리) 테스트입니다.
 *
 * 쓰는 법
 * - testSmWorkflow는 step 하나에 블록 하나입니다. stepper(WORKFLOW_ID, step id, 그 step에 넣을 값)으로 그 step에서 시작하는 실행을 만들고,
 *   step(id)로 그 step 하나만 실행합니다. 넣을 값은 그 step의 with를 계산한 결과와 같은 모양입니다.
 * - 뒤 step을 빼고 싶으면 그 블록을 주석으로 막습니다.
 * - LLM을 부르는 step은 실행 전에 this.chatModel.answer(...)로 LLM의 답을 넣어 둡니다.
 *
 * 가짜 LLM은 파일을 쓰지 않으므로, Agent가 저장했어야 할 문서를 임시 폴더에 미리 만들어 둡니다.
 * </pre>
 */
public class WorkFlowTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	private static final String REQUEST = "## 현업요청\n로그조회 메뉴에서 에러가 납니다.\n\n## 소스경로\nD:/AppHome/testApp\n";

	@TempDir
	Path workDir;

	/** 작업 폴더에 문서를 만듭니다. */
	private void writeDoc(String name, String text) throws IOException {
		Files.write(this.workDir.resolve(name), text.getBytes(StandardCharsets.UTF_8));
	}

	/** 모든 단계의 문서를 만듭니다. */
	private void writeAllDocs() throws IOException {
		this.writeDoc("00.request.md", REQUEST);
		this.writeDoc("01.customer_requirement.md", "분석서 내용");
		this.writeDoc("02.impact.md", "영향도 내용");
		this.writeDoc("03-1.design.md", "설계서 내용");
		this.writeDoc("03-2.taskitems.md", "태스크 내용");
	}

	/** 마지막으로 보낸 프롬프트의 글자입니다. */
	private String lastPrompt() {
		List<org.springframework.ai.chat.prompt.Prompt> prompts = this.chatModel.prompts();
		return ScriptedChatModel.textOf(prompts.get(prompts.size() - 1));
	}

	@Test
	void testSmWorkflow() throws IOException {
		assumeFalse(this.chatModel.isLlmCall());
		this.writeAllDocs();
		String dir = this.workDir.toString();

		// 1. step00 고객요청서 읽기
		WorkflowStepper step00 = this.stepper(WORKFLOW_ID, dir);
		step00.step("step00");
		assertThat(step00.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat((String) step00.state("request")).contains("로그조회");

		// 2. step01 고객요청사항 분석
		HashMap<String, Object> step01Param = new HashMap<String, Object>();
		step01Param.put("base_dir", dir);
		step01Param.put("request", step00.state("request"));
		step01Param.put("feedback", "");
		this.chatModel.answer(dir + "/01.customer_requirement.md");
		WorkflowStepper step01 = this.stepper(WORKFLOW_ID, "step01", step01Param);
		step01.step("step01");
		assertThat(step01.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(step01.currentStepId()).isEqualTo("step02");
	}

	@Test
	void 전체_흐름에서_문서는_입력으로_넘어가고_다시_돌릴_때_사유가_전달된다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall());
		this.writeAllDocs();

		// 리뷰 불통과 → 사람이 재분석을 고릅니다.
		this.chatModel.answer("저장함", "{\"pass\": false, \"reason\": \"TASK-01 완료 기준이 모호함\"}");
		WorkFlowExecution waiting = this.run(WORKFLOW_ID, this.workDir.toString());
		assertThat(waiting.status()).isEqualTo(WorkFlowExecutionStatus.WAITING_APPROVAL);
		assertThat(this.pendingApproval(waiting).stepId()).isEqualTo("step04");
		// 리뷰 Agent는 문서를 입력으로 받습니다(파일을 읽으려고 LLM을 또 부르지 않습니다).
		assertThat(this.lastPrompt()).contains("로그조회").contains("분석서 내용");

		// 재분석 때 리뷰 사유와 사람의 의견이 함께 전달되고, 이번에는 통과해서 영향도 승인까지 갑니다.
		this.chatModel.answer("저장함", "{\"pass\": true, \"reason\": \"문제없음\"}", "저장함");
		WorkFlowExecution impactWaiting = this.decide(waiting, "고객요청사항 재분석", "메뉴 이름도 적을 것");
		assertThat(this.pendingApproval(impactWaiting).stepId()).isEqualTo("step06");
		List<org.springframework.ai.chat.prompt.Prompt> prompts = this.chatModel.prompts();
		String reanalysis = ScriptedChatModel.textOf(prompts.get(prompts.size() - 3));
		assertThat(reanalysis).contains("TASK-01 완료 기준이 모호함").contains("메뉴 이름도 적을 것");

		// 영향도 재분석 때 승인 화면의 의견이 전달됩니다.
		this.chatModel.answer("저장함");
		WorkFlowExecution impactAgain = this.decide(impactWaiting, "영향도 재분석", "배치도 볼 것");
		assertThat(this.lastPrompt()).contains("배치도 볼 것");

		// 승인 → 설계 → 승인 → 개발
		this.chatModel.answer("저장함");
		WorkFlowExecution designWaiting = this.decide(impactAgain, "승인", null);
		assertThat(this.pendingApproval(designWaiting).stepId()).isEqualTo("step09");
		assertThat(this.lastPrompt()).contains("영향도 내용");

		this.chatModel.answer("완료 1건");
		WorkFlowExecution done = this.decide(designWaiting, "승인", null);
		assertThat(done.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.lastPrompt()).contains("설계서 내용").contains("태스크 내용");
	}

	@Test
	void 요청서가_없으면_LLM을_부르지_않고_실패한다() {
		assumeFalse(this.chatModel.isLlmCall());
		int before = this.chatModel.prompts().size();
		WorkFlowExecution failed = this.run(WORKFLOW_ID, this.workDir.toString());
		assertThat(failed.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(this.chatModel.prompts().size()).isEqualTo(before);
	}

	@Test
	void 설계_step이_태스크_목록을_저장하지_못하면_개발로_넘어가지_않는다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall());
		this.writeDoc("03-1.design.md", "설계서 내용");
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString());
		flow.jumpTo("step11");
		flow.step("step11");
		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
	}

}

package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.api.dto.PendingApproval;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.definition.workflow.step.AgentStepDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.support.ScriptedChatModel;
import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow에서 사람의 승인 의견이 다음 Agent step으로 가는지 확인합니다(LLM 없음, sm-workflow 2.0.0).
 * 의견은 Workflow가 손으로 잇지 않고 엔진이 결정 기록으로 넘깁니다(${arrival}, ${decisions}).
 * - 승인하고 앞으로 진행하면(step09 → TOOL step10 → step12) TOOL step을 건너뛰어 다음 Agent가 받는다.
 * - 승인 화면은 결정마다 그 의견을 받을 step(Agent)을 보여 준다.
 * - [~ 수정]을 고르면 문서 수정 Agent가 그 의견을 지시로 받는다.
 * - [재개발]을 고르면 설계서와 목록을 다시 읽고(step20~21) 개발 폴더는 그대로 둔 채 개발로 간다(sm-workflow 2.1.0).
 * - [전문가 해결]을 고르면 설계서와 목록을 다시 읽고(step22~23) 해결 Agent(step24)가 증상을 받아 고친 뒤 컴파일 확인으로 간다(2.2.0).
 * </pre>
 */
public class SmDecisionFlowTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	private static final String NOW = "20261120-0951";

	@TempDir
	Path workDir;

	@Test
	void 승인하고_진행하면_TOOL_step을_건너뛰어_다음_Agent가_의견을_받는다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		Path folder = this.folder();
		Files.writeString(folder.resolve("02.impact.md"), "# 영향도", StandardCharsets.UTF_8);
		String design = folder.resolve("03-1.design.md").toString().replace("\\", "/");
		this.chatModel.answerToolCalls("writeFile", "{\"filePath\": \"" + design + "\", \"fileContents\": \"# 설계\"}");
		this.chatModel.answer(design);

		WorkflowStepper flow = this.atStep("step09");
		flow.step("step09");
		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.WAITING_APPROVAL);

		PendingApproval pending = this.pendingApproval(flow.execution());
		assertThat(pending.receivers().get("승인(설계진행)")).isEqualTo("step37 13-기존 설계서 맞춤 · document-revise-agent 또는 step12 44-아키텍처 설계 · sm-architect-agent");
		assertThat(pending.receivers().get("영향도 수정")).isEqualTo("step26 27-영향도분석서 수정 · document-revise-agent");
		assertThat(pending.receivers().get("중지")).isEqualTo("실패로 끝냄(FAIL)");

		flow.decide("승인(설계진행)", "공통 코드 테이블도 같이 봐 주세요");
		flow.step("step10");
		flow.step("step46"); // 영향도분석서에 '참고 기능' 절이 없으면 본뜰 기능의 코드 없이 설계로 간다
		flow.step("step11"); // 설계서가 아직 없으므로 새로 쓰는 길로 간다
		flow.step("step12");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		String prompt = ScriptedChatModel.textOf(this.chatModel.prompts().get(0));
		assertThat(prompt).contains("영향도 분석 승인(step09)의 결정: 승인(설계진행) (승인자 tester)").contains("공통 코드 테이블도 같이 봐 주세요");
		// 늘 필요한 Skill은 시스템 프롬프트에 미리 들어간다(skills.preload).
		assertThat(prompt).contains("[Skill: handling-instructions]").contains("[Skill: source-investigation]").contains("[Skill: document-writing]");
		// Agent step이 성공하면 받아 쓴 결정은 비우고, 기록은 남긴다.
		assertThat(flow.execution().context().get(Constants.WorkFlow.Context.ARRIVAL)).isNull();
		assertThat(WorkFlowContext.decisions(flow.execution().context())).hasSize(1);
	}

	@Test
	void 수정을_고르면_문서_수정_Agent가_의견을_지시로_받는다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		Path folder = this.folder();
		Path requirement = folder.resolve("01.customer_requirement.md");
		Files.writeString(requirement, "| TASK-02 | 완료 기준: 잘 된다 |", StandardCharsets.UTF_8);
		String path = requirement.toString().replace("\\", "/");
		this.chatModel.answerToolCalls("editFileTexts", "{\"filePath\": \"" + path + "\", \"edits\": [{\"before\": \"잘 된다\", \"after\": \"목록에 10건씩 보인다\"}]}");
		this.chatModel.answer("고친 파일: " + path);

		WorkflowStepper flow = this.atStep("step06");
		flow.step("step06");
		flow.decide("요구사항분석서 수정", "TASK-02 완료 기준을 확인할 수 있게 고쳐 주세요");
		assertThat(flow.currentStepId()).isEqualTo("step39");
		flow.step("step39");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.currentStepId()).isEqualTo("step04");
		assertThat(Files.readString(requirement, StandardCharsets.UTF_8)).contains("목록에 10건씩 보인다");
		String prompt = ScriptedChatModel.textOf(this.chatModel.prompts().get(0));
		assertThat(prompt).contains(path).contains("리뷰 불통과 처리 결정(step06)의 결정: 요구사항분석서 수정").contains("TASK-02 완료 기준을 확인할 수 있게");
	}

	@Test
	void 재개발을_고르면_설계서와_목록을_다시_읽고_개발로_간다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		// 지난 재개발이 설계서와 목록을 고쳐 둔 상태입니다. state에는 고치기 전 내용이 남아 있습니다.
		Path folder = this.folder();
		Files.writeString(folder.resolve("03-1.design.md"), "# 설계 (재개발 반영)", StandardCharsets.UTF_8);
		Files.writeString(folder.resolve("03-2.taskitems.md"), "# 목록 (재개발 반영)", StandardCharsets.UTF_8);
		Files.createDirectories(folder.resolve("dev-resources"));
		Files.writeString(folder.resolve("dev-resources/A.java"), "class A {}", StandardCharsets.UTF_8);

		WorkflowStepper flow = this.atStep("step19");
		flow.put("design", "# 설계 (옛 내용)").put("taskitems", "# 목록 (옛 내용)");
		flow.step("step19");
		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.WAITING_APPROVAL);
		PendingApproval pending = this.pendingApproval(flow.execution());
		assertThat(pending.receivers().get("재개발")).isEqualTo("step17 18-개발 · sm-developer-agent");

		flow.decide("재개발", "업로드한 파일이 저장되지 않음");
		assertThat(flow.currentStepId()).isEqualTo("step20");
		flow.step("step20");
		flow.step("step21");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.currentStepId()).isEqualTo("step17");
		assertThat(flow.state("design")).isEqualTo("# 설계 (재개발 반영)");
		assertThat(flow.state("taskitems")).isEqualTo("# 목록 (재개발 반영)");
		// 재개발은 개발 폴더를 비우지 않습니다(비우는 step16는 설계 승인 뒤에만 지납니다).
		assertThat(folder.resolve("dev-resources/A.java")).exists();
		assertThat(this.chatModel.prompts()).isEmpty();
	}

	@Test
	void 전문가_해결을_고르면_해결_Agent가_증상을_받고_설계서를_고친_뒤_컴파일_확인으로_간다() throws IOException {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		Path folder = this.folder();
		Path design = folder.resolve("03-1.design.md");
		Files.writeString(design, "selectedFile = e.files[0];", StandardCharsets.UTF_8);
		Files.writeString(folder.resolve("03-2.taskitems.md"), "# 목록", StandardCharsets.UTF_8);
		String designPath = design.toString().replace("\\", "/");
		this.chatModel.answerToolCalls("editFileTexts", "{\"filePath\": \"" + designPath + "\", \"edits\": [{\"before\": \"e.files[0];\", \"after\": \"e.files[0].rawFile;\"}]}");
		this.chatModel.answer("{\"causes\": [\"업로드 안 됨 → Kendo 파일 객체를 그대로 보냄 → kendo.upload.min.js\"], \"changes\": [\"imageBoardView.jsp: rawFile 전송\"],"
			+ " \"designChanges\": [], \"verification\": [\"코드만 읽음: 업로드 후 테이블 행을 확인\"], \"unresolved\": [], \"reproduced\": \"못 함 - 테스트\", \"compiled\": true, \"designSynced\": true}");

		WorkflowStepper flow = this.atStep("step19");
		flow.step("step19");
		PendingApproval pending = this.pendingApproval(flow.execution());
		assertThat(pending.receivers().get("전문가 해결")).isEqualTo("step24 25-전문가 해결 · sm-troubleshooter-agent");

		flow.decide("전문가 해결", "업로드한 파일이 테이블에 저장되지 않음");
		assertThat(flow.currentStepId()).isEqualTo("step22");
		flow.step("step22");
		flow.step("step23");
		flow.step("step24");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.currentStepId()).isEqualTo("step18");
		assertThat(Files.readString(design, StandardCharsets.UTF_8)).contains("e.files[0].rawFile;");
		assertThat(String.valueOf(flow.state("troubleshoot"))).contains("rawFile");
		String prompt = ScriptedChatModel.textOf(this.chatModel.prompts().get(0));
		assertThat(prompt).contains("개발 승인(step19)의 결정: 전문가 해결").contains("업로드한 파일이 테이블에 저장되지 않음").contains("selectedFile = e.files[0];");
	}

	@Test
	void Agent_step은_모두_결정_기록을_읽는다() {
		Map<String, Object> context = WorkFlowContext.create(this.workDir.toString(), WORKFLOW_ID, "test");
		for (String stepId : List.of("step03", "step08", "step12", "step17", "step25", "step26", "step27", "step24")) {
			String with = String.valueOf(this.withOf(stepId));
			assertThat(with).as(stepId).contains("instruction=${arrival").contains("history=${decisions");
		}
		assertThat(context).doesNotContainKey(Constants.WorkFlow.Context.DECISIONS);
	}

	/** 작업 폴더(입력 폴더/실행 시각)를 만듭니다. */
	private Path folder() throws IOException {
		Path folder = this.workDir.resolve(NOW);
		Files.createDirectories(folder);
		return folder;
	}

	/** 앞 step이 저장했을 값을 넣고 stepId부터 시작하는 실행을 만듭니다. */
	private WorkflowStepper atStep(String stepId) {
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString().replace("\\", "/"));
		flow.put("now", NOW).put("request", "## 현업요청\n이미지 게시판\n## 소스경로\n/app/myApp").put("requirement", "# 요구사항").put("sourceDir", "/app/myApp");
		return flow.jumpTo(stepId);
	}

	/** step 정의의 with를 돌려줍니다(계산하지 않은 원래 글자). */
	private Object withOf(String stepId) {
		for (StepDefinition step : this.workFlowRegistry.resolve(WORKFLOW_ID, null).steps()) {
			if (step.id().equals(stepId) && step instanceof AgentStepDefinition agent) {
				return agent.input();
			}
		}
		throw new AssertionError("Agent step이 아닙니다: " + stepId);
	}

}

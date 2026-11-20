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
 * - 승인하고 앞으로 진행하면(step08 → TOOL step09 → step10) TOOL step을 건너뛰어 다음 Agent가 받는다.
 * - 승인 화면은 결정마다 그 의견을 받을 step(Agent)을 보여 준다.
 * - [~ 수정]을 고르면 문서 수정 Agent가 그 의견을 지시로 받는다.
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

		WorkflowStepper flow = this.atStep("step08");
		flow.step("step08");
		assertThat(flow.status()).isEqualTo(WorkFlowExecutionStatus.WAITING_APPROVAL);

		PendingApproval pending = this.pendingApproval(flow.execution());
		assertThat(pending.receivers().get("승인(설계진행)")).isEqualTo("step10 아키텍처 설계 · sm-architect-agent");
		assertThat(pending.receivers().get("영향도 수정")).isEqualTo("step19 영향도분석서 수정 · document-revise-agent");
		assertThat(pending.receivers().get("중지")).isEqualTo("실패로 끝냄(FAIL)");

		flow.decide("승인(설계진행)", "공통 코드 테이블도 같이 봐 주세요");
		flow.step("step09");
		flow.step("step10");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		String prompt = ScriptedChatModel.textOf(this.chatModel.prompts().get(0));
		assertThat(prompt).contains("영향도 분석 승인(step08)의 결정: 승인(설계진행) (승인자 tester)").contains("공통 코드 테이블도 같이 봐 주세요");
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
		flow.decide("요구사항 수정", "TASK-02 완료 기준을 확인할 수 있게 고쳐 주세요");
		assertThat(flow.currentStepId()).isEqualTo("step18");
		flow.step("step18");

		assertThat(flow.status()).isNotEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(flow.currentStepId()).isEqualTo("step04");
		assertThat(Files.readString(requirement, StandardCharsets.UTF_8)).contains("목록에 10건씩 보인다");
		String prompt = ScriptedChatModel.textOf(this.chatModel.prompts().get(0));
		assertThat(prompt).contains(path).contains("리뷰 불통과 처리 결정(step06)의 결정: 요구사항 수정").contains("TASK-02 완료 기준을 확인할 수 있게");
	}

	@Test
	void Agent_step은_모두_결정_기록을_읽는다() {
		Map<String, Object> context = WorkFlowContext.create(this.workDir.toString(), WORKFLOW_ID, "test");
		for (String stepId : List.of("step03", "step07", "step10", "step15", "step18", "step19", "step20")) {
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
		flow.put("now", NOW).put("request", "## 현업요청\n이미지 게시판\n## 소스경로\n/app/testApp").put("requirement", "# 요구사항").put("sourceDir", "/app/testApp");
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

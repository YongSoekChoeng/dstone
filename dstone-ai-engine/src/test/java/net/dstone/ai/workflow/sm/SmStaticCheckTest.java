package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sm-workflow가 사람에게 보내기 전에 기계로 잡을 수 있는 것을 먼저 잡는지 봅니다(LLM 없음, 2.8.0).
 * - 설계 문서의 형식 오류(닫히지 않은 코드 블록 등)는 문서 수정 Agent가 한 번 고친 뒤 설계 승인으로 간다.
 * - 개발 Agent가 손대지 못한 보류를 남기면 한 번 더 부른다(사람이 할 보류만 남으면 부르지 않는다).
 * - 개발 결과의 형식 오류(XML 등)는 개발 Agent가 한 번 고친 뒤 자동 검증으로 간다.
 * - 한도를 다 쓰면 멈추지 않고 다음으로 간다.
 * - 본뜰 기능의 코드는 '참고 기능' 절과 프로젝트ID가 있을 때만 모으고, 모으지 못해도 설계로 간다.
 * </pre>
 */
public class SmStaticCheckTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sm-workflow";

	private static final String NOW = "20261125-1200";

	@TempDir
	Path workDir;

	@Test
	void 설계_문서의_형식_오류는_한_번_고치고_사람에게_간다() throws IOException {
		this.write("03-1.design.md", "# 설계\n\n```java\nclass A {}\n");
		this.write("03-2.taskitems.md", "| TASK | 상태 |\n|---|---|\n| 1 | 대기 |\n");
		WorkflowStepper flow = this.atStep("step50");
		flow.step("step50");
		assertThat(flow.currentStepId()).isEqualTo("step52");
		assertThat(String.valueOf(flow.stateMap("docCheck").get("message"))).contains("03-1.design.md:3").contains("닫히지 않음");

		// 한 번 고친 뒤(step52의 limit이 1번을 셈) 다시 걸리면 한도를 다 써서 고치지 않고 설계 승인으로 간다
		this.countOnce(flow, "step52");
		flow.jumpTo("step50");
		flow.step("step50");
		flow.step("step52");
		assertThat(flow.currentStepId()).isEqualTo("step13");
	}

	@Test
	void 형식이_맞는_설계_문서는_바로_설계_승인으로_간다() throws IOException {
		this.write("03-1.design.md", "# 설계\n\n```java\nclass A {}\n```\n");
		this.write("03-3.ddl(데이터베이스).md", "# DDL\n\n```sql\nCREATE TABLE t (id INT);\n```\n");
		WorkflowStepper flow = this.atStep("step50");
		flow.step("step50");
		assertThat(flow.currentStepId()).isEqualTo("step13");
	}

	@Test
	void 손대지_못한_보류가_남으면_한_번_더_개발한다() {
		WorkflowStepper flow = this.atStep("step54");
		flow.put("develop", this.develop(2, 1));
		flow.step("step54");
		assertThat(flow.currentStepId()).isEqualTo("step57");

		// 두 번째도 남으면 한도(step57의 limit 1번)를 다 써서 컴파일 확인으로 간다
		this.countOnce(flow, "step57");
		flow.jumpTo("step57");
		flow.step("step57");
		assertThat(flow.currentStepId()).isEqualTo("step18");
	}

	@Test
	void 사람이_할_보류만_남으면_다시_부르지_않고_컴파일_확인으로_간다() {
		WorkflowStepper flow = this.atStep("step54");
		flow.put("develop", this.develop(1, 0));
		flow.step("step54");
		assertThat(flow.currentStepId()).isEqualTo("step18");
	}

	@Test
	void 답에_unfinished가_없으면_다시_부르지_않는다() {
		WorkflowStepper flow = this.atStep("step54");
		Map<String, Object> develop = this.develop(1, 0);
		develop.remove("unfinished");
		flow.put("develop", develop);
		flow.step("step54");
		assertThat(flow.currentStepId()).isEqualTo("step18");
	}

	@Test
	void 개발_결과의_형식_오류는_개발_Agent가_한_번_고친다() throws IOException {
		this.write("dev-resources/src/main/resources/sqlmap/a-mapper.xml", "<mapper namespace=\"A\">\n<select id=\"x\">SELECT 1 WHERE a < 2</select>\n</mapper>\n");
		WorkflowStepper flow = this.atStep("step53");
		flow.step("step53");
		assertThat(flow.currentStepId()).isEqualTo("step58");
		assertThat(String.valueOf(flow.stateMap("sourceCheck").get("message"))).contains("a-mapper.xml:2").contains("XML 형식 오류");

		this.countOnce(flow, "step58");
		flow.jumpTo("step58");
		flow.step("step58");
		assertThat(flow.currentStepId()).isEqualTo("step28");
	}

	@Test
	void 프로젝트ID가_없으면_본뜰_기능의_코드_없이_설계로_간다() throws IOException {
		this.write("02.impact.md", "# 영향도\n\n## 참고 기능\n- WEB-INF/views/board/list.jsp\n\n## 결정 필요 사항\n없음\n");
		Files.writeString(this.workDir.resolve("00.request.md"), "## 현업요청\n이미지 게시판\n## 소스경로\n/app/myApp\n", StandardCharsets.UTF_8);
		WorkflowStepper flow = this.atStep("step46");
		flow.step("step46");
		assertThat(String.valueOf(flow.state("referenceEntries"))).contains("WEB-INF/views/board/list.jsp");
		assertThat(flow.state("referenceError")).isNull();
		// 요청서의 프로젝트ID는 step02가 필수로 읽으므로, 그 값이 없으면 코드 모으기가 실패하고 설계로 간다
		flow.step("step48");
		assertThat(flow.currentStepId()).isEqualTo("step11");
		assertThat(String.valueOf(flow.state("referenceError"))).contains("projectId");
	}

	/**
	 * Agent step을 LLM 없이 한 번 실행한 것처럼, 그 step의 횟수 한도를 지금 결정 기록 범위에서 1번 센 상태로 둡니다.
	 * 한도 판정은 엔진이 step 실행 전에 하므로 Agent를 부르지 않고도 "두 번째에는 건너뛴다"를 볼 수 있습니다.
	 */
	private void countOnce(WorkflowStepper flow, String stepId) {
		Map<String, Object> context = flow.execution().context();
		Object scope = net.dstone.ai.common.expression.ContextResolver.resolve("${decisions:}", context, null);
		net.dstone.ai.runtime.workflow.execution.WorkFlowContext.setLimitCount(context, stepId,
			net.dstone.ai.tools.data.DataLogic.countUp(null, Integer.valueOf(1), net.dstone.ai.common.schema.JsonSchemaUtil.toText(scope)));
		this.workFlowExecutionStore.update(flow.execution());
	}

	private Map<String, Object> develop(int pending, int unfinished) {
		Map<String, Object> develop = new LinkedHashMap<String, Object>();
		develop.put("completed", Integer.valueOf(3));
		develop.put("pending", Integer.valueOf(pending));
		develop.put("unfinished", Integer.valueOf(unfinished));
		develop.put("pendingReasons", pending == 0 ? new java.util.ArrayList<String>() : java.util.Arrays.asList("TASK-ITEM-05: 파일 아직 생성되지 않음"));
		return develop;
	}

	private Path write(String name, String text) throws IOException {
		Path file = this.workDir.resolve(NOW).resolve(name);
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
		return file;
	}

	private WorkflowStepper atStep(String stepId) {
		WorkflowStepper flow = this.stepper(WORKFLOW_ID, this.workDir.toString().replace("\\", "/"));
		flow.put("now", NOW).put("request", "## 현업요청\n이미지 게시판").put("requirement", "# 요구사항").put("sourceDir", "/app/myApp");
		return flow.jumpTo(stepId);
	}

}

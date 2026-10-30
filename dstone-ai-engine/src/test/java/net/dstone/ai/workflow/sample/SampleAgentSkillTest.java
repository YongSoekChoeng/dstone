package net.dstone.ai.workflow.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.common.registry.DefinitionReloader;
import net.dstone.ai.common.registry.SkillRegistry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.ScriptedChatModel;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sample-agent-skill: Skill(skills.allowed)을 가진 Agent가 Skill Tool로 SKILL.md 본문을 불러 따르는지 봅니다.
 * 미리 넣은 답으로 "Skill 부르기 → 검사 Tool 부르기 → 답"을 흉내 내고, 엔진이 Tool 결과로 Skill 본문을 돌려줬는지 확인합니다.
 * 정의 검사(없는 Skill 이름, Skill 이름 규칙)는 파일을 쓰지 않는 DefinitionReloader.apply(..., false)로 봅니다.
 * </pre>
 */
public class SampleAgentSkillTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-agent-skill";

	private static final String AGENT_ID = "sample-skill-agent";

	private static final String SKILL_PATH = "skills/sample/sql-review/SKILL.md";

	@Autowired
	private DefinitionReloader reloader;
	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private SkillRegistry skillRegistry;
	@Autowired
	private YamlDefinitionLoader loader;

	@Test
	void Skill을_부르면_SKILL_md_본문이_Tool_결과로_돌아온다() {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 Tool 흐름을 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		this.chatModel.answerToolCalls("Skill", "{\"command\": \"sql-review\"}");
		this.chatModel.answerToolCalls("validateSqlSyntax", "{\"sql\": \"SELECT 1\"}");
		this.chatModel.answer("결과: 통과\nSELECT 1\n고친 곳 없음");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, "SELECT 1 이 SQL 좀 봐줘");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.state(execution).get("answer")).isEqualTo("결과: 통과\nSELECT 1\n고친 곳 없음");
		assertThat(this.chatModel.prompts()).hasSize(3);
		// 두 번째 LLM 호출에는 Skill Tool의 결과(본문과 참고 파일 폴더)가 들어 있어야 합니다.
		String second = ScriptedChatModel.textOf(this.chatModel.prompts().get(1));
		assertThat(second).contains("Skill 'sql-review' 안내입니다.").contains("# SQL 검토 순서").contains("Skill 폴더: ").contains("sql-review");
	}

	@Test
	void 없는_Skill_이름을_부르면_실패_안내가_돌아온다() {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 Tool 흐름을 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		this.chatModel.answerToolCalls("Skill", "{\"command\": \"no-such-skill\"}");
		this.chatModel.answer("Skill을 찾지 못했습니다.");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, "SELECT 1");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(ScriptedChatModel.textOf(this.chatModel.prompts().get(1))).contains("실패: 'no-such-skill'라는 Skill은 쓸 수 없습니다").contains("sql-review");
	}

	@Test
	void Skill은_기동할_때_등록된다() {
		assertThat(this.skillRegistry.find("sql-review")).isNotNull();
		assertThat(this.skillRegistry.find("sql-review").path()).isEqualTo(SKILL_PATH);
		assertThat(this.agentRegistry.find(AGENT_ID).skillNames()).containsExactly("sql-review");
	}

	@Test
	void 없는_Skill을_적은_Agent는_검사에서_막힌다() {
		String path = this.agentRegistry.pathOf(AGENT_ID);
		String text = this.source(path).replace("allowed: [sql-review]", "allowed: [sql-reviw]");

		String message = this.checkFails(Map.of(path, text));

		assertThat(message).contains("sql-reviw").contains("등록된 Skill이 아닙니다");
	}

	@Test
	void Skill_이름이_폴더_이름과_다르면_검사에서_막힌다() {
		String text = this.source(SKILL_PATH).replace("name: sql-review", "name: sql-check");

		String message = this.checkFails(Map.of(SKILL_PATH, text));

		assertThat(message).contains("sql-check").contains("폴더 이름");
	}

	@Test
	void Skill_이름은_영문_소문자와_하이픈만_쓴다() {
		String path = "skills/sample/Sql_Review/SKILL.md";
		String text = this.source(SKILL_PATH).replace("name: sql-review", "name: Sql_Review");

		String message = this.checkFails(Map.of(path, text));

		assertThat(message).contains("Sql_Review").contains("영문 소문자");
	}

	@Test
	void Skill_설명이_없으면_검사에서_막힌다() {
		String text = this.source(SKILL_PATH).replaceAll("(?m)^description:.*$", "");

		String message = this.checkFails(Map.of(SKILL_PATH, text));

		assertThat(message).contains("description이 없습니다");
	}

	/** 바꾼 내용으로 검사만 하고(파일은 쓰지 않음), 실패 메시지를 돌려줍니다. 통과하면 테스트를 실패시킵니다. */
	private String checkFails(Map<String, String> changes) {
		try {
			this.reloader.apply(changes, false);
		} catch (IllegalStateException e) {
			return e.getMessage();
		}
		throw new AssertionError("검사를 통과하면 안 됩니다: " + changes.keySet());
	}

	/** 정의 폴더의 파일을 글자 그대로 읽습니다. */
	private String source(String path) {
		try {
			return Files.readString(this.loader.rootDir().resolve(path));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}

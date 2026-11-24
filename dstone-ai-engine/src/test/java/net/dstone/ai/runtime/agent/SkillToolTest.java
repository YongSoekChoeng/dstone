package net.dstone.ai.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import net.dstone.ai.common.definition.skill.SkillDefinition;

/**
 * <pre>
 * Skill Tool이 한 호출 안에서 같은 Skill을 두 번 주지 않는지 서버 없이 확인합니다(SkillTool + ReadHistory).
 * </pre>
 */
class SkillToolTest {

	private static final String BODY = "샘플 Skill 본문입니다.";

	@Test
	void 처음_부르면_본문을_주고_또_부르면_안내만_준다() {
		ReadHistory history = new ReadHistory();
		ToolCallback tool = SkillTool.build("test-agent", List.of(skill("sample-skill"), skill("other-skill")), history);

		assertThat(tool.call("{\"command\":\"sample-skill\"}")).contains(BODY);

		String again = tool.call("{\"command\":\"sample-skill\"}");
		assertThat(again).doesNotContain(BODY).contains("이미 받았고").contains("최종 답");

		// 다른 Skill은 따로 셉니다.
		assertThat(tool.call("{\"command\":\"other-skill\"}")).contains(BODY);
	}

	@Test
	void 장부를_비우면_다시_본문을_준다() {
		ReadHistory history = new ReadHistory();
		ToolCallback tool = SkillTool.build("test-agent", List.of(skill("sample-skill")), history);

		tool.call("{\"command\":\"sample-skill\"}");
		// 오래된 Tool 결과를 줄였을 때(ToolResultCompactionAdvisor) 장부가 비워집니다.
		history.clear();

		assertThat(tool.call("{\"command\":\"sample-skill\"}")).contains(BODY);
	}

	@Test
	void 장부가_없으면_부를_때마다_본문을_준다() {
		ToolCallback tool = SkillTool.build("test-agent", List.of(skill("sample-skill")), null);

		assertThat(tool.call("{\"command\":\"sample-skill\"}")).contains(BODY);
		assertThat(tool.call("{\"command\":\"sample-skill\"}")).contains(BODY);
	}

	@Test
	void 없는_Skill은_장부와_상관없이_실패다() {
		ToolCallback tool = SkillTool.build("test-agent", List.of(skill("sample-skill")), new ReadHistory());

		assertThat(tool.call("{\"command\":\"no-such-skill\"}")).contains("실패");
		assertThat(tool.call("{\"command\":\"no-such-skill\"}")).contains("실패");
	}

	private static SkillDefinition skill(String name) {
		SkillDefinition.FrontMatter front = new SkillDefinition.FrontMatter(name, name + " 설명", null, null, null, null);
		return new SkillDefinition("skills/" + name + "/SKILL.md", null, front, BODY);
	}

}

package net.dstone.ai.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.ai.common.definition.skill.SkillDefinition;
import net.dstone.ai.common.registry.SkillRegistry;

/**
 * <pre>
 * skills.allowed의 항목(이름, 폴더 지정 projects/**)이 실제 Skill 목록으로 어떻게 풀리는지 봅니다.
 * 스프링 없이 SkillRegistry.select()만 부릅니다.
 * </pre>
 */
public class SkillSelectTest {

	/** 시험용 Skill 하나를 만듭니다. */
	private static SkillDefinition skill(String path, String name) {
		SkillDefinition.FrontMatter front = new SkillDefinition.FrontMatter(name, name + " 설명", null, null, null, null, null);
		return new SkillDefinition(path, null, front, "본문");
	}

	/** 시험용 Skill 묶음입니다. projects 폴더에 둘, 다른 폴더에 둘이 있습니다. */
	private static Map<String, SkillDefinition> skills() {
		Map<String, SkillDefinition> map = new LinkedHashMap<String, SkillDefinition>();
		map.put("zeta-conv", skill("skills/projects/zeta-conv/SKILL.md", "zeta-conv"));
		map.put("alpha-conv", skill("skills/projects/alpha-conv/SKILL.md", "alpha-conv"));
		map.put("source-investigation", skill("skills/sm/source-investigation/SKILL.md", "source-investigation"));
		map.put("projects-like", skill("skills/projectsx/projects-like/SKILL.md", "projects-like"));
		return map;
	}

	private static List<String> names(List<SkillDefinition> list) {
		List<String> result = new ArrayList<String>();
		for (SkillDefinition skill : list) {
			result.add(skill.name());
		}
		return result;
	}

	@Test
	void 폴더_지정은_그_폴더_아래_Skill만_이름순으로_준다() {
		List<SkillDefinition> selected = SkillRegistry.select(List.of("projects/**"), skills());
		// projectsx 폴더처럼 앞글자만 같은 폴더는 걸리지 않습니다.
		assertThat(names(selected)).containsExactly("alpha-conv", "zeta-conv");
	}

	@Test
	void 이름과_폴더_지정을_섞으면_중복_없이_적은_순서대로_준다() {
		List<SkillDefinition> selected = SkillRegistry.select(List.of("zeta-conv", "source-investigation", "projects/**", "없는-이름"), skills());
		assertThat(names(selected)).containsExactly("zeta-conv", "source-investigation", "alpha-conv");
	}

	@Test
	void input_값으로_채운_폴더의_Skill만_준다() {
		Map<String, SkillDefinition> map = new LinkedHashMap<String, SkillDefinition>();
		map.put("a-conv", skill("skills/projects/alpha/a-conv/SKILL.md", "a-conv"));
		map.put("b-conv", skill("skills/projects/beta/b-conv/SKILL.md", "b-conv"));
		List<String> entries = List.of("projects/${input.project_id}/**");

		List<String> resolved = SkillRegistry.resolveEntries(entries, Map.of("project_id", "alpha"));

		assertThat(resolved).containsExactly("projects/alpha/**");
		assertThat(names(SkillRegistry.select(resolved, map))).containsExactly("a-conv");
		assertThat(SkillRegistry.widestPattern(entries.get(0))).isEqualTo("projects/**");
		assertThat(SkillRegistry.widestPattern("${input.project_id}/**")).isNull();
	}

	@Test
	void 값이_없거나_폴더_이름이_아니면_그_항목을_뺀다() {
		List<String> entries = List.of("sql-review", "projects/${input.project_id}/**");

		assertThat(SkillRegistry.resolveEntries(entries, Map.of())).containsExactly("sql-review");
		assertThat(SkillRegistry.resolveEntries(entries, null)).containsExactly("sql-review");
		for (String bad : List.of(" ", "..", "alpha/beta", "*")) {
			assertThat(SkillRegistry.resolveEntries(entries, Map.of("project_id", bad))).containsExactly("sql-review");
		}
	}

	@Test
	void preload_표시가_있는_Skill만_항상_읽는_것으로_가른다() {
		SkillDefinition.FrontMatter front = new SkillDefinition.FrontMatter("basics", "설명", null, null, null, null, Boolean.TRUE);
		List<SkillDefinition> selected = List.of(new SkillDefinition("skills/projects/alpha/basics/SKILL.md", null, front, "본문"),
			skill("skills/projects/alpha/a-conv/SKILL.md", "a-conv"));

		assertThat(names(SkillRegistry.filter(selected, true))).containsExactly("basics");
		assertThat(names(SkillRegistry.filter(selected, false))).containsExactly("a-conv");
	}

	@Test
	void 걸리는_Skill이_없는_폴더_지정을_알아본다() {
		assertThat(SkillRegistry.isFolderPattern("projects/**")).isTrue();
		assertThat(SkillRegistry.isFolderPattern("projects")).isFalse();
		assertThat(SkillRegistry.matchesAny("projects/**", skills())).isTrue();
		assertThat(SkillRegistry.matchesAny("nothing/**", skills())).isFalse();
	}
}

package net.dstone.ai.common.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.skill.SkillDefinition;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 모든 Skill을 담아 두고 이름으로 찾아 주는 등록소입니다.
 * YamlDefinitionLoader가 skills 폴더의 SKILL.md를 읽고, 이 클래스가 기동할 때 검사해서 보관합니다.
 * 기동 뒤에 파일을 고치면 DefinitionReloader가 check()로 검사하고, 전부 통과했을 때만 install()로 바꿔 끼웁니다.
 *
 * 기동할 때 막는 것(Agent Skills 공개 형식의 규칙을 그대로 따릅니다)
 *   - name이 없거나, 영문 소문자/숫자/하이픈 64자 이내가 아님
 *   - name이 SKILL.md가 들어 있는 폴더 이름과 다름
 *   - description이 없거나 1,024자를 넘음
 *   - 본문이 비어 있음
 *   - 같은 이름의 Skill이 둘 이상 있음
 *   - 파일 이름이 SKILL.md가 아님(예: MY-SKILL.md)
 * </pre>
 */
@Component
public class SkillRegistry extends BaseObject {

	/** Skill 이름 규칙입니다. 영문 소문자와 숫자를 하이픈으로 이은 모양이고, 하이픈으로 시작하거나 끝나거나 두 번 이어질 수 없습니다. */
	private static final Pattern NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

	/** Skill 이름의 최대 길이입니다. */
	private static final int MAX_NAME_LENGTH = 64;

	/** Skill 설명의 최대 길이입니다. 설명은 Skill을 쓰는 모든 Agent의 Tool 설명에 늘 들어가므로 짧게 둡니다. */
	private static final int MAX_DESCRIPTION_LENGTH = 1024;

	@Autowired
	private YamlDefinitionLoader loader;

	/** 등록된 Skill(이름 → 정의). 다시 읽을 때 통째로 바꿔 끼웁니다. */
	private volatile Map<String, SkillDefinition> byName = Map.of();

	/** 앱이 기동될 때 Skill을 전부 읽고 검사한 뒤 등록합니다. 틀린 Skill이 하나라도 있으면 기동을 실패시킵니다. */
	@PostConstruct
	public void load() {
		this.install(this.check(this.loader.loadSkills()));
	}

	/**
	 * <pre>
	 * Skill들을 검사하고, 통과하면 이름 → 정의 맵을 돌려줍니다. 등록은 하지 않습니다(install()이 합니다).
	 * 틀렸으면 IllegalStateException입니다.
	 * </pre>
	 *
	 * @param skills 검사할 Skill들
	 */
	public Map<String, SkillDefinition> check(List<SkillDefinition> skills) {
		Map<String, SkillDefinition> resolved = new LinkedHashMap<>();
		for (SkillDefinition skill : skills) {
			String where = skill.path() + " - ";
			String fileName = skill.path().substring(skill.path().lastIndexOf('/') + 1);
			if (!Constants.Definition.SKILL_FILE.equals(fileName)) {
				throw new IllegalStateException(where + "Skill 파일 이름은 " + Constants.Definition.SKILL_FILE + "여야 합니다. Skill마다 폴더를 하나 만들고 그 안에 " + Constants.Definition.SKILL_FILE + "를 두십시오.");
			}
			String name = skill.name();
			if (StringUtil.isEmpty(name)) {
				throw new IllegalStateException(where + "머리말에 name이 있어야 합니다.");
			}
			if (name.length() > MAX_NAME_LENGTH || !NAME.matcher(name).matches()) {
				throw new IllegalStateException(where + "name '" + name + "'은 영문 소문자, 숫자, 하이픈(-)만 써서 " + MAX_NAME_LENGTH + "자 이내로 적어야 합니다(예: sql-review).");
			}
			String folder = this.folderOf(skill.path());
			if (!name.equals(folder)) {
				throw new IllegalStateException(where + "name '" + name + "'이 폴더 이름 '" + folder + "'과 다릅니다. 폴더 이름을 Skill 이름과 같게 맞추십시오.");
			}
			if (StringUtil.isEmpty(skill.description())) {
				throw new IllegalStateException(where + "description이 없습니다. Agent가 이 설명을 보고 언제 Skill을 쓸지 정하므로, 무슨 일을 할 때 쓰는 Skill인지 적으십시오.");
			}
			if (skill.description().length() > MAX_DESCRIPTION_LENGTH) {
				throw new IllegalStateException(where + "description이 " + MAX_DESCRIPTION_LENGTH + "자를 넘습니다(" + skill.description().length() + "자). 자세한 내용은 본문으로 옮기십시오.");
			}
			if (StringUtil.isEmpty(skill.content())) {
				throw new IllegalStateException(where + "본문이 비어 있습니다. 머리말(--- ... ---) 아래에 작업 안내를 적으십시오.");
			}
			SkillDefinition before = resolved.putIfAbsent(name, skill);
			if (before != null) {
				throw new IllegalStateException("skill 이름이 중복 등록되었습니다: " + name + " (" + before.path() + ", " + skill.path() + ")");
			}
		}
		return resolved;
	}

	/**
	 * check()를 통과한 Skill들로 등록 내용을 통째로 바꿉니다.
	 *
	 * @param checked check()가 돌려준 맵
	 */
	public void install(Map<String, SkillDefinition> checked) {
		this.byName = Map.copyOf(checked);
		LogUtil.sysout("dstone-ai-engine skill: 등록된 Skill = " + (this.byName.isEmpty() ? "없음" : this.byName.keySet()));
	}

	/** 지금 등록된 Skill 전부(이름 → 정의)입니다. 바뀌지 않는 맵입니다. */
	public Map<String, SkillDefinition> all() {
		return this.byName;
	}

	/**
	 * 이름으로 Skill을 찾습니다. 없으면 null입니다.
	 *
	 * @param name Skill 이름
	 */
	public SkillDefinition find(String name) {
		return this.byName.get(name);
	}

	/** 등록된 Skill을 이름 순으로 돌려줍니다(화면 목록용). */
	public List<SkillDefinition> list() {
		List<SkillDefinition> result = new ArrayList<>(this.byName.values());
		result.sort(new Comparator<SkillDefinition>() {
			@Override
			public int compare(SkillDefinition a, SkillDefinition b) {
				return a.name().compareTo(b.name());
			}
		});
		return result;
	}

	/** skills/sample/sql-review/SKILL.md에서 sql-review처럼, SKILL.md가 들어 있는 폴더 이름을 꺼냅니다. */
	private String folderOf(String path) {
		int end = path.lastIndexOf('/');
		if (end < 0) {
			return "";
		}
		int start = path.lastIndexOf('/', end - 1);
		return path.substring(start + 1, end);
	}

}

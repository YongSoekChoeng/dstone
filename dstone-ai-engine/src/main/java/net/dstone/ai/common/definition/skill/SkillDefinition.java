package net.dstone.ai.common.definition.skill;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * <pre>
 * Skill 하나의 정의입니다.
 * Skill은 "이런 일을 할 때는 이렇게 하라"는 작업 안내서입니다. Agent는 목록(이름과 설명)만 보고 있다가,
 * 지금 일에 맞는 Skill이 있으면 Skill Tool로 불러서 본문을 읽고 그대로 따릅니다.
 * 프롬프트에 모든 안내를 미리 넣어 두지 않고 필요할 때만 꺼내 읽게 하려는 것입니다.
 *
 * ■ 어디서 오나
 *   conf/definitions/skills/<폴더>/<Skill 이름>/SKILL.md 파일 하나 = Skill 하나입니다.
 *   같은 폴더의 다른 파일(reference.md, 예시 파일 등)은 참고 자료입니다. 엔진은 읽지 않고,
 *   Agent가 Skill 본문의 안내를 보고 readFile로 읽습니다.
 *
 * ■ 파일 모양(Agent Skills 공개 형식과 같습니다)
 *   ---
 *   name: sql-review                 ← (필수) 폴더 이름과 같아야 합니다. 영문 소문자, 숫자, 하이픈
 *   description: SQL을 검토할 때 ...  ← (필수) Agent가 이 설명을 보고 언제 쓸지 정합니다
 *   ---
 *   # SQL 검토 순서                   ← 본문. Skill을 부르면 Agent에게 그대로 갑니다
 *   1. ...
 *
 * ■ 누가 쓰나
 *   Agent 머리말의 skills.allowed에 이름을 적은 Agent만 씁니다(runtime.agent.SkillTool).
 * </pre>
 *
 * @param path    정의 폴더 기준 경로입니다(예: skills/sample/sql-review/SKILL.md).
 * @param baseDir Skill 폴더의 절대 경로입니다. 정의 폴더가 디스크 폴더가 아니면 null입니다.
 * @param front   머리말입니다.
 * @param content 본문입니다.
 */
public record SkillDefinition(String path, String baseDir, FrontMatter front, String content) {

	/**
	 * <pre>
	 * SKILL.md의 머리말입니다. 공개 형식의 키를 그대로 받습니다.
	 * 엔진이 쓰는 것은 name과 description뿐이고, 나머지는 다른 곳에서 만든 Skill을 그대로 가져와도
	 * 읽히게 하려고 받아만 둡니다. 이 밖의 키는 오타일 수 있어서 읽을 때 막습니다.
	 * </pre>
	 *
	 * @param name          (필수) Skill 이름입니다.
	 * @param description   (필수) 언제 쓰는 Skill인지 적은 설명입니다.
	 * @param license       라이선스 표기입니다. 엔진은 쓰지 않습니다.
	 * @param compatibility 실행 환경 요구 사항입니다. 엔진은 쓰지 않습니다.
	 * @param metadata      그 밖의 정보입니다. 엔진은 쓰지 않습니다.
	 * @param allowedTools  공개 형식의 allowed-tools입니다. 엔진은 쓰지 않습니다(쓸 Tool은 Agent의 tools.allowed가 정합니다).
	 */
	public record FrontMatter(
		String name
		, String description
		, String license
		, String compatibility
		, Map<String, Object> metadata
		, @JsonProperty("allowed-tools") Object allowedTools
		) {
	}

	/** Skill 이름입니다. */
	public String name() {
		return this.front == null ? null : this.front.name();
	}

	/** Skill 설명입니다. */
	public String description() {
		return this.front == null ? null : this.front.description();
	}

}

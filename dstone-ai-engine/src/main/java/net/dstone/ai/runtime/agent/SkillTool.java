package net.dstone.ai.runtime.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.skill.SkillDefinition;
import net.dstone.common.utils.LogUtil;

/**
 * <pre>
 * Agent의 skills.allowed에 적힌 Skill들을 Tool 하나(이름 Skill)로 만들어 줍니다.
 * spring-ai-agent-utils의 SkillsTool과 같은 방식이고, 그 라이브러리의 Skill 레코드와 인자 모양(SkillsInput)을 그대로 씁니다.
 *
 *   LLM에게 보이는 것        실제 값
 *   Tool 설명                고른 Skill들의 이름과 설명 목록
 *   Tool 인자                command = 부를 Skill 이름
 *   Tool 응답                SKILL.md 본문(+ 참고 파일이 있는 폴더 경로)
 *
 * 라이브러리의 SkillsTool.Builder를 쓰지 않고 직접 만드는 이유:
 * - Builder는 폴더를 통째로 읽거나, 폴더 경로 없이 Skill을 넣는 방법만 있습니다. 여기서는 등록소에 이미 읽어 둔 Skill 중
 *   이 Agent의 것만 골라 넣어야 하고, 참고 파일을 읽을 폴더 경로도 함께 알려 줘야 합니다.
 * - 설명문과 응답을 한국어로, "실패: ..." 같은 엔진의 다른 Tool과 같은 모양으로 맞추려고 합니다.
 *
 * Skill 본문은 Tool 결과로 대화에 들어가므로, 다른 Tool처럼 결과 크기 상한과 호출 횟수 한도가 함께 씌워집니다(AgentExecutor).
 *
 * 같은 Skill을 또 부르면 본문을 다시 주지 않고 짧은 안내만 돌려줍니다(ReadHistory.recordSkill()).
 * 본문은 이미 대화에 있으니 다시 줄 필요가 없고, 다시 주면 부를 때마다 본문만큼 입력이 불어납니다.
 * (2026-11-24 sm-developer-agent가 일을 마친 뒤 같은 Skill을 30번 연달아 불러 입력이 63k에서 117k 토큰까지 늘었습니다.)
 * 오래된 Tool 결과를 줄여 본문이 잘렸으면 장부가 비워지므로 그때는 다시 받을 수 있습니다(ToolResultCompactionAdvisor).
 * 장부가 없으면(dstone.ai.agent.tool.reread-guard=false) 부를 때마다 본문을 줍니다.
 * </pre>
 */
public final class SkillTool {

	/** LLM에게 보이는 Tool 이름입니다. */
	public static final String NAME = "Skill";

	/** Skill 폴더의 참고 파일을 읽을 때 쓰는 Tool 이름입니다(tools.utils.FileUtil.readFile). */
	public static final String READ_TOOL = "readFile";

	private static final String DESCRIPTION = String.join("\n"
		, "작업 안내서(Skill)를 불러옵니다."
		, "지금 할 일에 맞는 Skill이 아래 목록에 있으면, 일을 시작하기 전에 그 Skill의 이름으로 이 Tool을 부르십시오."
		, "돌아온 안내를 그대로 따르십시오. 같은 Skill은 한 번만 부르면 됩니다. 목록에 없는 이름은 부르지 마십시오."
		, ""
		, "<available_skills>"
		, "%s"
		, "</available_skills>"
	);

	private SkillTool() {
	}

	/**
	 * <pre>
	 * Skill들을 Tool 하나로 만듭니다. Skill이 하나도 없으면 null입니다.
	 * </pre>
	 *
	 * @param agentId 이 Tool을 쓰는 Agent의 id(로그용)
	 * @param skills  이 Agent가 쓸 수 있는 Skill들
	 * @param history 이번 호출에서 받은 Skill을 적어 둘 장부. null이면 같은 Skill을 또 불러도 본문을 줍니다
	 */
	public static ToolCallback build(String agentId, List<SkillDefinition> skills, ReadHistory history) {
		if (skills == null || skills.isEmpty()) {
			return null;
		}
		Map<String, SkillsTool.Skill> byName = new LinkedHashMap<>();
		List<String> listing = new ArrayList<>();
		for (SkillDefinition skill : skills) {
			Map<String, Object> front = new LinkedHashMap<>();
			front.put("name", skill.name());
			front.put("description", skill.description());
			SkillsTool.Skill librarySkill = new SkillsTool.Skill(skill.baseDir() == null ? "" : skill.baseDir(), front, skill.content());
			byName.put(skill.name(), librarySkill);
			listing.add(librarySkill.toXml());
		}
		return FunctionToolCallback.builder(NAME, new SkillFunction(agentId, byName, history))
			.description(String.format(DESCRIPTION, String.join("\n", listing)))
			.inputType(SkillsTool.SkillsInput.class)
			.build();
	}

	/**
	 * <pre>
	 * Skill 폴더에 SKILL.md 말고 다른 파일(참고 자료)이 있는지 봅니다.
	 * 있으면 그 Skill을 쓰는 Agent에게 readFile이 있어야 합니다(AgentRegistry가 기동할 때 경고합니다).
	 * </pre>
	 *
	 * @param skill 살펴볼 Skill
	 */
	public static boolean hasReferenceFiles(SkillDefinition skill) {
		if (skill.baseDir() == null) {
			return false;
		}
		Path dir = Paths.get(skill.baseDir());
		if (!Files.isDirectory(dir)) {
			return false;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			Iterator<Path> iterator = paths.iterator();
			while (iterator.hasNext()) {
				Path path = iterator.next();
				if (Files.isRegularFile(path) && !Constants.Definition.SKILL_FILE.equals(path.getFileName().toString())) {
					return true;
				}
			}
			return false;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * LLM이 Skill을 불렀을 때 본문을 돌려주는 함수입니다.
	 */
	private static final class SkillFunction implements Function<SkillsTool.SkillsInput, String> {

		private final String agentId;
		private final Map<String, SkillsTool.Skill> byName;
		private final ReadHistory history;

		private SkillFunction(String agentId, Map<String, SkillsTool.Skill> byName, ReadHistory history) {
			this.agentId = agentId;
			this.byName = byName;
			this.history = history;
		}

		@Override
		public String apply(SkillsTool.SkillsInput input) {
			String name = input == null || input.command() == null ? "" : input.command().trim();
			SkillsTool.Skill skill = this.byName.get(name);
			if (skill == null) {
				LogUtil.sysout("dstone-ai-engine skill: agent[" + this.agentId + "]가 없는 Skill '" + name + "'을 불렀습니다.");
				return "실패: '" + name + "'라는 Skill은 쓸 수 없습니다. 쓸 수 있는 Skill = " + this.byName.keySet();
			}
			// 이번 호출에서 이미 준 Skill이면 본문 대신 안내만 돌려줍니다.
			if (this.history != null && !this.history.recordSkill(name)) {
				LogUtil.sysout("dstone-ai-engine skill: agent[" + this.agentId + "]가 이미 받은 Skill '" + name + "'을 또 불러서 본문 대신 안내만 돌려줬습니다.");
				return alreadyLoadedNotice(name);
			}
			LogUtil.sysout("dstone-ai-engine skill: agent[" + this.agentId + "] -> skill[" + name + "] " + skill.content().length() + "자");
			StringBuilder reply = new StringBuilder();
			reply.append("Skill '").append(name).append("' 안내입니다.\n");
			if (!skill.basePath().isEmpty()) {
				reply.append("Skill 폴더: ").append(skill.basePath())
					.append(" (안내에 나오는 파일은 이 폴더 기준 경로입니다. 필요한 파일만 ").append(READ_TOOL).append("로 읽으십시오.)\n");
			}
			reply.append("\n").append(skill.content());
			return reply.toString();
		}

		/** 이미 받은 Skill을 또 불렀을 때 돌려주는 안내입니다. */
		private static String alreadyLoadedNotice(String name) {
			return "(안내: Skill '" + name + "'의 안내는 이번 작업에서 이미 받았고 위 대화에 그대로 있습니다. 다시 부르지 마십시오. "
				+ "그 안내대로 하던 일을 이어 가고, 할 일을 다 했으면 최종 답을 내십시오.)";
		}

	}

}

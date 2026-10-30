package net.dstone.ai.common.registry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.skill.SkillDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.loader.DefinitionFile;
import net.dstone.ai.common.loader.DefinitionText;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;

/**
 * <pre>
 * 엔진을 다시 띄우지 않고 Skill, Agent, Workflow 정의를 다시 읽습니다.
 *
 * 순서는 기동할 때와 같습니다: Skill을 전부 읽어 검사하고, 그 Skill들로 Agent를, 그 Agent들로 Workflow를 전부 검사합니다.
 * 바뀐 것만 검사하지 않고 늘 전부 검사합니다. Agent 하나를 고치면 그 Agent를 부르는 Workflow가 틀어질 수 있기 때문입니다.
 *
 * 하나라도 틀리면 아무것도 바꾸지 않고(파일도 쓰지 않고) 오류만 던집니다. 등록된 정의는 그대로입니다.
 * 전부 통과했을 때만 파일을 쓰고 SkillRegistry, AgentRegistry, WorkFlowRegistry를 한꺼번에 바꿔 끼웁니다.
 * Skill 폴더의 참고 파일은 엔진이 읽어 두지 않으므로(Agent가 그때그때 readFile로 읽습니다) 다시 읽을 것이 없습니다.
 *
 * MCP 서버 정의(mcp/**.yml)는 다시 읽지 않습니다. MCP는 기동할 때 외부 프로세스와 연결까지 맺어 두기 때문에,
 * 바꾸려면 엔진을 다시 띄워야 합니다.
 * </pre>
 */
@Component
public class DefinitionReloader extends BaseObject {

	@Autowired
	private YamlDefinitionLoader loader;
	@Autowired
	private SkillRegistry skillRegistry;
	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private WorkFlowRegistry workFlowRegistry;

	/**
	 * 다시 읽거나 검사한 결과입니다.
	 *
	 * @param workflows 검사를 통과한 Workflow 수
	 * @param agents    검사를 통과한 Agent 수
	 * @param skills    검사를 통과한 Skill 수
	 */
	public record Result(int workflows, int agents, int skills) {
	}

	/**
	 * <pre>
	 * 정의 폴더의 파일을 다시 읽어 등록합니다(손으로 고친 파일을 반영할 때).
	 * 틀린 파일이 있으면 IllegalStateException이고, 등록된 정의는 그대로입니다.
	 * </pre>
	 */
	public synchronized Result reload() {
		return this.apply(Map.of(), true);
	}

	/**
	 * <pre>
	 * 바꿀 파일들(changes)을 디스크의 파일 위에 얹어 Agent와 Workflow를 전부 검사합니다.
	 * write가 true이고 전부 통과하면 changes를 파일에 쓰고 등록 내용을 바꿔 끼웁니다.
	 * write가 false면 검사만 하고 아무것도 바꾸지 않습니다.
	 * 틀리면 IllegalStateException입니다(메시지가 곧 화면에 보일 이유입니다).
	 * </pre>
	 *
	 * @param changes 정의 폴더 기준 경로 → 새 내용. 내용이 null이면 그 파일을 지웁니다
	 * @param write   통과했을 때 파일을 쓰고 등록할지
	 */
	public synchronized Result apply(Map<String, String> changes, boolean write) {
		List<SkillDefinition> skillFiles = new ArrayList<>();
		for (DefinitionText text : this.merge(Constants.Definition.SKILL_DIR, Constants.Definition.SKILL_FILE, changes)) {
			skillFiles.add(this.loader.parseSkill(text));
		}
		List<DefinitionFile<AgentDefinition>> agentFiles = new ArrayList<>();
		for (DefinitionText text : this.merge(Constants.Definition.AGENT_DIR, Constants.Definition.AGENT_SUFFIX, changes)) {
			agentFiles.add(new DefinitionFile<>(text.path(), this.loader.parseAgent(text)));
		}
		List<DefinitionFile<WorkFlowDefinition>> workflowFiles = new ArrayList<>();
		for (DefinitionText text : this.merge(Constants.Definition.WORKFLOW_DIR, Constants.Definition.WORKFLOW_SUFFIX, changes)) {
			workflowFiles.add(new DefinitionFile<>(text.path(), this.loader.parseWorkflow(text)));
		}
		Map<String, SkillDefinition> skills = this.skillRegistry.check(skillFiles);
		Map<String, AgentDefinition> agents = this.agentRegistry.check(agentFiles, skills);
		Map<String, WorkFlowDefinition> workflows = this.workFlowRegistry.check(workflowFiles, agents);
		if (write) {
			this.writeFiles(changes);
			this.skillRegistry.install(skills);
			this.agentRegistry.install(agentFiles, agents);
			this.workFlowRegistry.install(workflowFiles, workflows);
			LogUtil.sysout("dstone-ai-engine definition: 다시 읽음 - Skill " + skills.size() + "개, Agent " + agents.size() + "개, Workflow " + workflows.size() + "개"
				+ (changes.isEmpty() ? "" : ", 바꾼 파일 " + changes.keySet()));
		}
		return new Result(workflows.size(), agents.size(), skills.size());
	}

	/**
	 * <pre>
	 * 한 종류의 파일을 디스크에서 읽고, 그 종류에 해당하는 changes를 얹은 목록을 돌려줍니다(경로 순).
	 * </pre>
	 *
	 * @param dir     종류 폴더(workflows, agents, skills)
	 * @param suffix  파일 이름 꼬리(.yml, .md, SKILL.md)
	 * @param changes 정의 폴더 기준 경로 → 새 내용(null이면 지움)
	 */
	private List<DefinitionText> merge(String dir, String suffix, Map<String, String> changes) {
		Map<String, String> byPath = new LinkedHashMap<>();
		for (DefinitionText text : this.loader.readTexts(dir, suffix)) {
			byPath.put(text.path(), text.text());
		}
		for (Map.Entry<String, String> change : changes.entrySet()) {
			if (change.getKey().startsWith(dir + "/") && change.getKey().endsWith(suffix)) {
				if (change.getValue() == null) {
					byPath.remove(change.getKey());
				} else {
					byPath.put(change.getKey(), change.getValue());
				}
			}
		}
		List<DefinitionText> texts = new ArrayList<>();
		for (Map.Entry<String, String> entry : byPath.entrySet()) {
			texts.add(new DefinitionText(entry.getKey(), entry.getValue()));
		}
		texts.sort(new Comparator<DefinitionText>() {
			@Override
			public int compare(DefinitionText a, DefinitionText b) {
				return a.path().compareTo(b.path());
			}
		});
		return texts;
	}

	/**
	 * <pre>
	 * changes를 정의 폴더에 씁니다. 내용이 null이면 지웁니다.
	 * 쓰는 도중에 끊겨 반쪽짜리 파일이 남지 않게, 같은 폴더에 임시 파일로 쓴 뒤 이름을 바꿉니다.
	 * </pre>
	 *
	 * @param changes 정의 폴더 기준 경로 → 새 내용(null이면 지움)
	 */
	private void writeFiles(Map<String, String> changes) {
		if (changes.isEmpty()) {
			return;
		}
		Path root = this.loader.rootDir();
		if (root == null) {
			throw new IllegalStateException("정의 폴더가 디스크 폴더(file:)가 아니라서 저장할 수 없습니다: " + this.loader.location());
		}
		for (Map.Entry<String, String> change : changes.entrySet()) {
			Path target = root.resolve(change.getKey()).normalize();
			if (!target.startsWith(root)) {
				throw new IllegalStateException("정의 폴더 밖에는 쓸 수 없습니다: " + change.getKey());
			}
			try {
				if (change.getValue() == null) {
					Files.deleteIfExists(target);
				} else {
					Files.createDirectories(target.getParent());
					Path temp = Files.createTempFile(target.getParent(), ".saving-", ".tmp");
					Files.writeString(temp, change.getValue(), StandardCharsets.UTF_8);
					Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				}
			} catch (IOException e) {
				throw new IllegalStateException(change.getKey() + " 파일을 " + (change.getValue() == null ? "지우지" : "쓰지") + " 못했습니다 - " + e.getMessage(), e);
			}
		}
	}

}

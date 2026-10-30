package net.dstone.ai.common.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.skill.SkillDefinition;
import net.dstone.ai.common.expression.PromptText;
import net.dstone.ai.common.loader.DefinitionFile;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.ai.common.schema.JsonSchemaUtil;
import net.dstone.ai.runtime.agent.SkillTool;
import net.dstone.ai.runtime.agent.SubAgentTasks;
import net.dstone.ai.runtime.agent.SubAgentToolCallback;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 모든 Agent 정보를 담아두고, id로 찾아 주는 등록소입니다. 
 * 로딩 순서는 YamlDefinitionLoader가 정의 폴더의 agents/**.md 파일들을 읽어서 AgentDefinition으로 바꾸고, 
 * 이 AgentRegistry가 그 AgentDefinition들을 앱이 기동될 때 모아서 보관하는 식입니다.
 * 기동 뒤에 파일을 고치면 DefinitionReloader가 check()로 검사하고, 전부 통과했을 때만 install()로 바꿔 끼웁니다.
 *
 * Agent를 찾아 쓰는 경로는 두 가지입니다. 
 * api.controller.ChatController가 request.agent() 값으로 직접 찾는 경우와, 
 * runtime.step의 AgentStepExecutor가 step의 agent 값(AgentStepDefinition.agent 등)으로 찾는 경우입니다. 
 * 어느 경로로 찾든 caller 화이트리스트 검사는 이 클래스 안에서 딱 한 번만 이뤄집니다.
 *
 * 기동할 때 각 Agent의 입출력 계약(input/output)이 올바른 JSON Schema로 바뀌었는지도 검사합니다.
 * 스키마가 틀린 Agent가 있으면 기동 자체를 실패시킵니다.
 *
 * 프롬프트의 ${input.필드} / ${output.필드} 자리도 기동할 때 검사합니다(checkPrompt(), 규칙은 PromptText).
 *
 * 정해진 값만 받는 항목도 기동할 때 검사합니다(checkCallOptions()). 오타가 조용히 무시되지 않게 하려는 것입니다.
 *   model.reasoning, model.temperature,
 *   context.sources, execution.onInvalidOutput / maxAttempts / timeoutSeconds / maxToolCalls
 *
 * Tool 허용 목록(tools)과 Sub Agent(subAgents)도 기동할 때 검사합니다(checkTools(), checkSubAgents()).
 *   오류(기동 실패)                                         경고(로그만)
 *   tools.allowed에 "*"와 다른 이름을 함께 적음               tools.allowed에 등록되지 않은 Tool 이름이 있음(MCP 서버가 꺼져 있을 수 있음)
 *   tools.allowed에 TOOL step 전용 Tool을 적음
 *   subAgents의 id가 없는 Agent임                            부모를 쓸 수 있는 caller가 Sub Agent는 쓸 수 없음
 *   Sub Agent가 자기 subAgents를 가짐(깊이는 한 단계뿐)
 *   Sub Agent에 description이 없음
 *   Sub Agent id가 Tool 이름으로 쓸 수 없는 모양이거나, 등록된 Tool 이름과 같음
 *   subAgents.background: true인데 맡길 Agent가 없음
 *   Sub Agent의 input에 run_in_background 필드가 있음(백그라운드 인자와 겹침)
 *
 * Skill(skills.allowed)도 기동할 때 검사합니다(checkSkills()).
 *   오류(기동 실패)                                         경고(로그만)
 *   skills.allowed에 등록되지 않은 Skill 이름을 적음          Skill 폴더에 참고 파일이 있는데 tools.allowed에 readFile이 없음
 *   엔진이 붙이는 Tool 이름(Skill, TaskOutput)과 같은 이름의 Tool이나 Sub Agent가 있음
 * </pre>
 */
@Component
public class AgentRegistry extends BaseObject {

	@Autowired
	private YamlDefinitionLoader loader;
	@Autowired
	private ConfigTool configTool;
	@Autowired
	private SkillRegistry skillRegistry;

	/** Sub Agent는 LLM에게 Tool로 보이므로, id가 LLM provider의 Tool 이름 규칙(영문/숫자/밑줄/하이픈, 64자 이하)에 맞아야 합니다. */
	private static final Pattern TOOL_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	/** 등록된 Agent(id → 정의). 다시 읽을 때 통째로 바꿔 끼우므로, 읽는 쪽은 항상 한 시점의 온전한 맵을 봅니다. */
	private volatile Map<String, AgentDefinition> byId = Map.of();
	/** 등록된 Agent가 들어 있는 파일(id → 정의 폴더 기준 경로) */
	private volatile Map<String, String> pathById = Map.of();

	/**
	 * <pre>
	 * 앱이 기동될 때 한 번 호출되어, agents/**.md에 정의된 Agent를 전부 읽고 검사한 뒤 등록합니다.
	 * 오류가 하나라도 있으면 기동 자체를 실패시켜서 잘못된 설정이 조용히 넘어가지 않게 합니다.
	 * </pre>
	 */
	@PostConstruct
	public void load() {
		List<DefinitionFile<AgentDefinition>> files = this.loader.loadAgents();
		this.install(files, this.check(files, this.skillRegistry.all()));
	}

	/**
	 * <pre>
	 * Agent들을 검사하고, 통과하면 id → 정의 맵을 돌려줍니다. 등록은 하지 않습니다(install()이 합니다).
	 * id, version, prompt가 비어 있는 Agent가 있거나, 같은 id의 Agent가 둘 이상 있거나,
	 * input/output 스키마가 올바른 JSON Schema가 아니면 IllegalStateException입니다.
	 * 다시 읽기(DefinitionReloader)가 바꿔 끼우기 전에 이것으로 먼저 검사합니다.
	 * </pre>
	 *
	 * @param files  검사할 Agent 파일들
	 * @param skills skills.allowed가 가리킬 수 있는 Skill(이름 → 정의). 다시 읽을 때는 아직 등록 전인 Skill이 들어옵니다
	 */
	public Map<String, AgentDefinition> check(List<DefinitionFile<AgentDefinition>> files, Map<String, SkillDefinition> skills) {
		Map<String, AgentDefinition> resolved = new HashMap<>();
		Map<String, String> paths = new HashMap<>();
		for (DefinitionFile<AgentDefinition> file : files) {
			AgentDefinition definition = file.definition();
			if (StringUtil.isEmpty(definition.id())) {
				throw new IllegalStateException(file.path() + " - 머리말에는 id가 있어야 합니다.");
			}
			if (StringUtil.isEmpty(definition.version())) {
				throw new IllegalStateException("agent[" + definition.id() + "]에 version이 없습니다. 예: version: \"1.0.0\"");
			}
			if (StringUtil.isEmpty(definition.promptText())) {
				throw new IllegalStateException("agent[" + definition.id() + "]의 프롬프트가 비어 있습니다. Agent 파일에서 머리말(--- ... ---) 아래에 프롬프트를 적으십시오.");
			}
			if (resolved.putIfAbsent(definition.id(), definition) != null) {
				throw new IllegalStateException("agent id가 중복 등록되었습니다: " + definition.id() + " (" + paths.get(definition.id()) + ", " + file.path() + ")");
			}
			paths.put(definition.id(), file.path());
			this.checkSchema(definition, "input", definition.inputSchema());
			this.checkSchema(definition, "output", definition.outputSchema());
			this.checkPrompt(definition);
			this.checkCallOptions(definition);
		}
		// tools/subAgents는 다른 Agent를 가리키므로, 전부 읽은 뒤에 검사합니다.
		List<String> registeredTools = this.configTool.toolNames();
		for (AgentDefinition definition : resolved.values()) {
			this.checkTools(definition, registeredTools);
			this.checkSubAgents(definition, resolved, registeredTools);
			this.checkSkills(definition, skills, registeredTools);
		}
		return resolved;
	}

	/**
	 * <pre>
	 * check()를 통과한 Agent들로 등록 내용을 통째로 바꿉니다.
	 * </pre>
	 *
	 * @param files   등록할 Agent 파일들(경로를 기억해 둡니다)
	 * @param checked check()가 돌려준 맵
	 */
	public void install(List<DefinitionFile<AgentDefinition>> files, Map<String, AgentDefinition> checked) {
		Map<String, String> paths = new HashMap<>();
		for (DefinitionFile<AgentDefinition> file : files) {
			paths.put(file.definition().id(), file.path());
		}
		this.byId = Map.copyOf(checked);
		this.pathById = Map.copyOf(paths);
		LogUtil.sysout("dstone-ai-engine agent: 등록된 Agent = " + (this.byId.isEmpty() ? "없음" : this.byId.keySet()));
	}

	/** 지금 등록된 Agent 전부(id → 정의)입니다. 바뀌지 않는 맵입니다. */
	public Map<String, AgentDefinition> all() {
		return this.byId;
	}

	/**
	 * Agent가 들어 있는 파일의 경로(정의 폴더 기준)입니다. 없는 id면 null입니다.
	 *
	 * @param agentId Agent id
	 */
	public String pathOf(String agentId) {
		return this.pathById.get(agentId);
	}

	/**
	 * <pre>
	 * Agent의 input/output 스키마가 올바른 JSON Schema인지 검사합니다. 
	 * 틀렸으면 어느 Agent의 어느 자리인지 담아 예외를 던집니다.
	 * </pre>
	 *
	 * @param definition 검사할 Agent 정의
	 * @param where      input 또는 output
	 * @param schema     검사할 스키마
	 */
	private void checkSchema(AgentDefinition definition, String where, Map<String, Object> schema) {
		List<String> problems = JsonSchemaUtil.checkSchema(schema);
		if (!problems.isEmpty()) {
			throw new IllegalStateException("agent[" + definition.id() + "]의 " + where + "가 올바른 JSON Schema가 아닙니다: " + problems);
		}
	}

	/**
	 * <pre>
	 * 프롬프트의 ${ } 자리가 이 Agent의 input/output에 있는 것만 가리키는지 검사합니다.
	 * 틀린 자리는 부를 때가 아니라 기동(또는 디자이너의 [검사])할 때 알게 하려는 것입니다.
	 * </pre>
	 *
	 * @param definition 검사할 Agent 정의
	 */
	private void checkPrompt(AgentDefinition definition) {
		List<String> problems = PromptText.check(definition.promptText(), definition.inputSchema(), definition.outputSchema());
		if (!problems.isEmpty()) {
			throw new IllegalStateException("agent[" + definition.id() + "]의 프롬프트에 잘못된 자리가 있습니다: " + String.join(" / ", problems));
		}
	}

	/**
	 * <pre>
	 * 정해진 값만 받는 항목(모델, 참고 자료, 실행 정책)을 검사합니다.
	 * 오타가 난 값이 조용히 무시되면 "껐는데 왜 느리지"를 한참 찾게 되므로 기동할 때 막습니다.
	 * </pre>
	 *
	 * @param definition 검사할 Agent 정의
	 */
	private void checkCallOptions(AgentDefinition definition) {
		String where = "agent[" + definition.id() + "]의 ";
		// model
		String reasoning = definition.reasoningLevel();
		if (reasoning != null && !AgentDefinition.REASONING_LEVELS.contains(reasoning)) {
			// YAML은 off/no를 글자가 아니라 false로 읽습니다. "끄려고 off를 적었는데 false라고 나온다"를 헤매지 않게 알려 줍니다.
			String hint = "false".equals(reasoning) ? " (YAML은 off를 false로 읽습니다. 추론을 끄려면 none을 적으십시오)" : "";
			throw new IllegalStateException(where + "model.reasoning은 " + AgentDefinition.REASONING_LEVELS + " 중 하나여야 합니다: " + definition.model().reasoning() + hint);
		}
		if (definition.temperature() != null && (definition.temperature().doubleValue() < 0 || definition.temperature().doubleValue() > 2)) {
			throw new IllegalStateException(where + "model.temperature는 0 이상 2 이하여야 합니다: " + definition.temperature());
		}
		// context
		for (String source : definition.contextSources()) {
			if (!AgentDefinition.SOURCES.contains(source)) {
				throw new IllegalStateException(where + "context.sources의 '" + source + "'는 쓸 수 없는 값입니다(쓸 수 있는 값 = " + AgentDefinition.SOURCES + ").");
			}
		}
		if (definition.context() != null && definition.context().retrieval() != null && !definition.ragEnabled()) {
			throw new IllegalStateException(where + "context.retrieval은 context.sources에 " + AgentDefinition.SOURCE_RETRIEVED_DOCUMENTS + "를 적었을 때만 씁니다.");
		}
		// execution
		AgentDefinition.Execution execution = definition.execution();
		if (execution == null) {
			return;
		}
		if (execution.onInvalidOutput() != null && !AgentDefinition.ON_INVALID_OUTPUTS.contains(execution.onInvalidOutput().trim().toUpperCase())) {
			throw new IllegalStateException(where + "execution.onInvalidOutput은 " + AgentDefinition.ON_INVALID_OUTPUTS + " 중 하나여야 합니다: " + execution.onInvalidOutput());
		}
		if (execution.maxAttempts() != null && execution.maxAttempts().intValue() <= 0) {
			throw new IllegalStateException(where + "execution.maxAttempts는 1 이상이어야 합니다: " + execution.maxAttempts());
		}
		if (execution.maxAttempts() != null && execution.maxAttempts().intValue() > 1 && definition.maxAttempts() == 1) {
			throw new IllegalStateException(where + "execution.maxAttempts(" + execution.maxAttempts() + ")는 onInvalidOutput: RETRY와 함께 적어야 쓰입니다.");
		}
		if (execution.timeoutSeconds() != null && execution.timeoutSeconds().intValue() <= 0) {
			throw new IllegalStateException(where + "execution.timeoutSeconds는 1 이상이어야 합니다: " + execution.timeoutSeconds());
		}
		if (execution.maxToolCalls() != null && execution.maxToolCalls().intValue() <= 0) {
			throw new IllegalStateException(where + "execution.maxToolCalls는 1 이상이어야 합니다(Tool을 쓰지 않으려면 tools를 적지 않습니다): " + execution.maxToolCalls());
		}
	}

	/**
	 * <pre>
	 * 이 Agent를 부를 때 쓸 모델 이름을 돌려줍니다.
	 * - model.name을 적었으면 그 이름
	 * - 없으면 null(provider 공통 기본 모델을 씁니다)
	 * </pre>
	 *
	 * @param definition 모델을 알아볼 Agent 정의
	 */
	public String modelOf(AgentDefinition definition) {
		return definition.modelName();
	}

	/**
	 * <pre>
	 * Agent의 tools(쓸 Tool 이름 목록)를 검사합니다.
	 * - "*"(전부 허용)는 혼자만 적어야 합니다. 다른 이름과 섞으면 "일부만 허용"인지 "전부 허용"인지 알 수 없어서 막습니다.
	 * - 등록되지 않은 이름은 경고만 남깁니다. MCP 서버가 지금 꺼져 있어서 안 보이는 것일 수 있기 때문입니다.
	 *   그 Tool은 붙지 않을 뿐이고, 기동은 계속합니다.
	 * </pre>
	 *
	 * @param definition      검사할 Agent 정의
	 * @param registeredTools 지금 등록되어 있는 Tool 이름 목록
	 */
	private void checkTools(AgentDefinition definition, List<String> registeredTools) {
		List<String> toolNames = definition.toolNames();
		if (definition.allowsAllTools()) {
			if (toolNames.size() > 1) {
				throw new IllegalStateException("agent[" + definition.id() + "]의 tools.allowed에 \"*\"(전부 허용)와 다른 이름을 함께 적을 수 없습니다: " + toolNames);
			}
			return;
		}
		for (String toolName : toolNames) {
			if (this.configTool.isStepOnly(toolName)) {
				throw new IllegalStateException("agent[" + definition.id() + "]의 tools.allowed에 적은 '" + toolName
					+ "'는 Workflow의 TOOL step에서만 부를 수 있는 Tool입니다(LLM에게 줄 수 없습니다). tools.allowed에서 빼십시오.");
			}
			if (!registeredTools.contains(toolName)) {
				LogUtil.sysout("dstone-ai-engine agent: [경고] agent[" + definition.id() + "]의 tools.allowed에 적은 '" + toolName
					+ "'는 지금 등록된 Tool이 아닙니다(이름이 틀렸거나 MCP 서버가 아직 안 떴을 수 있습니다). 이 Tool은 붙지 않습니다. 등록된 Tool = " + registeredTools);
			}
		}
	}

	/**
	 * <pre>
	 * Agent의 subAgents(일을 맡길 Agent 목록)를 검사합니다. 어떤 검사를 하는지는 클래스 설명의 표를 보십시오.
	 *
	 * "Sub Agent는 자기 subAgents를 가질 수 없다"는 규칙 하나로 세 가지가 함께 막힙니다.
	 * - 깊이 2 이상(부모 → 자식 → 손자)
	 * - 자기 자신을 적은 경우(자기가 subAgents를 가진 Agent이므로)
	 * - 서로를 적은 경우(A → B → A)
	 * </pre>
	 *
	 * @param definition      검사할 Agent 정의(부모)
	 * @param all             읽어 들인 Agent 전체(id → 정의)
	 * @param registeredTools 지금 등록되어 있는 Tool 이름 목록
	 */
	private void checkSubAgents(AgentDefinition definition, Map<String, AgentDefinition> all, List<String> registeredTools) {
		if (definition.subAgentBackground()) {
			if (definition.subAgentIds().isEmpty()) {
				throw new IllegalStateException("agent[" + definition.id() + "]의 subAgents.background가 true인데 subAgents.agents가 비어 있습니다. 맡길 Agent를 적거나 background를 지우십시오.");
			}
			this.checkEngineToolName(definition, SubAgentTasks.OUTPUT_TOOL_NAME, registeredTools);
		}
		for (String subAgentId : definition.subAgentIds()) {
			String where = "agent[" + definition.id() + "]의 subAgents '" + subAgentId + "'";
			AgentDefinition subAgent = all.get(subAgentId);
			if (subAgent == null) {
				throw new IllegalStateException(where + ": agents/**.md에 그런 Agent가 없습니다.");
			}
			if (!subAgent.subAgentIds().isEmpty()) {
				throw new IllegalStateException(where + ": 이 Agent도 subAgents를 가지고 있습니다. Sub Agent는 한 단계만 둘 수 있습니다(부모 → 자식). "
					+ "agent[" + subAgentId + "]의 subAgents를 지우십시오.");
			}
			if (StringUtil.isEmpty(subAgent.description())) {
				throw new IllegalStateException(where + ": description이 없습니다. 부모 LLM이 이 설명을 보고 일을 맡길지 정하므로, "
					+ "agent[" + subAgentId + "]에 무슨 일을 하는 Agent인지 description을 적으십시오.");
			}
			if (!TOOL_NAME.matcher(subAgentId).matches()) {
				throw new IllegalStateException(where + ": Sub Agent의 id는 영문, 숫자, 밑줄(_), 하이픈(-)만 쓰고 64자를 넘지 않아야 합니다(LLM에게 Tool 이름으로 보이기 때문입니다).");
			}
			if (registeredTools.contains(subAgentId)) {
				throw new IllegalStateException(where + ": 같은 이름의 Tool이 이미 등록되어 있습니다. LLM이 둘을 구분할 수 없으니 Agent id를 바꾸십시오.");
			}
			if (definition.subAgentBackground() && JsonSchemaUtil.OBJECT.equals(JsonSchemaUtil.typeOf(subAgent.inputSchema()))
				&& subAgent.inputSchema().get(JsonSchemaUtil.PROPERTIES) instanceof Map<?, ?> properties && properties.containsKey(SubAgentToolCallback.BACKGROUND_FIELD)) {
				throw new IllegalStateException(where + ": input에 " + SubAgentToolCallback.BACKGROUND_FIELD + " 필드가 있어서, 백그라운드로 맡길 때 쓰는 인자와 겹칩니다. 그 필드 이름을 바꾸십시오.");
			}
			// 부모는 쓸 수 있는데 Sub Agent는 쓸 수 없는 caller가 있으면, 그 caller에게는 Sub Agent가 붙지 않습니다. 실수일 수 있어 알려 둡니다.
			List<String> subCallers = subAgent.allowedCallers();
			if (subCallers == null || subCallers.isEmpty()) {
				continue;
			}
			List<String> parentCallers = definition.allowedCallers();
			if (parentCallers == null || parentCallers.isEmpty()) {
				LogUtil.sysout("dstone-ai-engine agent: [경고] " + where + ": 부모는 누구나 부를 수 있지만 이 Agent는 " + subCallers + "만 쓸 수 있습니다. 그 밖의 caller에게는 이 Sub Agent가 붙지 않습니다.");
				continue;
			}
			for (String parentCaller : parentCallers) {
				if (!subCallers.contains(parentCaller)) {
					LogUtil.sysout("dstone-ai-engine agent: [경고] " + where + ": caller[" + parentCaller + "]는 부모는 쓸 수 있지만 이 Agent는 쓸 수 없습니다. 이 caller에게는 이 Sub Agent가 붙지 않습니다.");
				}
			}
		}
	}

	/**
	 * <pre>
	 * Agent의 skills.allowed(꺼내 읽을 Skill 목록)를 검사합니다. 어떤 검사를 하는지는 클래스 설명의 표를 보십시오.
	 * </pre>
	 *
	 * @param definition      검사할 Agent 정의
	 * @param skills          등록될 Skill 전체(이름 → 정의)
	 * @param registeredTools 지금 등록되어 있는 Tool 이름 목록
	 */
	private void checkSkills(AgentDefinition definition, Map<String, SkillDefinition> skills, List<String> registeredTools) {
		String where = "agent[" + definition.id() + "]의 ";
		if (!definition.skillNames().isEmpty()) {
			this.checkEngineToolName(definition, SkillTool.NAME, registeredTools);
		}
		boolean needsReadFile = false;
		for (String name : definition.skillNames()) {
			SkillDefinition skill = skills.get(name);
			if (skill == null) {
				throw new IllegalStateException(where + "skills.allowed에 적은 '" + name + "'는 등록된 Skill이 아닙니다(conf/definitions/skills 아래 SKILL.md의 name). 등록된 Skill = " + skills.keySet());
			}
			needsReadFile = needsReadFile || SkillTool.hasReferenceFiles(skill);
		}
		if (needsReadFile && !definition.allowsAllTools() && !definition.toolNames().contains(SkillTool.READ_TOOL)) {
			LogUtil.sysout("dstone-ai-engine agent: [경고] " + where + "Skill 폴더에 참고 파일이 있지만 tools.allowed에 " + SkillTool.READ_TOOL
				+ "가 없습니다. 이 Agent는 Skill 본문만 읽고 참고 파일은 열지 못합니다.");
		}
	}

	/**
	 * <pre>
	 * 엔진이 직접 붙이는 Tool(Skill, TaskOutput)의 이름이 등록된 Tool이나 Sub Agent id와 겹치지 않는지 봅니다.
	 * 겹치면 LLM이 둘을 구분할 수 없습니다.
	 * </pre>
	 *
	 * @param definition      검사할 Agent 정의
	 * @param toolName        엔진이 붙이는 Tool 이름
	 * @param registeredTools 지금 등록되어 있는 Tool 이름 목록
	 */
	private void checkEngineToolName(AgentDefinition definition, String toolName, List<String> registeredTools) {
		if (registeredTools.contains(toolName)) {
			throw new IllegalStateException("agent[" + definition.id() + "]: 엔진이 붙이는 Tool '" + toolName + "'과 같은 이름의 Tool이 이미 등록되어 있습니다. 그 Tool의 이름을 바꾸십시오.");
		}
		if (definition.subAgentIds().contains(toolName)) {
			throw new IllegalStateException("agent[" + definition.id() + "]: Sub Agent id '" + toolName + "'은 엔진이 붙이는 Tool 이름과 같습니다. Agent id를 바꾸십시오.");
		}
	}

	/**
	 * <pre>
	 * caller가 이 Agent를 쓸 수 있는지만 알려줍니다(없는 Agent면 false). resolve()와 같은 규칙이지만 예외를 던지지 않습니다.
	 * runtime.agent.AgentExecutor가 Sub Agent를 붙일지 말지 정할 때 씁니다.
	 * </pre>
	 *
	 * @param agentId 확인할 Agent id
	 * @param caller  호출한 앱/서비스를 나타내는 식별자(tenant)
	 */
	public boolean isAllowed(String agentId, String caller) {
		AgentDefinition definition = this.byId.get(agentId);
		if (definition == null) {
			return false;
		}
		List<String> allowedCallers = definition.allowedCallers();
		return allowedCallers == null || allowedCallers.isEmpty() || (caller != null && allowedCallers.contains(caller));
	}

	/**
	 * <pre>
	 * id로 Agent를 찾습니다. caller 검사는 하지 않고, 없으면 null입니다. 
	 * 엔진이 켜질 때 WorkFlowRegistry가 step의 agent가 가리키는 Agent의 계약(input/output)을 보려고 씁니다. 
	 * 실제로 부를 때는 resolve()를 씁니다.
	 * </pre>
	 *
	 * @param agentId 찾을 Agent id
	 */
	public AgentDefinition find(String agentId) {
		return this.byId.get(agentId);
	}

	/**
	 * <pre>
	 * caller가 쓸 수 있는 Agent만 골라 목록으로 돌려줍니다. api.controller.ChatController의
	 * GET /api/ai/chat가 이 목록을 그대로 dstone-boot의 "채팅" 화면 드롭다운에 보여줍니다
	 * (common.registry.WorkFlowRegistry.list()와 완전히 같은 패턴입니다).
	 *
	 * resolve()와 똑같은 allowedCallers 규칙을 씁니다. caller가 쓸 수 없는 Agent는 나중에
	 * resolve()에서 막히기 전에, 애초에 이 목록에서부터 보이지 않아야 합니다.
	 * </pre>
	 *
	 * @param caller 호출한 앱/서비스를 나타내는 식별자(tenant)
	 */
	public List<AgentDefinition> list(String caller) {
		List<AgentDefinition> result = new ArrayList<>();
		for (AgentDefinition definition : this.byId.values()) {
			List<String> allowedCallers = definition.allowedCallers();
			if (allowedCallers == null || allowedCallers.isEmpty() || (caller != null && allowedCallers.contains(caller))) {
				result.add(definition);
			}
		}
		result.sort(new Comparator<AgentDefinition>() {
			@Override
			public int compare(AgentDefinition a, AgentDefinition b) {
				return a.id().compareTo(b.id());
			}
		});
		return result;
	}

	/**
	 * <pre>
	 * id로 Agent를 찾아서 돌려줍니다. 이때 caller가 그 Agent를 쓸 수 있는지도 함께
	 * 확인합니다. 등록되지 않은 id이거나, caller가 그 Agent의 화이트리스트를 통과하지
	 * 못하면 조용히 넘어가지 않고 바로 예외를 던져서 알려줍니다.
	 * </pre>
	 *
	 * @param agentId 조회할 Agent id
	 * @param caller  호출한 앱/서비스를 나타내는 식별자(tenant)
	 * @return 조건을 통과한 AgentDefinition
	 */
	public AgentDefinition resolve(String agentId, String caller) {
		AgentDefinition definition = this.byId.get(agentId);
		if (definition == null) {
			throw new IllegalArgumentException("등록되지 않은 agent입니다: " + agentId);
		}
		List<String> allowedCallers = definition.allowedCallers();
		if (allowedCallers != null && !allowedCallers.isEmpty() && (caller == null || !allowedCallers.contains(caller))) {
			throw new IllegalArgumentException("agent[" + agentId + "]는 caller[" + caller + "]에게 허용되지 않았습니다.");
		}
		return definition;
	}

}

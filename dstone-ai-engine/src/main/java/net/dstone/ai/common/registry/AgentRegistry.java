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
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.ai.common.schema.JsonSchemaUtil;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 모든 Agent 정보를 담아두고, id로 찾아 주는 등록소입니다. 
 * 로딩 순서는 YamlDefinitionLoader가 classpath:agents/*.yml 파일들을 읽어서 AgentDefinition으로 바꾸고, 
 * 이 AgentRegistry가 그 AgentDefinition들을 앱이 기동될 때 한 번 모아서 보관하는 식입니다.
 *
 * Agent를 찾아 쓰는 경로는 두 가지입니다. 
 * api.controller.ChatController가 request.agent() 값으로 직접 찾는 경우와, 
 * runtime.step의 AgentStepExecutor가 step의 agent 값(AgentStepDefinition.agent 등)으로 찾는 경우입니다. 
 * 어느 경로로 찾든 caller 화이트리스트 검사는 이 클래스 안에서 딱 한 번만 이뤄집니다.
 *
 * 기동할 때 각 Agent의 입출력 계약(input/output의 schema)이 올바른 JSON Schema인지도 검사합니다.
 * 스키마가 틀린 Agent가 있으면 기동 자체를 실패시킵니다.
 *
 * 정해진 값만 받는 항목도 기동할 때 검사합니다(checkCallOptions()). 오타가 조용히 무시되지 않게 하려는 것입니다.
 *   model.routing(설정에 있는 이름인지), model.reasoning, model.temperature, output.format, output.validation,
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
 * </pre>
 */
@Component
public class AgentRegistry extends BaseObject {

	@Autowired
	private YamlDefinitionLoader loader;
	@Autowired
	private ConfigTool configTool;
	@Autowired
	private ConfigProperty configProperty;

	/** model.routing에 적어서 "provider 공통 기본 모델"을 뜻하는 이름입니다. 설정에 따로 적지 않아도 쓸 수 있습니다. */
	public static final String DEFAULT_ROUTING = "default";

	/** Sub Agent는 LLM에게 Tool로 보이므로, id가 LLM provider의 Tool 이름 규칙(영문/숫자/밑줄/하이픈, 64자 이하)에 맞아야 합니다. */
	private static final Pattern TOOL_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	private Map<String, AgentDefinition> byId = Map.of();

	/**
	 * <pre>
	 * 앱이 기동될 때 한 번 호출되어, agents/*.yml에 정의된 Agent를 전부 읽어 id를 키로 하는 맵에 채워 넣습니다. 
	 * id, version, prompt가 비어 있는 Agent가 있거나, 같은 id의 Agent가 둘 이상 있거나, 
	 * input/output 스키마가 올바른 JSON Schema가 아니면 기동 자체를 실패시켜서 잘못된 설정이 조용히 넘어가지 않게 합니다.
	 * </pre>
	 */
	@PostConstruct
	public void load() {
		Map<String, AgentDefinition> resolved = new HashMap<>();
		for (AgentDefinition definition : this.loader.loadAgents()) {
			if (StringUtil.isEmpty(definition.id())) {
				throw new IllegalStateException("agents/*.yml 항목은 id가 있어야 합니다: " + definition);
			}
			if (StringUtil.isEmpty(definition.version())) {
				throw new IllegalStateException("agent[" + definition.id() + "]에 version이 없습니다. 예: version: \"1.0.0\"");
			}
			if (StringUtil.isEmpty(definition.promptText())) {
				throw new IllegalStateException("agent[" + definition.id() + "]에 prompt가 없거나 프롬프트 파일이 비어 있습니다. 예: prompt: {system: prompts/내agent/v1.st}");
			}
			if (resolved.putIfAbsent(definition.id(), definition) != null) {
				throw new IllegalStateException("agent id가 중복 등록되었습니다: " + definition.id());
			}
			this.checkSchema(definition, "input", definition.inputSchema());
			this.checkSchema(definition, "output", definition.outputSchema());
			this.checkCallOptions(definition);
		}
		// tools/subAgents는 다른 Agent를 가리키므로, 전부 읽은 뒤에 검사합니다.
		List<String> registeredTools = this.configTool.toolNames();
		for (AgentDefinition definition : resolved.values()) {
			this.checkTools(definition, registeredTools);
			this.checkSubAgents(definition, resolved, registeredTools);
		}
		this.byId = Map.copyOf(resolved);
		LogUtil.sysout("dstone-ai-engine agent: 등록된 Agent = " + (this.byId.isEmpty() ? "없음" : this.byId.keySet()));
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
			throw new IllegalStateException("agent[" + definition.id() + "]의 " + where + ".schema가 올바른 JSON Schema가 아닙니다: " + problems);
		}
	}

	/**
	 * <pre>
	 * 정해진 값만 받는 항목(모델, 출력 형식, 실행 정책 등)을 검사합니다.
	 * 오타가 난 값이 조용히 무시되면 "껐는데 왜 느리지"를 한참 찾게 되므로 기동할 때 막습니다.
	 * </pre>
	 *
	 * @param definition 검사할 Agent 정의
	 */
	private void checkCallOptions(AgentDefinition definition) {
		String where = "agent[" + definition.id() + "]의 ";
		// model
		if (definition.modelRouting() != null && definition.modelName() != null) {
			throw new IllegalStateException(where + "model에 routing과 name을 함께 적을 수 없습니다. 하나만 적으십시오.");
		}
		if (definition.modelRouting() != null && !DEFAULT_ROUTING.equals(definition.modelRouting())
				&& StringUtil.isEmpty(this.configProperty.getProperty(Constants.Agent.MODEL_ROUTING_PREFIX + "." + definition.modelRouting()))) {
			throw new IllegalStateException(where + "model.routing '" + definition.modelRouting() + "'가 설정에 없습니다. conf/application.yml의 "
				+ Constants.Agent.MODEL_ROUTING_PREFIX + "." + definition.modelRouting() + "에 모델 이름을 적으십시오(provider 기본 모델을 쓰려면 routing: " + DEFAULT_ROUTING + ").");
		}
		String reasoning = definition.reasoningLevel();
		if (reasoning != null && !AgentDefinition.REASONING_LEVELS.contains(reasoning)) {
			// YAML은 off/no를 글자가 아니라 false로 읽습니다. "끄려고 off를 적었는데 false라고 나온다"를 헤매지 않게 알려 줍니다.
			String hint = "false".equals(reasoning) ? " (YAML은 off를 false로 읽습니다. 추론을 끄려면 none을 적으십시오)" : "";
			throw new IllegalStateException(where + "model.reasoning은 " + AgentDefinition.REASONING_LEVELS + " 중 하나여야 합니다: " + definition.model().reasoning() + hint);
		}
		if (definition.temperature() != null && (definition.temperature().doubleValue() < 0 || definition.temperature().doubleValue() > 2)) {
			throw new IllegalStateException(where + "model.temperature는 0 이상 2 이하여야 합니다: " + definition.temperature());
		}
		// output
		if (definition.output() != null) {
			AgentDefinition.Output output = definition.output();
			if (output.format() != null && !AgentDefinition.FORMATS.contains(output.format())) {
				throw new IllegalStateException(where + "output.format은 " + AgentDefinition.FORMATS + " 중 하나여야 합니다: " + output.format());
			}
			if (output.isJson() && output.schema() == null) {
				throw new IllegalStateException(where + "output.format이 JSON이면 schema가 있어야 합니다. 예: output: {format: JSON, schema: schemas/내결과.schema.json}");
			}
			if (!output.isJson() && output.schema() != null && !JsonSchemaUtil.STRING.equals(JsonSchemaUtil.typeOf(output.schema()))) {
				throw new IllegalStateException(where + "output.format이 TEXT인데 schema가 글자(string)가 아닙니다. 정해진 모양으로 답을 받으려면 format: JSON으로 적으십시오.");
			}
			if (output.validation() != null && !AgentDefinition.VALIDATIONS.contains(output.validation())) {
				throw new IllegalStateException(where + "output.validation은 " + AgentDefinition.VALIDATIONS + " 중 하나여야 합니다: " + output.validation());
			}
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
	 * - model.routing을 적었으면 설정(dstone.ai.model.routing.{이름})에 적힌 모델 이름
	 * - 둘 다 없거나 routing: default면 null(provider 공통 기본 모델을 씁니다)
	 * </pre>
	 *
	 * @param definition 모델을 알아볼 Agent 정의
	 */
	public String modelOf(AgentDefinition definition) {
		if (definition.modelName() != null) {
			return definition.modelName();
		}
		String routing = definition.modelRouting();
		if (routing == null || DEFAULT_ROUTING.equals(routing)) {
			return null;
		}
		String model = this.configProperty.getProperty(Constants.Agent.MODEL_ROUTING_PREFIX + "." + routing);
		return StringUtil.isEmpty(model) ? null : model.trim();
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
		for (String subAgentId : definition.subAgentIds()) {
			String where = "agent[" + definition.id() + "]의 subAgents '" + subAgentId + "'";
			AgentDefinition subAgent = all.get(subAgentId);
			if (subAgent == null) {
				throw new IllegalStateException(where + ": agents/*.yml에 그런 Agent가 없습니다.");
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

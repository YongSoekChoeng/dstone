package net.dstone.ai.common.registry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * classpath:agents/*.yml 전체를 기동 시 한 번 읽어 이름으로 찾아주는 등록소다.
 * api.controller.ChatController가 request.agent()로 직접 찾을 수도 있고, runtime.step의
 * AgentStepRunner/ToolStepRunner가 StepDefinition.ref로 찾을 수도 있다 - 어느 경로든 caller
 * 화이트리스트 검사는 여기 한 곳에서만 이뤄진다.
 */
@Component
public class AgentRegistry extends BaseObject {

	@Autowired
	private YamlDefinitionLoader loader;

	private Map<String, AgentDefinition> byName = Map.of();

	@PostConstruct
	public void load() {
		Map<String, AgentDefinition> resolved = new HashMap<>();
		for (AgentDefinition definition : this.loader.loadAgents()) {
			if (StringUtil.isEmpty(definition.name()) || StringUtil.isEmpty(definition.promptName())) {
				throw new IllegalStateException("agents/*.yml 항목은 name과 promptName이 모두 있어야 합니다: " + definition);
			}
			if (resolved.putIfAbsent(definition.name(), definition) != null) {
				throw new IllegalStateException("agent 이름이 중복 등록되었습니다: " + definition.name());
			}
		}
		this.byName = Map.copyOf(resolved);
		LogUtil.sysout("dstone-ai-engine agent: 등록된 Agent = " + (this.byName.isEmpty() ? "없음" : this.byName.keySet()));
	}

	/**
	 * caller 에게 허용된 agentName 에 해당하는 AgentDefinition 을 반환한다.
	 * 모르는 이름이거나 caller가 화이트리스트를 통과하지 못하면 조용히 넘어가지 않고 바로 에러로 알려준다.
	 * @param agentName 조회할 agent 이름
	 * @param caller 호출한 앱/서비스 식별자
	 * @return
	 */
	public AgentDefinition resolve(String agentName, String caller) {
		AgentDefinition definition = this.byName.get(agentName);
		if (definition == null) {
			throw new IllegalArgumentException("등록되지 않은 agent입니다: " + agentName);
		}
		List<String> allowedCallers = definition.allowedCallers();
		if (allowedCallers != null && !allowedCallers.isEmpty() && (caller == null || !allowedCallers.contains(caller))) {
			throw new IllegalArgumentException("agent[" + agentName + "]는 caller[" + caller + "]에게 허용되지 않았습니다.");
		}
		return definition;
	}

}

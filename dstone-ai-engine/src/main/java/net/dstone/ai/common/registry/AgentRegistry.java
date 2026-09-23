package net.dstone.ai.common.registry;

import java.util.ArrayList;
import java.util.Comparator;
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
 * 모든 Agent 정보를 담아두고, id로 찾아 주는 등록소입니다. 로딩 순서는 YamlDefinitionLoader가
 * classpath:agents/*.yml 파일들을 읽어서 AgentDefinition으로 바꾸고, 이 AgentRegistry가 그
 * AgentDefinition들을 앱이 기동될 때 한 번 모아서 보관하는 식입니다.
 *
 * Agent를 찾아 쓰는 경로는 두 가지입니다: api.controller.ChatController가 request.agent() 값으로
 * 직접 찾는 경우와, runtime.step의 AgentStepRunner/ToolStepRunner가 StepDefinition.ref 값으로
 * 찾는 경우입니다. 어느 경로로 찾든 caller 화이트리스트 검사는 이 클래스 안에서 딱 한 번만
 * 이뤄집니다.
 */
@Component
public class AgentRegistry extends BaseObject {

	@Autowired
	private YamlDefinitionLoader loader;

	private Map<String, AgentDefinition> byId = Map.of();

	/**
	 * 앱이 기동될 때 한 번 호출되어, agents/*.yml에 정의된 Agent를 전부 읽어 id를 키로 하는
	 * 맵에 채워 넣습니다. id나 prompt가 비어 있는 Agent가 있거나, 같은 id의 Agent가
	 * 둘 이상 있으면 기동 자체를 실패시켜서 잘못된 설정이 조용히 넘어가지 않게 합니다.
	 */
	@PostConstruct
	public void load() {
		Map<String, AgentDefinition> resolved = new HashMap<>();
		for (AgentDefinition definition : this.loader.loadAgents()) {
			if (StringUtil.isEmpty(definition.id()) || StringUtil.isEmpty(definition.prompt())) {
				throw new IllegalStateException("agents/*.yml 항목은 id와 prompt가 모두 있어야 합니다: " + definition);
			}
			if (resolved.putIfAbsent(definition.id(), definition) != null) {
				throw new IllegalStateException("agent id가 중복 등록되었습니다: " + definition.id());
			}
		}
		this.byId = Map.copyOf(resolved);
		LogUtil.sysout("dstone-ai-engine agent: 등록된 Agent = " + (this.byId.isEmpty() ? "없음" : this.byId.keySet()));
	}

	/**
	 * caller가 쓸 수 있는 Agent만 골라 목록으로 돌려줍니다. api.controller.ChatController의
	 * GET /api/ai/chat가 이 목록을 그대로 dstone-boot의 "채팅" 화면 드롭다운에 보여줍니다
	 * (common.registry.WorkFlowRegistry.list()와 완전히 같은 패턴입니다).
	 *
	 * resolve()와 똑같은 allowedCallers 규칙을 씁니다. caller가 쓸 수 없는 Agent는 나중에
	 * resolve()에서 막히기 전에, 애초에 이 목록에서부터 보이지 않아야 합니다.
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
		result.sort(Comparator.comparing(AgentDefinition::id));
		return result;
	}

	/**
	 * id로 Agent를 찾아서 돌려줍니다. 이때 caller가 그 Agent를 쓸 수 있는지도 함께
	 * 확인합니다. 등록되지 않은 id이거나, caller가 그 Agent의 화이트리스트를 통과하지
	 * 못하면 조용히 넘어가지 않고 바로 예외를 던져서 알려줍니다.
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

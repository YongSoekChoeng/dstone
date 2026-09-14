package net.dstone.ai.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * dstone.ai.agent.definitions를 읽어 이름으로 찾아주는 등록소다 - capability.CapabilityRegistry/
 * process.ProcessRegistry와 완전히 같은 패턴(@PostConstruct에서 Binder로 로딩, 이름으로 조회, 모르는
 * 이름은 에러)이다. process.ProcessExecutor의 AGENT/SUPERVISOR step이 이 레지스트리로 이름을 풀어서
 * 실제 promptName/toolsEnabled/ragEnabled 조합을 얻는다.
 */
@Component
public class AgentRegistry extends BaseObject {

	private static final String PREFIX = "dstone.ai.agent";

	@Autowired
	Environment environment; // definitions(YAML 시퀀스) 바인딩 전용 - Binder.get(environment)에 필요

	private Map<String, AgentDefinition> byName = Map.of();

	@PostConstruct
	public void load() {
		List<AgentDefinition> definitions = Binder.get(this.environment)
			.bind(PREFIX + ".definitions", Bindable.listOf(AgentDefinition.class))
			.orElse(List.of());

		Map<String, AgentDefinition> resolved = new HashMap<>();
		for (AgentDefinition definition : definitions) {
			if (StringUtil.isEmpty(definition.name()) || StringUtil.isEmpty(definition.promptName())) {
				throw new IllegalStateException(
					PREFIX + ".definitions 항목은 name과 prompt-name이 둘 다 있어야 합니다: " + definition);
			}
			resolved.put(definition.name(), definition);
		}
		this.byName = Map.copyOf(resolved);

		LogUtil.sysout(
			"dstone-ai-engine agent-runtime: 등록된 Agent = " + (this.byName.isEmpty() ? "없음" : this.byName.keySet()));
	}

	/** 모르는 이름이 오면 그건 설정이 잘못된 것이니, 조용히 넘어가지 않고 바로 에러로 알려준다. */
	public AgentDefinition resolve(String name) {
		AgentDefinition definition = this.byName.get(name);
		if (definition == null) {
			throw new IllegalArgumentException("등록되지 않은 agent입니다: " + name);
		}
		return definition;
	}

}

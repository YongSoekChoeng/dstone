package net.dstone.ai.process;

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
 * dstone.ai.process.definitions를 읽어 이름으로 찾아주는 등록소다 - capability.CapabilityRegistry와
 * 완전히 같은 패턴(@PostConstruct에서 Binder로 로딩, 이름으로 조회, 모르는 이름은 에러)이다.
 */
@Component
public class ProcessRegistry extends BaseObject {

	private static final String PREFIX = "dstone.ai.process";

	@Autowired
	Environment environment; // definitions(YAML 시퀀스) 바인딩 전용 - Binder.get(environment)에 필요

	private Map<String, ProcessDefinition> byName = Map.of();

	@PostConstruct
	public void load() {
		List<ProcessDefinition> definitions = Binder.get(this.environment)
			.bind(PREFIX + ".definitions", Bindable.listOf(ProcessDefinition.class))
			.orElse(List.of());

		Map<String, ProcessDefinition> resolved = new HashMap<>();
		for (ProcessDefinition definition : definitions) {
			if (StringUtil.isEmpty(definition.name()) || definition.steps() == null || definition.steps().isEmpty()) {
				throw new IllegalStateException(
					PREFIX + ".definitions 항목은 name과 steps가 모두 있어야 합니다: " + definition);
			}
			resolved.put(definition.name(), definition);
		}
		this.byName = Map.copyOf(resolved);

		LogUtil.sysout("dstone-ai-engine process: 등록된 Process = "
			+ (this.byName.isEmpty() ? "없음" : this.byName.keySet()));
	}

	/** 모르는 이름이 오면 그건 설정이 잘못된 것이니, 조용히 넘어가지 않고 바로 에러로 알려준다. */
	public ProcessDefinition resolve(String name) {
		ProcessDefinition definition = this.byName.get(name);
		if (definition == null) {
			throw new IllegalArgumentException("등록되지 않은 process입니다: " + name);
		}
		return definition;
	}

}

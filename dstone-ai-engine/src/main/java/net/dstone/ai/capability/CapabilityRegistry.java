package net.dstone.ai.capability;

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
 * "이 요청, 뭘 하려는 거야?"라는 질문에 이름 하나로 답할 수 있게 해주는 클래스다.
 *
 * 예를 들어 오라클→PostgreSQL 변환처럼 정해진 목적의 요청은, 매번 promptName/toolsEnabled를
 * 하나하나 조합해서 보낼 필요 없이 capability 이름 하나("oracle-to-postgresql")만 실어 보내면
 * 된다. 실제로 어떤 프롬프트를 쓰고 Tool을 켤지는 dstone.ai.capability.definitions 설정에 미리
 * 적어두고, 이 클래스가 그 설정을 읽어 이름으로 찾아준다.
 *
 * 주의: 이건 그냥 "자주 쓰는 설정 조합에 이름표를 붙인 것"일 뿐이다. 조건분기/반복/여러 Agent가
 * 순서대로 일하는 워크플로우 엔진이 아니고, 그런 건 아직 만들 계획도 없다 - 진짜 필요해지는 순간이
 * 오면 그때 다시 설계한다.
 */
@Component
public class CapabilityRegistry extends BaseObject {

	private static final String PREFIX = "dstone.ai.capability";

	@Autowired
	Environment environment; // definitions(YAML 시퀀스) 바인딩 전용 - Binder.get(environment)에 필요

	private Map<String, CapabilityDefinition> byName = Map.of();

	@PostConstruct
	public void load() {
		List<CapabilityDefinition> definitions = Binder.get(this.environment)
			.bind(PREFIX + ".definitions", Bindable.listOf(CapabilityDefinition.class))
			.orElse(List.of());

		Map<String, CapabilityDefinition> resolved = new HashMap<>();
		for (CapabilityDefinition definition : definitions) {
			if (StringUtil.isEmpty(definition.name()) || StringUtil.isEmpty(definition.promptName())) {
				throw new IllegalStateException(
					PREFIX + ".definitions 항목은 name과 prompt-name이 둘 다 있어야 합니다: " + definition);
			}
			resolved.put(definition.name(), definition);
		}
		this.byName = Map.copyOf(resolved);

		LogUtil.sysout("dstone-ai-engine capability: 등록된 Capability = "
			+ (this.byName.isEmpty() ? "없음" : this.byName.keySet()));
	}

	/** 모르는 이름이 오면 그건 요청이 잘못된 것이니, 조용히 넘어가지 않고 바로 에러로 알려준다. */
	public CapabilityDefinition resolve(String name) {
		CapabilityDefinition definition = this.byName.get(name);
		if (definition == null) {
			throw new IllegalArgumentException("등록되지 않은 capability입니다: " + name);
		}
		return definition;
	}

}

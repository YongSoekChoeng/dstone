package net.dstone.ai.common.registry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.definition.McpServerDefinition;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * MCP 서버 정보를 저장하는 컴퍼넌트. YamlDefinitionLoader => McpServerDefinition => McpServerRegistry 순서로 내용이
 * 로딩된다. classpath:mcp/*.yml 전체를 기동 시 한 번 읽어둔다. Workflow/AgentRegistry와 달리 caller별 id resolve는
 * 없다 - common.config.ConfigMcp가 기동 시 이 목록 전체를 순회하며 서버마다 접속을 시도할 뿐, 실행 중에 "이 id로
 * 찾아줘" 하는 조회가 일어나지 않기 때문이다.
 */
@Component
public class McpServerRegistry extends BaseObject {

	@Autowired
	private YamlDefinitionLoader loader;

	private Map<String, McpServerDefinition> byId = Map.of();

	@PostConstruct
	public void load() {
		Map<String, McpServerDefinition> resolved = new HashMap<>();
		for (McpServerDefinition definition : this.loader.loadMcpServers()) {
			if (StringUtil.isEmpty(definition.id()) || definition.transport() == null) {
				throw new IllegalStateException("mcp/*.yml 항목은 id와 transport가 모두 있어야 합니다: " + definition);
			}
			if (resolved.putIfAbsent(definition.id(), definition) != null) {
				throw new IllegalStateException("mcpServer id가 중복 등록되었습니다: " + definition.id());
			}
		}
		this.byId = Map.copyOf(resolved);
		LogUtil.sysout("dstone-ai-engine mcp: 등록된 MCP 서버 정의 = " + (this.byId.isEmpty() ? "없음" : this.byId.keySet()));
	}

	/** 기동 시 common.config.ConfigMcp가 순회하며 접속을 시도할 전체 정의 목록. */
	public Collection<McpServerDefinition> all() {
		return this.byId.values();
	}

}

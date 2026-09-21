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
 * MCP 서버 정보를 담아두는 등록소입니다. 로딩 순서는 YamlDefinitionLoader가
 * classpath:mcp/*.yml 파일들을 읽어서 McpServerDefinition으로 바꾸고, 이 McpServerRegistry가
 * 앱이 기동될 때 그것들을 한 번 모아서 보관하는 식입니다.
 *
 * WorkFlowRegistry나 AgentRegistry와 달리, 여기에는 "caller별로 이 id를 써도 되는지"를 검사하는
 * 조회 기능이 없습니다. common.config.ConfigMcp가 기동 시 이 목록 전체를 한 바퀴 돌면서 서버마다
 * 접속을 시도할 뿐이고, 앱이 실행되는 도중에 "이 id로 서버 하나만 찾아줘" 하는 조회는 애초에
 * 일어나지 않기 때문입니다.
 */
@Component
public class McpServerRegistry extends BaseObject {

	@Autowired
	private YamlDefinitionLoader loader;

	private Map<String, McpServerDefinition> byId = Map.of();

	/**
	 * 앱이 기동될 때 한 번 호출되어, mcp/*.yml에 정의된 MCP 서버 정보를 전부 읽어 id를 키로
	 * 하는 맵에 채워 넣습니다. id나 transport가 비어 있는 정의가 있거나, 같은 id가 둘 이상
	 * 있으면 기동 자체를 실패시켜서 잘못된 설정이 조용히 넘어가지 않게 합니다.
	 */
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

	/** 등록된 MCP 서버 정의 전체를 돌려줍니다. 앱 기동 시 common.config.ConfigMcp가 이 목록을 순회하며 서버마다 접속을 시도합니다. */
	public Collection<McpServerDefinition> all() {
		return this.byId.values();
	}

}

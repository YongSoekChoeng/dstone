package net.dstone.ai.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.definition.McpServerDefinition;
import net.dstone.ai.common.registry.McpServerRegistry;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;

/**
 * resources/mcp/*.yml로 정의된 MCP 서버마다 접속해서, 그 서버가 제공하는 Tool을 common.config.ConfigTool의
 * ToolCallbackProvider에 합류시킨다. 합류 뒤에는 로컬 @AiTool Tool과 구분 없이 AGENT 스텝의 tool-calling이나
 * TOOL 스텝에서 이름으로 쓸 수 있다(별도 StepType 없이 기존 TOOL 경로 그대로 확장).
 *
 * 서버 하나에 접속이 실패해도(오타난 커맨드, 응답 없는 URL 등) 앱 기동 자체를 막지 않는다 - 서버별로 접속을
 * try-catch해서 실패한 서버만 로그를 남기고 건너뛴다. ConfigTool이 이 클래스를 @Autowired로 의존하고 있어서,
 * Spring이 ConfigTool을 초기화하기 전에 이 클래스의 @PostConstruct(connect())가 먼저 끝나는 게 보장된다.
 */
@Component
public class ConfigMcp extends BaseObject {

	@Autowired
	private McpServerRegistry mcpServerRegistry;

	private final List<ToolCallback> toolCallbacks = new ArrayList<>();

	@PostConstruct
	public void connect() {
		for (McpServerDefinition definition : this.mcpServerRegistry.all()) {
			try {
				this.connectOne(definition);
			} catch (Exception e) {
				LogUtil.sysout("dstone-ai-engine mcp: [" + definition.id() + "] 접속 실패 - 이 서버의 Tool은 등록되지 않습니다. 원인: " + e.getMessage());
			}
		}
	}

	/** @param definition 접속할 MCP 서버 정의 */
	private void connectOne(McpServerDefinition definition) {
		McpClientTransport transport = this.buildTransport(definition);
		McpSyncClient client = McpClient.sync(transport)
			.clientInfo(new McpSchema.Implementation("dstone-ai-engine", "1.0.0"))
			.build();
		client.initialize();

		List<ToolCallback> discovered = SyncMcpToolCallbackProvider.syncToolCallbacks(List.of(client));
		int added = 0;
		for (ToolCallback callback : discovered) {
			if (this.isAllowed(definition, callback)) {
				this.toolCallbacks.add(callback);
				added++;
			}
		}
		LogUtil.sysout("dstone-ai-engine mcp: [" + definition.id() + "] 접속 성공 - Tool " + discovered.size() + "개 중 " + added + "개 등록");
	}

	/** @param definition 트랜스포트를 만들 MCP 서버 정의 */
	private McpClientTransport buildTransport(McpServerDefinition definition) {
		McpJsonMapper jsonMapper = new JacksonMcpJsonMapperSupplier().get();
		return switch (definition.transport()) {
			case STDIO -> {
				ServerParameters params = ServerParameters.builder(definition.command())
					.args(definition.args() == null ? List.of() : definition.args())
					.build();
				yield new StdioClientTransport(params, jsonMapper);
			}
			case SSE -> HttpClientSseClientTransport.builder(definition.url()).jsonMapper(jsonMapper).build();
		};
	}

	/**
	 * @param definition allowedTools 화이트리스트를 가진 MCP 서버 정의
	 * @param callback    허용 여부를 판단할 Tool
	 */
	private boolean isAllowed(McpServerDefinition definition, ToolCallback callback) {
		List<String> allowedTools = definition.allowedTools();
		if (allowedTools == null || allowedTools.isEmpty()) {
			return true;
		}
		return allowedTools.contains(callback.getToolDefinition().name());
	}

	/** common.config.ConfigTool이 로컬 @AiTool Tool들과 합칠 MCP Tool 전체 목록. */
	public List<ToolCallback> toolCallbacks() {
		return List.copyOf(this.toolCallbacks);
	}

}

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
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.McpServerDefinition;
import net.dstone.ai.common.registry.McpServerRegistry;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * resources/mcp/*.yml에 적어둔 MCP 서버들에 하나씩 접속해서, 
 * 그 서버가 제공하는 Tool을 common.config.ConfigTool의 ToolCallbackProvider에 합류시켜 주는 클래스입니다. 
 * 한번 합류되고 나면 원래부터 있던 로컬 @AiTool Tool과 구분 없이 똑같이 취급됩니다 
 * 
 * MCP 서버 중 하나가 접속에 실패하더라도(커맨드를 잘못 적었거나, URL이 응답하지 않는 경우 등) 앱
 * 전체의 기동이 막히지는 않습니다. 서버 하나하나를 try-catch로 감싸서 접속을 시도하고, 실패한
 * 서버는 로그만 남기고 건너뛰기 때문입니다.
 *
 * ConfigTool이 이 클래스를 @Autowired로 의존하고 있기 때문에, 스프링은 ConfigTool을 초기화하기
 * 전에 이 클래스의 @PostConstruct 메소드(connect())가 먼저 끝나도록 순서를 보장해 줍니다. 즉
 * ConfigTool이 Tool 목록을 모을 때는 이미 MCP 서버 접속이 다 끝난 상태입니다.
 * </pre>
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

	/**
	 * MCP 서버 하나에 실제로 접속해서, 그 서버가 내놓는 Tool 중 허용된 것만 골라 등록합니다.
	 *
	 * @param definition 접속할 MCP 서버의 정의(주소, 접속 방식, 허용 Tool 목록 등)
	 */
	private void connectOne(McpServerDefinition definition) {
		try {
			this.debug("PATH=" + System.getenv("PATH"));
			this.debug("definition=" + definition);
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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * MCP 서버 정의에 적힌 접속 방식(STDIO 또는 SSE)에 맞춰 실제 통신에 쓸 트랜스포트를 만들어 줍니다.
	 *
	 * @param definition 트랜스포트를 만들 대상이 되는 MCP 서버 정의
	 */
	private McpClientTransport buildTransport(McpServerDefinition definition) {
		McpJsonMapper jsonMapper = new JacksonMcpJsonMapperSupplier().get();
		return switch (definition.transport()) {
			case STDIO -> {
				ServerParameters params = this.buildStdioParams(definition);
				yield new StdioClientTransport(params, jsonMapper);
			}
			case SSE -> HttpClientSseClientTransport.builder(definition.url()).jsonMapper(jsonMapper).build();
		};
	}

	/**
	 * STDIO MCP 서버를 실제로 띄울 커맨드/인자를 조립합니다. YAML에 적힌 command/args 그대로 쓰는 게
	 * 기본이지만, Constants.Mcp.STDIO_COMMAND_PREFIX_PROPERTY(MCP_STDIO_COMMAND_PREFIX) 시스템
	 * 프로퍼티가 설정되어 있으면(주로 Windows에서만 - conf/env.properties 참고) 그 값을 공백으로
	 * 쪼갠 토큰들을 커맨드/인자 맨 앞에 그대로 이어 붙입니다. 예를 들어 이 값이 "cmd.exe /c"이면,
	 * YAML의 "command: npx, args: [-y, ...]"는 실제로는
	 * "cmd.exe /c npx -y ..."로 실행됩니다 - npx처럼 .cmd/.bat인 커맨드는 Windows에서 cmd.exe 없이
	 * ProcessBuilder가 곧바로 실행시킬 수 없기 때문입니다.
	 *
	 * @param definition 커맨드/인자를 조립할 MCP 서버 정의(STDIO 전용)
	 */
	private ServerParameters buildStdioParams(McpServerDefinition definition) {
		List<String> commandLine = new ArrayList<>();
		String prefix = System.getProperty(Constants.Mcp.STDIO_COMMAND_PREFIX_PROPERTY);
		if (!StringUtil.isEmpty(prefix)) {
			for (String token : prefix.trim().split("\\s+")) {
				commandLine.add(token);
			}
		}
		commandLine.add(definition.command());
		if (definition.args() != null) {
			commandLine.addAll(definition.args());
		}
		return ServerParameters.builder(commandLine.get(0))
			.args(commandLine.subList(1, commandLine.size()))
			.build();
	}

	/**
	 * 이 Tool을 실제로 등록해도 되는지 판단합니다. 정의에 allowedTools 목록이 비어 있으면 전부
	 * 허용하고, 목록이 있으면 그 이름 안에 들어있는 Tool만 허용합니다.
	 *
	 * @param definition allowedTools 화이트리스트를 갖고 있는 MCP 서버 정의
	 * @param callback    허용 여부를 판단할 대상 Tool
	 */
	private boolean isAllowed(McpServerDefinition definition, ToolCallback callback) {
		List<String> allowedTools = definition.allowedTools();
		if (allowedTools == null || allowedTools.isEmpty()) {
			return true;
		}
		return allowedTools.contains(callback.getToolDefinition().name());
	}

	/** 접속에 성공한 MCP 서버들에서 모아온 Tool 전체 목록입니다. common.config.ConfigTool이 이 목록을 로컬 @AiTool Tool들과 합쳐서 씁니다. */
	public List<ToolCallback> toolCallbacks() {
		return List.copyOf(this.toolCallbacks);
	}

}

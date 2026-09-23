package net.dstone.ai.common.definition;

import java.util.List;

import net.dstone.ai.common.consts.McpTransport;

/**
 * 외부 MCP(Model Context Protocol) 서버 하나를 어떻게 연결할지 정의하는 클래스입니다. resources/mcp/*.yml
 * 파일 하나(그 안의 mcpServer: 라는 최상위 키)가 이 클래스의 값으로 채워집니다.
 *
 * 엔진이 기동할 때 common.config.ConfigMcp가 이 정의를 보고 실제로 MCP 서버에 접속하고, 그 서버가 제공하는
 * Tool들을 가져와서 common.config.ConfigTool이 관리하는 Tool 목록(ToolCallbackProvider)에 합쳐 넣습니다.
 * 한 번 합쳐지고 나면, 이 서버에서 온 Tool인지 우리 코드에서 직접 만든(@AiTool) Tool인지는 구분되지 않고
 * 똑같은 방식으로 쓸 수 있습니다 - AGENT step에서 LLM이 스스로 골라 부르거나, TOOL step에서 이름으로
 * 직접 지정해서 부를 수 있습니다.
 *
 * caller(호출 주체, tenant)별로 이 서버의 Tool을 쓸 수 있는지 없는지를 정하는 규칙은 이 클래스에는
 * 없습니다. 다른 모든 Tool과 완전히 똑같은 방식으로, common.config.ConfigTool의 설정값
 * dstone.ai.tool.allowed-by-caller(Tool 이름을 기준으로 한 화이트리스트)로 정해집니다. Workflow나
 * Agent는 "id로 찾을 때마다 caller를 검사"하는 방식이지만, MCP 서버는 "엔진이 켜질 때 한 번 접속해서
 * Tool 목록에 합류시켜 두는" 방식이라, 서버마다 별도의 caller 규칙을 두지 않고 화이트리스트 하나로
 * 통일해서 관리합니다.
 *
 * @param id           이 MCP 서버를 가리키는 이름입니다(로그에 남기거나 구분할 때 씁니다)
 * @param transport    이 서버에 어떤 방식으로 접속할지를 정합니다(STDIO 또는 SSE)
 * @param command      transport가 STDIO일 때, 그 서버를 실행할 명령어입니다
 * @param args         transport가 STDIO일 때, command에 함께 넘길 인자 목록입니다
 * @param url          transport가 SSE일 때, 접속할 서버의 URL입니다
 * @param allowedTools 이 서버가 제공하는 여러 Tool 중에서 실제로 가져다 쓸 Tool의 이름 목록입니다.
 *                     비워두면 이 서버가 제공하는 Tool을 전부 가져옵니다
 */
public record McpServerDefinition(String id, McpTransport transport, String command, List<String> args, String url, List<String> allowedTools) {
}

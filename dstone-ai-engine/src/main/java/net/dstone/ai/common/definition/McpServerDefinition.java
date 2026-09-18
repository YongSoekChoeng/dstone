package net.dstone.ai.common.definition;

import java.util.List;

/**
 * resources/mcp/*.yml 파일 하나(mcpServer: 최상위 키)가 이 구조로 바인딩된다. common.config.ConfigMcp가 기동 시 이 정의마다
 * MCP 서버에 접속해서 그 서버가 제공하는 Tool들을 common.config.ConfigTool의 ToolCallbackProvider에 합류시킨다 - 합류된
 * 뒤에는 로컬 @AiTool과 구분 없이 AGENT 스텝의 tool-calling이나 TOOL 스텝에서 이름으로 쓸 수 있다.
 *
 * caller(tenant)별 접근 제어는 이 정의 수준이 아니라, 다른 Tool과 완전히 동일하게 common.config.ConfigTool의
 * dstone.ai.tool.allowed-by-caller(Tool 이름 화이트리스트)로 한다 - Workflow/Agent처럼 "id로 resolve할 때 caller를
 * 검사"하는 흐름이 아니라 "기동 시 한 번 접속해 Tool 풀에 합류"하는 흐름이라, MCP 서버 전용 caller 필드를 따로 두면 이름
 * 기반 화이트리스트와 판단 기준이 두 곳으로 갈라진다.
 *
 * @param id           MCP 서버 식별자(로그/식별용)
 * @param transport    접속 방식(STDIO/SSE)
 * @param command      STDIO일 때 실행할 커맨드
 * @param args         STDIO일 때 커맨드에 넘길 인자 목록
 * @param url          SSE일 때 접속할 서버 URL
 * @param allowedTools 이 서버가 제공하는 Tool 중 실제로 가져올 이름 목록(비우면 전체 허용)
 */
public record McpServerDefinition(String id, McpTransport transport, String command, List<String> args, String url, List<String> allowedTools) {
}

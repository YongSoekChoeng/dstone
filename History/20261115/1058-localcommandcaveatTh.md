# localcommandcaveatTh

> 생성일시: 2026-11-15 10:58

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

로컬 windows 에서 dstone-ai-engine 을 기동할 때 이런 로그가 보이는데 이게 문제가 되는가? 

<pasted_content id="b7b8">
2026-11-15 10:56:39  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] MCP server starting.
2026-11-15 10:56:40  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] MCP server started
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE Unsupported engine {
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   package: '@modelcontextprotocol/core@2.3.1',
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   required: { node: '>=20' },
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   current: { node: 'v18.17.0', npm: '9.6.7' }
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE }
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE Unsupported engine {
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   package: '@modelcontextprotocol/server@2.3.1',
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   required: { node: '>=20' },
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   current: { node: 'v18.17.0', npm: '9.6.7' }
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE }
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE Unsupported engine {
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   package: 'brace-expansion@5.0.12',
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   required: { node: '20 || >=22' },
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   current: { node: 'v18.17.0', npm: '9.6.7' }
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE }
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE Unsupported engine {
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   package: 'minimatch@11.0.4',
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   required: { node: '20 || >=22' },
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   current: { node: 'v18.17.0', npm: '9.6.7' }
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE }
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE Unsupported engine {
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   package: '@modelcontextprotocol/server-filesystem@2.3.0',
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   required: { node: '>=24' },
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE   current: { node: 'v18.17.0', npm: '9.6.7' }
2026-11-15 10:56:51  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: npm WARN EBADENGINE }
2026-11-15 10:56:52  INFO [io.modelcontextprotocol.client.transport.StdioClientTransport] STDERR Message received: Secure MCP Filesystem Server running on stdio
</pasted_content id="b7b8">

---

## 답변

**지금 당장은 문제가 아닙니다.** 다만 언제든 깨질 수 있는 상태라 Windows의 Node를 올리는 것을 권합니다.

**지금 괜찮은 이유**
- `EBADENGINE`은 npm이 남기는 **경고(WARN)**일 뿐이고 설치를 막지 않습니다.
- 마지막 줄 `Secure MCP Filesystem Server running on stdio`를 보면 서버가 정상으로 떴습니다.
- ai-engine이 이 로그를 INFO로 찍는 건 MCP 클라이언트가 자식 프로세스의 stderr를 그대로 옮겨 적기 때문입니다. 오류가 아닙니다.

**그래도 고쳐야 하는 이유**
- `conf/definitions/mcp/sample/sample-filesystem.yml`은 `npx -y @modelcontextprotocol/server-filesystem`처럼 **버전을 고정하지 않습니다.** 그래서 매번 최신판(지금은 2.3.0)을 받고, 이 버전은 **Node 24 이상**을 요구합니다. 로컬 Windows의 Node는 18.17.0입니다.
- 패키지가 Node 20이나 24에만 있는 API를 쓰는 순간 기동이나 특정 Tool 호출(`read_text_file`, `edit_file` 등)이 런타임에 실패합니다. 지금은 우연히 안 걸렸을 뿐입니다.
- Node 18은 이미 지원이 끝난(EOL) 버전입니다.

**권장 조치 (택 1)**
1. **Windows의 Node를 24 LTS로 올리기 (권장).** 설치한 뒤 `node -v`로 확인하고, 필요하면 `npx clear-npx-cache`나 `%LocalAppData%\npm-cache\_npx` 정리로 받아 둔 캐시를 비운 다음 다시 기동하면 경고가 사라집니다.
2. Node를 못 올리는 상황이라면 yml의 `args`에 Node 18에서 돌아가던 옛 버전을 `@modelcontextprotocol/server-filesystem@<버전>`처럼 고정합니다. 이 방법은 임시방편이고 보안 패치도 받지 못합니다.

Node 버전이 WSL과 같은지도 확인해 보세요. 다르면 Windows에서만 생기는 문제가 됩니다.

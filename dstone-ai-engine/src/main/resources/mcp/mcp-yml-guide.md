
### 1 MCP 서버 항목

`mcpServer:` 아래에 적는다(`common.definition.McpServerDefinition`).

| 항목 | 필수 | 설명 |
|---|---|---|
| `id` | ✅ | 서버 이름(로그 구분용) |
| `transport` | ✅ | `STDIO`(로컬 프로세스를 띄워 표준 입출력으로 대화) / `SSE`(원격 HTTP 서버에 접속) |
| `command` | STDIO | 서버를 실행할 명령어(예: `npx`) |
| `args` | STDIO | `command`에 넘길 인자 리스트 |
| `url` | SSE | 접속할 서버 URL |
| `allowedTools` | | 이 서버의 Tool 중 실제로 가져올 이름 목록. 비우면 전부 |

접속에 실패한 서버는 로그만 남기고 건너뛴다(엔진 기동은 계속된다). caller별 접근 제어는 서버 단위가 아니라 다른 Tool과
똑같이 `dstone.ai.tool.allowed-by-caller`(Tool 이름 기준)로 한다.

**MCP 서버**: `mcp/sample/sample-filesystem-mcp.yml` — 공식 filesystem 레퍼런스 서버를 STDIO로 띄워
`${APP_HOME}/${APP_NAME}/mcp/server-filesystem` 하나만 노출한다(`list_directory`/`read_text_file`/`write_file`/`edit_file`/`move_file`만 허용).

`

### 2 MCP 서버 뼈대

**STDIO(로컬 프로세스)**

```yaml
mcpServer:
  id: <server-id>
  transport: STDIO
  command: npx
  args: ["-y", "<npm 패키지>", "${APP_HOME}/${APP_NAME}/<노출할 경로>"]
  allowedTools: ["<tool1>", "<tool2>"]   # 비우면 전부
```

Windows에서는 `npx`가 실제로는 `npx.cmd`(배치 스크립트)라 `ProcessBuilder`가 바로 실행하지 못한다. 그래서 Windows용
`conf/env.properties`에 `MCP_STDIO_COMMAND_PREFIX=cmd.exe /c`를 넣어 두면 `ConfigMcp`가 커맨드 앞에 붙여서 실행한다.
YAML은 환경마다 고칠 필요가 없다.

**SSE(원격 서버)**

```yaml
mcpServer:
  id: <server-id>
  transport: SSE
  url: http://<host>:<port>
  allowedTools: []                      # 비우면 전부
```

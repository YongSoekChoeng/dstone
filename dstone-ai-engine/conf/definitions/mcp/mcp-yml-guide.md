### 1 MCP 서버 항목

`mcp/**/<server-id>.yml` 파일 맨 위에 감싸는 키 없이 바로 적는다(`common.definition.mcp.McpServerDefinition`).

| 항목 | 필수 | 설명 |
|---|---|---|
| `id` | ✅ | 서버 이름(로그 구분용) |
| `transport` | ✅ | `STDIO`(로컬 프로세스를 띄워 표준 입출력으로 대화) / `SSE`(원격 HTTP 서버에 접속) |
| `command` | STDIO | 서버를 실행할 명령어(예: `npx`) |
| `args` | STDIO | `command`에 넘길 인자 리스트 |
| `url` | SSE | 접속할 서버 URL |
| `allowedTools` | | 이 서버의 Tool 중 실제로 가져올 이름 목록. 비우면 전부 |
| `repeatableTools` | | 같은 인자로 연달아 불러 같은 결과가 나와도 정상인 Tool(기다리는 용도) 이름 목록. "반복 호출" 감지(`dstone.ai.agent.tool.repeat-guard`)에서 빠진다. 비우면 모두 센다 |

접속에 실패한 서버는 로그만 남기고 건너뛴다(엔진 기동은 계속된다).
`command`·`args`·`url`의 `${이름}`은 `env*.properties` 값으로 바뀐다. 값이 비어 `${이름}`이 그대로 남으면 접속을 시도하지 않고
바로 건너뛴다(로그 "건너뜀 - 설정값이 비어 있습니다"). 설치한 환경에서만 쓰는 서버는 이렇게 환경 값으로 켜고 끈다(예: `common/browser.yml`).

caller별 접근 제어는 서버 단위가 아니라 다른 Tool과 똑같이 `dstone.ai.tool.allowed-by-caller`(Tool 이름 기준)로 한다.

**MCP 서버 예**: `definitions/mcp/common/browser.yml` — 설치해 둔 Playwright MCP를 `node`로 바로 띄우고(폐쇄망에서 내려받지 않게),
화면 재현에 필요한 Tool만 연다. 설치 경로는 `env*.properties` 값으로 받아, 설치하지 않은 환경에서는 건너뛴다.

### 2 MCP 서버 뼈대

**STDIO(로컬 프로세스)** — `definitions/mcp/<폴더>/<server-id>.yml`

```yaml
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
id: <server-id>
transport: SSE
url: http://<host>:<port>
allowedTools: []                      # 비우면 전부
```

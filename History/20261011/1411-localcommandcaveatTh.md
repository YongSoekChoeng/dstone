# localcommandcaveatTh

> 생성일시: 2026-10-11 14:11

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

현재 dstone-ai-engine 에 Firecrawl 를 접목할 수 있나?

---

## 답변

현재 구조에서 어떤 경로로 붙일 수 있는지 MCP 정의와 HTTP Tool 쪽을 잠깐 확인하겠습니다.

---

## 답변

네, 붙일 수 있습니다. 엔진에 이미 MCP 클라이언트가 있어서 Firecrawl MCP 서버를 YAML 하나로 등록하는 길이 가장 자연스럽습니다. 다만 지금 코드 그대로는 API 키를 넘길 방법이 없어 작은 수정이 하나 필요합니다. 아래 Firecrawl 쪽 사양(패키지 이름, Tool 이름, 키 변수)은 제 기억 기준이고 이번에 실제로 붙여 보지는 않았습니다.

## 방법 1: MCP 서버로 등록 (권장)

`definitions/mcp/` 아래에 `sample-filesystem-mcp.yml`과 같은 형태로 파일을 하나 추가합니다.

```yaml
mcpServer:
  id: firecrawl
  transport: STDIO
  command: npx
  args: ["-y", "firecrawl-mcp"]
  allowedTools: ["firecrawl_scrape", "firecrawl_search", "firecrawl_map"]
```

등록되면 `@AiTool`과 똑같이 취급됩니다. Agent는 `tools.allowed`에 이름을 적어 쓰고, Workflow는 TOOL step에서 LLM 없이 직접 부를 수 있습니다.

**걸리는 점: API 키 전달**
- `McpServerDefinition`에는 `id/transport/command/args/url/allowedTools`만 있고, `ConfigMcp.buildStdioParams()`도 환경변수를 넘기지 않습니다.
- Firecrawl MCP는 `FIRECRAWL_API_KEY` 환경변수가 필요하므로, 정의에 `env` 맵을 추가하고 `ServerParameters`에 넘기도록 고쳐야 합니다. 값은 `${FIRECRAWL_API_KEY}` 치환으로 받으면 됩니다.
- 수정 없이 하려면 엔진 프로세스 자체에 그 환경변수를 주고 띄우는 방법이 있는데, 자식 프로세스가 물려받는지는 확인해 봐야 합니다.
- 원격 접속은 `SSE`만 지원하고 인증 헤더 설정이 없어서, Firecrawl 호스팅 MCP에 붙으려면 `url`에 키를 넣는 방식만 가능합니다.

## 방법 2: 전용 `@AiTool` 작성

`KnowledgeTool`/`KnowledgeClient`처럼 Firecrawl REST API(`/scrape`, `/search` 등)를 부르는 `tools.firecrawl.FirecrawlTool`을 만드는 길입니다. Node/npx 의존이 없고, 키를 `ENC(...)`로 `application.yml`에 둘 수 있으며, 결과를 Tool 안에서 줄일 수 있습니다. 대신 Java 코드와 테스트가 늘어납니다.

기존 `HttpCallTool`로는 안 됩니다. GET 전용에 헤더를 못 붙이고 응답도 4,000자에서 자르는데, Firecrawl은 POST와 Bearer 인증이 필요합니다.

## 붙일 때 같이 정해야 할 것

- **결과 크기**: 스크랩 결과는 한 건이 수만 자가 될 수 있습니다. `max-result-chars`(60,000)와 결과 압축이 막아 주긴 하지만, `onlyMainContent` 같은 옵션을 쓰도록 프롬프트에 적고 `execution.maxToolCalls`를 낮게 잡는 편이 안전합니다.
- **허용 범위**: `firecrawl_crawl`처럼 오래 걸리고 크레딧을 많이 쓰는 Tool은 `allowedTools`에서 빼고 scrape/search 정도만 여는 것을 권합니다.
- **폐쇄망**: 클라우드 Firecrawl은 외부 인터넷과 유료 키가 필요합니다. 폐쇄망 SI 현장이라면 self-host Firecrawl을 띄우고 그쪽 주소를 가리키게 해야 합니다.
- **k8s Pod**: STDIO 방식은 컨테이너 이미지에 Node가 있어야 합니다. 현재 Dockerfile에 들어 있는지는 확인하지 않았습니다.
- **RAG 연계**: 긁어 온 페이지를 검색 대상으로 쌓으려면 엔진이 아니라 `dstone-knowledge`의 `/api/documents`로 올리는 흐름이 됩니다.

## 추천

방법 1에 `env` 지원을 추가하는 쪽을 권합니다. 수정이 `McpServerDefinition`, `ConfigMcp`, 가이드 문서 정도로 작고, `env` 지원은 키가 필요한 다른 MCP 서버에도 그대로 쓰입니다. Node 의존을 피해야 하거나 키를 `ENC(...)`로 관리해야 한다면 방법 2가 맞습니다.

진행하시려면 어느 방법으로 할지와 클라우드/self-host 중 어느 쪽인지 알려 주세요.

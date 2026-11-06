# localcommandcaveatTh

> 생성일시: 2026-11-06 14:38

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-ai-engine 을 이용해서 터미널용(CLI) AI를 구현할 수 있을까?

---

## 답변

네, 가능합니다. 필요한 기능이 이미 REST API로 대부분 열려 있어서, 새로 만들 부분은 터미널 쪽 껍데기 정도입니다.

## 지금 바로 쓸 수 있는 것

| 기능 | API | CLI에서의 용도 |
|---|---|---|
| 스트리밍 대화 | `POST /api/ai/chat/stream` (SSE, `Flux<String>`) | 답변을 토큰 단위로 바로 출력 |
| 대화 이어가기 | `ChatRequest.sessionId` + Redis 대화 메모리 | REPL에서 여러 턴 대화 |
| Agent·모델 선택 | `agent`, `model`, `ragEnabled`, `toolsEnabled` | `/agent sm-xxx`, `/model ...` 같은 명령 |
| Workflow 실행 | `POST /api/ai/workflow/{id}/submit`, `GET .../status/{executionId}` | 긴 작업을 실행하고 진행 상황 표시 |
| 승인 대기 | `POST /api/ai/workflow/executions/{id}/decision` | 터미널에서 `y/n` 입력으로 승인 |
| 일시정지·재개·되감기 | `/pause`, `/resume`, `/rewind` | Ctrl+C로 멈추고 이어서 실행 |
| 결과 파일 | `/{id}/files`, `/files/content` | 산출물 출력 또는 저장 |

## 구현 방식 세 가지

**A. 얇은 클라이언트** (추천하는 출발점)
- `dstone-cli` 같은 작은 Java 프로그램이 위 API를 호출하는 REPL입니다. JLine으로 입력을 받고, `java.net.http.HttpClient`로 SSE를 읽습니다.
- 서버는 고칠 필요가 없고, `X-API-Key` 인증도 그대로 쓸 수 있습니다.
- **한계:** Tool이 서버에서 실행됩니다. `FileTool`과 `ShellExecTool`은 사용자 PC가 아니라 ai-engine Pod 안의 파일시스템에서 돌아갑니다. 그래서 Knowledge 질의, SM Workflow, 대화형 질의응답에는 잘 맞지만, "내 로컬 프로젝트 코드를 고쳐 줘" 같은 Claude Code류 작업에는 맞지 않습니다.

**B. 엔진을 CLI 프로세스에 직접 넣기**
- ai-engine을 `spring.main.web-application-type=none`으로 띄우고 `AgentExecutor`/`WorkFlowExecutor`를 직접 호출합니다.
- 이렇게 하면 File/Shell Tool이 사용자의 현재 디렉터리에서 실행되므로 로컬 코딩 에이전트가 됩니다.
- **단점:** Spring 부팅에 몇 초가 걸립니다. 실행 이력용 PostgreSQL이 필요하고, 정의 파일과 `conf/`를 함께 배포해야 해서 무겁습니다.

**C. 하이브리드 (Tool은 클라이언트가 실행)**
- 서버는 LLM 판단만 하고, Tool 호출 요청을 CLI로 넘겨 로컬에서 실행한 뒤 결과를 되돌려 받습니다. Spring AI의 `internalToolExecutionEnabled=false`로 이런 흐름을 만들 수 있습니다.
- 구조로는 가장 깔끔하지만, 서버에 새 API(Tool 결과 반환 엔드포인트)와 대화 상태 관리를 추가해야 합니다. 작업량이 가장 많습니다.

## 추천

먼저 **A**로 대화, Workflow 실행, 승인까지 되는 CLI를 만들고, 로컬 파일을 다루는 에이전트가 필요해지면 **C**로 넓히는 순서가 좋겠습니다. **B**는 빠르게 시험해 보기에는 좋지만, 배포 구조(ai-engine은 k8s Pod)와 어긋납니다.

CLI의 주 용도를 알려 주시면 그에 맞춰 설계안을 잡거나 바로 A부터 구현하겠습니다. 예를 들면 SM 업무나 Knowledge 질의 같은 서버 작업인지, 로컬 코드 작업인지에 따라 방식이 달라집니다.

---

## 질문

dstone-ai-engine 을 이용해서 터미널용(CLI) AI를 구현할 수 있을까?
내가 원하는것은 dstone-boot>ai 처럼 서버를 이용한 수정 진행기능 말고도
개발자가 PC 내에서 터미널로 작업하는 방법을 말하는 거야. 너 클로드 코드 처럼.

---

## 답변

네, 가능합니다. 다만 지금 구조를 그대로 쓰면 안 되고, **Tool이 어디서 실행되느냐**를 바꿔야 합니다.

## 핵심 차이

지금 dstone-boot>ai는 LLM 판단과 Tool 실행이 모두 서버(ai-engine Pod)에서 일어납니다. 그래서 `FileTool`이 고치는 파일도 서버에 있는 파일입니다.

Claude Code는 이렇게 나뉩니다.
- **LLM(판단)**: 원격 서버
- **Tool(파일 읽기·수정·명령 실행)**: 개발자 PC의 터미널 프로세스

dstone CLI도 이 구조를 따르면 됩니다.

## 추천 구조: 판단은 서버, 실행은 PC

```
개발자 PC                                  ai-engine (서버)
┌──────────────────────────┐              ┌─────────────────────────┐
│ dstone-cli (java -jar)   │  사용자 입력   │ /api/ai/cli/turn (신규)  │
│  - REPL (JLine)          │ ───────────▶ │  Agent 정의 + LLM 호출   │
│  - 로컬 Tool 실행         │ ◀─────────── │  (Tool은 실행하지 않음)  │
│    read/grep/edit/bash   │ tool_call 요청 │                         │
│  - 실행 전 승인(y/n)      │ ───────────▶ │  다음 판단 / 최종 답변    │
│  - 현재 디렉터리 기준     │  tool 결과     │  API Key·로그·사용량 관리 │
└──────────────────────────┘              └─────────────────────────┘
```

Spring AI의 `internalToolExecutionEnabled=false`를 쓰면 LLM이 Tool 호출을 요청할 때 서버가 그 Tool을 직접 실행하지 않고 호출 내용만 돌려줍니다. CLI가 그 Tool을 로컬에서 실행하고 결과를 다시 보내면 루프가 이어집니다.

PC마다 엔진 전체를 띄우는 방식(앞에서 말한 B)보다 이 방식이 나은 이유는 다음과 같습니다.
- LLM API Key가 개발자 PC로 나가지 않습니다.
- `LlmLoggerAdvisor` 로그, Rate Limit, provider 교체 같은 거버넌스가 서버에 그대로 남습니다.
- PC에는 PostgreSQL이나 Redis가 필요 없고, 가벼운 jar 하나만 있으면 됩니다.
- Agent 프롬프트(`agents/cli/*.md`)와 Skill을 서버에서 한 번에 고칠 수 있습니다.

## 이미 있는 것과 새로 만들 것

| 구분 | 이미 있음 | 새로 만들 것 |
|---|---|---|
| 파일 Tool | `FileTool`에 Claude Code 수준의 Tool이 이미 있음 (`readFileLines`, 키워드 검색, `oldText→newText` 수정, 여러 곳 한 번에 수정, 줄 번호 수정) | 이 로직을 서버 Bean에서 떼어 `dstone-common`이나 공용 모듈로 옮기고, CLI에서도 쓸 수 있게 함 |
| 셸 | `ShellExecTool`은 등록된 스크립트만 실행 | 임의 명령을 실행하는 CLI 전용 Bash Tool + 실행 전 승인 + Windows(cmd/PowerShell) 대응 |
| 승인 | Workflow 단위의 승인 대기 | Tool 호출 단위로 묻기(y / n / 이번 세션은 항상 허용) |
| 대화 | `sessionId` + Redis 메모리, SSE 스트리밍 | Tool 호출·결과 메시지까지 포함한 대화 이력 관리 |
| 컨텍스트 관리 | `ToolResultCompactionAdvisor`, Tool 호출 예산 | 긴 세션의 대화 요약(compact), 세션 재개 |
| 프로젝트 지식 | `KnowledgeTool`(서버 측 RAG) | 그대로 서버 Tool로 쓰면 됨. 로컬 Tool과 서버 Tool을 함께 쓰는 구조 |
| 프로젝트 규칙 | — | `DSTONE.md` 같은 프로젝트별 지침 파일을 읽어 시스템 프롬프트에 붙이기 |

## 단계별로 나누면

1. **M1** REPL과 스트리밍 대화만 (기존 `/api/ai/chat/stream` 사용, 서버 수정 없음)
2. **M2** 서버 `/api/ai/cli/turn` 추가 + 로컬 읽기 전용 Tool (read, grep, 파일 목록)
3. **M3** 파일 수정 Tool + 승인 프롬프트 + 변경 diff 표시
4. **M4** Bash Tool (작업 디렉터리 제한, 위험 명령 차단 목록)
5. **M5** 대화 압축, 세션 이어가기, `DSTONE.md`, 슬래시 명령(`/agent`, `/model`, `/clear`)

## 미리 알아 둘 점

- **품질은 모델이 좌우합니다.** 에이전트 루프는 Claude 계열에서는 잘 돌지만, Ollama 같은 로컬 모델로는 Tool 호출이 불안정할 수 있습니다.
- **Bash가 가장 위험합니다.** 승인 없이 실행하는 모드는 처음부터 넣지 않는 편이 좋습니다.
- **개발자 PC는 대부분 Windows일 것이라** 경로와 셸 처리를 처음부터 고려해야 합니다.
- 서버 쪽 신규 코드도 CLAUDE.md 규칙(`AgentExecutor` 경유, `requestOptions()`, Advisor는 Bean으로 만들지 않기)을 따라야 해서, 기존 `AgentExecutor`에 "Tool은 실행하지 않고 호출 내용만 돌려주는" 경로를 하나 추가하는 형태가 됩니다.

이 방향이 맞다면 다음 중 하나로 진행하겠습니다.
- 설계 문서 작성 (API 스펙, 메시지 형식, Tool 목록, 보안 정책)
- M1~M2 프로토타입 바로 구현 (`dstone-cli` 모듈 신설)

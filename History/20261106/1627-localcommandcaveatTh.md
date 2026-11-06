# localcommandcaveatTh

> 생성일시: 2026-11-06 16:27

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

---

## 질문

dstone-ai-cli 라는 신규 모듈 만들고 dstone-ai-engine을 parent로 삼으면 어떨까?
굳이 dstone-ai-engine을 parent로 삼을 필요가 없을까나?

---

## 답변

**dstone-ai-engine을 parent로 둘 필요는 없습니다.** 지금 구조로는 애초에 불가능하기도 합니다.

## parent로 삼을 수 없는 이유

Maven에서 parent가 되려면 `<packaging>pom</packaging>`이어야 합니다. dstone-ai-engine은 jar 모듈이고, 이 저장소의 parent는 루트 `dstone`(pom) 하나뿐입니다. 다른 모듈도 모두 이 루트를 parent로 둡니다.

```
dstone (pom, parent)
 ├─ dstone-common
 ├─ dstone-boot
 ├─ dstone-ai-engine
 ├─ ...
 └─ dstone-ai-cli   ← 여기에 형제로 추가
```

## dependency로 넣는 것도 권하지 않습니다

parent 대신 ai-engine을 dependency로 넣는 방법도 떠올릴 수 있는데, 이것도 맞지 않습니다.

- **jar 형태 문제:** ai-engine은 `spring-boot-maven-plugin`으로 repackage된 실행 jar라서 클래스가 `BOOT-INF/classes` 아래에 들어갑니다. 다른 모듈이 그대로 가져다 쓸 수 없습니다.
- **무게 문제:** Spring AI(Anthropic/OpenAI/Ollama), MCP client, MyBatis, Redis, PostgreSQL 드라이버까지 전부 따라옵니다. 개발자 PC에서 `dstone-ai-cli`를 칠 때마다 이걸 다 띄우면 Claude Code처럼 바로 뜨는 CLI가 되지 않습니다.
- **구조 문제:** 앞에서 정한 대로 CLI는 서버와 HTTP로만 대화합니다. 코드 수준에서 엔진에 기댈 이유가 없습니다.

## 추천 구성

```xml
<parent>
    <groupId>net.dstone</groupId>
    <artifactId>dstone</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
<artifactId>dstone-ai-cli</artifactId>
```

의존성은 최소한으로 둡니다.
- JLine (입력, 히스토리, 자동완성)
- Jackson (JSON)
- `java.net.http.HttpClient` (JDK 기본, SSE 처리 포함)
- Spring Boot는 쓰지 않는 plain Java 21. 시작 속도와 jar 크기 때문입니다.

**dstone-common도 넣지 않는 쪽을 권합니다.** CLAUDE.md에는 "모든 모듈이 dstone-common에 의존한다"고 되어 있지만, common에는 Spring, MyBatis, 보안 같은 서버용 의존성이 60개 넘게 들어 있어서 CLI에는 맞지 않습니다. 이 모듈만 예외로 두고 CLAUDE.md에 한 줄 적어 두면 됩니다.

## 서버와 함께 쓰는 코드가 생기면

요청·응답 DTO처럼 CLI와 ai-engine이 같이 쓰는 것이 생길 수 있습니다. 처음에는 CLI 쪽에 따로 두는 편이 낫습니다. DTO 몇 개를 중복으로 두는 비용이 작기 때문입니다. `FileTool`의 수정 로직도 지금은 서버 Bean(`@Tool`, 서버 경로 정책)에 묶여 있으니, CLI에서는 로컬 정책에 맞게 새로 작성하는 게 자연스럽습니다.

나중에 중복이 커지면 그때 `dstone-ai-api`처럼 의존성 없는 작은 공용 jar를 하나 떼어 내고, ai-engine과 CLI가 함께 의존하게 하면 됩니다.

이 구성으로 모듈 뼈대(pom, 루트 `<modules>` 등록, 실행 jar 설정, M1 REPL)를 만들어 볼까요?

---

## 질문

그럼 dstone-ai-cli 의 구조와 서버통신구조 등 네가 생각하는 계획을 구체적으로 말해줘.

---

## 답변

아래는 지금 ai-engine 코드(`AgentExecutor.buildSpec()`, Advisor 구성, k8s 설정)를 확인하고 세운 계획입니다.

# dstone-ai-cli 설계안

## 1. 핵심 결정: 루프는 서버, Tool 실행은 PC

LLM과 Tool 사이의 루프를 어디에 둘지가 가장 큰 결정입니다. 방법은 두 가지가 있습니다.

| | (가) 서버가 Tool 호출만 돌려줌 (`internalToolExecutionEnabled=false`) | (나) 서버 루프 + **원격 Tool 콜백** |
|---|---|---|
| 동작 | 서버가 LLM 응답을 그대로 돌려주고, 클라이언트가 Tool을 실행한 뒤 대화 전체를 다시 보냄 | 서버에서 Spring AI 루프가 그대로 돌다가, CLI Tool 차례가 오면 SSE로 PC에 실행을 요청하고 결과를 기다림 |
| 기존 기능 재사용 | `ToolResultCompactionAdvisor`, Tool 호출 예산, `requiredTools`, Sub Agent, Skill이 모두 Spring AI 내부 루프를 전제로 만들어져 있어 **다시 구현해야 함** | **그대로 재사용** |
| 서버 Tool과 함께 쓰기 | `KnowledgeTool` 같은 서버 Tool도 직접 실행해 줘야 함 | 자연스럽게 섞임 (Knowledge는 서버, 파일은 PC) |
| 서버 상태 | 없음 | 진행 중인 turn을 메모리에 들고 있음 |

**(나)를 추천합니다.** 서버 상태가 생기는 약점은 지금 배포 환경(`replicas: 1`)에서는 문제가 되지 않습니다. 나중에 여러 Pod로 늘리면 sticky session이나 Redis pub/sub으로 보완하면 됩니다.

## 2. 전체 구조

```
개발자 PC                                        ai-engine (kind Pod, NodePort 30082)
┌────────────────────────────────┐              ┌──────────────────────────────────────┐
│ dstone-ai-cli                  │              │ CliController  /api/ai/cli/**         │
│                                │ ① 세션 시작   │   │                                  │
│  Repl ─ SlashCommands          │─────────────▶│ CliSessionService                    │
│   │                            │              │   │                                  │
│  TurnRunner ◀── SSE 이벤트 ────│◀─────────────│ CliTurnRunner                        │
│   │   text / tool_request      │ ② turn(SSE)  │   └ AgentExecutor.buildSpec()        │
│   │                            │              │       ├ 서버 Tool (KnowledgeTool …)   │
│  LocalToolRegistry             │              │       └ RemoteToolCallback ──┐        │
│   ├ PermissionManager (y/n)    │ ③ Tool 결과   │            (결과를 기다림)   │        │
│   └ read/grep/edit/bash …   ───│─────────────▶│ CliTurnRegistry ◀────────────┘        │
│                                │              │   (turnId → 대기 중인 Future)         │
│  ~/.dstone-ai/  .dstone-ai/    │              │ LlmLoggerAdvisor·RateLimit·API Key   │
└────────────────────────────────┘              └──────────────────────────────────────┘
```

## 3. 서버 통신 구조

### API

| 메서드 | 경로 | 역할 |
|---|---|---|
| POST | `/api/ai/cli/sessions` | 세션 시작. 클라이언트 버전, OS·셸, 작업 디렉터리, git 브랜치, `DSTONE.md` 내용을 보내면 `sessionId`, Agent, 사용 가능한 모델, 서버가 기대하는 Tool 목록(버전)을 받음 |
| POST | `/api/ai/cli/sessions/{sid}/turns` | 사용자 입력 한 번. **응답이 SSE 스트림** |
| POST | `/api/ai/cli/turns/{turnId}/tool-results/{callId}` | 로컬 Tool 실행 결과 전달 |
| POST | `/api/ai/cli/turns/{turnId}/cancel` | Esc/Ctrl+C로 중단 |
| POST | `/api/ai/cli/sessions/{sid}/compact` | 대화 요약 (`/compact`) |
| GET | `/api/ai/cli/sessions` | 이어서 할 세션 목록 (`--resume`) |

### SSE 이벤트

```
event: turn          data: {"turnId":"t-123"}
event: text          data: {"delta":"파일을 먼저 확인하겠습니다."}
event: tool_request  data: {"callId":"c-1","name":"client_read_file","input":{"path":"src/A.java"}}
event: tool_info     data: {"name":"KnowledgeTool.search","phase":"start"}     ← 서버 Tool 진행 표시용
event: usage         data: {"inputTokens":5321,"outputTokens":412}
event: done          data: {"stopReason":"end_turn"}
event: error         data: {"code":"LLM_TIMEOUT","message":"..."}
: ping                                                                          ← 15초마다 heartbeat
```

### Tool 결과 형식

```json
{ "ok": true, "output": "...", "truncated": false, "durationMs": 42 }
{ "ok": false, "denied": true, "reason": "사용자가 실행을 거부함: 테스트 DB를 건드리지 말 것" }
```

거부 사유를 LLM에 그대로 돌려줘야 모델이 방향을 바꿉니다. 출력 길이 제한은 CLI가 먼저 걸고(예: 30,000자), 서버의 `ToolResultCompactionAdvisor`가 한 번 더 걸러 줍니다.

### 타임아웃

| 대상 | 값 | 이유 |
|---|---|---|
| Tool 결과 대기 | 10분 (설정 가능) | 사람이 승인을 고민하는 시간이 포함됨 |
| SSE heartbeat | 15초 | NodePort나 프록시의 유휴 연결 끊김 방지 |
| LLM 호출 | `ConfigChatClient.requestOptions()` | CLAUDE.md 규칙 (Spring AI 기본 60초 덮어쓰기 대응) |
| 클라이언트 연결 끊김 | 대기 중인 Future를 즉시 취소 | 고아 turn 방지 |

## 4. 서버 쪽 변경 (dstone-ai-engine)

```
net.dstone.ai
├─ api.controller.CliController
├─ api.dto.cli.*                 (CliSessionRequest, CliTurnRequest, CliToolResult …)
└─ runtime.cli
   ├─ CliSessionService          세션 생성·목록, 환경 정보 → 프롬프트 조립
   ├─ CliTurnRunner              turn 하나 실행, SSE로 이벤트 발행
   ├─ CliTurnRegistry            turnId → 진행 상태, callId → CompletableFuture
   ├─ RemoteToolCallback         ToolCallback 구현. call()에서 tool_request를 보내고 결과를 기다림
   └─ RemoteToolCatalog          client_* Tool의 이름·설명·입력 스키마 정의
```

- **Tool 스키마는 서버가 정의합니다.** 부팅 시 정의 파일 검증이 `tools.allowed`의 이름을 레지스트리에서 찾기 때문입니다. 프롬프트 튜닝(Tool 설명 문구)을 서버에서 한 곳에서 관리할 수 있다는 장점도 있습니다. CLI는 세션 시작 때 Tool 목록 버전을 비교해서, 다르면 업데이트하라고 안내합니다.
- **`AgentExecutor`에는 경로를 하나만 추가합니다.** `buildSpec()`에 외부 ToolCallback 목록을 받는 인자를 더해서, 원격 Tool도 기존 예산·압축·로그 Advisor를 거치게 합니다. 직접 `ChatClient`를 부르는 코드는 만들지 않습니다.
- **Agent 정의:** `conf/definitions/agents/cli/cli-coder.md`. 프롬프트 본문은 코딩 에이전트용 지침이고, `tools.allowed`에는 `client_*`와 `KnowledgeTool`을 넣습니다. 나중에 `cli-reviewer`, `cli-sm` 같은 Agent를 정의 파일만으로 추가할 수 있습니다.
- **인증:** 지금은 `dstone.ai.security.auth.enabled=false`입니다. CLI를 열려면 켜야 하고, 그러면 dstone-boot도 `X-API-Key`를 보내도록 함께 바꿔야 합니다. 개발자별로 키를 발급하면 caller가 로그와 Rate Limit 기준이 됩니다.

## 5. 클라이언트 구조 (dstone-ai-cli)

```
dstone-ai-cli/
├─ pom.xml                       parent=dstone, Spring 없음, JLine + Jackson, shade로 실행 jar
├─ bin/dstone-ai(.cmd)           java -jar 래퍼 (Windows와 Linux)
└─ src/main/java/net/dstone/ai/cli
   ├─ DstoneAiCli                main. 인자: -p "질문"(1회 실행), --resume, --agent, --model
   ├─ config
   │  ├─ CliConfig               ~/.dstone-ai/config.properties (서버 URL, API Key)
   │  └─ ProjectConfig           <프로젝트>/.dstone-ai/settings.json (권한 규칙), DSTONE.md
   ├─ net
   │  ├─ EngineClient            java.net.http.HttpClient
   │  ├─ SseReader               이벤트 파싱 (줄 단위)
   │  └─ dto/*
   ├─ repl
   │  ├─ Repl                    JLine LineReader, 히스토리, 여러 줄 입력
   │  ├─ SlashCommands           /help /clear /agent /model /compact /resume /permissions /exit
   │  ├─ TurnRunner              이벤트 루프: text 출력, tool_request → 실행 → 결과 전송
   │  └─ Renderer                ANSI 색, 스피너, diff 표시, 간단한 마크다운
   ├─ tool
   │  ├─ LocalTool               interface (name, readOnly 여부, execute)
   │  ├─ LocalToolRegistry
   │  ├─ ReadFileTool            줄 번호, offset/limit
   │  ├─ ListFilesTool           glob
   │  ├─ GrepTool                정규식, 대소문자, 확장자 필터
   │  ├─ EditFileTool            oldText→newText (정확히 한 번 나와야 함)
   │  ├─ MultiEditTool
   │  ├─ WriteFileTool
   │  └─ BashTool                Linux: bash -c / Windows: powershell 또는 cmd /c, 타임아웃, 출력 제한
   ├─ permission
   │  ├─ PermissionManager       모드: ask(기본) / acceptEdits / readOnly
   │  ├─ PermissionRule          allow·deny 패턴 (예: "bash:mvn *", "edit:src/**")
   │  └─ WorkspaceGuard          작업 디렉터리 밖 쓰기 차단, .git·env.properties 보호
   └─ session
      └─ LocalTranscript         ~/.dstone-ai/sessions/<id>.jsonl (화면 기록, resume 때 보여 주기)
```

저장소 규칙대로 람다·메서드 참조·Lombok 없이 작성합니다.

### 권한 흐름

```
tool_request 도착
  → readOnly Tool(read/list/grep)?         → 바로 실행
  → deny 규칙에 걸림?                        → 거부 결과 전송
  → allow 규칙 또는 이번 세션 '항상 허용'?     → 실행
  → 그 외                                   → 화면에 diff/명령 표시 후 [y] 실행 [a] 항상 [n] 거부(사유 입력)
```

`BashTool`에는 모드와 상관없이 항상 확인을 받는 위험 명령 목록을 둡니다(`rm -rf`, `git push`, `git reset --hard`, `DROP`, `kubectl delete` 등).

## 6. 대화 맥락 관리

- **Redis 대화 메모리**(`MessageChatMemoryAdvisor`)에는 사용자 입력과 최종 답변만 남습니다. 한 turn 안의 Tool 호출과 결과는 다음 turn으로 넘어가지 않습니다. 다음 turn에서 모델이 파일을 다시 읽게 되지만, 토큰이 덜 들고 오래된 파일 내용을 믿는 실수도 줄어서 오히려 낫다고 봅니다. 써 보고 부족하면 turn 끝에 "이번에 한 일 요약"을 메모리에 남기는 방식을 더합니다.
- **환경 정보**(OS, cwd, git 상태, `DSTONE.md`)는 세션 시작 때 한 번 보내고, 서버가 시스템 프롬프트 뒤에 붙입니다.
- `/compact`는 서버가 대화를 요약해서 메모리를 그 요약으로 바꿉니다.

## 7. 단계별 계획

| 단계 | 내용 | 끝났다는 기준 |
|---|---|---|
| **M1** | 모듈 뼈대, REPL, 기존 `/api/ai/chat/stream`으로 스트리밍 대화 | 서버 수정 없이 터미널에서 대화가 됨 |
| **M2** | 서버 `CliController`·`RemoteToolCallback`, 읽기 전용 Tool 3종 | "이 프로젝트 구조 설명해 줘"에 로컬 파일을 읽고 답함 |
| **M3** | Edit/MultiEdit/Write, 권한 관리, diff 표시 | 승인 후 로컬 파일이 실제로 바뀜. 거부하면 모델이 방향을 바꿈 |
| **M4** | Bash Tool, 위험 명령 목록, Windows 셸 대응 | `mvn test` 실행 → 실패 로그를 보고 수정까지 이어짐 |
| **M5** | `/compact`, `--resume`, `-p` 1회 실행, `DSTONE.md`, API Key 인증 켜기 | 장시간 세션과 스크립트 연동 가능 |
| **M6** | KnowledgeTool 연동, Agent 추가(`cli-reviewer` 등), Skill 사용 | "이 테이블을 쓰는 화면 찾아서 고쳐 줘" 같은 작업이 서버 지식과 로컬 수정으로 함께 처리됨 |

## 8. 미리 확인할 위험 요소

1. **스트리밍 중 Tool 콜백이 막히는 문제.** Spring AI가 `stream()` 모드에서 Tool을 실행할 때 어느 스레드에서 돌리는지 M2 첫 작업으로 확인해야 합니다. Reactor 이벤트 스레드에서 실행된다면 Future를 기다리는 동안 다른 요청까지 막힐 수 있습니다. 그런 경우 turn을 별도 executor에서 `call()`로 돌리고 텍스트는 Tool 사이사이에 묶어서 보내는 방식으로 바꿉니다.
2. **인증을 켜면 dstone-boot에도 영향이 갑니다.** boot의 프록시 호출에 키를 넣는 작업을 M5에 같이 묶어야 합니다.
3. **모델 품질.** 코딩 에이전트 용도로는 Claude 계열을 기본으로 두고, `cli-coder.md`의 `model`로 고정하는 편이 안전합니다.
4. **Windows 경로와 인코딩.** 개발자 PC의 기본 콘솔 인코딩(CP949)과 줄바꿈(CRLF)을 Edit Tool에서 그대로 지켜야 합니다.

---

이 내용을 `docs/12.dstone-ai-cli.md` 설계 문서로 저장하고, 이어서 M1(모듈 뼈대와 REPL)부터 시작할까요?

---

## 질문

모델은 폐쇄망에서 사용할 qwen3.8-27b 모델이고 
이 dstone-ai-cli가 개발자가 본인 PC에서 터미널로 본인이 관리중인 어플리케이션을 유지보수(수정/신규기능추가) 하는데 문제가 없을까?
dstone-ai-engine에 올라가 있는 sm-workflow 모델을 활용해서 뭔가 작업이 가능한가?

---

## 답변

결론부터 말씀드리면, **qwen3.8-27b로 Claude Code처럼 자유롭게 루프를 돌리는 방식은 유지보수 용도로 불안정합니다.** 반대로 **sm-workflow를 CLI에서 돌리되 파일 작업만 개발자 PC에서 실행하는 방식은 충분히 해볼 만하고**, 오히려 이 구조에서 CLI의 장점이 가장 잘 드러납니다. 근거는 이 저장소에서 이미 같은 모델로 측정해 둔 기록(`.claude/docs/ai-engine-tuning.md`, `ai-engine-pilot-workflow.md`)입니다.

## 1. qwen3.8-27b로 자유 루프를 돌리면 생기는 문제

측정 기록에서 나온 실제 사례입니다.

| 상황 | 실제로 일어난 일 |
|---|---|
| 영향도 Agent에 파일 Tool만 준 경우 | 38라운드, Tool 93번(중복 24번). `genexon.js`, `BaseVO`까지 파고들다 멈추지 못함 |
| 개발 Agent가 `replaceInFile`로 수정한 경우 | 탭 개수만 다른 같은 `oldText`를 **87번** 다시 보내다 호출 예산 100번을 다 씀 |
| 수정 Agent에 `replaceInFile`을 준 경우 | 6개 파일 중 3~4개가 호출 한도 30번을 모두 소진. 한 번은 출력 16k 토큰을 전부 reasoning에 씀 |
| reasoning 설정 | 기본값이면 간단한 질문에도 41.7초. `low`/`medium`은 이 모델에서 효과가 없음 |
| 비용 | 개발 Agent 호출 한 번에 35라운드, 입력 토큰 184만 |

pilot-workflow의 신규개발 경로가 안정된 것도 **LLM에는 데이터만 만들게 하고, 실제 파일 수정은 Java TOOL step이 하도록** 바꾼 뒤였습니다. Claude Code는 모델이 스스로 계획하고, 조사를 멈출 때를 알고, 실패한 수정을 고쳐 다시 시도하는 능력에 기대는 구조입니다. 27B 모델은 바로 그 부분이 약합니다.

자유 루프로도 쓸 만한 작업은 다음 정도입니다.
- 코드 설명, 위치 찾기, 에러 로그 해석
- 파일 하나 안의 작은 수정
- 기존 기능을 본떠 새 파일 하나 만들기

## 2. 폐쇄망이라서 추가로 확인할 점

1. **서빙 방식:** OpenRouter 대신 vLLM이나 Ollama의 OpenAI 호환 API로 바꾸면 됩니다(`spring.ai.openai.base-url`만 변경). 다만 vLLM은 Tool 호출 파서(`--enable-auto-tool-choice --tool-call-parser ...`)를 켜야 Tool 호출이 됩니다. 이 설정이 맞지 않으면 Tool 호출 JSON이 텍스트로 섞여 나옵니다.
2. **prefix caching:** 라운드마다 대화 전체를 다시 보내는 구조라서, vLLM의 prefix caching을 켜느냐에 따라 속도가 몇 배 차이 납니다.
3. **동시 사용자 용량:** 이게 가장 현실적인 위험입니다. 개발자 한 명의 작업 하나가 입력 토큰 수십만~백만 단위를 씁니다. 개발자 10명이 동시에 쓰면 GPU 한두 장으로는 대기열이 길어집니다. 사용자 수와 GPU 사양으로 미리 계산해 봐야 합니다.
4. **컨텍스트 길이:** 모델의 최대치가 아니라 서빙 설정(`--max-model-len`)과 GPU 메모리가 실제 한도를 정합니다. 32k~64k로 잡히면 긴 세션은 금방 넘칩니다.

## 3. sm-workflow를 CLI에서 쓰는 방법

sm-workflow는 이미 원하는 흐름을 갖추고 있습니다.

```
step01 요청서 읽기 → step02 요구사항 분석 → step04 검토 → step05 승인
→ step06 영향도 분석(knowledge) → step07 승인 → step09 설계 → step10 승인
→ step13 개발 → step14 승인
```

지금은 `${input}`이 **서버 쪽 폴더**입니다(작업 폴더가 `D:/WorkShop/00.SM`). CLI에서 쓰려면 **실행 단위로 "파일 작업을 어디서 할지"를 정하는 기능** 하나만 추가하면 됩니다.

```
개발자 PC (dstone-ai-cli)                     ai-engine
                                              WorkFlowExecutor (sm-workflow 그대로)
> /sm ./work/REQ-0123         ── 실행 요청 ──▶  execution.workspace = client:<sessionId>
                                                │
  readFile, readFileLines,  ◀── tool_request ── ├ TOOL step(readFile)도 원격으로
  searchInFiles,                                ├ Agent의 파일 Tool도 원격으로
  editFileTexts, writeFile  ─── 결과 ─────────▶ ├ knowledgeSearch는 서버에서 실행
  (PC의 소스, PC의 작업 폴더)                     │
  [승인] 02.impact.md 확인? y/n/의견 ◀── 승인 대기 ┘ (기존 APPROVAL step)
```

필요한 변경은 다음과 같습니다.
- **ai-engine:** Workflow 실행에 `workspace` 속성을 추가합니다. 값이 `client:<sessionId>`이면 `ConfigTool.toolCallbacks()`와 TOOL step 실행기가 파일 Tool을 `RemoteToolCallback`으로 감싸서 CLI로 보냅니다. **정의 파일(yml, md)은 고치지 않습니다.** Agent 프롬프트와 측정으로 다듬은 설정이 그대로 살아 있습니다.
- **CLI:** `FileTool`과 같은 이름, 같은 입출력의 로컬 Tool을 구현합니다. `looseMatch`, 범위 없는 읽기 300줄 제한, 실패 문구 형식처럼 서버 Tool이 측정을 거쳐 얻은 동작도 똑같이 맞춰야 합니다. 여기서 앞서 말한 "공용 모듈로 떼어 내기"가 필요해집니다. `FileTool`의 순수 로직을 Spring 없는 `dstone-ai-tools-core` 같은 jar로 옮기고, 서버와 CLI가 같이 쓰는 방식이 맞습니다.
- **승인:** 기존 APPROVAL step과 `/decision` API를 그대로 씁니다. CLI는 문서를 보여 주고 `y / n / 의견`을 받습니다. 의견은 기존처럼 `feedback`으로 다시 들어갑니다.
- **연결이 끊기면:** 기존 일시정지·재개 기능으로 실행을 멈췄다가, `--resume`으로 다시 붙으면 이어서 진행합니다.

### 이 구조에서만 할 수 있는 것: 로컬 빌드로 검증하기

pilot 측정 기록에는 "**not built, not started**"가 반복해서 나옵니다. 서버에는 개발자의 빌드 환경이 없기 때문입니다. 개발자 PC에는 있습니다. step13(개발)이 끝나면 CLI가 프로젝트 설정에 적어 둔 빌드 명령(예: `mvn -q compile`)을 실행하고, 실패하면 오류만 추려서 개발 Agent에 다시 넘기는 루프를 최대 N번 돌 수 있습니다. 이 단계는 Workflow 정의에 범용 셸 Tool step 하나를 추가하는 정도로 됩니다(전용 Tool 클래스는 만들지 않습니다).

## 4. knowledge와 로컬 소스가 어긋나는 문제

영향도 Agent는 `knowledgeSearch`로 위치를 찾습니다. 그런데 knowledge가 분석한 리비전과 개발자 PC의 작업 사본이 다를 수 있습니다. 실제로 리비전 58에 지워진 `sample/imageboard` 파일이 남아 있어서 확인 라운드 3번을 낭비한 기록이 있습니다. 대응 원칙은 이렇게 잡습니다.
- knowledge는 "어디를 볼지"를 찾는 데만 쓰고, "지금 무엇이 있는지"는 항상 로컬 파일에서 읽습니다.
- CLI가 세션 시작 때 로컬 git HEAD와 knowledge의 분석 리비전을 비교해서, 다르면 경고합니다. 증분 분석 요청 기능을 붙일 수도 있습니다.

## 5. 수정한 계획

CLI에 모드 두 가지를 둡니다.

| 모드 | 용도 | 방식 |
|---|---|---|
| **대화 모드** (기본) | 설명, 위치 찾기, 로그 해석, 작은 수정 | 자유 루프. `maxToolCalls`를 낮게 잡고, 재읽기 방지와 범위 읽기 강제 같은 기존 장치를 그대로 씀. 수정은 파일 1~2개까지만 권장 |
| **작업 모드** (`/sm <폴더>`) | 수정, 신규 기능 추가 | sm-workflow를 실행하고 파일 작업만 로컬에서. 단계마다 터미널에서 승인, 마지막에 로컬 빌드 검증 |

단계는 이렇게 바뀝니다.
1. **M1** REPL과 스트리밍 대화
2. **M2** `FileTool` 순수 로직을 공용 jar로 분리 + 원격 Tool 콜백 + 읽기 전용 Tool
3. **M3** Workflow `workspace` 바인딩 + `/sm` 실행 + 터미널 승인 ← **핵심**
4. **M4** 로컬 빌드 검증 루프, 연결 끊김 시 재개
5. **M5** 대화 모드의 수정 Tool과 권한 관리, 인증

**가장 먼저 확인할 것은 폐쇄망 서빙 환경입니다.** vLLM인지 Ollama인지, Tool 호출 파서, 컨텍스트 길이, GPU 대수와 예상 동시 사용자 수를 알려 주시면 용량 계산과 M3 설계를 더 구체적으로 잡겠습니다. 정해진 게 아직 없다면, 지금 OpenRouter의 같은 모델로 M1~M3을 먼저 만들고 폐쇄망 서빙은 설정만 바꾸는 순서로 진행해도 됩니다.

---

## 질문

폐쇄망에서 workstation 으로 돌릴 수 있을만한 모델로 가장 나은게 뭐가 있을까?

---

## 답변

결론부터 말씀드리면, **지금 쓰고 있는 Qwen3.8-27B가 워크스테이션급에서는 여전히 가장 유력한 후보**입니다. 모델을 바꾸기 전에, 앞서 본 불안정한 결과가 모델 탓인지 우리 엔진 설정 탓인지부터 확인해 볼 필요가 있습니다. 조사하다 보니 설정 쪽에 의심 가는 부분이 있었습니다.

## 1. 하드웨어별 후보

| 워크스테이션 | 1순위 | 대안 | 비고 |
|---|---|---|---|
| **GPU 32GB** (RTX 5090 등) | Qwen3.8-27B (4~6bit 양자화) | Devstral Small 2 (24B) | 양자화하면 컨텍스트가 짧아지고 동시 사용자는 1~2명 수준 |
| **GPU 96GB** (RTX PRO 6000급) | Qwen3.8-27B (FP8, 긴 컨텍스트) | Qwen3-Coder-Next (80B MoE, 활성 3B, 4bit 약 46GB) | **가장 추천하는 구성.** 둘 다 올려서 비교해 볼 수 있음 |
| **통합 메모리 128GB** (DGX Spark, Mac Studio 등) | Qwen3-Coder-Next | gpt-oss-120b | 우리 구조는 라운드마다 긴 컨텍스트를 다시 보내므로, 프롬프트 처리(prefill)가 느린 통합 메모리 장비는 체감 속도가 크게 떨어질 수 있음 |
| 그 이상 (H200 여러 장) | GLM-5.x 등 | | 워크스테이션 범위를 벗어남 |

모델별 평가는 다음과 같습니다.
- **Qwen3.8-27B:** Apache-2.0 라이선스의 dense 27B 모델입니다. Claude Code 하네스로 SWE-bench Pro 61.7, Terminal Bench 2.1 73.0을 받았다고 발표했습니다(제조사 발표 수치, 정제된 문제 세트 기준).
- **Qwen3-Coder-Next:** 로컬 에이전트 코딩용으로 가장 많이 추천되는 모델입니다. 활성 파라미터가 3B라 빠르고, 터미널 오류를 보고 고치는 반복 작업에 강하다는 사용자 평이 많습니다. 다만 실망스러웠다는 의견도 있습니다.
- **Devstral Small 2:** VRAM이 적을 때 좋은 선택입니다. 하지만 Tool 사용이 불안정하고 컨텍스트가 길어지면 품질이 떨어진다는 보고가 있습니다.
- **gpt-oss-120b:** 빠르고 똑똑하지만, 긴 에이전트 루프에서는 10~20k 토큰쯤에서 무너진다는 평이 있습니다.
- **한국어:** 우리 Agent 프롬프트와 산출 문서는 모두 한국어입니다. 일반적으로 Qwen 계열이 한국어에 강한 편이고 Devstral이나 gpt-oss는 상대적으로 약하다고 알려져 있습니다. 그래도 실제 sm-workflow로 확인해야 합니다.

## 2. 모델을 바꾸기 전에 확인할 것: 우리 설정이 모델 권장값과 다릅니다

Qwen3.8-27B의 모델 카드 권장값과 지금 ai-engine 설정을 비교해 보면 차이가 큽니다.

| 항목 | 모델 카드 권장 | 현재 dstone 설정 |
|---|---|---|
| temperature (thinking 모드) | **1.0**, top_p 0.95, top_k 20 | Agent 0.2, 기본 0.4 |
| non-thinking 모드 | 0.7, top_p 0.8, **presence_penalty 1.5** | 해당 설정 없음 |
| 이전 reasoning 유지 | `preserve_thinking` **기본 켜짐** | `ReasoningStripAdvisor`가 제거함 (`keep-reasoning: false`) |

특히 마지막 항목이 중요합니다. 한 외부 분석에 따르면 **같은 체크포인트로도 하네스에 따라 DeepSWE 점수가 35.2%에서 46.0%까지 달라졌고**, 높은 점수를 낸 쪽이 "이전 thinking 유지 + Tool 오류 복구"를 쓴 하네스였습니다. 앞서 본 실패 사례(같은 `oldText` 87번 재전송, 조사를 멈추지 못함)는 낮은 temperature에서 흔히 생기는 반복 루프와 모양이 비슷합니다. Qwen이 thinking 모드에서 낮은 temperature를 쓰지 말라고 권하는 이유도 이것입니다.

참고로 reasoning을 지운 데는 이유가 있었습니다. 지난 측정에서 이전 reasoning이 매 라운드 다시 전송되면서 입력이 2k에서 33k 토큰으로 늘었습니다. 다만 폐쇄망에서 vLLM의 prefix caching을 켜면 이 비용이 상당 부분 줄어듭니다. 그때는 다시 판단할 만합니다.

또 하나, 지금까지의 측정은 OpenRouter 무료 엔드포인트에서 한 것이라 어떤 양자화로 서빙됐는지 알 수 없습니다. 로컬 FP8 서빙에서는 결과가 다를 수 있습니다.

## 3. 추천 진행 순서

1. **설정 A/B 측정 (모델 변경 없이)**: 같은 sm-workflow 요청으로 다음 두 가지를 비교합니다. 비교 지표는 라운드 수, Tool 호출 수, 실패 여부입니다.
   - 현재 설정
   - 모델 카드 권장 sampling + `dstone.ai.agent.keep-reasoning=true`

   `keep-reasoning`은 이미 설정 키가 있어서 코드 변경 없이 해볼 수 있습니다. top_k와 presence_penalty는 Spring AI OpenAI 옵션에서 넘길 수 있는지 확인이 필요합니다.
2. **장비 선정**: 96GB GPU 워크스테이션 한 대에 vLLM으로 Qwen3.8-27B FP8을 올리는 구성을 기준으로 잡습니다. Tool 호출 파서는 `qwen3_xml`(최신 vLLM 기준, 비공식 자료)과 `--reasoning-parser qwen3`을 쓰고, prefix caching을 켭니다. 파서 이름은 공식 문서에서 확인되지 않았으니 설치할 vLLM 버전에서 다시 확인해야 합니다.
3. **2순위 비교**: 같은 장비에 Qwen3-Coder-Next를 올려 같은 요청으로 비교합니다. 속도가 중요하거나 동시 사용자가 많다면 이 모델이 유리할 수 있습니다.
4. **동시 사용자 수 확인**: 워크스테이션 한 대로 감당할 수 있는 건 동시 에이전트 세션 몇 개 정도입니다. 개발자가 10명을 넘으면 서버급 GPU를 따로 검토해야 합니다.

주의할 점이 있습니다. 위 벤치마크 수치는 대부분 제조사 발표이거나 블로그·커뮤니티 의견이고, 2026년 9~11월 자료가 섞여 있습니다. 최종 선택은 우리 sm-workflow로 직접 측정해서 정하는 게 맞습니다.

원하시면 1번 A/B 측정부터 진행하겠습니다. 시험용 인스턴스를 다른 포트로 띄우고, 같은 요청을 두 설정으로 돌려서 `AgentCallStatsAdvisor` 로그로 비교하는 방식입니다.

Sources:
- [Best Open-Weight Coding Models to Self-Host in 2026 (digitalapplied)](https://www.digitalapplied.com/blog/best-open-weight-coding-models-self-host-hardware-match-2026)
- [Best open-weight models for coding (Faros)](https://www.faros.ai/blog/open-weight-models)
- [The Best Open-Source LLMs for Agentic Coding in 2026 (MindStudio)](https://www.mindstudio.ai/blog/best-open-source-llms-agentic-coding-2026)
- [Best Local LLM for Coding in 2026 (vdf.ai)](https://vdf.ai/blog/best-local-llm-for-coding/)
- [Qwen3.8-27B: Performance, benchmarks, GPU requirements (Northflank)](https://northflank.com/blog/qwen3-8-27b-performance-benchmarks-gpu-requirements-and-how-to-run-it)
- [Qwen3.8 27B Harness Engineering (regolo.ai)](https://regolo.ai/harness-engineering-for-qwen3-8-27b-a-technical-guide-to-pi-opencode-and-kilo-code/)
- [Qwen3.8-27B: Specs, Benchmarks & Verdict (kingy.ai)](https://kingy.ai/blog/qwen3-8-27b-specs-benchmarks-local-hardware/)
- [SWE-bench Pro Leaderboard Oct 2026 (Morph)](https://www.morphllm.com/swe-bench-pro)
- [Qwen3.8 27B model page (Friendli)](https://friendli.ai/models/Qwen/Qwen3.8-27B)
- [Qwen Fixed Chat Templates (Hugging Face)](https://huggingface.co/brakcity/Qwen-Fixed-Chat-Templates)
- [7 Best Local LLMs You Can Run for Coding (SSOJet)](https://ssojet.com/blog/best-local-llms-coding)
- [Reddit snapshot – local coding model comparisons](https://reddit.sentinel-team.org/posts/1r6jklq/snapshots/2026-02-17T00%3A30%3A13.01368Z)

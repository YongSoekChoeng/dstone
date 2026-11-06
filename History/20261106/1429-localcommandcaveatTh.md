# localcommandcaveatTh

> 생성일시: 2026-11-06 14:29

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

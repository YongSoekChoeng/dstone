# pasted_content_id4bc

> 생성일시: 2026-11-18 15:04

## 질문

<pasted_content id="4bc3">
dstone-boot-ai 의 chat 에서...

/ai/chat 에서 사용하는 에이전트가 sample-general-chat 인걸로 확인이 되.
그런데 채팅에서 호출되는 이 에이전트에 tool 이 설정되어 있다고 할 때 그 tool을 호출 가능하도록 되어있나?
</pasted_content id="4bc3">

---

## 답변

네, 호출할 수 있습니다. 다만 Agent 정의에 적힌 Tool이 모두 붙는 것은 아니고, 채팅 화면에서 고른 Tool만 이번 요청에 붙습니다.

**호출 경로** (`dstone-ai-engine`)

1. **화면** `static/ai/chat/chat.js`: 오른쪽에서 고른 Tool 이름을 `tools: chosenTools`에 담아 `POST /api/ai/chat/stream`으로 보냅니다.
2. **API** `ChatController`
   - `checkTools()`가 고른 이름이 그 Agent에 실제로 붙는 Tool인지 확인합니다. 아니면 400으로 거절합니다.
   - 그다음 `agentExecutor.stream(agent, …, request.tools(), …)`를 부릅니다.
3. **실행** `AgentExecutor.buildSpec()` (`AgentExecutor.java:537`)
   - Agent 정의의 `tools.allowed`에서 caller 화이트리스트를 통과한 Tool만 후보가 됩니다.
   - 후보 가운데 화면에서 고른 것만 남깁니다.
   - 남은 Tool은 `spec.toolCallbacks(...)`로 LLM 호출에 붙습니다. 그래서 LLM이 필요하다고 판단하면 그 Tool을 직접 부릅니다.

**`sample-general-chat`의 경우**

| 요청의 `tools` | 붙는 Tool |
|---|---|
| `null` (API로 직접 부를 때 생략) | Agent 정의대로 모두 붙습니다 (`["*"]` = 등록된 Tool 전부, caller 화이트리스트 적용) |
| 화면에서 몇 개 고름 | 고른 것만 붙습니다 |
| 하나도 고르지 않음 (`[]`) | 붙지 않고 Tool 없이 답합니다 |

`tools.allowed: ["*"]`는 화면의 [+ 찾아서 추가]에 나오는 목록의 상한입니다. 실제로 쓰는 Tool은 화면에서 고릅니다. 화면에서 고르면 Agent가 허용한 범위 안에서 좁혀질 뿐 넓어지지는 않습니다.

Agent 정의 파일에 Skill이나 `subAgents`가 있으면, 화면에서 Tool을 고르지 않아도 그것들은 별도로 붙습니다. `sample-general-chat`에는 둘 다 없습니다.

**한 가지 더**
`conf/definitions/agents/sample/sample-general-chat.md`의 주석과 `description`은 아직 "dstone-boot의 채팅 화면(`ai/service/ChatService.java`)이 이 id를 코드에 박아 두고 부른다"고 되어 있습니다. 오늘 화면을 dstone-ai-engine으로 옮겼으니 이 내용은 맞지 않습니다. 원하시면 주석을 지금 구조에 맞게 고치겠습니다.

---

## 질문

응 주석 고쳐줘.

---

## 답변

The chat screen sets the default via `defaultAgent` in `chat.html`, and `ChatService` no longer exists in dstone-boot either. I'll update the comment to match.

---

## 답변

`sample-general-chat.md`의 주석과 `description`을 지금 구조에 맞게 고쳤습니다. 수정은 이 파일 하나뿐입니다.

**고친 부분** (`conf/definitions/agents/sample/sample-general-chat.md`)
- **id 관련 주석**: 예전에는 "dstone-boot의 `ChatService.java`가 id를 코드에 박아 두고 부른다"고 적혀 있었습니다. 지금은 이 Agent를 기본으로 정하는 곳이 dstone-ai-engine의 `static/ai/chat/chat.html`(`defaultAgent`)이라 그렇게 바꿨습니다. 그래서 id를 바꾸면 `chat.html`의 `defaultAgent`와 화면 안내 표의 Agent 이름도 같이 고쳐야 한다고 적었습니다.
- **`description`**: "dstone-boot 채팅 화면이 사용한다"를 "채팅 화면(`/ai/chat`)이 처음에 고른다"로 바꿨습니다.

**문서**: `docs/09`에도 같은 옛 설명이 있는지 찾아봤는데, "채팅 화면(18장) 기본값"이라고만 되어 있어 고칠 곳이 없었습니다.

**반영과 커밋**
- 정의 파일은 부팅할 때 검사하므로, 실행 중인 인스턴스에서 바뀐 설명을 보려면 재시작하거나 디자이너에서 다시 불러와야 합니다.
- 아직 커밋하지 않았습니다. 필요하면 말씀해 주세요.

---

## 질문

올려줘.

---

## 답변

`ff12e458` 커밋을 `origin/main`에 푸시했습니다. 커밋에는 `sample-general-chat.md` 파일 하나만 들어갔습니다.

---

## 질문

<pasted_content id="4bc3">
그리고 이렇게 수정해줘. 채팅에서 호출할 때
에이전트에 만일 tool이 설정되어 있다면 그 tool은 기본적으로 호출할 수 있고 화면에서 tool을 세팅해서 보내는 경우 
에이전트에 설정되어있지 않은 tool만 추가해서 그 에이전트를 호출하도록 해줘.
</pasted_content id="4bc3">

---

## 답변

먼저 코드를 확인해, 채팅 요청의 `tools`가 Agent 실행과 화면에서 어떻게 쓰이는지 보겠습니다.

---

## 답변

sample-general-chat의 tools는 비우겠습니다. 관련 코드와 테스트, 문서를 확인합니다.

---

## 답변

Now editing AgentExecutor.

---

## 답변

Fixing the mismatched strings in the script and running it again; the file was not written yet.

---

## 답변

Now the controller, DTOs.

---

## 답변

Now the screen: Agent tools as fixed chips, picker lists only addable tools.

---

## 답변

Now the screen text in `chat.html` and the agent definition.

---

## 답변

Now the docs, then build and test.

---

## 답변

Docs done. Building `dstone-common` (CSS changed) and `dstone-ai-engine` with tests.

---

## 답변

컴파일 오류가 하나 있습니다. 그리고 `mvn clean`이 `target`을 지웠으니, 실행 중인 인스턴스가 그 jar를 쓰고 있었는지 확인하겠습니다.

---

## 답변

No dstone-ai-engine process is running locally, so wiping `target` affected nothing. Fixing the remaining call in `callForSchema`.

---

## 답변

Build passes. Running the test suite.

---

## 답변

The 2 failures are in `RequesterContextTest`, which my change doesn't touch. Checking whether they already fail on HEAD.

---

## 답변

The same 2 tests fail on HEAD without my change too, so they're pre-existing. Now I'll start a test copy on a separate port and check the API for real.

---

## 답변

The API lists the new fields as expected. Now checking request handling: an unknown name should get 400, and an added Tool should really be called.

---

## 답변

`tools: []`로 보내도 Agent 정의의 Tool(`getCurrentDateTime`)이 붙어서 호출됐습니다. 이제 시험 인스턴스를 내리고 남은 파일을 정리하겠습니다.

---

## 답변

요청하신 대로 바꿨습니다. 이제 채팅에서는 Agent 정의에 적힌 Tool이 항상 붙고, 화면에서 고른 Tool은 그 위에 더해집니다. 시험용 사본을 8181 포트에 띄워 API로 확인했고, 화면은 브라우저로 열어 보지 않았습니다.

**동작**
```
붙는 Tool = Agent 정의의 Tool(caller 화이트리스트 통과) + 화면에서 더 고른 Tool
```
- Agent 정의의 Tool은 요청으로 뺄 수 없습니다. `tools`가 `null`이나 `[]`이면 Agent 정의 그대로 부릅니다.
- 더 붙일 수 있는 Tool은 caller 화이트리스트를 통과한 Tool 중 그 Agent에 없는 것뿐입니다. 그 밖의 이름을 보내면 400입니다.
- `sample-general-chat`은 고르신 대로 `tools`를 지웠습니다. 기본은 Tool 없이 답하고, 화면에서 고른 Tool만 붙습니다.

**확인한 것** (`sample-tool-demo-agent`는 Agent 정의에 `getCurrentDateTime` 등 Tool이 적힌 기존 예제 Agent입니다)

| 확인 | 결과 |
|---|---|
| `GET /api/ai/chat` | `sample-general-chat`: 기본 0개, 더 붙일 수 있는 것 42개. `sample-knowledge-agent`: 기본 6개, 더 붙일 수 있는 것 36개 |
| 없는 Tool 이름을 보냄 | 400 |
| `sample-general-chat` + `tools: ["getCurrentDateTime"]` | 그 Tool을 실제로 불러서 답함(로그에 기록) |
| `sample-tool-demo-agent` + `tools: []` | 아무것도 고르지 않아도 정의에 적힌 `getCurrentDateTime`을 불러서 답함 |

테스트는 153개 중 2개가 실패했습니다. 둘 다 `RequesterContextTest`이고, 제 변경을 빼고 돌려도 똑같이 실패해서 이번 수정과는 무관합니다. 오늘 앞서 커밋된 로그인 변경과 관련 있어 보입니다.

**바뀐 것**
- **엔진**:
  - `AgentExecutor`: 화면에서 고른 Tool을 Agent 정의의 Tool에 더해 붙입니다. 파라미터 이름도 `toolsOverride`에서 `extraTools`로 바꿨습니다.
  - `ChatController`, `AgentSummary`: Agent 목록 응답에 `addableTools`(더 붙일 수 있는 Tool)를 함께 줍니다.
  - `execution.requiredTools`(꼭 성공해야 하는 Tool) 확인은 이제 예외 없이 모든 Tool에 적용됩니다. 정의의 Tool이 항상 붙기 때문입니다.
- **화면**:
  - Agent 정의의 Tool은 점선 칩(`기본 · 종류`)으로 보이고 ×가 없습니다.
  - [+ 찾아서 추가]에는 더 붙일 수 있는 Tool만 나옵니다.
  - 안내 문구와 안내 표도 새 방식에 맞게 고쳤습니다.
  - 칩 스타일이 `dstone-common`에 있어서 `dstone-common`도 다시 빌드했습니다.
- **문서**: `docs/09`(9절, 12장, 15.1절, 18장)과 `.claude/docs/ai-engine-packages.md`, `.claude/docs/ai-engine-features.md`를 새 방식으로 고쳤습니다.

시험 인스턴스는 내렸고, 아직 커밋하지 않았습니다. 화면을 직접 확인하신 뒤 "올려줘"라고 하시면 커밋하고 푸시하겠습니다.

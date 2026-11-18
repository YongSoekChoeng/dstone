# pasted_content_id4bc

> 생성일시: 2026-11-18 14:50

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

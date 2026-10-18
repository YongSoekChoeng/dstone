# dstoneaiengine_의_res

> 생성일시: 2026-10-18 17:49

## 질문

dstone-ai-engine 의 resources/definitions/agents/*.md 에서...
--- 사이에 있는 내용을 실제로 사용하는가?

---

## 답변

네, 실제로 씁니다. `---` 사이 머리말이 Agent 설정이고, 그 아래 본문이 시스템 프롬프트입니다.

**읽는 과정**
- `YamlDefinitionLoader.loadAgents()`가 `agents/**/*.md` 파일을 찾고, `readAgentFile()`(`common/loader/YamlDefinitionLoader.java:255`)에서 파일을 나눕니다.
  - 첫 `---`부터 다음 `---`까지는 YAML로 파싱합니다.
  - 그 아래 본문은 `prompt`에 넣습니다.
- 파싱한 결과는 Jackson으로 `AgentDefinition` record에 바인딩됩니다. 모르는 키가 있으면 기동이 실패합니다.
- 첫 줄이 `---`가 아니거나 닫는 `---`가 없어도 기동이 실패합니다.

**항목별로 실제 하는 일**

| 항목 | 쓰이는 곳 | 효과 |
|---|---|---|
| `id` | 레지스트리 키, Workflow의 `agent:` 참조 | 실제 동작 |
| `input` / `output` | `AgentExecutor`의 입력·출력 검증, `SchemaOutputConverter`, Workflow의 `with`·경로 검증 | 실제 동작 |
| `model.name/temperature/reasoning` | `AgentExecutor.buildSpec()` → `ConfigChatClient.requestOptions()` | 실제 동작 |
| `tools.allowed` | LLM에 보여 줄 Tool 목록 | 실제 동작 |
| `subAgents` | `SubAgentToolCallback`으로 감쌈 | 실제 동작 |
| `context.sources: [retrievedDocuments]`, `context.retrieval.*` | RAG Advisor를 붙임 | 실제 동작 |
| `execution.*` | 재시도, timeout, Tool 호출 한도 | 실제 동작 |
| `allowedCallers` | caller 허용 목록 검사 | 실제 동작 |
| `description` | Sub Agent로 쓸 때 Tool 설명이 되고, `GET /api/ai/chat` 목록에도 나옴 | Sub Agent일 때만 동작에 영향 |
| `version` | 기동 시 비어 있는지만 검사하고, 목록 API에 보여 줌 | 동작에는 영향 없음. 정의를 버전에 고정하는 기능이 없음 |
| `role` | `GET /api/ai/chat` 응답(`AgentSummary`)에 보여 주기만 함 | 동작에는 영향 없음 |
| `context.sources`의 `workflowInput` | 허용값 검사만 함 | 효과 없음. `retrievedDocuments`가 있는지만 봄 |

정리하면 머리말은 대부분 실제 실행에 쓰입니다. 다만 `role`, `version`, `context.sources`의 `workflowInput`은 검사나 목록 표시에만 쓰이고 실행 결과는 바꾸지 않습니다. `description`도 그 Agent를 Sub Agent로 쓸 때만 동작에 영향을 줍니다.

참고로 프롬프트 본문의 `{role}` 같은 `{변수}`는 머리말의 `role` 필드와 상관없습니다. Agent 입력에 있는 같은 이름의 필드 값으로 채워집니다.

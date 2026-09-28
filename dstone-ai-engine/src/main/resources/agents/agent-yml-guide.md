

### 1. Agent 항목

`agent:` 아래에 적는다(`common.definition.agent.AgentDefinition`).

| 항목 | 필수 | 타입 | 설명 |
|---|---|---|---|
| `id` | ✅ | 문자열 | Agent 이름. step의 `ref`와 채팅 API의 `agent`가 이 값을 쓴다. 중복 불가 |
| `prompt` | ✅ | 문자열(여러 줄) | 시스템 프롬프트 원문. `{변수}`는 호출 시 채워진다(Workflow에서는 Workflow `input`이 object일 때 그 필드들, 채팅에서는 요청의 `variables`) |
| `description` | | 문자열 | 사람이 읽는 설명. `GET /api/ai/chat` 목록에 쓰인다 |
| `input` | | 스키마 | 이 Agent가 **받는** 값의 모양(입력 계약). 비우면 `string` |
| `output` | | 스키마 | 이 Agent가 **돌려주는** 값의 모양(출력 계약). 비우면 `string`. SUPERVISOR/ROUTER용 Agent는 적지 않는다 |
| `model` | | 문자열 | 이 Agent만 쓸 모델 이름(같은 provider 안에서). 비우면 공통 기본값. 다른 provider의 모델 이름이면 기동이 아니라 첫 호출 때 실패한다 |
| `toolsEnabled` | | boolean | LLM이 Tool을 스스로 골라 부를 수 있는지. 기본 `false` |
| `ragEnabled` | | boolean | 적재된 문서를 검색해서 답변에 참고하는지. 기본 `false` |
| `ragTopK` | | 정수 | RAG 검색 결과 최대 개수. 비우면 `dstone.ai.rag.retrieval.top-k` |
| `ragSimilarityThreshold` | | 실수 | RAG 최소 유사도. 비우면 `dstone.ai.rag.retrieval.similarity-threshold` |
| `ragAllowEmptyContext` | | boolean | 검색 결과가 없을 때도 답을 시도할지. 비우면 `true`. `false`면 "모른다"고 답하도록 강제 |
| `allowedCallers` | | 문자열 리스트 | 호출을 허락할 caller 목록. 비우면 누구나(⚠️ Workflow와 같은 주의사항) |

**용도별 작성 요령**

| 용도 | `input` / `output` | prompt에 꼭 넣을 것 |
|---|---|---|
| 자유 텍스트를 받아 자유 텍스트로 답 | 비워 둠(둘 다 string) | 역할과 규칙, 결과물의 형식 |
| 다음 step이 값을 콕 집어 써야 하는 답 | `output: {schema: {type: object, ...}}` | 역할과 규칙. JSON 형식 지시는 엔진이 자동으로 붙이므로 적지 않는다. 필드 뜻은 스키마 `description`에 |
| 여러 값을 나눠 받아야 하는 입력 | `input: {schema: {type: object, ...}}` | 입력 JSON의 각 필드가 무엇인지(중괄호 없이 말로) |
| SUPERVISOR | `output`은 적지 않음(엔진이 `{pass, reason}`) | 무엇을 기준으로 pass/fail을 판정할지, reason에 무엇을 적을지 |
| ROUTER | `output`은 적지 않음(엔진이 `{route, reason}`) | route로 쓸 수 있는 이름 목록과 각각의 기준(Workflow의 `routes` 키와 정확히 같게) |

> 아래 1.1~1.12는 항목마다 **모양 → 동작 → 검사 시점 → 경우별 결과** 순서로 자세히 적었다.
> "기동 실패"는 엔진이 켜질 때 예외로 멈춘다는 뜻이다(`YamlDefinitionLoader`/`AgentRegistry`/`WorkFlowRegistry`). 모르는 키(오타)도 기동 실패다.

**Agent가 불리는 두 경로** — 항목의 의미는 같지만 몇 가지가 다르다. 각 항목 설명에서 이 차이를 함께 적었다.

| | 채팅 API(`POST /api/ai/chat[/stream]`) | Workflow step(AGENT/SUPERVISOR/ROUTER의 `ref`) |
|---|---|---|
| Agent 지정 | 요청의 `agent` | step의 `ref` |
| Agent에게 넣는 값 | 요청의 `input`(Agent `input` 모양) | step의 `input`을 채운 값(Agent `input` 모양) |
| 돌려받는 값 | 응답의 `output`(Agent `output` 모양) | `steps.<id>.output`(AGENT는 Agent `output` 모양, SUPERVISOR/ROUTER는 엔진이 정한 모양) |
| prompt `{변수}` 값 | 요청의 `variables` | Workflow `input`이 object면 그 필드들(글자면 없음) |
| `ragEnabled`/`toolsEnabled`/`model`을 요청마다 바꾸기 | 가능(요청의 같은 이름 값이 있으면 그 값을 쓴다) | 불가. 항상 Agent 정의값 |
| 대화 기억(sessionId) | 요청의 `sessionId`(비우면 새로 발급) | 기본은 기억 없음. step에 `memory: true`를 적으면 그 step만 `sessionId:stepId` 대화방에서 이전 시도를 기억한다. **step끼리는 공유하지 않는다** |
| 스트리밍(`/stream`) | `output`이 string인 Agent만 | — |
| 계약을 어기면 | input이 틀리면 400, LLM 답이 틀리면 오류 | step 실패 → `onFailure`([workflow-yml-guide.md 3절](../workflows/workflow-yml-guide.md#3-step-항목)) |

#### 1.1 `id`

```yaml
agent:
  id: sample-structured-extract-agent
```

- Agent를 부르는 이름이다. 채팅 요청의 `agent`, Workflow step의 `ref`가 이 값을 쓴다.
- 파일 경로나 파일 이름과는 관계없다. 파일 이름은 `id`와 맞추는 것을 권장한다.
- 대소문자를 구분한다.

| 경우 | 결과 |
|---|---|
| 생략하거나 빈 문자열 | 기동 실패 `agents/*.yml 항목은 id와 prompt가 모두 있어야 합니다` |
| 다른 파일과 `id`가 같음 | 기동 실패 `agent id가 중복 등록되었습니다` |
| 등록되지 않은 `id`로 호출 | 채팅은 거절 `등록되지 않은 agent입니다`. Workflow step의 `ref`면 **기동 실패** `agents/*.yml에 'x' Agent가 없습니다` |
| `id`를 바꿈 | 이 id를 `ref`로 쓰는 Workflow와 코드(예: dstone-boot 채팅 화면의 `sample-general-chat`)를 함께 고쳐야 한다 |

#### 1.2 `prompt`

```yaml
  prompt: |
    당신은 {role} 역할을 맡은 AI 어시스턴트입니다.
    답변은 항상 한국어로, 간결하게 합니다.
```

- 이 Agent의 **시스템 프롬프트 원문**이다. 호출할 때마다 system 메시지로 들어간다(`AgentExecutor.buildSpec()`). 별도의 프롬프트 저장소는 없다.
- 여러 줄은 `|`로 적는다(줄바꿈이 그대로 유지된다).
- 역할은 이렇게 나눈다.
  - prompt: "이 Agent가 누구이고 어떤 규칙을 지키는가"(바뀌지 않는 것)
  - input: "이번에 처리할 데이터"(Workflow에서는 step `input`, 채팅에서는 요청 `input`) → user 메시지

**`{변수}` 채우기** — prompt 안의 `{이름}` 자리는 **호출할 때마다** 값으로 바뀐다. Spring AI `PromptTemplate`(StringTemplate 문법)이 채운다.

Java로 비유하면 prompt는 `String.format()`의 틀이고, `{변수}`는 그 틀의 빈칸이다.

```
prompt 틀          "당신은 {domain} 분야 전문 번역가입니다. 결과는 {language}로만 작성합니다."
채울 값(Map)        { domain: "금융", language: "English" }
────────────────────────────────────────────────────────────────────────────────
system 메시지       "당신은 금융 분야 전문 번역가입니다. 결과는 English로만 작성합니다."
```

**중괄호 세 가지를 헷갈리지 않는다**

| 모양 | 어디에 쓰나 | 언제 채우나 | 무엇으로 채우나 |
|---|---|---|---|
| `${APP_HOME}` | 모든 YAML의 문자열 | **엔진 기동 시** 한 번(`YamlDefinitionLoader`) | `conf/env{-profile}.properties`(System 프로퍼티), 없으면 OS 환경변수 |
| `{domain}` | Agent `prompt` | **Agent를 호출할 때마다**(`AgentExecutor.buildSpec()`) | 채팅: 요청 `variables` / Workflow: Workflow `input`(object)의 필드들 |
| `{{input.x}}` | Workflow의 step `input`/`forEach`/`output.value` | **step을 실행할 때마다**(`WorkFlowExecutor`) | 실행 컨텍스트(`input`/`steps`) |

- Agent prompt에 `{{input.x}}`를 적으면 Workflow 값으로 채워지지 **않는다**. prompt에서는 `{x}`로 쓴다.

##### 1.2.1 한눈에 보기 — 값이 흘러가는 길

```
                         ┌──────────────────────────────────────┐
                         │ agents/doc-translator-agent.yml      │
                         │   prompt: "... {domain} ... {language}"│
                         │   input / output: (계약)               │
                         └──────────────────┬───────────────────┘
                   ═══════ 엔진 기동 시 (한 번) ═══════
                         ① YAML 글자 → Map        (SnakeYAML, ${VAR}만 치환)
                         ② Map → AgentDefinition  (Jackson, 스키마 축약형 펼침, prompt의 {domain}은 글자 그대로 보관)
                         ③ AgentRegistry에 id로 등록 (input/output 스키마 검사, {변수}는 검사하지 않음)
                                            │
             ┌──────────────────────────────┴──────────────────────────────┐
   경로 A: 채팅 API                                            경로 B: Workflow step
   POST /api/ai/chat                                          type: AGENT, ref: doc-translator-agent
   { agent, input, variables }                                요청 { input } → context.input
             │                                                              │
   ④ variables       ─────▶ {변수} 값                    ④' context.input(object)의 필드들 ─▶ {변수} 값
   ⑤ input           ─────▶ user 메시지                  ⑤' step input 템플릿을 채운 값   ─▶ user 메시지
             └──────────────────────────────┬──────────────────────────────┘
                         ⑥ AgentExecutor
                            input을 Agent input 스키마로 검사 → 글자면 그대로, 아니면 JSON 글자로
                            PromptTemplate(prompt).render(값) → system 메시지
                            + 대화 기억(채팅: sessionId / step: memory: true일 때만) + RAG/Tool/model + (output이 string이 아니면) 응답 형식 지시
                                            ▼
                         ⑦ LLM 호출 → 답을 Agent output 스키마로 검사 → 돌려줌
```

##### 1.2.2 샘플 Agent

(설명을 위해 만든 예시라 실제 파일은 없다. 실제 파일로는 `sample/sample-general-chat.yml`이 `{role}` 변수 하나를 쓴다.)

```yaml
# agents/doc-translator-agent.yml (설명용 예시)
agent:
  id: doc-translator-agent
  description: 분야와 대상 언어를 받아 문서를 번역한다
  prompt: |
    당신은 {domain} 분야 전문 번역가입니다.
    결과는 {language}로만 작성하고, 전문 용어는 원문을 괄호에 함께 적습니다.
  toolsEnabled: false
  ragEnabled: false
  # input/output을 비워 두었으므로 둘 다 string
```

이 Agent를 부르는 Workflow:

```yaml
# workflows/doc-translate.yml (설명용 예시)
workflow:
  id: doc-translate
  input:
    schema:
      type: object
      properties:
        text: string              # 번역할 글
        domain: string            # Agent prompt의 {domain}을 채울 값
        language: string          # Agent prompt의 {language}를 채울 값
      required: [text, domain, language]
  output:
    value: "{{steps.translate.output}}"
  steps:
    - id: translate
      type: AGENT
      ref: doc-translator-agent
      input: |
        아래 글을 번역하세요.
        {{input.text}}
```

##### 1.2.3 단계별로 따라가기

**① YAML 글자 → Map** — SnakeYAML이 읽는다. 이때 바뀌는 것은 `${VAR}`뿐이다. `{domain}`은 **글자 그대로** 남는다.

**② Map → `AgentDefinition`** — Jackson `convertValue()`. 적지 않은 항목은 `null`(boolean은 `false`)이 된다.
`input`/`output`은 `SchemaDefinition`으로 읽히면서 축약형이 펼쳐진다. 비워 두면 `inputSchema()`/`outputSchema()`가 `{type: string}`을 돌려준다.

```
AgentDefinition(
  id                    = "doc-translator-agent",
  prompt                = "당신은 {domain} 분야 전문 번역가입니다.\n...",   ← 아직 틀(template) 상태
  input                 = null      → inputSchema()  = {type: string}
  output                = null      → outputSchema() = {type: string}
  model                 = null      → 호출 때 공통 기본 모델
  toolsEnabled          = false,
  ragEnabled            = false,
  ...
  allowedCallers        = null      → 누구나
)
```

**③ 등록** — `AgentRegistry`가 `id`로 등록하고, `input`/`output` 스키마가 올바른 JSON Schema인지 검사한다.
`{domain}`에 값이 들어올지는 **검사하지 않는다**. Agent는 채팅에서도, 여러 Workflow에서도 불릴 수 있어서 기동 시점에는 어떤 값이 올지 알 수 없기 때문이다.

**④⑤ 경로 A — 채팅 API**

```json
POST /api/ai/chat
{
  "agent": "doc-translator-agent",
  "input": "Interest rates rose sharply.",
  "variables": { "domain": "금융", "language": "한국어" }
}
```

```
ChatRequest ──▶ ChatController ──▶ AgentExecutor.call(
                                      agent     = doc-translator-agent,
                                      variables = { domain: "금융", language: "한국어" },   ← request.variables 그대로
                                      input     = "Interest rates rose sharply."          ← request.input
                                   )
```

**④'⑤' 경로 B — Workflow step**

```json
POST /api/ai/workflow/doc-translate/execute
{
  "input": { "text": "Interest rates rose sharply.", "domain": "금융", "language": "한국어" }
}
```

```
context.input = { text: "Interest rates rose sharply.", domain: "금융", language: "한국어" }
        │
        ├─▶ step input 템플릿 채우기 ──▶ "아래 글을 번역하세요.\nInterest rates rose sharply."   → user 메시지
        │
        └─▶ WorkFlowContext.promptVariables(context) = context.input(object) ─────────────────▶ {변수} 값
```

- Workflow에서 `{변수}` 값은 **Workflow input이 object일 때 그 필드들**이다. input이 글자면 `{변수}` 값은 없다.
- step `input`이 무엇이든 `{변수}` 값은 **늘 같다**(Workflow input은 실행 중에 바뀌지 않는다). 앞 step의 결과를 `{변수}`로 받을 방법은 없다.
  앞 step 결과는 step `input`의 `{{steps.<id>.output}}`으로 user 메시지에 넣는다.

**⑥ 메시지 조립** — `AgentExecutor`가 input을 검사하고, prompt 틀을 채우고, 나머지 설정을 붙인다.

**⑦ LLM에게 실제로 가는 메시지** (경로 B 기준)

```
┌─ system ─────────────────────────────────────────────────────┐
│ 당신은 금융 분야 전문 번역가입니다.                                │  ← prompt + {변수}
│ 결과는 한국어로만 작성하고, 전문 용어는 원문을 괄호에 함께 적습니다.  │
└──────────────────────────────────────────────────────────────┘
┌─ (대화방의 이전 대화 — MessageChatMemoryAdvisor) ────────────────┐
│ user: ... / assistant: ...                                   │  ← 채팅은 같은 sessionId, Workflow는 memory: true인 step 자신의 이전 대화만
└──────────────────────────────────────────────────────────────┘
┌─ user ───────────────────────────────────────────────────────┐
│ 아래 글을 번역하세요.                                           │  ← input(글자면 그대로, 객체면 JSON 글자)
│ Interest rates rose sharply.                                 │
│ (ragEnabled면)  [참고자료] <검색된 문서 조각들>                   │  ← 1.8
│ (output이 string이 아니거나 SUPERVISOR/ROUTER면) JSON 형식 지시문 │  ← 엔진이 자동으로 붙임(1.4)
└──────────────────────────────────────────────────────────────┘
  + toolsEnabled면 후보 Tool 목록, model이 있으면 그 모델 이름(ChatOptions)
```

##### 1.2.4 같은 Agent, 두 경로 비교

| | 경로 A: 채팅 | 경로 B: Workflow step |
|---|---|---|
| `{domain}`, `{language}` | 요청 `variables.domain`, `.language` | Workflow `input.domain`, `.language`(input이 object일 때) |
| user 메시지 | 요청 `input`(글자면 그대로) | step `input`을 채운 값(글자면 그대로) |
| 값이 빠지면 | 채팅 요청 오류 | LLM 호출 예외라 FAILED([workflow-yml-guide.md 3절](../workflows/workflow-yml-guide.md#3-step-항목)) |
| 미리 막는 방법 | 호출하는 쪽이 항상 보낸다 | Workflow `input` 스키마의 `required`에 넣으면 값이 없을 때 실행 전에 **400**으로 막힌다([workflow-yml-guide.md 2.5](../workflows/workflow-yml-guide.md#25-input)) |

> Workflow에서 Agent의 `{변수}`를 쓴다면, 그 이름을 Workflow `input` 스키마에 **같은 이름으로, required로 선언**해 두는 것이 안전하다.

##### 1.2.5 자주 쓰는 패턴

**A. 변수 없음 — 가장 안전**

```yaml
  prompt: |
    당신은 SQL을 PostgreSQL로 변환하는 전문가입니다.
```

어느 경로에서든 `variables` 없이 항상 동작한다. 처리할 데이터는 모두 input으로 넘긴다.

**B. 역할/말투를 바꿔 끼우기**

```yaml
  prompt: |
    당신은 {role} 역할을 맡은 AI 어시스턴트입니다.     # sample-general-chat.yml
```

```json
채팅:     { "agent": "sample-general-chat", "input": "안녕", "variables": { "role": "친절한 상담원" } }
Workflow: { "input": { "message": "안녕", "role": "친절한 상담원" } }   ← Workflow input 스키마에 role 필드를 선언
```

**C. JSON 예시를 보여 주고 싶을 때 — 중괄호 대신 말로**

```yaml
  # ❌ 중괄호가 변수로 읽혀 호출이 실패한다(기동은 통과하므로 첫 호출 때 알게 된다)
  prompt: |
    {"sql": "...", "tables": [...]} 모양으로 답하세요.

  # ✅ 형식은 output 스키마에 맡기고, prompt에는 말로 설명한다
  prompt: |
    변환한 SQL과, 그 SQL이 쓰는 테이블 이름 목록을 답하세요.
  output:
    schema:
      type: object
      properties:
        sql: string
        tables: list<string>
      required: [sql, tables]
```

input이 object일 때도 마찬가지로 "입력으로 sql(검증에 실패한 SQL)과 error(실패 사유) 두 필드를 가진 JSON을 받습니다"처럼 **중괄호 없이** 설명한다(`sample-fix-agent.yml`).

##### 1.2.6 경우별 결과

| 경우 | 결과 |
|---|---|
| `{변수}`가 없음 | 원문 그대로. variables를 보내지 않아도 항상 동작 |
| `{role}`이 있는데 호출에 `role` 값이 없음 | **예외** `Not all variables were replaced in the template. Missing variable names are: [role]`(Spring AI 기본 검증 모드가 THROW). 채팅은 오류, Workflow는 FAILED |
| Workflow input이 글자인데 prompt에 `{x}` | 위와 같은 예외(글자 input은 `{변수}` 값을 주지 않는다) |
| 쓰지 않는 값이 더 들어옴 | 무시 |
| JSON 예시처럼 **중괄호 글자**를 그대로 적음(`{"a": 1}`) | 호출할 때 `The template string is not valid.` 예외. prompt에는 중괄호를 쓰지 않고 말로 설명한다 |
| Workflow 문법 `{{input.x}}`를 prompt에 적음 | 템플릿 문법 오류로 호출이 실패하거나 엉뚱한 글자가 들어간다. prompt에서는 `{x}`로 쓴다 |
| 변수 이름에 `-`, 공백, 한글 | 변수 이름으로 읽히지 않을 수 있다. 영문, 숫자, `_`만 쓴다 |
| `{options.strict}`(맵 값의 안쪽 키) | 동작한다. `options = {strict: true}`이면 `true`가 들어간다 |
| 값이 리스트 | 항목이 **구분자 없이** 붙는다. `["a","b"]` → `ab` |
| 값이 맵 | **키 이름만** 붙는다. `{strict: true}` → `strict` |
| ↳ 리스트/맵을 LLM에게 보여 줘야 할 때 | prompt `{x}` 대신 input으로 넘긴다(JSON 글자로 들어간다) |
| 생략하거나 빈 문자열 | 기동 실패 `id와 prompt가 모두 있어야 합니다` |

#### 1.3 `input` — 받는 값의 모양

```yaml
  input: string                     # 기본값과 같음(생략해도 됨)

  input:
    schema:
      type: object
      properties:
        sql: { type: string, description: 검증에 실패한 SQL }
        error: { type: string, description: 검증에 실패한 사유 }
      required: [sql, error]
```

- 이 Agent가 무엇을 받는지 선언한다. 모양은 [workflow-yml-guide.md 4절](../workflows/workflow-yml-guide.md#4-스키마-json-schema)의 JSON Schema(축약형 포함)로 적는다.
- **계약은 Agent 한 곳에만 있다.** Agent를 부르는 쪽(Workflow step, 채팅 API)은 이 모양에 맞춰 값을 넣는다.
- 동작(`AgentExecutor.toUserMessage()`):
  1. 넣은 값을 이 스키마로 검사한다. 틀리면 `AgentContractException`(채팅은 400, Workflow는 step 실패 → `onFailure`).
  2. 글자면 그대로, 그 밖의 값(맵, 리스트 등)은 JSON 글자로 바꿔 **user 메시지**로 보낸다.
- Workflow에서는 input 모양에 따라 step `input`을 적는 방법이 달라진다 — string이면 글자 템플릿, object면 맵(필드마다 템플릿).
  엔진이 켜질 때 step `input`의 모양과 이름을 이 스키마와 대조한다([workflow-yml-guide.md 3.4](../workflows/workflow-yml-guide.md#34-input)).

| 경우 | 결과 |
|---|---|
| 생략 | `string`. 비어 있지 않은 글자면 무엇이든 통과 |
| 스키마 오타(`type: strin`) | 기동 실패 `agent[x]의 input.schema가 올바른 JSON Schema가 아닙니다: [...]` |
| 축약형 오타(`input: strng`) | 기동 실패 `알 수 없는 타입 이름입니다: strng` |
| `schema:` 없이 바로 적음(`input: {type: object, ...}`) | 기동 실패 `'type'는 이 자리(SchemaDefinition)에서 쓸 수 없는 키입니다 ... input/output 아래에는 schema: 하나만 적고...` |
| object Agent를 채팅 화면에서 부름 | dstone-boot 채팅 화면은 Agent 목록의 input 스키마를 보고 입력창 내용을 JSON으로 읽어 보낸다 |
| 넣은 값이 비어 있음(null, `""`) | `input이 비어 있습니다` 오류 |

#### 1.4 `output` — 돌려주는 값의 모양

```yaml
  output: string                    # 기본값과 같음(생략해도 됨) → LLM 답 원문 그대로

  output:
    schema:
      type: object
      properties:
        sql: { type: string, description: 입력 문장에서 그대로 뽑아낸 SQL 문 하나 }
      required: [sql]
```

- 이 Agent가 무엇을 돌려주는지 선언한다. **프롬프트와 출력 모양은 한 몸**이라 같은 파일에 둔다(예: "응답 전체가 곧 설계서"라는 prompt에는 `string`이 맞다).
- 동작(`AgentExecutor.ask()`):
  - **string**: LLM 답 원문을 그대로 돌려준다(JSON으로 감싸지 않는다 — 긴 문서를 JSON 글자에 담으면 이스케이프가 깨지기 쉽다).
  - **그 밖의 타입(object, array, number, ...)**: "이 JSON Schema를 지키는 JSON 값 하나로만 답하라"는 지시를 user 메시지 끝에 붙이고(`SchemaOutputConverter`),
    답을 JSON으로 읽어(코드펜스는 벗겨 냄, 뒤에 글자가 더 붙으면 실패) 스키마로 검사한다. 통과한 값을 돌려준다.
- 돌려준 값은 채팅 응답의 `output`, Workflow의 `steps.<id>.output`이 된다. object면 `{{steps.<id>.output.sql}}`처럼 필드를 꺼낸다.
  엔진이 켜질 때 이런 참조 경로를 이 스키마와 대조한다.
- provider 고유의 structured output 기능은 쓰지 않는다. 그래서 어느 provider든 똑같이 동작하지만, 모양을 100% 보장하지는 않으므로 항상 검사한다.
- ⚠️ **SUPERVISOR/ROUTER가 부르는 Agent는 `output`을 적지 않는다.** 답의 모양은 엔진이 `{pass, reason}`/`{route, reason}`으로 정한다(`common.schema.StepOutputSchemas`).
  적어 두면 그 Agent를 SUPERVISOR/ROUTER step에서 부르는 Workflow가 기동 실패한다.

| 경우 | 결과 |
|---|---|
| 생략 | `string`. LLM 답 원문 |
| `required`를 빠뜨림 | 모든 필드가 선택이라 LLM이 `{}`로 답해도 통과한다. 꼭 필요한 필드는 `required`에 넣는다 |
| LLM이 JSON이 아닌 글로 답함 | `LLM 응답이 JSON이 아닙니다` → 채팅은 오류, Workflow는 step 실패 → `onFailure` |
| 필드가 빠졌거나 타입이 다름 | `LLM 응답이 정해진 output 모양을 지키지 않았습니다: [/: 필수 속성 'sql'을(를) 찾을 수 없습니다.]` → 위와 같음 |
| LLM이 선언하지 않은 필드를 더 넣음 | 허용(`additionalProperties: false`를 적으면 실패) |
| object output Agent를 `/api/ai/chat/stream`으로 부름 | 400(조각난 글자는 모양을 검사할 수 없다). `POST /api/ai/chat`을 쓴다 |

#### 1.5 `description`

```yaml
  description: 일반 대화용 기본 Agent. dstone-boot 채팅 화면이 사용한다.
```

- 사람이 읽는 설명이다. 호출 동작에는 **영향이 없다**(LLM에게 전달되지 않는다).
- `GET /api/ai/chat` 목록(`AgentSummary{id, description, input, output}`)에 나가고, dstone-boot 채팅 화면의 Agent 드롭다운에 보인다.
- 생략하면 목록에 빈 값으로 나갈 뿐 오류는 없다.

#### 1.6 `model`

```yaml
  model: claude-haiku-4-5-20251001
```

- 이 Agent만 쓸 모델 이름이다. provider(`spring.ai.model.chat`, 기본 anthropic)는 모든 Agent가 **하나를 공유**하고, 그 안에서 모델 이름만 바꾼다.
- 우선순위(`AgentExecutor.buildSpec()`):
  1. 채팅 요청의 `model`(채팅에서만)
  2. Agent의 `model`
  3. `spring.ai.{provider}.chat.options.model`(공통 기본값)
- 호출마다 `ChatOptions.model`로 적용된다. 채팅 응답의 `model` 필드에 위 우선순위로 정한 이름이 표시된다.

| 경우 | 결과 |
|---|---|
| 생략 또는 빈 문자열 | 공통 기본 모델 |
| 지금 provider의 모델 이름 | 그 모델로 호출 |
| 다른 provider의 모델 이름(예: anthropic인데 `gpt-4o`) | **기동은 통과**하고, 그 Agent를 처음 부를 때 provider API 오류로 실패한다 |
| 오타 등 존재하지 않는 모델 이름 | 위와 같다(호출 때 실패) |

#### 1.7 `toolsEnabled`

```yaml
  toolsEnabled: true      # 기본 false
```

- `true`면 LLM이 Tool을 **스스로 골라** 부를 수 있다(Spring AI의 tool-calling 루프). 호출 한 번 안에서 여러 번 부를 수도 있다.
- 후보 Tool = `@AiTool` 빈의 모든 `@Tool` 메서드 + 연결된 MCP 서버의 Tool 중에서 **caller 화이트리스트**
  (`dstone.ai.tool.allowed-by-caller`)를 통과한 것 전부다. Agent마다 Tool을 골라 붙이는 설정은 없다.
  - caller가 null(인증 꺼짐)이거나 화이트리스트에 그 caller가 없으면 → 모든 Tool이 후보
  - 그 caller의 `tools`가 `[]` → 후보 0개
- shell/python/http Tool은 후보에 들어가도 각자의 화이트리스트(`dstone.ai.tool.{shell,python,http}.allowed-*`)에 없는 명령/스크립트/호스트는 거부한다.
- Tool에는 `toolContext`로 caller가 전달된다(`RagSearchTool`처럼 tenant별로 범위를 좁히는 Tool이 쓴다).

| 경우 | 결과 |
|---|---|
| 생략 | `false`. Tool 없이 LLM만 답한다 |
| `false`인데 prompt에 "Tool을 호출하라"고 적음 | Tool이 붙지 않으므로 LLM이 지어내서 답할 수 있다 |
| 채팅 요청에 `toolsEnabled` 값을 보냄 | 이번 요청만 그 값으로 바뀐다(Workflow에서는 불가) |
| Workflow의 **TOOL step** | 이 값과 **관계없다**. TOOL step은 LLM 없이 `ref`의 Tool을 직접 부른다 |
| `true` + object `output`/SUPERVISOR/ROUTER | 함께 쓸 수 있다. Tool로 사실을 확인한 뒤 정해진 모양으로 답한다 |
| `"yes"`처럼 boolean이 아닌 값 | 기동 실패(YAML 바인딩 오류) |

#### 1.8 `ragEnabled`

```yaml
  ragEnabled: true        # 기본 false
```

- `true`면 호출할 때마다 사용자 메시지로 적재된 문서를 검색하고, 찾은 조각을 사용자 메시지 뒤에 붙인다(`RagRetrievalChain.buildAdvisor()`).
  ```
  <원래 사용자 메시지>

  [참고자료]
  <검색된 문서 조각들>
  ```
  prompt에는 "[참고자료]가 주어지면 그것을 근거로 답하라"처럼 이 모양을 전제로 적는다.
- 검색은 **caller의 문서만** 대상으로 한다(`tenant` 메타데이터 필터). 인증이 꺼져 caller가 null이면 필터 없이 전체 문서를 검색한다.
- 문서 적재는 `POST /api/ai/embed/documents`로 따로 한다. Agent 설정으로 적재하지는 않는다.

| 경우 | 결과 |
|---|---|
| 생략 | `false`. 검색하지 않는다 |
| `true`인데 `dstone.ai.rag.enabled=false`(기본값) | 호출할 때 예외 `RAG가 비활성화되어 있습니다`. **기동은 통과**한다 |
| `true`인데 VectorStore 빈이 없음(임베딩 설정 오류) | 호출할 때 예외 `VectorStore 빈이 없습니다` |
| 검색 결과가 없음 | `ragAllowEmptyContext`에 따른다([1.11](#111-ragallowemptycontext)) |
| 채팅 요청에 `ragEnabled` 값을 보냄 | 이번 요청만 그 값으로 바뀐다(Workflow에서는 불가) |
| RAG를 "Tool로" 쓰고 싶음 | `ragEnabled` 대신 `toolsEnabled: true`로 두면 LLM이 `RagSearchTool`을 필요할 때만 부른다. Workflow에서는 TOOL step으로 직접 부를 수도 있다 |

#### 1.9 `ragTopK`

```yaml
  ragTopK: 3
```

- RAG 검색 결과를 **최대 몇 개** 붙일지 정한다.
- 생략하면 `dstone.ai.rag.retrieval.top-k`(설정 파일 기본 5, 설정이 없으면 코드 기본 5).
- RAG가 실제로 켜진 호출(Agent `ragEnabled: true` 또는 채팅 요청 `ragEnabled: true`)에서만 쓰인다. 꺼져 있으면 무시된다.

| 경우 | 결과 |
|---|---|
| 크게 잡음 | 참고자료가 길어져 토큰 비용이 늘고 관련 없는 조각이 섞이기 쉽다 |
| 작게 잡음 | 답에 필요한 조각을 놓칠 수 있다 |
| `0` 이하 | Spring AI가 검색 요청을 만들 때 예외를 던질 수 있다. 1 이상으로 적는다 |
| 정수가 아닌 값(`"many"`) | 기동 실패(YAML 바인딩 오류) |

#### 1.10 `ragSimilarityThreshold`

```yaml
  ragSimilarityThreshold: 0.3
```

- 이 값 **이상**의 유사도(0.0~1.0)인 조각만 결과로 쓴다.
- 생략하면 `dstone.ai.rag.retrieval.similarity-threshold`(설정 파일 기본 0.35, 설정이 없으면 코드 기본 0.35).
- RAG가 켜진 호출에서만 쓰인다.
- 임베딩 모델마다 유사도 분포가 다르다. 이 환경의 bge-m3는 실제로 관련 있는 문서도 0.5를 넘지 못하는 경우가 흔해서, 0.5 이상으로 올리면
  문서가 있어도 결과가 통째로 빌 수 있다. `POST /api/ai/rag/search`로 먼저 점수를 확인하고 정한다.

| 경우 | 결과 |
|---|---|
| `0.0` | 유사도와 관계없이 상위 `ragTopK`개를 모두 쓴다 |
| 높게 잡음(0.7 등) | 결과가 비기 쉽다 → `ragAllowEmptyContext` 동작으로 넘어간다 |
| 0.0~1.0 밖 | Spring AI가 검색 요청을 만들 때 예외를 던질 수 있다 |
| 숫자가 아닌 값 | 기동 실패(YAML 바인딩 오류) |

#### 1.11 `ragAllowEmptyContext`

```yaml
  ragAllowEmptyContext: false
```

- 검색 결과가 **하나도 없을 때**의 동작이다.
- 생략하면 `true`.

| 값 | 검색 결과가 없을 때 |
|---|---|
| `true`(기본) | 사용자 메시지를 그대로 보낸다. LLM이 일반 지식으로 답한다 |
| `false` | Spring AI 기본 동작대로 "지식 범위 밖이라 답할 수 없다고 정중히 알려라"는 지시로 바꿔 보낸다. 문서에 근거한 답만 허용하는 Agent에 쓴다 |

- 검색 결과가 있으면 이 값과 관계없이 [1.8](#18-ragenabled)의 모양으로 참고자료를 붙인다.
- RAG가 켜진 호출에서만 쓰인다.

#### 1.12 `allowedCallers`

```yaml
  allowedCallers: [billing-app]
```

- 이 Agent를 쓸 수 있는 caller(호출 주체, tenant) 목록이다. caller는 `ApiKeyAuthFilter`가 `X-API-Key` 헤더로 알아낸다.
- 목록 조회(`GET /api/ai/chat`)와 호출(`AgentRegistry.resolve()`)에 **같은 규칙**을 쓴다. 채팅과 Workflow step 모두 이 한 곳을 거친다.

| 경우 | 결과 |
|---|---|
| 생략 또는 `[]` | 누구나 사용 가능(caller가 null이어도 됨) |
| 값이 있고 caller가 목록에 있음 | 사용 가능 |
| 값이 있고 caller가 목록에 없음 | 목록에서 빠지고, 채팅은 거절 `agent[x]는 caller[y]에게 허용되지 않았습니다`, Workflow step은 **FAILED**(`onFailure`를 따르지 않는다) |
| ⚠️ 값이 있고 인증이 꺼짐(`dstone.ai.security.auth.enabled=false`) | caller가 항상 null이라 **아무도 쓰지 못한다**. 인증을 켠 환경에서만 채운다 |

- Workflow의 `allowedCallers`와는 따로 검사한다. Workflow를 실행할 수 있는 caller라도 그 안의 Agent가 막으면 그 step에서 FAILED다.
  Workflow와 그 안의 Agent는 caller 목록을 같게 맞추는 것이 좋다.

#### 1.13 대화 기억(sessionId) — Agent YAML 항목이 아니다

- 대화를 기억할지는 Agent가 아니라 **부르는 쪽**이 정한다. 같은 Agent라도 부르는 흐름에 따라 필요 여부가 다르기 때문이다.
- 채팅 API: 요청의 `sessionId`로 항상 대화를 잇는다. 같은 `sessionId`를 다시 보내면 대화가 이어지고, 비우면 새 대화가 된다.
- Workflow: 기본적으로 **기억 없이** 시스템 프롬프트 + step `input`만 보고 답한다. step끼리 대화는 공유하지 않는다.
  - step에 `memory: true`를 적으면 그 step만 자기 대화방(`sessionId:stepId`)에서 이전에 자기가 나눈 대화를 기억한다(재작성 루프 등).
  - 자세한 내용은 [workflow-yml-guide.md 3.11](../workflows/workflow-yml-guide.md#311-memory-agentsupervisorrouter-전용).
- 기억은 `MessageChatMemoryAdvisor`가 Redis(`RedisChatMemoryRepository`)에서 대화방의 최근 대화(`dstone.ai.session.max-messages`개, 기본 20)를 불러와 붙인다.

### 2. 제공되는 샘플

샘플 Workflow 목록과 각 Workflow의 input 모양은 [workflow-yml-guide.md 6절](../workflows/workflow-yml-guide.md#6-제공되는-샘플)에 있다.

| Agent | input / output | 용도 |
|---|---|---|
| `sample-basic-echo-agent` | string / string | 가장 단순한 AGENT(변수/Tool 없음) |
| `sample-general-chat` | string / string | 일반 대화(`{role}` 변수). dstone-boot 채팅 화면 기본값 |
| `sample-tool-demo-agent` | string / string | 로컬 Tool 자율 호출(`toolsEnabled: true`) |
| `sample-mcp-filesystem-agent` | string / string | MCP filesystem Tool 자율 호출 |
| `sample-rag-demo-agent` | string / string | RAG 증강(`ragEnabled: true`, `ragTopK`/`ragSimilarityThreshold`) |
| `sample-model-override-agent` | string / string | Agent별 `model` 지정 |
| `sample-structured-extract-agent` | string / **object `{sql}`** | output을 object로 선언한 예시(문장에서 SQL 추출) |
| `sample-fix-agent` | **object `{sql, error}`** / **object `{sql}`** | input/output 둘 다 object인 예시(재시도 루프에서 SQL 수정) |
| `sample-verdict-judge-agent` | string / (엔진 `{pass, reason}`) | SUPERVISOR용 |
| `sample-router-classifier-agent` | string / (엔진 `{route, reason}`) | ROUTER용(billing/technical/other 분류) |
| `testApp-analysis-agent` / `testApp-spec-writer-agent` / `testApp-codegen-agent` | string / string | `testApp-sdlc`의 분석/명세/코드 생성 |

### 3 Agent 뼈대

```yaml
# <이 Agent가 무엇을 하는지 한두 줄>
agent:
  id: <agent-id>
  description: <사람이 읽는 설명>
  prompt: |
    당신은 <역할>입니다.
    <지켜야 할 규칙>
    답변은 항상 한국어로 합니다.
  # input: string                       # 선택. 비우면 string
  # output: string                      # 선택. 비우면 string
  toolsEnabled: false
  ragEnabled: false
  # model: <모델 이름>                  # 선택. 비우면 공통 기본 모델
  # allowedCallers: [<caller>]          # 선택. 인증을 켠 환경에서만 채운다
```

**정해진 모양으로 답하는 Agent** — 다음 step이 `{{steps.<id>.output.<필드>}}`로 꺼내 쓴다.

```yaml
agent:
  id: <agent-id>
  prompt: |
    당신은 <역할>입니다. <무엇을 뽑아낼지/판단할지 말로 설명>   # JSON 모양은 적지 않는다
  output:
    schema:
      type: object
      properties:
        <필드>: { type: string, description: <필드 뜻 - LLM에게 전달된다> }
        <목록필드>: list<string>
      required: [<필드>, <목록필드>]
  toolsEnabled: false
  ragEnabled: false
```

**여러 값을 나눠 받는 Agent** — step `input`을 맵으로 적는다.

```yaml
agent:
  id: <agent-id>
  prompt: |
    입력으로 <필드1>(<뜻>)과 <필드2>(<뜻>) 두 필드를 가진 JSON을 받습니다.   # 중괄호 없이 말로
    <할 일>
  input:
    schema:
      type: object
      properties:
        <필드1>: string
        <필드2>: string
      required: [<필드1>, <필드2>]
  toolsEnabled: false
  ragEnabled: false
```

**Tool을 스스로 쓰는 Agent**

```yaml
agent:
  id: <agent-id>
  prompt: |
    당신은 <역할>입니다. 필요하면 제공된 Tool을 호출해서 사실을 확인한 뒤 답하십시오.
  toolsEnabled: true                    # caller 화이트리스트를 통과한 Tool 전부가 후보가 된다
  ragEnabled: false
```

**RAG를 쓰는 Agent**

```yaml
agent:
  id: <agent-id>
  prompt: |
    당신은 적재된 문서를 참고해서 답하는 어시스턴트입니다.
    [참고자료]가 주어지면 그 내용을 근거로 답하고, 없으면 일반 지식으로 답한다는 점을 밝히십시오.
  toolsEnabled: false
  ragEnabled: true
  ragTopK: 3                            # 선택
  ragSimilarityThreshold: 0.3           # 선택
  ragAllowEmptyContext: true            # 선택. 기본 true
```

**SUPERVISOR용 Agent** — `output`은 적지 않는다(엔진이 `{pass, reason}`).

```yaml
agent:
  id: <agent-id>
  prompt: |
    당신은 주어진 텍스트가 <기준>을 만족하는지 판정하는 검토자입니다.
    만족하면 pass=true, 아니면 pass=false로 답하고,
    reason에는 그렇게 판정한 이유를 한국어로 한 줄 적으십시오.
  toolsEnabled: false
  ragEnabled: false
```

**ROUTER용 Agent** — `output`은 적지 않는다(엔진이 `{route, reason}`, route는 Workflow `routes` 키 중 하나로 강제).

```yaml
agent:
  id: <agent-id>
  prompt: |
    당신은 요청을 아래 갈래 중 정확히 하나로 분류하는 라우터입니다.
    - billing: <기준>
    - technical: <기준>
    - other: 위에 해당하지 않는 모든 요청
    route에는 반드시 billing/technical/other 중 하나만 그대로 적으십시오.
    reason에는 왜 그렇게 분류했는지 한국어로 한 줄 적으십시오.
  toolsEnabled: false
  ragEnabled: false
```



### 1. Agent 항목

`agent:` 아래에 적는다(`common.definition.AgentDefinition`).

| 항목 | 필수 | 타입 | 설명 |
|---|---|---|---|
| `id` | ✅ | 문자열 | Agent 이름. step의 `ref`와 채팅 API의 `agent`가 이 값을 쓴다. 중복 불가 |
| `prompt` | ✅ | 문자열(여러 줄) | 시스템 프롬프트 원문. `{변수}`는 호출 시 채워진다(Workflow에서는 컨텍스트의 `input`, 채팅에서는 요청의 `variables`) |
| `description` | | 문자열 | 사람이 읽는 설명. `GET /api/ai/chat` 목록에 쓰인다 |
| `model` | | 문자열 | 이 Agent만 쓸 모델 이름(같은 provider 안에서). 비우면 공통 기본값. 다른 provider의 모델 이름이면 기동이 아니라 첫 호출 때 실패한다 |
| `toolsEnabled` | | boolean | LLM이 Tool을 스스로 골라 부를 수 있는지. 기본 `false` |
| `ragEnabled` | | boolean | 적재된 문서를 검색해서 답변에 참고하는지. 기본 `false` |
| `ragTopK` | | 정수 | RAG 검색 결과 최대 개수. 비우면 `dstone.ai.rag.retrieval.top-k` |
| `ragSimilarityThreshold` | | 실수 | RAG 최소 유사도. 비우면 `dstone.ai.rag.retrieval.similarity-threshold` |
| `ragAllowEmptyContext` | | boolean | 검색 결과가 없을 때도 답을 시도할지. 비우면 `true`. `false`면 "모른다"고 답하도록 강제 |
| `allowedCallers` | | 문자열 리스트 | 호출을 허락할 caller 목록. 비우면 누구나(⚠️ Workflow와 같은 주의사항) |

**StepType별 prompt 작성 요령**

| 용도 | prompt에 꼭 넣을 것 |
|---|---|
| AGENT(schema 없음) | 역할과 규칙, 결과물의 형식 |
| AGENT + `output.schema` | 역할과 규칙. JSON 형식 지시는 엔진이 자동으로 붙이므로 적지 않아도 된다 |
| SUPERVISOR | 무엇을 기준으로 pass/fail을 판정할지, reason에 무엇을 적을지 |
| ROUTER | route로 쓸 수 있는 이름 목록과 각각의 기준(Workflow의 `routes` 키와 정확히 같게) |

> 아래 1.1~1.10은 항목마다 **모양 → 동작 → 검사 시점 → 경우별 결과** 순서로 자세히 적었다.
> "기동 실패"는 엔진이 켜질 때 예외로 멈춘다는 뜻이다(`YamlDefinitionLoader`/`AgentRegistry`). 모르는 키(오타)도 기동 실패다.

**Agent가 불리는 두 경로** — 항목의 의미는 같지만 몇 가지가 다르다. 각 항목 설명에서 이 차이를 함께 적었다.

| | 채팅 API(`POST /api/ai/chat[/stream]`) | Workflow step(AGENT/SUPERVISOR/ROUTER의 `ref`) |
|---|---|---|
| Agent 지정 | 요청의 `agent` | step의 `ref` |
| prompt `{변수}` 값 | 요청의 `variables`(`message`는 **들어가지 않는다**) | 실행 컨텍스트의 `input`(요청 `message` + `variables`) |
| 사용자 메시지 | 요청의 `message` | step의 `input`을 채운 글자 |
| `ragEnabled`/`toolsEnabled`/`model`을 요청마다 바꾸기 | 가능(요청의 같은 이름 값이 있으면 그 값을 쓴다) | 불가. 항상 Agent 정의값 |
| 대화 기억(sessionId) | 요청의 `sessionId`(비우면 새로 발급) | Workflow 실행의 sessionId. **같은 실행의 모든 Agent step이 공유**한다 |
| 호출이 예외로 끝나면 | 요청이 오류로 끝난다 | [workflow-yml-guide.md 3절](../workflows/workflow-yml-guide.md#3-step-항목)의 "onFailure로 가는 경우" 표를 따른다 |

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
| 등록되지 않은 `id`로 호출 | 채팅은 거절 `등록되지 않은 agent입니다`. Workflow는 **기동 시 검사하지 않으므로** 그 step이 실행될 때 FAILED |
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
  - 사용자 메시지: "이번에 처리할 데이터"(Workflow에서는 step `input`, 채팅에서는 `message`)

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
| `{domain}` | Agent `prompt` | **Agent를 호출할 때마다**(`AgentExecutor.buildSpec()`) | 채팅: 요청 `variables` / Workflow: 컨텍스트 `input` |
| `{{input.x}}` | Workflow의 step `input`/`forEach`/`output` | **step을 실행할 때마다**(`WorkFlowExecutor`) | 실행 컨텍스트(`input`/`steps`/`previous`) |

- Agent prompt에 `{{input.x}}`를 적으면 Workflow 값으로 채워지지 **않는다**. 오류도 나지 않고 `input.x`라는 글자가 그대로 들어가므로 알아차리기 어렵다. prompt에서는 `{x}`로 쓴다.

##### 1.2.1 한눈에 보기 — 값이 흘러가는 길

```
                         ┌──────────────────────────────────────┐
                         │ agents/doc-translator-agent.yml      │
                         │   prompt: "... {domain} ... {language}"│
                         └──────────────────┬───────────────────┘
                   ═══════ 엔진 기동 시 (한 번) ═══════
                         ① YAML 글자 → Map        (SnakeYAML, ${VAR}만 치환)
                         ② Map → AgentDefinition  (Jackson, prompt의 {domain}은 글자 그대로 보관)
                         ③ AgentRegistry에 id로 등록 ({변수}는 검사하지 않음)
                                            │
             ┌──────────────────────────────┴──────────────────────────────┐
   경로 A: 채팅 API                                            경로 B: Workflow step
   POST /api/ai/chat                                          type: AGENT, ref: doc-translator-agent
   { agent, message, variables }                              요청 { message, variables } → context.input
             │                                                              │
   ④ variables       ─────▶ {변수} 값                    ④' context.input 전체 ─────▶ {변수} 값
   ⑤ message         ─────▶ user 메시지                  ⑤' step input 템플릿을 채운 글자 ─▶ user 메시지
             └──────────────────────────────┬──────────────────────────────┘
                         ⑥ AgentExecutor.buildSpec()
                            PromptTemplate(prompt).render(값) → system 메시지
                            + 대화 기억(sessionId) + RAG/Tool/model/응답 형식
                                            ▼
                         ⑦ LLM 호출 → 답변
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
```

이 Agent를 부르는 Workflow:

```yaml
# workflows/doc-translate.yml (설명용 예시)
workflow:
  id: doc-translate
  inputs:
    domain: string            # Agent prompt의 {domain}을 채울 값
    language: string          # Agent prompt의 {language}를 채울 값
  steps:
    - id: translate
      type: AGENT
      ref: doc-translator-agent
      input: |
        아래 글을 번역하세요.
        {{input.message}}
```

##### 1.2.3 단계별로 따라가기

**① YAML 글자 → Map** — SnakeYAML이 읽는다. 이때 바뀌는 것은 `${VAR}`뿐이다. `{domain}`은 **글자 그대로** 남는다.

```
{ agent = { id = "doc-translator-agent",
            prompt = "당신은 {domain} 분야 전문 번역가입니다.\n결과는 {language}로만 ...\n",   ← |는 줄바꿈 유지
            toolsEnabled = false, ragEnabled = false } }
```

**② Map → `AgentDefinition`** — Jackson `convertValue()`. 적지 않은 항목은 `null`(boolean은 `false`)이 된다.

```
AgentDefinition(
  id                    = "doc-translator-agent",
  description           = "분야와 대상 언어를 받아 문서를 번역한다",
  prompt                = "당신은 {domain} 분야 전문 번역가입니다.\n...",   ← 아직 틀(template) 상태
  model                 = null      → 호출 때 공통 기본 모델
  toolsEnabled          = false,
  ragEnabled            = false,
  ragTopK               = null      → 호출 때 설정 파일 값
  ragSimilarityThreshold= null,
  ragAllowEmptyContext  = null      → 호출 때 true로 본다
  allowedCallers        = null      → 누구나
)
```

**③ 등록** — `AgentRegistry`가 `id`로 등록한다. `{domain}`에 값이 들어올지는 **검사하지 않는다**.
Agent는 채팅에서도, 여러 Workflow에서도 불릴 수 있어서 기동 시점에는 어떤 값이 올지 알 수 없기 때문이다.

**④⑤ 경로 A — 채팅 API**

```json
POST /api/ai/chat
{
  "agent": "doc-translator-agent",
  "message": "Interest rates rose sharply.",
  "variables": { "domain": "금융", "language": "한국어" }
}
```

```
ChatRequest ──▶ ChatController ──▶ AgentExecutor.call(
                                      agent       = doc-translator-agent,
                                      variables   = { domain: "금융", language: "한국어" },   ← request.variables 그대로
                                      userMessage = "Interest rates rose sharply."          ← request.message
                                   )
```

- `{변수}` 값은 요청의 `variables` **만** 쓴다. `message`는 variables에 들어가지 않으므로 **채팅에서 `{message}`는 채워지지 않는다**(예외).

**④'⑤' 경로 B — Workflow step**

```json
POST /api/ai/workflow/doc-translate/execute
{
  "message": "Interest rates rose sharply.",
  "variables": { "domain": "금융", "language": "한국어" }
}
```

```
context.input = { domain: "금융", language: "한국어", message: "Interest rates rose sharply." }
        │
        ├─▶ step input 템플릿 채우기 ──▶ "아래 글을 번역하세요.\nInterest rates rose sharply."   → user 메시지
        │
        └─▶ StepInput.workflowInput = context.input 전체                                → {변수} 값
                   │
AgentStepRunner ──▶ AgentExecutor.call(agent, variables = workflowInput, userMessage = 채운 step input)
```

- `{변수}` 값은 컨텍스트의 `input` 전체다. 그래서 Workflow에서는 `{message}`도 쓸 수 있다.
- step `input`이 무엇이든 `{변수}` 값은 **늘 같다**(`input`은 실행 중에 바뀌지 않는다). 앞 step의 결과를 `{변수}`로 받을 방법은 없다.
  앞 step 결과는 step `input`의 `{{steps.<id>.text}}`로 user 메시지에 넣는다.

**⑥ 메시지 조립** — `AgentExecutor.buildSpec()`이 prompt 틀을 채우고, 나머지 설정을 붙인다.

```
PromptTemplate("당신은 {domain} 분야 ... {language}로만 ...").render({ domain: "금융", language: "한국어", ... })
   = "당신은 금융 분야 전문 번역가입니다.\n결과는 한국어로만 작성하고, ..."
```

**⑦ LLM에게 실제로 가는 메시지** (경로 B 기준)

```
┌─ system ─────────────────────────────────────────────────────┐
│ 당신은 금융 분야 전문 번역가입니다.                                │  ← prompt + {변수}
│ 결과는 한국어로만 작성하고, 전문 용어는 원문을 괄호에 함께 적습니다.  │
└──────────────────────────────────────────────────────────────┘
┌─ (같은 sessionId의 이전 대화 — MessageChatMemoryAdvisor) ────────┐
│ user: ... / assistant: ...                                   │  ← Workflow면 앞 AGENT step들의 대화
└──────────────────────────────────────────────────────────────┘
┌─ user ───────────────────────────────────────────────────────┐
│ 아래 글을 번역하세요.                                           │  ← 채팅: message / Workflow: 채운 step input
│ Interest rates rose sharply.                                 │
│ (ragEnabled면)  [참고자료] <검색된 문서 조각들>                   │  ← 1.6
│ (output.schema/SUPERVISOR/ROUTER면) JSON 형식 지시문            │  ← 엔진이 자동으로 붙임
└──────────────────────────────────────────────────────────────┘
  + toolsEnabled면 후보 Tool 목록, model이 있으면 그 모델 이름(ChatOptions)
```

##### 1.2.4 같은 Agent, 두 경로 비교

| | 경로 A: 채팅 | 경로 B: Workflow step |
|---|---|---|
| `{domain}`, `{language}` | 요청 `variables.domain`, `.language` | `context.input.domain`, `.language`(= 요청 `variables`) |
| `{message}` | ❌ 채워지지 않음 → 예외 | ✅ 요청 `message` |
| `{그 밖의 이름}` | 요청 `variables`에 있으면 됨 | 요청 `variables`에 있으면 됨(Workflow `inputs`에 선언 안 해도 됨) |
| user 메시지 | 요청 `message` 그대로 | step `input`을 채운 글자(생략하면 `{{previous.text}}`) |
| 값이 빠지면 | 채팅 요청 오류 | step 종류에 따라 FAILED 또는 step 실패([workflow-yml-guide.md 3절](../workflows/workflow-yml-guide.md#3-step-항목)) |
| 미리 막는 방법 | 호출하는 쪽이 항상 보낸다 | Workflow `inputs`에 선언하면 값이 없을 때 실행 전에 **400**으로 막힌다([workflow-yml-guide.md 2.5](../workflows/workflow-yml-guide.md#25-inputs)) |

> Workflow에서 Agent의 `{변수}`를 쓴다면, 그 이름을 Workflow `inputs`에 **같은 이름으로 선언**해 두는 것이 안전하다.
> 선언하지 않으면 값이 빠진 요청도 실행이 시작되고, 그 Agent step에 가서야 실패한다.

##### 1.2.5 자주 쓰는 패턴

**A. 변수 없음 — 가장 안전**

```yaml
  prompt: |
    당신은 SQL을 PostgreSQL로 변환하는 전문가입니다.
```

어느 경로에서든 `variables` 없이 항상 동작한다. 처리할 데이터는 모두 user 메시지(step `input`)로 넘긴다.

**B. 역할/말투를 바꿔 끼우기 — 채팅 화면에서 주로 사용**

```yaml
  prompt: |
    당신은 {role} 역할을 맡은 AI 어시스턴트입니다.     # sample-general-chat.yml
```

```json
{ "agent": "sample-general-chat", "message": "안녕", "variables": { "role": "친절한 상담원" } }
```

**C. Workflow `inputs`와 짝지어 쓰기**

```yaml
# workflow                              # agent
  inputs:                                 prompt: |
    targetVersion: string                   당신은 PostgreSQL {targetVersion} 전문 DBA입니다.
```

Workflow가 `targetVersion`을 필수로 받으므로 Agent `{targetVersion}`이 비는 일이 없다.

**D. 요청 원문을 system에 박아 두기 — Workflow 전용 `{message}`**

```yaml
  prompt: |
    사용자의 원래 요청은 "{message}"입니다. 모든 판단은 이 요청을 기준으로 합니다.
```

뒤쪽 step(SUPERVISOR 등)의 user 메시지가 앞 step의 결과로 바뀌어도, 원래 요청을 system 쪽에서 계속 볼 수 있다.
⚠️ 이 Agent를 **채팅에서 부르면 예외**다. 채팅에서도 쓸 Agent라면 `{message}`를 쓰지 않는다.

**E. JSON 예시를 보여 주고 싶을 때 — 중괄호 대신 말로**

```yaml
  # ❌ 중괄호가 변수로 읽혀 호출이 실패한다
  prompt: |
    {"sql": "...", "tables": [...]} 모양으로 답하세요.

  # ✅ 형식은 말로 설명하거나, step의 output.schema에 맡긴다
  prompt: |
    변환한 SQL과, 그 SQL이 쓰는 테이블 이름 목록을 답하세요.
```

`output.schema`를 쓰면 엔진이 JSON 형식 지시문을 user 메시지 끝에 자동으로 붙인다.

##### 1.2.6 경우별 결과

| 경우 | 결과 |
|---|---|
| `{변수}`가 없음 | 원문 그대로. variables를 보내지 않아도 항상 동작 |
| `{role}`이 있는데 호출에 `role` 값이 없음 | **예외** `Not all variables were replaced in the template. Missing variable names are: [role]`(Spring AI 기본 검증 모드가 THROW). 채팅은 오류, Workflow는 step 종류에 따라 FAILED 또는 step 실패 |
| 채팅에서 `{message}` 사용 | 위와 같은 예외(채팅은 `message`를 variables에 넣지 않는다) |
| 쓰지 않는 값이 더 들어옴 | 무시 |
| JSON 예시처럼 **중괄호 글자**를 그대로 적음(`{"a": 1}`) | 변수로 읽히거나 템플릿 문법 오류로 호출이 실패한다. prompt에는 중괄호를 쓰지 않고 말로 설명한다(JSON 형식 지시가 필요하면 step의 `output.schema`를 쓴다) |
| Workflow 문법 `{{input.x}}`를 prompt에 적음 | 오류 없이 `input.x`라는 **글자**가 들어간다(값이 채워지지 않는다). prompt에서는 `{x}`로 쓴다 |
| 변수 이름에 `-`, 공백, 한글 | 변수 이름으로 읽히지 않을 수 있다. 영문, 숫자, `_`만 쓴다 |
| `{options.strict}`(맵 값의 안쪽 키) | 동작한다. `options = {strict: true}`이면 `true`가 들어간다 |
| 값이 리스트 | 항목이 **구분자 없이** 붙는다. `["a","b"]` → `ab` |
| 값이 맵 | **키 이름만** 붙는다. `{strict: true}` → `strict` |
| ↳ 리스트/맵을 LLM에게 보여 줘야 할 때 | prompt `{x}` 대신 step `input`의 `{{input.x}}`로 넘긴다(JSON 글자 `["a","b"]`로 들어간다) |
| 생략하거나 빈 문자열 | 기동 실패 `id와 prompt가 모두 있어야 합니다` |

##### 1.2.7 step 종류별로 엔진이 prompt에 덧붙이는 것

| 용도 | 엔진이 자동으로 하는 일 | prompt에 적을 것 |
|---|---|---|
| AGENT(schema 없음) | 없음. 답변 원문이 그대로 `text` | 결과물의 형식까지 모두 |
| AGENT + `output.schema` | "이 JSON Schema를 지키는 JSON 객체 하나로만 답하라"는 지시를 붙이고, 답을 읽어 필드와 타입을 검사한다(`SchemaOutputConverter`) | 역할과 규칙만. 각 필드의 의미는 schema의 `description`에 적는다 |
| SUPERVISOR | `{pass: boolean, reason: string}` 모양으로 답하라는 형식 지시를 붙인다(`Verdict`) | **판정 기준**, reason에 적을 내용 |
| ROUTER | `{route: string, reason: string}` 모양으로 답하라는 형식 지시를 붙인다(`RouteDecision`) | **쓸 수 있는 route 이름 목록**(Workflow `routes` 키와 똑같이)과 각각의 기준. 엔진은 route 이름 목록을 LLM에게 알려 주지 않는다 |

- 같은 Agent를 여러 step 종류에서 재사용할 수는 있지만, 판정용/분류용 prompt는 보통 그 용도 전용으로 만든다.

#### 1.3 `description`

```yaml
  description: 일반 대화용 기본 Agent. dstone-boot 채팅 화면이 사용한다.
```

- 사람이 읽는 설명이다. 호출 동작에는 **영향이 없다**(LLM에게 전달되지 않는다).
- `GET /api/ai/chat` 목록(`AgentSummary{id, description}`)에 나가고, dstone-boot 채팅 화면의 Agent 드롭다운에 보인다.
- 생략하면 목록에 빈 값으로 나갈 뿐 오류는 없다.

#### 1.4 `model`

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

#### 1.5 `toolsEnabled`

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
| `true` + `output.schema`/SUPERVISOR/ROUTER | 함께 쓸 수 있다. Tool로 사실을 확인한 뒤 정해진 모양으로 답한다 |
| `"yes"`처럼 boolean이 아닌 값 | 기동 실패(YAML 바인딩 오류) |

#### 1.6 `ragEnabled`

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
| 검색 결과가 없음 | `ragAllowEmptyContext`에 따른다([1.9](#19-ragallowemptycontext)) |
| 채팅 요청에 `ragEnabled` 값을 보냄 | 이번 요청만 그 값으로 바뀐다(Workflow에서는 불가) |
| RAG를 "Tool로" 쓰고 싶음 | `ragEnabled` 대신 `toolsEnabled: true`로 두면 LLM이 `RagSearchTool`을 필요할 때만 부른다. Workflow에서는 TOOL step으로 직접 부를 수도 있다 |

#### 1.7 `ragTopK`

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

#### 1.8 `ragSimilarityThreshold`

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

#### 1.9 `ragAllowEmptyContext`

```yaml
  ragAllowEmptyContext: false
```

- 검색 결과가 **하나도 없을 때**의 동작이다.
- 생략하면 `true`.

| 값 | 검색 결과가 없을 때 |
|---|---|
| `true`(기본) | 사용자 메시지를 그대로 보낸다. LLM이 일반 지식으로 답한다 |
| `false` | Spring AI 기본 동작대로 "지식 범위 밖이라 답할 수 없다고 정중히 알려라"는 지시로 바꿔 보낸다. 문서에 근거한 답만 허용하는 Agent에 쓴다 |

- 검색 결과가 있으면 이 값과 관계없이 [1.6](#16-ragenabled)의 모양으로 참고자료를 붙인다.
- RAG가 켜진 호출에서만 쓰인다.

#### 1.10 `allowedCallers`

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

#### 1.11 대화 기억(sessionId) — YAML 항목은 아니지만 알아둘 것

- 모든 호출에 `MessageChatMemoryAdvisor`가 붙어서, 같은 sessionId의 이전 대화가 Redis(`RedisChatMemoryRepository`)에서 불려 와 함께 전달된다.
- Workflow에서는 **한 실행의 모든 Agent step이 같은 sessionId**를 쓴다. 그래서 뒤 step의 LLM은 앞 step들이 주고받은 대화도 본다.
  - 장점: 앞 step의 맥락을 자연스럽게 이어받는다.
  - 주의: 판정용(SUPERVISOR)이나 분류용(ROUTER) Agent도 앞의 대화를 보고 영향을 받을 수 있다. 판정에 필요한 정보는 step `input`에 분명히 넣는다.
  - `forEach` AGENT step은 반복들이 같은 기억을 동시에 쓴다. 반복끼리 독립적이어야 하면 필요한 정보를 모두 `input`에 담는다.
- 채팅에서는 같은 `sessionId`를 다시 보내면 대화가 이어지고, 비우면 새 대화가 된다.

### 2. 제공되는 샘플

`src/main/resources/{agents,workflows,mcp}/` 아래에 두 묶음이 있다. 각 파일 맨 위 주석에 dstone-boot "Workflow 테스트"
화면에서 어떤 workflowId/message/variables로 호출하면 되는지 적혀 있다.

**Workflow**

| 기능 | Workflow |
|---|---|
| AGENT step 기본(변수/Tool 없이) | `sample/sample-agent-basic-echo.yml` |
| AGENT의 자율 tool-calling | `sample/sample-agent-tool-calling.yml` |
| AGENT의 RAG(ragEnabled) 증강 | `sample/sample-agent-rag-augmented.yml` |
| Agent별 model override | `sample/sample-agent-model-override.yml` |
| TOOL step 연쇄(LLM 없이) | `sample/sample-tool-chain-basic.yml` |
| MCP Tool + `output.parse: lines` + 앞 step data로 `forEach` | `sample/sample-mcp-filesystem-list.yml` |
| TOOL로 RAG 검색만 직접 호출 | `sample/sample-tool-rag-search.yml` |
| 위험 Tool(http/shell/python) 기본 거부 확인 | `sample/sample-tool-gated-external.yml` |
| AGENT `output.schema` → `{{steps.<id>.data.<키>}}`, Workflow `output` | `sample/sample-structured-output-chain.yml` |
| SUPERVISOR(pass/reason) 판정 | `sample/sample-supervisor-verdict-gate.yml` |
| ROUTER 다지 분기(routes) | `sample/sample-router-multiway.yml` |
| onFailure 재시도 루프 + `previous`/`??`/`steps.<id>.error` | `sample/sample-loop-retry-until-valid.yml` |
| `forEach` 병렬 실행 + Workflow `inputs` 계약 | `sample/sample-foreach-parallel.yml` |
| APPROVAL 일시중단/재개(HITL) | `sample/sample-approval-pause-resume.yml` |
| 실전 8단계 승인형 SDLC(AGENT+APPROVAL+TOOL 종합) | `testApp/testApp-sdlc.yml` |

**Agent**

| Agent | 용도 |
|---|---|
| `sample-basic-echo-agent` | 가장 단순한 AGENT(변수/Tool 없음) |
| `sample-general-chat` | 일반 대화(`{role}` 변수). dstone-boot 채팅 화면 기본값 |
| `sample-tool-demo-agent` | 로컬 Tool 자율 호출(`toolsEnabled: true`) |
| `sample-mcp-filesystem-agent` | MCP filesystem Tool 자율 호출 |
| `sample-rag-demo-agent` | RAG 증강(`ragEnabled: true`, `ragTopK`/`ragSimilarityThreshold`) |
| `sample-model-override-agent` | Agent별 `model` 지정 |
| `sample-structured-extract-agent` | `output.schema` AGENT용(문장에서 SQL 추출) |
| `sample-fix-agent` | 재시도 루프용(검증 실패한 SQL 수정) |
| `sample-verdict-judge-agent` | SUPERVISOR용(pass/reason 판정) |
| `sample-router-classifier-agent` | ROUTER용(billing/technical/other 분류) |
| `testApp-analysis-agent` / `testApp-spec-writer-agent` / `testApp-codegen-agent` | `testApp-sdlc`의 분석/명세/코드 생성 |


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
  toolsEnabled: false
  ragEnabled: false
  # model: <모델 이름>                  # 선택. 비우면 공통 기본 모델
  # allowedCallers: [<caller>]          # 선택. 인증을 켠 환경에서만 채운다
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

**SUPERVISOR용 Agent**

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

**ROUTER용 Agent**

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


### 1 파일 규칙

| 종류 | 위치 | 최상위 키 | 파일 하나에 |
|---|---|---|---|
| Workflow | `src/main/resources/workflows/**/*.yml` | `workflow:` | Workflow 1개 |
| Agent | `src/main/resources/agents/**/*.yml` | `agent:` | Agent 1개 |
| MCP 서버 | `src/main/resources/mcp/**/*.yml` | `mcpServer:` | MCP 서버 1개 |

- 폴더는 자유롭게 나눠도 된다(`workflows/billing/*.yml` 등). 구분 기준은 파일 경로가 아니라 `id`다.
- 파일 이름은 `id`와 맞추는 것을 권장한다(강제는 아님).
- 모르는 키를 쓰면 기동이 실패한다. 오타에 주의한다.
- 문자열 안의 `${VAR_NAME}`은 기동 시 `conf/env{-profile}.properties` 값으로 바뀐다(`YamlDefinitionLoader`). 찾지 못한 이름은 `${VAR_NAME}` 글자 그대로 남는다. `{{ }}`(실행 중 데이터 참조)와는 다른 것이다.
- 파일 맨 위에 "이 파일이 무엇을 하는지, 어떻게 호출해 보면 되는지"를 주석으로 적어 두는 것이 이 프로젝트의 관례다.

### 2 Workflow 항목

`workflow:` 아래에 적는다(`common.definition.WorkFlowDefinition`).

| 항목 | 필수 | 타입 | 설명 |
|---|---|---|---|
| `id` | ✅ | 문자열 | Workflow를 가리키는 이름. API 경로(`/api/ai/workflow/{id}/...`)에 쓰인다. 중복 불가 |
| `description` | | 문자열 | 사람이 읽는 설명. `GET /api/ai/workflow` 목록과 dstone-boot 화면 안내문에 쓰인다 |
| `maxIterations` | | 정수 | 전체 step 실행 횟수 상한(루프 방지). 비우면 **10** |
| `allowedCallers` | | 문자열 리스트 | 실행을 허락할 caller 목록. 비우면 누구나. ⚠️ 인증이 꺼져 있으면 caller가 항상 null이라, 채우는 순간 아무도 실행 못 한다 |
| `inputs` | | 맵(이름 → 타입) | 입력 계약. 요청의 `variables`에 이 값들이 없거나 타입이 다르면 실행 전에 **400**. `message`는 항상 들어오므로 선언하지 않는다. 선언하면 `{{input.이름}}` 참조도 이 목록으로 기동 시 검사한다 |
| `output` | | 템플릿 문자열 | 성공으로 끝났을 때 돌려줄 최종 결과. 비우면 마지막으로 실행된 step의 `text` |
| `steps` | ✅ | step 리스트 | 실행할 step 목록. 목록 순서가 기본 실행 순서다 |

> 아래 2.1~2.7은 항목마다 **모양 → 동작 → 검사 시점 → 경우별 결과** 순서로 자세히 적었다.
> "기동 실패"는 엔진이 켜질 때 예외로 멈춘다는 뜻이고(`WorkFlowRegistry`), "400"은 실행 요청을 거절한다는 뜻이며,
> "FAILED"는 실행은 시작됐지만 Workflow 전체가 실패 상태로 끝난다는 뜻이다.

#### 2.1 `id`

```yaml
workflow:
  id: sample-foreach-parallel
```

- 이 Workflow를 부르는 이름이다. `POST /api/ai/workflow/{id}/execute`, `/submit`의 `{id}`가 이 값이다.
- 파일 경로나 파일 이름과는 관계없다. `workflows/a/x.yml`에 `id: y`라고 적으면 이름은 `y`다.
- 대소문자를 구분한다. 경로에 들어가므로 영문 소문자, 숫자, `-`만 쓰는 것을 권장한다.

| 경우 | 결과 |
|---|---|
| 생략하거나 빈 문자열 | 기동 실패 `workflows/*.yml 항목은 id와 steps가 모두 있어야 합니다` |
| 다른 파일과 `id`가 같음 | 기동 실패 `workflow id가 중복 등록되었습니다` |
| 등록되지 않은 `id`로 실행 요청 | 실행 거절 `등록되지 않은 workflow입니다` |

#### 2.2 `description`

```yaml
  description: forEach 동작 확인용 테스트 Workflow
```

- 사람이 읽는 설명이다. 실행 동작에는 영향이 없다.
- `GET /api/ai/workflow` 목록에 `id`와 함께 나가고, dstone-boot "Workflow 테스트" 화면 드롭다운의 안내문으로 쓰인다.
- 생략하면 목록에 빈 값으로 나갈 뿐 오류는 없다.

#### 2.3 `maxIterations`

```yaml
  maxIterations: 6
```

- **step을 실행할 수 있는 전체 횟수의 상한**이다. `onFailure`로 앞 step에 되돌아가는 재시도 루프가 끝없이 돌지 않게 막는다.
- 생략하면 `Constants.WorkFlow.DEFAULT_MAX_ITERATIONS` = **10**.
- 셈하는 방법(`WorkFlowExecutor.run()`):
  - step을 한 번 실행할 때마다 1씩 센다. 같은 step을 루프로 다시 실행해도 매번 센다.
  - `forEach` step은 항목이 몇 개든 **1회**로 센다.
  - APPROVAL에서 멈췄다가 승인 결정으로 **재개하면 0부터 다시 센다**. 상한은 "`run()`을 한 번 부를 때마다"의 값이다.
- 상한을 넘으면 FAILED `최대 실행 횟수(N)를 초과했습니다(루프 정지)`로 끝난다.

| 경우 | 결과 |
|---|---|
| 루프 없는 순차 Workflow | step 수 이상이면 충분하다. step이 10개를 넘으면 **반드시 늘려야** 한다(생략하면 11번째 step에서 FAILED) |
| 검증↔수정 재시도 루프 | (루프를 이루는 step 수 × 원하는 재시도 횟수) + 나머지 step 수 이상으로 잡는다. 예: `validate`↔`fix` 3회전이면 6 |
| `0` 또는 음수 | 첫 step도 실행하지 못하고 바로 FAILED |
| 숫자가 아닌 값(`"abc"`) | 기동 실패(YAML 바인딩 오류) |

#### 2.4 `allowedCallers`

```yaml
  allowedCallers: [billing-app, admin-console]
```

- 이 Workflow를 실행할 수 있는 caller(호출 주체, tenant) 목록이다. caller는 `ApiKeyAuthFilter`가 `X-API-Key` 헤더로 알아낸다.
- 목록 조회(`GET /api/ai/workflow`)와 실행(`resolve()`)에 **같은 규칙**을 쓴다. 그래서 실행할 수 없는 Workflow는 목록에도 보이지 않는다.
- 승인 결정(`/decision`)으로 재개할 때도 실행을 시작한 caller로 다시 검사한다.

| 경우 | 결과 |
|---|---|
| 생략 또는 `[]` | 누구나 실행 가능(caller가 null이어도 됨) |
| 값이 있고 caller가 목록에 있음 | 실행 가능 |
| 값이 있고 caller가 목록에 없음 | 목록에서 빠지고, 실행하면 거절 `workflow[id]는 caller[x]에게 허용되지 않았습니다` |
| ⚠️ 값이 있고 인증이 꺼짐(`dstone.ai.security.auth.enabled=false`) | caller가 항상 null이라 **아무도 실행하지 못한다**. 인증을 켠 환경에서만 채운다 |

- Workflow의 `allowedCallers`와 step이 부르는 Agent의 `allowedCallers`는 **따로** 검사한다. Workflow를 실행할 수 있어도
  AGENT step의 Agent가 그 caller를 막으면 그 step에서 FAILED가 된다([3.3 `ref`](#33-ref) 참고).

#### 2.5 `inputs`

**한 줄 요약** — "이 Workflow를 실행하려면 요청에 **이런 값을 꼭 넣어 와라**"는 약속(입력 계약)이다.

Java 메서드의 매개변수 선언이라고 생각하면 쉽다.

```
YAML의 inputs                       ≒  Java 메서드 매개변수
──────────────────────────────────     ──────────────────────────────────────────────
inputs:                                String review(
  sqlList: list<string>                    List<String>        sqlList,
  targetVersion: string                    String              targetVersion,
  maxRows: integer                         Integer             maxRows,
  options: object                          Map<String,Object>  options,
(선언하지 않아도 항상 있음)                   String              message )   ← 요청의 message

실행 요청의 variables               ≒  메서드를 부를 때 넘기는 값(인자)
```

- 선언한 값은 **모두 필수**다. "있어도 되고 없어도 되는 값"을 표현하는 방법은 없다([2.5.5](#255-자주-쓰는-선언-패턴) 패턴 F 참고).
- `message`는 요청마다 항상 들어오므로 선언하지 않는다.
- `inputs`를 적으면 두 번 검사한다. **엔진이 켜질 때** `{{input.*}}` 참조가 선언된 이름인지, **요청이 올 때** 값이 빠지거나 타입이 틀리지 않았는지.

##### 2.5.1 한눈에 보기 — 값이 흘러가는 길

```
 ┌────────────────────────────┐                ┌──────────────────────────────────────────┐
 │ 개발자: workflows/xxx.yml   │                │ 호출자: POST /api/ai/workflow/{id}/execute │
 │   inputs:                  │                │   { "message": "...",                    │
 │     sqlList: list<string>  │                │     "variables": { "sqlList": [...] } }  │
 └─────────────┬──────────────┘                └────────────────────┬─────────────────────┘
               │                                                    │
   ════════ 엔진 기동 시 (한 번) ═════════            ════════ 요청이 올 때 (매번) ═════════
               ▼                                                    ▼
   ① YAML 글자 → Map          (SnakeYAML)            ⑤ message가 비었나?        → 400
   ② Map → WorkFlowDefinition (Jackson)              ⑥ 이 caller가 써도 되나?   (allowedCallers)
   ③ 타입 이름이 올바른가?      → 기동 실패             ⑦ inputs 계약을 지켰나?    → 400
   ④ {{input.x}}가 선언된 이름인가? → 기동 실패        ⑧ 실행 컨텍스트 만들기     (context.input)
               │                                                    │
               └──────────── WorkFlowRegistry에 등록 ──────────────┐ │
                                                                  ▼ ▼
                                  ═════════ step을 실행할 때 (step마다) ═════════
                                  ⑨  forEach: input.sqlList      → 리스트를 꺼내 항목 수만큼 반복
                                  ⑩  step input의 {{input.x}}    → Tool 인자 / LLM 사용자 메시지
                                  ⑪  Agent prompt의 {x}          → LLM 시스템 메시지
                                  ⑫  Workflow output의 {{input.x}} → 최종 응답
```

- ①~④는 엔진이 켜질 때 한 번, ⑤~⑧은 요청마다 한 번, ⑨~⑫는 step마다 일어난다.
- `input` 값은 ⑧에서 한 번 만들어진 뒤 **실행이 끝날 때까지 바뀌지 않는다**. 어느 step에서 꺼내도 같은 값이다.

##### 2.5.2 샘플 Workflow

아래 두 파일을 예로 ①~⑫를 따라가 본다.
(설명을 위해 만든 예시라 실제 파일은 없다. 그대로 돌려 볼 수 있는 가장 가까운 샘플은 `sample/sample-foreach-parallel.yml`이다.)

```yaml
# workflows/sql-review.yml (설명용 예시)
workflow:
  id: sql-review
  description: SQL 목록을 문법 검사한 뒤, 대상 PostgreSQL 버전 기준으로 검토한다
  maxIterations: 5
  inputs:
    sqlList: list<string>                 # 축약형: 타입만
    targetVersion:                        # 확장형: 타입 + 설명(설명은 사람만 읽는다)
      type: string
      description: 대상 PostgreSQL 버전(예 "16")
    maxRows: integer
    options: object
  output: "{{steps.review.text}}"
  steps:

    - id: check                           # ❶ 리스트를 forEach로 쪼개서 TOOL에 넘기기
      type: TOOL
      ref: validateSqlSyntax
      forEach: input.sqlList
      input:
        sql: "{{item}}"

    - id: review                          # ❷ 글자 안에 섞어 LLM에게 넘기기
      type: AGENT
      ref: sql-review-agent
      input: |
        아래 SQL을 PostgreSQL {{input.targetVersion}} 기준으로 검토해 주세요.
        조회 결과는 최대 {{input.maxRows}}건으로 제한해 주세요.
        엄격 모드: {{input.options.strict}}

        [SQL 목록]
        {{input.sqlList}}

        [문법 검사 결과]
        {{steps.check.text}}
```

```yaml
# agents/sql-review-agent.yml (설명용 예시)
agent:
  id: sql-review-agent
  prompt: |
    당신은 PostgreSQL {targetVersion} 전문 DBA입니다.
    요청자의 원래 메시지는 "{message}"입니다.
```

호출 요청:

```json
POST /api/ai/workflow/sql-review/execute
{
  "message": "운영 반영 전에 SQL 검토 부탁합니다",
  "variables": {
    "sqlList": ["SELECT * FROM emp", "SELECT name FROM dept"],
    "targetVersion": "16",
    "maxRows": 100,
    "options": { "strict": true }
  }
}
```

##### 2.5.3 단계별로 따라가기

**① YAML 글자 → Map** — `YamlDefinitionLoader`가 SnakeYAML로 파일을 읽는다. 아직은 평범한 Map/List/String이다.
이때 `${VAR}`(환경 값) 치환도 같이 한다.

```
{ workflow = {
    id = "sql-review",
    inputs = {
      sqlList       = "list<string>",                                   ← 축약형은 그냥 글자
      targetVersion = { type = "string", description = "대상 PostgreSQL ..." },   ← 확장형은 맵
      maxRows       = "integer",
      options       = "object"
    },
    steps = [ {...}, {...} ], ...
} }
```

**② Map → Java record** — Jackson `convertValue()`가 `WorkFlowDefinition`으로 바꾼다.
축약형과 확장형은 여기서 **같은 모양**(`FieldDefinition`)이 된다.

```
WorkFlowDefinition.inputs : Map<String, FieldDefinition>
  "sqlList"       → FieldDefinition(type="list<string>", description=null)     ← 글자 → FieldDefinition.of(글자)
  "targetVersion" → FieldDefinition(type="string",       description="대상 ...") ← 맵   → 생성자(type, description)
  "maxRows"       → FieldDefinition(type="integer",      description=null)
  "options"       → FieldDefinition(type="object",       description=null)
```

**③ 타입 이름 검사** — `WorkFlowRegistry`가 `FieldTypes.invalidFields()`로 확인한다.
`list<str>`처럼 틀린 이름, `x:`처럼 빈 값이 하나라도 있으면 기동 실패다.

```
inputs의 타입 이름이 올바르지 않습니다: [x(list<str>)]
```

**④ `{{input.*}}` 참조 검사** — Workflow 안의 모든 템플릿에서 `input.` 뒤의 **첫 번째 이름**만 확인한다.

| 샘플 속 참조 | 위치 | 기동 시 | 이유 |
|---|---|---|---|
| `input.sqlList` | `check.forEach` | ✅ | 선언됨 |
| `{{input.targetVersion}}` | `review.input` | ✅ | 선언됨 |
| `{{input.options.strict}}` | `review.input` | ✅ | `options`만 검사한다. `strict`는 실행 중에 찾는다 |
| `{{input.message}}` | 어디든 | ✅ | 선언하지 않아도 항상 허용 |
| `{{input.targetVer}}` (오타) | — | ❌ 기동 실패 | `input.targetVer는 Workflow의 inputs에 선언되어 있지 않습니다(선언된 inputs = message, [sqlList, targetVersion, maxRows, options])` |
| Agent prompt의 `{targetVersion}` | Agent YAML | 검사 안 함 | Agent는 어느 Workflow에서 불릴지 모른다. 값이 없으면 호출할 때 예외 |

> `inputs`를 **생략하면 ④를 건너뛴다**. 그래서 오타가 기동 때 잡히지 않고, 실행 중에 그 step이 "값을 찾을 수 없습니다"로 실패한다.
> 오타를 미리 잡고 싶다면 쓰는 값을 `inputs`에 선언해 두는 것이 좋다.

**⑤~⑦ 요청 검사** — `WorkFlowController`가 이 순서로 확인한다. `/execute`(동기)와 `/submit`(비동기) 모두 **실행을 시작하기 전에** 검사하므로,
실패해도 실행 이력(FAILED)이 남지 않고 HTTP **400**으로 바로 돌아간다.

```
⑤ validateMessage()           message가 비었나?          → 400 "message는 필수입니다."
⑥ WorkFlowRegistry.resolve()  등록된 id인가? caller 허용?  → 거절
⑦ checkInputs()               inputs 계약을 지켰나?       → 400 (문제를 모두 모아서 한 번에)
```

⑦에서는 요청 JSON이 Jackson을 거쳐 **Java 값**이 된 뒤에 타입을 비교한다(`FieldTypes.matches()`).

```
요청 JSON                                   Java 값                                선언 타입         결과
"sqlList":       ["SELECT * FROM emp", ...]  → ArrayList<String>                    list<string>      ✅
"targetVersion": "16"                        → String                               string            ✅
"maxRows":       100                         → Integer                              integer           ✅
"options":       { "strict": true }          → LinkedHashMap                        object            ✅ (안쪽은 검사 안 함)
```

**⑧ 실행 컨텍스트 만들기** — `WorkFlowContext.create(message, variables)`가 아래 트리를 만든다.
이 트리가 실행 전체의 저장소이고, `{{ }}`는 모두 이 트리에서 값을 찾는다(DB에는 `CONTEXT_JSON` 컬럼으로 저장된다).

```
context
├── input                               ← variables를 그대로 복사 + message 추가
│   ├── sqlList       : ["SELECT * FROM emp", "SELECT name FROM dept"]
│   ├── targetVersion : "16"
│   ├── maxRows       : 100
│   ├── options       : { strict: true }
│   └── message       : "운영 반영 전에 SQL 검토 부탁합니다"
├── steps          : { }                ← 아직 실행된 step이 없다
└── previous
    └── text       : "운영 반영 전에 SQL 검토 부탁합니다"   ← 첫 step이 {{previous.text}}로 message를 받게
```

**⑨ step `check` — `forEach`로 리스트 나누기**

```
forEach: input.sqlList
   │
   └─▶ context.input.sqlList = ["SELECT * FROM emp", "SELECT name FROM dept"]   (리스트 아니면 step 실패)
          │
          ├── 반복 [0] ── 컨텍스트 복사본 + item = "SELECT * FROM emp"
          │                input { sql: "{{item}}" }  →  { sql: "SELECT * FROM emp" }
          │                StepInput(text=null, arguments={sql: "SELECT * FROM emp"}, workflowInput=context.input)
          │                → validateSqlSyntax(sql = "SELECT * FROM emp")
          │
          └── 반복 [1] ── (동시에) item = "SELECT name FROM dept" → validateSqlSyntax(sql = "SELECT name FROM dept")

결과 → context.steps.check = { text: "<반복별 text를 줄바꿈으로 이은 것>", data: {}, items: [ {...}, {...} ] }
```

**⑩ step `review` — `{{input.x}}`를 글자 안에 채우기** — AGENT의 `input`은 항상 글자가 된다.
숫자/boolean은 `toString()`, 리스트/맵은 **JSON 글자**로 끼워진다.

```
채우기 전 (YAML)                                     채운 뒤 (LLM 사용자 메시지)
──────────────────────────────────────────────────  ─────────────────────────────────────────────────
아래 SQL을 PostgreSQL {{input.targetVersion}} 기준…  아래 SQL을 PostgreSQL 16 기준으로 검토해 주세요.
조회 결과는 최대 {{input.maxRows}}건으로…           조회 결과는 최대 100건으로 제한해 주세요.
엄격 모드: {{input.options.strict}}                  엄격 모드: true

[SQL 목록]                                          [SQL 목록]
{{input.sqlList}}                                   ["SELECT * FROM emp","SELECT name FROM dept"]

[문법 검사 결과]                                     [문법 검사 결과]
{{steps.check.text}}                                <check step의 결과 text>
```

이 글자는 `StepInput(text=<위 글자>, arguments=null, workflowInput=context.input)`에 담겨 `AgentStepRunner`로 간다.

**⑪ Agent prompt의 `{x}` 채우기** — `AgentExecutor`가 `workflowInput`(= `context.input` 전체)으로 system prompt를 채운다.
`{{ }}`가 아니라 **`{ }` 한 겹**(Spring AI `PromptTemplate`)이다.

```
LLM에게 실제로 가는 메시지
┌─ system ───────────────────────────────────────────────────────┐
│ 당신은 PostgreSQL 16 전문 DBA입니다.                               │  ← {targetVersion}
│ 요청자의 원래 메시지는 "운영 반영 전에 SQL 검토 부탁합니다"입니다.      │  ← {message}
└────────────────────────────────────────────────────────────────┘
┌─ (같은 sessionId의 이전 대화) ────────────────────────────────────┐
└────────────────────────────────────────────────────────────────┘
┌─ user ─────────────────────────────────────────────────────────┐
│ 아래 SQL을 PostgreSQL 16 기준으로 검토해 주세요. ...               │  ← ⑩에서 채운 step input
└────────────────────────────────────────────────────────────────┘
```

> **같은 `input` 값이 LLM에게 가는 문은 두 개다.**
> - `{{input.x}}` in step `input` → **user 메시지**. "이번 step에서 처리할 데이터". step마다 다르게 쓸 수 있다.
> - `{x}` in Agent `prompt` → **system 메시지**. "이 Agent의 역할/규칙". 그 Agent를 쓰는 모든 step에 똑같이 들어간다.

**⑫ Workflow `output`** — 성공으로 끝나면 `{{steps.review.text}}`를 채워 응답의 `message`로 돌려준다.
`output`에서도 `{{input.x}}`를 쓸 수 있다(예: `"[{{input.targetVersion}}] {{steps.review.text}}"`).

##### 2.5.4 같은 값, 다른 자리 — 모양이 어떻게 바뀌나

같은 `input` 값이라도 **어디에 쓰느냐**에 따라 넘어가는 모양이 다르다.

| 요청 값 (선언 타입) | TOOL 인자 `"{{input.x}}"` (값 전체가 참조 하나) | 글자에 섞음 `"n={{input.x}}"` / AGENT `input` | Agent prompt `{x}` | `forEach: input.x` |
|---|---|---|---|---|
| `"16"` (`string`) | `"16"` | `n=16` | `16` | ❌ step 실패(리스트 아님) |
| `100` (`integer`) | `100` (**숫자 그대로**) | `n=100` | `100` | ❌ step 실패 |
| `true` (`boolean`) | `true` (**boolean 그대로**) | `n=true` | `true` | ❌ step 실패 |
| `["a","b"]` (`list<string>`) | `["a","b"]` (**리스트 그대로**) | `n=["a","b"]` (JSON 글자) | ⚠️ `ab` (구분자 없이 붙음) | ✅ 2회 반복, `{{item}}` = `"a"`, `"b"` |
| `{"strict":true}` (`object`) | `{strict: true}` (**맵 그대로**) | `n={"strict":true}` (JSON 글자) | ⚠️ `strict` (키 이름만) | ❌ step 실패 |
| `{"strict":true}`의 안쪽 | `"{{input.options.strict}}"` → `true` | `n={{input.options.strict}}` → `n=true` | `{options.strict}` → `true` | — |

- TOOL 인자에 리스트/숫자를 **타입 그대로** 넘기려면 따옴표 안에 `{{ }}` 하나만 적는다(`maxRows: "{{input.maxRows}}"` → `100`).
- Agent prompt `{x}`에는 **문자열 값**을 쓰는 것이 안전하다. 리스트/맵이 필요하면 step `input`의 `{{input.x}}`로 넘긴다.

##### 2.5.5 자주 쓰는 선언 패턴

**A. 선언 없음 — message 하나로 충분할 때**

```yaml
workflow:
  id: summarize
  # inputs 생략
  steps:
    - id: sum
      type: AGENT
      ref: summary-agent
      input: "{{input.message}}"          # input을 생략해도 첫 step은 message를 받는다
```

```json
{ "message": "요약할 긴 글..." }
```

**B. 문자열 하나 — 옵션 값을 받을 때**

```yaml
  inputs:
    language: string
  steps:
    - id: translate
      type: AGENT
      ref: translator-agent
      input: |
        아래 글을 {{input.language}}로 번역하세요.
        {{input.message}}
```

```json
{ "message": "안녕하세요", "variables": { "language": "English" } }
```

**C. 문자열 리스트 — 항목마다 같은 일을 할 때 (`forEach`)**

```yaml
  inputs:
    sqlList: list<string>
  steps:
    - id: validate-each
      type: TOOL
      ref: validateSqlSyntax
      forEach: input.sqlList
      input:
        sql: "{{item}}"
```

```json
{ "message": "검사", "variables": { "sqlList": ["SELECT 1 FROM dual", "SELEC 1 FROM dual"] } }
```

**D. 객체 리스트 — 항목마다 여러 값이 필요할 때 (`list<object>` + `{{item.키}}`)**

```yaml
  inputs:
    tables: list<object>
  steps:
    - id: describe-each
      type: AGENT
      ref: table-doc-agent
      forEach: input.tables
      itemVariable: t                     # {{item}} 대신 {{t}}
      input: |
        테이블 {{t.name}}의 설명을 작성하세요.
        컬럼: {{t.columns}}
```

```json
{ "message": "문서화", "variables": { "tables": [
    { "name": "EMP",  "columns": ["EMPNO", "ENAME"] },
    { "name": "DEPT", "columns": ["DEPTNO", "DNAME"] } ] } }
```

- `list<object>`는 항목이 **맵인지만** 검사한다. `name`/`columns`가 있는지는 실행 중에 찾고, 없으면 그 반복이 실패한다.

**E. 숫자/boolean — Tool 인자로 타입 그대로 넘길 때**

```yaml
  inputs:
    limit: integer
    dryRun: boolean
  steps:
    - id: run
      type: TOOL
      ref: <tool-name>
      input:
        limit: "{{input.limit}}"          # → 100 (숫자)
        dryRun: "{{input.dryRun}}"        # → true (boolean)
        label: "limit={{input.limit}}"    # → "limit=100" (섞으면 글자)
```

```json
{ "message": "실행", "variables": { "limit": 100, "dryRun": true } }
```

- `integer`에 `100.0`이나 `"100"`을 보내면 400이다. `number`는 정수/실수를 모두 받는다.

**F. 있어도 되고 없어도 되는 값 — `object`로 묶고 `??`로 대체값**

`inputs`에 선언한 값은 모두 필수라서, "선택값"은 `object` 하나로 묶어 두고 안쪽 키를 `??`로 꺼낸다.
기동 시에는 `options`까지만 검사하므로 안쪽 키는 요청에 없어도 된다.

```yaml
  inputs:
    options: object
  steps:
    - id: answer
      type: AGENT
      ref: <agent-id>
      input: |
        말투: {{input.options.tone ?? input.message}}
        ...
```

```json
{ "message": "...", "variables": { "options": {} } }                       ← tone 없음 → ?? 오른쪽 값
{ "message": "...", "variables": { "options": { "tone": "정중하게" } } }   ← tone 있음
```

- `??`의 오른쪽 끝은 **반드시 있는 값**(`input.message` 등)으로 둔다. 모든 경로가 없으면 step이 실패한다.
- `options` 자체는 필수다. 선택값이 없어도 `"options": {}`는 보내야 한다.

##### 2.5.6 타입별로 통과하는 값

요청 JSON이 Jackson으로 바뀐 뒤의 Java 타입 기준이다(`FieldTypes.matches()`).

| 타입 | 통과 | 통과 못 함 |
|---|---|---|
| `string` | `"abc"`, `""` | `123`, `true` |
| `number` | `3`, `3.5` (모든 `Number`) | `"3.5"` |
| `integer` | `3` (`Integer`/`Long`/`BigInteger`) | `3.0`(Double이 된다), `"3"` |
| `boolean` | `true` | `"true"`, `1` |
| `object` | `{...}` (안쪽 모양은 검사하지 않음) | 배열, 문자열 |
| `list<T>` | 모든 항목이 T에 맞는 배열. **빈 배열 `[]`도 통과** | 항목 하나라도 타입이 다름 |

##### 2.5.7 요청별 결과

샘플 `sql-review`(2.5.2)에 여러 요청을 보냈을 때:

| 보낸 `variables` | 결과 |
|---|---|
| 2.5.2의 요청 그대로 | 정상 실행 |
| `sqlList` 빠짐 | 400 `workflow[sql-review]의 inputs 계약을 지키지 않았습니다: [sqlList(없음)]` |
| `"sqlList": "SELECT 1"` (리스트 아님) | 400 `[sqlList(list<string> 타입이어야 함)]` |
| `"sqlList": ["SELECT 1", 1]` (항목 하나만 숫자) | 400 `[sqlList(list<string> 타입이어야 함)]` |
| `"sqlList": []` | 통과. `check`는 0회 실행 후 성공 |
| `"maxRows": "100"` (글자) | 400 `[maxRows(integer 타입이어야 함)]` |
| `"maxRows": null` | 400 `[maxRows(없음)]` |
| `sqlList`와 `maxRows`가 모두 틀림 | 400 한 번에 모두 `[sqlList(없음), maxRows(integer 타입이어야 함)]` |
| `"options": {}` | 통과. 그러나 `{{input.options.strict}}`를 찾지 못해 `review` step이 실패 → `onFailure`가 없으므로 Workflow FAILED |
| 선언하지 않은 `"extra": "x"`를 더 보냄 | 통과. `context.input.extra`로 들어간다(템플릿 `{{input.extra}}`는 기동 실패라 못 쓰고, Agent prompt `{extra}`로는 쓸 수 있다) |
| `"message": "덮어쓰기"`를 variables에 넣음 | 요청 최상위의 `message`가 **덮어쓴다** |
| `variables` 자체를 생략 | 400. 선언된 값이 모두 `(없음)` |

##### 2.5.8 선언 쪽 경우별 정리

| 경우 | 결과 |
|---|---|
| `inputs` 생략 또는 `{}` | 요청 검사 없음. `{{input.아무이름}}`도 기동 시 검사하지 않음. 실행 중 값이 없으면 **그 step이 실패**하고 `onFailure`를 따름(`??`로 대체값 가능) |
| 잘못된 타입 이름(`list<str>`) 또는 빈 값(`x:`) | 기동 실패 |
| `description` 적음 | **설명용일 뿐**이다. 검사에도 쓰지 않고 LLM에게도 전달되지 않는다(같은 `FieldDefinition`이라도 `output.schema`에서는 LLM에게 전달된다) |
| `inputs`에 `message`를 선언 | 코드는 허용하며 `variables`가 아니라 요청 `message`로 검사한다. message는 항상 문자열이라 `string`이 아니면 **모든 요청이 400**. 선언하지 않는 것이 맞다 |
| `itemVariable`을 `input`으로 지음 | 그 반복 안에서 `{{input.x}}`가 항목을 가리키게 된다. 쓰지 않는다([3.12](#312-itemvariable)) |

샘플: `sample/sample-foreach-parallel.yml` — `sqlList: list<string>`을 선언하고 `forEach: input.sqlList`로 항목 수만큼 병렬 실행한다.

#### 2.6 `output`

```yaml
  output: "{{steps.convert.data.sql}}"
```

- Workflow가 **성공(DONE)으로 끝났을 때** 돌려줄 최종 결과의 템플릿이다. 동기 실행 응답의 `message`, 비동기 상태 조회의 결과가 이 값이다.
- 템플릿 문법은 [5절](#5-템플릿-참조-문법)과 같다. 단, 항상 **글자**로 채운다(`renderText`). 맵/리스트를 가리키면 JSON 글자가 된다.
- `{{item}}`은 쓸 수 없다(forEach step 밖이기 때문).
- 기동 시 `{{ }}` 안의 경로를 모두 검사한다(없는 step id, 없는 data 키 등은 기동 실패).

| 경우 | 결과 |
|---|---|
| 생략 | 마지막으로 끝난 step이 넘긴 메시지가 결과다. 보통은 그 step의 `text`이고, `onFailure: SUCCESS`로 끝났으면 그 step의 **실패 사유**가 결과가 된다 |
| 여러 줄/여러 참조를 섞음 | 각 `{{ }}`를 글자로 끼운 문자열이 결과 |
| 가리킨 값이 없음(실행되지 않은 step, null 값) | 성공 직전에 FAILED `Workflow output을 만들지 못했습니다` |
| `??` 사용(`{{steps.fix.data.sql ?? input.message}}`) | 왼쪽부터 처음으로 값이 있는 것을 씀. 분기 때문에 실행되지 않았을 수 있는 step을 가리킬 때 쓴다 |
| FAILED 또는 WAITING_APPROVAL로 끝남 | `output`은 쓰이지 않는다 |

#### 2.7 `steps`

```yaml
  steps:
    - id: analyze
      type: AGENT
      ...
```

- 실행할 step 목록이다. 항목 하나의 자세한 항목은 [3절](#3-step-항목)에 있다.
- **목록 순서가 기본 실행 순서**다. `onSuccess`를 생략하면 목록상 다음 step으로 가고, 마지막 step이 성공하면 Workflow가 성공으로 끝난다.
- `onSuccess`/`onFailure`/`routes`로 앞 step을 가리키면 루프, 뒤 step을 가리키면 건너뛰기가 된다. 순서와 상관없이 id로 이동한다.
- 서로 다른 step을 **동시에** 실행하는 방법은 없다. 병렬은 같은 step을 데이터만 바꿔 반복하는 `forEach` 하나뿐이다.

| 경우 | 결과 |
|---|---|
| 생략하거나 빈 리스트 | 기동 실패 `id와 steps가 모두 있어야 합니다` |
| step 하나에 `id`나 `type`이 없음 | 기동 실패 `모든 step은 id와 type이 있어야 합니다` |
| step id 중복 | 기동 실패 `step id가 중복되었습니다` |

### 3 Step 항목

`steps:` 리스트의 항목 하나(`common.definition.StepDefinition`).

| 항목 | 설명 |
|---|---|
| `id` | step 이름(필수). `onSuccess`/`onFailure`/`routes`와 `{{steps.<id>...}}` 참조가 이 이름을 쓴다. Workflow 안에서 중복 불가 |
| `type` | `AGENT` / `TOOL` / `SUPERVISOR` / `ROUTER` / `APPROVAL` (필수) |
| `ref` | AGENT/SUPERVISOR/ROUTER는 **Agent id**, TOOL은 **Tool 이름**(`@Tool` 메서드 이름 또는 MCP Tool 이름). APPROVAL은 쓰지 않는다 |
| `input` | 이 step에 넣을 값의 템플릿. AGENT류는 문자열(LLM 사용자 메시지), TOOL은 맵(Tool 인자) |
| `output.schema` | AGENT 전용. LLM이 이 모양의 JSON으로 답하게 강제하고, 그 JSON이 `data`가 된다. 선언한 필드는 모두 필수 |
| `output.parse` | TOOL 전용. `text`(기본, data 없음) / `json`(응답 JSON이 data) / `lines`(`{lines: [...]}`). 대소문자 무관 |
| `output.pattern` | TOOL + `parse: lines` 전용. 이 정규식에 맞는 줄만 남긴다. 괄호 그룹이 있으면 첫 번째 그룹만 값으로 쓴다 |
| `onSuccess` | 성공 시 다음 step id 또는 `SUCCESS`/`FAIL`. 비우면 목록상 다음 step(마지막이면 성공 종료) |
| `onFailure` | 실패 시 다음 step id 또는 `SUCCESS`/`FAIL`. 비우면 Workflow 실패. 앞쪽 step id를 적으면 재시도 루프 |
| `routes` | ROUTER 전용(필수, 1개 이상). `route 이름: 다음 step id`(또는 `SUCCESS`/`FAIL`) |
| `forEach` | 리스트를 가리키는 경로(`{{ }}` 없이 적음, 예: `input.sqlList`). 항목 수만큼 이 step을 동시에 실행한다 |
| `itemVariable` | `forEach` 반복에서 항목을 받는 이름. 비우면 `item`(→ `{{item}}`) |
| `approverRole` | APPROVAL 전용 기록용 값(누가 승인해야 하는지). 서버가 실제 권한을 검사하지는 않는다 |

**StepType별로 쓸 수 있는 항목** (✅ 사용, ⭕ 선택, ❌ 쓰면 기동 실패, — 무시됨)

| 항목 | AGENT | SUPERVISOR | ROUTER | TOOL | APPROVAL |
|---|---|---|---|---|---|
| `ref` | ✅ Agent id | ✅ Agent id | ✅ Agent id | ✅ Tool 이름 | — |
| `input` | ⭕ 문자열 | ⭕ 문자열 | ⭕ 문자열 | ⭕ 맵 | ❌ |
| `output.schema` | ⭕ | ❌ | ❌ | ❌ | ❌ |
| `output.parse` | ❌ | ❌ | ❌ | ⭕ | ❌ |
| `output.pattern` | ❌ | ❌ | ❌ | ⭕ | ❌ |
| `onSuccess`| ⭕ | ⭕ | — | ⭕ | ⭕ |
| `onFailure` | ⭕ | ⭕ | ⭕ (route를 고르지 못했을 때) | ⭕ | ⭕ |
| `routes` | — | — | ✅ | — | — |
| `forEach` | ⭕ | ⭕ | ❌ | ⭕ | ❌ |
| `itemVariable` | ⭕ | ⭕ | ❌ | ⭕ | ❌ |
| `approverRole` | — | — | — | — | ⭕ |

**각 step이 내놓는 `data` 키** — `{{steps.<id>.data.<키>}}`로 참조할 수 있는 키. 여기 없는 키를 참조하면 기동이 실패한다.

| step | `data` 키 |
|---|---|
| AGENT + `output.schema` | schema에 선언한 필드들 |
| AGENT(schema 없음) | 없음 |
| TOOL + `parse: json` | 알 수 없음(Tool 응답에 따라 다름 → 실행 중에 검사) |
| TOOL + `parse: lines` | `lines` |
| TOOL(parse 없음/`text`) | 없음 |
| SUPERVISOR | `pass`, `reason` |
| ROUTER | `route`, `reason` |
| APPROVAL | `approved`, `approver`, `comment` |
| `forEach` step | 없음. 반복별 결과는 `steps.<id>.items.<번호>.data.<키>` |

**모든 step이 남기는 결과** — step이 끝나면 컨텍스트의 `steps.<id>`와 `previous`에 아래 모양이 남는다(`WorkFlowContext.stepRecord()`).
같은 step이 루프로 다시 실행되면 **덮어쓴다**.

```
steps:
  <id>:
    input: 템플릿을 채운 뒤 실제로 받은 입력(AGENT류는 문자열, TOOL은 인자 맵, 채우다 실패했으면 null)
    text:  결과 텍스트
    data:  구조화된 결과(없으면 {})
    error: 실패 사유(성공이면 null)
    items: [ {input, text, data, error}, ... ]   ← forEach step만
```

| step | `text` | `data` | 실패하면 `error`에 |
|---|---|---|---|
| AGENT(schema 없음) | LLM 답변 원문 | `{}` | (실패하지 않음. 예외면 FAILED) |
| AGENT + `output.schema` | data를 JSON 글자로 | schema대로 읽은 값 | `Agent 응답을 output.schema 모양으로 읽지 못했습니다 - ...` |
| TOOL | Tool 응답 원문(성공/실패 모두) | `parse`에 따름 | `ToolOutcome.message`, 없으면 응답 원문 |
| SUPERVISOR | 받은 input 그대로 | `{pass: true, reason}` | LLM이 적은 `reason` |
| ROUTER | 받은 input 그대로 | `{route, reason}` | `라우팅 Agent가 route를 고르지 않았습니다` 등 |
| APPROVAL | 받은 input(직전 step의 text) 그대로 | `{approved: true, approver, comment}` | 반려 사유(`comment`, 비었으면 `(사유 없음)`) |
| forEach step | 반복별 text를 줄바꿈으로 이은 것 | `{}` | 실패한 반복마다 `id[i]: 사유`를 줄바꿈으로 이은 것 |

**실패가 `onFailure`로 가는 경우와 Workflow가 바로 FAILED가 되는 경우**

"값이 틀렸다"는 **비즈니스 실패**만 `onFailure`로 간다. 설정 오류나 시스템 오류는 `onFailure`를 무시하고 그 자리에서 FAILED로 끝난다.
재시도 루프가 시스템 오류를 되풀이하지 않게 하기 위해서다.

| `onFailure`를 따름(step 실패) | `onFailure`를 무시하고 바로 FAILED |
|---|---|
| `input` 템플릿이 가리키는 값이 없음 | `ref`의 Agent가 등록되지 않았거나 caller에게 허용되지 않음 |
| `forEach` 경로의 값이 없거나 리스트가 아님 | TOOL의 Tool 이름이 없거나 caller 화이트리스트에 없음 |
| `forEach` 반복 중 하나라도 실패 | Tool 메서드가 예외를 던짐 |
| TOOL이 실패로 답함(`ToolOutcome.success=false`, `실패`로 시작하는 문자열) | schema 없는 AGENT의 LLM 호출 예외(API 오류, prompt `{변수}` 누락, RAG 꺼짐 등) |
| TOOL `parse: json`인데 응답이 JSON이 아님 | `forEach` 반복 중 하나가 예외를 던짐 |
| AGENT `output.schema`를 LLM이 지키지 않음(이 경우는 LLM 호출 예외도 여기로 온다) | ROUTER가 고른 route가 `routes`에 없음 |
| SUPERVISOR `pass=false`, 또는 응답을 읽지 못함(호출 예외 포함) | `onSuccess`/`onFailure`/`routes`가 없는 step id를 가리킴 |
| ROUTER가 route를 고르지 못함(호출 예외 포함) | `maxIterations` 초과 |
| APPROVAL 반려 | Workflow `output`을 채우지 못함 |

> 아래 3.1~3.13은 step 항목마다 자세히 적었다. 기동 시 검사는 `WorkFlowRegistry.validateStepShape()`/`validateExpression()`,
> 실행 동작은 `WorkFlowExecutor`와 각 `StepRunner`가 한다.

#### 3.1 `id`

```yaml
    - id: validate
```

- step 이름이다. `onSuccess`/`onFailure`/`routes`의 이동 대상, `{{steps.<id>...}}` 참조, 실행 이력(`STEP_ID`)에 쓰인다.
- Workflow 안에서만 유일하면 된다. 다른 Workflow의 step id와 겹쳐도 된다.
- 템플릿 경로는 `.`으로 나누므로 **id에 `.`을 넣지 않는다**(`steps.a.b.text`는 step `a`로 읽힌다). `-`는 괜찮다.
- 예약어 `SUCCESS`/`FAIL`을 id로 쓰지 않는다. 이동 대상으로 적으면 step이 아니라 종료로 읽힌다.
- forEach 반복의 실행 이력은 `id[0]`, `id[1]`...로 남는다.

| 경우 | 결과 |
|---|---|
| 생략 | 기동 실패 `모든 step은 id와 type이 있어야 합니다` |
| 같은 Workflow 안에서 중복 | 기동 실패 `step id가 중복되었습니다` |
| 없는 id를 `{{steps.x...}}`로 참조 | 기동 실패 `이 Workflow에 'x' step이 없습니다` |

#### 3.2 `type`

```yaml
      type: AGENT        # AGENT | TOOL | SUPERVISOR | ROUTER | APPROVAL
```

| 값 | 하는 일 | 계열(`Kind`) | 담당 러너 |
|---|---|---|---|
| `AGENT` | Agent(LLM)를 한 번 부른다 | AGENT_CALL | `AgentStepRunner` |
| `SUPERVISOR` | Agent에게 pass/fail을 판정하게 한다 | AGENT_CALL | `AgentStepRunner` |
| `ROUTER` | Agent에게 여러 갈래 중 하나를 고르게 한다 | AGENT_CALL | `AgentStepRunner` |
| `TOOL` | Tool 하나를 LLM 없이 이름으로 직접 부른다 | DETERMINISTIC | `ToolStepRunner` |
| `APPROVAL` | 사람의 승인/반려를 기다린다 | DETERMINISTIC | `ApprovalStepRunner` |

- **대문자로 정확히** 적는다. `agent`, `Agent`는 기동 실패(YAML 바인딩 오류)다(`output.parse`와 달리 대소문자를 가리지 않는 처리가 없다).
- 생략하면 기동 실패. 어떤 항목을 같이 쓸 수 있는지는 위의 "StepType별로 쓸 수 있는 항목" 표를 따른다.

#### 3.3 `ref`

```yaml
      ref: sample-structured-extract-agent   # AGENT/SUPERVISOR/ROUTER → Agent id
      ref: validateSqlSyntax                 # TOOL → Tool 이름
```

- AGENT/SUPERVISOR/ROUTER: `agents/**/*.yml`의 `agent.id`.
- TOOL: `@Tool` 메서드 이름(메서드 이름이 기본값, `@Tool(name=...)`이면 그 이름) 또는 MCP 서버가 알려 준 Tool 이름(예: `list_directory`).
- APPROVAL: 쓰지 않는다(적어도 무시된다).
- ⚠️ **`ref`는 기동 시 검사하지 않는다.** 이름이 틀렸는지는 그 step이 처음 실행될 때 드러나고, 그때는 `onFailure`를 무시하고 바로 FAILED다.
  새 Workflow를 만들면 모든 분기를 한 번씩 실행해 보는 것이 안전하다.

| 경우 | 결과 |
|---|---|
| 등록되지 않은 Agent id | 실행 중 FAILED `등록되지 않은 agent입니다: x` |
| Agent의 `allowedCallers`가 이 caller를 막음 | 실행 중 FAILED `agent[x]는 caller[y]에게 허용되지 않았습니다` |
| 없는 Tool 이름, 또는 `dstone.ai.tool.allowed-by-caller`가 이 caller에게 그 Tool을 막음 | 실행 중 FAILED `caller[y]가 쓸 수 있는 Tool 중 'x'가 없습니다` |
| MCP Tool인데 MCP 서버 연결에 실패해 Tool이 등록되지 않음 | 위와 같은 FAILED |
| AGENT류 step에서 `ref` 생략 | 실행 중 FAILED(Agent를 찾는 중 예외) |

- TOOL step은 Agent의 `toolsEnabled`와 관계없이 Tool을 부른다. Tool 화이트리스트(`allowed-by-caller`)만 적용된다.

#### 3.4 `input`

step에 넣을 값의 템플릿이다. step이 실행되기 **직전에** 컨텍스트로 채운다(`WorkFlowExecutor.renderInput()`). 문법은 [5절](#5-템플릿-참조-문법).

**step 종류별 모양**

| step | YAML 모양 | 채운 결과 | 생략하면 |
|---|---|---|---|
| AGENT / SUPERVISOR / ROUTER | 문자열(여러 줄은 `\|`) | 문자열 → LLM **사용자 메시지** | `{{previous.text}}`(직전 step의 text, 첫 step이면 요청 `message`) |
| TOOL | 맵(인자 이름: 값) | 맵 → JSON으로 바꿔 Tool 인자 | 빈 인자 `{}` |
| APPROVAL | 쓸 수 없음 | — | 항상 `{{previous.text}}` |

```yaml
      # AGENT류: 문자열. 맵/리스트를 가리키면 JSON 글자로 끼워진다
      input: |
        [분석 결과]
        {{steps.analyze.text}}

        [대상 테이블]
        {{steps.extract.data.tables}}

      # TOOL: 맵. 값 전체가 {{ }} 하나면 원래 타입(리스트, 숫자, 맵) 그대로 넘어간다
      input:
        sql: "{{steps.extract.data.sql}}"
        tables: "{{steps.extract.data.tables}}"     # 리스트 그대로
        label: "대상: {{input.message}}"             # 섞여 있으면 글자
        limit: 100                                   # 템플릿이 아닌 값은 그대로
        options:                                     # 맵/리스트 안쪽도 같은 규칙으로 채운다
          strict: true
```

- 템플릿이 가리키는 값이 없으면(경로가 없거나 null) 빈 글자로 넘어가지 않는다. StepRunner를 부르지 않고 **step 실패**
  `input을 채우지 못했습니다 - {{...}}: 값을 찾을 수 없습니다`가 되고 `onFailure`를 따른다. 대체값이 필요하면 `??`를 쓴다.
- 채운 값은 `steps.<id>.input`에 남는다. 그래서 다음 step이 `{{steps.validate.input.sql}}`처럼 "실패한 입력값"을 다시 꺼낼 수 있다.
- system prompt의 `{변수}`는 `input`과 관계없이 항상 컨텍스트의 `input`(요청 message+variables)으로 채운다.
  "이번에 처리할 데이터"는 `input`으로, "Agent의 역할"은 prompt로 나누는 것이 원칙이다.
- YAML에서 `{{`로 시작하는 값은 **반드시 따옴표로 감싼다**(`sql: "{{item}}"`). 따옴표가 없으면 YAML이 `{`를 맵으로 읽어 버려서 기동에 실패하거나 템플릿이 아닌 엉뚱한 값이 된다.

| 경우 | 결과 |
|---|---|
| AGENT류에 맵을 적음 | 기동 실패 `AGENT step의 input은 문자열(LLM에게 보낼 메시지)이어야 합니다` |
| TOOL에 문자열을 적음 | 기동 실패 `TOOL step의 input은 맵(Tool 인자 이름: 값)이어야 합니다` |
| APPROVAL에 적음 | 기동 실패 `APPROVAL step은 input을 쓸 수 없습니다` |
| 참조 경로가 틀림(없는 step, 없는 data 키, 선언 안 된 `input.x`) | 기동 실패([5절](#5-템플릿-참조-문법) 기동 시 검사) |
| 첫 step에서 `{{previous.data.x}}` | 기동은 통과. 실행 중 값이 없어 step 실패. `{{previous.data.x ?? input.message}}`로 쓴다 |
| 분기로 아직 실행되지 않은 step 참조 | 기동은 통과. 실행 중 step 실패 |
| TOOL 인자 이름이 Tool 메서드의 파라미터 이름과 다름 | 엔진은 이름을 검사하지 않는다. Tool 쪽에서 그 인자가 비어서(null) 들어가거나 Spring AI가 인자 변환 중 예외를 던진다(예외면 FAILED) |

#### 3.5 `output.schema` (AGENT 전용)

```yaml
      output:
        schema:
          sql:
            type: string
            description: 입력 문장에서 뽑아낸 SQL 문 하나
          tables: list<string>
```

- LLM이 **이 모양의 JSON 객체 하나로만** 답하게 한다. 값 타입은 [4절](#4-값-타입-inputs-outputschema)과 같다.
- 동작(`runtime.agent.SchemaOutputConverter`):
  1. schema를 JSON Schema로 바꿔(`FieldTypes.toJsonSchema()`) "이 JSON Schema를 지키는 JSON 객체 하나로만 답하라"는 지시문을 프롬프트 끝에 붙인다.
     **선언한 필드는 모두 필수(required)**이고, `description`도 그대로 전달된다. prompt에 JSON 형식을 따로 적을 필요가 없다.
  2. 답 전체가 코드펜스(```` ```json ... ``` ````)로 감싸여 있으면 벗겨 낸다.
  3. JSON 객체로 읽고, 선언한 필드마다 값이 있는지와 타입이 맞는지 확인한다.
- 성공하면 `data` = 읽은 JSON 객체, `text` = 그 JSON 글자. 다음 step은 `{{steps.<id>.data.<필드>}}`로 꺼낸다.
- provider 고유의 structured output 기능은 쓰지 않는다. 그래서 어느 provider든 똑같이 동작하지만, 모양을 100% 보장하지는 않는다.

| 경우 | 결과 |
|---|---|
| AGENT가 아닌 step에 적음 | 기동 실패 `output.schema는 AGENT step에서만 쓸 수 있습니다` |
| `schema: {}` | 기동 실패 `output.schema에 필드가 하나도 없습니다` |
| 타입 이름이 틀림 | 기동 실패 `output.schema의 타입 이름이 올바르지 않습니다` |
| 선언하지 않은 필드를 `{{steps.<id>.data.x}}`로 참조 | 기동 실패 `'id' step의 data에는 [...]만 있습니다` |
| LLM이 JSON이 아닌 글로 답함 | step 실패 → `onFailure` |
| 필드가 빠졌거나 `null`, 또는 타입이 다름 | step 실패 `LLM 응답이 output.schema를 지키지 않았습니다: [sql(없음)]` → `onFailure` |
| LLM이 선언하지 않은 필드를 더 넣음 | 허용(data에 남지만 기동 시 검사 때문에 템플릿으로는 꺼낼 수 없다) |
| LLM 호출 자체가 예외(API 오류 등) | schema 없는 AGENT와 달리 **step 실패**로 처리되어 `onFailure`를 따른다 |

- schema가 **없는** AGENT는 LLM 답변을 그대로 `text`로 남기고 항상 성공이다. `data`가 없으므로 `{{steps.<id>.data.x}}`는 기동 실패다.

#### 3.6 `output.parse` (TOOL 전용)

```yaml
      output:
        parse: json        # text(기본) | json | lines, 대소문자 무관
```

Tool 응답 텍스트를 `data`로 정리하는 방법이다(`common.consts.ToolParse`). 어떤 값이든 `text`에는 **응답 원문**이 그대로 남는다.

| 값 | `data` | 기동 시 `{{steps.<id>.data.x}}` 검사 |
|---|---|---|
| 생략 / `text` | `{}` | 어떤 키든 기동 실패 |
| `json` | 응답이 JSON 객체면 그 객체, JSON 배열이면 `{items: [...]}` | 키를 미리 알 수 없어 **검사하지 않음**(실행 중 없으면 step 실패) |
| `lines` | `{lines: [...]}`(빈 줄은 버리고 앞뒤 공백 제거) | `lines`만 허용 |

- 성공/실패 판정은 `parse`보다 **먼저** 한다(`ToolStepRunner`).
  - 응답이 `{"success": ..., "message": ...}` 모양(`runtime.tool.ToolOutcome`)이면 `success` 값으로 판정한다.
  - 아니면 응답이 `실패`로 시작하는지로 판정한다.
  - 실패면 `parse`를 하지 않는다.
- Tool 응답은 `ToolExecutor`가 먼저 풀어 준다. 로컬 `@AiTool`이 String을 돌려주면 그 문자열이, MCP Tool이면
  content 배열의 `text`를 줄바꿈으로 이은 문자열이 응답 원문이다.

| 경우 | 결과 |
|---|---|
| TOOL이 아닌 step에 적음 | 기동 실패 `output.parse/pattern은 TOOL step에서만 쓸 수 있습니다` |
| `text`/`json`/`lines`가 아닌 값 | 기동 실패 `output.parse에는 text/json/lines 중 하나만 쓸 수 있습니다` |
| `json`인데 응답이 JSON 객체/배열이 아님 | step 실패 `output.parse=json인데 Tool 응답이 JSON 객체나 배열이 아닙니다` → `onFailure` |
| `json`인데 응답 JSON에 `"success": false`가 있음 | ToolOutcome으로 읽혀 **실패**로 판정된다. 구조화된 결과를 내는 Tool은 `success` 필드 이름을 피한다 |
| `json` + 배열 응답 | `{{steps.<id>.data.items}}`, `forEach: steps.<id>.data.items`로 쓴다 |
| `lines`인데 응답이 비어 있음 | `data.lines = []`로 성공 |

#### 3.7 `output.pattern` (TOOL + `parse: lines` 전용)

```yaml
      output:
        parse: lines
        pattern: '^\[FILE\] (.+)$'
```

- 줄마다(앞뒤 공백을 뗀 뒤) 정규식을 적용해 **맞는 줄만** 남긴다.
- 괄호 그룹이 있으면 줄 전체 대신 **첫 번째 그룹**만 값으로 쓴다. 위 예는 `[FILE] notes.txt` → `notes.txt`.
- `Matcher.find()`를 쓰므로 줄의 **일부만 맞아도** 맞는 것으로 본다. 줄 전체를 맞추려면 `^...$`를 붙인다.
- 역슬래시가 들어가므로 YAML에서는 **작은따옴표**로 감싼다(큰따옴표면 `\\[`처럼 두 번 적어야 한다).

| 경우 | 결과 |
|---|---|
| `parse: lines` 없이 적음 | 기동 실패 `output.pattern은 output.parse: lines와 함께만 쓸 수 있습니다` |
| 올바르지 않은 정규식 | 기동 실패 `output.pattern이 올바른 정규식이 아닙니다` |
| 맞는 줄이 하나도 없음 | `data.lines = []`로 성공(실패가 아니다). 이 결과로 `forEach`하면 0회 실행 후 성공 |

#### 3.8 `onSuccess`

```yaml
      onSuccess: validate      # 다른 step id | SUCCESS | FAIL
```

| 값 | 동작 |
|---|---|
| 생략 | 목록상 다음 step. 마지막 step이면 Workflow 성공(DONE) |
| 뒤쪽 step id | 그 step으로 건너뛴다(`NextStep`) |
| 앞쪽 step id 또는 자기 자신 | 그 step으로 되돌아간다(`Loop`). `maxIterations`가 상한 |
| `SUCCESS` | 그 자리에서 Workflow 성공. `output`이 없으면 이 step의 text가 결과 |
| `FAIL` | 그 자리에서 Workflow 실패. 이 step의 **text가 실패 메시지**가 된다 |

- 예약어는 **대문자로 정확히** 적는다. `success`는 step id로 읽혀서, 그런 step이 없으면 실행 중 FAILED `없는 step id로 이동하려 했습니다`.
- ⚠️ 이동 대상 step id는 **기동 시 검사하지 않는다**. 오타는 그 분기를 실제로 탈 때 FAILED로 드러난다.
- ROUTER는 `onSuccess`를 쓰지 않는다(적어도 무시). 성공하면 `routes`로 간다.

#### 3.9 `onFailure`

```yaml
      onFailure: fix           # 다른 step id | SUCCESS | FAIL
```

| 값 | 동작 |
|---|---|
| 생략 | Workflow 실패. 메시지는 `step[id]가 실패했고 onFailure가 지정되지 않았습니다: <실패 사유>` |
| 앞쪽 step id | **재시도 루프**. 되돌아간 step은 `{{steps.<실패한 id>.error}}`로 사유를, `{{steps.<id>.input.<인자>}}`로 실패한 입력을 읽는다 |
| 뒤쪽 step id | 실패 처리용 step으로 건너뛴다 |
| `SUCCESS` | 실패했지만 Workflow를 **성공**으로 끝낸다. `output`이 없으면 **실패 사유가 결과**가 된다 |
| `FAIL` | Workflow 실패. 메시지는 실패 사유 그대로 |

- `onFailure`는 **비즈니스 실패**에만 적용된다. 설정/시스템 오류는 무시하고 바로 FAILED다(위 "onFailure로 가는 경우" 표).
- ROUTER에서는 LLM이 route를 **고르지 못했을 때**(응답 모양이 깨졌거나 route가 비었을 때) 쓰인다. 고른 route가 `routes`에 없으면 `onFailure`와 관계없이 FAILED다.
- ⚠️ APPROVAL의 `onFailure`로 **앞 step으로 되돌리는 루프는 쓰지 않는다.** 한 번 기록된 결정(`approvals.<id>`)은 실행이
  끝날 때까지 지워지지 않아서, 되돌아와도 같은 APPROVAL step이 예전 반려 결정을 다시 읽고 **즉시 또 반려**된다.
  `onFailure: FAIL`로 끝내고, 고쳐서 다시 하려면 새 실행을 시작한다(`testApp/testApp-sdlc.yml` 참고).

#### 3.10 `routes` (ROUTER 전용)

```yaml
      routes:
        billing: billing-step
        technical: technical-step
        other: SUCCESS
```

- `route 이름: 이동할 곳(step id 또는 SUCCESS/FAIL)` 맵이다.
- ROUTER Agent는 `{route, reason}` JSON으로 답하도록 강제된다(`runtime.agent.RouteDecision`). 엔진은 `route` 값을 이 맵의 **키와 정확히**
  (대소문자까지) 비교한다.
- 엔진은 쓸 수 있는 route 이름을 LLM에게 알려 주지 않는다. **Agent prompt에 키 목록을 그대로 적어 둬야 한다**([agent-yml-guide.md](../agents/agent-yml-guide.md)의 ROUTER용 Agent).
- ROUTER의 `text`는 받은 input 그대로다. 그래서 갈라진 다음 step이 `input`을 생략하면 원래 메시지를 그대로 받는다.
  고른 경로와 이유는 `{{steps.<id>.data.route}}`, `{{steps.<id>.data.reason}}`.

| 경우 | 결과 |
|---|---|
| ROUTER인데 생략 또는 `{}` | 기동 실패 `ROUTER step은 routes를 최소 1개 이상 정의해야 합니다` |
| ROUTER가 아닌 step에 적음 | 무시된다 |
| LLM이 routes에 없는 이름을 고름(오타, 지어낸 이름) | 바로 FAILED `route['x']가 routes에 정의되어 있지 않습니다(정의된 route=[...])` |
| LLM 응답을 route/reason으로 읽지 못함, route가 빔 | step 실패 → `onFailure`(없으면 Workflow 실패) |
| 값(이동 대상)이 없는 step id | 기동은 통과. 그 route를 탈 때 FAILED |

#### 3.11 `forEach`

```yaml
      forEach: input.sqlList              # {{ }} 없이 경로만
      forEach: steps.list.data.lines      # 앞 step의 결과도 된다
      forEach: steps.a.data.items ?? input.list   # ?? 대체값도 된다
```

- 경로가 가리키는 **리스트의 항목 수만큼 이 step을 동시에** 실행한다(`WorkFlowExecutor.runForEach()`).
- 반복마다 컨텍스트 복사본에 `item`(또는 `itemVariable`) 하나만 더해서 `input`을 채운다. 항목이 맵이면 `{{item.name}}`처럼 안으로 들어갈 수 있다.
- 결과:
  - `steps.<id>.items[i]` = i번째 반복의 `{input, text, data, error}`. **항목 순서대로** 쌓인다(끝난 순서가 아니다).
  - `steps.<id>.text` = 반복별 text를 줄바꿈으로 이은 것, `data` = `{}`.
  - 반복이 **하나라도 실패하면 step 전체가 실패**다. `error`에는 실패한 반복마다 `id[i]: 사유`가 모인다.
  - 실행 이력은 `id[0]`, `id[1]`...로 반복마다 한 줄씩 남는다.
- `maxIterations`는 항목 수와 관계없이 1로 센다.

| 경우 | 결과 |
|---|---|
| `{{input.sqlList}}`처럼 괄호를 붙임 | 기동 실패(시작 이름을 `{{input`으로 읽는다) |
| 경로가 틀림(없는 step, 선언 안 된 `input.x`) | 기동 실패 |
| APPROVAL이나 ROUTER에 적음 | 기동 실패(APPROVAL은 결정이 step id 하나로만 구분되고, ROUTER는 어느 반복의 선택을 따를지 정할 수 없다) |
| 실행 중 값이 없음 | step 실패 `forEach[...] - 값을 찾을 수 없습니다` → `onFailure` |
| 값이 리스트가 아님(문자열, 맵) | step 실패 `forEach[...]의 값이 리스트가 아닙니다` → `onFailure` |
| 빈 리스트 | 0회 실행, **성공**(`items = []`) |
| 반복 하나가 예외(Tool 없음 등) | 그 반복의 이력을 남기고 바로 FAILED |
| `{{steps.<forEach id>.data.x}}` 참조 | 기동 실패. `{{steps.<id>.items.0.data.x}}`로 꺼낸다 |
| 다른 step에서 `{{item}}` 사용 | 기동 실패(`item`은 forEach step 자신의 `input` 안에서만 쓸 수 있다) |

- 병렬 실행은 JVM 공용 스레드 풀(`CompletableFuture.supplyAsync`)을 쓴다. 동시에 도는 개수는 CPU 코어 수에 따라 제한된다.
- AGENT forEach는 모든 반복이 **같은 sessionId(대화 기억)**를 공유한다. 반복끼리 대화 기억이 섞일 수 있으므로, 서로 독립된 판단이 필요하면
  prompt와 `input`에 필요한 정보를 모두 담는다.
- MCP Tool 호출은 서버별로 한 번에 하나씩만 실행된다(`ConfigMcp.SerializedToolCallback`). MCP TOOL forEach는 사실상 순차로 돈다.

#### 3.12 `itemVariable`

```yaml
      forEach: input.sqlList
      itemVariable: sql
      input:
        sql: "{{sql}}"
```

- forEach 반복에서 이번 항목을 받는 이름이다. 생략하면 `item`.
- `forEach`가 없는 step에 적으면 무시된다.
- ⚠️ 컨텍스트의 시작 이름 `input`/`steps`/`previous`/`approvals`를 쓰지 않는다. 항목이 컨텍스트 맨 위에 같은 이름으로 들어가므로
  그 반복 안에서 `{{input.message}}` 같은 참조가 항목을 가리키게 되고, 기동 시 검사도 건너뛴다.

#### 3.13 `approverRole` (APPROVAL 전용)

```yaml
    - id: design-review
      type: APPROVAL
      approverRole: "PL"
```

- "누가 승인해야 하는지"를 YAML을 읽는 사람에게 알려 주는 **기록용 값**이다.
- 엔진은 이 값을 **어디에도 쓰지 않는다**. 권한 검사를 하지 않고, API 응답에도 나가지 않는다. 승인 API(`POST /api/ai/workflow/executions/{executionId}/decision`)는
  누가 부르든 받아들인다. 실제 권한 통제는 호출하는 쪽 화면이나 서비스가 맡는다.
- 참고로 APPROVAL의 동작은 이렇다.
  1. 처음 실행하면 결정이 없으므로 Workflow를 `WAITING_APPROVAL`로 멈추고 저장한다.
  2. `/decision`에 `{approved, approver, comment}`가 오면 `approvals.<id>`에 기록하고 **같은 step을 다시 실행**한다.
  3. 승인이면 성공(`data = {approved: true, approver, comment}`), 반려면 실패(`error` = comment).
  - 승인 대기 상태가 아닌 실행에 결정을 보내면 거절된다(`지금 승인 대기 상태가 아닙니다`).

### 4 값 타입 (`inputs`, `output.schema`)

`common.definition.FieldDefinition`. 두 가지 방법으로 적는다.

```yaml
sql: string                     # 축약형: 타입만
sql:                            # 확장형: 설명까지(output.schema에서는 LLM에게 그대로 전달된다)
  type: string
  description: 변환된 PostgreSQL SQL
```

| 타입 | 맞는 값 |
|---|---|
| `string` | 문자열 |
| `number` | 숫자(정수/실수) |
| `integer` | 정수 |
| `boolean` | `true` / `false` |
| `object` | JSON 객체(맵) |
| `list<타입>` | 해당 타입의 리스트. 예: `list<string>`, `list<object>` |

### 5 템플릿 참조 문법

`common.template.Template`이 처리한다. **값 참조와 대체값(`??`) 두 가지만** 지원한다. 계산식이나 조건식이 필요하면 Tool로
만들어 TOOL step으로 처리한다(YAML이 프로그래밍 언어가 되지 않게 하기 위해서다).

| 문법 | 의미 |
|---|---|
| `{{input.message}}` | 실행 요청의 message |
| `{{input.<이름>}}` | 실행 요청의 `variables.<이름>` |
| `{{steps.<id>.text}}` | 그 step의 결과 텍스트 |
| `{{steps.<id>.data.<키>}}` | 그 step의 구조화된 결과([3절 data 키 표](#3-step-항목)) |
| `{{steps.<id>.input}}` / `.input.<인자>` | 그 step이 실제로 받은 입력(TOOL이면 인자 맵) |
| `{{steps.<id>.error}}` | 그 step의 실패 사유 |
| `{{steps.<id>.items.0.text}}` | forEach step의 0번째 반복 결과(숫자로 리스트 항목 선택) |
| `{{previous.text}}` / `{{previous.data.<키>}}` | 바로 직전에 실행된 step의 결과 |
| `{{item}}` (또는 `itemVariable` 이름) | forEach 반복 중 이번 항목(forEach step의 `input` 안에서만) |
| `{{a.b ?? c.d ?? e}}` | 왼쪽부터 차례로 찾아 처음으로 값이 있는 것을 씀 |

- **채우는 규칙**: 다른 글자와 섞여 있으면 값을 글자로 끼운다(맵/리스트는 JSON 글자). 값 전체가 `{{ ... }}` 하나뿐이면
  **원래 타입을 유지**한다(TOOL 인자에 리스트나 숫자를 그대로 넘길 때).
- **값이 없으면 실패한다**: 경로가 없거나 값이 null이면 빈 글자로 넘어가지 않고 그 step이 실패한다(onFailure를 따름).
  대체값이 필요하면 `??`를 쓴다.
- **기동 시 검사**: 시작 이름(`input`/`steps`/`previous`/`item`), step id, 필드 이름(`input`/`text`/`data`/`error`/`items`),
  `steps.<id>.data.<키>`, `input.<이름>`(inputs 선언 시)을 미리 검사한다. `previous.data.*`처럼 실행 순서에 따라 달라지는
  값만 실행 중에 검사한다.
- **쓰는 곳은 세 군데**: step `input`, step `forEach`(`{{ }}` 없이 경로만), Workflow `output`.
  Agent의 system prompt는 이 문법이 아니라 `{변수}`(Spring AI PromptTemplate)를 쓴다.

**시작 이름별 자세한 규칙**

| 시작 | 가리키는 것 | 기동 시 검사 | 주의 |
|---|---|---|---|
| `input` | 요청의 `message` + `variables` | `inputs`를 선언했으면 `input.<이름>`이 `message`이거나 선언된 이름인지 | 실행 내내 바뀌지 않는다 |
| `steps.<id>` | 그 step이 **마지막으로** 남긴 결과 | step id가 있는지, 다음 필드가 `input`/`text`/`data`/`error`/`items`인지, `data.<키>`를 그 step이 내놓는지 | 루프로 다시 실행되면 최신 결과로 덮어쓴다. 아직 실행되지 않았으면 실행 중 step 실패 |
| `previous` | 바로 직전에 **실행된** step의 결과(목록상 앞 step이 아니다) | 다음 필드 이름만 | 첫 step에서는 `{text: message}`뿐이다. 루프나 분기에 따라 달라지므로 `data.*`는 실행 중에만 검사한다 |
| `item` / `itemVariable` 이름 | forEach 반복의 이번 항목 | forEach step 자신의 `input` 안에서만 허용 | `forEach` 경로 자체나 다른 step에서는 쓸 수 없다 |
| 그 밖의 이름 | — | 기동 실패 `알 수 없는 시작 이름입니다` | `approvals`도 템플릿에서는 쓸 수 없다. 승인 내용은 `steps.<id>.data.*`로 꺼낸다 |

**경우별 결과**

| 템플릿 | 결과 |
|---|---|
| `"{{steps.list.data.lines}}"`(TOOL 인자, 값 전체) | 리스트 그대로 |
| `"파일: {{steps.list.data.lines}}"`(섞임) | `파일: ["a.txt","b.txt"]`(JSON 글자) |
| AGENT `input`에 `{{steps.extract.data}}` | AGENT류 input은 항상 글자라 JSON 글자로 들어간다 |
| `{{steps.list.data.lines.0}}` | 첫 항목. 범위를 벗어나면 값 없음 → step 실패 |
| `{{steps.a.text ?? steps.b.text ?? input.message}}` | 왼쪽부터 처음으로 null이 아닌 값. 빈 문자열 `""`도 값이 있는 것으로 본다 |
| `??`의 모든 경로가 없음 | step 실패 `값을 찾을 수 없습니다(찾아본 경로 = [...])` |
| `{{ input.message }}`(괄호 안 공백) | 허용(앞뒤 공백은 무시) |
| `{{input.message \| upper}}`, `{{a + b}}` | 지원하지 않는다. 경로를 찾지 못해 실패하거나 기동 실패. 가공이 필요하면 TOOL step을 쓴다 |
| 값이 숫자/boolean(섞임) | `toString()` 글자로 끼운다 |



### 6 제공되는 샘플

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

**MCP 서버**: `mcp/sample/sample-filesystem-mcp.yml` — 공식 filesystem 레퍼런스 서버를 STDIO로 띄워
`${APP_HOME}/${APP_NAME}/mcp/server-filesystem` 하나만 노출한다(`list_directory`/`read_text_file`/`write_file`/`edit_file`/`move_file`만 허용).

### 7 YAML 템플릿 샘플

복사해서 쓰는 뼈대다. `<...>` 자리를 채우고, 쓰지 않는 선택 항목은 지운다.

#### 7.1 Workflow 뼈대

```yaml
# <이 Workflow가 무엇을 하는지 한두 줄>
#
# dstone-boot의 "Workflow 테스트" 화면에서는 이렇게 호출해보면 됩니다:
#   workflowId: <workflow-id>
#   message   : <예시 메시지>
#   variables : <예시 JSON 또는 (비워도 됩니다)>
workflow:
  id: <workflow-id>
  description: <사람이 읽는 설명>
  maxIterations: 10                 # 선택. 루프가 있으면 (step 수 × 재시도 횟수)보다 넉넉하게
  # allowedCallers: [<caller>]      # 선택. 인증을 켠 환경에서만 채운다
  # inputs:                         # 선택. 요청 variables의 계약
  #   <이름>: string
  # output: "{{steps.<step-id>.text}}"   # 선택. 비우면 마지막 step의 text
  steps:
    - id: <step-id>
      type: AGENT
      ref: <agent-id>
      input: "{{input.message}}"
      onSuccess: SUCCESS
      onFailure: FAIL
```

#### 7.2 step 타입별 스니펫

**AGENT — 자유 텍스트**

```yaml
    - id: summarize
      type: AGENT
      ref: <agent-id>
      input: |
        [요약할 원문]
        {{input.message}}
      # input을 생략하면 {{previous.text}}(직전 step의 결과 텍스트)를 받는다
```

**AGENT + output.schema — 다음 step이 값을 콕 집어 쓸 때**

```yaml
    - id: extract
      type: AGENT
      ref: <agent-id>
      input: "{{input.message}}"
      output:
        schema:
          sql:
            type: string
            description: 입력 문장에서 뽑아낸 SQL 문 하나
          tables: list<string>
      onSuccess: validate
      onFailure: FAIL
      # → 다음 step에서 {{steps.extract.data.sql}}, {{steps.extract.data.tables}}
```

**TOOL — 기본(결과 텍스트만)**

```yaml
    - id: validate
      type: TOOL
      ref: validateSqlSyntax            # @Tool 메서드 이름 또는 MCP Tool 이름
      input:
        sql: "{{steps.extract.data.sql}}"
      onSuccess: SUCCESS
      onFailure: FAIL
      # 인자가 필요 없는 Tool이면 input을 통째로 생략한다(빈 인자 {}로 호출)
```

**TOOL + parse: json — Tool이 구조화된 JSON을 돌려줄 때**

```yaml
    - id: lookup
      type: TOOL
      ref: <tool-name>
      input:
        ids: "{{input.idList}}"         # 값 전체가 {{ }} 하나면 리스트 타입 그대로 넘어간다
      output:
        parse: json                     # 응답 JSON 객체 → data (배열이면 data.items)
      # → {{steps.lookup.data.<키>}} (키는 Tool 응답에 따라 다르므로 실행 중에 검사된다)
```

**TOOL + parse: lines — 줄 단위 응답에서 값 목록을 뽑을 때**

```yaml
    - id: list
      type: TOOL
      ref: list_directory
      input:
        path: "{{input.message}}"
      output:
        parse: lines
        pattern: '^\[FILE\] (.+)$'      # 맞는 줄만 남기고, 괄호 그룹 1을 값으로
      # → data.lines = ["notes.txt", "todo.txt"]
```

**SUPERVISOR — 앞 step의 결과를 LLM이 판정**

```yaml
    - id: judge
      type: SUPERVISOR
      ref: <판정용 agent-id>            # prompt에 판정 기준을 적어 둔다
      # input 생략 → 직전 step의 결과 텍스트를 판정
      onSuccess: SUCCESS                # pass=true
      onFailure: FAIL                   # pass=false → 사유는 {{steps.judge.error}}
```

**ROUTER — 세 갈래 이상 분기**

```yaml
    - id: classify
      type: ROUTER
      ref: <분류용 agent-id>            # prompt에 route 이름 목록을 routes 키와 똑같이 적어 둔다
      input: "{{input.message}}"
      routes:
        billing: billing-step
        technical: technical-step
        other: SUCCESS                  # 예약어도 쓸 수 있다
      # → 고른 경로와 이유는 {{steps.classify.data.route}}, {{steps.classify.data.reason}}
```

**APPROVAL — 사람의 승인 대기**

```yaml
    - id: review
      type: APPROVAL
      approverRole: "PL"                # 기록용. 서버가 권한을 검사하지는 않는다
      onSuccess: next-step              # 승인 → 코멘트는 {{steps.review.data.comment}}
      onFailure: FAIL                   # 반려 → 앞 step으로 되돌리는 루프는 쓰지 않는다(3.9 경고 참고)
```

**forEach — 리스트 항목마다 동시에 실행**

```yaml
    - id: validate-each
      type: TOOL
      ref: validateSqlSyntax
      forEach: input.sqlList            # {{ }} 없이 경로만. steps.<id>.data.lines 같은 앞 step 결과도 된다
      itemVariable: sql                 # 선택. 비우면 {{item}}
      input:
        sql: "{{sql}}"
      onSuccess: SUCCESS
      onFailure: FAIL
      # → steps.validate-each.items[i] = {input, text, data, error}, 하나라도 실패하면 step 실패
```

**재시도 루프 — 실패하면 고쳐서 다시 검증**

```yaml
workflow:
  id: <workflow-id>
  maxIterations: 6                      # 검증↔수정 최대 3회전
  output: "{{steps.validate.input.sql}}"
  steps:
    - id: validate
      type: TOOL
      ref: validateSqlSyntax
      input:
        sql: "{{previous.data.sql ?? input.message}}"   # 처음엔 사용자 메시지, 수정 뒤엔 fix의 결과
      onSuccess: SUCCESS
      onFailure: fix

    - id: fix
      type: AGENT
      ref: <수정용 agent-id>
      input: |
        [검증에 실패한 SQL]
        {{steps.validate.input.sql}}

        [실패 사유]
        {{steps.validate.error}}
      output:
        schema:
          sql: string
      onSuccess: validate               # 앞쪽 step으로 → Loop
```
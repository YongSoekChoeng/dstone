
### 1 파일 규칙

| 종류 | 위치 | 최상위 키 | 파일 하나에 |
|---|---|---|---|
| Workflow | `src/main/resources/workflows/**/*.yml` | `workflow:` | Workflow 1개 |
| Agent | `src/main/resources/agents/**/*.yml` | `agent:` | Agent 1개 |
| MCP 서버 | `src/main/resources/mcp/**/*.yml` | `mcpServer:` | MCP 서버 1개 |

- 폴더는 자유롭게 나눠도 된다(`workflows/billing/*.yml` 등). 구분 기준은 파일 경로가 아니라 `id`다.
- 파일 이름은 `id`와 맞추는 것을 권장한다(강제는 아님).
- 모르는 키를 쓰면 기동이 실패한다. 오타에 주의한다.
- 문자열 안의 `${VAR_NAME}`(영문 대문자/숫자/밑줄만, 공백 없음)은 기동 시 `conf/env{-profile}.properties` 값으로 바뀐다(`YamlDefinitionLoader`).
  실행 중 데이터를 읽는 표현식 `"${ .input }"`(5절)과는 다른 것이다. 표현식은 jq 식이라 항상 `.`이나 `$`, 공백 등으로 시작하므로 겹치지 않는다.
  찾지 못한 환경변수는 `${VAR_NAME}` 글자로 남고, 그 값이 표현식 자리에 있으면 기동 실패 `환경변수 VAR_NAME를 찾지 못했습니다`다.
- 파일 맨 위에 "이 파일이 무엇을 하는지, 어떻게 호출해 보면 되는지"를 주석으로 적어 두는 것이 이 프로젝트의 관례다.

**이 가이드의 세 가지 원칙**

1. **계약은 부르는 대상이 가진다.** 무엇을 받고 무엇을 돌려주는지는 Agent(`agents/*.yml`의 `input`/`output`)나 Tool(인자 스키마)이 정한다.
   Workflow의 step은 "무엇을 넣을지(`input`)"와 "다음에 어디로 갈지"만 적는다. step에 `output`을 적는 자리는 없다.
2. **값은 모두 JSON이고, 표현식은 jq다.** step 사이를 오가는 값(글자, 객체, 리스트)은 모두 JSON 값이다.
   YAML에서 값 전체를 `"${ ... }"`로 적으면 엔진이 실행 컨텍스트를 jq로 읽어 계산하고, 결과를 **타입 그대로** 넘긴다 — `"${ .input }"`(Workflow 입력),
   `"${ .steps.<id>.output }"`(step 결과). 그 밖의 값은 적힌 그대로(리터럴)다. 엔진이 몰래 채워 넣는 숨은 이름은 없다.
3. **모양은 JSON Schema로 적는다.** `input`/`output`의 `schema` 아래에 표준 JSON Schema를 YAML로 그대로 적는다(4절). 타입만 필요하면 축약형(`input: string`)을 쓴다.

### 2 Workflow 항목

`workflow:` 아래에 적는다(`common.definition.workflow.WorkFlowDefinition`).

| 항목 | 필수 | 타입 | 설명 |
|---|---|---|---|
| `id` | ✅ | 문자열 | Workflow를 가리키는 이름. API 경로(`/api/ai/workflow/{id}/...`)에 쓰인다. 중복 불가 |
| `description` | | 문자열 | 사람이 읽는 설명. `GET /api/ai/workflow` 목록과 dstone-boot 화면 안내문에 쓰인다 |
| `maxIterations` | | 정수 | 전체 step 실행 횟수 상한(루프 방지). 비우면 **10** |
| `allowedCallers` | | 문자열 리스트 | 실행을 허락할 caller 목록. 비우면 누구나. ⚠️ 인증이 꺼져 있으면 caller가 항상 null이라, 채우는 순간 아무도 실행 못 한다 |
| `input` | | 스키마 | 실행 요청의 `input` 모양(입력 계약). 비우면 `string`. 모양이 틀린 요청은 실행 전에 **400** |
| `output` | ✅ | `{value, schema}` | 성공으로 끝났을 때 돌려줄 최종 결과. `value`(무엇을 돌려줄지)는 필수, `schema`(모양 검사)는 선택 |
| `steps` | ✅ | step 리스트 | 실행할 step 목록. 목록 순서가 기본 실행 순서다 |

> 아래 2.1~2.7은 항목마다 **모양 → 동작 → 검사 시점 → 경우별 결과** 순서로 적었다.
> "기동 실패"는 엔진이 켜질 때 예외로 멈춘다는 뜻이고(`YamlDefinitionLoader`/`WorkFlowRegistry`), "400"은 실행 요청을 거절한다는 뜻이며,
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
- `GET /api/ai/workflow` 목록에 `id`, `input` 스키마와 함께 나가고, dstone-boot "Workflow 테스트" 화면 드롭다운의 안내문으로 쓰인다.
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

#### 2.5 `input`

**한 줄 요약** — "이 Workflow를 실행하려면 요청의 `input`에 **이런 모양의 값**을 넣어 와라"는 약속(입력 계약)이다.

Java 메서드의 매개변수 선언이라고 생각하면 쉽다.

```
YAML의 workflow.input               ≒  Java 메서드 매개변수
──────────────────────────────────     ──────────────────────────────────────────────
input: string                          String run(String input)
input:                                 String run(Input input)   ← record Input(List<String> sqlList, String targetVersion)
  schema:
    type: object
    properties:
      sqlList: list<string>
      targetVersion: string
    required: [sqlList]

실행 요청의 input                   ≒  메서드를 부를 때 넘기는 값(인자)
```

- 모양은 [4절](#4-스키마-json-schema)의 스키마로 적는다. 비우면 `string`이다(글자 하나).
- 요청 body는 `{"input": <값>, "sessionId": "..."}` 하나다. 예전의 `message`/`variables`는 없다.
- 요청의 `input`은 그대로 실행 컨텍스트의 `input`이 된다. step은 **`"${ .input }"`**(글자면 그 글자) 또는 **`"${ .input.필드 }"`**(object면 그 필드)로 꺼낸다.
- 두 번 검사한다. **엔진이 켜질 때** 스키마가 올바른 JSON Schema인지(오류)와 `.input.x`가 스키마에 있는 경로인지(경고), **요청이 올 때** 값이 스키마에 맞는지.
- Workflow input은 Agent prompt의 `{변수}`를 직접 채우지 않는다. prompt 변수는 **그 Agent가 받은 input 필드**로 채운다.
  Workflow input의 값을 prompt에 넣고 싶으면 step `input` 맵에 그 필드를 넘긴다(예: `input: {role: "${ .input.role }", ...}`).

##### 2.5.1 한눈에 보기 — 값이 흘러가는 길

```
 ┌──────────────────────────────┐              ┌───────────────────────────────────────────┐
 │ 개발자: workflows/xxx.yml     │              │ 호출자: POST /api/ai/workflow/{id}/execute  │
 │   input:                     │              │   { "input": { "sqlList": [...],          │
 │     schema: {type: object..} │              │                "targetVersion": "16" } }  │
 └──────────────┬───────────────┘              └─────────────────────┬─────────────────────┘
                │                                                    │
   ════════ 엔진 기동 시 (한 번) ═════════            ════════ 요청이 올 때 (매번) ═════════
                ▼                                                    ▼
   ① YAML 글자 → Map          (SnakeYAML)            ⑤ input이 비었나?          → 400
   ② Map → WorkFlowDefinition (Jackson, 축약형 펼침)   ⑥ 이 caller가 써도 되나?   (allowedCallers)
   ③ 스키마가 올바른 JSON Schema인가? → 기동 실패       ⑦ input이 스키마에 맞나?    → 400
   ④ 표현식 문법·참조 검사     → 기동 실패/경고       ⑧ 실행 컨텍스트 만들기     (context.input)
                │                                                    │
                └──────────── WorkFlowRegistry에 등록 ──────────────┐ │
                                                                   ▼ ▼
                                  ═════════ step을 실행할 때 (step마다) ═════════
                                  ⑨  forEach: "${ .input.sqlList }" → 리스트를 계산해 항목 수만큼 반복
                                  ⑩  step input의 "${ .input.x }"   → Tool 인자 / Agent input
                                  ⑪  Agent prompt의 {x}             → Agent가 받은 input 필드로 채움
                                  ⑫  output.value의 "${ ... }"      → 최종 결과
```

- ①~④는 엔진이 켜질 때 한 번, ⑤~⑧은 요청마다 한 번, ⑨~⑫는 step마다 일어난다.
- `input` 값은 ⑧에서 한 번 들어간 뒤 **실행이 끝날 때까지 바뀌지 않는다**. 어느 step에서 꺼내도 같은 값이다.

##### 2.5.2 샘플 Workflow

아래 두 파일을 예로 ①~⑫를 따라가 본다.
(설명을 위해 만든 예시라 실제 파일은 없다. 그대로 돌려 볼 수 있는 가장 가까운 샘플은 `sample/sample-foreach-parallel.yml`이다.)

```yaml
# workflows/sql-review.yml (설명용 예시)
workflow:
  id: sql-review
  description: SQL 목록을 문법 검사한 뒤, 대상 PostgreSQL 버전 기준으로 검토한다
  maxIterations: 5
  input:
    schema:
      type: object
      properties:
        sqlList: list<string>                  # 축약형: 타입만
        targetVersion:                         # 표준형: 타입 + 설명
          type: string
          description: 대상 PostgreSQL 버전(예 "16")
        maxRows: { type: integer, minimum: 1 }
      required: [sqlList, targetVersion]       # maxRows는 선택
  output:
    value: "${ .steps.review.output }"
  steps:

    - id: check                                # ❶ 리스트를 forEach로 쪼개서 TOOL에 넘기기
      type: TOOL
      ref: validateSqlSyntax
      forEach: "${ .input.sqlList }"
      input:
        sql: "${ $item }"

    - id: review                               # ❷ 맵으로 필요한 값만 골라 Agent(input: object)에게 넘기기
      type: AGENT
      ref: sql-review-agent
      input:
        targetVersion: "${ .input.targetVersion }"
        sqlList: "${ .input.sqlList }"                 # 리스트 그대로
        checks: "${ .steps.check.output }"             # forEach 결과 리스트 그대로
        failedCount: "${ [.steps.check.output[] | select(.success == false)] | length }"
```

```yaml
# agents/sql-review-agent.yml (설명용 예시)
agent:
  id: sql-review-agent
  prompt: |
    당신은 PostgreSQL {targetVersion} 전문 DBA입니다.   # ← 이 Agent가 받은 input.targetVersion
    입력 JSON의 sqlList를 checks(문법 검사 결과)와 함께 검토하십시오.
  input:
    schema:
      type: object
      properties:
        targetVersion: string
        sqlList: list<string>
        checks: array
        failedCount: integer
      required: [targetVersion, sqlList, checks]
  # output을 비워 두었으므로 string
```

호출 요청:

```json
POST /api/ai/workflow/sql-review/execute
{
  "input": {
    "sqlList": ["SELECT * FROM emp", "SELECT name FROM dept"],
    "targetVersion": "16",
    "maxRows": 100
  }
}
```

> 💡 **규칙 하나 — YAML에 적은 이름 그대로 꺼낸다**
>
> YAML은 **선언**(설계도)이고, `"${ }"` 표현식은 실행할 때 엔진이 만드는 **실행 컨텍스트**(값이 담긴 JSON 트리)를 jq로 읽는다.
> 두 쪽의 이름을 똑같이 맞춰 두었으므로 "YAML에 적은 이름 = 꺼낼 때 쓰는 이름"이다.
>
> ```
> [YAML = 선언, 기동 시 한 번 읽힘]            [실행 컨텍스트 = 값, 실행마다 새로 생김]
> workflow:                                  context
>   input:                     ────────────▶  ├── input       ← 요청에 실제로 온 값 그대로
>     schema: {...}                          │    ├── sqlList : ["SELECT ...", ...]
>   steps:                                   │    └── targetVersion : "16"
>     - id: check              ────────────▶  ├── steps
>       input: {sql: ...}      ────────────▶  │    ├── check  : { input, output, error }
>       forEach: ...                         │    │              ↑ 채운 값  ↑ 돌려준 값
>     - id: review             ────────────▶  │    └── review : { input, output, error }
>                                            └── (approvals) ← 엔진 내부용, 표현식에는 안 보임
>                                            $item           ← forEach 반복 중에만 있는 jq 변수
> ```
>
> | YAML에 적는 곳 (선언) | 표현식으로 꺼내는 곳 (값) | 누가, 언제 넣나 |
> |---|---|---|
> | `workflow.input` — 모양 | `.input`, `.input.sqlList` — 요청에 온 값 | 요청이 오면 `WorkFlowContext.create()`가 넣음 |
> | step `input` — 넣을 값 | `.steps.<id>.input` — 계산한 뒤 실제로 받은 값 | step 실행 직전에 표현식을 계산해서, 끝나면 기록 |
> | (Agent `output` / Tool 응답 / 엔진이 정한 모양) | `.steps.<id>.output` — 돌려준 값 | step이 끝나면 `StepOutcome.toRecord()` 모양으로 기록 |
> | (실패 사유) | `.steps.<id>.error` | step이 실패하면 기록(성공이면 null) |
> | step `forEach` / `itemVariable` | `$item` — 이번 반복의 항목 | `runForEach()`가 항목마다 jq 변수로 넣음 |

##### 2.5.3 단계별로 따라가기

**① YAML 글자 → Map** — `YamlDefinitionLoader`가 SnakeYAML로 파일을 읽는다. 이때 `${VAR}`(환경 값) 치환도 같이 한다.

**② Map → `WorkFlowDefinition`** — Jackson이 record로 바인딩한다. `input`은 `common.definition.SchemaDefinition`으로 읽히면서
**축약형이 표준 JSON Schema로 펼쳐진다**(`sqlList: list<string>` → `sqlList: {type: array, items: {type: string}}`).
모르는 키나 모르는 타입 이름(`strng`)은 여기서 기동 실패다.

**③ 스키마 검사** — `WorkFlowRegistry`가 `input.schema`(와 `output.schema`)를 JSON Schema 2020-12 메타스키마로 검사한다
(`JsonSchemas.checkSchema()`). 예: `type: strin`, `required: name`(리스트가 아님)은 기동 실패.

**④ 표현식 검사** — step `input`, `forEach`, `output.value` 안의 모든 표현식을 jq로 컴파일하고, 읽는 경로를 검사한다(5절).
`.input.x.y`는 스키마를 따라 내려가며 보고(`JsonSchemas.checkPath()`), `properties`에 없는 이름이면 **경고**
`.input에는 [sqlList, targetVersion, maxRows]만 있습니다('sqlLis' 없음).`를 남긴다.
(`additionalProperties`를 열어 두었거나 `properties`가 없는 object는 더 들어가 보지 않는다.)

**⑤ 요청 확인** — 요청의 `input`이 없거나 빈 글자면 400 `input은 필수입니다`.

**⑥ caller 확인** — [2.4](#24-allowedcallers).

**⑦ 모양 확인** — `WorkFlowExecutionService.checkInput()`이 요청 `input`을 스키마로 검사한다(networknt json-schema-validator).
틀리면 400 `workflow[x]의 input 모양이 맞지 않습니다: [/sqlList: string 발견, array 예상]`처럼 **위치와 이유**를 알려 준다.

**⑧ 컨텍스트 생성** — `context.input = 요청의 input`, `context.steps = {}`. 다른 것은 없다.

**⑨~⑫ 꺼내 쓰기** — `forEach: "${ .input.sqlList }"`, `"${ .input.targetVersion }"`, prompt의 `{targetVersion}`(Agent input 필드), `output.value`의 `"${ ... }"`.

##### 2.5.4 자주 쓰는 선언 패턴

**A. 글자 하나(가장 흔함)** — 요청 `{"input": "..."}`, 꺼낼 때 `"${ .input }"`.
```yaml
  input: string
```

**B. 필수 필드 몇 개** — 요청 `{"input": {"message": "...", "role": "..."}}`, 꺼낼 때 `"${ .input.message }"`.
```yaml
  input:
    schema:
      type: object
      properties:
        message: { type: string, description: 고객 문의 원문 }
        role: string
      required: [message, role]
```

**C. 선택 필드** — `required`에 넣지 않은 필드는 요청에 없어도 된다. 없는 필드를 꺼내면 `null`이므로 jq의 `//`로 기본값을 둔다.
```yaml
  input:
    schema:
      type: object
      properties:
        sql: string
        maxRows: { type: integer, minimum: 1 }
      required: [sql]
# step에서: "${ .input.maxRows // 100 }"
```

**D. 리스트** — forEach 대상.
```yaml
  input:
    schema:
      type: object
      properties:
        sqlList: { type: array, items: { type: string }, minItems: 1 }
      required: [sqlList]
```

**E. 값 제한** — JSON Schema 키워드를 그대로 쓴다(`enum`, `minimum`, `maxLength`, `pattern` 등).
```yaml
        priority: { type: string, enum: [HIGH, MID, LOW] }
```

##### 2.5.5 경우별 결과

| 경우 | 결과 |
|---|---|
| `input` 생략 | `string`. 요청 `input`은 비어 있지 않은 글자여야 한다 |
| 요청에 `input`이 없거나 `""` | 400 `input은 필수입니다` |
| 스키마는 object인데 요청이 글자 | 400 `...: string 발견, object 예상` |
| `required` 필드가 빠짐 | 400 `필수 속성 'x'을(를) 찾을 수 없습니다` |
| 스키마에 없는 필드가 더 옴 | 허용(`additionalProperties: false`를 적으면 400) |
| `"${ .input.x }"`인데 `x`가 스키마 `properties`에 없음 | 기동 경고(기동은 됨). 실행하면 `null` |
| 글자 input인데 `"${ .input.x }"` | 기동 경고 `.input는 string 값이라 그 아래('x')로 더 들어갈 수 없습니다`. 실행하면 jq 오류로 step 실패 |
| 예전 키 `inputs:` | 기동 실패 `'inputs'는 이 자리(WorkFlowDefinition)에서 쓸 수 없는 키입니다 ... inputs는 input으로 바뀌었습니다` |
| 스키마 오타(`type: strin`) | 기동 실패 `input.schema가 올바른 JSON Schema가 아닙니다: [...]` |
| 축약형 오타(`input: strng`) | 기동 실패 `알 수 없는 타입 이름입니다: strng` |

#### 2.6 `output`

```yaml
  output:
    value: "${ .steps.convert.output }"     # (필수) 무엇을 돌려줄지
    schema: string                          # (선택) 결과 모양 검사
```

- Workflow가 **성공(DONE)으로 끝났을 때** 돌려줄 최종 결과다(`common.definition.workflow.WorkFlowOutputDefinition`).
  동기 실행 응답의 `output`, 비동기 상태/상세 조회의 `output`이 이 값이다.
- `value`는 [5절](#5-표현식-문법-jq) 규칙을 따른다. 표현식의 결과는 **타입 그대로**라서 결과가 객체나 리스트일 수 있다.
  맵/리스트로 적으면 여러 step의 값을 모아 새 모양으로 돌려준다. jq 객체 생성(`{a: .x, b: .y}`)으로 적어도 같다.
  ```yaml
  output:
    value:
      sql: "${ .steps.convert.output.sql }"
      approvedBy: "${ .steps.review.output.approver }"
  ```
- `schema`를 적으면 성공 직전에 결과를 검사하고, 맞지 않으면 FAILED로 끝낸다.
- `$item`은 쓸 수 없다(forEach step 밖이기 때문). 어떤 step의 결과든 읽을 수 있다(실행 순서 검사를 하지 않는다).
- 결과는 DB(`AI_WORKFLOW_EXECUTION.RESULT_TEXT`)에 JSON 글자로 저장되고, 조회할 때 원래 값으로 되돌려진다.

| 경우 | 결과 |
|---|---|
| `output` 또는 `output.value` 생략 | 기동 실패 `output.value가 있어야 합니다(최종 결과로 무엇을 돌려줄지)` |
| `output: "${ ... }"`처럼 글자 하나로 적음 | 기동 실패 `workflow.output은 value(와 schema)를 가진 맵입니다` |
| 가리킨 값이 없음(실행되지 않은 step 등) | 결과가 `null`. `schema`를 적었으면 모양 검사에서 FAILED |
| jq 계산 오류(예: 글자에 `length` 대신 숫자 연산) | 성공 직전에 FAILED `Workflow output을 만들지 못했습니다` |
| `//` 사용(`"${ .steps.a.output // .steps.b.output }"`) | 왼쪽이 null(또는 false)이면 오른쪽. 분기 때문에 실행되지 않았을 수 있는 step을 가리킬 때 쓴다 |
| `schema`와 결과 모양이 다름 | FAILED `Workflow output이 output.schema 모양이 아닙니다: [...]` |
| FAILED 또는 WAITING_APPROVAL로 끝남 | `output`은 쓰이지 않는다(null) |

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
| step 하나에 `id`가 없음 | 기동 실패 `모든 step은 id가 있어야 합니다` |
| step id에 `-`, `.` 등이 들어감 | 기동 실패 `step id는 영문, 숫자, 밑줄(_)만 쓸 수 있습니다` |
| step 하나에 `type`이 없음 | 기동 실패 `[workflow.steps[i]] step의 type이 없거나 올바르지 않습니다(...)` |
| step id 중복 | 기동 실패 `step id가 중복되었습니다` |

### 3 Step 항목

`steps:` 리스트의 항목 하나. `type` 값에 따라 `common.definition.workflow.step` 패키지의 record 하나로 읽힌다.

| `type` | record | 실행기 | 계약을 가진 쪽 |
|---|---|---|---|
| `AGENT` | `AgentStepDefinition` | `AgentStepExecutor` | Agent(`input`/`output`) |
| `SUPERVISOR` | `SupervisorStepDefinition` | `SupervisorStepExecutor` | Agent(`input`) + 엔진(`{pass, reason}`) |
| `ROUTER` | `RouterStepDefinition` | `RouterStepExecutor` | Agent(`input`) + 엔진(`{route, reason}`) |
| `TOOL` | `ToolStepDefinition` | `ToolStepExecutor` | Tool(인자 스키마 / 응답) |
| `APPROVAL` | `ApprovalStepDefinition` | `ApprovalStepExecutor` | 엔진(`{approved, approver, comment}`) |

- record의 필드가 곧 그 종류의 step에 적을 수 있는 YAML 키다. step 정의 하나에 실행기(`runtime.step`) 하나가 1:1로 대응한다.
- 다섯 record는 sealed interface `StepDefinition`(`id`/`type`/`onFailure`만 가짐)을 구현한다.
- record마다 **그 종류가 쓰는 키만** 있다. 다른 종류의 키를 적으면 YAML을 읽는 단계에서 기동이 실패한다. 오류 메시지는 YAML 경로와 쓸 수 있는 키를 알려 준다.
  예: `[workflow.steps[2].routes] 'routes'는 이 자리(ToolStepDefinition)에서 쓸 수 없는 키입니다 (workflow.steps[2]에서 쓸 수 있는 키 = [ref, onSuccess, input, id, itemVariable, onFailure, forEach])`
- **어떤 step에도 `output` 키는 없다.** 돌려받는 값의 모양은 부르는 대상(Agent/Tool)이나 엔진이 정한다.

| 항목 | 설명 |
|---|---|
| `id` | step 이름(필수, 영문/숫자/밑줄만). `onSuccess`/`onFailure`/`routes`와 `.steps.<id>` 표현식이 이 이름을 쓴다. Workflow 안에서 중복 불가 |
| `type` | `AGENT` / `TOOL` / `SUPERVISOR` / `ROUTER` / `APPROVAL` (필수) |
| `ref` | AGENT/SUPERVISOR/ROUTER는 **Agent id**, TOOL은 **Tool 이름**(`@Tool` 메서드 이름 또는 MCP Tool 이름). APPROVAL은 쓰지 않는다 |
| `input` | 이 step에 넣을 값(표현식 또는 리터럴). AGENT류는 **필수**이고 Agent `input` 모양을 따른다(string이면 값 하나, object면 맵). TOOL은 인자 맵(선택) |
| `onSuccess` | 성공 시 다음 step id 또는 `SUCCESS`/`FAIL`. 비우면 목록상 다음 step(마지막이면 성공 종료) |
| `onFailure` | 실패 시 다음 step id 또는 `SUCCESS`/`FAIL`. 비우면 Workflow 실패. 앞쪽 step id를 적으면 재시도 루프 |
| `routes` | ROUTER 전용(필수, 1개 이상). `route 이름: 다음 step id`(또는 `SUCCESS`/`FAIL`) |
| `forEach` | 리스트를 돌려주는 표현식(예: `"${ .input.sqlList }"`). 항목 수만큼 이 step을 동시에 실행한다 |
| `itemVariable` | `forEach` 반복에서 항목을 받는 jq 변수 이름. 비우면 `item`(→ `$item`) |
| `memory` | AGENT/SUPERVISOR/ROUTER 전용(선택, 기본 `false`). `true`면 이 step이 **자기 대화방(`sessionId:stepId`)**에서 이전에 자기가 나눈 대화를 기억한다(재작성 루프 등). `false`면 대화 기억 없이 `input`만 보고 답한다. 다른 step의 대화는 어느 쪽이든 섞이지 않는다. `forEach`와 함께 쓰면 기동 실패 |
| `approverRole` | APPROVAL 전용 기록용 값(누가 승인해야 하는지). 서버가 실제 권한을 검사하지는 않는다 |

**StepType별로 쓸 수 있는 항목** (✅ 필수, ⭕ 선택, ❌ 쓰면 기동 실패 — 해당 record에 그 키가 없음)

| 항목 | AGENT | SUPERVISOR | ROUTER | TOOL | APPROVAL |
|---|---|---|---|---|---|
| `ref` | ✅ Agent id | ✅ Agent id | ✅ Agent id | ✅ Tool 이름 | ❌ |
| `input` | ✅ Agent input 모양 | ✅ Agent input 모양 | ✅ Agent input 모양 | ⭕ 맵 | ❌ |
| `onSuccess`| ⭕ | ⭕ | ❌ | ⭕ | ⭕ |
| `onFailure` | ⭕ | ⭕ | ⭕ (route를 고르지 못했을 때) | ⭕ | ⭕ |
| `routes` | ❌ | ❌ | ✅ | ❌ | ❌ |
| `forEach` | ⭕ | ⭕ | ❌ | ⭕ | ❌ |
| `itemVariable` | ⭕ | ⭕ | ❌ | ⭕ | ❌ |
| `memory` | ⭕ | ⭕ | ⭕ | ❌ | ❌ |
| `approverRole` | ❌ | ❌ | ❌ | ❌ | ⭕ |

**각 step이 돌려주는 값(`steps.<id>.output`)의 모양** — `.steps.<id>.output.<경로>`는 이 모양을 따라 기동 시 검사한다(없는 필드는 경고).

| step | `output` | 기동 시 경로 검사 |
|---|---|---|
| AGENT | ref Agent의 `output` 모양 그대로(비워 두면 **글자**) | Agent `output` 스키마로 |
| SUPERVISOR | `{pass: boolean, reason: string}` | 엔진 스키마(`StepOutputSchemas.verdict()`)로 |
| ROUTER | `{route: string, reason: string}` | 엔진 스키마(`StepOutputSchemas.routeDecision()`)로 |
| APPROVAL | `{approved: boolean, approver: string, comment: string}` | 엔진 스키마(`StepOutputSchemas.approval()`)로 |
| TOOL | Tool 응답이 JSON이면 그 값(객체/배열/숫자/true·false), 아니면 **글자** | 알 수 없음 → 검사하지 않음 |
| `forEach` step | 위 모양의 **리스트**(반복 순서대로) | `.steps.<id>.output[0].<경로>`처럼 번호를 붙여 검사 |

**모든 step이 남기는 결과** — step이 끝나면 컨텍스트의 `steps.<id>`에 아래 모양이 남는다(`StepOutcome.toRecord()`).
같은 step이 루프로 다시 실행되면 **덮어쓴다**. 이 세 이름 말고는 없다.

```
steps:
  <id>:
    input:  표현식을 계산한 뒤 실제로 받은 값(forEach면 반복별 값의 리스트, APPROVAL은 null)
    output: 돌려준 값(위 표. forEach면 반복별 값의 리스트, 실패했으면 받은 만큼 또는 null)
    error:  실패 사유(성공이면 null)
```

| step | 성공하면 `output` | 실패하는 경우 → `error` |
|---|---|---|
| AGENT | Agent 답(Agent `output` 모양) | input이 Agent input 모양이 아님, LLM 답이 Agent output 모양이 아님 → 그 이유 |
| TOOL | Tool 응답(JSON이면 값, 아니면 글자) | 응답이 `{success: false, message}`(→ message) 또는 `실패`로 시작하는 글자(→ 그 글자). 이때도 `output`에는 응답이 남는다 |
| SUPERVISOR | `{pass: true, reason}` | `pass=false`(→ reason, `output`은 `{pass: false, reason}`), 답이 모양을 지키지 않음 |
| ROUTER | `{route, reason}` | 답이 모양을 지키지 않음(routes에 없는 route를 고른 경우 포함) |
| APPROVAL | `{approved: true, approver, comment}` | 반려(→ comment, 비었으면 `(사유 없음)`. `output`은 `{approved: false, ...}`) |
| forEach step | 반복별 output 리스트 | 반복 중 하나라도 실패 → `id[i]: 사유`를 줄바꿈으로 이은 것 |

**실패가 `onFailure`로 가는 경우와 Workflow가 바로 FAILED가 되는 경우**

"값이 틀렸다"는 **비즈니스 실패**만 `onFailure`로 간다. 설정 오류나 시스템 오류는 `onFailure`를 무시하고 그 자리에서 FAILED로 끝난다.
재시도 루프가 시스템 오류를 되풀이하지 않게 하기 위해서다.

| `onFailure`를 따름(step 실패) | `onFailure`를 무시하고 바로 FAILED |
|---|---|
| `input` 표현식 계산 오류(jq 오류, 값이 여러 개 나옴) | Agent가 caller에게 허용되지 않음 |
| `forEach` 표현식 계산 오류, 결과가 리스트가 아님 | Tool 이름이 없거나 caller 화이트리스트에 없음 |
| `forEach` 반복 중 하나라도 실패 | Tool 메서드가 예외를 던짐 |
| 채운 input이 Agent `input` 모양이 아님 | LLM 호출 예외(API 오류, prompt `{변수}` 누락 등) — AGENT/SUPERVISOR/ROUTER 모두 |
| LLM 답이 Agent `output`(또는 엔진이 정한) 모양이 아님 | `forEach` 반복 중 하나가 예외를 던짐 |
| TOOL이 실패로 답함(`{success: false}`, `실패`로 시작하는 글자) | `maxIterations` 초과 |
| SUPERVISOR `pass=false` | Workflow `output.value`를 계산하지 못함, `output.schema`와 맞지 않음 |
| ROUTER가 route를 고르지 못함(routes에 없는 이름 포함) | |
| APPROVAL 반려 | |

> 아래 3.1~3.11은 step 항목마다 자세히 적었다. 기동 시 검사는 `YamlDefinitionLoader`(쓸 수 없는 키, 값 모양)와
> `WorkFlowRegistry.validateStepShape()`/`validateTemplate()`(부르는 대상의 계약, 필수 값, 표현식과 참조),
> 실행 동작은 `WorkFlowExecutor`와 각 `StepExecutor`가 한다.

#### 3.1 `id`

```yaml
    - id: validate
```

- step 이름이다. `onSuccess`/`onFailure`/`routes`의 이동 대상, `.steps.<id>` 표현식, 실행 이력(`STEP_ID`)에 쓰인다.
- Workflow 안에서만 유일하면 된다. 다른 Workflow의 step id와 겹쳐도 된다.
- **영문, 숫자, 밑줄(`_`)만** 쓴다(camelCase 권장. 예: `validateEach`). jq에서 `.steps.validate-each`는 "`.steps.validate` 빼기 `each`"로 읽히기 때문이다.
- 예약어 `SUCCESS`/`FAIL`은 id로 쓸 수 없다(기동 실패).
- forEach 반복의 실행 이력은 `id[0]`, `id[1]`...로 남는다.

| 경우 | 결과 |
|---|---|
| 생략 | 기동 실패 `모든 step은 id가 있어야 합니다` |
| 같은 Workflow 안에서 중복 | 기동 실패 `step id가 중복되었습니다` |
| `-`, `.`, 한글 등이 들어감 | 기동 실패 `step id는 영문, 숫자, 밑줄(_)만 쓸 수 있습니다 ...` |
| `SUCCESS`/`FAIL`을 id로 씀 | 기동 실패 `SUCCESS/FAIL은 흐름을 끝내는 예약어라 step id로 쓸 수 없습니다` |
| 없는 id를 `.steps.x`로 참조 | 기동 실패 `이 Workflow에 'x' step이 없습니다` |

#### 3.2 `type`

```yaml
      type: AGENT        # AGENT | TOOL | SUPERVISOR | ROUTER | APPROVAL
```

| 값 | 하는 일 | record | 담당 실행기 |
|---|---|---|---|
| `AGENT` | Agent(LLM)를 한 번 부른다 | `AgentStepDefinition` | `AgentStepExecutor` |
| `SUPERVISOR` | Agent에게 pass/fail을 판정하게 한다 | `SupervisorStepDefinition` | `SupervisorStepExecutor` |
| `ROUTER` | Agent에게 여러 갈래 중 하나를 고르게 한다 | `RouterStepDefinition` | `RouterStepExecutor` |
| `TOOL` | Tool 하나를 LLM 없이 이름으로 직접 부른다 | `ToolStepDefinition` | `ToolStepExecutor` |
| `APPROVAL` | 사람의 승인/반려를 기다린다 | `ApprovalStepDefinition` | `ApprovalStepExecutor` |

- `type` 값이 어느 record로 읽을지를 정한다. 그래서 `type`은 다른 항목보다 먼저 결정되고, 나머지 키는 그 record에 있는 것만 쓸 수 있다.
- **대문자로 정확히** 적는다. `agent`, `Agent`는 기동 실패 `step의 type이 없거나 올바르지 않습니다(적은 값 = agent, 쓸 수 있는 값 = AGENT, SUPERVISOR, ROUTER, TOOL, APPROVAL)`다.
- 생략해도 같은 메시지로 기동 실패.

#### 3.3 `ref`

```yaml
      ref: sample-structured-extract-agent   # AGENT/SUPERVISOR/ROUTER → Agent id
      ref: validateSqlSyntax                 # TOOL → Tool 이름
```

- AGENT/SUPERVISOR/ROUTER: `agents/**/*.yml`의 `agent.id`. **기동 시 Agent가 있는지 검사**하고, 그 Agent의 계약으로 step `input`과 참조 경로를 검사한다.
- SUPERVISOR/ROUTER가 부르는 Agent는 **`output`을 선언하지 않는다**(답의 모양은 엔진이 정한다). 선언돼 있으면 기동 실패.
- TOOL: `@Tool` 메서드 이름(메서드 이름이 기본값, `@Tool(name=...)`이면 그 이름) 또는 MCP 서버가 알려 준 Tool 이름(예: `directory_tree`).
  기동 시 Tool을 찾으면 인자 이름을 검사하고([3.4](#34-input)), 찾지 못하면(MCP 서버가 아직 안 떴거나 이름 오타) **경고만 남기고** 실행할 때 다시 찾는다.
- APPROVAL: `ref` 키가 없다. 적으면 기동 실패 `'ref'는 이 자리(ApprovalStepDefinition)에서 쓸 수 없는 키입니다`.

| 경우 | 결과 |
|---|---|
| AGENT류/TOOL에서 생략 | 기동 실패 `AGENT step은 ref(Agent id)가 있어야 합니다`(TOOL은 `ref(Tool 이름)`) |
| 등록되지 않은 Agent id | 기동 실패 `agents/*.yml에 'x' Agent가 없습니다` |
| SUPERVISOR/ROUTER가 `output`을 선언한 Agent를 부름 | 기동 실패 `SUPERVISOR step이 부르는 agent[x]는 output을 선언하지 않습니다(...)` |
| Agent의 `allowedCallers`가 이 caller를 막음 | 실행 중 FAILED `agent[x]는 caller[y]에게 허용되지 않았습니다` |
| 기동 시 Tool을 찾지 못함 | 기동은 통과(경고 로그). 실행할 때도 없으면 FAILED `caller[y]가 쓸 수 있는 Tool 중 'x'가 없습니다` |
| `dstone.ai.tool.allowed-by-caller`가 이 caller에게 그 Tool을 막음 | 실행 중 FAILED(위와 같은 메시지) |

- TOOL step은 Agent의 `toolsEnabled`와 관계없이 Tool을 부른다. Tool 화이트리스트(`allowed-by-caller`)만 적용된다.

#### 3.4 `input`

step에 넣을 값이다. step이 실행되기 **직전에** 엔진이 표현식을 계산한다(`WorkFlowExecutor.resolveInput()` → `ExpressionEvaluator.resolve()`).
값 전체가 `"${ ... }"`이면 jq로 계산해 **타입 그대로** 넣고, 그 밖의 값은 적힌 그대로 넣는다. 문법은 [5절](#5-표현식-문법-jq).

**step 종류별 모양** — 모양은 부르는 대상의 input 계약이 정한다.

| step | 부르는 대상의 input | YAML 모양 | 계산한 값이 가는 곳 |
|---|---|---|---|
| AGENT / SUPERVISOR / ROUTER | Agent `input`이 `string`(기본) | 값 하나(표현식 또는 글자) | LLM **사용자 메시지** 그대로 |
| AGENT / SUPERVISOR / ROUTER | Agent `input`이 `object` | 맵(필드 이름: 표현식 또는 리터럴), 또는 객체를 돌려주는 표현식 하나 | Agent input 스키마로 검사 → JSON 글자로 바꿔 사용자 메시지. 필드들은 Agent prompt의 `{변수}`도 채운다 |
| TOOL | Tool 인자 스키마 | 맵(인자 이름: 표현식 또는 리터럴) | JSON으로 바꿔 Tool 인자 |
| APPROVAL | — | 쓸 수 없음 | — |

```yaml
      # Agent input이 string: 값 하나
      input: "${ .steps.analyze.output }"

      # 글자를 이어 붙일 때는 jq로. 안에 큰따옴표가 있으면 YAML은 작은따옴표로 감싼다
      input: '${ "[분석 결과]\n" + .steps.analyze.output + "\n\n[검수 의견]\n" + .steps.designReview.output.comment }'

      # Agent input이 object(예: {sql, error}): 맵. 필드 이름은 Agent input의 properties와 맞아야 한다
      input:
        sql: "${ .steps.validate.input.sql }"
        error: "${ .steps.validate.error }"
        mode: strict                                    # 표현식이 아닌 값은 그대로

      # TOOL: 맵. 표현식의 결과는 타입(리스트, 숫자, 맵) 그대로 넘어간다
      input:
        sql: "${ .steps.extract.output.sql }"
        tables: "${ .steps.extract.output.tables }"     # 리스트 그대로
        count: "${ .steps.extract.output.tables | length }"
        limit: 100
```

- AGENT/SUPERVISOR/ROUTER는 **input이 필수**다. "생략하면 직전 step 결과" 같은 숨은 규칙은 없다.
- **문자열 중간에 `${ }`를 섞지 않는다**(`"대상: ${ .input }"`은 기동 실패). 글자 조합은 jq의 `+`로 한다.
  섞기를 허용하면 "객체를 글자 중간에 넣으면 어떻게 되나" 같은 모호한 규칙이 생기기 때문이다.
- **기동 시 모양 검사** — 부르는 대상의 스키마 타입과 YAML 모양을 비교한다.
  - string ↔ 값 하나, object ↔ 맵, array ↔ 리스트여야 한다.
  - object면 맵의 이름이 `properties`에 있어야 하고(`additionalProperties`를 열어 둔 경우 제외), `required` 이름이 모두 있어야 한다. TOOL도 같은 규칙으로 Tool 인자 스키마와 대조한다.
  - 값 전체가 표현식 하나뿐이면 계산해 봐야 모양을 알 수 있으므로 기동 시에는 넘어가고 **실행 중에 검사**한다.
- **실행 중 모양 검사** — AGENT류는 계산한 값을 Agent input 스키마로 다시 검사한다. 틀리면 step 실패 → `onFailure`.
- 표현식이 없는 값을 가리키면 오류가 아니라 **`null`**이다(jq 규칙). 기본값이 필요하면 `//`를 쓴다(`"${ .steps.fix.output.sql // .input }"`).
  Agent input이 string인데 `null`이 들어가면 `input이 비어 있습니다`로 step 실패다.
- 계산한 값은 `steps.<id>.input`에 남는다. 그래서 다음 step이 `"${ .steps.validate.input.sql }"`처럼 "실패한 입력값"을 다시 꺼낼 수 있다.
- Agent prompt의 `{변수}`는 **그 Agent가 받은 input(object)의 필드**로 채운다. Workflow input은 prompt에 직접 들어가지 않으므로,
  prompt에 넣을 값이 있으면 step `input` 맵으로 넘긴다([agent-yml-guide.md](../agents/agent-yml-guide.md) 참고).
- YAML에서 `${`로 시작하는 값은 **항상 따옴표로 감싼다**. jq 식 안에 `: `, `#`, `{` 같은 YAML 특수 문자가 들어가도 안전하다.

| 경우 | 결과 |
|---|---|
| AGENT류에서 생략 | 기동 실패 `input이 있어야 합니다(Agent에게 무엇을 넣을지)` |
| Agent input이 string인데 맵을 적음 | 기동 실패 `agent[x]의 input이 string이라 step의 input은 값 하나로 적어야 합니다` |
| Agent input이 object인데 글자를 적음(표현식 하나는 제외) | 기동 실패 `agent[x]의 input이 object라 step의 input은 맵으로 적어야 합니다` |
| 맵에 없는 이름 / required 이름 누락 | 기동 실패 `input의 'x'는 agent[y]의 input에 없는 이름입니다` / `input에 'x'가 빠졌습니다` |
| TOOL 인자 이름이 Tool 스키마에 없음 | 기동 실패 `input의 'query'는 Tool[validateSqlSyntax]의 인자에 없는 이름입니다(쓸 수 있는 이름 = [sql])` |
| TOOL에 글자를 적음 | 기동 실패 `[workflow.steps[i].input] 값의 모양이 맞지 않습니다(맵이어야 합니다 ...)` |
| APPROVAL에 적음 | 기동 실패 `'input'는 이 자리(ApprovalStepDefinition)에서 쓸 수 없는 키입니다` |
| 표현식 문법 오류, 없는 step, 흐름상 먼저 실행될 수 없는 step 참조 | 기동 실패([5절](#5-표현식-문법-jq) 기동 시 검사) |
| 스키마에 없는 필드 참조 | 기동 경고. 실행하면 `null` |
| 분기로 실행되지 않았을 수 있는 step 참조 | 기동은 통과. 실행되지 않았으면 `null`이므로 `//`로 기본값을 둔다 |
| 계산한 값이 Agent input 모양이 아님 | step 실패 `agent[x]의 input 모양이 맞지 않습니다: [...]` → `onFailure` |
| jq 실행 오류(예: 글자에 숫자 더하기), 결과가 여러 개 | step 실패 `input을 계산하지 못했습니다 - ...` → `onFailure` |

#### 3.5 `onSuccess`

```yaml
      onSuccess: validate      # 다른 step id | SUCCESS | FAIL
```

| 값 | 동작 |
|---|---|
| 생략 | 목록상 다음 step. 마지막 step이면 Workflow 성공(DONE) |
| 뒤쪽 step id | 그 step으로 건너뛴다 |
| 앞쪽 step id 또는 자기 자신 | 그 step으로 되돌아간다(루프). `maxIterations`가 상한 |
| `SUCCESS` | 그 자리에서 Workflow 성공. 결과는 `output.value` |
| `FAIL` | 그 자리에서 Workflow 실패. 메시지는 `step[id]가 onSuccess: FAIL로 Workflow를 끝냈습니다: <이 step의 output>` |

- 예약어는 **대문자로 정확히** 적는다. `success`는 step id로 읽힌다.
- 이동 대상은 **기동 시 검사한다**. 이 Workflow의 step id나 `SUCCESS`/`FAIL`이 아니면 기동 실패
  `onSuccess/onFailure/routes의 'x'는 없는 step입니다(쓸 수 있는 값 = [...], SUCCESS, FAIL)`.
- ROUTER에는 `onSuccess` 키가 없다(적으면 기동 실패). 성공하면 `routes`로 간다.

#### 3.6 `onFailure`

```yaml
      onFailure: fix           # 다른 step id | SUCCESS | FAIL
```

| 값 | 동작 |
|---|---|
| 생략 | Workflow 실패. 메시지는 `step[id]가 실패했고 onFailure가 지정되지 않았습니다: <실패 사유>` |
| 앞쪽 step id | **재시도 루프**. 되돌아간 step은 `"${ .steps.<실패한 id>.error }"`로 사유를, `"${ .steps.<id>.input.<인자> }"`로 실패한 입력을 읽는다 |
| 뒤쪽 step id | 실패 처리용 step으로 건너뛴다 |
| `SUCCESS` | 실패했지만 Workflow를 **성공**으로 끝낸다. 결과는 `output.value` |
| `FAIL` | Workflow 실패. 메시지는 실패 사유 그대로 |

- `onFailure`는 **비즈니스 실패**에만 적용된다. 설정/시스템 오류는 무시하고 바로 FAILED다(3절 "onFailure로 가는 경우" 표).
- ROUTER에서는 LLM이 route를 **고르지 못했을 때**(답이 `{route, reason}` 모양이 아니거나 routes에 없는 이름을 골랐을 때) 쓰인다.
- ⚠️ APPROVAL의 `onFailure`로 **앞 step으로 되돌리는 루프는 쓰지 않는다.** 한 번 기록된 결정(`approvals.<id>`)은 실행이
  끝날 때까지 지워지지 않아서, 되돌아와도 같은 APPROVAL step이 예전 반려 결정을 다시 읽고 **즉시 또 반려**된다.
  `onFailure: FAIL`로 끝내고, 고쳐서 다시 하려면 새 실행을 시작한다.

#### 3.7 `routes` (ROUTER 전용)

```yaml
      routes:
        billing: billingStep
        technical: technicalStep
        other: SUCCESS
```

- `route 이름: 이동할 곳(step id 또는 SUCCESS/FAIL)` 맵이다.
- ROUTER Agent는 `{route, reason}` JSON으로 답하도록 강제된다(`RouterStepExecutor`). 엔진은 route에 **`enum: [routes의 키들]`**을 건 스키마를 LLM에게 보여주고
  답도 그 스키마로 검사한다. 그래서 routes에 없는 이름을 고른 답은 "모양이 틀린 답"으로 step 실패 → `onFailure`다.
- **각 route를 언제 고르는지 기준은 Agent prompt에 적어 둬야 한다**([agent-yml-guide.md](../agents/agent-yml-guide.md)의 ROUTER용 Agent).
- 고른 경로와 이유는 `"${ .steps.<id>.output.route }"`, `"${ .steps.<id>.output.reason }"`. 갈라진 다음 step은 원래 메시지가 필요하면 `"${ .input... }"` 등으로 **명시해서** 받는다.

| 경우 | 결과 |
|---|---|
| ROUTER인데 생략 또는 `{}` | 기동 실패 `ROUTER step은 routes를 최소 1개 이상 정의해야 합니다` |
| ROUTER가 아닌 step에 적음 | 기동 실패 `'routes'는 이 자리(...)에서 쓸 수 없는 키입니다` |
| LLM이 routes에 없는 이름을 고름 | step 실패 `라우팅 Agent 응답을 {route, reason} 모양으로 받지 못했습니다 - ...` → `onFailure` |
| 값(이동 대상)이 없는 step id | 기동 실패 `onSuccess/onFailure/routes의 'x'는 없는 step입니다` |

#### 3.8 `forEach`

```yaml
      forEach: "${ .input.sqlList }"                        # 요청의 리스트
      forEach: "${ .steps.tree.output }"                    # 앞 step이 돌려준 리스트
      forEach: "${ .steps.a.output.items // .input.list }"  # 기본값
      forEach: "${ [.steps.tree.output[] | select(.type == \"file\")] }"   # 걸러낸 리스트
```

- 표현식이 돌려준 **리스트의 항목 수만큼 이 step을 동시에** 실행한다(`WorkFlowExecutor.runForEach()`).
- 반복마다 이번 항목을 **jq 변수 `$item`**(또는 `itemVariable` 이름)으로 넣고 `input`을 계산한다. 항목이 맵이면 `$item.name`처럼 안으로 들어간다.
  `$item`은 그 step의 `input` 안에서만 쓸 수 있다(forEach 표현식 자체나 다른 step에서는 기동 실패).
- 결과:
  - `steps.<id>.input` = 반복별로 받은 값의 리스트, `steps.<id>.output` = 반복별로 돌려준 값의 리스트. **항목 순서대로** 쌓인다(끝난 순서가 아니다).
  - 반복이 **하나라도 실패하면 step 전체가 실패**다. `error`에는 실패한 반복마다 `id[i]: 사유`가 모인다.
  - 실행 이력은 `id[0]`, `id[1]`...로 반복마다 한 줄씩 남는다.
- `maxIterations`는 항목 수와 관계없이 1로 센다.

| 경우 | 결과 |
|---|---|
| 표현식이 아닌 글자(`forEach: input.sqlList`) | 기동 실패 `forEach는 리스트를 돌려주는 표현식으로 적습니다` |
| 표현식 문법 오류, 없는 step | 기동 실패 |
| APPROVAL이나 ROUTER에 적음 | 기동 실패 `'forEach'는 이 자리(...)에서 쓸 수 없는 키입니다` |
| 실행 중 jq 오류 | step 실패 `forEach - ... 계산하지 못했습니다` → `onFailure` |
| 결과가 리스트가 아님(글자, 맵, null) | step 실패 `forEach[...]의 값이 리스트가 아닙니다` → `onFailure` |
| 빈 리스트 | 0회 실행, **성공**(`output = []`) |
| 반복 하나가 예외(Tool 없음 등) | 그 반복의 이력을 남기고 바로 FAILED |
| 다른 step에서 `$item` 사용 | 기동 실패 `$item is not defined (이 자리에서는 jq 변수를 쓸 수 없습니다 ...)` |

- 병렬 실행은 JVM 공용 스레드 풀(`CompletableFuture.supplyAsync`)을 쓴다. 동시에 도는 개수는 CPU 코어 수에 따라 제한된다.
- forEach 반복은 대화 기억 없이 각자 `input`만 보고 답한다(`memory: true`와 함께 쓸 수 없다 — 3.11 참고).
- MCP Tool 호출은 서버별로 한 번에 하나씩만 실행된다(`ConfigMcp.SerializedToolCallback`). MCP TOOL forEach는 사실상 순차로 돈다.

#### 3.9 `itemVariable`

```yaml
      forEach: "${ .steps.tree.output }"
      itemVariable: entry
      input:
        path: '${ .input + "/" + $entry.name }'
```

- forEach 반복에서 이번 항목을 받는 **jq 변수 이름**이다. 생략하면 `item`(→ `$item`).
- 영문, 숫자, 밑줄만 쓴다(아니면 기동 실패).
- AGENT/SUPERVISOR/TOOL에서 `forEach`를 비워 두고 적으면 무시된다. APPROVAL/ROUTER에는 `itemVariable` 키가 없어서 적으면 기동 실패다.
- 변수라서 컨텍스트(`.input`, `.steps`)와 이름이 겹치지 않는다.

#### 3.10 `approverRole` (APPROVAL 전용)

```yaml
    - id: designReview
      type: APPROVAL
      approverRole: "PL"
```

- "누가 승인해야 하는지"를 YAML을 읽는 사람에게 알려 주는 **기록용 값**이다. `ApprovalStepDefinition`에만 있는 키라서 다른 step에 적으면 기동 실패다.
- 엔진은 이 값을 **어디에도 쓰지 않는다**. 권한 검사를 하지 않고, API 응답에도 나가지 않는다. 승인 API(`POST /api/ai/workflow/executions/{executionId}/decision`)는
  누가 부르든 받아들인다. 실제 권한 통제는 호출하는 쪽 화면이나 서비스가 맡는다.
- 참고로 APPROVAL의 동작은 이렇다.
  1. 처음 실행하면 결정이 없으므로 Workflow를 `WAITING_APPROVAL`로 멈추고 저장한다.
  2. `/decision`에 `{approved, approver, comment}`가 오면 `approvals.<id>`에 기록하고 **같은 step을 다시 실행**한다.
  3. 결정을 `output = {approved, approver, comment}`로 남기고, 승인이면 성공, 반려면 실패(`error` = comment).
  - 승인 대기 상태가 아닌 실행에 결정을 보내면 거절된다(`지금 승인 대기 상태가 아닙니다`).
  - 승인할 내용은 사람이 실행 상세 화면의 `context.steps`(앞 step들의 결과)에서 확인한다. APPROVAL은 값을 넘겨주지 않으므로,
    다음 step은 필요한 문서를 `"${ .steps.<앞 step>.output }"`으로 직접 가져온다.

#### 3.11 `memory` (AGENT/SUPERVISOR/ROUTER 전용)

```yaml
    - id: convert
      type: AGENT
      ref: sql-converter-agent
      input: "${ .steps.validate.error // .input }"
      memory: true        # 비우면 false
```

- LLM이 **이전 대화를 기억할지**를 step마다 정한다. 기억할지는 Agent가 아니라 "그 Agent를 어떤 흐름에서 부르는지"에 달려
  있으므로 Agent YAML이 아니라 step에 적는다(같은 Agent라도 한 Workflow에서는 한 번만, 다른 Workflow에서는 재작성 루프로 부를 수 있다).

| 값 | 대화방 | 동작 |
|---|---|---|
| `false`(기본) | 없음 | 이전 대화 없이 시스템 프롬프트 + 이번 `input`만 보고 답한다 |
| `true` | `sessionId:stepId` | 이 step이 이전에 자기가 나눈 대화(질문+답)를 함께 받는다. 답한 뒤 이번 대화도 저장한다 |

- **다른 step의 대화는 어느 쪽이든 섞이지 않는다.** step 사이에 값을 넘기는 방법은 `input` 표현식(`"${ .steps.<id>.output }"`) 하나뿐이다.
  예전처럼 한 실행의 모든 step이 대화를 공유하면, 앞 step의 출력 형식 지시("JSON으로만 답하라" + 스키마)와 역할이 뒤 step에
  새어 들어가 답의 모양이 흐트러지고, 같은 내용이 `input`과 대화 기록으로 두 번 들어가 토큰이 낭비됐다.
- `true`가 쓸모 있는 곳: `onFailure`로 같은 step에 되돌아오는 **재작성 루프**. 이전 시도와 그때의 답을 기억하므로 같은 실수를 덜 반복한다.
- 같은 `sessionId`로 다시 실행하면 이전 실행에서 그 step이 나눈 대화도 이어진다(대화방 이름이 같으므로). 요청에 `sessionId`를 비우면
  실행마다 새로 발급되므로 섞이지 않는다.
- 기억하는 양은 대화방마다 최근 `dstone.ai.session.max-messages`개(기본 20)다.
- `forEach`와 함께 쓰면 **기동 실패**다(동시에 도는 반복들이 한 대화방에 섞여 쓰인다). TOOL/APPROVAL에는 키가 없어서 적으면 기동 실패다.
- 채팅 API(`/api/ai/chat`)는 이 옵션과 상관없이 항상 요청의 `sessionId`로 대화를 이어 간다.

### 4 스키마 (JSON Schema)

Workflow `input`/`output.schema`와 Agent `input`/`output`의 모양은 **표준 JSON Schema(2020-12)를 YAML로 그대로** 적는다
(`common.definition.SchemaDefinition`, 도구는 `common.schema.JsonSchemas`, 검사기는 networknt json-schema-validator).

```yaml
input: string                        # 축약형 = input: {schema: {type: string}}

input:
  schema:                            # 표준형: schema 아래에 JSON Schema
    type: object
    properties:
      name: { type: string, description: 사용자의 풀네임 }
      age: { type: integer, minimum: 0 }
      skills: list<string>           # properties 값에도 축약형 가능
    required: [name]
```

**축약형** — 타입만 필요한 세 자리(최상위, `properties`의 값, `items`)에서는 타입 이름 하나만 적어도 된다. 로더가 표준 모양으로 펼친다.

| 축약형 | 펼친 모양 |
|---|---|
| `string` / `number` / `integer` / `boolean` / `object` / `array` | `{type: <그 이름>}` |
| `list<string>` | `{type: array, items: {type: string}}` |
| `list<list<integer>>` | `{type: array, items: {type: array, items: {type: integer}}}` |

- 그 밖의 JSON Schema 키워드(`required`, `enum`, `minimum`, `maxLength`, `pattern`, `additionalProperties`, `description` 등)는 그대로 쓴다.
- `description`은 LLM에게 그대로 전달된다(Agent `output`). 필드의 뜻을 적어 두면 답의 품질이 좋아진다.
- `required`에 넣은 필드만 필수다. **`required`를 빠뜨리면 모든 필드가 선택**이므로, LLM이 빈 객체 `{}`로 답해도 통과한다. 꼭 필요한 필드는 `required`에 넣는다.
- 스키마 자체가 틀리면(예: `type: strin`) 엔진이 켜질 때 메타스키마 검사로 기동 실패다.
- **기동 시 경로 검사 규칙**(`JsonSchemas.checkPath()`) — `"${ .steps.a.output.b.c }"` 같은 표현식이 읽는 경로를 스키마를 따라 내려가며 본다. 어긋나면 **경고**다.
  - object: 다음 이름이 `properties`에 있어야 한다(`properties`가 없거나 `additionalProperties`를 열어 두었으면 더 보지 않는다).
  - array: 다음은 `[0]`처럼 몇 번째 항목인지(0부터)여야 한다.
  - string/number/integer/boolean: 그 아래로 더 들어갈 수 없다.
  - `type`이 없거나 리스트(`[string, "null"]`)면 더 보지 않는다.

### 5 표현식 문법 (jq)

`common.schema.ExpressionEvaluator`가 처리한다. 표현식 안은 **jq** 문법이다(Java 구현 jackson-jq, jq 1.7 문법).
jq는 JSON 변환 전용 언어라 필드 추출, 배열 필터, 객체 재조립을 한 줄로 쓸 수 있고, **부작용이 없어서** YAML 작성자가 서버에서 임의 코드를 실행할 수 없다.
같은 조합(`${ }` + jq)을 CNCF Serverless Workflow 1.0도 쓴다.

**규칙은 하나**

> 값 전체가 `${`로 시작하고 `}`로 끝나면 **표현식**이다. 계산 결과를 **타입 그대로** 넘긴다. 그 밖의 값은 **리터럴**이다.
> 맵과 리스트는 안쪽 값마다 같은 규칙을 적용한다.

**표현식이 보는 값** — 실행 컨텍스트 중 `input`과 `steps`만 보인다(`approvals`는 엔진 내부용이라 숨긴다).

```json
{
  "input": "요청의 input 그대로(글자 또는 객체)",
  "steps": {
    "extract": { "input": "...", "output": { "sql": "..." }, "error": null },
    "validate": { "input": { "sql": "..." }, "output": { "success": false, "message": "..." }, "error": "..." }
  }
}
```

| 표현식 | 의미 |
|---|---|
| `"${ .input }"` | 실행 요청의 input 전체(글자면 그 글자) |
| `"${ .input.<필드> }"` | input이 object일 때 그 필드 |
| `"${ .steps.<id>.output }"` / `.output.<경로>` | 그 step이 돌려준 값([3절 output 표](#3-step-항목)) |
| `"${ .steps.<id>.input }"` / `.input.<경로>` | 그 step이 실제로 받은 값(TOOL이면 인자 맵) |
| `"${ .steps.<id>.error }"` | 그 step의 실패 사유 |
| `"${ .steps.<id>.output[0].name }"` | 리스트는 `[번호]`로 항목 선택(0부터) |
| `"${ .input["user name"] }"` | 이름에 공백·점·한글 등이 있으면 `["..."]`로 |
| `"${ $item }"` (또는 `$<itemVariable>`) | forEach 반복 중 이번 항목(forEach step의 `input` 안에서만) |
| `"${ .a // .b // "기본값" }"` | 왼쪽이 `null`/`false`면 오른쪽 |
| `"${ .steps.tree.output \| length }"` | 계산(길이, 합계 등) |
| `'${ "요약: " + .steps.draft.output }'` | 글자 이어 붙이기 |
| `"${ [.steps.tree.output[] \| .name] }"` | 리스트에서 필드만 모으기 |
| `"${ .steps.check.output \| map(select(.success == false)) }"` | 조건으로 걸러내기 |
| `"${ {sql: .steps.fix.output.sql, tries: 2} }"` | 객체 새로 만들기 |
| `"${ .steps.classify.output.route == "billing" }"` | 조건식(true/false) |

- **문자열 중간에 섞지 않는다.** `"요약: ${ .x }"`는 기동 실패다. 글자 조합은 jq로 쓴다(`'${ "요약: " + .x }'`).
  글자와 숫자를 붙일 때는 `tostring`을 쓴다(`'${ "개수: " + (.list | length | tostring) }'`).
- **따옴표** — `${`로 시작하는 값은 항상 따옴표로 감싼다. jq 식 안에 큰따옴표가 있으면 YAML은 작은따옴표(`'...'`)로 감싸는 것이 가장 읽기 쉽다.
- **없는 값은 `null`** — 경로가 없어도 오류가 아니다. 기본값은 `//`로 준다. 반대로 jq 실행 오류(글자에 숫자 더하기 등)는 그 step의 실패다.
- **결과는 하나** — `.list[]`처럼 값이 여러 개 나오면 오류다(`값이 2개 나왔습니다 ... [ ]로 감싸십시오`). 리스트로 받으려면 `[ ]`로 감싼다.
  하나도 나오지 않으면(`empty`, 걸러져서 없음) `null`이다.
- **쓰는 곳은 세 군데**: step `input`, step `forEach`, Workflow `output.value`.
  Agent의 system prompt는 이 문법이 아니라 `{변수}`(Spring AI PromptTemplate)를 쓰고, 값은 그 Agent가 받은 input 필드로 채운다.
- `${VAR_NAME}`(대문자, 공백 없음)은 기동 시 환경 값 치환이다(1절). 표현식과는 다른 것이다.

**기동 시 검사**(`WorkFlowRegistry.validateTemplate()`)

| 검사 | 결과 |
|---|---|
| 예전 문법 `{{ ... }}` | 기동 실패 `{{ }} 문법은 쓰지 않습니다. 값 전체를 jq 표현식으로 적으십시오` |
| 글자 중간에 `${ }` | 기동 실패 `글자 중간에 ${ }를 섞어 쓸 수 없습니다` |
| jq 문법 오류 | 기동 실패 `jq 문법이 올바르지 않습니다 - ...` |
| 없는 함수(예: `.steps.my-step` → `step` 함수), 쓸 수 없는 변수(`$item`을 forEach 밖에서) | 기동 실패 `Function step/0 does not exist` / `$item is not defined` |
| `.steps.<id>`의 step이 없음 | 기동 실패 `이 Workflow에 'x' step이 없습니다` |
| `.steps.<id>` 다음이 `input`/`output`/`error`가 아님 | 기동 실패 `.steps.x 다음에는 [input, output, error] 중 하나가 와야 합니다` |
| 읽는 step이 흐름상 그 step 뒤에 실행될 수 없음 | 기동 실패 `step[x]는 흐름상 이 step보다 먼저 실행될 수 없어서 그 결과를 읽을 수 없습니다` |
| 그 뒤의 필드가 스키마에 없음(`.input.x`, `.steps.a.output.x`) | **경고**(기동은 됨). 실행하면 `null` |

- "흐름상 먼저 실행될 수 있는가"는 `onSuccess`/`onFailure`/`routes`(비우면 목록의 다음 step / FAIL)를 따라가 본다.
  예: 첫 step이 뒤 step의 결과를 읽으면 막는다. 재시도 루프(`validate` → `fix` → `validate`)처럼 되돌아오는 길이 있으면 허용한다.
  `output.value`는 모든 step이 끝난 뒤라 이 검사를 하지 않는다.
- 경로는 식의 글자를 보고 찾는다. 흔한 모양(`.steps.a.output.b`, `[0]`, `["이름"]`)만 알아보고, `|` 뒤나 jq 변수 안의 경로는 검사하지 않는다.

### 6 제공되는 샘플

`src/main/resources/{agents,workflows,mcp}/` 아래에 있다. 각 파일 맨 위 주석에 dstone-boot "Workflow 테스트"
화면에서 어떤 workflowId/input으로 호출하면 되는지 적혀 있다.

**Workflow**

| 기능 | Workflow | input |
|---|---|---|
| AGENT step 기본(변수/Tool 없이) | `sample/sample-agent-basic-echo.yml` | string |
| AGENT의 자율 tool-calling | `sample/sample-agent-tool-calling.yml` | string |
| AGENT의 RAG(ragEnabled) 증강 | `sample/sample-agent-rag-augmented.yml` | string |
| Agent별 model override | `sample/sample-agent-model-override.yml` | string |
| TOOL step 연쇄(LLM 없이) + `output.value`를 맵으로 | `sample/sample-tool-chain-basic.yml` | string |
| MCP Tool(JSON 응답) + 앞 step output으로 `forEach` + `itemVariable` + jq 글자 이어 붙이기 | `sample/sample-mcp-filesystem-list.yml` | string |
| TOOL로 RAG 검색만 직접 호출 | `sample/sample-tool-rag-search.yml` | string |
| 위험 Tool(http/shell/python) 기본 거부 확인 | `sample/sample-tool-gated-external.yml` | string |
| Agent `output`(object) → `.steps.<id>.output.<필드>`, `output.schema` | `sample/sample-structured-output-chain.yml` | string |
| SUPERVISOR(pass/reason) 판정 + object Agent input(prompt `{role}` ← Agent input 필드) | `sample/sample-supervisor-verdict-gate.yml` | object `{message, role}` |
| ROUTER 다지 분기(routes) + jq 글자 이어 붙이기 + `//`로 결과 고르기 | `sample/sample-router-multiway.yml` | object `{message, role}` |
| onFailure 재시도 루프 + object input Agent + `//`/`.steps.<id>.error` | `sample/sample-loop-retry-until-valid.yml` | string |
| `forEach` 병렬 실행 + object Workflow input | `sample/sample-foreach-parallel.yml` | object `{sqlList}` |
| APPROVAL 일시중단/재개(HITL) | `sample/sample-approval-pause-resume.yml` | string |
| 문서 읽기(MCP TOOL) → 분석(AGENT, object output) → 리뷰(AGENT, object input) | `testApp/testApp-workflow.yml` | string(파일 경로) |

**Agent**

| Agent | input / output | 용도 |
|---|---|---|
| `sample-basic-echo-agent` | string / string | 가장 단순한 AGENT(변수/Tool 없음) |
| `sample-general-chat` | string / string | 일반 대화(`{role}` 변수 ← 채팅 API의 variables). dstone-boot 채팅 화면 기본값 |
| `sample-role-reply-agent` | **object `{role, message}`** / string | prompt `{role}`을 Agent input 필드로 채우는 예시 |
| `sample-tool-demo-agent` | string / string | 로컬 Tool 자율 호출(`toolsEnabled: true`) |
| `sample-mcp-filesystem-agent` | string / string | MCP filesystem Tool 자율 호출 |
| `sample-rag-demo-agent` | string / string | RAG 증강(`ragEnabled: true`, `ragTopK`/`ragSimilarityThreshold`) |
| `sample-model-override-agent` | string / string | Agent별 `model` 지정 |
| `sample-structured-extract-agent` | string / **object `{sql}`** | output을 object로 선언한 예시(문장에서 SQL 추출) |
| `sample-fix-agent` | **object `{sql, error}`** / **object `{sql}`** | input/output 둘 다 object인 예시(재시도 루프에서 SQL 수정) |
| `sample-verdict-judge-agent` | string / (엔진) | SUPERVISOR용(pass/reason 판정) |
| `sample-router-classifier-agent` | string / (엔진) | ROUTER용(billing/technical/other 분류) |
| `testApp-agent-01` / `testApp-agent-02` | string / object, object / string | `testApp-workflow`의 분석/리뷰 |

**MCP 서버**: `mcp/sample/sample-filesystem-mcp.yml` — 공식 filesystem 레퍼런스 서버를 STDIO로 띄워
`${APP_HOME}/${APP_NAME}/mcp/server-filesystem` 하나만 노출한다(`list_directory`/`directory_tree`/`read_text_file`/`write_file`/`edit_file`/`move_file`만 허용).

### 7 YAML 템플릿 샘플

복사해서 쓰는 뼈대다. `<...>` 자리를 채우고, 쓰지 않는 선택 항목은 지운다.

#### 7.1 Workflow 뼈대

```yaml
# <이 Workflow가 무엇을 하는지 한두 줄>
#
# dstone-boot의 "Workflow 테스트" 화면에서는 이렇게 호출해보면 됩니다:
#   workflowId: <workflow-id>
#   input     : <예시 글자 또는 JSON>
workflow:
  id: <workflow-id>
  description: <사람이 읽는 설명>
  maxIterations: 10                 # 선택. 루프가 있으면 (step 수 × 재시도 횟수)보다 넉넉하게
  # allowedCallers: [<caller>]      # 선택. 인증을 켠 환경에서만 채운다
  input: string                     # 선택(비우면 string). object면 input: {schema: {...}}
  output:
    value: "${ .steps.<stepId>.output }"   # 필수. 최종 결과로 무엇을 돌려줄지
    # schema: string                       # 선택. 결과 모양 검사
  steps:
    - id: <stepId>                  # 영문/숫자/밑줄만(camelCase 권장)
      type: AGENT
      ref: <agent-id>
      input: "${ .input }"
      onSuccess: SUCCESS
      onFailure: FAIL
```

#### 7.2 step 타입별 스니펫

**AGENT — Agent input이 string(기본)**

```yaml
    - id: summarize
      type: AGENT
      ref: <agent-id>
      input: '${ "[요약할 원문]\n" + .input }'
      # → 답은 "${ .steps.summarize.output }" (Agent output 모양. 비워 뒀으면 글자)
```

**AGENT — Agent output이 object일 때 필드를 콕 집어 쓰기**

```yaml
    - id: extract
      type: AGENT
      ref: <output: {schema: {type: object, properties: {sql: ...}}}을 선언한 agent-id>
      input: "${ .input }"
      onSuccess: validate
      onFailure: FAIL
      # → 다음 step에서 "${ .steps.extract.output.sql }"
```

**AGENT — Agent input이 object일 때(필드는 Agent prompt의 {변수}도 채움)**

```yaml
    - id: fix
      type: AGENT
      ref: <input: {schema: {type: object, properties: {sql: ..., error: ...}}}을 선언한 agent-id>
      input:                            # 맵으로. 이름은 Agent input의 properties와 같아야 한다
        sql: "${ .steps.validate.input.sql }"
        error: "${ .steps.validate.error }"
      memory: true                      # 선택. 재작성 루프에서 이 step의 이전 시도를 기억(대화방 sessionId:stepId). 비우면 false
```

**TOOL**

```yaml
    - id: validate
      type: TOOL
      ref: validateSqlSyntax            # @Tool 메서드 이름 또는 MCP Tool 이름
      input:                            # 인자 이름은 기동 시 Tool 인자 스키마와 대조한다
        sql: "${ .steps.extract.output.sql }"
      onSuccess: SUCCESS
      onFailure: FAIL
      # 인자가 필요 없는 Tool이면 input을 통째로 생략한다(빈 인자 {}로 호출)
      # → "${ .steps.validate.output }": 응답이 JSON이면 그 값(여기선 {success, message}), 아니면 글자
```

**SUPERVISOR — 앞 step의 결과를 LLM이 판정**

```yaml
    - id: judge
      type: SUPERVISOR
      ref: <판정용 agent-id>            # prompt에 판정 기준을 적어 둔다. output은 선언하지 않는다
      input: "${ .steps.draft.output }" # 무엇을 판정할지 명시
      onSuccess: SUCCESS                # pass=true
      onFailure: FAIL                   # pass=false → 사유는 .steps.judge.error / .steps.judge.output.reason
```

**ROUTER — 세 갈래 이상 분기**

```yaml
    - id: classify
      type: ROUTER
      ref: <분류용 agent-id>            # prompt에 각 route를 언제 고르는지 적어 둔다. output은 선언하지 않는다
      input: "${ .input }"
      routes:
        billing: billingStep
        technical: technicalStep
        other: SUCCESS                  # 예약어도 쓸 수 있다
      # → 고른 경로와 이유는 .steps.classify.output.route, .steps.classify.output.reason
```

**APPROVAL — 사람의 승인 대기**

```yaml
    - id: review
      type: APPROVAL
      approverRole: "PL"                # 기록용. 서버가 권한을 검사하지는 않는다
      onSuccess: nextStep               # 승인 → 코멘트는 "${ .steps.review.output.comment }"
      onFailure: FAIL                   # 반려 → 앞 step으로 되돌리는 루프는 쓰지 않는다(3.6 경고 참고)
```

**forEach — 리스트 항목마다 동시에 실행**

```yaml
    - id: validateEach
      type: TOOL
      ref: validateSqlSyntax
      forEach: "${ .input.sqlList }"    # 리스트를 돌려주는 표현식. 앞 step 결과(.steps.<id>.output)도 된다
      itemVariable: sql                 # 선택. 비우면 $item
      input:
        sql: "${ $sql }"
      onSuccess: SUCCESS
      onFailure: FAIL
      # → .steps.validateEach.output = [반복별 output...], 하나라도 실패하면 step 실패
```

**재시도 루프 — 실패하면 고쳐서 다시 검증**

```yaml
workflow:
  id: <workflow-id>
  maxIterations: 6                      # 검증↔수정 최대 3회전
  input: string
  output:
    value: "${ .steps.validate.input.sql }"
  steps:
    - id: validate
      type: TOOL
      ref: validateSqlSyntax
      input:
        sql: "${ .steps.fix.output.sql // .input }"   # 처음엔 요청 input, 수정 뒤엔 fix의 결과
      onSuccess: SUCCESS
      onFailure: fix

    - id: fix
      type: AGENT
      ref: <input {sql, error} / output {sql}을 선언한 agent-id>
      input:
        sql: "${ .steps.validate.input.sql }"
        error: "${ .steps.validate.error }"
      onSuccess: validate               # 앞쪽 step으로 → 루프
```


### 1 파일 규칙

| 종류 | 위치 | 최상위 키 | 파일 하나에 |
|---|---|---|---|
| Workflow | `src/main/resources/workflows/**/*.yml` | `workflow:` | Workflow 1개 |
| Agent | `src/main/resources/agents/**/*.yml` | `agent:` | Agent 1개 |
| MCP 서버 | `src/main/resources/mcp/**/*.yml` | `mcpServer:` | MCP 서버 1개 |

- 폴더는 자유롭게 나눠도 된다(`workflows/billing/*.yml` 등). 구분 기준은 파일 경로가 아니라 `id`다.
- 파일 이름은 `id`와 맞추는 것을 권장한다(강제는 아님).
- 모르는 키를 쓰면 기동이 실패한다. 오타에 주의한다.
- 문자열 안의 `${VAR_NAME}`은 기동 시 `conf/env{-profile}.properties` 값으로 바뀐다([4절](#4-기동-yaml-로딩과-검증)).
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
| `onFailure` | ⭕ | ⭕ | — | ⭕ | ⭕ |
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
| `{{steps.<id>.data.<키>}}` | 그 step의 구조화된 결과([7.3절 data 키 표](#73-step-항목)) |
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
      onFailure: FAIL                   # 반려 → 앞 step으로 되돌리는 루프는 쓰지 않는다(5절 경고 참고)
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
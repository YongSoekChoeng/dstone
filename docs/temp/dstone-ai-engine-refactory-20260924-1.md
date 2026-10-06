# dstone-ai-engine: Step 간 I/O 규약 재설계 (2026-09-24)

> 이 문서는 `dstone-ai-engine-refactory-20260923-1.md` §8(미결)의 결론이다.
> 2026-09-24 논의에서 방향에 합의했고, 같은 날 §5의 결정을 모두 권장안대로 확정해 구현·라이브 검증까지 마쳤다
> (구현 중 계획과 달라진 점은 §7 참고).

## 0. 목표 다시 확인

이 엔진이 지향하는 목표는 다음 한 문장이다.

> **Workflow의 조립과 step 사이의 데이터 흐름은 100% YAML로 한다.
> Java는 새로운 "부품"(Tool, StepType)을 만들 때만 건드린다.**

| 영역 | YAML만으로 가능? | 비고 |
|---|---|---|
| Workflow 흐름(순차/분기/루프/병렬/승인) | ✅ 지금도 가능 | `StepType` + `onSuccess/onFailure/routes/forEach` |
| Agent 역할/프롬프트/모델/RAG | ✅ 지금도 가능 | `agents/*.yml` |
| 외부 Tool 연결 | ✅ 지금도 가능 | MCP(`mcp/*.yml`), 화이트리스트된 http/shell/python |
| 새로운 결정적 로직(Tool 자체) | ❌ (의도된 경계) | `@AiTool` Java 작성, 또는 MCP 서버로 외부화 |
| **step 간 데이터 전달** | ⚠️ **부분적** | **← 이번 재설계 대상** |

한 가지 원칙을 같이 둔다. **YAML을 프로그래밍 언어로 만들지 않는다.** 템플릿 표현식은
"값 참조"와 "없으면 대체값(`??`)" 두 가지만 허용한다. 조건 계산이나 가공 로직이 필요하면
Tool(부품)로 뺀다.

## 1. 지금 구조의 문제 (코드 기준)

### 1.1 데이터 통로가 셋인데, step 타입마다 규칙이 다르다

| 통로 | 방식 | 어디서 쓸 수 있나 |
|---|---|---|
| `{previous}` (`variables.__previous`) | 직전 step 텍스트를 암묵적으로 넘기는 파이프 방식 | TOOL의 `inputTemplate`만. AGENT는 이 텍스트가 무조건 user 메시지가 됨 |
| `variables.{이름}` | 사용자가 처음 넘긴 입력 | TOOL의 `inputTemplate`, Agent system prompt(`{role}` 등) |
| `data` → `{stepId.키}` | 이름 붙은 명시적 핸드오프 | TOOL의 `inputTemplate`만 (Agent 쪽은 `promptSafeVariables()`가 걸러냄) |

### 1.2 구체적인 비대칭 네 가지

1. **AGENT step은 입력을 고를 수 없다.** `inputTemplate()`을 읽는 곳은 `ToolStepRunner.java:66`
   한 곳뿐이다. AGENT/SUPERVISOR/ROUTER는 무조건 "직전 step 텍스트"를 받는다. 그래서
   "analyze 결과 중 tables만 convert에 넘긴다"를 YAML로 표현할 수 없다.
2. **Agent는 `{stepId.키}`를 읽지 못한다.** ST4(`PromptTemplate`)가 키의 `.`을 처리하지 못해서
   `AgentExecutor.promptSafeVariables()`가 해당 키를 걸러낸다.
3. **정리를 소비자가 한다.** `ToolStepRunner.stripLlmArtifacts()`가 *다음* step 입력 시점에
   코드펜스와 `[레이블]`을 벗긴다. 생산자의 포맷이 바뀌면 소비자가 깨진다.
   `sample-mcp-filesystem-list`의 list→read-notes 연결도 두 정규식이 우연히 맞아서 동작한 것이다.
4. **같은 필드가 타입마다 반대로 동작한다.** `structuredOutput`은 AGENT에서는 "형식을 어기면 실패",
   TOOL에서는 "형식을 어겨도 텍스트로 대체"다. TOOL의 `structuredOutput: false`는 "Tool 응답 대신
   원본 입력을 넘긴다"는 전혀 다른 의미까지 겸한다.

부수적인 문제도 있다.

- SUPERVISOR/APPROVAL은 결과를 텍스트 뒤에 `[검토 결과] 실패: ...`, `[승인] ...`처럼 **덧붙인다.**
  그래서 testApp Agent 3개의 prompt에 "대괄호 표시는 무시하라"는 방어 문구가 들어가 있다.
- `YamlDefinitionLoader`가 파일 하나를 읽다 실패해도 `printStackTrace()`만 하고 넘어간다.
  잘못된 YAML이 기동 시점에 잡히지 않는다.

### 1.3 근본 원인

각 step이 **무엇을 받고 무엇을 내놓는지가 step 정의(YAML)에 선언되어 있지 않고, Runner 구현
(Java) 안에 숨어 있다.** YAML만으로는 통제가 안 된다고 느껴지는 이유가 이것이다.

## 2. 합의한 방향: 공유 컨텍스트 + step별 input/output 선언

### 2.1 계층별 I/O 한눈에 보기

```
사용자 → Workflow   : {message, variables}  →  {status, message(=output), ...}
Workflow → Step     : 템플릿으로 렌더링한 input  →  StepOutcome{text, data, route | error}
Step(AGENT류) → Agent: system = agent.yml prompt, user = 렌더링된 input(문자열), 응답 스키마 = output.schema
Agent → LLM → Tool  : Spring AI tool-calling (@Tool의 JSON 스키마). 엔진이 규정하지 않는 구간
Step(TOOL) → Tool   : 렌더링된 input(맵 → JSON 인자)  →  output.parse로 data 정규화
```

엔진이 규약을 가져야 하는 곳은 **Workflow ↔ Step 경계 하나**다. `Agent → LLM → Tool` 구간은
LLM이 판단하는 영역이므로 지금처럼 Spring AI에 맡긴다.

### 2.2 실행 컨텍스트: 하나의 트리

Workflow 실행 중의 모든 상태는 아래 트리 하나에 담긴다(DB의 JSON 컬럼에 그대로 저장된다).

```yaml
input:                     # 사용자가 넘긴 값 (Workflow를 시작할 때 한 번 채워지고 바뀌지 않음)
  message: "사용자 메시지"
  sqlList: [...]           # 요청의 variables가 여기에 펼쳐짐
steps:                     # step이 실행될 때마다 자기 id 아래에 결과를 남김
  analyze:
    input: "렌더링된 입력"   # 이 step이 실제로 받은 입력 (루프/디버깅용)
    text:  "결과 텍스트"
    data:  { tables: [...] }
    error: null            # 실패했을 때의 사유
  validate-each:
    text: "..."            # forEach면 각 반복의 text를 줄바꿈으로 이은 값
    items:                 # forEach 반복별 결과 (input/text/data/error)
      - { input: {...}, text: "...", data: {...}, error: null }
previous:                  # 방금 실행된 step의 결과 (steps.<그 step>과 같은 모양)
  text: "..."              # 첫 step에서는 input.message
approvals:                 # APPROVAL 결정 수신함 (엔진 내부용, 템플릿에서는 참조하지 않음)
  design-review: { approved: true, approver: "...", comment: "..." }
```

- `previous`는 "목록상 바로 앞 step"이 아니라 **"실제로 바로 직전에 실행된 step"**이다. 루프에서
  되돌아왔을 때도 올바르게 동작한다.
- 같은 step이 루프로 다시 실행되면 `steps.<id>`는 최신 결과로 덮어쓴다.

### 2.3 템플릿 문법: `{{ ... }}`

| 문법 | 의미 |
|---|---|
| `{{input.message}}` | 사용자 입력 참조 |
| `{{steps.analyze.data.tables}}` | 특정 step 결과 참조 (`input`/`text`/`data`/`error`/`items`) |
| `{{previous.text}}` | 직전에 실행된 step의 결과 |
| `{{item}}` (또는 `itemVariable`로 정한 이름) | forEach 반복 중 현재 항목 |
| `{{a.b.0.c}}` | 리스트는 숫자 인덱스로 접근 |
| `{{previous.data.sql ?? input.message}}` | 왼쪽 값이 없으면 오른쪽 값 사용 (여러 번 이어 쓸 수 있음) |

- **`${...}`와 겹치지 않게 `{{ }}`를 쓴다.** `${APP_HOME}`은 지금처럼 *기동 시* YAML 로더가
  환경값으로 바꾸고, `{{ }}`는 *실행 중* 데이터를 참조한다. 역할이 다르니 모양도 다르게 둔다.
- 엔진(`WorkFlowExecutor`)이 Runner를 호출하기 **전에** 렌더링한다. 모든 step 타입이 같은 규칙을 쓴다.
- 참조한 값이 없으면(아직 실행 안 된 step, 없는 키) **step을 실패로 처리한다**(fail-closed).
  빈 문자열로 조용히 넘어가지 않는다. 대체값이 필요하면 `??`를 쓴다.
- 렌더링 규칙
  - 문자열 안에 섞여 있으면 → 값을 문자열로 바꿔 끼운다(Map/List는 JSON 문자열로).
  - 값 전체가 `{{...}}` 하나뿐이면(TOOL 인자 맵의 값 등) → **원래 타입을 그대로 유지**한다
    (리스트는 리스트로, 숫자는 숫자로).

### 2.4 Step 정의: `input` / `output`

```yaml
- id: analyze
  type: AGENT
  ref: sql-analyzer
  input: |                       # AGENT/SUPERVISOR/ROUTER: 문자열 = LLM에게 보낼 user 메시지
    다음 Oracle SQL을 분석해줘:
    {{input.message}}
  output:
    schema:                      # 선언하면 LLM 응답을 이 모양의 JSON으로 강제 → data에 담김
      tables: list<string>
      oracleFeatures:
        type: list<string>
        description: 사용된 Oracle 전용 문법

- id: validate
  type: TOOL
  ref: validateSqlSyntax
  input:                         # TOOL: 맵 = Tool 인자 (엔진이 JSON으로 직렬화)
    sql: "{{steps.convert.data.sql}}"
  onFailure: convert

- id: list
  type: TOOL
  ref: list_directory
  input:
    path: "{{input.message}}"
  output:
    parse: lines                 # TOOL 응답을 data로 정규화: text | json | lines
    pattern: '^\[FILE\] (.+)$'   # lines 전용: 맞는 줄만 남기고, 그룹 1을 값으로 씀
```

**`input` 생략 시 기본값**

| step 타입 | `input`을 생략하면 |
|---|---|
| AGENT / SUPERVISOR / ROUTER | `{{previous.text}}` (지금과 같은 동작이라 단순한 YAML은 그대로 짧게 유지된다) |
| TOOL | 빈 인자 `{}` |
| APPROVAL | 입력을 쓰지 않음 |

**`output` 규칙: 생산자가 자기 출력을 책임진다**

| step 타입 | `output` | `text` | `data` |
|---|---|---|---|
| AGENT (schema 없음) | – | LLM 응답 원문 | `{}` |
| AGENT (schema 있음) | `schema` | data를 JSON 문자열로 | 스키마대로 파싱·검증한 값. 형식을 어기면 **실패** |
| TOOL | `parse: text`(기본) | Tool 응답 원문 | `{}` |
| TOOL | `parse: json` | Tool 응답 원문 | 응답 JSON 객체(배열이면 `{items: [...]}`). JSON이 아니면 **실패** |
| TOOL | `parse: lines` (+`pattern`) | Tool 응답 원문 | `{lines: [...]}` |
| SUPERVISOR | (고정) | 받은 input 그대로 통과 | `{pass, reason}` |
| ROUTER | (고정) | 받은 input 그대로 통과 | `{route, reason}` |
| APPROVAL | (고정) | 받은 input 그대로 통과 | `{approved, approver, comment}` |

- 실패하면 `error`에 사유가 남는다. **사유를 텍스트 뒤에 덧붙이지 않는다**
  (`[검토 결과]`, `[승인]`, `[검증 결과]` 모두 없앰). 필요한 step이 `{{steps.judge.error}}`처럼
  명시적으로 참조한다.
- TOOL의 성공/실패 판정은 지금과 같다(`ToolOutcome.success` 또는 `"실패"` 접두사).
- 스키마 표기: `필드: 타입` 축약형과 `필드: {type, description}` 확장형 둘 다 허용한다.
  타입은 `string`/`number`/`integer`/`boolean`/`list<T>`/`object`(중첩 맵)이고, 모든 필드는 필수다.

### 2.5 Workflow 정의: `inputs` / `output`

```yaml
workflow:
  id: oracle-to-pg
  inputs:                            # 입력 계약: 실행 요청 시점에 검사 (없으면 검사 안 함)
    message: string
    targetVersion: { type: string, description: PostgreSQL 버전 }
  output: "{{steps.convert.data.sql}}" # 최종 결과. 생략하면 마지막 step의 text
  steps: [...]
```

- `message`는 예약된 입력 이름이다. API의 `WorkFlowRequest.message`가 `input.message`로 들어간다.
  **API 요청/응답 모양은 바뀌지 않는다**(dstone-boot 화면 수정 불필요).
- `inputs`에 선언한 필드가 요청에 없으면 실행 전에 400으로 거절한다.

### 2.6 Agent system prompt와의 역할 분리

| | 무엇을 담나 | 무엇을 참조할 수 있나 |
|---|---|---|
| `agents/*.yml`의 `prompt` (system) | **역할**: 이 Agent는 어떤 일을 하는 자인가 | `{role}`처럼 `input.*`의 값만 (ST4 `{}` 문법, 지금과 같음) |
| step의 `input` (user) | **이번에 할 일과 데이터** | 컨텍스트 전체 (`{{ }}` 문법) |

system prompt에는 `input` 맵(평평한 키)만 넘기므로 `.`이 섞인 키가 들어갈 일이 없다.
`promptSafeVariables()`는 필요 없어진다.

### 2.7 기동 시점 검증 (YAML만으로 운영하기 위한 필수 조건)

`WorkFlowRegistry`가 기동할 때 모든 `{{ }}` 참조를 검사한다. 하나라도 틀리면 **기동 자체를 실패**시킨다.

- 루트가 `input`/`steps`/`previous`/(forEach step 안에서) `item`(또는 `itemVariable`) 중 하나인가
- `steps.X`의 X가 이 Workflow에 있는 step id인가
- `steps.X.<필드>`가 `input`/`text`/`data`/`error`/`items` 중 하나인가
- `steps.X.data.K`에서 X가 `output.schema`를 선언한 AGENT면 K가 그 스키마에 있는가,
  X가 `parse: lines`인 TOOL이면 K가 `lines`인가
- `input.K`에서 Workflow가 `inputs`를 선언했다면 K가 그 안에 있는가
- 모양 검사: AGENT류의 `input`은 문자열, TOOL의 `input`은 맵, `output.schema`는 AGENT에만,
  `output.parse`는 TOOL에만
- 알 수 없는 YAML 키(예: 예전의 `inputTemplate`)는 로딩 실패로 처리한다(Jackson 기본 동작 유지).
  `YamlDefinitionLoader`는 예외를 삼키지 않고 그대로 던진다.

`previous.data.*`나 `json`으로 파싱한 data의 키처럼 정적으로 알 수 없는 것은 실행 시점에
fail-closed로 처리한다(§2.3).

## 3. 없애는 것 / 바뀌는 것 한눈에 보기

> 구현 원칙 1: 불필요해진 항목은 **모두 제거**한다. 호환용 별칭이나 옛 이름을 남기지 않는다.
> 구현 원칙 2: 주석은 쉽고 자세하게 쓰되 **최종 기능 설명만** 남긴다. "예전에는 ~였다",
> "~때문에 바꿨다" 같은 수정 이력/사유 주석은 이번에 손대는 파일에서 모두 걷어낸다.

### 3.1 YAML 필드

| 이전 | 이후 |
|---|---|
| `step.inputTemplate` (TOOL 전용 JSON 문자열) | `step.input` (모든 타입. AGENT류는 문자열, TOOL은 맵) |
| `step.structuredOutput` (AGENT/TOOL 의미가 반대) | **제거.** AGENT는 `output.schema`, TOOL은 `output.parse` |
| `step.forEachVariable` (최상위 변수 이름) | `step.forEach` (참조 경로. 예: `input.sqlList`, `steps.list.data.lines`) |
| `step.itemVariable` | 유지 |
| 토큰 `{previous}` / `{변수명}` / `{stepId.키}` | `{{previous.text}}` / `{{input.변수명}}` / `{{steps.stepId.data.키}}` |
| (없음) | `workflow.inputs`, `workflow.output` |

### 3.2 Java 코드

| 대상 | 처리 |
|---|---|
| `runtime.tool.ToolPayload` | **삭제.** 구조화 값을 내고 싶은 Tool은 JSON 객체를 반환하고 YAML에서 `parse: json` |
| `ToolStepRunner.stripLlmArtifacts()` + 정규식 2개 | **삭제.** 생산자 책임으로 전환 |
| `ToolStepRunner.renderToolInput()` / `jsonEscape()` | **삭제.** 템플릿 렌더러 + Jackson 직렬화로 대체 |
| `ToolStepRunner.runStructuredOutput()` / `tryParsePayload()` | **삭제.** `output.parse` 처리로 대체 |
| `AgentExecutor.promptSafeVariables()` | **삭제** (§2.6) |
| `Constants.WorkFlow.PREVIOUS_TEXT_VARIABLE_KEY` (`__previous`) | **삭제.** 컨텍스트의 `previous` 루트로 대체 |
| `WorkFlowExecutor.namespaced()` / `mergeVariables()` / `previousText()` | **삭제.** 컨텍스트 기록 메서드로 대체 |
| forEach의 `{stepId.i.키}` / `{stepId.results}` 키 | **삭제.** `steps.<id>.items[i]`로 대체 |
| `StepOutcome.Success`를 LLM 응답 스키마로 쓰던 방식 | **삭제.** `output.schema`로 만든 동적 스키마 사용 |
| SUPERVISOR/APPROVAL/TOOL의 `[검토 결과]`/`[승인]`/`[반려]`/`[검증 결과]` 텍스트 덧붙이기 | **삭제.** `data`/`error`로 구조화 |
| testApp Agent 3개 prompt의 "대괄호 표시는 무시하라" 문단 | **삭제** |
| `StepOutcome.primaryText` | `text`로 이름 변경 (컨텍스트의 `steps.X.text`와 같은 이름) |
| 주석의 옛 이름 언급(`StepPayload` 등) | 제거 |

## 4. 구현 계획

각 Phase가 끝날 때마다 `mvn -pl dstone-ai-engine -am compile`과 샘플 Workflow 라이브 실행으로 확인한다.
Phase 1~3은 기존 YAML 형식을 깨뜨리므로 **한 번에 이어서 진행**하고, 샘플 YAML 이관(Phase 5)까지
끝나야 기동이 된다. Phase 단위로 커밋은 나누되, 중간 커밋이 기동되지 않는 것은 감수한다.

### Phase 1. 정의(Definition)와 템플릿 부품

| 파일 | 작업 |
|---|---|
| `common/definition/StepDefinition.java` | `inputTemplate`/`structuredOutput`/`forEachVariable` 제거. `Object input`, `StepOutputDefinition output`, `String forEach` 추가. 클래스 주석 전면 재작성 |
| `common/definition/StepOutputDefinition.java` (신규) | `Map<String, FieldDefinition> schema`, `ToolParse parse`, `String pattern` |
| `common/definition/FieldDefinition.java` (신규) | `type`, `description`. `"string"` 같은 축약 문자열도 받도록 `@JsonCreator` |
| `common/definition/WorkFlowDefinition.java` | `Map<String, FieldDefinition> inputs`, `String output` 추가 |
| `common/consts/ToolParse.java` (신규) | `TEXT`/`JSON`/`LINES` |
| `common/consts/Constants.java` | `PREVIOUS_TEXT_VARIABLE_KEY` 제거. 컨텍스트 루트 이름(`input`/`steps`/`previous`/`approvals`)과 step 필드 이름(`input`/`text`/`data`/`error`/`items`), 예약 입력 `message` 추가 |
| `common/template/Template.java` (신규) | `{{ }}` 파싱(경로 + `??`), 참조 목록 추출(검증용), 렌더링(문자열 끼우기 / 단독 표현식은 타입 유지 / 맵·리스트 재귀). 순수 Java, Spring 의존 없음 |
| `common/template/TemplateException.java` (신규) | 참조를 못 찾았을 때. 어떤 경로가 왜 없는지 메시지에 담음 |

### Phase 2. 실행 컨텍스트와 Executor

| 파일 | 작업 |
|---|---|
| `runtime/workflow/execution/WorkFlowContext.java` (신규) | 컨텍스트 트리(Map)를 감싸는 도우미. `newContext(message, variables)`, `recordStep(id, record)`, `recordForEach(id, items)`, `setPrevious(...)`, `workflowInput()`. 저장은 지금처럼 Map 그대로 JSON 직렬화 |
| `runtime/step/StepInput.java` | `StepInput(String text, Map<String,Object> arguments, Map<String,Object> workflowInput)`. AGENT류는 `text`, TOOL은 `arguments`, system prompt 변수는 `workflowInput` |
| `runtime/step/StepOutcome.java` | `primaryText` → `text`. `Failure(text, failureReason)` 유지. 주석 재작성 |
| `runtime/workflow/WorkFlowExecutor.java` | `runOne`: step `input`(또는 기본값)을 렌더링 → `StepInput` → 결과를 `steps.<id>`와 `previous`에 기록. 렌더링 실패는 step 실패로 처리하고 `onFailure`를 따른다. `runForEach`: `forEach` 경로를 리스트로 해석하고, 반복마다 `item` 변수를 추가한 컨텍스트로 렌더링한 뒤 `items`로 모은다. Done 시 `workflow.output` 렌더링(없으면 마지막 text). `namespaced`/`mergeVariables`/`previousText` 삭제. 클래스 주석의 데이터 흐름 설명 재작성 |
| `api/service/WorkFlowExecutionService.java` | `newExecution`: `WorkFlowContext.newContext(message, variables)`로 초기화. `inputs` 계약 검사(누락 시 `IllegalArgumentException` → 400). approvals 기록 위치는 그대로 |
| `runtime/workflow/execution/WorkFlowExecution.java` 등 | `variables` 필드의 의미가 "컨텍스트 트리 전체"로 바뀜 → **§5 결정 D1**에 따라 이름 처리 |

### Phase 3. Runner별 I/O

| 파일 | 작업 |
|---|---|
| `runtime/step/AgentStepRunner.java` | AGENT: `output.schema`가 있으면 스키마 호출 → `success(json(data), data)`, 없으면 `success(answer)`. SUPERVISOR: `success(input, {pass, reason})` / `failure(input, reason)`. ROUTER: `routed(input, {route, reason}, route)`. 텍스트 덧붙이기 제거 |
| `runtime/agent/SchemaOutputConverter.java` (신규) | `StructuredOutputConverter<Map<String,Object>>` 구현. `FieldDefinition` 맵 → JSON Schema 문자열 → format 지시문. 파싱은 코드펜스를 허용한 뒤 필수 필드와 타입 검사 |
| `runtime/agent/AgentExecutor.java` | `callForSchema(agent, ..., Map<String,FieldDefinition>)` 추가. system prompt 렌더링에 `workflowInput`을 사용하고 `promptSafeVariables` 삭제. `StepPayload` 등 옛 이름 언급 정리 |
| `runtime/step/ToolStepRunner.java` | `arguments` → Jackson JSON → `ToolExecutor.call`. 성공/실패 판정은 유지. 성공 시 `output.parse`로 data 생성. 삭제 목록은 §3.2 참고. 클래스 주석 재작성 |
| `runtime/step/ApprovalStepRunner.java` | `success(input, {approved, approver, comment})` / `failure(input, comment)`. 텍스트 덧붙이기 제거 |
| `runtime/tool/ToolPayload.java` | **삭제** |
| `runtime/tool/ToolOutcome.java` | 주석에서 "다음 step으로 이어붙는다" 등 바뀐 동작 설명 수정 |

### Phase 4. 기동 시점 검증

| 파일 | 작업 |
|---|---|
| `common/registry/WorkFlowRegistry.java` | §2.7 검사 추가(`validateReferences`, `validateShapes`). 기존 APPROVAL/ROUTER 검사 유지(`forEachVariable` → `forEach` 반영) |
| `common/loader/YamlDefinitionLoader.java` | 예외를 삼키는 `try/catch + printStackTrace` 제거 → 파일명을 담아 그대로 던짐 |

### Phase 5. YAML / 문서 이관

**Workflow 이관표**

| 파일 | 바뀌는 내용 |
|---|---|
| `sample-agent-basic-echo` / `-model-override` / `-rag-augmented` / `-tool-calling` / `sample-approval-pause-resume` | 변경 없음 (`input` 생략 = `{{previous.text}}`) |
| `sample-foreach-parallel` | `forEach: input.sqlList`, `input: {sql: "{{item}}"}` |
| `sample-loop-retry-until-valid` | `validate.input.sql: "{{previous.data.sql ?? input.message}}"`. `fix`는 `input`에 `{{steps.validate.input.sql}}` + `{{steps.validate.error}}`, `output.schema: {sql: string}` |
| `sample-mcp-filesystem-list` | `list`: `parse: lines` + `pattern`. `read-notes`: `forEach: steps.list.data.lines`, `path: ".../{{item}}"` → **파일이 여러 개여도 동작**(이전 문서 §7의 "예시 B"가 YAML만으로 해결됨) |
| `sample-router-multiway` | 변경 없음 (ROUTER가 input을 그대로 통과시킴) |
| `sample-structured-output-chain` | `extract.output.schema: {sql: string}`, `validate.input.sql: "{{steps.extract.data.sql}}"` |
| `sample-supervisor-verdict-gate` | 변경 없음 |
| `sample-tool-chain-basic` | `validate.input.sql: "{{input.message}}"`, `now`는 input 생략 |
| `sample-tool-gated-external` | `inputTemplate` JSON 문자열 → `input` 맵 |
| `sample-tool-rag-search` | `input.query: "{{input.message}}"` |
| `testApp-sdlc` | `write-spec.input: "{{steps.analyze.text}}"` + 승인 코멘트 `{{steps.design-review.data.comment}}` 명시. `generate-code`도 같은 방식. `trigger-jenkins`는 `input: {jobName: testApp}` |

APPROVAL은 텍스트를 그대로 통과시키므로, 명시하지 않아도 `previous.text`는 이전 AGENT 결과가 된다.
그래도 testApp처럼 여러 단계를 거치는 흐름은 **명시적 참조를 권장 예시로 보여주기 위해** 적어 둔다.

**Agent 이관**

| 파일 | 바뀌는 내용 |
|---|---|
| `sample-structured-extract-agent` | prompt에서 "data 맵의 sql 키 / primaryText" 설명 제거. 스키마는 step의 `output.schema`가 강제 |
| `sample-fix-agent` | 입력이 "SQL + 오류 사유"로 명확해진 것에 맞춰 prompt 수정 |
| `testApp-*-agent` (3개) | "대괄호 표시는 무시하라" 문단 삭제 |

**YAML 파일 머리 주석**: 옛 필드명이나 수정 경위를 설명하는 부분을 걷어내고, 새 필드 기준의 기능 설명만 남긴다.

**문서**

| 파일 | 작업 |
|---|---|
| `docs/09.dstone-ai-engine.md` | §5(실행 모델), §6(Step IN/OUT) 전면 재작성: 컨텍스트 트리, 템플릿 문법, 타입별 input/output 표. §10(YAML 작성법) 필드 레퍼런스 교체. §14(검증 상태)에 라이브 검증 기록 추가. §15(달라진 점)에 2026-09-24 항목 추가 |
| `CLAUDE.md` | dstone-ai-engine 섹션의 `StepOutcome`/`runtime.step` 설명을 새 규약에 맞게 한두 줄 갱신 |
| `docs/temp/dstone-ai-engine-refactory-20260923-1.md` | §8 머리에 "→ 20260924-1 문서에서 결론" 한 줄 링크 |
| `docs/temp/workflow-executor-run-process.md` 등 옛 temp 문서 | 이력 문서이므로 수정하지 않음 |

### Phase 6. 검증

1. `mvn -pl dstone-ai-engine -am clean compile`
2. 엔진 기동 → 등록 로그에 Workflow 15개가 모두 뜨는지 확인
3. **음성 테스트**: 임시 YAML로 ①없는 step 참조 ②스키마에 없는 키 ③옛 필드 `inputTemplate` ④TOOL `input`을 문자열로 작성 → 각각 기동이 실패하고 메시지가 원인을 가리키는지 확인한 뒤 임시 파일 삭제
4. 샘플 Workflow 전체를 `/execute`로 라이브 실행하고, 실행 상세 API로 `steps.*` 트리가 기대대로 쌓이는지 확인
   - 핵심 확인: `sample-structured-output-chain`, `sample-loop-retry-until-valid`(루프 2회 이상 유도),
     `sample-mcp-filesystem-list`(파일 2개 이상), `sample-foreach-parallel`, `testApp-sdlc`(승인 재개 포함)
5. dstone-boot의 "Workflow 테스트" 화면과 관리자 실행 상세 화면에서 정상 동작 확인

## 5. 구현 전에 확인받을 결정 사항 (2026-09-24 전부 권장안으로 결정)

| # | 결정 | 권장 | 이유 |
|---|---|---|---|
| D1 | `WorkFlowExecution.variables`(DB `VARIABLES_JSON`, API `variables`)의 이름 | **`context`로 변경** (Java 필드, DB 컬럼 `CONTEXT_JSON`, API 필드, dstone-boot 관리자 VO/JSP) | 이제 사용자 변수는 `input` 아래에 있고, 이 맵은 "실행 상태 전체"다. `variables`라는 이름을 두면 헷갈린다(원칙 1). 대신 DB `ALTER TABLE ... RENAME COLUMN`과 dstone-boot 수정(`WorkFlowExecutionDetailResult`, 관리자 `workflow.jsp`)이 따라온다. 변경하지 않으면 엔진 안에서만 끝난다 |
| D2 | 템플릿 문법 모양 | **`{{ }}`** | `${}`(기동 시 환경값)와 구분된다. Mustache, GitHub Actions와 비슷해서 익숙하다 |
| D3 | 스키마 표기법 | **축약형(`sql: string`) + 확장형(`{type, description}`) 둘 다 허용** | 대부분은 한 줄로 끝나고, LLM에게 필드 설명을 줘야 할 때만 확장형을 쓴다. 정식 JSON Schema는 YAML 작성자에게 너무 장황하다 |
| D4 | 표현식 범위 | **참조 + `??`만** | YAML이 프로그래밍 언어가 되는 것을 막는다(§0). 필터·조건식은 필요해지면 그때 다시 논의 |
| D5 | Structured Output 방식 | **1차는 format 지시문 + 파싱/검증**(Spring AI의 converter 방식, provider 무관) | provider 네이티브 structured output 옵션은 provider별 지원 차이가 있으므로 후속 과제로 둔다 |

## 6. 이번 범위에서 제외 (후속 과제)

- `type: WORKFLOW` step(Workflow를 부품처럼 재사용). `inputs`/`output` 계약이 생기면 자연스럽게 가능해진다
- 서로 다른 step을 동시에 실행하는 정적 병렬 그룹
- 실행 이력(`StepHistoryEntry`)에 렌더링된 input 저장. 지금은 컨텍스트의 `steps.<id>.input`으로 볼 수 있다
- MCP `structuredContent` 활용(Spring AI 버전 제약, 이전 문서 §0 참고)

## 7. 구현 결과 (2026-09-24)

§3~§4의 계획대로 구현했고, `docs/09.dstone-ai-engine.md` §6(전면 재작성)·§10·§14·§15와 `CLAUDE.md`에
반영했다. 라이브 검증 기록은 `docs/09.dstone-ai-engine.md` §14의 2026-09-24 항목에 있다.

계획과 달라지거나 계획에 없던 부분:

1. **MCP 호출 서버별 직렬화 추가** - `sample-mcp-filesystem-list`를 `forEach`로 바꾸자, 같은 MCP 서버의
   `read_text_file`을 동시에 두 번 부르는 순간 STDIO 클라이언트가 `Failed to enqueue message`로 한쪽을
   실패시켰다. `ConfigMcp`가 서버마다 공용 잠금을 가진 `SerializedToolCallback`으로 Tool을 감싸서 해결했다.
2. **forEach 반복의 시스템 예외 처리 통일** - 기존 forEach는 반복 하나가 예외를 던지면 "반복 실패"로
   삼켜서 onFailure로 흘려보냈다. `docs/09` §2의 "ERROR와 FAILURE 분리" 원칙(러너 예외는 onFailure를
   거치지 않고 즉시 FAILED)에 맞춰, 단일 step과 똑같이 이력을 남긴 뒤 실행 전체를 FAILED로 끝내게 했다.
   반면 템플릿 참조를 못 찾은 경우는 계획대로 step 실패(onFailure를 따름)다.
3. **`sample-mcp-filesystem-list`의 호출 방법 변경** - `message`에 `.../mcp/server-filesystem/sample`
   (파일이 있는 디렉토리)을 넣고, 읽을 경로도 `{{input.message}}/{{fileName}}`으로 같은 값에서 만든다.
   여러 파일을 확인하려고 `mcp/server-filesystem/sample/todo.txt`를 추가했다.
4. **YAML/주석 정리 범위** - 원칙 2에 따라, 손댄 Java 파일과 YAML 파일(testApp 3개 Agent·Workflow 포함)의
   수정 이력/경위 주석을 기능 설명으로 바꿨다. `WorkflowTransition`의 "예전에는 ~" 주석도 함께 정리했다.
5. **로컬 DB 이관** - `ALTER TABLE AI_WORKFLOW_EXECUTION RENAME COLUMN VARIABLES_JSON TO CONTEXT_JSON`을
   로컬 PostgreSQL에 실행했다(스키마 파일 `02-create-table-postgresql-dstone-ai.sql`도 `CONTEXT_JSON`으로 수정).
   다른 환경(k8s 등)의 DB도 같은 ALTER가 필요하다.

아직 확인하지 못한 것: TOOL `output.parse: json`의 라이브 동작(구조화 JSON을 반환하는 샘플 Tool이 없음),
`testApp-sdlc`의 Jenkins 기동 구간, dstone-boot 관리자 화면의 실제 동작(컴파일만 확인).

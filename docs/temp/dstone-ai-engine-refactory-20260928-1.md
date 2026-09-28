# dstone-ai-engine: 입출력 계약 모델 재설계 (2026-09-28)

> `dstone-ai-engine-refactory-20260924-1.md`(step 간 I/O 규약)의 후속이다.
> 2026-09-28 논의에서 방향과 세부 결정을 모두 확정했고, 같은 날 구현·기동 검증·라이브 검증까지 마쳤다(§6).

## 0. 무엇이 문제였나

09-24 재설계로 step 사이의 데이터는 실행 컨텍스트 하나로 모였지만, 두 가지가 남아 있었다.

1. **계약이 호출하는 쪽(step)에 있었다.** AGENT step의 `output: {sql: string}`이 "Agent가 무엇을 돌려주는가"를 정했다.
   그런데 프롬프트와 출력 모양은 한 몸이다. 같은 Agent를 부르는 step마다 `output`을 다시 적어야 했고,
   "응답 전체가 곧 설계서"라는 프롬프트에 step이 `output: object`를 강제하는 식의 불일치를 막을 방법이 없었다.
2. **엔진이 몰래 채우는 이름이 있었다.** `steps.<id>.text`(결과 글자), `previous`(직전 step 결과), `items`(forEach 반복별 결과),
   `inputs.message`(요청 message 자동 주입), AGENT류 `input`을 비우면 `{{previous.text}}`를 쓰는 기본값,
   Workflow `output`을 비우면 "마지막 step의 text"를 쓰는 기본값. YAML에 적지 않은 것이 결과를 좌우했다.

## 1. 원칙

1. **계약은 부르는 대상 한 곳에만 둔다.** Agent는 `input`/`output`, Tool은 인자 스키마/응답, SUPERVISOR/ROUTER/APPROVAL은 엔진이 정한 모양.
   step은 "무엇을 넣을지(`input`)"와 "다음에 어디로 갈지"만 적는다.
2. **숨은 이름을 두지 않는다.** 컨텍스트는 `input` / `steps.<id>.{input, output, error}` / `approvals`(내부용)뿐이다.
3. **모양은 JSON Schema로 적는다.** YAML에 표준 JSON Schema를 그대로 쓰고(`schema:` 아래), 타입만 필요하면 축약형을 허용한다.
4. **`{{ }}`는 유지한다.** 글자와 참조가 섞이는 자리에서 참조의 시작과 끝을 가르는 유일한 표시다. 한 겹 `{ }`는 YAML 맵과 prompt 변수가,
   `${ }`는 기동 시 환경 값 치환이 이미 쓰고 있다. 대신 "값 전체가 `{{x}}` 하나면 원래 타입 유지" 규칙을 step input·Workflow output 모두에 적용한다.

## 2. 확정한 결정

| # | 주제 | 결정 |
|---|---|---|
| 1 | 계약 위치 | Agent 한 곳. step의 `output` 키 삭제(AGENT/TOOL 모두) |
| 2 | 참조 대상 | Agent id가 아니라 step id(`{{steps.analyze.output.name}}`). 같은 Agent를 두 step이 부르거나 루프로 다시 돌아도 구분된다. 스키마 `title`은 경로에 들어가지 않는다 |
| 3 | Workflow 결과 | `output: {value, schema?}`. `value`(어디서 가져올지) 필수, `schema` 선택 |
| 4 | 암묵 항목 | `text`, `previous`, `items`, `inputs.message`, AGENT류 input 기본값, Workflow output 기본값 모두 삭제. forEach step은 `output`/`input`이 반복별 리스트 |
| 5 | `steps.<id>.error` | 유지(데이터가 아니라 실패 정보. 재시도 루프에 필요) |
| 6 | 스키마 표기 | JSON Schema를 YAML로 바로(`schema:` 아래). 축약형은 최상위·`properties` 값·`items`에서만(`string`, `list<string>` 등) |
| 7 | 기본값 | Agent `input`/`output`, Workflow `input`을 비우면 `{type: string}`. "자유 텍스트"라는 별도 경로 없이 string 스키마로 통일. string output은 JSON으로 감싸지 않고 원문 그대로 |
| 8 | `inputs` → `input` | Workflow 입력도 `input`(YAML 키 = 컨텍스트 이름). 요청 body는 `{input, sessionId}` |
| 9 | Agent input이 object | step `input`을 맵으로 적는다. 채운 맵을 Agent input 스키마로 검사한 뒤 JSON 글자로 user 메시지에 보낸다 |
| 10 | TOOL 출력 | step에서 선언하지 않는다. 응답이 JSON이면 그 값(객체/배열/숫자/boolean), 아니면 글자 |
| 11 | TOOL `lines`/`pattern` | 비정형 가공이라 **삭제**. 줄 단위로 답하던 MCP `list_directory` 샘플은 JSON으로 답하는 `directory_tree`로 교체 |
| 12 | TOOL 입력 계약 | Spring AI가 `@Tool` 파라미터/MCP `inputSchema`로 만든 인자 스키마와 step `input` 이름을 기동 시 대조. Tool을 못 찾으면(MCP 미기동 등) 경고만 |
| 13 | SUPERVISOR/ROUTER | 답의 모양은 엔진이 정한다(`{pass, reason}` / `{route, reason}`). 이 step이 부르는 Agent가 `output`을 선언하면 기동 실패. ROUTER는 route에 `enum: routes 키`를 걸어 없는 이름을 계약 위반으로 처리 |
| 14 | API | 한 번에 변경(호출처가 dstone-boot 하나뿐). 채팅 `{agent, input, variables, ...}` → `{output, ...}`, Workflow `{input, sessionId}` → `{status, output, ...}` |
| 15 | 검증 라이브러리 | networknt json-schema-validator 3.0.1(Spring AI 2.0.1이 이미 끌어옴, Jackson 3 기반). 문자열 API(`getSchema(String, InputFormat)` / `validate(String, InputFormat)`)만 써서 엔진 코드에 Jackson 3 타입이 새지 않게 함. 메타스키마(2020-12)도 jar에 들어 있어 오프라인 검사 가능 |

## 3. YAML 모양

```yaml
# agents/sample/sample-fix-agent.yml — input/output 모두 object
agent:
  id: sample-fix-agent
  prompt: |
    입력으로 sql(검증에 실패한 SQL)과 error(실패 사유) 두 필드를 가진 JSON을 받습니다. ...
  input:
    schema:
      type: object
      properties:
        sql: { type: string, description: 검증에 실패한 SQL }
        error: { type: string, description: 검증에 실패한 사유 }
      required: [sql, error]
  output:
    schema:
      type: object
      properties:
        sql: { type: string, description: 문법 오류를 고친 단일 SQL 문 }
      required: [sql]
```

```yaml
# workflows/sample/sample-loop-retry-until-valid.yml
workflow:
  id: sample-loop-retry-until-valid
  maxIterations: 6
  input: string
  output:
    value: "{{steps.validate.input.sql}}"
  steps:
    - id: validate
      type: TOOL
      ref: validateSqlSyntax
      input:
        sql: "{{steps.fix.output.sql ?? input}}"
      onSuccess: SUCCESS
      onFailure: fix
    - id: fix
      type: AGENT
      ref: sample-fix-agent
      input:
        sql: "{{steps.validate.input.sql}}"
        error: "{{steps.validate.error}}"
      onSuccess: validate
```

## 4. 코드 변경 요약

| 영역 | 변경 |
|---|---|
| `common.definition` | `SchemaDefinition` 추가(JSON Schema 맵, 축약형 펼침 creator). `AgentDefinition`에 `input`/`output` + `inputSchema()`/`outputSchema()`. `WorkFlowDefinition`의 `inputs`(필드 맵) → `input`(스키마), `output`(글자) → `WorkFlowOutputDefinition(value, schema)`. step record에서 `output`/`pattern` 삭제, AGENT류 `input`은 `Object`(글자 또는 맵). `FieldDefinition` 삭제 |
| `common.schema` | `FieldTypes` → `JsonSchemas`(normalize/checkSchema/validate/checkPath) + `StepOutputSchemas`(SUPERVISOR/ROUTER/APPROVAL) |
| `common.consts` | `ToolParse` 삭제. `Context`는 `INPUT`/`STEPS`/`APPROVALS` + `FIELD_INPUT`/`FIELD_OUTPUT`/`FIELD_ERROR`만 |
| `common.registry` | `AgentRegistry`: 스키마 메타 검사, `find()`. `WorkFlowRegistry`: 계약 대조(Agent 존재, input 모양/이름, SUPERVISOR/ROUTER output 금지, Tool 인자 이름), 스키마를 따라가는 참조 경로 검사, 없어진 이름 안내 |
| `common.loader` | 없어진 키(step `output`, TOOL `output`/`pattern`, `workflow.inputs`, `schema:` 누락) 안내 |
| `runtime.agent` | `AgentExecutor.call()`이 계약을 지킴(input 검사 → 메시지 변환, output string이면 원문/아니면 형식 지시 + 검사). `AgentContractException` 추가. `SchemaOutputConverter`는 JSON Schema 기반, 뒤에 글자가 붙은 JSON도 거부 |
| `runtime.step` | `StepOutcome` = `{success, pending, input, output, error, route, durationMs}`. TOOL은 JSON 응답을 값으로, APPROVAL은 input 없이 결정만 output으로 |
| `runtime.workflow` | `renderInput()`이 모든 step에 `Template.render()`(타입 유지). forEach는 input/output 리스트. SUCCESS 시 `output.value` 렌더 + `output.schema` 검사. `WorkFlowContext.create(input)`, `promptVariables()` |
| 저장 | `WorkFlowExecution.resultText`(String) → `output`(Object). `RESULT_TEXT` 컬럼에 JSON 글자로 저장, 읽을 때 복원(JSON이 아닌 예전 행은 글자 그대로). 스키마 변경 없음 |
| API | `ChatRequest.message` → `input`(Object), `ChatResponse.message` → `output`. `WorkFlowRequest(input, sessionId)`. 응답 `message`/`result`/`resultText` → `output`. 목록 API에 스키마 포함(`WorkFlowSummary.input`, `AgentSummary.input/output`) |
| dstone-boot | VO/서비스/컨트롤러를 `input`/`output`으로. Workflow 테스트 화면은 input 칸 하나 + 스키마 안내(string이면 글자, 아니면 JSON 파싱). 채팅 화면은 Agent input 스키마로 JSON 파싱 여부 결정, 스트리밍 불가 Agent 안내. 관리자 상세는 `output`을 JSON으로 펼쳐 표시 |

## 5. 동작이 달라진 점(호환성)

- 요청/응답 모양이 바뀌었다(§2-14). 예전 `message`/`variables` 요청은 400.
- 예전 YAML(`inputs:`, step `output:`, TOOL `pattern:`, `{{previous...}}`, `.text`, `.items`)은 기동 실패하고 메시지가 새 모양을 알려 준다.
- AGENT류 step의 `ref`는 이제 **기동 시** 존재를 검사한다(예전엔 실행 중 FAILED).
- SUPERVISOR/ROUTER에서 LLM 호출 자체가 예외면(API 오류 등) 예전엔 step 실패였지만, 이제 AGENT와 똑같이 시스템 오류로 FAILED다.
  계약 위반(`AgentContractException`)만 step 실패다.
- ROUTER가 routes에 없는 이름을 고르면 예전엔 FAILED였지만, 이제 계약 위반이라 step 실패 → `onFailure`다.
- SUPERVISOR 불통과 시 `output`이 비어 있던 것이 `{pass: false, reason}`으로 채워진다. APPROVAL 반려도 `output`에 결정이 남는다.

## 6. 검증

- **기동**: 13개 Agent + 15개 Workflow 샘플이 새 검증을 모두 통과하고 엔진이 기동(로컬 WSL, `-Dspring.profiles.active=wsl`).
- **라이브(실제 Anthropic 호출 포함)**: TOOL 연쇄(`output.value` 맵), forEach(`output` 리스트, DB 저장/복원), MCP `directory_tree`→forEach `read_text_file`,
  APPROVAL 대기→승인→DONE, structured-output-chain(object output Agent), 재시도 루프(object input/output `sample-fix-agent`로 `FORM` 오타 수정 후 통과),
  ROUTER(billing 분기), SUPERVISOR 불통과 FAIL, 채팅 API(string Agent, object output Agent, object input Agent, input 모양 틀림 400, object output Agent 스트리밍 400).
- **기동 실패 케이스 11종**(모두 의도한 한국어 메시지로 기동 실패): step `output` 키, 없는 output 필드, `previous`, SUPERVISOR가 output 있는 Agent 호출,
  object Agent에 글자 input, TOOL 인자 이름 오타, `inputs` 키, `.text`, Workflow input 스키마에 없는 필드, `output` 누락, Agent 스키마 오타/축약형 오타.
- 구현 중 발견: Agent prompt에 JSON 예시를 중괄호로 적으면 Spring AI PromptTemplate이 변수로 읽어 호출 때 실패한다(`The template string is not valid.`).
  샘플 prompt에서 중괄호를 빼고, 가이드에 주의사항으로 남겼다.

## 7. 남은 논의 거리

- 값 전체가 `"{{x}}"` 하나인 step input은 기동 시 모양을 알 수 없어 실행 중에 검사한다. 참조 경로의 스키마(예: object output)와 대상 Agent의 input 타입(string)을
  기동 시 비교하면 더 일찍 잡을 수 있다.
- TOOL output 모양은 Tool이 선언하지 않아 기동 시 경로 검사를 못 한다. `@Tool` 반환 타입이나 MCP `outputSchema`를 읽어 스키마로 쓰는 방법을 검토할 수 있다.
- `onSuccess`/`onFailure`/`routes`의 이동 대상 step id는 여전히 기동 시 검사하지 않는다.

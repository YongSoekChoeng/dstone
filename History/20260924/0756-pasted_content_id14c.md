# pasted_content_id14c

> 생성일시: 2026-09-24 07:56

## 질문

<pasted_content id="14c4">
궁금한게 설정파일(yml) 조정만으로 Workflow 통제가 가능한지 모르겠어.
dstone-ai-engine 전반을 통털어서 하고자 했던것은 java파일은 건드리지 않고 설정들만 수정/조립해서 모든 일을 처리하고자 했던 것이거든.
그게 내가 만들고자 하는 ai엔진 프레임워크의 골자가 되는 목표이자 개념이야.
그런데 먼저 각 계층간 주고 받는 I/O에 대해서 명확한 방법이 필요할 것 같아.
네 의견을 말해줘.

<호출구조>
사용자
    -> Workflow 
        -> Step(Agent)
            -> AgentExecutor
                -> LLM (내부적으로 Tool 호출)                
        -> Step(Tool)
        -> Step(Agent/...)
        ...    
</pasted_content id="14c4">

---

## 답변

짧게 답하면 **가능합니다.** 다만 조건이 하나 있습니다. "YAML은 조립, Java는 부품"이라는 경계를 먼저 인정해야 하고, 지금 코드에서 막히는 지점은 말씀하신 대로 계층 사이의 I/O 규약입니다. 코드를 확인해 보고 드리는 의견입니다.

## 1. "설정만으로"가 어디까지 되는가

| 영역 | YAML만으로 가능? | 비고 |
|---|---|---|
| Workflow 흐름 (순차/분기/루프/병렬/승인) | ✅ | 이미 `StepType` + `onSuccess/onFailure/routes/forEach`로 됩니다 |
| Agent 역할/프롬프트/모델/RAG | ✅ | `agents/*.yml` |
| 외부 Tool 연결 | ✅ | MCP(`mcp/*.yml`), 화이트리스트된 http/shell/python |
| **새로운 결정적 로직 (Tool 자체)** | ❌ | `@AiTool` Java 작성 또는 MCP 서버로 외부화 |
| **step 간 데이터 전달** | ⚠️ 부분적 | ← 지금 문제의 핵심 |

목표 문장을 "Java를 안 건드린다"에서 **"Workflow 조립과 데이터 흐름은 100% YAML, 새 능력(Tool)은 Java 또는 MCP 플러그인"**으로 다듬기를 권합니다. n8n, Airflow, GitHub Actions도 모두 이 경계를 둡니다.

그리고 경고할 점이 하나 있습니다. "전부 설정으로" 하겠다고 YAML에 조건식, 반복, 스크립트를 계속 넣다 보면 **YAML이 나쁜 프로그래밍 언어가 됩니다.** 표현식은 "값 참조" 정도로 최소화하고, 로직이 필요하면 Tool로 빼는 쪽이 오래 갑니다.

## 2. 지금 I/O의 실제 문제점 (코드 기준)

step 간 데이터 채널이 세 개인데, 규칙이 step 타입마다 제각각입니다.

1. **`{previous}`**: 직전 step 텍스트를 암묵적으로 넘기는 파이프 방식입니다.
2. **`variables`**: 사용자가 처음 넘긴 입력입니다.
3. **`data` → `{stepId.key}`**: 이름 붙은 명시적 핸드오프입니다.

비대칭이 세 군데 있습니다.

- **AGENT step은 `inputTemplate`을 아예 쓰지 않습니다.** `inputTemplate()`을 참조하는 곳이 `ToolStepRunner.java:66` 한 곳뿐이고, AGENT는 무조건 직전 텍스트가 user 메시지가 됩니다. 그래서 "analyze 결과의 tables만 골라서 convert Agent에 넘긴다" 같은 것을 YAML로 표현할 수 없습니다.
- **Agent 쪽에서는 `{stepId.key}`를 읽을 수 없습니다.** ST4 렌더링이 키에 들어간 `.`을 처리하지 못해서 `promptSafeVariables()`가 해당 키를 걸러냅니다.
- **정리를 소비자 쪽에서 합니다.** `stripLlmArtifacts()`가 TOOL step 입력 시점에 돌기 때문에, 생산자 포맷이 바뀌면 소비자가 깨집니다. list→read-notes가 우연히 동작한 것도 이 때문입니다.

즉, 각 step이 무엇을 받고 무엇을 내놓는지가 **step 정의에 선언되어 있지 않고 Runner 구현 안에 숨어 있습니다.** YAML만으로 통제가 안 된다고 느끼시는 근본 원인이 이것입니다.

## 3. 제안: 공유 컨텍스트 + step별 input/output 선언

모든 step이 **같은 봉투(envelope)**로 I/O하게 만드는 것입니다.

```yaml
id: oracle-to-pg
inputs:                       # ① Workflow의 입력 계약
  sql: { type: string, required: true }
output: ${steps.convert.data.sql}   # ② Workflow의 최종 결과가 무엇인지

steps:
  - id: analyze
    type: AGENT
    ref: sql-analyzer
    input: |                  # ③ 모든 타입에 공통인 입력 템플릿 (엔진이 렌더링)
      다음 Oracle SQL을 분석해줘:
      ${inputs.sql}
    output:                   # ④ 출력 스키마 선언 → Structured Output의 스키마로 사용
      schema:
        tables: list<string>
        oracleFeatures: list<string>

  - id: convert
    type: AGENT
    ref: sql-converter
    input: |
      원본: ${inputs.sql}
      사용된 Oracle 기능: ${steps.analyze.data.oracleFeatures}
    output:
      schema: { sql: string }

  - id: validate
    type: TOOL
    ref: sqlSyntaxCheck
    input: '{"sql": ${json(steps.convert.data.sql)}}'
    onFailure: convert
```

핵심 원칙은 다섯 가지입니다.

| 원칙 | 내용 |
|---|---|
| **단일 컨텍스트** | 실행 중 상태는 `inputs.*`, `steps.<id>.text`, `steps.<id>.data.*` 하나의 트리입니다 (LangGraph state, GitHub Actions `steps.<id>.outputs`와 같은 모델) |
| **단일 템플릿 문법, 엔진 측 렌더링** | `${...}`를 WorkFlowExecutor가 Runner 호출 **전에** 렌더링합니다. AGENT/TOOL/SUPERVISOR/ROUTER 모두 같은 규칙을 따르고, Agent의 system prompt(ST4 `{}`)와는 문법이 분리되므로 `.` 문제가 사라집니다 |
| **출력은 생산자가 책임** | AGENT는 `output.schema`를 Structured Output 스키마로 씁니다 (지금의 범용 `Success{primaryText, data: Map}` 대신). TOOL은 `output.parse: json | lines | text` 같은 소수의 파서로 생산 시점에 정규화합니다. `stripLlmArtifacts()` 같은 소비자 쪽 정리는 없앱니다 |
| **로드 시점 검증** | `WorkFlowRegistry`가 기동할 때 `${steps.X.data.Y}`에 대해 X가 앞선 step인지, Y가 X의 스키마에 있는지 검사합니다. **YAML만으로 운영하려면 이게 필수입니다.** 틀린 조립이 런타임이 아니라 기동 시점에 잡혀야 하니까요 |
| **`previous`는 편의 문법으로 격하** | `input`을 생략하면 `${steps.<직전>.text}`로 기본값을 줍니다. 기존 YAML과 호환되고, 진짜 데이터 전달은 항상 명시적 참조로 합니다 |

계층별 I/O를 정리하면 이렇습니다.

```
사용자 → Workflow   : {inputs}                       → {status, output, stepHistory}
Workflow → Step     : 렌더링된 input + 읽기 전용 컨텍스트 → StepResult{status, text, data(스키마 준수), route}
Step(AGENT) → Agent : system=agent.yml prompt, user=렌더링된 input, responseSchema=output.schema
Agent → LLM → Tool  : Spring AI tool-calling (@Tool JSON 스키마). 엔진이 규정하지 않아도 되는 구간
Step(TOOL) → Tool   : 렌더링된 input(JSON) → output.parse로 정규화
```

`Agent → LLM → Tool` 구간은 LLM이 알아서 판단하는 영역이라 엔진이 I/O를 강제할 필요가 없습니다. 엔진이 규약을 가져야 하는 곳은 **Workflow ↔ Step 경계 하나**입니다.

부수 효과도 있습니다. Workflow에 `inputs/output` 계약이 생기면 나중에 `type: WORKFLOW` step으로 **Workflow를 부품처럼 재사용하는 조립**도 YAML만으로 가능해집니다.

## 4. 진행 순서 제안

1. **엔진 측 공통 `input` 렌더링**: AGENT도 `input`을 쓰게 합니다. 비대칭을 없애는 효과가 가장 큽니다.
2. **`output.schema` / `output.parse`**: 생산자 쪽 정규화로 바꾸고 `stripLlmArtifacts` 의존을 제거합니다.
3. **로드 시점 참조 검증.**
4. **Workflow `inputs/output` 계약** (그 후 WORKFLOW step은 선택).

1~3이 끝나면 "Java 수정 없이 YAML로 Workflow를 조립한다"는 목표가 실질적으로 성립한다고 봅니다.

방향에 동의하시면 이 내용을 `docs/temp/dstone-ai-engine-refactory-20260923-1.md` §8의 결론으로 정리하고, 1단계부터 구현 계획을 잡아 보겠습니다. 문법(`${}` vs `{{}}`)이나 스키마 표기법처럼 세부적으로 다르게 가져가고 싶은 부분이 있으면 말씀해 주세요.

---

## 질문

<pasted_content id="14c4">
진행방향에 기본적으로 동의해.
이 내용을 /app/dstone/docs/temp/dstone-ai-engine-refactory-20260924-1.md 에 정리해줘.
그리고 구현 계획을 잡아주되 아래의 내용을 참고해줘.
<구현 참고>
1. 불필요해진 모든 항목들은 제거.(잔여가 남아있을 때 직관적인 이해도를 저하시킴)
2. 주석처리는 최대한 쉽고 자세하게 남기되 수정이력이나 수정사유관련된 주석은 제거. 최종기능 설명만 남김.
</pasted_content id="14c4">

---

## 답변

Plan 작성을 위해 핵심 파일들을 전부 읽어보겠습니다.

---

## 답변

코드 조사는 끝났습니다. 지금 문서를 작성하고 있습니다. 설계 논의 정리, 구체적인 문법, 파일별 변경/제거 목록, 단계별 계획을 담습니다.

---

## 답변

`docs/temp/dstone-ai-engine-refactory-20260924-1.md`에 논의 결론과 구현 계획을 정리했습니다. 코드는 아직 건드리지 않았고, 커밋도 하지 않았습니다.

**설계 핵심**
- **공유 컨텍스트 하나**: 실행 중 상태를 `input` / `steps.<id>.{input,text,data,error,items}` / `previous` 트리 하나에 담습니다.
- **템플릿 문법 `{{ }}`**: 기동 시 환경값인 `${APP_HOME}`과 구분됩니다. 표현식은 값 참조와 `??`(값이 없을 때 대체값) 두 가지만 허용해서, YAML이 프로그래밍 언어처럼 커지지 않게 합니다.
- **모든 step 타입 공통의 `input`**: AGENT 쪽은 문자열(LLM에 보낼 메시지), TOOL은 맵(Tool 인자)입니다.
- **`output`은 만드는 쪽이 정리**: AGENT는 `output.schema`, TOOL은 `output.parse`(`text`/`json`/`lines`)로 자기 결과를 정규화합니다.
- **기동 시점 참조 검증**: 잘못된 참조가 있으면 실행 중이 아니라 기동할 때 실패합니다.

**조사하면서 추가로 확인한 것**
- `YamlDefinitionLoader`는 YAML 파일을 읽다 실패해도 예외를 삼키고 넘어갑니다. 지금은 잘못된 YAML이 기동 시점에 잡히지 않아서, 이것도 계획에 넣었습니다.
- 어제 보류했던 "여러 파일 읽기"(`sample-mcp-filesystem-list`)는 `parse: lines`와 `forEach: steps.list.data.lines` 조합으로 YAML만으로 해결됩니다.

**구현 원칙 반영**
- §3에 없애는 항목을 모두 목록으로 정리했습니다. `inputTemplate`, `structuredOutput`, `ToolPayload`, `stripLlmArtifacts`, `promptSafeVariables`, `__previous`, 결과 텍스트 뒤에 붙이던 `[승인]`/`[검토 결과]`, testApp Agent의 방어 문구 등입니다.
- 이번에 손대는 파일은 모두 수정 이력 주석을 걷어내고 기능 설명만 남기도록 계획에 적었습니다.

**구현 전에 정해 주셔야 할 것 (§5)**
나머지 네 항목(D2~D5)은 권장안대로 진행해도 무리가 없습니다. 판단이 필요한 것은 **D1** 하나입니다.

- **D1**: 실행 상태를 담는 `variables`를 `context`로 이름을 바꿀지 정해야 합니다. 이제 이 맵은 사용자 변수가 아니라 실행 상태 전체라서 바꾸는 쪽을 권장합니다. 다만 바꾸면 DB 컬럼 이름 변경과 dstone-boot 관리자 화면(VO와 JSP) 수정까지 따라옵니다. 유지하면 엔진 안에서만 끝납니다.

D1을 정해 주시거나 문서 내용에 수정할 점을 말씀해 주시면, Phase 1부터 구현하겠습니다.

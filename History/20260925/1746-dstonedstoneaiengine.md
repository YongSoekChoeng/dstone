# dstonedstoneaiengine

> 생성일시: 2026-09-25 17:46

## 질문

dstone/dstone-ai-engine/src/main/resources/workflows/workflow-yml-guide.md
에서 2.5 inputs 에 대한 설명을 보다 쉽고 다양하게 수정해줘. 실제로 샘플용 workflow.yml를 표현하고 yml 로부터 각 단계마다 파싱하는 단계 및 전달되는 모양을 입체적으로 표현해줘.
dstone/dstone-ai-engine/src/main/resources/agents/agent-yml-guide.md 도 마찬가지로 수정해줘.

---

## 답변

두 가이드 파일부터 읽어보겠습니다.

---

## 답변

Now the core code paths for input flow.

---

## 답변

코드 흐름 확인이 끝났습니다. 이제 workflow 가이드의 `2.5 inputs`를 새로 작성합니다.

---

## 답변

샘플 속 주장 몇 가지(빈 문자열 통과 여부, `??`의 누락 경로 처리)를 코드로 검증하겠습니다.

---

## 답변

검증 완료, 문서 내용과 일치합니다. 이제 workflow 가이드의 기존 2.5 절(102~191행)을 새 내용으로 교체합니다.

---

## 답변

검증이 필요한 부분(AgentDefinition 필드, `{{ }}`를 prompt에 적었을 때의 동작)을 확인하겠습니다.

---

## 답변

`description` 필드를 목록에 추가하고, `{{ }}`/`{a.b}`를 prompt에 넣었을 때 실제 동작은 jshell로 직접 확인해 보겠습니다.

---

## 답변

이제 agent 가이드의 기존 `{변수}` 블록(75~90행)을 교체합니다.

---

## 답변

두 가이드를 고쳤습니다. 둘 다 **샘플 YAML → 파싱 → 요청 → 실행 중 전달 → LLM에게 실제로 가는 메시지** 순서로 따라가며 보여 주는 구조입니다. 커밋은 하지 않았습니다.

### `workflow-yml-guide.md` — 2.5 `inputs` 새로 작성
- **도입**: `inputs`를 Java 메서드의 매개변수로, `variables`를 넘기는 인자로 비유했습니다.
- **2.5.1 한눈에 보기**: ①~⑫ 단계를 기동 시(한 번) / 요청 시(매번) / step 실행 시(step마다)로 나눠 한 그림에 담았습니다.
- **2.5.2 샘플**: 설명용 `sql-review` Workflow와 Agent, 호출 요청 JSON입니다. 네 가지 타입(`list<string>`, `string`, `integer`, `object`)을 모두 씁니다.
- **2.5.3 단계별 따라가기**: 각 단계에서 값의 모양을 차례로 보여 줍니다.
  - SnakeYAML이 만든 Map
  - Jackson이 만든 `FieldDefinition` (축약형과 확장형이 같은 모양이 됨)
  - 기동 시 참조 검사 표
  - 요청 검사 순서와, JSON 값이 어떤 Java 타입으로 바뀌는지
  - 실행 컨텍스트 트리, forEach 반복, 채우기 전/후 비교, 최종 LLM 메시지 상자
- **2.5.4**: 같은 값이 TOOL 인자 / 글자 속 / Agent prompt / forEach 중 어디에 쓰이느냐에 따라 어떤 모양이 되는지 비교한 표입니다.
- **2.5.5 패턴 A~F**: 선언 없음, 문자열, 리스트+forEach, `list<object>`, 숫자/boolean, 그리고 선택값 흉내(`object` + `??`)입니다.
- **2.5.6~2.5.8**: 타입별 통과 값, 요청별 결과(실제 오류 문구 포함), 선언 쪽 경우별 정리입니다. 기존 내용은 모두 옮겨 담았습니다.

### `agent-yml-guide.md` — 1.2 `prompt`의 `{변수}` 채우기 새로 작성
Agent 가이드에는 `inputs` 절이 따로 없어서, 같은 역할을 하는 `{변수}` 채우기를 같은 방식으로 풀었습니다.
- 중괄호 세 가지(`${VAR}` / `{x}` / `{{input.x}}`)를 언제, 무엇으로 채우는지 비교한 표를 넣었습니다.
- **1.2.1~1.2.3**: 채팅 API와 Workflow step 두 경로를 한 그림으로 보여 주고, 설명용 `doc-translator-agent`로 `AgentDefinition`, 두 경로, 최종 메시지 구성(system / 이전 대화 / user + RAG + 형식 지시)까지 따라갑니다.
- **1.2.4~1.2.6**: 두 경로 비교, 패턴 A~E, 경우별 결과입니다. 기존의 "step 종류별로 덧붙이는 것" 표는 1.2.7로 번호만 붙였습니다.

### 직접 실행해 보고 바로잡은 점
Spring AI `PromptTemplate`을 직접 돌려 보니 기존 문서의 서술과 다른 점이 있어서 두 문서에 실제 동작으로 반영했습니다.
- prompt에 `{{input.x}}`를 적으면 오류 없이 `input.x`라는 글자가 들어갑니다. 조용히 틀리므로 문서에 경고로 적었습니다.
- `{options.strict}`처럼 맵 안쪽 키를 꺼내는 것은 **동작합니다**.
- 리스트는 구분자 없이 `ab`로, 맵은 키 이름만 `strict`로 들어갑니다. 기존 문서는 "모양을 보장하지 않는다"고만 적혀 있었습니다.

### 참고
- 두 샘플(`sql-review`, `doc-translator-agent`)은 **설명용이라 실제 파일은 만들지 않았고**, 본문에도 그렇게 적었습니다. 실제로 돌려 볼 수 있는 가장 가까운 샘플(`sample-foreach-parallel.yml`, `sample-general-chat.yml`)도 함께 안내했습니다. 원하시면 실제 샘플 파일로도 만들어 드리겠습니다.
- `docs/09.dstone-ai-engine.md`는 요약만 두고 자세한 내용은 이 두 가이드를 가리키고 있으며, 서로 어긋나는 서술이 없어서 고치지 않았습니다.

---

## 질문

<pasted_content id="052a">

아래의 샘플을 보면...

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
  output: "{{steps.review.text}}" ==========>>> review 라는 step 에서 text 라는 항목은 없는 것 같은데... ?
  steps:

    - id: check                           # ❶ 리스트를 forEach로 쪼개서 TOOL에 넘기기
      type: TOOL
      ref: validateSqlSyntax
      forEach: input.sqlList ==============>>> workflow 에서 inputs.sqlList 는 존재하지만 input.sqlList 는 없는 것 같은데?
      input:
        sql: "{{item}}" ==================>>> {{item}} 이 부분은 workflow.inputs 에서 어떤 메카니즘으로 파싱해서 세팅해주는건가?

    - id: review                          # ❷ 글자 안에 섞어 LLM에게 넘기기
      type: AGENT
      ref: sql-review-agent
      input: |
        아래 SQL을 PostgreSQL {{input.targetVersion}} 기준으로 검토해 주세요. ==============>>> workflow 에서 inputs.targetVersion 는 존재하지만 input.targetVersion 는 없는 것 같은데?
        조회 결과는 최대 {{input.maxRows}}건으로 제한해 주세요. ==============>>> workflow 에서 inputs 는 존재하지만 input 는 없는 것 같은데?
        엄격 모드: {{input.options.strict}} ==============>>> workflow 에서 inputs 는 존재하지만 input 는 없는 것 같은데?

        [SQL 목록]
        {{input.sqlList}} ==============>>> workflow 에서 inputs 는 존재하지만 input 는 없는 것 같은데?

        [문법 검사 결과]
        {{steps.check.text}} ===========>>> check 스텝에서 에서 text 라는 항목은 없는 것 같은데... ?
</pasted_content id="052a">

---

## 답변

짚으신 곳은 모두 같은 원인에서 나옵니다. `{{ }}`는 **YAML 파일의 구조를 가리키는 게 아니라, 실행 중에 엔진이 따로 만드는 "실행 컨텍스트" 트리를 가리킵니다.** 제가 쓴 문서가 이 점을 먼저 짚지 않아서 헷갈리셨을 것 같습니다.

### 두 세계는 서로 다릅니다

```
[YAML 파일 = 설계도]                        [실행 컨텍스트 = 실행할 때마다 새로 생기는 메모리]
 엔진이 켜질 때 한 번 읽힘                      요청이 올 때 만들어지고, step이 끝날 때마다 채워짐

workflow:                                   context
  inputs:          ← "이런 값이 와야 한다"      ├── input        ← 요청에 실제로 온 값
    sqlList: list<string>   (타입 선언만)      │    ├── sqlList : ["SELECT * FROM emp", ...]
    ...                                      │    └── message : "..."
  steps:                                     ├── steps        ← 끝난 step의 결과
    - id: check    ← 결과를 담을 키 이름         │    ├── check  : { input, text, data, error, items }
    - id: review                             │    └── review : { input, text, data, error }
                                             └── previous     ← 바로 앞 step의 결과
```

`{{input.x}}`, `{{steps.x.text}}`는 모두 오른쪽 트리의 경로입니다.

### 짚으신 곳마다 답변

| 템플릿 | 실제로 가리키는 곳 | 누가, 언제 넣나 | YAML과의 관계 |
|---|---|---|---|
| `input.sqlList`, `{{input.targetVersion}}` 등 | `context.input.<이름>` | 요청이 오면 `WorkFlowContext.create()`가 요청의 `variables`를 복사해 넣습니다 | `inputs.sqlList`는 **타입 선언**일 뿐 값이 없습니다. 값은 요청에서 옵니다 |
| `{{steps.check.text}}` | `context.steps.check.text` | `check` step이 끝나면 `WorkFlowContext.stepRecord()`가 `{input, text, data, error}`를 기록합니다 | YAML의 `id: check`는 그 결과를 담을 **키 이름**만 정합니다 |
| `{{steps.review.text}}` | `context.steps.review.text` | `review` step이 끝난 뒤 같은 방식으로 기록됩니다. `output`은 모든 step이 끝난 뒤에 채우므로 이미 값이 있습니다 | 위와 같습니다 |
| `{{item}}` | forEach 반복마다 만드는 컨텍스트 복사본의 `item` | `runForEach()`가 리스트 항목마다 `WorkFlowContext.withItem()`으로 복사본을 만들고 `item = 그 항목`을 넣습니다 | `inputs`와 직접 관계는 없습니다. `forEach:`가 가리킨 리스트의 항목입니다 |

- **`inputs`와 `input`의 관계**: Java 메서드로 치면 `inputs`는 **매개변수 선언**(`List<String> sqlList`)이고 `input`은 호출할 때 실제로 넘어온 **인자 값**입니다. 선언 쪽에는 값이 없으니 템플릿은 늘 값 쪽인 `input`을 읽습니다. `inputs`를 선언해 두면, 엔진이 켜질 때 `{{input.이름}}`에 오타가 없는지 이 선언 목록과 대조해 줍니다.
- **`text`의 정체**: YAML에 적는 항목이 아니라 엔진이 step 실행 결과로 만드는 필드입니다. `check`처럼 forEach를 쓰는 TOOL step이면 반복마다 받은 Tool 응답 원문을 줄바꿈으로 이은 글자이고, `review` 같은 AGENT step이면 LLM의 답변입니다.
- **`{{item}}`이 채워지는 흐름**:
  ```
  요청 variables.sqlList ─▶ context.input.sqlList ─▶ forEach: input.sqlList 가 리스트를 꺼냄
     ─▶ 항목마다 context 복사본 + item="SELECT * FROM emp" ─▶ 그 복사본으로 {{item}}을 채움 ─▶ Tool 호출
  ```

같은 헷갈림을 막도록 가이드 샘플 바로 아래에 이 설명을 넣겠습니다.

---

## 답변

`workflow-yml-guide.md`의 2.5.2 샘플 바로 아래(2.5.3 앞)에 "헷갈리기 쉬운 점" 상자를 넣었습니다. 위 설명을 줄인 것으로, 두 트리를 나란히 놓은 그림과 템플릿별 표, 매개변수 선언과 인자 값 비유가 들어 있습니다.

제가 마지막으로 파일을 읽은 뒤 누군가 이 파일을 고쳐 두었습니다. 제 추가 부분은 그 변경과 겹치지 않아 그대로 들어갔고, 그 변경 내용은 건드리지 않았습니다.

---

## 질문

git 에 반영해줘

---

## 답변

`188bef1` 커밋으로 main에 반영하고 GitHub(`origin/main`)에 push까지 마쳤습니다. 로컬과 원격이 같은 상태입니다.

이번 커밋에는 방금 추가한 "헷갈리기 쉬운 점" 상자만 들어갔습니다. 앞서 고친 두 가이드(`workflow-yml-guide.md` 2.5, `agent-yml-guide.md` 1.2)는 이전 자동 history 커밋(`619c5e0` 등)에 이미 들어가 있었습니다.

---

## 질문

이렇게 yml 정의와 실행엔진에서 부여하는 변수명이 다름으로 인한 혼돈이 꽤 있어.
이 부분을 최대한 통일 시켜줘.

---

## 답변

큰 변경이라 먼저 YAML 항목 이름과 실행 컨텍스트 이름이 어디서 어긋나는지 전부 조사하겠습니다.

---

## 답변

조사 결과 어긋나는 이름은 두 쌍입니다. 방향에 따라 바뀌는 YAML이 달라지므로 정하고 진행하겠습니다.

---

## 답변

검사 로직은 파악했습니다. 이제 Java 전체에서 옛 이름이 들어간 주석과 메시지를 줄 단위로 뽑아 보겠습니다.

---

## 답변

Java 변경 계획이 섰습니다. dstone-boot 화면에 옛 이름이 들어간 안내문이 있는지, YAML·문서 쪽 전체 범위도 함께 확인하겠습니다.

---

## 답변

Next, `WorkFlowContext`.

---

## 답변

`WorkFlowContext` 완료. 이어서 `StepOutcome`(data→output), `StepInput`(workflowInput→workflowInputs), `WorkFlowExecutor` 호출부를 바꿉니다.

---

## 답변

러너 클래스들의 지역 변수 `data`도 `output`으로 맞추겠습니다. 이름이 겹치지 않는지 먼저 확인합니다.

---

## 답변

충돌이 없습니다(`ToolStepRunner`의 `parse(StepOutputDefinition output, ...)`만 조심하면 되고, 거기의 `data`는 다른 메서드에 있습니다). 치환하겠습니다.

---

## 답변

한국어 조사(`output를` → `output을` 등)는 마지막에 한 번에 바로잡겠습니다. `ToolStepRunner`와 나머지 파일의 주석을 이어서 바꿉니다.

---

## 답변

`WorkFlowRegistry`를 고쳤습니다. `Template.java`에 시작 이름이 하드코딩돼 있는지 확인하고, 남은 주석을 파일별로 치환하겠습니다.

---

## 답변

`Template`에는 하드코딩이 없어 주석만 고치면 됩니다. 남은 Java 주석을 규칙에 따라 일괄 치환합니다(주석 줄에만 적용).

---

## 답변

`ToolOutcome.java`가 변경 목록에 없어서 확인하겠습니다.

---

## 답변

Python의 `\b`가 한글을 단어 문자로 취급해서 `step의 data가`처럼 한글에 붙은 `data`를 놓쳤습니다. ASCII 기준 경계로 다시 치환합니다.

---

## 답변

`StepDefinition`의 한 줄이 "output을 … output에 선언합니다"로 어색해져서 다듬고, 표 정렬도 맞춥니다. 그다음 컴파일해 봅니다.

---

## 답변

Java는 컴파일까지 통과했습니다. 이제 샘플 YAML의 옛 이름을 모두 확인합니다(Agent YAML은 prompt 속 일반 단어일 수 있어 따로 봅니다).

---

## 답변

모든 사용처가 템플릿 관련이라 일괄 치환하겠습니다.

---

## 답변

YAML 치환이 끝났습니다. `testApp-sdlc.yml` 주석의 정렬이 한 칸 밀렸는지 확인합니다.

---

## 답변

정규식이 CLAUDE.md의 일반 영어 단어 `data`(예: "main app data")까지 바꿨습니다. CLAUDE.md와 docs/09의 변경을 검토해서 잘못 바뀐 곳을 되돌리겠습니다.

---

## 답변

몇 군데를 잘못 바꿨습니다(`spring.data.redis` → `spring.output.redis`, CLAUDE.md의 "main app data"). 바로잡고, 문장이 어색해진 곳도 고치겠습니다.

---

## 답변

Now I'll edit docs/09 §6.2's tree so it states the names match the YAML.

---

## 답변

docs/09 정리가 끝났습니다. 이제 workflow 가이드에서 "inputs와 input이 다르다"고 설명하던 부분을 새 규칙에 맞게 다시 씁니다. 해당 구간을 확인합니다.

---

## 답변

Rewriting the "헷갈리기 쉬운 점" box around the "same name, declaration vs value" rule.

---

## 답변

상자를 새 규칙으로 다시 썼습니다. 가이드에 컨텍스트 루트를 뜻하는 `` `input` ``이 남았는지 줄 단위로 확인합니다.

---

## 답변

컨텍스트 루트를 뜻하는데 아직 `input`으로 남은 곳이 몇 군데 있어서 고칩니다.

---

## 답변

2.5 도입부에 "선언한 이름 그대로 꺼낸다"는 규칙을 넣고, 문서에 적힌 오류 메시지가 코드의 새 문구와 같은지 확인합니다.

---

## 답변

5절 표에 "예전 이름" 행을 추가합니다. docs/09에도 같은 표가 있는지 확인합니다.

---

## 답변

Loader가 독립적이라 리플렉션으로 Registry를 직접 돌려 볼 수 있습니다. 다시 컴파일하고(리소스 복사 포함) 검증용 하네스를 실행합니다.

---

## 답변

dstone-boot는 context를 JSON 그대로 보여 주기만 해서 동작 영향은 없습니다. 주석 한 줄만 새 이름으로 맞추고, 전체 변경을 점검합니다.

---

## 답변

마지막으로 가이드에 옛 이름이나 조사가 어긋난 문장이 남았는지 훑어봅니다.

---

## 답변

템플릿에서 꺼내는 이름을 YAML에 적는 이름과 똑같이 맞췄습니다. 이제 선언한 이름 그대로 꺼내 쓰면 됩니다. 컴파일, 기동 검사, 실행 경로 확인까지 마쳤고, 커밋은 하지 않았습니다.

### 바뀐 이름

| YAML에 적는 곳 | 예전 참조 | 지금 참조 |
|---|---|---|
| `workflow.inputs.sqlList` | `{{input.sqlList}}` | **`{{inputs.sqlList}}`** |
| step의 `output` (`schema`/`parse`) | `{{steps.x.data.sql}}` | **`{{steps.x.output.sql}}`** |
| step의 `input` | `{{steps.x.input}}` | 그대로 (원래 같았음) |

실행 컨텍스트 트리는 이제 `inputs` / `steps.<id>.{input, output, text, error, items}` / `previous`입니다. YAML에 없는 이름은 엔진이 만드는 결과 필드(`text`, `error`, `items`, `previous`, `item`)뿐입니다.

### 옛 이름은 받지 않음
옛 이름을 쓰면 기동이 실패하고, 오류 메시지가 새 이름을 알려 줍니다(실제 출력):
```
{{input.message}}의 경로 'input.message' - input은 inputs로 이름이 바뀌었습니다(YAML의 workflow.inputs와 같은 이름). inputs.message로 적으십시오.
steps.a 다음에는 [input, output, text, error, items] 중 하나가 와야 합니다. data는 output으로 이름이 바뀌었습니다(YAML step의 output과 같은 이름).
```

### 고친 곳 (34개 파일)
- **Java**: 핵심 로직은 세 파일입니다.
  - `Constants`: 컨텍스트 이름 상수 `INPUTS`, `FIELD_OUTPUT`
  - `WorkFlowContext`: 트리를 새 이름으로 만듦
  - `WorkFlowRegistry`: 새 이름으로 검사하고, 옛 이름이면 안내 메시지를 냄
  - 코드 안의 이름도 맞췄습니다: `StepOutcome.data()` → `output()`, `StepInput.workflowInput` → `workflowInputs`, 각 러너의 지역 변수. 나머지는 주석과 오류 문구만 바꿨습니다.
- **샘플 YAML 11개**: 참조와 주석을 새 이름으로 바꿨습니다.
- **문서**:
  - `workflow-yml-guide.md`: "헷갈리기 쉬운 점" 상자를 "YAML에 적은 이름 그대로 꺼낸다" 규칙과 선언↔값 대응표로 다시 썼습니다. 5절 표에는 옛 이름 행을 넣었습니다.
  - `agent-yml-guide.md`, `docs/09.dstone-ai-engine.md`(6.2절에 대응표 추가), `CLAUDE.md`도 맞췄습니다.
  - `docs/temp/`는 과거 설계 기록이라 그대로 뒀습니다.
- **dstone-boot**: 실행 상세 화면은 context를 JSON 그대로 보여 주기만 해서 동작 영향은 없습니다. 주석 한 줄만 바꿨습니다.

### 검증
- `dstone-ai-engine`, `dstone-boot` 컴파일이 통과합니다.
- `WorkFlowRegistry`를 직접 돌려 봤습니다.
  - 기존 샘플 Workflow 전체와 새 이름 조합(`inputs.options.q ?? inputs.message`, `forEach: steps.a.output.lines` 등)은 기동 검사를 통과합니다.
  - 옛 이름은 위 메시지로 실패합니다.
- 실행 경로도 확인했습니다. 컨텍스트 트리 생성, `{{inputs.x}}`, `{{steps.a.output.lines}}`, `{{previous.output...}}` 모두 실제 값을 찾습니다.
- 앱을 띄워 실제 Workflow를 호출하는 확인은 하지 않았습니다.

### 주의할 점
- **이미 저장된 실행**: 승인 대기(`WAITING_APPROVAL`)로 DB에 남은 실행은 context가 옛 이름(`input`, `data`)으로 저장돼 있습니다. 재개하면 참조를 찾지 못해 실패하므로 새로 실행해야 합니다.
- **배포된 엔진**: kind에 떠 있는 `dstone-ai-engine`은 아직 옛 버전이라 재배포가 필요합니다.
- **이 저장소 밖의 YAML**: 다른 프로젝트에서 이 엔진용 YAML을 쓰고 있다면 같은 방식으로 바꿔야 합니다. 옛 이름은 기동 때 바로 드러납니다.

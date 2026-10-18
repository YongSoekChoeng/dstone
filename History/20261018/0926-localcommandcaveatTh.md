# localcommandcaveatTh

> 생성일시: 2026-10-18 09:26

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="a049">
다시 물어볼께. 아무리 봐도 /definitions/**/*.yml 이 마음에 들지 않아.
특히 in/out 정의에 대해서.
네가 웹서치를 통해서이건 네 자체 판단이건 가장 명확하고 직관적인 방법을 고민해봐.
어떤 방법이 가장 좋을지. 그리고 생각을 이야기 해줘.
</pasted_content id="a049">

---

## 답변

현재 정의 파일부터 실제로 보고 말씀드리겠습니다.

---

## 질문

[Request interrupted by user for tool use]

---

## 질문

<pasted_content id="a049">
다시 물어볼께. 아무리 봐도 /definitions/**/*.yml 이 마음에 들지 않아.
특히 in/out 정의에 대해서.
네가 웹서치를 통해서이건 네 자체 판단이건 가장 명확하고 직관적인 방법을 고민해봐.
어떤 방법이 가장 좋을지. 그리고 생각을 이야기 해줘.
</pasted_content id="a049">

---

## 질문

<pasted_content id="a049">
다시 물어볼께. 아무리 봐도 /definitions/**/*.yml 이 마음에 들지 않아.
특히 in/out 정의에 대해서.
네가 웹서치를 통해서이건 네 자체 판단이건 가장 명확하고 직관적인 방법을 고민해봐.
어떤 방법이 가장 좋을지. 그리고 생각을 이야기 해줘.
필요하다면 파일타입들을 yml이 아닌 다른 타입으로 바꾸는 방법도 고려해도 되.
</pasted_content id="a049">

---

## 답변

## 제 생각 요약

지금 in/out이 불편한 진짜 이유는 YAML이라서가 아니라고 봅니다. 문제는 아래 세 가지입니다.

1. **같은 단어가 세 가지 뜻으로 쓰입니다.**
   - Agent의 `output`은 "내가 돌려주는 것"입니다.
   - Step의 `output`은 "결과를 어디에 저장할지"입니다. 그런데 키가 출처(`result`)이고 값이 목적지(`state.x`)라서, 읽는 방향도 거꾸로입니다.
   - Workflow의 `output.value`는 "최종 반환값"입니다.
2. **계약 하나를 보려면 파일 4개를 열어야 합니다.** Agent 하나에 `agent.yml`, `.st`, `input.schema.json`, `output.schema.json`이 있습니다. 게다가 JSON Schema는 기계용 형식이라 사람이 읽기에는 너무 장황합니다.
3. **state가 선언 없이 생깁니다.** 어떤 step이 저장하면 그 순간 이름이 생깁니다. 그래서 Workflow에 어떤 데이터가 흐르는지 파일 맨 위에서 한눈에 볼 수 없고, step 목록을 끝까지 읽어야 합니다.

## 참고한 방식들 (웹 검색은 하지 않았고, 원래 알고 있던 지식으로 비교했습니다)

| 방식 | 장점 | 우리에게 맞나 |
|---|---|---|
| **Google Dotprompt** (`.prompt` = YAML 머리말 + 프롬프트 본문, 짧은 타입 표기 *Picoschema*) | Agent 한 파일에 모델·입출력·프롬프트가 다 들어감 | **가장 잘 맞음** |
| **BAML** (`function Extract(text: string) -> Resume`) | 입출력 계약이 함수 시그니처처럼 명확함 | 생각은 최고지만 자체 언어라 Java 연동·학습 비용이 큼 |
| GitHub Actions (`with:` 입력, `steps.x.outputs`) | `with:`로 입력을 넘기는 방식이 직관적 | step 쪽 문법만 참고 |
| AWS Step Functions (`InputPath`/`ResultPath`/`OutputPath`) | — | 지금 구조와 비슷한데, 헷갈리기로 유명한 반면교사 |
| LangGraph (state를 타입으로 먼저 선언) | 데이터 흐름이 맨 위에 보임 | state 선언만 참고 |
| CUE / Pkl / JSON / TOML | 타입 검사가 강하거나(CUE/Pkl) 익숙함(JSON/TOML) | 비개발자에게 새 언어이거나 중첩 구조에 약해서 제외 |

## 추천안

### 1) Agent는 Markdown 한 파일로 (`agents/**/*.agent.md`)

Dotprompt 방식입니다. 머리말에 계약을 쓰고, 본문이 곧 프롬프트입니다. 그러면 `.st`와 `schema.json`이 사라집니다.

```markdown
---
id: pilot-newdev-file-designer
version: 1.0.0
model: { reasoning: none }
tools: []

input:
  path:        string, 설계할 파일 경로
  reference?:  string, 참고 구현 파일 내용
  sharedNames: list<string>
  feedback?:   string

output:
  path:     string
  action:   신규 | 수정 | 복사
  renames?: list<{from: string, to: string}>
  adds:     list<{after?: string, content: string}>
---
너는 파일 하나의 변경 설계를 맡는다.
대상 파일: {path}
...
```

타입은 이렇게 짧게 적습니다.
- 끝에 붙은 `?`는 선택 항목입니다.
- `, 설명`은 그 항목의 설명입니다.
- `a | b | c`는 enum입니다.
- `list<T>`는 목록입니다.

기동할 때 이것을 JSON Schema로 바꿉니다. 지금 있는 shorthand 확장(`normalize`)을 넓히면 되고, LLM에 보내는 형식도 그대로입니다. 여러 Agent가 함께 쓰는 구조는 `types/*.yml`에 이름을 붙여 두고 `output: FileDesign`처럼 가져다 씁니다.

### 2) Workflow step은 "함수 호출"처럼 읽히게

```yaml
state:                       # 이 Workflow에 흐르는 데이터를 맨 위에 다 선언
  request:      string
  requirements: RequirementDoc
  designs:      list<FileDesign>

steps:
  - id: step02
    agent: pilot-requirement-analyzer
    with:                    # 입력 (GitHub Actions의 with)
      request: ${state.request}
    save: requirements       # 결과 → state.requirements
    next: step03
```

- 읽는 순서가 "무엇을 넣고 → 누구를 부르고 → 어디에 저장하고 → 다음"으로 한 방향입니다.
- step에서는 `output`이라는 단어를 아예 없앱니다. 저장은 `save`, 실패 내용은 `saveError`, forEach 기록은 `saveEach`로 씁니다.
- Workflow의 최종 반환은 `return: ${state.reply}` 한 줄입니다.
- `state:` 선언이 있으니 기동 검증이 더 엄격해질 수 있습니다. 저장 대상이 선언에 있는지, Agent의 output 타입과 맞는지를 경고가 아니라 에러로 잡을 수 있습니다.

### 파일 형식에 대한 결론

- Workflow는 YAML을 유지합니다. 순서·분기가 있는 데이터에는 YAML이 여전히 가장 무난합니다.
- Agent는 Markdown 머리말 방식으로 바꿉니다. 프롬프트가 본문이 되니 긴 프롬프트도 자연스럽습니다.
- 새 언어(BAML/CUE/Pkl)는 비개발자도 YAML을 쓴다는 전제와 맞지 않아 권하지 않습니다.

## 정하실 것

1. Agent를 `.agent.md` 한 파일로 합칠지, 아니면 지금처럼 파일을 나누고 타입 표기만 짧게 바꿀지
2. step 키 이름(`with` / `save` / `return`)과 `state:` 선언을 필수로 할지 여부

제가 말씀드린 내용은 CLAUDE.md와 기본 샘플 Workflow 하나만 보고 판단한 것입니다. 방향을 정해 주시면 `pilot-workflow`의 실제 step 몇 개와 Agent 하나를 바꾸기 전과 후로 나란히 써서 보여드리겠습니다. 실제 파일로 비교해 보시는 게 결정하기 가장 쉬울 것 같습니다.

---

## 질문

<pasted_content id="a049">
  1. Agent를 .agent.md 한 파일로 합칠지, 아니면 지금처럼 파일을 나누고 타입 표기만 짧게 바꿀지
    ==>> 한 파일로.
  2. step 키 이름(with / save / return)과 state: 선언을 필수로 할지 여부
    ==>> ok.

  제가 말씀드린 내용은 CLAUDE.md와 기본 샘플 Workflow 하나만 보고 판단한 것입니다. 방향을 정해
  주시면 pilot-workflow의 실제 step 몇 개와 Agent 하나를 바꾸기 전과 후로 나란히 써서
  보여드리겠습니다. 실제 파일로 비교해 보시는 게 결정하기 가장 쉬울 것 같습니다.
  ==>> 그래 보여줘. 
</pasted_content id="a049">

---

## 답변

실제 파일을 읽어서 비교본을 만들겠습니다.

---

## 답변

## 1. Agent: 파일 4개를 1개로

**지금** (`pilot-newdev-file-designer-agent`): 정의 yml 29줄, 프롬프트 `.st` 32줄, 입력 스키마 41줄, 출력 스키마 127줄로 모두 229줄입니다. Agent가 무엇을 받는지 보려면 JSON 파일을 열어야 하고, 프롬프트는 또 다른 파일에 있습니다.

**바꾼 뒤** (`agents/pilot/pilot-newdev-file-designer-agent.agent.md`):

```markdown
---
# 신규 개발 경로의 설계 Agent. 파일 하나의 설계만 합니다.
# step17(forEach)이 변경 대상 파일마다 새 대화로 한 번씩 부릅니다. Tool이 없고, 파일 내용은 Workflow가 넣어 줍니다.
# (기존 머리 주석은 여기로 옮깁니다. 아래 본문은 LLM에 그대로 가니 주석을 본문에 쓰면 안 됩니다)
id: pilot-newdev-file-designer-agent
version: 1.0.0
description: 신규 개발 - 변경 대상 파일 하나의 설계를 데이터로 낸다
model:
  reasoning: none        # 2026-10-08 측정: 파일 하나에 10~21초

input:
  target:           ChangeTarget, 이번에 설계할 변경 대상 파일 하나
  request?:         string, 요청서(00-request.md)의 내용
  requirements?:    list<object>, 기능 요구사항 전체
  plan?:
    targets:        list<ChangeTarget>, 변경 대상 파일 전체
    sharedNames:    list<SharedName>, 여러 파일이 함께 쓰는 이름
  referenceSource?: string, 본뜰 참고 파일의 내용. 없으면 빈 글자
  currentSource?:   string, 수정할 파일의 지금 내용. 신규 파일이면 빈 글자
  feedback?:        string, 다시 설계할 때 반영할 지적 사항

output: FileDesign
---
[역할] 너는 이 시스템의 애플리케이션 아키텍트다. 파일 하나에 대해 ...
(프롬프트 32줄 그대로)
```

여러 파일이 함께 쓰는 구조는 `types/pilot.types.yml`에 이름을 붙여 둡니다. 출력 스키마 127줄은 아래처럼 약 25줄이 됩니다.

```yaml
ChangeTarget:
  path:           string, 만들거나 고칠 파일의 절대경로
  changeType:     신규 | 수정
  referencePath?: string, 신규 파일이 본뜰 참고 파일. 없으면 빈 글자
  change:         string, 무엇을 만들거나 바꾸는지
  frs:            list<string>, 이 변경이 해결하는 FR 번호

SharedName:
  what:  string, 무엇의 이름인지(테이블, 컬럼, URL, 쿼리ID ...)
  name:  string, 새 기능에서 쓸 이름
  from?: string, 참고 구현에서 이에 해당하는 이름

FileDesign:
  path:           string, 설계한 파일의 절대경로. target.path 그대로
  changeType:     신규 | 수정
  referencePath?: string, target.referencePath 그대로
  summary:        string, 이 파일에서 하는 일 한두 줄
  renames?:       list<{from: string, to: string, what?: string}>, 신규 - 바뀌는 이름
  removes?:       list<string>, 신규 - 참고 파일에서 빼는 것
  adds?:          list<{where: string, content: string}>, 신규 - 글자 그대로 넣는 내용
  edits?:         list<FileEdit>, 수정 - 바꿀 곳
  frs?:           list<string>
  verify?:        string, 확인 방법
  questions?:     list<string>, 결정이 필요한 사항

FileEdit:
  location: string, 위치(쿼리ID, 클래스.메서드 ...)
  before?:  string, 지금 파일에서 글자 그대로 옮긴 변경 전
  after:    string, 변경 후
  append?:  boolean, 파일 끝에 덧붙이면 true
  reason?:  string, 이유 한 줄
```

규칙은 아래와 같습니다.
- `output`이 타입이면 JSON으로 엄격하게 검증합니다. `string`이거나 생략하면 글자로 받습니다. 그래서 `format`과 `validation` 키가 없어집니다.
- `prompt.system` 경로도 없어집니다. 본문이 곧 프롬프트입니다.
- 원래 `object`로만 적혀 있던 `target`·`plan`에 실제 구조가 생깁니다. 지금은 LLM이 `target` 안에 무엇이 오는지 모르고 받는 셈입니다.

## 2. Workflow: step16~22

**지금:**

```yaml
  input: string
  output:
    value:
      route: "${state.classify.route}"
      ...
  steps:
    - id: step17
      type: AGENT
      agent: pilot-newdev-file-designer-agent
      forEach: "${state.designTargets}"
      input:
        request: "${state.request}"
        target: "${item.item}"
        ...
      output:
        result: "state.designs"
        error: "state.designError"
      next: step18
      onFailure: step18
```

**바꾼 뒤:**

```yaml
workflow:
  id: pilot-workflow
  version: 1.0.0
  input: string, 00-request.md가 있는 폴더 경로

  state:                                   # 이 Workflow에 흐르는 데이터 전부
    request:           string
    classify:          Route               # 엔진이 정한 타입(ROUTER 결과)
    analysis:          NewdevAnalysis
    designTargets:     list<object>
    designs:           list<FileDesign>
    designError?:      string
    designCheck:       CheckResult         # 엔진이 정한 타입(checkData 결과)
    editDesigns:       list<FileDesign>
    editCheckError?:   string
    newDesignApproval: Approval            # 엔진이 정한 타입(APPROVAL 결과)
    ...

  return:
    route:        ${state.classify.route}
    requirements: ${state.requirements}
    ...

  steps:
    - id: step16
      type: TOOL
      tool: readItemFiles
      with:
        items: ${state.analysis.targets}
        read: { referenceSource: referencePath, currentSource: path }
      save: designTargets
      next: step17
      onFailure: FAIL

    - id: step17
      type: AGENT
      agent: pilot-newdev-file-designer-agent
      forEach: ${state.designTargets}
      with:
        request:         ${state.request}
        requirements:    ${state.analysis.requirements}
        target:          ${item.item}
        plan:
          targets:       ${state.analysis.targets}
          sharedNames:   ${state.analysis.sharedNames}
        referenceSource: ${item.referenceSource}
        currentSource:   ${item.currentSource}
        feedback: "${state.designCheck.feedback:}\n${state.editCheckError:}\n${state.newDesignApproval.comment:}"
      save: designs
      saveError: designError
      next: step18
      onFailure: step18

    - id: step20
      type: TOOL
      tool: editFileTexts
      forEach: ${state.editDesigns}
      with: { filePath: ${item.path}, edits: ${item.edits}, dryRun: true }
      saveError: editCheckError
      next: step21
      onFailure: step21

    - id: step22
      type: APPROVAL
      approval: { artifact: architecture-document, approverRole: PL }
      save: newDesignApproval
      routes: { 진행: step23, 재설계: step16, 재분석: step11, 중단: FAIL }
```

한 줄씩 비교하면 이렇습니다.

| | 지금 | 바꾼 뒤 |
|---|---|---|
| 넣는 것 | `input:` (Workflow의 `input`과 이름이 같음) | `with:` |
| 저장 | `output: { result: "state.designs" }` | `save: designs` |
| 실패 저장 | `output: { error: "state.designError" }` | `saveError: designError` |
| 최종 결과 | `output: { value: ... }` | `return:` |
| 식의 따옴표 | 항상 `"${...}"` | 필요 없음(`\n`이 들어갈 때만) |
| 데이터 목록 | step을 다 읽어야 앎 | 맨 위 `state:` |

**새로 생기는 기동 검사**: step17은 `FileDesign`을 내는 Agent를 forEach로 돌리므로 결과는 `list<FileDesign>`입니다. 그런데 `state.designs`를 `string`으로 선언했다면, 지금처럼 경고로 그치지 않고 기동이 실패합니다. `save:`한 이름이 `state:`에 없어도 실패합니다.

## 바꾸면서 보인 것 (이번 범위 밖)

- **`${item.item}`이 어색합니다.** `readItemFiles`가 원래 항목을 `item` 안에 감싸서 생기는 일입니다. 형식 문제가 아니라 Tool이 돌려주는 모양의 문제라 따로 다뤄야 합니다.
- **`type: AGENT`는 줄일 수 있습니다.** `tool:`이나 `approval:` 키만 보고도 종류를 알 수 있기 때문입니다. 다만 ROUTER와 SUPERVISOR도 `agent:`를 쓰기 때문에 `type`을 남겨 두는 편이 헷갈리지 않습니다. 저는 남기기를 권합니다.

## 바꾸는 데 드는 일

엔진 쪽과 정의 파일 쪽을 함께 바꿔야 합니다.

- **엔진**
  - 로더: `.agent.md`의 머리말과 본문을 나누고, `types/*.yml`을 읽습니다.
  - 짧은 타입 표기 파서를 넓힙니다(`?`, `, 설명`, `a | b`, `{...}`, 타입 이름).
  - 키 이름을 바꿉니다(`with`/`save`/`saveError`/`saveEach`/`return`).
  - `state:` 선언 검증을 넣고, 옛 키에는 안내 메시지를 붙입니다.
- **정의 파일**: Agent 28개, Workflow 19개, 스키마 JSON 33개를 옮깁니다. 스크립트로 기계적으로 바꾸고, 타입에 이름을 붙이는 일만 직접 손봅니다.
- **문서**: `docs/09.dstone-ai-engine.md`의 §7·§8과 CLAUDE.md를 고칩니다.

이 모양으로 진행해도 될까요? 고칠 곳(키 이름, `type` 유지 여부, `types` 파일 위치)이 있으면 말씀해 주세요. 정해지면 엔진 수정부터 시작하겠습니다.

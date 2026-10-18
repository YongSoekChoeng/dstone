# localcommandcaveatTh

> 생성일시: 2026-10-18 10:20

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

---

## 질문

mcp 도 동일한 규칙을 적용할 수 있나?

---

## 답변

네, 적용할 수 있습니다. 다만 MCP 파일은 Agent와 성격이 달라서 바뀌는 범위가 작습니다.

## 차이점

MCP 파일(`mcp/sample/sample-filesystem-mcp.yml`)은 **접속 설정**입니다. 어떤 프로세스를 띄울지, 그 서버의 Tool 중 무엇을 받을지만 적습니다. Tool의 입력 계약은 서버가 접속 시점에 직접 알려 줍니다(MCP `tools/list`의 `inputSchema`). 그래서 Agent처럼 `input`/`output`을 손으로 적을 자리가 원래 없습니다.

## 같은 규칙으로 바꿀 수 있는 것

**1) Workflow에서 MCP Tool을 부르는 step**: 다른 TOOL step과 똑같이 `with` / `save`를 씁니다. 이미 앞의 안에 들어 있습니다.

**2) Tool마다 결과 타입을 적는 칸**: `state:` 선언을 필수로 하면 문제가 하나 생깁니다. MCP Tool의 결과 타입을 기동 시점에 알 수 없다는 점입니다. 접속에 실패한 서버는 건너뛰고 기동하기 때문입니다. 그래서 `allowedTools` 목록을 `tools:` 맵으로 바꾸고, 값에 결과 타입을 적을 수 있게 하자는 생각입니다.

```yaml
# mcp/sample/sample-filesystem.mcp.yml
# (머리 주석은 그대로)
id: sample-filesystem
transport: STDIO              # STDIO | SSE
command: npx
args: [-y, "@modelcontextprotocol/server-filesystem", "${APP_HOME}/${APP_NAME}/mcp/server-filesystem"]

tools:                        # 여기 적은 Tool만 등록. 값은 결과 타입(생략 가능)
  list_directory: string
  directory_tree: string
  read_text_file: string
  write_file:
  edit_file:
  move_file:
```

결과 타입은 다음 순서로 정하자는 안입니다.
- 적어 둔 타입이 있으면 그것을 씁니다.
- 없으면 서버가 알려 준 `outputSchema`를 씁니다. MCP 스펙 2025-06 판에 Tool 출력 스키마가 생긴 것으로 알고 있는데, Spring AI 2.0.1이 그 값을 넘겨주는지는 확인해 봐야 합니다.
- 그것도 없으면 `string`으로 봅니다.

`tools:` 맵에 적은 타입은 MCP 서버가 검증해 주는 계약이 아닙니다. 그래서 기동 검사는 `state:`와 맞는지만 보고, 실제 값은 실행할 때 확인합니다.

**3) Agent 쪽 키 이름과 맞추기**: `allowedTools`를 `tools`로 바꿉니다. 지금도 "비우면 전부 허용"인데, 그 의미는 `tools: "*"`로 분명하게 적게 하자는 생각입니다.

## 파일 형식

MCP 파일은 YAML로 둡니다. Agent를 `.md`로 바꾼 이유는 프롬프트가 본문이 되기 때문인데, MCP에는 본문이 없습니다.

## 함께 맞추고 싶은 것

앞의 Agent 안에서 저는 `agent:` 감싸개를 없앴습니다. 그런데 Workflow의 `workflow:`와 MCP의 `mcpServer:`는 그대로 남아 있습니다. 셋을 같은 규칙으로 맞추는 게 좋겠습니다.

제 추천은 **감싸개를 모두 없애고, 파일 종류는 확장자로 구분하는 것**입니다.

| 종류 | 확장자 |
|---|---|
| Agent | `*.agent.md` |
| Workflow | `*.workflow.yml` |
| MCP | `*.mcp.yml` |
| 타입 | `*.types.yml` |

그러면 파일 이름만 봐도 무엇인지 알 수 있고, 내용이 한 단계 덜 들여써집니다.

MCP의 `tools:` 맵 방식과 감싸개 제거, 이 두 가지도 함께 넣을까요?

---

## 질문

agent 만 확장자가 md 인가?

---

## 답변

네, 제 안에서는 Agent만 `.md`입니다. 기준은 하나입니다. **"본문에 긴 글이 있고, 그 글이 LLM에 그대로 가는 파일"만 `.md`로 합니다.**

| 종류 | 본문(긴 글) | 형식 |
|---|---|---|
| Agent | 프롬프트 | `.agent.md` |
| Workflow | 없음 (step만 있는 구조 데이터) | `.workflow.yml` |
| MCP | 없음 (접속 설정) | `.mcp.yml` |
| 타입 | 없음 | `.types.yml` |

나머지를 `.yml`로 두는 이유는 두 가지입니다.
- **본문이 없습니다.** `.md`로 만들면 머리말만 있고 본문이 빈 파일이 되어, 바꾸는 의미가 없습니다.
- **YAML 편집기 도움을 잃습니다.** IDE는 `.yml`에 대해 문법 색, 들여쓰기 오류 표시, 스키마 기반 자동 완성을 해 줍니다. `.md` 머리말 안에서는 이런 도움이 대부분 안 됩니다. step이 수백 줄인 `pilot-workflow`에서는 이 차이가 큽니다.

## 반대로, 전부 `.md`로 맞추는 안

생각해 볼 만한 지점이 하나 있습니다. Workflow 파일은 지금 머리 주석이 길다는 점입니다. `pilot-workflow.yml`은 첫 50줄이 설명 주석입니다. 모두 `.md`로 하면 그 설명을 본문에 마크다운으로 쓸 수 있고, 형식도 하나로 통일됩니다.

그래도 권하지 않는 이유는 두 가지입니다.
1. **본문의 뜻이 파일마다 달라집니다.** Agent의 본문은 LLM에 가는 글이고, Workflow의 본문은 엔진이 무시하는 설명입니다. 같은 모양인데 한쪽은 고치면 동작이 바뀌고 한쪽은 안 바뀌니, 오히려 헷갈립니다.
2. 위에서 말한 대로, 긴 step 목록을 편집할 때 YAML 편집기 도움을 잃습니다.

그래서 Agent만 `.md`이고, 나머지는 `.yml`에 주석으로 설명을 다는 지금 방식을 유지하는 쪽을 추천합니다. 통일하는 쪽이 더 좋으시면 말씀해 주세요. 전부 `.md`로도 할 수 있습니다.

---

## 질문

그리고 타입이 반드시 필요할까? 샘플을 볼여줘.

---

## 답변

반드시 필요하지는 않습니다. 다시 따져 보니, 앞의 안은 타입을 **두 번** 적게 하고 있었습니다.

- `state:`에 적은 타입은 엔진이 이미 알고 있습니다. `save: designs` 하는 step이 부르는 Agent의 `output`이 곧 그 타입이니, 사람이 또 적는 것은 같은 내용의 중복입니다.
- 타입 파일(`types/*.yml`)의 쓸모는 같은 구조를 여러 Agent가 함께 쓸 때 한 번만 적는 것뿐입니다. 그런데 그렇게 하면 Agent 파일 하나만 봐서는 계약을 다 알 수 없게 됩니다. 처음에 불만이셨던 "여러 파일을 열어야 한다"는 문제가 다시 생깁니다.

## 고친 안

1. **타입 파일을 없앱니다.** Agent는 자기 입출력을 자기 파일에 모두 적습니다. 다른 Agent와 구조가 겹치더라도 그대로 적습니다.
2. **`state:`에는 이름과 설명만 적습니다.** 타입은 저장하는 step에서 엔진이 알아냅니다. 선언하지 않은 이름에 저장하면 기동이 실패하고, 읽는 경로가 그 타입에 없으면 경고 대신 기동 실패로 올립니다.
3. **짧은 타입 표기는 Google Dotprompt의 Picoschema 문법을 그대로 씁니다.** 제가 앞에서 지어낸 `list<…>`나 `a | b`보다, 이미 있는 규격을 따르는 편이 설명하기도 쉽습니다(문법은 제가 알고 있는 대로 옮긴 것이라, 구현할 때 원문과 맞춰 보겠습니다).
   - `name: string, 설명` 형식으로 적습니다.
   - 끝에 붙은 `?`는 선택 항목입니다.
   - `field(array, 설명): string`은 글자 목록입니다.
   - `field(array, 설명):` 아래에 필드를 적으면 객체 목록입니다.
   - `field(enum, 설명): [A, B]`는 enum입니다.
   - 아래에 필드를 적은 `field(object, 설명):`는 객체입니다.
   - 아무 값이나 받으면 `any`입니다.

## 샘플 1: 가장 단순한 경우 (`sample-basic-echo`)

**지금**: `agents/sample/sample-basic-echo-agent.yml`과 `prompts/sample/sample-basic-echo-agent.st`, 이렇게 2개 파일입니다.

**바꾼 뒤** `agents/sample/sample-basic-echo-agent.agent.md`:

```markdown
---
id: sample-basic-echo-agent
version: 1.0.0
description: AGENT step 자체의 기본 동작 확인 전용 - 프롬프트 템플릿 변수도, Tool도 쓰지 않는다.
# input / output을 안 적으면 둘 다 글자(string)
---
당신은 짧고 명확하게 한국어로 답하는 AI 어시스턴트입니다.
```

`workflows/sample/sample-agent-basic-echo.workflow.yml`:

```yaml
id: sample-agent-basic-echo
version: 1.0.0
description: AGENT step 1개만으로 구성된 가장 기본적인 Workflow. input에 글자 하나만 보내면 된다.
input: string, 질문 한 줄
settings:
  maxIterations: 3

state:
  reply: Agent의 답

return: ${state.reply}

steps:
  - id: step00
    type: AGENT
    agent: sample-basic-echo-agent
    with: ${input}
    save: reply
    next: END
    memory: true
```

## 샘플 2: 복잡한 경우 (`pilot-newdev-file-designer-agent`)

`agents/pilot/pilot-newdev-file-designer-agent.agent.md` 한 파일로 끝납니다. 타입 파일을 참조하지 않습니다.

```markdown
---
# 신규 개발 경로의 설계 Agent. 파일 하나의 설계만 합니다.
# step17(forEach)이 변경 대상 파일마다 새 대화로 한 번씩 부릅니다. Tool 없음, 파일 내용은 Workflow가 넣어 줍니다.
id: pilot-newdev-file-designer-agent
version: 1.0.0
description: 신규 개발 - 변경 대상 파일 하나의 설계를 데이터로 낸다
model:
  reasoning: none          # 2026-10-08 측정: 파일 하나에 10~21초

input:
  target(object, 이번에 설계할 변경 대상 파일 하나):
    path: string, 만들거나 고칠 파일의 절대경로
    changeType(enum): [신규, 수정]
    referencePath?: string, 본뜰 참고 파일의 절대경로. 없으면 빈 글자
    change: string, 무엇을 만들거나 바꾸는지
    frs(array, 이 변경이 해결하는 FR 번호): string
  request?: string, 요청서(00-request.md)의 내용
  requirements?(array, 기능 요구사항 전체): any
  plan?(object, 변경 대상 전체와 함께 쓰는 이름):
    targets(array, 변경 대상 파일 전체): any
    sharedNames(array, 여러 파일이 함께 쓰는 이름):
      what: string, 무엇의 이름인지(테이블, 컬럼, URL, 쿼리ID ...)
      name: string, 새 기능에서 쓸 이름
      from?: string, 참고 구현에서 이에 해당하는 이름
  referenceSource?: string, 본뜰 참고 파일의 내용. 없으면 빈 글자
  currentSource?: string, 수정할 파일의 지금 내용. 신규 파일이면 빈 글자
  feedback?: string, 다시 설계할 때 반영할 지적 사항

output:
  path: string, 설계한 파일의 절대경로. target.path 그대로
  changeType(enum): [신규, 수정]
  referencePath?: string, target.referencePath 그대로. 없으면 빈 글자
  summary: string, 이 파일에서 하는 일 한두 줄
  renames?(array, 신규 파일 - 참고 파일에서 바뀌는 이름):
    from: string, 참고 파일에 있는 글자
    to: string, 새 파일에서 쓸 글자
    what?: string, 무엇의 이름인지
  removes?(array, 신규 파일 - 참고 파일에서 빼는 것): string
  adds?(array, 신규 파일 - 글자 그대로 넣는 내용):
    where: string, 넣을 자리
    content: string, 넣을 내용(글자 그대로)
  edits?(array, 수정 파일 - 바꿀 곳):
    location: string, 위치(쿼리ID, 클래스.메서드, 무엇의 아래)
    before?: string, 지금 파일에서 글자 그대로 옮긴 변경 전. 끝에 덧붙이면 빈 글자
    after: string, 변경 후. 끝에 덧붙이면 덧붙일 내용
    append?: boolean, 파일 끝에 덧붙이는 변경이면 true
    reason?: string, 이유 한 줄
  frs?(array, 이 파일의 변경이 해결하는 FR 번호): string
  verify?: string, 이 파일의 변경을 확인하는 방법
  questions?(array, 결정이 필요한 사항. 없으면 빈 배열): string
---
[역할] 너는 이 시스템의 애플리케이션 아키텍트다. 파일 하나에 대해 ...
(프롬프트 32줄 그대로)
```

`pilot-workflow.workflow.yml`의 `state:`는 이렇게 됩니다.

```yaml
state:
  request:           요청서(00-request.md) 내용 - step00
  classify:          요청 분류(신규개발/일반) - step01
  analysis:          요구사항 + 변경 대상 + 함께 쓰는 이름 - step11
  designTargets:     변경 대상마다 참고 파일·지금 내용을 붙인 것 - step16
  designs:           파일별 설계 - step17
  designError:       설계에 실패한 파일의 사유 - step17
  designCheck:       설계 검증 결과 - step18
  editDesigns:       수정 파일의 설계만 - step19
  editCheckError:    변경 전 글자가 안 맞는 곳 - step20
  newDesignApproval: 설계 승인 결과 - step22
  ...
```

## 대가

타입 파일을 없애면 `targets`처럼 같은 구조가 분석 Agent의 output과 설계 Agent의 input에 두 번 적힙니다. 두 곳이 어긋날 수도 있는데, 이는 Workflow 기동 검사로 잡습니다. step17의 `target: ${item.item}`이 가리키는 값의 구조를 엔진이 분석 Agent의 output에서 알아내서, 설계 Agent가 받는 input과 비교하면 됩니다.

설계 Agent 샘플에서 `requirements`와 `plan.targets`를 `any`로 둔 것은 지금 JSON 스키마가 그 둘을 구조 없이 `array`로만 적고 있기 때문입니다.

이 모양(타입 파일 없음, `state:`는 이름과 설명만, Picoschema 표기)으로 정해도 될까요?

---

## 질문

그래 그렇게 진행해. 그리고 올려줘.

---

## 답변

먼저 엔진 코드를 읽겠습니다. 정의 파일을 읽어 들이는 곳부터 보겠습니다.

---

## 답변

Picoschema 원문을 확인하고, 지금 스키마 파일들에 쓰인 JSON Schema 키워드가 Picoschema로 다 옮겨지는지 보겠습니다.

---

## 답변

이제 Agent 정의와 스키마 처리 코드를 읽겠습니다.

---

## 답변

이제 `JsonSchemaUtil`에서 옛 축약형을 걷어내고, 선택 필드(`[타입, "null"]`)도 타입을 읽을 수 있게 고칩니다.

---

## 답변

다음은 `SchemaDefinition`입니다. `{schema: …}` 감싸개 없이 Picoschema 값을 바로 받게 바꿉니다.

---

## 답변

이제 `AgentDefinition`입니다. 프롬프트는 본문 글자 하나가 되고, output은 input과 같은 Picoschema 계약이 됩니다.

---

## 답변

`AgentRegistry`에서 없어진 `output.format`/`validation` 검사를 빼고, 안내 문구를 새 형식에 맞춥니다.

---

## 답변

`saveInput`도 남기기로 했습니다(이 샘플이 "실제로 검사한 SQL"을 저장해 씁니다). step record 다섯 개와 공통 interface를 고칩니다.

---

## 답변

`WorkFlowContext.saveOutput`도 "state 이름에 통째로 저장"으로 단순하게 고칩니다.

---

## 답변

`WorkFlowDefinition`을 고칩니다. `state:`는 "이름 → 설명" 맵, 최종 결과는 `return:`입니다. `WorkFlowOutputDefinition`은 없어집니다.

---

## 답변

이제 로더(`YamlDefinitionLoader`)를 고칩니다. `.agent.md` 머리말과 본문 나누기, 감싸개 제거, 옛 형식 파일이 남아 있으면 기동을 막는 검사를 넣습니다.

---

## 답변

이제 로더 본체를 다시 씁니다.

---

## 답변

엔진 쪽 1차 수정이 끝났습니다. 컴파일해서 남은 곳을 찾겠습니다.

---

## 답변

컴파일이 통과했습니다. 이제 쓰이지 않게 된 코드를 정리합니다.

---

## 답변

Java 쪽 문구 정리까지 끝났습니다. 다음은 정의 파일 28+19+1개를 새 형식으로 옮기는 변환 스크립트입니다.

---

## 답변

변환 스크립트를 만들기 전에, Workflow 머리 주석에 옛 형식 설명이 들어 있는 두 파일을 확인합니다.

---

## 답변

변환 스크립트를 작성합니다. 결과가 원래 스키마와 같은지도 함께 검사하게 만듭니다. Picoschema를 다시 JSON Schema로 풀어서 원본과 비교합니다.

---

## 답변

먼저 복사본에서 시험 삼아 돌려 보겠습니다.

---

## 답변

구조 없는 목록(`items: {}`)은 원래와 같은 뜻이므로 비교에서 같게 보도록 고치고 다시 돌립니다.

---

## 답변

Escaping이 꼬였습니다. 편집 도구로 직접 고치겠습니다.

---

## 답변

`sm-workflow`는 저장 위치에 하위 경로(`state.classify.route`)를 쓰고 있습니다. 전체를 보고 판단하겠습니다.

---

## 답변

변환이 끝까지 돌았습니다. 결과물을 직접 읽어 보겠습니다. 설계 Agent 하나, Workflow 둘을 봅니다.

---

## 답변

결과가 의도대로 나왔습니다. 자동으로 만든 state 설명 문구만 "step01 step의 결과"처럼 더 자연스럽게 다듬은 뒤, 실제 저장소에 적용합니다.

---

## 답변

변환이 실제 저장소에 적용됐습니다. 빌드하고, 엔진을 다른 포트로 띄워 기동 검증 로그를 보겠습니다.

---

## 답변

엔진 하나가 정상으로 떠 있습니다. LLM이 필요 없는 Workflow들을 골라 실행 예시를 확인합니다.

---

## 답변

시험이 뒤에서 도는 동안 정의 파일의 주석을 정리하겠습니다. 옛 파일 이름, `output:`, `prompts/`, `schemas/` 같은 말이 남은 곳을 찾습니다.

---

## 답변

기계적으로 바꿀 수 있는 것(파일 이름)은 일괄로 바꾸고, 뜻을 설명하는 문장은 하나씩 고칩니다.

---

## 답변

남은 것은 `state.schema` 예시 설명과 pilot/sm 머리의 입력·출력 안내 블록입니다.

---

## 답변

pilot과 sm의 안내 주석 블록을 새 형식에 맞게 다시 씁니다.

---

## 답변

주석 안 `파일이름.yml의`처럼 한글이 바로 붙은 경우는 바꾸지 못했습니다. 정규식 경계 문제여서 고쳐서 다시 돌립니다.

---

## 답변

파일 이름은 정리됐습니다. 이제 pilot-workflow의 `state:` 설명을 자동 문구("step02 step의 결과") 대신 실제 뜻으로 씁니다.

---

## 답변

`{route: …}`처럼 값 안에 `: `가 들어가면 YAML이 깨집니다. 고친 뒤 파일 전체가 YAML로 읽히는지 확인합니다.

---

## 답변

"신규개발: " 접두어의 콜론이 문제입니다. 괄호 표기로 바꿉니다.

---

## 답변

이제 설계 Agent의 `target`과 `plan`에 실제 구조를 적습니다. 앞에서 보여 드린 그 모양이고, 분석 Agent output의 `targets`/`sharedNames`와 같은 구조입니다.

---

## 답변

2~5번 모두 의도한 문구로 막힙니다. 조사(을/를)가 어색한 문장 하나는 나중에 고치고, 6~11번을 돌립니다.

---

## 답변

11가지 잘못된 경우가 모두 의도한 문구로 막혔습니다. 설계 Agent를 고친 새 jar도 경고 없이 뜨는지 확인합니다.

---

## 답변

LLM 호출도 바뀐 스키마로 정상입니다(구조화 출력 2건, 각 3초 안팎).

이제 문서입니다. 옛 형식을 설명하는 곳을 저장소 전체에서 찾습니다.

---

## 질문

[Usage limit reached; a short grace allowance remains, then this turn is cut off without warning. Judge what's left: if it's genuinely only a few steps, finish it properly, then stop and say so without extra polishing. Otherwise don't attempt it all: finish the piece in hand and at most two more like it, then stop. The allowance also pays for any subagents you have running: they don't get this message and will keep going, so it's up to you how to handle them. Either way, end by telling the user their usage limit was reached, what's done and what's left.]

---

## 답변

사용 한도에 도달해 여기서 멈춥니다. 정의 파일 형식 변경은 끝났고 `main`에 커밋·푸시했습니다(`aba7d2b`). 다만 상세 문서(`docs/09.dstone-ai-engine.md`)는 아직 옛 형식 설명 그대로입니다.

**바뀐 형식**
- **Agent:** `agents/**/이름.agent.md` 한 파일입니다. `---` 사이 머리말에 설정과 `input`/`output`을 적고, 본문이 프롬프트입니다. `prompts/`, `schemas/` 폴더는 없앴습니다.
- **Workflow / MCP:** `.workflow.yml` / `.mcp.yml`이고, 맨 위 `workflow:` / `mcpServer:` 키를 없앴습니다.
- **입출력 모양:** Picoschema로 그 자리에 적습니다. Picoschema와 다른 점이 두 가지 있습니다.
  - 필드 이름에 공백·한글·괄호를 쓸 수 있게 넓혔습니다(testApp에 `2. 현재 상태(As-Is)` 같은 이름이 있어서).
  - 선언하지 않은 필드가 와도 받습니다. LLM이 필드를 하나 더 붙였다고 step이 실패하지 않게 하려는 것입니다.
- **선택 필드(`이름?`):** Picoschema 규칙대로 `null`도 통과합니다. LLM이 `null`을 내도 받는 편집·필터 Tool은 빈 글자로 처리하는 것을 확인했습니다.
- **step과 state:**
  - 키는 `with` / `save` / `saveError` / `saveInput` / `saveEach` / `return`입니다.
  - `state:`에 저장하는 이름을 모두 선언해야 합니다.
  - 읽는 경로 검사는 이제 경고가 아니라 기동 실패입니다.
  - 옛 형식 파일이나 키가 남아 있으면 안내 문구와 함께 기동이 멈춥니다.

**확인한 것**
- 기동: Agent 28개, Workflow 20개, 경고 0건
- LLM 없이 실제 실행: tool chain, forEach(잘못된 입력 400), 재시도 루프, 파일 편집 보고서, MCP, 승인 대기 → 의견 없는 반려 400 → 승인 후 완료
- LLM 호출 2건: 구조화 출력이 정상으로 나왔습니다.
- 일부러 틀리게 쓴 정의 11가지: 모두 의도한 안내 문구로 기동이 막혔습니다.

**직접 고친 정의 두 개**
- **`sample-loop-retry-until-valid`:** 하위 필드 저장(`input.sql`)이 없어져서, 받은 입력 전체를 `checkedInput`에 저장하고 `${state.checkedInput.sql}`로 읽게 바꿨습니다.
- **`sm-workflow`:** ROUTER 결과를 `classify.route` 자리가 아니라 `classify`에 통째로 저장하게 바꿨습니다. 그래서 `return`이 `{route, reason}` 객체가 아니라 고른 경로 이름만 돌려줍니다. 원래 뜻이 이쪽이라고 판단했는데, 객체가 필요하셨다면 말씀해 주세요.

**남은 일**
- `docs/09.dstone-ai-engine.md` 7·8장(필드 설명, 복사해 쓰는 뼈대), `mcp/mcp-yml-guide.md`, `docs/06.dstone-boot.md`의 해당 부분, CLAUDE.md 본문을 새 형식으로 다시 써야 합니다. 지금은 CLAUDE.md 맨 앞에 "형식이 바뀌었고 아래 설명은 옛 형식"이라는 안내만 넣어 두었습니다.
- dstone-boot의 `workflow.js`와 `ChatService.java`에 옛 키 이름이 문구로만 남아 있는지 확인해야 합니다. 동작에 영향이 있는지는 아직 보지 못했습니다.
- `pilot-workflow`는 기동 검증만 했고, LLM으로 처음부터 끝까지는 돌려 보지 않았습니다.

# localcommandcaveatTh

> 생성일시: 2026-10-18 09:16

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

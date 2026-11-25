# localcommandcaveatTh

> 생성일시: 2026-11-25 15:39

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="9483">
dstone-ai-engine 에서 아래의 내용 확인해줘.
<확인 요청>
1. sm-auto-fix-agent 에 requiredWrites 가 03-1.design.md 로 잡혀있어. 아래의 내용 확인해줘.
    - 이게 의도된 설정인가?
    - execution.requiredWrites 가 양식모드에서 보이지 않고 편집도 안되. 편집가능해야 하지 않아?
2. 아직도 이해되지 않는 부분이 격리서버를 여러번 올렸다 내렸다 하는 부분인데 정확한 사유가 뭐야?
</pasted_content id="9483">

---

## 답변

requiredWrites는 오늘 14:31 커밋에서 이미 빠져 있습니다. 왜 들어갔는지 이력과 디자이너 양식 모드를 이어서 확인하고 있습니다.

---

## 답변

1번은 정의 이력을 다 봤고, 이제 2번(격리 서버를 여러 번 올리고 내리는 이유)을 Workflow와 `TestServerTool` 코드에서 확인합니다.

---

## 답변

Now the Tool itself — does each call rebuild/clone?

---

## 답변

구조는 파악했습니다. 실제 최근 실행 로그에서 서버가 몇 번, 왜 올라가고 내려갔는지 확인하겠습니다.

---

## 답변

오늘 `sm-auto-fix-agent`가 requiredWrites 위반으로 두 번 실패한 기록이 있습니다. 그때 무엇을 답했는지 확인합니다.

---

## 답변

## 1. `sm-auto-fix-agent`의 `requiredWrites`

**의도한 설정이 맞습니다.** 11-23 커밋 `d186e03d`에서 Agent를 처음 만들 때 넣었습니다. 프롬프트에 다음 규칙이 있는데, 이걸 엔진이 강제하려는 장치입니다.

> 고친 것은 03-1.design.md에도 같은 before/after로 고치고 '## 수정 이력'에 한 줄을 남긴다. (…) 고칠 것이 없다고 판단했어도 그 한 줄은 남긴다.

설계를 다시 하면 개발 폴더를 비우고 설계서대로 새로 만듭니다. 그래서 설계서에 적히지 않은 자동 수정은 그때 사라집니다.

확인하면서 알게 된 점이 있습니다.
- **지금 파일에는 `requiredWrites`가 없습니다.** 오늘 14:31 커밋 `acac55ad`(메시지 ".")에서 두 줄이 빠졌습니다. 이 저장소와 D: 클론 모두 마찬가지입니다.
- 오늘 Windows 로그에는 이 계약에 걸린 실패가 2건 있습니다(작업 폴더 `20261124-1652`, `20261125-1332`). 13:59 실패를 보면 Agent는 LLM을 20번 불렀지만 쓰는 Tool을 한 번도 부르지 않았습니다(`쓴 파일 = []`). 고칠 것도 없고 수정 이력 한 줄도 안 남긴 채 끝난 것이라, 계약이 막도록 만든 경우가 정확히 이것입니다. 이 실패로 step은 개발 승인(step19)으로 넘어갔습니다.
- 계약을 빼면 아무것도 안 고친 자동 수정도 성공으로 처리됩니다. 그러면 바로 컴파일 → 격리 기동 → 동작 검증을 같은 코드로 한 번 더 돕니다. 2번에서 설명할 '서버를 다시 띄우는 일'이 하나 더 생기는 셈입니다.

그래서 저는 되돌려 두기를 권합니다. 다만 빼신 데에 이유가 있었다면 그 이유부터 알려 주세요.

**양식 모드에 안 보이는 문제:** 말씀하신 대로 고쳐야 할 빈틈입니다. `workflow-designer.js:4017-4018`의 양식에는 `execution` 아래 `maxToolCalls`·`requiredTools`까지만 있고, `requiredWrites`와 `toolPrerequisites`는 칸이 없습니다. 양식에서 저장해도 모르는 키는 그대로 남기므로(`orderKeys`) 값이 사라지지는 않습니다. 보고 고칠 수 없을 뿐입니다. 두 칸을 추가하는 걸 제안합니다. 각각 쉼표로 구분한 목록과 `Tool: [선행 Tool…]` 모양입니다.

## 2. 격리 서버를 여러 번 띄웠다 내리는 이유

14:50~15:28 실행 로그를 시간순으로 보면, 실제 기동(복제 DB + clean 빌드 + 기동, 한 번에 약 85~100초)이 **7번** 있었습니다.

| # | 시각 | 누가 | 왜 새로 띄웠나 |
|---|---|---|---|
| 1 | 14:50 | step29 격리 기동 확인 | 첫 기동. 동작 검증(step35)은 이 서버를 그대로 이어서 씀 |
| 2 | 14:54 | step34 자동 수정 | 고치기 전 재현. 그 직전 14:53에 검증 Agent가 `stopTestServer`로 서버를 이미 내렸음 |
| 3 | 14:56 | step29 | 자동 수정이 파일을 고쳐서 코드가 바뀜 |
| 4 | 15:02 | step29 | 두 번째 자동 수정 뒤라 코드가 바뀜 |
| 5 | 15:15 | step24 전문가 해결 | 고치기 전 재현(프롬프트 규칙) |
| 6 | 15:19 | step24 | 고친 뒤 확인(프롬프트 규칙) |
| 7 | 15:23 | step29 | 6번과 같은 코드인데 다시 띄움 |

정리하면 이유는 세 가지입니다.

1. **코드가 바뀌면 반드시 다시 띄워야 합니다(#3, #4, #6).** 서버는 그 시점의 `dev-resources`를 복사해서 빌드합니다. 입력 파일의 크기·수정 시각(fingerprint)이 달라지면 이전 서버를 쓸 수 없습니다. 지난번에 남은 class 파일이 문제를 일으킨 적이 있어서 빌드도 매번 clean으로 합니다.
2. **서버는 '바로 다음 step 하나'에만 넘겨집니다(#7).** `TestServerTool.stepEnded`는 서버를 띄운 step이 끝나면 다음 step 한 번까지만 살려 두고, 그 step이 쓰지 않으면 내립니다. 전문가 해결(step24)과 step29 사이에는 컴파일·형식 검사 같은 TOOL step이 6개 있습니다. 그래서 입력이 같아도 서버가 이미 내려가 있습니다. 승인 대기로 실행이 엔진 루프를 벗어날 때도 서버를 모두 내립니다.
3. **Agent가 직접 서버를 내립니다(#2).** 검증 Agent가 끝날 때 `stopTestServer`를 불렀고, 바로 뒤 자동 수정 Agent가 같은 입력으로 서버를 다시 띄웠습니다.

**줄일 수 있는 것은 #2와 #7, 약 3분입니다.** 나머지는 코드가 바뀌었거나 프롬프트가 '고치기 전 재현, 고친 뒤 확인'을 요구해서 생긴 기동입니다.

줄이는 방법은 두 가지가 있습니다.
- (a) 입력이 같으면 TOOL step 사이에서도 서버를 이어 쓰게 합니다. 범위는 '다음 step 하나'에서 '다음 AGENT/TOOL이 다시 부를 때까지'로 넓힙니다.
- (b) Agent 프롬프트에서 `stopTestServer` 지시를 빼고, 서버를 내리는 일은 엔진에 맡깁니다.

한 가지 득실이 있습니다. 이어 쓴 서버에는 앞 Agent가 화면에서 등록·삭제한 데이터가 복제 DB에 남아 있습니다. 검증(step35)은 깨끗한 복제본에서 하는 편이 낫다면, #7은 "앱은 이어 쓰고 DB 복제본만 새로 만드는" 방식이 따로 필요합니다.

1번의 `requiredWrites` 되돌리기, 양식 칸 추가, 2번의 (a)/(b) 가운데 어떤 것을 진행할지 알려 주세요. 아직 아무 파일도 고치지 않았습니다.

---

## 질문

일단 1번만 진행해줘. 그리고 화면양식에서도 조회/수정이 가능해야해.

---

## 답변

Now the edits: restore the agent definition, then add the two form fields.

---

## 답변

Now the read/parse side.

---

## 답변

JS 문법 검사는 통과했습니다. 새 함수가 원래 값으로 되돌아오는지 node로 확인합니다.

---

## 답변

Docs: add a note that the designer form now edits these two keys.

---

## 답변

1번 작업은 끝났고, 디자이너 양식에서도 두 항목을 보고 고칠 수 있게 했습니다. 단, 화면에서 직접 눌러 보지는 않았고 커밋도 하지 않았습니다.

**1. `sm-auto-fix-agent`에 `requiredWrites` 되돌림** (`dstone-ai-engine/conf/definitions/agents/sm/sm-auto-fix-agent.md`)
- 14:31 커밋에서 빠졌던 `requiredWrites: [${input.base_dir}/03-1.design.md]`를 다시 넣었습니다.
- 맨 위 주석에 이 계약을 두는 이유를 적었습니다. 빼면 아무것도 쓰지 않은 답이 성공으로 처리되고, 같은 코드로 격리 기동과 동작 검증을 한 번 더 돕니다. 오늘 이런 호출이 2건 있었습니다.
- 계약 자체는 원래대로 돌린 것이라 버전 번호는 올리지 않았습니다.

**2. 디자이너 Agent [양식]의 '실행' 칸에 두 항목 추가** (`workflow-designer.js`)
- `execution.requiredWrites`: 한 줄에 표현식 하나씩 적습니다. 예: `${input.base_dir}/03-1.design.md`
- `execution.toolPrerequisites`: 한 줄에 `선행 Tool: 막을 Tool, …` 모양으로 적습니다. 예: `startSandboxServer: dbQuery, browser_*`
  - 모양이 틀린 줄이 있으면 몇 번째 줄인지 알려 주고 저장하지 않습니다.
- 칸을 비우면 그 키를 지웁니다. 다시 만드는 머리말에서는 `requiredWrites`를 `toolPrerequisites` 다음에 둡니다.

**확인한 것**
- JS 문법 검사를 통과했습니다.
- 새 함수만 따로 꺼내 node로 돌려 봤습니다. 양식 글자로 바꿨다가 다시 읽으면 원래 값과 같게 돌아오고, 틀린 줄에는 오류 문구가 나옵니다.
- 화면에서 실제로 눌러 보는 확인은 하지 않았습니다.
- 같은 내용의 메모를 `.claude/docs/ai-engine-features.md`에도 남겼습니다.

**반영하려면**
- Windows 쪽 엔진은 D: 클론을 읽으므로, git으로 동기화해야 반영됩니다.
- 화면 JS는 jar 안의 정적 리소스라서 다시 빌드하고 재시작해야 바뀝니다.
- Agent 정의 파일(`conf/definitions`)은 정의를 다시 읽거나 재시작하면 반영됩니다.

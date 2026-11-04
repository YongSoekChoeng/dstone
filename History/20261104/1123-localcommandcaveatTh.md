# localcommandcaveatTh

> 생성일시: 2026-11-04 11:23

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="d5ef">
Window 에서 workflowId(sm-workflow)를 진행했어.
executionId : 6e02b73d-4f28-40c6-b76b-1d9ce272ecab
그런데 step13 에서 "Tool 호출 한도를 모두 사용했습니다. 작업 결과를 정리하겠습니다..." 이렇게 오류가 났어.
Tool 호출경로에 대한 문제를 분석해줘.
</pasted_content id="d5ef">

---

## 답변

지금 Windows 쪽 `execution.log`에서 step13(`sm-developer-agent`)의 Tool 호출 기록을 차례로 따라가며 반복 원인을 찾고 있습니다.

---

## 답변

Now the `nearestText` helper — what the model sees on a miss.

---

## 답변

step13이 멈춘 이유는 Tool 호출 경로(엔진 → `ConfigTool` → `FileTool`) 고장이 아닙니다. `replaceInFile` 한 번이 계속 실패했고, 모델이 같은 인자로 87번 다시 불러 Tool 한도 100회를 다 쓴 것입니다.

## 확인한 것

Windows 쪽 `D:/AppHome/framework/dstone/LOGS/dstone-ai-engine/execution/execution.log`의 step13 구간(10:55:37 ~ 10:59:02)을 봤습니다.

```
합계 - LLM 92회, Tool {copyFileWithReplace=3, readFileLines=10, replaceInFile=87}, 입력 4,860,388토큰, 210초
```

| 회차 | 한 일 |
|---|---|
| 1~2 | `readFileLines`로 원본 9개 파일 읽기 (정상) |
| 3 | `copyFileWithReplace`로 `dev-resources/`에 3개 파일 복사 (정상) |
| 4~5 | `logs-mapper.xml`에 `replaceInFile` → 실패 |
| 6 | `readFileLines` 84~88줄 다시 읽음 |
| 7~91 | **똑같은 `replaceInFile`을 85번 반복.** 11회차부터는 매번 출력이 정확히 310토큰이라 같은 응답을 되풀이한 것으로 보입니다 |
| 92 | 한도를 다 써서 "작업 결과를 정리하겠습니다…"라는 요약을 답함 |

`dev-resources/.../logs-mapper.xml`은 원본과 바이트 단위로 같습니다. 바뀐 파일이 하나도 없고, `03-2.taskitems.md`에도 결과가 기록되지 않았습니다.

## 왜 실패했나: 탭 개수

실제 파일(CRLF, 탭 들여쓰기)의 86번째 줄 근처는 이렇습니다.

```
␉␉␉SELECT tb1.mb_id         /* 회사코드 VARCHAR(20)*/     ← 탭 3개
␉␉␉␉ , tb1.emp_cd        /* 사원코드 VARCHAR(20)*/     ← 탭 4개 + 공백
```

모델이 보낸 텍스트는 `"\t\t\tSELECT …\n\t\t\t , tb1.mb_id …"`처럼 이어지는 줄을 **탭 3개**로 적었습니다. 로그에는 인자가 앞부분만 남아서 `newText`로 확인했고, `oldText`도 같은 모양이었다고 봅니다. 그래서 일치하는 곳이 없었습니다.

`replaceInFile`은 못 찾으면 `nearestText()`로 파일의 실제 글자(탭 4개)를 답에 붙여 주고 "같은 oldText로 다시 부르지 마세요"라고 안내합니다. 그런데 모델은 탭 3개와 4개를 구별하지 못한 채 같은 인자를 계속 보냈습니다. 이 증상은 CLAUDE.md의 2026-10 `pilot` 신규개발 경로 측정 기록에 있는 것과 같습니다("Tool이 실제 글자를 알려 줘도 실패한 oldText를 그대로 다시 보냄"). 그때도 이 때문에 `replaceInFile` 루프를 버리고 줄 번호 편집(`editFileLines`)으로 옮겼습니다.

참고로 설계서의 '변경 전'은 `SELECT tb1.mb_id         /* 회사코드 VARCHAR(20)*/` 한 줄뿐이고, 이 줄은 파일에 한 번만 나옵니다. 모델이 그 한 줄만 `oldText`로 썼다면 성공했을 겁니다. 모델이 다음 줄까지 들여쓰기째로 옮겨 적으려다 틀렸습니다.

## 엔진 쪽에서 막지 못한 점

1. **같은 호출을 반복해도 막는 장치가 없습니다.** `ToolCallBudgetCallback`은 횟수만 세기 때문에 100회에 닿을 때까지 그대로 진행됐습니다. 그 사이 남은 9개 태스크(DDL, seed, FAQ 새 파일 6개)는 손도 대지 못했습니다.
2. **`replaceInFile`이 공백까지 정확히 같아야만 찾습니다.** 줄바꿈 방식(CRLF/LF) 차이만 맞춰 주고 들여쓰기 차이는 맞춰 주지 않습니다.
3. **바꾼 파일이 없어도 step13은 성공입니다.** 요약은 "완료 0건 / 보류 12건"인데, 출력 형식이 없는 텍스트 Agent라서 `StepOutcome.success=true`가 됐고 실행 전체가 `DONE`으로 끝났습니다. 그래서 화면에서는 오류처럼 보이는데 상태는 정상 종료입니다.

## 고치는 방향 (추천 순서)

1. **같은 인자로 실패한 Tool을 다시 부르면 끊습니다.** `ToolCallBudgetCallback`에서 (Tool 이름, 인자) 쌍이 직전에 `실패:`로 끝났는데 그대로 다시 오면 실행하지 않습니다. 대신 "같은 실패 호출이다, 이 태스크는 보류로 두고 다음으로 넘어가라"고 답합니다. 이런 일이 3번 쌓이면 `AgentContractException`으로 step을 실패시킵니다. 어느 Agent에서나 효과가 있고 범위가 좁아서 가장 먼저 할 일로 봅니다.
2. **`replaceInFile`(`editFileTexts`도 함께)에 들여쓰기를 무시하는 2차 매칭을 넣습니다.** 정확히 찾지 못하면 각 줄의 앞 공백을 뺀 채로 다시 찾습니다. 그렇게 해서 딱 한 곳만 나오면 파일의 실제 범위를 바꾸고, `newText`의 들여쓰기는 그 자리에 맞춥니다. 이번 실패는 이것만으로도 성공했을 겁니다.
3. **개발 Agent 프롬프트를 고칩니다.** "`oldText`는 설계서의 '변경 전'을 그대로 쓰고, 줄을 끼워 넣을 때도 기준 줄 하나만 쓴다"와 "같은 태스크에서 `replaceInFile`이 두 번 실패하면 보류"를 넣습니다. 지금은 한 번 더 시도하라고만 되어 있고, 횟수 상한이 없습니다.
4. 더 근본적인 방법은 신규개발 경로처럼 "LLM은 줄 번호 편집만 돌려주고 적용은 TOOL step의 `editFileLines`가 한다"로 바꾸는 것입니다. 다만 sm-workflow 구조가 바뀌므로 별도로 정할 일입니다.

원하시면 1번과 2번부터 구현하겠습니다.

---

## 질문

원인 설명을 제대로 이해 못하겠어

---

## 답변

쉽게 다시 말씀드리면, **AI가 파일 속 글자를 똑같이 베껴 쓰지 못해서 같은 실수를 87번 되풀이한 것**입니다.

## 1. `replaceInFile`이 하는 일

"파일에서 **이 글자**를 찾아서 **저 글자**로 바꿔라"는 Tool입니다. 찾을 글자(`oldText`)는 파일에 있는 것과 **띄어쓰기 하나까지 똑같아야** 찾을 수 있습니다.

## 2. 파일에 실제로 있던 글자

`logs-mapper.xml` 86~87번째 줄입니다. `→`가 탭 한 칸입니다.

```
→→→SELECT tb1.mb_id ...        ← 탭 3칸
→→→→ , tb1.emp_cd ...          ← 탭 4칸
```

## 3. AI가 찾으라고 보낸 글자

```
→→→SELECT tb1.mb_id ...        ← 탭 3칸 (맞음)
→→→ , tb1.emp_cd ...           ← 탭 3칸 (틀림, 실제는 4칸)
```

두 번째 줄의 탭이 **한 칸 모자랍니다**. 그래서 Tool은 "그런 글자는 파일에 없다"고 답했습니다.

## 4. 그다음에 생긴 일

Tool은 실패를 알리면서 친절하게 덧붙였습니다.
> "파일에는 실제로 이렇게 적혀 있다(탭 4칸). 이걸 보고 다시 써라. 같은 걸 다시 보내지 마라."

그런데 AI는 탭 3칸과 4칸의 차이를 알아보지 못했습니다. 그래서 **같은 틀린 글자를 그대로 또 보냈습니다.** 이 일이 87번 반복됐습니다.

## 5. 결과

- 이 Agent는 Tool을 최대 100번까지 쓸 수 있습니다.
- 파일 읽기와 복사에 13번, 이 실패 반복에 87번을 써서 한도 100번이 다 찼습니다.
- 한도가 끝나 남은 일(DDL 추가, FAQ 파일 6개 만들기 등)은 시작도 못 했습니다.
- 마지막에 AI가 "한도를 다 썼습니다, 0건 완료"라고 요약하고 끝냈습니다.
- 그래서 **실제로 바뀐 파일은 하나도 없습니다.**

## 한 줄로 정리하면

| 누구 탓 | 내용 |
|---|---|
| AI(모델) | 탭 개수를 잘못 베꼈고, 정답을 알려 줘도 고치지 못했습니다 |
| 엔진 | **같은 실패를 계속 반복하는데도 멈추지 않고** 한도가 끝날 때까지 놔뒀습니다 |

그래서 앞서 드린 해결책도 이 두 가지를 겨냥합니다.
- **같은 실패를 반복하면 엔진이 바로 끊기**: 남은 Tool 한도로 다른 일을 계속할 수 있습니다.
- **탭이나 공백 개수가 달라도 찾아 주기**: 이번 같은 실수는 처음부터 성공했을 겁니다.

---

## 질문

4. 더 근본적인 방법은 신규개발 경로처럼 "LLM은 줄 번호 편집만 돌려주고 적용은 TOOL step의 editFileLines가 한다"로 바꾸는
     것입니다. 다만 sm-workflow 구조가 바뀌므로 별도로 정할 일입니다. ==>> 이것은 step의 갯수가 늘어난다는 말인가?

---

## 답변

네, step 수가 늘어납니다. 지금은 step13 하나가 하는 일을 여러 step이 나눠 맡게 됩니다.

## 지금 (step13 한 개)

```
step13 개발 (AGENT)
  └ AI 한 명이 Tool을 100번까지 쓰면서 전부 함
    (파일 읽기 → 복사 → 고치기 → 실패하면 다시 → 결과 기록)
```

일을 다 맡기니 정의는 간단합니다. 대신 이번처럼 한 곳에서 막히면 나머지 일도 같이 못 합니다.

## 바꾸면 (`pilot-workflow` 신규개발 경로를 본뜬 모양)

| step | 종류 | 하는 일 | AI 사용 |
|---|---|---|---|
| ① | TOOL | 대상 파일을 `dev-resources/`로 복사 (`copyFileWithReplace`, 파일마다) | 없음 |
| ② | TOOL | 고칠 파일을 줄 번호를 붙여 읽음 (`readItemFiles`) | 없음 |
| ③ | AGENT | 파일 하나씩 "몇 번째 줄을 어떻게 바꿀지" 목록만 답함. Tool은 쓰지 않음 | 파일마다 1번 |
| ④ | TOOL | ③의 목록대로 실제로 고침 (`editFileLines`) | 없음 |
| ⑤ | AGENT | 결과를 `03-2.taskitems.md`에 기록 | 1번 |

step13 하나가 4~5개로 늘어납니다. 새 파일 만들기와 끝에 덧붙이기를 따로 나누면 더 늘어납니다. `pilot-workflow`에서는 이 부분이 step23~step32로, 10개였습니다.

## 좋아지는 점

- AI는 탭을 베껴 쓸 필요 없이 "86번째 줄 뒤에 이 줄을 넣어라"라고만 답하면 됩니다. 이번 같은 실패가 나기 어렵습니다.
- 파일을 실제로 고치는 건 Java Tool이라 반복이나 헛돎이 없습니다.
- 한 파일이 실패해도 나머지 파일은 따로 진행됩니다.
- `pilot`에서 같은 방식으로 바꿨을 때 측정한 결과, 파일 하나에 1~27초가 걸렸습니다. Tool로 고치게 했을 때는 몇 분이 걸리거나 한도에 걸렸습니다.

## 대신 해야 할 일 (이게 "별도로 정할 일"인 이유)

step 수보다 **앞 단계가 내놓는 결과물 모양**이 더 큰 변화입니다.

- 지금 설계 Agent(step08)는 사람이 읽는 **md 문서**만 씁니다.
- 위의 ①~④를 하려면 "어느 파일을, 무엇을 참고해, 새로 만드는지 고치는지"가 **JSON 목록**으로 `state`에 있어야 합니다. TOOL step은 md 문서를 읽고 판단할 수 없기 때문입니다.
- 그래서 설계 Agent에 출력 형식(JSON)을 추가하거나, md 문서에서 목록을 뽑는 step을 하나 더 두어야 합니다.

즉 바뀌는 범위가 step13 하나가 아니라 **설계 단계(step08)부터 개발 끝까지**입니다.

그래서 순서는 이렇게 권합니다. 먼저 작은 수정 두 가지(같은 실패 반복 차단, 탭·공백 차이 허용)를 넣고 sm-workflow를 다시 돌려 봅니다. 그래도 개발 단계가 불안정하면 그때 이 구조 변경을 진행합니다.

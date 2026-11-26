# localcommandcaveatTh

> 생성일시: 2026-11-26 09:04

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="d6df">
dstone-ai-engine 에서 아래의 내용 확인해줘.

<확인 요청>
Windows 에서 진행.
실행 ID : d9f0bcc0-7f94-44aa-8a7a-37a4a9a70f4d
내용 :
반복 호출: agent[sm-auto-fix-agent]가 [Skill]를 같은 인자로 5번 연달아 불렀고 결과도 매번 같아서 중단했습니다. 인자: {"command": "gnx-crud-screen"}
</pasted_content id="d6df">

---

## 답변

조사를 시작합니다. 먼저 반복 호출 감지 코드와 `gnx-crud-screen` Skill 정의를 찾겠습니다.

---

## 답변

Skill 반복 호출 경로를 코드로 따라가 원인 가설을 세웠습니다. 값으로 확인하겠습니다.

---

## 답변

`sm-auto-fix-agent`가 `Skill` Tool을 `gnx-crud-screen` 인자로 5번 연달아 불렀고, 매번 결과가 같았다는 건 코드 분석으로 확인했습니다. 다만 5번 모두 결과가 같았던 이유는 가설입니다. Windows 쪽 로그와 실행 이력(`d9f0bcc0`)은 이 WSL 저장소에 없어서 실제 기록으로는 확인하지 못했습니다.

## 확인한 것

- 중단 문구는 `RepeatCallGuardCallback.java:127`에서 나옵니다. 같은 Tool을 같은 인자로 불렀고 결과까지 같으면 센다는 장치입니다. 5번째(`fail-at`)에 끊습니다.
- `Skill` Tool(`SkillTool.java`)은 같은 Skill을 두 번째 부르면 본문 대신 짧은 안내를 돌려줍니다. 이 경우 5번 연속이 되려면 6번 불러야 합니다. 그런데 오류는 5번이라고 했으니, 호출마다 Skill 본문이 통째로 돌아왔다는 뜻입니다. `ReadHistory.skills` 장부가 매번 비어 있었다는 얘기입니다.

## 의심하는 원인

이 장부를 비우는 곳이 `ToolResultCompactionAdvisor.java:97`입니다. 대화에 쌓인 Tool 결과가 `keep-result-chars`(기본 200000자)를 넘으면 오래된 결과를 앞 300자만 남기고 줄입니다. 이때 `readHistory.clear()`가 읽은 파일 기록과 받은 Skill 기록을 모두 지웁니다.

추정되는 흐름은 이렇습니다.

1. `sm-auto-fix-agent`는 시험 서버 로그, 브라우저 스냅숏, 네트워크 요청 같은 큰 결과를 많이 받습니다. 그래서 압축이 계속 일어납니다.
2. 8,365자짜리 `gnx-crud-screen` 본문은 압축 기준(600자)을 넘어, 가장 먼저 300자로 줄어듭니다. 줄인 자리에는 "같은 Tool을 다시 부르십시오"라는 문구가 붙습니다.
3. 모델이 그 문구대로 `Skill`을 다시 부릅니다. 장부가 비어 있으니 본문 전체가 돌아옵니다.
4. 다음 호출에서 또 압축되면서 같은 일이 되풀이됩니다. 결과가 매번 같아서 반복 호출로 끊깁니다.

2026-11-24에 30번 반복했을 때 만든 장치끼리, 그러니까 압축 안내문과 중복 호출 방지가 서로 부딪히는 것으로 보입니다.

이 가설이 맞는지 확인하려면 Windows 쪽 `execution.log`에서 `d9f0bcc0` 실행 시점을 찾아 아래 두 가지를 보면 됩니다.

- `대화에 쌓인 Tool 결과가 …자라서 오래된 것부터 줄였습니다`가 Skill 호출 사이사이에 찍혔는지
- `이미 받은 Skill '…'을 또 불러서 본문 대신 안내만 돌려줬습니다`가 한 번도 없었는지

## 제안하는 수정

원인이 맞다면 `ToolResultCompactionAdvisor`가 `readHistory.clear()`로 전부 지우는 대신, 줄인 결과에 해당하는 항목만 지우게 하는 방향이 맞습니다. Skill 본문은 줄여도 앞부분이 남으니, 모델이 필요하면 `readFile`로 SKILL.md를 다시 읽게 안내하는 방법도 있습니다.

로그를 확인한 뒤에 고칠지, 바로 고칠지 알려 주세요.

---

## 질문

일단 이 호출은 windows에서 호출했고 로그를 확인해봐.

---

## 답변

Windows 로그(`D:/AppHome/framework/dstone/LOGS/dstone-ai-engine/execution/execution.log` 47587~48110행, 실행 `d9f0bcc0`, step34)를 읽기만 해서 확인했습니다. 앞서 세운 압축·장부 초기화 가설은 틀렸습니다.

## 실제로 일어난 일

- **압축은 없었습니다.** 같은 구간에 `오래된 것부터 줄였습니다` 로그가 0건이고, 입력 토큰도 44k에서 46.7k로 호출마다 100토큰 안팎만 늘었습니다. Skill 본문이 다시 실린 일은 없었습니다.
- **Skill 중복 방지는 정상이었습니다.** 23회차에 `gnx-crud-screen` 본문을 한 번 받았습니다. 27회차부터 31회차까지 같은 Skill을 5번 불렀고, 매번 `이미 받았다, 다시 부르지 마십시오`라는 같은 안내가 돌아왔습니다. 이 같은 안내 5번이 `RepeatCallGuardCallback`에 반복으로 잡혀 중단됐습니다.
- **일은 21회차에 끝나 있었습니다.** 21회차까지 시험 DB의 `tbts_image_board` 구조를 고쳤고(`seq`에 `PRIMARY KEY`와 `AUTO_INCREMENT` 추가), 설계서와 DDL을 수정했고, 시험 서버를 다시 띄워 구조를 확인하고 내렸습니다. 그 뒤로 모델은 최종 답(`causes`/`changes`/`unresolved`)을 내지 않고 계속 Tool을 불렀습니다.
  - 22회차: 이미 내린 서버에 `stopTestServer`를 한 번 더 불렀습니다.
  - 23회차: `Skill gnx-crud-screen`
  - 24회차: `stopTestServer`
  - 25~26회차: `Skill troubleshooting`, `Skill test-environment`. 둘 다 `쓸 수 있는 Skill = [gnx-crud-screen]`이라는 실패가 돌아왔습니다.
  - 27~31회차: `gnx-crud-screen` 안내를 5번 연속 받고 중단됐습니다.

## 원인

1. **프롬프트의 "Skill troubleshooting을 따른다"가 모델을 Skill Tool로 이끕니다.** 이 두 Skill은 `skills.preload`라서 프롬프트 뒤에 이미 붙어 있고 Skill Tool 목록에는 없습니다. `sm-auto-fix-agent.md`는 "Skill troubleshooting", "Skill test-environment"라고만 적어서, 모델이 Tool로 불러야 하는 줄 알고 실패합니다. 이 실패가 Skill Tool 반복의 시작입니다.
2. **모델이 마무리를 못 합니다.** 일을 다 한 뒤 최종 답을 내야 하는데 Tool만 부릅니다. Skill 안내문이 "최종 답을 내십시오"라고 두 번 말했는데도 따르지 않았습니다. 이번 반복은 이 증상이 Skill 호출로 나타난 것입니다. 이전 실행 `9f14f909`의 30번 반복도 같은 모양입니다.
3. **중단 문구가 원인을 못 알려 줍니다.** "Skill를 5번 불렀다"만 보이고, 실제 일이 끝난 상태였다는 점이 드러나지 않습니다.

## 제안

- `sm-auto-fix-agent.md`의 "Skill troubleshooting을 따른다", "Skill test-environment로 보아"를 "아래에 붙은 troubleshooting 안내를 따른다"처럼 고칩니다. `skills.preload` 대상은 Tool로 부르지 않는다고 한 줄 적습니다. 다른 Agent 정의에도 같은 문구가 있는지 같이 보겠습니다.
- `RepeatCallGuardCallback`이 중단할 때 지금까지 쓴 파일과 마지막 정상 Tool 호출을 오류에 함께 남깁니다. 그러면 일이 끝났는지가 한눈에 보입니다. (선택)
- 일을 다 하고도 최종 답을 안 내는 문제는 `requiredWrites` 같은 계약이 이미 있지만 이번에는 막지 못했습니다. 반복 호출로 끊긴 호출을 한 번 재시도하며 "최종 답만 내라"고 안내하는 방안은 설계가 더 필요합니다.

첫 번째 항목(프롬프트 문구 정리)부터 고칠까요?

---

## 질문

첫 번째부터 고쳐줘

---

## 답변

프롬프트 문구를 고쳤습니다. 이 문구들이 정의 파일 검증을 통과하는지는 엔진을 띄워 확인하지 않았습니다.

preload Skill은 프롬프트 뒤에 `[Skill: 이름]`으로 이미 붙어 있고 Skill Tool 목록에는 없습니다. 그래서 "Skill X를 따른다"를 "아래에 붙은 [Skill: X] 안내를 따른다"로 바꿨습니다. 6개 Agent 파일이 대상이고, 해당 Skill이 각 Agent의 `preload`에 들어 있는 것도 확인했습니다.

| Agent | 고친 곳 |
|---|---|
| `sm-auto-fix-agent` | `test-environment`, `troubleshooting` 두 곳. 버전을 1.3.1로 올리고 이유를 주석에 남겼습니다. |
| `sm-verifier-agent` | `test-environment` |
| `sm-troubleshooter-agent` | `troubleshooting`, `source-investigation` |
| `sm-architect-agent`, `sm-analyze-impact-agent` | `source-investigation` |
| `document-revise-agent` | `source-investigation` |

`sm-auto-fix-agent`만 버전을 올렸고 나머지 다섯은 문구만 바꿨습니다. 정의 파일은 무재시작 편집이 되는지 확인하지 못했으니, 이미 떠 있는 엔진에는 반영 여부를 확인해 주세요. 변경은 커밋하지 않았습니다.

이번 고침으로 `troubleshooting`, `test-environment`를 Tool로 부르는 실패는 줄어들 것으로 봅니다. 하지만 일을 끝내고도 최종 답을 안 내는 문제는 그대로입니다. 그 부분은 아직 손대지 않았습니다.

---

## 질문

그 부분은 어떻게 진행해야 해?

---

## 답변

권하는 순서는 "원인 확인 → 가드 보강 → 필요하면 재시도"입니다.

## 1. 모델이 왜 마무리를 못 했는지부터 확인하기
지금 로그에는 모델이 마지막에 무슨 말을 했는지가 없어서, 원인이 둘 중 무엇인지 모릅니다.
- **(a) 모델 문제**: qwen이 "다 했다"는 판단을 못 하고 습관적으로 Tool을 부릅니다. 이 경우 22회차 이후 stopTestServer와 Skill을 되풀이한 모양과 맞습니다.
- **(b) 답 형식 문제**: `causes`/`changes`/`unresolved`로 최종 답을 내려다 실패하거나 거부당하고, 그때마다 Tool로 도망갑니다.

확인은 간단합니다. 반복에 걸렸을 때 마지막 몇 회차의 모델 출력(본문 텍스트와 Tool 요청)을 `execution.log`나 `llm/` 로그에 남기면 됩니다. 지금은 REQ 로그가 호출의 첫 요청만 남아서 이 부분이 안 보입니다. 다음 번에 같은 일이 나면 바로 판별되도록 로깅을 먼저 넣는 걸 권합니다.

## 2. 가드가 끊을 때 상태를 오류에 남기기
`RepeatCallGuardCallback`이 중단할 때, 그 호출에서 쓴 파일과 마지막으로 성공한 Tool 이름을 오류 문구에 붙입니다. 사람이 "일은 끝났는데 답만 못 냈다"를 바로 알아봅니다. `AgentExecutor`가 `writtenPaths`와 `succeededTools`를 이미 갖고 있어서 변경이 작습니다.

## 3. 끝난 일을 버리지 않는 재시도
이번 실행은 21회차까지 DDL과 설계서 수정을 마쳤는데, 전체가 실패 처리되어 그 시간이 버려졌습니다. 반복 호출로 끊기면 한 번만 다시 불러 보는 방법이 있습니다.
- 재시도에서는 Tool을 빼고 "작업은 끝났다. 지금까지 쓴 파일은 이것이다. 최종 답만 내라"고 시킵니다.
- Tool 없이 부르면 반복 자체가 불가능해서 가장 확실합니다.
- 다만 Agent 호출 결과의 의미가 바뀌는 엔진 변경입니다. 모든 Agent에 적용할지, `requiredWrites`가 이미 채워진 경우로 한정할지 정해야 합니다. 한정하는 쪽이 안전합니다. 쓴 파일이 있다는 것은 일이 됐다는 증거이기 때문입니다.

## 제안
1번과 2번은 바로 할 수 있고 위험이 작습니다. 3번은 1번의 결과를 보고, (a)로 확인되면 진행하는 게 좋습니다. (b)라면 재시도가 아니라 답 형식을 고쳐야 하기 때문입니다.

1번과 2번을 지금 진행할까요?

---

## 질문

1번, 2번 진행해줘

---

## 답변

Tests를 추가하고 실행합니다.

---

## 답변

1번과 2번을 모두 반영했고, 관련 테스트는 통과했습니다. 아직 커밋하지는 않았습니다.

**1번: 모델이 한 말을 로그에 남기기** (`AgentCallStatsAdvisor`)
- Tool을 요청한 회차의 줄 끝에 `, 말 [...]`로 모델이 함께 쓴 본문을 300자까지 붙입니다. 추론 글은 들어가지 않습니다.
- 마지막 답(Tool 요청 없음)의 본문은 남기지 않습니다. 결과 내용이 로그에 쌓이는 것을 피하려는 것입니다.
- 다음에 같은 반복이 나면 `execution.log`에서 모델이 무슨 말을 하며 Tool을 부르는지 보입니다. 마무리를 못 하는 이유가 모델 문제인지 답 형식 문제인지 그걸로 가릅니다.

**2번: 중단 오류에 그때까지 한 일 붙이기** (`RepeatCallGuardCallback`, `AgentExecutor`)
- 중단 문구 끝에 ` / 성공한 Tool: [...] / 쓴 파일 N개: [...]`가 붙습니다.
- 이번 실행이었다면 `startSandboxServer`와 `editFileTexts`가 성공했고 `03-1.design.md`, `03-3.ddl(데이터베이스).md`를 썼다는 것이 오류에 바로 나타납니다.
- 성공한 Tool은 기록된 순서가 아니라 이름 목록입니다.

**테스트와 문서**
- 테스트는 `RepeatCallGuardTest`에 케이스 1개, `AgentCallStatsAdvisorTest`에 케이스 1개를 추가했고, 두 클래스 모두 통과했습니다. 전체 테스트는 돌리지 않았습니다.
- `docs/09.dstone-ai-engine.md`의 두 표 행에도 위 내용을 반영했습니다.

실행 중인 엔진에는 Java 변경이 반영되지 않습니다. 재빌드와 재시작이 필요합니다. 이 변경은 가까운 실행 하나에서 반복 호출이 다시 나면 그 로그로 3번 진행 여부를 판단합니다.

---

## 질문

올려줘.

---

## 답변

변경을 커밋하고 `origin/main`에 푸시했습니다. 푸시 뒤 로컬과 원격이 같은 상태입니다.

- 커밋: `ai-engine: preload Skill을 Tool로 부르지 않게 프롬프트 정리, 반복 호출 중단 오류에 진행 상황 추가`
- 포함한 내용: Agent 정의 6개, `RepeatCallGuardCallback`, `AgentCallStatsAdvisor`, `AgentExecutor`, 테스트 2개, `docs/09.dstone-ai-engine.md`

Windows 쪽에서 반영하려면 `git pull`로 받은 뒤 엔진을 다시 빌드해서 띄워야 합니다. 정의 파일만 바뀐 프롬프트 수정은 무재시작 편집이 되는지 확인하지 못했습니다.

---

## 질문

<pasted_content id="d6df">
dstone-ai-engine 에서 아래의 내용 확인해줘.

<확인 요청>
Windows 에서 진행.
실행 ID : d9f0bcc0-7f94-44aa-8a7a-37a4a9a70f4d
내용 :
반복 호출: agent[sm-auto-fix-agent]가 [Skill]를 같은 인자로 5번 연달아 불렀고 결과도 매번 같아서 중단했습니다. 인자: {"command": "gnx-crud-screen"}
</pasted_content id="d6df">

---

## 답변

방금 처리한 것과 같은 요청입니다. 실행 `d9f0bcc0`의 `Skill gnx-crud-screen` 5번 반복 중단은 이미 확인을 끝냈고, 고친 내용을 커밋해 푸시했습니다.

- **원인**: 일은 21회차에 끝났는데 모델이 최종 답을 내지 않고 Tool만 불렀습니다. 프롬프트의 "Skill troubleshooting을 따른다" 같은 문구가 preload Skill을 Tool로 부르게 이끌었고, 그 실패가 Skill 호출 반복으로 이어졌습니다. 압축 때문에 장부가 초기화됐다는 처음 가설은 로그로 틀린 것이 확인됐습니다.
- **반영한 것**: 프롬프트 문구 정리, 중단 오류에 성공한 Tool과 쓴 파일 표시, Tool을 요청한 회차의 모델 말 로깅.
- **남은 것**: 마무리를 못 하는 문제 자체는 그대로입니다. Windows에서 `git pull`과 재빌드 후 같은 실행을 다시 돌려 로그의 `말 [...]`을 보고, 그 결과로 3번(Tool 없이 최종 답만 내게 하는 재시도)을 할지 정하려 했습니다.

새로 확인할 내용이 있어서 다시 보내신 거라면 알려 주세요. 다른 실행 ID이거나 재실행에서 같은 반복이 또 났다면 그 로그를 보고 이어서 보겠습니다.

---

## 질문

음.. 20-개발부터 다시 실행을 진행했어.

---

## 답변

새 로그(Windows 엔진이 새 코드로 떠 있습니다)에서 마무리를 못 하는 이유가 확인됐습니다. 이번에는 `sm-developer-agent`였고 같은 5번 반복으로 중단됐습니다.

## 모델이 한 말

| 회차 | Tool 요청 | 모델의 말 |
|---|---|---|
| 11~13 | `compileJava`, `checkSources`, `readFileLines` | "컴파일 통과", "형식 검사 통과" 등 정상 진행 |
| 14 | `Skill` | "All 5 task items are already recorded… Summary of work: …" |
| 15~19 | `Skill` | "모든 작업이 완료되었습니다. 최종 답을 드립니다. 구현 완료: … compileJava: 통과 …" |

작업은 13회차에 끝났고(7개 파일 작성, 컴파일·형식 검사 통과), 모델은 14회차부터 이미 **최종 답을 본문 텍스트로 쓰고 있습니다.** 그런데 같은 메시지에 쓸모없는 `Skill gnx-crud-screen` Tool 호출을 매번 끼워 넣습니다. Spring AI는 Tool 호출이 있는 응답을 "아직 끝나지 않음"으로 보고 본문을 버린 채 다시 돌립니다. 그래서 답이 두세 번 완성되고도 매번 버려졌고, 반복 가드가 5번째에 끊었습니다.

앞서 세운 두 가설(a: 마무리를 못 하는 모델, b: 답 형식 문제)은 둘 다 정확하지 않았습니다. 모델은 마무리할 줄 알지만, 마무리하면서 불필요한 Skill Tool 호출을 한 번 더 붙입니다. `sm-auto-fix-agent`도 같은 모양일 가능성이 큽니다. 당시에는 말 로깅이 없어서 확인할 수 없었습니다.

앞서 고친 프롬프트 문구는 이 문제와 무관하게 유효합니다. 이번 `sm-developer-agent`에서는 존재하지 않는 Skill을 부르는 실패(`troubleshooting` 등)는 나오지 않았습니다.

## 제안: 3번을 "마지막 말을 답으로 채택"으로 바꿔서 진행

반복 호출로 중단할 때, 직전 라운드의 본문이 충분히 길고(예: 일 끝났다는 요약) 쓴 파일이 있으면 그 본문을 최종 답으로 쓰고, 기존 `reshape` 단계로 output 스키마에 맞춥니다. 이렇게 하면 끝난 일을 버리지 않고, 재호출 비용도 없습니다. 반대로 쓴 파일도 없고 본문도 없으면 지금처럼 실패합니다.

고민할 점이 둘 있습니다.
- Spring AI에서 가드 예외가 나기 전 본문을 어디서 붙잡을지는 코드를 더 봐야 합니다. `AgentCallStatsAdvisor`처럼 Advisor에서 마지막 본문을 기억해 두는 방식이 가장 단순해 보입니다.
- 반복이 아니라 일반 오류에도 적용할지는 정해야 합니다. 이번에는 반복 호출 중단에만 한정하는 것을 권합니다.

이 방향으로 진행할까요?

---

## 질문

지금 진행하는게 솔루션  전체적인 구조를 파악한 후에 진행하는 거야? 조금 주먹구구식으로 진행하는 것 같아.

---

## 답변

아니요, 솔루션 구조를 파악하고 진행한 게 아닙니다. 지적하신 대로 이번 작업은 주먹구구식이었습니다.

- **문서를 읽지 않았습니다.** CLAUDE.md는 Agent 실패를 분석할 때 `ai-engine-agent-principles.md`로 실패를 분류하고, 데이터 흐름은 `ai-engine-dataflow.md`, 기능은 `ai-engine-features.md`를 읽으라고 합니다. 이번에는 하나도 열지 않고 소스 몇 개만 보고 가설을 세웠습니다.
- **가설을 두 번 틀렸습니다.** 처음에는 압축이 장부를 비운다고 했고, 그다음에는 모델이 마무리를 못 한다고 했습니다. 둘 다 로그를 보고서야 아니라는 걸 알았습니다. 결과적으로 이번 커밋의 프롬프트 문구 수정은 이번 증상의 원인이 아니었습니다. 로깅 추가가 오히려 진짜 원인을 보여 줬습니다.
- **엔진의 Tool 호출 루프를 정확히 보지 않았습니다.** Spring AI의 `ToolCallingAdvisor`가 본문과 Tool 호출이 같이 오면 어떻게 처리하는지, `RunawayRetryAdvisor`·`reshape`·`requiredWrites` 같은 기존 장치가 이 경우에 어떻게 맞물리는지 모른 채 "마지막 말을 답으로 채택"을 제안했습니다. 새 장치를 또 얹기 전에 기존 구조에 이미 맞는 자리가 있는지 봐야 합니다.

## 제안: 고치기 전에 먼저 구조를 파악하겠습니다

1. `ai-engine-agent-principles.md`, `ai-engine-dataflow.md`, `ai-engine-features.md`, `docs/09`의 Agent 실행 부분을 읽습니다.
2. `AgentExecutor`의 호출 흐름 전체(Advisor 순서, 최종 답 판정, 계약 검증, 재시도 경로)를 따라갑니다.
3. 모델이 최종 답을 쓰면서 Tool 호출을 끼워 넣는 현상이 이번 두 Agent 말고 다른 실행에서도 있었는지 `execution.log`로 확인합니다. 한 모델의 습관인지 엔진 구조 문제인지 가르는 근거입니다.
4. 위 결과로 원인을 분류하고, 기존 장치를 확장할지 새로 만들지 정해서 설계안을 먼저 보여 드립니다. 승인받기 전에는 코드를 고치지 않겠습니다.

이 순서로 진행할까요?

---

## 질문

응 전체적인 구조를 상세히 파악한 후에 진행하는게 맞는 것 같아.

---

## 답변

구조를 읽었고, 이번 반복이 어디서 생기는지 정리했습니다. 코드는 아직 고치지 않았습니다.

## 읽은 것

- `ai-engine-agent-principles.md` 전체
- `ai-engine-dataflow.md`, `ai-engine-features.md`, `ai-engine-packages.md`, `ai-engine-tuning.md`에서 반복·재시도·답 처리 관련 부분(`ai-engine-dataflow.md`는 63KB라 전부 읽지는 못했습니다)
- `AgentExecutor`의 `call()`, `ask()`, `reshape()`, `textOf()`, `buildSpec()`
- Windows `execution.log` 전체에서 반복 중단 집계

## Agent 한 번 호출이 끝나는 구조

1. `WorkFlowExecutor`가 AGENT step을 `AgentExecutor.call()`로 부릅니다.
2. `call()`은 `maxAttempts`만큼 시도합니다. `AgentContractException`이 나면 지난 실패 사유와 지난 답(`failure.answer()`)을 `retryNote`로 붙여 다시 부릅니다.
3. `buildSpec()`이 요청을 조립합니다. Tool은 `Budget(Repeat(RepeatFailure(Reread(tool))))` 순으로 감싸고, Advisor는 Spring AI의 `ToolCallingAdvisor` 루프 안쪽에서 `ReasoningStrip` → `Compaction` → `RunawayRetry` → `Stats` → `Gate` 순으로 돕니다.
4. 루프는 Tool 호출이 없는 응답이 올 때만 끝납니다. Tool 호출과 같이 온 본문은 버려집니다.
5. `ask()`와 `textOf()`가 끝난 이유를 검사하고, `convert()`와 `reshape()`가 output 형식을 맞춥니다. 마지막에 `requiredTools`와 `requiredWrites`를 확인합니다.

실패했을 때 쓰는 복구 장치는 이미 `retryNote`, `EmptyAnswerException` 추가 시도, `RunawayRetryAdvisor`, `reshape`, Tool 호출 한도 검사가 있습니다. 반복 호출 중단은 이 복구 장치 어디에도 연결되어 있지 않고, 그냥 실패로 끝납니다.

## 로그에서 확인한 사실

- 정상 종료 52건에 반복 호출 중단 4건입니다. `sm-developer-agent` 3건, `sm-auto-fix-agent` 1건이고, 전부 `Skill gnx-crud-screen`이며 전부 testApp 프로젝트입니다.
- 4건 모두 일이 끝난 뒤 꼬리에서 일어났습니다. 오늘 17:33 건은 13회차에 컴파일과 형식 검사를 통과했고 7개 파일을 썼습니다. 14~19회차는 최종 요약을 본문으로 쓰면서 `Skill`을 같이 불렀습니다. 출력은 57토큰으로 거의 Tool 호출만 있는 응답입니다.
- `sm-developer-agent.md` 8~9행 주석에 이미 같은 사고가 적혀 있습니다(실행 `55b0be49`, "같은 Skill만 되풀이하다 멈췄다"). 11-24부터 반복되는 문제이고, 프롬프트 쪽 완화만 해 왔습니다.
- 지금까지 정리한 틀리지 않은 점: 압축 장부 문제가 아니고, 모델이 마무리를 못 하는 것도 아닙니다. 마무리하면서 Skill 호출을 덧붙이는 것입니다.

## 아직 모르는 것

모델이 왜 Skill 호출을 덧붙이는지입니다. 후보는 세 가지입니다.

1. OpenRouter 위의 qwen3.8-27b가 마지막 라운드에 Tool 호출을 덧붙입니다. 서빙 측 Tool 호출 파서 문제일 수도 있습니다.
2. `ReasoningStripAdvisor`가 앞 라운드 추론을 빼서 모델이 "마쳤다"를 못 이어 갑니다.
3. Skill Tool 설명이 모델을 끕니다.

원 응답(raw)이 로그에 없어서 가릴 수 없습니다.

## 분류와 대응 방향

원칙 문서의 분류로는 **엔진이 기계적으로 보장할 수 있는 것**입니다. 파일이 써졌고 `requiredWrites`가 채워졌다는 것은 코드로 확인할 수 있고, 프롬프트로 더 말릴 일이 아닙니다.

| 단계 | 내용 | 비고 |
|---|---|---|
| A. 원인 가르기 | 꼬리 라운드의 원 응답을 로그에 남기거나, 같은 단계를 `keep-reasoning: true`로 한 번 돌려 봅니다 | 후보 1·2를 가름. 코드 변경은 작음 |
| B. 안전망 | 반복 호출로 중단할 때 직전 본문이 있으면 그것을 답으로 `convert`/`reshape`에 태우고, `requiredTools`/`requiredWrites`를 통과하면 성공으로 받습니다. 통과하지 못하면 지금처럼 실패합니다 | 기존 검증을 그대로 쓰므로 "끝난 척"을 막음. 원인이 무엇이든 필요 |
| C. 원인 제거 | A의 결과에 따라 provider 설정이나 Skill Tool 설명을 고칩니다 | A 뒤에 정함 |

B를 새 장치로 만들지, 기존 `retryNote` 재시도 경로를 확장할지는 정해야 합니다. `sm-developer-agent`는 `maxAttempts: 1`이라 재시도 경로를 쓰려면 설정이 바뀝니다. 안전망은 재시도가 아니라 "받아들이기"가 맞아 보입니다.

## 제안

A와 B를 같이 진행하고, 둘 다 코드를 쓰기 전에 설계안을 먼저 보여 드리겠습니다. A는 Windows에서 같은 단계를 한 번 더 돌려야 확인되는 일이라, 이 조사 방식(원 응답 로깅 또는 `keep-reasoning` 비교)이 괜찮은지 의견을 주세요.

---

## 질문

소스레벨로도 깊숙히 파악해.

---

## 답변

소스까지 내려가 보니 이번 반복의 구조가 지난번 설명보다 구체적으로 정리됩니다. 앞서 제안한 B안(안전망)에는 허점도 하나 발견했습니다. 코드는 아직 고치지 않았습니다.

## 소스 수준에서 확인한 것

**1. Tool 루프의 종료 조건은 `hasToolCalls()` 하나뿐입니다.**
- `ToolCallingAdvisor`의 기본 판정 함수(`lambda$static$0`)는 `chatResponse != null && chatResponse.hasToolCalls()`입니다. 바이트코드로 확인했습니다. 끝난 이유(`finish_reason`)나 본문 유무는 보지 않습니다.
- `OpenAiChatModel.buildGeneration`은 응답 메시지에 `tool_calls`가 있으면 끝난 이유와 상관없이 `ToolCall`로 옮깁니다.
- 그래서 "최종 답 본문 + Skill 호출" 응답은 루프가 계속 도는 응답이 됩니다. 본문은 대화 이력에는 남지만 호출자에게 돌아가지 않습니다.

**2. 우리 쪽 장치는 루프 안쪽에서 응답을 바꿀 수 있는 자리가 이미 있습니다.**
- `RunawayRetryAdvisor`(-60)가 그 선례입니다. `callAdvisorChain.copy(this).nextCall(...)`로 라운드를 버리고 다시 부릅니다. 이 Advisor는 `ToolCallingAdvisor`(HIGHEST+300)보다 안쪽이라, 판정 전에 응답을 볼 수 있습니다.
- 반복 가드(`RepeatCallGuardCallback`)는 Tool 콜백 안에서 `AgentContractException`을 던집니다. 이 예외는 Spring AI의 `ToolExecutionExceptionProcessor`에 걸리지 않고 그대로 올라옵니다(`ToolExecutionException`이 아니라서). 로그에서 관측한 대로입니다.

**3. 이번 실패는 모든 라운드가 "본문 + Tool 호출" 형태였다는 것과 연결됩니다.**
- 17:33 실행 1~14회차 전부 `말 [Now the two JSP files.]`, `[Let me verify …]`처럼 설명 문장과 Tool 호출을 함께 냅니다. 모델은 "내 턴 = 본문 + Tool 호출"이라는 패턴을 대화에서 14번 봤습니다.
- 마무리 단계의 본문은 최종 답인데도 같은 패턴대로 Tool 호출을 붙입니다. 출력이 57토큰인 것도 호출만 거의 새로 쓰고 있다는 뜻입니다. 붙이는 Tool이 `Skill`인 것은 인자(`gnx-crud-screen`)를 이미 알고 있고 부작용이 없기 때문으로 보입니다.
- 정상 종료는 52건, 이 패턴은 4건(약 7%)입니다. 모델의 확률적 습관이라는 설명과 맞습니다.
- `ReasoningStripAdvisor`가 앞 라운드 추론을 지우는 것이 이 습관을 키우는지는 확인하지 못했습니다. 원 응답이 없어서 가설로만 둡니다.

**4. 지난번 B안은 이번 사례를 구하지 못합니다.**
- `sm-developer-agent`는 `requiredTools: [editFileTexts]`입니다. 17:33 실행의 성공한 Tool 목록에 `editFileTexts`가 없습니다. 20번 개발부터 다시 돌려서 태스크아이템이 이미 "완료"로 기록돼 있었고, 모델이 "고칠 것 없음"으로 판단했습니다.
- 반복을 끊고 마지막 본문을 답으로 받아도 `checkRequiredTools()`에서 `AgentContractException`이 나고, `maxAttempts`는 1이라 같은 실패가 됩니다.
- 이 충돌은 반복 문제와 별개의 두 번째 문제입니다. 재개발이 "이미 반영됨"으로 끝나는 경우를 `requiredTools`가 막습니다. `ai-engine-features.md`에는 다른 Agent에서 `requiredTools`를 뺀 선례가 있지만, 이 Agent는 어떻게 할지 결정이 필요합니다.

## 수정안 (분류: 엔진이 기계적으로 보장할 일)

**M3. 불필요한 Skill 재호출 응답을 루프 안쪽에서 끝내는 Advisor (새로 제안)**
- 위치: `RunawayRetryAdvisor` 옆, 루프 안쪽.
- 조건: 응답의 Tool 호출이 전부 이미 받은 Skill을 다시 부르는 것이고(`ReadHistory`의 Skill 장부로 판별), 본문이 비어 있지 않을 때.
- 동작: 그 응답에서 Tool 호출을 떼어 낸 사본을 돌려줍니다. 그러면 `hasToolCalls()`가 false가 되어 루프가 끝나고, 본문이 최종 답으로 `convert`/`reshape`로 갑니다.
- 안전장치: 첫 재호출은 그대로 두어 "이미 받았다" 안내를 한 번 보여 줍니다. 안내를 받고도 또 불렀을 때(연속 2회차)만 적용합니다. 설명 문장과 함께 부르는 정상적인 Skill 호출은 건드리지 않습니다.
- 효과: 오늘 17:33 건은 15회차에서 끝납니다. 5회차 낭비와 라운드당 5만 토큰 입력이 줄어듭니다.
- 이 Advisor는 `ToolCallLimit`나 가드를 거치지 않고 `ReadHistory`만 읽습니다. 기존 반복 가드는 그대로 두어 다른 Tool의 반복은 지금처럼 잡습니다.

**M4. `sm-developer-agent`의 `requiredTools` 정리 (결정 필요)**
- "이미 반영돼 고칠 것이 없다"는 정상 결과를 허용하려면 `requiredTools`를 빼고 `requiredWrites`로 대체하거나, "고칠 것 없음" 한 줄을 `editFileTexts`로 남기도록 프롬프트와 계약을 맞춰야 합니다.

## 아직 모르는 것

- 이 습관이 모델이나 OpenRouter 제공자 쪽의 문제인지, 우리가 추론을 지워서 커진 것인지는 원 응답이 있어야 가립니다. 이것은 M3와 별개로, 가능하면 `keep-reasoning: true`로 같은 단계를 몇 번 돌려 반복 빈도를 비교하고 싶습니다. 같은 입력으로 3번 이상 돌려야 의미가 있습니다.

## 제안 순서

1. M3 구현 + `RunawayRetryAdvisorTest` 모양의 단위 테스트(응답 모양은 이번 로그에서 복사).
2. M4는 `requiredTools` 대신 어떤 계약이 맞는지 결정해 주세요. 지금은 프롬프트와 계약이 어긋나 있습니다.
3. 원인 실험(`keep-reasoning`)은 Windows에서 돌려야 하므로 M3 반영 뒤 편할 때 진행합니다.

M3부터 구현할까요? M4는 어느 쪽으로 정할지도 알려 주세요.

---

## 질문

네가할 수 있는 한  자세하게 분석해서 할 수 있는 모든 조치를 취해.

---

## 답변

Skill 재호출 응답을 루프 안에서 끝내는 Advisor를 구현합니다.

---

## 답변

Full suite is running in the background; meanwhile I'll update the docs.

---

## 답변

반복 호출 문제의 원인을 소스 수준에서 찾아 고치고, 로컬 커밋(`2ea21a53`)까지 했습니다. 푸시는 하지 않았습니다.

## 원인
- Spring AI 2.0.1의 Tool 고리(`ToolCallingAdvisor`)는 응답에 Tool 호출이 하나라도 있으면 계속 돕니다. 끝난 이유나 본문은 보지 않고, 본문은 버립니다. 바이트코드로 확인했습니다.
- qwen3.8-27b는 모든 라운드를 "설명 문장 + Tool 호출"로 냅니다. 마무리 라운드에서도 최종 답을 본문에 쓰고 쓸모없는 `Skill` 호출을 덧붙입니다. 그래서 답이 매번 버려지다가 반복 가드가 호출 전체를 실패시킵니다.
- 오늘 Agent 호출 약 56건 중 4건이 이랬고, 모두 `Skill gnx-crud-screen`입니다. `sm-developer-agent` 3번, `sm-auto-fix-agent` 1번입니다. 압축 문제도, 모델이 마무리를 못 하는 문제도 아니었습니다.

## 한 일
1. **`RedundantSkillCallAdvisor` 추가.** 이미 받은 Skill만 다시 부르는 응답이 "이미 받았다" 안내를 받고도 연달아 두 번째 나오면, 호출을 떼고 가장 긴 본문을 최종 답으로 삼아 고리를 끝냅니다. 그 뒤 `output` 모양, `requiredTools`, `requiredWrites` 검사는 그대로 거칩니다. 끄는 설정은 `dstone.ai.agent.redundant-skill-end`(기본 true)입니다.
2. **`sm-developer-agent` 2.5.1.** 개발 단계부터 다시 돌릴 때 태스크아이템이 이미 "완료"로 기록돼 있으면 `editFileTexts`를 안 부르는 게 정상인데, `requiredTools: [editFileTexts]`가 이를 실패로 만들었습니다. 이 계약을 뺐습니다. 기록을 빼먹었는지는 답의 `taskitemsRecorded`와 승인 화면의 목록으로 봅니다. 이 판단은 되돌릴 수 있으니, 계약을 유지하고 싶으면 알려 주세요.
3. 앞서 푸시한 변경(preload Skill 문구 정리, 중단 오류에 성공한 Tool·쓴 파일 표시, 모델 말 로깅)은 그대로 유지했습니다.
4. 문서(`docs/09`, `.claude/docs/ai-engine-features.md`)를 반영했습니다.

## 검증
- 새 단위 테스트 6개와 종단 테스트 1개를 추가했습니다. 종단 테스트는 실제 `ToolCallingAdvisor` 루프로 돌립니다. `redundant-skill-end=false`로 끄면 실패하는 것도 확인했습니다. 실제 LLM으로는 아직 돌리지 않았습니다.
- 전체 테스트 303개 중 3개가 실패합니다. 모두 `SmDecisionFlowTest`이고, 단계 이름의 번호(`step17 18-개발`이 지금은 `20-개발`)가 workflow 파일의 최근 변경과 어긋나서입니다. 제 변경과는 무관하지만 원래 상태에서 다시 돌려 확인하지는 못했습니다. 기대값만 바꾸면 되지만, 진행 중인 workflow 수정과 겹칠 수 있어 건드리지 않았습니다.
- `application.yml`의 `llmcall: true` 때문에 미리 넣은 답을 쓰는 테스트가 기본 설정에서는 건너뜁니다. 전체 테스트는 `-Ddstone.ai.test.llmcall=false`로 돌렸습니다.

## 남은 일
- 푸시하면 Windows에서 pull, 재빌드, 재기동 후 20-개발부터 다시 돌려 볼 수 있습니다. 푸시할까요?
- 이 모델의 설명 문장 + Tool 호출 습관이 추론을 지우는 설정(`keep-reasoning: false`) 때문에 심해지는지는 측정하지 못했습니다. Windows에서 같은 단계를 3번 이상 돌려 비교하면 알 수 있습니다.
- 이번 Advisor는 Skill 재호출만 다룹니다. 다른 Tool을 같은 방식으로 덧붙이는 경우는 기존 반복 가드가 5번째에 끊으며, 중단 오류에 성공한 Tool과 쓴 파일이 함께 적힙니다.

---

## 질문

푸시해주고 그다음 진행이 있나?

---

## 답변

푸시했습니다. 로컬과 `origin/main`이 같은 상태입니다.

이어서 할 일은 둘입니다. 둘 다 Windows에서 하셔야 합니다.

1. **반영과 재실행**
   - Windows에서 `git pull`, 재빌드, 엔진 재기동 후 20-개발부터 다시 돌려 주세요. 로그에서 `이미 받은 Skill을 … 떼고 본문을 최종 답으로 삼았습니다`가 보이면 새 Advisor가 작동한 것입니다. `sm-developer-agent`가 `requiredTools` 없이 끝나는지도 같이 보면 됩니다.
   - 정의 파일만 바뀐 `sm-developer-agent` 수정은 무재시작 편집이 되는지 확인하지 못했습니다. Java가 바뀌었으니 어차피 재기동이 필요합니다.

2. **원인 실험(선택)**
   - 모델의 "본문 + Tool 호출" 습관이 추론을 지워서 심해지는지 보려면, `dstone.ai.agent.keep-reasoning: true`로 같은 단계를 3번 이상 돌려 반복 빈도를 비교합니다.
   - 새 Advisor가 증상을 막아 주므로 급하지는 않습니다. 입력 토큰이 늘어나는 비용이 있습니다.

제가 더 할 수 있는 일은 재실행 로그를 보고 결과를 판단하는 것입니다. 로그를 확인해 달라고 하시면 보겠습니다. `SmDecisionFlowTest` 3건의 기대값 정리는 workflow 수정이 끝난 뒤에 하는 게 좋습니다.

---

## 질문

window 쪽의 서버는 내렸어. 이제 wsl 쪽의 서버를 띄워서 네가 계속 진행하는계 어떨까? 고객요청은 /temp/00.SM/work002/ 아래에 있어. 그리고 여러번 테스트 해서 개선에 대한 결론을 내봐.

---

## 답변

3개 실행이 설계 승인까지 지나 개발 단계로 들어갔습니다. 계속 지켜보겠습니다.

---

## 답변

Now rewrite the class body for the generalized logic.

---

## 답변

Now a sequential driver (sandbox is exclusive per project) and 4 runs.

---

## 답변

WSL에서 같은 요청(`/temp/00.SM/work002`)으로 반복 검증했고, 근본 원인에 맞춘 수정을 적용해 푸시했습니다(`47cbae43`).

## 근본 원인
- Spring AI의 Tool 고리는 응답에 Tool 호출이 하나라도 있으면 본문을 버리고 계속 돕니다.
- qwen3.8-27b가 마무리 라운드에서 최종 답을 본문에 쓰면서 "또 불러도 달라질 것이 없는 Tool"을 덧붙입니다. 그래서 답이 버려지고, 5번 반복되면 반복 호출 가드가 이미 끝난 일을 통째로 실패시켰습니다.
- 처음에는 이 Tool이 `Skill`뿐이라고 봤는데, WSL 실행에서 `checkSources`로도 같은 반복이 재현됐습니다. Skill 전용으로 만든 첫 수정으로는 부족했습니다.

## 적용한 수정
1. **`RedundantToolCallAdvisor`**: 본문이 있고 Tool 호출이 모두 아래 둘 중 하나인 응답에서 호출을 떼고, 본문을 최종 답으로 삼아 고리를 끝냅니다. 이후 `output`, `requiredTools`, `requiredWrites` 검사는 그대로 거칩니다.
   - 이미 받은 Skill을 안내를 받고도 연달아 2번째 부르는 것
   - 바로 앞 호출과 같은 호출이 같은 결과로 이미 두 번 끝난 뒤 또 부르는 것
   - 사이에 다른 Tool이 실행되면(고친 뒤 재검사) 셈이 다시 시작하고, 기다리는 용도의 Tool은 제외됩니다.
2. **`sm-developer-agent` 2.5.1**: 이미 기록된 태스크아이템을 다시 돌릴 때 정상 결과를 실패로 만들던 `requiredTools`를 뺐습니다.
3. 진단용으로 모델이 한 말을 로그에 남기고, 반복 중단 오류에 성공한 Tool과 쓴 파일을 붙였습니다. 이것으로 원인을 찾았습니다.

## 검증
| 시험 | 결과 |
|---|---|
| 전체 실행 4회 | 3회 `DONE`, 1회는 LLM 연결 끊김(`Connection reset`)으로 실패. 반복 호출 중단은 0건 |
| 개발 단계만 12회 반복(되감기 후 일시정지) | 12/12 성공 |
| 같은 날 Windows 기준선(수정 전) | `sm-developer-agent` 12회 중 3회, `sm-auto-fix-agent` 10회 중 1회가 반복 중단 |
| 단위 테스트 | 새 Advisor 9개, `SampleAgentSkillTest` 종단 1개. 끄면 실패하는 것도 확인 |

개발 단계 반복의 9번째에서 Advisor가 실제로 작동했습니다. 17회차에 최종 요약과 `Skill` 호출이 함께 나왔고, 연속 2번째에서 호출을 떼어 단계가 성공했습니다. 그 실행은 태스크아이템이 이미 "완료"여서 `editFileTexts`도 안 불렀으니, 2번 수정이 모두 필요했던 경우입니다.

## 한계와 주의
- 이 습관은 확률적이어서(기준선 약 8~25%) 표본이 작습니다. 실제 작동은 1건 확인했고, "수정 후 0건"이 우연일 가능성도 남습니다. 몇 번 더 돌리면 더 확실해집니다.
- 전체 테스트 305개 중 `SmDecisionFlowTest` 3개는 이번 수정과 무관하게 계속 실패합니다. 단계 이름의 번호가 어긋나서입니다.
- `knowledgeFeatureCode` step이 4번 모두 `/api/revisions/239/feature` 없는 주소로 실패했습니다. 4081의 knowledge 인스턴스가 11-22에 빌드된 jar라 이 엔드포인트(11-25 추가)가 없습니다. 설계가 참고 기능 코드 없이 진행됩니다. 재빌드하려면 실행 중인 인스턴스를 멈춰야 해서 건드리지 않았습니다. 필요하면 재기동하겠습니다.
- 두 번 실행이 동시에 돌면 임시 시험 서버를 서로 쓰고 있다고 거절됩니다(프로젝트당 하나). 병렬 시험이 아니라 순차로 해야 합니다.
- `sm-auto-fix-agent`가 브라우저로 디버깅하면 109회차, 입력 700만 토큰까지 쓰는 경우가 있었습니다. 반복이 아니라 실제 작업이라 손대지 않았지만 비용 문제로는 볼 만합니다.
- WSL의 ai-engine 서버(8081, PID 64752)는 떠 있는 채로 두었습니다.

---

## 질문

- 두 번 실행이 동시에 돌면 임시 시험 서버를 서로 쓰고 있다고 거절됩니다(프로젝트당 하나). 병렬 시험이 아니라 순차로 해야 합니다.
==>> testserver 를 이야기 하는건가? 아니면 sendboxserver를 이야기 하는건가?

---

## 답변

둘 다 해당합니다. 거절 메시지에 나온 "임시 시험 서버"는 `startTestServer`만이 아니라 `startSandboxServer`로 띄운 서버까지 가리키는 통칭이고, 실제로 부딪힌 것은 `startSandboxServer`(step29)였습니다.

## 소스로 확인한 내용
- 거절 문구는 `TestServerTool.acquireLock()`(`TestServerTool.java:896`)에서 나옵니다. 이 Tool 클래스가 `startTestServer`와 `startSandboxServer`를 둘 다 담고 있고, 둘이 같은 프로젝트 잠금을 씁니다.
- 잠금은 작업 폴더의 파일 잠금(`LOCK_FILE`, `FileChannel.tryLock`)입니다. 프로젝트별로 하나이고, 다른 실행이 쥐고 있으면 `lock-wait-seconds`(기본 60초) 동안 기다리다가 위 메시지로 거절합니다. 이 엔진의 다른 실행이든 다른 엔진이든 같습니다.
- 이번 시험에서는 3개 실행을 동시에 돌렸고, 두 번째 실행의 step29(`startSandboxServer`)가 60초 만에 거절됐습니다. 첫 번째 실행의 id가 메시지에 찍혔습니다.

## 정리
- `startSandboxServer`는 일회용 DB로 격리하지만, 프로젝트 잠금은 그대로 공유합니다. 그래서 격리 서버끼리도 프로젝트당 하나씩만 뜹니다.
- `sandboxError`에 이 거절이 들어가면 그 실행은 자동 검증을 건너뛰고 개발 승인으로 넘어갑니다. 두 번째 실행이 검증 없이 `DONE`이 된 것이 이 경우입니다.
- 그래서 병렬 시험의 결과는 검증을 거치지 않은 것으로 봐야 하고, 순차로 돌린 4회분만 자동 검증을 거쳤습니다.

격리 서버끼리는 포트와 DB가 서로 달라서 원리상 동시에 띄워도 될 것 같습니다. 이 잠금이 꼭 필요한지는 확인하지 못했습니다. 병렬 실행이 필요하면 따로 살펴보겠습니다.

---

## 질문

응 격리서버는 동시에 띄우는걸로 변경해줘. 
그리고 격리서버가 있는데 testserver 가 반드시 필요한지 진단해줘.

---

## 답변

Now the implementation patch of `TestServerTool`.

---

## 답변

Three slots are in use at the same time — all three runs are in step29/33 without the lock rejection. Waiting for them to finish.

---

## 답변

격리 서버를 같은 프로젝트에서 동시에 띄우도록 바꿨습니다. 푸시까지 했습니다(`fd7a9e10`). `startTestServer`가 꼭 필요한지는 아래에 진단했습니다.

## 1. 격리 서버 동시 실행

**원인**: 서버가 쓰는 폴더(사본, Tomcat, 로그, 일회용 DB 폴더)와 잠금 파일이 프로젝트당 하나였습니다. 그래서 격리 서버끼리도 프로젝트당 한 실행만 쓸 수 있었습니다.

**변경**
- 폴더를 "자리"로 나눴습니다. `testApp-server`, `testApp-server-2`, … 가 있고, 자리마다 사본, Tomcat, 로그, 일회용 mysqld, 잠금 파일이 따로입니다. `startSandboxServer`는 다른 실행이 쓰지 않는 첫 자리를 잡습니다.
- 자리 수는 `dstone.ai.tool.test-server.sandbox-max-parallel`(기본 4)입니다. 모두 차면 `lock-wait-seconds` 동안 기다립니다.
- 첫 자리는 예전 폴더 이름과 서버 키를 그대로 씁니다. `startTestServer`는 원래 DB에 붙으므로 첫 자리만 씁니다.
- 부수 수정
  - `stopTestServer`는 이 실행이 띄운 서버만 내립니다.
  - 동시에 띄울 때 같은 포트를 두 번 주지 않도록 최근 포트를 피합니다.
  - 남은 프로세스 정리에서 `testApp`과 `testApp~2`를 혼동하지 않도록 표시 뒤가 공백일 때만 맞춥니다.

**검증**: WSL에서 같은 요청 3개를 동시에 돌렸습니다. 이전에는 두 번째 실행이 60초 만에 거절됐는데, 이번에는 3개 모두 격리 서버를 띄워 검증(step29, 35)을 거쳤고(2개는 자동 수정까지) `DONE`이 됐습니다. 끝난 뒤 남은 서버와 DB 프로세스는 0개입니다. `TestServerSlotTest` 4개를 새로 추가했습니다.

## 2. `startTestServer`가 꼭 필요한가

**쓰는 곳은 한 곳입니다.** 도구 목록에 넣은 Agent는 `sm-troubleshooter-agent`뿐입니다. 자동 검증과 자동 수정 Agent는 의도적으로 뺐고, 프롬프트도 격리 서버가 `cause: environment`로 실패했을 때만 쓰라고 합니다.

**실제로는 안 쓰였습니다.** 이 엔진의 전체 로그(WSL, Windows, 11-21~11-26)를 세어 보니 `startTestServer`는 11-22에 22번 불렸고, 격리 서버가 생긴 11-23 이후에는 0번입니다. `startSandboxServer`는 200번 넘게 불렸습니다.

**격리 서버로 못 하는 구성에서만 필요합니다.** 격리 서버가 못 띄우면 환경 실패로 끝나는데, 이때 `startTestServer`만 띄웁니다.

| 격리 서버가 못 하는 것 | `startTestServer` |
|---|---|
| SQL Server, MariaDB, Tibero 등 MySQL·PostgreSQL·Oracle 밖의 DB | 원래 DB에 붙어서 띄움 |
| Gradle, 다중 모듈, Maven 플러그인 기동(`tomcat7:run`, `jetty:run`) | 플러그인 방식으로 띄움 |
| NIO 접속 라이브러리(Lettuce, Kafka, Netty 등)가 든 앱 | 격리 장치 없이 띄움 |
| 프로젝트ID가 없는 요청 | 폴더 이름 키로 띄움 |

**결론**
- 지금 시험 대상 `testApp`(MySQL, Maven WAR, Tomcat)에서는 필요 없습니다. 격리 서버로 충분합니다.
- 다만 이 제품은 고객 환경이 제각각(금융권, 폐쇄망)이라 위 구성이 나올 수 있고, 그때는 `startTestServer`가 유일한 재현 수단입니다. 그래서 지금 지우면 그 고객에서는 "BLOCKED, 사람이 확인"으로 후퇴합니다.
- 위험은 그대로입니다. 원래 DB에 붙어서 등록·삭제가 실제 데이터를 바꿀 수 있고, 지키는 장치는 프롬프트의 "기존 데이터를 건드리지 않는다"뿐입니다.

**선택지**
1. **그대로 둔다.** 11-23 이후 호출이 0번이어서 현재 영향은 없습니다.
2. **기본 끄기(권하는 쪽).** `test-server.allow-unisolated: false`를 기본값으로 두고, 격리를 지원하지 않는 고객 현장에서만 켭니다. 원래 DB에 쓰는 도구가 기본으로 열려 있는 상태가 사라집니다.
3. **지운다.** 가장 단순하지만 위 구성의 고객에서는 전문가 해결의 화면 재현이 불가능해집니다.

2번으로 진행할까요, 아니면 다른 방향이 있으신가요? 이 부분은 코드를 건드리지 않았습니다. WSL의 ai-engine(8081, PID 78004)은 새 코드로 떠 있습니다.

---

## 질문

물리적으로 testserver 의 위치가 어떻게 되는건가?

---

## 답변

물리 위치는 엔진을 띄운 PC의 사용자 홈 아래입니다. 설정으로 바꿀 수 있습니다.

## 위치
- **작업 루트**: `dstone.ai.tool.test-server.work-root`(env의 `TEST_SERVER_WORK_ROOT`)입니다. 비어 있으면 `{사용자 홈}/.dstone-ai-engine`입니다.
  - WSL: `/home/jysn007/.dstone-ai-engine/`
  - Windows: `C:\Users\jysn007\.dstone-ai-engine\`(이전 Windows 로그의 서버 로그 경로에서 확인했습니다)
- **자리별 폴더**: 작업 루트 아래 `{프로젝트ID}-server`(첫 자리), `{프로젝트ID}-server-2`, `-server-3`, …
- **폴더 안 구성**
  - `app/`: 원본에 개발 결과를 얹은 사본. 빌드 결과 `target`도 여기에 생깁니다.
  - `tomcat/`: WAR용 전용 CATALINA_BASE(`conf`, `webapps`, `logs`, `work`, `temp`)
  - `server.log`: 서버 출력. `readFileTail`로 읽는 파일입니다.
  - `server.pid`, `server.lock`, `source.txt`
  - `sandbox/`: 격리 서버의 일회용 mysqld 데이터 폴더. 서버를 내릴 때 지워지므로 지금은 없습니다.
- **설치된 Tomcat 본체**는 복사하지 않고 그대로 씁니다(`TEST_SERVER_TOMCAT_HOME`, WSL은 `/opt/tomcat9`). 자리의 `tomcat/` 폴더가 그 본체를 가리키는 별도 설정입니다. 사람이 쓰는 Tomcat은 건드리지 않습니다.

## 현재 WSL 상태
`testApp-server`, `testApp-server-2`, `testApp-server-3`이 있고 각각 약 1.1GB입니다. 서버를 내려도 사본은 다음에 다시 쓰려고 남깁니다(처음만 전체 복사). 자리를 4개까지 쓰면 4.4GB쯤 필요합니다.

지우고 싶으면 서버가 안 떠 있을 때 폴더를 직접 지우면 되고, 다음 실행에서 다시 만듭니다. 위치를 옮기려면 `TEST_SERVER_WORK_ROOT`를 지정하면 되는데, 이때 mysqld가 거기에 쓸 수 있어야 합니다(Linux에서 AppArmor가 켜져 있으면 허용이 필요합니다).

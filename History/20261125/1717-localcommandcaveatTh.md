# localcommandcaveatTh

> 생성일시: 2026-11-25 17:17

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

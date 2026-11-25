# localcommandcaveatTh

> 생성일시: 2026-11-25 17:13

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

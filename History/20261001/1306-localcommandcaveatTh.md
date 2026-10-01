# localcommandcaveatTh

> 생성일시: 2026-10-01 13:06

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="9685">
지금 pilot-workflow 를 진행시켰는데 step03 까지 진행은 잘 되었으나... 아래의 내용(결과가 통과이건 실패이건 증거파일(산출물)을 남기고 사유를 기록하라.)은 잘 지켜지지 않은 것 같아. 왜 그럴까?
    [산출물]
    - {workDir}/03-impact-review.md
    - 파일이 존재하지 않을 경우 파일 생성 하고 이미 존재할 경우 수정된 내용으로 재생성 하라.
    - 결과가 통과이건 실패이건 증거파일(산출물)을 남기고 사유를 기록하라.
</pasted_content id="9685">

---

## 답변

pilot-workflow 정의와 step03 관련 Agent, 실제 실행 흔적부터 확인해 보겠습니다.

---

## 답변

사용자께서 실행한 쪽은 Windows 클론(12:52에 해당 문구 추가됨)으로 보입니다. 실제 실행 로그와 작업 폴더를 읽기 전용으로 확인하겠습니다.

---

## 답변

03 파일이 실제로 없네요. step03 에서 LLM이 무엇을 했는지 실행 로그를 찾아보겠습니다.

---

## 답변

원인은 두 가지가 겹쳐 있습니다. 로그(`D:/AppHome/framework/dstone/LOGS/dstone-ai-engine/execution/execution.log`)와 작업 폴더를 확인했고, `03-impact-review.md`는 실제로 만들어지지 않았습니다.

## 1. 추가하신 문구가 그 실행에는 들어가지 않았습니다

- 로그 전체에서 "통과이건"이라는 문구는 0건입니다.
- 12:56 step03 호출에 찍힌 프롬프트는 수정 전 내용입니다(`이 코드를 작성하지 않았다`, `[산출물]` 두 줄뿐).
- 원본 YAML은 12:52에 고쳤고 서버는 12:53:57에 재기동됐지만, 엔진이 실제로 읽는 `target/classes/agents/pilot/03.impact-analyzer-review-agent.yml`은 13:02에야 갱신됐습니다.

즉 12:55 실행은 재기동은 됐어도 예전 프롬프트로 돌았습니다. 지금은 `target/classes`에 새 문구가 들어 있으니, 서버를 한 번 더 재기동하면 반영됩니다.

## 2. 문구가 반영돼도 SUPERVISOR 구조상 파일을 안 쓸 가능성이 큽니다

수정 전 프롬프트에도 `[산출물] {workDir}/03-impact-review.md`는 있었는데 지켜지지 않았습니다. 이유는 다음과 같습니다.

- **엔진이 붙이는 지시문이 파일 쓰기와 충돌합니다.** step03은 `type: SUPERVISOR`라서 `AgentExecutor.ask()`가 사용자 메시지 끝에 "Your response must be a single JSON value only… `{pass, reason}`"을 붙입니다. 모델 입장에서는 가장 마지막에 온 가장 구체적인 지시가 "JSON 하나만 답하라"입니다.
- **실제 Tool 호출이 읽기뿐이었습니다.** 12:41 실행에서는 `readFile` 3번 뒤 바로 `{pass:false, reason:…}`로 끝났고(`toolCalls: []`, `finishReason: STOP`), `writeFile`은 한 번도 부르지 않았습니다. 12:56 실행은 요청 상세가 로그에 없지만 결과는 같습니다. 두 실행 모두 약 7.5초 만에 끝났습니다.
- **모델이 작습니다.** 호출 모델이 `google/gemma-4-26b-a4b-it`인데, "문서 작성 → 파일 저장 → 판정 JSON 반환"을 한 번에 시키면 중간 단계를 건너뛰기 쉽습니다.
- **프롬프트의 `[결과형식]`도 판정만 말합니다.** 01·02 Agent는 `output`에 `file`이 있어 파일을 써야 답을 채울 수 있지만, SUPERVISOR는 출력이 `{pass, reason}`으로 고정이라 파일을 안 써도 계약상 문제가 없습니다.

## 권장 수정

step03을 둘로 나누는 것이 가장 확실합니다.

1. **리뷰 작성 (AGENT)**: 리뷰 문서를 `writeFile`로 저장하고 `output: {file, summary}`를 돌려주게 합니다. 01·02와 같은 패턴이라 이미 잘 동작하는 방식입니다.
2. **판정 (SUPERVISOR)**: `03-impact-review.md`만 읽고 `{pass, reason}`을 판정하게 합니다. Tool은 `readFile`만 쓰면 됩니다.

step을 나누지 않고 프롬프트만 고친다면 `[작업 순서]`로 "1) 세 파일을 읽는다 2) 반드시 `writeFile`로 03-impact-review.md를 저장한다 3) 저장이 끝난 뒤에만 판정 JSON을 반환한다"처럼 순서를 못박는 방법이 있습니다. 다만 지금 모델에서는 여전히 건너뛸 수 있습니다.

참고로 이 문구 수정은 Windows 클론에만 있고 `/app/dstone`에는 아직 없습니다. 분리안으로 진행하기를 원하시면 `/app/dstone` 쪽에 YAML 두 개와 workflow를 수정해 드리겠습니다.

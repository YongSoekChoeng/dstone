# localcommandcaveatTh

> 생성일시: 2026-11-04 13:03

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

Workflow의 특정 step부터 다시 수행하고 싶다면 방법이 없을까?

---

## 답변

지금은 임의의 step을 골라 거기서부터 다시 돌리는 기능이 없습니다. `POST /api/ai/workflow/executions/{id}/resume`은 인자를 받지 않고, 실행이 멈춘 그 step(`context.currentStep`)에서만 이어 갑니다(`WorkFlowExecutionController.java:138`). 2026-11-02에 일시정지/재개를 만들 때 "이전 step으로 되감기", "state 고치기"는 일부러 빼 두었습니다.

## 지금 할 수 있는 것

| 상황 | 방법 |
|---|---|
| step이 실패했다 | `FAILED` 실행에 [재개]를 누르면 실패한 그 step부터 다시 돕니다. Agent 정의나 프롬프트를 고친 뒤 재개하면 고친 정의로 돕니다. 실행은 시작할 때의 정의에 고정되지 않습니다. |
| 다음 step을 고치고 나서 이어 가고 싶다 | 실행 중에 [일시정지]를 누르면 다음 step 앞에서 `PAUSED`로 멈춥니다. 정의를 고친 뒤 [재개]하면 됩니다. |
| 앞쪽 산출물(md 문서)만 손보고 싶다 | 실행 관리 화면의 문서 팝업에서 [수정]합니다. 다만 그 파일을 다시 읽는 step에만 반영됩니다. 이미 `state`에 들어간 값은 바뀌지 않습니다. |
| 이전 단계로 돌아가야 한다 | Workflow에 되돌아가는 경로를 넣어 두면 됩니다. APPROVAL `routes`(`재분석` → 앞 step)가 그 예입니다. 사람이 거절하면서 되돌아갈 곳을 고르는 방식이라 지금 구조에서는 이 방법이 가장 안전합니다. |
| 개발 중 한 step만 시험하고 싶다 | 테스트의 `stepper(id, "stepXX", input)`로 원하는 step을 원하는 입력으로 바로 돌립니다. `put()` + `jumpTo()`로 중간부터 시작할 수도 있습니다. |

## 기능으로 만든다면

`resume`에 `{fromStep: "step05"}`를 받게 하는 것이 가장 작은 변경입니다. 지금 재개 로직은 step id로 위치를 찾기 때문에, `currentStep`만 바꿔 넣으면 나머지는 그대로 쓸 수 있습니다. 다만 아래 세 가지는 미리 정해야 합니다.

1. **뒤쪽 step이 남긴 `state`.** 되감은 지점보다 뒤에서 저장한 값이 그대로 남습니다. 다시 돌면 같은 이름으로 덮어쓰기는 하지만, 경로가 달라지면 예전 값을 읽을 수 있습니다. 대상 step과 그 뒤에서 저장하는 이름을 지울지 정해야 합니다.
2. **이미 바뀐 파일.** `sm-workflow`의 developer step처럼 소스를 고친 step은 되감아도 파일이 원래대로 돌아가지 않습니다. 그 상태로 다시 돌면 `replaceInFile`이 이미 바뀐 텍스트를 찾지 못해 실패할 수 있습니다.
3. **허용 범위.** 아무 step이나 고를 수 있게 하면 위 두 문제가 커집니다. 이미 실행 기록(step history)에 있는 step만 고를 수 있게 하고, 1번은 자동 삭제가 아니라 경고로 보여 주는 쪽을 권합니다. ("step07 이후에 저장된 `design`, `devResult`가 남아 있습니다" 같은 식)

이렇게 만들면 엔진 쪽은 `resume` 인자 하나와 위치 검증, 화면 쪽은 step 이력에 [여기서 다시] 버튼 하나 정도면 됩니다. 진행할지 알려 주시면 그 방향으로 구현하겠습니다.

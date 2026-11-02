# localcommandcaveatTh

> 생성일시: 2026-11-02 13:44

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-boot>ai 의 workflow 를 진행하는데 있어서 
특정 step 에서 문제가 생겼음을 목격했을 때 pause 하고 나중에 문제를 해결하고나서 resume 하는 기능을 넣는 것은 어때?

---

## 답변

현재 실행 구조에서 어디까지 가능한지 코드부터 확인해 보겠습니다.

---

## 답변

**넣는 게 좋다고 봅니다. 다만 pause만 넣기보다 실패한 실행도 함께 이어 가게 하는 편이 낫습니다.** 실제 문제는 대개 사람이 pause를 누르기 전에 `FAILED`로 끝나기 때문입니다. 지금은 LLM 제공사 오류 하나로 실패해도 처음부터 다시 돌려야 합니다.

아직 코드는 고치지 않았고, 구조만 확인했습니다.

## 이미 있는 기반
- **상태 저장:** step이 끝날 때마다 다음 step 위치와 `state`를 DB에 저장합니다. `WorkFlowExecutor.run()`은 저장된 위치부터 이어 도는 구조이고, 승인 대기 후 재개도 이렇게 동작합니다.
- **실패 위치:** 실패한 실행도 실패한 step을 가리키고 있어, 그 step부터 다시 돌릴 수 있습니다.
- **정의 수정:** 재개할 때 현재 등록된 정의를 읽습니다. 디자이너에서 Agent 프롬프트를 고치고 저장한 뒤 재개하면 바로 반영됩니다.

## 설계할 때 주의할 점
1. **실행 중인 step은 멈출 수 없습니다.** pause는 지금 step이 끝난 뒤에 걸립니다. 오래 걸리는 LLM 호출이나 forEach 도중이면 끝날 때까지 기다려야 하므로, 화면에 "일시정지 요청됨"과 "일시정지됨"을 나눠 보여 줘야 합니다.
2. **상태를 덮어쓰는 문제가 있습니다.** step이 끝날 때 상태를 `RUNNING`으로 다시 저장하므로, 상태값만 바꾸면 pause 요청이 지워집니다. 요청 표시용 컬럼을 따로 두고 step 사이에서 확인해야 합니다.
3. **위치를 번호로 저장하고 있습니다.** 멈춘 동안 step을 끼워 넣으면 다른 step에서 재개될 수 있습니다. step id도 함께 저장하고, 그 id가 없으면 재개를 거절해야 합니다. `pilot-workflow`처럼 끼워 넣을 때 id까지 다시 매기는 정의는 이것으로도 못 막으므로, 재개 화면에서 시작 step을 확인받아야 합니다.
4. **다시 돌리면 안전하지 않은 step이 있습니다.** 파일을 고치는 forEach가 일부만 성공한 뒤 다시 돌면 `copyFileWithReplace`는 이미 있는 파일을 거부하고, `editFileLines`는 내용이 달라져 실패합니다. 이런 경우 재실행을 막는 대신 경고를 보여 주는 것부터 시작하는 게 좋습니다.
5. **재개는 비동기여야 합니다.** 승인 후 재개(`decide()`)는 실행이 끝날 때까지 응답을 잡고 있습니다. 긴 Workflow에서는 요청이 끊길 수 있으므로, 재개 API는 바로 응답하고 화면에서 진행 상황을 조회하게 합니다.

## 제안 범위

| 단계 | 내용 | 해결되는 문제 |
|---|---|---|
| 1 | `FAILED` 실행을 실패한 step부터 다시 실행 | 외부 장애, 프롬프트나 정의 오류 |
| 1 | 실행 중 pause 요청 → step 사이에서 `PAUSED` → resume | 결과가 이상한 것을 보고 멈추기 |
| 2 | 재개할 step을 앞쪽으로 골라 되돌리기 | 앞 step의 결과가 원인인 경우 |
| 3 | 멈춘 상태에서 `state` 값 직접 수정 | 값 하나만 고치면 되는 경우 |

3단계는 일단 빼는 것을 권합니다. 잘못 고친 값이 뒤 step으로 그대로 넘어가고, 나중에 결과만 봐서는 사람이 고쳤는지 알 수 없기 때문입니다. 2단계도 되돌린 뒤의 `state`에 이전 값이 남는 점을 먼저 정리해야 합니다.

화면은 dstone-boot의 "Workflow 실행 관리"에 일시정지, 재개, 실패 지점부터 재실행 버튼을 붙이면 됩니다.

서버가 재시작되면 돌던 실행이 `RUNNING`으로 남는 문제도 있습니다. 코드에서 이를 복구하는 처리를 찾지 못했는데, 1단계에 묶어서 기동할 때 이런 실행을 `FAILED`로 바꾸면 같은 재실행 버튼으로 이어 갈 수 있습니다. 다만 Pod를 여러 개 띄우면 다른 Pod가 돌리는 실행까지 실패 처리될 수 있어, 지금처럼 인스턴스가 하나일 때만 안전합니다.

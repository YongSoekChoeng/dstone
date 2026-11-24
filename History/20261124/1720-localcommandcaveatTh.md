# localcommandcaveatTh

> 생성일시: 2026-11-24 17:20

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="c555">
dstone-ai-engine Workflow 실행 관리 를 진행 중이야.

<확인 요청>
Windows 에서 진행.
실행 ID : 9f14f909-0689-4911-8733-59289a54bf7b

이 실행아이디 에서 오류가 발생했어. 내용을 파악해줘.
</pasted_content id="c555">

---

## 답변

DB에서 실행 기록을 찾았습니다. step17에서 LLM 제공자 쪽 402(크레딧 부족) 오류로 실패했네요. step17이 어떤 Agent·provider를 쓰는지 확인하겠습니다.

---

## 답변

Windows 쪽 실행 로그(execution.log)를 찾았습니다. step17 실패 시점 앞뒤를 보겠습니다.

---

## 답변

## 실행 `9f14f909-…` 실패 원인

**결론:** `sm-developer-agent`(step17, 18-개발)가 일을 거의 끝낸 뒤 `Skill(gnx-crud-screen)`만 30번 연속 불렀습니다. 그동안 요청이 약 11.7만 토큰까지 커졌고, OpenRouter가 크레딧 부족(402)으로 요청을 거절해 실행이 FAILED가 됐습니다. 오류 메시지는 크레딧 부족이지만 근본 원인은 이 반복 호출입니다.

### 무슨 일이 있었나 (DB 기록 + Windows `execution.log`)
- step00~step16은 모두 성공했습니다. 영향도 분석과 설계는 각각 한 번씩 재작업 지시 후 승인됐습니다.
- **step17 1~26회차는 정상이었습니다.** 소스 6개를 만들고 고쳤고, 24회차 `compileJava` 결과는 "컴파일 통과: 바뀐 파일 5개"였습니다. 26회차에는 `03-2.taskitems.md`를 12곳 고쳐 '완료'로 기록했습니다.
- 그 다음 회차에서 최종 답을 내야 했는데, **27~56회차 내내 `Skill {"command":"gnx-crud-screen"}`만 요청했습니다.**
  - Skill은 매번 같은 안내문 전체를 돌려줬고, 입력이 회차마다 약 2,028토큰씩 늘었습니다(63k → 117k).
  - 출력은 매번 약 212토큰으로, 같은 생각을 되풀이하는 모양입니다.
- 17:16:45, 57번째 요청에서 `402: This request would exceed your available credits given your current in-flight requests`가 났습니다.

### 실패 분류
| 구분 | 내용 |
|---|---|
| 직접 원인(환경) | OpenRouter 크레딧 부족. 모델이 `qwen/qwen3.8-27b`이고 `:free`가 없어서 유료로 호출됩니다. OpenRouter는 요청을 받기 전에 "입력 토큰 + `max-tokens: 60000`"만큼 비용을 미리 잡아 두기 때문에, 커진 요청에서 잔액을 넘었습니다. 동시에 처리 중인 요청까지 합쳐서 계산합니다. |
| 엔진 | 같은 Skill을 다시 부르는 것을 막는 장치가 없습니다. `reread-guard`는 `readFile`/`readFileLines`만 막고, `SkillTool.SkillFunction.apply()`는 이미 준 안내도 매번 다시 돌려줍니다. `tool.max-calls: 100`은 30번 반복으로는 걸리지 않습니다. Tool 설명에 "같은 Skill은 한 번만 부르면 됩니다"라고 적혀 있지만 모델이 무시했습니다. |
| 모델 | qwen3.8-27b가 할 일을 마친 뒤 최종 답을 내지 못하고 반복에 빠졌습니다. 이번 프롬프트 탓은 아닙니다. |

크레딧을 채웠더라도 이 반복은 `tool.max-calls` 100회에 걸리거나 컨텍스트 한도에 닿을 때까지 계속됐을 가능성이 높습니다.

### 지금 할 일
1. OpenRouter 크레딧을 충전하거나 `:free` 모델로 바꿉니다.
2. 실행 관리에서 step17부터 다시 시작합니다. `dev-resources`의 결과물과 taskitems 기록은 이미 남아 있습니다.

### 고칠 방향 (제안, 아직 손대지 않음)
- **범용 엔진 원칙:** 같은 Agent 호출 안에서 이미 준 Skill을 다시 부르면 본문 대신 짧은 안내를 돌려줍니다. 예: "이미 받은 Skill입니다. 할 일을 마쳤으면 최종 답을 내십시오." `reread-guard`가 파일에 하는 일을 Skill에도 하는 셈입니다. 같은 Tool을 같은 인자로 N번 연달아 부르면 step을 실패로 끝내는 상한도 검토할 만합니다.
- **설정:** OpenRouter에서는 `max-tokens: 60000`이 미리 잡히는 크레딧을 키웁니다. 실제 출력은 최대 약 2,300토큰이었으니 낮춰도 됩니다.
- **평가 사례:** 실행 관리 > 진행 기록에서 이 step17 스냅숏을 [평가 사례로 만들기]로 남겨 두면, 고친 뒤 기준선과 비교할 수 있습니다.

참고로 Windows 클론(`D:/AppHome/framework/dstone`)은 `6f1c85d9`에 있어 WSL보다 커밋 3개 뒤입니다. 엔진 고치기를 진행할지 말씀해 주세요.

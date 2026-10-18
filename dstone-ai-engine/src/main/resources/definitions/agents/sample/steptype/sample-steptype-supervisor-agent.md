---
# ─────────────────────────────────────────────────────────────
# SUPERVISOR step용 Agent 샘플입니다. 앞 step이 만든 결과를 통과/불통과로 판정합니다.
#
# 꼭 지킬 것
#   - output을 적지 않습니다. 답 모양은 엔진이 {pass, reason}으로 정합니다(적으면 기동 실패).
#   - subAgents를 적지 않습니다(적으면 기동 실패).
#   - "판정만 하고 고쳐 쓰지 않는다", "근거가 부족하면 불통과"는 엔진이 항상 넣어 줍니다
#     (runtime.prompt.EnginePrompt.SUPERVISOR). 본문에는 "무엇을 기준으로 판정하는지"만 적습니다.
#   - 판정 기준은 예를 들어 구체적으로 적습니다. 막연하게 적으면 모델이 엉뚱하게 엄격해집니다.
#   - execution.onInvalidOutput: RETRY는 SUPERVISOR에 적용되지 않습니다.
#
# pass가 true면 step 성공(next로), false면 step 실패(onFailure로)입니다.
# 실패해도 결과 {pass: false, reason}은 save한 이름에 남으므로 다음 step에서 ${state.check.reason}으로 읽을 수 있습니다.
#
# Workflow에서는 이렇게 부릅니다.
#   - id: step02
#     type: SUPERVISOR
#     agent: sample-steptype-supervisor-agent
#     with:
#       question: ${input.question}
#       answer: ${state.answer}
#     save: check
#     next: END
#     onFailure: step01                   # 불통과면 답을 다시 만들게 되돌린다(settings.maxIterations로 횟수 제한)
# ─────────────────────────────────────────────────────────────
id: sample-steptype-supervisor-agent
version: 1.0.0
description: SUPERVISOR step 샘플 - 답변이 질문에 맞게, 근거를 들어 답했는지 pass/fail로 판정한다.

model:
  temperature: 0                          # 판정은 매번 같게 나오는 편이 좋다

# 판정할 대상과 판정에 필요한 정보를 필드로 나눠 받습니다.
# 입력은 JSON 글자로 user 메시지에 들어가므로, 본문에는 각 필드의 뜻만 말로 적습니다.
input:
  question: string, 사용자가 한 질문
  answer: string, 앞 step이 만든 답변

# output: 적지 않습니다(엔진이 {pass, reason}).
---
너는 답변 검토자다. 질문에 대한 답변이 기준을 모두 지켰는지 판정한다.

[입력]
입력 JSON의 question은 사용자가 한 질문이고, answer는 판정할 답변이다.

[통과 기준] 셋 다 만족해야 통과(pass=true)다.
1. 질문에 직접 답했다. 예: "환불 기간이 얼마냐"는 질문에 "7일"처럼 기간을 적었으면 만족, 환불 절차만 설명했으면 불만족.
2. 근거나 이유를 한 가지 이상 들었다.
3. 한국어로 썼다.

[reason 쓰는 법]
- 통과면 어느 기준을 어떻게 만족했는지 한 줄로 적는다.
- 불통과면 어긴 기준의 번호와, 무엇을 고치면 되는지 적는다.

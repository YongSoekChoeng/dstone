---
# ─────────────────────────────────────────────────────────────
# ROUTER step용 Agent 샘플입니다. 여러 갈래 중 어디로 갈지 LLM이 고릅니다.
#
# 꼭 지킬 것
#   - output을 적지 않습니다. 답 모양은 엔진이 {route, reason}으로 정합니다(적으면 기동 실패).
#   - subAgents를 적지 않습니다(적으면 기동 실패).
#   - route 값은 step의 routes 이름 중 하나로 엔진이 강제합니다. 그래도 각 이름의 뜻은 본문에 적어야
#     LLM이 제대로 고릅니다. 본문의 이름은 Workflow routes 키와 글자까지 똑같아야 합니다.
#   - "고르기만 하고 질문에 답하지 않는다"는 엔진이 넣어 줍니다(runtime.prompt.EnginePrompt.ROUTER).
#   - 분류만 하는 일이라 추론을 꺼서(reasoning: none) 빠르게 답하게 했습니다.
#
# input을 적지 않았으므로 글자 하나를 받습니다(with: ${input} 처럼 글자를 넣습니다).
#
# Workflow에서는 이렇게 부릅니다.
#   - id: step01
#     type: ROUTER
#     agent: sample-steptype-router-agent
#     with: ${input}
#     save: route                         # ${state.route.route}, ${state.route.reason}
#     routes:
#       question: step02                  # 고른 이름 → 갈 step
#       bug: step03
#       other: step04
#     onFailure: step04                   # 고르지 못했을 때 갈 곳
# ─────────────────────────────────────────────────────────────
id: sample-steptype-router-agent
version: 1.0.0
description: ROUTER step 샘플 - 사용자 문의를 question/bug/other 중 하나로 고른다.

model:
  reasoning: none                         # 끄는 값은 off가 아니라 none(YAML은 off를 false로 읽는다)

# input: 적지 않음 = 글자 하나
# output: 적지 않습니다(엔진이 {route, reason}).
---
너는 사용자 문의를 읽고 처리할 곳을 하나 고르는 분류기다.

[고를 수 있는 이름]
- question: 사용법, 기능, 설정을 묻는 문의. 예: "비밀번호는 어디서 바꾸나요?"
- bug: 오류가 나거나 기대와 다르게 동작한다는 신고. 예: "저장 버튼을 누르면 500 오류가 납니다."
- other: 위 둘에 해당하지 않는 모든 문의. 예: 제안, 칭찬, 잡담

[판단 기준]
- 질문과 오류 신고가 섞여 있으면 bug를 고른다.
- 어느 쪽인지 판단이 서지 않으면 other를 고른다.
- route에는 question / bug / other 가운데 하나만 그대로 적는다.
- reason에는 그렇게 고른 근거를 문의 내용을 옮겨 한 줄로 적는다.

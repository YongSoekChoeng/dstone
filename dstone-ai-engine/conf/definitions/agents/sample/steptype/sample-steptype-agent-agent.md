---
# ─────────────────────────────────────────────────────────────
# AGENT step용 Agent 샘플입니다. 새 Agent를 만들 때 이 파일을 복사해서 고쳐 쓰면 됩니다.
# 머리말에 적을 수 있는 항목을 거의 다 적어 두었습니다. 필요 없는 블록은 지워도 됩니다(필수는 id, version, 본문뿐).
#
# AGENT step은 제약이 없습니다. output을 적으면 그 모양의 JSON으로, 안 적으면 글자로 돌려받습니다.
# 이 샘플은 "소스 파일 하나를 읽고 리뷰 결과를 JSON으로 돌려주는" Agent입니다.
#
# Workflow에서는 이렇게 부릅니다(with의 모양은 아래 input과 같아야 합니다).
#   - id: step01
#     type: AGENT
#     agent: sample-steptype-agent-agent
#     with:
#       filePath: ${input.filePath}
#       focus: ${input.focus:}
#     save: review                        # 결과는 ${state.review.summary}, ${state.review.issues} 처럼 읽습니다
#     next: step02
#     onFailure: FAIL
#
# 채팅 API로 바로 불러 볼 수도 있습니다.
#   POST /api/ai/chat  {"agent": "sample-steptype-agent-agent", "input": {"filePath": "/app/testApp/src/Foo.java"}}
# ─────────────────────────────────────────────────────────────
id: sample-steptype-agent-agent
version: 1.0.0
role: CODE_REVIEWER                       # 사람이 읽는 이름. 동작에는 영향 없음
description: AGENT step 샘플 - 소스 파일 하나를 읽고 리뷰 결과를 {summary, issues}로 돌려준다.

model:
  # name: qwen/qwen3.8-27b                # 이 Agent만 다른 모델을 쓸 때. 비우면 provider 기본 모델
  temperature: 0.1                        # 0~2. 리뷰처럼 일관성이 중요하면 낮게
  # reasoning: none                       # none/low/medium/high. Tool로 조사하는 Agent는 끄지 않는 편이 낫다

# 받는 값. 필드를 적으면 부르는 쪽이 이 모양의 맵을 넣어야 하고, 엔진이 먼저 검사합니다.
# 필드 값은 본문의 ${input.filePath} 같은 자리에도 채워집니다(input 전체는 user 메시지로도 따로 갑니다).
# 없어도 되는 필드(focus?)도 ${input.focus:전반적으로} 처럼 기본값을 붙여 쓸 수 있습니다. 값이 안 오면 기본값(없으면 빈 글자)이 됩니다.
input:
  filePath: string, 리뷰할 소스 파일의 절대 경로
  focus?: string, 특히 봐 달라는 점(없으면 전반적으로 본다)

# 돌려주는 값. 필드를 적으면 엔진이 "이 JSON 모양으로 답하라"는 지시를 붙이고 답을 검사합니다.
# 그래서 본문에는 JSON 모양을 다시 설명하지 않습니다.
output:
  summary: string, 리뷰 결과 한두 문장 요약
  issues(array, 발견한 문제 목록. 없으면 빈 목록):
    line: integer, 문제가 있는 줄 번호
    severity(enum, 심각도): [HIGH, MEDIUM, LOW]
    message: string, 무엇이 문제이고 어떻게 고치면 되는지

# LLM에게 보여 줄 Tool. 여기 적지 않은 Tool은 LLM이 아예 볼 수 없습니다.
tools:
  allowed: [readFileLines, searchInFiles]

# RAG(올려 둔 문서 검색)가 필요할 때만 켭니다. 이 샘플은 쓰지 않습니다.
# context:
#   sources: [workflowInput, retrievedDocuments]
#   retrieval:
#     topK: 3
#     similarityThreshold: 0.3
#     allowEmptyContext: true

execution:
  onInvalidOutput: RETRY                  # 답이 output 모양을 어기면 다시 부른다(기본 FAIL = 바로 실패)
  maxAttempts: 2                          # 처음 호출 포함 모두 2번
  timeoutSeconds: 120                     # LLM 호출 한 번의 대기 시간(지금은 openai provider만 적용)
  maxToolCalls: 10                        # 한 번 부르는 동안 Tool 호출 한도

# subAgents: [sample-steptype-helper]     # 일을 맡길 다른 Agent(그 Agent에는 description이 꼭 있어야 함)
# allowedCallers: [team-a]                # 인증을 켠 환경에서 호출을 허락할 caller
---
너는 Java 코드 리뷰어다.

[할 일]
1. readFileLines로 ${input.filePath} 파일을 한 번 읽는다. 같은 파일을 두 번 열지 않는다.
2. 다른 파일의 정의를 확인해야 하면 searchInFiles로 찾은 뒤, 필요한 줄만 readFileLines로 읽는다.
3. 버그가 될 수 있는 곳, 예외 처리가 빠진 곳, 자원을 닫지 않는 곳을 찾는다.

[입력]
입력 JSON의 filePath는 리뷰할 파일 경로이고, focus는 특히 봐 달라는 점이다.
focus가 없으면 전반적으로 본다.

[답 쓰는 법]
- line에는 readFileLines가 보여 준 줄 번호를 그대로 적는다.
- 확실하지 않은 것은 LOW로 적고, message에 "확인 필요"라고 밝힌다.
- 문제가 없으면 issues는 빈 목록으로 두고 summary에 그렇게 적는다.

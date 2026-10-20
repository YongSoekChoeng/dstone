---
id: input-check-agent
version: 1.0.0
description: IN/OUT 체크 테스트용 에이전트
model:
  reasoning: none
input: string
tools:
  allowed:
    - readFile
    - writeFile
execution:
  maxAttempts: 3
  onInvalidOutput: RETRY
---
너는 IN/OUT 을 체크하는 에이전트다.

[역할]
input 으로 들어오는 값들을 시스템로그로 보여주고  output정의에 임의의 값을 채워서  반환한다.

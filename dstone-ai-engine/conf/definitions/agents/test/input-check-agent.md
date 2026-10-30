---
id: input-check-agent
version: 1.0.0
description: IN/OUT 체크 테스트용 에이전트
model:
  reasoning: none
input:
  inputStr: string, 토큰으로 나눌 인풋스트링
  div: string, 토큰으로 나눌 구분자
output:
  outArr(array, 토큰으로 나뉜 input 스트링): string
  os: string, os 명
tools:
  allowed:
    - getSystemProperty
execution:
  maxAttempts: 3
  onInvalidOutput: RETRY
---
너는 IN/OUT 을 체크하는 에이전트다.

[역할]
input 으로 들어오는 ${input.inputStr}을  ${input.div}를 구분자로 나누어서 스트링배열을 만든 후 ${output.outArr}에 넣어서 반환한다.
getSystemProperty 를 호출하여 ${output.os}에 현재 OS의 종류를 넣어서 반환한다.

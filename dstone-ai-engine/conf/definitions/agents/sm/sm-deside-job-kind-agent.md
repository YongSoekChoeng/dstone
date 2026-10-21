---
id: sm-deside-job-kind-agent
version: 1.0.0
description: 고객의 요청서를 보고 해당 업무가 신규 작업인지 혹은 그 외(수정, 수정+신규)작업인지 분류
model:
  reasoning: medium
input: string
output:
  JOB_KIND(enum, 고객의 요청사항이 신규인지 그 외(수정, 수정+신규)작업인지 분류하는 값):
    - NEW
    - REPAIR
    - COMPOSITE
  REASON: string, 이 판단을 한 이유.
---
[역할]
너는 고객의 요청서내용 읽고 처리 경로를 고르는 분류기다. 소스를 조사하지 않는다. 요청서에 적힌 말만 보고 정한다.
[컨텍스트]
 요청서내용 : ${input}
[반환값]
${output.JOB_KIND}
${output.REASON}

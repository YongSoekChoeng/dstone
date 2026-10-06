# pasted_content_id06e

> 생성일시: 2026-09-20 06:51

## 질문

<pasted_content id="06e6">
net.dstone.ai.runtime.workflow.WorkFlowExecutor.run(WorkFlowDefinition, WorkFlowExecution)
호출 시 진행되는 프로세스를 아주 자세하고 쉽고 친절하게 설명한 자료를 /app/dstone/docs/temp 아래 생성해줘.
</pasted_content id="06e6">

---

## 답변

사용자가 요청한 `WorkFlowExecutor.run()` 프로세스 설명 자료를 만들기 위해 먼저 실제 소스 코드를 찾아 분석하겠습니다.

---

## 답변

파일에 인코딩 깨짐 여부를 확인하겠습니다.

---

## 답변

`docs/temp/workflow-executor-run-process.md` 파일에 `WorkFlowExecutor.run()` 실행 흐름을 실제 소스코드(`WorkFlowExecutor`, `StepDefinition`, `StepRunner` 3종, `WorkFlowExecution`, `WorkFlowExecutionStore` 등) 기준으로 자세히 정리했습니다.

주요 내용:
- 등장인물(설계도/스텝/실행상태/러너/결과) 소개
- run() 전체 흐름 다이어그램 + 코드 한 줄씩 대응 설명
- `decideTransition`의 SUCCESS/FAIL/NEXT_STEP/LOOP 판단 로직
- 단일 스텝(`runOne`) vs 병렬 그룹(`runGroup`) 실행 차이
- AGENT/SUPERVISOR/TOOL/APPROVAL 각 타입의 성공·실패 판정 방식
- DB 저장(영속화) 시점 총정리, 종료/중단 경로 7가지 표
- `oracle-to-postgresql` 워크플로우 예시로 실제 값 흐름 워크스루

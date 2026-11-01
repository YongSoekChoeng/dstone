# approvalartifact_의_정

> 생성일시: 2026-11-01 15:36

## 질문

approval.artifact 의 정확한 의미는 무엇인가?

---

## 답변

`approval.artifact`는 **이 승인 단계에서 무엇을 승인하는지 가리키는 이름표**입니다. 그냥 문자열이고, 실행 흐름에는 아무 영향이 없습니다.

**코드 동작**
- **검사:** `artifact`가 비어 있거나 어떤 값이 와도 부팅 검사는 따지지 않습니다. `WorkFlowRegistry`와 `ApprovalStepExecutor`에는 이 값을 읽는 코드가 없습니다.
- **표현식:** 표현식으로 평가하지 않습니다. `${state.x}`라고 적으면 그 글자가 그대로 나갑니다.
- **사용처:** `WorkFlowExecutionService.pendingApproval()`이 YAML 값을 그대로 실어 보내는 것이 전부입니다. 승인 대기 중에 `GET .../executions/{id}`를 호출하면 `pendingApproval.artifact`로 나옵니다.
- **기록:** 실행 기록에 따로 저장하지 않습니다. 승인 결과(`save`)는 `{decision, approver, comment}`뿐이고, 값을 조회할 때마다 현재 등록된 정의에서 읽습니다.

즉 `pilot-workflow.yml`의 `artifact: analysis-document`는 "분석 문서를 승인한다"는 표시일 뿐, 실제 문서 파일과 연결되지 않습니다. 승인자에게 문서 내용을 보여 주는 기능도 아닙니다.

**현재 코드와 설명이 어긋나는 곳 세 군데**

| 위치 | 현재 설명 | 실제 |
|---|---|---|
| `workflow-designer.js:66` 필드 설명 | "승인자에게 보여 줄 값(표현식)" | 표현식이 평가되지 않음 |
| `ApprovalStepDefinition.java:64` 주석, `docs/09.dstone-ai-engine.md:589` | "화면에 보여 주고 기록에 남깁니다" | 실행 기록에 남지 않음 |
| `ApprovalStepDefinition.java:23` 주석, `docs/09.dstone-ai-engine.md:1659` | 화면에 보여 줄 이름 / "화면에 보임" | 승인 화면 `workflow-admin.js`는 `stepId`와 `approverRole`만 표시하고 `artifact`는 표시하지 않음 |

지금은 API 응답을 직접 보는 사람에게만 보이는 값이고, 코드를 읽는 사람에게는 메모 역할을 합니다. 파일은 아무것도 고치지 않았습니다.

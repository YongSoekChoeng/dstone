package net.dstone.boot.ai.vo.admin;

/**
 * dstone-ai-engine의 GET /api/ai/workflow/executions 응답 배열 한 항목과 JSON 모양만 맞춘 VO. 모듈 간
 * 클래스 의존 없이 REST 계약으로만 연동한다(WorkFlowCallResult와 같은 이유). 목록 화면이라 history는 없다 -
 * 필요하면 executionId로 상세(WorkFlowExecutionDetailResult)를 다시 조회한다.
 *
 * @param executionId      실행 id
 * @param workflowId       실행된 Workflow id
 * @param caller           호출한 앱/서비스 식별자(tenant)
 * @param status           실행 상태(RUNNING/WAITING_APPROVAL/DONE/FAILED/CANCELLED)
 * @param currentStepIndex 지금 실행 중이거나 막 끝낸 스텝의 순번
 * @param createdAt        생성 시각
 * @param updatedAt        마지막 상태 변경 시각
 */
public record WorkFlowExecutionSummaryResult(String executionId, String workflowId, String caller, String status, int currentStepIndex, String createdAt, String updatedAt) {
}

package net.dstone.ai.api.dto;

/**
 * /execute(동기)의 응답이다. status는 DONE 또는 WAITING_APPROVAL 둘 중 하나만 온다 - FAILED/ERROR는 컨트롤러가 예외로
 * 바꿔서 던지므로 이 DTO까지 내려오지 않는다. WAITING_APPROVAL이면 message는 비어 있고, executionId로
 * GET /api/ai/workflow/executions/{executionId}를 조회하거나 POST .../decision으로 승인/반려하면 된다.
 *
 * @param status      DONE 또는 WAITING_APPROVAL
 * @param message     Workflow 실행 결과 메시지(WAITING_APPROVAL이면 null)
 * @param sessionId   대화 세션 ID
 * @param workflowId  실행된 Workflow의 id
 * @param executionId 이 실행의 id(승인 대기 시 후속 조회/승인 API 호출에 쓴다)
 */
public record WorkFlowResponse(String status, String message, String sessionId, String workflowId, String executionId) {
}

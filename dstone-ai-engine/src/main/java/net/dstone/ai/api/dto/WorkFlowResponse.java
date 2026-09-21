package net.dstone.ai.api.dto;

/**
 * /execute(동기 실행) 요청에 대한 응답입니다.
 *
 * status에는 DONE 또는 WAITING_APPROVAL 둘 중 하나만 담깁니다 - Workflow가 실패(FAILED)하거나
 * 에러(ERROR)가 나면 컨트롤러가 그 자리에서 예외로 바꿔서 던지기 때문에, 그런 경우는 이 응답까지
 * 내려오지 않습니다. status가 WAITING_APPROVAL이면 message는 비어 있습니다. 이때는 executionId
 * 값으로 GET /api/ai/workflow/executions/{executionId}를 조회해서 진행 상황을 확인하거나,
 * POST .../decision을 호출해서 승인 또는 반려하면 됩니다.
 *
 * @param status      DONE(완료) 또는 WAITING_APPROVAL(승인 대기) 중 하나입니다.
 * @param message     Workflow 실행 결과 메시지입니다. WAITING_APPROVAL 상태라면 null입니다.
 * @param sessionId   대화를 구분하는 세션 ID입니다.
 * @param workflowId  실행된 Workflow의 id입니다.
 * @param executionId 이번 실행을 가리키는 id입니다. 승인 대기 중일 때 후속 조회나 승인 API를 호출할
 *                    때 이 값을 씁니다.
 */
public record WorkFlowResponse(String status, String message, String sessionId, String workflowId, String executionId) {
}

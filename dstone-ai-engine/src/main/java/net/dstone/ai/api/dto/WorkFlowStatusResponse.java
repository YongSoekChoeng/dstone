package net.dstone.ai.api.dto;

/**
 * WorkFlowController의 GET /status/{executionId} 엔드포인트가 돌려주는 응답입니다.
 *
 * status에는 RUNNING(실행 중), WAITING_APPROVAL(승인 대기), DONE(완료), FAILED(실패),
 * CANCELLED(취소) 중 하나가 담깁니다(자세한 값은 runtime.status.WorkFlowExecutionStatus 참고).
 * status가 DONE이면 result가 채워지고, FAILED면 error가 채워집니다. 스텝별 실행 이력처럼 더 자세한
 * 정보가 필요하다면 GET /executions/{executionId}를 대신 쓰면 됩니다.
 *
 * @param executionId 이 실행을 가리키는 식별자입니다.
 * @param status      지금 이 실행이 어떤 상태인지를 나타냅니다.
 * @param result      실행이 성공적으로 끝났을 때의 결과입니다.
 * @param error       실행이 실패했을 때 그 사유입니다.
 */
public record WorkFlowStatusResponse(String executionId, String status, String result, String error) {
}

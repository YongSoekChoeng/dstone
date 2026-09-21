package net.dstone.ai.api.dto;

/**
 * WorkFlowController의 GET /status/{executionId} 엔드포인트 응답이다. status는
 * RUNNING/WAITING_APPROVAL/DONE/FAILED/CANCELLED 중 하나다(runtime.status.WorkFlowExecutionStatus). DONE이면
 * result가, FAILED면 error가 채워진다. 더 자세한 정보(스텝별 이력 등)가 필요하면 GET /executions/{executionId}를 쓴다.
 *
 * @param executionId 실행 id
 * @param status      실행 상태
 * @param result      실행이 성공했을 때의 결과
 * @param error       실행이 실패했을 때의 사유
 */
public record WorkFlowStatusResponse(String executionId, String status, String result, String error) {
}

package net.dstone.ai.api.dto;

/**
 * status는 RUNNING/DONE/FAILED 중 하나다. DONE이면 result가, FAILED면 error가 채워진다.
 *
 * @param jobId 비동기 작업 id
 * @param status 작업 상태(RUNNING/DONE/FAILED)
 * @param result 작업이 성공했을 때의 결과
 * @param error 작업이 실패했을 때의 사유
 */
public record WorkflowStatusResponse(String jobId, String status, String result, String error) {
}

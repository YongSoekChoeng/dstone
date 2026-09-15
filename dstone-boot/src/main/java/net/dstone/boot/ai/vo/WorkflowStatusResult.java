package net.dstone.boot.ai.vo;

/**
 * /ai/workflow/status.do 응답. dstone-ai-engine의 WorkflowStatusResponse와 status 값(RUNNING/DONE/
 * FAILED)은 같지만, dstone-boot ↔ dstone-ai-engine 호출 자체가 실패한 경우(만료된 jobId, 네트워크 오류
 * 등)를 화면에 알려주기 위한 "ERROR" 상태가 하나 더 있다 - 이때는 error에 실패 사유가 담긴다.
 *
 * @param jobId 조회한 비동기 작업 id
 * @param status 작업 상태(RUNNING/DONE/FAILED, 조회 자체가 실패했으면 ERROR)
 * @param result 작업이 성공했을 때의 결과
 * @param error 작업이 실패했거나 조회 자체가 실패했을 때의 사유
 */
public record WorkflowStatusResult(String jobId, String status, String result, String error) {
}

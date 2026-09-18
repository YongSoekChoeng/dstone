package net.dstone.boot.ai.vo;

/**
 * /ai/workflow/status.do 응답. dstone-ai-engine의 실행 상태(RUNNING/WAITING_APPROVAL/DONE/FAILED/
 * CANCELLED)와 값은 같지만, dstone-boot ↔ dstone-ai-engine 호출 자체가 실패한 경우(존재하지 않는
 * executionId, 네트워크 오류 등)를 화면에 알려주기 위한 "ERROR" 상태가 하나 더 있다 - 이때는 error에 실패
 * 사유가 담긴다.
 *
 * @param executionId 조회한 실행 id
 * @param status      실행 상태, 조회 자체가 실패했으면 ERROR
 * @param result      실행이 성공했을 때의 결과
 * @param error       실행이 실패했거나 조회 자체가 실패했을 때의 사유
 */
public record WorkFlowStatusResult(String executionId, String status, String result, String error) {
}

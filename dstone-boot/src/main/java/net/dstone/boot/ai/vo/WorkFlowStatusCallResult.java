package net.dstone.boot.ai.vo;

/**
 * dstone-ai-engine의 GET /api/ai/workflow/executions/{executionId} 응답과 JSON 모양만 맞춘 VO다 - 실제
 * 응답엔 workflowId/variables/history 등이 더 있지만, /ai/workflow/status.do가 화면에 보여주는 데 필요한
 * 필드(executionId/status/resultText/errorMessage)만 골라 받는다(나머지는 Jackson이 조용히 무시한다).
 *
 * @param executionId  실행 id
 * @param status       실행 상태(RUNNING/WAITING_APPROVAL/DONE/FAILED/CANCELLED)
 * @param resultText   실행이 성공했을 때의 결과
 * @param errorMessage 실행이 실패했을 때의 사유
 */
public record WorkFlowStatusCallResult(String executionId, String status, String resultText, String errorMessage) {
}

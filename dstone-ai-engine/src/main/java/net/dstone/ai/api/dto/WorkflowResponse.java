package net.dstone.ai.api.dto;

/**
 * @param message Workflow 실행 결과 메시지
 * @param sessionId 대화 세션 ID
 * @param workflowId 실행된 Workflow의 id
 */
public record WorkflowResponse(String message, String sessionId, String workflowId) {
}

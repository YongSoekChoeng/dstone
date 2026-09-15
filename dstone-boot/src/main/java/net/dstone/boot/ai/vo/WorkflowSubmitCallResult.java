package net.dstone.boot.ai.vo;

/**
 * dstone-ai-engine의 POST /api/ai/workflow/{workflowId}/submit 응답 계약(WorkflowSubmitResponse)과
 * JSON 모양만 맞춘 VO. 모듈 간 클래스 의존 없이 REST 계약으로만 연동한다(WorkflowCallResult와 같은 이유).
 *
 * @param jobId 발급된 비동기 작업 id
 */
public record WorkflowSubmitCallResult(String jobId) {
}

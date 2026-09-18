package net.dstone.boot.ai.vo;

/**
 * dstone-ai-engine의 POST /api/ai/workflow/{workflowId}/submit 응답 계약(WorkFlowSubmitResponse)과
 * JSON 모양만 맞춘 VO. 모듈 간 클래스 의존 없이 REST 계약으로만 연동한다(WorkFlowCallResult와 같은 이유).
 *
 * @param executionId 발급된 실행 id
 */
public record WorkFlowSubmitCallResult(String executionId) {
}

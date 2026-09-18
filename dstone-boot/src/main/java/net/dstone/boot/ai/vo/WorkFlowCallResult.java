package net.dstone.boot.ai.vo;

/**
 * dstone-ai-engine의 POST /api/ai/workflow/{workflowId}/execute 응답 계약(WorkFlowResponse)과
 * JSON 모양만 맞춘 VO. 모듈 간 클래스 의존 없이 REST 계약으로만 연동한다. status/executionId 필드는
 * SqlConvertService가 쓰지 않으므로(oracle-to-postgresql은 APPROVAL 스텝이 없어 항상 DONE이거나 예외) 여기엔 없다.
 */
public record WorkFlowCallResult(String message, String sessionId, String workflowId) {
}

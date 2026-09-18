package net.dstone.ai.api.dto;

/** @param executionId 발급된 실행 id(GET .../executions/{executionId}로 상태를 조회한다) */
public record WorkFlowSubmitResponse(String executionId) {
}

package net.dstone.ai.api.dto;

/**
 * Workflow를 비동기로 제출(/submit)했을 때 바로 돌려받는 응답입니다.
 *
 * @param executionId 이번 실행에 새로 발급된 id입니다. 이 값으로 GET .../executions/{executionId}를
 *                    호출하면 실행이 어떻게 진행되고 있는지 조회할 수 있습니다.
 */
public record WorkFlowSubmitResponse(String executionId) {
}

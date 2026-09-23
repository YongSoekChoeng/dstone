package net.dstone.boot.ai.vo;

/**
 * dstone-ai-engine의 GET /api/ai/chat 응답 한 항목과 JSON 모양을 맞춘 VO다(net.dstone.ai.api.dto.AgentSummary
 * 참고). WorkFlowSummaryCallResult와 같은 이유로, dstone-ai-engine 응답을 그대로 받아 매핑하는 용도로만 쓴다.
 *
 * @param id          Agent 식별자
 * @param description Agent 설명
 */
public record AgentSummaryCallResult(String id, String description) {
}

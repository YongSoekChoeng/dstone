package net.dstone.boot.ai.vo;

/**
 * dstone-ai-engine의 GET /api/ai/workflow 응답 한 항목과 JSON 모양을 맞춘 VO다(net.dstone.ai.api.dto.WorkFlowSummary
 * 참고). WorkFlowStatusCallResult와 같은 이유로, dstone-ai-engine 응답을 그대로 받아 매핑하는 용도로만 쓴다.
 *
 * @param id          Workflow 식별자
 * @param description Workflow 설명
 */
public record WorkFlowSummaryCallResult(String id, String description) {
}

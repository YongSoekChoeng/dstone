package net.dstone.boot.ai.vo;

import java.util.Map;

/**
 * dstone-ai-engine의 GET /api/ai/workflow 응답 한 항목과 JSON 모양을 맞춘 VO다(net.dstone.ai.api.dto.WorkFlowSummary
 * 참고). WorkFlowStatusCallResult와 같은 이유로, dstone-ai-engine 응답을 그대로 받아 매핑하는 용도로만 쓴다.
 *
 * @param id          Workflow 식별자
 * @param description Workflow 설명
 * @param input       실행 요청의 input 모양(JSON Schema). 화면이 input을 글자로 보낼지 JSON 객체로 보낼지 정하는 데 쓴다
 */
public record WorkFlowSummaryCallResult(String id, String description, Map<String, Object> input) {
}

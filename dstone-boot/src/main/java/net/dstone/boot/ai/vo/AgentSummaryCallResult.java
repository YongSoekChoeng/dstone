package net.dstone.boot.ai.vo;

import java.util.Map;

/**
 * dstone-ai-engine의 GET /api/ai/chat 응답 한 항목과 JSON 모양을 맞춘 VO다(net.dstone.ai.api.dto.AgentSummary
 * 참고). WorkFlowSummaryCallResult와 같은 이유로, dstone-ai-engine 응답을 그대로 받아 매핑하는 용도로만 쓴다.
 *
 * @param id          Agent 식별자
 * @param description Agent 설명
 * @param input       Agent가 받는 값의 모양(JSON Schema). object면 화면이 입력창 글자를 JSON으로 읽어 보낸다
 * @param output      Agent가 돌려주는 값의 모양(JSON Schema). string이 아니면 스트리밍 채팅으로 부를 수 없다
 */
public record AgentSummaryCallResult(String id, String description, Map<String, Object> input, Map<String, Object> output) {
}

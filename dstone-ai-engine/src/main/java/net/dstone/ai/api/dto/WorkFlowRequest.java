package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * Workflow 실행 요청에 담을 내용입니다.
 *
 * message와 variables는 모두 실행 컨텍스트의 input 아래에 들어갑니다. message는 input.message가 되고,
 * variables의 각 값은 input.{이름}이 됩니다. step의 input 템플릿은 {{input.message}}, {{input.이름}}으로,
 * Agent의 system prompt는 {message}, {이름}으로 이 값들을 가져다 씁니다. 첫 step이 input을 따로 적지
 * 않았다면 message가 그대로 첫 step의 입력이 됩니다.
 *
 * @param message   Workflow에 넘겨줄 메시지입니다(필수).
 * @param sessionId 대화를 구분하는 세션 ID입니다.
 * @param variables message 외에 Workflow에 넘겨줄 값들입니다(Workflow가 inputs를 선언했다면 그 계약을 지켜야 합니다).
 */
public record WorkFlowRequest(String message, String sessionId, Map<String, Object> variables) {
}

package net.dstone.ai.api.dto;

/**
 * Workflow 실행 요청에 담을 내용입니다.
 *
 * input은 그 Workflow의 input 계약(workflows/*.yml 의 workflow.input, 비워두면 string) 모양이어야 하고,
 * 모양이 다르면 실행하기 전에 400으로 거절합니다. 그대로 실행 컨텍스트의 input이 되어 step의 input 표현식이
 * "${input}"(object면 "${input.필드}")으로 꺼내 씁니다.
 *
 * @param input     Workflow에 넘겨줄 값입니다(필수. 글자, 객체 등 Workflow input 모양).
 * @param sessionId 대화를 구분하는 세션 ID입니다.
 */
public record WorkFlowRequest(Object input, String sessionId) {
}

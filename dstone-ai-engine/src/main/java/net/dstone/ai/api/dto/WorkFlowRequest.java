package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * Workflow 실행 요청에 담을 내용입니다.
 *
 * message는 Workflow의 맨 첫 번째 step에 들어가는 입력값이 됩니다. variables는 그 뒤로 이어지는
 * 각 step의 프롬프트나 Tool 입력 템플릿에 있는 {변수명} 자리를 채우는 값입니다.
 *
 * @param message   Workflow의 첫 step에 넘겨줄 입력 메시지입니다.
 * @param sessionId 대화를 구분하는 세션 ID입니다.
 * @param variables 각 step의 프롬프트나 Tool 입력 템플릿을 채우는 데 쓰는 값들입니다.
 */
public record WorkFlowRequest(String message, String sessionId, Map<String, Object> variables) {
}

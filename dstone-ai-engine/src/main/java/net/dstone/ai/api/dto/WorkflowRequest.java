package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * message는 Workflow 첫 step의 입력이 된다. variables는 각 step의 프롬프트/Tool 입력 템플릿을 채우는 값이다.
 *
 * @param message   Workflow 첫 step의 입력 메시지
 * @param sessionId 대화 세션 ID
 * @param variables 각 step의 프롬프트/Tool 입력 템플릿을 채우는 값
 */
public record WorkflowRequest(String message, String sessionId, Map<String, Object> variables) {
}

package net.dstone.ai.api.dto;

import java.util.Map;

/** message는 Workflow 첫 step의 입력이 된다. variables는 각 step의 프롬프트/Tool 입력 템플릿을 채우는 값이다. */
public record WorkflowRequest(String message, String sessionId, Map<String, Object> variables) {
}

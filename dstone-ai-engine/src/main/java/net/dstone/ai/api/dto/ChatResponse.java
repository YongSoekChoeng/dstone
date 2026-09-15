package net.dstone.ai.api.dto;

/**
 * @param message Agent 응답 메시지
 * @param provider 응답에 사용된 LLM provider 이름
 * @param sessionId 대화 세션 ID
 * @param agent 호출된 Agent 이름
 */
public record ChatResponse(String message, String provider, String sessionId, String agent) {
}

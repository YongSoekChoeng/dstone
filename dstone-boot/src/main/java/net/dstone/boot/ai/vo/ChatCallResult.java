package net.dstone.boot.ai.vo;

/**
 * dstone-ai-engine의 POST /api/ai/chat 응답 계약(ChatResponse)과 JSON 모양만 맞춘 VO.
 * 모듈 간 클래스 의존 없이 REST 계약으로만 연동한다.
 */
public record ChatCallResult(String message, String provider, String sessionId) {
}

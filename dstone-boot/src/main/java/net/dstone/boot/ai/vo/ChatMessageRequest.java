package net.dstone.boot.ai.vo;

/**
 * /ai/chat/sendMessage.do 요청 바디. sessionId는 화면에서 받지 않는다 - dstone-boot가 로그인 사용자
 * ID를 자체 sessionId로 써서 dstone-ai-engine에 넘기므로 클라이언트가 관리할 필요가 없다.
 */
public record ChatMessageRequest(String message, Boolean ragEnabled, Boolean toolsEnabled, String provider) {
}

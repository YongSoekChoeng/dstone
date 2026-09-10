package net.dstone.boot.ai.vo;

/**
 * /ai/chat/sendMessage.do 요청 바디. sessionId는 화면에서 받지 않는다 - dstone-boot가 로그인 사용자
 * ID를 자체 sessionId로 써서 dstone-ai-engine에 넘기므로 클라이언트가 관리할 필요가 없다.
 *
 * ollamaModel은 provider=ollama일 때만 의미가 있다 - dstone-ai-engine의 dstone.ai.gateway.ollama-override.model
 * 기본값 대신 이 요청 한 번만 그 모델로 호출한다(예: "sqlcoder", "llama3.2"). 실제로 Ollama에 pull되어
 * 있는 모델 태그와 정확히 같아야 하고, 채팅을 지원하지 않는 모델(예: 임베딩 전용인 bge-m3)을 넣으면
 * Ollama가 그대로 에러를 반환한다.
 */
public record ChatMessageRequest(String message, Boolean ragEnabled, Boolean toolsEnabled, String provider, String ollamaModel) {
}

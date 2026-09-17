package net.dstone.boot.ai.vo;

/**
 * /ai/chat/sendMessage.do 요청 바디. sessionId는 화면에서 받지 않는다 - dstone-boot가 로그인 사용자
 * ID를 자체 sessionId로 써서 dstone-ai-engine에 넘기므로 클라이언트가 관리할 필요가 없다.
 *
 * model은 비워서 보내면(null/빈 문자열) general-chat Agent 정의값(agents/general-chat.yml의 model, 그마저
 * 없으면 provider 공통 기본값)을 그대로 쓰고, 채워서 보내면 이번 메시지 한 번만 그 모델로 강제한다 - dstone-ai-engine의
 * AgentDefinition.model(Agent별 기본 모델) 기능을 화면에서 재배포 없이 바로 테스트해볼 수 있게 하기 위한 입력칸이다.
 */
public record ChatMessageRequest(String message, Boolean ragEnabled, Boolean toolsEnabled, String model) {
}

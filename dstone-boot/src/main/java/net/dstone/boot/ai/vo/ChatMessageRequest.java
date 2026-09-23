package net.dstone.boot.ai.vo;

/**
 * /ai/chat/sendMessage.do 요청 바디. sessionId는 화면에서 받지 않는다 - dstone-boot가 로그인 사용자
 * ID를 자체 sessionId로 써서 dstone-ai-engine에 넘기므로 클라이언트가 관리할 필요가 없다.
 *
 * agent는 비워서 보내면(null/빈 문자열) 기본 Agent(ChatService.AGENT, sample-general-chat)를 그대로
 * 쓰고, 채워서 보내면(화면의 agent 드롭다운에서 고른 값) 이번 메시지 한 번만 그 Agent로 호출한다 -
 * /ai/chat/list.do(dstone-ai-engine의 GET /api/ai/chat을 그대로 받아온 목록)가 이 드롭다운을 채운다.
 *
 * model은 비워서 보내면(null/빈 문자열) 선택된 Agent 정의값(그마저 없으면 provider 공통 기본값)을
 * 그대로 쓰고, 채워서 보내면 이번 메시지 한 번만 그 모델로 강제한다 - dstone-ai-engine의
 * AgentDefinition.model(Agent별 기본 모델) 기능을 화면에서 재배포 없이 바로 테스트해볼 수 있게 하기 위한 입력칸이다.
 */
public record ChatMessageRequest(String message, String agent, Boolean ragEnabled, Boolean toolsEnabled, String model) {
}

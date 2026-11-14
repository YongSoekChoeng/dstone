package net.dstone.boot.ai.vo;

import java.util.List;

/**
 * /ai/chat/sendMessage.do 요청 바디.
 *
 * input은 선택한 Agent의 input 모양이어야 한다 - 보통은 입력창의 글자 그대로이고, Agent input이 object면
 * 화면이 입력창 글자를 JSON으로 읽어서 객체로 보낸다(/ai/chat/list.do가 돌려주는 input 스키마로 판단). sessionId는 화면에서 받지 않는다 - dstone-boot가 로그인 사용자
 * ID를 자체 sessionId로 써서 dstone-ai-engine에 넘기므로 클라이언트가 관리할 필요가 없다.
 *
 * agent는 비워서 보내면(null/빈 문자열) 기본 Agent(ChatService.AGENT, sample-general-chat)를 그대로
 * 쓰고, 채워서 보내면(화면의 agent 드롭다운에서 고른 값) 이번 메시지 한 번만 그 Agent로 호출한다 -
 * /ai/chat/list.do(dstone-ai-engine의 GET /api/ai/chat을 그대로 받아온 목록)가 이 드롭다운을 채운다.
 *
 * tools는 이번 메시지에서 쓸 Tool 이름이다(화면의 Tool 선택 칸에서 고른 것, /ai/chat/list.do의 tools 가운데).
 * 하나도 고르지 않으면 빈 목록이라 Tool 없이 답한다. dstone-ai-engine은 그 Agent에 붙지 않는 이름이 있으면 400으로 거절한다.
 *
 * model은 비워서 보내면(null/빈 문자열) 선택된 Agent 정의값(그마저 없으면 provider 공통 기본값)을
 * 그대로 쓰고, 채워서 보내면 이번 메시지 한 번만 그 모델로 강제한다 - dstone-ai-engine의
 * AgentDefinition.model(Agent별 기본 모델) 기능을 화면에서 재배포 없이 바로 테스트해볼 수 있게 하기 위한 입력칸이다.
 */
public record ChatMessageRequest(Object input, String agent, List<String> tools, String model) {
}

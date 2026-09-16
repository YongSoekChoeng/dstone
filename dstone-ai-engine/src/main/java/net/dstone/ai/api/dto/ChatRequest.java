package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * sessionId는 비워서 보내도 된다 - 서버가 새로 하나 발급해서 응답의 sessionId 값으로 돌려준다. 이후 같은 대화를 이어가고 싶으면 그 sessionId를 다음 요청에 그대로 담아 보내면
 * 된다(대화 내용은 common.session.RedisChatMemorySession이 Redis에 저장해 관리한다).
 *
 * agent는 필수다 - common.registry.AgentRegistry에 미리 등록해둔(resources/agents/*.yml) Agent 이름이어야 한다. promptName은 항상 그 Agent
 * 정의값 그대로 쓰인다.
 *
 * ragEnabled/toolsEnabled는 비워두면(null) Agent 정의값을 그대로 쓰고, true/false를 명시하면 그 요청 한 번만 Agent 정의값을 무시하고 강제로 켜거나 끈다 - 같은
 * Agent를 쓰면서도 요청마다 RAG/Tool을 켜고 끄고 싶은 화면(예: dstone-boot 채팅 화면의 체크박스)을 위한 것이다.
 *
 * variables는 promptName 템플릿을 렌더링할 때 쓰이는 값이다(common.prompt.PromptTemplateRegistry).
 *
 * @param message      사용자 메시지
 * @param sessionId    대화 세션 ID
 * @param agent        호출할 Agent 이름
 * @param variables    프롬프트 템플릿에 채울 값
 * @param ragEnabled   RAG 사용 여부 override
 * @param toolsEnabled Tool 사용 여부 override
 */
public record ChatRequest(String message, String sessionId, String agent, Map<String, Object> variables, Boolean ragEnabled, Boolean toolsEnabled) {
}

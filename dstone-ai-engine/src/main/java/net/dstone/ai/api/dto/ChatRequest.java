package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * sessionId는 비워서 보내도 된다 - 서버가 새로 하나 발급해서 응답의 sessionId 값으로 돌려준다. 이후
 * 같은 대화를 이어가고 싶으면 그 sessionId를 다음 요청에 그대로 담아 보내면 된다(대화 내용은
 * common.session.RedisChatMemorySession이 Redis에 저장해 관리한다).
 *
 * agent는 필수다 - common.registry.AgentRegistry에 미리 등록해둔(resources/agents/*.yml) Agent
 * 이름이어야 한다. 그 Agent의 promptName/toolsEnabled/ragEnabled 설정 그대로 호출된다 - 요청에서
 * 이 셋을 따로 켜고 끌 수는 없다(호출할 때마다 다른 조합이 필요하면 Agent를 하나 더 등록한다).
 *
 * variables는 promptName 템플릿을 렌더링할 때 쓰이는 값이다(common.prompt.PromptTemplateRegistry).
 */
public record ChatRequest(String message, String sessionId, String agent, Map<String, Object> variables) {
}

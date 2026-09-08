package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * sessionId를 비워두면 서버가 새로 발급해서 응답({@link ChatResponse#sessionId()})에 담아준다.
 * 이후 같은 대화를 이어가려면 그 sessionId를 다음 요청에 그대로 실어 보내면 된다
 * (히스토리는 net.dstone.ai.session.RedisChatMemoryRepository에 저장됨).
 *
 * promptName을 지정하면 net.dstone.ai.prompt.PromptTemplateRegistry가 해당 템플릿을
 * variables로 렌더링해 시스템 프롬프트로 사용한다.
 */
public record ChatRequest(String message, String sessionId, String promptName, Map<String, Object> variables) {
}

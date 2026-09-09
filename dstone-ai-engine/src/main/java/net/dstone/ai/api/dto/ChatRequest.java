package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * sessionId를 비워두면 서버가 새로 발급해서 응답({@link ChatResponse#sessionId()})에 담아준다.
 * 이후 같은 대화를 이어가려면 그 sessionId를 다음 요청에 그대로 실어 보내면 된다
 * (히스토리는 net.dstone.ai.session.RedisChatMemoryRepository에 저장됨).
 *
 * promptName을 지정하면 net.dstone.ai.prompt.PromptTemplateRegistry가 해당 템플릿을
 * variables로 렌더링해 시스템 프롬프트로 사용한다.
 *
 * ragEnabled를 true로 보내면(dstone.ai.rag.enabled=true로 RAG가 켜져 있어야 함) VectorStore에서
 * message와 유사한 문서 조각을 찾아 QuestionAnswerAdvisor로 자동으로 컨텍스트에 끼워넣는다
 * (검색 자체를 직접 다루려면 /api/ai/rag/search를 쓴다).
 *
 * toolsEnabled를 true로 보내면(Phase 3) net.dstone.ai.config.ConfigTool에 등록된 Tool들을
 * ChatClient에 붙인다 - 이후 실제로 어떤 Tool을 호출할지, 몇 번 호출할지는 LLM과 Spring AI의
 * ChatClient가 알아서 주고받으며 처리한다(사람이 미리 정해두는 게 아님, tool_choice=auto).
 *
 * requiredTool을 지정하면(ConfigTool에 등록된 @AiTool 메서드명과 정확히 일치해야 함) 위 auto 판단을
 * 건너뛰고 Anthropic의 tool_choice=tool을 걸어 해당 Tool을 반드시 한 번 호출하도록 강제한다
 * (toolsEnabled 값과 무관하게 동작하며, 현재는 spring.ai.model.chat=anthropic일 때만 지원한다).
 */
public record ChatRequest(String message, String sessionId, String promptName, Map<String, Object> variables,
		Boolean ragEnabled, Boolean toolsEnabled, String requiredTool) {
}

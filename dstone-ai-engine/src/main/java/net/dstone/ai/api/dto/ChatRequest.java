package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * sessionId는 비워서 보내도 된다 - 서버가 새로 하나 발급해서 응답의 sessionId 값으로 돌려준다.
 * 이후 같은 대화를 이어가고 싶으면 그 sessionId를 다음 요청에 그대로 담아 보내면 된다(대화 내용은
 * net.dstone.ai.session.RedisChatMemoryRepository가 Redis에 저장해 관리한다).
 *
 * promptName을 지정하면 net.dstone.ai.prompt.PromptTemplateRegistry가 그 이름의 템플릿을
 * variables로 채워 렌더링한 뒤 시스템 프롬프트로 써준다.
 *
 * ragEnabled를 true로 보내면(사전에 dstone.ai.rag.enabled=true로 RAG를 켜둬야 한다) VectorStore에서
 * message와 비슷한 문서 조각을 찾아 QuestionAnswerAdvisor가 자동으로 컨텍스트에 끼워 넣어준다.
 * 검색 결과만 따로 확인해보고 싶을 때는 /api/ai/rag/search를 쓰면 된다.
 *
 * toolsEnabled를 true로 보내면(Phase 3) net.dstone.ai.config.ConfigTool에 등록된 Tool들을
 * ChatClient에 붙여준다. 실제로 어떤 Tool을 언제, 몇 번 호출할지는 사람이 미리 정해두는 게 아니라
 * LLM과 Spring AI의 ChatClient가 대화를 주고받으며 그때그때 알아서 판단한다(tool_choice=auto).
 *
 * requiredTool을 지정하면(ConfigTool에 등록된 @AiTool 메서드 이름과 정확히 같아야 한다) 방금 말한
 * auto 판단을 건너뛰고 Anthropic의 tool_choice=tool로 그 Tool을 반드시 한 번은 호출하게 만든다.
 * toolsEnabled 값과는 상관없이 동작하고, 지금은 spring.ai.model.chat=anthropic일 때만 지원한다.
 */
public record ChatRequest(String message, String sessionId, String promptName, Map<String, Object> variables,
		Boolean ragEnabled, Boolean toolsEnabled, String requiredTool) {
}

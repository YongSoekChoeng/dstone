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
 *
 * provider를 지정하면 spring.ai.model.chat으로 정해진 기본 provider 대신 그 provider로 라우팅한다.
 * 값은 gateway.AiProvider의 propertyValue(anthropic/openai/ollama)와 같아야 한다. 다만 실제로
 * 기본값과 별개로 항상 띄워둘 수 있는 override 빈이 있는 provider만 지원되는데, 지금은 ollama뿐이다
 * (dstone.ai.gateway.ollama-override.enabled=true일 때만 사용 가능 - net.dstone.ai.config.ConfigOllamaOverride
 * 참고). 비워두면 기존과 동일하게 기본 provider를 쓴다.
 *
 * model을 지정하면(provider=ollama일 때만 의미 있다) dstone.ai.gateway.ollama-override.model에 고정된
 * 기본 모델 대신 이 요청 한 번만 그 모델로 호출한다(예: "sqlcoder", "llama3.2"). 실제로 Ollama에
 * pull되어 있는 모델 태그와 정확히 같아야 하고, 채팅을 지원하지 않는 모델(예: 임베딩 전용인 bge-m3)을
 * 넣으면 Ollama가 그대로 에러를 반환한다 - 이 필드는 어떤 모델명이 유효한지 검증하지 않는다.
 */
public record ChatRequest(String message, String sessionId, String promptName, Map<String, Object> variables,
		Boolean ragEnabled, Boolean toolsEnabled, String requiredTool, String provider, String model) {
}

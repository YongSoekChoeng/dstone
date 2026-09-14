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
 * toolsEnabled를 true로 보내면(Phase 3) net.dstone.ai.common.config.ConfigTool에 등록된 Tool들을
 * ChatClient에 붙여준다. 실제로 어떤 Tool을 언제, 몇 번 호출할지는 사람이 미리 정해두는 게 아니라
 * LLM과 Spring AI의 ChatClient가 대화를 주고받으며 그때그때 알아서 판단한다(tool_choice=auto).
 *
 * requiredTool/provider/ollamaModel 세 필드는 아래에 적힌 대로 동작하도록 "의도"하고 만들어 둔
 * 자리이지만, 2026-09-11 gateway/RAG 재설계 이후 net.dstone.ai.api.service.ChatService.getSpec()가
 * 이 세 필드를 전혀 읽지 않는다 - 즉 지금은 요청에 넣어 보내도 조용히 무시된다(docs/09.dstone-ai-engine.md
 * 7.6절 참고). 아래 설명은 구현 예정 사양이지 현재 동작이 아니다.
 *
 * requiredTool을 지정하면(ConfigTool에 등록된 @AiTool 메서드 이름과 정확히 같아야 한다) 방금 말한
 * auto 판단을 건너뛰고 provider의 tool_choice 강제 옵션(예: Anthropic이면 tool_choice=tool)으로 그
 * Tool을 반드시 한 번은 호출하게 만들 계획이다. toolsEnabled 값과는 상관없이 동작할 예정이다.
 *
 * provider를 지정하면 spring.ai.model.chat으로 정해진 기본 provider 대신 그 provider로 라우팅할
 * 계획이다. 값은 net.dstone.ai.common.consts.AiProvider의 propertyValue(anthropic/openai/ollama)와
 * 같아야 한다. 비워두면 기존과 동일하게 기본 provider를 쓴다.
 *
 * ollamaModel을 지정하면(provider=ollama일 때만 의미 있다) 이 요청 한 번만 그 모델로 호출할 계획이다
 * (예: "sqlcoder", "llama3.2"). 실제로 Ollama에 pull되어 있는 모델 태그와 정확히 같아야 하고,
 * 채팅을 지원하지 않는 모델(예: 임베딩 전용인 bge-m3)을 넣으면 Ollama가 그대로 에러를 반환할 것이다
 * - 이 필드는 어떤 모델명이 유효한지 검증하지 않을 예정이다.
 *
 * capability를 지정하면 "이 요청이 뭘 하려는 건지" 이름 하나로 알려줄 수 있다(예:
 * "oracle-to-postgresql"). net.dstone.ai.capability.CapabilityRegistry에 미리 등록해둔 대로
 * promptName/toolsEnabled/ragEnabled를 대신 채워준다 - 매번 이 조합을 직접 만들 필요가 없어진다.
 * 등록 안 된 이름을 보내면 그 자리에서 바로 에러가 난다(promptName에 없는 템플릿 이름을 넣었을 때와
 * 동일하게 처리됨). 이 필드는 그냥 "자주 쓰는 조합에 이름 붙인 것"일 뿐이라, promptName/toolsEnabled/
 * ragEnabled를 요청에 직접 넣으면 그 값이 항상 우선한다 - 그래서
 * 지금까지 쓰던 일반 채팅 방식은 이 필드와 상관없이 그대로 동작한다.
 */
public record ChatRequest(String message, String sessionId, String capability, Map<String, Object> variables,
		Boolean ragEnabled, Boolean toolsEnabled, String requiredTool, String provider, String ollamaModel) {
}

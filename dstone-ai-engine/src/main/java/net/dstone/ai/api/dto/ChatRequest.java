package net.dstone.ai.api.dto;

import java.util.List;

/**
 * POST /api/ai/chat, POST /api/ai/chat/stream 요청에 담을 내용입니다.
 *
 * sessionId는 비워서 보내도 괜찮습니다. 비워서 보내면 서버가 새로 하나 만들어서 응답의 sessionId
 * 값으로 돌려줍니다. 그다음부터 같은 대화를 이어가고 싶다면, 그 sessionId 값을 다음 요청에도 똑같이
 * 담아서 보내면 됩니다. 대화 내용은 common.session.RedisChatMemoryRepository이 Redis에 저장해서
 * 관리합니다.
 *
 * agent는 꼭 있어야 하는 값입니다. common.registry.AgentRegistry에 미리 등록해 둔 Agent(즉
 * conf/definitions/agents/**.md 파일로 만들어 둔 Agent)의 이름을 넣어야 합니다.
 *
 * tools는 이번 요청에서 쓸 Tool 이름입니다. 비워 두면(null) Agent 정의(tools.allowed) 그대로 쓰고,
 * 빈 목록([])이면 Tool 없이 부릅니다. 이름은 그 Agent에 실제로 붙는 Tool(GET /api/ai/chat의 availableTools) 가운데서만
 * 고를 수 있습니다. 그 밖의 이름이 있으면 400으로 거절합니다(Agent에 허락되지 않은 Tool을 요청으로 켤 수 없습니다).
 * dstone-boot의 채팅 화면이 Agent를 고르듯 Tool을 고르게 하는 것이 이 값입니다.
 * 올린 문서(개발 가이드 등)를 찾게 하려면 knowledgeListDocs / knowledgeSearchDocs / knowledgeGetChunks를 고릅니다.
 *
 * model도 같은 방식으로 동작합니다. 비워 두면(null이거나 빈 문자열) Agent 정의에 적힌 model 값을
 * 쓰고(그마저 없으면 provider 공통 기본 모델을 씁니다), 값을 채우면 이번 요청 한 번만 그 모델을
 * 강제로 씁니다(자세한 우선순위는 common.definition.agent.AgentDefinition.model을 참고하세요). 다만 이건
 * 이 Agent가 쓰는 provider(model.provider, 없으면 spring.ai.model.chat) 안에서만 모델을 바꾸는 기능입니다. 만약 다른
 * provider의 모델 이름을 넣으면, 이 요청을 처리하는 시점에 그 provider의 API가 에러를 냅니다.
 *
 * input은 그 Agent의 input 계약(agents/**.md 의 input, 비워두면 string) 모양이어야 합니다.
 * 모양이 다르면 400으로 거절합니다.
 *
 * @param input        Agent에게 넣을 값입니다(필수. 보통은 사용자가 입력한 글자, Agent input이 object면 객체).
 * @param sessionId    대화를 구분하는 세션 ID입니다. 비워 보내면 서버가 새로 발급합니다.
 * @param agent        호출할 Agent의 이름입니다. 반드시 있어야 합니다.
 * @param tools        이번 요청에서 쓸 Tool 이름입니다(비우면 Agent 정의 그대로, 빈 목록이면 Tool 없음).
 * @param model        이번 요청에서만 쓸 모델명을 강제로 지정하고 싶을 때 씁니다(비우면 Agent 정의값 또는 provider 기본값을 사용).
 */
public record ChatRequest(Object input, String sessionId, String agent, List<String> tools, String model) {
}

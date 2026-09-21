package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * POST /api/ai/chat, POST /api/ai/chat/stream 요청에 담을 내용입니다.
 *
 * sessionId는 비워서 보내도 괜찮습니다. 비워서 보내면 서버가 새로 하나 만들어서 응답의 sessionId
 * 값으로 돌려줍니다. 그다음부터 같은 대화를 이어가고 싶다면, 그 sessionId 값을 다음 요청에도 똑같이
 * 담아서 보내면 됩니다. 대화 내용은 common.session.RedisChatMemorySession이 Redis에 저장해서
 * 관리합니다.
 *
 * agent는 꼭 있어야 하는 값입니다. common.registry.AgentRegistry에 미리 등록해 둔 Agent(즉
 * resources/agents/*.yml 파일로 만들어 둔 Agent)의 이름을 넣어야 합니다.
 *
 * ragEnabled와 toolsEnabled는 둘 다 비워 두면(null) Agent 정의에 적힌 기본값을 그대로 씁니다.
 * true나 false를 직접 넣으면, 이번 요청 한 번만 Agent 정의값을 무시하고 그 값을 강제로 적용합니다.
 * 예를 들어 dstone-boot의 채팅 화면에서 RAG/Tool 체크박스를 켰다 껐다 할 수 있는 것도 이 기능
 * 덕분입니다 - 같은 Agent를 쓰면서도 요청마다 RAG/Tool 사용 여부를 다르게 줄 수 있습니다.
 *
 * model도 같은 방식으로 동작합니다. 비워 두면(null이거나 빈 문자열) Agent 정의에 적힌 model 값을
 * 쓰고(그마저 없으면 provider 공통 기본 모델을 씁니다), 값을 채우면 이번 요청 한 번만 그 모델을
 * 강제로 씁니다(자세한 우선순위는 common.definition.AgentDefinition.model을 참고하세요). 다만 이건
 * 지금 활성화된 provider(spring.ai.model.chat 설정값) 안에서만 모델을 바꾸는 기능입니다. 만약 다른
 * provider의 모델 이름을 넣으면, 이 요청을 처리하는 시점에 그 provider의 API가 에러를 냅니다.
 *
 * variables는 Agent의 prompt(resources/agents/*.yml 안에 직접 써 있는 프롬프트 원문)에
 * {변수명} 같은 형태의 토큰이 있을 때, 그 자리를 실제 값으로 채워 넣기 위한 값입니다.
 *
 * @param message      사용자가 입력한 메시지입니다.
 * @param sessionId    대화를 구분하는 세션 ID입니다. 비워 보내면 서버가 새로 발급합니다.
 * @param agent        호출할 Agent의 이름입니다. 반드시 있어야 합니다.
 * @param variables    프롬프트의 {변수명} 자리에 채워 넣을 값들입니다.
 * @param ragEnabled   이번 요청에서만 RAG 사용 여부를 강제로 지정하고 싶을 때 씁니다(비우면 Agent 정의값을 그대로 사용).
 * @param toolsEnabled 이번 요청에서만 Tool 사용 여부를 강제로 지정하고 싶을 때 씁니다(비우면 Agent 정의값을 그대로 사용).
 * @param model        이번 요청에서만 쓸 모델명을 강제로 지정하고 싶을 때 씁니다(비우면 Agent 정의값 또는 provider 기본값을 사용).
 */
public record ChatRequest(String message, String sessionId, String agent, Map<String, Object> variables, Boolean ragEnabled, Boolean toolsEnabled, String model) {
}

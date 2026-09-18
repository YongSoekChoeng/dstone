package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * sessionId는 비워서 보내도 된다 - 서버가 새로 하나 발급해서 응답의 sessionId 값으로 돌려준다. 이후 같은 대화를 이어가고 싶으면 그 sessionId를 다음 요청에 그대로 담아 보내면
 * 된다(대화 내용은 common.session.RedisChatMemorySession이 Redis에 저장해 관리한다).
 *
 * agent는 필수다 - common.registry.AgentRegistry에 미리 등록해둔(resources/agents/*.yml) Agent 이름이어야 한다.
 *
 * ragEnabled/toolsEnabled는 비워두면(null) Agent 정의값을 그대로 쓰고, true/false를 명시하면 그 요청 한 번만 Agent 정의값을 무시하고 강제로 켜거나 끈다 - 같은
 * Agent를 쓰면서도 요청마다 RAG/Tool을 켜고 끄고 싶은 화면(예: dstone-boot 채팅 화면의 체크박스)을 위한 것이다.
 *
 * model도 같은 방식이다 - 비워두면(null/빈 문자열) Agent 정의(agents/*.yml의 model, 그마저 없으면 provider 공통 기본값)를 그대로 쓰고, 값을 채우면 그
 * 요청 한 번만 그 모델로 강제한다(common.definition.AgentDefinition.model 참고). 지금 활성화된 provider(spring.ai.model.chat) 안에서 모델만
 * 바꾸는 것이라, 다른 provider의 모델명을 넣으면 이 요청 시점에 그 provider API가 에러를 낸다.
 *
 * variables는 Agent의 prompt(resources/agents/*.yml에 인라인)에 {변수명} 토큰이 있을 때 그 자리를 채우는 값이다.
 *
 * @param message      사용자 메시지
 * @param sessionId    대화 세션 ID
 * @param agent        호출할 Agent 이름
 * @param variables    프롬프트 템플릿에 채울 값
 * @param ragEnabled   RAG 사용 여부 override
 * @param toolsEnabled Tool 사용 여부 override
 * @param model        모델명 override
 */
public record ChatRequest(String message, String sessionId, String agent, Map<String, Object> variables, Boolean ragEnabled, Boolean toolsEnabled, String model) {
}

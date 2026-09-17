package net.dstone.ai.api.dto;

/**
 * @param message   Agent 응답 메시지
 * @param provider  응답에 사용된 LLM provider 이름
 * @param sessionId 대화 세션 ID
 * @param agent     호출된 Agent 이름
 * @param model     실제로 이 응답에 쓰인 모델명(ChatRequest.model() > AgentDefinition.model() > provider 공통 기본값 순으로 해석된 결과 -
 *                  runtime.agent.AgentExecutor가 고르는 우선순위와 동일하다). ChatController.chat()이 계산해서 채운다.
 */
public record ChatResponse(String message, String provider, String sessionId, String agent, String model) {
}

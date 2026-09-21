package net.dstone.ai.api.dto;

/**
 * POST /api/ai/chat, POST /api/ai/chat/stream 요청에 대한 응답입니다.
 *
 * @param message   Agent가 돌려준 응답 메시지입니다.
 * @param provider  이번 응답을 만드는 데 쓰인 LLM provider(예: anthropic, openai)의 이름입니다.
 * @param sessionId 이 대화를 구분하는 세션 ID입니다.
 * @param agent     이번에 호출된 Agent의 이름입니다.
 * @param model     이번 응답에 실제로 쓰인 모델 이름입니다. ChatRequest.model() 값이 있으면 그걸 쓰고, 없으면
 *                  AgentDefinition.model() 값을, 그것도 없으면 provider의 공통 기본 모델을 쓰는 순서로
 *                  정해집니다(runtime.agent.AgentExecutor가 실제 호출 시 쓰는 우선순위와 동일합니다). 이 값은
 *                  ChatController.chat()이 계산해서 채워 넣습니다.
 */
public record ChatResponse(String message, String provider, String sessionId, String agent, String model) {
}

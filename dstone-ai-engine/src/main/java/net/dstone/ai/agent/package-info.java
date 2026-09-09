/**
 * Tool/Function calling(Phase 3): {@link net.dstone.ai.agent.tool}의 {@code @AiTool} 등록 체계 +
 * {@link net.dstone.ai.agent.tool.ToolRegistry}가 만든 {@code ToolCallbackProvider}를
 * {@code ChatController}가 {@code toolsEnabled}일 때 ChatClient에 붙이는 구조다.
 *
 * "오케스트레이션"은 별도 워크플로우/그래프 엔진을 직접 만드는 게 아니라, Spring AI의 ChatClient가
 * 이미 제공하는 tool-calling 루프(LLM이 Tool 호출을 요청 → 실행 → 결과를 다시 LLM에 넘겨 최종 답변을
 * 만들 때까지 반복)를 그대로 쓰는 "단순" 수준으로 한정한다. 대화 히스토리(memory)는 이미 Phase 1의
 * {@link net.dstone.ai.session}이 담당하고 있어 tool-calling 루프도 같은 ChatMemory를 그대로 공유한다 -
 * 이 Phase에서 별도로 memory를 새로 만들지 않는다.
 */
package net.dstone.ai.agent;

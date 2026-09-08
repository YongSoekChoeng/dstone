/**
 * LLM Provider 게이트웨이 — OpenAI/Anthropic/사내 로컬모델(vLLM, Ollama 등)을
 * 설정만으로 교체 가능하게 추상화한다(Phase 1).
 * (Azure OpenAI는 Spring AI 2.x에서 chat model provider로 제거되어 더 이상 지원하지 않는다.)
 *
 * 실제 provider별 ChatModel 구현은 각 Spring AI provider starter의 자동설정이 담당하고,
 * spring.ai.model.chat 프로퍼티 하나로 그중 하나만 활성화된다({@link net.dstone.ai.gateway.AiProvider}).
 * {@link net.dstone.ai.gateway.GatewayProperties}가 그 값을 검증하고 다른 패키지에 노출한다.
 */
package net.dstone.ai.gateway;

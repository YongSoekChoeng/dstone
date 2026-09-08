/**
 * provider별(OpenAI/Anthropic/local) 어댑터 구현체(Phase 1).
 * (Azure OpenAI는 Spring AI 2.x에서 chat model provider로 제거되어 더 이상 지원하지 않는다.)
 *
 * anthropic/openai/ollama는 각각 spring-ai-starter-model-* 의 자동설정이
 * ChatModel 어댑터 역할을 이미 하고 있어 이 패키지에 별도 구현을 두지 않는다
 * (application.yml + {@link net.dstone.ai.gateway.AiProvider} 참고).
 *
 * local vLLM처럼 starter가 없는 OpenAI 호환 서버는 provider는 openai로 두고
 * spring.ai.openai.base-url만 vLLM 엔드포인트로 override해서 재사용한다.
 * 이 패키지는 향후 그런 provider별 커스터마이징(요청/응답 인터셉터, 모델명 기본값 등)이
 * 실제로 필요해졌을 때 그 구현을 담는 자리로 남겨둔다.
 */
package net.dstone.ai.gateway.provider;

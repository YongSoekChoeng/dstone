/**
 * 임베딩 provider 검증(Phase 2) - 실제 임베딩 생성은 gateway 패키지와 동일한 철학으로,
 * spring.ai.model.embedding 값(openai/ollama)에 따라 해당 spring-ai-starter-model-* 의
 * 자동설정이 담당한다({@link net.dstone.ai.rag.embedding.EmbeddingProperties} 참고).
 * anthropic은 임베딩 모델이 없어 {@link net.dstone.ai.rag.embedding.EmbeddingProvider}에 없다.
 */
package net.dstone.ai.rag.embedding;

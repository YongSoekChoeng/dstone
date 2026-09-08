/**
 * RAG(Retrieval-Augmented Generation) 파이프라인(Phase 2): 문서 적재({@link net.dstone.ai.rag.ingest})
 * → 임베딩 provider 검증({@link net.dstone.ai.rag.embedding}) → VectorStore 검색
 * ({@link net.dstone.ai.rag.retrieval}). VectorStore 자체(pgvector)는 spring-ai의
 * PgVectorStoreAutoConfiguration이 DataSource(spring.datasource.*)와 EmbeddingModel
 * (spring.ai.model.embedding) 빈으로부터 자동설정한다.
 *
 * 전부 dstone.ai.rag.enabled=true일 때만 활성화된다 - 이 엔진을 가져다 쓰는 SI 프로젝트 중
 * RAG가 필요 없는 경우 이 설정을 안 켜면 Postgres/pgvector/임베딩 설정 없이도 그대로 기동된다.
 */
package net.dstone.ai.rag;

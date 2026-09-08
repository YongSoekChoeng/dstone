/**
 * VectorStore(pgvector) 연동 및 유사도 검색(Phase 2). {@link net.dstone.ai.rag.retrieval.RetrievalService}가
 * net.dstone.ai.api.RagController의 단독 검색 API와 net.dstone.ai.api.ChatController의
 * RAG-증강 채팅(QuestionAnswerAdvisor) 양쪽에서 공통으로 쓰인다.
 * dstone.ai.rag.enabled=true일 때만 빈이 활성화된다.
 */
package net.dstone.ai.rag.retrieval;

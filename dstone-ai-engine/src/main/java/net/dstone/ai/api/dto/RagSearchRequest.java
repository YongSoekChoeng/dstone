package net.dstone.ai.api.dto;

/**
 * topK/similarityThreshold/sourceId를 비워두면 net.dstone.ai.rag.retrieval.RetrievalService의
 * 기본값(dstone.ai.rag.retrieval.*)이 쓰인다. sourceId를 지정하면 그 문서로 적재된 청크로만 검색을 좁힌다.
 */
public record RagSearchRequest(String query, Integer topK, Double similarityThreshold, String sourceId) {
}

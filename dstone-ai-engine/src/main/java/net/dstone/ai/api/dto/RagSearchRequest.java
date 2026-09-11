package net.dstone.ai.api.dto;

/**
 * topK/similarityThreshold/sourceId는 비워둬도 된다 - 비우면 net.dstone.ai.api.service.RagService의
 * 기본값(dstone.ai.rag.retrieval.* 설정)을 그대로 쓴다. sourceId를 지정하면 그 문서로 적재된 청크로만
 * 검색 범위를 좁혀준다.
 */
public record RagSearchRequest(String query, Integer topK, Double similarityThreshold, String sourceId) {
}

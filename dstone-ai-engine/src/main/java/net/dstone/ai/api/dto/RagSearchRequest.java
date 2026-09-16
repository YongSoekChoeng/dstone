package net.dstone.ai.api.dto;

/**
 * topK/similarityThreshold/sourceId는 비워둬도 된다 - 비우면 rag.RagService의 기본값 (dstone.ai.rag.retrieval.* 설정)을 그대로 쓴다.
 * sourceId를 지정하면 그 문서로 적재된 청크로만 검색 범위를 좁혀준다.
 *
 * @param query               검색어
 * @param topK                검색 결과 최대 개수
 * @param similarityThreshold 검색 결과 유사도 임계값
 * @param sourceId            검색 범위를 좁힐 문서 식별자
 */
public record RagSearchRequest(String query, Integer topK, Double similarityThreshold, String sourceId) {
}

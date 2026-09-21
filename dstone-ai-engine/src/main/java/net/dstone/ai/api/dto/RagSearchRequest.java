package net.dstone.ai.api.dto;

/**
 * POST /api/ai/rag/search 요청에 담을 내용입니다.
 *
 * topK, similarityThreshold, sourceId는 비워서 보내도 괜찮습니다. 비우면 common.rag.RagRetrievalChain에
 * 정해져 있는 기본값(dstone.ai.rag.retrieval.* 설정)을 그대로 씁니다. sourceId 값을 채우면, 그 문서로
 * 적재된 청크로만 검색 범위를 좁혀서 찾아줍니다.
 *
 * @param query               검색하고 싶은 내용을 담은 검색어입니다.
 * @param topK                검색 결과를 최대 몇 개까지 돌려받을지 정합니다.
 * @param similarityThreshold 검색 결과로 인정할 최소 유사도 값입니다. 이보다 유사도가 낮은 결과는 걸러집니다.
 * @param sourceId            검색 범위를 특정 문서 하나로 좁히고 싶을 때 그 문서의 식별자를 넣습니다.
 */
public record RagSearchRequest(String query, Integer topK, Double similarityThreshold, String sourceId) {
}

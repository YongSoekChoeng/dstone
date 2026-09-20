package net.dstone.ai.api.dto;

/**
 * api.service.EmbedService.listSources()가 돌려주는, vector_store에 적재된 문서 하나(sourceId 기준)의 요약.
 *
 * @param sourceId   문서 논리 식별자
 * @param tenant     적재 시 태깅된 tenant(caller). 없으면 null
 * @param chunkCount 이 sourceId로 적재된 청크 개수
 */
public record DocumentSourceSummary(String sourceId, String tenant, long chunkCount) {
}

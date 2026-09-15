package net.dstone.ai.api.dto;

/**
 * @param sourceId 적재된 문서 식별자
 * @param chunkCount 적재된 청크 개수
 */
public record IngestResponse(String sourceId, int chunkCount) {
}

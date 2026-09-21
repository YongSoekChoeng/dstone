package net.dstone.ai.api.dto;

/**
 * 문서 하나를 vector_store에 적재하고 난 뒤 돌려주는 결과입니다.
 *
 * @param sourceId   이번에 적재한 문서를 가리키는 식별자입니다.
 * @param chunkCount 이번에 적재하면서 문서를 몇 개의 청크(조각)로 나눴는지를 나타냅니다.
 */
public record IngestResponse(String sourceId, int chunkCount) {
}

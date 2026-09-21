package net.dstone.ai.api.dto;

/**
 * vector_store에 적재되어 있는 문서 하나를 sourceId 기준으로 요약한 값입니다.
 * api.service.EmbedService.listSources()를 호출하면 이 요약 목록을 돌려받습니다.
 *
 * @param sourceId   이 문서를 가리키는 논리적인 식별자입니다.
 * @param tenant     문서를 적재할 때 함께 붙여둔 tenant(caller) 값입니다. 태깅된 게 없으면 null입니다.
 * @param chunkCount 이 sourceId로 적재된 청크(문서를 잘게 쪼갠 조각)의 개수입니다.
 */
public record DocumentSourceSummary(String sourceId, String tenant, long chunkCount) {
}

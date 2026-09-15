package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * @param text 검색된 청크의 본문 텍스트
 * @param metadata 청크에 붙은 메타데이터(tenant 등)
 * @param score 검색 유사도 점수
 */
public record RetrievedChunk(String text, Map<String, Object> metadata, Double score) {
}

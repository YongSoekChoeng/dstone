package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * RAG 검색으로 찾아낸 청크(문서 조각) 하나를 담는 값입니다.
 *
 * @param text     검색된 청크의 실제 본문 텍스트입니다.
 * @param metadata 이 청크에 붙어 있는 메타데이터입니다(예: tenant 값 등).
 * @param score    검색어와 이 청크가 얼마나 비슷한지를 나타내는 유사도 점수입니다.
 */
public record RetrievedChunk(String text, Map<String, Object> metadata, Double score) {
}

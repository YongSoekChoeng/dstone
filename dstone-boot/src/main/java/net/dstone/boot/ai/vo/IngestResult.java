package net.dstone.boot.ai.vo;

/**
 * dstone-ai-engine의 POST /api/ai/rag/documents 응답 계약(IngestResponse)과 JSON 모양만 맞춘 VO.
 * 모듈 간 클래스 의존 없이 REST 계약으로만 연동한다.
 */
public record IngestResult(String sourceId, int chunkCount) {
}

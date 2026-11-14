package net.dstone.boot.ai.vo;

/**
 * 채팅 화면이 고를 수 있는 Tool 한 개. dstone-ai-engine의 GET /api/ai/chat 응답의 availableTools 항목과
 * JSON 모양을 맞췄다(net.dstone.ai.api.dto.ToolSummary 참고). 화면의 "Tool 찾기" 창이 종류별 표로 보여 준다.
 *
 * @param name        Tool 이름. 채팅 요청의 tools에 이 이름을 넣는다
 * @param description Tool 설명
 * @param category    종류: CODE_RAG(소스 RAG), DOC_RAG(문서 RAG), MCP, GENERAL(일반)
 */
public record ToolSummaryResult(String name, String description, String category) {
}

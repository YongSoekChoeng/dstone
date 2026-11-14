package net.dstone.ai.api.dto;

/**
 * <pre>
 * 채팅 화면이 고를 수 있는 Tool 한 개의 요약입니다(GET /api/ai/chat 응답의 availableTools 항목).
 * 화면의 "Tool 찾기" 창이 종류별 표로 보여 주는 데 씁니다.
 * </pre>
 *
 * @param name        Tool 이름. 채팅 요청의 tools에 이 이름을 넣습니다
 * @param description Tool 설명(LLM에게 보이는 설명 그대로)
 * @param category    종류(common.annotation.ToolCategory 이름): CODE_RAG, DOC_RAG, MCP, GENERAL
 */
public record ToolSummary(String name, String description, String category) {
}

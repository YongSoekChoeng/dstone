package net.dstone.ai.common.annotation;

/**
 * <pre>
 * Tool의 종류입니다. 화면의 "Tool 찾기" 창이 이 값으로 Tool을 묶어 보여 줍니다.
 * 실행에는 영향이 없습니다(어느 종류든 똑같이 부르고, 똑같은 화이트리스트를 거칩니다).
 *
 *   CODE_RAG  분석해 둔 소스 코드를 찾는 Tool (tools.knowledge.KnowledgeTool)
 *   DOC_RAG   올린 문서(개발 가이드 등)를 찾는 Tool (tools.knowledge.KnowledgeDocTool)
 *   MCP       MCP 서버에서 가져온 Tool. @AiTool에 적지 않습니다. common.config.ConfigTool이 알아서 붙입니다
 *   GENERAL   그 밖의 Tool. @AiTool의 기본값입니다
 * </pre>
 */
public enum ToolCategory {

	CODE_RAG,

	DOC_RAG,

	MCP,

	GENERAL

}

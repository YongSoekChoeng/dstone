package net.dstone.ai.tools.rag;

import java.util.List;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.api.dto.RagSearchRequest;
import net.dstone.ai.api.dto.RetrievedChunk;
import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.rag.RagRetrievalChain;
import net.dstone.common.core.BaseObject;

/**
 * LLM 호출 없이 검색 결과만 필요한 워크플로우가 일반 TOOL 스텝에서 호출하는 순수 RAG 검색 Tool이다.
 * common.rag.RagRetrievalChain을 그대로 감싸는 얇은 래퍼라 검색 로직은 여기 새로 만들지 않는다.
 * SqlSyntaxTool처럼 위험하지 않은 조회성 동작이라 화이트리스트 없이 항상 활성화된다.
 *
 * caller(tenant)는 파라미터로 직접 받지 않고 Spring AI의 ToolContext로 전달받는다 - runtime.agent.AgentExecutor
 * (Agent의 tool-calling 경로)와 runtime.tool.ToolExecutor(Workflow TOOL step 경로) 둘 다 caller를 ToolContext에
 * 실어서 호출하므로, AGENT의 ragEnabled 경로와 동일하게 tenant 필터가 걸린다. ToolContext가 없거나 caller
 * 키가 비어 있으면(예: security.auth가 꺼진 배포) 격리 없이 전체 문서를 대상으로 검색한다.
 */
@AiTool
public class RagSearchTool extends BaseObject {

	@Autowired
	private RagRetrievalChain ragRetrievalChain;

	/**
	 * @param query       검색어
	 * @param topK        검색 결과 최대 개수(생략하면 dstone.ai.rag.retrieval.top-k 기본값)
	 * @param toolContext Spring AI가 주입하는 호출 컨텍스트(LLM에게 노출되는 파라미터 스키마에는 포함되지 않는다) - caller를 여기서 꺼낸다
	 */
	@Tool(description = "질의어로 벡터스토어에 적재된 문서를 검색해서 관련 청크 텍스트를 반환한다(LLM을 부르지 않는 순수 검색). RAG가 비활성화돼 있으면 실패 문구를 반환한다.")
	public String searchDocuments(@ToolParam(description = "검색어") String query, @ToolParam(description = "검색 결과 최대 개수(생략 가능)", required = false) Integer topK, ToolContext toolContext) {
		List<RetrievedChunk> chunks;
		try {
			chunks = this.ragRetrievalChain.search(new RagSearchRequest(query, topK, null, null), this.callerOf(toolContext));
		} catch (IllegalStateException e) {
			return "실패: " + e.getMessage();
		}
		if (chunks.isEmpty()) {
			return "검색 결과가 없습니다.";
		}
		StringBuilder combined = new StringBuilder();
		for (int i = 0; i < chunks.size(); i++) {
			if (i > 0) {
				combined.append("\n---\n");
			}
			combined.append(chunks.get(i).text());
		}
		return combined.toString();
	}

	/** @param toolContext Spring AI가 넘겨준 호출 컨텍스트(호출 경로에 따라 null일 수 있음) */
	private String callerOf(ToolContext toolContext) {
		if (toolContext == null) {
			return null;
		}
		Object caller = toolContext.getContext().get(Constants.Security.Caller.ADVISOR_CONTEXT_KEY);
		return caller == null ? null : caller.toString();
	}

}

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
 * LLM을 부르지 않고 검색 결과만 필요한 Workflow가 일반 TOOL step에서 호출하는, 순수하게
 * 검색만 하는 RAG Tool입니다. common.rag.RagRetrievalChain을 그대로 감싸는 얇은 래퍼일 뿐이라,
 * 실제 검색 로직은 여기서 새로 만들지 않고 그쪽 것을 그대로 씁니다. SqlSyntaxTool처럼 위험하지
 * 않은 단순 조회 동작이라 별도 화이트리스트 없이 항상 켜져 있습니다.
 *
 * caller(tenant, 호출 주체)는 메소드 파라미터로 직접 받지 않고, Spring AI가 제공하는 ToolContext를
 * 통해 전달받습니다. runtime.agent.AgentExecutor(Agent의 tool-calling 경로)와
 * runtime.tool.ToolExecutor(Workflow TOOL step 경로) 둘 다 caller 값을 ToolContext에 실어서
 * 이 Tool을 호출하므로, AGENT의 ragEnabled 경로와 똑같이 tenant별로 문서를 걸러내는 필터가
 * 적용됩니다. 다만 ToolContext 자체가 없거나 그 안에 caller 값이 비어 있으면(예: 인증 기능이
 * 꺼져 있는 배포 환경) tenant 구분 없이 전체 문서를 대상으로 검색합니다.
 */
@AiTool
public class RagSearchTool extends BaseObject {

	@Autowired
	private RagRetrievalChain ragRetrievalChain;

	/**
	 * @param query       검색할 질의어입니다.
	 * @param topK        검색 결과로 가져올 최대 개수입니다(생략하면 dstone.ai.rag.retrieval.top-k 기본값을 씁니다).
	 * @param toolContext Spring AI가 자동으로 넣어주는 호출 컨텍스트입니다(LLM에게 보여주는 파라미터 목록에는 나타나지 않습니다) - 여기서 caller 값을 꺼냅니다.
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

	/** @param toolContext Spring AI가 넘겨준 호출 컨텍스트입니다(호출 경로에 따라 null일 수 있습니다). */
	private String callerOf(ToolContext toolContext) {
		if (toolContext == null) {
			return null;
		}
		Object caller = toolContext.getContext().get(Constants.Security.Caller.ADVISOR_CONTEXT_KEY);
		return caller == null ? null : caller.toString();
	}

}

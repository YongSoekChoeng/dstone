package net.dstone.ai.tools.rag;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.rag.RetrievedChunk;
import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.rag.RagRetrievalChain;
import net.dstone.common.core.BaseObject;

/**
 * LLM을 부르지 않고 검색 결과만 필요한 Workflow가 일반 TOOL step에서 호출하는, 순수하게
 * 검색만 하는 RAG Tool입니다. common.rag.RagRetrievalChain을 그대로 감싸는 얇은 래퍼일 뿐이라,
 * 실제 검색 로직은 여기서 새로 만들지 않고 그쪽 것을 그대로 씁니다. SqlSyntaxTool처럼 위험하지
 * 않은 단순 조회 동작이라 별도 화이트리스트 없이 항상 켜져 있습니다.
 *
 * 문서는 dstone-knowledge에 올려 둔 것입니다(dstone-boot의 "코드 분석(Knowledge) > 검색 · 문서" 화면). caller별로 가리지 않습니다.
 */
@AiTool
public class RagSearchTool extends BaseObject {

	@Autowired
	private RagRetrievalChain ragRetrievalChain;

	/**
	 * @param query 검색할 질의어입니다.
	 * @param topK  검색 결과로 가져올 최대 개수입니다(생략하면 dstone.ai.rag.retrieval.top-k 기본값을 씁니다).
	 */
	@Tool(description = "질의어로 올려 둔 문서를 검색해서 관련 청크 텍스트를 반환한다(LLM을 부르지 않는 순수 검색). RAG가 비활성화돼 있으면 실패 문구를 반환한다.")
	public String searchDocuments(@ToolParam(description = "검색어") String query, @ToolParam(description = "검색 결과 최대 개수(생략 가능)", required = false) Integer topK) {
		List<RetrievedChunk> chunks;
		try {
			chunks = this.ragRetrievalChain.search(query, topK, null);
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

}

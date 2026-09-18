package net.dstone.ai.tools.rag;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.api.dto.RagSearchRequest;
import net.dstone.ai.api.dto.RetrievedChunk;
import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.rag.RagRetrievalChain;
import net.dstone.common.core.BaseObject;

/**
 * StepType.RAG가 없어진 자리를 대신한다 - LLM 호출 없이 검색 결과만 필요한 워크플로우는 이 Tool을 일반 TOOL
 * 스텝에서 호출한다. common.rag.RagRetrievalChain을 그대로 감싸는 얇은 래퍼라 검색 로직은 여기 새로 만들지 않는다.
 * SqlSyntaxTool처럼 위험하지 않은 조회성 동작이라 화이트리스트 없이 항상 활성화된다.
 *
 * 다른 Tool들과 마찬가지로 이 메서드는 caller(tenant)를 받지 않는다 - 지금 구조에서는 Tool 호출 경로에 caller가 전달되지
 * 않으므로, 이 검색은 tenant 격리 없이 전체 문서를 대상으로 한다(AGENT의 ragEnabled 경로는 caller를 알고 있어 tenant
 * 필터가 걸린다 - runtime.agent.AgentExecutor 참고). caller별로도 격리해야 하면 Spring AI의 ToolContext로 caller를
 * 전달하는 확장이 필요하다.
 */
@AiTool
public class RagSearchTool extends BaseObject {

	@Autowired
	private RagRetrievalChain ragRetrievalChain;

	/**
	 * @param query 검색어
	 * @param topK  검색 결과 최대 개수(생략하면 dstone.ai.rag.retrieval.top-k 기본값)
	 */
	@Tool(description = "질의어로 벡터스토어에 적재된 문서를 검색해서 관련 청크 텍스트를 반환한다(LLM을 부르지 않는 순수 검색). RAG가 비활성화돼 있으면 실패 문구를 반환한다.")
	public String searchDocuments(@ToolParam(description = "검색어") String query, @ToolParam(description = "검색 결과 최대 개수(생략 가능)", required = false) Integer topK) {
		List<RetrievedChunk> chunks;
		try {
			chunks = this.ragRetrievalChain.search(new RagSearchRequest(query, topK, null, null), null);
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

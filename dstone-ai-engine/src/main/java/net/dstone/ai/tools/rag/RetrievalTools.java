package net.dstone.ai.tools.rag;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.rag.retrieval.RetrievalService;
import net.dstone.common.core.BaseObject;

/**
 * RAG(Phase 2) 검색을 Tool(Phase 3)로 노출한다 - 흔히 "Agentic RAG"라고 부르는 패턴이다.
 * ChatController의 ragEnabled 옵션은 요청이 오면 무조건 먼저 검색부터 하고 시작하는 방식인데
 * (QuestionAnswerAdvisor), 이 Tool은 toolsEnabled로 붙여두면 LLM이 스스로 "이 질문은 지식베이스를
 * 찾아봐야겠다"고 판단할 때만 호출한다. 그래서 잡담이나 일반 상식 질문에는 검색을 건너뛸 수 있어
 * 더 유연하게 동작한다.
 *
 * RetrievalService와 마찬가지로 dstone.ai.rag.enabled=true일 때만 존재한다 - RAG 자체가 꺼져
 * 있으면 검색할 VectorStore가 없으니, 이 Tool도 함께 등록되지 않는 게 맞다.
 */
@AiTool
@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true")
public class RetrievalTools extends BaseObject {

	private final RetrievalService retrievalService;

	public RetrievalTools(RetrievalService retrievalService) {
		this.retrievalService = retrievalService;
	}

	@Tool(description = "사내 문서/지식베이스에서 질문과 관련된 내용을 검색한다. 사용자의 질문이 일반 상식으로는 "
			+ "답할 수 없는, 이 조직/프로젝트에 특화된 문서 내용을 물어볼 때만 사용한다.")
	public String searchKnowledgeBase(
			@ToolParam(description = "검색할 질문 또는 키워드") String query) {
		List<Document> results = this.retrievalService.search(query, null, null, null);
		if (results.isEmpty()) {
			return "지식베이스에서 관련된 문서를 찾지 못했습니다.";
		}
		return results.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
	}

}

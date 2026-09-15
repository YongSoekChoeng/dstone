package net.dstone.ai.runtime.step;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.api.dto.RagSearchRequest;
import net.dstone.ai.api.dto.RetrievedChunk;
import net.dstone.ai.rag.RagService;

/** RAG step - 검색만 하고 LLM은 부르지 않는다. 찾은 청크 텍스트를 이어붙여 다음 step의 입력으로 넘긴다. */
@Component
public class RagStepRunner {

	@Autowired
	private RagService ragService;

	/**
	 * @param caller 호출한 앱/서비스 식별자
	 * @param query 검색 질의어
	 */
	public String run(String caller, String query) {
		List<RetrievedChunk> chunks = this.ragService.search(new RagSearchRequest(query, null, null, null), caller);
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

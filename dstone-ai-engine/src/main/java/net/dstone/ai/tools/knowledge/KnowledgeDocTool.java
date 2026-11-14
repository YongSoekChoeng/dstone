package net.dstone.ai.tools.knowledge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.JsonNode;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.annotation.ToolCategory;
import net.dstone.ai.common.exception.KnowledgeCallException;
import net.dstone.ai.common.knowledge.KnowledgeClient;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * dstone-knowledge에 올린 문서(개발 가이드, 설계서, 매뉴얼 등)를 읽는 Tool 모음입니다.
 * 문서는 dstone-boot의 "코드 분석(Knowledge) > 검색 · 문서" 화면에서 묶음(collection) 이름을 붙여 올립니다.
 *
 *   knowledgeListDocs       올린 문서와 묶음 목록. 무엇이 있는지 먼저 볼 때
 *   knowledgeSearchDocs     올린 문서에서 뜻과 이름으로 찾기. 묶음이나 문서로 범위를 좁힐 수 있다
 *   knowledgeGetChunks      찾은 조각의 앞뒤 조각을 이어 읽기(문서 ID + 청크 번호)
 *
 * 분석한 소스 코드를 찾는 Tool은 KnowledgeTool에 있습니다. 둘을 나눈 것은 화면의 "Tool 찾기" 창이
 * 종류(문서 RAG / 소스 RAG)별로 묶어 보여 주기 때문입니다(@AiTool의 category).
 * 문서 Tool은 프로젝트와 상관없습니다. 어느 문서를 볼지는 서버가 기억하지 않고, LLM이 부를 때마다 범위(collections, documentIds)를 정합니다.
 * dstone-knowledge가 호출자 인증을 켜 두었으면 dstone.ai.tool.knowledge.api-key의 키로 부르고, 그 키의 호출자가 올린 문서만 보입니다.
 * </pre>
 */
@AiTool(category = ToolCategory.DOC_RAG)
public class KnowledgeDocTool extends BaseObject {

	/** 설정(dstone.ai.tool.knowledge.max-top-k)이 없을 때, knowledgeSearchDocs가 돌려주는 결과의 최대 개수 */
	private static final int DEFAULT_MAX_TOP_K = 10;

	/** knowledgeSearchDocs에서 topK를 주지 않았을 때의 결과 개수 */
	private static final int DEFAULT_TOP_K = 5;

	/**
	 * 올린 문서 조각 한 건의 본문 상한. 조각 크기는 dstone-knowledge가 정한다(dstone.knowledge.rag.upload.chunk-max-chars, 1000자 + 머리말).
	 * 조각을 자르면 장/절 단위로 나눈 뜻이 없어지므로 max-content-chars(코드용)를 쓰지 않고, 서버 설정이 커졌을 때를 대비한 안전선만 둔다
	 */
	private static final int DOC_MAX_CONTENT_CHARS = 2000;

	/** knowledgeListDocs가 한 번에 가져오는 문서 수(dstone-knowledge의 목록 상한) */
	private static final int DOC_LIST_SIZE = 200;

	/** knowledgeGetChunks의 앞뒤 조각 수 기본값과 상한 */
	private static final int DEFAULT_NEIGHBORS = 1;
	private static final int MAX_NEIGHBORS = 3;

	@Autowired
	private KnowledgeClient knowledgeClient;

	@Autowired
	private Environment environment;

	@Tool(description = "올린 문서(개발 가이드, 설계서, 매뉴얼 등)와 묶음(collection) 목록을 돌려준다. 문서마다 documentId, 제목, 조각 수가 나온다. "
		+ "어떤 문서가 있는지 모를 때 먼저 부르고, 여기서 본 묶음 이름과 documentId로 knowledgeSearchDocs의 범위를 좁힌다.")
	public String knowledgeListDocs(@ToolParam(description = "이 묶음의 문서만. 생략하면 전부", required = false) String collection) {
		try {
			Map<String, String> params = new LinkedHashMap<String, String>();
			params.put("collection", StringUtil.isEmpty(collection) ? null : collection.trim());
			params.put("size", String.valueOf(DOC_LIST_SIZE));
			JsonNode result = this.get("/api/documents", params);
			JsonNode documents = result.path("documents");
			if (documents.size() == 0) {
				return StringUtil.isEmpty(collection) ? "올린 문서가 없습니다." : "그 묶음에 올린 문서가 없습니다: " + collection;
			}
			// 묶음별로 모은다(묶음이 없는 문서는 맨 뒤).
			Map<String, List<JsonNode>> groups = new LinkedHashMap<String, List<JsonNode>>();
			List<JsonNode> ungrouped = new ArrayList<JsonNode>();
			for (int i = 0; i < documents.size(); i++) {
				JsonNode document = documents.get(i);
				String group = text(document, "collection");
				if (StringUtil.isEmpty(group)) {
					ungrouped.add(document);
					continue;
				}
				if (!groups.containsKey(group)) {
					groups.put(group, new ArrayList<JsonNode>());
				}
				groups.get(group).add(document);
			}
			StringBuilder sb = new StringBuilder();
			sb.append("올린 문서 ").append(result.path("total").asInt()).append("건 (documentId | 제목 | 조각 수, 그중 임베딩이 끝난 수)\n");
			Iterator<Map.Entry<String, List<JsonNode>>> it = groups.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, List<JsonNode>> group = it.next();
				sb.append("\n[묶음] ").append(group.getKey()).append(" (").append(group.getValue().size()).append("건)\n");
				this.appendDocs(sb, group.getValue());
			}
			if (!ungrouped.isEmpty()) {
				sb.append("\n[묶음 없음] (").append(ungrouped.size()).append("건)\n");
				this.appendDocs(sb, ungrouped);
			}
			this.appendMore(sb, result.path("total").asInt(), documents.size());
			sb.append("\nknowledgeSearchDocs의 collections에 묶음 이름을, documentIds에 documentId를 넣으면 그 안에서만 찾는다.\n");
			return sb.toString();
		} catch (KnowledgeCallException e) {
			return e.getMessage();
		}
	}

	@Tool(description = "올린 문서(개발 가이드, 설계서, 매뉴얼 등)에서 질문과 가까운 조각을 찾아 돌려준다. 뜻으로도 찾고, 질문에 든 영문 이름(함수, 속성, 컴포넌트)으로도 찾는다. "
		+ "조각마다 documentId, chunkNo, 문서 안의 위치(장/절 제목 경로, 쪽)가 붙는다. 조각이 중간에서 끊겼거나 앞뒤 설명이 더 필요하면 knowledgeGetChunks로 이어 읽는다. "
		+ "묶음이나 문서를 모르면 knowledgeListDocs로 먼저 본다. 분석한 소스 코드는 여기서 찾지 않는다(knowledgeSearch).")
	public String knowledgeSearchDocs(@ToolParam(description = "찾을 내용. 자연어 질문이나 이름") String query,
		@ToolParam(description = "이 묶음(collection)들에서만 찾는다. 쉼표로 구분. 생략하면 전부", required = false) String collections,
		@ToolParam(description = "이 문서(documentId)들에서만 찾는다. 쉼표로 구분. 생략하면 전부", required = false) String documentIds,
		@ToolParam(description = "결과 최대 개수(기본 5). 서버에 정한 상한을 넘으면 상한까지만 준다", required = false) Integer topK) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("query", query);
		int wanted = topK == null ? DEFAULT_TOP_K : topK.intValue();
		body.put("topK", Integer.valueOf(Math.max(1, Math.min(wanted, this.maxTopK()))));
		List<String> collectionList = splitList(collections);
		if (!collectionList.isEmpty()) {
			body.put("collections", collectionList);
		}
		List<String> documentIdList = splitList(documentIds);
		if (!documentIdList.isEmpty()) {
			body.put("documentIds", documentIdList);
		}
		try {
			JsonNode result = this.post("/api/search", body);
			JsonNode hits = result.path("hits");
			if (hits.size() == 0) {
				return "검색 결과가 없습니다. 범위(collections, documentIds)를 넓히거나 다른 낱말로 찾아 보세요.";
			}
			StringBuilder sb = new StringBuilder();
			if (result.hasNonNull("warning")) {
				sb.append("주의: ").append(result.get("warning").asText()).append("\n\n");
			}
			for (int i = 0; i < hits.size(); i++) {
				JsonNode hit = hits.get(i);
				sb.append("### ").append(i + 1).append(". ").append(text(hit, "title"))
					.append("  (documentId=").append(text(hit, "documentId")).append(", chunkNo=").append(text(hit, "chunkNo"));
				if (!StringUtil.isEmpty(text(hit, "collection"))) {
					sb.append(", 묶음=").append(text(hit, "collection"));
				}
				sb.append(")\n");
				// 본문 첫머리에 [문서] 제목 (몇 번째/전체), [위치] 제목 경로 (쪽)이 들어 있다.
				String content = text(hit, "content");
				sb.append(content.length() > DOC_MAX_CONTENT_CHARS ? content.substring(0, DOC_MAX_CONTENT_CHARS) + "\n...(생략)" : content).append("\n\n");
			}
			return sb.toString();
		} catch (KnowledgeCallException e) {
			return e.getMessage();
		}
	}

	@Tool(description = "올린 문서 하나의 조각을 chunkNo 앞뒤로 이어서 돌려준다. knowledgeSearchDocs로 찾은 조각이 중간에서 끊겼거나, 그 앞뒤의 설명(절차의 이전 단계, 예제의 나머지)이 필요할 때 쓴다.")
	public String knowledgeGetChunks(@ToolParam(description = "문서 ID(knowledgeSearchDocs나 knowledgeListDocs 결과의 documentId)") String documentId,
		@ToolParam(description = "가운데 조각 번호(knowledgeSearchDocs 결과의 chunkNo)") Integer chunkNo,
		@ToolParam(description = "앞으로 몇 조각(기본 1, 최대 3)", required = false) Integer before,
		@ToolParam(description = "뒤로 몇 조각(기본 1, 최대 3)", required = false) Integer after) {
		if (StringUtil.isEmpty(documentId) || chunkNo == null || chunkNo.intValue() < 0) {
			return "실패: documentId와 0 이상의 chunkNo가 있어야 합니다.";
		}
		int from = Math.max(0, chunkNo.intValue() - neighbors(before));
		int to = chunkNo.intValue() + neighbors(after);
		try {
			Map<String, String> params = new LinkedHashMap<String, String>();
			params.put("sourceId", documentId);
			params.put("from", String.valueOf(from));
			params.put("to", String.valueOf(to));
			JsonNode result = this.get("/api/documents/chunks", params);
			JsonNode chunks = result.path("chunks");
			int total = result.path("totalChunks").asInt();
			if (chunks.size() == 0) {
				return "그 범위에 조각이 없습니다. 이 문서의 chunkNo는 0부터 " + (total - 1) + "까지입니다.";
			}
			StringBuilder sb = new StringBuilder();
			sb.append("문서: ").append(text(result, "title")).append("  (documentId=").append(documentId).append(", 전체 조각 ").append(total).append("개, chunkNo 0 ~ ").append(total - 1).append(")\n");
			for (int i = 0; i < chunks.size(); i++) {
				JsonNode chunk = chunks.get(i);
				String content = text(chunk, "content");
				sb.append("\n--- chunkNo ").append(text(chunk, "chunkNo")).append(" ---\n")
					.append(content.length() > DOC_MAX_CONTENT_CHARS ? content.substring(0, DOC_MAX_CONTENT_CHARS) + "\n...(생략)" : content).append('\n');
			}
			return sb.toString();
		} catch (KnowledgeCallException e) {
			return e.getMessage();
		}
	}

	/** 문서 목록 줄: documentId | 제목 | 조각 수 */
	private void appendDocs(StringBuilder sb, List<JsonNode> documents) {
		for (int i = 0; i < documents.size(); i++) {
			JsonNode document = documents.get(i);
			sb.append("- ").append(text(document, "sourceId")).append(" | ").append(text(document, "title"))
				.append(" | 조각 ").append(text(document, "chunks")).append(" (임베딩 ").append(text(document, "embedded")).append(")\n");
		}
	}

	/** 쉼표로 나눈 목록(빈 값은 뺀다) */
	private static List<String> splitList(String value) {
		List<String> list = new ArrayList<String>();
		if (StringUtil.isEmpty(value)) {
			return list;
		}
		String[] parts = value.split(",");
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].trim().length() > 0) {
				list.add(parts[i].trim());
			}
		}
		return list;
	}

	/** knowledgeGetChunks의 앞뒤 조각 수(기본 1, 0 ~ 3) */
	private static int neighbors(Integer value) {
		return value == null ? DEFAULT_NEIGHBORS : Math.max(0, Math.min(MAX_NEIGHBORS, value.intValue()));
	}

	private void appendMore(StringBuilder sb, int total, int shown) {
		if (total > shown) {
			sb.append("... 외 ").append(total - shown).append("건 (조건을 좁혀 다시 조회하세요)\n");
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? "" : value.asText();
	}

	// 실제 호출은 common.knowledge.KnowledgeClient가 합니다.

	private JsonNode get(String path, Map<String, String> params) throws KnowledgeCallException {
		return this.knowledgeClient.get(path, params);
	}

	private JsonNode post(String path, Map<String, Object> body) throws KnowledgeCallException {
		return this.knowledgeClient.post(path, body);
	}

	/** knowledgeSearchDocs 결과 개수의 상한입니다(코드 검색과 같은 설정). 설정이 없거나 1보다 작으면 기본값입니다. */
	private int maxTopK() {
		Integer value = this.environment.getProperty("dstone.ai.tool.knowledge.max-top-k", Integer.class);
		return value == null || value.intValue() < 1 ? DEFAULT_MAX_TOP_K : value.intValue();
	}

}

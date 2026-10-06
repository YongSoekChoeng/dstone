package net.dstone.ai.tools.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.knowledge.KnowledgeCallException;
import net.dstone.ai.common.knowledge.KnowledgeClient;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * dstone-knowledge(Java 분석 결과와 올린 문서를 담아 둔 서버)에 물어보는 Tool 모음입니다.
 *
 * dstone-knowledge는 답을 지어내지 않고 사실만 돌려줍니다(어느 메소드가 무엇을 부르는지, 어느 SQL이 어느 테이블을 건드리는지).
 * 그 사실로 답을 만드는 일은 이 Tool을 쓰는 Agent(LLM)가 합니다. 그래서 Tool은 조회만 하고, 결과를 LLM이 읽기 쉬운 짧은 글로 바꿔 줍니다.
 *
 *   knowledgeListProjects   분석해 둔 프로젝트 목록
 *   knowledgeSearch         뜻과 이름으로 찾기(코드, SQL, 화면, 올린 문서)
 *   knowledgeFindMethods    이름으로 메소드 찾기(호출 관계를 조회하려면 메소드 ID가 필요하다)
 *   knowledgeCallers        이 메소드를 부르는 쪽
 *   knowledgeCallees        이 메소드가 부르는 쪽(SQL, 테이블, 화면까지)
 *   knowledgeTableUsage     테이블을 건드리는 SQL과 메소드
 *   knowledgeImpact         이것을 고치면 닿는 진입점, 화면, 메소드
 *
 * 켜고 끄는 설정은 따로 없습니다. dstone.ai.tool.knowledge.base-url이 비어 있으면(기본값) 어떤 호출도 실패 문구로 끝납니다.
 * 모든 Tool은 프로젝트 ID를 받고, 그 프로젝트에서 분석이 끝난 가장 최근 리비전을 씁니다.
 * dstone-knowledge가 호출자 인증을 켜 두었으면 dstone.ai.tool.knowledge.api-key의 키로 부릅니다.
 * 이때 올린 문서는 그 키의 호출자 것만 보입니다(이 엔진을 부른 쪽이 누구인지와는 상관없습니다).
 * </pre>
 */
@AiTool
public class KnowledgeTool extends BaseObject {

	/** 검색 결과 한 건의 본문을 이 글자 수까지만 돌려준다. 결과는 대화에 쌓여 매번 다시 보내지므로 짧게 유지한다 */
	private static final int MAX_CONTENT_CHARS = 1500;

	/** 목록 하나에서 돌려주는 최대 줄 수. 넘으면 몇 건이 더 있는지만 알린다 */
	private static final int MAX_LINES = 60;

	@Autowired
	private KnowledgeClient knowledgeClient;

	@Tool(description = "dstone-knowledge에 분석해 둔 Java 프로젝트 목록을 돌려준다. 다른 knowledge Tool에 넘길 projectId를 모를 때 먼저 부른다.")
	public String knowledgeListProjects() {
		try {
			JsonNode projects = this.get("/api/projects", null);
			if (projects.size() == 0) {
				return "분석해 둔 프로젝트가 없습니다.";
			}
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < projects.size(); i++) {
				JsonNode project = projects.get(i);
				sb.append(text(project, "projectId")).append(" - ").append(text(project, "projectName"));
				if (!StringUtil.isEmpty(text(project, "description"))) {
					sb.append(" (").append(text(project, "description")).append(")");
				}
				sb.append('\n');
			}
			return sb.toString();
		} catch (KnowledgeCallException e) {
			return e.getMessage();
		}
	}

	@Tool(description = "분석한 프로젝트에서 질문과 가까운 코드(메소드/타입), SQL, 화면(JSP), 올린 문서를 찾아 그 내용을 돌려준다. "
		+ "뜻으로도 찾고(\"주문 취소는 어디서 처리하나\"), 질문에 들어 있는 클래스/메소드/테이블 이름이나 주소로도 찾는다. "
		+ "어디를 봐야 할지 모를 때 가장 먼저 쓴다.")
	public String knowledgeSearch(@ToolParam(description = "프로젝트 ID") String projectId, @ToolParam(description = "찾을 내용. 자연어 질문이나 이름") String query,
		@ToolParam(description = "찾을 종류를 쉼표로 구분: METHOD, TYPE, FILE, MAPPER(SQL), VIEW(화면), UPLOAD(올린 문서). 생략하면 전부", required = false) String docTypes,
		@ToolParam(description = "올린 일반 문서(설계서 등)도 같이 찾을지. 기본 false", required = false) Boolean includeDocuments,
		@ToolParam(description = "결과 최대 개수(기본 5, 최대 10)", required = false) Integer topK) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("query", query);
		body.put("projectId", projectId);
		body.put("topK", Integer.valueOf(topK == null ? 5 : Math.max(1, Math.min(topK.intValue(), 10))));
		if (!StringUtil.isEmpty(docTypes)) {
			body.put("docTypes", java.util.Arrays.asList(docTypes.toUpperCase().replace(" ", "").split(",")));
		}
		if (Boolean.TRUE.equals(includeDocuments)) {
			body.put("sourceTypes", java.util.Arrays.asList("CODE", "DOCUMENT"));
		}
		try {
			JsonNode result = this.post("/api/search", body);
			JsonNode hits = result.path("hits");
			if (hits.size() == 0) {
				return "검색 결과가 없습니다.";
			}
			StringBuilder sb = new StringBuilder();
			if (result.hasNonNull("warning")) {
				sb.append("주의: ").append(result.get("warning").asText()).append("\n\n");
			}
			for (int i = 0; i < hits.size(); i++) {
				JsonNode hit = hits.get(i);
				sb.append("### ").append(i + 1).append(". [").append(text(hit, "docType")).append("] ").append(text(hit, "title")).append('\n');
				sb.append("위치: ").append(text(hit, "path"));
				if (hit.hasNonNull("lineStart")) {
					sb.append(':').append(hit.get("lineStart").asText()).append('-').append(text(hit, "lineEnd"));
				}
				if ("METHOD".equals(text(hit, "refKind"))) {
					sb.append("  (methodId: ").append(text(hit, "refId")).append(")");
				}
				sb.append('\n');
				String content = text(hit, "content");
				sb.append(content.length() > MAX_CONTENT_CHARS ? content.substring(0, MAX_CONTENT_CHARS) + "\n...(생략)" : content).append("\n\n");
			}
			return sb.toString();
		} catch (KnowledgeCallException e) {
			return e.getMessage();
		}
	}

	@Tool(description = "메소드 이름으로 메소드를 찾아 methodId를 돌려준다. knowledgeCallers / knowledgeCallees / knowledgeImpact에 넘길 methodId가 필요할 때 쓴다.")
	public String knowledgeFindMethods(@ToolParam(description = "프로젝트 ID") String projectId, @ToolParam(description = "메소드 이름(정확히 일치)") String name,
		@ToolParam(description = "타입 이름의 일부(예: OrderService). 같은 이름의 메소드가 여러 타입에 있을 때 좁힌다", required = false) String owner) {
		try {
			Map<String, String> params = new LinkedHashMap<String, String>();
			params.put("name", name);
			params.put("owner", owner);
			params.put("size", String.valueOf(MAX_LINES));
			JsonNode result = this.get("/api/revisions/" + this.latestRevision(projectId) + "/methods", params);
			JsonNode methods = result.path("methods");
			if (methods.size() == 0) {
				return "그 이름의 메소드가 없습니다: " + name;
			}
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < methods.size(); i++) {
				JsonNode method = methods.get(i);
				sb.append(text(method, "ownerFqn")).append('#').append(text(method, "signature")).append("  methodId=").append(text(method, "methodId"))
					.append("  (").append(text(method, "path")).append(':').append(text(method, "lineStart")).append(")\n");
			}
			this.appendMore(sb, result.path("total").asInt(), methods.size());
			return sb.toString();
		} catch (KnowledgeCallException e) {
			return e.getMessage();
		}
	}

	@Tool(description = "이 메소드를 부르는 쪽을 거슬러 올라가며 돌려준다(부르는 메소드, 그 주소를 요청하는 화면). 인터페이스를 통해 부르는 호출도 포함한다. "
		+ "신뢰도가 LOW인 줄은 이름만 보고 짐작한 것이니 참고로만 본다.")
	public String knowledgeCallers(@ToolParam(description = "프로젝트 ID") String projectId, @ToolParam(description = "메소드 ID(knowledgeFindMethods나 knowledgeSearch 결과에 있다)") String methodId,
		@ToolParam(description = "몇 단계까지 거슬러 올라갈지(기본 2, 최대 5)", required = false) Integer depth) {
		return this.callGraph(projectId, methodId, depth, "callers");
	}

	@Tool(description = "이 메소드가 부르는 쪽을 따라 내려가며 돌려준다. 부르는 메소드뿐 아니라 실행하는 SQL, 그 SQL이 읽고 쓰는 테이블, 여는 화면(JSP)까지 나온다. "
		+ "신뢰도가 LOW인 줄은 이름만 보고 짐작한 것이니 참고로만 본다.")
	public String knowledgeCallees(@ToolParam(description = "프로젝트 ID") String projectId, @ToolParam(description = "메소드 ID(knowledgeFindMethods나 knowledgeSearch 결과에 있다)") String methodId,
		@ToolParam(description = "몇 단계까지 따라 내려갈지(기본 2, 최대 5)", required = false) Integer depth) {
		return this.callGraph(projectId, methodId, depth, "callees");
	}

	@Tool(description = "테이블 하나를 읽거나 쓰는 SQL statement와 그 SQL을 실행하는 메소드를 돌려준다. crud는 C(넣기)/R(읽기)/U(고치기)/D(지우기)다.")
	public String knowledgeTableUsage(@ToolParam(description = "프로젝트 ID") String projectId, @ToolParam(description = "테이블 이름") String table) {
		try {
			JsonNode result = this.get("/api/revisions/" + this.latestRevision(projectId) + "/tables/" + table.trim().toUpperCase(), null);
			JsonNode usage = result.path("usage");
			if (usage.size() == 0) {
				return "그 테이블을 건드리는 SQL이 없습니다: " + table;
			}
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < usage.size() && i < MAX_LINES; i++) {
				JsonNode row = usage.get(i);
				sb.append(text(row, "crud")).append("  ").append(text(row, "statement")).append("  (").append(text(row, "mapperFile")).append(':').append(text(row, "lineStart")).append(")");
				if (row.hasNonNull("method")) {
					sb.append("  ← ").append(text(row, "method")).append("  methodId=").append(text(row, "methodId"));
				}
				sb.append("  [").append(text(row, "confidence")).append("]\n");
			}
			this.appendMore(sb, usage.size(), Math.min(usage.size(), MAX_LINES));
			return sb.toString();
		} catch (KnowledgeCallException e) {
			return e.getMessage();
		}
	}

	@Tool(description = "영향도 분석: 대상을 고치면 닿는 진입점(주소), 화면(JSP), 메소드를 한 번에 돌려준다. "
		+ "대상은 테이블 / SQL statement / 타입 / 메소드 가운데 하나다. 줄마다 신뢰도가 붙는다: HIGH는 확실한 관계만 따라온 것, MEDIUM은 덜 확실한 관계가 낀 것, "
		+ "LOW는 짐작이 낀 것이라 참고로만 본다. 리플렉션이나 문자열로 조립한 주소처럼 분석이 잇지 못한 호출은 나오지 않으므로, 결과가 전부라고 단정하지 않는다.")
	public String knowledgeImpact(@ToolParam(description = "프로젝트 ID") String projectId,
		@ToolParam(description = "대상의 종류: TABLE(테이블 이름) / STATEMENT(SQL statement 이름) / TYPE(타입 전체 이름) / METHOD(메소드 ID)") String targetKind,
		@ToolParam(description = "대상. 종류에 따라 테이블 이름, statement 이름(네임스페이스.id), 타입 전체 이름, 메소드 ID") String target,
		@ToolParam(description = "대상이 테이블일 때만: ALL(기본) / READ(읽는 쪽만) / WRITE(쓰는 쪽만)", required = false) String access) {
		String kind = targetKind == null ? "" : targetKind.trim().toUpperCase();
		String paramName = "TABLE".equals(kind) ? "table" : "STATEMENT".equals(kind) ? "statement" : "TYPE".equals(kind) ? "type" : "METHOD".equals(kind) ? "methodId" : null;
		if (paramName == null) {
			return "실패: targetKind는 TABLE / STATEMENT / TYPE / METHOD 가운데 하나여야 합니다: " + targetKind;
		}
		try {
			Map<String, String> params = new LinkedHashMap<String, String>();
			params.put(paramName, target);
			params.put("access", access);
			JsonNode result = this.get("/api/revisions/" + this.latestRevision(projectId) + "/impact", params);
			JsonNode summary = result.path("summary");
			StringBuilder sb = new StringBuilder();
			sb.append("대상: ").append(kind).append(' ').append(result.path("target").path("name").asText()).append('\n');
			sb.append("요약: 직접 건드리는 메소드 ").append(summary.path("startMethods").asInt()).append("개, 닿는 메소드 ").append(summary.path("methods").asInt())
				.append("개, 진입점 ").append(summary.path("endpoints").asInt()).append("개, 화면 ").append(summary.path("screens").asInt()).append("개\n");

			JsonNode endpoints = result.path("endpoints");
			sb.append("\n[진입점] (거리, 신뢰도, 주소 ← 처리하는 메소드)\n");
			for (int i = 0; i < endpoints.size() && i < MAX_LINES; i++) {
				JsonNode row = endpoints.get(i);
				sb.append(text(row, "depth")).append("  ").append(text(row, "confidence")).append("  ").append(text(row, "endpointType")).append(' ')
					.append(text(row, "httpMethod")).append(' ').append(text(row, "path")).append("  ← ").append(text(row, "handler")).append('\n');
			}
			this.appendMore(sb, endpoints.size(), Math.min(endpoints.size(), MAX_LINES));

			JsonNode screens = result.path("screens");
			sb.append("\n[화면] (거리, 신뢰도, 경로, 이어진 방식: REQUESTS=화면이 그 주소를 요청 / RENDERS=메소드가 이 화면을 연다 / CALLS=화면의 코드가 직접 부른다)\n");
			for (int i = 0; i < screens.size() && i < MAX_LINES; i++) {
				JsonNode row = screens.get(i);
				sb.append(text(row, "depth")).append("  ").append(text(row, "confidence")).append("  ").append(text(row, "path")).append("  ").append(text(row, "via")).append('\n');
			}
			this.appendMore(sb, screens.size(), Math.min(screens.size(), MAX_LINES));

			JsonNode methods = result.path("methods");
			sb.append("\n[메소드] (거리 0 = 대상을 직접 건드린다)\n");
			for (int i = 0; i < methods.size() && i < MAX_LINES; i++) {
				JsonNode row = methods.get(i);
				sb.append(text(row, "depth")).append("  ").append(text(row, "confidence")).append("  ").append(text(row, "layer")).append("  ").append(text(row, "method")).append('\n');
			}
			this.appendMore(sb, methods.size(), Math.min(methods.size(), MAX_LINES));
			return sb.toString();
		} catch (KnowledgeCallException e) {
			return e.getMessage();
		}
	}

	/** 부르는 쪽 / 불리는 쪽 조회. 두 Tool의 모양이 같아서 한곳에서 한다 */
	private String callGraph(String projectId, String methodId, Integer depth, String direction) {
		try {
			Map<String, String> params = new LinkedHashMap<String, String>();
			params.put("depth", String.valueOf(depth == null ? 2 : Math.max(1, Math.min(depth.intValue(), 5))));
			JsonNode result = this.get("/api/revisions/" + this.latestRevision(projectId) + "/methods/" + methodId + "/" + direction, params);
			JsonNode rows = result.path(direction);
			JsonNode method = result.path("method");
			StringBuilder sb = new StringBuilder();
			sb.append("기준: ").append(text(method, "ownerFqn")).append('#').append(text(method, "signature")).append('\n');
			if (rows.size() == 0) {
				return sb.append("callers".equals(direction) ? "부르는 쪽이 없습니다(진입점이거나, 분석이 잇지 못한 호출일 수 있습니다)." : "부르는 것이 없습니다.").toString();
			}
			sb.append("(거리, 관계, 신뢰도, 대상)\n");
			for (int i = 0; i < rows.size() && i < MAX_LINES; i++) {
				JsonNode row = rows.get(i);
				boolean callers = "callers".equals(direction);
				String name = text(row, callers ? "from" : "to");
				String id = text(row, callers ? "fromId" : "toId");
				String nodeKind = text(row, callers ? "fromKind" : "toKind");
				sb.append(text(row, "depth")).append("  ").append(text(row, "relationType")).append("  ").append(text(row, "confidence")).append("  ").append(name);
				if ("METHOD".equals(nodeKind) && !StringUtil.isEmpty(id)) {
					sb.append("  methodId=").append(id);
				}
				sb.append('\n');
			}
			this.appendMore(sb, rows.size(), Math.min(rows.size(), MAX_LINES));
			return sb.toString();
		} catch (KnowledgeCallException e) {
			return e.getMessage();
		}
	}

	/** 프로젝트에서 분석이 끝난(READY) 가장 최근 리비전의 번호 */
	private long latestRevision(String projectId) throws KnowledgeCallException {
		if (StringUtil.isEmpty(projectId)) {
			throw new KnowledgeCallException("실패: projectId가 비어 있습니다. knowledgeListProjects로 프로젝트 ID를 먼저 확인하세요.");
		}
		JsonNode revisions = this.get("/api/projects/" + projectId.trim() + "/revisions", null);
		long latest = -1;
		for (int i = 0; i < revisions.size(); i++) {
			JsonNode revision = revisions.get(i);
			if ("READY".equals(text(revision, "status"))) {
				latest = Math.max(latest, revision.path("revisionId").asLong());
			}
		}
		if (latest < 0) {
			throw new KnowledgeCallException("실패: 분석이 끝난 리비전이 없는 프로젝트입니다: " + projectId);
		}
		return latest;
	}

	private void appendMore(StringBuilder sb, int total, int shown) {
		if (total > shown) {
			sb.append("... 외 ").append(total - shown).append("건 (조건을 좁혀 다시 조회하세요)\n");
		}
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? "" : value.asText();
	}

	/* ============================== HTTP 호출 ============================== */

	// 실제 호출은 common.knowledge.KnowledgeClient가 합니다(RAG도 같은 창구를 씁니다).

	private JsonNode get(String path, Map<String, String> params) throws KnowledgeCallException {
		return this.knowledgeClient.get(path, params);
	}

	private JsonNode post(String path, Map<String, Object> body) throws KnowledgeCallException {
		return this.knowledgeClient.post(path, body);
	}

}

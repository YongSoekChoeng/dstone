package net.dstone.knowledge.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dstone.knowledge.common.util.HashText;

/**
 * <pre>
 * Java가 아닌 것(SQL 매퍼, 화면)의 RAG 문서를 짓습니다. CodeDocumentBuilder와 같은 방식입니다.
 * DB도 파일도 건드리지 않고, 받은 재료로 글만 만듭니다.
 *
 * 만드는 문서:
 *   MAPPER  SQL statement 하나. "어느 테이블을 읽고 쓰는지, 어느 메소드가 실행하는지"를 한글로 적고 SQL을 붙인다.
 *   VIEW    화면(JSP) 하나. "어느 메소드가 여는지, 어느 주소를 요청하는지"를 한글로 적고 JSP의 앞부분을 붙인다.
 *
 * 사실을 한글로 먼저 적는 까닭은 메소드 문서와 같습니다. "회원 테이블을 고치는 SQL", "주문 저장 화면" 같은 질문은
 * SQL이나 JSP의 글자와 닮지 않아서, 사실을 글로 적어 두어야 검색에 걸립니다.
 * </pre>
 */
public class ResourceDocumentBuilder {

	/** 문서에 적는 "실행하는 메소드", "요청하는 주소" 등의 최대 수. 넘는 것은 "외 N건"으로 적습니다. */
	private static final int MAX_ITEMS = 15;

	/** 화면 문서 하나의 최대 청크 수. JSP는 대부분 HTML이라 길게 담아도 검색에 보탬이 적고 임베딩 시간만 늘어납니다. */
	private static final int MAX_VIEW_CHUNKS = 2;

	private final String projectId;
	private final int chunkMaxChars;

	/** 문서와 청크를 만들어 담는 일은 CodeDocumentBuilder의 것을 그대로 씁니다. */
	private final CodeDocumentBuilder documents;

	public ResourceDocumentBuilder(String projectId, long revisionId, int chunkMaxChars) {
		this.projectId = projectId;
		this.chunkMaxChars = chunkMaxChars;
		this.documents = new CodeDocumentBuilder(projectId, revisionId, false, chunkMaxChars);
	}

	/* ============================== SQL 매퍼 ============================== */

	/**
	 * @param file analysis_file 한 행. 키: fileId, path
	 * @param statements 이 파일의 statement들. 키: mapperId, mapperType, namespace, statementId, statementType, parameterType, resultType, sqlBody, lineStart, lineEnd
	 * @param tables statement가 건드리는 테이블. 키: fromId("S" + mapperId), table, crud(C / R / U / D)
	 * @param executors statement를 실행하는 메소드. 키: toId("S" + mapperId), caller
	 */
	public CodeDocumentBuilder.Result buildMapper(Map<String, Object> file, List<Map<String, Object>> statements, List<Map<String, Object>> tables
			, List<Map<String, Object>> executors) {
		long fileId = ((Number) file.get("fileId")).longValue();
		String path = (String) file.get("path");
		Map<String, List<Map<String, Object>>> tablesByStatement = groupBy(tables, "fromId");
		Map<String, List<Map<String, Object>>> executorsByStatement = groupBy(executors, "toId");
		CodeDocumentBuilder.Result result = new CodeDocumentBuilder.Result();

		for (int i = 0; i < statements.size(); i++) {
			Map<String, Object> statement = statements.get(i);
			String nodeId = "S" + statement.get("mapperId");
			String name = (statement.get("namespace") == null ? "" : statement.get("namespace") + ".") + statement.get("statementId");
			Integer lineStart = intOf(statement.get("lineStart"));
			Integer lineEnd = intOf(statement.get("lineEnd"));

			List<String> reads = new ArrayList<String>();
			List<String> writes = new ArrayList<String>();
			List<Map<String, Object>> uses = tablesByStatement.get(nodeId);
			for (int t = 0; uses != null && t < uses.size(); t++) {
				String crud = (String) uses.get(t).get("crud");
				String table = (String) uses.get(t).get("table");
				if ("R".equals(crud)) {
					addOnce(reads, table);
				} else {
					addOnce(writes, table + "(" + crudLabel(crud) + ")");
				}
			}
			List<String> callers = new ArrayList<String>();
			List<Map<String, Object>> executorRows = executorsByStatement.get(nodeId);
			for (int c = 0; executorRows != null && c < executorRows.size(); c++) {
				addOnce(callers, shortName((String) executorRows.get(c).get("caller")));
			}

			StringBuilder sb = new StringBuilder();
			sb.append("[SQL] ").append(name).append(" (").append(statement.get("statementType")).append(")\n");
			sb.append("파일: ").append(path).append(rangeText(lineStart, lineEnd)).append('\n');
			line(sb, "매퍼 종류", mapperTypeLabel((String) statement.get("mapperType")));
			line(sb, "파라미터", (String) statement.get("parameterType"));
			line(sb, "결과", (String) statement.get("resultType"));
			line(sb, "읽는 테이블", limited(reads));
			line(sb, "쓰는 테이블", limited(writes));
			line(sb, "실행하는 메소드", limited(callers));
			sb.append("SQL:\n").append(statement.get("sqlBody") == null ? "" : statement.get("sqlBody")).append('\n');

			Map<String, Object> metadata = new LinkedHashMap<String, Object>();
			metadata.put("docType", "MAPPER");
			metadata.put("statement", name);
			metadata.put("statementType", statement.get("statementType"));
			if (!reads.isEmpty()) {
				metadata.put("reads", reads);
			}
			if (!writes.isEmpty()) {
				metadata.put("writes", writes);
			}
			// 같은 statement면 리비전이 달라도 같은 문서 ID가 되게, 번호가 아니라 이름으로 만든다.
			String documentId = "SQL:" + HashText.sha256(projectId + "|" + path + "|" + name);
			documents.addDocument(result, documentId, "MAPPER", "SQL", nodeId, name, path, metadata, fileId
					, documents.splitByLines(sb.toString(), chunkMaxChars * 2), null, lineStart, lineEnd);
		}
		return result;
	}

	/* ============================== 화면(JSP) ============================== */

	/**
	 * @param file analysis_file 한 행. 키: fileId, path
	 * @param text JSP의 내용(올바른 인코딩으로 읽은 글)
	 * @param links 이 JSP와 이어진 것들. 키: kind(OPENED_BY / REQUESTS / INCLUDES / INCLUDED_BY / CALLS), text, target
	 */
	public CodeDocumentBuilder.Result buildView(Map<String, Object> file, String text, List<Map<String, Object>> links) {
		long fileId = ((Number) file.get("fileId")).longValue();
		String path = (String) file.get("path");
		String[] lines = text.split("\r?\n", -1);

		List<String> openedBy = new ArrayList<String>();
		List<String> requests = new ArrayList<String>();
		List<String> includes = new ArrayList<String>();
		List<String> includedBy = new ArrayList<String>();
		List<String> calls = new ArrayList<String>();
		for (int i = 0; i < links.size(); i++) {
			Map<String, Object> link = links.get(i);
			String kind = (String) link.get("kind");
			String target = (String) link.get("target");
			if ("OPENED_BY".equals(kind)) {
				addOnce(openedBy, shortName(target));
			} else if ("REQUESTS".equals(kind)) {
				addOnce(requests, link.get("text") + " → " + shortName(target));
			} else if ("INCLUDES".equals(kind)) {
				addOnce(includes, target);
			} else if ("INCLUDED_BY".equals(kind)) {
				addOnce(includedBy, target);
			} else if ("CALLS".equals(kind)) {
				addOnce(calls, shortName(target));
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("[화면] ").append(path).append('\n');
		line(sb, "제목", titleOf(text));
		line(sb, "화면 종류", screenTypeLabel((String) file.get("fileType")));
		sb.append("줄 수: ").append(lines.length).append('\n');
		line(sb, "이 화면을 여는 메소드", limited(openedBy));
		line(sb, "이 화면이 요청하는 주소", limited(requests));
		line(sb, "끼워 넣는 화면", limited(includes));
		line(sb, "이 화면을 끼워 넣는 화면", limited(includedBy));
		line(sb, "화면 안의 Java 코드가 호출하는 것", limited(calls));
		sb.append("소스:\n");
		for (int i = 0; i < lines.length; i++) {
			// 빈 줄과 줄 앞의 들여쓰기는 뺀다. 같은 글자 수에 더 많은 내용을 담기 위해서다.
			String trimmed = lines[i].trim();
			if (trimmed.length() > 0) {
				sb.append(trimmed).append('\n');
			}
		}

		List<String> contents = documents.splitByLines(sb.toString(), chunkMaxChars * 2);
		if (contents.size() > MAX_VIEW_CHUNKS) {
			contents = new ArrayList<String>(contents.subList(0, MAX_VIEW_CHUNKS));
		}

		Map<String, Object> metadata = new LinkedHashMap<String, Object>();
		metadata.put("docType", "VIEW");
		metadata.put("path", path);
		metadata.put("layer", "VIEW");
		String documentId = "VIEW:" + HashText.sha256(projectId + "|" + path);
		CodeDocumentBuilder.Result result = new CodeDocumentBuilder.Result();
		documents.addDocument(result, documentId, "VIEW", "FILE", "F" + fileId, path, path, metadata, fileId, contents, null
				, Integer.valueOf(1), Integer.valueOf(lines.length));
		return result;
	}

	private String mapperTypeLabel(String mapperType) {
		if ("IBATIS".equals(mapperType)) {
			return "iBATIS";
		}
		if ("QUERY_XML".equals(mapperType)) {
			return "쿼리 XML";
		}
		return "MyBatis";
	}

	/** 리치클라이언트 화면 파일인지(analysis_file.file_type). JSP는 언어로 가려지므로 여기에 없다. */
	public static boolean isRichClientScreen(String fileType) {
		return "WEBSQUARE".equals(fileType) || "NEXACRO".equals(fileType);
	}

	/** 화면 종류를 사람이 읽는 이름으로. JSP는 따로 적지 않는다(null). */
	private String screenTypeLabel(String fileType) {
		if ("WEBSQUARE".equals(fileType)) {
			return "WebSquare";
		}
		if ("NEXACRO".equals(fileType)) {
			return "Nexacro / X-Platform";
		}
		return null;
	}

	/** JSP의 title 태그 안의 글. 없거나 실행할 때 정해지는 값이면 null */
	private String titleOf(String text) {
		String lower = text.toLowerCase(java.util.Locale.ROOT);
		int open = lower.indexOf("<title>");
		int close = open < 0 ? -1 : lower.indexOf("</title>", open);
		if (close < 0) {
			return null;
		}
		String title = text.substring(open + "<title>".length(), close).trim();
		return title.length() == 0 || title.length() > 200 || title.indexOf('<') >= 0 || title.indexOf("${") >= 0 ? null : title;
	}

	/* ============================== 도구 ============================== */

	private String crudLabel(String crud) {
		if ("C".equals(crud)) {
			return "넣기";
		}
		if ("U".equals(crud)) {
			return "고치기";
		}
		if ("D".equals(crud)) {
			return "지우기";
		}
		return crud;
	}

	/** 앞에서부터 MAX_ITEMS개만 적고, 넘는 것은 "외 N건"으로 적습니다. */
	private String limited(List<String> values) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.size() && i < MAX_ITEMS; i++) {
			sb.append(i > 0 ? ", " : "").append(values.get(i));
		}
		if (values.size() > MAX_ITEMS) {
			sb.append(" 외 ").append(values.size() - MAX_ITEMS).append("건");
		}
		return sb.toString();
	}

	private void addOnce(List<String> list, String value) {
		if (value != null && !list.contains(value)) {
			list.add(value);
		}
	}

	/** com.x.order.OrderDAO#find(String) → OrderDAO#find(String). 패키지는 검색에 보탬이 적어서 뗀다. */
	private String shortName(String name) {
		if (name == null) {
			return null;
		}
		int sharp = name.indexOf('#');
		String owner = sharp < 0 ? name : name.substring(0, sharp);
		int dot = owner.lastIndexOf('.');
		return dot < 0 ? name : name.substring(dot + 1);
	}

	private String rangeText(Integer lineStart, Integer lineEnd) {
		if (lineStart == null) {
			return "";
		}
		return " (줄 " + lineStart + (lineEnd == null || lineEnd.equals(lineStart) ? "" : "-" + lineEnd) + ")";
	}

	/** 값이 있을 때만 "이름: 값" 한 줄을 붙입니다. */
	private void line(StringBuilder sb, String label, String value) {
		if (value != null && value.length() > 0) {
			sb.append(label).append(": ").append(value).append('\n');
		}
	}

	private Map<String, List<Map<String, Object>>> groupBy(List<Map<String, Object>> rows, String key) {
		Map<String, List<Map<String, Object>>> grouped = new HashMap<String, List<Map<String, Object>>>();
		for (int i = 0; i < rows.size(); i++) {
			String value = (String) rows.get(i).get(key);
			List<Map<String, Object>> list = grouped.get(value);
			if (list == null) {
				list = new ArrayList<Map<String, Object>>();
				grouped.put(value, list);
			}
			list.add(rows.get(i));
		}
		return grouped;
	}

	private Integer intOf(Object value) {
		return value == null ? null : Integer.valueOf(((Number) value).intValue());
	}

}

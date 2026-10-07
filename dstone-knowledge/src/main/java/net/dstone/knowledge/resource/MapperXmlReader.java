package net.dstone.knowledge.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.dstone.knowledge.common.util.SafeXml;

/**
 * <pre>
 * MyBatis 매퍼(mapper)와 iBATIS 매퍼(sqlMap)에서 SQL statement를 읽습니다. 두 가지는 구조가 거의 같아서 한곳에서 읽습니다.
 * JEF 계열 프레임워크의 쿼리 XML(document / query / statement)도 여기서 읽습니다(readQueryXml).
 *
 * SQL의 주석(-- 로 시작하는 줄 주석과 블록 주석)은 그대로 둡니다. 무엇을 하는 SQL인지 적어 둔 설명인 경우가 많아서 문서로 만들 때 씁니다.
 * (테이블을 뽑을 때는 SqlTableExtractor가 주석을 떼고 봅니다.)
 *
 * statement 하나의 SQL은 XML 안에 조각나 있습니다. 조건에 따라 붙는 부분(if, choose, foreach, isNotEmpty ...)과
 * 다른 곳의 조각을 가져오는 부분(include)이 섞여 있기 때문입니다. 여기서는 그것을 "글 한 줄"로 펴 둡니다.
 *   - 조건 태그는 벗기고 안의 글을 모두 남긴다. 실제로는 그중 일부만 실행되지만, 어떤 테이블을 건드리는지 보는 데는 전부 있는 편이 낫다.
 *   - where / set 태그는 WHERE / SET 글자로 바꾼다. trim, foreach, dynamic의 prefix / open / prepend도 글자로 붙인다.
 *   - include는 "@@include(이름)@@" 표시로 남긴다. 다른 파일의 조각일 수 있어서, 모든 매퍼를 읽은 뒤에 채운다(MyBatisPlugin).
 *   - selectKey(키를 미리 뽑는 딸린 SQL)는 뺀다.
 * </pre>
 */
public class MapperXmlReader {

	/** include 자리에 남겨 두는 표시의 앞뒤 */
	public static final String INCLUDE_OPEN = "@@include(";
	public static final String INCLUDE_CLOSE = ")@@";

	/**
	 * @param text 매퍼 파일의 내용
	 * @return statement와 조각(sql)의 목록. 키: mapperType, namespace, statementId, statementType, parameterType, resultType, sqlBody, lineStart, lineEnd
	 */
	public List<Map<String, Object>> read(String text) throws Exception {
		return read(text, null);
	}

	/**
	 * @param text 매퍼 파일의 내용
	 * @param fileName 파일 이름(경로 없이). 쿼리 XML은 네임스페이스가 파일 안에 없고 파일 이름이라서 필요하다. 매퍼만 읽을 때는 null이어도 된다
	 * @return statement와 조각(sql)의 목록. 키는 read(String)과 같다
	 */
	public List<Map<String, Object>> read(String text, String fileName) throws Exception {
		List<Map<String, Object>> statements = new ArrayList<Map<String, Object>>();
		Document document = SafeXml.parse(text);
		Element root = document.getDocumentElement();
		if (root == null) {
			return statements;
		}
		String rootName = root.getNodeName();
		if ("document".equals(rootName)) {
			return readQueryXml(text, root, fileName);
		}
		boolean ibatis = "sqlMap".equals(rootName);
		if (!ibatis && !"mapper".equals(rootName)) {
			return statements;
		}
		String namespace = emptyToNull(root.getAttribute("namespace"));

		// 줄 번호: DOM은 줄 번호를 알려 주지 않아서, 원본 글에서 id="..." 가 나오는 자리를 차례로 찾는다.
		int searchFrom = 0;
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i).getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			Element element = (Element) children.item(i);
			String tag = element.getNodeName();
			String id = emptyToNull(element.getAttribute("id"));
			if (id == null) {
				continue;
			}
			boolean fragment = "sql".equals(tag);
			boolean statement = "select".equals(tag) || "insert".equals(tag) || "update".equals(tag) || "delete".equals(tag)
					|| "statement".equals(tag) || "procedure".equals(tag);
			if (!fragment && !statement) {
				// resultMap, parameterMap, cache, typeAlias 등
				continue;
			}

			StringBuilder sql = new StringBuilder();
			flatten(element, sql);
			String body = tidy(sql.toString());

			int[] lines = linesOf(text, id, searchFrom);
			searchFrom = lines[2];

			Map<String, Object> row = new HashMap<String, Object>();
			row.put("mapperType", ibatis ? "IBATIS" : "MYBATIS");
			row.put("namespace", namespace);
			row.put("statementId", id);
			row.put("statementType", fragment ? "SQL_FRAGMENT" : statementTypeOf(tag, body));
			row.put("parameterType", firstAttribute(element, "parameterType", "parameterClass", "parameterMap"));
			row.put("resultType", firstAttribute(element, "resultType", "resultClass", "resultMap"));
			row.put("sqlBody", body);
			row.put("lineStart", lines[0] == 0 ? null : Integer.valueOf(lines[0]));
			row.put("lineEnd", lines[1] == 0 ? null : Integer.valueOf(lines[1]));
			statements.add(row);
		}
		return statements;
	}

	/**
	 * <pre>
	 * 쿼리 XML을 읽습니다. JEF 계열 프레임워크가 SQL을 적어 두는 파일입니다.
	 *
	 *   &lt;document&gt;
	 *     &lt;query id="selectOrderList" isDynamic="true"&gt;
	 *       &lt;statement&gt;&lt;![CDATA[ SELECT ... WHERE ORDER_NO = :orderNo  #if($status) AND STATUS = :status #end ]]&gt;&lt;/statement&gt;
	 *     &lt;/query&gt;
	 *   &lt;/document&gt;
	 *
	 * 매퍼와 다른 점:
	 *   - 네임스페이스가 파일 안에 없다. 파일 이름이 네임스페이스다(OrderD.xml → Java에서 "OrderD.selectOrderList"로 부른다).
	 *   - 감싸는 태그 이름(query, combo, delete ...)은 SQL의 종류가 아니라 쓰임새다. 종류는 SQL의 첫 단어로 정한다.
	 *   - 조건에 따라 붙는 부분을 태그가 아니라 Velocity 지시문(#if ... #elseif ... #else ... #end)으로 적는다.
	 *     매퍼의 조건 태그와 똑같이, 지시문만 떼고 안의 글은 모두 남긴다.
	 *   - 다른 곳의 조각을 가져오는 include가 없다.
	 * </pre>
	 */
	private List<Map<String, Object>> readQueryXml(String text, Element root, String fileName) {
		List<Map<String, Object>> statements = new ArrayList<Map<String, Object>>();
		String namespace = null;
		if (fileName != null) {
			String name = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
			namespace = emptyToNull(name.lastIndexOf('.') > 0 ? name.substring(0, name.lastIndexOf('.')) : name);
		}
		int searchFrom = 0;
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i).getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			Element element = (Element) children.item(i);
			String id = emptyToNull(element.getAttribute("id"));
			Element statement = firstChild(element, "statement");
			if (id == null || statement == null) {
				continue;
			}
			StringBuilder sql = new StringBuilder();
			flatten(statement, sql);
			String body = tidy(stripVelocity(sql.toString()));

			int[] lines = linesOf(text, id, searchFrom);
			searchFrom = lines[2];

			Map<String, Object> row = new HashMap<String, Object>();
			row.put("mapperType", "QUERY_XML");
			row.put("namespace", namespace);
			row.put("statementId", id);
			row.put("statementType", statementTypeOf("statement", withoutLeadingComments(body)));
			row.put("parameterType", null);
			row.put("resultType", null);
			row.put("sqlBody", body);
			row.put("lineStart", lines[0] == 0 ? null : Integer.valueOf(lines[0]));
			row.put("lineEnd", lines[1] == 0 ? null : Integer.valueOf(lines[1]));
			statements.add(row);
		}
		return statements;
	}

	private Element firstChild(Element parent, String tag) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i).getNodeType() == Node.ELEMENT_NODE && tag.equals(children.item(i).getNodeName())) {
				return (Element) children.item(i);
			}
		}
		return null;
	}

	/**
	 * <pre>
	 * Velocity 지시문을 뗍니다. #if(조건), #elseif(조건), #foreach(...), #set(...) 은 괄호 끝까지, #else 와 #end 는 그 낱말만 뗍니다.
	 * 안의 SQL은 그대로 남습니다. 조건의 괄호는 겹칠 수 있고 따옴표 안에 괄호가 있을 수도 있어서 글자를 하나씩 따라갑니다.
	 * </pre>
	 */
	String stripVelocity(String sql) {
		StringBuilder sb = new StringBuilder();
		int length = sql.length();
		int pos = 0;
		while (pos < length) {
			char c = sql.charAt(pos);
			if (c != '#') {
				sb.append(c);
				pos++;
				continue;
			}
			int wordEnd = pos + 1;
			while (wordEnd < length && Character.isLetter(sql.charAt(wordEnd))) {
				wordEnd++;
			}
			String word = sql.substring(pos + 1, wordEnd);
			if ("else".equals(word) || "end".equals(word)) {
				sb.append(' ');
				pos = wordEnd;
			} else if ("if".equals(word) || "elseif".equals(word) || "foreach".equals(word) || "set".equals(word)) {
				int open = wordEnd;
				while (open < length && (sql.charAt(open) == ' ' || sql.charAt(open) == '\t')) {
					open++;
				}
				int close = open < length && sql.charAt(open) == '(' ? closingParen(sql, open) : -1;
				if (close < 0) {
					// 괄호가 없거나 닫히지 않았다. 지시문이 아닌 것으로 보고 그대로 둔다.
					sb.append(c);
					pos++;
				} else {
					sb.append(' ');
					pos = close + 1;
				}
			} else {
				sb.append(c);
				pos++;
			}
		}
		return sb.toString();
	}

	/** 여는 괄호와 짝이 맞는 닫는 괄호의 자리. 따옴표 안의 괄호는 세지 않는다. 못 찾으면 -1 */
	private int closingParen(String text, int open) {
		int depth = 0;
		char quote = 0;
		for (int i = open; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				}
			} else if (c == '"' || c == '\'') {
				quote = c;
			} else if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0) {
					return i;
				}
			} else if (c == '\n' && depth == 0) {
				return -1;
			}
		}
		return -1;
	}

	/** SQL 맨 앞의 주석을 뗍니다. 종류(SELECT / INSERT ...)를 첫 단어로 정하려면 주석 뒤의 첫 단어를 봐야 합니다. */
	private String withoutLeadingComments(String sql) {
		String rest = sql.trim();
		while (true) {
			if (rest.startsWith("/*")) {
				int end = rest.indexOf("*/");
				if (end < 0) {
					return "";
				}
				rest = rest.substring(end + 2).trim();
			} else if (rest.startsWith("--")) {
				int end = rest.indexOf('\n');
				if (end < 0) {
					return "";
				}
				rest = rest.substring(end + 1).trim();
			} else {
				return rest;
			}
		}
	}

	/**
	 * <pre>
	 * SQL을 보기 좋게 다듬습니다. 줄마다 앞뒤 공백을 떼고 빈 줄을 뺍니다.
	 * 줄바꿈은 남깁니다. SQL의 -- 주석은 "그 줄 끝까지"라서, 줄을 하나로 이으면 주석이 뒤의 SQL까지 삼켜 버립니다.
	 * </pre>
	 */
	private String tidy(String sql) {
		StringBuilder sb = new StringBuilder();
		String[] lines = sql.split("\r?\n");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].replaceAll("[ \t]+", " ").trim();
			if (line.length() > 0) {
				sb.append(sb.length() > 0 ? "\n" : "").append(line);
			}
		}
		return sb.toString();
	}

	/** 요소 안의 SQL을 글 하나로 폅니다. */
	private void flatten(Node node, StringBuilder out) {
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
				out.append(child.getNodeValue());
				continue;
			}
			if (child.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			Element element = (Element) child;
			String tag = element.getNodeName();
			if ("include".equals(tag)) {
				out.append(' ').append(INCLUDE_OPEN).append(element.getAttribute("refid")).append(INCLUDE_CLOSE).append(' ');
			} else if ("selectKey".equals(tag)) {
				// 키를 미리 뽑는 딸린 SQL. 본문에 섞으면 SQL이 깨진다.
				continue;
			} else if ("where".equals(tag)) {
				out.append(" WHERE ");
				flatten(element, out);
			} else if ("set".equals(tag)) {
				out.append(" SET ");
				flatten(element, out);
			} else {
				// if, choose/when/otherwise, foreach, trim, bind, dynamic, isNotEmpty, iterate ...
				// 앞뒤에 붙이라고 적어 둔 글자(prefix, open, prepend)가 있으면 붙인다.
				out.append(' ').append(firstAttributeOrEmpty(element, "prefix", "open", "prepend")).append(' ');
				flatten(element, out);
				out.append(' ').append(firstAttributeOrEmpty(element, "suffix", "close")).append(' ');
			}
		}
	}

	/** select / insert ... 태그는 그대로, statement / procedure 태그는 SQL의 첫 단어로 정합니다. */
	private String statementTypeOf(String tag, String body) {
		if ("select".equals(tag) || "insert".equals(tag) || "update".equals(tag) || "delete".equals(tag)) {
			return tag.toUpperCase(Locale.ROOT);
		}
		String upper = body.toUpperCase(Locale.ROOT);
		if (upper.startsWith("SELECT") || upper.startsWith("WITH")) {
			return "SELECT";
		}
		if (upper.startsWith("INSERT") || upper.startsWith("MERGE")) {
			return "INSERT";
		}
		if (upper.startsWith("UPDATE")) {
			return "UPDATE";
		}
		if (upper.startsWith("DELETE")) {
			return "DELETE";
		}
		return "procedure".equals(tag) ? "PROCEDURE" : "OTHER";
	}

	/**
	 * <pre>
	 * 원본 글에서 id="..." 가 있는 요소의 시작 줄과 끝 줄을 찾습니다.
	 * </pre>
	 *
	 * @return [시작 줄, 끝 줄, 다음에 찾기 시작할 자리]. 못 찾으면 줄은 0
	 */
	private int[] linesOf(String text, String id, int from) {
		int at = indexOfId(text, id, from);
		if (at < 0) {
			return new int[] { 0, 0, from };
		}
		int open = text.lastIndexOf('<', at);
		int startLine = lineAt(text, open < 0 ? at : open);
		// 닫는 태그: 여는 태그의 이름을 읽어 그 닫는 태그를 찾는다.
		int nameEnd = open + 1;
		while (nameEnd < text.length() && !Character.isWhitespace(text.charAt(nameEnd)) && text.charAt(nameEnd) != '>') {
			nameEnd++;
		}
		String tag = open < 0 ? "" : text.substring(open + 1, nameEnd);
		int close = tag.length() == 0 ? -1 : text.indexOf("</" + tag, at);
		int endLine = close < 0 ? startLine : lineAt(text, close);
		return new int[] { startLine, endLine, at + 1 };
	}

	private int indexOfId(String text, String id, int from) {
		int doubleQuoted = text.indexOf("id=\"" + id + "\"", from);
		int singleQuoted = text.indexOf("id='" + id + "'", from);
		if (doubleQuoted < 0) {
			return singleQuoted;
		}
		if (singleQuoted < 0) {
			return doubleQuoted;
		}
		return Math.min(doubleQuoted, singleQuoted);
	}

	private int lineAt(String text, int index) {
		int line = 1;
		for (int i = 0; i < index && i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	private String firstAttribute(Element element, String... names) {
		String value = firstAttributeOrEmpty(element, names);
		return value.length() == 0 ? null : value;
	}

	private String firstAttributeOrEmpty(Element element, String... names) {
		for (int i = 0; i < names.length; i++) {
			if (element.hasAttribute(names[i])) {
				return element.getAttribute(names[i]);
			}
		}
		return "";
	}

	private String emptyToNull(String value) {
		return value == null || value.trim().length() == 0 ? null : value.trim();
	}

}

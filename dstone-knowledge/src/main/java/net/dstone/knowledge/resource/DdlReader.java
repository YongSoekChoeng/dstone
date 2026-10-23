package net.dstone.knowledge.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dstone.knowledge.common.util.JsonText;

/**
 * <pre>
 * DDL 파일(.sql)에서 테이블과 컬럼, 그리고 그 설명(COMMENT)을 읽습니다.
 *
 * 읽는 것:
 *   CREATE TABLE 이름 ( 컬럼 타입 ... [COMMENT '설명'], ... ) [COMMENT [=] '설명']   (MySQL 은 설명을 여기에 적는다)
 *   COMMENT ON TABLE 이름 IS '설명'                                              (Oracle / PostgreSQL)
 *   COMMENT ON COLUMN 이름.컬럼 IS '설명'
 *
 * 결과는 analysis_resource 에 들어갈 행입니다.
 *   DDL_TABLE  name = 테이블,         value = 설명(없으면 null)
 *   DDL_COLUMN name = 테이블.컬럼,    value = 설명(없으면 null), properties_json = {table, column, dataType, ordinal}
 * COMMENT ON 은 CREATE TABLE 과 다른 파일에 있을 수 있어서, 설명만 있는 행(dataType 없음)으로 따로 남깁니다.
 * 같은 테이블의 행을 합치는 일은 읽는 쪽(TableDictionaryService)이 합니다.
 *
 * 이름은 따옴표를 벗기고 대문자로 맞추며, 스키마는 뗍니다(SQL 쪽 테이블 이름과 같은 기준).
 * 문법을 다 따지지 않습니다. 모르는 문장(INSERT, CREATE INDEX ...)은 건너뜁니다.
 * </pre>
 */
public class DdlReader {

	private static final Pattern CREATE_TABLE = Pattern.compile(
			"(?is)^CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:GLOBAL\\s+|LOCAL\\s+)?(?:TEMPORARY\\s+|TEMP\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w$#.\"`\\[\\]]+)\\s*\\(");

	private static final Pattern COMMENT_ON = Pattern.compile(
			"(?is)^COMMENT\\s+ON\\s+(TABLE|COLUMN)\\s+([\\w$#.\"`\\[\\]]+)\\s+IS\\s+'((?:[^']|'')*)'");

	/** 컬럼 정의 안의 MySQL 설명: COMMENT '설명' */
	private static final Pattern COLUMN_COMMENT = Pattern.compile("(?is)\\bCOMMENT\\s+'((?:[^']|'')*)'");

	/** 괄호 뒤 테이블 옵션의 MySQL 설명: COMMENT = '설명' 또는 COMMENT '설명' */
	private static final Pattern TABLE_COMMENT = Pattern.compile("(?is)\\bCOMMENT\\s*=?\\s*'((?:[^']|'')*)'");

	/** 컬럼이 아니라 제약조건 / 인덱스를 적은 줄의 첫 낱말 */
	private static final String NOT_COLUMNS = "|CONSTRAINT|PRIMARY|UNIQUE|KEY|INDEX|FOREIGN|CHECK|FULLTEXT|SPATIAL|EXCLUDE|PERIOD|LIKE|";

	/** 설명 하나의 최대 길이 */
	private static final int MAX_COMMENT = 1000;

	/**
	 * @param text DDL 파일의 내용
	 * @return analysis_resource 에 넣을 행들(revisionId / fileId 는 부르는 쪽이 채운다)
	 */
	public List<Map<String, Object>> read(String text) {
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		String source = text == null ? "" : text;
		List<int[]> statements = split(source);
		// 줄 번호는 앞 문장에서 센 데 이어서 센다. 문장마다 처음부터 세면 큰 파일에서 느려진다.
		int counted = 0;
		int line = 1;
		for (int i = 0; i < statements.size(); i++) {
			int[] range = statements.get(i);
			String sql = withoutComments(source.substring(range[0], range[1])).trim();
			int at = firstWordAt(source, range[0], range[1]);
			for (; counted < at; counted++) {
				if (source.charAt(counted) == '\n') {
					line++;
				}
			}
			Matcher create = CREATE_TABLE.matcher(sql);
			if (create.find()) {
				readCreateTable(sql, create, line, rows);
				continue;
			}
			Matcher comment = COMMENT_ON.matcher(sql);
			if (comment.find()) {
				readCommentOn(comment, line, rows);
			}
		}
		return rows;
	}

	private void readCreateTable(String sql, Matcher create, int line, List<Map<String, Object>> rows) {
		String table = tableName(create.group(1));
		int open = create.end() - 1;
		int close = closingParen(sql, open);
		if (table == null || close < 0) {
			return;
		}
		Matcher tableComment = TABLE_COMMENT.matcher(sql.substring(close + 1));
		rows.add(row("DDL_TABLE", table, tableComment.find() ? unquote(tableComment.group(1)) : null, new LinkedHashMap<String, Object>(), line));

		List<String> parts = topLevelParts(sql.substring(open + 1, close));
		int ordinal = 0;
		for (int i = 0; i < parts.size(); i++) {
			String part = parts.get(i).trim();
			String[] words = part.split("\\s+", 3);
			if (words.length < 2 || NOT_COLUMNS.indexOf("|" + words[0].toUpperCase(Locale.ROOT) + "|") >= 0) {
				continue;
			}
			String column = bareName(words[0]);
			if (column.length() == 0) {
				continue;
			}
			ordinal++;
			Matcher comment = COLUMN_COMMENT.matcher(part);
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("table", table);
			properties.put("column", column);
			properties.put("dataType", dataTypeOf(part.substring(words[0].length()).trim()));
			properties.put("ordinal", ordinal);
			rows.add(row("DDL_COLUMN", table + "." + column, comment.find() ? unquote(comment.group(1)) : null, properties, line));
		}
	}

	private void readCommentOn(Matcher comment, int line, List<Map<String, Object>> rows) {
		String target = comment.group(2);
		String text = unquote(comment.group(3));
		if ("TABLE".equalsIgnoreCase(comment.group(1))) {
			String table = tableName(target);
			if (table != null) {
				rows.add(row("DDL_TABLE", table, text, new LinkedHashMap<String, Object>(), line));
			}
			return;
		}
		// 스키마.테이블.컬럼 이거나 테이블.컬럼. 마지막 마디가 컬럼, 그 앞이 테이블이다.
		int dot = target.lastIndexOf('.');
		if (dot <= 0) {
			return;
		}
		String table = tableName(target.substring(0, dot));
		String column = bareName(target.substring(dot + 1));
		if (table == null || column.length() == 0) {
			return;
		}
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("table", table);
		properties.put("column", column);
		rows.add(row("DDL_COLUMN", table + "." + column, text, properties, line));
	}

	/** 컬럼 정의에서 타입 부분: "VARCHAR(20) NOT NULL ..." → "VARCHAR(20)" */
	private String dataTypeOf(String rest) {
		StringBuilder sb = new StringBuilder();
		int depth = 0;
		for (int i = 0; i < rest.length(); i++) {
			char c = rest.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
			} else if (Character.isWhitespace(c) && depth == 0) {
				// "NUMBER (10)" 처럼 괄호 앞에 띄어 쓴 경우는 이어 붙인다.
				int next = i;
				while (next < rest.length() && Character.isWhitespace(rest.charAt(next))) {
					next++;
				}
				if (next < rest.length() && rest.charAt(next) == '(') {
					i = next - 1;
					continue;
				}
				break;
			}
			sb.append(c);
		}
		String type = sb.toString().toUpperCase(Locale.ROOT);
		return type.length() > 100 ? type.substring(0, 100) : type;
	}

	/** 문장 안에서 공백과 주석을 건너뛴 첫 글자의 위치. 줄 번호가 CREATE / COMMENT 가 적힌 줄을 가리키게 한다 */
	private int firstWordAt(String text, int from, int to) {
		int at = from;
		while (at < to) {
			if (Character.isWhitespace(text.charAt(at))) {
				at++;
			} else if (text.startsWith("--", at)) {
				int end = text.indexOf('\n', at);
				at = end < 0 || end > to ? to : end;
			} else if (text.startsWith("/*", at)) {
				int end = text.indexOf("*/", at + 2);
				at = end < 0 || end + 2 > to ? to : end + 2;
			} else {
				break;
			}
		}
		return at;
	}

	/** 문장을 ; 로 나눕니다(따옴표와 주석 안의 ; 는 빼고). Oracle 스크립트의 줄 하나짜리 / 도 문장 끝으로 봅니다. */
	private List<int[]> split(String text) {
		List<int[]> ranges = new ArrayList<int[]>();
		int start = 0;
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '\'') {
				i = skipQuoted(text, i);
				continue;
			}
			if (c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '-') {
				int end = text.indexOf('\n', i);
				i = end < 0 ? text.length() : end;
				continue;
			}
			if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
				int end = text.indexOf("*/", i + 2);
				i = end < 0 ? text.length() : end + 2;
				continue;
			}
			boolean slashLine = c == '/' && lineIsOnly(text, i, "/");
			if (c == ';' || slashLine) {
				ranges.add(new int[] { start, i });
				start = i + 1;
			}
			i++;
		}
		if (start < text.length()) {
			ranges.add(new int[] { start, text.length() });
		}
		return ranges;
	}

	private boolean lineIsOnly(String text, int at, String token) {
		int lineStart = text.lastIndexOf('\n', at - 1) + 1;
		int lineEnd = text.indexOf('\n', at);
		String line = text.substring(lineStart, lineEnd < 0 ? text.length() : lineEnd).trim();
		return token.equals(line);
	}

	/** 작은따옴표 글자의 끝 바로 뒤 위치. '' 는 따옴표 하나다. */
	private int skipQuoted(String text, int open) {
		int i = open + 1;
		while (i < text.length()) {
			if (text.charAt(i) == '\'') {
				if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
					i += 2;
					continue;
				}
				return i + 1;
			}
			i++;
		}
		return text.length();
	}

	/** 주석을 공백으로 바꿉니다. 따옴표 안의 -- 나 /* 는 그대로 둡니다. */
	private String withoutComments(String sql) {
		StringBuilder sb = new StringBuilder(sql.length());
		int i = 0;
		while (i < sql.length()) {
			char c = sql.charAt(i);
			if (c == '\'') {
				int end = skipQuoted(sql, i);
				sb.append(sql, i, end);
				i = end;
			} else if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
				int end = sql.indexOf('\n', i);
				i = end < 0 ? sql.length() : end;
				sb.append(' ');
			} else if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
				int end = sql.indexOf("*/", i + 2);
				i = end < 0 ? sql.length() : end + 2;
				sb.append(' ');
			} else {
				sb.append(c);
				i++;
			}
		}
		return sb.toString();
	}

	/** 여는 괄호와 짝이 맞는 닫는 괄호의 위치. 없으면 -1 */
	private int closingParen(String sql, int open) {
		int depth = 0;
		int i = open;
		while (i < sql.length()) {
			char c = sql.charAt(i);
			if (c == '\'') {
				i = skipQuoted(sql, i);
				continue;
			}
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
			i++;
		}
		return -1;
	}

	/** 괄호 안의 글을 맨 바깥 쉼표로 나눕니다. NUMBER(10,2) 의 쉼표나 따옴표 안의 쉼표에서는 나누지 않습니다. */
	private List<String> topLevelParts(String body) {
		List<String> parts = new ArrayList<String>();
		int depth = 0;
		int start = 0;
		int i = 0;
		while (i < body.length()) {
			char c = body.charAt(i);
			if (c == '\'') {
				i = skipQuoted(body, i);
				continue;
			}
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
			} else if (c == ',' && depth == 0) {
				parts.add(body.substring(start, i));
				start = i + 1;
			}
			i++;
		}
		parts.add(body.substring(start));
		return parts;
	}

	/** 스키마.테이블 → TABLE. 이름이 아니면 null */
	private String tableName(String written) {
		String name = written;
		int dot = name.lastIndexOf('.');
		if (dot >= 0) {
			name = name.substring(dot + 1);
		}
		name = bareName(name);
		return name.length() == 0 || name.length() > 300 ? null : name;
	}

	private String bareName(String name) {
		return name.replace("\"", "").replace("`", "").replace("[", "").replace("]", "").trim().toUpperCase(Locale.ROOT);
	}

	private String unquote(String text) {
		String value = text.replace("''", "'").trim();
		if (value.length() == 0) {
			return null;
		}
		return value.length() > MAX_COMMENT ? value.substring(0, MAX_COMMENT) : value;
	}

	private Map<String, Object> row(String type, String name, String value, Map<String, Object> properties, int line) {
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("resourceType", type);
		row.put("name", name);
		row.put("location", null);
		row.put("value", value);
		row.put("propertiesJson", JsonText.of(properties));
		row.put("lineStart", line);
		return row;
	}

}

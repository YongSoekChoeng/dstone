package net.dstone.knowledge.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <pre>
 * 사람이 읽기 좋게 SQL에 줄바꿈과 들여쓰기를 넣습니다. 노드 맵의 [SQL 보기] 팝업이 씁니다.
 *
 *   SELECT
 *       컬럼1
 *       , 컬럼2
 *   FROM
 *       테이블 A
 *       LEFT JOIN 테이블 B
 *           ON A.X = B.X
 *   WHERE
 *       A.Y = 'AA'
 *       AND B.Z = #{z}
 *   ORDER BY
 *       A.Y
 *
 * 규칙
 *   - 절(SELECT, FROM, WHERE, GROUP BY, ORDER BY ...)은 한 줄에 혼자 두고, 그 내용은 다음 줄부터 4칸 들여 쓴다.
 *   - 절 안의 쉼표는 줄 앞에 둔다(", 컬럼2"). WHERE / HAVING / ON 의 AND · OR 도 줄 앞에 둔다. BETWEEN a AND b 의 AND 는 그대로 둔다.
 *   - 괄호 안이 SELECT 면(서브쿼리) 4칸 더 들여 쓰고, INSERT 의 컬럼 목록과 VALUES 목록은 한 줄에 하나씩 쓴다.
 *     함수 호출, IN 목록 같은 그 밖의 괄호는 한 줄에 둔다.
 *   - CASE 는 WHEN / ELSE 를 한 줄씩, END 를 CASE 와 같은 칸에 둔다.
 *   - 주석은 원래 줄 끝에 있었으면 줄 끝에, 혼자 한 줄이었으면 혼자 한 줄에 둔다.
 *   - 글자(대소문자, 문자열, 파라미터 #{..} / #x# / @@include(..)@@)는 바꾸지 않는다. 공백과 줄바꿈만 바꾼다.
 *     공백을 뺀 글자가 원문과 하나라도 다르게 되면 원문을 그대로 돌려준다.
 * </pre>
 */
public final class SqlFormatter {

	private static final int INDENT = 4;

	/** 이것보다 긴 SQL은 정리하지 않는다 */
	private static final int MAX_LENGTH = 500000;

	/** 내용이 다음 줄에 들여 써지는 절 */
	private static final String CONTENT_CLAUSES = "|SELECT|FROM|WHERE|HAVING|SET|VALUES|USING|WITH|INTO|UPDATE|DELETE|LIMIT|OFFSET|FETCH|"
			+ "GROUP BY|ORDER BY|ORDER SIBLINGS BY|CONNECT BY|START WITH|INSERT INTO|INSERT|DELETE FROM|MERGE INTO|";

	/** 혼자 한 줄에 서는 절(뒤에 오는 SELECT 가 다시 줄을 바꾼다) */
	private static final String ALONE_CLAUSES = "|UNION|UNION ALL|MINUS|INTERSECT|EXCEPT|";

	/** AND / OR 를 줄 앞에 두는 절 */
	private static final String CONDITION_CLAUSES = "|WHERE|HAVING|ON|CONNECT BY|START WITH|";

	/** JOIN 앞에 붙는 말 */
	private static final String JOIN_PREFIXES = "|LEFT|RIGHT|FULL|INNER|CROSS|NATURAL|OUTER|";

	/** 앞뒤로 한 칸씩 띄우는 비교 연산자 */
	private static final String COMPARISONS = "|=|<>|!=|^=|>=|<=|<|>|";

	private enum Type { SPACE, BLOCK_COMMENT, LINE_COMMENT, STRING, PARAM, WORD, OPEN, CLOSE, COMMA, SEMI, OP }

	private static class Token {
		final Type type;
		final String text;
		/** 앞에 공백이 있었는지, 그 공백에 줄바꿈이 있었는지 */
		boolean spaceBefore;
		boolean newlineBefore;

		Token(Type type, String text) {
			this.type = type;
			this.text = text;
		}

		String upper() {
			return text.toUpperCase(Locale.ROOT);
		}

		boolean is(String word) {
			return type == Type.WORD && text.equalsIgnoreCase(word);
		}
	}

	/** 괄호 한 단계 */
	private static class Frame {
		/** QUERY: SELECT 문(맨 바깥, 서브쿼리) / LIST: 한 줄에 하나씩 쓰는 목록 / INLINE: 한 줄에 두는 괄호 */
		final String kind;
		/** 절 이름이 서는 칸(QUERY), 목록 항목이 서는 칸(LIST) */
		final int base;
		/** 이 괄호를 연 줄의 칸. 닫는 괄호가 여기에 선다 */
		final int openIndent;
		String clause;
		boolean pendingContent;
		boolean inOn;
		boolean betweenPending;
		final List<Integer> cases = new ArrayList<Integer>();

		Frame(String kind, int base, int openIndent) {
			this.kind = kind;
			this.base = base;
			this.openIndent = openIndent;
		}
	}

	private final List<Token> tokens;
	private final StringBuilder out = new StringBuilder();
	private final List<Frame> frames = new ArrayList<Frame>();
	/** 지금 줄의 들여쓰기와, 그 줄에 아직 아무것도 안 썼는지 */
	private int lineIndent = 0;
	private boolean lineEmpty = true;
	/** 줄 주석 다음에는 반드시 줄을 바꾼다 */
	private boolean mustBreak = false;
	/** 방금 쓴 것이 줄 앞 쉼표(", ")라서 다음 글자를 붙여 써야 하는지 */
	private boolean glueNext = false;
	private Token previous;

	private SqlFormatter(List<Token> tokens) {
		this.tokens = tokens;
	}

	/**
	 * @param sql 매퍼에 적힌 SQL
	 * @return 정리한 SQL. 정리할 수 없으면 원문 그대로
	 */
	public static String format(String sql) {
		if (sql == null || sql.length() == 0 || sql.length() > MAX_LENGTH) {
			return sql;
		}
		try {
			SqlFormatter formatter = new SqlFormatter(tokenize(sql));
			String formatted = formatter.run();
			// 공백을 뺀 글자가 원문과 같아야 한다. 다르면 무언가 잘못 읽은 것이다.
			return withoutSpaces(formatted).equals(withoutSpaces(sql)) ? formatted : sql;
		} catch (RuntimeException e) {
			return sql;
		}
	}

	/* ============================== 글자 나누기 ============================== */

	private static List<Token> tokenize(String sql) {
		List<Token> result = new ArrayList<Token>();
		int length = sql.length();
		int i = 0;
		boolean space = false;
		boolean newline = false;
		while (i < length) {
			char c = sql.charAt(i);
			int start = i;
			Type type;
			if (Character.isWhitespace(c)) {
				space = true;
				if (c == '\n' || c == '\r') {
					newline = true;
				}
				i++;
				continue;
			} else if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
				int end = sql.indexOf("*/", i + 2);
				i = end < 0 ? length : end + 2;
				type = Type.BLOCK_COMMENT;
			} else if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
				while (i < length && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
					i++;
				}
				type = Type.LINE_COMMENT;
			} else if (c == '\'' || c == '"' || c == '`') {
				i = quotedEnd(sql, i, c);
				type = c == '\'' ? Type.STRING : Type.WORD;
			} else if ((c == '#' || c == '$') && i + 1 < length && sql.charAt(i + 1) == '{') {
				int end = sql.indexOf('}', i + 2);
				i = end < 0 ? length : end + 1;
				type = Type.PARAM;
			} else if ((c == '#' || c == '$') && i + 1 < length && isIdentifierStart(sql.charAt(i + 1)) && ibatisEnd(sql, i) > 0) {
				i = ibatisEnd(sql, i);
				type = Type.PARAM;
			} else if (c == '@' && sql.startsWith("@@include(", i)) {
				int end = sql.indexOf(")@@", i);
				i = end < 0 ? length : end + 3;
				type = Type.PARAM;
			} else if (isWordChar(c)) {
				while (i < length && (isWordChar(sql.charAt(i)) || sql.charAt(i) == '#' || sql.charAt(i) == '$')) {
					i++;
				}
				type = Type.WORD;
			} else if (c == '(') {
				i++;
				type = Type.OPEN;
			} else if (c == ')') {
				i++;
				type = Type.CLOSE;
			} else if (c == ',') {
				i++;
				type = Type.COMMA;
			} else if (c == ';') {
				i++;
				type = Type.SEMI;
			} else {
				i++;
				// 두 글자 연산자
				if (i < length && "<>!^=|:".indexOf(c) >= 0 && "=>|:".indexOf(sql.charAt(i)) >= 0) {
					i++;
				}
				type = Type.OP;
			}
			Token token = new Token(type, sql.substring(start, i));
			token.spaceBefore = space;
			token.newlineBefore = newline;
			result.add(token);
			space = false;
			newline = false;
		}
		return result;
	}

	private static int quotedEnd(String sql, int start, char quote) {
		int i = start + 1;
		while (i < sql.length()) {
			if (sql.charAt(i) == quote) {
				// '' 는 문자열 안의 따옴표다.
				if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
					i += 2;
					continue;
				}
				return i + 1;
			}
			i++;
		}
		return sql.length();
	}

	/** iBATIS 의 #name# / $name$. 아니면 -1 */
	private static int ibatisEnd(String sql, int start) {
		char mark = sql.charAt(start);
		int i = start + 1;
		while (i < sql.length() && i - start < 200) {
			char c = sql.charAt(i);
			if (c == mark) {
				return i + 1;
			}
			if (!isWordChar(c) && c != '[' && c != ']' && c != ':' && c != ',' && c != '=') {
				return -1;
			}
			i++;
		}
		return -1;
	}

	private static boolean isIdentifierStart(char c) {
		return Character.isLetter(c) || c == '_';
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '.';
	}

	private static String withoutSpaces(String text) {
		StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			if (!Character.isWhitespace(text.charAt(i))) {
				sb.append(text.charAt(i));
			}
		}
		return sb.toString();
	}

	/* ============================== 쓰기 ============================== */

	private String run() {
		frames.add(new Frame("QUERY", 0, 0));
		int i = 0;
		while (i < tokens.size()) {
			i = handle(i);
		}
		return trimEnd(out.toString());
	}

	private Frame frame() {
		return frames.get(frames.size() - 1);
	}

	/** 토큰 하나(여러 단어로 된 절이면 그 단어들)를 쓰고 다음 위치를 돌려줍니다 */
	private int handle(int i) {
		Token token = tokens.get(i);
		Frame frame = frame();
		boolean query = "QUERY".equals(frame.kind);

		if (token.type == Type.BLOCK_COMMENT || token.type == Type.LINE_COMMENT) {
			comment(token, frame);
			return i + 1;
		}
		if (query && token.type == Type.WORD && frame.cases.isEmpty()) {
			int next = clause(i, frame);
			if (next > i) {
				return next;
			}
			next = join(i, frame);
			if (next > i) {
				return next;
			}
		}
		if (token.type == Type.OPEN) {
			open(i, frame);
			return i + 1;
		}
		if (token.type == Type.CLOSE) {
			close(token);
			return i + 1;
		}

		startContent(frame);
		if (token.type == Type.COMMA) {
			if ((query && frame.cases.isEmpty()) || "LIST".equals(frame.kind)) {
				// 절이나 목록의 쉼표는 줄 앞에 둔다.
				newline(contentIndent(frame));
				frame.inOn = false;
			}
			// 쉼표 뒤는 한 칸 띄우고, 다음 글자는 그 뒤에 붙여 쓴다.
			write(", ", false);
			glueNext = true;
			previous = token;
			return i + 1;
		}
		if (token.type == Type.SEMI) {
			write(";", false);
			newline(0);
			out.append('\n');
			frame.clause = null;
			previous = token;
			return i + 1;
		}
		if (query) {
			if (token.is("BETWEEN") && frame.cases.isEmpty()) {
				frame.betweenPending = true;
			} else if ((token.is("AND") || token.is("OR")) && frame.cases.isEmpty()) {
				if (token.is("AND") && frame.betweenPending) {
					frame.betweenPending = false;
				} else if (frame.inOn) {
					newline(frame.base + INDENT * 2);
				} else if (frame.clause != null && CONDITION_CLAUSES.indexOf("|" + frame.clause + "|") >= 0) {
					newline(frame.base + INDENT);
				}
			} else if (token.is("CASE")) {
				write(token.text, needSpace(token));
				frame.cases.add(Integer.valueOf(lineIndent));
				previous = token;
				return i + 1;
			} else if (!frame.cases.isEmpty() && (token.is("WHEN") || token.is("ELSE"))) {
				newline(frame.cases.get(frame.cases.size() - 1).intValue() + INDENT);
			} else if (!frame.cases.isEmpty() && token.is("END")) {
				newline(frame.cases.remove(frame.cases.size() - 1).intValue());
			} else if (token.is("WHEN") && frame.cases.isEmpty()) {
				// MERGE 의 WHEN MATCHED / WHEN NOT MATCHED
				newline(frame.base);
				frame.clause = null;
			}
		}
		write(token.text, needSpace(token));
		previous = token;
		return i + 1;
	}

	/** 절 이름이면 줄을 바꿔 쓰고 다음 위치를, 아니면 i 를 돌려줍니다 */
	private int clause(int i, Frame frame) {
		String one = tokens.get(i).upper();
		String two = one + " " + wordAt(i, 1);
		String three = two + " " + wordAt(i, 2);
		String name;
		int words;
		if (CONTENT_CLAUSES.indexOf("|" + three + "|") >= 0 || ALONE_CLAUSES.indexOf("|" + three + "|") >= 0) {
			name = three;
			words = 3;
		} else if (CONTENT_CLAUSES.indexOf("|" + two + "|") >= 0 || ALONE_CLAUSES.indexOf("|" + two + "|") >= 0) {
			name = two;
			words = 2;
		} else if (CONTENT_CLAUSES.indexOf("|" + one + "|") >= 0 || ALONE_CLAUSES.indexOf("|" + one + "|") >= 0 || "ON".equals(one)) {
			name = one;
			words = 1;
		} else {
			return i;
		}
		// FOR UPDATE, ON DELETE 같은 것은 절이 아니다. SELECT ... INTO 변수(PL/SQL)도 그대로 둔다.
		if (previous != null && previous.type == Type.WORD && (previous.is("FOR") || previous.is("ON")) && !"ON".equals(name)) {
			return i;
		}
		if ("ON".equals(name)) {
			if ("FROM".equals(frame.clause)) {
				newline(frame.base + INDENT * 2);
				frame.inOn = true;
				return writeWords(i, 1);
			}
			if (!"USING".equals(frame.clause)) {
				return i;
			}
			name = "ON";
		}
		if ("INTO".equals(name) && !"INSERT".equals(frame.clause) && !"MERGE INTO".equals(frame.clause)) {
			return i;
		}
		// WITH 는 문장 맨 앞에서만 절이다(WITH (NOLOCK), WITH TIES 는 아니다). USING 은 MERGE 의 것만 절이다(JOIN ... USING (컬럼) 은 아니다).
		if ("WITH".equals(name) && previous != null && previous.type != Type.OPEN && previous.type != Type.SEMI) {
			return i;
		}
		if ("USING".equals(name) && !"MERGE INTO".equals(frame.clause)) {
			return i;
		}
		if ("SELECT".equals(name) || "WITH".equals(name)) {
			// SELECT DISTINCT / SELECT ALL 은 한 줄에 둔다.
			String after = wordAt(i, 1);
			if ("DISTINCT".equals(after) || "ALL".equals(after) || "UNIQUE".equals(after)) {
				words = 2;
			}
		}
		newline(frame.base);
		int next = writeWords(i, words);
		frame.clause = name;
		frame.inOn = false;
		frame.betweenPending = false;
		frame.pendingContent = ALONE_CLAUSES.indexOf("|" + name + "|") < 0;
		if (!frame.pendingContent) {
			frame.clause = null;
		}
		return next;
	}

	/** FROM 절의 JOIN 이면 줄을 바꿔 쓰고 다음 위치를, 아니면 i 를 돌려줍니다 */
	private int join(int i, Frame frame) {
		if (!"FROM".equals(frame.clause)) {
			return i;
		}
		int words = 0;
		while (JOIN_PREFIXES.indexOf("|" + wordAt(i, words) + "|") >= 0 && words < 3) {
			words++;
		}
		if (!"JOIN".equals(wordAt(i, words))) {
			return i;
		}
		frame.pendingContent = false;
		newline(frame.base + INDENT);
		frame.inOn = false;
		return writeWords(i, words + 1);
	}

	/** i 에서 공백만 건너뛰며 n 번째 뒤의 단어(대문자). 단어가 아니면 빈 글자 */
	private String wordAt(int i, int n) {
		int index = i + n;
		if (index >= tokens.size()) {
			return "";
		}
		for (int k = i + 1; k <= index; k++) {
			if (tokens.get(k).type != Type.WORD) {
				return "";
			}
		}
		return tokens.get(index).upper();
	}

	private int writeWords(int i, int count) {
		for (int k = 0; k < count; k++) {
			Token token = tokens.get(i + k);
			write(token.text, k > 0 || needSpace(token));
			previous = token;
		}
		return i + count;
	}

	private void open(int i, Frame frame) {
		Token next = nextSignificant(i);
		boolean subquery = next != null && (next.is("SELECT") || next.is("WITH"));
		boolean list = false;
		if (!subquery && "QUERY".equals(frame.kind) && frame.cases.isEmpty()) {
			// INSERT INTO 테이블 ( 컬럼 목록 ), VALUES ( 값 목록 )
			list = (("INSERT INTO".equals(frame.clause) || "INTO".equals(frame.clause)) && previous != null && previous.type == Type.WORD
					&& !frame.pendingContent) || ("VALUES".equals(frame.clause) && frame.pendingContent);
		}
		startContent(frame);
		Token token = tokens.get(i);
		write("(", needSpace(token) || (list && !lineEmpty));
		previous = token;
		if (subquery) {
			frames.add(new Frame("QUERY", lineIndent + INDENT, lineIndent));
		} else if (list) {
			Frame child = new Frame("LIST", lineIndent + INDENT, lineIndent);
			child.pendingContent = true;
			frames.add(child);
		} else {
			frames.add(new Frame("INLINE", lineIndent, lineIndent));
		}
		glueNext = true;
	}

	private void close(Token token) {
		if (frames.size() == 1) {
			write(")", false);
			previous = token;
			return;
		}
		Frame closing = frames.remove(frames.size() - 1);
		if (!"INLINE".equals(closing.kind)) {
			newline(closing.openIndent);
		}
		write(")", false);
		previous = token;
	}

	private void comment(Token token, Frame frame) {
		if (token.newlineBefore || mustBreak) {
			int indent = frame.pendingContent ? contentIndent(frame) : (lineEmpty ? lineIndent : Math.max(lineIndent, contentIndent(frame)));
			if ("INLINE".equals(frame.kind)) {
				indent = lineIndent + INDENT;
			}
			newline(indent);
		}
		write(token.text, !lineEmpty);
		glueNext = false;
		mustBreak = token.type == Type.LINE_COMMENT;
	}

	/** 절 이름 다음의 첫 내용이면 줄을 바꿉니다 */
	private void startContent(Frame frame) {
		if (frame.pendingContent) {
			newline(contentIndent(frame));
			frame.pendingContent = false;
		} else if (mustBreak) {
			newline(lineIndent);
		}
	}

	private int contentIndent(Frame frame) {
		if ("LIST".equals(frame.kind)) {
			return frame.base;
		}
		if ("INLINE".equals(frame.kind)) {
			return lineIndent;
		}
		return frame.clause == null ? frame.base : frame.base + INDENT;
	}

	private Token nextSignificant(int i) {
		for (int k = i + 1; k < tokens.size(); k++) {
			Type type = tokens.get(k).type;
			if (type != Type.BLOCK_COMMENT && type != Type.LINE_COMMENT) {
				return tokens.get(k);
			}
		}
		return null;
	}

	private boolean needSpace(Token token) {
		if (lineEmpty || glueNext) {
			return false;
		}
		if (previous != null && previous.type == Type.OP && COMPARISONS.indexOf("|" + previous.text + "|") >= 0) {
			return true;
		}
		if (token.type == Type.OP && COMPARISONS.indexOf("|" + token.text + "|") >= 0) {
			return true;
		}
		return token.spaceBefore;
	}

	private void write(String text, boolean space) {
		if (mustBreak && !lineEmpty) {
			newline(lineIndent);
		}
		mustBreak = false;
		if (space && !lineEmpty) {
			out.append(' ');
		}
		out.append(text);
		lineEmpty = false;
		glueNext = false;
	}

	private void newline(int indent) {
		mustBreak = false;
		glueNext = false;
		if (!lineEmpty) {
			trimLineEnd();
			out.append('\n');
		} else {
			// 아직 아무것도 안 쓴 줄: 들여쓰기만 다시 맞춘다.
			int lineStart = out.lastIndexOf("\n") + 1;
			out.setLength(lineStart);
		}
		for (int i = 0; i < indent; i++) {
			out.append(' ');
		}
		lineIndent = indent;
		lineEmpty = true;
	}

	private void trimLineEnd() {
		int end = out.length();
		while (end > 0 && out.charAt(end - 1) == ' ') {
			end--;
		}
		out.setLength(end);
	}

	private static String trimEnd(String text) {
		int end = text.length();
		while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
			end--;
		}
		int start = 0;
		while (start < end && text.charAt(start) == '\n') {
			start++;
		}
		return text.substring(start, end);
	}

}

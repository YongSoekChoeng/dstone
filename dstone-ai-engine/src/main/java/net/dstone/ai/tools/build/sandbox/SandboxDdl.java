package net.dstone.ai.tools.build.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * DDL 파일에서 실행할 SQL 문장을 꺼냅니다. DB에 접속하지 않습니다.
 *
 * - .sql 파일은 전체가 SQL입니다.
 * - 마크다운은 ```sql 코드 블록만 실행합니다(말이 없는 ``` 블록은 SQL 낱말로 시작할 때만).
 * - 바로 위 제목이나 줄에 롤백·원복이라고 적힌 블록은 실행하지 않습니다. 방금 만든 것을 되돌려 버리기 때문입니다.
 * - 블록 밖에 DDL이 있으면 실패입니다. 조용히 빼먹고 "반영했다"고 하지 않으려는 것입니다.
 *
 * mysql 명령줄 도구에 넘기지 않고 문장으로 나눠 JDBC로 보냅니다.
 * 명령줄 도구는 system, \! 같은 OS 명령을 실행할 수 있는데, 이 파일은 LLM이 쓴 것이기 때문입니다.
 * </pre>
 */
final class SandboxDdl {

	private static final Pattern SQL_START = Pattern.compile("(?is)^(\\s|--[^\\n]*\\n|#[^\\n]*\\n|/\\*.*?\\*/)*(CREATE|ALTER|INSERT|COMMENT|GRANT|DO|DROP|UPDATE|DELETE|REPLACE|RENAME|TRUNCATE|SET|USE|DELIMITER)\\b.*");
	private static final Pattern DDL_OUTSIDE = Pattern.compile("(?i)^\\s*(CREATE\\s+(TABLE|INDEX|UNIQUE|VIEW)|ALTER\\s+TABLE|DROP\\s+TABLE|INSERT\\s+INTO)\\s+\\S.*");
	private static final Pattern UNDO = Pattern.compile("(?i).*(롤백|원복|되돌리|rollback).*");

	/**
	 * 꺼낸 결과입니다.
	 *
	 * @param statements 실행할 문장들
	 * @param skipped    실행하지 않은 롤백 블록 수
	 * @param problem    실행할 수 없는 이유. 없으면 null
	 */
	record Script(List<String> statements, int skipped, String problem) {
	}

	private static final Pattern DOLLAR_TAG = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)?\\$");

	private SandboxDdl() {
	}

	/**
	 * @param fileName 파일 이름(.sql인지 봄)
	 * @param text     파일 내용
	 */
	static Script parse(String fileName, String text, boolean backslashEscapes) {
		List<String> statements = new ArrayList<String>();
		if (fileName.toLowerCase().endsWith(".sql")) {
			statements.addAll(split(text, backslashEscapes));
			return new Script(statements, 0, null);
		}
		int skipped = 0;
		String heading = "";
		String before = "";
		String language = null;
		StringBuilder block = new StringBuilder();
		String[] lines = text.replace("\r\n", "\n").split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			String trimmed = line.trim();
			if (language == null) {
				if (trimmed.startsWith("```")) {
					language = trimmed.substring(3).trim().toLowerCase();
					block.setLength(0);
				} else if (DDL_OUTSIDE.matcher(line).matches()) {
					return new Script(statements, skipped, (i + 1) + "번째 줄의 SQL이 ```sql 코드 블록 밖에 있습니다. 블록 안으로 옮기십시오: " + trimmed);
				} else if (!trimmed.isEmpty()) {
					if (trimmed.startsWith("#")) {
						heading = trimmed;
					}
					before = trimmed;
				}
			} else if (trimmed.startsWith("```")) {
				String body = block.toString();
				boolean sql = "sql".equals(language) || "mysql".equals(language) || "ddl".equals(language)
					|| (language.isEmpty() && SQL_START.matcher(body).matches());
				if (sql) {
					if (UNDO.matcher(heading).matches() || UNDO.matcher(before).matches()) {
						skipped++;
					} else {
						statements.addAll(split(body, backslashEscapes));
					}
				}
				language = null;
				before = "";
			} else {
				block.append(line).append('\n');
			}
		}
		if (language != null) {
			return new Script(statements, skipped, "닫히지 않은 코드 블록(```)이 있습니다.");
		}
		return new Script(statements, skipped, null);
	}

	/** 문장으로 나눕니다. 따옴표·주석 안의 구분자는 건너뛰고 DELIMITER 줄을 따릅니다. 주석뿐인 조각은 버립니다. */
	static List<String> split(String text, boolean backslashEscapes) {
		List<String> result = new ArrayList<String>();
		String sql = text.replace("\r\n", "\n");
		String delimiter = ";";
		StringBuilder current = new StringBuilder();
		boolean hasCode = false;
		int i = 0;
		while (i < sql.length()) {
			char c = sql.charAt(i);
			if (!hasCode && sql.regionMatches(true, i, "DELIMITER ", 0, 10)) {
				int end = endOfLine(sql, i);
				String next = sql.substring(i + 10, end).trim();
				if (!next.isEmpty()) {
					delimiter = next;
				}
				current.setLength(0);
				i = end;
				continue;
			}
			if (sql.startsWith(delimiter, i)) {
				if (hasCode) {
					result.add(current.toString().trim());
				}
				current.setLength(0);
				hasCode = false;
				i += delimiter.length();
				continue;
			}
			int end = i + 1;
			if (c == '\'' || c == '"' || c == '`') {
				hasCode = true;
				while (end < sql.length()) {
					char q = sql.charAt(end++);
					if (q == '\\' && backslashEscapes && c != '`' && end < sql.length()) {
						end++;
					} else if (q == c) {
						break;
					}
				}
			} else if (c == '$' && !backslashEscapes) {
				// PostgreSQL의 $$ ... $$, $tag$ ... $tag$ 안에서는 ;로 나누지 않는다
				Matcher tag = DOLLAR_TAG.matcher(sql).region(i, sql.length());
				hasCode = true;
				if (tag.lookingAt()) {
					int close = sql.indexOf(tag.group(), tag.end());
					end = close < 0 ? sql.length() : close + tag.group().length();
				}
			} else if ((c == '#' && backslashEscapes) || (sql.startsWith("--", i) && (i + 2 == sql.length() || Character.isWhitespace(sql.charAt(i + 2))))) {
				end = endOfLine(sql, i);
			} else if (sql.startsWith("/*", i)) {
				int close = sql.indexOf("*/", i + 2);
				end = close < 0 ? sql.length() : close + 2;
				if (sql.startsWith("/*!", i)) {
					hasCode = true; // 버전 주석은 MySQL이 실행한다
				}
			} else if (!Character.isWhitespace(c)) {
				hasCode = true;
			}
			current.append(sql, i, end);
			i = end;
		}
		if (hasCode) {
			result.add(current.toString().trim());
		}
		return result;
	}

	private static int endOfLine(String text, int from) {
		int end = text.indexOf('\n', from);
		return end < 0 ? text.length() : end;
	}

}

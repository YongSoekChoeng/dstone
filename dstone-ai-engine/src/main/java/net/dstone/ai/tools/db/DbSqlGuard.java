package net.dstone.ai.tools.db;

import java.util.List;
import java.util.regex.Pattern;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;

/**
 * <pre>
 * LLM이 만든 SQL이 "조회만 하는 문장 하나"인지 실행 전에 확인합니다.
 *
 * 이것이 유일한 방어선은 아닙니다. DbQueryTool은 읽기 전용 연결로 실행하고, 끝나면 항상 롤백합니다.
 * 그래도 실행 전에 막으면 DB에 닿지도 않고, LLM이 왜 막혔는지 바로 알 수 있습니다.
 *
 * 막는 것:
 *   - 문장이 둘 이상(세미콜론으로 이은 것)
 *   - SELECT / WITH ... SELECT 가 아닌 문장(INSERT, UPDATE, DELETE, MERGE, DDL, CALL ...)
 *   - SELECT ... INTO (테이블을 만들거나 변수에 넣는다), INTO OUTFILE / DUMPFILE (서버에 파일을 쓴다)
 *   - SELECT ... FOR UPDATE (행에 잠금을 건다)
 * </pre>
 */
public class DbSqlGuard {

	/** MySQL이 서버에 파일을 쓰는 구문. 파서가 놓칠 수 있어 글자로도 한 번 더 본다 */
	private static final Pattern INTO_FILE = Pattern.compile("(?i)\\bINTO\\s+(OUTFILE|DUMPFILE)\\b");

	private DbSqlGuard() {
	}

	/**
	 * @param sql 검사할 SQL
	 * @return 문제가 없으면 null, 있으면 그 까닭
	 */
	public static String check(String sql) {
		if (sql == null || sql.trim().length() == 0) {
			return "SQL이 비어 있습니다.";
		}
		if (INTO_FILE.matcher(sql).find()) {
			return "INTO OUTFILE / DUMPFILE은 쓸 수 없습니다(조회만 합니다).";
		}
		Statements statements;
		try {
			statements = CCJSqlParserUtil.parseStatements(sql);
		} catch (Exception e) {
			return "SQL을 해석하지 못했습니다(조회 문장인지 확인할 수 없어 실행하지 않습니다) - " + firstLine(e.getMessage());
		}
		if (statements == null || statements.size() != 1) {
			return "한 번에 SQL 문장 하나만 실행합니다. 문장 수=" + (statements == null ? 0 : statements.size());
		}
		Statement statement = statements.get(0);
		if (!(statement instanceof Select)) {
			return "조회(SELECT) 문장만 실행합니다: " + statement.getClass().getSimpleName();
		}
		return checkSelect((Select) statement);
	}

	/** SELECT 안을 따라가며 INTO / FOR UPDATE를 찾는다 */
	private static String checkSelect(Select select) {
		List<WithItem> withItems = select.getWithItemsList();
		if (withItems != null) {
			for (int i = 0; i < withItems.size(); i++) {
				String problem = checkSelect(withItems.get(i));
				if (problem != null) {
					return problem;
				}
			}
		}
		if (select instanceof PlainSelect) {
			PlainSelect plain = (PlainSelect) select;
			if (plain.getIntoTables() != null && !plain.getIntoTables().isEmpty()) {
				return "SELECT ... INTO는 쓸 수 없습니다(조회만 합니다).";
			}
			if (plain.isForUpdate()) {
				return "SELECT ... FOR UPDATE는 쓸 수 없습니다(행에 잠금을 겁니다).";
			}
		} else if (select instanceof SetOperationList) {
			List<Select> selects = ((SetOperationList) select).getSelects();
			for (int i = 0; i < selects.size(); i++) {
				String problem = checkSelect(selects.get(i));
				if (problem != null) {
					return problem;
				}
			}
		} else if (select instanceof ParenthesedSelect) {
			Select inner = ((ParenthesedSelect) select).getSelect();
			if (inner != null) {
				return checkSelect(inner);
			}
		}
		return null;
	}

	private static String firstLine(String message) {
		if (message == null) {
			return "";
		}
		int newline = message.indexOf('\n');
		return newline < 0 ? message : message.substring(0, newline);
	}

}

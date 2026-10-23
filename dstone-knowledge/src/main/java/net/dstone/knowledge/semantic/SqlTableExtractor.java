package net.dstone.knowledge.semantic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * <pre>
 * SQL 하나가 어느 테이블을 읽고 쓰는지 알아냅니다.
 *
 * 먼저 SQL 파서(JSQLParser)로 읽습니다. 정확합니다.
 * 파서가 못 읽으면 FROM / JOIN / INTO / UPDATE 뒤의 이름을 정규식으로 찾습니다. 덜 정확하지만 대부분 맞습니다.
 *
 * 파서가 못 읽는 경우가 드물지 않습니다. 매퍼의 SQL은 조건에 따라 붙는 조각을 전부 이어 붙인 글이라
 * 문법에 안 맞을 수 있고(가장 흔한 WHERE AND ... 는 미리 다듬지만, 값 목록이나 컬럼 목록이 조건에 따라 달라지면 못 다듬는다),
 * DB마다 다른 문법(오라클의 (+), CONNECT BY ...)도 있습니다.
 * 그래서 정규식으로 찾은 것은 "덜 확실하다"는 표시(guessed)를 붙여 돌려줍니다.
 *
 * 파서는 "빠른 모드"로만, 이 스레드에서 직접 부릅니다.
 * JSQLParser는 빠른 모드로 못 읽으면 "복잡한 파싱" 모드로 다시 시도하는데, 그 모드는 어떤 SQL에서 끝없이 오래 걸립니다.
 * 라이브러리가 주는 시간 제한 기능은 쓰지 않습니다. 그 기능은 파싱을 다른 스레드에 맡기고 시간이 지나면 기다리기만 그만둘 뿐,
 * 그 스레드는 계속 돌면서 CPU를 씁니다. 실제로 매퍼 1,900개를 돌렸을 때 그런 스레드가 수십 개 쌓여 서버 전체가 느려졌습니다.
 * 빠른 모드로 못 읽는 SQL은 정규식으로 찾으면 충분합니다.
 *
 * 파서로 읽은 SQL에서는 테이블마다 쓰인 컬럼 이름도 모읍니다(ColumnFinder).
 * 어느 테이블의 컬럼인지 확실할 때만 넣습니다. 정규식으로 찾은 SQL에서는 컬럼을 뽑지 않습니다.
 * </pre>
 */
public class SqlTableExtractor {

	/** 괄호가 이보다 깊게 겹친 SQL은 파서에 넣지 않습니다. 깊게 겹친 식에서 파서가 느려지기 때문입니다. */
	private static final int MAX_PAREN_DEPTH = 12;

	/** 이보다 긴 SQL은 파서에 넣지 않고 바로 정규식으로 찾습니다. */
	private static final int MAX_PARSE_LENGTH = 20000;

	private static final Pattern MYBATIS_PARAMETER = Pattern.compile("#\\{[^}]*\\}");
	private static final Pattern MYBATIS_SUBSTITUTION = Pattern.compile("\\$\\{[^}]*\\}");
	private static final Pattern IBATIS_PARAMETER = Pattern.compile("#[A-Za-z_][\\w.\\[\\]:,=]*#");
	private static final Pattern IBATIS_SUBSTITUTION = Pattern.compile("\\$[A-Za-z_][\\w.\\[\\]]*\\$");
	private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
	private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");

	/** WITH 이름 AS ( ... ) 의 이름. SQL 안에서만 쓰는 임시 이름이라 테이블이 아니다. */
	private static final Pattern CTE_NAME = Pattern.compile("(?i)(?:\\bWITH\\s+(?:RECURSIVE\\s+)?|,\\s*)([A-Za-z_][\\w$]*)\\s*(?:\\([^()]*\\))?\\s+AS\\s*\\(");

	/**
	 * 조건 조각을 전부 이어 붙이면 생기는 군더더기: WHERE AND ..., WHERE OR ..., ( AND ..., AND AND ...
	 * 매퍼는 실행할 때 이것을 떼어 주지만(where 태그, prepend), 펴 놓은 글에는 그대로 남아 파서가 읽지 못한다.
	 */
	private static final Pattern DANGLING_CONDITION = Pattern.compile("(?i)(\\bWHERE|\\(|\\bAND|\\bOR)\\s+(?:AND|OR)\\b");

	/** 조건이 하나도 없을 때 남는 꼬리: ... WHERE ORDER BY, ... WHERE GROUP BY, ... WHERE ) , 글의 끝 */
	private static final Pattern EMPTY_WHERE = Pattern.compile("(?i)\\bWHERE\\s*(?=ORDER\\s+BY\\b|GROUP\\s+BY\\b|\\)|$)");

	private static final Pattern LEFTOVER_INCLUDE = Pattern.compile("@@include\\([^)]*\\)@@");

	/** FROM / JOIN / INTO / UPDATE 뒤에 오는 이름 */
	private static final Pattern TABLE_AFTER_KEYWORD = Pattern.compile("(?i)\\b(FROM|JOIN|INTO|UPDATE)\\s+([A-Za-z_][\\w$]*(?:\\.[A-Za-z_][\\w$]*)?)");

	/** 한 SQL에서 테이블 하나에 모으는 컬럼 수의 한도 */
	private static final int MAX_COLUMNS = 300;

	/** 컬럼 자리에 오지만 컬럼이 아닌 것들(DB가 주는 값, 예약어) */
	private static final String NOT_COLUMNS = "|SYSDATE|SYSTIMESTAMP|CURRENT_DATE|CURRENT_TIMESTAMP|CURRENT_TIME|LOCALTIMESTAMP|LOCALTIME"
			+ "|ROWNUM|ROWID|LEVEL|USER|NULL|TRUE|FALSE|NEXTVAL|CURRVAL|";

	/** 테이블 이름 자리에 올 수 있지만 테이블이 아닌 것들 */
	private static final String NOT_TABLES = "|SELECT|DUAL|SET|WHERE|LATERAL|TABLE|ONLY|VALUES|X_|";

	/**
	 * <pre>
	 * 테이블 하나와 그것을 어떻게 건드리는지
	 * </pre>
	 */
	public static class TableUse {

		/** 테이블 이름(대문자) */
		public final String table;

		/** C(넣기) / R(읽기) / U(고치기) / D(지우기) */
		public final String crud;

		/** 파서가 아니라 정규식으로 찾았으면 true */
		public final boolean guessed;

		/** 이 SQL에서 쓰인 이 테이블의 컬럼(대문자, 이름 순). 정규식으로 찾았으면 null(읽지 못함) */
		public final List<String> columns;

		TableUse(String table, String crud, boolean guessed) {
			this(table, crud, guessed, null);
		}

		TableUse(String table, String crud, boolean guessed, List<String> columns) {
			this.table = table;
			this.crud = crud;
			this.guessed = guessed;
			this.columns = columns;
		}
	}

	/**
	 * @param sql 매퍼에서 편 SQL(include는 이미 채워진 것)
	 * @param statementType SELECT / INSERT / UPDATE / DELETE ...
	 */
	public List<TableUse> extract(String sql, String statementType) {
		String cleaned = clean(sql);
		if (cleaned.length() == 0) {
			return new ArrayList<TableUse>();
		}
		// WITH 절에서 붙인 임시 이름들. 파서는 스스로 가려내지만 정규식은 못 가려내므로 두 경우 모두 여기서 뺀다.
		Set<String> temporaryNames = new HashSet<String>();
		Matcher cte = CTE_NAME.matcher(cleaned);
		while (cte.find()) {
			temporaryNames.add(cte.group(1).toUpperCase(Locale.ROOT));
		}
		if (cleaned.length() <= MAX_PARSE_LENGTH && parenDepthOf(cleaned) <= MAX_PAREN_DEPTH) {
			try {
				return without(byParser(cleaned), temporaryNames);
			} catch (Exception e) {
				// 문법에 안 맞거나 시간이 넘었다. 정규식으로 찾는다.
			} catch (StackOverflowError e) {
				// 지나치게 깊게 중첩된 SQL
			}
		}
		return without(byPattern(cleaned, statementType), temporaryNames);
	}

	private List<TableUse> without(List<TableUse> uses, Set<String> names) {
		if (names.isEmpty()) {
			return uses;
		}
		List<TableUse> kept = new ArrayList<TableUse>();
		for (int i = 0; i < uses.size(); i++) {
			if (!names.contains(uses.get(i).table)) {
				kept.add(uses.get(i));
			}
		}
		return kept;
	}

	/** 매퍼의 자리표시자를 SQL 파서가 읽을 수 있는 글자로 바꿉니다. */
	private String clean(String sql) {
		String text = sql == null ? "" : sql;
		// 주석을 먼저 뗀다. 주석 안의 글자(FROM 다음의 설명 등)가 테이블로 잡히지 않게 하고, 파서가 주석 때문에 실패하지 않게 한다.
		text = BLOCK_COMMENT.matcher(text).replaceAll(" ");
		text = LINE_COMMENT.matcher(text).replaceAll(" ");
		text = LEFTOVER_INCLUDE.matcher(text).replaceAll(" ");
		text = MYBATIS_PARAMETER.matcher(text).replaceAll("?");
		// ${...} 는 SQL 글자 자체를 끼워 넣는 것이라(테이블 이름, 정렬 컬럼 등) 무엇이 올지 알 수 없다. 이름 하나가 오는 것으로 친다.
		text = MYBATIS_SUBSTITUTION.matcher(text).replaceAll("X_");
		text = IBATIS_PARAMETER.matcher(text).replaceAll("?");
		text = IBATIS_SUBSTITUTION.matcher(text).replaceAll("X_");
		text = text.replaceAll("\\s+", " ").trim();
		// 군더더기는 겹쳐 있을 수 있어서(WHERE AND AND ...) 더 바뀌지 않을 때까지 뗀다. 횟수를 정해 두어 끝없이 돌지 않게 한다.
		for (int i = 0; i < 5; i++) {
			String trimmed = DANGLING_CONDITION.matcher(text).replaceAll("$1");
			if (trimmed.equals(text)) {
				break;
			}
			text = trimmed;
		}
		return EMPTY_WHERE.matcher(text).replaceAll("").trim();
	}

	private List<TableUse> byParser(String sql) throws Exception {
		// 빠른 모드만 쓰고(복잡한 파싱 끔), 다른 스레드에 맡기지 않고 여기서 바로 읽는다.
		CCJSqlParser parser = CCJSqlParserUtil.newParser(sql).withAllowComplexParsing(false);
		Statement statement = parser.Statement();
		// 쓰는 대상 테이블과 그 종류
		Table target = null;
		String targetCrud = null;
		if (statement instanceof Insert) {
			target = ((Insert) statement).getTable();
			targetCrud = "C";
		} else if (statement instanceof Update) {
			target = ((Update) statement).getTable();
			targetCrud = "U";
		} else if (statement instanceof Delete) {
			target = ((Delete) statement).getTable();
			targetCrud = "D";
		} else if (statement instanceof Merge) {
			target = ((Merge) statement).getTable();
			targetCrud = "U";
		}
		String targetName = target == null ? null : normalize(target.getFullyQualifiedName());

		ColumnFinder finder = new ColumnFinder();
		List<String> names = finder.getTableList(statement);

		Map<String, TableUse> uses = new LinkedHashMap<String, TableUse>();
		if (targetName != null) {
			uses.put(targetName + "|" + targetCrud, new TableUse(targetName, targetCrud, false, finder.columnsOf(targetName)));
		}
		boolean targetSeen = false;
		for (int i = 0; i < names.size(); i++) {
			String name = normalize(names.get(i));
			if (name == null) {
				continue;
			}
			if (name.equals(targetName) && !targetSeen) {
				// 쓰는 대상으로 한 번 나온 것은 "읽기"로 다시 세지 않는다. 같은 테이블이 서브쿼리에 또 나오면 그것은 읽기다.
				targetSeen = true;
				continue;
			}
			uses.put(name + "|R", new TableUse(name, "R", false, finder.columnsOf(name)));
		}
		return new ArrayList<TableUse>(uses.values());
	}

	/**
	 * <pre>
	 * 테이블 이름을 모으면서, 어느 테이블의 컬럼인지 확실한 컬럼도 함께 모읍니다.
	 *
	 * SELECT / UPDATE / DELETE 마다 "지금 보고 있는 FROM"을 쌓아 두고 컬럼을 그 기준으로 가립니다.
	 *   A.COL  : 별칭 A가 가리키는 테이블. 안쪽 FROM부터 바깥으로 찾는다(서브쿼리가 바깥 별칭을 쓰는 경우).
	 *            서브쿼리에 붙인 별칭이거나 어디에도 없는 이름이면 넣지 않는다.
	 *   COL    : 그 FROM에 테이블이 하나뿐일 때만 그 테이블. 둘 이상이거나 서브쿼리가 끼어 있으면 넣지 않는다.
	 *   INSERT 의 컬럼 목록, UPDATE 의 SET 왼쪽은 대상 테이블의 컬럼이다.
	 * SELECT 목록에서 붙인 별칭(AS CNT)은 컬럼이 아니므로 뺍니다.
	 * </pre>
	 */
	private class ColumnFinder extends TablesNamesFinder {

		/** FROM 하나에 있는 이름(별칭 또는 테이블 이름) → 테이블. 서브쿼리 별칭이면 "" */
		private final List<Map<String, String>> scopes = new ArrayList<Map<String, String>>();

		/** 그 FROM에 테이블이 하나뿐이면 그 테이블, 아니면 null */
		private final List<String> onlyTables = new ArrayList<String>();

		private final Set<String> selectAliases = new HashSet<String>();

		private final Map<String, Set<String>> columns = new HashMap<String, Set<String>>();

		/** 이 테이블에서 쓰인 컬럼. 하나도 없으면 빈 목록 */
		List<String> columnsOf(String table) {
			Set<String> found = columns.get(table);
			return found == null ? new ArrayList<String>() : new ArrayList<String>(found);
		}

		@Override
		public void visit(PlainSelect select) {
			List<FromItem> items = new ArrayList<FromItem>();
			items.add(select.getFromItem());
			addJoins(items, select.getJoins());
			if (select.getSelectItems() != null) {
				for (int i = 0; i < select.getSelectItems().size(); i++) {
					SelectItem<?> item = select.getSelectItems().get(i);
					// USER_ID AS USER_ID 처럼 컬럼에 같은 이름을 붙인 것은 별칭으로 치지 않는다.
					if (item.getAlias() != null && !(item.getExpression() instanceof Column
							&& nameOf(((Column) item.getExpression()).getColumnName()).equals(nameOf(item.getAlias().getName())))) {
						selectAliases.add(nameOf(item.getAlias().getName()));
					}
				}
			}
			push(items);
			try {
				super.visit(select);
			} finally {
				pop();
			}
		}

		@Override
		public void visit(Update update) {
			List<FromItem> items = new ArrayList<FromItem>();
			items.add(update.getTable());
			addJoins(items, update.getStartJoins());
			items.add(update.getFromItem());
			addJoins(items, update.getJoins());
			String target = normalize(update.getTable().getFullyQualifiedName());
			if (update.getUpdateSets() != null) {
				for (int i = 0; i < update.getUpdateSets().size(); i++) {
					UpdateSet set = update.getUpdateSets().get(i);
					for (int c = 0; set.getColumns() != null && c < set.getColumns().size(); c++) {
						// SET 왼쪽에 A.COL 처럼 별칭을 붙였어도 대상 테이블의 컬럼이다.
						add(target, set.getColumns().get(c).getColumnName());
					}
				}
			}
			push(items);
			try {
				super.visit(update);
			} finally {
				pop();
			}
		}

		@Override
		public void visit(Delete delete) {
			List<FromItem> items = new ArrayList<FromItem>();
			items.add(delete.getTable());
			addJoins(items, delete.getJoins());
			push(items);
			try {
				super.visit(delete);
			} finally {
				pop();
			}
		}

		@Override
		public void visit(Insert insert) {
			String target = normalize(insert.getTable().getFullyQualifiedName());
			if (insert.getColumns() != null) {
				for (int i = 0; i < insert.getColumns().size(); i++) {
					add(target, insert.getColumns().get(i).getColumnName());
				}
			}
			super.visit(insert);
		}

		@Override
		public void visit(Column column) {
			super.visit(column);
			Table owner = column.getTable();
			String qualifier = owner == null || owner.getName() == null ? null : nameOf(owner.getName());
			if (qualifier == null) {
				if (onlyTables.isEmpty() || selectAliases.contains(nameOf(column.getColumnName()))) {
					return;
				}
				add(onlyTables.get(onlyTables.size() - 1), column.getColumnName());
				return;
			}
			for (int i = scopes.size() - 1; i >= 0; i--) {
				String table = scopes.get(i).get(qualifier);
				if (table != null) {
					add(table, column.getColumnName());
					return;
				}
			}
		}

		private void addJoins(List<FromItem> items, List<Join> joins) {
			for (int i = 0; joins != null && i < joins.size(); i++) {
				items.add(joins.get(i).getRightItem());
			}
		}

		private void push(List<FromItem> items) {
			Map<String, String> scope = new HashMap<String, String>();
			int tables = 0;
			String only = null;
			boolean others = false;
			for (int i = 0; i < items.size(); i++) {
				FromItem item = items.get(i);
				if (item == null) {
					continue;
				}
				if (item instanceof Table) {
					Table table = (Table) item;
					String name = normalize(table.getFullyQualifiedName());
					if (name == null) {
						// DUAL 같은 것. 컬럼의 주인이 될 수 없다.
						continue;
					}
					tables++;
					only = name;
					scope.put(nameOf(table.getName()), name);
					scope.put(name, name);
					if (table.getAlias() != null) {
						scope.put(nameOf(table.getAlias().getName()), name);
					}
				} else {
					// 서브쿼리 등. 그 별칭으로 적힌 컬럼은 테이블의 것이 아니다.
					others = true;
					if (item.getAlias() != null) {
						scope.put(nameOf(item.getAlias().getName()), "");
					}
				}
			}
			scopes.add(scope);
			onlyTables.add(tables == 1 && !others ? only : null);
		}

		private void pop() {
			scopes.remove(scopes.size() - 1);
			onlyTables.remove(onlyTables.size() - 1);
		}

		private void add(String table, String columnName) {
			if (table == null || table.length() == 0 || columnName == null) {
				return;
			}
			String name = nameOf(columnName);
			// X_ 는 ${...} 자리에 넣은 글자다. 무엇이 올지 모른다.
			if (name.length() == 0 || name.startsWith("X_") || NOT_COLUMNS.indexOf("|" + name + "|") >= 0 || !isIdentifier(name)) {
				return;
			}
			Set<String> found = columns.get(table);
			if (found == null) {
				found = new TreeSet<String>();
				columns.put(table, found);
			}
			if (found.size() < MAX_COLUMNS) {
				found.add(name);
			}
		}
	}

	/** 따옴표를 벗기고 대문자로 맞춘 이름 */
	private String nameOf(String name) {
		return name == null ? "" : name.replace("\"", "").replace("`", "").replace("[", "").replace("]", "").trim().toUpperCase(Locale.ROOT);
	}

	private boolean isIdentifier(String name) {
		if (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_') {
			return false;
		}
		for (int i = 1; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '_' && c != '$' && c != '#') {
				return false;
			}
		}
		return true;
	}

	private int parenDepthOf(String sql) {
		int depth = 0;
		int max = 0;
		for (int i = 0; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if (c == '(') {
				depth++;
				max = Math.max(max, depth);
			} else if (c == ')') {
				depth--;
			}
		}
		return max;
	}

	private List<TableUse> byPattern(String sql, String statementType) {
		Map<String, TableUse> uses = new LinkedHashMap<String, TableUse>();
		Matcher m = TABLE_AFTER_KEYWORD.matcher(sql);
		boolean targetFound = false;
		while (m.find()) {
			String keyword = m.group(1).toUpperCase(Locale.ROOT);
			String name = normalize(m.group(2));
			if (name == null) {
				continue;
			}
			String crud = "R";
			if (!targetFound) {
				// 쓰는 대상은 SQL에서 가장 먼저 나오는 INTO / UPDATE / (DELETE) FROM 뒤의 이름이다.
				if ("INTO".equals(keyword) && ("INSERT".equals(statementType) || "MERGE".equals(statementType))) {
					crud = "C";
				} else if ("UPDATE".equals(keyword) && "UPDATE".equals(statementType)) {
					crud = "U";
				} else if ("FROM".equals(keyword) && "DELETE".equals(statementType)) {
					crud = "D";
				}
				targetFound = !"R".equals(crud);
			}
			uses.put(name + "|" + crud, new TableUse(name, crud, true));
		}
		return new ArrayList<TableUse>(uses.values());
	}

	/** 따옴표를 벗기고 대문자로 맞춥니다. 테이블이 아닌 것(DUAL 등)은 null */
	private String normalize(String name) {
		if (name == null) {
			return null;
		}
		String cleaned = name.replace("\"", "").replace("`", "").replace("[", "").replace("]", "").trim().toUpperCase(Locale.ROOT);
		if (cleaned.length() == 0 || NOT_TABLES.indexOf("|" + cleaned + "|") >= 0 || cleaned.indexOf("X_") >= 0 || cleaned.indexOf('?') >= 0) {
			return null;
		}
		return cleaned.length() > 300 ? cleaned.substring(0, 300) : cleaned;
	}

}

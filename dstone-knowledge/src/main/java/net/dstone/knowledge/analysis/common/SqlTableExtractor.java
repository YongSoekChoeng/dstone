package net.dstone.knowledge.analysis.common;

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

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;

import net.dstone.knowledge.common.util.KoreanName;
import net.dstone.knowledge.common.util.TableName;
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
 * JSQLParser 가 못 읽으면 Druid 파서로 다시 읽습니다. 테이블과 CRUD 만 받습니다(컬럼은 뽑지 않는다).
 * 둘 다 못 읽으면 FROM / JOIN / INTO / UPDATE 뒤의 이름을 정규식으로 찾습니다. 덜 정확하지만 대부분 맞습니다.
 *   정규식은 함수 괄호 안의 FROM(trim(both ' ' from x), extract(year from d)), IS DISTINCT FROM, FROM 함수(...) 를 건너뜁니다.
 *
 * DB의 시스템 카탈로그(information_schema.tables, pg_class, ALL_TABLES 등)는 system 표시를 붙여 돌려줍니다. 업무 테이블이 아닙니다.
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
 * 빠른 모드로 못 읽는 SQL은 Druid 와 정규식으로 찾으면 충분합니다.
 *
 * 파서로 읽은 SQL에서는 테이블마다 쓰인 컬럼 이름도 모읍니다(ColumnFinder).
 * 어느 테이블의 컬럼인지 확실할 때만 넣습니다. 정규식으로 찾은 SQL에서는 컬럼을 뽑지 않습니다.
 *
 * SQL 주석에 적힌 한글명도 모읍니다(SqlComments). 예:
 *   SELECT tb1.mb_nm   /* 회사명 VARCHAR(100) *&#47;     → TBSY_MEMBER.MB_NM = 회사명
 *     FROM tbsy_member tb1 /* 회원사관리 *&#47;          → TBSY_MEMBER = 회원사관리
 *        , tbsy_auth tb2   /* 권한 *&#47;                → TBSY_AUTH = 권한 (FROM 을 앞줄에 쓰거나 쉼표로 늘어놓은 테이블도)
 * 한글이 들어 있는 주석만, 그리고 어느 테이블(컬럼)의 것인지 확실할 때만 넣습니다.
 * "식 AS 별칭 /* 설명 *&#47;" 처럼 별칭을 설명한 주석은 테이블 컬럼의 것이 아니라서 뺍니다.
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

	/**
	 * <pre>
	 * WITH 이름 AS ( ... ) 의 이름. SQL 안에서만 쓰는 임시 이름이라 테이블이 아니다.
	 * PostgreSQL 의 "이름 AS MATERIALIZED (" / "이름 AS NOT MATERIALIZED (" 도 받는다.
	 * </pre>
	 */
	private static final Pattern CTE_NAME = Pattern.compile("(?i)(?:\\bWITH\\s+(?:RECURSIVE\\s+)?|,\\s*)([A-Za-z_][\\w$]*)\\s*(?:\\([^()]*\\))?\\s+AS\\s*(?:NOT\\s+)?(?:MATERIALIZED\\s*)?\\(");

	/** 바로 앞이 DISTINCT 다: "a IS [NOT] DISTINCT FROM b" 의 FROM 은 비교다 */
	private static final Pattern DISTINCT_BEFORE = Pattern.compile("(?i)\\bDISTINCT\\s*$");

	/** 이름 바로 뒤에 여는 괄호가 온다: FROM unnest( ... ) 처럼 함수를 부른 것 */
	private static final Pattern FUNCTION_CALL_AFTER = Pattern.compile("\\s*\\(");

	/** 괄호 안이 서브쿼리로 시작한다 */
	private static final Pattern SUBQUERY_START = Pattern.compile("(?i)\\s*(?:SELECT|WITH)\\b");

	/** 괄호 앞에 와도 함수 이름이 아닌 키워드 */
	private static final String NOT_FUNCTIONS = "|IN|EXISTS|FROM|JOIN|AS|ON|AND|OR|NOT|VALUES|ANY|ALL|SOME|LATERAL|USING|WHERE|SELECT|INTO"
			+ "|SET|UNION|EXCEPT|INTERSECT|MINUS|THEN|ELSE|WHEN|CASE|BY|HAVING|OVER|MATERIALIZED|RECURSIVE|TABLE|";

	/** 시스템 카탈로그의 스키마 */
	private static final String SYSTEM_SCHEMAS = "|INFORMATION_SCHEMA|PG_CATALOG|SYS|SYSIBM|SYSCAT|MYSQL|PERFORMANCE_SCHEMA|";

	/** 스키마 없이 써도 시스템 카탈로그인 이름(PostgreSQL 의 pg_catalog 와 Oracle 의 사전) */
	private static final String SYSTEM_TABLES = "|PG_CLASS|PG_NAMESPACE|PG_ATTRIBUTE|PG_ATTRDEF|PG_TYPE|PG_PROC|PG_EXTENSION|PG_DATABASE"
			+ "|PG_TABLES|PG_VIEWS|PG_MATVIEWS|PG_INDEXES|PG_INDEX|PG_CONSTRAINT|PG_DESCRIPTION|PG_TRIGGER|PG_SEQUENCES|PG_SEQUENCE"
			+ "|PG_ROLES|PG_USER|PG_AUTHID|PG_SETTINGS|PG_LOCKS|PG_STAT_ACTIVITY|PG_STAT_USER_TABLES|PG_STAT_ALL_TABLES|PG_STAT_STATEMENTS"
			+ "|PG_STATIO_USER_TABLES|PG_TABLESPACE|PG_DEPEND|PG_INHERITS|PG_ENUM|PG_TIMEZONE_NAMES"
			+ "|DICTIONARY|DICT|";

	/** Oracle 사전 뷰에서 ALL_ / USER_ / DBA_ 뒤에 오는 이름 */
	private static final String ORACLE_DICTIONARY = "|TABLES|TAB_COLUMNS|TAB_COLS|TAB_COMMENTS|COL_COMMENTS|OBJECTS|SEQUENCES|INDEXES|IND_COLUMNS"
			+ "|CONSTRAINTS|CONS_COLUMNS|VIEWS|SOURCE|SYNONYMS|USERS|PROCEDURES|ARGUMENTS|TRIGGERS|TAB_PRIVS|ROLE_PRIVS|SYS_PRIVS"
			+ "|DEPENDENCIES|TAB_PARTITIONS|MVIEWS|DB_LINKS|JOBS|SCHEDULER_JOBS|SEGMENTS|EXTENTS|TABLESPACES|ERRORS|TYPES|LOBS|";

	/** Druid 로 읽어 볼 DB 문법. 앞의 것부터 시도한다 */
	private static final DbType[] DRUID_DIALECTS = new DbType[] { DbType.oracle, DbType.postgresql, DbType.mysql };

	/**
	 * <pre>
	 * 조건 조각을 전부 이어 붙이면 생기는 군더더기: WHERE AND ..., WHERE OR ..., ( AND ..., AND AND ...
	 * 매퍼는 실행할 때 이것을 떼어 주지만(where 태그, prepend), 펴 놓은 글에는 그대로 남아 파서가 읽지 못한다.
	 * </pre>
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

		/** 무엇으로 찾았나: VIA_PARSER(JSQLParser) / VIA_DRUID(Druid 파서) / VIA_PATTERN(정규식) */
		public final String via;

		/** 정규식으로 찾았으면 true(덜 확실하다) */
		public final boolean guessed;

		/** DB가 가진 시스템 카탈로그(information_schema.tables, pg_class, ALL_TABLES 등)면 true. 업무 테이블이 아니다 */
		public final boolean system;

		/** 이 SQL에서 쓰인 이 테이블의 컬럼(대문자, 이름 순). 컬럼을 읽지 못했으면 null(정규식, Druid) */
		public final List<String> columns;

		/** SQL 주석에 적힌 이 테이블 컬럼의 한글명(컬럼 → 한글명). 없으면 빈 Map */
		public final Map<String, String> columnComments;

		/** SQL 주석에 적힌 이 테이블의 한글명. 없으면 null */
		public final String tableComment;

		TableUse(String table, String crud, String via, boolean system, List<String> columns) {
			this(table, crud, via, system, columns, new LinkedHashMap<String, String>(), null);
		}

		TableUse(String table, String crud, String via, boolean system, List<String> columns, Map<String, String> columnComments, String tableComment) {
			this.table = table;
			this.crud = crud;
			this.via = via;
			this.guessed = VIA_PATTERN.equals(via);
			this.system = system;
			this.columns = columns;
			this.columnComments = columnComments;
			this.tableComment = tableComment;
		}
	}

	/** 찾은 방법: JSQLParser 로 읽었다 */
	public static final String VIA_PARSER = "PARSER";

	/** 찾은 방법: JSQLParser 는 못 읽고 Druid 파서로 읽었다. 테이블과 CRUD 는 확실하지만 컬럼은 뽑지 않는다 */
	public static final String VIA_DRUID = "DRUID";

	/** 찾은 방법: 두 파서 모두 못 읽어 정규식으로 찾았다 */
	public static final String VIA_PATTERN = "PATTERN";

	/**
	 * <pre>
	 * SQL 하나가 읽고 쓰는 테이블을 돌려줍니다.
	 *
	 * 흐름:
	 *   1) 주석의 한글명을 먼저 읽어 둔다(다듬기에서 주석이 지워지므로).
	 *   2) SQL 을 다듬고, WITH 절의 임시 이름을 모아 둔다(진짜 테이블이 아니다).
	 *   3) 너무 길거나 괄호가 너무 깊지 않으면 파서로 읽는다.
	 *   4) 파서가 못 읽으면 Druid 로 읽는다(테이블과 CRUD 만).
	 *   5) 그래도 못 읽으면 정규식으로 찾는다(guessed).
	 * </pre>
	 *
	 * @param sql 매퍼에서 편 SQL(include는 이미 채워진 것)
	 * @param statementType SELECT / INSERT / UPDATE / DELETE ...
	 */
	public List<TableUse> extract(String sql, String statementType) {
		// 주석은 clean() 이 지우므로 그 전에 읽어 둔다.
		List<SqlComment> comments = commentsOf(sql);
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
				return without(byParser(cleaned, comments), temporaryNames);
			} catch (Exception e) {
				// 문법에 안 맞는다. Druid 로 다시 읽어 본다.
			} catch (StackOverflowError e) {
				// 지나치게 깊게 중첩된 SQL
			}
			List<TableUse> byDruid = byDruid(cleaned);
			if (byDruid != null) {
				// Druid 로 읽은 SQL도 별칭의 주인을 따로 모으지 않으므로 테이블 한글명만 쓴다.
				return without(withComments(byDruid, comments, null), temporaryNames);
			}
		}
		// 정규식으로 찾은 SQL은 별칭이 어느 테이블인지 모르므로 테이블 이름 바로 뒤의 주석(테이블 한글명)만 쓴다.
		return without(withComments(byPattern(cleaned, statementType), comments, null), temporaryNames);
	}

	/**
	 * <pre>
	 * 같은 statement 를 &lt;choose&gt; 가지마다 따로 편 SQL 여러 개에서 테이블을 뽑아 합칩니다.
	 *
	 * 매퍼는 &lt;choose&gt; 의 가지 중 하나만 실행하지만, 펴 놓은 SQL 하나에는 모든 가지가 이어 붙어 있어서
	 * "VALUES (...) SELECT NULL WHERE FALSE" 처럼 문법에 안 맞는 글이 되기 쉽습니다. 그러면 어떤 파서도 읽지 못합니다.
	 * 가지마다 편 SQL은 각각 문법에 맞으므로 파서로 읽힐 가능성이 큽니다.
	 *
	 * 합치는 방법: 같은 (테이블, CRUD)는 하나로. 더 확실한 방법으로 찾은 쪽을 남기고(파서 &gt; Druid &gt; 정규식), 컬럼과 한글명은 모은다.
	 * </pre>
	 *
	 * @param sqls 가지마다 편 SQL(include 는 이미 채운 것). 하나뿐이면 extract(sql, statementType) 과 같다
	 */
	public List<TableUse> extractAll(List<String> sqls, String statementType) {
		Map<String, TableUse> merged = new LinkedHashMap<String, TableUse>();
		for (int s = 0; s < sqls.size(); s++) {
			List<TableUse> uses = extract(sqls.get(s), statementType);
			for (int i = 0; i < uses.size(); i++) {
				TableUse use = uses.get(i);
				String key = use.table + "|" + use.crud;
				TableUse before = merged.get(key);
				merged.put(key, before == null ? use : merge(before, use));
			}
		}
		return new ArrayList<TableUse>(merged.values());
	}

	/** 같은 (테이블, CRUD)를 두 가지에서 찾았을 때 하나로 합칩니다. */
	private TableUse merge(TableUse a, TableUse b) {
		TableUse better = rankOf(b.via) > rankOf(a.via) ? b : a;
		List<String> columns = better.columns;
		if (a.columns != null && b.columns != null) {
			Set<String> union = new TreeSet<String>(a.columns);
			union.addAll(b.columns);
			columns = new ArrayList<String>(union);
		}
		Map<String, String> columnComments = new LinkedHashMap<String, String>(a.columnComments);
		for (Map.Entry<String, String> entry : b.columnComments.entrySet()) {
			if (!columnComments.containsKey(entry.getKey())) {
				columnComments.put(entry.getKey(), entry.getValue());
			}
		}
		return new TableUse(a.table, a.crud, better.via, a.system || b.system, columns, columnComments
				, a.tableComment != null ? a.tableComment : b.tableComment);
	}

	/** 찾은 방법의 확실한 정도. 클수록 확실하다 */
	private int rankOf(String via) {
		if (VIA_PARSER.equals(via)) {
			return 3;
		}
		return VIA_DRUID.equals(via) ? 2 : 1;
	}

	/**
	 * <pre>
	 * JSQLParser 가 못 읽은 SQL을 Druid 파서로 읽어 테이블과 CRUD 를 뽑습니다. 못 읽으면 null(그러면 정규식으로 간다).
	 *
	 * Druid 는 DB 문법마다 파서가 따로 있습니다. 어느 DB의 SQL인지 모르므로 Oracle → PostgreSQL → MySQL 순으로 읽어 보고, 처음 읽힌 것을 씁니다.
	 * 이 스레드에서 바로 읽습니다(다른 스레드에 맡기지 않는다).
	 * 컬럼은 뽑지 않습니다. 별칭이 어느 테이블인지 가리는 일을 ColumnFinder 만큼 확인하지 않았기 때문입니다.
	 *
	 * CRUD 는 Druid 가 센 횟수로 정합니다. 넣기 → C, 고치기 / MERGE → U, 지우기 → D, 읽기 → R.
	 * 한 테이블을 넣으면서 읽기도 하면 둘 다 남깁니다(INSERT INTO t SELECT ... FROM t).
	 * </pre>
	 */
	private List<TableUse> byDruid(String sql) {
		for (int d = 0; d < DRUID_DIALECTS.length; d++) {
			try {
				List<SQLStatement> statements = SQLUtils.parseStatements(sql, DRUID_DIALECTS[d]);
				if (statements.isEmpty()) {
					return null;
				}
				Map<String, TableUse> uses = new LinkedHashMap<String, TableUse>();
				for (int s = 0; s < statements.size(); s++) {
					SchemaStatVisitor visitor = SQLUtils.createSchemaStatVisitor(DRUID_DIALECTS[d]);
					statements.get(s).accept(visitor);
					for (Map.Entry<TableStat.Name, TableStat> entry : visitor.getTables().entrySet()) {
						String written = entry.getKey().getName();
						String name = normalize(written);
						if (name == null) {
							continue;
						}
						boolean system = isSystemObject(written);
						TableStat stat = entry.getValue();
						if (stat.getInsertCount() > 0) {
							uses.put(name + "|C", new TableUse(name, "C", VIA_DRUID, system, null));
						}
						if (stat.getUpdateCount() > 0 || stat.getMergeCount() > 0) {
							uses.put(name + "|U", new TableUse(name, "U", VIA_DRUID, system, null));
						}
						if (stat.getDeleteCount() > 0) {
							uses.put(name + "|D", new TableUse(name, "D", VIA_DRUID, system, null));
						}
						if (stat.getSelectCount() > 0) {
							uses.put(name + "|R", new TableUse(name, "R", VIA_DRUID, system, null));
						}
					}
				}
				return new ArrayList<TableUse>(uses.values());
			} catch (Exception e) {
				// 이 문법으로는 못 읽는다. 다음 문법으로.
			} catch (StackOverflowError e) {
				return null;
			}
		}
		return null;
	}

	/** names(WITH 절의 임시 이름 등 진짜 테이블이 아닌 것)에 든 테이블을 뺀 목록 */
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

	/**
	 * <pre>
	 * JSQLParser 로 읽어 테이블과 CRUD, 쓰인 컬럼, 주석의 한글명을 모읍니다. 못 읽으면 예외를 던집니다(그러면 byPattern 으로 간다).
	 *
	 * 흐름:
	 *   1) 빠른 모드로만 파싱한다.
	 *   2) INSERT / UPDATE / DELETE 면 쓰는 대상 테이블과 그 종류(C/U/D)를 정한다.
	 *   3) 문장 안의 모든 테이블 이름을 모아, 대상이 아닌 것은 읽기(R)로 둔다.
	 *   4) ColumnFinder 로 테이블마다 컬럼을, SqlComments 로 한글명을 붙인다.
	 * </pre>
	 */
	private List<TableUse> byParser(String sql, List<SqlComment> comments) throws Exception {
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
			uses.put(targetName + "|" + targetCrud, new TableUse(targetName, targetCrud, VIA_PARSER, isSystemObject(target.getFullyQualifiedName())
					, finder.columnsOf(targetName)));
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
			uses.put(name + "|R", new TableUse(name, "R", VIA_PARSER, isSystemObject(names.get(i)), finder.columnsOf(name)));
		}
		return withComments(new ArrayList<TableUse>(uses.values()), comments, finder);
	}

	/* ============================== SQL 주석의 한글명 ============================== */

	/** 주석: /* … *&#47; 또는 -- … */
	private static final Pattern COMMENT = Pattern.compile("/\\*(.*?)\\*/|--([^\\n]*)", Pattern.DOTALL);

	/** 주석 앞이 "FROM / JOIN / INTO / UPDATE 테이블 [별칭]" 으로 끝난다 → 테이블의 한글명 */
	private static final Pattern BEFORE_TABLE_COMMENT = Pattern.compile("(?i)\\b(?:FROM|JOIN|INTO|UPDATE)\\s+([A-Za-z_][\\w$#.]*)(?:\\s+(?:AS\\s+)?([A-Za-z_][\\w$#]*))?\\s*$");

	/** 주석 앞이 "[별칭.]컬럼" 으로 끝나고, 그 앞은 줄의 처음 / 쉼표 / 괄호 / SELECT 다 → 컬럼의 한글명(조회 목록, INSERT 컬럼 목록) */
	private static final Pattern BEFORE_COLUMN_COMMENT = Pattern.compile("(?i)(?:^|[,(]|\\bSELECT|\\bDISTINCT)\\s*(?:([A-Za-z_][\\w$#]*)\\.)?([A-Za-z_][\\w$#]*)\\s*$");

	/**
	 * <pre>
	 * 주석 앞이 "[별칭.]컬럼 = 값" 으로 끝난다 → 컬럼의 한글명(UPDATE 의 SET, WHERE 조건).
	 * 값은 파라미터와 현재 시각만 본다.
	 *   "status = '01' /* 접수 *&#47;" 처럼 글자 / 숫자와 비교한 줄의 주석은 컬럼이 아니라 그 값의 뜻을 적은 것이다.
	 *   "ON a.x = b.x /* 설명 *&#47;" 같은 조인 조건은 어느 쪽 컬럼의 설명인지 모른다.
	 * </pre>
	 */
	private static final Pattern BEFORE_ASSIGN_COMMENT = Pattern.compile("(?i)(?:^|[,(]|\\bSET|\\bAND|\\bOR|\\bWHERE)\\s*(?:([A-Za-z_][\\w$#]*)\\.)?([A-Za-z_][\\w$#]*)\\s*=\\s*"
			+ "(?:#\\{[^}]*\\}|#[\\w.\\[\\]]+#|\\?|SYSDATE|SYSTIMESTAMP|NOW\\(\\)|CURRENT_TIMESTAMP)\\s*$");

	/**
	 * <pre>
	 * 주석 앞(같은 줄)이 "[,] 테이블 [별칭]" 으로 끝난다 → FROM / JOIN 절 안이면 테이블의 한글명.
	 *   FROM
	 *        JC_BENEFIT A        /* 보험금 *&#47;      ← FROM 을 앞줄에 쓴 경우
	 *       ,JC_ACDT_AGMT B      /* 사고계약 *&#47;    ← 쉼표로 늘어놓은 두 번째 테이블
	 * 같은 모양이 조회 목록(SELECT a.col alias)에도 나오므로, 절이 무엇인지는 ClauseTracker 로 확인한다.
	 * </pre>
	 */
	private static final Pattern TABLE_ITEM_COMMENT = Pattern.compile("(?i)(?:^|,)\\s*([A-Za-z_][\\w$#.]*)(?:\\s+(?:AS\\s+)?([A-Za-z_][\\w$#]*))?\\s*$");

	/** 주석 앞에 오면 별칭이 아니라 다음 낱말인 것 */
	private static final String NOT_ALIASES = "|SET|WHERE|ON|USING|VALUES|SELECT|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|CROSS|GROUP|ORDER|UNION|WITH|AND|OR|";


	/**
	 * <pre>
	 * SQL 주석 하나와, 그 주석이 무엇을 설명하는지
	 * </pre>
	 */
	private static class SqlComment {

		/** 테이블을 설명하면 그 테이블 이름, 컬럼을 설명하면 null */
		String table;

		/** 컬럼 앞에 적힌 별칭(tb1.col 의 TB1). 없으면 null */
		String qualifier;

		String column;

		String text;
	}

	/** SQL에서 한글이 들어 있는 주석과 그 앞에 적힌 것을 찾습니다. */
	private List<SqlComment> commentsOf(String sql) {
		List<SqlComment> found = new ArrayList<SqlComment>();
		if (sql == null || sql.indexOf("/*") < 0 && sql.indexOf("--") < 0) {
			return found;
		}
		Matcher m = COMMENT.matcher(sql);
		ClauseTracker clauses = new ClauseTracker(sql);
		int previousEnd = 0;
		while (m.find()) {
			String text = KoreanName.clean(m.group(1) != null ? m.group(1) : m.group(2));
			int lineStart = sql.lastIndexOf('\n', m.start() - 1) + 1;
			String before = sql.substring(Math.max(lineStart, previousEnd), m.start()).trim();
			previousEnd = m.end();
			if (text == null) {
				continue;
			}
			if (before.endsWith(",")) {
				// "tb1.col, /* 설명 */" 처럼 쉼표 뒤에 단 주석
				before = before.substring(0, before.length() - 1).trim();
			}
			SqlComment comment = new SqlComment();
			comment.text = text;
			Matcher table = BEFORE_TABLE_COMMENT.matcher(before);
			if (table.find() && (table.group(2) == null || NOT_ALIASES.indexOf("|" + table.group(2).toUpperCase(Locale.ROOT) + "|") < 0)) {
				comment.table = normalize(table.group(1));
				if (comment.table != null) {
					found.add(comment);
				}
				continue;
			}
			Matcher item = TABLE_ITEM_COMMENT.matcher(before);
			if (item.find() && (item.group(2) == null || NOT_ALIASES.indexOf("|" + item.group(2).toUpperCase(Locale.ROOT) + "|") < 0)
					&& clauses.inTableList(m.start())) {
				comment.table = normalize(item.group(1));
				if (comment.table != null) {
					found.add(comment);
				}
				continue;
			}
			Matcher column = BEFORE_COLUMN_COMMENT.matcher(before);
			if (!column.find()) {
				column = BEFORE_ASSIGN_COMMENT.matcher(before);
				if (!column.find()) {
					continue;
				}
			}
			comment.qualifier = column.group(1) == null ? null : nameOf(column.group(1));
			comment.column = nameOf(column.group(2));
			found.add(comment);
		}
		return found;
	}

	/**
	 * <pre>
	 * SQL 을 앞에서부터 읽으며, 지금 자리가 어느 절(SELECT / FROM / WHERE ...) 안인지 기억합니다.
	 * 괄호마다 따로 기억하므로 서브쿼리 안에서는 그 서브쿼리의 절을, 괄호를 닫으면 바깥 절을 봅니다.
	 * 주석과 '글자' 안의 낱말은 절로 치지 않습니다.
	 * 주석은 앞에서부터 차례로 묻기 때문에 매번 처음부터 읽지 않고 이어서 읽습니다.
	 * </pre>
	 */
	private static class ClauseTracker {

		/** 절을 바꾸는 낱말 */
		private static final String CLAUSES = "|SELECT|FROM|JOIN|WHERE|ON|USING|GROUP|ORDER|HAVING|SET|VALUES|INTO|UPDATE|DELETE|INSERT|MERGE"
				+ "|UNION|INTERSECT|MINUS|EXCEPT|CONNECT|START|WITH|WHEN|THEN|ELSE|";

		/** 이 절 안에 늘어놓은 것은 테이블이다 */
		private static final String TABLE_CLAUSES = "|FROM|JOIN|INTO|UPDATE|";

		private final String sql;
		private int position = 0;
		/** 괄호 단계마다 마지막으로 나온 절. 맨 끝이 지금 단계 */
		private final List<String> stack = new ArrayList<String>();

		ClauseTracker(String sql) {
			this.sql = sql;
			stack.add(null);
		}

		/** end 자리(주석이 시작하는 곳)가 FROM / JOIN / INTO / UPDATE 절 안인지 */
		boolean inTableList(int end) {
			readTo(end);
			String clause = stack.get(stack.size() - 1);
			return clause != null && TABLE_CLAUSES.indexOf("|" + clause + "|") >= 0;
		}

		/** end 위치까지 글자를 읽어 나가며 괄호 깊이를 맞춥니다. 주석과 따옴표 글 안의 괄호는 세지 않습니다. */
		private void readTo(int end) {
			while (position < end) {
				char c = sql.charAt(position);
				if (c == '/' && position + 1 < sql.length() && sql.charAt(position + 1) == '*') {
					int close = sql.indexOf("*/", position + 2);
					position = close < 0 ? sql.length() : close + 2;
				} else if (c == '-' && position + 1 < sql.length() && sql.charAt(position + 1) == '-') {
					int newline = sql.indexOf('\n', position);
					position = newline < 0 ? sql.length() : newline + 1;
				} else if (c == '\'') {
					int close = sql.indexOf('\'', position + 1);
					position = close < 0 ? sql.length() : close + 1;
				} else if (c == '(') {
					stack.add(null);
					position++;
				} else if (c == ')') {
					if (stack.size() > 1) {
						stack.remove(stack.size() - 1);
					}
					position++;
				} else if (Character.isLetter(c) || c == '_') {
					int start = position;
					while (position < sql.length() && (Character.isLetterOrDigit(sql.charAt(position)) || "_$#".indexOf(sql.charAt(position)) >= 0)) {
						position++;
					}
					String word = sql.substring(start, position).toUpperCase(Locale.ROOT);
					if (CLAUSES.indexOf("|" + word + "|") >= 0) {
						stack.set(stack.size() - 1, word);
					}
				} else {
					position++;
				}
			}
		}
	}

	/**
	 * <pre>
	 * 찾은 주석을 테이블별로 나눠 붙입니다.
	 * 컬럼 주석의 주인은 이렇게 정합니다(파서로 읽은 경우만. finder 가 null 이면 컬럼 주석은 버린다).
	 *   별칭이 있으면: 그 별칭이 가리키는 테이블이 하나일 때 그 테이블
	 *   별칭이 없으면: 이 SQL에서 그 컬럼을 쓴 테이블이 하나일 때 그 테이블
	 * 같은 것에 주석이 여러 번 달렸으면 처음 것을 씁니다.
	 * </pre>
	 */
	private List<TableUse> withComments(List<TableUse> uses, List<SqlComment> comments, ColumnFinder finder) {
		if (comments.isEmpty() || uses.isEmpty()) {
			return uses;
		}
		Set<String> tables = new HashSet<String>();
		for (int i = 0; i < uses.size(); i++) {
			tables.add(uses.get(i).table);
		}
		Map<String, String> tableComments = new HashMap<String, String>();
		Map<String, Map<String, String>> columnComments = new HashMap<String, Map<String, String>>();
		for (int i = 0; i < comments.size(); i++) {
			SqlComment comment = comments.get(i);
			if (comment.table != null) {
				if (tables.contains(comment.table) && !tableComments.containsKey(comment.table)) {
					tableComments.put(comment.table, comment.text);
				}
				continue;
			}
			String owner = finder == null ? null : finder.ownerOf(comment.qualifier, comment.column);
			if (owner == null || !tables.contains(owner)) {
				continue;
			}
			Map<String, String> byColumn = columnComments.get(owner);
			if (byColumn == null) {
				byColumn = new LinkedHashMap<String, String>();
				columnComments.put(owner, byColumn);
			}
			if (!byColumn.containsKey(comment.column)) {
				byColumn.put(comment.column, comment.text);
			}
		}
		List<TableUse> result = new ArrayList<TableUse>();
		for (int i = 0; i < uses.size(); i++) {
			TableUse use = uses.get(i);
			Map<String, String> byColumn = columnComments.get(use.table);
			result.add(new TableUse(use.table, use.crud, use.via, use.system, use.columns
					, byColumn == null ? new LinkedHashMap<String, String>() : byColumn, tableComments.get(use.table)));
		}
		return result;
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

		/** SQL 전체에서 별칭(또는 테이블 이름) → 그것이 가리킨 테이블들. 주석의 주인을 찾을 때 쓴다 */
		private final Map<String, Set<String>> aliasTables = new HashMap<String, Set<String>>();

		/**
		 * <pre>
		 * 주석이 달린 컬럼의 테이블. 정할 수 없으면 null
		 * </pre>
		 *
		 * @param qualifier 컬럼 앞의 별칭. 없으면 null
		 */
		String ownerOf(String qualifier, String column) {
			if (qualifier != null) {
				Set<String> found = aliasTables.get(qualifier);
				return found != null && found.size() == 1 ? found.iterator().next() : null;
			}
			String owner = null;
			for (Map.Entry<String, Set<String>> entry : columns.entrySet()) {
				if (entry.getValue().contains(column)) {
					if (owner != null) {
						return null;
					}
					owner = entry.getKey();
				}
			}
			return owner;
		}

		/** 이 테이블에서 쓰인 컬럼. 하나도 없으면 빈 목록 */
		List<String> columnsOf(String table) {
			Set<String> found = columns.get(table);
			return found == null ? new ArrayList<String>() : new ArrayList<String>(found);
		}

		/** SELECT: FROM·JOIN 의 테이블로 범위를 열고, SELECT 목록의 별칭을 기억한 뒤(별칭은 컬럼이 아니다) 안으로 내려갑니다. */
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

		/** UPDATE: SET 왼쪽의 컬럼은 대상 테이블의 컬럼입니다. 그 밖은 FROM·JOIN 범위 안에서 찾습니다. */
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

		/** DELETE: 대상과 JOIN 테이블로 범위를 열고 내려갑니다. */
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

		/** INSERT: 컬럼 목록은 대상 테이블의 컬럼입니다. VALUES / SELECT 부분은 그대로 내려갑니다. */
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

		/**
		 * <pre>
		 * 컬럼 하나의 주인 테이블을 찾습니다.
		 *   - 앞에 별칭이 붙었으면(a.col) 안쪽 범위부터 그 별칭이 가리키는 테이블
		 *   - 안 붙었으면 그 범위에 테이블이 하나뿐일 때만 그 테이블(SELECT 별칭이면 뺀다)
		 * </pre>
		 */
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

		/** JOIN 오른쪽의 테이블(또는 서브쿼리)을 items 에 더합니다. */
		private void addJoins(List<FromItem> items, List<Join> joins) {
			for (int i = 0; joins != null && i < joins.size(); i++) {
				items.add(joins.get(i).getRightItem());
			}
		}

		/** 새 범위(SELECT 하나)를 엽니다. 별칭 → 테이블 표를 만들고, 테이블이 딱 하나면 "별칭 없는 컬럼의 주인"으로 기억합니다. */
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
					remember(nameOf(table.getName()), name);
					if (table.getAlias() != null) {
						scope.put(nameOf(table.getAlias().getName()), name);
						remember(nameOf(table.getAlias().getName()), name);
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

		/** 별칭이 가리키는 테이블을 기억합니다(같은 별칭이 여러 테이블에 쓰일 수 있어 집합으로). */
		private void remember(String alias, String table) {
			Set<String> found = aliasTables.get(alias);
			if (found == null) {
				found = new HashSet<String>();
				aliasTables.put(alias, found);
			}
			found.add(table);
		}

		/** 범위 하나를 닫습니다. */
		private void pop() {
			scopes.remove(scopes.size() - 1);
			onlyTables.remove(onlyTables.size() - 1);
		}

		/** 테이블의 컬럼으로 이름 하나를 더합니다. 키워드·${} 자리·이름이 아닌 것은 빼고, 테이블마다 MAX_COLUMNS 개까지만 */
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

	/** 괄호가 가장 깊이 겹친 정도. 너무 깊은 SQL 은 파서에 넘기지 않을 때 씁니다. */
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

	/**
	 * <pre>
	 * 파서가 못 읽은 SQL: FROM / JOIN / INTO / UPDATE 뒤의 이름을 정규식으로 찾습니다(guessed 표시, 컬럼은 뽑지 않음).
	 * 처음 나온 INTO / UPDATE / (DELETE) FROM 뒤의 이름이 쓰는 대상이고, 나머지는 읽기입니다.
	 * </pre>
	 */
	private List<TableUse> byPattern(String sql, String statementType) {
		Map<String, TableUse> uses = new LinkedHashMap<String, TableUse>();
		boolean[] inFunction = functionArgumentsOf(sql);
		Matcher m = TABLE_AFTER_KEYWORD.matcher(sql);
		boolean targetFound = false;
		while (m.find()) {
			String keyword = m.group(1).toUpperCase(Locale.ROOT);
			boolean fromOrJoin = "FROM".equals(keyword) || "JOIN".equals(keyword);
			// 테이블 앞의 FROM 이 아닌 것들은 건너뛴다.
			//   함수 괄호 안의 FROM: trim(both '"' from a.text), extract(year from d), substring(s from 2)
			//   비교의 FROM: a.x IS DISTINCT FROM b.x
			//   FROM 함수(...): FROM unnest(...), FROM generate_series(...) 은 테이블이 아니다(INTO t(...) 는 컬럼 목록이라 뺀다)
			if ("FROM".equals(keyword) && (inFunction[m.start()] || DISTINCT_BEFORE.matcher(sql.substring(Math.max(0, m.start() - 20), m.start())).find())) {
				continue;
			}
			if (fromOrJoin && FUNCTION_CALL_AFTER.matcher(sql).region(m.end(), sql.length()).lookingAt()) {
				continue;
			}
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
			uses.put(name + "|" + crud, new TableUse(name, crud, VIA_PATTERN, isSystemObject(m.group(2)), null));
		}
		return new ArrayList<TableUse>(uses.values());
	}

	/**
	 * <pre>
	 * 글자마다 "함수 괄호 안인가"를 표시합니다. byPattern 이 함수 인자 속의 FROM 을 테이블로 잡지 않게 할 때 씁니다.
	 *
	 * 괄호 바로 앞에 이름이 붙어 있으면 함수 괄호로 봅니다: trim( / extract( / substring(
	 * 다만 괄호 안이 SELECT / WITH 로 시작하면 서브쿼리이고, IN / EXISTS 같은 키워드 뒤의 괄호도 함수가 아닙니다.
	 * 따옴표 안의 괄호는 세지 않습니다.
	 * </pre>
	 */
	private boolean[] functionArgumentsOf(String sql) {
		boolean[] inFunction = new boolean[sql.length() + 1];
		List<Boolean> stack = new ArrayList<Boolean>();
		boolean quoted = false;
		for (int i = 0; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if (c == '\'') {
				quoted = !quoted;
			} else if (!quoted && c == '(') {
				stack.add(Boolean.valueOf(isFunctionParen(sql, i)));
			} else if (!quoted && c == ')' && !stack.isEmpty()) {
				stack.remove(stack.size() - 1);
			}
			inFunction[i] = !stack.isEmpty() && stack.get(stack.size() - 1).booleanValue();
		}
		return inFunction;
	}

	/** open 자리의 여는 괄호가 함수 호출의 괄호인지: 앞에 이름이 붙어 있고, 그 이름이 키워드가 아니고, 안이 서브쿼리가 아닐 때 */
	private boolean isFunctionParen(String sql, int open) {
		if (SUBQUERY_START.matcher(sql).region(open + 1, sql.length()).lookingAt()) {
			return false;
		}
		int end = open;
		while (end > 0 && sql.charAt(end - 1) == ' ') {
			end--;
		}
		int start = end;
		while (start > 0 && (Character.isLetterOrDigit(sql.charAt(start - 1)) || sql.charAt(start - 1) == '_' || sql.charAt(start - 1) == '$')) {
			start--;
		}
		if (start == end) {
			return false;
		}
		String word = sql.substring(start, end).toUpperCase(Locale.ROOT);
		return NOT_FUNCTIONS.indexOf("|" + word + "|") < 0;
	}

	/**
	 * <pre>
	 * DB가 가진 시스템 카탈로그인지 봅니다. 업무 테이블이 아니라서 테이블 관계로 넣지 않습니다.
	 *
	 *   스키마가 붙은 것: information_schema.* / pg_catalog.* / sys.* / mysql.* / performance_schema.* / sysibm.* / syscat.*
	 *   이름만으로: PostgreSQL 카탈로그(pg_class, pg_extension ...), Oracle 사전 뷰(ALL_TABLES, USER_TAB_COLUMNS ...), V$ / GV$ 뷰
	 * PG_ / USER_ 로 시작한다고 모두 빼지는 않습니다. PG_PAYMENT(결제대행), USER_INFO 같은 업무 테이블이 흔해서, 알려진 이름만 봅니다.
	 * </pre>
	 */
	static boolean isSystemObject(String written) {
		if (written == null) {
			return false;
		}
		String name = written.replace("\"", "").replace("`", "").replace("[", "").replace("]", "").trim().toUpperCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		String bare = dot < 0 ? name : name.substring(dot + 1);
		if (dot > 0) {
			String schema = name.substring(0, dot);
			schema = schema.substring(schema.lastIndexOf('.') + 1);
			if (SYSTEM_SCHEMAS.indexOf("|" + schema + "|") >= 0) {
				return true;
			}
		}
		if (bare.startsWith("V$") || bare.startsWith("GV$")) {
			return true;
		}
		if (bare.startsWith("ALL_") || bare.startsWith("USER_") || bare.startsWith("DBA_")) {
			return ORACLE_DICTIONARY.indexOf("|" + bare.substring(bare.indexOf('_') + 1) + "|") >= 0;
		}
		return SYSTEM_TABLES.indexOf("|" + bare + "|") >= 0;
	}

	/** 따옴표와 스키마를 떼고 대문자로 맞춥니다(DPHJSDBA.JZ_EMP_INFO → JZ_EMP_INFO). 테이블이 아닌 것(DUAL 등)은 null */
	private String normalize(String name) {
		if (name == null) {
			return null;
		}
		String cleaned = TableName.bare(name);
		if (cleaned.length() == 0 || NOT_TABLES.indexOf("|" + cleaned + "|") >= 0 || cleaned.indexOf("X_") >= 0 || cleaned.indexOf('?') >= 0) {
			return null;
		}
		return cleaned.length() > 300 ? cleaned.substring(0, 300) : cleaned;
	}

}

package net.dstone.knowledge.analysis.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <pre>
 * 테이블마다 "어떤 컬럼이 있는가"를 담은 목록입니다. SQL 에서 테이블 관계를 만들 때 씁니다(SqlTableExtractor.applyCatalog).
 *
 * 출처는 두 가지이고, 테이블마다 앞의 것이 이깁니다.
 *   1) DB  : 프로젝트에 등록한 DB 접속에서 이번 분석 때 읽어 둔 것(analysis_resource 의 DB_TABLE / DB_COLUMN).
 *            접속이 여러 개면 등록한 순서대로 보고, 테이블마다 처음 나온 접속의 것만 씁니다.
 *   2) DDL : 소스에 들어 있는 DDL 파일(DDL_TABLE / DDL_COLUMN). DB 에 없는 테이블만 여기서 채웁니다.
 *
 * DDL 은 일부 테이블만 있을 때가 많습니다. 그래서 "목록에 없는 이름" 을 알리는 일은 DB 에서 읽은 것만 합니다.
 * 테이블 · 컬럼 이름은 스키마를 뗀 대문자입니다(테이블 관계와 같은 모양).
 * </pre>
 */
public class DbCatalog {

	public static final String FROM_DB = "DB";
	public static final String FROM_DDL = "DDL";

	/** 테이블 → 컬럼(정의된 순서) */
	private final Map<String, Set<String>> columns = new HashMap<String, Set<String>>();

	/** 테이블 → 출처(DB / DDL) */
	private final Map<String, String> sources = new HashMap<String, String>();

	/** 테이블 → DB 접속 이름. 처음 나온 접속만 */
	private final Map<String, String> connections = new HashMap<String, String>();

	private boolean anyDb;

	/** 아무것도 없는 목록 */
	public static DbCatalog empty() {
		return new DbCatalog();
	}

	/**
	 * <pre>
	 * analysis_resource 행으로 목록을 만듭니다. 행은 resource_id 순서(= DB 접속을 등록한 순서)여야 합니다.
	 * 행마다 resourceType, table, column, location(DB 접속 이름) 을 읽습니다.
	 * </pre>
	 */
	public static DbCatalog of(List<Map<String, Object>> rows) {
		DbCatalog catalog = new DbCatalog();
		// DB 를 먼저 다 담고, 그다음 DDL 로 DB 에 없는 테이블만 채운다.
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String type = (String) row.get("resourceType");
			if ("DB_TABLE".equals(type) || "DB_COLUMN".equals(type)) {
				catalog.add(FROM_DB, (String) row.get("location"), (String) row.get("table"), (String) row.get("column"));
			}
		}
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String type = (String) row.get("resourceType");
			if ("DDL_TABLE".equals(type) || "DDL_COLUMN".equals(type)) {
				catalog.add(FROM_DDL, null, (String) row.get("table"), (String) row.get("column"));
			}
		}
		return catalog;
	}

	/** 테이블(그리고 컬럼) 하나를 담는다. 그 테이블을 이미 다른 출처나 다른 접속에서 담았으면 버린다 */
	private void add(String source, String connection, String table, String column) {
		if (table == null || table.length() == 0) {
			return;
		}
		String known = sources.get(table);
		if (known == null) {
			sources.put(table, source);
			columns.put(table, new LinkedHashSet<String>());
			if (connection != null) {
				connections.put(table, connection);
			}
			anyDb = anyDb || FROM_DB.equals(source);
		} else if (!known.equals(source) || connection != null && !connection.equals(connections.get(table))) {
			return;
		}
		if (column != null && column.length() > 0) {
			columns.get(table).add(column);
		}
	}

	/** DB 에서 읽은 것이 하나라도 있으면 true. 이때만 "DB 에 없는 이름"을 알린다 */
	public boolean hasDb() {
		return anyDb;
	}

	/** 이 테이블을 안다(DB 나 DDL 어디서든) */
	public boolean knows(String table) {
		return sources.containsKey(table);
	}

	/** 이 테이블의 출처(DB / DDL). 모르면 null */
	public String sourceOf(String table) {
		return sources.get(table);
	}

	/** 이 테이블이 DB 에 있다 */
	public boolean inDb(String table) {
		return FROM_DB.equals(sources.get(table));
	}

	/** 이 테이블의 컬럼(정의된 순서). 모르면 빈 목록 */
	public List<String> columnsOf(String table) {
		Set<String> found = columns.get(table);
		return found == null ? new ArrayList<String>() : new ArrayList<String>(found);
	}

	/** 이 테이블에 이 컬럼이 있다 */
	public boolean has(String table, String column) {
		Set<String> found = columns.get(table);
		return found != null && found.contains(column);
	}

	/** 담은 테이블 수 */
	public int size() {
		return sources.size();
	}

}

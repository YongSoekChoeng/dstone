package net.dstone.ai.tools.db;

import java.io.Reader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 미리 등록해 둔 DB에서 테이블 구조를 보고 조회 쿼리를 실행하는 Tool입니다.
 *
 *   dbListConnections   등록된 DB 별칭 목록(주소, 계정, 설명)
 *   dbListTables        테이블 / 뷰 목록
 *   dbDescribeTable     테이블 하나의 컬럼, 타입, PK, 코멘트
 *   dbQuery             조회(SELECT) 문장 하나 실행
 *
 * 접속 정보는 LLM에게서 받지 않습니다. 설정(dstone.ai.tool.db.connections.{별칭})에 적어 둔 DB만,
 * 별칭으로만 부를 수 있습니다. 등록된 DB가 없으면(기본값) 모든 호출이 실패 문구로 끝나므로,
 * 별칭을 등록하는 것이 곧 "이 Tool을 켠 상태"입니다.
 *
 * 어떤 DB를 등록해야 하는지는 KnowledgeTool.knowledgeDatasources(분석한 애플리케이션의 데이터소스 설정)로 찾습니다.
 * 분석 결과에는 비밀번호가 없으므로(****) 사람이 여기에 ENC(...)로 등록해야 접속할 수 있습니다.
 *
 * 쓰기를 막는 장치는 세 겹입니다.
 *   1. DbSqlGuard: 실행 전에 조회 문장 하나인지 확인
 *   2. 읽기 전용 연결(Connection.setReadOnly)
 *   3. 자동 커밋을 끄고, 끝나면 항상 롤백
 * 그래도 DB 계정은 읽기 권한만 있는 것으로 등록하기를 권합니다(함수 호출의 부작용까지는 막지 못합니다).
 *
 * 결과는 대화에 쌓여 뒤의 LLM 호출마다 다시 실리므로 짧게 줄입니다(행 수, 칸 하나의 글자 수 상한).
 * 비밀번호처럼 보이는 이름의 컬럼은 값 대신 ****를 돌려줍니다.
 * </pre>
 */
@AiTool
public class DbQueryTool extends BaseObject implements DisposableBean {

	/** 설정(max-rows)이 없을 때 dbQuery가 돌려주는 최대 행 수 */
	private static final int DEFAULT_MAX_ROWS = 100;

	/** 설정(max-cell-chars)이 없을 때 칸 하나의 최대 글자 수 */
	private static final int DEFAULT_MAX_CELL_CHARS = 200;

	/** 설정(query-timeout-seconds)이 없을 때 쿼리 대기 시간(초) */
	private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;

	/** 설정(mask-columns)이 없을 때 값을 가리는 컬럼 이름 */
	private static final String DEFAULT_MASK_COLUMNS = "(?i)(password|passwd|pwd|secret|token|api_?key|ssn|jumin|resident_?no|card_?no)";

	/** 테이블 목록 / 컬럼 목록에서 돌려주는 최대 줄 수 */
	private static final int MAX_LINES = 200;

	@Autowired
	private Environment environment;

	/** 별칭마다 하나씩 만든 커넥션 풀. 처음 부를 때 만든다 */
	private final Map<String, Pool> pools = new ConcurrentHashMap<String, Pool>();

	/** 커넥션 풀과, 그 풀을 만들 때의 설정 */
	private static class Pool {
		final String signature;
		final HikariDataSource dataSource;

		Pool(String signature, HikariDataSource dataSource) {
			this.signature = signature;
			this.dataSource = dataSource;
		}
	}

	@Tool(description = "조회할 수 있도록 등록된 DB의 별칭 목록을 돌려준다(JDBC 주소, 계정, 설명). 다른 db Tool에 넘길 alias를 모를 때 먼저 부른다. "
		+ "knowledgeDatasources로 찾은 애플리케이션의 JDBC 주소와 같은 주소의 별칭을 고르면 그 애플리케이션의 DB다.")
	public String dbListConnections() {
		Map<String, DbConnectionSetting> connections = this.connections();
		if (connections.isEmpty()) {
			return "실패: 등록된 DB가 없습니다. 관리자가 dstone.ai.tool.db.connections에 별칭을 등록해야 합니다.";
		}
		StringBuilder sb = new StringBuilder();
		Iterator<Map.Entry<String, DbConnectionSetting>> it = connections.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, DbConnectionSetting> entry = it.next();
			DbConnectionSetting setting = entry.getValue();
			sb.append(entry.getKey()).append("  ").append(maskUrl(setting.getUrl())).append("  user=").append(nvl(setting.getUsername()));
			if (!StringUtil.isEmpty(setting.getSchema())) {
				sb.append("  schema=").append(setting.getSchema());
			}
			if (!StringUtil.isEmpty(setting.getDescription())) {
				sb.append("  - ").append(setting.getDescription());
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	@Tool(description = "등록된 DB의 테이블과 뷰 목록을 돌려준다(종류, 스키마.이름, 코멘트). 이름 일부로 좁힐 수 있다.")
	public String dbListTables(@ToolParam(description = "DB 별칭(dbListConnections 결과)") String alias,
		@ToolParam(description = "테이블 이름에 들어 있는 글자. 생략하면 전부", required = false) String nameContains,
		@ToolParam(description = "스키마(MySQL은 데이터베이스 이름). 생략하면 등록한 기본 스키마", required = false) String schema) {
		DbConnectionSetting setting = this.connections().get(nvl(alias).trim());
		if (setting == null) {
			return this.unknownAlias(alias);
		}
		Connection connection = null;
		try {
			connection = this.open(alias.trim(), setting);
			DatabaseMetaData meta = connection.getMetaData();
			Scope scope = this.scope(connection, meta, StringUtil.isEmpty(schema) ? setting.getSchema() : schema);
			List<String> lines = new ArrayList<String>();
			int total = 0;
			List<String> patterns = casesOf(StringUtil.isEmpty(nameContains) ? null : nameContains.trim());
			for (int p = 0; p < patterns.size() && total == 0; p++) {
				String pattern = patterns.get(p) == null ? "%" : "%" + patterns.get(p) + "%";
				ResultSet rs = meta.getTables(scope.catalog, scope.schema, pattern, new String[] { "TABLE", "VIEW" });
				try {
					while (rs.next()) {
						total++;
						if (lines.size() < MAX_LINES) {
							String owner = nvl(rs.getString("TABLE_SCHEM")).length() > 0 ? rs.getString("TABLE_SCHEM") : nvl(rs.getString("TABLE_CAT"));
							String remarks = oneLine(rs.getString("REMARKS"));
							lines.add(rs.getString("TABLE_TYPE") + "  " + (owner.length() > 0 ? owner + "." : "") + rs.getString("TABLE_NAME")
								+ (remarks.length() > 0 ? "  - " + remarks : ""));
						}
					}
				} finally {
					rs.close();
				}
			}
			this.audit(alias, "listTables " + nvl(nameContains), total + "건");
			if (total == 0) {
				return "테이블이 없습니다(스키마=" + scope.label() + (StringUtil.isEmpty(nameContains) ? "" : ", 이름에 '" + nameContains + "' 포함") + ").";
			}
			StringBuilder sb = new StringBuilder();
			sb.append("스키마: ").append(scope.label()).append('\n');
			for (int i = 0; i < lines.size(); i++) {
				sb.append(lines.get(i)).append('\n');
			}
			appendMore(sb, total, lines.size());
			return sb.toString();
		} catch (Exception e) {
			this.audit(alias, "listTables " + nvl(nameContains), "실패 " + e.getMessage());
			return "실패: 테이블 목록을 읽지 못했습니다 - " + oneLine(e.getMessage());
		} finally {
			this.close(connection);
		}
	}

	@Tool(description = "테이블 하나의 컬럼(순서, 이름, 타입, NULL 허용, PK, 코멘트)을 돌려준다. 쿼리를 쓰기 전에 컬럼 이름을 확인할 때 쓴다.")
	public String dbDescribeTable(@ToolParam(description = "DB 별칭(dbListConnections 결과)") String alias,
		@ToolParam(description = "테이블 이름(정확히). 대소문자는 DB에 맞춰 찾아 본다") String table,
		@ToolParam(description = "스키마(MySQL은 데이터베이스 이름). 생략하면 등록한 기본 스키마", required = false) String schema) {
		DbConnectionSetting setting = this.connections().get(nvl(alias).trim());
		if (setting == null) {
			return this.unknownAlias(alias);
		}
		if (StringUtil.isEmpty(table)) {
			return "실패: table이 비어 있습니다.";
		}
		String tableName = table.trim();
		// SCHEMA.TABLE 로 주면 나눈다
		String schemaName = StringUtil.isEmpty(schema) ? setting.getSchema() : schema;
		int dot = tableName.lastIndexOf('.');
		if (dot > 0) {
			schemaName = tableName.substring(0, dot);
			tableName = tableName.substring(dot + 1);
		}
		Connection connection = null;
		try {
			connection = this.open(alias.trim(), setting);
			DatabaseMetaData meta = connection.getMetaData();
			Scope scope = this.scope(connection, meta, schemaName);
			List<String> names = casesOf(tableName);
			for (int n = 0; n < names.size(); n++) {
				String name = names.get(n);
				String described = this.describe(meta, scope, name);
				if (described != null) {
					this.audit(alias, "describeTable " + name, "성공");
					return described;
				}
			}
			this.audit(alias, "describeTable " + tableName, "없음");
			return "실패: 테이블이 없습니다: " + tableName + " (스키마=" + scope.label() + "). dbListTables로 이름을 확인하세요.";
		} catch (Exception e) {
			this.audit(alias, "describeTable " + tableName, "실패 " + e.getMessage());
			return "실패: 테이블 구조를 읽지 못했습니다 - " + oneLine(e.getMessage());
		} finally {
			this.close(connection);
		}
	}

	@Tool(description = "등록된 DB에서 조회(SELECT) 문장 하나를 실행하고 결과를 돌려준다. INSERT/UPDATE/DELETE/DDL과 여러 문장은 실행하지 않는다. "
		+ "결과는 정해진 행 수까지만 오므로, 필요한 컬럼과 조건(WHERE)을 좁혀서 쓴다. 컬럼 이름을 모르면 dbDescribeTable을 먼저 부른다.")
	public String dbQuery(@ToolParam(description = "DB 별칭(dbListConnections 결과)") String alias,
		@ToolParam(description = "실행할 SELECT 문장 하나. 세미콜론으로 여러 문장을 잇지 않는다") String sql,
		@ToolParam(description = "돌려받을 최대 행 수. 생략하거나 상한보다 크면 상한까지", required = false) Integer maxRows) {
		DbConnectionSetting setting = this.connections().get(nvl(alias).trim());
		if (setting == null) {
			return this.unknownAlias(alias);
		}
		String trimmed = trimSql(sql);
		String problem = DbSqlGuard.check(trimmed);
		if (problem != null) {
			this.audit(alias, trimmed, "거부 " + problem);
			return "실패: " + problem;
		}
		int limit = this.maxRows();
		if (maxRows != null && maxRows.intValue() > 0 && maxRows.intValue() < limit) {
			limit = maxRows.intValue();
		}
		Connection connection = null;
		Statement statement = null;
		try {
			connection = this.open(alias.trim(), setting);
			statement = connection.createStatement();
			// 한 행 더 읽어서 "더 있다"를 알린다
			statement.setMaxRows(limit + 1);
			statement.setFetchSize(limit + 1);
			statement.setQueryTimeout(this.queryTimeoutSeconds());
			ResultSet rs = statement.executeQuery(trimmed);
			String result;
			int rows;
			try {
				StringBuilder sb = new StringBuilder();
				rows = this.writeRows(rs, limit, sb);
				result = sb.toString();
			} finally {
				rs.close();
			}
			this.audit(alias, trimmed, rows + "행");
			return result;
		} catch (Exception e) {
			this.audit(alias, trimmed, "실패 " + e.getMessage());
			return "실패: 쿼리 실행 오류 - " + oneLine(e.getMessage());
		} finally {
			if (statement != null) {
				try {
					statement.close();
				} catch (Exception ignore) {
					// 닫다가 난 오류는 결과와 상관없다
				}
			}
			this.close(connection);
		}
	}

	/* ============================== 결과 만들기 ============================== */

	/** 테이블 하나의 설명. 그 이름의 테이블이 없으면 null */
	private String describe(DatabaseMetaData meta, Scope scope, String table) throws Exception {
		String tableRemarks = null;
		String owner = null;
		boolean found = false;
		ResultSet tables = meta.getTables(scope.catalog, scope.schema, table, null);
		try {
			if (tables.next()) {
				found = true;
				tableRemarks = oneLine(tables.getString("REMARKS"));
				owner = nvl(tables.getString("TABLE_SCHEM")).length() > 0 ? tables.getString("TABLE_SCHEM") : tables.getString("TABLE_CAT");
			}
		} finally {
			tables.close();
		}
		if (!found) {
			return null;
		}
		Set<String> primaryKeys = new LinkedHashSet<String>();
		ResultSet pk = meta.getPrimaryKeys(scope.catalog, scope.schema, table);
		try {
			Map<Integer, String> ordered = new TreeMap<Integer, String>();
			while (pk.next()) {
				ordered.put(Integer.valueOf(pk.getInt("KEY_SEQ")), pk.getString("COLUMN_NAME"));
			}
			primaryKeys.addAll(ordered.values());
		} finally {
			pk.close();
		}
		StringBuilder sb = new StringBuilder();
		sb.append("테이블: ").append(nvl(owner).length() > 0 ? owner + "." : "").append(table);
		if (!StringUtil.isEmpty(tableRemarks)) {
			sb.append(" (").append(tableRemarks).append(')');
		}
		sb.append('\n');
		if (!primaryKeys.isEmpty()) {
			sb.append("PK: ").append(String.join(", ", primaryKeys)).append('\n');
		}
		sb.append("(순서, 컬럼, 타입, NULL, 코멘트)\n");
		ResultSet columns = meta.getColumns(scope.catalog, scope.schema, table, "%");
		int total = 0;
		int shown = 0;
		try {
			while (columns.next()) {
				total++;
				if (shown >= MAX_LINES) {
					continue;
				}
				shown++;
				String name = columns.getString("COLUMN_NAME");
				String type = columns.getString("TYPE_NAME");
				int size = columns.getInt("COLUMN_SIZE");
				int scale = columns.getInt("DECIMAL_DIGITS");
				boolean scaleNull = columns.wasNull();
				String remarks = oneLine(columns.getString("REMARKS"));
				sb.append(columns.getInt("ORDINAL_POSITION")).append("  ").append(name).append(primaryKeys.contains(name) ? "(PK)" : "").append("  ").append(type);
				if (size > 0) {
					sb.append('(').append(size).append(!scaleNull && scale > 0 ? "," + scale : "").append(')');
				}
				sb.append("  ").append("NO".equals(columns.getString("IS_NULLABLE")) ? "NOT NULL" : "NULL");
				if (remarks.length() > 0) {
					sb.append("  ").append(remarks);
				}
				sb.append('\n');
			}
		} finally {
			columns.close();
		}
		appendMore(sb, total, shown);
		return sb.toString();
	}

	/** 결과 행을 "열1 | 열2" 모양으로 쓴다. 쓴 행 수를 돌려준다 */
	private int writeRows(ResultSet rs, int limit, StringBuilder sb) throws Exception {
		ResultSetMetaData meta = rs.getMetaData();
		int count = meta.getColumnCount();
		Pattern mask = this.maskPattern();
		boolean[] masked = new boolean[count + 1];
		sb.append("열: ");
		for (int c = 1; c <= count; c++) {
			String label = meta.getColumnLabel(c);
			masked[c] = mask.matcher(nvl(label)).find();
			sb.append(c > 1 ? " | " : "").append(label);
		}
		sb.append('\n');
		int maxCell = this.maxCellChars();
		int rows = 0;
		boolean more = false;
		while (rs.next()) {
			if (rows >= limit) {
				more = true;
				break;
			}
			rows++;
			sb.append(rows).append(") ");
			for (int c = 1; c <= count; c++) {
				sb.append(c > 1 ? " | " : "").append(masked[c] ? "****" : cell(rs, c, maxCell));
			}
			sb.append('\n');
		}
		if (rows == 0) {
			sb.append("(결과 없음)\n");
		} else if (more) {
			sb.append(rows).append("행까지만 보였습니다. 더 있습니다(조건을 좁히거나 집계 쿼리로 다시 조회하세요).\n");
		} else {
			sb.append(rows).append("행\n");
		}
		return rows;
	}

	/** 칸 하나의 값을 짧은 한 줄 글로 바꾼다 */
	private static String cell(ResultSet rs, int column, int maxChars) throws Exception {
		Object value = rs.getObject(column);
		if (value == null) {
			return "NULL";
		}
		String text;
		if (value instanceof byte[]) {
			return "(이진값 " + ((byte[]) value).length + "바이트)";
		} else if (value instanceof Clob) {
			text = readClob((Clob) value, maxChars + 1);
		} else if (value instanceof java.sql.Blob) {
			return "(이진값 " + ((java.sql.Blob) value).length() + "바이트)";
		} else {
			text = String.valueOf(value);
		}
		text = oneLine(text);
		return text.length() > maxChars ? text.substring(0, maxChars) + "...(" + text.length() + "자)" : text;
	}

	private static String readClob(Clob clob, int maxChars) throws Exception {
		Reader reader = clob.getCharacterStream();
		try {
			char[] buffer = new char[maxChars];
			int read = 0;
			while (read < maxChars) {
				int n = reader.read(buffer, read, maxChars - read);
				if (n < 0) {
					break;
				}
				read += n;
			}
			return new String(buffer, 0, read);
		} finally {
			reader.close();
		}
	}

	/* ============================== 접속 ============================== */

	/** 어느 카탈로그 / 스키마에서 찾을지. MySQL은 데이터베이스가 카탈로그다 */
	private static class Scope {
		String catalog;
		String schema;

		String label() {
			return schema != null ? schema : catalog != null ? catalog : "(기본)";
		}
	}

	private Scope scope(Connection connection, DatabaseMetaData meta, String schema) throws Exception {
		Scope scope = new Scope();
		// MySQL처럼 스키마 대신 카탈로그(데이터베이스)로 나누는 DB가 있다
		boolean useCatalog = !meta.supportsSchemasInTableDefinitions() && meta.supportsCatalogsInTableDefinitions();
		String name = StringUtil.isEmpty(schema) ? null : schema.trim();
		if (useCatalog) {
			scope.catalog = name != null ? name : connection.getCatalog();
		} else {
			scope.schema = name != null ? name : connection.getSchema();
			// 스키마 이름도 DB마다 대소문자가 다르다(Oracle은 대문자, PostgreSQL은 소문자)
			if (scope.schema != null && name != null) {
				if (meta.storesUpperCaseIdentifiers()) {
					scope.schema = scope.schema.toUpperCase(Locale.ROOT);
				} else if (meta.storesLowerCaseIdentifiers()) {
					scope.schema = scope.schema.toLowerCase(Locale.ROOT);
				}
			}
		}
		return scope;
	}

	/** 별칭의 커넥션 풀에서 연결 하나를 빌린다. 읽기 전용, 자동 커밋 꺼짐 */
	private Connection open(String alias, DbConnectionSetting setting) throws Exception {
		if (StringUtil.isEmpty(setting.getUrl())) {
			throw new IllegalStateException("별칭 [" + alias + "]의 url이 비어 있습니다.");
		}
		Pool pool = this.pools.get(alias);
		if (pool == null || !pool.signature.equals(setting.signature())) {
			synchronized (this.pools) {
				pool = this.pools.get(alias);
				if (pool == null || !pool.signature.equals(setting.signature())) {
					if (pool != null) {
						pool.dataSource.close();
					}
					pool = new Pool(setting.signature(), createDataSource(alias, setting));
					this.pools.put(alias, pool);
				}
			}
		}
		Connection connection = pool.dataSource.getConnection();
		connection.setReadOnly(true);
		connection.setAutoCommit(false);
		return connection;
	}

	/** 조회용 작은 풀. 쓰지 않으면 연결을 모두 놓는다 */
	static HikariDataSource createDataSource(String alias, DbConnectionSetting setting) {
		HikariConfig config = new HikariConfig();
		config.setPoolName("dstone-ai-db-" + alias);
		config.setJdbcUrl(setting.getUrl());
		config.setUsername(setting.getUsername());
		config.setPassword(setting.getPassword());
		config.setReadOnly(true);
		config.setAutoCommit(false);
		config.setMaximumPoolSize(2);
		config.setMinimumIdle(0);
		config.setIdleTimeout(60000L);
		config.setConnectionTimeout(10000L);
		// 기동할 때 DB가 죽어 있어도 풀은 만들고, 부를 때 실패하게 한다
		config.setInitializationFailTimeout(-1L);
		return new HikariDataSource(config);
	}

	/** 쓰기가 섞였어도 남지 않게 항상 롤백하고 돌려준다 */
	private void close(Connection connection) {
		if (connection == null) {
			return;
		}
		try {
			connection.rollback();
		} catch (Exception ignore) {
			// 롤백할 것이 없거나 연결이 끊긴 경우
		}
		try {
			connection.close();
		} catch (Exception ignore) {
			// 이미 닫힌 경우
		}
	}

	@Override
	public void destroy() {
		Iterator<Pool> it = this.pools.values().iterator();
		while (it.hasNext()) {
			it.next().dataSource.close();
		}
		this.pools.clear();
	}

	/* ============================== 설정 ============================== */

	/** 등록된 DB들. 별칭 순서는 설정에 적은 순서 */
	Map<String, DbConnectionSetting> connections() {
		Map<String, DbConnectionSetting> empty = new LinkedHashMap<String, DbConnectionSetting>();
		return Binder.get(this.environment).bind(Constants.Tool.Db.PREFIX + ".connections", Bindable.mapOf(String.class, DbConnectionSetting.class)).orElse(empty);
	}

	private int maxRows() {
		return this.intProperty("max-rows", DEFAULT_MAX_ROWS);
	}

	private int maxCellChars() {
		return this.intProperty("max-cell-chars", DEFAULT_MAX_CELL_CHARS);
	}

	private int queryTimeoutSeconds() {
		return this.intProperty("query-timeout-seconds", DEFAULT_QUERY_TIMEOUT_SECONDS);
	}

	private Pattern maskPattern() {
		String value = this.environment.getProperty(Constants.Tool.Db.PREFIX + ".mask-columns");
		return Pattern.compile(StringUtil.isEmpty(value) ? DEFAULT_MASK_COLUMNS : value);
	}

	/** 설정이 없거나 1보다 작으면 기본값 */
	private int intProperty(String name, int defaultValue) {
		Integer value = this.environment.getProperty(Constants.Tool.Db.PREFIX + "." + name, Integer.class);
		return value == null || value.intValue() < 1 ? defaultValue : value.intValue();
	}

	/* ============================== 도구 ============================== */

	private String unknownAlias(String alias) {
		Map<String, DbConnectionSetting> connections = this.connections();
		if (connections.isEmpty()) {
			return "실패: 등록된 DB가 없습니다. 관리자가 dstone.ai.tool.db.connections에 별칭을 등록해야 합니다.";
		}
		return "실패: 등록되지 않은 DB 별칭입니다: " + alias + ". 쓸 수 있는 별칭: " + String.join(", ", connections.keySet());
	}

	/** 누가 무엇을 조회했는지 남긴다 */
	private void audit(String alias, String what, String result) {
		LogUtil.sysout("dstone-ai-engine tool-audit: db alias=" + alias + " " + oneLine(what) + " -> " + oneLine(result));
	}

	/** 주어진 이름, 대문자, 소문자 순서로(같은 것은 한 번만). null이면 [null] */
	static List<String> casesOf(String name) {
		List<String> names = new ArrayList<String>();
		if (name == null) {
			names.add(null);
			return names;
		}
		Set<String> unique = new LinkedHashSet<String>();
		unique.add(name);
		unique.add(name.toUpperCase(Locale.ROOT));
		unique.add(name.toLowerCase(Locale.ROOT));
		names.addAll(unique);
		return names;
	}

	/** 끝의 세미콜론과 공백을 뗀다. LLM이 습관처럼 붙이는 경우가 많다 */
	static String trimSql(String sql) {
		if (sql == null) {
			return null;
		}
		String text = sql.trim();
		while (text.endsWith(";")) {
			text = text.substring(0, text.length() - 1).trim();
		}
		return text;
	}

	/** JDBC 주소에 password=... 가 들어 있으면 가린다 */
	static String maskUrl(String url) {
		if (url == null) {
			return "";
		}
		return url.replaceAll("(?i)(password|pwd)=([^&;]*)", "$1=****");
	}

	private static void appendMore(StringBuilder sb, int total, int shown) {
		if (total > shown) {
			sb.append("... 외 ").append(total - shown).append("건 (조건을 좁혀 다시 조회하세요)\n");
		}
	}

	private static String oneLine(String text) {
		return text == null ? "" : text.replace("\r", " ").replace("\n", " ").trim();
	}

	private static String nvl(String text) {
		return text == null ? "" : text;
	}

}

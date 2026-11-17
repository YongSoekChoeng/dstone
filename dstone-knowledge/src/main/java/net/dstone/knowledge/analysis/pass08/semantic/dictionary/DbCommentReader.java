package net.dstone.knowledge.analysis.pass08.semantic.dictionary;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.EncUtil;
import net.dstone.knowledge.api.dao.ProjectDbDao;
import net.dstone.knowledge.common.util.ErrorText;

/**
 * <pre>
 * 분석 대상 프로젝트가 쓰는 DB에서 테이블 / 컬럼의 정의와 코멘트(한글명)를 읽습니다.
 *
 * 있어도 되고 없어도 되는 기능입니다. 접속 정보가 없으면 아무 데도 접속하지 않고 NOT_CONFIGURED 를 돌려줍니다.
 * 그때는 DDL 파일, SQL 주석의 한글명을 씁니다.
 *
 * 접속 정보는 두 곳에서 찾습니다.
 *   1) 프로젝트 화면에서 등록한 것(analysis_project_db). 여러 개일 수 있고, 등록한 순서대로 찾습니다.
 *      비밀번호는 암호화돼 저장돼 있고 여기서만 풀어 씁니다.
 *   2) 1)이 하나도 없을 때만, 예전 방식인 설정 파일(conf/application.yml, 프로젝트 ID 마다):
 *        dstone.knowledge.dictionary.db.{프로젝트ID}.url       jdbc:mysql://... / jdbc:oracle:thin:@... / jdbc:postgresql://...
 *        dstone.knowledge.dictionary.db.{프로젝트ID}.username
 *        dstone.knowledge.dictionary.db.{프로젝트ID}.password  ENC(...) 로 적는다
 *        dstone.knowledge.dictionary.db.{프로젝트ID}.schema    (선택)
 *
 * 접속이 여러 개일 때 테이블 하나를 찾는 순서:
 *   - SCHEMA.TABLE 처럼 스키마가 붙어 있으면 그 스키마를 적어 둔 접속을 먼저 봅니다.
 *   - 그 밖에는 등록한 순서대로 보고, 처음 찾은 곳의 것을 씁니다(결과의 source 가 그 접속 이름).
 *
 * 분석 결과(DB)에 저장하지 않고, 화면에서 테이블 상세를 열 때 그 테이블 하나만 읽습니다.
 * 코멘트는 소스가 아니라 운영 DB의 것이라 리비전과 상관이 없고, 저장해 두면 DB에서 고친 코멘트가 다시 분석할 때까지 안 바뀌기 때문입니다.
 * JDBC 의 표준 메타데이터(getTables / getColumns 의 REMARKS)로 읽으므로 DB 종류마다 SQL을 따로 두지 않습니다.
 * 읽기만 하고(read-only 접속), 오래 기다리지 않습니다(접속 10초, 응답 30초).
 * 같은 테이블은 10분 동안 다시 읽지 않습니다. 실패도 1분 동안 기억해서, DB가 죽었을 때 상세를 열 때마다 기다리지 않게 합니다.
 * 접속 정보를 고치면 forget() 으로 그 프로젝트의 기억을 지웁니다.
 * </pre>
 */
@Component
public class DbCommentReader extends BaseObject {

	public static final String NOT_CONFIGURED = "NOT_CONFIGURED";
	public static final String OK = "OK";
	public static final String NOT_FOUND = "NOT_FOUND";
	public static final String FAILED = "FAILED";

	private static final String PREFIX = "dstone.knowledge.dictionary.db.";

	private static final long KEEP_MILLIS = 10 * 60 * 1000L;
	private static final long KEEP_FAILED_MILLIS = 60 * 1000L;

	/** 캐시가 이보다 커지면 비웁니다. 테이블 상세를 여는 만큼만 쌓이므로 거의 닿지 않습니다. */
	private static final int MAX_CACHE = 5000;

	@Autowired
	private ConfigProperty configProperty;

	@Autowired
	private ProjectDbDao projectDbDao;

	private final Map<String, Cached> cache = new ConcurrentHashMap<String, Cached>();

	/** 프로젝트 ID → 테이블 목록. 프로젝트 수만큼만 쌓인다 */
	private final Map<String, CachedList> listCache = new ConcurrentHashMap<String, CachedList>();

	/**
	 * <pre>
	 * DB에서 읽은 테이블 하나의 설명
	 * </pre>
	 */
	public static class Result {

		/** NOT_CONFIGURED / OK / NOT_FOUND / FAILED */
		public final String status;

		/** 실패했을 때 그 까닭 */
		public final String message;

		/** 테이블 코멘트. 없으면 null */
		public final String tableComment;

		/** 컬럼마다 {column, comment, dataType, ordinal}. DB에 정의된 순서 */
		public final List<Map<String, Object>> columns;

		/** 찾은 접속의 이름(OK 일 때). 접속이 여러 개일 때 어디서 찾았는지 알린다 */
		public final String source;

		Result(String status, String message, String tableComment, List<Map<String, Object>> columns) {
			this(status, message, tableComment, columns, null);
		}

		Result(String status, String message, String tableComment, List<Map<String, Object>> columns, String source) {
			this.status = status;
			this.message = message;
			this.tableComment = tableComment;
			this.columns = columns;
			this.source = source;
		}
	}

	/**
	 * <pre>
	 * DB에서 읽은 테이블 이름 목록
	 * </pre>
	 */
	public static class TableList {

		/** NOT_CONFIGURED / OK / FAILED */
		public final String status;

		/** 실패했을 때 그 까닭 */
		public final String message;

		/** 테이블 이름(대문자) */
		public final List<String> tables;

		TableList(String status, String message, List<String> tables) {
			this.status = status;
			this.message = message;
			this.tables = tables;
		}
	}

	/**
	 * <pre>
	 * 접속 하나. 비밀번호는 풀린 값이라 이 클래스 밖으로 내보내지 않습니다(로그, 응답 모두).
	 * </pre>
	 */
	public static class Target {
		final String name;
		final String url;
		final String username;
		final String password;
		final String schema;

		public Target(String name, String url, String username, String password, String schema) {
			this.name = name;
			this.url = url;
			this.username = username;
			this.password = password;
			this.schema = schema;
		}
	}

	/** 캐시에 넣어 둔 테이블 목록과 그 만료 시각 */
	private static class CachedList {
		final long expiresAt;
		final TableList result;

		CachedList(long expiresAt, TableList result) {
			this.expiresAt = expiresAt;
			this.result = result;
		}
	}

	/** 캐시에 넣어 둔 조회 결과와 그 만료 시각 */
	private static class Cached {
		final long expiresAt;
		final Result result;

		Cached(long expiresAt, Result result) {
			this.expiresAt = expiresAt;
			this.result = result;
		}
	}

	/**
	 * <pre>
	 * 대상 DB에서 테이블 하나의 코멘트와 컬럼을 읽습니다(접속 설정이 있을 때만, 결과는 잠시 캐시).
	 * </pre>
	 *
	 * @param projectId 분석 프로젝트 ID. 설정 키를 찾는 데 쓴다
	 * @param table 테이블 이름(대문자)
	 */
	public Result read(String projectId, String table) {
		List<Target> targets = targets(projectId);
		if (targets.isEmpty()) {
			return new Result(NOT_CONFIGURED, null, null, new ArrayList<Map<String, Object>>());
		}
		String key = projectId + "|" + table;
		Cached cached = cache.get(key);
		long now = System.currentTimeMillis();
		if (cached != null && cached.expiresAt > now) {
			return cached.result;
		}
		Result result = readFrom(ordered(targets, table), projectId, table);
		if (cache.size() > MAX_CACHE) {
			cache.clear();
		}
		cache.put(key, new Cached(now + (FAILED.equals(result.status) ? KEEP_FAILED_MILLIS : KEEP_MILLIS), result));
		return result;
	}

	/**
	 * <pre>
	 * 접속을 차례로 보며 테이블을 찾습니다. 처음 찾은 곳의 결과를 돌려줍니다.
	 * 어디서도 못 찾았으면 NOT_FOUND, 붙지도 못한 곳만 있었으면 FAILED(까닭을 이어 적는다).
	 * </pre>
	 */
	private Result readFrom(List<Target> targets, String projectId, String table) {
		String bare = table.indexOf('.') >= 0 ? table.substring(table.lastIndexOf('.') + 1) : table;
		StringBuilder failures = new StringBuilder();
		StringBuilder notFound = new StringBuilder();
		for (int i = 0; i < targets.size(); i++) {
			Target target = targets.get(i);
			try {
				Result one = load(target, bare);
				if (OK.equals(one.status)) {
					return one;
				}
				notFound.append(notFound.length() == 0 ? "" : ", ").append(target.name);
			} catch (Exception e) {
				// 비밀번호가 메시지에 섞여 나올 일은 없지만, 접속 이름과 주소 정도만 남긴다.
				warn("DB 코멘트를 읽지 못했습니다. projectId=" + projectId + ", table=" + table + ", 접속=" + target.name + ", url=" + target.url
						+ ", 까닭=" + ErrorText.summaryOf(e));
				failures.append(failures.length() == 0 ? "" : " / ").append(target.name).append(": ").append(ErrorText.summaryOf(e));
			}
		}
		if (notFound.length() == 0 && failures.length() > 0) {
			return new Result(FAILED, failures.toString(), null, new ArrayList<Map<String, Object>>());
		}
		String message = "DB에 이 테이블이 없습니다(찾아본 접속: " + notFound + ")." + (failures.length() > 0 ? " 붙지 못한 접속 - " + failures : "");
		return new Result(NOT_FOUND, message, null, new ArrayList<Map<String, Object>>());
	}

	/**
	 * <pre>
	 * 찾는 순서. SCHEMA.TABLE 처럼 스키마가 붙어 있으면 그 스키마를 적어 둔 접속을 앞으로 옮기고, 나머지는 등록한 순서 그대로.
	 * </pre>
	 */
	private List<Target> ordered(List<Target> targets, String table) {
		int dot = table.lastIndexOf('.');
		if (dot <= 0) {
			return targets;
		}
		String schema = table.substring(0, dot);
		List<Target> first = new ArrayList<Target>();
		List<Target> rest = new ArrayList<Target>();
		for (int i = 0; i < targets.size(); i++) {
			Target target = targets.get(i);
			if (target.schema != null && target.schema.equalsIgnoreCase(schema)) {
				first.add(target);
			} else {
				rest.add(target);
			}
		}
		first.addAll(rest);
		return first;
	}

	/**
	 * <pre>
	 * 대상 DB에 직접 붙어 테이블 하나의 코멘트와 컬럼(이름, 코멘트, 타입, 순서)을 읽습니다.
	 *
	 * 흐름:
	 *   1) 접속 정보의 계정으로 읽기 전용 연결을 연다(시간 제한 옵션 포함).
	 *   2) 스키마: 설정 값 → 없으면 연결의 기본 스키마
	 *   3) DB가 이름을 저장하는 대소문자부터 테이블을 찾고, 찾으면 컬럼 메타데이터를 읽는다.
	 *   4) 어느 쪽으로도 없으면 NOT_FOUND
	 * </pre>
	 */
	private Result load(Target target, String table) throws SQLException {
		Connection connection = open(target);
		try {
			DatabaseMetaData meta = connection.getMetaData();
			String catalog = connection.getCatalog();
			String schema = schemaFor(connection, target);
			// DB가 이름을 어떤 글자로 저장하는지에 따라 대문자 / 소문자로 찾는다(MySQL, PostgreSQL 은 보통 소문자).
			String[] names = meta.storesLowerCaseIdentifiers() ? new String[] { table.toLowerCase(Locale.ROOT), table }
					: new String[] { table, table.toLowerCase(Locale.ROOT) };
			for (int i = 0; i < names.length; i++) {
				String tableComment = null;
				boolean found = false;
				ResultSet tables = meta.getTables(catalog, schema, names[i], null);
				try {
					if (tables.next()) {
						found = true;
						tableComment = blankToNull(tables.getString("REMARKS"));
					}
				} finally {
					tables.close();
				}
				if (!found) {
					continue;
				}
				List<Map<String, Object>> columns = new ArrayList<Map<String, Object>>();
				ResultSet rs = meta.getColumns(catalog, schema, names[i], null);
				try {
					while (rs.next()) {
						Map<String, Object> column = new LinkedHashMap<String, Object>();
						column.put("column", rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
						column.put("comment", blankToNull(rs.getString("REMARKS")));
						column.put("dataType", typeOf(rs));
						column.put("ordinal", Integer.valueOf(rs.getInt("ORDINAL_POSITION")));
						columns.add(column);
					}
				} finally {
					rs.close();
				}
				return new Result(OK, null, tableComment, columns, target.name);
			}
			return new Result(NOT_FOUND, "DB에 이 테이블이 없습니다(스키마 " + schema + ").", null, new ArrayList<Map<String, Object>>());
		} finally {
			connection.close();
		}
	}

	/**
	 * <pre>
	 * 대상 DB 스키마에 있는 테이블 이름 전부(대문자). 미사용 분석이 "DB에는 있는데 아무 SQL도 안 쓰는 테이블" 을 찾을 때 씁니다.
	 * 접속 설정이 없으면 NOT_CONFIGURED 이고 아무 데도 접속하지 않습니다. 결과는 read() 와 같이 잠시 캐시합니다.
	 * 뷰는 넣지 않고 TABLE 만 셉니다.
	 * </pre>
	 *
	 * @return status / message 와, OK 일 때 tables(이름 목록)
	 */
	public TableList listTables(String projectId) {
		List<Target> targets = targets(projectId);
		if (targets.isEmpty()) {
			return new TableList(NOT_CONFIGURED, null, new ArrayList<String>());
		}
		long now = System.currentTimeMillis();
		CachedList cached = listCache.get(projectId);
		if (cached != null && cached.expiresAt > now) {
			return cached.result;
		}
		// 접속마다 읽어 합친다. 한 곳이라도 읽었으면 OK 이고, 못 읽은 곳은 message 에 남긴다.
		java.util.Set<String> tables = new java.util.TreeSet<String>();
		StringBuilder failures = new StringBuilder();
		int ok = 0;
		for (int i = 0; i < targets.size(); i++) {
			Target target = targets.get(i);
			try {
				tables.addAll(tableNames(target));
				ok++;
			} catch (Exception e) {
				warn("DB 테이블 목록을 읽지 못했습니다. projectId=" + projectId + ", 접속=" + target.name + ", url=" + target.url + ", 까닭=" + ErrorText.summaryOf(e));
				failures.append(failures.length() == 0 ? "" : " / ").append(target.name).append(": ").append(ErrorText.summaryOf(e));
			}
		}
		TableList result = ok > 0 ? new TableList(OK, failures.length() == 0 ? null : failures.toString(), new ArrayList<String>(tables))
				: new TableList(FAILED, failures.toString(), new ArrayList<String>());
		listCache.put(projectId, new CachedList(now + (FAILED.equals(result.status) ? KEEP_FAILED_MILLIS : KEEP_MILLIS), result));
		return result;
	}

	/** 접속 하나의 스키마에 있는 테이블 이름(대문자). 뷰는 넣지 않는다 */
	private List<String> tableNames(Target target) throws SQLException {
		Connection connection = open(target);
		try {
			List<String> tables = new ArrayList<String>();
			ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), schemaFor(connection, target), "%", new String[] { "TABLE" });
			try {
				while (rs.next()) {
					tables.add(rs.getString("TABLE_NAME").toUpperCase(Locale.ROOT));
				}
			} finally {
				rs.close();
			}
			return tables;
		} finally {
			connection.close();
		}
	}

	/**
	 * <pre>
	 * 접속 시험. 프로젝트 화면의 [접속 시험] 버튼이 부릅니다. 캐시를 쓰지 않고 그때그때 붙어 봅니다.
	 * 돌려주는 것: ok, message(실패 까닭), product(DB 이름 · 버전), schema(실제로 볼 스키마), tableCount(그 스키마의 테이블 수)
	 * 비밀번호는 돌려주지 않습니다.
	 * </pre>
	 */
	public Map<String, Object> test(Target target) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		long started = System.currentTimeMillis();
		try {
			Connection connection = open(target);
			try {
				DatabaseMetaData meta = connection.getMetaData();
				String schema = schemaFor(connection, target);
				int count = 0;
				ResultSet rs = meta.getTables(connection.getCatalog(), schema, "%", new String[] { "TABLE" });
				try {
					while (rs.next()) {
						count++;
					}
				} finally {
					rs.close();
				}
				result.put("ok", Boolean.TRUE);
				result.put("product", meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion());
				result.put("schema", schema);
				result.put("tableCount", Integer.valueOf(count));
			} finally {
				connection.close();
			}
		} catch (Exception e) {
			result.put("ok", Boolean.FALSE);
			result.put("message", ErrorText.summaryOf(e));
		}
		result.put("elapsedMillis", Long.valueOf(System.currentTimeMillis() - started));
		return result;
	}

	/** 프로젝트의 접속 정보를 고쳤을 때 부릅니다. 그 프로젝트에 대해 기억해 둔 결과를 지웁니다 */
	public void forget(String projectId) {
		listCache.remove(projectId);
		String prefix = projectId + "|";
		java.util.Iterator<String> keys = cache.keySet().iterator();
		while (keys.hasNext()) {
			if (keys.next().startsWith(prefix)) {
				keys.remove();
			}
		}
	}

	/**
	 * <pre>
	 * 이 프로젝트의 접속 목록. 화면에서 등록한 것이 있으면 그것을 순서대로, 없으면 설정 파일의 것 하나, 그것도 없으면 빈 목록.
	 * 저장된 비밀번호는 여기서 풉니다. 풀지 못하면(암호화 키가 바뀐 경우 등) 그 접속은 비밀번호 없이 시도합니다.
	 * </pre>
	 */
	List<Target> targets(String projectId) {
		List<Target> targets = new ArrayList<Target>();
		List<Map<String, Object>> rows = projectDbDao.selectList(projectId);
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			targets.add(new Target(String.valueOf(row.get("dbName")), (String) row.get("jdbcUrl"), blankToNull((String) row.get("username")),
					decrypt(projectId, (String) row.get("passwordEnc")), blankToNull((String) row.get("schemaName"))));
		}
		if (targets.isEmpty()) {
			String url = configured(projectId, "url");
			if (url != null) {
				targets.add(new Target("설정 파일", url, configured(projectId, "username"), configured(projectId, "password"), configured(projectId, "schema")));
			}
		}
		return targets;
	}

	/** 저장된 비밀번호를 풉니다. 비었으면 null */
	private String decrypt(String projectId, String encrypted) {
		if (blankToNull(encrypted) == null) {
			return null;
		}
		try {
			return EncUtil.decrypt(encrypted);
		} catch (RuntimeException e) {
			warn("저장된 DB 비밀번호를 풀지 못했습니다(암호화 키가 바뀌었을 수 있습니다). projectId=" + projectId);
			return null;
		}
	}

	/** 접속 정보의 계정으로 읽기 전용 연결을 엽니다(시간 제한 옵션 포함). */
	private Connection open(Target target) throws SQLException {
		Properties properties = new Properties();
		if (target.username != null) {
			properties.setProperty("user", target.username);
		}
		if (target.password != null) {
			properties.setProperty("password", target.password);
		}
		addDriverOptions(target.url, properties);
		Connection connection = DriverManager.getConnection(target.url, properties);
		try {
			connection.setReadOnly(true);
		} catch (SQLException e) {
			// 읽기 전용을 지원하지 않는 드라이버. 어차피 메타데이터만 읽는다.
		}
		return connection;
	}

	/** 테이블을 찾을 스키마: 접속 정보에 적은 값 → 없으면 연결의 기본 스키마 */
	private String schemaFor(Connection connection, Target target) {
		return target.schema != null ? target.schema : schemaOf(connection);
	}

	/** 접속 / 응답 시간 제한과, 코멘트를 돌려 달라는 옵션. 드라이버마다 이름과 단위가 다르다. */
	private void addDriverOptions(String url, Properties properties) {
		String lower = url.toLowerCase(Locale.ROOT);
		if (lower.indexOf(":mysql:") >= 0 || lower.indexOf(":mariadb:") >= 0) {
			properties.setProperty("connectTimeout", "10000");
			properties.setProperty("socketTimeout", "30000");
			// MySQL 드라이버는 이 옵션이 있어야 getTables 의 REMARKS 에 테이블 코멘트를 넣는다.
			properties.setProperty("useInformationSchema", "true");
		} else if (lower.indexOf(":postgresql:") >= 0) {
			properties.setProperty("connectTimeout", "10");
			properties.setProperty("socketTimeout", "30");
		} else if (lower.indexOf(":oracle:") >= 0) {
			properties.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
			properties.setProperty("oracle.jdbc.ReadTimeout", "30000");
			// Oracle 드라이버는 이 옵션이 있어야 REMARKS 에 코멘트를 넣는다.
			properties.setProperty("remarksReporting", "true");
		}
	}

	/** 연결의 기본 스키마. 드라이버가 알려 주지 않으면 null(모든 스키마에서 찾는다) */
	private String schemaOf(Connection connection) {
		try {
			return connection.getSchema();
		} catch (Throwable e) {
			// 오래된 드라이버는 getSchema() 가 없다. 모든 스키마에서 찾는다.
			return null;
		}
	}

	/** VARCHAR(20), NUMBER(10,2) 처럼 */
	private String typeOf(ResultSet rs) throws SQLException {
		String type = rs.getString("TYPE_NAME");
		int size = rs.getInt("COLUMN_SIZE");
		if (rs.wasNull() || size <= 0 || type == null || type.indexOf('(') >= 0) {
			return type;
		}
		String upper = type.toUpperCase(Locale.ROOT);
		if (upper.indexOf("CHAR") >= 0 || upper.indexOf("BINARY") >= 0) {
			return type + "(" + size + ")";
		}
		if (upper.indexOf("NUMBER") >= 0 || upper.indexOf("NUMERIC") >= 0 || upper.indexOf("DECIMAL") >= 0) {
			int digits = rs.getInt("DECIMAL_DIGITS");
			return type + "(" + size + (digits > 0 ? "," + digits : "") + ")";
		}
		return type;
	}

	/** 이 프로젝트의 DB 접속 설정 값 하나(비었으면 null) */
	private String configured(String projectId, String name) {
		return blankToNull(configProperty.getProperty(PREFIX + projectId + "." + name));
	}

	/** 앞뒤 공백을 떼고, 비었으면 null */
	private String blankToNull(String value) {
		return value == null || value.trim().length() == 0 ? null : value.trim();
	}

}

package net.dstone.knowledge.dictionary;

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
import net.dstone.knowledge.common.util.ErrorText;

/**
 * <pre>
 * 분석 대상 프로젝트가 쓰는 DB에서 테이블 / 컬럼 코멘트(한글명)를 읽습니다.
 *
 * 있어도 되고 없어도 되는 기능입니다. 대부분의 프로젝트는 DB 접속 정보를 받지 못하므로,
 * 설정이 없으면 아무 데도 접속하지 않고 NOT_CONFIGURED 를 돌려줍니다. 그때는 DDL 파일, SQL 주석의 한글명을 씁니다.
 *
 * 설정(conf/application.yml, 프로젝트 ID 마다):
 *   dstone.knowledge.dictionary.db.{프로젝트ID}.url       jdbc:mysql://... / jdbc:oracle:thin:@... / jdbc:postgresql://...
 *   dstone.knowledge.dictionary.db.{프로젝트ID}.username
 *   dstone.knowledge.dictionary.db.{프로젝트ID}.password  ENC(...) 로 적는다
 *   dstone.knowledge.dictionary.db.{프로젝트ID}.schema    (선택) 테이블이 있는 스키마. 없으면 접속한 사용자의 기본 스키마
 *
 * 분석 결과(DB)에 저장하지 않고, 화면에서 테이블 상세를 열 때 그 테이블 하나만 읽습니다.
 * 코멘트는 소스가 아니라 운영 DB의 것이라 리비전과 상관이 없고, 저장해 두면 DB에서 고친 코멘트가 다시 분석할 때까지 안 바뀌기 때문입니다.
 * JDBC 의 표준 메타데이터(getTables / getColumns 의 REMARKS)로 읽으므로 DB 종류마다 SQL을 따로 두지 않습니다.
 * 읽기만 하고(read-only 접속), 오래 기다리지 않습니다(접속 10초, 응답 30초).
 * 같은 테이블은 10분 동안 다시 읽지 않습니다. 실패도 1분 동안 기억해서, DB가 죽었을 때 상세를 열 때마다 기다리지 않게 합니다.
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

	private final Map<String, Cached> cache = new ConcurrentHashMap<String, Cached>();

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

		Result(String status, String message, String tableComment, List<Map<String, Object>> columns) {
			this.status = status;
			this.message = message;
			this.tableComment = tableComment;
			this.columns = columns;
		}
	}

	private static class Cached {
		final long expiresAt;
		final Result result;

		Cached(long expiresAt, Result result) {
			this.expiresAt = expiresAt;
			this.result = result;
		}
	}

	/**
	 * @param projectId 분석 프로젝트 ID. 설정 키를 찾는 데 쓴다
	 * @param table 테이블 이름(대문자)
	 */
	public Result read(String projectId, String table) {
		String url = configured(projectId, "url");
		if (url == null) {
			return new Result(NOT_CONFIGURED, null, null, new ArrayList<Map<String, Object>>());
		}
		String key = projectId + "|" + table;
		Cached cached = cache.get(key);
		long now = System.currentTimeMillis();
		if (cached != null && cached.expiresAt > now) {
			return cached.result;
		}
		Result result;
		try {
			result = load(url, projectId, table);
		} catch (Exception e) {
			// 비밀번호가 메시지에 섞여 나올 일은 없지만, 접속 주소 정도만 남긴다.
			warn("DB 코멘트를 읽지 못했습니다. projectId=" + projectId + ", table=" + table + ", url=" + url + ", 까닭=" + ErrorText.summaryOf(e));
			result = new Result(FAILED, ErrorText.summaryOf(e), null, new ArrayList<Map<String, Object>>());
		}
		if (cache.size() > MAX_CACHE) {
			cache.clear();
		}
		cache.put(key, new Cached(now + (FAILED.equals(result.status) ? KEEP_FAILED_MILLIS : KEEP_MILLIS), result));
		return result;
	}

	private Result load(String url, String projectId, String table) throws SQLException {
		Properties properties = new Properties();
		String username = configured(projectId, "username");
		String password = configured(projectId, "password");
		if (username != null) {
			properties.setProperty("user", username);
		}
		if (password != null) {
			properties.setProperty("password", password);
		}
		addDriverOptions(url, properties);

		Connection connection = DriverManager.getConnection(url, properties);
		try {
			try {
				connection.setReadOnly(true);
			} catch (SQLException e) {
				// 읽기 전용을 지원하지 않는 드라이버. 어차피 메타데이터만 읽는다.
			}
			DatabaseMetaData meta = connection.getMetaData();
			String catalog = connection.getCatalog();
			String schema = configured(projectId, "schema");
			if (schema == null) {
				schema = schemaOf(connection);
			}
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
				return new Result(OK, null, tableComment, columns);
			}
			return new Result(NOT_FOUND, "DB에 이 테이블이 없습니다(스키마 " + schema + ").", null, new ArrayList<Map<String, Object>>());
		} finally {
			connection.close();
		}
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

	private String configured(String projectId, String name) {
		return blankToNull(configProperty.getProperty(PREFIX + projectId + "." + name));
	}

	private String blankToNull(String value) {
		return value == null || value.trim().length() == 0 ? null : value.trim();
	}

}

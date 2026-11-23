package net.dstone.ai.tools.build.sandbox;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <pre>
 * 스키마 하나를 원래 Oracle에서 시험 PDB로 떠 옵니다(Data Pump의 네트워크 가져오기).
 *
 * - 원래 DB에는 앱 계정으로 읽기만 합니다. 파일도, 테이블도 만들지 않습니다.
 * - 가져오기도 시험 PDB의 같은 이름 계정(관리자 아님)으로 합니다. 관리자로 하면 Oracle이 보통 계정의 DB 링크를 거절합니다(ORA-39149).
 * - Data Pump는 실패가 섞여도 COMPLETED로 끝납니다. 그래서 테이블마다 행 수를 원본과 맞춰 봅니다.
 * </pre>
 */
final class OracleClone {

	private static final String LINK = "DSTONE_SBX_SRC";
	private static final String JOB = "DSTONE_SBX_IMP";
	private static final int IMPORT_MINUTES = 20;
	private static final int MAX_NOTES = 5;

	private static final String TABLES = "SELECT table_name FROM user_tables WHERE nested = 'NO' AND secondary = 'N' AND temporary = 'N'"
		+ " AND (iot_type IS NULL OR iot_type = 'IOT') AND table_name NOT LIKE 'BIN$%' AND table_name NOT LIKE '" + JOB + "%'"
		+ " AND table_name NOT LIKE 'MLOG$%' ORDER BY table_name";

	private OracleClone() {
	}

	/**
	 * @param from       원래 DB(그 스키마 계정)
	 * @param to         시험 PDB(같은 이름 계정)
	 * @param source     복제할 원본
	 * @param descriptor 시험 서버가 원래 DB로 갈 접속 서술자
	 * @param maxDbMb    크기 상한
	 * @return 사람에게 알릴 참고
	 */
	static List<String> copy(Connection from, Connection to, DatabaseSource source, String descriptor, int maxDbMb) throws SQLException, SandboxException {
		String owner = source.username();
		long megabytes = number(from, "SELECT NVL(CEIL(SUM(bytes) / 1048576), 0) FROM user_segments");
		if (megabytes > maxDbMb) {
			throw new SandboxException("스키마 " + owner + "가 " + megabytes + "MB라 복제하지 않습니다(설정 sandbox-max-db-mb " + maxDbMb + ").", false);
		}
		Map<String, Long> before = counts(from);
		List<String> notes = new ArrayList<String>();
		try (Statement statement = to.createStatement()) {
			statement.setEscapeProcessing(false);
			try {
				statement.execute("CREATE DATABASE LINK " + LINK + " CONNECT TO " + quoted(owner) + " IDENTIFIED BY " + quoted(source.password())
					+ " USING " + literal(descriptor));
			} catch (SQLException e) {
				// 문장에 비밀번호가 있어 원인 예외를 붙이지 않는다
				throw new SandboxException("시험 PDB에 원본 읽기용 링크를 만들지 못했습니다(ORA-" + e.getErrorCode() + ").", false);
			}
			try (ResultSet rs = statement.executeQuery("SELECT 1 FROM dual@" + LINK)) {
				rs.next();
			} catch (SQLException e) {
				throw new SandboxException("시험 전용 Oracle 서버에서 원래 DB(" + source.hosts().get(0).host() + ":" + source.hosts().get(0).port()
					+ ")에 닿지 못합니다. 네트워크와 주소(localhost는 시험 서버 자신을 뜻함)를 확인하십시오: " + brief(e), false);
			}
			to.commit();
			notes.addAll(load(to, owner, tablespaces(from)));
			to.commit();
			statement.execute("DROP DATABASE LINK " + LINK);
		}
		Map<String, Long> after = counts(to);
		List<String> wrong = new ArrayList<String>();
		for (Map.Entry<String, Long> table : before.entrySet()) {
			Long copied = after.get(table.getKey());
			if (!table.getValue().equals(copied)) {
				wrong.add(table.getKey() + "(원본 " + table.getValue() + "행, 복제 " + (copied == null ? "없음" : copied + "행") + ")");
			}
		}
		if (!wrong.isEmpty()) {
			throw new SandboxException("스키마 " + owner + "를 온전히 복제하지 못했습니다: " + String.join(", ", wrong.subList(0, Math.min(MAX_NOTES, wrong.size())))
				+ ". LONG 컬럼, 문자셋 차이(한글 길이), 시험 서버에 없는 기능, 복제 중 원본 변경을 확인하십시오."
				+ (notes.isEmpty() ? "" : " Data Pump: " + notes.get(0)), false);
		}
		return notes;
	}

	private static List<String> load(Connection to, String owner, List<String> tablespaces) throws SQLException, SandboxException {
		StringBuilder block = new StringBuilder();
		block.append("DECLARE h NUMBER; st VARCHAR2(30) := 'UNDEFINED'; s ku$_Status; i NUMBER; errs VARCHAR2(8000); hours NUMBER := DBMS_UTILITY.GET_TIME; BEGIN ");
		block.append("h := DBMS_DATAPUMP.OPEN('IMPORT', 'SCHEMA', '").append(LINK).append("', '").append(JOB).append("'); ");
		block.append("DBMS_DATAPUMP.METADATA_FILTER(h, 'SCHEMA_EXPR', ").append(literal("IN (" + literal(owner) + ")")).append("); ");
		// DB 링크와 예약 작업은 시험 서버 밖으로 나가거나 저절로 돈다
		block.append("DBMS_DATAPUMP.METADATA_FILTER(h, 'EXCLUDE_PATH_EXPR', ").append(literal("IN ('DB_LINK','JOB','PROCOBJ','STATISTICS','USER')")).append("); ");
		block.append("DBMS_DATAPUMP.METADATA_TRANSFORM(h, 'SEGMENT_ATTRIBUTES', 0); ");
		for (String tablespace : tablespaces) {
			if (!SandboxOracle.TABLESPACE.equals(tablespace)) {
				block.append("DBMS_DATAPUMP.METADATA_REMAP(h, 'REMAP_TABLESPACE', ").append(literal(tablespace)).append(", '").append(SandboxOracle.TABLESPACE).append("'); ");
			}
		}
		block.append("DBMS_DATAPUMP.START_JOB(h); ");
		block.append("WHILE st NOT IN ('COMPLETED', 'STOPPED') LOOP ");
		block.append("DBMS_DATAPUMP.GET_STATUS(h, DBMS_DATAPUMP.KU$_STATUS_JOB_ERROR + DBMS_DATAPUMP.KU$_STATUS_JOB_STATUS, 5, st, s); ");
		block.append("IF BITAND(s.mask, DBMS_DATAPUMP.KU$_STATUS_JOB_ERROR) <> 0 AND s.error IS NOT NULL THEN i := s.error.FIRST; ");
		block.append("WHILE i IS NOT NULL LOOP IF NVL(LENGTH(errs), 0) < 2500 THEN errs := errs || SUBSTR(s.error(i).LogText, 1, 400) || CHR(10); END IF; i := s.error.NEXT(i); END LOOP; END IF; ");
		block.append("IF DBMS_UTILITY.GET_TIME - hours > ").append(IMPORT_MINUTES * 6000).append(" THEN DBMS_DATAPUMP.STOP_JOB(h, 1, 0); st := 'TIMEOUT'; EXIT; END IF; ");
		block.append("END LOOP; ");
		block.append("IF st <> 'TIMEOUT' THEN BEGIN DBMS_DATAPUMP.DETACH(h); EXCEPTION WHEN OTHERS THEN NULL; END; END IF; ");
		block.append("? := st; ? := errs; END;");
		try (CallableStatement call = to.prepareCall(block.toString())) {
			call.registerOutParameter(1, Types.VARCHAR);
			call.registerOutParameter(2, Types.VARCHAR);
			call.execute();
			String errors = call.getString(2);
			if (!"COMPLETED".equals(call.getString(1))) {
				throw new SandboxException("스키마 " + owner + "의 복제가 끝나지 않았습니다(" + call.getString(1) + "). " + (errors == null ? "" : errors.trim()), false);
			}
			List<String> notes = new ArrayList<String>();
			if (errors != null) {
				for (String line : errors.split("\n")) {
					if (!line.isBlank() && notes.size() < MAX_NOTES) {
						notes.add("복제 주의(" + owner + "): " + line.trim());
					}
				}
			}
			return notes;
		} catch (SQLException e) {
			throw new SandboxException("스키마 " + owner + "를 Data Pump로 가져오지 못했습니다. 원래 DB와 시험 서버의 버전이 DB 링크로 이어지는 조합인지 확인하십시오: " + brief(e), false);
		}
	}

	private static List<String> tablespaces(Connection from) throws SQLException {
		List<String> result = new ArrayList<String>();
		try (Statement statement = from.createStatement(); ResultSet rs = statement.executeQuery("SELECT tablespace_name FROM user_tablespaces")) {
			while (rs.next()) {
				result.add(rs.getString(1));
			}
		}
		return result;
	}

	private static Map<String, Long> counts(Connection connection) throws SQLException {
		Map<String, Long> result = new LinkedHashMap<String, Long>();
		for (String table : strings(connection, TABLES)) {
			result.put(table, number(connection, "SELECT COUNT(*) FROM " + quoted(table)));
		}
		return result;
	}

	/**
	 * <pre>
	 * 복제 데이터 속 원본 폴더 경로를 사본 경로로 바꿉니다. 덤프 파일이 없으니 행을 찾아 고칩니다.
	 * 고치는 테이블의 트리거는 그동안 끕니다. 복제일 뿐인데 이력 행 같은 것이 생기면 안 되기 때문입니다.
	 * </pre>
	 *
	 * @param to    시험 PDB(그 스키마 계정)
	 * @param paths 경로 규칙
	 * @return 고친 칸 수
	 */
	static int shiftPaths(Connection to, SandboxPaths paths) throws SQLException {
		int changed = 0;
		for (String table : strings(to, TABLES)) {
			List<String> columns = new ArrayList<String>();
			try (PreparedStatement select = to.prepareStatement("SELECT column_name FROM user_tab_cols WHERE table_name = ? AND virtual_column = 'NO'"
				+ " AND hidden_column = 'NO' AND data_type IN ('VARCHAR2', 'NVARCHAR2', 'CHAR', 'NCHAR', 'CLOB', 'NCLOB')")) {
				select.setString(1, table);
				try (ResultSet rs = select.executeQuery()) {
					while (rs.next()) {
						columns.add(rs.getString(1));
					}
				}
			}
			List<String> triggers = null;
			for (String column : columns) {
				Map<String, String> updates = new LinkedHashMap<String, String>();
				for (String needle : paths.needles()) {
					try (PreparedStatement select = to.prepareStatement("SELECT ROWIDTOCHAR(ROWID), " + quoted(column) + " FROM " + quoted(table)
						+ " WHERE INSTR(LOWER(" + quoted(column) + "), LOWER(?)) > 0")) {
						select.setString(1, needle);
						try (ResultSet rs = select.executeQuery()) {
							while (rs.next()) {
								String value = rs.getString(2);
								String shifted = paths.shift(value);
								if (!shifted.equals(value)) {
									updates.put(rs.getString(1), shifted);
								}
							}
						}
					}
				}
				if (updates.isEmpty()) {
					continue;
				}
				if (triggers == null) {
					triggers = strings(to, "SELECT trigger_name FROM user_triggers WHERE status = 'ENABLED' AND table_name = " + literal(table));
					switchTriggers(to, triggers, "DISABLE");
				}
				try (PreparedStatement update = to.prepareStatement("UPDATE " + quoted(table) + " SET " + quoted(column) + " = ? WHERE ROWID = CHARTOROWID(?)")) {
					for (Map.Entry<String, String> row : updates.entrySet()) {
						update.setString(1, row.getValue());
						update.setString(2, row.getKey());
						changed += update.executeUpdate();
					}
				}
				to.commit();
			}
			if (triggers != null) {
				switchTriggers(to, triggers, "ENABLE");
			}
		}
		return changed;
	}

	private static void switchTriggers(Connection to, List<String> triggers, String action) throws SQLException {
		try (Statement statement = to.createStatement()) {
			for (String trigger : triggers) {
				statement.execute("ALTER TRIGGER " + quoted(trigger) + " " + action);
			}
		}
	}

	static List<String> strings(Connection connection, String sql) throws SQLException {
		List<String> result = new ArrayList<String>();
		try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
			while (rs.next()) {
				result.add(rs.getString(1));
			}
		}
		return result;
	}

	private static long number(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	static String brief(SQLException e) {
		String message = String.valueOf(e.getMessage()).replaceAll("\\s+", " ").trim();
		return message.length() > 300 ? message.substring(0, 300) : message;
	}

	static String quoted(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	static String literal(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

}

package net.dstone.ai.tools.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.project.ProjectStore;
import net.dstone.ai.tools.build.sandbox.SandboxDdl;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 설계의 DDL을 사람이 개발 DB에 반영했는지 확인합니다(TOOL step 전용). 설계: docs/temp/dstone-ai-테스트관련재설계/01.설계.md 7.3절.
 * 테스트 서버는 DDL을 실행하지 않으므로, 새 테이블·컬럼이 필요한 개발은 사람이 먼저 반영해야 테스트할 수 있습니다.
 *
 * DDL 문서(.sql 또는 ```sql 블록이 든 .md)를 SandboxDdl 규칙으로 읽어 CREATE TABLE과 ALTER TABLE … ADD의 대상,
 * 그리고 INSERT INTO의 초기 데이터를 봅니다.
 *   - 테이블·컬럼: 프로젝트 관리의 첫째 DB에 JDBC 메타데이터로 있는지 봅니다.
 *   - 초기 데이터: 컬럼 목록이 있는 INSERT는 행마다 첫 컬럼 값(문자열·숫자)으로 그 행이 있는지 SELECT COUNT(*)로 봅니다.
 *     그렇게 볼 수 없는 INSERT(컬럼 목록 없음, 첫 값이 함수 등)는 테이블에 행이 하나라도 있는지만 봅니다.
 *     테이블은 있는데 데이터가 없으면 화면 목록이 비어 검증이 아무것도 볼 수 없습니다(실행 0fb4c669의 회의실 0건).
 * 확인은 읽기만 합니다.
 * 결과: {route: none(볼 DDL·데이터 없음) | ok(모두 있음) | missing(없는 것이 있음), missing: [...], checked: [...],
 *        missingRows: [...], checkedRows: 건수, message}
 * </pre>
 */
@AiTool(stepOnly = true)
public class DdlCheckTool extends BaseObject {

	private static final Pattern CREATE_TABLE = Pattern.compile("(?is)^\\s*CREATE\\s+(?:GLOBAL\\s+TEMPORARY\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([`\"\\w.$]+)");
	private static final Pattern ALTER_ADD = Pattern.compile("(?is)^\\s*ALTER\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?([`\"\\w.$]+)\\s+ADD\\s+(?:COLUMN\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?\\(?\\s*([`\"\\w$]+)");
	private static final Pattern INSERT_INTO = Pattern.compile("(?is)^\\s*INSERT\\s+INTO\\s+([`\"\\w.$]+)\\s*(?:\\(([^)]*)\\))?\\s*VALUES\\s*(.+)$");
	private static final Pattern IDENTIFIER = Pattern.compile("[\\w$]+");
	private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
	/** 문서에 INSERT가 아주 많아도 확인은 이만큼만 합니다(나머지는 테이블에 행이 있는지만 봄). */
	private static final int MAX_ROW_CHECKS = 200;
	private static final java.util.Set<String> NOT_COLUMN = java.util.Set.of("CONSTRAINT", "PRIMARY", "UNIQUE", "INDEX", "KEY", "FOREIGN", "CHECK", "PARTITION");

	@Autowired
	private ProjectStore projectStore;

	@Tool(description = "DDL 문서의 CREATE TABLE·ALTER TABLE ADD 대상이 프로젝트의 개발 DB에 실제로 있는지 확인한다. "
		+ "{route: none | ok | missing, missing, checked, message}")
	public Map<String, Object> checkDdlApplied(@ToolParam(description = "프로젝트ID") String projectId,
		@ToolParam(description = "DDL 문서(.sql 또는 .md)의 절대경로") String ddlFile) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		List<String> missing = new ArrayList<String>();
		List<String> checked = new ArrayList<String>();
		List<String> missingRows = new ArrayList<String>();
		result.put("missing", missing);
		result.put("checked", checked);
		result.put("missingRows", missingRows);
		result.put("checkedRows", Integer.valueOf(0));
		File file = ddlFile == null ? null : new File(ddlFile.trim());
		if (file == null || !file.isFile()) {
			result.put("route", "none");
			result.put("message", "DDL 문서가 없습니다.");
			return result;
		}
		Map<String, String> db = null;
		if (projectId != null) {
			for (Map.Entry<String, Map<String, String>> entry : this.projectStore.connections().entrySet()) {
				if (entry.getKey().startsWith(projectId.trim() + ".")) {
					db = entry.getValue();
					break;
				}
			}
		}
		String text;
		try {
			text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			result.put("route", "none");
			result.put("message", "DDL 문서를 읽지 못했습니다: " + e.getMessage());
			return result;
		}
		String url = db == null ? "" : String.valueOf(db.get("url"));
		SandboxDdl.Dialect dialect = url.contains(":oracle:") ? SandboxDdl.Dialect.ORACLE : url.contains(":postgresql:") ? SandboxDdl.Dialect.POSTGRESQL : SandboxDdl.Dialect.MYSQL;
		SandboxDdl.Script script = SandboxDdl.parse(file.getName(), text, dialect);
		List<String[]> targets = new ArrayList<String[]>();
		Map<String, List<Map<String, Object>>> seeds = new LinkedHashMap<String, List<Map<String, Object>>>();
		for (String statement : script.statements()) {
			Matcher insert = INSERT_INTO.matcher(statement);
			if (insert.find()) {
				String table = name(insert.group(1));
				if (!seeds.containsKey(table)) {
					seeds.put(table, new ArrayList<Map<String, Object>>());
				}
				seeds.get(table).addAll(seedRows(insert.group(2), insert.group(3)));
				continue;
			}
			Matcher create = CREATE_TABLE.matcher(statement);
			if (create.find()) {
				targets.add(new String[] { name(create.group(1)), null });
				continue;
			}
			Matcher add = ALTER_ADD.matcher(statement);
			if (add.find() && !NOT_COLUMN.contains(add.group(2).toUpperCase(Locale.ROOT))) {
				targets.add(new String[] { name(add.group(1)), name(add.group(2)) });
			}
		}
		if (targets.isEmpty() && seeds.isEmpty()) {
			result.put("route", "none");
			result.put("message", "새로 만들거나 더하는 테이블·컬럼과 초기 데이터가 없습니다.");
			return result;
		}
		if (db == null) {
			result.put("route", "missing");
			result.put("message", "프로젝트 관리에 이 프로젝트의 DB가 없어 DDL 반영을 확인하지 못했습니다.");
			return result;
		}
		try (Connection connection = DriverManager.getConnection(url, db.get("username"), db.get("password"))) {
			DatabaseMetaData meta = connection.getMetaData();
			String catalog = connection.getCatalog();
			String schema = db.get("schema");
			for (String[] target : targets) {
				String label = target[1] == null ? target[0] : target[0] + "." + target[1];
				checked.add(label);
				if (!exists(meta, catalog, schema, target[0], target[1])) {
					missing.add(label);
				}
			}
			result.put("checkedRows", Integer.valueOf(this.checkSeeds(connection, schema, seeds, missing, missingRows)));
		} catch (SQLException e) {
			result.put("route", "missing");
			result.put("message", "개발 DB에 접속하지 못해 DDL 반영을 확인하지 못했습니다: " + e.getMessage());
			return result;
		}
		boolean ok = missing.isEmpty() && missingRows.isEmpty();
		result.put("route", ok ? "ok" : "missing");
		if (ok) {
			result.put("message", "DDL의 테이블·컬럼과 초기 데이터가 개발 DB에 모두 있습니다"
				+ (checked.isEmpty() ? "" : ": " + String.join(", ", checked)) + (seeds.isEmpty() ? "" : " (초기 데이터 " + result.get("checkedRows") + "건 확인)"));
		} else {
			StringBuilder message = new StringBuilder();
			if (!missing.isEmpty()) {
				message.append("개발 DB에 아직 없는 테이블·컬럼: ").append(String.join(", ", missing)).append(". ");
			}
			if (!missingRows.isEmpty()) {
				message.append("개발 DB에 아직 없는 초기 데이터(INSERT): ").append(String.join(", ", missingRows)).append(". ");
			}
			message.append("사람이 DDL 문서의 DDL과 INSERT를 개발 DB에 반영한 뒤 테스트합니다. 데이터가 없으면 화면 목록이 비어 검증할 수 없습니다.");
			result.put("message", message.toString());
		}
		return result;
	}

	/**
	 * <pre>
	 * INSERT 한 문장에서 확인할 행들을 뽑습니다. 행마다 {컬럼: 값}이고, 값은 문자열이면 String, 숫자면 BigDecimal입니다.
	 * NULL, 함수(NOW() 등), 식은 비교할 수 없어 뺍니다.
	 * 컬럼 목록이 없거나 VALUES 모양이 맞지 않으면 빈 맵 하나를 돌려줍니다(테이블에 행이 있는지만 봄).
	 * </pre>
	 */
	private static List<Map<String, Object>> seedRows(String columns, String values) {
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		List<String> names = new ArrayList<String>();
		if (columns != null) {
			for (String column : columns.split(",")) {
				String candidate = name(column.trim());
				names.add(IDENTIFIER.matcher(candidate).matches() ? candidate : null);
			}
		}
		List<List<String>> tuples = tuples(values);
		if (names.isEmpty() || tuples.isEmpty()) {
			rows.add(new LinkedHashMap<String, Object>());
			return rows;
		}
		for (List<String> tuple : tuples) {
			Map<String, Object> row = new LinkedHashMap<String, Object>();
			for (int i = 0; i < tuple.size() && i < names.size(); i++) {
				String value = tuple.get(i).trim();
				if (names.get(i) == null) {
					continue;
				}
				if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
					row.put(names.get(i), value.substring(1, value.length() - 1).replace("''", "'"));
				} else if (NUMBER.matcher(value).matches()) {
					row.put(names.get(i), new java.math.BigDecimal(value));
				}
			}
			rows.add(row);
		}
		return rows;
	}

	/**
	 * <pre>
	 * VALUES 뒤의 글("(a, b), (c, d)")을 행마다 값 글자 목록으로 나눕니다.
	 * 따옴표 안의 쉼표·괄호와 함수 괄호(NOW())는 값 안의 글자로 둡니다. 모양이 맞지 않으면 빈 목록입니다.
	 * </pre>
	 */
	private static List<List<String>> tuples(String values) {
		List<List<String>> tuples = new ArrayList<List<String>>();
		List<String> current = null;
		StringBuilder value = new StringBuilder();
		int depth = 0;
		boolean quoted = false;
		for (int i = 0; i < values.length(); i++) {
			char ch = values.charAt(i);
			if (quoted) {
				value.append(ch);
				if (ch == '\'') {
					if (i + 1 < values.length() && values.charAt(i + 1) == '\'') {
						value.append('\'');
						i++;
					} else {
						quoted = false;
					}
				}
				continue;
			}
			if (ch == '\'') {
				quoted = true;
				value.append(ch);
			} else if (ch == '(') {
				depth++;
				if (depth == 1) {
					current = new ArrayList<String>();
					value.setLength(0);
				} else {
					value.append(ch);
				}
			} else if (ch == ')') {
				depth--;
				if (depth == 0 && current != null) {
					current.add(value.toString());
					tuples.add(current);
					current = null;
				} else if (depth > 0) {
					value.append(ch);
				} else {
					return new ArrayList<List<String>>();
				}
			} else if (ch == ',' && depth == 1) {
				current.add(value.toString());
				value.setLength(0);
			} else if (depth >= 1) {
				value.append(ch);
			}
		}
		return depth == 0 && !quoted ? tuples : new ArrayList<List<String>>();
	}

	/**
	 * <pre>
	 * 초기 데이터 행이 개발 DB에 있는지 봅니다. 없는 것은 missingRows에 "테이블(컬럼=값, …)"이나 "테이블(행 없음)"으로 적습니다.
	 * 행은 기본키 컬럼으로 찾습니다. 기본키를 모르거나 기본키 값이 INSERT에 리터럴로 없으면 리터럴 값이 있는 컬럼 전부로 찾습니다.
	 * 테이블 자체가 없으면(missing에 있으면) 그 테이블의 행은 보지 않습니다. 이미 테이블이 없다고 알렸기 때문입니다.
	 * 한 행을 조회하다 오류가 나면(값과 컬럼 타입이 안 맞는 등) 그 테이블에 행이 있는지만 봅니다.
	 * </pre>
	 *
	 * @param seeds {테이블: 행 목록}
	 * @return 확인한 행 수
	 */
	private int checkSeeds(Connection connection, String schema, Map<String, List<Map<String, Object>>> seeds, List<String> missing, List<String> missingRows) throws SQLException {
		String prefix = schema == null || schema.isBlank() || !IDENTIFIER.matcher(schema).matches() ? "" : schema + ".";
		DatabaseMetaData meta = connection.getMetaData();
		int checkedRows = 0;
		for (Map.Entry<String, List<Map<String, Object>>> entry : seeds.entrySet()) {
			String table = entry.getKey();
			if (missing.contains(table) || !IDENTIFIER.matcher(table).matches()) {
				continue;
			}
			List<String> keys = primaryKeys(meta, connection.getCatalog(), schema, table);
			boolean tableChecked = false;
			for (Map<String, Object> row : entry.getValue()) {
				Map<String, Object> where = new LinkedHashMap<String, Object>();
				for (String key : keys) {
					for (Map.Entry<String, Object> column : row.entrySet()) {
						if (column.getKey().equalsIgnoreCase(key)) {
							where.put(column.getKey(), column.getValue());
						}
					}
				}
				if (where.isEmpty() || where.size() < keys.size()) {
					where = row;
				}
				if (!where.isEmpty() && checkedRows < MAX_ROW_CHECKS) {
					Boolean found = this.rowExists(connection, prefix + table, where);
					if (found != null) {
						checkedRows++;
						if (!found.booleanValue()) {
							missingRows.add(table + "(" + describe(where) + ")");
						}
						continue;
					}
				}
				if (!tableChecked) {
					tableChecked = true;
					Boolean any = this.rowExists(connection, prefix + table, new LinkedHashMap<String, Object>());
					checkedRows++;
					if (any != null && !any.booleanValue()) {
						missingRows.add(table + "(행 없음)");
					}
				}
			}
		}
		return checkedRows;
	}

	/** 테이블의 기본키 컬럼입니다. 모르면 빈 목록입니다. 이름은 그대로, 대문자, 소문자로 찾습니다. */
	private static List<String> primaryKeys(DatabaseMetaData meta, String catalog, String schema, String table) {
		List<String> keys = new ArrayList<String>();
		String[] schemas = schema == null || schema.isBlank() ? new String[] { null } : new String[] { schema, schema.toUpperCase(Locale.ROOT), null };
		for (String s : schemas) {
			for (String t : new String[] { table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT) }) {
				try (ResultSet rs = meta.getPrimaryKeys(catalog, s, t)) {
					while (rs.next()) {
						keys.add(rs.getString("COLUMN_NAME"));
					}
				} catch (SQLException e) {
					return keys;
				}
				if (!keys.isEmpty()) {
					return keys;
				}
			}
		}
		return keys;
	}

	/** "컬럼=값, 컬럼=값" 모양입니다. 사람이 어떤 행인지 알아볼 수 있게 씁니다. */
	private static String describe(Map<String, Object> where) {
		StringBuilder text = new StringBuilder();
		for (Map.Entry<String, Object> column : where.entrySet()) {
			if (text.length() > 0) {
				text.append(", ");
			}
			text.append(column.getKey()).append('=').append(column.getValue());
		}
		return text.toString();
	}

	/** 조건에 맞는 행이 있으면 true, 없으면 false, 조회하지 못하면 null입니다. 조건이 비면 테이블에 행이 하나라도 있는지 봅니다. */
	private Boolean rowExists(Connection connection, String table, Map<String, Object> where) {
		StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table);
		List<Object> params = new ArrayList<Object>();
		for (Map.Entry<String, Object> column : where.entrySet()) {
			sql.append(params.isEmpty() ? " WHERE " : " AND ").append(column.getKey()).append(" = ?");
			params.add(column.getValue());
		}
		try (java.sql.PreparedStatement statement = connection.prepareStatement(sql.toString())) {
			for (int i = 0; i < params.size(); i++) {
				statement.setObject(i + 1, params.get(i));
			}
			try (ResultSet rs = statement.executeQuery()) {
				return Boolean.valueOf(rs.next() && rs.getLong(1) > 0);
			}
		} catch (SQLException e) {
			this.warn("초기 데이터 확인 조회에 실패했습니다(" + sql + "): " + e.getMessage());
			return null;
		}
	}

	/** 테이블(컬럼이 null이면) 또는 컬럼이 있는지 봅니다. 이름은 그대로, 대문자, 소문자로 찾습니다. */
	private static boolean exists(DatabaseMetaData meta, String catalog, String schema, String table, String column) throws SQLException {
		String[] tables = { table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT) };
		String[] schemas = schema == null || schema.isBlank() ? new String[] { null } : new String[] { schema, schema.toUpperCase(Locale.ROOT), null };
		for (String s : schemas) {
			for (String t : tables) {
				if (column == null) {
					try (ResultSet rs = meta.getTables(catalog, s, t, null)) {
						if (rs.next()) {
							return true;
						}
					}
				} else {
					for (String c : new String[] { column, column.toUpperCase(Locale.ROOT), column.toLowerCase(Locale.ROOT) }) {
						try (ResultSet rs = meta.getColumns(catalog, s, t, c)) {
							if (rs.next()) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	/** 따옴표와 스키마 접두를 뗀 이름입니다. */
	private static String name(String raw) {
		String value = raw.replace("`", "").replace("\"", "");
		int dot = value.lastIndexOf('.');
		return dot >= 0 ? value.substring(dot + 1) : value;
	}

}

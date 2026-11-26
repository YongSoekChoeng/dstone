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
 * DDL 문서(.sql 또는 ```sql 블록이 든 .md)를 SandboxDdl 규칙으로 읽어 CREATE TABLE과 ALTER TABLE … ADD의 대상만 봅니다.
 * 확인은 프로젝트 관리의 첫째 DB에 JDBC 메타데이터로 합니다(읽기만).
 * 결과: {route: none(실행할 DDL 없음) | ok(모두 있음) | missing(없는 것이 있음), missing: [...], checked: [...], message}
 * </pre>
 */
@AiTool(stepOnly = true)
public class DdlCheckTool extends BaseObject {

	private static final Pattern CREATE_TABLE = Pattern.compile("(?is)^\\s*CREATE\\s+(?:GLOBAL\\s+TEMPORARY\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([`\"\\w.$]+)");
	private static final Pattern ALTER_ADD = Pattern.compile("(?is)^\\s*ALTER\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?([`\"\\w.$]+)\\s+ADD\\s+(?:COLUMN\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?\\(?\\s*([`\"\\w$]+)");
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
		result.put("missing", missing);
		result.put("checked", checked);
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
		for (String statement : script.statements()) {
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
		if (targets.isEmpty()) {
			result.put("route", "none");
			result.put("message", "새로 만들거나 더하는 테이블·컬럼이 없습니다.");
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
		} catch (SQLException e) {
			result.put("route", "missing");
			result.put("message", "개발 DB에 접속하지 못해 DDL 반영을 확인하지 못했습니다: " + e.getMessage());
			return result;
		}
		result.put("route", missing.isEmpty() ? "ok" : "missing");
		result.put("message", missing.isEmpty() ? "DDL의 테이블·컬럼이 개발 DB에 모두 있습니다: " + String.join(", ", checked)
			: "개발 DB에 아직 없는 테이블·컬럼: " + String.join(", ", missing) + ". 사람이 DDL을 개발 DB에 반영한 뒤 테스트합니다.");
		return result;
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

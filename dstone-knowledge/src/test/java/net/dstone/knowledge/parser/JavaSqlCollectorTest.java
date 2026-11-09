package net.dstone.knowledge.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.knowledge.parser.model.FileDeclarations;

/**
 * <pre>
 * Java 문자열 SQL 을 찾는 일(JavaSqlCollector)과, 그것이 DECLARE 결과(analysis_mapper 의 JAVA 행)로 나오는 모양을 확인합니다.
 * </pre>
 */
public class JavaSqlCollectorTest {

	private final JavaSourceParser parser = new JavaSourceParser();

	private List<Map<String, Object>> sqlOf(String source) {
		JavaSourceParser.Result parsed = parser.parse(source, null);
		assertTrue(parsed.isSuccessful(), String.valueOf(parsed.error));
		FileDeclarations declarations = new DeclarationCollector("test", 1L, 7L, "src").collect(parsed.unit);
		return declarations.getSqlStatements();
	}

	private Map<String, Object> byName(List<Map<String, Object>> rows, String statementId) {
		for (int i = 0; i < rows.size(); i++) {
			if (statementId.equals(rows.get(i).get("statementId"))) {
				return rows.get(i);
			}
		}
		throw new AssertionError("statement 가 없습니다: " + statementId + " / " + names(rows));
	}

	private List<String> names(List<Map<String, Object>> rows) {
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < rows.size(); i++) {
			names.add((String) rows.get(i).get("statementId"));
		}
		return names;
	}

	@Test
	public void 이어_붙인_문자열과_지역_변수를_따라간다() {
		List<Map<String, Object>> rows = sqlOf("package a;\n"
				+ "class OrderDao {\n"
				+ "  void list(java.sql.Connection con, String name) throws Exception {\n"
				+ "    String sql = \"SELECT O.ID, O.NAME \"\n"
				+ "        + \"FROM TB_ORDER O \";\n"
				+ "    if (name != null) sql += \"WHERE O.NAME = '\" + name + \"'\";\n"
				+ "    con.prepareStatement(sql);\n"
				+ "  }\n"
				+ "}\n");
		assertEquals(1, rows.size());
		Map<String, Object> row = rows.get(0);
		assertEquals("JAVA", row.get("mapperType"));
		assertEquals("a.OrderDao", row.get("namespace"));
		assertEquals("list", row.get("statementId"));
		assertEquals("SELECT", row.get("statementType"));
		assertEquals("SELECT O.ID, O.NAME FROM TB_ORDER O WHERE O.NAME = '?'", row.get("sqlBody"));
		assertEquals(Integer.valueOf(4), row.get("lineStart"));
		String properties = (String) row.get("propertiesJson");
		assertTrue(properties.contains("\"call\":\"prepareStatement\""), properties);
		assertTrue(properties.contains("\"dynamic\":true"), properties);
	}

	@Test
	public void StringBuilder_의_append_사슬을_따라간다() {
		List<Map<String, Object>> rows = sqlOf("package a;\n"
				+ "class OrderDao {\n"
				+ "  void update(Object jdbc) {\n"
				+ "    StringBuilder sb = new StringBuilder();\n"
				+ "    sb.append(\"UPDATE TB_ORDER \").append(\"SET STATUS = ? \");\n"
				+ "    sb.append(\"WHERE ID = ?\");\n"
				+ "    jdbc.update(sb.toString(), 1, 2);\n"
				+ "  }\n"
				+ "  String count() {\n"
				+ "    return new StringBuffer(\"SELECT COUNT(*) \").append(\"FROM TB_ORDER\").toString();\n"
				+ "  }\n"
				+ "}\n");
		assertEquals("UPDATE TB_ORDER SET STATUS = ? WHERE ID = ?", byName(rows, "update").get("sqlBody"));
		assertEquals("UPDATE", byName(rows, "update").get("statementType"));
		assertEquals("SELECT COUNT(*) FROM TB_ORDER", byName(rows, "count").get("sqlBody"));
		assertTrue(((String) byName(rows, "count").get("propertiesJson")).contains("\"call\":\"return\""));
	}

	@Test
	public void 상수는_한_번만_남기고_그대로_넘긴_메소드를_적는다() {
		List<Map<String, Object>> rows = sqlOf("package a;\n"
				+ "class OrderDao {\n"
				+ "  private static final String TABLE = \"TB_ORDER\";\n"
				+ "  private static final String SELECT_SQL = \"SELECT ID FROM \" + TABLE + \" WHERE ID = ?\";\n"
				+ "  void find(java.sql.Connection con) throws Exception { con.prepareStatement(SELECT_SQL); }\n"
				+ "  void findLocked(java.sql.Connection con) throws Exception { con.prepareStatement(SELECT_SQL + \" FOR UPDATE\"); }\n"
				+ "}\n");
		assertEquals(2, rows.size(), String.valueOf(names(rows)));
		Map<String, Object> constant = byName(rows, "SELECT_SQL");
		assertEquals("SELECT ID FROM TB_ORDER WHERE ID = ?", constant.get("sqlBody"));
		String properties = (String) constant.get("propertiesJson");
		assertTrue(properties.contains("\"fieldId\""), properties);
		assertTrue(properties.contains("\"methods\":[\""), properties);
		assertEquals("SELECT ID FROM TB_ORDER WHERE ID = ? FOR UPDATE", byName(rows, "findLocked").get("sqlBody"));
	}

	@Test
	public void 사람의_글과_짧은_글은_SQL_로_보지_않는다() {
		assertFalse(JavaSqlCollector.looksLikeSql("Select an item from the list"));
		assertFalse(JavaSqlCollector.looksLikeSql("Update failed for user"));
		assertFalse(JavaSqlCollector.looksLikeSql("Insert into cart failed"));
		assertFalse(JavaSqlCollector.looksLikeSql("delete it"));
		assertTrue(JavaSqlCollector.looksLikeSql("select distinct name from member"));
		assertTrue(JavaSqlCollector.looksLikeSql("  (SELECT * FROM A) UNION (SELECT * FROM B)"));
		assertTrue(JavaSqlCollector.looksLikeSql("insert into tb_log (a, b) values (?, ?)"));
		assertTrue(JavaSqlCollector.looksLikeSql("update Order o set o.status = :s where o.id = :id"));
		assertTrue(JavaSqlCollector.looksLikeSql("WITH t AS (SELECT 1 FROM DUAL) SELECT * FROM t"));
		assertEquals("DELETE", JavaSqlCollector.statementTypeOf(" delete from TB_ORDER"));
		assertEquals("SELECT", JavaSqlCollector.statementTypeOf("with x as (select 1) select * from x"));
	}

	@Test
	public void 익명_클래스의_SQL_은_그_클래스의_메소드에_붙는다() {
		List<Map<String, Object>> rows = sqlOf("package a;\n"
				+ "class OrderDao {\n"
				+ "  void run(Object jdbc) {\n"
				+ "    Runnable r = new Runnable() { public void run() { execute(\"DELETE FROM TB_TEMP WHERE ID = 1\"); } };\n"
				+ "  }\n"
				+ "  void execute(String sql) {}\n"
				+ "}\n");
		assertEquals(1, rows.size());
		assertEquals("a.OrderDao$1", rows.get(0).get("namespace"));
	}

	@Test
	public void 레거시_표본의_JDBC_SQL_을_찾는다() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src/test/resources/samples/legacy-app/WEB-INF/src/com/legacy/order/dao/OrderDAO.java"))
				, Charset.forName("EUC-KR"));
		JavaSourceParser.Result parsed = parser.parse(source, null);
		List<Map<String, Object>> rows = new DeclarationCollector("test", 1L, 7L, "src").collect(parsed.unit).getSqlStatements();
		assertEquals("SELECT ORDER_ID, CUSTOMER_NAME, AMOUNT FROM TB_ORDER WHERE CUSTOMER_NAME = ?", byName(rows, "SELECT_SQL").get("sqlBody"));
		Map<String, Object> delete = null;
		for (int i = 0; i < rows.size(); i++) {
			if ("DELETE".equals(rows.get(i).get("statementType"))) {
				delete = rows.get(i);
			}
		}
		assertEquals("DELETE FROM TB_ORDER WHERE ORDER_ID = ?", delete == null ? null : delete.get("sqlBody"));
		assertNull(byName(rows, "SELECT_SQL").get("parameterType"));
	}

}

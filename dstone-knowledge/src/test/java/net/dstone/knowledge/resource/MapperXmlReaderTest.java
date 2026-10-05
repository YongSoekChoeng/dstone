package net.dstone.knowledge.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * MyBatis / iBATIS 매퍼에서 SQL statement를 읽는 규칙을 확인합니다.
 * </pre>
 */
public class MapperXmlReaderTest {

	private final MapperXmlReader reader = new MapperXmlReader();

	private static final String MYBATIS = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://no-such-host.invalid/dtd/mybatis-3-mapper.dtd\">\n"
			+ "<mapper namespace=\"Order\">\n"
			+ "  <resultMap id=\"orderMap\" type=\"OrderVO\"/>\n"
			+ "  <sql id=\"columns\">ORDER_ID, CUSTOMER_NM</sql>\n"
			+ "  <select id=\"selectOrderList\" parameterType=\"OrderVO\" resultMap=\"orderMap\">\n"
			+ "    /* 주문 목록 */\n"
			+ "    SELECT <include refid=\"columns\"/>\n"
			+ "      FROM TB_ORDER\n"
			+ "    <where>\n"
			+ "      <if test=\"customerNm != null\">AND CUSTOMER_NM = #{customerNm}</if>\n"
			+ "      <if test=\"ids != null\">AND ORDER_ID IN <foreach collection=\"ids\" item=\"id\" open=\"(\" close=\")\" separator=\",\">#{id}</foreach></if>\n"
			+ "    </where>\n"
			+ "  </select>\n"
			+ "  <insert id=\"insertOrder\">\n"
			+ "    <selectKey keyProperty=\"orderId\" resultType=\"long\" order=\"BEFORE\">SELECT SEQ_ORDER.NEXTVAL FROM DUAL</selectKey>\n"
			+ "    <![CDATA[ INSERT INTO TB_ORDER (ORDER_ID, AMOUNT) VALUES (#{orderId}, #{amount}) ]]>\n"
			+ "  </insert>\n"
			+ "</mapper>\n";

	@Test
	public void MyBatis_매퍼의_statement와_조각을_읽는다() throws Exception {
		List<Map<String, Object>> rows = reader.read(MYBATIS);

		// resultMap은 SQL이 아니라서 빠진다: 조각 1 + select 1 + insert 1
		assertEquals(3, rows.size());

		Map<String, Object> fragment = rows.get(0);
		assertEquals("SQL_FRAGMENT", fragment.get("statementType"));
		assertEquals("columns", fragment.get("statementId"));
		assertEquals("ORDER_ID, CUSTOMER_NM", fragment.get("sqlBody"));

		Map<String, Object> select = rows.get(1);
		assertEquals("MYBATIS", select.get("mapperType"));
		assertEquals("Order", select.get("namespace"));
		assertEquals("selectOrderList", select.get("statementId"));
		assertEquals("SELECT", select.get("statementType"));
		assertEquals("OrderVO", select.get("parameterType"));
		assertEquals("orderMap", select.get("resultType"));
		assertEquals(Integer.valueOf(6), select.get("lineStart"));
		assertEquals(Integer.valueOf(14), select.get("lineEnd"));
	}

	@Test
	public void 조건_태그는_벗기고_include는_표시로_남긴다() throws Exception {
		String sql = (String) reader.read(MYBATIS).get(1).get("sqlBody");

		// 설명으로 적어 둔 주석은 그대로 둔다.
		assertTrue(sql.startsWith("/* 주문 목록 */"));
		// include는 다른 파일의 조각일 수 있어서 여기서 채우지 않는다.
		assertTrue(sql.contains(MapperXmlReader.INCLUDE_OPEN + "columns" + MapperXmlReader.INCLUDE_CLOSE));
		assertTrue(sql.contains("FROM TB_ORDER"));
		// where 태그는 글자로, if 안의 글은 전부, foreach의 open / close도 글자로.
		assertTrue(sql.contains("WHERE"));
		assertTrue(sql.contains("AND CUSTOMER_NM = #{customerNm}"));
		assertTrue(sql.replace(" ", "").contains("ORDER_IDIN(#{id})"));
	}

	@Test
	public void selectKey의_SQL은_본문에_섞지_않고_CDATA는_보통_글로_읽는다() throws Exception {
		Map<String, Object> insert = reader.read(MYBATIS).get(2);

		assertEquals("INSERT", insert.get("statementType"));
		assertEquals("INSERT INTO TB_ORDER (ORDER_ID, AMOUNT) VALUES (#{orderId}, #{amount})", insert.get("sqlBody"));
		assertNull(insert.get("parameterType"));
	}

	@Test
	public void iBATIS_매퍼도_같은_방식으로_읽는다() throws Exception {
		String text = "<?xml version=\"1.0\" encoding=\"EUC-KR\"?>\n"
				+ "<!DOCTYPE sqlMap PUBLIC \"-//iBATIS.com//DTD SQL Map 2.0//EN\" \"http://no-such-host.invalid/dtd/sql-map-2.dtd\">\n"
				+ "<sqlMap namespace=\"Member\">\n"
				+ "  <typeAlias alias=\"member\" type=\"com.x.MemberVO\"/>\n"
				+ "  <statement id=\"findMember\" parameterClass=\"string\" resultClass=\"member\">\n"
				+ "    SELECT * FROM TB_MEMBER\n"
				+ "    <dynamic prepend=\"WHERE\"><isNotEmpty property=\"id\" prepend=\"AND\">MEMBER_ID = #id#</isNotEmpty></dynamic>\n"
				+ "  </statement>\n"
				+ "  <procedure id='closeMonth'>{ call PRC_CLOSE_MONTH(#ym#) }</procedure>\n"
				+ "</sqlMap>\n";
		List<Map<String, Object>> rows = reader.read(text);

		assertEquals(2, rows.size());
		Map<String, Object> statement = rows.get(0);
		assertEquals("IBATIS", statement.get("mapperType"));
		assertEquals("Member", statement.get("namespace"));
		// statement 태그는 SQL의 첫 단어로 종류를 정한다.
		assertEquals("SELECT", statement.get("statementType"));
		assertEquals("string", statement.get("parameterType"));
		assertEquals("member", statement.get("resultType"));
		String sql = ((String) statement.get("sqlBody")).replaceAll("\\s+", " ");
		assertEquals("SELECT * FROM TB_MEMBER WHERE AND MEMBER_ID = #id#", sql);

		assertEquals("PROCEDURE", rows.get(1).get("statementType"));
		// 작은따옴표로 적은 id도 줄 번호를 찾는다.
		assertEquals(Integer.valueOf(9), rows.get(1).get("lineStart"));
	}

	@Test
	public void 매퍼가_아닌_XML에서는_아무것도_읽지_않는다() throws Exception {
		assertTrue(reader.read("<beans><bean id=\"a\" class=\"com.x.A\"/></beans>").isEmpty());
	}

}

package net.dstone.knowledge.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * SQL 들여쓰기 규칙을 확인합니다.
 * </pre>
 */
public class SqlFormatterTest {

	@Test
	public void 절은_한_줄에_두고_내용은_들여_쓰며_쉼표와_AND는_줄_앞에_둔다() {
		assertEquals(
				"SELECT\n"
				+ "    A.COL1\n"
				+ "    , A.COL2 AS C2\n"
				+ "FROM\n"
				+ "    TB_ORDER A\n"
				+ "WHERE\n"
				+ "    A.COL1 = 'AA'\n"
				+ "    AND A.COL2 BETWEEN #{from} AND #{to}\n"
				+ "    OR A.COL3 IN ('1', '2')\n"
				+ "ORDER BY\n"
				+ "    A.COL1\n"
				+ "    , A.COL2 DESC",
				SqlFormatter.format("SELECT A.COL1,A.COL2 AS C2 FROM TB_ORDER A WHERE A.COL1='AA' AND A.COL2 BETWEEN #{from} AND #{to} OR A.COL3 IN ('1','2') ORDER BY A.COL1, A.COL2 DESC"));
	}

	@Test
	public void 글자는_바꾸지_않는다() {
		// 대소문자는 원문 그대로다.
		assertEquals("select\n    a\nfrom\n    t", SqlFormatter.format("select a from t"));
	}

	@Test
	public void 서브쿼리는_더_들여_쓰고_닫는_괄호는_연_줄의_칸에_둔다() {
		assertEquals(
				"SELECT\n"
				+ "    T1.A\n"
				+ "    , (\n"
				+ "        SELECT\n"
				+ "            MAX(T2.B)\n"
				+ "        FROM\n"
				+ "            TB2 T2\n"
				+ "        WHERE\n"
				+ "            T2.A = T1.A\n"
				+ "    ) AS MAX_B\n"
				+ "FROM\n"
				+ "    TB1 T1",
				SqlFormatter.format("SELECT T1.A, (SELECT MAX(T2.B) FROM TB2 T2 WHERE T2.A = T1.A) AS MAX_B FROM TB1 T1"));
	}

	@Test
	public void JOIN과_ON은_FROM_아래에_둔다() {
		assertEquals(
				"SELECT\n"
				+ "    *\n"
				+ "FROM\n"
				+ "    TB1 A\n"
				+ "    LEFT OUTER JOIN TB2 B\n"
				+ "        ON A.X = B.X\n"
				+ "        AND B.Y = 'Y'\n"
				+ "    , TB3 C\n"
				+ "WHERE\n"
				+ "    C.Z = A.Z",
				SqlFormatter.format("SELECT * FROM TB1 A LEFT OUTER JOIN TB2 B ON A.X = B.X AND B.Y = 'Y', TB3 C WHERE C.Z = A.Z"));
	}

	@Test
	public void CASE는_WHEN과_ELSE를_한_줄씩_쓴다() {
		assertEquals(
				"SELECT\n"
				+ "    CASE\n"
				+ "        WHEN A = '1' AND B = '2' THEN 'X'\n"
				+ "        ELSE 'Y'\n"
				+ "    END AS C\n"
				+ "FROM\n"
				+ "    T",
				SqlFormatter.format("SELECT CASE WHEN A = '1' AND B = '2' THEN 'X' ELSE 'Y' END AS C FROM T"));
	}

	@Test
	public void INSERT의_컬럼_목록과_값_목록은_한_줄에_하나씩_쓴다() {
		assertEquals(
				"INSERT INTO\n"
				+ "    TB_ORDER (\n"
				+ "        ORDER_ID\n"
				+ "        , AMOUNT\n"
				+ "    )\n"
				+ "VALUES\n"
				+ "    (\n"
				+ "        #{orderId}\n"
				+ "        , NVL(#amount#, 0)\n"
				+ "    )",
				SqlFormatter.format("INSERT INTO TB_ORDER(ORDER_ID, AMOUNT) VALUES (#{orderId}, NVL(#amount#,0))"));
	}

	@Test
	public void 줄_끝_주석은_줄_끝에_혼자_있던_주석은_혼자_둔다() {
		assertEquals(
				"/* 주문 목록 */\n"
				+ "SELECT\n"
				+ "    A.ID /* 아이디 */\n"
				+ "    , A.NM -- 이름\n"
				+ "    , A.AMT\n"
				+ "FROM\n"
				+ "    TB A",
				SqlFormatter.format("/* 주문 목록 */\nSELECT A.ID /* 아이디 */\n, A.NM -- 이름\n, A.AMT FROM TB A"));
	}

	@Test
	public void UPDATE와_DELETE와_UNION() {
		assertEquals("UPDATE\n    TB\nSET\n    A = #{a}\n    , B = SYSDATE\nWHERE\n    ID = #{id}",
				SqlFormatter.format("UPDATE TB SET A = #{a}, B = SYSDATE WHERE ID = #{id}"));
		assertEquals("DELETE FROM\n    TB\nWHERE\n    ID = ?", SqlFormatter.format("DELETE FROM TB WHERE ID = ?"));
		assertEquals("SELECT\n    A\nFROM\n    T1\nUNION ALL\nSELECT\n    A\nFROM\n    T2",
				SqlFormatter.format("SELECT A FROM T1 UNION ALL SELECT A FROM T2"));
		// FOR UPDATE 는 절이 아니다.
		assertEquals("SELECT\n    A\nFROM\n    T\nWHERE\n    ID = 1 FOR UPDATE", SqlFormatter.format("SELECT A FROM T WHERE ID = 1 FOR UPDATE"));
	}

	@Test
	public void 함수_안의_FROM과_ORDER_BY는_건드리지_않는다() {
		assertEquals("SELECT\n    EXTRACT(YEAR FROM D)\n    , ROW_NUMBER() OVER (PARTITION BY A ORDER BY B) RN\nFROM\n    T",
				SqlFormatter.format("SELECT EXTRACT(YEAR FROM D), ROW_NUMBER() OVER (PARTITION BY A ORDER BY B) RN FROM T"));
	}

	@Test
	public void 문자열과_include_표시는_그대로_둔다() {
		assertEquals("SELECT\n    'a,  b'\nFROM\n    T\nWHERE\n    1 = 1 @@include(Order.where)@@",
				SqlFormatter.format("SELECT 'a,  b' FROM T WHERE 1=1 @@include(Order.where)@@"));
	}

	@Test
	public void 읽지_못하는_SQL은_원문을_돌려준다() {
		assertEquals(null, SqlFormatter.format(null));
		assertEquals("", SqlFormatter.format(""));
	}

}

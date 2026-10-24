package net.dstone.knowledge.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * SQL에서 테이블과 읽기/쓰기 종류를 뽑는 규칙을 확인합니다.
 * </pre>
 */
public class SqlTableExtractorTest {

	private final SqlTableExtractor extractor = new SqlTableExtractor();

	@Test
	public void 조회는_읽는_테이블을_모두_찾는다() {
		List<SqlTableExtractor.TableUse> uses = extractor.extract(
				"SELECT o.ORDER_ID, c.NAME FROM tb_order o LEFT JOIN TB_CUSTOMER c ON c.ID = o.CUSTOMER_ID WHERE o.ORDER_ID = #{orderId}", "SELECT");

		// 이름은 대문자로 맞춘다.
		assertEquals("[TB_CUSTOMER:R, TB_ORDER:R]", textOf(uses));
		assertTrue(!anyGuessed(uses), "파서가 읽은 것이다.");
	}

	@Test
	public void 넣기는_대상은_C_가져오는_곳은_R이다() {
		List<SqlTableExtractor.TableUse> uses = extractor.extract(
				"INSERT INTO TB_ORDER_HIST (ORDER_ID, AMOUNT) SELECT ORDER_ID, AMOUNT FROM TB_ORDER WHERE ORDER_ID = #{orderId}", "INSERT");

		assertEquals("[TB_ORDER:R, TB_ORDER_HIST:C]", textOf(uses));
	}

	@Test
	public void 고치기와_지우기의_대상을_찾고_서브쿼리의_테이블은_읽기로_센다() {
		assertEquals("[TB_GRADE:R, TB_MEMBER:U]", textOf(extractor.extract(
				"UPDATE TB_MEMBER SET GRADE = (SELECT MAX(GRADE) FROM TB_GRADE) WHERE MEMBER_ID = #{id}", "UPDATE")));
		assertEquals("[TB_MEMBER:D]", textOf(extractor.extract("DELETE FROM TB_MEMBER WHERE MEMBER_ID = #id#", "DELETE")));
	}

	@Test
	public void WITH_절의_임시_이름과_DUAL은_테이블이_아니다() {
		List<SqlTableExtractor.TableUse> uses = extractor.extract(
				"WITH recent AS (SELECT * FROM TB_ORDER WHERE ORDER_DT > SYSDATE - 7) SELECT COUNT(*) FROM recent", "SELECT");
		assertEquals("[TB_ORDER:R]", textOf(uses));

		assertEquals("[]", textOf(extractor.extract("SELECT SEQ_ORDER.NEXTVAL FROM DUAL", "SELECT")));
	}

	@Test
	public void 주석_안의_글자는_테이블로_잡지_않는다() {
		List<SqlTableExtractor.TableUse> uses = extractor.extract(
				"/* order-mapper.xml: 예전에는 FROM TB_OLD 를 읽었다 */\nSELECT * -- JOIN TB_GONE\n FROM TB_ORDER", "SELECT");

		assertEquals("[TB_ORDER:R]", textOf(uses));
	}

	@Test
	public void 조건_조각을_이어_붙여_생긴_군더더기는_다듬어서_파서로_읽는다() {
		// 조건 조각을 전부 이어 붙이면 이런 글이 된다(WHERE 바로 뒤에 AND, 괄호 바로 뒤에 OR).
		List<SqlTableExtractor.TableUse> uses = extractor.extract(
				"UPDATE TB_ORDER SET STATUS = #{status} WHERE AND ORDER_ID = #{orderId} AND ( OR ORDER_ID IN (SELECT ORDER_ID FROM TB_ORDER_ITEM))", "UPDATE");
		assertEquals("[TB_ORDER:U, TB_ORDER_ITEM:R]", textOf(uses));
		assertTrue(!anyGuessed(uses));

		// 조건이 하나도 없어서 WHERE만 남은 경우
		uses = extractor.extract("SELECT * FROM TB_ORDER WHERE ORDER BY ORDER_ID", "SELECT");
		assertEquals("[TB_ORDER:R]", textOf(uses));
		assertTrue(!anyGuessed(uses));
	}

	@Test
	public void 문법에_안_맞는_SQL은_정규식으로_찾고_짐작이라고_표시한다() {
		// 값 목록이 조건에 따라 달라지는 SQL을 펴면 쉼표가 어긋난다. 이런 것은 다듬지 못한다.
		List<SqlTableExtractor.TableUse> uses = extractor.extract(
				"UPDATE TB_ORDER SET , STATUS = #{status} , WHERE ORDER_ID IN (SELECT ORDER_ID FROM TB_ORDER_ITEM)", "UPDATE");

		assertEquals("[TB_ORDER:U, TB_ORDER_ITEM:R]", textOf(uses));
		assertTrue(anyGuessed(uses));
	}

	@Test
	public void 실행할_때_정해지는_테이블_이름은_테이블로_세지_않는다() {
		// ${...} 와 $...$ 는 SQL 글자 자체를 끼워 넣는 자리라 무엇이 올지 알 수 없다.
		assertEquals("[TB_CODE:R]", textOf(extractor.extract("SELECT * FROM ${tableName} t JOIN TB_CODE c ON c.CD = t.CD", "SELECT")));
		assertEquals("[]", textOf(extractor.extract("SELECT * FROM $tableName$", "SELECT")));
	}

	@Test
	public void 비어_있거나_include_표시만_남은_SQL은_결과가_없다() {
		assertEquals("[]", textOf(extractor.extract(null, "SELECT")));
		assertEquals("[]", textOf(extractor.extract("  @@include(Common.paging)@@  ", "SELECT")));
	}

	@Test
	public void 별칭으로_가리킨_컬럼은_그_테이블의_컬럼이다() {
		List<SqlTableExtractor.TableUse> uses = extractor.extract(
				"SELECT o.ORDER_ID, c.NAME AS CUSTOMER_NAME, NVL(o.AMOUNT, 0) FROM TB_ORDER o JOIN TB_CUSTOMER c ON c.ID = o.CUSTOMER_ID"
				+ " WHERE o.STATUS = #{status} AND CREATED_BY = #{user}", "SELECT");

		// 테이블이 둘인 FROM에서 별칭 없이 적힌 CREATED_BY는 어느 쪽 것인지 몰라서 넣지 않는다.
		assertEquals("[TB_CUSTOMER:[ID, NAME], TB_ORDER:[AMOUNT, CUSTOMER_ID, ORDER_ID, STATUS]]", columnsOf(uses));
	}

	@Test
	public void 테이블이_하나뿐이면_별칭_없는_컬럼도_그_테이블의_것이다() {
		assertEquals("[TB_MEMBER:[GRADE, MEMBER_ID, REG_DT, USER_ID]]", columnsOf(extractor.extract(
				"SELECT USER_ID AS USER_ID, GRADE, COUNT(*) AS CNT FROM TB_MEMBER WHERE MEMBER_ID = #{id} AND REG_DT > SYSDATE", "SELECT")));
	}

	@Test
	public void 넣기와_고치기의_대상_컬럼과_서브쿼리의_컬럼을_나눠_담는다() {
		assertEquals("[TB_ORDER:[AMOUNT, ORDER_ID], TB_ORDER_HIST:[AMOUNT, ORDER_ID, REG_DT]]", columnsOf(extractor.extract(
				"INSERT INTO TB_ORDER_HIST (ORDER_ID, AMOUNT, REG_DT) SELECT ORDER_ID, AMOUNT, SYSDATE FROM TB_ORDER WHERE ORDER_ID = #{orderId}", "INSERT")));

		// 서브쿼리 안의 ID는 그 서브쿼리의 테이블(TB_GRADE), 바깥의 MEMBER_ID는 대상 테이블의 것이다.
		assertEquals("[TB_GRADE:[GRADE, ID], TB_MEMBER:[GRADE, MEMBER_ID, MEMBER_NO, UPD_DT]]", columnsOf(extractor.extract(
				"UPDATE TB_MEMBER m SET GRADE = (SELECT MAX(GRADE) FROM TB_GRADE g WHERE g.ID = m.MEMBER_NO), UPD_DT = SYSDATE WHERE MEMBER_ID = #{id}", "UPDATE")));
	}

	@Test
	public void 서브쿼리를_FROM에_둔_쪽의_컬럼은_테이블의_것으로_치지_않는다() {
		assertEquals("[TB_BOARD:[BOARD_ID, TITLE]]", columnsOf(extractor.extract(
				"SELECT * FROM (SELECT ROWNUM RN, X.* FROM (SELECT BOARD_ID, TITLE FROM TB_BOARD ORDER BY BOARD_ID) X) WHERE RN BETWEEN 1 AND 10", "SELECT")));
	}

	@Test
	public void 정규식으로_찾은_SQL은_컬럼을_읽지_않는다() {
		List<SqlTableExtractor.TableUse> uses = extractor.extract(
				"UPDATE TB_ORDER SET , STATUS = #{status} , WHERE ORDER_ID = #{id}", "UPDATE");
		assertTrue(anyGuessed(uses));
		assertEquals(null, uses.get(0).columns);
	}

	@Test
	public void SQL_주석에서_테이블과_컬럼의_한글명을_읽는다() {
		List<SqlTableExtractor.TableUse> uses = extractor.extract(
				"SELECT tb1.seq             /* 접근유형           BIGINT        */\n"
				+ "     , tb1.mb_nm           /* 회사명             VARCHAR(100)  */\n"
				+ "     , tb2.auth_nm,        /* 권한명 */\n"
				+ "       SUBSTR(tb1.addr1, 1, 10) AS ADDR /* 주소 앞부분 */\n"
				+ "     , tb1.use_yn          /* use flag */\n"
				+ "  FROM tbsy_member tb1 /* [SY][시스템] 회원사관리 */\n"
				+ "  JOIN tbsy_auth tb2 ON tb2.mb_id = tb1.mb_id /* 권한 조인 */\n"
				+ " WHERE tb1.mb_id = #{mb_id} /* 회원사ID */\n"
				+ "   AND tb1.use_yn = 'Y' /* 사용하는 것만 */", "SELECT");

		// 별칭을 설명한 주석(AS ADDR), 한글이 없는 주석, 조인 조건과 값 비교(= 'Y')에 단 주석은 넣지 않는다.
		assertEquals("[TBSY_AUTH:{AUTH_NM=권한명}:null, TBSY_MEMBER:{SEQ=접근유형, MB_NM=회사명, MB_ID=회원사ID}:[SY][시스템] 회원사관리]", commentsOf(uses));
	}

	@Test
	public void 고치기의_SET과_넣기의_컬럼_목록에_단_주석도_읽는다() {
		assertEquals("[TB_ORDER:{STATUS=주문상태, UPD_DT=수정일시}:주문]", commentsOf(extractor.extract(
				"UPDATE tb_order /* 주문 */\n   SET status = #{status} /* 주문상태 */\n     , upd_dt = SYSDATE /* 수정일시 */\n WHERE order_id = #{id}", "UPDATE")));
		assertEquals("[TB_ORDER_HIST:{ORDER_ID=주문번호, AMOUNT=금액}:null]", commentsOf(extractor.extract(
				"INSERT INTO tb_order_hist (\n  order_id /* 주문번호 */\n, amount -- 금액\n) VALUES (#{id}, #{amount})", "INSERT")));
	}

	@Test
	public void FROM을_앞줄에_쓰거나_쉼표로_늘어놓은_테이블의_주석도_읽는다() {
		// HI-Japan 매퍼에 흔한 모양. 서브쿼리 안의 FROM 목록도 같다. 같은 테이블에 주석이 둘이면 먼저 나온 것을 쓴다.
		assertEquals("[JC_ACDT_AGMT:{}:클레임_사고계약, JC_BENEFIT:{}:보험금, JC_PLY:{}:클레임_증권]", commentsOf(extractor.extract(
				"SELECT A.ACDT_MGNT_NO                 AS ACDT_NO     /* 사고번호 */\n"
				+ "     , NVL((SELECT MAX(S1.PLY_NO)\n"
				+ "              FROM JC_ACDT_AGMT S1      /* 클레임_사고계약 */\n"
				+ "                  ,JC_PLY S2            /* 클레임_증권 */\n"
				+ "             WHERE S1.PLY_NO = S2.PLY_NO), 0) AS PLY_NO /* 증권번호 */\n"
				+ "  FROM\n"
				+ "       JC_BENEFIT A       /* 보험금       */\n"
				+ "      ,JC_ACDT_AGMT B     /* 사고계약     */\n"
				+ " WHERE A.ACDT_MGNT_NO = B.ACDT_MGNT_NO", "SELECT")));
	}

	@Test
	public void 스키마를_붙여_적은_테이블의_주석도_그_테이블의_것이다() {
		// 테이블 이름은 스키마째 남으므로 주석도 스키마째 짝을 짓는다.
		assertEquals("[DPHJSDBA.JZ_EMP_INFO:{}:사원정보, TB_ORDER:{}:주문]", commentsOf(extractor.extract(
				"SELECT o.ORDER_ID\n  FROM TB_ORDER o              /* 주문 */\n     , DPHJSDBA.JZ_EMP_INFO e    /* 사원정보 */\n WHERE o.EMP_NO = e.EMP_NO", "SELECT")));
	}

	@Test
	public void 조회_목록의_별칭_주석은_테이블_주석으로_보지_않는다() {
		// "식 별칭 /* 설명 */" 은 FROM 절이 아니므로 테이블 이름으로 잡히지 않는다.
		assertEquals("[TB_ORDER:{}:null]", commentsOf(extractor.extract(
				"SELECT\n       o.ORDER_ID ORDER_NO     /* 주문번호 */\n     , o.AMOUNT AMT          /* 금액 */\n  FROM TB_ORDER o", "SELECT")));
	}

	@Test
	public void 테이블이_둘인데_별칭_없이_적은_컬럼의_주석은_주인을_정하지_못하면_버린다() {
		// CREATED_BY 는 어느 테이블의 것인지 모른다. NAME 은 c 의 것으로 확실하다.
		assertEquals("[TB_CUSTOMER:{NAME=고객명}:null, TB_ORDER:{}:null]", commentsOf(extractor.extract(
				"SELECT c.NAME /* 고객명 */, CREATED_BY /* 등록자 */ FROM TB_ORDER o JOIN TB_CUSTOMER c ON c.ID = o.CUSTOMER_ID", "SELECT")));
	}

	/** "테이블:{컬럼=한글명}:테이블한글명"을 이름순으로 늘어놓습니다. */
	private String commentsOf(List<SqlTableExtractor.TableUse> uses) {
		List<String> texts = new ArrayList<String>();
		for (int i = 0; i < uses.size(); i++) {
			String text = uses.get(i).table + ":" + uses.get(i).columnComments + ":" + uses.get(i).tableComment;
			if (!texts.contains(text)) {
				texts.add(text);
			}
		}
		Collections.sort(texts);
		return texts.toString();
	}

	/** 순서와 상관없이 비교하려고 "테이블:[컬럼]"을 이름순으로 늘어놓습니다. 같은 테이블이 두 번 나오면 한 번만 씁니다. */
	private String columnsOf(List<SqlTableExtractor.TableUse> uses) {
		List<String> texts = new ArrayList<String>();
		for (int i = 0; i < uses.size(); i++) {
			String text = uses.get(i).table + ":" + uses.get(i).columns;
			if (!texts.contains(text)) {
				texts.add(text);
			}
		}
		Collections.sort(texts);
		return texts.toString();
	}

	/** 순서와 상관없이 비교하려고 "테이블:종류"를 이름순으로 늘어놓습니다. */
	private String textOf(List<SqlTableExtractor.TableUse> uses) {
		List<String> texts = new ArrayList<String>();
		for (int i = 0; i < uses.size(); i++) {
			texts.add(uses.get(i).table + ":" + uses.get(i).crud);
		}
		Collections.sort(texts);
		return texts.toString();
	}

	private boolean anyGuessed(List<SqlTableExtractor.TableUse> uses) {
		for (int i = 0; i < uses.size(); i++) {
			if (uses.get(i).guessed) {
				return true;
			}
		}
		return false;
	}

}

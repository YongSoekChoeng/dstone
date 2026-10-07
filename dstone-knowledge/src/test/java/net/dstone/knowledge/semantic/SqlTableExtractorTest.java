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

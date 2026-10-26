package net.dstone.knowledge.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 메소드 위 주석에서 기능명을 뽑는 규칙을 확인합니다.
 * </pre>
 */
public class MethodTitleTest {

	@Test
	public void 태그_앞의_첫_줄이_기능명이다() {
		String[] lines = {
				"	/**",
				"	 * 출력장표발행",
				"	 * @Method : getBenefit",
				"	 * @Description : (Be sure to write a brief description)",
				"	 *",
				"	 * @param jcpr0100VO",
				"	 */",
				"	public JCPR0100VO getBenefit(JCPR0100VO jcpr0100VO) throws Exception {"
		};
		assertEquals("출력장표발행", MethodTitle.of(lines, Integer.valueOf(8)));
	}

	@Test
	public void 첫_줄이_없으면_Description_값을_쓴다() {
		String[] lines = {
				"	/**",
				"	 * @Method : selectRate",
				"	 * @Description : 계약별대리점수수료율조회<br>",
				"	 */",
				"",
				"	@Override",
				"	public List selectRate() {"
		};
		assertEquals("계약별대리점수수료율조회", MethodTitle.of(lines, Integer.valueOf(6)));
	}

	@Test
	public void 양식만_남은_자리_채움_글은_버린다() {
		String[] lines = {
				"	/**",
				"	 * @Description : (Please, don't forget to write a brief description)<br>",
				"	 */",
				"	public void a() {"
		};
		assertNull(MethodTitle.of(lines, Integer.valueOf(4)));
		String[] nonJavadoc = {
				"	/* (non-Javadoc)",
				"	 * @see a.B#c()",
				"	 */",
				"	public void c() {"
		};
		assertNull(MethodTitle.of(nonJavadoc, Integer.valueOf(4)));
	}

	@Test
	public void 기호나_막아_둔_코드나_소문자_한_단어는_버리고_Description을_본다() {
		String[] bracket = {
				"	/**",
				"	 * ]",
				"	 * @Method : searchItemCdList",
				"	 * @Description : 품목코드 List 조회",
				"	 */",
				"	public ArrayList searchItemCdList(JIAM0300VO vo) throws Exception {"
		};
		assertEquals("품목코드 List 조회", MethodTitle.of(bracket, Integer.valueOf(6)));
		String[] code = {
				"	// public JACA0190VO selectList(JACA0191VO entity) throws Exception {",
				"	public JACA0190VO selectList(JACA0190VO entity) throws Exception {"
		};
		assertNull(MethodTitle.of(code, Integer.valueOf(2)));
		String[] word = {
				"	// result",
				"	public void run() {"
		};
		assertNull(MethodTitle.of(word, Integer.valueOf(2)));
		String[] placeholder = {
				"/**",
				" * (Right here!, please make sure to write a brief description for class in English,",
				" *  and remove this statement)",
				" * @Description : 계정코드조회 mapper",
				" */",
				"public class A {"
		};
		assertEquals("계정코드조회 mapper", MethodTitle.of(placeholder, Integer.valueOf(6)));
	}

	@Test
	public void 줄_주석도_읽고_주석이_없으면_null이다() {
		String[] lines = {
				"	int count;",
				"	// 주문 취소",
				"	public void cancel() {",
				"	}",
				"	public void none() {"
		};
		assertEquals("주문 취소", MethodTitle.of(lines, Integer.valueOf(3)));
		assertNull(MethodTitle.of(lines, Integer.valueOf(5)));
		assertNull(MethodTitle.of(lines, Integer.valueOf(1)));
	}

}

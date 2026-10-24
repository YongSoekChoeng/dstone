package net.dstone.knowledge.dictionary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 사전 파일(CSV)에서 테이블 / 컬럼 한글명을 읽는 규칙을 확인합니다.
 * </pre>
 */
public class DictionaryFileReaderTest {

	@Test
	public void 테이블_목록을_읽는다() {
		// HI-Japan 의 D-01_테이블목록.csv 모양. 따옴표 안의 쉼표는 칸을 나누지 않는다.
		DictionaryFileReader.Dictionary dictionary = DictionaryFileReader.parse(
				"테이블,한글명,도메인,추정PK\n"
				+ "JA_ACTG_DLN_C,경리 거래선,JA,\"DLN_CAT,DLN_CD,SEQNO\"\n"
				+ "JC_BENEFIT,보험금(지급),JC,\"ACDT_MGNT_NO,HSEQ_NO,PYMT_ORD\"\n", "D-01_테이블목록.csv");

		assertEquals("보험금(지급)", dictionary.tables.get("JC_BENEFIT").text);
		assertEquals("D-01_테이블목록.csv:3", dictionary.tables.get("JC_BENEFIT").where);
		assertEquals("경리 거래선", dictionary.tables.get("JA_ACTG_DLN_C").text);
		assertNull(dictionary.problem);
	}

	@Test
	public void 컬럼_사전을_읽는다() {
		// 컬럼 열이 있으면 "한글명" 이 아니라 "테이블한글명" 이 테이블의 것이고, "컬럼설명(SQL주석)" 이 컬럼의 것이다.
		DictionaryFileReader.Dictionary dictionary = DictionaryFileReader.parse(
				"\uFEFF테이블,테이블한글명,컬럼,컬럼설명(SQL주석),추정PK\n"
				+ "JA_ACACNT_CD_C,은행계좌 코드,BNK_ACNT_CD,은행계좌코드,Y\n"
				+ "JA_ACACNT_CD_C,은행계좌 코드,HSEQ_NO,,\n"
				+ "ja_acacnt_cd_c,은행계좌 코드,use_yn,use flag,\n", "D-01_테이블컬럼사전.csv");

		assertEquals("은행계좌 코드", dictionary.tables.get("JA_ACACNT_CD_C").text);
		assertEquals("은행계좌코드", dictionary.columns.get("JA_ACACNT_CD_C").get("BNK_ACNT_CD").text);
		// 빈 칸, 한글이 없는 설명은 한글명이 아니다.
		assertNull(dictionary.columns.get("JA_ACACNT_CD_C").get("HSEQ_NO"));
		assertNull(dictionary.columns.get("JA_ACACNT_CD_C").get("USE_YN"));
	}

	@Test
	public void 테이블ID와_테이블명이_함께_있으면_테이블명이_한글명이다() {
		DictionaryFileReader.Dictionary dictionary = DictionaryFileReader.parse(
				"테이블ID\t테이블명\t컬럼ID\t컬럼명\n"
				+ "APP.TB_ORDER\t주문\tORDER_ID\t주문번호\n"
				+ "주문 테이블 아님\t-\t\t\n", "table.tsv");

		assertEquals("주문", dictionary.tables.get("TB_ORDER").text);
		assertEquals("주문번호", dictionary.columns.get("TB_ORDER").get("ORDER_ID").text);
		assertEquals(1, dictionary.tables.size());
	}

	@Test
	public void 머리글을_못_알아보면_까닭을_남긴다() {
		assertEquals("머리글에서 테이블 열과 한글명 열을 찾지 못했습니다", DictionaryFileReader.parse("a,b\n1,2\n", "x.csv").problem);
	}

	@Test
	public void UTF8이_아니면_MS949로_읽는다() {
		String text = "테이블,한글명\nTB_A,가나다\n";
		assertEquals(text, DictionaryFileReader.decode(text.getBytes(Charset.forName("MS949"))));
		assertEquals(text, DictionaryFileReader.decode(("\uFEFF" + text).getBytes(StandardCharsets.UTF_8)));
	}

}

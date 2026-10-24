package net.dstone.knowledge.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * DDL 파일에서 테이블 / 컬럼과 그 설명을 읽는 규칙을 확인합니다.
 * </pre>
 */
public class DdlReaderTest {

	private final DdlReader reader = new DdlReader();

	@Test
	public void MySQL_의_컬럼_COMMENT와_테이블_COMMENT를_읽는다() {
		List<Map<String, Object>> rows = reader.read(
				"-- 회원\n"
				+ "CREATE TABLE IF NOT EXISTS `tbsy_member` (\n"
				+ "  `mb_id` VARCHAR(20) NOT NULL COMMENT '회원사ID',\n"
				+ "  amount DECIMAL(10, 2) DEFAULT 0 COMMENT '금액(원, 부가세 포함)',\n"
				+ "  memo TEXT,\n"
				+ "  PRIMARY KEY (mb_id),\n"
				+ "  UNIQUE KEY uk_seq (seq)\n"
				+ ") ENGINE=InnoDB COMMENT='회원사관리';\n"
				+ "INSERT INTO tbsy_member VALUES ('a;b', 1, 'x');");

		assertEquals("[DDL_TABLE TBSY_MEMBER=회원사관리 @2"
				+ ", DDL_COLUMN TBSY_MEMBER.MB_ID=회원사ID {\"table\":\"TBSY_MEMBER\",\"column\":\"MB_ID\",\"dataType\":\"VARCHAR(20)\",\"ordinal\":1} @2"
				+ ", DDL_COLUMN TBSY_MEMBER.AMOUNT=금액(원, 부가세 포함) {\"table\":\"TBSY_MEMBER\",\"column\":\"AMOUNT\",\"dataType\":\"DECIMAL(10, 2)\",\"ordinal\":2} @2"
				+ ", DDL_COLUMN TBSY_MEMBER.MEMO=null {\"table\":\"TBSY_MEMBER\",\"column\":\"MEMO\",\"dataType\":\"TEXT\",\"ordinal\":3} @2]", textOf(rows, true));
	}

	@Test
	public void Oracle_PostgreSQL_의_COMMENT_ON을_읽는다() {
		List<Map<String, Object>> rows = reader.read(
				"CREATE TABLE APP.TB_ORDER (ORDER_ID NUMBER (10) NOT NULL, STATUS VARCHAR2(2));\n"
				+ "COMMENT ON TABLE APP.TB_ORDER IS '주문';\n"
				+ "COMMENT ON COLUMN APP.TB_ORDER.STATUS IS '주문상태(''01'' 접수)'\n"
				+ "/\n"
				+ "comment on column \"tb_order\".\"order_id\" is '주문번호';");

		assertEquals("[DDL_TABLE TB_ORDER=null, DDL_COLUMN TB_ORDER.ORDER_ID=null, DDL_COLUMN TB_ORDER.STATUS=null"
				+ ", DDL_TABLE TB_ORDER=주문, DDL_COLUMN TB_ORDER.STATUS=주문상태('01' 접수), DDL_COLUMN TB_ORDER.ORDER_ID=주문번호]", textOf(rows, false));
		assertEquals("NUMBER(10)", dataTypeOf(rows.get(1)));
		assertEquals(Integer.valueOf(5), rows.get(5).get("lineStart"));
	}

	@Test
	public void 정식_COMMENT가_없으면_일반_주석의_한글명을_읽는다() {
		List<Map<String, Object>> rows = reader.read(
				"-- testApp 로컬 MySQL 스키마\n"
				+ "\n"
				+ "-- [SY] 회원사 마스터\n"
				+ "CREATE TABLE tbsy_member (\n"
				+ "    mb_id   VARCHAR(20) NOT NULL,   -- 회원사ID\n"
				+ "    -- 회사명\n"
				+ "    mb_nm   VARCHAR(100) NULL,\n"
				+ "    use_yn  VARCHAR(1) DEFAULT '-' /* 사용여부 */,\n"
				+ "    memo    VARCHAR(200) COMMENT '메모' -- 주석보다 COMMENT 가 먼저\n"
				+ "  , seq     BIGINT -- index\n"
				+ ");\n"
				+ "CREATE TABLE tb_code ( -- 공통코드\n"
				+ "    cd VARCHAR(10)\n"
				+ ");");

		// 파일 맨 위의 설명은 빈 줄로 떨어져 있어서 테이블 한글명이 아니다. 한글이 없는 주석(-- index)도 쓰지 않는다.
		assertEquals("[DDL_TABLE TBSY_MEMBER=[SY] 회원사 마스터, DDL_COLUMN TBSY_MEMBER.MB_ID=회원사ID, DDL_COLUMN TBSY_MEMBER.MB_NM=회사명"
				+ ", DDL_COLUMN TBSY_MEMBER.USE_YN=사용여부, DDL_COLUMN TBSY_MEMBER.MEMO=메모, DDL_COLUMN TBSY_MEMBER.SEQ=null"
				+ ", DDL_TABLE TB_CODE=공통코드, DDL_COLUMN TB_CODE.CD=null]", textOf(rows, false));
		assertEquals(true, ((String) rows.get(1).get("propertiesJson")).indexOf("\"commentKind\":\"NOTE\"") >= 0);
		assertEquals(false, ((String) rows.get(4).get("propertiesJson")).indexOf("commentKind") >= 0, "정식 COMMENT 는 NOTE 가 아니다");
	}

	@Test
	public void 주석_안의_DDL은_읽지_않는다() {
		assertEquals("[]", textOf(reader.read("/* CREATE TABLE OLD_T (A INT); */\n-- COMMENT ON TABLE X IS 'y';\n"), false));
	}

	private String dataTypeOf(Map<String, Object> row) {
		String json = (String) row.get("propertiesJson");
		int at = json.indexOf("\"dataType\":\"") + 12;
		return json.substring(at, json.indexOf('"', at));
	}

	private String textOf(List<Map<String, Object>> rows, boolean detail) {
		List<String> texts = new ArrayList<String>();
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String text = row.get("resourceType") + " " + row.get("name") + "=" + row.get("value");
			if (detail) {
				String json = (String) row.get("propertiesJson");
				text += ("{}".equals(json) ? "" : " " + json) + " @" + row.get("lineStart");
			}
			texts.add(text);
		}
		return texts.toString();
	}

}

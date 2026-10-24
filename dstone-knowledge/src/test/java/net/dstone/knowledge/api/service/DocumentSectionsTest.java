package net.dstone.knowledge.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 설명 문서를 "설명"과 "소스 / SQL"로 나누는 규칙을 확인합니다.
 * </pre>
 */
public class DocumentSectionsTest {

	@Test
	public void 메소드_문서는_소스_앞까지가_설명이다() {
		DocumentSections sections = DocumentSections.split(Arrays.asList(
				"[메소드] a.B#c()\n파일: a/B.java (줄 3-5)\n소스:\n  void c() {\n    run();\n  }\n"));

		assertEquals("[메소드] a.B#c()\n파일: a/B.java (줄 3-5)", sections.description);
		assertEquals("SOURCE", sections.codeKind);
		assertEquals("  void c() {\n    run();\n  }", sections.code);
	}

	@Test
	public void 긴_메소드의_이어지는_조각은_머리말_세_줄을_떼고_잇는다() {
		DocumentSections sections = DocumentSections.split(Arrays.asList(
				"[메소드] a.B#c()\n파일: a/B.java (줄 3-6)\n소스:\n  void c() {\n    one();\n",
				"[메소드] a.B#c() (이어서 2/2)\n파일: a/B.java (줄 5-6)\n소스:\n    two();\n  }\n"));

		assertEquals("[메소드] a.B#c()\n파일: a/B.java (줄 3-6)", sections.description);
		assertEquals("  void c() {\n    one();\n    two();\n  }", sections.code);
	}

	@Test
	public void SQL_문서는_SQL_본문을_나누고_줄로_나뉜_조각의_첫_줄은_뗀다() {
		DocumentSections sections = DocumentSections.split(Arrays.asList(
				"[SQL] Order.select (SELECT)\n파일: OrderD.xml\nSQL:\nSELECT *\n",
				"[SQL] Order.select (SELECT) (이어서)\n  FROM TB_ORDER\n"));

		assertEquals("[SQL] Order.select (SELECT)\n파일: OrderD.xml", sections.description);
		assertEquals("SQL", sections.codeKind);
		assertEquals("SELECT *\n  FROM TB_ORDER", sections.code);
	}

	@Test
	public void 소스가_없는_타입_문서는_설명만_있다() {
		DocumentSections sections = DocumentSections.split(Arrays.asList("[타입] a.B (클래스)\n메소드: c()\n", "[타입] a.B (클래스) (이어서)\n필드: x: int\n"));

		assertEquals("[타입] a.B (클래스)\n메소드: c()\n필드: x: int", sections.description);
		assertNull(sections.codeKind);
		assertNull(sections.code);
	}

	@Test
	public void 문서가_없으면_모두_null이다() {
		DocumentSections sections = DocumentSections.split(new ArrayList<String>());

		assertNull(sections.description);
		assertNull(sections.code);
	}

}

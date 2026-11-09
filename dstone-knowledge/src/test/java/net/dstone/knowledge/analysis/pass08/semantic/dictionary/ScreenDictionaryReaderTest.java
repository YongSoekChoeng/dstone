package net.dstone.knowledge.analysis.pass08.semantic.dictionary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 화면 사전 파일(CSV) 읽기를 확인합니다. DB나 Spring 없이 도는 테스트입니다.
 * </pre>
 */
public class ScreenDictionaryReaderTest {

	@Test
	public void 화면_목록을_읽는다() {
		// HI-Japan 의 00-1.화면목록.csv 와 같은 머리글. "화면명(한글)" 은 화면명 열이고, 원어명은 쓰지 않는다.
		String csv = "﻿화면ID,화면명(한글),원어명,유형,파일\n"
				+ "JCIQ0700G,이행기한 관리,履行期間管理,메인,was/ui/jc/JCIQ0700G.xml\n"
				+ "JCRC0300P,\"사고 접수, 팝업\",事故受付,팝업,\n"
				+ "JZZZ0100G,ZZZ,ZZZ,메인,was/ui/jz/JZZZ0100G.xml\n";
		ScreenDictionaryReader.Dictionary dictionary = ScreenDictionaryReader.parse(csv, "00-1.화면목록.csv");
		assertNull(dictionary.problem);
		assertEquals(1, dictionary.byPath.size());
		assertEquals("이행기한 관리", dictionary.byPath.get(0)[1]);
		assertEquals("00-1.화면목록.csv:2", dictionary.byPath.get(0)[2]);
		assertEquals("사고 접수, 팝업", dictionary.byId.get("JCRC0300P")[0]);
		// 한글이 없는 이름은 건너뛴다.
		assertFalse(dictionary.byId.containsKey("JZZZ0100G"));
	}

	@Test
	public void 머리글이_맞지_않으면_까닭을_남긴다() {
		assertNotNull(ScreenDictionaryReader.parse("테이블,한글명\nTB_A,주문\n", "a.csv").problem);
	}

	@Test
	public void 경로는_한쪽이_다른_쪽으로_끝나면_같다() {
		assertTrue(ScreenDictionaryReader.samePath("was/ui/jc/a.xml", "ui/jc/A.xml"));
		assertTrue(ScreenDictionaryReader.samePath("ui/jc/A.xml", "ui/jc/A.xml"));
		assertFalse(ScreenDictionaryReader.samePath("was/ui/jc/BA.xml", "A.xml"));
		assertEquals("ui/jc/A.xml", ScreenDictionaryReader.normalize("\\ui\\jc\\A.xml"));
		assertEquals("JCIQ0700G", ScreenDictionaryReader.idOf("ui/jc/JCIQ0700G.xml"));
	}

	@Test
	public void 경로로_먼저_찾고_없으면_화면_ID_로_찾는다() {
		java.util.List<ScreenDictionaryReader.Dictionary> dictionaries = new java.util.ArrayList<ScreenDictionaryReader.Dictionary>();
		dictionaries.add(ScreenDictionaryReader.parse("화면ID,화면명(한글),파일\nJABA1010G,보험료 입출금 처리,was/ui/ja/JABA1010G.xml\nJABA1020P,미수보험료 조회 팝업,\n", "a.csv"));
		ScreenDictionaryReader.Loaded loaded = new ScreenDictionaryReader.Loaded(DictionaryFileReader.OK, null, new java.util.ArrayList<String>(), dictionaries);
		assertEquals("보험료 입출금 처리", ScreenDictionaryReader.find(loaded, "ui/ja/JABA1010G.xml")[0]);
		assertEquals("a.csv:3", ScreenDictionaryReader.find(loaded, "ui/ja/JABA1020P.xml")[1]);
		assertNull(ScreenDictionaryReader.find(loaded, "ui/ja/JABA9999G.xml"));
	}

}

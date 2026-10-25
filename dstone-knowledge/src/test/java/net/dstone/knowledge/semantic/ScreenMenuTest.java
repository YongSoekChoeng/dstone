package net.dstone.knowledge.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 메뉴 목록에서 "화면명 + 화면 경로" 쌍을 찾는 로직을 확인합니다. DB나 Spring 없이 도는 테스트입니다.
 * </pre>
 */
public class ScreenMenuTest {

	private final ScreenMenu screenMenu = new ScreenMenu();

	private final List<ScreenMenu.Rule> rules = ScreenMenu.parseRules("MENU_NM=SRC_PATH");

	@Test
	public void 규칙_글자를_읽는다() {
		List<ScreenMenu.Rule> parsed = ScreenMenu.parseRules(" MENU_NM=SRC_PATH , menuName=url, =x, y=, bad");
		assertEquals(2, parsed.size());
		assertEquals("menuName", parsed.get(1).getNameKey());
		assertEquals("url", parsed.get(1).getPathKey());
		assertTrue(ScreenMenu.parseRules(null).isEmpty());
	}

	@Test
	public void 스크립트에_적힌_메뉴_목록을_읽는다() {
		// HI-Japan ui/main.xml 과 같은 모양. 경로가 빈 상위 메뉴는 뺀다.
		String text = "<script><![CDATA[\n"
				+ "var menus = [\n"
				+ " {'MENU_ID':'M050012','MENU_NM':'事故照会','PARNT_MENU_ID':'M050001','PAGE_ID':'','SRC_PATH':''}\n"
				+ ",{'MENU_ID':'M050031','MENU_NM':'履行期間管理','PARNT_MENU_ID':'M050012','PAGE_ID':'JCIQ0700G','SRC_PATH':'/ui/jc/JCIQ0700G.xml'}\n"
				+ "];\n"
				+ "]]></script>";
		List<ScreenMenu.Entry> entries = screenMenu.findEntries(text, rules);
		assertEquals(1, entries.size());
		assertEquals("履行期間管理", entries.get(0).getName());
		assertEquals("/ui/jc/JCIQ0700G.xml", entries.get(0).getPath());
		assertEquals(4, entries.get(0).getLine());
	}

	@Test
	public void JSON과_따옴표_없는_키와_XML_속성도_읽는다() {
		String json = "[{\"SRC_PATH\": \"/order/list.jsp\", \"MENU_NM\": \"주문 목록\"}, {MENU_NM: '주문 상세', SRC_PATH: '/order/detail.jsp'}]";
		List<ScreenMenu.Entry> entries = screenMenu.findEntries(json, rules);
		assertEquals(2, entries.size());
		assertEquals("주문 목록", entries.get(0).getName());
		assertEquals("주문 상세", entries.get(1).getName());

		List<ScreenMenu.Entry> xml = screenMenu.findEntries("<menus><menu MENU_NM=\"게시판\" SRC_PATH=\"/board/list.jsp\"/></menus>", rules);
		assertEquals(1, xml.size());
		assertEquals("게시판", xml.get(0).getName());
	}

	@Test
	public void 다른_항목의_이름이나_비슷한_키는_쓰지_않는다() {
		// 이름이 없는 항목은 옆 항목의 이름을 가져오지 않는다. SUB_MENU_NM 은 MENU_NM 이 아니다.
		String text = "{'MENU_NM':'앞 메뉴','SRC_PATH':''},{'SUB_MENU_NM':'하위','SRC_PATH':'/a/B.xml'}";
		assertTrue(screenMenu.findEntries(text, rules).isEmpty());
	}

	@Test
	public void 주석_안의_메뉴는_건너뛴다() {
		String text = "// {'MENU_NM':'옛 메뉴','SRC_PATH':'/old/A.xml'}\n"
				+ "<!-- <menu MENU_NM=\"옛 메뉴\" SRC_PATH=\"/old/B.jsp\"/> -->\n"
				+ "{'MENU_NM':'새 메뉴','SRC_PATH':'/new/A.xml'}";
		List<ScreenMenu.Entry> entries = screenMenu.findEntries(text, rules);
		assertEquals(1, entries.size());
		assertEquals("새 메뉴", entries.get(0).getName());
	}
}

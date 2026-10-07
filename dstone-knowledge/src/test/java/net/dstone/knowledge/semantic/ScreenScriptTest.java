package net.dstone.knowledge.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 리치클라이언트 화면에서 "서버를 부르는 곳"을 찾는 로직을 확인합니다.
 * DB나 Spring 없이 도는 테스트입니다. 표본은 samples/websquare-app, samples/nexacro-app 입니다.
 * </pre>
 */
public class ScreenScriptTest {

	private static final String WEBSQUARE_APP = "src/test/resources/samples/websquare-app/";
	private static final String NEXACRO_APP = "src/test/resources/samples/nexacro-app/";

	private final ScreenScript screenScript = new ScreenScript();

	@Test
	public void 규칙_글자를_읽는다() {
		List<ScreenScript.CallRule> rules = ScreenScript.parseRules(" doRequestAjax=perform{id} , gfnTran = {id}Service ,잘못된것, noId=perform ");
		assertEquals(2, rules.size());
		assertEquals("doRequestAjax", rules.get(0).getFunction());
		assertEquals("performORD0100M01S", rules.get(0).methodNameOf("ORD0100M01S"));
		assertEquals("ORD0100M01SService", rules.get(1).methodNameOf("ORD0100M01S"));
		// 메소드 이름이 될 수 없는 글자가 섞이면 잇지 않는다.
		assertNull(rules.get(0).methodNameOf("order.list"));
		assertTrue(ScreenScript.parseRules(null).isEmpty());
	}

	@Test
	public void 거래_호출을_찾고_주석과_변수는_가려낸다() throws Exception {
		String text = read(WEBSQUARE_APP + "ui/order/OrderList.xml");
		List<ScreenScript.Call> calls = screenScript.findCalls(text, ScreenScript.parseRules("doRequestAjax=perform{id}"));

		List<String> ids = new ArrayList<String>();
		int unknown = 0;
		for (int i = 0; i < calls.size(); i++) {
			if (calls.get(i).getId() == null) {
				unknown++;
			} else {
				ids.add(calls.get(i).getId());
			}
		}
		// 주석 안의 ORD0100M03D 는 없어야 한다. 변수로 넘긴 호출은 ID 없이 한 건.
		assertEquals("[ORD0100M01S, ORD0100M02U, ORD0100M09S]", ids.toString());
		assertEquals(1, unknown);
		assertEquals(23, screenScript.lineAt(text, calls.get(0).getIndex()));
	}

	@Test
	public void 함수를_만드는_곳과_이름이_다른_함수는_호출이_아니다() {
		List<ScreenScript.CallRule> rules = ScreenScript.parseRules("doRequestAjax=perform{id}");
		assertTrue(screenScript.findCalls("function doRequestAjax(tranId) {}", rules).isEmpty());
		assertTrue(screenScript.findCalls("xdoRequestAjax(\"A1\")", rules).isEmpty());
		assertEquals(1, screenScript.findCalls("a = 1; doRequestAjax(\"A1\")", rules).size());
	}

	@Test
	public void 주소를_찾는다() throws Exception {
		assertTrue(texts(screenScript.findPaths(read(WEBSQUARE_APP + "ui/order/OrderList.xml"))).contains("/order/list.do"));
		assertTrue(texts(screenScript.findPaths(read(WEBSQUARE_APP + "ui/order/OrderPopup.xml"))).contains("/shop/order/detail.do"));

		// Nexacro: 서비스 접두어를 떼고 앞에 /를 붙인다. 주석 안의 save.do 는 없어야 한다.
		List<String> list = texts(screenScript.findPaths(read(NEXACRO_APP + "nxui/order/OrderList.xfdl")));
		assertTrue(list.contains("/order/list.do"));
		assertFalse(list.contains("/order/save.do"));
		assertTrue(texts(screenScript.findPaths(read(NEXACRO_APP + "nxui/order/OrderDetail.xfdl"))).contains("/order/save.do"));
	}

	@Test
	public void 다른_화면을_가리키는_경로를_찾는다() throws Exception {
		List<String> refs = texts(screenScript.findScreenRefs(read(WEBSQUARE_APP + "ui/order/OrderList.xml"), new String[] { ".xml" }));
		// ?mode=view 는 떼고 돌려준다.
		assertEquals("[/shop/ui/order/OrderPopup.xml, /shop/ui/common/Header.xml]", refs.toString());

		refs = texts(screenScript.findScreenRefs(read(NEXACRO_APP + "nxui/order/OrderList.xfdl"), new String[] { ".xfdl" }));
		assertEquals("[order::OrderDetail.xfdl]", refs.toString());
	}

	@Test
	public void 주석인지_가려낸다() {
		assertTrue(screenScript.inComment("a(); // b();", 8));
		assertFalse(screenScript.inComment("a(); // b();\nc();", 13));
		assertTrue(screenScript.inComment("/* a(); */ b(); /* c();", 19));
		assertFalse(screenScript.inComment("/* a(); */ b();", 11));
		assertTrue(screenScript.inComment("<!-- <a/> -->\n<!-- <b/>", 19));
		// http:// 의 //는 주석이 아니다.
		assertFalse(screenScript.inComment("url = \"http://host/a.do\"; b();", 26));
	}

	@Test
	public void 큰_화면_파일도_금방_끝난다() {
		// 실제 프로젝트에 2MB가 넘는 화면이 있었다. 찾은 것마다 앞쪽을 거슬러 훑으면 몇 분이 걸린다.
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 150000; i++) {
			sb.append("<w2:column id=\"c").append(i).append("\" src=\"/a/b").append(i).append(".do\"/>\n");
		}
		final String text = sb.toString();
		org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(10), new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				assertEquals(150000, screenScript.findPaths(text).size());
			}
		});
	}

	private List<String> texts(List<ScreenScript.Hit> hits) {
		List<String> texts = new ArrayList<String>();
		for (int i = 0; i < hits.size(); i++) {
			texts.add(hits.get(i).getText());
		}
		return texts;
	}

	private String read(String path) throws Exception {
		return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
	}

}

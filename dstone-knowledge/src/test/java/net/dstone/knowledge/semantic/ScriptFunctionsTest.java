package net.dstone.knowledge.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 공통 스크립트의 함수 정의와 그 함수를 부르는 곳을 찾는 로직을 확인합니다.
 * DB나 Spring 없이 도는 테스트입니다. 표본은 samples/websquare-app/js/shop_lib.js 입니다.
 * </pre>
 */
public class ScriptFunctionsTest {

	private static final String WEBSQUARE_APP = "src/test/resources/samples/websquare-app/";

	private final ScriptFunctions scriptFunctions = new ScriptFunctions();

	private final ScreenScript screenScript = new ScreenScript();

	@Test
	public void 공통_스크립트의_함수를_찾는다() throws Exception {
		String text = read("js/shop_lib.js");
		List<ScriptFunctions.Function> functions = scriptFunctions.findFunctions(text);
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < functions.size(); i++) {
			names.add(functions.get(i).getName());
		}
		// 주석 안의 예시, 함수 안의 success: function 은 함수로 보지 않는다.
		assertEquals("[shopLib.openPopup, shopLib.openOrderPopup, shopLib.searchOrder, shopLib.printOrder, formatAmount]", names.toString());

		// 본문은 정규식 글자(/["{]/)와 문자열 안의 중괄호에 속지 않고 제 닫는 중괄호에서 끝난다.
		String openOrderPopup = functions.get(1).getBody();
		assertTrue(openOrderPopup.startsWith("{"));
		assertTrue(openOrderPopup.endsWith("}"));
		assertTrue(openOrderPopup.indexOf("/ui/order/OrderPopup.xml") > 0);
		assertFalse(openOrderPopup.indexOf("searchOrder") > 0);

		// 본문에 적힌 화면 경로. 주석으로 지운 옛 화면은 빠진다.
		List<ScreenScript.Hit> refs = screenScript.findScreenRefs(openOrderPopup, new String[] { ".xml" });
		assertEquals(1, refs.size());
		assertEquals("/ui/order/OrderPopup.xml", refs.get(0).getText());
	}

	@Test
	public void 공통_함수가_부르는_다른_공통_함수를_찾는다() throws Exception {
		String text = read("js/shop_lib.js");
		List<ScriptFunctions.Function> functions = scriptFunctions.findFunctions(text);
		String searchOrder = functions.get(2).getBody();
		Set<String> names = new HashSet<String>(Arrays.asList("shopLib.openPopup", "shopLib.openOrderPopup", "shopLib.searchOrder", "formatAmount"));
		List<ScriptFunctions.Call> calls = scriptFunctions.findCalls(searchOrder, names, scriptFunctions.nonCode(searchOrder));
		assertEquals(1, calls.size());
		assertEquals("shopLib.openOrderPopup", calls.get(0).getName());
	}

	@Test
	public void 화면에서_공통_함수_호출을_찾는다() throws Exception {
		String screen = read("ui/common/Header.xml");
		Set<String> names = new HashSet<String>(Arrays.asList("shopLib.searchOrder", "formatAmount"));
		List<ScriptFunctions.Call> calls = scriptFunctions.findCalls(screen, names, screenScript.commentMask(screen));
		assertEquals(1, calls.size());
		assertEquals("shopLib.searchOrder", calls.get(0).getName());
		assertEquals(8, screenScript.lineAt(screen, calls.get(0).getIndex()));
		assertFalse(scriptFunctions.definesItself(screen, "shopLib.searchOrder"));
	}

	@Test
	public void 이름은_뒤쪽이_같아야_맞는다() {
		Set<String> names = new HashSet<String>(Arrays.asList("comLib.gfnOpenPopupEmp", "gfnBare"));
		String text = "window.comLib.gfnOpenPopupEmp(a); otherLib.gfnOpenPopupEmp(b); gfnOpenPopupEmp(c);\n"
				+ "gfnBare(d); scwin.gfnBare(e); function gfnBare(f) {}\n"
				+ "// comLib.gfnOpenPopupEmp(g);\n"
				+ "var s = \"comLib.gfnOpenPopupEmp(h)\";";
		List<ScriptFunctions.Call> calls = scriptFunctions.findCalls(text, names, scriptFunctions.nonCode(text));
		List<String> found = new ArrayList<String>();
		for (int i = 0; i < calls.size(); i++) {
			found.add(calls.get(i).getName() + "@" + text.substring(calls.get(i).getIndex(), calls.get(i).getIndex() + 4));
		}
		// 다른 객체의 같은 이름, 앞에 무엇이 붙은 점 없는 이름, 정의, 주석, 문자열 안은 빠진다.
		assertEquals("[comLib.gfnOpenPopupEmp@wind, gfnBare@gfnB]", found.toString());
	}

	@Test
	public void 점_없는_이름은_파일_맨_바깥의_것만_함수로_본다() {
		String text = "(function() {\n"
				+ "  function local() { }\n"
				+ "  var inner = function() { };\n"
				+ "  lib.open = function() { lib.go(\"/ui/A.xml\"); };\n"
				+ "})();\n"
				+ "var top = function() { if (a) { b(); } };\n"
				+ "lib.broken = function() { if (a) { ";
		List<ScriptFunctions.Function> functions = scriptFunctions.findFunctions(text);
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < functions.size(); i++) {
			names.add(functions.get(i).getName());
		}
		// 닫히지 않은 함수(lib.broken)는 본문의 끝을 몰라 버린다.
		assertEquals("[lib.open, top]", names.toString());
		assertEquals("{ if (a) { b(); } }", functions.get(1).getBody());
	}

	@Test
	public void 화면이_스스로_만든_함수는_공통_함수가_아니다() {
		assertTrue(scriptFunctions.definesItself("comLib.gfnX = function(a) {}", "comLib.gfnX"));
		assertTrue(scriptFunctions.definesItself("function gfnY(a) {}", "gfnY"));
		assertFalse(scriptFunctions.definesItself("comLib.gfnX(a);", "comLib.gfnX"));
	}

	private String read(String path) throws Exception {
		return new String(Files.readAllBytes(Paths.get(WEBSQUARE_APP + path)), StandardCharsets.UTF_8);
	}

}

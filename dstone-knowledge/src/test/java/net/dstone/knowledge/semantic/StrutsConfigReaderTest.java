package net.dstone.knowledge.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * Struts 설정 파일을 읽는 규칙과, 주소·화면 경로를 만드는 규칙을 확인합니다.
 * </pre>
 */
public class StrutsConfigReaderTest {

	private final StrutsConfigReader reader = new StrutsConfigReader();

	@Test
	public void struts_app의_Struts_1_설정을_읽는다() throws Exception {
		String text = new String(Files.readAllBytes(Paths.get("src/test/resources/samples/struts-app/WEB-INF/struts-config.xml")), StandardCharsets.UTF_8);
		StrutsConfigReader.StrutsConfig config = reader.read(text);

		assertTrue(!config.struts2);
		// global-forwards 의 forward 는 요청을 받는 설정이 아니라서 세지 않는다.
		assertEquals(4, config.actions.size());

		StrutsConfigReader.Action list = config.actions.get(0);
		assertEquals("/board/list", list.path);
		assertEquals("com.sample.board.web.BoardListAction", list.className);
		assertEquals("/board/list.jsp", list.forwards.get("success"));

		StrutsConfigReader.Action save = config.actions.get(1);
		assertEquals("/board/list.do", save.forwards.get("success"));
		assertEquals("/board/edit.jsp", save.forwards.get("fail"));

		// 클래스 없이 화면으로 바로 넘기는 설정
		StrutsConfigReader.Action edit = config.actions.get(2);
		assertNull(edit.className);
		assertEquals("/board/edit.jsp", edit.forwards.get("forward"));

		// 요청 파라미터로 메소드를 고르는 방식
		assertEquals("cmd", config.actions.get(3).parameter);
	}

	@Test
	public void Struts_2_설정은_네임스페이스와_이름을_이어_주소를_만든다() throws Exception {
		String text = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<!DOCTYPE struts PUBLIC \"-//Apache Software Foundation//DTD Struts Configuration 2.3//EN\" \"http://no-such-host.invalid/dtds/struts-2.3.dtd\">\n"
				+ "<struts>\n"
				+ "  <constant name=\"struts.action.extension\" value=\"do,action\"/>\n"
				+ "  <package name=\"root\" namespace=\"/\" extends=\"struts-default\">\n"
				+ "    <action name=\"index\"><result>/index.jsp</result></action>\n"
				+ "  </package>\n"
				+ "  <package name=\"order\" namespace=\"/order\" extends=\"struts-default\">\n"
				+ "    <action name=\"list\" class=\"com.x.OrderAction\" method=\"list\">\n"
				+ "      <result name=\"success\">/order/list.jsp</result>\n"
				+ "      <result name=\"input\"> /order/search.jsp </result>\n"
				+ "    </action>\n"
				+ "  </package>\n"
				+ "</struts>\n";
		StrutsConfigReader.StrutsConfig config = reader.read(text);

		assertTrue(config.struts2);
		assertEquals("do,action", config.constants.get("struts.action.extension"));
		assertEquals(2, config.actions.size());

		// 네임스페이스가 / 면 이름 앞에 /만 붙는다. 이름 없는 result는 success다.
		assertEquals("/index", config.actions.get(0).path);
		assertNull(config.actions.get(0).className);
		assertEquals("/index.jsp", config.actions.get(0).forwards.get("success"));

		StrutsConfigReader.Action list = config.actions.get(1);
		assertEquals("/order/list", list.path);
		assertEquals("com.x.OrderAction", list.className);
		assertEquals("list", list.method);
		assertEquals("/order/search.jsp", list.forwards.get("input"));
	}

	@Test
	public void Struts_설정이_아닌_XML에서는_아무것도_읽지_않는다() throws Exception {
		assertTrue(reader.read("<beans><bean id=\"a\" class=\"com.x.A\"/></beans>").actions.isEmpty());
	}

	@Test
	public void 서블릿에_걸어_둔_주소_모양을_설정의_path에_입힌다() {
		assertEquals("/order/list.do", StrutsPlugin.applyPattern("*.do", "/order/list"));
		assertEquals("/do/order/list", StrutsPlugin.applyPattern("/do/*", "/order/list"));
		assertEquals("/order/list", StrutsPlugin.applyPattern("/", "/order/list"));
	}

	@Test
	public void 화면_경로는_JSP일_때만_파일을_찾는_경로로_바꾼다() {
		assertEquals("order/list.jsp", StrutsPlugin.jspPathOf("/order/list.jsp"));
		assertEquals("order/list.jsp", StrutsPlugin.jspPathOf("/order/list.jsp?mode=1"));
		// 다른 주소로 보내는 것, Tiles 정의 이름, 실행할 때 정해지는 경로는 JSP 파일이 아니다.
		assertNull(StrutsPlugin.jspPathOf("/order/list.do"));
		assertNull(StrutsPlugin.jspPathOf("order.list"));
		assertNull(StrutsPlugin.jspPathOf("/order/${view}.jsp"));
	}

}

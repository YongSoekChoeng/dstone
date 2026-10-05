package net.dstone.knowledge.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * JSP 안의 Java 코드를 Java 소스로 바꾸는 규칙을 확인합니다.
 * </pre>
 */
public class JspToJavaTest {

	@Test
	public void legacy_app의_JSP를_바꾸면_문법에_맞고_줄_번호가_원본과_같다() throws Exception {
		// 표본은 일부러 EUC-KR로 저장돼 있다.
		String jsp = new String(Files.readAllBytes(Paths.get("src/test/resources/samples/legacy-app/order/list.jsp")), Charset.forName("EUC-KR"));
		String java = JspToJava.convert(jsp, "order/list.jsp");

		assertTrue(java.startsWith("package jsp.order; "));
		assertTrue(java.contains("import com.legacy.order.service.OrderServiceImpl; "));
		assertTrue(java.contains("public class list_jsp { "));
		assertTrue(new JavaSourceParser().parse(java, "1.4").isSuccessful(), "바꾼 소스는 Java 문법에 맞아야 한다.");

		// 스크립틀릿의 호출은 원본 JSP와 같은 줄에 있어야 한다. 그래야 분석 결과의 위치가 JSP를 가리킨다.
		assertEquals(lineOf(jsp, "new OrderServiceImpl()"), lineOf(java, "new OrderServiceImpl()"));
		// <%= 식 %> 은 out.print(식); 으로 바뀌고, 역시 같은 줄이다.
		assertTrue(java.contains("out.print( vo.getAmount() );"));
		assertEquals(lineOf(jsp, "vo.getAmount()"), lineOf(java, "vo.getAmount()"));
	}

	@Test
	public void Java_코드가_없는_JSP는_바꿀_것이_없다() {
		String jsp = "<%@ page contentType=\"text/html\" %>\n"
				+ "<%-- 주석 안의 <% int a = 1; %> 는 코드가 아니다 --%>\n"
				+ "<c:forEach items=\"${orders}\" var=\"o\">${o.name}</c:forEach>\n";
		assertNull(JspToJava.convert(jsp, "order/view.jsp"));
	}

	@Test
	public void 선언은_클래스의_멤버로_useBean은_변수_선언으로_바꾼다() {
		String jsp = "<%@ page import=\"shop.Cart, shop.Item\" %>\n"
				+ "<%! private int count(Cart c) { return c.size(); } %>\n"
				+ "<jsp:useBean id=\"cart\"\n"
				+ "             class=\"shop.Cart\" scope=\"session\"/>\n"
				+ "<b><%= count(cart) %></b>\n";
		String java = JspToJava.convert(jsp, "shop/cart.jsp");

		assertTrue(java.contains("import shop.Cart; import shop.Item; "));
		assertTrue(java.contains("shop.Cart cart = new shop.Cart();"));
		// 선언은 _jspService() 밖, 클래스의 멤버 자리에 온다.
		assertTrue(java.indexOf("private int count(Cart c)") > java.indexOf("\n}\n"));
		assertTrue(new JavaSourceParser().parse(java, "8").isSuccessful());
		// 두 줄에 걸친 useBean 태그 뒤에서도 줄 번호가 밀리지 않는다.
		assertEquals(5, lineOf(java, "count(cart)"));
	}

	@Test
	public void 경로로_패키지와_클래스_이름을_만든다() {
		assertEquals("jsp", JspToJava.packageOf("index.jsp"));
		assertEquals("jsp.WEB_INF.views.order", JspToJava.packageOf("WEB-INF/views/order/list.jsp"));
		assertEquals("list_jsp", JspToJava.classNameOf("WEB-INF/views/order/list.jsp"));
		// 폴더 이름이 Java 예약어이거나 숫자로 시작하면 이름으로 쓸 수 있게 고친다.
		assertEquals("jsp.new_._2024", JspToJava.packageOf("new/2024/a.jsp"));
		assertEquals("_1st_page_jsp", JspToJava.classNameOf("1st-page.jsp"));
	}

	@Test
	public void JSP로_다룰_파일을_확장자로_알아본다() {
		assertTrue(JspToJava.isJsp("a/b.JSP"));
		assertTrue(JspToJava.isJsp("a/header.jspf"));
		assertTrue(JspToJava.isJsp("WEB-INF/tags/box.tag"));
		assertTrue(!JspToJava.isJsp("a/b.java"));
	}

	/** 글에서 이 문구가 처음 나오는 줄 번호(1부터) */
	private int lineOf(String text, String phrase) {
		int index = text.indexOf(phrase);
		assertTrue(index >= 0, "문구가 없다: " + phrase);
		int line = 1;
		for (int i = 0; i < index; i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

}

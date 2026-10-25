package net.dstone.knowledge.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 화면 파일에서 화면명을 찾는 로직을 확인합니다. DB나 Spring 없이 도는 테스트입니다.
 * </pre>
 */
public class ScreenTitleTest {

	@Test
	public void JSP의_title을_읽는다() throws Exception {
		String text = new String(Files.readAllBytes(Paths.get("src/test/resources/samples/struts-app/board/list.jsp")), StandardCharsets.UTF_8);
		ScreenTitle title = ScreenTitle.of(text);
		assertEquals("게시글 목록", title.getName());
		assertEquals(ScreenTitle.VIA_TITLE, title.getVia());
		assertEquals(4, title.getLine());
	}

	@Test
	public void Nexacro의_titletext를_읽는다() throws Exception {
		String text = new String(Files.readAllBytes(Paths.get("src/test/resources/samples/nexacro-app/nxui/order/OrderList.xfdl")), StandardCharsets.UTF_8);
		ScreenTitle title = ScreenTitle.of(text);
		assertEquals("주문 목록", title.getName());
		assertEquals(ScreenTitle.VIA_TITLETEXT, title.getVia());
		assertEquals(3, title.getLine());
	}

	@Test
	public void 줄바꿈과_문자_참조를_정리한다() {
		assertEquals("주문 & 배송 조회", ScreenTitle.of("<html><head>\n<title>\n  주문 &amp;\n  배송 조회\n</title>").getName());
		assertEquals("주문 목록", ScreenTitle.of("<Form id='a' titletext='주문 목록'>").getName());
	}

	@Test
	public void 실행할_때_정해지는_제목은_쓰지_않는다() {
		assertNull(ScreenTitle.of("<title>${pageTitle}</title>"));
		assertNull(ScreenTitle.of("<title><%= title %></title>"));
		assertNull(ScreenTitle.of("<title><spring:message code=\"board.title\"/></title>"));
		assertNull(ScreenTitle.of("<title>  </title>"));
		assertNull(ScreenTitle.of("<html><body>제목 없음</body></html>"));
	}

	@Test
	public void 주석_안의_제목은_건너뛴다() {
		ScreenTitle title = ScreenTitle.of("<!-- <title>옛 화면</title> -->\n<title>새 화면</title>");
		assertEquals("새 화면", title.getName());
		assertEquals(2, title.getLine());
		assertNull(ScreenTitle.of("<!-- <title>옛 화면</title> -->"));
	}
}

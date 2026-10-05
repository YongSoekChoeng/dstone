package net.dstone.knowledge.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * web.xml에서 서블릿, 필터, 리스너 설정을 읽는 규칙을 확인합니다.
 * </pre>
 */
public class WebXmlReaderTest {

	private final WebXmlReader reader = new WebXmlReader();

	@Test
	public void legacy_app의_web_xml에서_서블릿_매핑을_읽는다() throws Exception {
		// 표본은 일부러 EUC-KR로 저장돼 있다.
		String text = new String(Files.readAllBytes(Paths.get("src/test/resources/samples/legacy-app/WEB-INF/web.xml")), Charset.forName("EUC-KR"));
		WebXmlReader.WebXml webXml = reader.read(text);

		assertEquals("com.legacy.order.web.OrderServlet", webXml.servletClasses.get("orderServlet"));
		assertEquals(1, webXml.servletMappings.size());
		assertEquals("orderServlet", webXml.servletMappings.get(0)[0]);
		assertEquals("/order.do", webXml.servletMappings.get(0)[1]);
	}

	@Test
	public void 인터넷의_DTD를_적어_둔_구버전_web_xml도_밖에_접속하지_않고_읽는다() throws Exception {
		// 서블릿 2.3 방식: DOCTYPE에 DTD 주소가 있다. 실제로 접속을 시도하면 이 주소는 없는 곳이라 실패하거나 오래 걸린다.
		String text = "<?xml version=\"1.0\" encoding=\"EUC-KR\"?>\n"
				+ "<!DOCTYPE web-app PUBLIC \"-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN\" \"http://no-such-host.invalid/dtd/web-app_2_3.dtd\">\n"
				+ "<web-app>\n"
				+ "  <servlet><servlet-name>a</servlet-name><servlet-class> com.x.AServlet </servlet-class></servlet>\n"
				+ "  <servlet-mapping><servlet-name>a</servlet-name><url-pattern>*.do</url-pattern></servlet-mapping>\n"
				+ "</web-app>";
		long startedAt = System.currentTimeMillis();
		WebXmlReader.WebXml webXml = reader.read(text);

		assertTrue(System.currentTimeMillis() - startedAt < 3000, "밖에 접속하지 않으므로 바로 끝나야 한다.");
		// 앞뒤 공백은 떼어 낸다.
		assertEquals("com.x.AServlet", webXml.servletClasses.get("a"));
		assertEquals("*.do", webXml.servletMappings.get(0)[1]);
	}

	@Test
	public void 주소를_여러_개_적은_매핑과_필터_리스너를_읽는다() throws Exception {
		String text = "<web-app xmlns=\"http://java.sun.com/xml/ns/javaee\" version=\"2.5\">\n"
				+ "  <listener><listener-class>com.x.StartListener</listener-class></listener>\n"
				+ "  <filter><filter-name>enc</filter-name><filter-class>com.x.EncodingFilter</filter-class></filter>\n"
				+ "  <filter-mapping><filter-name>enc</filter-name><url-pattern>/*</url-pattern><servlet-name>app</servlet-name></filter-mapping>\n"
				+ "  <servlet><servlet-name>app</servlet-name><servlet-class>com.x.AppServlet</servlet-class></servlet>\n"
				+ "  <servlet><servlet-name>page</servlet-name><jsp-file>/page.jsp</jsp-file></servlet>\n"
				+ "  <servlet-mapping><servlet-name>app</servlet-name><url-pattern>*.do</url-pattern><url-pattern>*.ajax</url-pattern></servlet-mapping>\n"
				+ "</web-app>";
		WebXmlReader.WebXml webXml = reader.read(text);

		// 매핑 하나에 주소를 둘 적으면 두 건이 된다.
		assertEquals(2, webXml.servletMappings.size());
		assertEquals("*.ajax", webXml.servletMappings.get(1)[1]);
		// 클래스 대신 JSP 파일을 적은 서블릿은 클래스 목록에 넣지 않는다.
		assertEquals(1, webXml.servletClasses.size());
		assertEquals("com.x.EncodingFilter", webXml.filterClasses.get("enc"));
		// 필터는 주소로도, 서블릿 이름으로도 걸 수 있다.
		assertEquals(2, webXml.filterMappings.size());
		assertEquals("servlet:app", webXml.filterMappings.get(1)[1]);
		assertEquals("com.x.StartListener", webXml.listenerClasses.get(0));
	}

}

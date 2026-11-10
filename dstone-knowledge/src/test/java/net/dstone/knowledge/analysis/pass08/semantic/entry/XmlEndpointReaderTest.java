package net.dstone.knowledge.analysis.pass08.semantic.entry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.dstone.knowledge.analysis.pass08.semantic.rule.Rule;
import net.dstone.knowledge.analysis.pass08.semantic.rule.RuleFileParser;

/**
 * <pre>
 * 설정 XML 에서 진입점(주소 · 클래스 · 메소드)을 꺼내는 XmlEndpointReader 를 확인합니다.
 * </pre>
 */
public class XmlEndpointReaderTest {

	private final XmlEndpointReader reader = new XmlEndpointReader();

	/** 규칙 파일의 entry 한 줄을 읽어 XML 진입점 규칙을 만든다 */
	private Rule.XmlEntry xmlRule(String type, String xml, String path) {
		String yaml = "entry:\n  " + type + ":\n    - file: 'x'\n      xml: '" + xml + "'\n" + (path == null ? "" : "      path: \"" + path + "\"\n");
		return new RuleFileParser().parse(yaml, "projects/test/backend.yml").xmlEntries.get(0);
	}

	@Test
	public void 요소의_속성에서_주소와_클래스_메소드를_꺼내고_줄_번호를_센다() throws Exception {
		String xml = "<?xml version=\"1.0\"?>\n"
				+ "<services>\n"
				+ "  <!-- <service id=\"OLD\" class=\"x.Old\" method=\"a\"/> -->\n"
				+ "  <service id=\"ORD001\" class=\"com.x.OrderService\" method=\"list\"/>\n"
				+ "  <svc:service id=\"${dynamic}\" class=\"com.x.OrderService\" method=\"save\"/>\n"
				+ "  <service\n     id=\"ORD003\" class=\"OrderService\"/>\n"
				+ "</services>\n";
		Rule.XmlEntry rule = xmlRule("TRANSACTION", "<service id=\"KEY\" class=\"CLASS\" method=\"METHOD\"/>", null);
		List<XmlEndpointReader.Entry> entries = reader.read(xml, rule);
		// 주석 안의 요소는 없는 것이고, ${...} 처럼 실행할 때 정해지는 주소는 건너뛴다.
		assertEquals(2, entries.size());
		assertEquals("ORD001", entries.get(0).path);
		assertEquals("com.x.OrderService", entries.get(0).className);
		assertEquals("list", entries.get(0).methodName);
		assertEquals(Integer.valueOf(4), entries.get(0).line);
		assertEquals("ORD003", entries.get(1).path);
		assertNull(entries.get(1).methodName);
		assertEquals(Integer.valueOf(6), entries.get(1).line);
	}

	@Test
	public void 클래스는_바깥_요소에서도_찾고_주소에_틀을_입힌다() throws Exception {
		String xml = "<controllers>\n"
				+ "  <controller class=\"com.x.OrderController\">\n"
				+ "    <action url=\"/order/list\" method=\"list\"/>\n"
				+ "    <action url=\"/order/save\" method=\"save\"/>\n"
				+ "  </controller>\n"
				+ "</controllers>\n";
		// 클래스(CLASS)는 action 요소에 없고 바깥의 controller 요소에 있다.
		Rule.XmlEntry rule = xmlRule("HTTP", "<action url=\"KEY\" class=\"CLASS\" method=\"METHOD\"/>", "{KEY}.do");
		assertEquals("ALL", rule.httpMethod);
		List<XmlEndpointReader.Entry> entries = reader.read(xml, rule);
		assertEquals(2, entries.size());
		assertEquals("/order/list.do", entries.get(0).path);
		assertEquals("com.x.OrderController", entries.get(1).className);
		assertEquals("save", entries.get(1).methodName);
	}

}

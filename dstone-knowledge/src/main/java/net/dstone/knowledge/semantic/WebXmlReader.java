package net.dstone.knowledge.semantic;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

/**
 * <pre>
 * web.xml에서 서블릿, 필터, 리스너의 설정을 읽습니다.
 *
 * 구버전 웹 애플리케이션은 "어느 주소가 어느 클래스로 가는지"를 소스가 아니라 web.xml에 적습니다.
 * 그래서 서블릿의 주소는 여기서 읽어야 알 수 있습니다.
 *
 * 오래된 web.xml(서블릿 2.3 이하)은 맨 위에 DOCTYPE으로 인터넷의 DTD 주소를 적어 둡니다.
 * 그대로 파싱하면 그 주소로 접속하려다 멈추거나 실패하므로, 밖의 것은 아무것도 가져오지 않도록 막아 둡니다.
 * </pre>
 */
public class WebXmlReader {

	/**
	 * <pre>
	 * web.xml 하나를 읽은 결과
	 * </pre>
	 */
	public static class WebXml {

		/** 서블릿 이름 → 서블릿 클래스의 전체 이름 */
		public final Map<String, String> servletClasses = new LinkedHashMap<String, String>();

		/** [서블릿 이름, 주소 패턴] */
		public final List<String[]> servletMappings = new ArrayList<String[]>();

		/** 필터 이름 → 필터 클래스의 전체 이름 */
		public final Map<String, String> filterClasses = new LinkedHashMap<String, String>();

		/** [필터 이름, 주소 패턴 또는 서블릿 이름] */
		public final List<String[]> filterMappings = new ArrayList<String[]>();

		/** 리스너 클래스의 전체 이름 */
		public final List<String> listenerClasses = new ArrayList<String>();
	}

	/**
	 * @param text web.xml의 내용(이미 올바른 인코딩으로 읽은 글)
	 */
	public WebXml read(String text) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// 이름공간을 따지지 않는다. 버전마다 이름공간이 달라도 요소 이름(servlet, servlet-mapping ...)은 같다.
		factory.setNamespaceAware(false);
		factory.setValidating(false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);

		DocumentBuilder builder = factory.newDocumentBuilder();
		// DOCTYPE에 적힌 밖의 DTD를 달라고 하면 빈 내용을 준다.
		builder.setEntityResolver(new EntityResolver() {
			@Override
			public InputSource resolveEntity(String publicId, String systemId) {
				return new InputSource(new StringReader(""));
			}
		});
		// 글 맨 앞의 XML 선언에 적힌 encoding은 이미 글자로 바꾼 뒤라 의미가 없다. StringReader로 주면 무시된다.
		Document document = builder.parse(new InputSource(new StringReader(text)));

		WebXml webXml = new WebXml();
		List<Element> servlets = elementsOf(document, "servlet");
		for (int i = 0; i < servlets.size(); i++) {
			String name = childText(servlets.get(i), "servlet-name");
			String className = childText(servlets.get(i), "servlet-class");
			// servlet-class 대신 jsp-file을 적은 서블릿도 있다. 클래스가 없으면 넘어간다.
			if (name != null && className != null) {
				webXml.servletClasses.put(name, className);
			}
		}
		addMappings(document, "servlet-mapping", "servlet-name", webXml.servletMappings, false);

		List<Element> filters = elementsOf(document, "filter");
		for (int i = 0; i < filters.size(); i++) {
			String name = childText(filters.get(i), "filter-name");
			String className = childText(filters.get(i), "filter-class");
			if (name != null && className != null) {
				webXml.filterClasses.put(name, className);
			}
		}
		addMappings(document, "filter-mapping", "filter-name", webXml.filterMappings, true);

		List<Element> listeners = elementsOf(document, "listener");
		for (int i = 0; i < listeners.size(); i++) {
			String className = childText(listeners.get(i), "listener-class");
			if (className != null) {
				webXml.listenerClasses.add(className);
			}
		}
		return webXml;
	}

	/** 매핑 하나에 url-pattern을 여러 개 적을 수 있다(서블릿 2.5부터). 패턴마다 한 건으로 편다. */
	private void addMappings(Document document, String mappingTag, String nameTag, List<String[]> sink, boolean alsoServletName) {
		List<Element> mappings = elementsOf(document, mappingTag);
		for (int i = 0; i < mappings.size(); i++) {
			String name = childText(mappings.get(i), nameTag);
			if (name == null) {
				continue;
			}
			List<String> patterns = childTexts(mappings.get(i), "url-pattern");
			if (alsoServletName) {
				// 필터는 주소 대신 "이 서블릿으로 가는 요청"이라고 적을 수도 있다.
				List<String> servletNames = childTexts(mappings.get(i), "servlet-name");
				for (int s = 0; s < servletNames.size(); s++) {
					patterns.add("servlet:" + servletNames.get(s));
				}
			}
			for (int p = 0; p < patterns.size(); p++) {
				sink.add(new String[] { name, patterns.get(p) });
			}
		}
	}

	private List<Element> elementsOf(Document document, String tagName) {
		List<Element> elements = new ArrayList<Element>();
		NodeList nodes = document.getElementsByTagName(tagName);
		for (int i = 0; i < nodes.getLength(); i++) {
			elements.add((Element) nodes.item(i));
		}
		return elements;
	}

	private String childText(Element parent, String tagName) {
		List<String> texts = childTexts(parent, tagName);
		return texts.isEmpty() ? null : texts.get(0);
	}

	/** 바로 아래 자식 요소 가운데 이 이름인 것들의 글(앞뒤 공백 제거, 빈 것은 뺌) */
	private List<String> childTexts(Element parent, String tagName) {
		List<String> texts = new ArrayList<String>();
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
				String text = child.getTextContent();
				if (text != null && text.trim().length() > 0) {
					texts.add(text.trim());
				}
			}
		}
		return texts;
	}

}

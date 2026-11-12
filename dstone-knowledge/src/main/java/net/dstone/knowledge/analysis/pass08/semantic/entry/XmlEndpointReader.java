package net.dstone.knowledge.analysis.pass08.semantic.entry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.dstone.knowledge.common.util.SafeXml;
import net.dstone.knowledge.analysis.pass08.semantic.rule.Rule;

/**
 * <pre>
 * 설정 XML 한 파일에서 규칙(entry 의 { file, xml })이 말하는 요소를 찾아 "주소 · 클래스 · 메소드"를 꺼냅니다.
 * DB 도 Spring 도 쓰지 않는 순수한 클래스라 단위 테스트로 확인합니다. DB 에 넣는 일은 EntryFinder 가 합니다.
 *
 * - 요소 이름은 네임스페이스 앞붙이를 떼고 맞춥니다(&lt;svc:service&gt; 도 service).
 * - 클래스 · 메소드 속성은 그 요소에 없으면 바깥 요소에서 찾습니다(&lt;controller class="…"&gt;&lt;action id="…" method="…"/&gt;).
 * - 주소 속성이 비었거나 ${…} 처럼 실행할 때 정해지는 값이면 건너뜁니다(짐작하지 않는다).
 * - 줄 번호는 글에서 같은 이름의 여는 태그를 차례로 세어 맞춥니다(DOM 은 줄 번호를 주지 않는다).
 * </pre>
 */
public class XmlEndpointReader {

	/** 요소 하나에서 꺼낸 진입점 */
	public static class Entry {
		/** 틀을 입힌 주소(또는 거래 ID) */
		public final String path;
		/** 처리 클래스. 속성이 없으면 null */
		public final String className;
		/** 처리 메소드. 속성이 없으면 null */
		public final String methodName;
		/** 요소가 있는 줄(1부터). 못 세면 null */
		public final Integer line;

		Entry(String path, String className, String methodName, Integer line) {
			this.path = path;
			this.className = className;
			this.methodName = methodName;
			this.line = line;
		}
	}

	/**
	 * <pre>
	 * 설정 XML 하나에서 규칙에 적힌 요소를 모두 찾아 진입점 후보로 만듭니다.
	 *
	 *   - 줄 번호: DOM 은 줄을 주지 않으므로 원본 글에서 같은 이름의 여는 태그를 차례로 세어 맞춘다.
	 *   - 주소: 규칙의 KEY 속성 값에 접두어·접미어를 붙인다. ${...} 처럼 실행할 때 정해지는 값은 건너뛴다.
	 *   - 클래스·메소드: 그 요소나 가장 가까운 바깥 요소의 속성에서 읽는다.
	 * </pre>
	 *
	 * @param text XML 의 내용(올바른 인코딩으로 읽은 글)
	 * @throws Exception XML 이 아니거나 깨졌을 때
	 */
	public List<Entry> read(String text, Rule.XmlEntry rule) throws Exception {
		Document document = SafeXml.parse(text);
		List<Integer> lines = openTagLines(text, rule.element);
		List<Entry> entries = new ArrayList<Entry>();
		NodeList all = document.getElementsByTagName("*");
		int seen = 0;
		for (int i = 0; i < all.getLength(); i++) {
			Element element = (Element) all.item(i);
			if (!rule.element.equals(localName(element.getNodeName()))) {
				continue;
			}
			Integer line = seen < lines.size() ? lines.get(seen) : null;
			seen++;
			String value = element.getAttribute(rule.pathAttribute).trim();
			if (value.length() == 0 || value.indexOf("${") >= 0 || value.indexOf("#{") >= 0) {
				continue;
			}
			String className = rule.classAttribute == null ? null : nearestAttribute(element, rule.classAttribute);
			String methodName = rule.methodAttribute == null ? null : nearestAttribute(element, rule.methodAttribute);
			entries.add(new Entry(rule.prefix + value + rule.suffix, className, methodName, line));
		}
		return entries;
	}

	/** 이 요소나 바깥 요소 가운데 가장 가까운 곳의 속성 값. 없으면 null */
	static String nearestAttribute(Element element, String attribute) {
		Node node = element;
		while (node != null && node.getNodeType() == Node.ELEMENT_NODE) {
			Element e = (Element) node;
			if (e.hasAttribute(attribute)) {
				String value = e.getAttribute(attribute).trim();
				return value.length() == 0 || value.indexOf("${") >= 0 ? null : value;
			}
			node = node.getParentNode();
		}
		return null;
	}

	/** 접두어를 뗀 요소 이름: beans:bean → bean */
	static String localName(String nodeName) {
		int colon = nodeName.indexOf(':');
		return colon < 0 ? nodeName : nodeName.substring(colon + 1);
	}

	/** 글에서 이 이름의 여는 태그가 있는 줄들(나온 차례). 주석 안의 것은 세지 않는다(DOM 도 세지 않으므로 차례가 맞는다) */
	static List<Integer> openTagLines(String text, String element) {
		List<Integer> lines = new ArrayList<Integer>();
		Matcher m = Pattern.compile("<(?:[A-Za-z_][A-Za-z0-9_.-]*:)?" + Pattern.quote(element) + "(?=[\\s/>])").matcher(stripComments(text));
		int line = 1;
		int from = 0;
		while (m.find()) {
			for (int i = from; i < m.start(); i++) {
				if (text.charAt(i) == '\n') {
					line++;
				}
			}
			from = m.start();
			lines.add(Integer.valueOf(line));
		}
		return lines;
	}

	/** 주석을 같은 길이의 빈칸으로 바꿉니다(줄 바꿈은 남긴다). 줄 번호를 그대로 셀 수 있게 하려는 것이다 */
	static String stripComments(String text) {
		StringBuilder sb = new StringBuilder(text);
		int start = text.indexOf("<!--");
		while (start >= 0) {
			int end = text.indexOf("-->", start + 4);
			int stop = end < 0 ? text.length() : end + 3;
			for (int i = start; i < stop; i++) {
				if (sb.charAt(i) != '\n') {
					sb.setCharAt(i, ' ');
				}
			}
			start = end < 0 ? -1 : text.indexOf("<!--", stop);
		}
		return sb.toString();
	}

}

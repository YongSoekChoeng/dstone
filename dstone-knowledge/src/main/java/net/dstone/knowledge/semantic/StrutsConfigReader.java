package net.dstone.knowledge.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.dstone.knowledge.common.util.SafeXml;

/**
 * <pre>
 * Struts 설정 파일에서 "어느 주소를 어느 클래스가 처리하고, 끝나면 어느 화면으로 가는지"를 읽습니다.
 * Struts 1(struts-config.xml)과 Struts 2(struts.xml)는 모양이 달라서 맨 위 요소로 구분합니다.
 *
 * Struts 1:
 *   &lt;action path="/order/list" type="com.x.OrderListAction" parameter="cmd"&gt;
 *       &lt;forward name="success" path="/order/list.jsp"/&gt;
 *   &lt;/action&gt;
 *
 * Struts 2:
 *   &lt;package name="order" namespace="/order"&gt;
 *       &lt;action name="list" class="com.x.OrderAction" method="list"&gt;
 *           &lt;result name="success"&gt;/order/list.jsp&lt;/result&gt;
 *       &lt;/action&gt;
 *   &lt;/package&gt;
 *
 * 여기서는 파일에 적힌 것만 읽습니다. 실제 주소(뒤에 .do가 붙는지 등)와 Java 메소드를 찾는 일은 StrutsPlugin이 합니다.
 * </pre>
 */
public class StrutsConfigReader {

	/**
	 * <pre>
	 * 설정 파일 하나에서 읽은 것
	 * </pre>
	 */
	public static class StrutsConfig {

		/** Struts 2면 true, Struts 1이면 false */
		public boolean struts2;

		public final List<Action> actions = new ArrayList<Action>();

		/** Struts 2의 constant 값들. 예: struts.action.extension = do */
		public final Map<String, String> constants = new LinkedHashMap<String, String>();
	}

	/**
	 * <pre>
	 * 요청 하나를 처리하는 설정
	 * </pre>
	 */
	public static class Action {

		/** Struts 1: path 그대로(/order/list). Struts 2: 네임스페이스 + 이름(/order/list) */
		public String path;

		/** 처리하는 클래스. 화면으로 바로 넘기는 설정이면 null */
		public String className;

		/** Struts 2에서 부를 메소드. 적지 않았으면 null(execute) */
		public String method;

		/** Struts 1의 parameter. 한 클래스가 여러 기능을 처리할 때 "어느 메소드를 부를지"를 담는 요청 파라미터의 이름 */
		public String parameter;

		/** 끝나고 가는 곳: 이름 → 경로. 예: success → /order/list.jsp */
		public final Map<String, String> forwards = new LinkedHashMap<String, String>();
	}

	public StrutsConfig read(String text) throws Exception {
		StrutsConfig config = new StrutsConfig();
		Document document = SafeXml.parse(text);
		Element root = document.getDocumentElement();
		if (root == null) {
			return config;
		}
		if ("struts".equals(root.getNodeName())) {
			config.struts2 = true;
			readStruts2(root, config);
		} else if ("struts-config".equals(root.getNodeName())) {
			readStruts1(root, config);
		}
		return config;
	}

	private void readStruts1(Element root, StrutsConfig config) {
		NodeList actions = root.getElementsByTagName("action");
		for (int i = 0; i < actions.getLength(); i++) {
			Element element = (Element) actions.item(i);
			String path = attribute(element, "path");
			if (path == null) {
				continue;
			}
			Action action = new Action();
			action.path = path;
			action.className = attribute(element, "type");
			action.parameter = attribute(element, "parameter");
			// 클래스 없이 화면으로 바로 넘기는 설정: forward="/main.jsp"
			String direct = attribute(element, "forward");
			if (direct != null) {
				action.forwards.put("forward", direct);
			}
			List<Element> forwards = childrenOf(element, "forward");
			for (int f = 0; f < forwards.size(); f++) {
				String forwardPath = attribute(forwards.get(f), "path");
				if (forwardPath != null) {
					action.forwards.put(nameOr(attribute(forwards.get(f), "name"), "forward" + f), forwardPath);
				}
			}
			config.actions.add(action);
		}
	}

	private void readStruts2(Element root, StrutsConfig config) {
		List<Element> constants = childrenOf(root, "constant");
		for (int i = 0; i < constants.size(); i++) {
			String name = attribute(constants.get(i), "name");
			if (name != null) {
				config.constants.put(name, constants.get(i).getAttribute("value"));
			}
		}
		List<Element> packages = childrenOf(root, "package");
		for (int p = 0; p < packages.size(); p++) {
			String namespace = attribute(packages.get(p), "namespace");
			if (namespace == null || "/".equals(namespace)) {
				namespace = "";
			}
			List<Element> actions = childrenOf(packages.get(p), "action");
			for (int i = 0; i < actions.size(); i++) {
				Element element = actions.get(i);
				String name = attribute(element, "name");
				if (name == null) {
					continue;
				}
				Action action = new Action();
				action.path = namespace + "/" + name;
				action.className = attribute(element, "class");
				action.method = attribute(element, "method");
				List<Element> results = childrenOf(element, "result");
				for (int r = 0; r < results.size(); r++) {
					String location = results.get(r).getTextContent() == null ? "" : results.get(r).getTextContent().trim();
					if (location.length() > 0) {
						// 이름을 적지 않은 result는 success다.
						action.forwards.put(nameOr(attribute(results.get(r), "name"), "success"), location);
					}
				}
				config.actions.add(action);
			}
		}
	}

	/** 바로 아래의 자식 요소 가운데 이름이 맞는 것 */
	private List<Element> childrenOf(Element parent, String tagName) {
		List<Element> found = new ArrayList<Element>();
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i).getNodeType() == Node.ELEMENT_NODE && tagName.equals(children.item(i).getNodeName())) {
				found.add((Element) children.item(i));
			}
		}
		return found;
	}

	private String attribute(Element element, String name) {
		String value = element.getAttribute(name);
		return value == null || value.trim().length() == 0 ? null : value.trim();
	}

	private String nameOr(String name, String fallback) {
		return name == null ? fallback : name;
	}

}

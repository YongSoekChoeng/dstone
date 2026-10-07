package net.dstone.knowledge.resource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.common.util.SafeXml;

/**
 * <pre>
 * 설정 파일, 빌드 파일, Spring XML에서 항목을 읽습니다. 파일 종류마다 메소드가 하나씩 있습니다.
 *
 * 읽은 것은 "나중에 물어볼 수 있는 사실"로 저장합니다.
 *   설정 값   → analysis_config   (예: spring.datasource.url = jdbc:...)
 *   의존성    → analysis_resource (DEPENDENCY: org.springframework:spring-core = 5.3.9)
 *   Spring 빈 → analysis_resource (SPRING_BEAN: dataSource = org.apache.commons.dbcp.BasicDataSource)
 *
 * 비밀번호처럼 보이는 설정 값은 저장하지 않습니다(값 대신 ****). 분석 결과는 검색되고 다른 곳으로 전달되는 데이터라서,
 * 분석 대상의 비밀 값이 거기 섞여 들어가면 안 됩니다.
 * </pre>
 */
public class ResourceReaders {

	/** 이런 글자가 든 키의 값은 비밀로 본다. */
	private static final Pattern SECRET_KEY = Pattern.compile("(?i)(password|passwd|pwd|secret|token|api[-_.]?key|private[-_.]?key|credential|access[-_.]?key)");

	/** 값 안의 ${...} */
	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

	/** Gradle의 의존성 줄. 예: implementation 'org.springframework:spring-core:5.3.9' */
	private static final Pattern GRADLE_DEPENDENCY = Pattern.compile(
			"(?m)^\\s*(implementation|api|compile|compileOnly|runtimeOnly|runtime|testImplementation|testCompile|annotationProcessor)\\s*\\(?\\s*['\"]([^'\":]+):([^'\":]+)(?::([^'\"]+))?['\"]");

	private static final int MAX_VALUE = 2000;

	/* ============================== 설정 ============================== */

	/**
	 * <pre>
	 * .properties 파일의 키와 값.
	 * </pre>
	 *
	 * @return 키: keyPath, value, valueType, placeholder, lineStart
	 */
	public List<Map<String, Object>> readProperties(String text) throws Exception {
		Properties properties = new Properties();
		properties.load(new StringReader(text));
		List<Map<String, Object>> configs = new ArrayList<Map<String, Object>>();
		List<String> keys = new ArrayList<String>(properties.stringPropertyNames());
		java.util.Collections.sort(keys);
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			configs.add(config(key, properties.getProperty(key), "STRING", lineOfKey(text, key)));
		}
		return configs;
	}

	/**
	 * <pre>
	 * YAML 파일의 값들. 중첩된 키는 점으로 이어 붙입니다(spring.datasource.url). 목록은 [0], [1]을 붙입니다.
	 * 한 파일에 문서가 여러 개(---)면 모두 읽습니다.
	 * </pre>
	 */
	public List<Map<String, Object>> readYaml(String text) {
		List<Map<String, Object>> configs = new ArrayList<Map<String, Object>>();
		// SafeConstructor: YAML에 적힌 대로 아무 Java 객체나 만들지 못하게 한다(분석 대상의 파일을 믿지 않는다).
		Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
		for (Object document : yaml.loadAll(text)) {
			flattenYaml("", document, configs);
		}
		return configs;
	}

	@SuppressWarnings("unchecked")
	private void flattenYaml(String prefix, Object value, List<Map<String, Object>> configs) {
		if (value instanceof Map) {
			for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) value).entrySet()) {
				String key = String.valueOf(entry.getKey());
				flattenYaml(prefix.length() == 0 ? key : prefix + "." + key, entry.getValue(), configs);
			}
		} else if (value instanceof List) {
			List<Object> list = (List<Object>) value;
			for (int i = 0; i < list.size(); i++) {
				flattenYaml(prefix + "[" + i + "]", list.get(i), configs);
			}
		} else if (prefix.length() > 0) {
			String type = value == null ? "NULL" : (value instanceof Number ? "NUMBER" : (value instanceof Boolean ? "BOOLEAN" : "STRING"));
			configs.add(config(prefix, value == null ? null : String.valueOf(value), type, null));
		}
	}

	private Map<String, Object> config(String key, String value, String valueType, Integer line) {
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("keyPath", key.length() > 1000 ? key.substring(0, 1000) : key);
		if (value != null && SECRET_KEY.matcher(key).find() && value.trim().length() > 0) {
			row.put("value", "****");
			row.put("valueType", "MASKED");
			row.put("placeholder", null);
		} else {
			row.put("value", value != null && value.length() > MAX_VALUE ? value.substring(0, MAX_VALUE) : value);
			row.put("valueType", valueType);
			Matcher m = value == null ? null : PLACEHOLDER.matcher(value);
			row.put("placeholder", m != null && m.find() ? m.group(1) : null);
		}
		row.put("lineStart", line);
		return row;
	}

	/** 키가 처음 나오는 줄. 못 찾으면 null */
	private Integer lineOfKey(String text, String key) {
		String[] lines = text.split("\r?\n", -1);
		for (int i = 0; i < lines.length; i++) {
			String trimmed = lines[i].trim();
			if (trimmed.startsWith(key) && trimmed.length() > key.length()) {
				char next = trimmed.charAt(key.length());
				if (next == '=' || next == ':' || Character.isWhitespace(next)) {
					return Integer.valueOf(i + 1);
				}
			}
		}
		return null;
	}

	/* ============================== 빌드 파일 ============================== */

	/**
	 * <pre>
	 * pom.xml의 의존성. dependencyManagement 안의 것은 "버전만 정해 둔 것"이라 properties_json에 managed로 표시합니다.
	 * </pre>
	 *
	 * @return 키: resourceType(DEPENDENCY), name(groupId:artifactId), value(버전), propertiesJson
	 */
	public List<Map<String, Object>> readPom(String text) throws Exception {
		List<Map<String, Object>> resources = new ArrayList<Map<String, Object>>();
		Document document = SafeXml.parse(text);
		NodeList dependencies = document.getElementsByTagName("dependency");
		for (int i = 0; i < dependencies.getLength(); i++) {
			Element dependency = (Element) dependencies.item(i);
			String groupId = childText(dependency, "groupId");
			String artifactId = childText(dependency, "artifactId");
			if (groupId == null || artifactId == null) {
				continue;
			}
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("scope", childText(dependency, "scope"));
			properties.put("managed", Boolean.valueOf(hasAncestor(dependency, "dependencyManagement")));
			if (hasAncestor(dependency, "plugin")) {
				properties.put("plugin", Boolean.TRUE);
			}
			resources.add(resource("DEPENDENCY", groupId + ":" + artifactId, childText(dependency, "version"), properties));
		}
		return resources;
	}

	/** build.gradle의 의존성. 문법을 다 따지지 않고 흔한 모양(implementation 'g:a:v')만 읽습니다. */
	public List<Map<String, Object>> readGradle(String text) {
		List<Map<String, Object>> resources = new ArrayList<Map<String, Object>>();
		Matcher m = GRADLE_DEPENDENCY.matcher(text);
		while (m.find()) {
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("scope", m.group(1));
			resources.add(resource("DEPENDENCY", m.group(2) + ":" + m.group(3), m.group(4), properties));
		}
		return resources;
	}

	/* ============================== Spring XML ============================== */

	/**
	 * <pre>
	 * Spring 설정 XML의 빈 정의와 컴포넌트 스캔 범위.
	 *   bean                   → SPRING_BEAN (name = id, value = class, properties_json에 property 값들)
	 *   context:component-scan → COMPONENT_SCAN (value = base-package)
	 *   import                 → SPRING_IMPORT (value = resource)
	 * </pre>
	 */
	public List<Map<String, Object>> readSpringXml(String text) throws Exception {
		List<Map<String, Object>> resources = new ArrayList<Map<String, Object>>();
		Document document = SafeXml.parse(text);

		NodeList beans = document.getElementsByTagName("*");
		for (int i = 0; i < beans.getLength(); i++) {
			Element element = (Element) beans.item(i);
			String tag = localName(element.getNodeName());
			if ("bean".equals(tag)) {
				String className = emptyToNull(element.getAttribute("class"));
				String id = emptyToNull(element.getAttribute("id"));
				if (id == null) {
					id = emptyToNull(element.getAttribute("name"));
				}
				if (className == null && id == null) {
					continue;
				}
				Map<String, Object> values = new LinkedHashMap<String, Object>();
				NodeList children = element.getChildNodes();
				for (int c = 0; c < children.getLength(); c++) {
					if (children.item(c).getNodeType() != Node.ELEMENT_NODE || !"property".equals(localName(children.item(c).getNodeName()))) {
						continue;
					}
					Element property = (Element) children.item(c);
					String name = property.getAttribute("name");
					String value = property.hasAttribute("ref") ? "ref:" + property.getAttribute("ref") : property.getAttribute("value");
					// 빈의 속성에도 DB 비밀번호 같은 것이 직접 적혀 있을 수 있다.
					values.put(name, SECRET_KEY.matcher(name).find() && value.length() > 0 ? "****" : value);
				}
				Map<String, Object> properties = new LinkedHashMap<String, Object>();
				properties.put("properties", values);
				if (element.hasAttribute("parent")) {
					properties.put("parent", element.getAttribute("parent"));
				}
				resources.add(resource("SPRING_BEAN", id == null ? className : id, className, properties));
			} else if ("component-scan".equals(tag)) {
				resources.add(resource("COMPONENT_SCAN", "component-scan", element.getAttribute("base-package"), new LinkedHashMap<String, Object>()));
			} else if ("import".equals(tag) && element.hasAttribute("resource")) {
				resources.add(resource("SPRING_IMPORT", "import", element.getAttribute("resource"), new LinkedHashMap<String, Object>()));
			}
		}
		return resources;
	}

	/* ============================== 도구 ============================== */

	private Map<String, Object> resource(String type, String name, String value, Map<String, Object> properties) {
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("resourceType", type);
		row.put("name", name != null && name.length() > 1000 ? name.substring(0, 1000) : name);
		row.put("location", null);
		row.put("value", value);
		row.put("propertiesJson", JsonText.of(properties));
		row.put("lineStart", null);
		return row;
	}

	/** beans:bean → bean */
	private String localName(String nodeName) {
		int colon = nodeName.indexOf(':');
		return colon < 0 ? nodeName : nodeName.substring(colon + 1);
	}

	private boolean hasAncestor(Node node, String tagName) {
		Node current = node.getParentNode();
		while (current != null) {
			if (tagName.equals(current.getNodeName())) {
				return true;
			}
			current = current.getParentNode();
		}
		return false;
	}

	private String childText(Element parent, String tagName) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
				String text = child.getTextContent();
				return text == null || text.trim().length() == 0 ? null : text.trim();
			}
		}
		return null;
	}

	private String emptyToNull(String value) {
		return value == null || value.trim().length() == 0 ? null : value.trim();
	}

}

package net.dstone.knowledge.scanner;

import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 파일이 무엇인지(언어와 용도) 가려냅니다.
 *
 * 언어는 확장자로 정합니다. 분석 대상이 아닌 파일(이미지, js, class, jar ...)은 null을 돌려주고 스캔에서 빠집니다.
 * XML은 확장자만으로는 용도를 알 수 없어서 맨 위 요소(root element)를 보고 가립니다.
 */
@Component
public class FileClassifier {

	/**
	 * 확장자로 언어를 정합니다.
	 *
	 * @return JAVA/JSP/XML/YAML/PROPERTIES/GRADLE. 분석 대상이 아니면 null
	 */
	public String languageOf(String fileName) {
		String name = fileName.toLowerCase(Locale.ROOT);
		if (name.endsWith(".java")) {
			return "JAVA";
		}
		if (name.endsWith(".jsp") || name.endsWith(".jspf") || name.endsWith(".jspx") || name.endsWith(".tag")) {
			return "JSP";
		}
		if (name.endsWith(".xml")) {
			return "XML";
		}
		if (name.endsWith(".yml") || name.endsWith(".yaml")) {
			return "YAML";
		}
		if (name.endsWith(".properties")) {
			return "PROPERTIES";
		}
		if (name.endsWith(".gradle") || name.endsWith(".gradle.kts")) {
			return "GRADLE";
		}
		return null;
	}

	/**
	 * 파일의 용도를 정합니다.
	 *
	 * @param text 파일 내용. 너무 커서 읽지 않은 파일은 null
	 */
	public String fileTypeOf(String language, String fileName, String text) {
		if ("JAVA".equals(language)) {
			return "SOURCE";
		}
		if ("JSP".equals(language)) {
			return "JSP";
		}
		if ("YAML".equals(language) || "PROPERTIES".equals(language)) {
			return "CONFIG";
		}
		if ("GRADLE".equals(language)) {
			return "BUILD";
		}
		return xmlTypeOf(fileName.toLowerCase(Locale.ROOT), text);
	}

	private String xmlTypeOf(String lowerName, String text) {
		if ("pom.xml".equals(lowerName)) {
			return "BUILD";
		}
		if (text == null) {
			return "XML";
		}
		String root = rootElementOf(text);
		if (root == null) {
			return "XML";
		}
		// beans:beans 처럼 접두어가 붙은 경우 뒤쪽 이름만 본다.
		int colon = root.indexOf(':');
		String name = colon >= 0 ? root.substring(colon + 1) : root;

		if ("mapper".equals(name)) {
			return "MYBATIS_MAPPER";
		}
		if ("sqlMap".equals(name)) {
			return "IBATIS_MAPPER";
		}
		if ("sqlMapConfig".equals(name)) {
			return "IBATIS_CONFIG";
		}
		if ("beans".equals(name)) {
			return "SPRING_XML";
		}
		if ("web-app".equals(name) || "web-fragment".equals(name)) {
			return "WEB_XML";
		}
		if ("struts-config".equals(name) || "struts".equals(name)) {
			return "STRUTS_CONFIG";
		}
		if ("tiles-definitions".equals(name)) {
			return "TILES";
		}
		if ("project".equals(name)) {
			// pom.xml 이 아닌데 맨 위가 project 면 Ant 빌드 파일이거나, 이름을 바꿔 둔 pom 이다.
			return "BUILD";
		}
		if ("configuration".equals(name) || "Configuration".equals(name)) {
			// 맨 위 이름이 같은 설정 파일이 여럿이라, 앞부분에 적힌 단서로 한 번 더 가린다.
			String head = text.length() > 1000 ? text.substring(0, 1000) : text;
			if (head.indexOf("mybatis") >= 0) {
				return "MYBATIS_CONFIG";
			}
			return "LOG_CONFIG";
		}
		if (name.startsWith("log4j")) {
			return "LOG_CONFIG";
		}
		return "XML";
	}

	/**
	 * XML의 맨 위 요소 이름을 찾습니다.
	 * 그 앞에 올 수 있는 XML 선언, 주석, DOCTYPE은 건너뜁니다.
	 */
	String rootElementOf(String text) {
		int pos = 0;
		int length = text.length();
		while (pos < length) {
			int lt = text.indexOf('<', pos);
			if (lt < 0 || lt + 1 >= length) {
				return null;
			}
			if (text.startsWith("<?", lt)) {
				int end = text.indexOf("?>", lt);
				if (end < 0) {
					return null;
				}
				pos = end + 2;
			} else if (text.startsWith("<!--", lt)) {
				int end = text.indexOf("-->", lt);
				if (end < 0) {
					return null;
				}
				pos = end + 3;
			} else if (text.startsWith("<!", lt)) {
				// DOCTYPE. 안에 [ ... ] 로 내부 선언이 들어 있으면 그 닫는 괄호 뒤의 '>' 까지가 끝이다.
				int end = text.indexOf('>', lt);
				int bracket = text.indexOf('[', lt);
				if (bracket >= 0 && end >= 0 && bracket < end) {
					int close = text.indexOf("]", bracket);
					if (close < 0) {
						return null;
					}
					end = text.indexOf('>', close);
				}
				if (end < 0) {
					return null;
				}
				pos = end + 1;
			} else {
				int nameStart = lt + 1;
				int nameEnd = nameStart;
				while (nameEnd < length) {
					char c = text.charAt(nameEnd);
					if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':') {
						nameEnd++;
					} else {
						break;
					}
				}
				if (nameEnd == nameStart) {
					return null;
				}
				return text.substring(nameStart, nameEnd);
			}
		}
		return null;
	}

}

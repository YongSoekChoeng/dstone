package net.dstone.knowledge.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * JSP 안의 Java 코드를 Java 소스 한 벌로 바꿉니다. JSP 컴파일러(Jasper)가 하는 일을 분석에 필요한 만큼만 흉내 냅니다.
 *
 * 왜 이렇게 하는가:
 * 오래된 화면은 JSP 안에서 Java를 직접 부릅니다(new OrderService().findOrders(...)).
 * 이것을 Java 소스로 바꿔 놓으면 일반 Java 파일과 똑같이 선언을 뽑고 호출을 풀 수 있어서,
 * "이 화면이 어느 메소드를 부르나"가 다른 호출 관계와 같은 방식으로 저장됩니다. JSP용 분석기를 따로 만들 필요가 없습니다.
 *
 * 바꾸는 규칙:
 *   &lt;% 코드 %&gt;        → _jspService() 메소드의 몸통에 그대로
 *   &lt;%= 식 %&gt;         → out.print(식);
 *   &lt;%! 선언 %&gt;       → 클래스의 멤버로
 *   &lt;%@ page import %&gt; → import 문
 *   &lt;jsp:useBean id class&gt; → 그 타입의 변수 선언
 *   그 밖의 글(HTML, EL, 태그 라이브러리) → 버린다
 * request, response, session, out 같은 JSP의 기본 객체는 _jspService()의 파라미터로 선언해 둡니다.
 *
 * 줄 번호를 원본과 맞춥니다. 버리는 글도 줄바꿈만은 남겨서, 바꾼 소스의 N번째 줄이 JSP의 N번째 줄이 되게 합니다.
 * 그래야 분석 결과의 위치가 원본 JSP를 가리킵니다. (선언(&lt;%! %&gt;)만은 맨 뒤로 옮겨져 줄이 맞지 않습니다.)
 *
 * 클래스 이름은 경로로 만듭니다: order/list.jsp → jsp.order.list_jsp
 *
 * Java 코드가 하나도 없는 JSP(EL과 태그만 쓴 화면)는 바꿀 것이 없어서 null을 돌려줍니다.
 * 바꾼 결과가 문법에 맞지 않을 수 있습니다(다른 JSP를 끼워 넣어야 괄호가 맞는 화면 등). 그때는 파싱 단계에서 실패로 남습니다.
 * </pre>
 */
public class JspToJava {

	private static final Pattern IMPORT = Pattern.compile("import\\s*=\\s*\"([^\"]*)\"");
	private static final Pattern USE_BEAN = Pattern.compile("<jsp:useBean\\b([^>]*?)/?>");
	private static final Pattern ATTRIBUTE_ID = Pattern.compile("\\bid\\s*=\\s*\"([^\"]+)\"");
	private static final Pattern ATTRIBUTE_CLASS = Pattern.compile("\\b(?:class|type)\\s*=\\s*\"([^\"]+)\"");

	/** 폴더나 파일 이름으로 쓸 수 없는 Java 예약어들(흔한 것만). 뒤에 _를 붙여 피한다. */
	private static final String KEYWORDS = "|new|default|import|package|class|interface|public|private|static|final|int|long|for|if|else|do|while|switch|case|return|void|this|super|null|true|false|try|catch|throw|enum|abstract|native|goto|const|char|byte|short|float|double|boolean|";

	private JspToJava() {
	}

	/** JSP로 다뤄야 하는 파일인지 */
	public static boolean isJsp(String path) {
		String lower = path.toLowerCase(Locale.ROOT);
		return lower.endsWith(".jsp") || lower.endsWith(".jspf") || lower.endsWith(".jspx") || lower.endsWith(".tag");
	}

	/**
	 * @param jsp JSP의 내용
	 * @param path 프로젝트 루트 기준 경로. 클래스 이름을 만드는 데 씁니다.
	 * @return 바꾼 Java 소스. Java 코드가 없는 JSP면 null
	 */
	public static String convert(String jsp, String path) {
		// useBean 태그는 같은 자리의 변수 선언으로 먼저 바꿔 둔다.
		String text = replaceUseBeans(jsp);

		List<String> imports = new ArrayList<String>();
		StringBuilder body = new StringBuilder();
		StringBuilder declarations = new StringBuilder();
		boolean hasJava = false;

		int position = 0;
		while (position < text.length()) {
			int open = text.indexOf("<%", position);
			if (open < 0) {
				appendNewlines(body, text, position, text.length());
				break;
			}
			appendNewlines(body, text, position, open);

			if (text.startsWith("<%--", open)) {
				// JSP 주석
				int close = text.indexOf("--%>", open + 4);
				int end = close < 0 ? text.length() : close + 4;
				appendNewlines(body, text, open, end);
				position = end;
				continue;
			}
			int close = text.indexOf("%>", open + 2);
			int end = close < 0 ? text.length() : close;
			char kind = open + 2 < text.length() ? text.charAt(open + 2) : ' ';
			if (kind == '@') {
				// 지시자. import만 가져온다.
				String directive = text.substring(open + 3, end);
				Matcher m = IMPORT.matcher(directive);
				while (m.find()) {
					String[] names = m.group(1).split(",");
					for (int i = 0; i < names.length; i++) {
						if (names[i].trim().length() > 0) {
							imports.add(names[i].trim());
						}
					}
				}
				appendNewlines(body, text, open, end);
			} else if (kind == '!') {
				String code = text.substring(open + 3, end);
				hasJava = hasJava || code.trim().length() > 0;
				declarations.append(code).append('\n');
				appendNewlines(body, text, open, end);
			} else if (kind == '=') {
				String code = text.substring(open + 3, end);
				if (code.trim().length() > 0) {
					hasJava = true;
					body.append("out.print(").append(code).append(");");
				} else {
					appendNewlines(body, text, open, end);
				}
			} else {
				String code = text.substring(open + 2, end);
				hasJava = hasJava || code.trim().length() > 0;
				body.append(code).append(' ');
			}
			position = close < 0 ? text.length() : close + 2;
		}
		if (!hasJava) {
			return null;
		}

		StringBuilder java = new StringBuilder();
		String packageName = packageOf(path);
		java.append("package ").append(packageName).append("; ");
		for (int i = 0; i < imports.size(); i++) {
			java.append("import ").append(imports.get(i)).append("; ");
		}
		// 머리말은 줄을 바꾸지 않고 한 줄에 다 적는다. 그래야 몸통의 줄 번호가 원본과 같다.
		java.append("public class ").append(classNameOf(path)).append(" { ");
		java.append("public void _jspService(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response")
				.append(", javax.servlet.http.HttpSession session, javax.servlet.jsp.JspWriter out, javax.servlet.ServletContext application")
				.append(", javax.servlet.jsp.PageContext pageContext, javax.servlet.ServletConfig config, Object page) throws Exception { ");
		java.append(body);
		java.append("\n}\n");
		java.append(declarations);
		java.append("}\n");
		return java.toString();
	}

	/** order/list.jsp → jsp.order */
	public static String packageOf(String path) {
		StringBuilder sb = new StringBuilder("jsp");
		String[] parts = path.split("/");
		for (int i = 0; i < parts.length - 1; i++) {
			sb.append('.').append(identifierOf(parts[i]));
		}
		return sb.toString();
	}

	/** order/list.jsp → list_jsp */
	public static String classNameOf(String path) {
		String fileName = path.substring(path.lastIndexOf('/') + 1);
		return identifierOf(fileName.replace('.', '_'));
	}

	/** 이름으로 쓸 수 없는 글자를 _로 바꿉니다. */
	private static String identifierOf(String name) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			sb.append(Character.isLetterOrDigit(c) || c == '_' ? c : '_');
		}
		if (sb.length() == 0 || Character.isDigit(sb.charAt(0))) {
			sb.insert(0, '_');
		}
		if (KEYWORDS.indexOf("|" + sb + "|") >= 0) {
			sb.append('_');
		}
		return sb.toString();
	}

	/** 버리는 글에서 줄바꿈만 남깁니다. */
	private static void appendNewlines(StringBuilder out, String text, int from, int to) {
		for (int i = from; i < to; i++) {
			if (text.charAt(i) == '\n') {
				out.append('\n');
			}
		}
	}

	/** &lt;jsp:useBean id="cart" class="shop.Cart"/&gt; → &lt;% shop.Cart cart = new shop.Cart(); %&gt; */
	private static String replaceUseBeans(String jsp) {
		Matcher m = USE_BEAN.matcher(jsp);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			Matcher id = ATTRIBUTE_ID.matcher(m.group(1));
			Matcher type = ATTRIBUTE_CLASS.matcher(m.group(1));
			String replacement = m.group();
			if (id.find() && type.find()) {
				// 태그가 여러 줄에 걸쳐 있었으면 그 줄 수만큼 줄바꿈을 붙여 뒤의 줄 번호가 밀리지 않게 한다.
				StringBuilder code = new StringBuilder("<% " + type.group(1) + " " + id.group(1) + " = new " + type.group(1) + "(); %>");
				for (int i = 0; i < m.group().length(); i++) {
					if (m.group().charAt(i) == '\n') {
						code.append('\n');
					}
				}
				replacement = code.toString();
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(sb);
		return sb.toString();
	}

}

package net.dstone.knowledge.analysis.pass08.semantic.rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 규칙 파일에 "코드 모양 그대로" 적은 글자를 읽습니다. DB 도 Spring 도 쓰지 않아 단위 테스트로 확인합니다.
 *
 *   모양                                    뜻
 *   "@Service"                              애노테이션
 *   "@GetMapping(value|path = URL)"         애노테이션과 값을 꺼낼 속성. URL 은 꺼낸 값의 이름(자리)이다
 *   "@Scheduled(KEY)"                       괄호 안에 자리만 적으면 value 속성
 *   extends jef.application.dao.JdbcDAO     이 타입을 바로 상속(구현)한 클래스
 *   name *Dao                               클래스 단순 이름(글롭: * 는 아무 글자, ? 는 한 글자)
 *   _.selectList(SQL)                       아무 객체의 selectList 호출. SQL 자리의 인자를 본다
 *   com.acme.BaseDao#executeList(_, SQL)    BaseDao(하위 타입 포함)의 executeList 호출. _ 는 아무 인자
 *   new QueryProperty(SQL)                  생성자 호출
 *   java.lang.Runnable#run()                Runnable 을 구현한 클래스의 run(). () 는 인자 없음, (...) 는 인자 수를 보지 않음
 *   doRequestAjax(ID)                       화면 스크립트의 함수 호출
 *   &lt;service id="KEY" class="CLASS"/&gt;       XML 요소와 값을 꺼낼 속성
 *
 * 자리 이름(URL, SQL, KEY ...)은 영문 대문자로 적습니다. 어떤 자리를 쓸 수 있는지는 규칙 절마다 다릅니다.
 * 틀리면 IllegalArgumentException 을 던지고, RuleFileParser 가 파일 이름과 줄 번호를 붙여 알려 줍니다.
 * </pre>
 */
public final class CodePattern {

	private static final String ID = "[A-Za-z_$][A-Za-z0-9_$]*";
	private static final Pattern IDENTIFIER = Pattern.compile(ID);
	private static final Pattern QUALIFIED = Pattern.compile(ID + "(\\." + ID + ")*");
	private static final Pattern PLACEHOLDER = Pattern.compile("[A-Z][A-Z0-9_]*");
	/** 메소드 · 클래스 이름 글롭 */
	private static final Pattern GLOB = Pattern.compile("[A-Za-z0-9_$*?]+");
	private static final Pattern ANNOTATION = Pattern.compile("@(" + ID + "(?:\\." + ID + ")*)\\s*(?:\\((.*)\\))?");
	/** 이름(인자). 이름 앞에 "new " / "_." / "타입#" 이 올 수 있다 */
	private static final Pattern CALL = Pattern.compile("(new\\s+)?([A-Za-z0-9_$.*?#]+)\\s*\\((.*)\\)");
	private static final Pattern XML_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
	private static final Pattern XML_ELEMENT = Pattern.compile("<\\s*([^\\s/>]+)((?:\\s+[^\\s=]+\\s*=\\s*\"[^\"]*\")*)\\s*/?\\s*>");
	private static final Pattern XML_ATTRIBUTE = Pattern.compile("([^\\s=]+)\\s*=\\s*\"([^\"]*)\"");

	private CodePattern() {
	}

	/* ============================== 애노테이션 ============================== */

	/** 읽은 애노테이션 모양 */
	public static class Annotation {
		/** @ 뺀 단순 이름 */
		public final String name;
		/** 자리 이름 → 값을 꺼낼 속성들(적힌 순서) */
		public final Map<String, List<String>> slots;

		Annotation(String name, Map<String, List<String>> slots) {
			this.name = name;
			this.slots = slots;
		}

		/** 이 자리의 속성들. 적지 않았으면 빈 목록 */
		public List<String> attributes(String slot) {
			List<String> attributes = slots.get(slot);
			return attributes == null ? new ArrayList<String>() : attributes;
		}

		/** 이 자리의 속성 하나. 적지 않았으면 null. 둘 이상 적었으면 오류 */
		public String attribute(String slot) {
			List<String> attributes = slots.get(slot);
			if (attributes == null) {
				return null;
			}
			if (attributes.size() != 1) {
				throw new IllegalArgumentException(slot + " 자리에는 속성을 하나만 적습니다: " + attributes);
			}
			return attributes.get(0);
		}
	}

	/**
	 * <pre>
	 * "@이름" 또는 "@이름(속성|속성 = 자리, ...)" 를 읽습니다.
	 * </pre>
	 *
	 * @param allowedSlots 쓸 수 있는 자리 이름. 빈 목록이면 괄호를 쓸 수 없다
	 */
	public static Annotation annotation(String text, Collection<String> allowedSlots) {
		String t = text.trim();
		Matcher m = ANNOTATION.matcher(t);
		if (!m.matches()) {
			throw new IllegalArgumentException("애노테이션 모양이 아닙니다(예: \"@Service\", \"@GetMapping(value|path = URL)\"): " + text);
		}
		String name = m.group(1);
		if (name.indexOf('.') >= 0) {
			throw new IllegalArgumentException("애노테이션은 단순 이름으로 적습니다(와일드카드 import 면 전체 이름을 알 수 없어서): @"
					+ name.substring(name.lastIndexOf('.') + 1) + " (적힌 것: " + text + ")");
		}
		Map<String, List<String>> slots = new LinkedHashMap<String, List<String>>();
		String inside = m.group(2);
		if (inside != null && inside.trim().length() > 0) {
			if (allowedSlots.isEmpty()) {
				throw new IllegalArgumentException("여기서는 애노테이션 이름만 적습니다(괄호 없이): @" + name);
			}
			String[] parts = inside.split(",");
			for (int i = 0; i < parts.length; i++) {
				String part = parts[i].trim();
				int eq = part.indexOf('=');
				String slot = (eq < 0 ? part : part.substring(eq + 1)).trim();
				List<String> attributes = new ArrayList<String>();
				if (eq < 0) {
					// 자리만 적으면 Java 의 기본 속성 value 다. @Scheduled(KEY) = @Scheduled(value = KEY)
					attributes.add("value");
				} else {
					String[] names = part.substring(0, eq).split("\\|");
					for (int n = 0; n < names.length; n++) {
						String attribute = names[n].trim();
						if (!IDENTIFIER.matcher(attribute).matches()) {
							throw new IllegalArgumentException("속성 이름이 아닙니다: " + attribute + " (적힌 것: " + text + ")");
						}
						attributes.add(attribute);
					}
				}
				checkSlot(slot, allowedSlots, text);
				if (slots.containsKey(slot)) {
					throw new IllegalArgumentException(slot + " 자리를 두 번 적었습니다. 속성이 여럿이면 | 로 잇습니다(value|path = URL): " + text);
				}
				slots.put(slot, attributes);
			}
		}
		return new Annotation(name, slots);
	}

	/* ============================== 호출 ============================== */

	/** 읽은 호출 모양 */
	public static class Call {
		/** new 타입(...) 이면 true */
		public final boolean constructor;
		/** "타입#이름" 의 타입(전체 이름). "_.이름" 이나 생성자, 이름만 적었으면 null */
		public final String receiverType;
		/** "_.이름" 으로 적었으면 true */
		public final boolean anyReceiver;
		/** 메소드 · 함수 · 생성자 이름. 앞의 마디(HiJS.svr.)는 뗀 마지막 이름 */
		public final String name;
		/** 인자 자리마다 적힌 것: "_" 또는 자리 이름 */
		public final List<String> args;
		/** 마지막에 "..." 를 적었으면 true(인자 수를 보지 않는다) */
		public final boolean anyArgs;

		Call(boolean constructor, String receiverType, boolean anyReceiver, String name, List<String> args, boolean anyArgs) {
			this.constructor = constructor;
			this.receiverType = receiverType;
			this.anyReceiver = anyReceiver;
			this.name = name;
			this.args = args;
			this.anyArgs = anyArgs;
		}

		/** 이 자리 이름이 몇 번째 인자인지(0부터). 없으면 -1 */
		public int indexOf(String slot) {
			return args.indexOf(slot);
		}

		/** 이름에 글롭(* ?)이 들어 있는지 */
		public boolean isGlob() {
			return name.indexOf('*') >= 0 || name.indexOf('?') >= 0;
		}
	}

	/** 호출이 어떤 앞붙이를 가질 수 있는지 */
	public enum CallKind {
		/** _.이름(...) / 타입#이름(...) / new 타입(...) : sql, view */
		JAVA_CALL,
		/** 타입#이름(...) 만, 이름은 글롭 가능: entry 의 상위 타입 진입점 */
		TYPE_METHOD,
		/** 이름(...) 또는 앞.마디.이름(...) : 화면 스크립트의 함수 */
		SCRIPT_FUNCTION
	}

	public static Call call(String text, CallKind kind, Collection<String> allowedSlots) {
		String t = text.trim();
		Matcher m = CALL.matcher(t);
		if (!m.matches()) {
			if (t.indexOf('(') >= 0 && t.indexOf(')') < 0) {
				throw new IllegalArgumentException("괄호가 닫히지 않았습니다. [a, b] 안에 쉼표가 든 호출을 적으면 YAML 이 쉼표에서 나눕니다."
						+ " 따옴표로 감싸거나(\"" + t + ", ...)\") 한 줄에 하나씩 - 로 적으세요: " + text);
			}
			throw new IllegalArgumentException("호출 모양이 아닙니다(" + example(kind) + "): " + text);
		}
		boolean constructor = m.group(1) != null;
		String head = m.group(2);
		String receiverType = null;
		boolean anyReceiver = false;
		String name;
		int hash = head.indexOf('#');
		if (constructor) {
			if (kind != CallKind.JAVA_CALL || !IDENTIFIER.matcher(head).matches()) {
				throw new IllegalArgumentException("생성자는 new 타입이름(...) 으로 적습니다. 타입 이름은 소스에 적힌 대로(보통 단순 이름): " + text);
			}
			name = head;
		} else if (hash >= 0) {
			receiverType = head.substring(0, hash);
			name = head.substring(hash + 1);
			if (kind == CallKind.SCRIPT_FUNCTION || !QUALIFIED.matcher(receiverType).matches()) {
				throw new IllegalArgumentException("타입#메소드(...) 의 타입은 전체 이름(패키지.클래스)이어야 합니다: " + text);
			}
		} else if (head.startsWith("_.")) {
			anyReceiver = true;
			name = head.substring(2);
			if (kind != CallKind.JAVA_CALL) {
				throw new IllegalArgumentException(kind == CallKind.TYPE_METHOD
						? "상위 타입 진입점은 상위타입#메소드(...) 로 적습니다(예: java.lang.Runnable#run()): " + text
						: "화면 함수는 이름(...) 으로 적습니다(예: doRequestAjax(ID)): " + text);
			}
		} else {
			if (kind == CallKind.JAVA_CALL) {
				throw new IllegalArgumentException("메소드 호출은 _.이름(...) 이나 타입#이름(...) 으로, 생성자는 new 이름(...) 으로 적습니다: " + text);
			}
			if (kind == CallKind.TYPE_METHOD) {
				throw new IllegalArgumentException("상위 타입 진입점은 상위타입#메소드(...) 로 적습니다(예: java.lang.Runnable#run()): " + text);
			}
			if (!QUALIFIED.matcher(head).matches()) {
				throw new IllegalArgumentException("함수 이름이 아닙니다: " + text);
			}
			// HiJS.svr.doRequestAjax(ID) 처럼 적어도 마지막 이름으로 찾는다(앞의 마디는 없어도 맞는다).
			name = head.substring(head.lastIndexOf('.') + 1);
		}
		boolean glob = kind == CallKind.TYPE_METHOD;
		if (!(glob ? GLOB : IDENTIFIER).matcher(name).matches()) {
			throw new IllegalArgumentException("이름이 아닙니다(" + example(kind) + "): " + name);
		}

		List<String> args = new ArrayList<String>();
		boolean anyArgs = false;
		String inside = m.group(3).trim();
		if (inside.length() > 0) {
			String[] parts = inside.split(",");
			for (int i = 0; i < parts.length; i++) {
				String part = parts[i].trim();
				if ("...".equals(part)) {
					if (i != parts.length - 1) {
						throw new IllegalArgumentException("... 는 맨 뒤에만 적습니다(나머지 인자는 보지 않는다): " + text);
					}
					anyArgs = true;
				} else if ("_".equals(part)) {
					args.add(part);
				} else {
					checkSlot(part, allowedSlots, text);
					if (args.contains(part)) {
						throw new IllegalArgumentException(part + " 자리를 두 번 적었습니다: " + text);
					}
					args.add(part);
				}
			}
		}
		return new Call(constructor, receiverType, anyReceiver, name, args, anyArgs);
	}

	private static String example(CallKind kind) {
		switch (kind) {
		case TYPE_METHOD:
			return "예: java.lang.Runnable#run(), com.fw.BaseService#perform*(...)";
		case SCRIPT_FUNCTION:
			return "예: doRequestAjax(ID), fnOpenPage(_, ID)";
		default:
			return "예: _.selectList(SQL), com.fw.BaseDao#execute(_, SQL), new QueryProperty(SQL)";
		}
	}

	/* ============================== 상속, 이름 ============================== */

	/** "extends 전체.이름" 의 전체 이름. 이 모양이 아니면 null */
	public static String extendsType(String text) {
		String t = text.trim();
		if (!t.startsWith("extends ") && !t.startsWith("extends\t")) {
			return null;
		}
		String type = t.substring("extends".length()).trim();
		if (!QUALIFIED.matcher(type).matches() || type.indexOf('.') < 0) {
			throw new IllegalArgumentException("extends 뒤에는 상위 타입의 전체 이름(패키지.클래스)을 적습니다: " + text);
		}
		return type;
	}

	/** "name 글롭" 의 글롭. 이 모양이 아니면 null */
	public static String nameGlob(String text) {
		String t = text.trim();
		if (!t.startsWith("name ") && !t.startsWith("name\t")) {
			return null;
		}
		String glob = t.substring("name".length()).trim();
		if (!GLOB.matcher(glob).matches()) {
			throw new IllegalArgumentException("name 뒤에는 클래스 이름 글롭을 적습니다(* 는 아무 글자, ? 는 한 글자. 예: name *Dao): " + text);
		}
		return glob;
	}

	/** 글롭을 이름 전체에 맞는 정규식으로 바꿉니다. *Dao → ^.*Dao$ (PostgreSQL ~ 과 Java 정규식 둘 다 같은 뜻) */
	public static String globToRegex(String glob) {
		StringBuilder sb = new StringBuilder("^");
		for (int i = 0; i < glob.length(); i++) {
			char c = glob.charAt(i);
			if (c == '*') {
				sb.append(".*");
			} else if (c == '?') {
				sb.append('.');
			} else if (c == '$') {
				sb.append("\\$");
			} else {
				sb.append(c);
			}
		}
		return sb.append('$').toString();
	}

	/* ============================== XML ============================== */

	/** 읽은 XML 요소 모양 */
	public static class XmlElement {
		public final String element;
		/** 자리 이름 → 속성 이름 */
		public final Map<String, String> slots;

		XmlElement(String element, Map<String, String> slots) {
			this.element = element;
			this.slots = slots;
		}
	}

	/** '&lt;service id="KEY" class="CLASS" method="METHOD"/&gt;' 를 읽습니다 */
	public static XmlElement xmlElement(String text, Collection<String> allowedSlots) {
		Matcher m = XML_ELEMENT.matcher(text.trim());
		if (!m.matches()) {
			throw new IllegalArgumentException("XML 요소 모양이 아닙니다(예: <service id=\"KEY\" class=\"CLASS\" method=\"METHOD\"/>): " + text);
		}
		String element = m.group(1);
		if (!XML_NAME.matcher(element).matches()) {
			throw new IllegalArgumentException("요소 이름은 네임스페이스 앞붙이 없이 적습니다(영문자로 시작, 영문자 · 숫자 · _ . -): " + element);
		}
		Map<String, String> slots = new LinkedHashMap<String, String>();
		Matcher a = XML_ATTRIBUTE.matcher(m.group(2));
		while (a.find()) {
			String attribute = a.group(1);
			String slot = a.group(2).trim();
			if (!XML_NAME.matcher(attribute).matches()) {
				throw new IllegalArgumentException("속성 이름은 네임스페이스 앞붙이 없이 적습니다: " + attribute);
			}
			checkSlot(slot, allowedSlots, text);
			if (slots.containsKey(slot)) {
				throw new IllegalArgumentException(slot + " 자리를 두 번 적었습니다: " + text);
			}
			slots.put(slot, attribute);
		}
		return new XmlElement(element, slots);
	}

	/* ============================== 도우미 ============================== */

	private static void checkSlot(String slot, Collection<String> allowedSlots, String text) {
		if (!PLACEHOLDER.matcher(slot).matches() || !allowedSlots.contains(slot)) {
			throw new IllegalArgumentException("여기서 쓸 수 있는 자리는 " + allowedSlots + " 입니다. 적힌 것: " + slot + " (" + text + ")");
		}
	}

	/** 타입의 전체 이름인지 */
	public static boolean isQualifiedName(String text) {
		return QUALIFIED.matcher(text).matches();
	}

	/** Java 이름(식별자)인지 */
	public static boolean isIdentifier(String text) {
		return IDENTIFIER.matcher(text).matches();
	}

}

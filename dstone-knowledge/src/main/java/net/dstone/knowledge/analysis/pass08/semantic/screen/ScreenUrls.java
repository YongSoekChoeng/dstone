package net.dstone.knowledge.analysis.pass08.semantic.screen;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 화면이 서버 주소를 "어디에" 적어 부르는지 규칙(screen: 의 url:)으로 찾습니다. 파일 하나만 보는 순수한 클래스입니다.
 *
 * 규칙은 세 모양입니다.
 *   CALL  this.gfn_Transaction(_, URL)   그 함수의 n번째 인자가 주소
 *   PROP  url: URL                       객체의 그 속성 값이 주소 ({ url: "/a.do" })
 *   ATTR  &lt;form action="URL"&gt;          HTML 태그의 그 속성 값이 주소
 *
 * 값은 계산할 수 있는 것만 받습니다(짐작하지 않는다).
 *   - 따옴표 글자: "/hhr/ws/insertWabDtAutoCrt.do"
 *   - 변수(CALL, PROP 만): 그 호출을 감싼 함수 안에서 따옴표 글자를 딱 한 번 대입한 변수.
 *     함수 안에서 대입하지 않았으면 파일 전체에서 딱 한 번 대입한 변수.
 *     매개변수이거나, 두 번 이상 대입했거나, 글자를 이어 붙였으면("/a/" + id + ".do") 모르는 값입니다.
 * 주소는 ?query 와 #… 를 떼고, 앞에 붙은 컨텍스트 경로 변수(${ctx}, &lt;%= … %&gt;)와 서비스 접두어(svc::)를 뗀 모양입니다.
 * 규칙에 컨텍스트 경로(context: /hi-japan)를 적었으면, 주소가 그 글자로 시작할 때만 떼어 냅니다.
 * </pre>
 */
public class ScreenUrls {

	/** 주소처럼 생긴 글자. ScreenScript 의 주소 모양과 같다 */
	private static final Pattern PATH = Pattern.compile("/[A-Za-z0-9_\\-]+(?:/[A-Za-z0-9_\\-]+)*(?:\\.[A-Za-z0-9]+)?");

	/** 서비스 접두어가 붙은 주소. 예: svc::order/list.do */
	private static final Pattern PREFIXED = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*::/?(.*)$");

	/** 변수 이름 */
	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

	/** 함수 정의의 매개변수 목록을 찾으러 거슬러 볼 글자 수의 한도 */
	private static final int MAX_PARAMS_CHARS = 2000;

	/** 규칙 모양 */
	public enum Kind {
		/** 함수의 n번째 인자 */
		CALL,
		/** 객체 속성 값 */
		PROP,
		/** HTML 태그의 속성 값 */
		ATTR
	}

	/** 주소 규칙 하나 */
	public static class Rule {

		private final Kind kind;
		/** CALL: 함수 이름, PROP: 속성 이름, ATTR: 태그 이름 */
		private final String name;
		/** ATTR: 속성 이름. 그 밖에는 null */
		private final String attribute;
		/** CALL: 주소가 든 인자 자리(0부터). 그 밖에는 0 */
		private final int argIndex;
		/** 규칙 파일에 적은 그대로. 결과의 rule 에 남긴다 */
		private final String text;
		/** 규칙이 적힌 자리(규칙 파일:줄). 모르면 null */
		private final String at;
		/** 화면이 주소 앞에 글자로 붙여 쓰는 컨텍스트 경로. 예: /hi-japan. 없으면 null */
		private String context;
		private final Pattern pattern;

		private Rule(Kind kind, String name, String attribute, int argIndex, String text, String at) {
			this.kind = kind;
			this.name = name;
			this.attribute = attribute;
			this.argIndex = argIndex;
			this.text = text;
			this.at = at;
			if (kind == Kind.CALL) {
				// 함수 이름 앞에 다른 이름 글자가 붙으면 다른 함수다(xgfn_Transaction). 점은 괜찮다(this.gfn_Transaction).
				this.pattern = Pattern.compile("(?<![A-Za-z0-9_$])" + Pattern.quote(name) + "\\s*\\(");
			} else if (kind == Kind.PROP) {
				// 속성 이름은 따옴표로 싸도 된다("url": …). 값은 뒤에서 따로 읽는다.
				this.pattern = Pattern.compile("(?<![A-Za-z0-9_$.])([\"']?)" + Pattern.quote(name) + "\\1\\s*:(?!:)\\s*");
			} else {
				// 자바스크립트 글자 안에 적은 태그면 따옴표 앞에 \ 가 붙기도 한다(action=\"/a.do\").
				this.pattern = Pattern.compile("<" + Pattern.quote(name) + "(?=[\\s/>\\\\])[^<>]*?\\s" + Pattern.quote(attribute)
						+ "\\s*=\\s*\\\\?([\"'])([^\"'<>\\r\\n\\\\]*)\\\\?\\1", Pattern.CASE_INSENSITIVE);
			}
		}

		/** 함수의 argIndex 번째 인자가 주소인 규칙 */
		public static Rule call(String function, int argIndex, String text, String at) {
			return new Rule(Kind.CALL, function, null, argIndex, text, at);
		}

		/** 객체 속성 값이 주소인 규칙 */
		public static Rule prop(String property, String text, String at) {
			return new Rule(Kind.PROP, property, null, 0, text, at);
		}

		/** HTML 태그의 속성 값이 주소인 규칙 */
		public static Rule attr(String tag, String attribute, String text, String at) {
			return new Rule(Kind.ATTR, tag, attribute, 0, text, at);
		}

		public Kind getKind() {
			return kind;
		}

		public String getName() {
			return name;
		}

		public String getAttribute() {
			return attribute;
		}

		public int getArgIndex() {
			return argIndex;
		}

		public String getText() {
			return text;
		}

		public String getAt() {
			return at;
		}

		public String getContext() {
			return context;
		}

		/** 화면이 주소 앞에 글자로 붙여 쓰는 컨텍스트 경로를 정합니다(규칙 파일의 context:). 이 규칙을 돌려준다 */
		public Rule withContext(String context) {
			this.context = context;
			return this;
		}

		/** 컨텍스트 경로를 뗀 주소. 그 경로로 시작하지 않으면 그대로 */
		String withoutContext(String url) {
			if (context != null && url.startsWith(context + "/")) {
				return url.substring(context.length());
			}
			return url;
		}

		/** 똑같은 규칙을 하나만 남길 때 쓰는 열쇠 */
		public String key() {
			return kind + "|" + name + "|" + attribute + "|" + argIndex + "|" + context;
		}
	}

	/** 화면에서 찾은 주소 하나 */
	public static class Hit {

		private final Rule rule;
		private final String url;
		private final int index;
		private final String variable;

		Hit(Rule rule, String url, int index, String variable) {
			this.rule = rule;
			this.url = url;
			this.index = index;
			this.variable = variable;
		}

		public Rule getRule() {
			return rule;
		}

		/** 다듬은 주소. 예: /hhr/ws/insertWabDtAutoCrt.do */
		public String getUrl() {
			return url;
		}

		/** 파일 안에서 규칙에 맞은 자리(글자 번호). 줄 번호를 셀 때 쓴다 */
		public int getIndex() {
			return index;
		}

		/** 변수로 넘겨 그 값을 읽었으면 변수 이름. 글자를 바로 적었으면 null */
		public String getVariable() {
			return variable;
		}
	}

	/** 찾은 주소를 진입점 메소드와 이은 것 하나. 연결기(ScreenLinker, JspLinker)가 이것으로 REQUESTS 행을 만든다 */
	public static class Link {

		private final Hit hit;
		private final String methodId;
		private final boolean single;

		Link(Hit hit, String methodId, boolean single) {
			this.hit = hit;
			this.methodId = methodId;
			this.single = single;
		}

		public Hit getHit() {
			return hit;
		}

		public String getMethodId() {
			return methodId;
		}

		/** 그 주소를 처리하는 메소드가 하나뿐이면 HIGH, 여럿이면 MEDIUM */
		public String getConfidence() {
			return single ? "HIGH" : "MEDIUM";
		}

		public String getResolutionStatus() {
			return single ? "RESOLVED" : "HEURISTIC";
		}

		/** 관계의 properties_json: path, via = URL_RULE, rule(적은 모양), ruleAt, variable(변수로 넘겼으면) */
		public Map<String, Object> properties() {
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("path", hit.getUrl());
			properties.put("via", "URL_RULE");
			properties.put("rule", hit.getRule().getText());
			properties.put("ruleAt", hit.getRule().getAt());
			if (hit.getRule().getContext() != null) {
				properties.put("context", hit.getRule().getContext());
			}
			if (hit.getVariable() != null) {
				properties.put("variable", hit.getVariable());
			}
			return properties;
		}
	}

	/**
	 * <pre>
	 * 찾은 주소를 진입점 주소와 똑같을 때만 그 메소드와 잇습니다. 컨텍스트 경로를 떼어 맞춰 보는 짐작은 하지 않습니다
	 * (그런 주소는 지금처럼 "화면에 적힌 주소 글자"로 LOW 가 됩니다).
	 * 이미 이은 메소드(linked)는 다시 잇지 않고, 새로 이은 메소드는 linked 에 넣습니다.
	 * </pre>
	 *
	 * @param handlers 주소 → 그 주소를 처리하는 메소드들
	 */
	public static List<Link> links(List<Hit> hits, Map<String, List<String>> handlers, Set<String> linked) {
		List<Link> links = new ArrayList<Link>();
		for (int h = 0; h < hits.size(); h++) {
			Hit hit = hits.get(h);
			List<String> methods = handlers.get(hit.getUrl());
			if (methods == null) {
				continue;
			}
			for (int i = 0; i < methods.size(); i++) {
				if (linked.add(methods.get(i))) {
					links.add(new Link(hit, methods.get(i), methods.size() == 1));
				}
			}
		}
		return links;
	}

	private final ScreenScript screenScript = new ScreenScript();

	private final ScriptFunctions scriptFunctions = new ScriptFunctions();

	/**
	 * <pre>
	 * 규칙에 맞는 주소를 찾습니다. 주석 안의 것은 보지 않습니다.
	 *
	 * 흐름:
	 *   1) 주석 자리와, 코드가 아닌 자리(주석·문자열·정규식)를 한 번씩 표시해 둔다.
	 *   2) 함수 본문 범위를 한 번 모아 둔다(변수 값을 읽을 때 "감싼 함수"를 찾으려고).
	 *   3) 규칙마다 맞는 자리를 찾아 값을 읽는다: 따옴표 글자, 또는 한 번만 대입한 변수.
	 *   4) 값을 주소 모양으로 다듬는다. 주소가 아니면 버린다.
	 * </pre>
	 */
	public List<Hit> find(String text, List<Rule> rules) {
		List<Hit> hits = new ArrayList<Hit>();
		if (rules == null || rules.isEmpty()) {
			return hits;
		}
		// 1) 주석 자리, 코드가 아닌 자리
		BitSet comments = screenScript.commentMask(text);
		BitSet nonCode = null;
		List<int[]> scopes = null;
		for (int r = 0; r < rules.size(); r++) {
			Rule rule = rules.get(r);
			Matcher m = rule.pattern.matcher(text);
			while (m.find()) {
				if (comments.get(m.start())) {
					continue;
				}
				String written;
				if (rule.kind == Kind.ATTR) {
					written = m.group(2);
				} else if (rule.kind == Kind.CALL) {
					if (isDeclaration(text, m.start())) {
						continue;
					}
					List<String> args = screenScript.arguments(text, m.end(), comments);
					if (args == null || rule.argIndex >= args.size()) {
						continue;
					}
					written = args.get(rule.argIndex);
				} else {
					// 객체 속성은 { 나 , 바로 뒤에만 온다. 삼항 연산자(a ? url : b)를 속성으로 읽지 않게 한다.
					if (!startsProperty(text, m.start(), comments)) {
						continue;
					}
					written = valueText(text, m.end());
				}
				if (written == null) {
					continue;
				}
				String variable = null;
				// HTML 속성은 정규식이 따옴표를 이미 벗겨 냈다. 나머지는 따옴표 글자인지 본다.
				String value = rule.kind == Kind.ATTR ? written : literal(written);
				if (value == null && rule.kind != Kind.ATTR && IDENTIFIER.matcher(written).matches()) {
					// 2) 변수로 넘겼다. 필요할 때 한 번만 코드 자리와 함수 범위를 모은다.
					if (nonCode == null) {
						nonCode = scriptFunctions.nonCode(text);
						scopes = functionScopes(text, nonCode);
					}
					value = variableValue(text, nonCode, scopes, written, m.start());
					variable = value == null ? null : written;
				}
				// 3) 주소 모양으로 다듬기
				String url = normalize(value);
				if (url != null) {
					url = rule.withoutContext(url);
					hits.add(new Hit(rule, url, m.start(), variable));
				}
			}
		}
		return hits;
	}

	/**
	 * <pre>
	 * 적힌 값을 주소로 다듬습니다. 주소가 아니면 null.
	 *   "${ctx}/order/list.do?x=1"   → /order/list.do
	 *   "svc::order/list.do"          → /order/list.do
	 *   "/a/" + id + ".do", "list.do" → null
	 * </pre>
	 */
	public static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String s = value.trim();
		// 앞에 붙은 컨텍스트 경로 변수를 뗀다. 여러 개 이어져 있어도 된다.
		while (true) {
			if (s.startsWith("${")) {
				int end = s.indexOf('}');
				if (end < 0) {
					return null;
				}
				s = s.substring(end + 1);
			} else if (s.startsWith("<%=")) {
				int end = s.indexOf("%>");
				if (end < 0) {
					return null;
				}
				s = s.substring(end + 2);
			} else {
				break;
			}
		}
		Matcher prefixed = PREFIXED.matcher(s);
		if (prefixed.matches()) {
			s = "/" + prefixed.group(1);
		}
		int cut = s.length();
		int question = s.indexOf('?');
		int hash = s.indexOf('#');
		int semicolon = s.indexOf(';');
		if (question >= 0) {
			cut = Math.min(cut, question);
		}
		if (hash >= 0) {
			cut = Math.min(cut, hash);
		}
		if (semicolon >= 0) {
			cut = Math.min(cut, semicolon);
		}
		s = s.substring(0, cut);
		return PATH.matcher(s).matches() ? s : null;
	}

	/** 인자나 값 하나가 따옴표 글자 하나면 그 내용, 아니면 null */
	private String literal(String written) {
		String s = written.trim();
		if (s.length() < 2) {
			return null;
		}
		char quote = s.charAt(0);
		if ((quote != '"' && quote != '\'') || s.charAt(s.length() - 1) != quote) {
			return null;
		}
		String inside = s.substring(1, s.length() - 1);
		if (inside.indexOf(quote) >= 0 || inside.indexOf('\\') >= 0 || inside.indexOf('\n') >= 0) {
			return null;
		}
		return inside;
	}

	/**
	 * <pre>
	 * 속성 값(콜론 뒤)을 읽습니다. 따옴표 글자 하나나 변수 이름 하나이고 그 뒤가 , } ; 줄바꿈이면 그 글자, 아니면 null.
	 *   url: "/a.do",        → "/a.do"
	 *   url: url }           → url
	 *   url: base + "/a.do"  → null
	 * </pre>
	 */
	private String valueText(String text, int from) {
		int i = from;
		int length = text.length();
		if (i >= length) {
			return null;
		}
		char c = text.charAt(i);
		int end;
		if (c == '"' || c == '\'') {
			end = text.indexOf(c, i + 1);
			int newline = text.indexOf('\n', i + 1);
			if (end < 0 || (newline >= 0 && newline < end)) {
				return null;
			}
			end++;
		} else {
			Matcher m = IDENTIFIER.matcher(text);
			m.region(i, length);
			if (!m.lookingAt()) {
				return null;
			}
			end = m.end();
		}
		return endsValue(text, end) ? text.substring(i, end) : null;
	}

	/** 값 뒤가 , } ) ; 줄바꿈(또는 파일 끝)인지. 공백은 건너뛴다 */
	private boolean endsValue(String text, int from) {
		for (int i = from; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == ' ' || c == '\t') {
				continue;
			}
			return c == ',' || c == '}' || c == ')' || c == ';' || c == '\r' || c == '\n' || c == '/';
		}
		return true;
	}

	/** index 바로 앞(공백·주석 제외)이 { 나 , 인지. 객체 속성의 시작이다 */
	private boolean startsProperty(String text, int index, BitSet comments) {
		for (int i = index - 1; i >= 0; i--) {
			char c = text.charAt(i);
			if (comments.get(i) || Character.isWhitespace(c)) {
				continue;
			}
			return c == '{' || c == ',';
		}
		return false;
	}

	/** 호출이 아니라 함수를 만드는 곳인지. 예: this.fn_trans = function (sId, sUrl) 는 아니지만 function fn_trans(sId, sUrl) 는 맞다 */
	private boolean isDeclaration(String text, int index) {
		int from = Math.max(0, index - 12);
		return text.substring(from, index).trim().endsWith("function");
	}

	/* ---------------- 변수 값 읽기 ---------------- */

	/**
	 * <pre>
	 * 함수 본문 범위를 모두 모읍니다. 한 칸은 {여는 중괄호, 닫는 중괄호, 매개변수 시작, 매개변수 끝}.
	 * "function 이름(…) {" 와 "… = function(…) {" 모양의 중괄호만 함수 본문으로 봅니다.
	 * </pre>
	 */
	private List<int[]> functionScopes(String text, BitSet nonCode) {
		List<int[]> scopes = new ArrayList<int[]>();
		List<int[]> stack = new ArrayList<int[]>();
		for (int i = 0; i < text.length(); i++) {
			if (nonCode.get(i)) {
				continue;
			}
			char c = text.charAt(i);
			if (c == '{') {
				// 함수 본문이면 {i, -1, 매개변수 시작, 매개변수 끝}, 아니면 {i, -1, -1, -1}
				int[] params = functionParams(text, nonCode, i);
				stack.add(new int[] { i, -1, params == null ? -1 : params[0], params == null ? -1 : params[1] });
			} else if (c == '}' && !stack.isEmpty()) {
				int[] open = stack.remove(stack.size() - 1);
				if (open[2] >= 0) {
					scopes.add(new int[] { open[0], i, open[2], open[3] });
				}
			}
		}
		return scopes;
	}

	/** brace 자리의 { 가 함수 본문을 여는 것이면 {매개변수 시작, 매개변수 끝}, 아니면 null */
	private int[] functionParams(String text, BitSet nonCode, int brace) {
		int close = previousCode(text, nonCode, brace - 1);
		if (close < 0 || text.charAt(close) != ')') {
			return null;
		}
		// 짝이 맞는 여는 괄호를 거슬러 찾는다
		int depth = 0;
		int open = -1;
		int limit = Math.max(0, close - MAX_PARAMS_CHARS);
		for (int i = close; i >= limit; i--) {
			if (nonCode.get(i)) {
				continue;
			}
			char c = text.charAt(i);
			if (c == ')') {
				depth++;
			} else if (c == '(') {
				depth--;
				if (depth == 0) {
					open = i;
					break;
				}
			}
		}
		if (open < 0) {
			return null;
		}
		// 괄호 앞이 function 이거나 function 이름 이면 함수 정의다
		int before = previousCode(text, nonCode, open - 1);
		String word = wordEndingAt(text, before);
		if (!"function".equals(word) && word != null) {
			word = wordEndingAt(text, previousCode(text, nonCode, before - word.length()));
		}
		return "function".equals(word) ? new int[] { open + 1, close } : null;
	}

	/** from 부터 거슬러 처음 나오는 코드 글자(공백 제외)의 자리. 없으면 -1 */
	private int previousCode(String text, BitSet nonCode, int from) {
		for (int i = from; i >= 0; i--) {
			if (!nonCode.get(i) && !Character.isWhitespace(text.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	/** end 자리에서 끝나는 이름 글자. 이름 글자가 아니면 null */
	private String wordEndingAt(String text, int end) {
		if (end < 0) {
			return null;
		}
		int start = end;
		while (start >= 0 && isNameChar(text.charAt(start))) {
			start--;
		}
		return start == end ? null : text.substring(start + 1, end + 1);
	}

	private boolean isNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '$';
	}

	/**
	 * <pre>
	 * 변수 name 의 값을 읽습니다. 따옴표 글자를 딱 한 번 대입했을 때만 그 글자, 아니면 null.
	 *
	 * 흐름:
	 *   1) index 를 감싼 가장 안쪽 함수를 찾는다.
	 *   2) 그 함수의 매개변수면 모른다.
	 *   3) 그 함수 안에 대입이 있거나 var 로 만들었으면 그 함수 안만 본다. 아니면 파일 전체를 본다.
	 *   4) 대입이 하나뿐이고, = 이고(+= 아님), 오른쪽이 따옴표 글자 하나면 그 글자.
	 * </pre>
	 */
	String variableValue(String text, BitSet nonCode, List<int[]> scopes, String name, int index) {
		// 1) 감싼 함수
		int[] scope = null;
		for (int i = 0; i < scopes.size(); i++) {
			int[] s = scopes.get(i);
			if (s[0] < index && index < s[1] && (scope == null || s[0] > scope[0])) {
				scope = s;
			}
		}
		Pattern assign = Pattern.compile("(?<![A-Za-z0-9_$.])" + Pattern.quote(name) + "\\s*([-+*/%|&]?=)(?!=)");
		if (scope != null) {
			// 2) 매개변수
			Matcher param = Pattern.compile("(?<![A-Za-z0-9_$])" + Pattern.quote(name) + "(?![A-Za-z0-9_$])").matcher(text);
			param.region(scope[2], scope[3]);
			if (param.find()) {
				return null;
			}
			// 3) 함수 안의 대입
			List<int[]> inside = assignments(text, nonCode, assign, scope[0], scope[1]);
			Matcher declared = Pattern.compile("(?<![A-Za-z0-9_$.])(?:var|let|const)\\s+" + Pattern.quote(name) + "(?![A-Za-z0-9_$])").matcher(text);
			declared.region(scope[0], scope[1]);
			if (!inside.isEmpty() || declared.find()) {
				return single(text, inside);
			}
		}
		return single(text, assignments(text, nonCode, assign, 0, text.length()));
	}

	/** from ~ to 안의 대입들. 한 칸은 {대입 연산자 시작, 연산자 끝, = 하나뿐이면 1 아니면 0} */
	private List<int[]> assignments(String text, BitSet nonCode, Pattern assign, int from, int to) {
		List<int[]> found = new ArrayList<int[]>();
		Matcher m = assign.matcher(text);
		m.region(from, to);
		while (m.find()) {
			if (nonCode.get(m.start())) {
				continue;
			}
			found.add(new int[] { m.start(1), m.end(1), "=".equals(m.group(1)) ? 1 : 0 });
		}
		return found;
	}

	/** 대입이 딱 하나이고, = 이고, 오른쪽이 따옴표 글자 하나면 그 글자 */
	private String single(String text, List<int[]> found) {
		if (found.size() != 1 || found.get(0)[2] != 1) {
			return null;
		}
		int i = found.get(0)[1];
		while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
			i++;
		}
		String value = valueText(text, i);
		return value == null ? null : literal(value);
	}
}

package net.dstone.knowledge.semantic;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 리치클라이언트 화면(WebSquare, Nexacro, X-Platform) 파일의 글자에서 "서버를 부르는 곳"을 찾습니다.
 * 파일 하나의 글자만 봅니다. DB나 다른 파일은 보지 않으므로 그대로 단위 테스트할 수 있습니다.
 *
 * 찾는 것은 세 가지입니다.
 *   1) 주소          /order/list.do 처럼 /로 시작하는 경로, svc::order/list.do 처럼 서비스 접두어가 붙은 경로
 *   2) 거래 호출     HiJS.svr.doRequestAjax("JCST0200M01S", ...) 처럼 주소 대신 거래 ID로 부르는 호출
 *   3) 다른 화면     src="/app/ui/jc/JCAR0101G.xml" 처럼 따옴표 안에 적힌 다른 화면 파일
 *
 * 주석 안에 있는 것은 뺍니다. 지워 둔 옛 호출이 지금도 불리는 것처럼 보이면 안 되기 때문입니다.
 * </pre>
 */
public class ScreenScript {

	/** /로 시작하는 경로처럼 생긴 글자. 예: /order/list.do */
	private static final Pattern PATH = Pattern.compile("/[A-Za-z0-9_\\-]+(?:/[A-Za-z0-9_\\-]+)*(?:\\.[A-Za-z0-9]+)?");

	/** 서비스 접두어가 붙은 경로. 예: svc::order/list.do, svc::/order/list.do (Nexacro / X-Platform의 transaction) */
	private static final Pattern PREFIXED_PATH = Pattern.compile("[A-Za-z][A-Za-z0-9_]*::/?([A-Za-z0-9_\\-]+(?:/[A-Za-z0-9_\\-]+)*(?:\\.[A-Za-z0-9]+)?)");

	/** 자바 메소드 이름으로 쓸 수 있는 글자인지 */
	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

	/**
	 * <pre>
	 * 거래 호출 규칙 하나. "이 함수의 첫 인자(거래 ID)로 이 이름의 메소드가 불린다"는 뜻입니다.
	 * 예: 함수 doRequestAjax, 메소드 이름 틀 perform{id}
	 *     → doRequestAjax("JCST0200M01S") 는 performJCST0200M01S 메소드를 부른다.
	 * </pre>
	 */
	public static class CallRule {

		private final String function;
		private final String methodTemplate;
		private final Pattern pattern;

		public CallRule(String function, String methodTemplate) {
			this.function = function;
			this.methodTemplate = methodTemplate;
			// 함수 이름 앞에 다른 이름 글자가 붙어 있으면 다른 함수다(xdoRequestAjax). 점은 괜찮다(HiJS.svr.doRequestAjax).
			// 첫 인자가 따옴표로 싼 글자가 아니면(변수 등) 2번 묶음이 비어 있다.
			this.pattern = Pattern.compile("(?<![A-Za-z0-9_$])" + Pattern.quote(function) + "\\s*\\(\\s*(?:([\"'])([^\"'\\\\\\r\\n]{1,200})\\1)?");
		}

		public String getFunction() {
			return function;
		}

		public String getMethodTemplate() {
			return methodTemplate;
		}

		/** 거래 ID로 메소드 이름을 만듭니다. 메소드 이름이 될 수 없는 글자가 섞이면 null */
		public String methodNameOf(String id) {
			String name = methodTemplate.replace("{id}", id);
			return IDENTIFIER.matcher(name).matches() ? name : null;
		}
	}

	/** 화면에서 찾은 거래 호출 하나 */
	public static class Call {

		private final CallRule rule;
		private final String id;
		private final int index;

		Call(CallRule rule, String id, int index) {
			this.rule = rule;
			this.id = id;
			this.index = index;
		}

		public CallRule getRule() {
			return rule;
		}

		/** 거래 ID. 글자로 적혀 있지 않으면(변수로 넘긴 경우) null */
		public String getId() {
			return id;
		}

		/** 파일 안에서의 위치(글자 번호) */
		public int getIndex() {
			return index;
		}
	}

	/** 화면에서 찾은 글자 하나와 그 위치 (주소, 다른 화면의 경로) */
	public static class Hit {

		private final String text;
		private final int index;

		Hit(String text, int index) {
			this.text = text;
			this.index = index;
		}

		public String getText() {
			return text;
		}

		public int getIndex() {
			return index;
		}
	}

	/**
	 * <pre>
	 * 설정에 적힌 규칙 글자를 읽습니다.
	 * 모양: "함수=메소드이름틀" 을 쉼표로 이어 쓴다. 예: "doRequestAjax=perform{id}, gfnTransaction={id}"
	 * {id}가 없는 틀이나 빈 항목은 버립니다.
	 * </pre>
	 */
	public static List<CallRule> parseRules(String configured) {
		List<CallRule> rules = new ArrayList<CallRule>();
		if (configured == null) {
			return rules;
		}
		String[] items = configured.split(",");
		for (int i = 0; i < items.length; i++) {
			String item = items[i].trim();
			int eq = item.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String function = item.substring(0, eq).trim();
			String template = item.substring(eq + 1).trim();
			if (function.length() == 0 || template.indexOf("{id}") < 0) {
				continue;
			}
			rules.add(new CallRule(function, template));
		}
		return rules;
	}

	/** 규칙에 맞는 거래 호출을 파일에 나온 순서대로 찾습니다. */
	public List<Call> findCalls(String text, List<CallRule> rules) {
		List<Call> calls = new ArrayList<Call>();
		BitSet comments = commentMask(text);
		for (int i = 0; i < rules.size(); i++) {
			CallRule rule = rules.get(i);
			Matcher m = rule.pattern.matcher(text);
			while (m.find()) {
				if (comments.get(m.start()) || isDeclaration(text, m.start())) {
					continue;
				}
				calls.add(new Call(rule, m.group(2), m.start()));
			}
		}
		return calls;
	}

	/**
	 * <pre>
	 * 주소처럼 생긴 글자를 모두 찾습니다. 진입점과 같은지는 부르는 쪽이 맞춰 봅니다.
	 * 서비스 접두어가 붙은 것(svc::order/list.do)은 접두어를 떼고 앞에 /를 붙여 돌려줍니다.
	 * </pre>
	 */
	public List<Hit> findPaths(String text) {
		List<Hit> hits = new ArrayList<Hit>();
		BitSet comments = commentMask(text);
		Matcher m = PATH.matcher(text);
		while (m.find()) {
			if (!comments.get(m.start())) {
				hits.add(new Hit(m.group(), m.start()));
			}
		}
		m = PREFIXED_PATH.matcher(text);
		while (m.find()) {
			if (!comments.get(m.start())) {
				hits.add(new Hit("/" + m.group(1), m.start()));
			}
		}
		return hits;
	}

	/**
	 * <pre>
	 * 따옴표 안에 적힌, 이 확장자로 끝나는 경로를 찾습니다(다른 화면을 가리키는 글자).
	 * 뒤에 붙은 ?파라미터는 뗍니다. 실행할 때 조립되는 것(+ 로 이어 붙인 것)은 따옴표 안이 확장자로 끝나지 않아 잡히지 않습니다.
	 * </pre>
	 *
	 * @param extensions 점을 포함한 확장자들. 예: {".xml"}
	 */
	public List<Hit> findScreenRefs(String text, String[] extensions) {
		List<Hit> hits = new ArrayList<Hit>();
		StringBuilder alternatives = new StringBuilder();
		for (int i = 0; i < extensions.length; i++) {
			alternatives.append(i > 0 ? "|" : "").append(Pattern.quote(extensions[i]));
		}
		Pattern pattern = Pattern.compile("[\"']([A-Za-z0-9_\\-./:]+(?:" + alternatives + "))(?:\\?[^\"']*)?[\"']");
		BitSet comments = commentMask(text);
		Matcher m = pattern.matcher(text);
		while (m.find()) {
			if (!comments.get(m.start())) {
				hits.add(new Hit(m.group(1), m.start(1)));
			}
		}
		return hits;
	}

	/** 글자 번호가 몇 번째 줄인지 (1부터) */
	public int lineAt(String text, int index) {
		int line = 1;
		for (int i = 0; i < index && i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	/** 이 위치가 주석 안인지 봅니다. 한 번만 물어볼 때 쓰는 편의 메소드입니다(파일 전체를 훑으므로 반복해서 부르지 않는다). */
	boolean inComment(String text, int index) {
		return commentMask(text).get(index);
	}

	/**
	 * <pre>
	 * 파일을 앞에서부터 한 번 훑어, 주석 안에 있는 글자 자리를 표시해 돌려줍니다.
	 * 줄 주석(//), 블록 주석, XML 주석 세 가지를 봅니다.
	 * http://... 의 //는 주석이 아니므로, // 바로 앞이 :이면 주석으로 보지 않습니다.
	 *
	 * 찾은 것마다 앞쪽을 거슬러 훑으면 큰 화면 파일(수 MB)에서 시간이 제곱으로 늘어납니다. 그래서 한 번에 표시해 둡니다.
	 * </pre>
	 */
	private BitSet commentMask(String text) {
		BitSet mask = new BitSet(text.length());
		int length = text.length();
		int pos = 0;
		while (pos < length) {
			char c = text.charAt(pos);
			int end = -1;
			if (c == '/' && pos + 1 < length) {
				char next = text.charAt(pos + 1);
				if (next == '/' && (pos == 0 || text.charAt(pos - 1) != ':')) {
					end = text.indexOf('\n', pos);
					end = end < 0 ? length : end;
				} else if (next == '*') {
					end = closeOf(text, pos + 2, "*/");
				}
			} else if (c == '<' && text.startsWith("<!--", pos)) {
				end = closeOf(text, pos + 4, "-->");
			}
			if (end < 0) {
				pos++;
				continue;
			}
			mask.set(pos, end);
			pos = end;
		}
		return mask;
	}

	/** 주석이 닫히는 곳의 바로 다음 자리. 닫히지 않았으면 파일 끝 */
	private int closeOf(String text, int from, String close) {
		int found = text.indexOf(close, from);
		return found < 0 ? text.length() : found + close.length();
	}

	/** 호출이 아니라 함수를 만드는 곳인지. 예: function doRequestAjax(tranId) { */
	private boolean isDeclaration(String text, int index) {
		int from = Math.max(0, index - 12);
		return text.substring(from, index).trim().endsWith("function");
	}

}

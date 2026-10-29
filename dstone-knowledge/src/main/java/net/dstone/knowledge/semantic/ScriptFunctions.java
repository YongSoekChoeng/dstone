package net.dstone.knowledge.semantic;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 공통 스크립트(.js, .xjs)에서 함수 정의를 찾고, 글자에서 그 함수를 부르는 곳을 찾습니다.
 * 파일 하나의 글자만 봅니다. DB나 다른 파일은 보지 않으므로 그대로 단위 테스트할 수 있습니다.
 *
 * 화면이 다른 화면을 직접 열지 않고 공통 함수에 맡기는 프로젝트가 있습니다.
 *   화면       comLib.gfnOpenPopupEmp(joParam);
 *   com_lib.js comLib.gfnOpenPopupEmp = function(joParam) { comLib.openPopup("JZCO0102P", "/ui/jz/JZCO0102P.xml", options); };
 * 화면 글자만 봐서는 JZCO0102P 를 여는 줄 알 수 없으므로, 공통 함수의 본문을 읽어 둡니다(RichClientPlugin 이 씁니다).
 *
 * 함수로 보는 정의는 세 가지 모양입니다.
 *   comLib.gfnX = function(...) { ... }   이름에 점이 있으면 어느 깊이에 있어도 본다
 *   var gfnX = function(...) { ... }      점이 없는 이름은 파일 맨 바깥(중괄호 밖)에 있을 때만 본다
 *   function gfnX(...) { ... }            함수 안의 지역 함수(success, callback ...)까지 공통 함수로 보면 잡음이다
 * 객체 글자 안의 이름(success: function ...)은 보지 않습니다. 어느 객체의 것인지 알 수 없습니다.
 *
 * 문자열, 주석, 정규식 글자 안의 중괄호는 세지 않습니다. 본문의 끝을 찾지 못한 함수는 버립니다(짐작하지 않는다).
 * </pre>
 */
public class ScriptFunctions {

	/** 이름 한 마디 */
	private static final String NAME = "[A-Za-z_$][A-Za-z0-9_$]*";

	/** 점으로 이은 이름. 예: comLib.gfnOpenPopupEmp */
	private static final String CHAIN = NAME + "(?:\\." + NAME + ")*";

	/** comLib.gfnX = function(  /  var gfnX = function name( */
	private static final Pattern ASSIGNED = Pattern.compile("(?<![A-Za-z0-9_$.])(" + CHAIN + ")\\s*=\\s*function\\b\\s*(?:" + NAME + ")?\\s*\\(");

	/** function gfnX( */
	private static final Pattern DECLARED = Pattern.compile("(?<![A-Za-z0-9_$.])function\\s+(" + NAME + ")\\s*\\(");

	/** 이름( 처럼 부르는 곳. 앞에 이름 글자나 점이 붙어 있으면 더 긴 이름의 일부다 */
	private static final Pattern CALL = Pattern.compile("(?<![A-Za-z0-9_$.])(" + CHAIN + ")\\s*\\(");

	/** 정규식 글자(/ab+c/)가 시작될 수 있는 바로 앞 글자. 그 밖의 / 는 나눗셈이다 */
	private static final String REGEX_BEFORE = "(,=:[!&|?{};+-*%<>~^";

	/** 스크립트에서 찾은 함수 정의 하나 */
	public static class Function {

		private final String name;
		private final int index;
		private final String body;

		Function(String name, int index, String body) {
			this.name = name;
			this.index = index;
			this.body = body;
		}

		/** 적힌 그대로의 이름. 예: comLib.gfnOpenPopupEmp, gfnX */
		public String getName() {
			return name;
		}

		/** 파일 안에서 이름이 시작하는 자리(글자 번호) */
		public int getIndex() {
			return index;
		}

		/** 본문. 여는 중괄호부터 닫는 중괄호까지 */
		public String getBody() {
			return body;
		}
	}

	/** 글자에서 찾은 함수 호출 하나 */
	public static class Call {

		private final String name;
		private final int index;

		Call(String name, int index) {
			this.name = name;
			this.index = index;
		}

		/** 부른 함수. 찾을 이름 가운데 맞은 것 */
		public String getName() {
			return name;
		}

		/** 글자 번호 */
		public int getIndex() {
			return index;
		}
	}

	/** 스크립트 파일의 함수 정의를 파일에 나온 순서대로 찾습니다. */
	public List<Function> findFunctions(String text) {
		BitSet skip = nonCode(text);
		List<int[]> found = new ArrayList<int[]>();
		List<String> names = new ArrayList<String>();
		collect(text, skip, ASSIGNED, found, names);
		collect(text, skip, DECLARED, found, names);

		// 나온 순서로 세워 두고, 앞에서부터 중괄호 깊이를 한 번만 센다.
		sortByIndex(found, names);
		List<Function> functions = new ArrayList<Function>();
		int depth = 0;
		int cursor = 0;
		for (int i = 0; i < found.size(); i++) {
			int[] at = found.get(i);
			depth += braceDelta(text, skip, cursor, at[0]);
			cursor = at[0];
			String name = names.get(i);
			if (name.indexOf('.') < 0 && depth != 0) {
				// 점 없는 이름이 함수 안에 있다. 지역 함수다.
				continue;
			}
			int open = codeIndexOf(text, skip, '{', closeOf(text, skip, at[1], '(', ')'));
			int close = open < 0 ? -1 : closeOf(text, skip, open, '{', '}');
			if (close < 0) {
				continue;
			}
			functions.add(new Function(name, at[0], text.substring(open, close)));
		}
		return functions;
	}

	/**
	 * <pre>
	 * 글자에서 이 이름들의 함수를 부르는 곳을 찾습니다.
	 * 이름에 점이 있으면 적힌 호출의 뒤쪽이 같으면 맞습니다(window.comLib.gfnX 도 comLib.gfnX 다).
	 * 점이 없는 이름은 앞에 아무것도 붙지 않은 호출만 맞습니다(scwin.gfnX 는 다른 함수다).
	 * </pre>
	 *
	 * @param skip 보지 않을 글자 자리(주석 등). null 이면 모두 본다
	 */
	public List<Call> findCalls(String text, Set<String> names, BitSet skip) {
		List<Call> calls = new ArrayList<Call>();
		if (names.isEmpty()) {
			return calls;
		}
		Matcher m = CALL.matcher(text);
		while (m.find()) {
			if ((skip != null && skip.get(m.start())) || isDeclaration(text, m.start())) {
				continue;
			}
			String name = matchName(m.group(1), names);
			if (name != null) {
				calls.add(new Call(name, m.start()));
			}
		}
		return calls;
	}

	/** 화면이 이 이름의 함수를 스스로 만드는지. 그러면 그 화면에서는 공통 함수가 아니다 */
	public boolean definesItself(String text, String name) {
		String quoted = Pattern.quote(name);
		return Pattern.compile("(?<![A-Za-z0-9_$.])" + quoted + "\\s*=\\s*function\\b").matcher(text).find()
				|| (name.indexOf('.') < 0 && Pattern.compile("(?<![A-Za-z0-9_$.])function\\s+" + quoted + "\\s*\\(").matcher(text).find());
	}

	/** 적힌 호출(a.b.c)이 찾을 이름 가운데 무엇인지. 없으면 null */
	private String matchName(String written, Set<String> names) {
		if (names.contains(written)) {
			return written;
		}
		int dot = written.indexOf('.');
		while (dot >= 0) {
			String tail = written.substring(dot + 1);
			if (tail.indexOf('.') < 0) {
				// 마지막 한 마디만 남았다. 점 없는 이름은 앞에 붙은 것이 없을 때만 맞는다.
				return null;
			}
			if (names.contains(tail)) {
				return tail;
			}
			dot = written.indexOf('.', dot + 1);
		}
		return null;
	}

	/** 호출이 아니라 함수를 만드는 곳인지. 예: function gfnX(a) { */
	private boolean isDeclaration(String text, int index) {
		int from = Math.max(0, index - 12);
		return text.substring(from, index).trim().endsWith("function");
	}

	private void collect(String text, BitSet skip, Pattern pattern, List<int[]> found, List<String> names) {
		Matcher m = pattern.matcher(text);
		while (m.find()) {
			if (skip.get(m.start(1))) {
				continue;
			}
			// [이름 시작, 매개변수를 여는 괄호 자리]
			found.add(new int[] { m.start(1), m.end() - 1 });
			names.add(m.group(1));
		}
	}

	/** 두 목록을 함께 글자 번호 순으로 세웁니다(정의 수가 적어 단순 삽입 정렬로 충분하다). */
	private void sortByIndex(List<int[]> found, List<String> names) {
		for (int i = 1; i < found.size(); i++) {
			int[] key = found.get(i);
			String name = names.get(i);
			int j = i - 1;
			while (j >= 0 && found.get(j)[0] > key[0]) {
				found.set(j + 1, found.get(j));
				names.set(j + 1, names.get(j));
				j--;
			}
			found.set(j + 1, key);
			names.set(j + 1, name);
		}
	}

	/** from 부터 to 앞까지 코드 안의 { 와 } 수의 차이 */
	private int braceDelta(String text, BitSet skip, int from, int to) {
		int delta = 0;
		for (int i = from; i < to; i++) {
			if (skip.get(i)) {
				continue;
			}
			char c = text.charAt(i);
			if (c == '{') {
				delta++;
			} else if (c == '}') {
				delta--;
			}
		}
		return delta;
	}

	/** from 부터 찾은 코드 안의 글자 c 의 자리. 없으면 -1 */
	private int codeIndexOf(String text, BitSet skip, char c, int from) {
		if (from < 0) {
			return -1;
		}
		for (int i = from; i < text.length(); i++) {
			if (!skip.get(i) && text.charAt(i) == c) {
				return i;
			}
		}
		return -1;
	}

	/** open 자리의 여는 괄호를 닫는 괄호의 바로 다음 자리. 닫히지 않으면 -1 */
	private int closeOf(String text, BitSet skip, int open, char opening, char closing) {
		int depth = 0;
		for (int i = open; i < text.length(); i++) {
			if (skip.get(i)) {
				continue;
			}
			char c = text.charAt(i);
			if (c == opening) {
				depth++;
			} else if (c == closing) {
				depth--;
				if (depth == 0) {
					return i + 1;
				}
			}
		}
		return -1;
	}

	/**
	 * <pre>
	 * 코드가 아닌 글자 자리(주석, 문자열, 정규식 글자)를 표시해 돌려줍니다. 앞에서부터 한 번만 훑습니다.
	 * 정규식 글자는 / 바로 앞의 코드 글자로 가립니다(그 앞이 ( , = 같은 것이면 정규식, 이름이나 ) 면 나눗셈).
	 * 문자열과 정규식은 줄이 바뀌면 끝난 것으로 봅니다(닫히지 않은 따옴표 하나가 파일 끝까지 먹지 않게).
	 * </pre>
	 */
	BitSet nonCode(String text) {
		BitSet mask = new BitSet(text.length());
		int length = text.length();
		char previous = 0;
		int pos = 0;
		while (pos < length) {
			char c = text.charAt(pos);
			int end = -1;
			boolean comment = false;
			if (c == '/' && pos + 1 < length && text.charAt(pos + 1) == '/') {
				end = text.indexOf('\n', pos);
				end = end < 0 ? length : end;
				comment = true;
			} else if (c == '/' && pos + 1 < length && text.charAt(pos + 1) == '*') {
				int found = text.indexOf("*/", pos + 2);
				end = found < 0 ? length : found + 2;
				comment = true;
			} else if (c == '"' || c == '\'' || c == '`') {
				end = endOfQuoted(text, pos, c, c != '`');
			} else if (c == '/' && (previous == 0 || REGEX_BEFORE.indexOf(previous) >= 0)) {
				end = endOfRegex(text, pos);
			}
			if (end < 0) {
				if (!Character.isWhitespace(c)) {
					previous = c;
				}
				pos++;
				continue;
			}
			mask.set(pos, end);
			// 문자열·정규식 뒤의 / 는 나눗셈이다. 주석은 앞 글자를 바꾸지 않는다.
			if (!comment) {
				previous = 'a';
			}
			pos = end;
		}
		return mask;
	}

	/** 따옴표 글자의 끝(닫는 따옴표 다음 자리) */
	private int endOfQuoted(String text, int start, char quote, boolean stopAtNewline) {
		for (int i = start + 1; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\\') {
				i++;
			} else if (c == quote) {
				return i + 1;
			} else if (c == '\n' && stopAtNewline) {
				return i;
			}
		}
		return text.length();
	}

	/** 정규식 글자의 끝. [ ] 안의 / 는 끝이 아니다 */
	private int endOfRegex(String text, int start) {
		boolean inClass = false;
		for (int i = start + 1; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\\') {
				i++;
			} else if (c == '[') {
				inClass = true;
			} else if (c == ']') {
				inClass = false;
			} else if (c == '/' && !inClass) {
				return i + 1;
			} else if (c == '\n') {
				return i;
			}
		}
		return text.length();
	}

}

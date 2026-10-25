package net.dstone.knowledge.semantic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 화면 파일(JSP, WebSquare, Nexacro / X-Platform)의 글자에서 화면명을 찾습니다.
 * 파일 하나의 글자만 보므로 그대로 단위 테스트할 수 있습니다.
 *
 * 찾는 곳은 두 군데입니다. 먼저 나오는 것을 씁니다.
 *   1) <title>게시글 목록</title>                       JSP, WebSquare (via = TITLE)
 *   2) <Form id="OrderList" titletext="주문 목록">      Nexacro / X-Platform (via = TITLETEXT)
 *
 * 실행할 때 정해지는 글(${...}, <%= %>, #{...})이나 태그가 섞인 글은 화면명으로 치지 않습니다.
 * 주석(<!-- -->) 안에 있는 것도 뺍니다.
 * </pre>
 */
public class ScreenTitle {

	public static final String VIA_TITLE = "TITLE";
	public static final String VIA_TITLETEXT = "TITLETEXT";

	/** 화면명으로 쓰기에 너무 긴 글. 이보다 길면 제목이 아니라 다른 것이 들어 있는 것이다 */
	private static final int MAX_LENGTH = 200;

	private static final Pattern TITLE = Pattern.compile("<title(?:\\s[^>]*)?>(.*?)</title\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern TITLETEXT = Pattern.compile("<Form\\b[^>]*?\\stitletext\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", Pattern.DOTALL);

	private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

	private final String name;
	private final String via;
	private final int line;

	private ScreenTitle(String name, String via, int line) {
		this.name = name;
		this.via = via;
		this.line = line;
	}

	/** 화면명 */
	public String getName() {
		return name;
	}

	/** 어디서 찾았는지: TITLE / TITLETEXT */
	public String getVia() {
		return via;
	}

	/** 찾은 줄(1부터) */
	public int getLine() {
		return line;
	}

	/**
	 * <pre>
	 * 화면명을 찾습니다. 없으면 null입니다.
	 * </pre>
	 */
	public static ScreenTitle of(String text) {
		if (text == null) {
			return null;
		}
		// 주석은 같은 길이의 공백으로 바꾼다. 위치(줄 번호)가 그대로 남는다.
		String visible = blankComments(text);

		Matcher title = TITLE.matcher(visible);
		if (title.find()) {
			String name = clean(title.group(1));
			if (name != null) {
				return new ScreenTitle(name, VIA_TITLE, lineOf(visible, title.start()));
			}
		}
		Matcher form = TITLETEXT.matcher(visible);
		if (form.find()) {
			String name = clean(form.group(2) != null ? form.group(2) : form.group(3));
			if (name != null) {
				return new ScreenTitle(name, VIA_TITLETEXT, lineOf(visible, form.start()));
			}
		}
		return null;
	}

	/** 앞뒤 공백과 줄바꿈을 정리합니다. 화면명으로 쓸 수 없는 글이면 null */
	static String clean(String raw) {
		if (raw == null) {
			return null;
		}
		String name = unescape(raw.replaceAll("\\s+", " ").trim());
		if (name.length() == 0 || name.length() > MAX_LENGTH) {
			return null;
		}
		if (name.indexOf('<') >= 0 || name.indexOf('>') >= 0 || name.indexOf("${") >= 0 || name.indexOf("#{") >= 0) {
			return null;
		}
		return name;
	}

	/** XML 에서 자주 쓰는 문자 참조만 되돌립니다 */
	private static String unescape(String text) {
		if (text.indexOf('&') < 0) {
			return text;
		}
		return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
				.replace("&nbsp;", " ").replace("&amp;", "&");
	}

	private static String blankComments(String text) {
		Matcher comment = COMMENT.matcher(text);
		if (!comment.find()) {
			return text;
		}
		StringBuilder sb = new StringBuilder(text);
		do {
			for (int i = comment.start(); i < comment.end(); i++) {
				if (sb.charAt(i) != '\n') {
					sb.setCharAt(i, ' ');
				}
			}
		} while (comment.find());
		return sb.toString();
	}

	private static int lineOf(String text, int offset) {
		int line = 1;
		for (int i = 0; i < offset; i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}
}

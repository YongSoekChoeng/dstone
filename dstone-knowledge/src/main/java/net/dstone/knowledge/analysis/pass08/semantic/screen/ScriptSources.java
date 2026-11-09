package net.dstone.knowledge.analysis.pass08.semantic.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 화면(JSP) 글자에서 &lt;script src="..."&gt; 로 불러오는 스크립트 파일의 경로를 찾습니다.
 * 파일 하나의 글자만 봅니다. DB는 보지 않으므로 그대로 단위 테스트할 수 있습니다(JspPlugin 이 씁니다).
 *
 * 화면이 서버 주소를 JSP 에 적지 않고 불러온 .js 파일에 적는 경우가 있습니다.
 *   impact.jsp           &lt;script src="&lt;%=ctx%&gt;/knowledge/assets/js/knowledge-common.js"&gt;
 *   knowledge-common.js  fetch(contextPath + "/knowledge/api/call.do", ...)
 * 그래서 화면이 불러오는 스크립트를 찾아 두고, 그 스크립트에 적힌 주소까지 화면의 요청으로 봅니다.
 *
 * 경로는 실행할 때 붙는 앞부분을 떼고 "글자로 적힌 뒷부분"만 씁니다.
 *   &lt;%=ctx%&gt;/js/a.js, ${ctx}/js/a.js 처럼 앞에 붙은 것 → /js/a.js
 *   &lt;c:url value='/js/a.js'/&gt;                          → /js/a.js
 *   /js/a.js?v=3                                                 → /js/a.js
 * 다른 서버의 파일(http://, https://, //cdn...)과 .js 로 끝나지 않는 것은 뺍니다.
 * 뒷부분까지 실행할 때 정해지는 경로(${ctx}/js/${name}.js)도 뺍니다. 짐작하지 않습니다.
 * </pre>
 */
public class ScriptSources {

	/** &lt;script ... src="..."&gt;. 따옴표는 큰따옴표와 작은따옴표 둘 다 본다 */
	private static final Pattern SCRIPT_SRC = Pattern.compile("<script\\b[^>]*?\\bsrc\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	/** src 안의 &lt;c:url value="..."/&gt; 의 value */
	private static final Pattern C_URL = Pattern.compile("<c:url\\s+value\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

	/** 찾은 스크립트 경로 하나 */
	public static class Source {

		private final String written;
		private final String path;
		private final int index;

		Source(String written, String path, int index) {
			this.written = written;
			this.path = path;
			this.index = index;
		}

		/** src 에 적힌 그대로. 예: &lt;%=ctx%&gt;/js/a.js?v=3 */
		public String getWritten() {
			return written;
		}

		/** 앞부분과 ?파라미터를 뗀 경로. 앞의 / 와 ./ ../ 도 뗀다. 예: js/a.js */
		public String getPath() {
			return path;
		}

		/** 화면 글자에서 &lt;script 가 시작하는 자리 */
		public int getIndex() {
			return index;
		}
	}

	/** 화면 글자에서 불러오는 스크립트 경로를 나온 순서대로 찾습니다. 같은 경로는 한 번만 돌려줍니다. */
	public List<Source> find(String text) {
		List<Source> sources = new ArrayList<Source>();
		List<String> seen = new ArrayList<String>();
		Matcher m = SCRIPT_SRC.matcher(text);
		while (m.find()) {
			String written = m.group(2).trim();
			String path = pathOf(written);
			if (path == null || seen.contains(path)) {
				continue;
			}
			seen.add(path);
			sources.add(new Source(written, path, m.start()));
		}
		return sources;
	}

	/** src 값에서 글자로 적힌 뒷부분의 경로. 쓸 수 없으면 null */
	static String pathOf(String written) {
		String value = written;
		Matcher cUrl = C_URL.matcher(value);
		if (cUrl.find()) {
			value = cUrl.group(1);
		}
		int query = value.indexOf('?');
		if (query >= 0) {
			value = value.substring(0, query);
		}
		String lower = value.toLowerCase();
		if (lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("//")) {
			return null;
		}
		// 실행할 때 붙는 앞부분(<%= %>, ${ }, <c:url .../>)은 마지막 닫는 자리 뒤만 쓴다.
		int cut = Math.max(Math.max(value.lastIndexOf("%>"), value.lastIndexOf('}')), value.lastIndexOf("/>"));
		if (cut >= 0) {
			value = value.substring(cut + (value.startsWith("/>", cut) || value.startsWith("%>", cut) ? 2 : 1));
			if (!value.startsWith("/")) {
				// 앞부분이 아니라 이름 한가운데가 실행할 때 정해진다(${ctx}/js/${name}.js → ".js" 만 남는다).
				return null;
			}
		}
		if (value.indexOf("${") >= 0 || value.indexOf("<%") >= 0 || value.indexOf('<') >= 0) {
			// 뒷부분에도 실행할 때 정해지는 것이 있다.
			return null;
		}
		while (value.startsWith("/") || value.startsWith("./") || value.startsWith("../")) {
			value = value.substring(value.indexOf('/') + 1);
		}
		if (value.length() == 0 || !value.toLowerCase().endsWith(".js") || value.indexOf("..") >= 0) {
			return null;
		}
		return value;
	}

}

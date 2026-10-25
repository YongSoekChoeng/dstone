package net.dstone.knowledge.semantic;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 파일 안에 적힌 메뉴 목록에서 "화면명 + 화면 경로" 쌍을 찾습니다.
 * 화면 파일에 &lt;title&gt; 도 titletext 도 없을 때 화면명을 채우는 데 씁니다(ScreenMenuPlugin).
 * 파일 하나의 글자만 보므로 그대로 단위 테스트할 수 있습니다.
 *
 * 규칙은 "화면명 키=화면 경로 키" 입니다. 예: MENU_NM=SRC_PATH
 * 한 메뉴 항목 안에 두 키가 함께 있으면 쌍으로 칩니다. 이런 모양을 읽습니다.
 *   스크립트 / JSON   {'MENU_ID':'M050031','MENU_NM':'履行期間管理', ... ,'SRC_PATH':'/ui/jc/JCIQ0700G.xml'}
 *                     {"MENU_NM": "주문 목록", "SRC_PATH": "/order/list.jsp"},  {MENU_NM: '주문 목록', SRC_PATH: '...'}
 *   XML 속성          <menu MENU_NM="주문 목록" SRC_PATH="/order/list.jsp"/>
 * 한 메뉴 항목은 경로 키를 감싼 가장 가까운 { } 또는 &lt; &gt; 입니다.
 * 경로가 비어 있는 항목(하위 메뉴만 묶는 상위 메뉴)과 주석 안의 항목은 뺍니다.
 * </pre>
 */
public class ScreenMenu {

	/**
	 * <pre>
	 * 메뉴 규칙 하나. 화면명이 적힌 키와 화면 경로가 적힌 키입니다.
	 * </pre>
	 */
	public static class Rule {

		private final String nameKey;
		private final String pathKey;
		private final Pattern namePattern;
		private final Pattern pathPattern;

		public Rule(String nameKey, String pathKey) {
			this.nameKey = nameKey;
			this.pathKey = pathKey;
			this.namePattern = pairPattern(nameKey);
			this.pathPattern = pairPattern(pathKey);
		}

		public String getNameKey() {
			return nameKey;
		}

		public String getPathKey() {
			return pathKey;
		}

		/** 키: '값' / "키": "값" / 키="값" 모양. 3번 묶음이 값이다. 키 앞에 이름 글자가 붙어 있으면 다른 키다(SUB_MENU_NM) */
		private static Pattern pairPattern(String key) {
			return Pattern.compile("(?<![A-Za-z0-9_$])([\"']?)" + Pattern.quote(key) + "\\1\\s*[:=]\\s*([\"'])([^\"'\\r\\n]*)\\2");
		}
	}

	/**
	 * <pre>
	 * 찾은 메뉴 항목 하나.
	 * </pre>
	 */
	public static class Entry {

		private final String name;
		private final String path;
		private final int line;

		Entry(String name, String path, int line) {
			this.name = name;
			this.path = path;
			this.line = line;
		}

		/** 화면명 */
		public String getName() {
			return name;
		}

		/** 메뉴에 적힌 화면 경로 그대로. 예: /ui/jc/JCIQ0700G.xml */
		public String getPath() {
			return path;
		}

		/** 화면 경로가 적힌 줄(1부터) */
		public int getLine() {
			return line;
		}
	}

	private final ScreenScript screenScript = new ScreenScript();

	/**
	 * <pre>
	 * 설정 글자를 규칙으로 바꿉니다.
	 * 모양: "화면명 키=화면 경로 키" 를 쉼표로 이어 쓴다. 예: "MENU_NM=SRC_PATH, menuName=url"
	 * 키가 하나라도 비어 있는 항목은 버립니다.
	 * </pre>
	 */
	public static List<Rule> parseRules(String configured) {
		List<Rule> rules = new ArrayList<Rule>();
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
			String nameKey = item.substring(0, eq).trim();
			String pathKey = item.substring(eq + 1).trim();
			if (nameKey.length() == 0 || pathKey.length() == 0) {
				continue;
			}
			rules.add(new Rule(nameKey, pathKey));
		}
		return rules;
	}

	/** 이 파일에 메뉴 목록이 있을 수 있는지. 경로 키가 글자로 들어 있지 않으면 훑지 않는다 */
	public static boolean mayContain(String text, List<Rule> rules) {
		for (int i = 0; i < rules.size(); i++) {
			if (text.indexOf(rules.get(i).getPathKey()) >= 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * <pre>
	 * 파일 하나에서 메뉴 항목을 찾습니다. 파일에 적힌 순서대로 돌려줍니다.
	 * </pre>
	 */
	public List<Entry> findEntries(String text, List<Rule> rules) {
		List<Entry> entries = new ArrayList<Entry>();
		if (text == null || rules.isEmpty() || !mayContain(text, rules)) {
			return entries;
		}
		BitSet comments = screenScript.commentMask(text);
		for (int r = 0; r < rules.size(); r++) {
			Rule rule = rules.get(r);
			Matcher path = rule.pathPattern.matcher(text);
			while (path.find()) {
				String written = path.group(3).trim();
				if (written.length() == 0 || comments.get(path.start())) {
					continue;
				}
				int start = recordStart(text, path.start());
				int end = recordEnd(text, path.end());
				Matcher name = rule.namePattern.matcher(text).region(start, end);
				String found = null;
				while (found == null && name.find()) {
					found = ScreenTitle.clean(name.group(3));
				}
				if (found != null) {
					entries.add(new Entry(found, written, screenScript.lineAt(text, path.start())));
				}
			}
		}
		return entries;
	}

	/** 메뉴 항목이 시작하는 곳: 앞쪽에서 가장 가까운 { 또는 &lt; 의 자리. 없으면 파일 처음 */
	private int recordStart(String text, int from) {
		int brace = text.lastIndexOf('{', from);
		int tag = text.lastIndexOf('<', from);
		return Math.max(0, Math.max(brace, tag));
	}

	/** 메뉴 항목이 끝나는 곳: 뒤쪽에서 가장 가까운 } 또는 &gt; 의 자리. 없으면 파일 끝 */
	private int recordEnd(String text, int from) {
		int brace = text.indexOf('}', from);
		int tag = text.indexOf('>', from);
		int end = text.length();
		if (brace >= 0) {
			end = Math.min(end, brace);
		}
		if (tag >= 0) {
			end = Math.min(end, tag);
		}
		return end;
	}
}

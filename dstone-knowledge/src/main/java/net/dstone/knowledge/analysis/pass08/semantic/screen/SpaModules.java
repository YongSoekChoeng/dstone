package net.dstone.knowledge.analysis.pass08.semantic.screen;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * Vue / React 파일(.vue .jsx .tsx .js .ts) 하나에서 "무엇을 어디서 가져오고(import), 맨 바깥에 무엇을 만드나(정의)"를 읽습니다.
 * 파일 하나만 보는 순수한 클래스입니다. 파일 사이를 잇는 일은 SpaLinker 가 합니다.
 *
 * 읽는 것:
 *   import          import X from './a', import { a, b as c } from '@/api/board', import * as api from './api'
 *                   import('./views/A.vue'), const x = require('./x')
 *                   import type { T } from './types' 는 값이 아니라서 뺍니다.
 *   다시 내보내기    export { a, b as c } from './x', export * from './x'
 *   맨 바깥 정의     function a() {…}, export async function a() {…}, export const a = (…) =&gt; …, class A {…}
 *                   정의의 범위는 "그 정의가 시작한 곳부터 다음 맨 바깥 문장이 시작하기 전까지"입니다.
 *   기본 내보내기    export default http → 이름 http, export default { … } / defineStore(…) → 이름 없는 범위
 *
 * 정규식과 중괄호 깊이로 읽습니다(파서를 쓰지 않는다). 맨 바깥 문장은 줄 맨 앞에서 시작한다고 봅니다(프레티어 등 포매터의 모양).
 * </pre>
 */
public class SpaModules {

	/** 기본 내보내기의 이름 자리. export default 다음이 이름 하나가 아닐 때 그 범위를 이 이름의 정의로 둔다 */
	public static final String DEFAULT = "default";

	private static final String NAME = "[A-Za-z_$][A-Za-z0-9_$]*";

	/** import … from '…' (여러 줄에 걸쳐도 된다). 1: type, 2: 가져오는 것들, 3: 모듈 이름 */
	private static final Pattern IMPORT_FROM = Pattern.compile("(?m)^[ \\t]*import\\s+(type\\s+)?([^;'\"`]*?)\\s+from\\s*['\"]([^'\"\\r\\n]+)['\"]");

	/** import '…' (가져오는 이름 없이). 1: 모듈 이름 */
	private static final Pattern IMPORT_BARE = Pattern.compile("(?m)^[ \\t]*import\\s*['\"]([^'\"\\r\\n]+)['\"]");

	/** import('…'). 1: 모듈 이름 */
	private static final Pattern IMPORT_DYNAMIC = Pattern.compile("(?<![A-Za-z0-9_$.])import\\s*\\(\\s*['\"`]([^'\"`\\r\\n]+)['\"`]\\s*\\)");

	/** const x = require('…'), const { a, b } = require('…'). 1: 이름, 2: { … } 안, 3: 모듈 이름 */
	private static final Pattern REQUIRE = Pattern.compile("(?<![A-Za-z0-9_$.])(?:const|let|var)\\s+(?:(" + NAME + ")|\\{([^}]*)\\})\\s*=\\s*require\\s*\\(\\s*['\"]([^'\"\\r\\n]+)['\"]\\s*\\)");

	/** export { … } from '…', export * from '…', export * as ns from '…'. 1: { … } 안, 2: * 다음의 as 이름, 3: 모듈 이름 */
	private static final Pattern RE_EXPORT = Pattern.compile("(?m)^[ \\t]*export\\s+(?:type\\s+)?(?:\\{([^}]*)\\}|\\*(?:\\s+as\\s+(" + NAME + "))?)\\s*from\\s*['\"]([^'\"\\r\\n]+)['\"]");

	/** export { a, b as c } (from 없이). 1: { … } 안 */
	private static final Pattern EXPORT_LIST = Pattern.compile("(?m)^[ \\t]*export\\s*\\{([^}]*)\\}(?!\\s*from)");

	/**
	 * 이름 있는 맨 바깥 정의. 1: export default, 2: 함수 이름, 3: 변수 이름, 4: 클래스 이름
	 */
	private static final Pattern DEFINITION = Pattern.compile("(?m)^[ \\t]*(?:export\\s+(default\\s+)?)?(?:declare\\s+)?(?:async\\s+)?(?:function\\s*\\*?\\s*(" + NAME
			+ ")\\s*[<(]|(?:const|let|var)\\s+(" + NAME + ")\\s*[:=]|(?:abstract\\s+)?class\\s+(" + NAME + ")\\b)");

	/** export default 다음이 이름 하나인 것. 1: 이름 */
	private static final Pattern DEFAULT_NAME = Pattern.compile("(?m)^[ \\t]*export\\s+default\\s+(" + NAME + ")\\s*;?[ \\t]*(?:$|//)");

	/** export default 로 시작하는 줄 */
	private static final Pattern DEFAULT_ANY = Pattern.compile("(?m)^[ \\t]*export\\s+default\\b");

	/** 맨 바깥 문장의 시작으로 보는 줄. 정의의 끝을 정할 때 쓴다 */
	private static final Pattern STATEMENT = Pattern.compile("(?m)^[ \\t]*(?:export|import|const|let|var|function|async|class|abstract|type|interface|enum|declare)\\b");

	/** Vue 단일 파일 컴포넌트의 &lt;script&gt; 와 &lt;/script&gt; */
	private static final Pattern SCRIPT_OPEN = Pattern.compile("(?i)<script\\b[^>]*>");

	private final ScriptFunctions scriptFunctions = new ScriptFunctions();

	private final ScreenScript screenScript = new ScreenScript();

	/** 가져오기 하나 */
	public static class Import {

		private final String local;
		private final String imported;
		private final String spec;
		private final int index;
		private final boolean dynamic;

		Import(String local, String imported, String spec, int index, boolean dynamic) {
			this.local = local;
			this.imported = imported;
			this.spec = spec;
			this.index = index;
			this.dynamic = dynamic;
		}

		/** 이 파일 안에서 부르는 이름. 이름 없이 가져오면(import './a.css', import('./A.vue')) null */
		public String getLocal() {
			return local;
		}

		/** 가져오는 모듈 쪽 이름. 기본 내보내기면 "default", 통째(* as ns)면 "*". 이름 없이 가져오면 null */
		public String getImported() {
			return imported;
		}

		/** 적힌 모듈 이름. 예: ./http, @/api/board, vue */
		public String getSpec() {
			return spec;
		}

		/** 파일 안에서 적힌 자리(글자 번호) */
		public int getIndex() {
			return index;
		}

		/** import('…') 로 가져왔는지 */
		public boolean isDynamic() {
			return dynamic;
		}
	}

	/** 다시 내보내기 하나: export { imported as exported } from 'spec', export * from 'spec' */
	public static class ReExport {

		private final String exported;
		private final String imported;
		private final String spec;

		ReExport(String exported, String imported, String spec) {
			this.exported = exported;
			this.imported = imported;
			this.spec = spec;
		}

		/** 내보내는 이름. export * from 이면 "*" */
		public String getExported() {
			return exported;
		}

		/** 그 모듈 쪽 이름. export * from 이면 "*" */
		public String getImported() {
			return imported;
		}

		public String getSpec() {
			return spec;
		}
	}

	/** 파일 하나를 읽은 결과 */
	public static class Module {

		private final List<Import> imports = new ArrayList<Import>();
		/** import 문이 차지한 범위들. 이름이 쓰인 곳을 셀 때 뺀다 */
		private final List<int[]> importRanges = new ArrayList<int[]>();
		private final List<ReExport> reExports = new ArrayList<ReExport>();
		/** 내보내는 이름 → 파일 안의 이름(export { a as b } 의 b → a) */
		private final Map<String, String> exportAliases = new LinkedHashMap<String, String>();
		/** 맨 바깥 정의 이름 → {시작, 끝} */
		private final Map<String, int[]> definitions = new LinkedHashMap<String, int[]>();
		/** export default 이름 이면 그 이름. 아니면 null */
		private String defaultName;

		public List<Import> getImports() {
			return imports;
		}

		public List<ReExport> getReExports() {
			return reExports;
		}

		public Map<String, int[]> getDefinitions() {
			return definitions;
		}

		/**
		 * <pre>
		 * 내보내는 이름이 이 파일 안의 어느 이름인지. 정의나 import 로 이어지지 않으면 null.
		 *   default → export default 이름 의 이름, 아니면 기본 내보내기 범위("default")
		 *   b       → export { a as b } 의 a, 아니면 b
		 * </pre>
		 */
		public String localNameOf(String exported) {
			if (DEFAULT.equals(exported)) {
				if (defaultName != null) {
					return defaultName;
				}
				String alias = exportAliases.get(DEFAULT);
				if (alias != null) {
					return alias;
				}
				return definitions.containsKey(DEFAULT) ? DEFAULT : null;
			}
			String alias = exportAliases.get(exported);
			return alias != null ? alias : exported;
		}

		/** 이 이름으로 가져온 import. 없으면 null */
		public Import importOf(String local) {
			for (int i = 0; i < imports.size(); i++) {
				if (local.equals(imports.get(i).getLocal())) {
					return imports.get(i);
				}
			}
			return null;
		}

		/** index 가 import 문 안인지 */
		boolean inImport(int index) {
			for (int i = 0; i < importRanges.size(); i++) {
				int[] range = importRanges.get(i);
				if (range[0] <= index && index < range[1]) {
					return true;
				}
			}
			return false;
		}
	}

	/** 이름이 쓰인 곳 하나 */
	public static class Use {

		private final String name;
		private final String member;
		private final int index;

		Use(String name, String member, int index) {
			this.name = name;
			this.member = member;
			this.index = index;
		}

		/** 쓰인 이름(import 한 이름이나 맨 바깥 정의 이름) */
		public String getName() {
			return name;
		}

		/** ns.fetchBoards 처럼 점 뒤의 이름까지 쓰였으면 그 이름. 아니면 null */
		public String getMember() {
			return member;
		}

		public int getIndex() {
			return index;
		}
	}

	/**
	 * <pre>
	 * 파일 하나를 읽습니다.
	 *
	 * 흐름:
	 *   1) import, require, import('…') 를 모은다(주석 안의 것은 뺀다).
	 *   2) 다시 내보내기와 export { … } 를 모은다.
	 *   3) 맨 바깥(중괄호 깊이 0)에서 시작하는 정의를 찾고, 다음 맨 바깥 문장 앞까지를 그 범위로 둔다.
	 *      .vue 는 &lt;script&gt; 안만 본다(템플릿에는 정의가 없다).
	 * </pre>
	 */
	public Module parse(String text) {
		Module module = new Module();
		BitSet comments = screenScript.commentMask(text);
		// 1) 가져오기
		readImports(text, comments, module);
		// 2) 다시 내보내기
		Matcher m = RE_EXPORT.matcher(text);
		while (m.find()) {
			if (comments.get(m.start())) {
				continue;
			}
			module.importRanges.add(new int[] { m.start(), m.end() });
			if (m.group(1) == null) {
				String as = m.group(2);
				module.reExports.add(new ReExport(as == null ? "*" : as, "*", m.group(3)));
				continue;
			}
			String[] parts = m.group(1).split(",");
			for (int i = 0; i < parts.length; i++) {
				String[] pair = asPair(parts[i]);
				if (pair != null) {
					module.reExports.add(new ReExport(pair[1], pair[0], m.group(3)));
				}
			}
		}
		m = EXPORT_LIST.matcher(text);
		while (m.find()) {
			if (comments.get(m.start())) {
				continue;
			}
			String[] parts = m.group(1).split(",");
			for (int i = 0; i < parts.length; i++) {
				String[] pair = asPair(parts[i]);
				if (pair != null) {
					module.exportAliases.put(pair[1], pair[0]);
				}
			}
		}
		// 3) 맨 바깥 정의
		readDefinitions(text, comments, module);
		return module;
	}

	/** import / require / import('…') 를 모읍니다 */
	private void readImports(String text, BitSet comments, Module module) {
		Matcher m = IMPORT_FROM.matcher(text);
		while (m.find()) {
			if (comments.get(m.start())) {
				continue;
			}
			module.importRanges.add(new int[] { m.start(), m.end() });
			if (m.group(1) != null) {
				// import type { … } 은 타입만 가져온다. 실행되는 코드가 아니다.
				continue;
			}
			readClause(m.group(2), m.group(3), m.start(), module);
		}
		m = IMPORT_BARE.matcher(text);
		while (m.find()) {
			if (!comments.get(m.start())) {
				module.importRanges.add(new int[] { m.start(), m.end() });
				module.imports.add(new Import(null, null, m.group(1), m.start(), false));
			}
		}
		m = IMPORT_DYNAMIC.matcher(text);
		while (m.find()) {
			if (!comments.get(m.start())) {
				module.importRanges.add(new int[] { m.start(), m.end() });
				module.imports.add(new Import(null, null, m.group(1), m.start(), true));
			}
		}
		m = REQUIRE.matcher(text);
		while (m.find()) {
			if (comments.get(m.start())) {
				continue;
			}
			module.importRanges.add(new int[] { m.start(), m.end() });
			if (m.group(1) != null) {
				module.imports.add(new Import(m.group(1), DEFAULT, m.group(3), m.start(), false));
				continue;
			}
			String[] parts = m.group(2).split(",");
			for (int i = 0; i < parts.length; i++) {
				// const { a: b } = require('x') 의 a: b 는 a as b 와 같다.
				String[] pair = asPair(parts[i].replace(':', ' ').replaceAll("\\s+", " ").trim().replace(" ", " as "));
				if (pair != null) {
					module.imports.add(new Import(pair[1], pair[0], m.group(3), m.start(), false));
				}
			}
		}
	}

	/**
	 * <pre>
	 * import 와 from 사이를 읽습니다.
	 *   X                → X = default
	 *   X, { a, b as c } → X = default, a = a, c = b
	 *   { a, type T }    → a = a (type 은 뺀다)
	 *   * as ns          → ns = *
	 * </pre>
	 */
	private void readClause(String clause, String spec, int index, Module module) {
		String rest = clause.trim();
		int brace = rest.indexOf('{');
		if (brace >= 0) {
			int close = rest.indexOf('}', brace);
			if (close < 0) {
				return;
			}
			String[] parts = rest.substring(brace + 1, close).split(",");
			for (int i = 0; i < parts.length; i++) {
				String part = parts[i].trim();
				if (part.startsWith("type ")) {
					continue;
				}
				String[] pair = asPair(part);
				if (pair != null) {
					module.imports.add(new Import(pair[1], pair[0], spec, index, false));
				}
			}
			rest = (rest.substring(0, brace) + rest.substring(close + 1)).trim();
		}
		String[] heads = rest.split(",");
		for (int i = 0; i < heads.length; i++) {
			String head = heads[i].trim();
			if (head.length() == 0) {
				continue;
			}
			if (head.startsWith("*")) {
				String ns = head.substring(1).trim();
				if (ns.startsWith("as ")) {
					ns = ns.substring(3).trim();
					if (ns.matches(NAME)) {
						module.imports.add(new Import(ns, "*", spec, index, false));
					}
				}
				continue;
			}
			if (head.matches(NAME)) {
				module.imports.add(new Import(head, DEFAULT, spec, index, false));
			}
		}
	}

	/** "a" → {a, a}, "a as b" → {a, b}, "default as b" → {default, b}. 이름이 아니면 null */
	private String[] asPair(String part) {
		String p = part.trim();
		if (p.startsWith("type ")) {
			return null;
		}
		String[] words = p.split("\\s+as\\s+");
		if (words.length == 1 && words[0].matches(NAME)) {
			return new String[] { words[0], words[0] };
		}
		if (words.length == 2 && words[0].trim().matches(NAME) && words[1].trim().matches(NAME)) {
			return new String[] { words[0].trim(), words[1].trim() };
		}
		return null;
	}

	/**
	 * <pre>
	 * 맨 바깥 정의를 찾습니다.
	 *   1) 코드가 아닌 자리(주석, 문자열, 정규식)를 빼고 중괄호 깊이를 앞에서부터 한 번 센다.
	 *   2) 깊이 0 에서 시작하는 정의와 맨 바깥 문장의 시작 자리를 모은다.
	 *   3) 정의마다, 그 뒤에 처음 오는 맨 바깥 문장 앞까지를 범위로 둔다.
	 * </pre>
	 */
	private void readDefinitions(String text, BitSet comments, Module module) {
		int[] script = scriptRange(text);
		BitSet nonCode = scriptFunctions.nonCode(text);
		int[] depthAt = depths(text, nonCode, script);
		// 2) 맨 바깥 문장의 시작들
		List<Integer> starts = new ArrayList<Integer>();
		Matcher s = STATEMENT.matcher(text);
		s.region(script[0], script[1]);
		while (s.find()) {
			if (!comments.get(s.start()) && depthAt[s.start() - script[0]] == 0) {
				starts.add(Integer.valueOf(s.start()));
			}
		}
		// 3) 이름 있는 정의
		Matcher m = DEFINITION.matcher(text);
		m.region(script[0], script[1]);
		while (m.find()) {
			if (comments.get(m.start()) || depthAt[m.start() - script[0]] != 0) {
				continue;
			}
			String name = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
			if (!module.definitions.containsKey(name)) {
				int end = statementEnd(text, nonCode, m.end(), nextStart(starts, m.start(), script[1]));
				module.definitions.put(name, new int[] { m.start(), end });
				if (m.group(3) != null) {
					readMembers(text, nonCode, depthAt, script[0], name, m.end(), end, module);
				}
			}
			if (m.group(1) != null) {
				module.defaultName = name;
			}
		}
		// 기본 내보내기
		Matcher d = DEFAULT_NAME.matcher(text);
		d.region(script[0], script[1]);
		if (module.defaultName == null && d.find() && !comments.get(d.start())) {
			module.defaultName = d.group(1);
			return;
		}
		Matcher any = DEFAULT_ANY.matcher(text);
		any.region(script[0], script[1]);
		if (module.defaultName == null && any.find() && !comments.get(any.start()) && depthAt[any.start() - script[0]] == 0) {
			module.definitions.put(DEFAULT, new int[] { any.start(), statementEnd(text, nonCode, any.end(), nextStart(starts, any.start(), script[1])) });
		}
	}

	/** 객체 멤버의 시작. listGroups(…) {, async save(…) {, list: (…) =&gt;, list: async (…) =&gt;, list: function, list: x =&gt; */
	private static final Pattern MEMBER = Pattern.compile("(?m)^[ \\t]*(?:async\\s+)?(" + NAME + ")\\s*(?:<[^<>()]*>)?\\s*(?:\\(|:\\s*(?:async\\s*)?(?:function\\b|\\(|" + NAME + "\\s*=>))");

	/**
	 * <pre>
	 * 변수의 값이 객체 리터럴이면(export const codeService = { listGroups() {…}, … }) 멤버마다 "이름.멤버" 정의를 따로 둡니다.
	 * 화면이 codeService.listGroups() 를 쓰면 객체 전체가 아니라 그 멤버만 따라가게 하려는 것입니다.
	 * 멤버의 범위는 그 멤버가 시작한 곳부터 다음 멤버가 시작하기 전(마지막이면 객체가 닫히는 곳)까지입니다.
	 * </pre>
	 */
	private void readMembers(String text, BitSet nonCode, int[] depthAt, int offset, String name, int from, int end, Module module) {
		int open = from;
		while (open < end && Character.isWhitespace(text.charAt(open))) {
			open++;
		}
		if (open >= end || text.charAt(open) != '{') {
			return;
		}
		int depth = depthAt[open - offset] + 1;
		int close = text.lastIndexOf('}', end - 1);
		if (close <= open) {
			return;
		}
		List<int[]> starts = new ArrayList<int[]>();
		List<String> names = new ArrayList<String>();
		Matcher m = MEMBER.matcher(text);
		m.region(open + 1, close);
		while (m.find()) {
			if (!nonCode.get(m.start(1)) && depthAt[m.start(1) - offset] == depth) {
				starts.add(new int[] { m.start(1) });
				names.add(m.group(1));
			}
		}
		for (int i = 0; i < starts.size(); i++) {
			int start = starts.get(i)[0];
			int stop = i + 1 < starts.size() ? starts.get(i + 1)[0] : close;
			String key = name + "." + names.get(i);
			if (!module.definitions.containsKey(key)) {
				module.definitions.put(key, new int[] { start, stop });
			}
		}
	}

	/** 줄 끝에 오면 식이 다음 줄로 이어지는 글자. 예: const a = (x) =&gt;\n  f(x) */
	private static final String CONTINUES_AFTER = "=>,([{+-*/%&|?:.";

	/** 줄 맨 앞에 오면 앞 줄의 식을 잇는 글자. 예: axios\n  .get(…) */
	private static final String CONTINUES_BEFORE = ".?:+-*/%&|=,)]}";

	/**
	 * <pre>
	 * 정의 문장이 끝나는 자리. 괄호 깊이가 0 인 곳의 ; 나, 식이 이어지지 않는 줄바꿈에서 끝납니다. limit 를 넘지 않습니다.
	 *   const http = axios.create({ … })
	 *   http.interceptors.response.use(…)   ← 여기는 http 정의가 아니다(다른 문장)
	 * </pre>
	 */
	private int statementEnd(String text, BitSet nonCode, int from, int limit) {
		int depth = 0;
		char previous = 0;
		for (int i = from; i < limit; i++) {
			if (nonCode.get(i)) {
				previous = 'a';
				continue;
			}
			char c = text.charAt(i);
			if (c == '(' || c == '[' || c == '{') {
				depth++;
			} else if (c == ')' || c == ']' || c == '}') {
				depth = Math.max(0, depth - 1);
			} else if (depth == 0 && c == ';') {
				return i + 1;
			} else if (depth == 0 && c == '\n' && previous != 0 && CONTINUES_AFTER.indexOf(previous) < 0) {
				int next = i + 1;
				while (next < limit && (Character.isWhitespace(text.charAt(next)) || nonCode.get(next))) {
					next++;
				}
				if (next >= limit || CONTINUES_BEFORE.indexOf(text.charAt(next)) < 0) {
					return i;
				}
			}
			if (!Character.isWhitespace(c)) {
				previous = c;
			}
		}
		return limit;
	}

	/** from 뒤에 처음 오는 맨 바깥 문장의 자리. 없으면 end */
	private int nextStart(List<Integer> starts, int from, int end) {
		for (int i = 0; i < starts.size(); i++) {
			int at = starts.get(i).intValue();
			if (at > from) {
				return at;
			}
		}
		return end;
	}

	/** 범위 안 글자마다 그 자리의 중괄호 깊이 */
	private int[] depths(String text, BitSet nonCode, int[] range) {
		int[] depth = new int[Math.max(0, range[1] - range[0]) + 1];
		int current = 0;
		for (int i = range[0]; i < range[1]; i++) {
			depth[i - range[0]] = current;
			if (nonCode.get(i)) {
				continue;
			}
			char c = text.charAt(i);
			if (c == '{') {
				current++;
			} else if (c == '}' && current > 0) {
				current--;
			}
		}
		depth[depth.length - 1] = current;
		return depth;
	}

	/**
	 * <pre>
	 * 정의를 찾을 범위. .vue(태그로 시작하는 파일)면 첫 &lt;script&gt; 안, 아니면 파일 전체.
	 * &lt;script&gt; 와 &lt;script setup&gt; 이 둘 다 있으면 앞의 것만 봅니다(import 는 파일 전체에서 읽는다).
	 * </pre>
	 */
	private int[] scriptRange(String text) {
		int[] whole = new int[] { 0, text.length() };
		if (!text.trim().startsWith("<")) {
			return whole;
		}
		Matcher open = SCRIPT_OPEN.matcher(text);
		if (!open.find()) {
			return whole;
		}
		int close = text.toLowerCase(Locale.ROOT).indexOf("</script>", open.end());
		return new int[] { open.end(), close < 0 ? text.length() : close };
	}

	/**
	 * <pre>
	 * from ~ to 안에서 이 이름들이 쓰인 곳을 찾습니다. 주석과 import 문 안은 뺍니다.
	 * 이름 앞이 점이면(obj.fetchBoards) 다른 것의 속성이라 뺍니다. 이름 뒤가 점이면 그 뒤의 이름을 member 로 남깁니다(api.fetchBoards).
	 * 부르지 않고 넘기기만 해도(queryFn: fetchBoards, onMounted(search)) 쓴 것으로 봅니다.
	 * 정의하는 자리(function fetchBoards)도 걸리므로, 정의 안을 볼 때는 부르는 쪽이 자기 이름을 뺍니다.
	 * </pre>
	 */
	public List<Use> uses(String text, Module module, int from, int to, Collection<String> names) {
		List<Use> uses = new ArrayList<Use>();
		if (names.isEmpty() || from >= to) {
			return uses;
		}
		StringBuilder alternatives = new StringBuilder();
		for (String name : names) {
			alternatives.append(alternatives.length() > 0 ? "|" : "").append(Pattern.quote(name));
		}
		Pattern pattern = Pattern.compile("(?<![A-Za-z0-9_$.])(" + alternatives + ")(?![A-Za-z0-9_$])(?:\\s*\\??\\.\\s*(" + NAME + "))?");
		BitSet comments = screenScript.commentMask(text);
		Matcher m = pattern.matcher(text);
		m.region(from, Math.min(to, text.length()));
		while (m.find()) {
			if (comments.get(m.start()) || module.inImport(m.start())) {
				continue;
			}
			uses.add(new Use(m.group(1), m.group(2), m.start()));
		}
		return uses;
	}

}

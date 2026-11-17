package net.dstone.knowledge.analysis.pass08.semantic.screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.analysis.common.EncodingDetector;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.common.util.JsonText;

/**
 * <pre>
 * Vue / React 화면(SCAN 이 VUE, REACT 로 가린 파일)을 Java 쪽과 잇습니다.
 *
 * 이런 화면은 서버를 바로 부르기보다 API 모듈(api/board.js), 훅(useBoardList.ts), 컴포저블, 스토어를 import 해서 부릅니다.
 * 그래서 화면에 적힌 import 를 따라가, 화면이 실제로 쓰는 함수 안의 서버 호출까지 찾습니다.
 *
 * 만드는 관계:
 *   화면 ─REQUESTS→ 메소드   화면이 이 진입점을 부른다. 서버를 부르는 자리는 규칙 파일 screen: 의 url: 로 적는다(conf/rules/common/spa.yml).
 *       화면에 바로 적힌 호출   http.get('/sample/boards') (via = URL_RULE). 진입점이 하나로 정해지면 HIGH
 *       import 를 따라간 호출  fetchBoards() → api/board.js 의 http.get('/sample/boards') (via = IMPORT, function, script)
 *                             함수 안에서 조건에 따라 부를 수도 있으므로 진입점이 하나여도 MEDIUM, 여럿이면 LOW
 *       진입점을 못 찾은 호출   사실 그대로 UNRESOLVED 로 남긴다(to_external = "GET /sample/boards")
 *       주소 맞추기: 경로 변수(`/boards/${seq}` ↔ /boards/{seq}), HTTP 메소드(get ↔ GET), baseURL(screen: 의 base:)을 본다(SpaEndpoints).
 *       baseURL 이 프로젝트에 여럿이면 어느 것인지 몰라 한 단계 낮춘다.
 *   화면 ─INCLUDES→ 화면     화면이 다른 컴포넌트 파일(.vue, React 컴포넌트)을 import 한다 (via = IMPORT, HIGH)
 *   스크립트 ─INCLUDES→ 화면  라우터(router/index.js)나 main.js 처럼 화면이 아닌 스크립트가 화면을 import 한다
 *
 * import 의 모듈 이름은 상대 경로(./a, ../api/board)와, tsconfig.json · jsconfig.json 의 paths 별칭(@/* → src/*)으로 찾습니다.
 * 설정 파일이 없으면 @/ 와 ~/ 를 가장 가까운 src 폴더로 봅니다(Vue CLI, Vite, Nuxt 의 기본값). 확장자는 .ts .tsx .js .jsx .vue .mjs 와 /index.* 를 차례로 붙여 봅니다.
 * 패키지 이름(vue, axios, react)은 프로젝트 밖이라 따라가지 않습니다.
 *
 * 아직 다루지 않는 것:
 * - 라우터의 주소(path: '/sample/boards')와 화면명(meta.title). 화면과 라우터 파일의 연결(INCLUDES)만 남긴다.
 * - vite.config 의 resolve.alias, webpack 의 alias 처럼 코드로 적은 별칭.
 * - 객체를 통째로 내보낸 API 모듈(export default { list() {…} })의 메소드 하나하나. 통째로 따라간다.
 * - 스토어 안의 액션 하나하나. 화면이 스토어를 쓰면 그 스토어가 부르는 것을 모두 이 화면이 부르는 것으로 본다(MEDIUM / LOW).
 * </pre>
 */
@Component
public class SpaLinker implements SemanticStep {

	/** Vue / React 화면의 파일 종류(analysis_file.file_type) */
	public static final List<String> SCREEN_TYPES = Arrays.asList("VUE", "REACT");

	/** import 에 확장자가 없을 때 붙여 보는 것들. 차례대로 본다 */
	private static final String[] EXTENSIONS = { ".ts", ".tsx", ".js", ".jsx", ".vue", ".mjs" };

	/** 설정 파일이 없을 때 src 폴더로 보는 별칭 */
	private static final String[] DEFAULT_ALIASES = { "@/", "~/" };

	/** 별칭을 읽는 설정 파일. 같은 폴더에 여럿이면 앞의 것부터 본다 */
	private static final String[] ALIAS_CONFIGS = { "tsconfig.json", "tsconfig.app.json", "jsconfig.json" };

	/** import 를 따라가는 깊이의 한도(화면 → 훅 → API 함수 → 래퍼 …) */
	private static final int MAX_DEPTH = 12;

	/** tsconfig 의 "baseUrl": "." */
	private static final Pattern BASE_URL = Pattern.compile("\"baseUrl\"\\s*:\\s*\"([^\"]*)\"");

	/** tsconfig 의 "paths": { 안의 "@/*": ["src/*"] */
	private static final Pattern PATH_ENTRY = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private EncodingDetector encodingDetector;

	private final SpaModules spaModules = new SpaModules();

	private final ScreenUrls screenUrls = new ScreenUrls();

	private final ScreenScript screenScript = new ScreenScript();

	@Override
	public String name() {
		return "spa";
	}

	/**
	 * <pre>
	 * 흐름:
	 *   1) 스크립트 파일(.js .ts .vue .jsx .tsx …) 목록을 읽는다. Vue / React 화면이 없으면 바로 끝난다.
	 *   2) 화면이 아닌 스크립트를 한 번씩 읽어 baseURL(screen: 의 base:)을 모으고, 화면을 import 하면 INCLUDES 로 잇는다.
	 *   3) 진입점(주소, HTTP 메소드, 처리 메소드)을 읽어 둔다.
	 *   4) 화면마다: 바로 적힌 호출 → 다른 화면 import → 쓰는 import 를 따라간 호출 순서로 잇는다.
	 * </pre>
	 */
	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		// 1) 스크립트 파일 목록
		Map<String, Map<String, Object>> files = new LinkedHashMap<String, Map<String, Object>>();
		List<Map<String, Object>> rows = semanticDao.selectScriptFiles(revisionId);
		int screens = 0;
		for (int i = 0; i < rows.size(); i++) {
			files.put((String) rows.get(i).get("path"), rows.get(i));
			if (SCREEN_TYPES.contains(rows.get(i).get("fileType"))) {
				screens++;
			}
		}
		if (screens == 0) {
			return "Vue / React 화면 없음.";
		}
		Run run = new Run(context, Paths.get(context.getProjectValue("localPath")).toRealPath(), files);

		// 2) baseURL, 스크립트 → 화면
		List<ScreenUrls.Rule> baseRules = context.getProjectRules().screenBases();
		for (Map.Entry<String, Map<String, Object>> entry : files.entrySet()) {
			if (SCREEN_TYPES.contains(entry.getValue().get("fileType"))) {
				continue;
			}
			context.checkCancelled();
			String text = run.read(entry.getKey());
			if (text == null) {
				continue;
			}
			List<ScreenUrls.Hit> found = screenUrls.findBases(text, baseRules);
			for (int b = 0; b < found.size(); b++) {
				if (!run.bases.containsKey(found.get(b).getUrl())) {
					run.bases.put(found.get(b).getUrl(), found.get(b).getRule());
				}
			}
			if (text.indexOf("import") >= 0) {
				linkIncludes(run, entry.getKey(), text, spaModules.parse(text));
			}
		}

		// 3) 진입점
		run.endpoints = new SpaEndpoints(semanticDao.selectEndpointRoutes(revisionId));

		// 4) 화면
		for (Map.Entry<String, Map<String, Object>> entry : files.entrySet()) {
			if (!SCREEN_TYPES.contains(entry.getValue().get("fileType"))) {
				continue;
			}
			context.checkCancelled();
			String text = run.read(entry.getKey());
			if (text == null) {
				continue;
			}
			run.counts.screens++;
			linkScreen(run, entry.getKey(), text);
		}
		Counts c = run.counts;
		return "Vue / React 화면 " + c.screens + "개. 화면에 적힌 호출로 이은 요청 " + c.direct + ", import 를 따라가 이은 요청 " + c.imported
				+ ", 진입점을 못 찾은 호출 " + c.unresolved + ", 화면 → 화면 " + c.includes + ", 스크립트 → 화면 " + c.scriptIncludes
				+ ", baseURL " + run.bases.keySet() + ", 따라간 모듈 " + run.modules.size() + ".";
	}

	/* ============================== 화면 하나 ============================== */

	/**
	 * <pre>
	 * 화면 하나를 잇습니다.
	 *   a) 화면에 바로 적힌 서버 호출
	 *   b) import 한 다른 화면(컴포넌트) → INCLUDES
	 *   c) 화면이 쓰는 import 이름마다 그 정의를 따라가 서버 호출을 모은다(화면이 쓰지 않는 import 는 따라가지 않는다)
	 * 같은 메소드는 한 번만 잇습니다. a) 를 먼저 해서 HIGH 가 남게 합니다.
	 * </pre>
	 */
	private void linkScreen(Run run, String path, String text) {
		long fileId = run.fileId(path);
		ModuleInfo self = run.store(path, text);
		Set<String> linked = new HashSet<String>();
		Set<String> unresolved = new HashSet<String>();

		// a) 화면에 바로 적힌 호출
		for (int i = 0; i < self.allHits.size(); i++) {
			ScreenUrls.Hit hit = self.allHits.get(i);
			linkRequest(run, fileId, hit, null, null, screenScript.lineAt(text, hit.getIndex()), linked, unresolved);
		}

		// b) 다른 화면 import
		linkIncludes(run, path, text, self.module);

		// c) 화면이 쓰는 import 를 따라간 호출
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < self.module.getImports().size(); i++) {
			String local = self.module.getImports().get(i).getLocal();
			if (local != null && !names.contains(local)) {
				names.add(local);
			}
		}
		List<SpaModules.Use> uses = spaModules.uses(text, self.module, 0, text.length(), names);
		Set<String> seen = new HashSet<String>();
		for (int u = 0; u < uses.size(); u++) {
			SpaModules.Use use = uses.get(u);
			SpaModules.Import imp = self.module.importOf(use.getName());
			boolean namespace = "*".equals(imp.getImported());
			if (namespace && use.getMember() == null) {
				continue;
			}
			Target target = run.resolveImport(path, imp, namespace ? use.getMember() : imp.getImported(), 0);
			if (target == null) {
				continue;
			}
			// codeService.listGroups() 처럼 객체의 멤버를 쓰면 그 멤버만 따라간다.
			Target member = namespace ? null : run.member(target, use.getMember());
			if (member != null) {
				target = member;
			}
			String label = namespace || member != null ? use.getName() + "." + use.getMember() : use.getName();
			if (!seen.add(label)) {
				continue;
			}
			List<Reached> reached = new ArrayList<Reached>();
			run.collect(target, new HashSet<String>(), reached, 0);
			int line = screenScript.lineAt(text, use.getIndex());
			for (int r = 0; r < reached.size(); r++) {
				linkRequest(run, fileId, reached.get(r).hit, label, reached.get(r).script, line, linked, unresolved);
			}
		}
	}

	/**
	 * <pre>
	 * 파일이 import 하는 Vue / React 화면을 INCLUDES 로 잇습니다. 자기 자신과, 한 번 이은 화면은 다시 잇지 않습니다.
	 * React 화면(.tsx)에서 이름만 가져오는 것(import { useAuth } from './useAuth')은 컴포넌트를 쓰는 것이 아니라서 잇지 않습니다.
	 * 대문자로 시작하는 이름(컴포넌트), 기본 내보내기, import('…'), .vue 파일만 잇습니다.
	 * </pre>
	 */
	private void linkIncludes(Run run, String path, String text, SpaModules.Module module) {
		Set<String> done = new HashSet<String>();
		boolean fromScreen = SCREEN_TYPES.contains(run.files.get(path).get("fileType"));
		for (int i = 0; i < module.getImports().size(); i++) {
			SpaModules.Import imp = module.getImports().get(i);
			String target = run.resolveSpec(path, imp.getSpec());
			if (target == null || target.equals(path) || done.contains(target)) {
				continue;
			}
			String fileType = (String) run.files.get(target).get("fileType");
			if (!SCREEN_TYPES.contains(fileType)) {
				continue;
			}
			boolean component = "VUE".equals(fileType) || imp.isDynamic() || imp.getLocal() == null || SpaModules.DEFAULT.equals(imp.getImported())
					|| Character.isUpperCase(imp.getLocal().charAt(0));
			if (!component) {
				continue;
			}
			done.add(target);
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("path", imp.getSpec());
			properties.put("via", "IMPORT");
			semanticDao.insertRelation(relation(run.revisionId, run.fileId(path), "INCLUDES", "FILE", "F" + run.fileId(target), null, "HIGH", "RESOLVED"
					, properties, Integer.valueOf(screenScript.lineAt(text, imp.getIndex()))));
			if (fromScreen) {
				run.counts.includes++;
			} else {
				run.counts.scriptIncludes++;
			}
		}
	}

	/**
	 * <pre>
	 * 찾은 서버 호출 하나를 진입점과 잇습니다.
	 *   1) 진입점과 맞춰 볼 수 없는 주소(주소 전체가 값인 래퍼의 fetch(API_BASE + path) 등)는 버린다.
	 *   2) 적힌 그대로 맞춰 보고, 없으면 baseURL 을 앞에 붙여 맞춰 본다.
	 *   3) 없으면 UNRESOLVED 로 한 번만 남긴다.
	 *   4) 있으면 메소드마다 REQUESTS. 신뢰도는 바로 적힌 호출이면 HIGH(진입점 하나) / MEDIUM(여럿),
	 *      import 를 따라간 호출이면 MEDIUM / LOW. 여러 baseURL 가운데 하나를 붙여 맞았으면 한 단계 낮춘다.
	 * </pre>
	 *
	 * @param function import 를 따라갔으면 화면이 쓴 이름(fetchBoards, api.fetchBoards). 바로 적힌 호출이면 null
	 * @param script   import 를 따라갔으면 호출이 적힌 파일
	 */
	private void linkRequest(Run run, long fileId, ScreenUrls.Hit hit, String function, String script, int line, Set<String> linked
			, Set<String> unresolved) {
		// 1) 맞춰 볼 만한 주소인지
		if (!SpaEndpoints.usable(hit.getUrl())) {
			return;
		}
		// 2) 그대로, 그다음 baseURL 을 붙여서
		String url = hit.getUrl();
		String base = null;
		boolean guessedBase = false;
		List<String> methods = run.endpoints.match(url, hit.getVerb());
		if (methods.isEmpty()) {
			for (String candidate : run.bases.keySet()) {
				List<String> found = run.endpoints.match(candidate + url, hit.getVerb());
				if (found.isEmpty()) {
					continue;
				}
				if (base != null) {
					guessedBase = true;
					continue;
				}
				base = candidate;
				methods = found;
			}
			guessedBase = guessedBase || (base != null && run.bases.size() > 1);
		}
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("path", base == null ? url : base + url);
		if (hit.getVerb() != null) {
			properties.put("verb", hit.getVerb());
		}
		properties.put("via", function == null ? "URL_RULE" : "IMPORT");
		properties.put("rule", hit.getRule().getText());
		properties.put("ruleAt", hit.getRule().getAt());
		if (base != null) {
			properties.put("base", base);
			properties.put("baseRuleAt", run.bases.get(base).getAt());
		}
		if (hit.getVariable() != null) {
			properties.put("variable", hit.getVariable());
		}
		if (function != null) {
			properties.put("function", function);
			properties.put("script", script);
		}
		// 3) 진입점 없음
		if (methods.isEmpty()) {
			String key = (hit.getVerb() == null ? "" : hit.getVerb() + " ") + url;
			if (unresolved.add(key)) {
				semanticDao.insertRelation(relation(run.revisionId, fileId, "REQUESTS", "METHOD", null, key, "UNRESOLVED", "UNRESOLVED", properties
						, Integer.valueOf(line)));
				run.counts.unresolved++;
			}
			return;
		}
		// 4) 진입점마다
		boolean single = methods.size() == 1;
		int rank = (function == null ? 3 : 2) - (single ? 0 : 1) - (guessedBase ? 1 : 0);
		String confidence = rank >= 3 ? "HIGH" : rank == 2 ? "MEDIUM" : "LOW";
		for (int i = 0; i < methods.size(); i++) {
			if (!linked.add(methods.get(i))) {
				continue;
			}
			semanticDao.insertRelation(relation(run.revisionId, fileId, "REQUESTS", "METHOD", methods.get(i), null, confidence
					, single && !guessedBase ? "RESOLVED" : "HEURISTIC", properties, Integer.valueOf(line)));
			if (function == null) {
				run.counts.direct++;
			} else {
				run.counts.imported++;
			}
		}
	}

	/** 화면 파일에서 나가는 관계 한 행을 Map 으로 만듭니다(DAO 에 넘길 모양). */
	private Map<String, Object> relation(long revisionId, long fileId, String relationType, String toKind, String toId, String toExternal
			, String confidence, String resolutionStatus, Map<String, Object> properties, Integer lineStart) {
		Map<String, Object> relation = new HashMap<String, Object>();
		relation.put("revisionId", revisionId);
		relation.put("fromKind", "FILE");
		relation.put("fromId", "F" + fileId);
		relation.put("relationType", relationType);
		relation.put("toKind", toKind);
		relation.put("toId", toId);
		relation.put("toExternal", toExternal);
		relation.put("confidence", confidence);
		relation.put("resolutionStatus", resolutionStatus);
		relation.put("propertiesJson", JsonText.of(properties));
		relation.put("fileId", Long.valueOf(fileId));
		relation.put("lineStart", lineStart);
		return relation;
	}

	/* ============================== 모듈 ============================== */

	/** 읽어 둔 파일 하나. 글자는 들고 있지 않고, 정의마다 찾은 호출과 쓰는 이름만 남긴다 */
	private static class ModuleInfo {
		final String path;
		final SpaModules.Module module;
		/** 파일 전체에서 찾은 서버 호출 */
		final List<ScreenUrls.Hit> allHits;
		/** 맨 바깥 정의 이름 → 그 안의 서버 호출 */
		final Map<String, List<ScreenUrls.Hit>> hits = new HashMap<String, List<ScreenUrls.Hit>>();
		/** 맨 바깥 정의 이름 → 그 안에서 쓴 이름(같은 파일의 정의, import 한 이름) */
		final Map<String, List<SpaModules.Use>> refs = new HashMap<String, List<SpaModules.Use>>();

		ModuleInfo(String path, SpaModules.Module module, List<ScreenUrls.Hit> allHits) {
			this.path = path;
			this.module = module;
			this.allHits = allHits;
		}
	}

	/** 따라갈 곳: 파일과 그 안의 맨 바깥 정의 이름 */
	private static class Target {
		final ModuleInfo info;
		final String name;

		Target(ModuleInfo info, String name) {
			this.info = info;
			this.name = name;
		}
	}

	/** 따라가서 찾은 서버 호출과, 그 호출이 적힌 파일 */
	private static class Reached {
		final ScreenUrls.Hit hit;
		final String script;

		Reached(ScreenUrls.Hit hit, String script) {
			this.hit = hit;
			this.script = script;
		}
	}

	/** 한 번의 실행에서 센 것들 */
	private static class Counts {
		int screens;
		int direct;
		int imported;
		int unresolved;
		int includes;
		int scriptIncludes;
	}

	/** 한 번의 실행이 들고 다니는 것: 파일 목록, 읽어 둔 모듈, 별칭, baseURL, 진입점 */
	private class Run {

		final AnalysisJobContext context;
		final long revisionId;
		final Path root;
		/** 스크립트 파일 경로 → {fileId, path, encoding, fileType} */
		final Map<String, Map<String, Object>> files;
		/** 읽어 둔 모듈. 경로 → 모듈(읽지 못했으면 null 값) */
		final Map<String, ModuleInfo> modules = new HashMap<String, ModuleInfo>();
		/** 폴더 → 그 폴더에 쓰는 별칭(접두어 → 가리키는 폴더). 별칭 설정이 없으면 빈 Map */
		final Map<String, Map<String, String>> aliases = new HashMap<String, Map<String, String>>();
		/** baseURL → 그것을 찾은 규칙. 찾은 순서대로 */
		final Map<String, ScreenUrls.Rule> bases = new LinkedHashMap<String, ScreenUrls.Rule>();
		final List<ScreenUrls.Rule> urlRules;
		SpaEndpoints endpoints;
		final Counts counts = new Counts();

		Run(AnalysisJobContext context, Path root, Map<String, Map<String, Object>> files) {
			this.context = context;
			this.revisionId = context.getRevisionId();
			this.root = root;
			this.files = files;
			this.urlRules = context.getProjectRules().screenUrls();
		}

		long fileId(String path) {
			return ((Number) files.get(path).get("fileId")).longValue();
		}

		/** 파일을 읽습니다. 못 읽으면 null(파일 하나 때문에 전체를 멈추지 않는다) */
		String read(String path) {
			try {
				byte[] bytes = Files.readAllBytes(root.resolve(path));
				return encodingDetector.decode(bytes, (String) files.get(path).get("encoding"));
			} catch (Exception e) {
				return null;
			}
		}

		/** 읽어 둔 모듈. 처음이면 읽어서 둔다. 못 읽으면 null */
		ModuleInfo load(String path) {
			if (modules.containsKey(path)) {
				return modules.get(path);
			}
			String text = read(path);
			if (text == null) {
				modules.put(path, null);
				return null;
			}
			return store(path, text);
		}

		/**
		 * <pre>
		 * 파일을 모듈로 읽어 둡니다. 맨 바깥 정의마다 그 안의 서버 호출과, 그 안에서 쓴 이름(같은 파일의 정의, import 한 이름)을 남깁니다.
		 * 정의 안에서 자기 이름이 쓰인 곳(정의하는 자리, 되부르기)은 뺍니다.
		 * </pre>
		 */
		ModuleInfo store(String path, String text) {
			SpaModules.Module module = spaModules.parse(text);
			ModuleInfo info = new ModuleInfo(path, module, screenUrls.find(text, urlRules));
			Set<String> names = new LinkedHashSet<String>();
			for (String defined : module.getDefinitions().keySet()) {
				// 기본 내보내기 범위와 객체 멤버(codeService.listGroups)는 이름으로 쓰이지 않는다.
				if (!SpaModules.DEFAULT.equals(defined) && defined.indexOf('.') < 0) {
					names.add(defined);
				}
			}
			for (int i = 0; i < module.getImports().size(); i++) {
				if (module.getImports().get(i).getLocal() != null) {
					names.add(module.getImports().get(i).getLocal());
				}
			}
			for (Map.Entry<String, int[]> entry : module.getDefinitions().entrySet()) {
				int[] range = entry.getValue();
				List<ScreenUrls.Hit> inside = new ArrayList<ScreenUrls.Hit>();
				for (int i = 0; i < info.allHits.size(); i++) {
					int at = info.allHits.get(i).getIndex();
					if (range[0] <= at && at < range[1]) {
						inside.add(info.allHits.get(i));
					}
				}
				info.hits.put(entry.getKey(), inside);
				List<SpaModules.Use> used = spaModules.uses(text, module, range[0], range[1], names);
				List<SpaModules.Use> others = new ArrayList<SpaModules.Use>();
				for (int i = 0; i < used.size(); i++) {
					if (!used.get(i).getName().equals(entry.getKey())) {
						others.add(used.get(i));
					}
				}
				info.refs.put(entry.getKey(), others);
			}
			modules.put(path, info);
			return info;
		}

		/**
		 * <pre>
		 * import 하나가 가리키는 정의를 찾습니다.
		 * 다른 화면 파일(.vue, React 컴포넌트)의 기본 내보내기는 따라가지 않습니다. 그 화면 자체이고, INCLUDES 로 이미 잇습니다.
		 * </pre>
		 *
		 * @param exported 그 모듈에서 내보내는 이름(default 포함)
		 */
		Target resolveImport(String importer, SpaModules.Import imp, String exported, int depth) {
			String target = resolveSpec(importer, imp.getSpec());
			if (target == null || exported == null || "*".equals(exported)) {
				return null;
			}
			if (SpaModules.DEFAULT.equals(exported) && SCREEN_TYPES.contains(files.get(target).get("fileType"))) {
				return null;
			}
			return resolveExport(target, exported, depth + 1);
		}

		/**
		 * <pre>
		 * 모듈이 내보내는 이름의 정의를 찾습니다.
		 *   1) 그 파일의 맨 바깥 정의
		 *   2) 그 파일이 import 한 것을 다시 내보낸 것(import { a } from './x'; export { a })
		 *   3) export { a } from './x', export * from './x' (index.ts 로 모아 내보내는 모양)
		 * </pre>
		 */
		Target resolveExport(String path, String exported, int depth) {
			if (depth > MAX_DEPTH) {
				return null;
			}
			ModuleInfo info = load(path);
			if (info == null) {
				return null;
			}
			String local = info.module.localNameOf(exported);
			if (local != null && info.hits.containsKey(local)) {
				return new Target(info, local);
			}
			if (local != null) {
				SpaModules.Import imp = info.module.importOf(local);
				if (imp != null && imp.getImported() != null) {
					return resolveImport(path, imp, imp.getImported(), depth);
				}
			}
			List<SpaModules.ReExport> reExports = info.module.getReExports();
			for (int i = 0; i < reExports.size(); i++) {
				SpaModules.ReExport re = reExports.get(i);
				String target = resolveSpec(path, re.getSpec());
				if (target == null) {
					continue;
				}
				if (re.getExported().equals(exported) && !"*".equals(re.getImported())) {
					return resolveExport(target, re.getImported(), depth + 1);
				}
				if ("*".equals(re.getExported())) {
					Target found = resolveExport(target, exported, depth + 1);
					if (found != null) {
						return found;
					}
				}
			}
			return null;
		}

		/**
		 * <pre>
		 * 정의 하나에서 시작해 서버 호출을 모읍니다. 정의 안에서 쓴 같은 파일의 정의와 import 한 이름을 따라갑니다.
		 * 한 번 본 정의는 다시 보지 않습니다(서로 부르는 함수도 끝난다).
		 * </pre>
		 */
		void collect(Target target, Set<String> visited, List<Reached> out, int depth) {
			if (depth > MAX_DEPTH || !visited.add(target.info.path + "#" + target.name)) {
				return;
			}
			List<ScreenUrls.Hit> hits = target.info.hits.get(target.name);
			for (int i = 0; i < hits.size(); i++) {
				out.add(new Reached(hits.get(i), target.info.path));
			}
			List<SpaModules.Use> refs = target.info.refs.get(target.name);
			for (int i = 0; i < refs.size(); i++) {
				SpaModules.Use use = refs.get(i);
				Target next;
				if (target.info.hits.containsKey(use.getName())) {
					next = new Target(target.info, use.getName());
				} else {
					SpaModules.Import imp = target.info.module.importOf(use.getName());
					if (imp == null || imp.getImported() == null) {
						continue;
					}
					String exported = "*".equals(imp.getImported()) ? use.getMember() : imp.getImported();
					next = resolveImport(target.info.path, imp, exported, depth);
				}
				if (next != null) {
					Target member = "*".equals(importedOf(target.info, use)) ? null : member(next, use.getMember());
					collect(member != null ? member : next, visited, out, depth + 1);
				}
			}
		}

		/** 정의가 객체 리터럴이고 그 멤버(name.member)가 있으면 그 멤버. 아니면 null */
		Target member(Target target, String member) {
			if (target == null || member == null) {
				return null;
			}
			String key = target.name + "." + member;
			return target.info.hits.containsKey(key) ? new Target(target.info, key) : null;
		}

		/** 쓰인 이름이 import 한 것이면 그 import 의 가져오는 이름, 아니면 null */
		private String importedOf(ModuleInfo info, SpaModules.Use use) {
			SpaModules.Import imp = info.module.importOf(use.getName());
			return imp == null ? null : imp.getImported();
		}

		/* ---------- 모듈 이름 → 파일 ---------- */

		/**
		 * <pre>
		 * import 의 모듈 이름을 프로젝트 안의 스크립트 파일 경로로 바꿉니다. 프로젝트 밖(패키지)이거나 없으면 null.
		 *   ./http, ../api/board  → import 한 파일의 폴더 기준
		 *   @/api/board           → 별칭(tsconfig / jsconfig 의 paths, 없으면 가장 가까운 src 폴더)
		 * </pre>
		 */
		String resolveSpec(String importer, String spec) {
			if (spec == null) {
				return null;
			}
			String clean = spec;
			int query = clean.indexOf('?');
			if (query >= 0) {
				clean = clean.substring(0, query);
			}
			if (clean.startsWith("./") || clean.startsWith("../") || ".".equals(clean) || "..".equals(clean)) {
				return existing(normalize(dirOf(importer) + "/" + clean));
			}
			Map<String, String> found = aliasesFor(dirOf(importer));
			for (Map.Entry<String, String> alias : found.entrySet()) {
				String prefix = alias.getKey();
				if (prefix.endsWith("/") ? clean.startsWith(prefix) : clean.equals(prefix)) {
					String resolved = existing(normalize(alias.getValue() + (prefix.endsWith("/") ? "/" + clean.substring(prefix.length()) : "")));
					if (resolved != null) {
						return resolved;
					}
				}
			}
			if (found.isEmpty()) {
				for (int i = 0; i < DEFAULT_ALIASES.length; i++) {
					if (clean.startsWith(DEFAULT_ALIASES[i])) {
						String src = srcOf(dirOf(importer));
						return src == null ? null : existing(normalize(src + "/" + clean.substring(DEFAULT_ALIASES[i].length())));
					}
				}
			}
			return null;
		}

		/** 그 경로이거나, 확장자나 /index.* 를 붙인 경로 가운데 스캔된 파일. 없으면 null */
		String existing(String base) {
			if (base == null) {
				return null;
			}
			if (files.containsKey(base)) {
				return base;
			}
			for (int i = 0; i < EXTENSIONS.length; i++) {
				if (files.containsKey(base + EXTENSIONS[i])) {
					return base + EXTENSIONS[i];
				}
			}
			for (int i = 0; i < EXTENSIONS.length; i++) {
				String index = (base.length() == 0 ? "" : base + "/") + "index" + EXTENSIONS[i];
				if (files.containsKey(index)) {
					return index;
				}
			}
			return null;
		}

		/** 가장 가까운 src 폴더(자기 자신 포함). 없으면 null */
		private String srcOf(String dir) {
			String d = dir;
			while (true) {
				if (d.equals("src") || d.endsWith("/src")) {
					return d;
				}
				if (d.length() == 0) {
					return null;
				}
				d = parentOf(d);
			}
		}

		/**
		 * <pre>
		 * 폴더에 쓰는 별칭. 그 폴더부터 위로 올라가며 처음 만나는 tsconfig.json / tsconfig.app.json / jsconfig.json 의
		 * compilerOptions.paths 를 읽습니다(baseUrl 기준). 예: "@/*": ["src/*"] → "@/" → "frontend/src"
		 * 설정 파일은 스캔 대상이 아니라서 디스크에서 바로 읽습니다.
		 * </pre>
		 */
		private Map<String, String> aliasesFor(String dir) {
			Map<String, String> cached = aliases.get(dir);
			if (cached != null) {
				return cached;
			}
			Map<String, String> found = new LinkedHashMap<String, String>();
			String d = dir;
			while (found.isEmpty()) {
				for (int i = 0; i < ALIAS_CONFIGS.length && found.isEmpty(); i++) {
					readAliases(d, ALIAS_CONFIGS[i], found);
				}
				if (d.length() == 0) {
					break;
				}
				d = parentOf(d);
			}
			aliases.put(dir, found);
			return found;
		}

		/** 설정 파일 하나의 paths 를 읽어 found 에 넣습니다 */
		private void readAliases(String dir, String name, Map<String, String> found) {
			Path file = root.resolve(dir.length() == 0 ? name : dir + "/" + name);
			String text;
			try {
				if (!Files.isRegularFile(file) || !file.toRealPath().startsWith(root)) {
					return;
				}
				text = withoutComments(new String(Files.readAllBytes(file), "UTF-8"));
			} catch (Exception e) {
				return;
			}
			int paths = text.indexOf("\"paths\"");
			if (paths < 0) {
				return;
			}
			int open = text.indexOf('{', paths);
			int close = open < 0 ? -1 : text.indexOf('}', open);
			if (close < 0) {
				return;
			}
			Matcher baseUrl = BASE_URL.matcher(text);
			String base = normalize((dir.length() == 0 ? "" : dir + "/") + (baseUrl.find() ? baseUrl.group(1) : "."));
			if (base == null) {
				return;
			}
			Matcher entry = PATH_ENTRY.matcher(text.substring(open + 1, close));
			while (entry.find()) {
				String key = entry.group(1);
				String value = entry.group(2);
				String target;
				if (key.endsWith("/*") && value.endsWith("/*")) {
					key = key.substring(0, key.length() - 1);
					target = normalize((base.length() == 0 ? "" : base + "/") + value.substring(0, value.length() - 2));
				} else if (key.indexOf('*') < 0 && value.indexOf('*') < 0) {
					target = normalize((base.length() == 0 ? "" : base + "/") + value);
				} else {
					continue;
				}
				if (target != null && !found.containsKey(key)) {
					found.put(key, target);
				}
			}
		}
	}

	/* ============================== 경로 도구 ============================== */

	/** a/b/c.vue → a/b. 폴더가 없으면 "" */
	static String dirOf(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? "" : path.substring(0, slash);
	}

	/** a/b → a, a → "" */
	static String parentOf(String dir) {
		int slash = dir.lastIndexOf('/');
		return slash < 0 ? "" : dir.substring(0, slash);
	}

	/** a/./b/../c → a/c. 프로젝트 폴더 위로 올라가면 null */
	static String normalize(String path) {
		String[] parts = path.split("/");
		List<String> out = new ArrayList<String>();
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if (part.length() == 0 || ".".equals(part)) {
				continue;
			}
			if ("..".equals(part)) {
				if (out.isEmpty()) {
					return null;
				}
				out.remove(out.size() - 1);
				continue;
			}
			out.add(part);
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < out.size(); i++) {
			sb.append(i > 0 ? "/" : "").append(out.get(i));
		}
		return sb.toString();
	}

	/** JSON 의 // 와 /* … *\/ 주석을 뺍니다(tsconfig 는 주석을 허용한다). 따옴표 안은 그대로 둡니다 */
	static String withoutComments(String text) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '"') {
				int end = i + 1;
				while (end < text.length() && text.charAt(end) != '"') {
					end += text.charAt(end) == '\\' ? 2 : 1;
				}
				end = Math.min(end + 1, text.length());
				sb.append(text, i, end);
				i = end;
			} else if (text.startsWith("//", i)) {
				int end = text.indexOf('\n', i);
				i = end < 0 ? text.length() : end;
			} else if (text.startsWith("/*", i)) {
				int end = text.indexOf("*/", i + 2);
				i = end < 0 ? text.length() : end + 2;
			} else {
				sb.append(c);
				i++;
			}
		}
		return sb.toString();
	}

}

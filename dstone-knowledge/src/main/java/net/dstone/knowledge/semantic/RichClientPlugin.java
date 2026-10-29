package net.dstone.knowledge.semantic;

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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.scanner.EncodingDetector;

/**
 * <pre>
 * 리치클라이언트 화면(WebSquare, Nexacro, X-Platform)을 Java 쪽과 잇습니다.
 *
 * 이런 화면은 JSP와 달리 서버가 열어 주지 않고(RENDERS 없음), 화면이 혼자 떠서 데이터만 주고받습니다.
 * 그래서 "화면이 서버의 무엇을 부르나" 하나가 화면과 Java를 잇는 전부입니다.
 *
 * 만드는 관계:
 *   화면 ─REQUESTS→ 메소드   화면이 이 메소드를 부른다. 찾는 길이 둘이다.
 *       주소      화면 안의 /order/list.do, svc::order/list.do 가 진입점 주소와 같을 때 (via = PATH_TEXT, MEDIUM)
 *                 WebSquare의 submission action, $p.ajax 의 주소, Nexacro의 transaction 주소가 여기에 걸린다.
 *                 앞에 컨텍스트 경로가 붙은 주소(/myapp/order/list.do)는 첫 마디를 떼고 한 번 더 맞춰 본다 (LOW)
 *       거래 호출 화면이 주소 대신 거래 ID로 부를 때 (via = CALL_RULE)
 *                 프로젝트 공통 함수가 "ID → 메소드 이름" 규칙으로 서버를 부르는 방식이다.
 *                 규칙은 설정 dstone.knowledge.semantic.screen.call-rules 에 적는다(ScreenScript.parseRules).
 *                 폼 필드에 거래 ID를 담아 보내는 화면(&lt;input name='tranId' value='JIMC1000M03S'&gt; 를 만들어 submit)은
 *                 설정 dstone.knowledge.semantic.screen.field-rules 에 "필드 이름=메소드이름틀" 로 적는다(properties.field).
 *                 그 이름의 메소드가 하나면 HIGH, 여럿이면 어느 것인지 몰라 모두 LOW로 잇는다.
 *                 화면이 부르는 공통 함수(아래 "공통 함수") 안에 글자로 적힌 거래 호출도 잇는다(via = COMMON_SCRIPT).
 *                 예: comLib.gfnOneResultSearchAgnt(p) → com_lib.js 의 doRequestAjax("JMAT0116M01L") → performJMAT0116M01L.
 *                 함수 안에서 조건에 따라 부를 수도 있으므로 메소드가 하나여도 MEDIUM 이다.
 *                 메소드가 없으면 사실 그대로 UNRESOLVED로 남긴다(이름은 to_external).
 *   진입점 TRANSACTION       거래 ID로 불리는 메소드(이름이 규칙의 틀에 맞는 공개 메소드). 주소 자리에 거래 ID를 둔다
 *   화면 ─INCLUDES→ 화면     화면이 다른 화면을 끼워 넣거나 띄운다. 찾는 길이 셋이다.
 *       경로      src="/app/ui/a/B.xml" 처럼 따옴표 안에 적힌 화면 파일
 *       화면 열기 화면이 경로 대신 화면 ID로 다른 화면을 열 때 (via = OPEN_RULE)
 *                 규칙은 설정 dstone.knowledge.semantic.screen.open-rules 에 "함수=화면 경로 틀" 로 적는다.
 *                 예: fnOpenPage={id}.xml → parent.scwin.fnOpenPage("JIMC1000G") 는 JIMC1000G.xml 로 끝나는 화면을 연다.
 *                 그 경로로 끝나는 화면이 하나일 때만 잇는다
 *       공통 함수 화면이 공통 스크립트(.js, .xjs)의 함수를 부르고, 그 함수가 다른 화면을 열 때 (via = COMMON_SCRIPT, MEDIUM)
 *                 예: comLib.gfnOpenPopupEmp(p) → com_lib.js 의 그 함수 본문에 적힌 /ui/jz/JZCO0102P.xml
 *                 공통 함수가 다른 공통 함수를 부르면 따라간다(gfnOneResultSearchEmp → gfnOpenPopupEmp).
 *                 함수 본문에서 화면을 찾는 방법은 위의 경로, 화면 열기와 같다. 규칙 설정은 따로 없다.
 *                 함수 안에서 조건에 따라 열 수도 있으므로 MEDIUM 이다.
 *
 * 아직 다루지 않는 것:
 * - 거래 ID나 주소를 변수로 넘기는 호출(글자로 적혀 있지 않으면 계산하지 않는다. 짐작해서 잇지 않는다).
 * - 공통 스크립트 안의 주소(/order/list.do). 공통 스크립트에서는 화면과 거래 ID만 본다.
 * - Nexacro의 서비스 접두어(svc::)가 가리키는 실제 주소(typedefinition). 접두어는 떼고 뒤의 경로만 본다.
 * </pre>
 */
@Component
public class RichClientPlugin implements SemanticPlugin {

	/** 리치클라이언트 화면의 파일 종류(analysis_file.file_type) */
	private static final List<String> SCREEN_TYPES = Arrays.asList("WEBSQUARE", "NEXACRO");

	private static final String[] WEBSQUARE_EXTENSIONS = { ".xml" };

	private static final String[] NEXACRO_EXTENSIONS = { ".xfdl" };

	/** 공통 스크립트의 파일 종류. 공통 스크립트는 어느 화면 도구의 것인지 모르므로 두 확장자를 다 본다 */
	private static final List<String> SCRIPT_TYPES = Arrays.asList("SCRIPT");

	private static final String[] SCRIPT_EXTENSIONS = { ".xml", ".xfdl" };

	private static final int PAGE_SIZE = 200;

	/** 같은 이름의 메소드가 이보다 많으면 잇지 않는다(어느 것인지 알 수 없는데 다 이으면 잡음이다). */
	private static final int MAX_CANDIDATES = 5;

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Autowired
	private ConfigProperty configProperty;

	private final ScreenScript screenScript = new ScreenScript();

	private final ScriptFunctions scriptFunctions = new ScriptFunctions();

	@Override
	public String name() {
		return "richClient";
	}

	@Override
	public int order() {
		// 진입점(Spring, 서블릿, Struts)이 다 만들어진 뒤에 돌아야 한다. JSP 다음이다.
		return 510;
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		List<ScreenScript.CallRule> rules = ScreenScript.parseRules(configProperty.getProperty("dstone.knowledge.semantic.screen.call-rules"));
		// 폼 필드에 거래 ID를 담아 보내는 화면도 같은 방식으로 잇는다(출력 화면이 이렇게 많이 부른다).
		rules.addAll(ScreenScript.parseFieldRules(configProperty.getProperty("dstone.knowledge.semantic.screen.field-rules")));
		List<ScreenScript.CallRule> openRules = ScreenScript.parseRules(configProperty.getProperty("dstone.knowledge.semantic.screen.open-rules"));

		Map<String, List<String>> handlers = null;
		Map<String, CommonFunction> commons = null;
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		Counts counts = new Counts();
		long afterId = 0;
		while (true) {
			context.checkCancelled();
			List<Map<String, Object>> page = semanticDao.selectFilePageByTypes(revisionId, SCREEN_TYPES, afterId, PAGE_SIZE);
			if (page.isEmpty()) {
				break;
			}
			if (handlers == null) {
				// 화면이 하나라도 있을 때만 읽는다. 리치클라이언트가 아닌 프로젝트에서는 여기까지 오지 않는다.
				handlers = loadHandlers(revisionId);
				commons = loadCommonFunctions(context, root, rules, openRules, counts);
			}
			for (int i = 0; i < page.size(); i++) {
				Map<String, Object> file = page.get(i);
				long fileId = ((Number) file.get("fileId")).longValue();
				afterId = fileId;
				String text;
				try {
					byte[] bytes = Files.readAllBytes(root.resolve((String) file.get("path")));
					text = encodingDetector.decode(bytes, (String) file.get("encoding"));
				} catch (Exception e) {
					// 파일 하나를 못 읽었다고 전체를 멈추지 않는다.
					continue;
				}
				counts.files++;
				counts.titled += saveScreenName(revisionId, fileId, (String) file.get("path"), text);
				Set<String> linked = new HashSet<String>();
				List<CommonUse> uses = commonUsesOf(text, commons);
				linkPaths(revisionId, fileId, text, handlers, linked, counts);
				linkCalls(revisionId, fileId, text, rules, uses, linked, counts);
				Set<Long> screens = linkScreens(revisionId, fileId, (String) file.get("path"), (String) file.get("fileType"), text, openRules, counts);
				linkCommonScreens(revisionId, fileId, uses, screens, counts);
			}
		}
		if (counts.files == 0) {
			return "리치클라이언트 화면 없음.";
		}
		int endpoints = insertEndpoints(revisionId, counts);
		return "리치클라이언트 화면 " + counts.files + "개. 주소로 이은 요청 " + counts.byPath + ", 거래 호출로 이은 요청 " + counts.byCall
				+ "(메소드 없음 " + counts.missing + ", ID를 글자로 적지 않은 호출 " + counts.unknown + "), 화면 → 화면 " + counts.screens
				+ "(그 가운데 공통 함수를 거친 것 " + counts.byCommon + "), 공통 함수를 거친 요청 " + counts.byCommonCall
				+ "(화면을 열거나 서버를 부르는 공통 함수 " + (commons == null ? 0 : commons.size())
				+ ", 여러 곳에 정의돼 쓰지 않은 이름 " + counts.ambiguous + "), 거래 진입점 " + endpoints
				+ ", 화면명을 찾은 화면 " + counts.titled + ".";
	}

	/** 화면명(<title>, titletext)을 찾아 남깁니다. 남겼으면 1 */
	private int saveScreenName(long revisionId, long fileId, String path, String text) {
		ScreenTitle title = ScreenTitle.of(text);
		if (title == null) {
			return 0;
		}
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("via", title.getVia());
		semanticDao.insertScreenName(revisionId, fileId, path, title.getName(), properties, Integer.valueOf(title.getLine()));
		return 1;
	}

	/**
	 * <pre>
	 * 거래 ID로 불리는 메소드를 진입점으로 올립니다. 영향 분석이 "어느 거래가 영향을 받는지"를 말할 수 있게 됩니다.
	 *
	 * 올리는 조건 두 가지:
	 *   - 이 프로젝트의 화면이 그 규칙의 함수를 실제로 쓴다. 설정에는 여러 프로젝트의 규칙이 함께 적혀 있을 수 있어서,
	 *     쓰지 않는 규칙으로 이름만 비슷한 메소드(performance...)를 입구로 올리면 안 된다.
	 *   - 메소드 이름 틀에 고정된 글자가 3자 이상 있다(perform{id}). {id}뿐인 틀이면 모든 메소드가 맞아 버린다.
	 * 여러 규칙이 같은 메소드에 맞아도 진입점은 하나만 넣는다(먼저 맞은 규칙의 것. SQL 이 막는다).
	 * </pre>
	 */
	private int insertEndpoints(long revisionId, Counts counts) {
		int endpoints = 0;
		for (int i = 0; i < counts.usedRules.size(); i++) {
			ScreenScript.CallRule rule = counts.usedRules.get(i);
			String template = rule.getMethodTemplate();
			int at = template.indexOf("{id}");
			String prefix = template.substring(0, at);
			String suffix = template.substring(at + "{id}".length());
			if (prefix.length() + suffix.length() < 3 || suffix.indexOf("{id}") >= 0) {
				continue;
			}
			endpoints += semanticDao.insertCallRuleEndpoints(revisionId, rule.getFunction(), template, prefix, suffix);
		}
		return endpoints;
	}

	/** 주소 → 그 주소를 처리하는 메소드들. 진입점 수만큼만 메모리에 올라간다. */
	private Map<String, List<String>> loadHandlers(long revisionId) {
		Map<String, List<String>> handlers = new HashMap<String, List<String>>();
		List<Map<String, Object>> endpoints = semanticDao.selectEndpointHandlers(revisionId);
		for (int i = 0; i < endpoints.size(); i++) {
			String path = (String) endpoints.get(i).get("path");
			List<String> list = handlers.get(path);
			if (list == null) {
				list = new ArrayList<String>();
				handlers.put(path, list);
			}
			list.add((String) endpoints.get(i).get("methodId"));
		}
		return handlers;
	}

	/** 화면 안의 주소 가운데 진입점과 같은 것을 찾아 잇습니다. 같은 메소드는 한 번만 잇습니다. */
	private void linkPaths(long revisionId, long fileId, String text, Map<String, List<String>> handlers, Set<String> linked, Counts counts) {
		if (handlers.isEmpty()) {
			return;
		}
		List<ScreenScript.Hit> hits = screenScript.findPaths(text);
		for (int h = 0; h < hits.size(); h++) {
			ScreenScript.Hit hit = hits.get(h);
			String path = hit.getText();
			String confidence = "MEDIUM";
			List<String> methods = handlers.get(path);
			if (methods == null) {
				// 컨텍스트 경로를 글자로 붙여 쓴 주소일 수 있다. 첫 마디를 떼고 한 번 더 본다.
				int second = path.indexOf('/', 1);
				if (second < 0) {
					continue;
				}
				path = path.substring(second);
				methods = handlers.get(path);
				confidence = "LOW";
			}
			if (methods == null) {
				continue;
			}
			for (int i = 0; i < methods.size(); i++) {
				if (!linked.add(methods.get(i))) {
					continue;
				}
				Map<String, Object> properties = new LinkedHashMap<String, Object>();
				properties.put("path", path);
				properties.put("via", "PATH_TEXT");
				semanticDao.insertRelation(relation(revisionId, fileId, "REQUESTS", "METHOD", methods.get(i), null, confidence, "HEURISTIC"
						, properties, screenScript.lineAt(text, hit.getIndex())));
				counts.byPath++;
			}
		}
	}

	/**
	 * <pre>
	 * 거래 ID로 부르는 호출을 찾아, 규칙이 말하는 이름의 메소드와 잇습니다.
	 * 화면이 부르는 공통 함수 안의 거래 호출도 같이 잇습니다(via = COMMON_SCRIPT).
	 * 공통 함수는 조건에 따라 부를 수도 있으므로 메소드가 하나여도 MEDIUM 이고, 줄 번호는 화면에서 그 함수를 부른 줄입니다.
	 * </pre>
	 */
	private void linkCalls(long revisionId, long fileId, String text, List<ScreenScript.CallRule> rules, List<CommonUse> uses
			, Set<String> linked, Counts counts) {
		if (rules.isEmpty()) {
			return;
		}
		List<ScreenScript.Call> calls = screenScript.findCalls(text, rules);
		// 같은 자리의 공통 함수(없으면 null). calls 와 순서가 같다
		List<CommonUse> through = new ArrayList<CommonUse>();
		for (int i = 0; i < calls.size(); i++) {
			through.add(null);
		}
		for (int u = 0; u < uses.size(); u++) {
			CommonUse use = uses.get(u);
			for (ScreenScript.Call call : use.function.requests.values()) {
				calls.add(call);
				through.add(use);
			}
		}
		if (calls.isEmpty()) {
			return;
		}
		// 이 화면이 부르는 메소드 이름을 모아 한 번에 찾는다.
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < calls.size(); i++) {
			String name = methodNameOf(calls.get(i));
			if (name != null && !names.contains(name)) {
				names.add(name);
			}
		}
		Map<String, List<String>> methodsByName = new HashMap<String, List<String>>();
		if (!names.isEmpty()) {
			List<Map<String, Object>> rows = semanticDao.selectMethodsByNamesInProject(revisionId, names);
			for (int i = 0; i < rows.size(); i++) {
				String name = (String) rows.get(i).get("name");
				List<String> list = methodsByName.get(name);
				if (list == null) {
					list = new ArrayList<String>();
					methodsByName.put(name, list);
				}
				list.add((String) rows.get(i).get("methodId"));
			}
		}

		Set<String> missingNames = new HashSet<String>();
		for (int c = 0; c < calls.size(); c++) {
			ScreenScript.Call call = calls.get(c);
			String name = methodNameOf(call);
			if (name == null) {
				// ID를 변수로 넘겼거나, 메소드 이름이 될 수 없는 글자다. 짐작하지 않는다.
				counts.unknown++;
				continue;
			}
			if (!counts.usedRules.contains(call.getRule())) {
				counts.usedRules.add(call.getRule());
			}
			CommonUse use = through.get(c);
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			// path 에는 주소 대신 거래 ID를 둔다. 화면 문서와 조회 화면이 "무엇으로 불렀나"를 이 값으로 보여 준다.
			properties.put("path", call.getId());
			properties.put("via", use == null ? "CALL_RULE" : "COMMON_SCRIPT");
			properties.put(call.getRule().isField() ? "field" : "function", call.getRule().getFunction());
			if (use != null) {
				properties.put("commonFunction", use.name);
				properties.put("script", use.function.scriptPath);
			}
			Integer line = use == null ? screenScript.lineAt(text, call.getIndex()) : use.line;

			List<String> methods = methodsByName.get(name);
			if (methods == null || methods.size() > MAX_CANDIDATES) {
				if (missingNames.add(name)) {
					semanticDao.insertRelation(relation(revisionId, fileId, "REQUESTS", "METHOD", null, name, "UNRESOLVED", "UNRESOLVED", properties, line));
					counts.missing++;
				}
				continue;
			}
			boolean single = methods.size() == 1;
			for (int i = 0; i < methods.size(); i++) {
				if (!linked.add(methods.get(i))) {
					continue;
				}
				String confidence = !single ? "LOW" : use == null ? "HIGH" : "MEDIUM";
				semanticDao.insertRelation(relation(revisionId, fileId, "REQUESTS", "METHOD", methods.get(i), null
						, confidence, single ? "RESOLVED" : "HEURISTIC", properties, line));
				if (use == null) {
					counts.byCall++;
				} else {
					counts.byCommonCall++;
				}
			}
		}
	}

	private String methodNameOf(ScreenScript.Call call) {
		return call.getId() == null ? null : call.getRule().methodNameOf(call.getId());
	}

	/**
	 * 화면 안에 적힌 다른 화면 파일과, 화면 ID로 여는 다른 화면(openRules)을 찾아 잇습니다.
	 * 가리키는 화면이 하나로 정해질 때만 잇고, 자기 자신은 잇지 않습니다.
	 *
	 * @return 이은 화면들(file_id). 공통 함수로 한 번 더 잇지 않으려고 돌려준다
	 */
	private Set<Long> linkScreens(long revisionId, long fileId, String path, String fileType, String text, List<ScreenScript.CallRule> openRules
			, Counts counts) {
		String[] extensions = "NEXACRO".equals(fileType) ? NEXACRO_EXTENSIONS : WEBSQUARE_EXTENSIONS;
		List<ScreenScript.Hit> refs = screenScript.findScreenRefs(text, extensions);
		Set<Long> linked = new HashSet<Long>();
		for (int r = 0; r < refs.size(); r++) {
			ScreenScript.Hit ref = refs.get(r);
			Long target = findScreen(revisionId, ref.getText());
			if (target == null || target.longValue() == fileId || !linked.add(target)) {
				continue;
			}
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("path", ref.getText());
			semanticDao.insertRelation(relation(revisionId, fileId, "INCLUDES", "FILE", "F" + target, null, "HIGH", "RESOLVED"
					, properties, screenScript.lineAt(text, ref.getIndex())));
			counts.screens++;
		}
		if (openRules.isEmpty()) {
			return linked;
		}
		List<ScreenScript.Call> opens = screenScript.findCalls(text, openRules);
		for (int c = 0; c < opens.size(); c++) {
			ScreenScript.Call open = opens.get(c);
			if (open.getId() == null) {
				// 화면 ID를 변수로 넘겼다. 짐작하지 않는다.
				continue;
			}
			Long target = findScreen(revisionId, open.getRule().fill(open.getId()));
			if (target == null || target.longValue() == fileId || !linked.add(target)) {
				continue;
			}
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("path", open.getId());
			properties.put("via", "OPEN_RULE");
			properties.put("function", open.getRule().getFunction());
			semanticDao.insertRelation(relation(revisionId, fileId, "INCLUDES", "FILE", "F" + target, null, "HIGH", "RESOLVED"
					, properties, screenScript.lineAt(text, open.getIndex())));
			counts.screens++;
		}
		return linked;
	}

	/**
	 * <pre>
	 * 화면이 부르는 공통 함수들을 찾습니다. 같은 함수는 처음 부른 곳 하나만 남깁니다.
	 * 화면이 같은 이름의 함수를 스스로 만들면 그 화면에서는 공통 함수가 아니므로 뺍니다.
	 * </pre>
	 */
	private List<CommonUse> commonUsesOf(String text, Map<String, CommonFunction> commons) {
		List<CommonUse> uses = new ArrayList<CommonUse>();
		if (commons == null || commons.isEmpty()) {
			return uses;
		}
		List<ScriptFunctions.Call> calls = scriptFunctions.findCalls(text, commons.keySet(), screenScript.commentMask(text));
		Set<String> seen = new HashSet<String>();
		for (int c = 0; c < calls.size(); c++) {
			ScriptFunctions.Call call = calls.get(c);
			String name = call.getName();
			if (!seen.add(name) || scriptFunctions.definesItself(text, name)) {
				continue;
			}
			uses.add(new CommonUse(name, commons.get(name), screenScript.lineAt(text, call.getIndex())));
		}
		return uses;
	}

	/**
	 * <pre>
	 * 화면이 부르는 공통 함수가 여는 화면을 잇습니다(via = COMMON_SCRIPT).
	 * 이미 다른 길로 이은 화면은 다시 잇지 않습니다.
	 * </pre>
	 */
	private void linkCommonScreens(long revisionId, long fileId, List<CommonUse> uses, Set<Long> linked, Counts counts) {
		for (int u = 0; u < uses.size(); u++) {
			CommonUse use = uses.get(u);
			for (Long target : use.function.screens) {
				if (target.longValue() == fileId || !linked.add(target)) {
					continue;
				}
				Map<String, Object> properties = new LinkedHashMap<String, Object>();
				properties.put("path", use.name);
				properties.put("via", "COMMON_SCRIPT");
				properties.put("function", use.name);
				properties.put("script", use.function.scriptPath);
				semanticDao.insertRelation(relation(revisionId, fileId, "INCLUDES", "FILE", "F" + target, null, "MEDIUM", "RESOLVED"
						, properties, Integer.valueOf(use.line)));
				counts.screens++;
				counts.byCommon++;
			}
		}
	}

	/**
	 * <pre>
	 * 공통 스크립트(file_type = SCRIPT)를 모두 읽어 "공통 함수 → 여는 화면들, 부르는 거래들"을 만듭니다.
	 *
	 * 1) 함수마다 본문에 직접 적힌 화면(경로, 화면 열기 규칙)과 거래 호출(거래 호출 규칙. ID가 글자로 적힌 것만)을 찾는다.
	 *    거래 ID를 변수로 받아 넘기는 감싸기 함수(gfnCallService(tranId) 안의 doRequestAjax(tranId))는 세지 않는다.
	 *    그런 호출은 화면 쪽에서 글자로 적힌 ID로 이미 잡힌다.
	 * 2) 본문이 다른 공통 함수를 부르면 그 함수의 것도 합친다. 더 늘지 않을 때까지 되풀이한다(서로 부르는 함수도 끝난다).
	 * 같은 이름이 여러 곳에 정의돼 있으면 어느 것이 불리는지 알 수 없으므로 그 이름은 쓰지 않는다.
	 * 스크립트는 화면보다 훨씬 적어서(HI-Japan 34개) 한 번에 메모리에 올린다.
	 * </pre>
	 *
	 * @return 화면을 열거나 거래를 부르는 함수만. 이름은 적힌 그대로(comLib.gfnOpenPopupEmp)
	 */
	private Map<String, CommonFunction> loadCommonFunctions(AnalysisJobContext context, Path root, List<ScreenScript.CallRule> rules
			, List<ScreenScript.CallRule> openRules, Counts counts) {
		long revisionId = context.getRevisionId();
		Map<String, CommonFunction> functions = new LinkedHashMap<String, CommonFunction>();
		Set<String> ambiguous = new HashSet<String>();
		long afterId = 0;
		while (true) {
			context.checkCancelled();
			List<Map<String, Object>> page = semanticDao.selectFilePageByTypes(revisionId, SCRIPT_TYPES, afterId, PAGE_SIZE);
			if (page.isEmpty()) {
				break;
			}
			for (int i = 0; i < page.size(); i++) {
				Map<String, Object> file = page.get(i);
				afterId = ((Number) file.get("fileId")).longValue();
				String path = (String) file.get("path");
				String text;
				try {
					byte[] bytes = Files.readAllBytes(root.resolve(path));
					text = encodingDetector.decode(bytes, (String) file.get("encoding"));
				} catch (Exception e) {
					continue;
				}
				List<ScriptFunctions.Function> found = scriptFunctions.findFunctions(text);
				for (int f = 0; f < found.size(); f++) {
					ScriptFunctions.Function definition = found.get(f);
					String name = definition.getName();
					if (ambiguous.contains(name)) {
						continue;
					}
					if (functions.containsKey(name)) {
						functions.remove(name);
						ambiguous.add(name);
						continue;
					}
					functions.put(name, new CommonFunction(path, definition.getBody()));
				}
			}
		}
		counts.ambiguous = ambiguous.size();

		// 1) 본문에 직접 적힌 화면
		for (Map.Entry<String, CommonFunction> entry : functions.entrySet()) {
			CommonFunction function = entry.getValue();
			List<ScreenScript.Hit> refs = screenScript.findScreenRefs(function.body, SCRIPT_EXTENSIONS);
			for (int r = 0; r < refs.size(); r++) {
				Long target = findScreen(revisionId, refs.get(r).getText());
				if (target != null) {
					function.screens.add(target);
				}
			}
			if (!openRules.isEmpty()) {
				List<ScreenScript.Call> opens = screenScript.findCalls(function.body, openRules);
				for (int o = 0; o < opens.size(); o++) {
					ScreenScript.Call open = opens.get(o);
					Long target = open.getId() == null ? null : findScreen(revisionId, open.getRule().fill(open.getId()));
					if (target != null) {
						function.screens.add(target);
					}
				}
			}
			if (!rules.isEmpty()) {
				List<ScreenScript.Call> requests = screenScript.findCalls(function.body, rules);
				for (int q = 0; q < requests.size(); q++) {
					ScreenScript.Call request = requests.get(q);
					if (methodNameOf(request) != null) {
						function.requests.put(request.getRule().getFunction() + "|" + request.getId(), request);
					}
				}
			}
			List<ScriptFunctions.Call> calls = scriptFunctions.findCalls(function.body, functions.keySet(), scriptFunctions.nonCode(function.body));
			for (int c = 0; c < calls.size(); c++) {
				String callee = calls.get(c).getName();
				if (!callee.equals(entry.getKey()) && !function.callees.contains(callee)) {
					function.callees.add(callee);
				}
			}
		}

		// 2) 부르는 공통 함수가 여는 화면과 부르는 거래를 합친다
		boolean grew = true;
		while (grew) {
			grew = false;
			for (CommonFunction function : functions.values()) {
				for (int c = 0; c < function.callees.size(); c++) {
					CommonFunction callee = functions.get(function.callees.get(c));
					if (function.screens.addAll(callee.screens)) {
						grew = true;
					}
					int before = function.requests.size();
					function.requests.putAll(callee.requests);
					if (function.requests.size() > before) {
						grew = true;
					}
				}
			}
		}

		Map<String, CommonFunction> useful = new LinkedHashMap<String, CommonFunction>();
		for (Map.Entry<String, CommonFunction> entry : functions.entrySet()) {
			if (!entry.getValue().screens.isEmpty() || !entry.getValue().requests.isEmpty()) {
				useful.put(entry.getKey(), entry.getValue());
			}
		}
		return useful;
	}

	/**
	 * <pre>
	 * 화면 안에 적힌 경로가 가리키는 화면 파일을 찾습니다.
	 * 적힌 경로는 웹 주소라서 앞에 컨텍스트 경로나 서비스 접두어가 붙어 있을 수 있습니다(/hi-japan/ui/jc/A.xml, Base::A.xfdl).
	 * 그래서 "그 경로로 끝나는 화면"을 찾고, 없으면 앞 마디를 하나 떼고 한 번 더 찾습니다.
	 * 메뉴 목록의 화면 경로를 찾는 ScreenMenuPlugin 도 같이 씁니다(그때는 JSP 도 화면 종류에 넣는다).
	 * </pre>
	 *
	 * @return 화면이 정확히 하나일 때 그 file_id. 없거나 여럿이면 null
	 */
	private Long findScreen(long revisionId, String written) {
		return findScreen(semanticDao, revisionId, written, SCREEN_TYPES);
	}

	static Long findScreen(SemanticDao semanticDao, long revisionId, String written, List<String> screenTypes) {
		String path = written;
		int prefix = path.lastIndexOf("::");
		if (prefix >= 0) {
			path = path.substring(prefix + 2);
		}
		while (path.startsWith("/") || path.startsWith("./")) {
			path = path.substring(path.indexOf('/') + 1);
		}
		if (path.length() == 0 || path.indexOf("..") >= 0) {
			return null;
		}
		for (int attempt = 0; attempt < 2; attempt++) {
			List<Long> found = semanticDao.selectScreenByPathEnd(revisionId, path, screenTypes);
			if (found.size() == 1) {
				return found.get(0);
			}
			int slash = path.indexOf('/');
			if (found.size() > 1 || slash < 0) {
				return null;
			}
			path = path.substring(slash + 1);
		}
		return null;
	}

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

	/** 공통 스크립트의 함수 하나 */
	private static class CommonFunction {
		/** 정의된 스크립트 파일 */
		final String scriptPath;
		/** 함수 본문. 다 읽고 나면 쓰지 않는다 */
		final String body;
		/** 이 함수가 여는 화면들(file_id). 부르는 공통 함수가 여는 것까지 */
		final Set<Long> screens = new LinkedHashSet<Long>();
		/** 이 함수가 거래 ID로 부르는 호출들("함수|ID" → 호출). 부르는 공통 함수의 것까지 */
		final Map<String, ScreenScript.Call> requests = new LinkedHashMap<String, ScreenScript.Call>();
		/** 본문에서 부르는 다른 공통 함수 이름들 */
		final List<String> callees = new ArrayList<String>();

		CommonFunction(String scriptPath, String body) {
			this.scriptPath = scriptPath;
			this.body = body;
		}
	}

	/** 화면이 공통 함수를 부른 곳 하나 */
	private static class CommonUse {
		final String name;
		final CommonFunction function;
		/** 화면에서 부른 줄 */
		final int line;

		CommonUse(String name, CommonFunction function, int line) {
			this.name = name;
			this.function = function;
			this.line = line;
		}
	}

	/** 한 번의 실행에서 센 것들 */
	private static class Counts {
		int files;
		int byPath;
		int byCall;
		int missing;
		int unknown;
		int screens;
		/** 화면 → 화면 가운데 공통 함수를 거쳐 이은 것 */
		int byCommon;
		/** 공통 함수 안의 거래 호출로 이은 요청 */
		int byCommonCall;
		/** 여러 곳에 정의돼 있어 쓰지 않은 공통 함수 이름 수 */
		int ambiguous;
		/** 화면명을 찾은 화면 수 */
		int titled;
		/** 이 프로젝트의 화면이 실제로 쓰는 규칙들 */
		List<ScreenScript.CallRule> usedRules = new ArrayList<ScreenScript.CallRule>();
	}

}

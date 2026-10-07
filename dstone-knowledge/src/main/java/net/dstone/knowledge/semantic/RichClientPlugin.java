package net.dstone.knowledge.semantic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 *                 그 이름의 메소드가 하나면 HIGH, 여럿이면 어느 것인지 몰라 모두 LOW로 잇는다.
 *                 메소드가 없으면 사실 그대로 UNRESOLVED로 남긴다(이름은 to_external).
 *   진입점 TRANSACTION       거래 ID로 불리는 메소드(이름이 규칙의 틀에 맞는 공개 메소드). 주소 자리에 거래 ID를 둔다
 *   화면 ─INCLUDES→ 화면     화면이 다른 화면을 끼워 넣거나 띄운다 (src="/app/ui/a/B.xml" 처럼 따옴표 안에 적힌 화면 파일)
 *
 * 아직 다루지 않는 것:
 * - 거래 ID나 주소를 변수로 넘기는 호출(글자로 적혀 있지 않으면 계산하지 않는다. 짐작해서 잇지 않는다).
 * - 화면 밖의 공통 스크립트(.js, .xjs) 안에서 일어나는 호출.
 * - Nexacro의 서비스 접두어(svc::)가 가리키는 실제 주소(typedefinition). 접두어는 떼고 뒤의 경로만 본다.
 * </pre>
 */
@Component
public class RichClientPlugin implements SemanticPlugin {

	/** 리치클라이언트 화면의 파일 종류(analysis_file.file_type) */
	private static final List<String> SCREEN_TYPES = Arrays.asList("WEBSQUARE", "NEXACRO");

	private static final String[] WEBSQUARE_EXTENSIONS = { ".xml" };

	private static final String[] NEXACRO_EXTENSIONS = { ".xfdl" };

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

		Map<String, List<String>> handlers = null;
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
				Set<String> linked = new HashSet<String>();
				linkPaths(revisionId, fileId, text, handlers, linked, counts);
				linkCalls(revisionId, fileId, text, rules, linked, counts);
				linkScreens(revisionId, fileId, (String) file.get("path"), (String) file.get("fileType"), text, counts);
			}
		}
		if (counts.files == 0) {
			return "리치클라이언트 화면 없음.";
		}
		int endpoints = insertEndpoints(revisionId, counts);
		return "리치클라이언트 화면 " + counts.files + "개. 주소로 이은 요청 " + counts.byPath + ", 거래 호출로 이은 요청 " + counts.byCall
				+ "(메소드 없음 " + counts.missing + ", ID를 글자로 적지 않은 호출 " + counts.unknown + "), 화면 → 화면 " + counts.screens + ", 거래 진입점 " + endpoints + ".";
	}

	/**
	 * <pre>
	 * 거래 ID로 불리는 메소드를 진입점으로 올립니다. 영향 분석이 "어느 거래가 영향을 받는지"를 말할 수 있게 됩니다.
	 *
	 * 올리는 조건 두 가지:
	 *   - 이 프로젝트의 화면이 그 규칙의 함수를 실제로 쓴다. 설정에는 여러 프로젝트의 규칙이 함께 적혀 있을 수 있어서,
	 *     쓰지 않는 규칙으로 이름만 비슷한 메소드(performance...)를 입구로 올리면 안 된다.
	 *   - 메소드 이름 틀에 고정된 글자가 3자 이상 있다(perform{id}). {id}뿐인 틀이면 모든 메소드가 맞아 버린다.
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

	/** 거래 ID로 부르는 호출을 찾아, 규칙이 말하는 이름의 메소드와 잇습니다. */
	private void linkCalls(long revisionId, long fileId, String text, List<ScreenScript.CallRule> rules, Set<String> linked, Counts counts) {
		if (rules.isEmpty()) {
			return;
		}
		List<ScreenScript.Call> calls = screenScript.findCalls(text, rules);
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
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			// path 에는 주소 대신 거래 ID를 둔다. 화면 문서와 조회 화면이 "무엇으로 불렀나"를 이 값으로 보여 준다.
			properties.put("path", call.getId());
			properties.put("via", "CALL_RULE");
			properties.put("function", call.getRule().getFunction());
			Integer line = screenScript.lineAt(text, call.getIndex());

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
				semanticDao.insertRelation(relation(revisionId, fileId, "REQUESTS", "METHOD", methods.get(i), null
						, single ? "HIGH" : "LOW", single ? "RESOLVED" : "HEURISTIC", properties, line));
				counts.byCall++;
			}
		}
	}

	private String methodNameOf(ScreenScript.Call call) {
		return call.getId() == null ? null : call.getRule().methodNameOf(call.getId());
	}

	/** 화면 안에 적힌 다른 화면 파일을 찾아 잇습니다. 가리키는 화면이 하나로 정해질 때만 잇습니다. */
	private void linkScreens(long revisionId, long fileId, String path, String fileType, String text, Counts counts) {
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
	}

	/**
	 * <pre>
	 * 화면 안에 적힌 경로가 가리키는 화면 파일을 찾습니다.
	 * 적힌 경로는 웹 주소라서 앞에 컨텍스트 경로나 서비스 접두어가 붙어 있을 수 있습니다(/hi-japan/ui/jc/A.xml, Base::A.xfdl).
	 * 그래서 "그 경로로 끝나는 화면"을 찾고, 없으면 앞 마디를 하나 떼고 한 번 더 찾습니다.
	 * </pre>
	 *
	 * @return 화면이 정확히 하나일 때 그 file_id. 없거나 여럿이면 null
	 */
	private Long findScreen(long revisionId, String written) {
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
			List<Long> found = semanticDao.selectScreenByPathEnd(revisionId, path, SCREEN_TYPES);
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

	/** 한 번의 실행에서 센 것들 */
	private static class Counts {
		int files;
		int byPath;
		int byCall;
		int missing;
		int unknown;
		int screens;
		/** 이 프로젝트의 화면이 실제로 쓰는 규칙들 */
		List<ScreenScript.CallRule> usedRules = new ArrayList<ScreenScript.CallRule>();
	}

}

package net.dstone.knowledge.semantic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.scanner.EncodingDetector;
import net.dstone.knowledge.semantic.rule.RuleSet;

/**
 * <pre>
 * 화면(JSP)을 Java 쪽과 잇습니다.
 *
 * 만드는 관계:
 *   메소드 ─RENDERS→ JSP    컨트롤러의 메소드가 이 화면을 연다 (new ModelAndView("order/list"), setViewName(...))
 *                           어떤 호출의 몇 번째 인자가 화면 이름인지는 규칙 파일의 callArguments(target: VIEW)에 있다(conf/rules/spring.yml)
 *   JSP ─REQUESTS→ 메소드   이 화면이 이 메소드의 주소를 요청한다 (form action, AJAX url, 링크)
 *                           화면이 &lt;script src&gt; 로 불러오는 프로젝트 안의 .js 에 적힌 주소도 잇는다(via = SCRIPT_SRC, ScriptSources)
 *   JSP ─INCLUDES→ JSP      이 화면이 다른 JSP를 끼워 넣는다 (jsp:include, include 지시자)
 * 이것이 있어야 "이 화면에서 저장을 누르면 어디로 가나", "이 메소드는 어느 화면에서 불리나"를 따라갈 수 있습니다.
 *
 * JSP 안의 Java 코드(스크립틀릿)가 부르는 것은 여기서 하지 않습니다. JSP를 Java로 바꿔서(JspToJava)
 * 일반 Java 파일과 같은 길(DECLARE, RESOLVE)로 이미 풀려 있습니다. 여기서는 그 타입에 화면 계층(VIEW)을 붙이고
 * JSP 진입점에 실행 메소드를 달아 주기만 합니다.
 *
 * "요청하는 주소"를 찾는 방법:
 * JSP에서 주소가 적히는 모양은 제각각입니다(action="...", url: '...', location.href = ..., ${ctx}/..., c:url ...).
 * 모양을 하나하나 따지지 않고, JSP 안에서 /로 시작하는 경로처럼 생긴 글자를 전부 찾아
 * 그것이 이 프로젝트의 진입점 주소와 정확히 같을 때만 잇습니다. 진입점과 같지 않은 글자는 버려지므로 잘못 잇는 일이 적습니다.
 * 글자가 같다는 것만으로 잇는 것이라 신뢰도는 MEDIUM입니다.
 *
 * 아직 다루지 않는 것:
 * - 주소를 JavaScript에서 조립하는 경우('/order/' + type + '.do').
 * - 불러온 스크립트가 또 다른 스크립트를 불러오는 경우(import, 동적 로딩). 화면에 적힌 &lt;script src&gt; 한 단계만 본다.
 * - 컨텍스트 경로를 글자로 붙여 쓴 경우(/myapp/order/list.do).
 * - return "order/list" 처럼 메소드가 뷰 이름을 문자열로 돌려주는 방식, Tiles 정의 이름.
 *
 * Struts의 화면 이동(forward / result)은 StrutsPlugin이 같은 RENDERS 관계로 넣습니다.
 * </pre>
 */
@Component
public class JspPlugin implements SemanticPlugin {

	/** /로 시작하는 경로처럼 생긴 글자. 예: /order/list.do, /api/auth/me */
	private static final Pattern PATH = Pattern.compile("/[A-Za-z0-9_\\-]+(?:/[A-Za-z0-9_\\-]+)*(?:\\.[A-Za-z0-9]+)?");

	/** 끼워 넣는 JSP. &lt;jsp:include page="..."&gt; 와 &lt;%@ include file="..." %&gt; */
	private static final Pattern INCLUDE = Pattern.compile("(?:<jsp:include\\s+[^>]*page|<%@\\s*include\\s+file)\\s*=\\s*\"([^\"]+)\"");

	private static final int PAGE_SIZE = 200;

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private EncodingDetector encodingDetector;

	private final ScriptSources scriptSources = new ScriptSources();

	private final ScreenScript screenScript = new ScreenScript();

	@Override
	public String name() {
		return "jsp";
	}

	@Override
	public int order() {
		// 진입점(Spring, 서블릿, JSP)이 다 만들어진 뒤에 돌아야 한다.
		return 500;
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		// 화면과 이어진 관계는 SEMANTIC 단계가 시작할 때 이미 지워져 있다(SemanticDao.clear).

		int layered = semanticDao.updateJspLayer(revisionId);
		semanticDao.updateJspEndpointMethods(revisionId);
		int renders = 0;
		List<RuleSet.CallArgumentRule> viewRules = context.getSemanticRules().getViewArguments();
		for (int i = 0; i < viewRules.size(); i++) {
			renders += semanticDao.insertViewArgumentRelations(revisionId, viewRules.get(i));
		}

		// 주소 → 그 주소를 처리하는 메소드들. 진입점 수만큼만 메모리에 올라간다.
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

		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		// 스크립트 경로 → 그 스크립트에 적힌 진입점 주소. 여러 화면이 같은 공통 스크립트를 불러오므로 한 번만 읽는다.
		Map<String, ScriptPaths> scripts = new HashMap<String, ScriptPaths>();
		int files = 0, requests = 0, includes = 0, titled = 0, byScript = 0;
		long afterId = 0;
		while (true) {
			context.checkCancelled();
			List<Map<String, Object>> page = semanticDao.selectFilePage(revisionId, "JSP", afterId, PAGE_SIZE);
			if (page.isEmpty()) {
				break;
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
				files++;
				titled += saveScreenName(revisionId, fileId, (String) file.get("path"), text);
				Set<String> linked = new HashSet<String>();
				requests += linkRequests(revisionId, fileId, text, handlers, linked);
				byScript += linkScriptRequests(revisionId, fileId, text, handlers, linked, root, scripts);
				includes += linkIncludes(revisionId, fileId, (String) file.get("path"), text);
			}
		}
		return "JSP " + files + "개. 화면 계층 " + layered + ", 메소드가 여는 화면 " + renders + ", 화면이 요청하는 주소 " + requests + "(불러온 스크립트에 적힌 것 " + byScript + "), 끼워 넣기 " + includes + ", 화면명 " + titled + ".";
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

	/** JSP 안의 주소 가운데 진입점과 같은 것을 찾아 잇습니다. 같은 메소드는 한 번만 잇습니다(linked). */
	private int linkRequests(long revisionId, long fileId, String text, Map<String, List<String>> handlers, Set<String> linked) {
		if (handlers.isEmpty()) {
			return 0;
		}
		int count = 0;
		Matcher m = PATH.matcher(text);
		while (m.find()) {
			List<String> methods = handlers.get(m.group());
			if (methods == null) {
				continue;
			}
			for (int i = 0; i < methods.size(); i++) {
				if (!linked.add(methods.get(i))) {
					continue;
				}
				Map<String, Object> properties = new LinkedHashMap<String, Object>();
				properties.put("path", m.group());
				properties.put("via", "PATH_TEXT");
				semanticDao.insertRelation(relation(revisionId, "FILE", "F" + fileId, "REQUESTS", "METHOD", methods.get(i), "MEDIUM", "HEURISTIC"
						, properties, fileId, lineAt(text, m.start())));
				count++;
			}
		}
		return count;
	}

	/** 스크립트 파일 하나와 거기에 적힌 진입점 주소들 */
	private static class ScriptPaths {
		/** 스크립트의 프로젝트 안 경로. 찾지 못했거나 여럿이면 null */
		String scriptPath;
		/** 진입점과 같은 주소. 나온 순서대로, 같은 것은 한 번 */
		List<String> paths = new ArrayList<String>();
	}

	/**
	 * <pre>
	 * 화면이 &lt;script src&gt; 로 불러오는 스크립트에 적힌 주소 가운데 진입점과 같은 것을 잇습니다(via = SCRIPT_SRC).
	 * 스크립트는 "그 경로로 끝나는 SCRIPT 파일"이 하나일 때만 씁니다. 줄 번호는 화면에서 그 스크립트를 불러온 줄입니다.
	 * 불러온 스크립트에 주소가 적혀 있다는 것만 보고 잇는 것이라 MEDIUM 입니다(화면이 그 함수를 실제로 부르는지는 모른다).
	 * 같은 메소드를 화면 글자에서 이미 이었으면(PATH_TEXT) 다시 잇지 않습니다.
	 * </pre>
	 */
	private int linkScriptRequests(long revisionId, long fileId, String text, Map<String, List<String>> handlers, Set<String> linked
			, Path root, Map<String, ScriptPaths> scripts) {
		if (handlers.isEmpty()) {
			return 0;
		}
		int count = 0;
		List<ScriptSources.Source> sources = scriptSources.find(text);
		for (int s = 0; s < sources.size(); s++) {
			ScriptSources.Source source = sources.get(s);
			ScriptPaths script = scriptPathsOf(revisionId, source.getPath(), handlers, root, scripts);
			if (script.scriptPath == null) {
				continue;
			}
			for (int p = 0; p < script.paths.size(); p++) {
				String path = script.paths.get(p);
				List<String> methods = handlers.get(path);
				for (int i = 0; i < methods.size(); i++) {
					if (!linked.add(methods.get(i))) {
						continue;
					}
					Map<String, Object> properties = new LinkedHashMap<String, Object>();
					properties.put("path", path);
					properties.put("via", "SCRIPT_SRC");
					properties.put("script", script.scriptPath);
					semanticDao.insertRelation(relation(revisionId, "FILE", "F" + fileId, "REQUESTS", "METHOD", methods.get(i), "MEDIUM", "HEURISTIC"
							, properties, fileId, lineAt(text, source.getIndex())));
					count++;
				}
			}
		}
		return count;
	}

	/** 스크립트를 찾아 읽고, 적힌 주소 가운데 진입점과 같은 것을 모읍니다. 한 번 본 스크립트는 다시 읽지 않습니다. */
	private ScriptPaths scriptPathsOf(long revisionId, String path, Map<String, List<String>> handlers, Path root, Map<String, ScriptPaths> scripts) {
		ScriptPaths cached = scripts.get(path);
		if (cached != null) {
			return cached;
		}
		ScriptPaths script = new ScriptPaths();
		scripts.put(path, script);
		List<Map<String, Object>> found = semanticDao.selectScriptByPathEnd(revisionId, path);
		if (found.size() != 1) {
			return script;
		}
		String scriptPath = (String) found.get(0).get("path");
		String text;
		try {
			byte[] bytes = Files.readAllBytes(root.resolve(scriptPath));
			text = encodingDetector.decode(bytes, (String) found.get(0).get("encoding"));
		} catch (Exception e) {
			return script;
		}
		script.scriptPath = scriptPath;
		// 주석 안의 주소는 뺀다(지워 둔 옛 호출).
		List<ScreenScript.Hit> hits = screenScript.findPaths(text);
		for (int i = 0; i < hits.size(); i++) {
			String hit = hits.get(i).getText();
			if (handlers.containsKey(hit) && !script.paths.contains(hit)) {
				script.paths.add(hit);
			}
		}
		return script;
	}

	/** 끼워 넣는 JSP를 찾아 잇습니다. 경로가 가리키는 JSP가 하나로 정해질 때만 잇습니다. */
	private int linkIncludes(long revisionId, long fileId, String path, String text) {
		int count = 0;
		Set<Long> linked = new HashSet<Long>();
		Matcher m = INCLUDE.matcher(text);
		while (m.find()) {
			String target = m.group(1).trim();
			if (target.indexOf("${") >= 0 || target.indexOf("<%") >= 0) {
				// 실행할 때 정해지는 경로
				continue;
			}
			String resolved;
			if (target.startsWith("/")) {
				// 웹 루트 기준의 경로: 앞의 /를 떼고 "그 경로로 끝나는 JSP"를 찾는다.
				resolved = target.substring(1);
			} else {
				// 지금 파일이 있는 폴더 기준의 경로
				String directory = path.lastIndexOf('/') < 0 ? "" : path.substring(0, path.lastIndexOf('/') + 1);
				resolved = normalize(directory + target);
			}
			List<Long> found = semanticDao.selectJspByPathEnd(revisionId, resolved);
			if (found.size() != 1 || found.get(0).longValue() == fileId || !linked.add(found.get(0))) {
				continue;
			}
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("path", target);
			semanticDao.insertRelation(relation(revisionId, "FILE", "F" + fileId, "INCLUDES", "FILE", "F" + found.get(0), "HIGH", "RESOLVED"
					, properties, fileId, lineAt(text, m.start())));
			count++;
		}
		return count;
	}

	/** a/b/../c.jsp → a/c.jsp */
	private String normalize(String path) {
		List<String> parts = new ArrayList<String>();
		String[] segments = path.split("/");
		for (int i = 0; i < segments.length; i++) {
			if ("..".equals(segments[i])) {
				if (!parts.isEmpty()) {
					parts.remove(parts.size() - 1);
				}
			} else if (!".".equals(segments[i]) && segments[i].length() > 0) {
				parts.add(segments[i]);
			}
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			sb.append(i > 0 ? "/" : "").append(parts.get(i));
		}
		return sb.toString();
	}

	private Integer lineAt(String text, int index) {
		int line = 1;
		for (int i = 0; i < index; i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return Integer.valueOf(line);
	}

	private Map<String, Object> relation(long revisionId, String fromKind, String fromId, String relationType, String toKind, String toId
			, String confidence, String resolutionStatus, Map<String, Object> properties, long fileId, Integer lineStart) {
		Map<String, Object> relation = new HashMap<String, Object>();
		relation.put("revisionId", revisionId);
		relation.put("fromKind", fromKind);
		relation.put("fromId", fromId);
		relation.put("relationType", relationType);
		relation.put("toKind", toKind);
		relation.put("toId", toId);
		relation.put("toExternal", null);
		relation.put("confidence", confidence);
		relation.put("resolutionStatus", resolutionStatus);
		relation.put("propertiesJson", JsonText.of(properties));
		relation.put("fileId", Long.valueOf(fileId));
		relation.put("lineStart", lineStart);
		return relation;
	}

}

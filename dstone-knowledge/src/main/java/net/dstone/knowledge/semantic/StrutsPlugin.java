package net.dstone.knowledge.semantic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.job.AnalysisJobReporter;
import net.dstone.knowledge.scanner.EncodingDetector;

/**
 * <pre>
 * Struts(1과 2)로 만든 프로그램의 진입점을 찾습니다. Struts 설정 파일이 없는 프로젝트에서는 아무것도 하지 않습니다.
 *
 * Struts는 "어느 주소를 어느 클래스가 처리하나"를 애노테이션이 아니라 설정 파일(struts-config.xml / struts.xml)에 적습니다.
 * 그래서 Java만 봐서는 진입점을 알 수 없습니다. 설정 파일을 읽어서(StrutsConfigReader) 이렇게 만듭니다.
 *   주소 → 처리하는 메소드     HTTP 진입점 (properties_json.framework = STRUTS1 / STRUTS2)
 *   처리하는 클래스            계층 CONTROLLER
 *   메소드 ─RENDERS→ JSP      끝나고 가는 화면(forward / result)
 *
 * 주소를 정하는 방법:
 *   Struts 1: 설정의 path에, web.xml에서 Struts 서블릿(ActionServlet)에 걸어 둔 주소 모양을 입힌다.
 *             *.do 면 /order/list → /order/list.do, /do/* 면 /do/order/list. 서블릿 매핑을 못 찾으면 *.do로 본다.
 *   Struts 2: 네임스페이스 + 이름 + 확장자. 확장자는 설정의 struts.action.extension, 없으면 action.
 *
 * 처리하는 메소드를 정하는 방법:
 *   Struts 1: 그 클래스의 execute(옛 버전은 perform). 없으면(DispatchAction처럼 요청 파라미터로 메소드를 고르는 방식) 타입만 남긴다.
 *   Struts 2: 설정의 method, 없으면 execute.
 *
 * 아직 다루지 않는 것:
 * - 요청 파라미터로 메소드를 고르는 방식(DispatchAction)에서 어느 메소드가 불리는지.
 * - Struts 2의 이름 패턴(name="*_*" 과 {1}), 애노테이션으로 적은 액션(convention 플러그인).
 * - Tiles 정의 이름으로 넘어가는 화면.
 * </pre>
 */
@Component
public class StrutsPlugin implements SemanticPlugin {

	private static final String PASS = "SEMANTIC";

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private SymbolDao symbolDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Autowired
	private AnalysisJobReporter reporter;

	private final StrutsConfigReader reader = new StrutsConfigReader();

	@Override
	public String name() {
		return "struts";
	}

	@Override
	public int order() {
		// web.xml의 서블릿 매핑을 읽어 둔 ServletPlugin(200) 뒤, 화면이 요청하는 주소를 진입점과 맞춰 보는 JspPlugin(500) 앞.
		return 250;
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		List<Map<String, Object>> files = semanticDao.selectFilesByType(revisionId, "STRUTS_CONFIG");
		if (files.isEmpty()) {
			return "Struts 설정 파일이 없습니다.";
		}
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		List<String> urlPatterns = strutsUrlPatterns(revisionId);

		int endpoints = 0, layered = 0, renders = 0;
		for (int i = 0; i < files.size(); i++) {
			context.checkCancelled();
			Map<String, Object> file = files.get(i);
			long fileId = ((Number) file.get("fileId")).longValue();
			StrutsConfigReader.StrutsConfig config;
			try {
				byte[] bytes = Files.readAllBytes(root.resolve((String) file.get("path")));
				config = reader.read(encodingDetector.decode(bytes, (String) file.get("encoding")));
			} catch (Exception e) {
				// 설정 파일 하나를 못 읽었다고 의미 분석 전체를 멈추지 않는다. 기록만 남긴다.
				reporter.error(context, PASS, Long.valueOf(fileId), "PARSE_ERROR", "Struts 설정 파일을 읽지 못했습니다: " + file.get("path"), ErrorText.summaryOf(e));
				continue;
			}
			for (int a = 0; a < config.actions.size(); a++) {
				int[] added = addAction(revisionId, fileId, config, config.actions.get(a), urlPatterns);
				endpoints += added[0];
				layered += added[1];
				renders += added[2];
			}
		}
		return "설정 파일 " + files.size() + "개. 진입점 " + endpoints + ", 계층 " + layered + ", 끝나고 가는 화면 " + renders + ".";
	}

	/**
	 * @return [만든 진입점 수, 계층을 정한 타입 수, 화면 관계 수]
	 */
	private int[] addAction(long revisionId, long fileId, StrutsConfigReader.StrutsConfig config, StrutsConfigReader.Action action, List<String> urlPatterns) {
		List<String> urls = new ArrayList<String>();
		if (config.struts2) {
			urls.add(action.path + struts2Extension(config));
		} else {
			for (int i = 0; i < urlPatterns.size(); i++) {
				urls.add(applyPattern(urlPatterns.get(i), action.path));
			}
		}

		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("framework", config.struts2 ? "STRUTS2" : "STRUTS1");
		properties.put("actionClass", action.className);
		if (action.parameter != null) {
			properties.put("dispatchParameter", action.parameter);
		}
		if (!action.forwards.isEmpty()) {
			properties.put("forwards", action.forwards);
		}

		// 처리하는 타입과 메소드
		String symbolId = null;
		List<Map<String, Object>> handlers = new ArrayList<Map<String, Object>>();
		int layered = 0;
		Map<String, Object> location = action.className == null ? null : symbolDao.selectTypeLocation(revisionId, action.className);
		if (location != null) {
			symbolId = (String) location.get("symbolId");
			layered = semanticDao.updateLayerBySymbol(revisionId, symbolId, "CONTROLLER");
			List<String> names = config.struts2
					? Arrays.asList(action.method == null ? "execute" : action.method)
					: Arrays.asList("execute", "perform");
			// 이름 패턴({1})으로 적은 메소드는 실행할 때 정해진다.
			if (action.method == null || action.method.indexOf('{') < 0) {
				handlers = semanticDao.selectMethodsByName(revisionId, symbolId, names);
			}
		} else if (action.className != null) {
			// 프로젝트 밖의 클래스(Struts가 주는 ForwardAction 등)거나 소스에 없다.
			properties.put("external", Boolean.TRUE);
		}

		int endpoints = 0;
		for (int u = 0; u < urls.size(); u++) {
			if (handlers.isEmpty()) {
				addEndpoint(revisionId, fileId, urls.get(u), symbolId, null, properties, null);
				endpoints++;
			}
			for (int h = 0; h < handlers.size(); h++) {
				Object line = handlers.get(h).get("lineStart");
				addEndpoint(revisionId, fileId, urls.get(u), symbolId, (String) handlers.get(h).get("methodId"), properties
						, line == null ? null : Integer.valueOf(((Number) line).intValue()));
				endpoints++;
			}
		}

		// 끝나고 가는 화면. 경로가 가리키는 JSP가 하나로 정해질 때만 잇는다.
		int renders = 0;
		for (int h = 0; h < handlers.size(); h++) {
			Iterator<Map.Entry<String, String>> forwards = action.forwards.entrySet().iterator();
			while (forwards.hasNext()) {
				Map.Entry<String, String> forward = forwards.next();
				String jspPath = jspPathOf(forward.getValue());
				if (jspPath == null) {
					continue;
				}
				List<Long> found = semanticDao.selectJspByPathEnd(revisionId, jspPath);
				if (found.size() != 1) {
					continue;
				}
				Map<String, Object> relationProperties = new LinkedHashMap<String, Object>();
				relationProperties.put("view", forward.getValue());
				relationProperties.put("forward", forward.getKey());
				relationProperties.put("via", "STRUTS_CONFIG");
				semanticDao.insertRelation(relation(revisionId, (String) handlers.get(h).get("methodId"), "F" + found.get(0), relationProperties, fileId));
				renders++;
			}
		}
		return new int[] { endpoints, layered, renders };
	}

	/**
	 * <pre>
	 * web.xml에서 Struts 1의 서블릿에 걸어 둔 주소 모양들을 찾습니다. 못 찾으면 가장 흔한 *.do로 봅니다.
	 * </pre>
	 */
	private List<String> strutsUrlPatterns(long revisionId) {
		List<String> patterns = new ArrayList<String>();
		List<Map<String, Object>> mappings = semanticDao.selectServletMappings(revisionId);
		for (int i = 0; i < mappings.size(); i++) {
			String servletClass = (String) mappings.get(i).get("servletClass");
			String urlPattern = (String) mappings.get(i).get("urlPattern");
			if (servletClass == null || urlPattern == null) {
				continue;
			}
			// Struts가 주는 ActionServlet이거나, 그것을 물려받아 이름을 바꾼 것(...ActionServlet)
			if ((servletClass.indexOf(".struts.") >= 0 || servletClass.endsWith("ActionServlet")) && !patterns.contains(urlPattern)) {
				patterns.add(urlPattern);
			}
		}
		if (patterns.isEmpty()) {
			patterns.add("*.do");
		}
		return patterns;
	}

	/** 주소 모양에 설정의 path를 입힙니다. *.do + /order/list → /order/list.do,  /do/* + /order/list → /do/order/list */
	static String applyPattern(String urlPattern, String path) {
		if (urlPattern.startsWith("*.")) {
			return path + urlPattern.substring(1);
		}
		if (urlPattern.endsWith("/*")) {
			return urlPattern.substring(0, urlPattern.length() - 2) + path;
		}
		return path;
	}

	/** Struts 2의 주소 뒤에 붙는 확장자. 여러 개를 적었으면 첫 번째 것, 빈 값이면 붙이지 않는다. */
	private String struts2Extension(StrutsConfigReader.StrutsConfig config) {
		String configured = config.constants.get("struts.action.extension");
		if (configured == null) {
			return ".action";
		}
		String first = configured.split(",", -1)[0].trim();
		return first.length() == 0 ? "" : "." + first;
	}

	/** 화면 경로에서 JSP 파일을 찾을 때 쓸 경로를 만듭니다. JSP가 아니면 null. 예: /order/list.jsp?mode=1 → order/list.jsp */
	static String jspPathOf(String location) {
		String path = location;
		int question = path.indexOf('?');
		if (question >= 0) {
			path = path.substring(0, question);
		}
		if (!path.toLowerCase(Locale.ROOT).endsWith(".jsp") || path.indexOf("${") >= 0 || path.indexOf('{') >= 0) {
			return null;
		}
		while (path.startsWith("/")) {
			path = path.substring(1);
		}
		return path.length() == 0 ? null : path;
	}

	private void addEndpoint(long revisionId, long fileId, String path, String symbolId, String methodId, Map<String, Object> properties, Integer lineStart) {
		Map<String, Object> endpoint = new HashMap<String, Object>();
		endpoint.put("revisionId", revisionId);
		endpoint.put("endpointType", "HTTP");
		endpoint.put("httpMethod", "ALL");
		endpoint.put("path", path);
		endpoint.put("symbolId", symbolId);
		endpoint.put("methodId", methodId);
		endpoint.put("propertiesJson", JsonText.of(properties));
		endpoint.put("fileId", fileId);
		endpoint.put("lineStart", lineStart);
		semanticDao.insertEndpoint(endpoint);
	}

	private Map<String, Object> relation(long revisionId, String methodId, String fileNodeId, Map<String, Object> properties, long fileId) {
		Map<String, Object> relation = new HashMap<String, Object>();
		relation.put("revisionId", revisionId);
		relation.put("fromKind", "METHOD");
		relation.put("fromId", methodId);
		relation.put("relationType", "RENDERS");
		relation.put("toKind", "FILE");
		relation.put("toId", fileNodeId);
		relation.put("toExternal", null);
		relation.put("confidence", "HIGH");
		relation.put("resolutionStatus", "RESOLVED");
		relation.put("propertiesJson", JsonText.of(properties));
		relation.put("fileId", Long.valueOf(fileId));
		relation.put("lineStart", null);
		return relation;
	}

}

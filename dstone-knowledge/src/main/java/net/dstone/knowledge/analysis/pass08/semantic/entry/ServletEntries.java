package net.dstone.knowledge.analysis.pass08.semantic.entry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.job.AnalysisJobReporter;
import net.dstone.knowledge.analysis.common.EncodingDetector;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;

/**
 * <pre>
 * 서블릿 기반 웹 애플리케이션의 진입점을 알아냅니다. Spring을 쓰지 않는 구버전 프로젝트에서 주로 쓰입니다.
 *
 *   web.xml의 servlet / servlet-mapping → SERVLET 진입점. 주소마다, 처리 메소드(doGet, doPost ...)마다 한 건.
 *   web.xml의 filter / listener         → analysis_resource에 기록(FILTER_MAPPING, LISTENER)
 *   &#64;WebServlet                         → SERVLET 진입점(서블릿 3.0 이후의 방식)
 *   HttpServlet을 상속한 클래스의 계층(CONTROLLER)은 규칙 파일 conf/rules/common/servlet.yml 에 있고 LayerResolver 가 정한다.
 *
 * 서블릿 클래스가 프로젝트 밖에 있어도(Spring의 DispatcherServlet 등) 진입점으로 남깁니다.
 * "*.do로 끝나는 요청은 Spring이 받는다" 같은 사실이 거기서 나오기 때문입니다. 이때 처리 메소드는 비어 있습니다.
 *
 * 아직 다루지 않는 것:
 * - 처리 메소드를 상위 클래스에서 물려받은 서블릿(자기 클래스에 doGet이 없는 경우): 메소드 없이 타입만 남긴다.
 * - web-fragment.xml, jar 안의 web.xml.
 * </pre>
 */
@Component
public class ServletEntries extends BaseObject implements SemanticStep {

	private static final String PASS = "SEMANTIC";

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private SymbolDao symbolDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Autowired
	private AnalysisJobReporter reporter;

	private final WebXmlReader webXmlReader = new WebXmlReader();

	@Override
	public String name() {
		return "servlet";
	}


	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();

		int endpoints = 0;
		int resources = 0;
		List<Map<String, Object>> files = semanticDao.selectFilesByType(revisionId, "WEB_XML");
		for (int i = 0; i < files.size(); i++) {
			Map<String, Object> file = files.get(i);
			long fileId = ((Number) file.get("fileId")).longValue();
			WebXmlReader.WebXml webXml;
			try {
				byte[] bytes = Files.readAllBytes(root.resolve((String) file.get("path")));
				webXml = webXmlReader.read(encodingDetector.decode(bytes, (String) file.get("encoding")));
			} catch (Exception e) {
				// web.xml 하나를 못 읽었다고 의미 분석 전체를 멈추지 않는다. 기록만 남긴다.
				reporter.error(context, PASS, Long.valueOf(fileId), "PARSE_ERROR", "web.xml을 읽지 못했습니다: " + file.get("path"), ErrorText.summaryOf(e));
				continue;
			}
			endpoints += addServletEndpoints(revisionId, fileId, webXml);
			resources += addResources(revisionId, fileId, webXml);
		}

		endpoints += semanticDao.insertWebServletEndpoints(revisionId);

		return "web.xml " + files.size() + "개, 서블릿 진입점 " + endpoints + ", 필터/리스너 " + resources + ".";
	}

	/**
	 * <pre>
	 * servlet-mapping 마다 SERVLET 진입점을 만듭니다.
	 *
	 *   - 서블릿 클래스가 프로젝트 밖이면(DispatcherServlet 등) 주소만 있는 진입점(external)
	 *   - 클래스에 doGet/doPost 등이 직접 있으면 그 메소드마다 진입점
	 *   - 없으면(상위 클래스에서 물려받음) 클래스만 가리키는 진입점
	 * </pre>
	 *
	 * @return 만든 진입점 수
	 */
	private int addServletEndpoints(long revisionId, long fileId, WebXmlReader.WebXml webXml) {
		int count = 0;
		for (int i = 0; i < webXml.servletMappings.size(); i++) {
			String servletName = webXml.servletMappings.get(i)[0];
			String urlPattern = webXml.servletMappings.get(i)[1];
			String className = webXml.servletClasses.get(servletName);

			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("servletName", servletName);
			properties.put("servletClass", className);

			addResource(revisionId, fileId, "SERVLET_MAPPING", servletName, urlPattern, className);

			Map<String, Object> location = className == null ? null : symbolDao.selectTypeLocation(revisionId, className);
			if (location == null) {
				// 프로젝트 밖의 서블릿(Spring의 DispatcherServlet 등)이거나, web.xml에 적힌 클래스가 소스에 없다.
				properties.put("external", Boolean.TRUE);
				addEndpoint(revisionId, fileId, "ALL", urlPattern, null, null, properties, null);
				count++;
				continue;
			}
			String symbolId = (String) location.get("symbolId");
			List<Map<String, Object>> handlers = semanticDao.selectServletHandlers(revisionId, symbolId);
			if (handlers.isEmpty()) {
				// 이 클래스에 doGet 등이 직접 적혀 있지 않다(상위 클래스에서 물려받음). 타입만 남긴다.
				addEndpoint(revisionId, fileId, "ALL", urlPattern, symbolId, null, properties, null);
				count++;
				continue;
			}
			for (int h = 0; h < handlers.size(); h++) {
				Map<String, Object> handler = handlers.get(h);
				String methodName = (String) handler.get("name");
				// doGet → GET, doPost → POST. service()는 모든 메소드를 받는다.
				String httpMethod = "service".equals(methodName) ? "ALL" : methodName.substring(2).toUpperCase();
				Object line = handler.get("lineStart");
				addEndpoint(revisionId, fileId, httpMethod, urlPattern, symbolId, (String) handler.get("methodId"), properties
						, line == null ? null : Integer.valueOf(((Number) line).intValue()));
				count++;
			}
		}
		return count;
	}

	/** 필터 매핑과 리스너를 analysis_resource 에 남깁니다(진입점은 아니다). */
	private int addResources(long revisionId, long fileId, WebXmlReader.WebXml webXml) {
		int count = 0;
		for (int i = 0; i < webXml.filterMappings.size(); i++) {
			String filterName = webXml.filterMappings.get(i)[0];
			addResource(revisionId, fileId, "FILTER_MAPPING", filterName, webXml.filterMappings.get(i)[1], webXml.filterClasses.get(filterName));
			count++;
		}
		for (int i = 0; i < webXml.listenerClasses.size(); i++) {
			addResource(revisionId, fileId, "LISTENER", webXml.listenerClasses.get(i), null, webXml.listenerClasses.get(i));
			count++;
		}
		return count;
	}

	/** SERVLET 진입점 한 행을 넣습니다. */
	private void addEndpoint(long revisionId, long fileId, String httpMethod, String path, String symbolId, String methodId
			, Map<String, Object> properties, Integer lineStart) {
		Map<String, Object> endpoint = new HashMap<String, Object>();
		endpoint.put("revisionId", revisionId);
		endpoint.put("endpointType", "SERVLET");
		endpoint.put("httpMethod", httpMethod);
		endpoint.put("path", path);
		endpoint.put("symbolId", symbolId);
		endpoint.put("methodId", methodId);
		endpoint.put("propertiesJson", JsonText.of(properties));
		endpoint.put("fileId", fileId);
		endpoint.put("lineStart", lineStart);
		semanticDao.insertEndpoint(endpoint);
	}

	/**
	 * <pre>
	 * web.xml 에서 읽은 것을 analysis_resource 한 행으로 남깁니다.
	 * </pre>
	 *
	 * @param value 주소 패턴. 리스너처럼 주소가 없으면 null
	 */
	private void addResource(long revisionId, long fileId, String resourceType, String name, String value, String className) {
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("class", className);

		Map<String, Object> resource = new HashMap<String, Object>();
		resource.put("revisionId", revisionId);
		resource.put("resourceType", resourceType);
		resource.put("name", name);
		resource.put("location", null);
		resource.put("value", value);
		resource.put("propertiesJson", JsonText.of(properties));
		resource.put("fileId", fileId);
		resource.put("lineStart", null);
		semanticDao.insertResource(resource);
	}

}

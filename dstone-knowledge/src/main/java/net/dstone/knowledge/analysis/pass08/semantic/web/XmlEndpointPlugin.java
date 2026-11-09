package net.dstone.knowledge.analysis.pass08.semantic.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.job.AnalysisJobReporter;
import net.dstone.knowledge.analysis.common.EncodingDetector;
import net.dstone.knowledge.analysis.pass08.semantic.rule.RuleSet;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticPlugin;

/**
 * <pre>
 * 규칙 파일의 endpoints.xml 로, 프로젝트 자체의 설정 XML 에 적힌 진입점을 만듭니다.
 * "주소(거래 ID) → 처리 클래스 · 메소드" 를 애노테이션이 아니라 설정 파일에 적는 자체 프레임워크를 위한 것입니다.
 *   예: &lt;service id="ORD001" class="com.x.OrderService" method="list"/&gt;
 *       endpoints:
 *         xml:
 *           - { endpointType: TRANSACTION, files: 'service-config\.xml$', element: service,
 *               pathAttribute: id, classAttribute: class, methodAttribute: method }
 *
 * 클래스는 전체 이름이면 그대로, 단순 이름이면 프로젝트에서 그 이름의 타입이 하나일 때만 찾습니다.
 * 메소드는 그 클래스에서 이름으로 찾습니다(오버로드가 여럿이면 모두 진입점이 된다).
 * 클래스를 못 찾으면 진입점은 남기되 메소드가 비어 있고 properties_json.unresolvedClass 에 이름을 남깁니다.
 * 규칙이 없는 프로젝트에서는 아무것도 하지 않습니다. 파일 하나를 못 읽어도 분석을 멈추지 않고 오류만 남깁니다.
 * </pre>
 */
@Component
public class XmlEndpointPlugin implements SemanticPlugin {

	private static final String PASS = "SEMANTIC";

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private SymbolDao symbolDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Autowired
	private AnalysisJobReporter reporter;

	private final XmlEndpointReader reader = new XmlEndpointReader();

	@Override
	public String name() {
		return "xmlEndpoint";
	}

	@Override
	public int order() {
		// Struts(250) 뒤, 화면이 요청하는 주소를 진입점과 맞춰 보는 JspPlugin(500) · RichClientPlugin(510) 앞.
		return 260;
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		List<RuleSet.XmlEndpointRule> rules = context.getSemanticRules().getXmlEndpoints();
		if (rules.isEmpty()) {
			return "설정 XML 진입점 규칙이 없습니다.";
		}
		long revisionId = context.getRevisionId();
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		List<Map<String, Object>> xmlFiles = semanticDao.selectFilesByLanguage(revisionId, "XML");
		int files = 0, endpoints = 0, noClass = 0;
		for (int r = 0; r < rules.size(); r++) {
			RuleSet.XmlEndpointRule rule = rules.get(r);
			Pattern pattern = Pattern.compile(rule.files);
			for (int i = 0; i < xmlFiles.size(); i++) {
				context.checkCancelled();
				Map<String, Object> file = xmlFiles.get(i);
				String path = (String) file.get("path");
				if (!pattern.matcher(path).find()) {
					continue;
				}
				long fileId = ((Number) file.get("fileId")).longValue();
				List<XmlEndpointReader.Entry> entries;
				try {
					byte[] bytes = Files.readAllBytes(root.resolve(path));
					entries = reader.read(encodingDetector.decode(bytes, (String) file.get("encoding")), rule);
				} catch (Exception e) {
					reporter.error(context, PASS, Long.valueOf(fileId), "PARSE_ERROR", "설정 XML 을 읽지 못했습니다: " + path, ErrorText.summaryOf(e));
					continue;
				}
				files++;
				for (int e = 0; e < entries.size(); e++) {
					int[] added = addEntry(revisionId, fileId, rule, entries.get(e));
					endpoints += added[0];
					noClass += added[1];
				}
			}
		}
		return "규칙 " + rules.size() + "개, 설정 XML " + files + "개. 진입점 " + endpoints + (noClass > 0 ? " (그 가운데 처리 클래스를 못 찾은 것 " + noClass + ")" : "") + ".";
	}

	/** @return [만든 진입점 수, 클래스를 못 찾은 수] */
	private int[] addEntry(long revisionId, long fileId, RuleSet.XmlEndpointRule rule, XmlEndpointReader.Entry entry) {
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("via", "XML_RULE");
		properties.put("element", rule.element);
		String symbolId = null;
		List<Map<String, Object>> handlers = new ArrayList<Map<String, Object>>();
		int noClass = 0;
		if (entry.className != null) {
			symbolId = findType(revisionId, entry.className);
			if (symbolId == null) {
				properties.put("unresolvedClass", entry.className);
				noClass = 1;
			} else if (entry.methodName != null) {
				List<String> names = new ArrayList<String>();
				names.add(entry.methodName);
				handlers = semanticDao.selectMethodsByName(revisionId, symbolId, names);
				if (handlers.isEmpty()) {
					properties.put("unresolvedMethod", entry.methodName);
				}
			}
		}
		if (handlers.isEmpty()) {
			insert(revisionId, fileId, rule, entry, symbolId, null, properties);
			return new int[] { 1, noClass };
		}
		for (int h = 0; h < handlers.size(); h++) {
			insert(revisionId, fileId, rule, entry, symbolId, (String) handlers.get(h).get("methodId"), properties);
		}
		return new int[] { handlers.size(), noClass };
	}

	/** 전체 이름이면 그 타입, 단순 이름이면 그 이름의 타입이 하나일 때만. 못 찾으면 null */
	private String findType(long revisionId, String className) {
		if (className.indexOf('.') >= 0) {
			Map<String, Object> location = symbolDao.selectTypeLocation(revisionId, className);
			return location == null ? null : (String) location.get("symbolId");
		}
		List<Map<String, Object>> types = symbolDao.selectTypesBySimpleName(revisionId, className, 2);
		return types.size() == 1 ? (String) types.get(0).get("symbolId") : null;
	}

	private void insert(long revisionId, long fileId, RuleSet.XmlEndpointRule rule, XmlEndpointReader.Entry entry, String symbolId, String methodId
			, Map<String, Object> properties) {
		Map<String, Object> endpoint = new HashMap<String, Object>();
		endpoint.put("revisionId", revisionId);
		endpoint.put("endpointType", rule.endpointType);
		endpoint.put("httpMethod", rule.httpMethod);
		endpoint.put("path", entry.path);
		endpoint.put("symbolId", symbolId);
		endpoint.put("methodId", methodId);
		endpoint.put("propertiesJson", JsonText.of(properties));
		endpoint.put("fileId", fileId);
		endpoint.put("lineStart", entry.line);
		semanticDao.insertEndpoint(endpoint);
	}

}

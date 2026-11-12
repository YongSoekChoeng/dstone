package net.dstone.knowledge.analysis.pass08.semantic.entry;

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
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.analysis.common.EncodingDetector;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.job.AnalysisJobReporter;
import net.dstone.knowledge.analysis.pass08.semantic.ConstantEvaluator;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;
import net.dstone.knowledge.analysis.pass08.semantic.rule.ProjectRules;
import net.dstone.knowledge.analysis.pass08.semantic.rule.Rule;

/**
 * <pre>
 * 규칙 파일의 entry: 절로 진입점(밖에서 이 프로그램으로 들어오는 입구)을 찾습니다.
 *
 *   entry 의 모양                                  만드는 것
 *   HTTP: { class: "@RequestMapping(...)", GET: "@GetMapping(...)" }
 *                                                  HTTP 진입점. 클래스 주소 + 메소드 주소(HttpEndpointBuilder)
 *                                                  상수로 적은 주소는 계산하고, 상위 클래스의 주소 메소드도 Spring 처럼 물려받는다
 *   SCHEDULED: "@Scheduled(cron = KEY)"            애노테이션이 붙은 메소드. KEY 자리 값이 진입점 주소 자리(path)
 *   THREAD: java.lang.Runnable#run()               이 타입을 바로 구현(상속)한 클래스의 run()
 *   TRANSACTION: { file: …, xml: '&lt;service id="KEY" class="CLASS" method="METHOD"/&gt;' }
 *                                                  설정 XML 에 적힌 주소 · 클래스 · 메소드(XmlEndpointReader)
 *
 * 규칙 없이도 아는 입구 두 가지도 여기서 만듭니다.
 *   public static void main(String[])  → MAIN
 *   주소로 바로 열 수 있는 JSP(WEB-INF 밖) → JSP
 *
 * 만든 진입점마다 그 규칙의 자리(properties_json.ruleAt, 예: common/spring.yml:17)를 남깁니다.
 * 계층(LayerResolver) 다음에 돕니다. 화면 → 주소를 잇는 JspLinker, ScreenLinker 보다는 먼저 돌아야 합니다.
 *
 * 아직 다루지 않는 것:
 * - static import 한 상수, 다른 jar 에 있는 상수로 적은 주소(properties_json.unresolvedPath 에 적힌 글이 남는다).
 * - 람다로 적은 Runnable(new Thread(() -> ...)): 람다는 메소드로 저장하지 않는다.
 * - Runnable 을 프로젝트 안의 다른 타입을 거쳐 구현한 경우(class A implements MyTask, interface MyTask extends Runnable).
 * </pre>
 */
@Component
public class EntryFinder implements SemanticStep {

	private static final String PASS = "SEMANTIC";

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private ResourceDao resourceDao;

	@Autowired
	private SymbolDao symbolDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Autowired
	private AnalysisJobReporter reporter;

	private final XmlEndpointReader xmlReader = new XmlEndpointReader();

	@Override
	public String name() {
		return "entry";
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		ProjectRules rules = context.getProjectRules();

		int[] http = insertHttpEntries(context, rules.httpEntries());
		int annotated = 0;
		List<Rule.AnnotationEntry> annotationRules = rules.annotationEntries();
		for (int i = 0; i < annotationRules.size(); i++) {
			annotated += semanticDao.insertAnnotationEntries(revisionId, annotationRules.get(i));
		}
		int[] xml = insertXmlEntries(context, rules.xmlEntries());
		int mains = semanticDao.insertMainEndpoints(revisionId);
		int jsps = semanticDao.insertJspEndpoints(revisionId);
		int extended = insertExtendsEntries(revisionId, rules.extendsEntries());

		return "HTTP " + http[0] + "(상위 타입에서 물려받은 것 " + http[1] + ", 상수로 적은 주소 " + http[2]
				+ (http[3] > 0 ? ", 상수를 계산하지 못한 주소 " + http[3] : "") + "), 애노테이션(스케줄, 리스너 등) " + annotated
				+ ", 설정 XML " + xml[0] + (xml[1] > 0 ? "(처리 클래스를 못 찾은 것 " + xml[1] + ")" : "")
				+ ", main " + mains + ", JSP " + jsps + ", 상위 타입(스레드 등) " + extended + ".";
	}

	/* ============================== HTTP ============================== */

	/**
	 * <pre>
	 * HTTP 진입점 규칙마다 애노테이션과 상속 관계를 읽어 진입점을 만듭니다.
	 *
	 * 흐름(규칙 하나마다):
	 *   1) 규칙에 적힌 주소 애노테이션이 붙은 곳을 DB에서 읽는다. 없으면 다음 규칙.
	 *   2) 타입 상속 그래프를 한 번 만들어 두고, 애노테이션이 붙은 타입과 그 하위 타입을 모두 모은다
	 *      (상위 클래스의 주소를 물려받는 컨트롤러가 있어서).
	 *   3) 그 타입들의 성질(구체 클래스인지, 컨트롤러인지)과 메소드 목록을 1000개씩 끊어 읽는다.
	 *   4) HttpEndpointBuilder 로 "클래스 주소 + 메소드 주소"를 조합해 진입점을 만들어 넣는다.
	 *      주소를 상수로 적었으면 ConstantEvaluator 로 계산한다.
	 * </pre>
	 *
	 * @return [진입점 수, 물려받은 것, 상수를 계산한 주소, 계산하지 못한 주소]
	 */
	private int[] insertHttpEntries(AnalysisJobContext context, List<Rule.HttpEntry> httpRules) throws Exception {
		int[] counts = new int[4];
		if (httpRules.isEmpty()) {
			return counts;
		}
		final long revisionId = context.getRevisionId();
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		final ConstantEvaluator constants = new ConstantEvaluator(revisionId, root, resourceDao, symbolDao, encodingDetector);
		HttpEndpointBuilder.PathEvaluator evaluator = new HttpEndpointBuilder.PathEvaluator() {
			@Override
			public String evaluate(String expression, String typeId) {
				return constants.evaluate(expression, typeId);
			}
		};
		HttpEndpointBuilder.TypeGraph graph = null;
		Set<String> interfaces = new HashSet<String>();
		for (int r = 0; r < httpRules.size(); r++) {
			context.checkCancelled();
			Rule.HttpEntry rule = httpRules.get(r);
			// 1) 이 규칙의 애노테이션이 붙은 곳
			List<Map<String, Object>> rows = semanticDao.selectHttpMappingAnnotations(revisionId, rule.annotationNames());
			if (rows.isEmpty()) {
				continue;
			}
			// 2) 상속 그래프(처음 한 번만)와 관련 타입 모으기
			if (graph == null) {
				graph = typeGraph(revisionId, interfaces);
			}
			Set<String> involved = graph.withDescendants(HttpEndpointBuilder.mappedOwners(rows));
			List<String> ids = new ArrayList<String>(involved);
			Map<String, HttpEndpointBuilder.TypeInfo> types = new HashMap<String, HttpEndpointBuilder.TypeInfo>();
			Map<String, List<HttpEndpointBuilder.MethodInfo>> methods = new HashMap<String, List<HttpEndpointBuilder.MethodInfo>>();
			// 3) 타입 성질과 메소드 목록(IN 절이 너무 길어지지 않게 1000개씩)
			for (int from = 0; from < ids.size(); from += 1000) {
				List<String> chunk = ids.subList(from, Math.min(ids.size(), from + 1000));
				List<Map<String, Object>> flags = semanticDao.selectTypeFlags(revisionId, chunk);
				for (int i = 0; i < flags.size(); i++) {
					Map<String, Object> f = flags.get(i);
					boolean concrete = "CLASS".equals(f.get("kind")) && !Boolean.TRUE.equals(f.get("isAbstract"));
					types.put((String) f.get("symbolId"), new HttpEndpointBuilder.TypeInfo((String) f.get("symbolId"), (String) f.get("fqn"), concrete
							, isAnnotatedController(f)));
				}
				List<Map<String, Object>> declared = semanticDao.selectMethodsOfTypes(revisionId, chunk);
				for (int i = 0; i < declared.size(); i++) {
					Map<String, Object> m = declared.get(i);
					String owner = (String) m.get("ownerSymbolId");
					List<HttpEndpointBuilder.MethodInfo> list = methods.get(owner);
					if (list == null) {
						list = new ArrayList<HttpEndpointBuilder.MethodInfo>();
						methods.put(owner, list);
					}
					list.add(new HttpEndpointBuilder.MethodInfo((String) m.get("methodId"), (String) m.get("name"), ((Number) m.get("paramCount")).intValue()));
				}
			}
			// 4) 주소 조합 → 진입점 저장
			HttpEndpointBuilder builder = new HttpEndpointBuilder(rule, graph, types, methods, evaluator);
			List<Map<String, Object>> endpoints = builder.build(rows);
			for (int i = 0; i < endpoints.size(); i++) {
				Map<String, Object> endpoint = endpoints.get(i);
				endpoint.put("revisionId", revisionId);
				endpoint.put("endpointType", "HTTP");
				semanticDao.insertEndpoint(endpoint);
			}
			counts[0] += endpoints.size();
			counts[1] += builder.getInherited();
			counts[2] += builder.getEvaluated();
			counts[3] += builder.getUnresolved();
		}
		return counts;
	}

	/**
	 * <pre>
	 * 상위 클래스의 주소 메소드를 물려받을 수 있는 컨트롤러인지. 애노테이션 규칙("@Controller")으로 CONTROLLER 가 된 클래스만 그렇습니다.
	 * Spring 은 애노테이션이 붙은 빈만 요청 처리기로 등록하므로, 이름(name *Controller)이나 상속으로 짐작한 것은 아닙니다.
	 * </pre>
	 */
	private static boolean isAnnotatedController(Map<String, Object> flags) {
		Object layerBy = flags.get("layerBy");
		return "CONTROLLER".equals(flags.get("layer")) && layerBy != null && String.valueOf(layerBy).startsWith("@");
	}

	/** 프로젝트 안 타입끼리의 상속 관계. 인터페이스의 EXTENDS 는 상위 인터페이스로 넣는다 */
	private HttpEndpointBuilder.TypeGraph typeGraph(long revisionId, Set<String> interfaces) {
		HttpEndpointBuilder.TypeGraph graph = new HttpEndpointBuilder.TypeGraph();
		List<Map<String, Object>> edges = semanticDao.selectTypeEdges(revisionId);
		Set<String> children = new HashSet<String>();
		for (int i = 0; i < edges.size(); i++) {
			children.add((String) edges.get(i).get("fromId"));
		}
		List<String> ids = new ArrayList<String>(children);
		for (int from = 0; from < ids.size(); from += 1000) {
			List<Map<String, Object>> flags = semanticDao.selectTypeFlags(revisionId, ids.subList(from, Math.min(ids.size(), from + 1000)));
			for (int i = 0; i < flags.size(); i++) {
				if ("INTERFACE".equals(flags.get(i).get("kind"))) {
					interfaces.add((String) flags.get(i).get("symbolId"));
				}
			}
		}
		for (int i = 0; i < edges.size(); i++) {
			String child = (String) edges.get(i).get("fromId");
			graph.add(child, (String) edges.get(i).get("toId"), (String) edges.get(i).get("relationType"), interfaces.contains(child));
		}
		return graph;
	}

	/* ============================== 상위 타입 ============================== */

	/**
	 * <pre>
	 * 상위 타입 진입점 규칙을 (진입점 종류, 메소드, 인자 수)가 같은 것끼리 묶어 한 번에 넣습니다.
	 * 예: Runnable#run(), Thread#run(), TimerTask#run() 은 한 번에. 한 메소드에 여럿이 맞아도 하나만 남는다.
	 * </pre>
	 */
	private int insertExtendsEntries(long revisionId, List<Rule.ExtendsEntry> rules) {
		Map<String, List<Rule.ExtendsEntry>> groups = new LinkedHashMap<String, List<Rule.ExtendsEntry>>();
		for (int i = 0; i < rules.size(); i++) {
			Rule.ExtendsEntry rule = rules.get(i);
			String key = rule.endpointType + "|" + rule.methodName + "|" + rule.methodRegex + "|" + rule.paramCount;
			List<Rule.ExtendsEntry> group = groups.get(key);
			if (group == null) {
				group = new ArrayList<Rule.ExtendsEntry>();
				groups.put(key, group);
			}
			group.add(rule);
		}
		int count = 0;
		for (Map.Entry<String, List<Rule.ExtendsEntry>> group : groups.entrySet()) {
			count += semanticDao.insertExtendsEntries(revisionId, group.getValue());
		}
		return count;
	}

	/* ============================== 설정 XML ============================== */

	/** @return [만든 진입점 수, 처리 클래스를 못 찾은 수] */
	private int[] insertXmlEntries(AnalysisJobContext context, List<Rule.XmlEntry> rules) throws Exception {
		int[] counts = new int[2];
		if (rules.isEmpty()) {
			return counts;
		}
		long revisionId = context.getRevisionId();
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		List<Map<String, Object>> xmlFiles = semanticDao.selectFilesByLanguage(revisionId, "XML");
		for (int r = 0; r < rules.size(); r++) {
			Rule.XmlEntry rule = rules.get(r);
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
					entries = xmlReader.read(encodingDetector.decode(bytes, (String) file.get("encoding")), rule);
				} catch (Exception e) {
					reporter.error(context, PASS, Long.valueOf(fileId), "PARSE_ERROR", "설정 XML 을 읽지 못했습니다: " + path, ErrorText.summaryOf(e));
					continue;
				}
				for (int e = 0; e < entries.size(); e++) {
					int[] added = addXmlEntry(revisionId, fileId, rule, entries.get(e));
					counts[0] += added[0];
					counts[1] += added[1];
				}
			}
		}
		return counts;
	}

	/** @return [만든 진입점 수, 클래스를 못 찾은 수] */
	private int[] addXmlEntry(long revisionId, long fileId, Rule.XmlEntry rule, XmlEndpointReader.Entry entry) {
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("via", "XML_RULE");
		properties.put("element", rule.element);
		properties.put("ruleAt", rule.at());
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
			insertXmlEndpoint(revisionId, fileId, rule, entry, symbolId, null, properties);
			return new int[] { 1, noClass };
		}
		for (int h = 0; h < handlers.size(); h++) {
			insertXmlEndpoint(revisionId, fileId, rule, entry, symbolId, (String) handlers.get(h).get("methodId"), properties);
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

	/** 설정 XML 규칙으로 찾은 진입점 한 행을 넣습니다. 종류와 방식은 규칙에 적힌 것을 씁니다. */
	private void insertXmlEndpoint(long revisionId, long fileId, Rule.XmlEntry rule, XmlEndpointReader.Entry entry, String symbolId, String methodId
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

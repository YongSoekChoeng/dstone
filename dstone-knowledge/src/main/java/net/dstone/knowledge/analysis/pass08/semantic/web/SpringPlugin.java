package net.dstone.knowledge.analysis.pass08.semantic.web;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.common.EncodingDetector;
import net.dstone.knowledge.analysis.pass08.semantic.rule.RuleSet;
import net.dstone.knowledge.analysis.pass08.semantic.rule.SemanticRules;
import net.dstone.knowledge.analysis.pass08.semantic.ConstantEvaluator;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticPlugin;

/**
 * <pre>
 * 애노테이션에서 뜻을 읽어 냅니다. 어떤 애노테이션이 무슨 뜻인지는 규칙 파일(conf/rules/*.yml)에 있습니다.
 * 기본 규칙은 Spring 의 것(conf/rules/spring.yml)이고, 자체 프레임워크의 애노테이션은 규칙 파일을 더해 알려 줍니다.
 *
 *   endpoints.http         예: @RequestMapping / @GetMapping ...   → HTTP 진입점. 클래스의 주소와 메소드의 주소를 이어 붙인다(HttpEndpointBuilder).
 *                          상수로 적은 주소는 상수를 따라가 계산하고, 상위 클래스 · 인터페이스의 주소와 메소드도 Spring 처럼 물려받는다.
 *   endpoints.annotations  예: @Scheduled → SCHEDULED, @KafkaListener → LISTENER 진입점
 *   injects                예: @Autowired / @Inject / @Resource    → INJECTS 관계(필드를 가진 타입 → 필드의 타입)
 *   proxies                예: @Transactional / @Async ...         → 프록시가 끼어드는 메소드 표시(proxy_related)
 *   layers.annotations     예: @Controller / @Service ...          → 계층(HIGH). 규칙 묶음 order 순서, 묶음 안에서는 적힌 순서로 먼저 맞는 것
 *
 * 애노테이션은 단순 이름으로 알아봅니다(예: Service). 전체 이름(org.springframework.stereotype.Service)은
 * import가 와일드카드면 DECLARE 단계에서 알 수 없기 때문입니다. 같은 이름의 다른 애노테이션을 쓰는 프로젝트에서는 틀릴 수 있습니다.
 *
 * 계층(layers.annotations)을 진입점보다 먼저 정합니다. 상위 클래스의 주소 메소드를 물려받는 컨트롤러를 계층으로 알아보기 때문입니다.
 *
 * 아직 다루지 않는 것:
 * - static import 한 상수, 다른 jar 에 있는 상수로 적은 주소(properties_json.unresolvedPath 에 적힌 글이 남는다).
 * - 생성자 주입, setter 주입.
 * - XML로 적은 빈 설정(M5).
 * </pre>
 */
@Component
public class SpringPlugin implements SemanticPlugin {

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private ResourceDao resourceDao;

	@Autowired
	private SymbolDao symbolDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Override
	public String name() {
		return "spring";
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		SemanticRules rules = context.getSemanticRules();

		int layered = 0;
		List<RuleSet.LayerAnnotationRule> layerRules = rules.getLayerAnnotations();
		for (int i = 0; i < layerRules.size(); i++) {
			layered += semanticDao.updateLayerByAnnotation(revisionId, layerRules.get(i).layer, layerRules.get(i).annotations);
		}

		int[] http = insertHttpEndpoints(context, rules.getHttpMappings());
		int annotated = 0;
		List<RuleSet.AnnotatedEndpointRule> annotatedRules = rules.getAnnotatedEndpoints();
		for (int i = 0; i < annotatedRules.size(); i++) {
			annotated += semanticDao.insertAnnotatedEndpoints(revisionId, annotatedRules.get(i));
		}
		int injects = semanticDao.insertInjects(revisionId, rules.getInjectAnnotations());
		int proxied = semanticDao.markProxyRelated(revisionId, rules.getProxyAnnotations());

		return "HTTP 진입점 " + http[0] + "(상위 타입에서 물려받은 것 " + http[1] + ", 상수로 적은 주소 " + http[2]
				+ (http[3] > 0 ? ", 상수를 계산하지 못한 주소 " + http[3] : "") + "), 애노테이션 진입점(스케줄, 리스너 등) " + annotated
				+ ", 주입 " + injects + ", 프록시 메소드 " + proxied + ", 계층 " + layered + ".";
	}

	/**
	 * HTTP 진입점 규칙마다 애노테이션과 상속 관계를 읽어 진입점을 만듭니다.
	 *
	 * @return [진입점 수, 물려받은 것, 상수를 계산한 주소, 계산하지 못한 주소]
	 */
	private int[] insertHttpEndpoints(AnalysisJobContext context, List<RuleSet.HttpMappingRule> httpRules) throws Exception {
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
			RuleSet.HttpMappingRule rule = httpRules.get(r);
			List<Map<String, Object>> rows = semanticDao.selectHttpMappingAnnotations(revisionId, HttpEndpointBuilder.annotationNames(rule));
			if (rows.isEmpty()) {
				continue;
			}
			if (graph == null) {
				graph = typeGraph(revisionId, interfaces);
			}
			Set<String> involved = graph.withDescendants(HttpEndpointBuilder.mappedOwners(rule, rows));
			List<String> ids = new ArrayList<String>(involved);
			Map<String, HttpEndpointBuilder.TypeInfo> types = new HashMap<String, HttpEndpointBuilder.TypeInfo>();
			Map<String, List<HttpEndpointBuilder.MethodInfo>> methods = new HashMap<String, List<HttpEndpointBuilder.MethodInfo>>();
			for (int from = 0; from < ids.size(); from += 1000) {
				List<String> chunk = ids.subList(from, Math.min(ids.size(), from + 1000));
				List<Map<String, Object>> flags = semanticDao.selectTypeFlags(revisionId, chunk);
				for (int i = 0; i < flags.size(); i++) {
					Map<String, Object> f = flags.get(i);
					boolean concrete = "CLASS".equals(f.get("kind")) && !Boolean.TRUE.equals(f.get("isAbstract"));
					types.put((String) f.get("symbolId"), new HttpEndpointBuilder.TypeInfo((String) f.get("symbolId"), (String) f.get("fqn"), concrete
							, "CONTROLLER".equals(f.get("layer"))));
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

}

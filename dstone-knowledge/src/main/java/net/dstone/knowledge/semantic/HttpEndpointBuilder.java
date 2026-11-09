package net.dstone.knowledge.semantic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.semantic.rule.RuleSet;

/**
 * <pre>
 * HTTP 진입점 규칙(endpoints.http) 하나로 진입점 행을 만듭니다. DB 를 읽고 쓰는 일은 SpringPlugin 이 하고, 여기서는 읽어 온 것으로 계산만 합니다.
 * 그래서 상속 관계를 흉내 낸 값으로 단위 테스트할 수 있습니다.
 *
 * 주소 = 클래스의 주소 + 메소드의 주소 (클래스 주소가 /* 로 끝나면 /* 를 떼고 잇는다. Spring 과 같다)
 *
 * 애노테이션만 보던 예전 방식(SQL)에 더해 세 가지를 Spring 처럼 다룹니다.
 *   1) 주소를 상수로 적은 것: @RequestMapping(Urls.ORDER), @GetMapping(BASE + "/list")
 *      상수를 따라가 계산합니다(ConstantEvaluator). 계산하지 못하면 적힌 글을 그대로 두고 properties_json.unresolvedPath 에 남깁니다.
 *   2) 클래스에 주소가 없으면 상위 클래스 · 인터페이스의 클래스 주소를 씁니다(Spring 이 찾는 차례: 자기 → 인터페이스 → 상위 클래스).
 *   3) 상위 클래스 · 인터페이스의 메소드에 붙은 주소는 그것을 물려받은 컨트롤러의 진입점이 됩니다.
 *        abstract class BaseCrudController { @GetMapping("/list") list() }
 *        @RestController @RequestMapping("/order") class OrderController extends BaseCrudController
 *        → GET /order/list, 처리 클래스 OrderController, 처리 메소드 list(재정의했으면 재정의한 메소드)
 *      물려받는 클래스: abstract 가 아닌 클래스이면서 클래스 주소나 주소 붙은 메소드가 있거나, 애노테이션으로 정한 계층이 CONTROLLER.
 *      하위 클래스가 같은 메소드를 다시 정의하면서 자기 주소를 붙였으면 물려받지 않습니다(자기 것이 진입점).
 *      abstract 클래스 · 인터페이스 자신의 진입점은, 물려받은 컨트롤러가 하나도 없을 때만 남깁니다(빈으로 뜨지 않으므로).
 * </pre>
 */
public class HttpEndpointBuilder {

	/** 주소의 상수 식을 계산하는 곳 */
	public interface PathEvaluator {
		/** @param typeId 식이 적힌 타입 @return 계산한 값. 모르면 null */
		String evaluate(String expression, String typeId);
	}

	/** 타입 하나의 사정 */
	public static class TypeInfo {
		public final String symbolId;
		public final String fqn;
		public final boolean concreteClass;
		public final boolean controller;

		public TypeInfo(String symbolId, String fqn, boolean concreteClass, boolean controller) {
			this.symbolId = symbolId;
			this.fqn = fqn;
			this.concreteClass = concreteClass;
			this.controller = controller;
		}
	}

	/** 메소드 하나(재정의를 알아볼 때) */
	public static class MethodInfo {
		public final String methodId;
		public final String name;
		public final int paramCount;

		public MethodInfo(String methodId, String name, int paramCount) {
			this.methodId = methodId;
			this.name = name;
			this.paramCount = paramCount;
		}
	}

	/** 상속 관계. 상위 타입은 인터페이스(IMPLEMENTS, 인터페이스의 EXTENDS)를 앞에, 상위 클래스를 뒤에 둔다 */
	public static class TypeGraph {
		final Map<String, List<String>> interfaces = new HashMap<String, List<String>>();
		final Map<String, String> superclass = new HashMap<String, String>();
		final Map<String, List<String>> children = new HashMap<String, List<String>>();
		final Set<String> interfaceTypes = new HashSet<String>();

		/**
		 * @param relationType EXTENDS / IMPLEMENTS
		 * @param childIsInterface 하위 타입이 인터페이스면 true(인터페이스의 EXTENDS 는 상위 인터페이스다)
		 */
		public void add(String child, String parent, String relationType, boolean childIsInterface) {
			if ("IMPLEMENTS".equals(relationType) || childIsInterface) {
				listOf(interfaces, child).add(parent);
			} else if (!superclass.containsKey(child)) {
				superclass.put(child, parent);
			}
			listOf(children, parent).add(child);
		}

		/** 이 타입들과 그 하위 타입 전부 */
		public Set<String> withDescendants(Set<String> roots) {
			Set<String> all = new LinkedHashSet<String>(roots);
			List<String> queue = new ArrayList<String>(roots);
			for (int i = 0; i < queue.size(); i++) {
				List<String> kids = children.get(queue.get(i));
				if (kids != null) {
					for (int k = 0; k < kids.size(); k++) {
						if (all.add(kids.get(k))) {
							queue.add(kids.get(k));
						}
					}
				}
			}
			return all;
		}

		/** 상위 타입들. Spring 이 애노테이션을 찾는 차례(자기 인터페이스 → 상위 클래스) */
		List<String> parentsOf(String type) {
			List<String> parents = new ArrayList<String>();
			List<String> ifaces = interfaces.get(type);
			if (ifaces != null) {
				parents.addAll(ifaces);
			}
			String sup = superclass.get(type);
			if (sup != null) {
				parents.add(sup);
			}
			return parents;
		}

		private static <T> List<T> listOf(Map<String, List<T>> map, String key) {
			List<T> list = map.get(key);
			if (list == null) {
				list = new ArrayList<T>();
				map.put(key, list);
			}
			return list;
		}
	}

	/** 애노테이션 한 줄 */
	private static class Mapping {
		final Map<String, Object> row;
		final Map<String, Object> attributes;
		/** 애노테이션이 붙은 타입(메소드면 그 메소드의 타입) */
		final String typeId;

		Mapping(Map<String, Object> row) {
			this.row = row;
			this.attributes = AnnotationValues.parse((String) row.get("attributesJson"));
			this.typeId = "TYPE".equals(row.get("targetKind")) ? (String) row.get("targetId") : (String) row.get("ownerSymbolId");
		}

		String methodId() {
			return (String) row.get("targetId");
		}

		String methodName() {
			return (String) row.get("methodName");
		}

		int paramCount() {
			return row.get("paramCount") == null ? -1 : ((Number) row.get("paramCount")).intValue();
		}

		String annotation() {
			return (String) row.get("annotationName");
		}
	}

	private final RuleSet.HttpMappingRule rule;
	private final TypeGraph graph;
	private final Map<String, TypeInfo> types;
	private final Map<String, List<MethodInfo>> methodsByType;
	private final PathEvaluator evaluator;

	private final Map<String, RuleSet.HttpMethodAnnotation> methodRules = new HashMap<String, RuleSet.HttpMethodAnnotation>();
	private final Map<String, List<Mapping>> classMappings = new HashMap<String, List<Mapping>>();
	private final Map<String, List<Mapping>> methodMappingsByType = new LinkedHashMap<String, List<Mapping>>();
	/** 주소 애노테이션이 붙은 메소드 ID */
	private final Set<String> mappedMethods = new HashSet<String>();

	/** 계산한 진입점 수 가운데 상수를 따라간 것 · 물려받은 것 */
	private int evaluated;
	private int unresolved;
	private int inherited;

	public HttpEndpointBuilder(RuleSet.HttpMappingRule rule, TypeGraph graph, Map<String, TypeInfo> types, Map<String, List<MethodInfo>> methodsByType
			, PathEvaluator evaluator) {
		this.rule = rule;
		this.graph = graph;
		this.types = types;
		this.methodsByType = methodsByType;
		this.evaluator = evaluator;
		for (int i = 0; i < rule.methodAnnotations.size(); i++) {
			methodRules.put(rule.methodAnnotations.get(i).annotation, rule.methodAnnotations.get(i));
		}
	}

	/** 이 규칙이 보는 애노테이션 이름(메소드의 것 + 클래스의 것) */
	public static List<String> annotationNames(RuleSet.HttpMappingRule rule) {
		Set<String> names = new LinkedHashSet<String>();
		for (int i = 0; i < rule.methodAnnotations.size(); i++) {
			names.add(rule.methodAnnotations.get(i).annotation);
		}
		names.addAll(rule.classAnnotations);
		return new ArrayList<String>(names);
	}

	/** 주소 애노테이션이 붙은 메소드를 가진 타입들(상속을 따라 하위 타입을 찾을 출발점) */
	public static Set<String> mappedOwners(RuleSet.HttpMappingRule rule, List<Map<String, Object>> rows) {
		Set<String> owners = new LinkedHashSet<String>();
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			if ("METHOD".equals(row.get("targetKind")) && row.get("methodName") != null) {
				owners.add((String) row.get("ownerSymbolId"));
			} else if ("TYPE".equals(row.get("targetKind"))) {
				owners.add((String) row.get("targetId"));
			}
		}
		return owners;
	}

	/**
	 * @param rows 이 규칙의 애노테이션(SemanticDao.selectHttpMappingAnnotations, annotation_id 순)
	 * @return 진입점 행들. 키: httpMethod, path, symbolId, methodId, propertiesJson, fileId, lineStart
	 */
	public List<Map<String, Object>> build(List<Map<String, Object>> rows) {
		for (int i = 0; i < rows.size(); i++) {
			Mapping mapping = new Mapping(rows.get(i));
			if ("TYPE".equals(mapping.row.get("targetKind")) && rule.classAnnotations.contains(mapping.annotation())) {
				TypeGraph.listOf(classMappings, mapping.typeId).add(mapping);
			} else if ("METHOD".equals(mapping.row.get("targetKind")) && mapping.methodName() != null && methodRules.containsKey(mapping.annotation())) {
				TypeGraph.listOf(methodMappingsByType, mapping.typeId).add(mapping);
				mappedMethods.add(mapping.methodId());
			}
		}

		List<Map<String, Object>> endpoints = new ArrayList<Map<String, Object>>();
		Set<String> done = new HashSet<String>();
		for (Map.Entry<String, List<Mapping>> entry : methodMappingsByType.entrySet()) {
			String owner = entry.getKey();
			List<String> heirs = heirsOf(owner);
			List<Mapping> mappings = entry.getValue();
			for (int i = 0; i < mappings.size(); i++) {
				Mapping mapping = mappings.get(i);
				boolean passedOn = false;
				for (int h = 0; h < heirs.size(); h++) {
					String handler = handlerMethod(heirs.get(h), owner, mapping);
					if (handler == null) {
						// 하위 클래스가 다시 정의하면서 자기 주소를 붙였다. 그것이 진입점이다.
						passedOn = true;
						continue;
					}
					if (done.add(heirs.get(h) + "|" + mapping.methodId() + "|" + mapping.annotation())) {
						TypeInfo from = types.get(owner);
						add(endpoints, heirs.get(h), handler, mapping, from == null ? owner : from.fqn);
						inherited++;
					}
					passedOn = true;
				}
				TypeInfo info = types.get(owner);
				boolean concrete = info == null || info.concreteClass;
				if (concrete || !passedOn) {
					add(endpoints, owner, mapping.methodId(), mapping, null);
				}
			}
		}
		return endpoints;
	}

	public int getEvaluated() {
		return evaluated;
	}

	public int getUnresolved() {
		return unresolved;
	}

	public int getInherited() {
		return inherited;
	}

	/** 이 타입의 주소 메소드를 물려받아 진입점으로 삼는 하위 클래스들 */
	private List<String> heirsOf(String owner) {
		Set<String> roots = new LinkedHashSet<String>();
		roots.add(owner);
		List<String> heirs = new ArrayList<String>();
		for (String type : graph.withDescendants(roots)) {
			if (type.equals(owner)) {
				continue;
			}
			TypeInfo info = types.get(type);
			if (info == null || !info.concreteClass) {
				continue;
			}
			if (info.controller || classMappings.containsKey(type) || methodMappingsByType.containsKey(type)) {
				heirs.add(type);
			}
		}
		return heirs;
	}

	/**
	 * 하위 클래스 heir 에서 owner 의 주소 메소드를 실제로 처리하는 메소드.
	 * heir 에서 owner 쪽으로 올라가며 같은 이름 · 같은 파라미터 수의 메소드를 찾는다(가장 가까운 재정의).
	 *
	 * @return 처리 메소드 ID. 재정의한 메소드에 자기 주소가 붙어 있으면 null(물려받지 않는다)
	 */
	private String handlerMethod(String heir, String owner, Mapping mapping) {
		Set<String> between = ancestorsUpTo(heir, owner);
		List<String> queue = new ArrayList<String>();
		Set<String> seen = new HashSet<String>();
		queue.add(heir);
		for (int i = 0; i < queue.size(); i++) {
			String type = queue.get(i);
			if (type.equals(owner) || !seen.add(type)) {
				continue;
			}
			MethodInfo override = declared(type, mapping.methodName(), mapping.paramCount());
			if (override != null) {
				return mappedMethods.contains(override.methodId) ? null : override.methodId;
			}
			List<String> parents = graph.parentsOf(type);
			for (int p = 0; p < parents.size(); p++) {
				if (between.contains(parents.get(p))) {
					queue.add(parents.get(p));
				}
			}
		}
		return mapping.methodId();
	}

	/** heir 의 상위 타입 가운데 owner 의 하위(또는 owner 자신)인 것. 재정의를 찾을 때 이 길만 본다 */
	private Set<String> ancestorsUpTo(String heir, String owner) {
		Set<String> roots = new LinkedHashSet<String>();
		roots.add(owner);
		Set<String> belowOwner = graph.withDescendants(roots);
		Set<String> between = new HashSet<String>();
		List<String> queue = new ArrayList<String>();
		queue.add(heir);
		for (int i = 0; i < queue.size() && i < 200; i++) {
			List<String> parents = graph.parentsOf(queue.get(i));
			for (int p = 0; p < parents.size(); p++) {
				if (belowOwner.contains(parents.get(p)) && between.add(parents.get(p))) {
					queue.add(parents.get(p));
				}
			}
		}
		return between;
	}

	private MethodInfo declared(String type, String name, int paramCount) {
		List<MethodInfo> methods = methodsByType.get(type);
		if (methods == null) {
			return null;
		}
		for (int i = 0; i < methods.size(); i++) {
			if (methods.get(i).name.equals(name) && methods.get(i).paramCount == paramCount) {
				return methods.get(i);
			}
		}
		return null;
	}

	/** 이 타입의 클래스 주소 애노테이션. 자기에게 없으면 인터페이스 → 상위 클래스 차례로 가장 먼저 만나는 것 */
	private List<Mapping> classMappingsOf(String type) {
		List<String> queue = new ArrayList<String>();
		Set<String> seen = new HashSet<String>();
		queue.add(type);
		for (int i = 0; i < queue.size(); i++) {
			String t = queue.get(i);
			if (!seen.add(t)) {
				continue;
			}
			List<Mapping> found = classMappings.get(t);
			if (found != null) {
				return found;
			}
			queue.addAll(graph.parentsOf(t));
		}
		return new ArrayList<Mapping>();
	}

	private void add(List<Map<String, Object>> endpoints, String handlerType, String handlerMethod, Mapping mapping, String inheritedFrom) {
		List<String> unresolvedPaths = new ArrayList<String>();
		List<String> classPaths = new ArrayList<String>();
		List<Mapping> classes = classMappingsOf(handlerType);
		if (classes.isEmpty()) {
			classPaths.add("");
		}
		for (int c = 0; c < classes.size(); c++) {
			classPaths.addAll(paths(classes.get(c), unresolvedPaths));
		}
		List<String> methodPaths = paths(mapping, unresolvedPaths);
		List<String> httpMethods = httpMethods(mapping);

		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("annotation", mapping.annotation());
		if (inheritedFrom != null) {
			properties.put("inheritedFrom", inheritedFrom);
		}
		if (!unresolvedPaths.isEmpty()) {
			properties.put("unresolvedPath", unresolvedPaths.get(0));
		}
		String propertiesJson = JsonText.of(properties);
		for (int c = 0; c < classPaths.size(); c++) {
			for (int m = 0; m < methodPaths.size(); m++) {
				String path = join(classPaths.get(c), methodPaths.get(m));
				for (int h = 0; h < httpMethods.size(); h++) {
					Map<String, Object> endpoint = new HashMap<String, Object>();
					endpoint.put("httpMethod", httpMethods.get(h));
					endpoint.put("path", path);
					endpoint.put("symbolId", handlerType);
					endpoint.put("methodId", handlerMethod);
					endpoint.put("propertiesJson", propertiesJson);
					endpoint.put("fileId", mapping.row.get("fileId"));
					endpoint.put("lineStart", mapping.row.get("lineStart"));
					endpoints.add(endpoint);
				}
			}
		}
	}

	/** 규칙의 주소 속성(pathAttributes) 가운데 처음으로 적힌 것의 값들. 없거나 비었으면 [""] */
	private List<String> paths(Mapping mapping, List<String> unresolvedPaths) {
		List<String> values = new ArrayList<String>();
		for (int i = 0; i < rule.pathAttributes.size(); i++) {
			if (mapping.attributes.get(rule.pathAttributes.get(i)) != null) {
				values = AnnotationValues.strings(mapping.attributes, rule.pathAttributes.get(i));
				break;
			}
		}
		if (values.isEmpty()) {
			values.add("");
		}
		List<String> paths = new ArrayList<String>();
		for (int i = 0; i < values.size(); i++) {
			String value = values.get(i);
			if (AnnotationValues.isExpression(value)) {
				String computed = evaluator.evaluate(value, mapping.typeId);
				if (computed != null) {
					evaluated++;
					value = computed;
				} else {
					unresolved++;
					unresolvedPaths.add(value);
				}
			}
			paths.add(value);
		}
		return paths;
	}

	/** 규칙에 정한 HTTP 메소드, 아니면 속성(method = RequestMethod.GET)의 값. 없으면 ALL */
	private List<String> httpMethods(Mapping mapping) {
		RuleSet.HttpMethodAnnotation methodRule = methodRules.get(mapping.annotation());
		List<String> methods = new ArrayList<String>();
		if (methodRule.httpMethod != null) {
			methods.add(methodRule.httpMethod);
			return methods;
		}
		List<String> values = methodRule.methodAttribute == null ? new ArrayList<String>()
				: AnnotationValues.strings(mapping.attributes, methodRule.methodAttribute);
		if (values.isEmpty()) {
			values.add("");
		}
		for (int i = 0; i < values.size(); i++) {
			String name = values.get(i).replaceFirst("^.*\\.", "");
			methods.add(name.length() == 0 ? "ALL" : name);
		}
		return methods;
	}

	/** 클래스 주소 + 메소드 주소. /* 로 끝나는 클래스 주소는 /* 를 뗀다. 겹친 / 는 하나로, 끝의 / 는 뗀다(/ 하나뿐이면 그대로) */
	static String join(String classPath, String methodPath) {
		String joined = ("/" + classPath.replaceFirst("/\\*$", "") + "/" + methodPath).replaceAll("/+", "/");
		return "/".equals(joined) ? joined : joined.replaceFirst("/$", "");
	}

}

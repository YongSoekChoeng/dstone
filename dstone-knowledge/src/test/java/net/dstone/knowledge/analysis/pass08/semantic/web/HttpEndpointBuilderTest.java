package net.dstone.knowledge.analysis.pass08.semantic.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.knowledge.analysis.pass08.semantic.rule.RuleSet;
import net.dstone.knowledge.analysis.pass08.semantic.AnnotationValues;

/**
 * <pre>
 * HTTP 진입점을 만드는 계산(HttpEndpointBuilder)을 확인합니다. 상속 관계와 애노테이션 행은 흉내 낸 값입니다.
 *
 *   abstract BaseController            @GetMapping("/list") list()      @PostMapping("/save") save(x)
 *   OrderController extends Base       @RequestMapping("/order"), list() 를 애노테이션 없이 재정의, save(x) 를 @PostMapping("/save2") 로 재정의
 *   ItemController extends Base        @RequestMapping(Urls.ITEM) → "/item" 으로 계산된다
 *   PlainController                    클래스 주소가 상수인데 계산하지 못함(Paths.NONE), @RequestMapping(value = "/a", method = RequestMethod.POST)
 * </pre>
 */
public class HttpEndpointBuilderTest {

	private RuleSet.HttpMappingRule springRule() {
		List<RuleSet.HttpMethodAnnotation> methods = new ArrayList<RuleSet.HttpMethodAnnotation>();
		methods.add(new RuleSet.HttpMethodAnnotation("RequestMapping", null, "method"));
		methods.add(new RuleSet.HttpMethodAnnotation("GetMapping", "GET", null));
		methods.add(new RuleSet.HttpMethodAnnotation("PostMapping", "POST", null));
		List<String> paths = new ArrayList<String>();
		paths.add("value");
		paths.add("path");
		return new RuleSet.HttpMappingRule(Collections.singletonList("RequestMapping"), paths, methods);
	}

	private Map<String, Object> row(String kind, String target, String annotation, String json, String owner, String method, int params) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("targetKind", kind);
		row.put("targetId", target);
		row.put("annotationName", annotation);
		row.put("attributesJson", json);
		row.put("fileId", Long.valueOf(1));
		row.put("lineStart", Integer.valueOf(10));
		row.put("ownerSymbolId", owner);
		row.put("methodName", method);
		row.put("paramCount", method == null ? null : Integer.valueOf(params));
		return row;
	}

	private List<String> run(List<Map<String, Object>> rows, HttpEndpointBuilder.TypeGraph graph, Map<String, HttpEndpointBuilder.TypeInfo> types
			, Map<String, List<HttpEndpointBuilder.MethodInfo>> methods) {
		HttpEndpointBuilder builder = new HttpEndpointBuilder(springRule(), graph, types, methods, new HttpEndpointBuilder.PathEvaluator() {
			@Override
			public String evaluate(String expression, String typeId) {
				return "Urls.ITEM".equals(expression) ? "/item" : null;
			}
		});
		List<Map<String, Object>> endpoints = builder.build(rows);
		List<String> result = new ArrayList<String>();
		for (int i = 0; i < endpoints.size(); i++) {
			Map<String, Object> e = endpoints.get(i);
			String properties = (String) e.get("propertiesJson");
			result.add(e.get("httpMethod") + " " + e.get("path") + " " + e.get("symbolId") + "#" + e.get("methodId")
					+ (properties.contains("unresolvedPath") ? " ?" : "") + (properties.contains("inheritedFrom") ? " ^" : ""));
		}
		Collections.sort(result);
		return result;
	}

	@Test
	public void 상위_클래스의_주소_메소드를_컨트롤러가_물려받는다() {
		HttpEndpointBuilder.TypeGraph graph = new HttpEndpointBuilder.TypeGraph();
		graph.add("O", "B", "EXTENDS", false);
		graph.add("I", "B", "EXTENDS", false);
		Map<String, HttpEndpointBuilder.TypeInfo> types = new HashMap<String, HttpEndpointBuilder.TypeInfo>();
		types.put("B", new HttpEndpointBuilder.TypeInfo("B", "a.BaseController", false, false));
		types.put("O", new HttpEndpointBuilder.TypeInfo("O", "a.OrderController", true, true));
		types.put("I", new HttpEndpointBuilder.TypeInfo("I", "a.ItemController", true, true));
		Map<String, List<HttpEndpointBuilder.MethodInfo>> methods = new HashMap<String, List<HttpEndpointBuilder.MethodInfo>>();
		List<HttpEndpointBuilder.MethodInfo> order = new ArrayList<HttpEndpointBuilder.MethodInfo>();
		order.add(new HttpEndpointBuilder.MethodInfo("oList", "list", 0));
		order.add(new HttpEndpointBuilder.MethodInfo("oSave", "save", 1));
		methods.put("O", order);

		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		rows.add(row("METHOD", "bList", "GetMapping", "{\"value\": \"/list\"}", "B", "list", 0));
		rows.add(row("METHOD", "bSave", "PostMapping", "{\"value\": \"/save\"}", "B", "save", 1));
		rows.add(row("TYPE", "O", "RequestMapping", "{\"value\": \"/order\"}", null, null, 0));
		rows.add(row("METHOD", "oSave", "PostMapping", "{\"value\": \"/save2\"}", "O", "save", 1));
		rows.add(row("TYPE", "I", "RequestMapping", "{\"value\": \"Urls.ITEM\"}", null, null, 0));

		assertEquals("[GET /item/list I#bList ^, GET /order/list O#oList ^, POST /item/save I#bSave ^, POST /order/save2 O#oSave]"
				, String.valueOf(run(rows, graph, types, methods)));
	}

	@Test
	public void 클래스_주소가_없으면_상위_타입의_것을_쓰고_계산하지_못한_상수는_표시한다() {
		HttpEndpointBuilder.TypeGraph graph = new HttpEndpointBuilder.TypeGraph();
		graph.add("C", "Api", "IMPLEMENTS", false);
		Map<String, HttpEndpointBuilder.TypeInfo> types = new HashMap<String, HttpEndpointBuilder.TypeInfo>();
		types.put("C", new HttpEndpointBuilder.TypeInfo("C", "a.C", true, false));
		types.put("Api", new HttpEndpointBuilder.TypeInfo("Api", "a.Api", false, false));
		types.put("P", new HttpEndpointBuilder.TypeInfo("P", "a.P", true, true));
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		rows.add(row("TYPE", "Api", "RequestMapping", "{\"path\": [\"/api\"]}", null, null, 0));
		rows.add(row("METHOD", "cGet", "GetMapping", "{}", "C", "get", 0));
		rows.add(row("TYPE", "P", "RequestMapping", "{\"value\": \"Paths.NONE\"}", null, null, 0));
		rows.add(row("METHOD", "pA", "RequestMapping", "{\"value\": [\"/a\", \"/b/\"], \"method\": \"RequestMethod.POST\"}", "P", "a", 0));

		assertEquals("[GET /api C#cGet, POST /Paths.NONE/a P#pA ?, POST /Paths.NONE/b P#pA ?]", String.valueOf(run(rows, graph, types
				, new HashMap<String, List<HttpEndpointBuilder.MethodInfo>>())));
	}

	@Test
	public void 주소를_잇는_규칙은_Spring_과_같다() {
		assertEquals("/sample/user/listUser.do", HttpEndpointBuilder.join("/sample/user/*", "/listUser.do"));
		assertEquals("/a/**/b", HttpEndpointBuilder.join("/a/**", "b"));
		assertEquals("/", HttpEndpointBuilder.join("", ""));
		assertEquals("/order", HttpEndpointBuilder.join("/order/", ""));
		assertTrue(AnnotationValues.isExpression("ApiPaths.Order.LIST"));
		assertTrue(AnnotationValues.isExpression("BASE + \"/list\""));
		assertTrue(AnnotationValues.isExpression("LIST"));
		assertTrue(!AnnotationValues.isExpression("Main.do") && !AnnotationValues.isExpression("list") && !AnnotationValues.isExpression("/order/list"));
	}

}

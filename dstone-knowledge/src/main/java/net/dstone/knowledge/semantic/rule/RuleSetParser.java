package net.dstone.knowledge.semantic.rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import net.dstone.knowledge.common.util.HashText;
import net.dstone.knowledge.semantic.ScreenMenu;
import net.dstone.knowledge.semantic.ScreenScript;

/**
 * <pre>
 * 규칙 파일(YAML) 하나를 RuleSet 으로 읽습니다. DB 도 Spring 도 쓰지 않는 순수한 클래스라 단위 테스트로 확인합니다.
 *
 * 엄격하게 읽습니다. 모르는 키, 잘못된 계층 이름, 틀린 정규식이 있으면 RuleException 을 던지고
 * 메시지에 "어느 파일의 어느 자리" 인지 적습니다. 오타가 조용히 무시되면 "규칙을 적었는데 왜 안 이어지지?" 를
 * 찾느라 시간을 버리기 때문입니다. 서버가 뜰 때 conf/rules 전체를 이렇게 검사합니다(SemanticRuleRegistry).
 *
 * 파일 모양은 conf/rules/README.md 에 있습니다.
 * </pre>
 */
public class RuleSetParser {

	/** 계층으로 쓸 수 있는 이름 */
	public static final List<String> LAYERS = Arrays.asList(
			"CONTROLLER", "SERVICE", "REPOSITORY", "MODEL", "VIEW", "CONFIG", "WEB_FILTER", "UTIL", "EXCEPTION", "BATCH", "COMPONENT", "ASPECT");

	/** 진입점 종류: 영문 대문자와 밑줄, 20자 이내(analysis_endpoint.endpoint_type) */
	private static final Pattern ENDPOINT_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{1,19}");
	/** 애노테이션 이름, 메소드 이름, 속성 이름: Java 식별자 */
	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
	/** 타입의 전체 이름 */
	private static final Pattern QUALIFIED = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
	private static final Pattern HTTP_METHOD = Pattern.compile("[A-Z]{3,10}");
	/** 묶음 이름: 파일 이름으로 쓸 수 있는 글자 */
	private static final Pattern SET_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,59}");
	/** 인자 위치의 한도. DECLARE 가 남기는 인자 수도 이만큼이다 */
	public static final int MAX_ARG_INDEX = 9;

	/**
	 * @param text     파일 내용
	 * @param fileName 파일 이름(메시지와 묶음 이름 확인에 쓴다). 예: spring.yml
	 */
	public RuleSet parse(String text, String fileName) {
		Object root;
		try {
			root = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
		} catch (RuntimeException e) {
			throw new RuleException(fileName, "", "YAML 문법이 맞지 않습니다: " + firstLine(e.getMessage()));
		}
		Reader r = new Reader(fileName);
		Map<String, Object> top = r.map(root, "");
		r.keys(top, "", "name", "description", "order", "autoApply", "endpoints", "injects", "proxies", "layers", "callArguments", "screens");

		RuleSet.Builder b = new RuleSet.Builder();
		String baseName = fileName.replaceFirst("\\.ya?ml$", "");
		b.name = r.optString(top, "", "name", baseName);
		if (!SET_NAME.matcher(b.name).matches()) {
			throw r.error("name", "묶음 이름은 영문자, 숫자, - _ . 로 60자 이내여야 합니다: " + b.name);
		}
		if (!b.name.equals(baseName)) {
			throw r.error("name", "묶음 이름(" + b.name + ")은 파일 이름(" + baseName + ")과 같아야 합니다. 프로젝트의 ruleSets 에 적는 이름이라 헷갈리지 않게 맞춥니다.");
		}
		b.description = r.optString(top, "", "description", null);
		b.order = r.optInt(top, "", "order", 500);
		b.autoApply = r.optBool(top, "", "autoApply", true);
		b.source = fileName;
		b.hash = HashText.sha256(text).substring(0, 12);

		readEndpoints(r, top.get("endpoints"), b);
		if (top.get("injects") != null) {
			Map<String, Object> injects = r.map(top.get("injects"), "injects");
			r.keys(injects, "injects", "annotations");
			b.injectAnnotations.addAll(r.identifiers(injects, "injects", "annotations", true));
		}
		if (top.get("proxies") != null) {
			Map<String, Object> proxies = r.map(top.get("proxies"), "proxies");
			r.keys(proxies, "proxies", "annotations");
			b.proxyAnnotations.addAll(r.identifiers(proxies, "proxies", "annotations", true));
		}
		readLayers(r, top.get("layers"), b);
		readCallArguments(r, top.get("callArguments"), b);
		readScreens(r, top.get("screens"), b);
		return b.build();
	}

	private void readEndpoints(Reader r, Object node, RuleSet.Builder b) {
		if (node == null) {
			return;
		}
		Map<String, Object> endpoints = r.map(node, "endpoints");
		r.keys(endpoints, "endpoints", "http", "annotations", "superTypes");

		List<Object> https = r.list(endpoints.get("http"), "endpoints.http");
		for (int i = 0; i < https.size(); i++) {
			String path = "endpoints.http[" + i + "]";
			Map<String, Object> item = r.map(https.get(i), path);
			r.keys(item, path, "classAnnotations", "pathAttributes", "methodAnnotations");
			List<RuleSet.HttpMethodAnnotation> methods = new ArrayList<RuleSet.HttpMethodAnnotation>();
			List<Object> list = r.list(item.get("methodAnnotations"), path + ".methodAnnotations");
			if (list.isEmpty()) {
				throw r.error(path + ".methodAnnotations", "메소드에 붙는 애노테이션을 하나 이상 적어야 합니다.");
			}
			for (int j = 0; j < list.size(); j++) {
				String p = path + ".methodAnnotations[" + j + "]";
				Map<String, Object> m = r.map(list.get(j), p);
				r.keys(m, p, "annotation", "httpMethod", "methodAttribute");
				String annotation = r.identifier(m, p, "annotation");
				String httpMethod = r.optString(m, p, "httpMethod", null);
				String methodAttribute = r.optString(m, p, "methodAttribute", null);
				if ((httpMethod == null) == (methodAttribute == null)) {
					throw r.error(p, "httpMethod(정해진 HTTP 메소드)와 methodAttribute(HTTP 메소드가 적힌 속성) 가운데 하나만 적어야 합니다.");
				}
				if (httpMethod != null && !HTTP_METHOD.matcher(httpMethod).matches()) {
					throw r.error(p + ".httpMethod", "GET, POST 처럼 영문 대문자여야 합니다: " + httpMethod);
				}
				if (methodAttribute != null && !IDENTIFIER.matcher(methodAttribute).matches()) {
					throw r.error(p + ".methodAttribute", "속성 이름이 아닙니다: " + methodAttribute);
				}
				methods.add(new RuleSet.HttpMethodAnnotation(annotation, httpMethod, methodAttribute));
			}
			b.httpMappings.add(new RuleSet.HttpMappingRule(r.identifiers(item, path, "classAnnotations", false)
					, r.identifiers(item, path, "pathAttributes", true), methods));
		}

		List<Object> annotated = r.list(endpoints.get("annotations"), "endpoints.annotations");
		for (int i = 0; i < annotated.size(); i++) {
			String path = "endpoints.annotations[" + i + "]";
			Map<String, Object> item = r.map(annotated.get(i), path);
			r.keys(item, path, "endpointType", "annotations", "pathAttributes");
			b.annotatedEndpoints.add(new RuleSet.AnnotatedEndpointRule(r.endpointType(item, path)
					, r.identifiers(item, path, "annotations", true), r.identifiers(item, path, "pathAttributes", false)));
		}

		List<Object> superTypes = r.list(endpoints.get("superTypes"), "endpoints.superTypes");
		for (int i = 0; i < superTypes.size(); i++) {
			String path = "endpoints.superTypes[" + i + "]";
			Map<String, Object> item = r.map(superTypes.get(i), path);
			r.keys(item, path, "endpointType", "superTypes", "methods", "methodPattern", "paramCount");
			List<String> methods = r.identifiers(item, path, "methods", false);
			String methodPattern = r.optString(item, path, "methodPattern", null);
			if (methods.isEmpty() == (methodPattern == null)) {
				throw r.error(path, "methods(메소드 이름 목록)와 methodPattern(메소드 이름 정규식) 가운데 하나만 적어야 합니다.");
			}
			if (methodPattern != null) {
				r.regex(methodPattern, path + ".methodPattern");
			}
			Integer paramCount = item.get("paramCount") == null ? null : Integer.valueOf(r.optInt(item, path, "paramCount", 0));
			b.superTypeEndpoints.add(new RuleSet.SuperTypeEndpointRule(r.endpointType(item, path), r.qualifiedNames(item, path, "superTypes")
					, methods, methodPattern, paramCount));
		}
	}

	private void readLayers(Reader r, Object node, RuleSet.Builder b) {
		if (node == null) {
			return;
		}
		Map<String, Object> layers = r.map(node, "layers");
		r.keys(layers, "layers", "annotations", "superTypes", "names");

		List<Object> annotations = r.list(layers.get("annotations"), "layers.annotations");
		for (int i = 0; i < annotations.size(); i++) {
			String path = "layers.annotations[" + i + "]";
			Map<String, Object> item = r.map(annotations.get(i), path);
			r.keys(item, path, "layer", "annotations");
			b.layerAnnotations.add(new RuleSet.LayerAnnotationRule(r.layer(item, path), r.identifiers(item, path, "annotations", true)));
		}
		List<Object> superTypes = r.list(layers.get("superTypes"), "layers.superTypes");
		for (int i = 0; i < superTypes.size(); i++) {
			String path = "layers.superTypes[" + i + "]";
			Map<String, Object> item = r.map(superTypes.get(i), path);
			r.keys(item, path, "layer", "types");
			b.layerSuperTypes.add(new RuleSet.LayerSuperTypeRule(r.layer(item, path), r.qualifiedNames(item, path, "types")));
		}
		List<Object> names = r.list(layers.get("names"), "layers.names");
		for (int i = 0; i < names.size(); i++) {
			String path = "layers.names[" + i + "]";
			Map<String, Object> item = r.map(names.get(i), path);
			r.keys(item, path, "layer", "pattern");
			String pattern = r.string(item, path, "pattern");
			r.regex(pattern, path + ".pattern");
			b.layerNames.add(new RuleSet.LayerNameRule(r.layer(item, path), pattern));
		}
	}

	private void readCallArguments(Reader r, Object node, RuleSet.Builder b) {
		List<Object> rules = r.list(node, "callArguments");
		for (int i = 0; i < rules.size(); i++) {
			String path = "callArguments[" + i + "]";
			Map<String, Object> item = r.map(rules.get(i), path);
			r.keys(item, path, "target", "methods", "constructors", "argIndex", "receiverTypes", "template", "exclude");
			String target = r.string(item, path, "target").toUpperCase(Locale.ROOT);
			if (!RuleSet.CallArgumentRule.TARGET_SQL.equals(target) && !RuleSet.CallArgumentRule.TARGET_VIEW.equals(target)) {
				throw r.error(path + ".target", "SQL 또는 VIEW 여야 합니다: " + target);
			}
			List<String> methods = r.identifiers(item, path, "methods", false);
			List<String> constructors = r.identifiers(item, path, "constructors", false);
			if (methods.isEmpty() && constructors.isEmpty()) {
				throw r.error(path, "methods(메소드 이름)나 constructors(생성자 타입 이름)를 하나 이상 적어야 합니다.");
			}
			int argIndex = r.optInt(item, path, "argIndex", 0);
			if (argIndex < 0 || argIndex > MAX_ARG_INDEX) {
				throw r.error(path + ".argIndex", "0 부터 " + MAX_ARG_INDEX + " 사이여야 합니다: " + argIndex);
			}
			List<String> receiverTypes = r.qualifiedNamesOptional(item, path, "receiverTypes");
			if (!receiverTypes.isEmpty() && methods.isEmpty()) {
				throw r.error(path + ".receiverTypes", "receiverTypes 는 메소드 호출(methods)에만 씁니다. 생성자는 타입 이름 자체가 대상입니다.");
			}
			String defaultTemplate = RuleSet.CallArgumentRule.TARGET_VIEW.equals(target) ? "{value}.jsp" : "{value}";
			String template = r.optString(item, path, "template", defaultTemplate);
			int at = template.indexOf("{value}");
			if (at < 0 || template.indexOf("{value}", at + 1) >= 0) {
				throw r.error(path + ".template", "{value} 를 한 번 적어야 합니다: " + template);
			}
			String exclude = r.optString(item, path, "exclude", null);
			if (exclude != null) {
				r.regex(exclude, path + ".exclude");
			}
			b.callArguments.add(new RuleSet.CallArgumentRule(target, methods, constructors, argIndex, receiverTypes
					, template.substring(0, at), template.substring(at + "{value}".length()), exclude));
		}
	}

	private void readScreens(Reader r, Object node, RuleSet.Builder b) {
		if (node == null) {
			return;
		}
		Map<String, Object> screens = r.map(node, "screens");
		r.keys(screens, "screens", "calls", "fields", "opens", "menus");

		List<Object> calls = r.list(screens.get("calls"), "screens.calls");
		for (int i = 0; i < calls.size(); i++) {
			String path = "screens.calls[" + i + "]";
			Map<String, Object> item = r.map(calls.get(i), path);
			r.keys(item, path, "function", "method");
			b.screenCalls.add(new ScreenScript.CallRule(r.identifier(item, path, "function"), r.idTemplate(item, path, "method")));
		}
		List<Object> fields = r.list(screens.get("fields"), "screens.fields");
		for (int i = 0; i < fields.size(); i++) {
			String path = "screens.fields[" + i + "]";
			Map<String, Object> item = r.map(fields.get(i), path);
			r.keys(item, path, "field", "method");
			b.screenFields.add(ScreenScript.CallRule.field(r.identifier(item, path, "field"), r.idTemplate(item, path, "method")));
		}
		List<Object> opens = r.list(screens.get("opens"), "screens.opens");
		for (int i = 0; i < opens.size(); i++) {
			String path = "screens.opens[" + i + "]";
			Map<String, Object> item = r.map(opens.get(i), path);
			r.keys(item, path, "function", "screen");
			b.screenOpens.add(new ScreenScript.CallRule(r.identifier(item, path, "function"), r.idTemplate(item, path, "screen")));
		}
		List<Object> menus = r.list(screens.get("menus"), "screens.menus");
		for (int i = 0; i < menus.size(); i++) {
			String path = "screens.menus[" + i + "]";
			Map<String, Object> item = r.map(menus.get(i), path);
			r.keys(item, path, "nameKey", "pathKey");
			b.screenMenus.add(new ScreenMenu.Rule(r.identifier(item, path, "nameKey"), r.identifier(item, path, "pathKey")));
		}
	}

	private static String firstLine(String text) {
		if (text == null) {
			return "";
		}
		int nl = text.indexOf('\n');
		return nl < 0 ? text : text.substring(0, nl);
	}

	/**
	 * <pre>
	 * YAML 에서 읽은 Map/List 를 자리(path)를 붙여 가며 꺼내는 도우미. 틀리면 그 자리를 적어 RuleException 을 던진다.
	 * </pre>
	 */
	private static class Reader {

		private final String fileName;

		Reader(String fileName) {
			this.fileName = fileName;
		}

		RuleException error(String path, String message) {
			return new RuleException(fileName, path, message);
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> map(Object node, String path) {
			if (!(node instanceof Map)) {
				throw error(path, "키: 값 모양이어야 합니다.");
			}
			return (Map<String, Object>) node;
		}

		@SuppressWarnings("unchecked")
		List<Object> list(Object node, String path) {
			if (node == null) {
				return new ArrayList<Object>();
			}
			if (!(node instanceof List)) {
				throw error(path, "목록(- 항목)이어야 합니다.");
			}
			return (List<Object>) node;
		}

		/** 모르는 키가 있으면 오류. 오타를 잡으려는 것이다 */
		void keys(Map<String, Object> map, String path, String... allowed) {
			List<String> known = Arrays.asList(allowed);
			Iterator<String> it = map.keySet().iterator();
			while (it.hasNext()) {
				Object key = it.next();
				if (!known.contains(String.valueOf(key))) {
					throw error(path.length() == 0 ? String.valueOf(key) : path + "." + key, "모르는 키입니다. 쓸 수 있는 키: " + known);
				}
			}
		}

		String string(Map<String, Object> map, String path, String key) {
			String value = optString(map, path, key, null);
			if (value == null) {
				throw error(path.length() == 0 ? key : path + "." + key, "값이 필요합니다.");
			}
			return value;
		}

		String optString(Map<String, Object> map, String path, String key, String defaultValue) {
			Object value = map.get(key);
			if (value == null) {
				return defaultValue;
			}
			if (value instanceof Map || value instanceof Collection) {
				throw error(path.length() == 0 ? key : path + "." + key, "글자 하나여야 합니다.");
			}
			String text = String.valueOf(value).trim();
			return text.length() == 0 ? defaultValue : text;
		}

		int optInt(Map<String, Object> map, String path, String key, int defaultValue) {
			Object value = map.get(key);
			if (value == null) {
				return defaultValue;
			}
			if (!(value instanceof Integer)) {
				throw error(path.length() == 0 ? key : path + "." + key, "정수여야 합니다: " + value);
			}
			return ((Integer) value).intValue();
		}

		boolean optBool(Map<String, Object> map, String path, String key, boolean defaultValue) {
			Object value = map.get(key);
			if (value == null) {
				return defaultValue;
			}
			if (!(value instanceof Boolean)) {
				throw error(path.length() == 0 ? key : path + "." + key, "true 또는 false 여야 합니다: " + value);
			}
			return ((Boolean) value).booleanValue();
		}

		String identifier(Map<String, Object> map, String path, String key) {
			String value = string(map, path, key);
			if (!IDENTIFIER.matcher(value).matches()) {
				throw error(path + "." + key, "이름(영문자, 숫자, _, $)이어야 합니다: " + value);
			}
			return value;
		}

		/** 글자 목록. 하나만 적었으면(목록이 아니면) 그 하나로 본다 */
		List<String> strings(Map<String, Object> map, String path, String key, boolean required) {
			Object node = map.get(key);
			List<String> values = new ArrayList<String>();
			if (node != null) {
				List<Object> items = node instanceof List ? list(node, path + "." + key) : Arrays.asList(node);
				for (int i = 0; i < items.size(); i++) {
					Object item = items.get(i);
					if (item == null || item instanceof Map || item instanceof Collection) {
						throw error(path + "." + key + "[" + i + "]", "글자여야 합니다.");
					}
					String text = String.valueOf(item).trim();
					if (text.length() > 0 && !values.contains(text)) {
						values.add(text);
					}
				}
			}
			if (required && values.isEmpty()) {
				throw error(path + "." + key, "하나 이상 적어야 합니다.");
			}
			return values;
		}

		List<String> identifiers(Map<String, Object> map, String path, String key, boolean required) {
			List<String> values = strings(map, path, key, required);
			for (int i = 0; i < values.size(); i++) {
				if (!IDENTIFIER.matcher(values.get(i)).matches()) {
					throw error(path + "." + key, "이름(영문자, 숫자, _, $)이어야 합니다. 애노테이션은 @ 없이 단순 이름으로 적습니다: " + values.get(i));
				}
			}
			return values;
		}

		List<String> qualifiedNames(Map<String, Object> map, String path, String key) {
			List<String> values = qualifiedNamesOptional(map, path, key);
			if (values.isEmpty()) {
				throw error(path + "." + key, "하나 이상 적어야 합니다.");
			}
			return values;
		}

		List<String> qualifiedNamesOptional(Map<String, Object> map, String path, String key) {
			List<String> values = strings(map, path, key, false);
			for (int i = 0; i < values.size(); i++) {
				if (!QUALIFIED.matcher(values.get(i)).matches()) {
					throw error(path + "." + key, "타입의 전체 이름(패키지.클래스)이어야 합니다: " + values.get(i));
				}
			}
			return values;
		}

		String layer(Map<String, Object> map, String path) {
			String layer = string(map, path, "layer").toUpperCase(Locale.ROOT);
			if (!LAYERS.contains(layer)) {
				throw error(path + ".layer", "쓸 수 있는 계층이 아닙니다: " + layer + " (쓸 수 있는 값: " + LAYERS + ")");
			}
			return layer;
		}

		String endpointType(Map<String, Object> map, String path) {
			String type = string(map, path, "endpointType").toUpperCase(Locale.ROOT);
			if (!ENDPOINT_TYPE.matcher(type).matches()) {
				throw error(path + ".endpointType", "영문 대문자와 _ 로 20자 이내여야 합니다: " + type);
			}
			return type;
		}

		/** {id} 가 들어 있는 틀 */
		String idTemplate(Map<String, Object> map, String path, String key) {
			String template = string(map, path, key);
			if (template.indexOf("{id}") < 0) {
				throw error(path + "." + key, "{id} 가 들어 있어야 합니다: " + template);
			}
			return template;
		}

		void regex(String pattern, String path) {
			try {
				Pattern.compile(pattern);
			} catch (PatternSyntaxException e) {
				throw error(path, "정규식이 맞지 않습니다: " + firstLine(e.getMessage()));
			}
		}
	}

}

package net.dstone.knowledge.semantic.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dstone.knowledge.semantic.ScreenMenu;
import net.dstone.knowledge.semantic.ScreenScript;

/**
 * <pre>
 * 규칙 파일(conf/rules/*.yml) 하나를 읽은 결과입니다. 파일 하나가 규칙 묶음 하나입니다.
 *
 * 프레임워크마다 다른 "관례"(어떤 애노테이션이 진입점인지, 어떤 메소드가 SQL 이름을 받는지,
 * 어떤 상위 타입을 상속하면 서비스인지)를 코드가 아니라 이 파일에 적습니다.
 * 새 환경의 프로젝트를 분석하다 새 관례를 만나면 규칙 파일만 고치고 SEMANTIC 부터 다시 돌리면 됩니다.
 *
 * 규칙 하나하나는 값만 담는 작은 클래스입니다. 만든 뒤에는 바뀌지 않습니다.
 * 어떤 키를 쓸 수 있는지는 RuleSetParser 와 conf/rules/README.md 에 있습니다.
 * </pre>
 */
public class RuleSet {

	/** 묶음 이름. 파일 이름(확장자 뺀 것)과 같다. 프로젝트의 ruleSets 에 이 이름을 적는다 */
	public final String name;
	public final String description;
	/** 작은 값이 먼저 적용된다. "먼저 정한 계층은 뒤에서 바꾸지 않는다" 라서 순서가 결과를 바꿀 수 있다 */
	public final int order;
	/** true 면 모든 프로젝트에 적용한다. false 면 프로젝트의 ruleSets 에 이름을 적었을 때만 적용한다 */
	public final boolean autoApply;
	/** 읽은 파일 이름. 설정(application.yml)에서 온 옛 규칙이면 "application.yml" */
	public final String source;
	/** 파일 내용의 해시(앞 12글자). 리비전에 남겨서 "어떤 규칙으로 분석했나" 를 알 수 있게 한다 */
	public final String hash;

	public final List<HttpMappingRule> httpMappings;
	public final List<AnnotatedEndpointRule> annotatedEndpoints;
	public final List<SuperTypeEndpointRule> superTypeEndpoints;
	public final List<String> injectAnnotations;
	public final List<String> proxyAnnotations;
	public final List<LayerAnnotationRule> layerAnnotations;
	public final List<LayerSuperTypeRule> layerSuperTypes;
	public final List<LayerNameRule> layerNames;
	public final List<CallArgumentRule> callArguments;
	public final List<ScreenScript.CallRule> screenCalls;
	public final List<ScreenScript.CallRule> screenFields;
	public final List<ScreenScript.CallRule> screenOpens;
	public final List<ScreenMenu.Rule> screenMenus;

	RuleSet(Builder b) {
		this.name = b.name;
		this.description = b.description;
		this.order = b.order;
		this.autoApply = b.autoApply;
		this.source = b.source;
		this.hash = b.hash;
		this.httpMappings = Collections.unmodifiableList(b.httpMappings);
		this.annotatedEndpoints = Collections.unmodifiableList(b.annotatedEndpoints);
		this.superTypeEndpoints = Collections.unmodifiableList(b.superTypeEndpoints);
		this.injectAnnotations = Collections.unmodifiableList(b.injectAnnotations);
		this.proxyAnnotations = Collections.unmodifiableList(b.proxyAnnotations);
		this.layerAnnotations = Collections.unmodifiableList(b.layerAnnotations);
		this.layerSuperTypes = Collections.unmodifiableList(b.layerSuperTypes);
		this.layerNames = Collections.unmodifiableList(b.layerNames);
		this.callArguments = Collections.unmodifiableList(b.callArguments);
		this.screenCalls = Collections.unmodifiableList(b.screenCalls);
		this.screenFields = Collections.unmodifiableList(b.screenFields);
		this.screenOpens = Collections.unmodifiableList(b.screenOpens);
		this.screenMenus = Collections.unmodifiableList(b.screenMenus);
	}

	/** 이 묶음에 든 규칙 수. 리비전 기록과 화면에 보여 줄 때 쓴다 */
	public int ruleCount() {
		return httpMappings.size() + annotatedEndpoints.size() + superTypeEndpoints.size()
				+ (injectAnnotations.isEmpty() ? 0 : 1) + (proxyAnnotations.isEmpty() ? 0 : 1)
				+ layerAnnotations.size() + layerSuperTypes.size() + layerNames.size() + callArguments.size()
				+ screenCalls.size() + screenFields.size() + screenOpens.size() + screenMenus.size();
	}

	/** RuleSet 을 만드는 동안만 쓰는 그릇. RuleSetParser 와 옛 설정을 읽는 곳에서 씁니다 */
	static class Builder {
		String name;
		String description;
		int order = 500;
		boolean autoApply = true;
		String source;
		String hash;
		List<HttpMappingRule> httpMappings = new ArrayList<HttpMappingRule>();
		List<AnnotatedEndpointRule> annotatedEndpoints = new ArrayList<AnnotatedEndpointRule>();
		List<SuperTypeEndpointRule> superTypeEndpoints = new ArrayList<SuperTypeEndpointRule>();
		List<String> injectAnnotations = new ArrayList<String>();
		List<String> proxyAnnotations = new ArrayList<String>();
		List<LayerAnnotationRule> layerAnnotations = new ArrayList<LayerAnnotationRule>();
		List<LayerSuperTypeRule> layerSuperTypes = new ArrayList<LayerSuperTypeRule>();
		List<LayerNameRule> layerNames = new ArrayList<LayerNameRule>();
		List<CallArgumentRule> callArguments = new ArrayList<CallArgumentRule>();
		List<ScreenScript.CallRule> screenCalls = new ArrayList<ScreenScript.CallRule>();
		List<ScreenScript.CallRule> screenFields = new ArrayList<ScreenScript.CallRule>();
		List<ScreenScript.CallRule> screenOpens = new ArrayList<ScreenScript.CallRule>();
		List<ScreenMenu.Rule> screenMenus = new ArrayList<ScreenMenu.Rule>();

		RuleSet build() {
			return new RuleSet(this);
		}
	}

	/* ============================== 규칙 하나하나 ============================== */

	/**
	 * <pre>
	 * HTTP 진입점 규칙. 메소드에 붙은 애노테이션이 주소를 정하고, 클래스에 붙은 애노테이션의 주소가 앞에 붙습니다.
	 * 예(Spring): 클래스의 @RequestMapping("/order") + 메소드의 @GetMapping("/list") → GET /order/list
	 * </pre>
	 */
	public static class HttpMappingRule {
		/** 클래스에 붙어 주소 앞부분을 정하는 애노테이션 */
		public final List<String> classAnnotations;
		/** 주소가 적힌 속성 이름. 앞에 있는 것부터 본다 */
		public final List<String> pathAttributes;
		public final List<HttpMethodAnnotation> methodAnnotations;

		public HttpMappingRule(List<String> classAnnotations, List<String> pathAttributes, List<HttpMethodAnnotation> methodAnnotations) {
			this.classAnnotations = Collections.unmodifiableList(new ArrayList<String>(classAnnotations));
			this.pathAttributes = Collections.unmodifiableList(new ArrayList<String>(pathAttributes));
			this.methodAnnotations = Collections.unmodifiableList(new ArrayList<HttpMethodAnnotation>(methodAnnotations));
		}
	}

	/** 메소드에 붙는 HTTP 애노테이션 하나. HTTP 메소드는 정해져 있거나(httpMethod) 속성에 적혀 있다(methodAttribute) */
	public static class HttpMethodAnnotation {
		public final String annotation;
		/** 예: GetMapping → GET. 속성에서 읽는 애노테이션이면 null */
		public final String httpMethod;
		/** 예: RequestMapping 의 method 속성. 속성이 비어 있으면 ALL(모든 메소드)이다 */
		public final String methodAttribute;

		public HttpMethodAnnotation(String annotation, String httpMethod, String methodAttribute) {
			this.annotation = annotation;
			this.httpMethod = httpMethod;
			this.methodAttribute = methodAttribute;
		}
	}

	/** 애노테이션이 붙은 메소드를 진입점으로 올리는 규칙. 예: @Scheduled → SCHEDULED, @KafkaListener → LISTENER */
	public static class AnnotatedEndpointRule {
		public final String endpointType;
		public final List<String> annotations;
		/** 진입점의 path 자리에 넣을 속성 이름. 앞에 있는 것부터 본다(예: cron, fixedRate). 없으면 path 는 비어 있다 */
		public final List<String> pathAttributes;

		public AnnotatedEndpointRule(String endpointType, List<String> annotations, List<String> pathAttributes) {
			this.endpointType = endpointType;
			this.annotations = Collections.unmodifiableList(new ArrayList<String>(annotations));
			this.pathAttributes = Collections.unmodifiableList(new ArrayList<String>(pathAttributes));
		}
	}

	/** 이 상위 타입을 바로 상속(구현)한 클래스의 메소드를 진입점으로 올리는 규칙. 예: Runnable 의 run() → THREAD */
	public static class SuperTypeEndpointRule {
		public final String endpointType;
		public final List<String> superTypes;
		/** 메소드 이름 목록. 비어 있으면 methodPattern 으로 본다 */
		public final List<String> methods;
		/** 메소드 이름 정규식. methods 가 있으면 null */
		public final String methodPattern;
		/** 인자 수. null 이면 보지 않는다 */
		public final Integer paramCount;

		public SuperTypeEndpointRule(String endpointType, List<String> superTypes, List<String> methods, String methodPattern, Integer paramCount) {
			this.endpointType = endpointType;
			this.superTypes = Collections.unmodifiableList(new ArrayList<String>(superTypes));
			this.methods = Collections.unmodifiableList(new ArrayList<String>(methods));
			this.methodPattern = methodPattern;
			this.paramCount = paramCount;
		}
	}

	/** 애노테이션 → 계층(HIGH). 예: @Service → SERVICE */
	public static class LayerAnnotationRule {
		public final String layer;
		public final List<String> annotations;

		public LayerAnnotationRule(String layer, List<String> annotations) {
			this.layer = layer;
			this.annotations = Collections.unmodifiableList(new ArrayList<String>(annotations));
		}
	}

	/** 바로 위의 상위 타입 → 계층(MEDIUM). 예: HttpServlet 을 상속하면 CONTROLLER */
	public static class LayerSuperTypeRule {
		public final String layer;
		public final List<String> types;

		public LayerSuperTypeRule(String layer, List<String> types) {
			this.layer = layer;
			this.types = Collections.unmodifiableList(new ArrayList<String>(types));
		}
	}

	/** 클래스 이름(단순 이름) 정규식 → 계층(LOW). 예: ...DAO 로 끝나면 REPOSITORY */
	public static class LayerNameRule {
		public final String layer;
		/** PostgreSQL 정규식(~)으로 쓴다 */
		public final String pattern;

		public LayerNameRule(String layer, String pattern) {
			this.layer = layer;
			this.pattern = pattern;
		}
	}

	/**
	 * <pre>
	 * 호출 인자 규칙. "이 메소드(또는 생성자)의 n번째 인자로 넘긴 글자가 SQL 이름 / 화면 이름이다" 라는 관례입니다.
	 * 예: sqlSession.selectList("Order.list", vo) → statement Order.list 와 잇는다(EXECUTES_SQL)
	 *     new ModelAndView("order/list")          → order/list.jsp 와 잇는다(RENDERS)
	 *
	 * 글자로 적힌 값만 잇습니다. SQL 이름은 상수와 + 를 따라가 계산하고(ConstantEvaluator), 화면 이름은 따옴표 글자 하나만 봅니다.
	 * </pre>
	 */
	public static class CallArgumentRule {
		public static final String TARGET_SQL = "SQL";
		public static final String TARGET_VIEW = "VIEW";

		/** 어디와 잇나: SQL(statement) | VIEW(JSP 화면) */
		public final String target;
		/** 이 이름의 메소드 호출 */
		public final List<String> methods;
		/** 이 이름의 생성자 호출(new 타입(...)). 소스에 적힌 대로의 이름이라 보통 단순 이름이다 */
		public final List<String> constructors;
		/** 몇 번째 인자를 보나. 0 부터 센다 */
		public final int argIndex;
		/** 호출 대상의 타입. 비어 있으면 보지 않는다(이름만 맞으면 된다). 생성자 호출에는 쓰지 않는다 */
		public final List<String> receiverTypes;
		/** 계산한 값 앞뒤에 붙일 글자. 틀이 "{value}.jsp" 면 prefix 는 "", suffix 는 ".jsp" */
		public final String prefix;
		public final String suffix;
		/** 이 정규식에 맞는 값은 버린다(예: redirect:/... 는 화면이 아니다). 없으면 null */
		public final String exclude;

		public CallArgumentRule(String target, List<String> methods, List<String> constructors, int argIndex, List<String> receiverTypes
				, String prefix, String suffix, String exclude) {
			this.target = target;
			this.methods = Collections.unmodifiableList(new ArrayList<String>(methods));
			this.constructors = Collections.unmodifiableList(new ArrayList<String>(constructors));
			this.argIndex = argIndex;
			this.receiverTypes = Collections.unmodifiableList(new ArrayList<String>(receiverTypes));
			this.prefix = prefix;
			this.suffix = suffix;
			this.exclude = exclude;
		}
	}

}

package net.dstone.knowledge.analysis.pass08.semantic.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <pre>
 * 규칙 파일에 적힌 규칙 한 줄입니다. "코드에 이런 모양이 있으면 → 이런 뜻이다"를 담습니다.
 *
 * 모든 규칙은 자기가 어디서 왔는지(파일과 줄 번호)를 압니다. 분석 결과에 그 자리(ruleAt)를 함께 남겨서
 * "이 클래스가 왜 CONTROLLER 인가?"에 "common/spring.yml 5번째 줄 때문"이라고 답할 수 있게 합니다.
 *
 * 규칙 파일의 절과 이 클래스의 짝:
 *   layer:              Layer           → LayerResolver
 *   entry:              HttpEntry, AnnotationEntry, ExtendsEntry, XmlEntry → EntryFinder
 *   inject: / proxy:    Mark            → WiringMarker
 *   sql: / view:        CallArgument    → SqlLinker / ViewLinker
 *   jpa:                JpaQuery (그 밖의 jpa 값은 RuleFile 에 바로 있다) → JpaQueryReader, JavaSqlLinker
 *   screen:             ScreenScript.CallRule, ScreenMenu.Rule → ScreenLinker, ScreenMenuReader
 *
 * 만든 뒤에는 바뀌지 않습니다.
 * </pre>
 */
public abstract class Rule {

	/** 규칙 폴더 안의 경로. 예: common/spring.yml */
	public final String file;
	/** 파일 안의 줄 번호(1부터) */
	public final int line;
	public final Tier tier;
	/** 파일에 적힌 모양 그대로. 예: "@Service", "extends jef.application.dao.jdbc.JdbcDAO" */
	public final String text;

	protected Rule(Origin origin, String text) {
		this.file = origin.file;
		this.line = origin.line;
		this.tier = origin.tier;
		this.text = text;
	}

	/** 결과에 남기는 근거. 예: "common/spring.yml:5" */
	public String at() {
		return file + ":" + line;
	}

	@Override
	public String toString() {
		return text + " (" + at() + ")";
	}

	/** 규칙이 적힌 자리. 해석기가 만들어 규칙마다 넘깁니다 */
	public static class Origin {
		public final String file;
		public final int line;
		public final Tier tier;

		public Origin(String file, int line, Tier tier) {
			this.file = file;
			this.line = line;
			this.tier = tier;
		}

		public String at() {
			return file + ":" + line;
		}
	}

	static List<String> copy(List<String> values) {
		return Collections.unmodifiableList(new ArrayList<String>(values));
	}

	/* ============================== layer: 계층 ============================== */

	/**
	 * <pre>
	 * 계층 규칙. 세 가지 모양이 있고 모양마다 신뢰도가 다릅니다.
	 *   "@Service"                         애노테이션 → HIGH
	 *   extends jef...AbstractSubService   바로 위 상위 타입 → MEDIUM
	 *   name *Dao                          클래스 단순 이름(글롭) → LOW (짐작)
	 * </pre>
	 */
	public static class Layer extends Rule {
		public static final String BY_ANNOTATION = "ANNOTATION";
		public static final String BY_EXTENDS = "EXTENDS";
		public static final String BY_NAME = "NAME";

		public final String layer;
		/** ANNOTATION / EXTENDS / NAME */
		public final String by;
		/** 애노테이션 단순 이름, 상위 타입 전체 이름, 또는 이름 글롭(*Dao) */
		public final String value;
		/** by 가 NAME 일 때 글롭을 바꾼 PostgreSQL 정규식(~). 아니면 null */
		public final String nameRegex;

		public Layer(Origin origin, String text, String layer, String by, String value, String nameRegex) {
			super(origin, text);
			this.layer = layer;
			this.by = by;
			this.value = value;
			this.nameRegex = nameRegex;
		}

		/** 모양에 따른 신뢰도 */
		public String confidence() {
			return BY_ANNOTATION.equals(by) ? "HIGH" : BY_EXTENDS.equals(by) ? "MEDIUM" : "LOW";
		}
	}

	/* ============================== entry: 입구(진입점) ============================== */

	/**
	 * <pre>
	 * HTTP 진입점 규칙 한 덩어리. 클래스에 붙은 주소 + 메소드에 붙은 주소.
	 *   HTTP:
	 *     class: "@RequestMapping(value|path = URL)"
	 *     GET:   "@GetMapping(value|path = URL)"
	 *     ANY:   "@RequestMapping(value|path = URL, method = VERB)"
	 * </pre>
	 */
	public static class HttpEntry extends Rule {
		/** 클래스에 붙어 주소 앞부분을 정하는 애노테이션들 */
		public final List<HttpAnnotation> classAnnotations;
		/** 메소드에 붙어 주소를 정하는 애노테이션들 */
		public final List<HttpAnnotation> methodAnnotations;

		public HttpEntry(Origin origin, String text, List<HttpAnnotation> classAnnotations, List<HttpAnnotation> methodAnnotations) {
			super(origin, text);
			this.classAnnotations = Collections.unmodifiableList(new ArrayList<HttpAnnotation>(classAnnotations));
			this.methodAnnotations = Collections.unmodifiableList(new ArrayList<HttpAnnotation>(methodAnnotations));
		}

		public HttpAnnotation classAnnotation(String name) {
			return find(classAnnotations, name);
		}

		public HttpAnnotation methodAnnotation(String name) {
			return find(methodAnnotations, name);
		}

		private static HttpAnnotation find(List<HttpAnnotation> list, String name) {
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).annotation.equals(name)) {
					return list.get(i);
				}
			}
			return null;
		}

		/** 이 규칙이 보는 애노테이션 이름(메소드의 것 + 클래스의 것) */
		public List<String> annotationNames() {
			List<String> names = new ArrayList<String>();
			for (int i = 0; i < methodAnnotations.size(); i++) {
				if (!names.contains(methodAnnotations.get(i).annotation)) {
					names.add(methodAnnotations.get(i).annotation);
				}
			}
			for (int i = 0; i < classAnnotations.size(); i++) {
				if (!names.contains(classAnnotations.get(i).annotation)) {
					names.add(classAnnotations.get(i).annotation);
				}
			}
			return names;
		}
	}

	/** HTTP 진입점 규칙의 애노테이션 하나. "@GetMapping(value|path = URL)" */
	public static class HttpAnnotation extends Rule {
		public final String annotation;
		/** 주소가 적힌 속성(URL 자리). 앞에 있는 것부터 본다 */
		public final List<String> pathAttributes;
		/** 정해진 HTTP 메소드(GET ...). 속성에서 읽거나(verbAttribute) 클래스 애노테이션이면 null */
		public final String httpMethod;
		/** HTTP 메소드가 적힌 속성(VERB 자리). 비어 있으면 ALL. 없으면 null */
		public final String verbAttribute;

		public HttpAnnotation(Origin origin, String text, String annotation, List<String> pathAttributes, String httpMethod, String verbAttribute) {
			super(origin, text);
			this.annotation = annotation;
			this.pathAttributes = copy(pathAttributes);
			this.httpMethod = httpMethod;
			this.verbAttribute = verbAttribute;
		}
	}

	/** 애노테이션이 붙은 메소드 → 진입점. "@Scheduled(cron|fixedRate = KEY)" → SCHEDULED */
	public static class AnnotationEntry extends Rule {
		public final String endpointType;
		public final String annotation;
		/** 진입점의 path 자리에 넣을 속성(KEY 자리). 앞에 있는 것부터 본다. 없으면 path 는 비어 있다 */
		public final List<String> pathAttributes;

		public AnnotationEntry(Origin origin, String text, String endpointType, String annotation, List<String> pathAttributes) {
			super(origin, text);
			this.endpointType = endpointType;
			this.annotation = annotation;
			this.pathAttributes = copy(pathAttributes);
		}
	}

	/** 이 상위 타입을 바로 상속(구현)한 클래스의 메소드 → 진입점. "java.lang.Runnable#run()" → THREAD */
	public static class ExtendsEntry extends Rule {
		public final String endpointType;
		/** 상위 타입 전체 이름 */
		public final String superType;
		/** 메소드 이름. 글롭(perform*)이면 null 이고 methodRegex 를 본다 */
		public final String methodName;
		/** 메소드 이름 글롭을 바꾼 PostgreSQL 정규식. 이름이 정해져 있으면 null */
		public final String methodRegex;
		/** 인자 수. "(...)" 로 적어 보지 않으면 null */
		public final Integer paramCount;

		public ExtendsEntry(Origin origin, String text, String endpointType, String superType, String methodName, String methodRegex, Integer paramCount) {
			super(origin, text);
			this.endpointType = endpointType;
			this.superType = superType;
			this.methodName = methodName;
			this.methodRegex = methodRegex;
			this.paramCount = paramCount;
		}
	}

	/**
	 * <pre>
	 * 설정 XML 에 적힌 진입점.
	 *   - file: 'config/service-.*\.xml$'
	 *     xml: '&lt;service id="KEY" class="CLASS" method="METHOD"/&gt;'
	 * CLASS / METHOD 속성이 그 요소에 없으면 바깥 요소에서 찾습니다.
	 * </pre>
	 */
	public static class XmlEntry extends Rule {
		public final String endpointType;
		/** 파일 경로(프로젝트 안의 경로)에 대한 Java 정규식. find 로 본다 */
		public final String files;
		public final String element;
		/** KEY 자리의 속성 */
		public final String pathAttribute;
		/** CLASS 자리의 속성. 없으면 null */
		public final String classAttribute;
		/** METHOD 자리의 속성. 없으면 null */
		public final String methodAttribute;
		/** HTTP 진입점이면 HTTP 메소드(모르면 ALL). 다른 종류면 null */
		public final String httpMethod;
		/** 주소 값 앞뒤에 붙일 글자. path: "{KEY}.do" 면 "", ".do" */
		public final String prefix;
		public final String suffix;

		public XmlEntry(Origin origin, String text, String endpointType, String files, String element, String pathAttribute, String classAttribute
				, String methodAttribute, String httpMethod, String prefix, String suffix) {
			super(origin, text);
			this.endpointType = endpointType;
			this.files = files;
			this.element = element;
			this.pathAttribute = pathAttribute;
			this.classAttribute = classAttribute;
			this.methodAttribute = methodAttribute;
			this.httpMethod = httpMethod;
			this.prefix = prefix;
			this.suffix = suffix;
		}
	}

	/* ============================== inject: / proxy: ============================== */

	/** 애노테이션 하나로 표시만 하는 규칙. inject: 필드 → INJECTS 관계, proxy: 메소드 → proxy_related */
	public static class Mark extends Rule {
		public final String annotation;

		public Mark(Origin origin, String text, String annotation) {
			super(origin, text);
			this.annotation = annotation;
		}
	}

	/* ============================== sql: / view: ============================== */

	/**
	 * <pre>
	 * 호출의 n번째 인자로 넘긴 글자가 SQL 이름(sql:) / 화면 이름(view:)이라는 규칙.
	 *   _.selectList(SQL)                          아무 객체의 selectList 첫 인자
	 *   com.acme.fw.BaseDao#executeList(_, SQL)    BaseDao(하위 타입 포함)의 executeList 둘째 인자
	 *   new QueryProperty(SQL)                     생성자 호출의 첫 인자
	 * </pre>
	 */
	public static class CallArgument extends Rule {
		public static final String TARGET_SQL = "SQL";
		public static final String TARGET_VIEW = "VIEW";

		/** SQL | VIEW */
		public final String target;
		/** 메소드 이름. 생성자 규칙이면 null */
		public final String method;
		/** 생성자 타입 이름(소스에 적힌 대로라 보통 단순 이름). 메소드 규칙이면 null */
		public final String constructor;
		/** 호출 대상 타입(하위 타입 포함). "_." 로 적었으면 null(이름만 본다) */
		public final String receiverType;
		/** 몇 번째 인자인가(0부터) */
		public final int argIndex;
		/** 값 앞뒤에 붙일 글자. name: "Order.{SQL}" 면 "Order.", "" */
		public final String prefix;
		public final String suffix;
		/** 이 정규식에 맞는 값은 버린다. 없으면 null */
		public final String skip;

		public CallArgument(Origin origin, String text, String target, String method, String constructor, String receiverType, int argIndex
				, String prefix, String suffix, String skip) {
			super(origin, text);
			this.target = target;
			this.method = method;
			this.constructor = constructor;
			this.receiverType = receiverType;
			this.argIndex = argIndex;
			this.prefix = prefix;
			this.suffix = suffix;
			this.skip = skip;
		}

		/** 호출의 종류: 메소드 호출이면 CALL, 생성자면 CREATE(analysis_reference.ref_kind) */
		public String refKind() {
			return method != null ? "CALL" : "CREATE";
		}

		/** 호출 이름(메소드 이름이나 생성자 타입 이름) */
		public String callName() {
			return method != null ? method : constructor;
		}
	}

	/* ============================== jpa: ============================== */

	/**
	 * <pre>
	 * JPA 쿼리 애노테이션 규칙.
	 *   query:      "@Query(value = JPQL, nativeQuery = NATIVE)"           메소드에 붙는다. NATIVE 가 true 면 SQL
	 *   namedQuery: "@NamedQuery(name = NAME, query = JPQL) in @NamedQueries"   엔티티에 붙는다. in 뒤는 여럿을 묶는 애노테이션
	 *               "@NamedNativeQuery(name = NAME, query = SQL)"         JPQL 대신 SQL 이면 언제나 SQL 이다
	 * </pre>
	 */
	public static class JpaQuery extends Rule {
		public final String annotation;
		/** 쿼리 글이 든 속성(JPQL / SQL 자리) */
		public final String attribute;
		/** NATIVE 자리의 속성. 없으면 null */
		public final String nativeAttribute;
		/** SQL 자리로 적었으면 true(언제나 SQL) */
		public final boolean nativeQuery;
		/** NAME 자리의 속성(namedQuery). query 규칙은 null */
		public final String nameAttribute;
		/** "in @NamedQueries" 의 묶는 애노테이션. 없으면 null */
		public final String container;

		public JpaQuery(Origin origin, String text, String annotation, String attribute, String nativeAttribute, boolean nativeQuery
				, String nameAttribute, String container) {
			super(origin, text);
			this.annotation = annotation;
			this.attribute = attribute;
			this.nativeAttribute = nativeAttribute;
			this.nativeQuery = nativeQuery;
			this.nameAttribute = nameAttribute;
			this.container = container;
		}
	}

}

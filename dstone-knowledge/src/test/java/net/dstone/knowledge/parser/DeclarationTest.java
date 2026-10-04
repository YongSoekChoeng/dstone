package net.dstone.knowledge.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.dstone.knowledge.parser.model.AnnotationRow;
import net.dstone.knowledge.parser.model.FieldRow;
import net.dstone.knowledge.parser.model.FileDeclarations;
import net.dstone.knowledge.parser.model.MethodRow;
import net.dstone.knowledge.parser.model.ReferenceRow;
import net.dstone.knowledge.parser.model.TypeRow;

/**
 * DECLARE 단계의 판단 로직(문법 수준 탐지, 선언/참조 수집, Lombok 멤버 만들기)을 확인합니다.
 * DB나 Spring 없이 도는 테스트입니다.
 */
public class DeclarationTest {

	private static final String LEGACY_SRC = "src/test/resources/samples/legacy-app/WEB-INF/src/com/legacy/order/";

	private static final String[] LEGACY_FILES = {
		"vo/OrderVO.java", "util/DBUtil.java", "dao/OrderDAO.java", "service/OrderService.java"
		, "service/OrderServiceImpl.java", "web/OrderServlet.java", "batch/OrderBatchMain.java"
	};

	private final JavaSourceParser parser = new JavaSourceParser();

	private FileDeclarations collect(String source, long fileId) {
		JavaSourceParser.Result parsed = parser.parse(source, null);
		assertTrue(parsed.isSuccessful(), String.valueOf(parsed.error));
		return new DeclarationCollector("test", 1L, fileId, "src").collect(parsed.unit);
	}

	private String legacySource(String file) throws Exception {
		return new String(Files.readAllBytes(Paths.get(LEGACY_SRC + file)), Charset.forName("EUC-KR"));
	}

	@Test
	public void enum을_변수_이름으로_쓴_파일은_낮은_문법_수준으로_읽는다() throws Exception {
		// 최신 문법으로는 실패하고 1.4로 내려가서 성공해야 한다.
		JavaSourceParser.Result legacy = parser.parse(legacySource("service/OrderServiceImpl.java"), null);
		assertTrue(legacy.isSuccessful(), String.valueOf(legacy.error));
		assertEquals("1.4", legacy.languageLevel);

		// 걸리는 것이 없는 파일은 최신 문법으로 읽힌다.
		assertEquals("21", parser.parse(legacySource("vo/OrderVO.java"), null).languageLevel);

		// 프로젝트에 문법 수준을 지정해 두면 그것부터 시도한다.
		assertEquals("1.4", parser.parse(legacySource("vo/OrderVO.java"), "1.4").languageLevel);
		assertEquals("8", parser.parse("class A { java.util.function.Supplier<String> s = () -> \"a\"; }", "1.8").languageLevel);

		// 어느 수준으로도 읽히지 않으면 실패와 오류 내용을 돌려준다.
		JavaSourceParser.Result broken = parser.parse("class A { void f( { }", null);
		assertFalse(broken.isSuccessful());
		assertNotNull(broken.error);
	}

	@Test
	public void legacy_app의_선언_수가_README의_기대_값과_같다() throws Exception {
		int namedTypes = 0, anonymousTypes = 0, methods = 0, anonymousMethods = 0, constructors = 0, defaultConstructors = 0, fields = 0, superTypes = 0;
		Set<String> anonymousIds = new HashSet<String>();
		Map<String, Integer> references = new HashMap<String, Integer>();

		for (int f = 0; f < LEGACY_FILES.length; f++) {
			FileDeclarations declarations = collect(legacySource(LEGACY_FILES[f]), f + 1);
			assertTrue(declarations.getWarnings().isEmpty());
			for (TypeRow type : declarations.getTypes()) {
				if ("ANONYMOUS".equals(type.getKind())) {
					anonymousTypes++;
					anonymousIds.add(type.getSymbolId());
				} else {
					namedTypes++;
				}
			}
			for (MethodRow method : declarations.getMethods()) {
				if (method.getIsConstructor()) {
					if (method.getIsSynthetic()) {
						defaultConstructors++;
					} else {
						constructors++;
					}
				} else if (anonymousIds.contains(method.getOwnerSymbolId())) {
					anonymousMethods++;
				} else {
					methods++;
				}
			}
			fields += declarations.getFields().size();
			for (ReferenceRow reference : declarations.getReferences()) {
				Integer count = references.get(reference.getRefKind());
				references.put(reference.getRefKind(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
				if ("EXTENDS".equals(reference.getRefKind()) || "IMPLEMENTS".equals(reference.getRefKind())) {
					superTypes++;
				}
			}
		}

		assertEquals(8, namedTypes);
		assertEquals(1, anonymousTypes);
		assertEquals(19, methods);
		assertEquals(1, anonymousMethods);
		assertEquals(2, constructors);
		// 생성자를 적지 않은 클래스 5개(OrderVO, OrderDAO, OrderServiceImpl, OrderServlet, OrderBatchMain)에는 기본 생성자를 만들어 넣는다.
		assertEquals(5, defaultConstructors);
		assertEquals(8, fields);
		assertEquals(4, superTypes);
		assertEquals(Integer.valueOf(1), references.get("ANONYMOUS_SUPER"));
	}

	@Test
	public void 호출은_그것이_적힌_메소드에서_나가는_것으로_기록된다() throws Exception {
		FileDeclarations declarations = collect(legacySource("service/OrderServiceImpl.java"), 1);

		MethodRow findOrders = method(declarations, "findOrders(String)");
		MethodRow compare = method(declarations, "compare(Object,Object)");
		assertEquals("List", findOrders.getReturnType());

		// orderDAO.findByCustomer(...)와 Collections.sort(...)는 findOrders에서 나간다.
		assertNotNull(reference(declarations, findOrders.getMethodId(), "CALL", "findByCustomer"));
		ReferenceRow sort = reference(declarations, findOrders.getMethodId(), "CALL", "sort");
		assertEquals("Collections", sort.getScopeText());
		assertEquals(Integer.valueOf(2), sort.getArgCount());
		assertNotNull(reference(declarations, findOrders.getMethodId(), "CREATE", "Comparator"));

		// 익명 클래스 안의 getAmount() 호출은 바깥 메소드가 아니라 익명 클래스의 compare에서 나간다.
		assertNotNull(reference(declarations, compare.getMethodId(), "CALL", "getAmount"));
		assertNull(reference(declarations, findOrders.getMethodId(), "CALL", "getAmount"));

		// 이어진 호출 a.get().get()은 식의 시작 위치가 같다. 이름의 위치를 적으므로 서로 다른 칸이어야 한다.
		FileDeclarations chained = collect("class A { Object f(java.util.Optional<java.util.Optional<String>> a) { return a.get().get(); } }", 2);
		Set<Integer> columns = new HashSet<Integer>();
		for (ReferenceRow reference : chained.getReferences()) {
			if ("CALL".equals(reference.getRefKind())) {
				columns.add(reference.getColumnStart());
			}
		}
		assertEquals(2, columns.size());

		// 필드 초기값의 new OrderDAO()는 그 필드에서 나간다.
		FieldRow orderDAO = declarations.getFields().get(0);
		assertEquals("orderDAO", orderDAO.getName());
		assertEquals("new OrderDAO()", orderDAO.getInitializerSummary());
		assertNotNull(reference(declarations, orderDAO.getFieldId(), "CREATE", "OrderDAO"));
	}

	@Test
	public void ID는_리비전과_파일_번호가_달라도_같다() throws Exception {
		String source = legacySource("vo/OrderVO.java");
		JavaSourceParser.Result parsed = parser.parse(source, null);
		FileDeclarations a = new DeclarationCollector("p", 1L, 10L, "WEB-INF/src").collect(parsed.unit);
		FileDeclarations b = new DeclarationCollector("p", 2L, 99L, "WEB-INF/src").collect(parser.parse(source, null).unit);
		assertEquals(a.getTypes().get(0).getSymbolId(), b.getTypes().get(0).getSymbolId());
		assertEquals(a.getMethods().get(0).getMethodId(), b.getMethods().get(0).getMethodId());
		assertEquals(40, a.getTypes().get(0).getSymbolId().length());

		// 소스 루트가 다르면(같은 이름의 클래스가 다른 모듈에 또 있는 경우) ID가 달라야 한다.
		FileDeclarations c = new DeclarationCollector("p", 1L, 10L, "other/src").collect(parser.parse(source, null).unit);
		assertFalse(a.getTypes().get(0).getSymbolId().equals(c.getTypes().get(0).getSymbolId()));
	}

	@Test
	public void 최신_문법의_선언도_읽는다() {
		FileDeclarations declarations = collect(
				"package a.b;\n"
				+ "import org.springframework.web.bind.annotation.GetMapping;\n"
				+ "public class Outer {\n"
				+ "  public interface Api { int MAX = 10; String name(); default void hello() {} }\n"
				+ "  public enum Color { RED, GREEN { public String toString() { return \"g\"; } } }\n"
				+ "  public record Point(int x, int y) { public Point { if (x < 0) throw new IllegalArgumentException(); } }\n"
				+ "  @GetMapping(value = {\"/a\", \"/b\"}, produces = \"text/plain\")\n"
				+ "  public <T> java.util.List<T> find(java.util.Map<String, T> map, String... names) {\n"
				+ "    class Local { void run() { helper(); } }\n"
				+ "    names = java.util.Arrays.stream(names).map(String::trim).toArray(String[]::new);\n"
				+ "    return null;\n"
				+ "  }\n"
				+ "  void helper() {}\n"
				+ "}\n", 1);

		assertEquals("a.b.Outer.Api", type(declarations, "Api").getFqn());
		assertEquals("INTERFACE", type(declarations, "Api").getKind());
		assertEquals("ENUM", type(declarations, "Color").getKind());
		assertEquals("RECORD", type(declarations, "Point").getKind());
		assertEquals("a.b.Outer$1Local", type(declarations, "Local").getFqn());
		assertEquals("a.b.Outer.Color$1", type(declarations, "$1").getFqn());

		// 인터페이스 멤버는 적지 않아도 public이고, 몸통이 없으면 abstract다.
		assertTrue(method(declarations, "name()").getIsAbstract());
		assertFalse(method(declarations, "hello()").getIsAbstract());
		assertEquals("public", method(declarations, "name()").getVisibility());
		assertTrue(field(declarations, "MAX").getIsStatic());

		// enum 상수는 필드, record는 조회 메소드가 생긴다.
		assertEquals("Color", field(declarations, "GREEN").getType());
		assertEquals("RECORD_ACCESSOR", method(declarations, "x()").getSyntheticOrigin());
		assertFalse(method(declarations, "<init>(int,int)").getIsSynthetic());

		// 시그니처는 제네릭을 뺀 적힌 그대로의 타입
		MethodRow find = method(declarations, "find(java.util.Map,String...)");
		assertEquals("java.util.List<T>", find.getReturnType());
		assertNotNull(reference(declarations, find.getMethodId(), "METHOD_REF", "trim"));
		// 지역 클래스 안의 호출은 그 클래스의 메소드에서 나간다.
		assertNotNull(reference(declarations, method(declarations, "run()").getMethodId(), "CALL", "helper"));

		AnnotationRow mapping = declarations.getAnnotations().get(0);
		assertEquals("GetMapping", mapping.getAnnotationName());
		assertEquals("org.springframework.web.bind.annotation.GetMapping", mapping.getAnnotationFqn());
		assertEquals("{\"value\":[\"/a\",\"/b\"],\"produces\":\"text/plain\"}", mapping.getAttributesJson());
	}

	@Test
	public void Lombok이_만드는_멤버를_심볼로_넣는다() {
		FileDeclarations declarations = collect(
				"package a;\n"
				+ "import lombok.*;\n"
				+ "import lombok.extern.slf4j.Slf4j;\n"
				+ "@Getter @Setter @Slf4j @Builder @RequiredArgsConstructor\n"
				+ "public class Member {\n"
				+ "  private static int count;\n"
				+ "  private final String id;\n"
				+ "  private String name;\n"
				+ "  private boolean isActive;\n"
				+ "  @Getter(AccessLevel.NONE) private String secret;\n"
				+ "  public String getName() { return name == null ? \"\" : name; }\n"
				+ "}\n", 1);

		// 소스에 직접 적은 getName()은 그대로 두고 새로 만들지 않는다.
		assertFalse(method(declarations, "getName()").getIsSynthetic());
		assertEquals("LOMBOK_GETTER", method(declarations, "getId()").getSyntheticOrigin());
		assertEquals(Integer.valueOf(7), method(declarations, "getId()").getLineStart());
		// boolean 필드 isActive → isActive() / setActive(boolean)
		assertNotNull(method(declarations, "isActive()"));
		assertNotNull(method(declarations, "setActive(boolean)"));
		// final 필드에는 setter가 없고, static 필드와 AccessLevel.NONE인 필드에는 getter가 없다.
		assertNull(methodOrNull(declarations, "setId(String)"));
		assertNull(methodOrNull(declarations, "getCount()"));
		assertNull(methodOrNull(declarations, "getSecret()"));
		assertNotNull(method(declarations, "setSecret(String)"));

		// @RequiredArgsConstructor: 값을 넣지 않은 final 필드만 받는다. 생성자가 생겼으니 기본 생성자는 없다.
		assertEquals("LOMBOK_REQUIRED_ARGS_CONSTRUCTOR", method(declarations, "<init>(String)").getSyntheticOrigin());
		assertNull(methodOrNull(declarations, "<init>()"));

		// @Slf4j → log 필드, @Builder → builder()와 중첩 클래스 MemberBuilder
		assertEquals("org.slf4j.Logger", field(declarations, "log").getType());
		assertTrue(method(declarations, "builder()").getIsStatic());
		assertEquals("a.Member.MemberBuilder", type(declarations, "MemberBuilder").getFqn());
		assertNotNull(method(declarations, "build()"));

		// Lombok을 쓰지 않는 클래스에는 아무것도 만들지 않는다(기본 생성자만).
		FileDeclarations plain = collect("class Plain { private String name; }", 2);
		assertEquals(1, plain.getMethods().size());
		assertEquals("DEFAULT_CONSTRUCTOR", plain.getMethods().get(0).getSyntheticOrigin());
	}

	private TypeRow type(FileDeclarations declarations, String simpleName) {
		for (TypeRow type : declarations.getTypes()) {
			if (type.getSimpleName().equals(simpleName)) {
				return type;
			}
		}
		throw new AssertionError("타입 없음: " + simpleName);
	}

	private MethodRow methodOrNull(FileDeclarations declarations, String signature) {
		for (MethodRow method : declarations.getMethods()) {
			if (method.getSignature().equals(signature)) {
				return method;
			}
		}
		return null;
	}

	private MethodRow method(FileDeclarations declarations, String signature) {
		MethodRow method = methodOrNull(declarations, signature);
		if (method == null) {
			throw new AssertionError("메소드 없음: " + signature);
		}
		return method;
	}

	private FieldRow field(FileDeclarations declarations, String name) {
		for (FieldRow field : declarations.getFields()) {
			if (field.getName().equals(name)) {
				return field;
			}
		}
		throw new AssertionError("필드 없음: " + name);
	}

	private ReferenceRow reference(FileDeclarations declarations, String fromId, String refKind, String name) {
		List<ReferenceRow> references = declarations.getReferences();
		for (ReferenceRow reference : references) {
			if (reference.getFromId().equals(fromId) && reference.getRefKind().equals(refKind) && reference.getName().equals(name)) {
				return reference;
			}
		}
		return null;
	}

}

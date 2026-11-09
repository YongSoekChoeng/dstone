package net.dstone.knowledge.semantic.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * <pre>
 * 규칙 파일(conf/rules/*.yml)을 읽는 RuleSetParser 와, 묶음을 합치는 SemanticRules 를 확인합니다.
 * DB 나 Spring 없이 돕니다. 실제 conf/rules 폴더의 파일도 모두 읽어 봅니다(잘못 적으면 서버가 뜨지 않으므로).
 * </pre>
 */
public class RuleSetParserTest {

	private final RuleSetParser parser = new RuleSetParser();

	private RuleSet parse(String text, String fileName) {
		return parser.parse(text, fileName);
	}

	private RuleException parseFails(final String text, final String fileName) {
		return assertThrows(RuleException.class, new Executable() {
			@Override
			public void execute() {
				parser.parse(text, fileName);
			}
		});
	}

	@Test
	public void 규칙_파일을_읽으면_키마다_규칙이_된다() {
		String text = "name: sample\n"
				+ "order: 400\n"
				+ "autoApply: false\n"
				+ "endpoints:\n"
				+ "  http:\n"
				+ "    - classAnnotations: [MyMapping]\n"
				+ "      pathAttributes: [value]\n"
				+ "      methodAnnotations:\n"
				+ "        - { annotation: MyMapping, methodAttribute: method }\n"
				+ "        - { annotation: MyGet, httpMethod: GET }\n"
				+ "  annotations:\n"
				+ "    - { endpointType: batch_job, annotations: [MyJob], pathAttributes: [name] }\n"
				+ "  superTypes:\n"
				+ "    - { endpointType: TRANSACTION, superTypes: [com.fw.BaseHandler], methodPattern: '^handle' }\n"
				+ "layers:\n"
				+ "  annotations:\n"
				+ "    - { layer: service, annotations: MyService }\n"
				+ "  superTypes:\n"
				+ "    - { layer: REPOSITORY, types: [com.fw.BaseDao] }\n"
				+ "  names:\n"
				+ "    - { layer: CONTROLLER, pattern: 'M$' }\n"
				+ "callArguments:\n"
				+ "  - target: sql\n"
				+ "    methods: [execute]\n"
				+ "    argIndex: 1\n"
				+ "    receiverTypes: [com.fw.BaseDao]\n"
				+ "    template: 'Order.{value}'\n"
				+ "  - target: VIEW\n"
				+ "    methods: [forward]\n"
				+ "    exclude: '^redirect:'\n"
				+ "screens:\n"
				+ "  calls: [{ function: callTx, method: 'do{id}' }]\n"
				+ "  fields: [{ field: txId, method: 'do{id}' }]\n"
				+ "  opens: [{ function: openPage, screen: '{id}.xml' }]\n"
				+ "  menus: [{ nameKey: MENU_NM, pathKey: SRC_PATH }]\n";
		RuleSet set = parse(text, "sample.yml");

		assertEquals("sample", set.name);
		assertEquals(400, set.order);
		assertFalse(set.autoApply);
		assertEquals(12, set.hash.length());
		assertEquals(Arrays.asList("MyMapping"), set.httpMappings.get(0).classAnnotations);
		assertEquals("GET", set.httpMappings.get(0).methodAnnotations.get(1).httpMethod);
		assertEquals("method", set.httpMappings.get(0).methodAnnotations.get(0).methodAttribute);
		// 진입점 종류와 계층은 대문자로 맞춘다. 목록 대신 하나만 적어도 된다(annotations: MyService).
		assertEquals("BATCH_JOB", set.annotatedEndpoints.get(0).endpointType);
		assertEquals("SERVICE", set.layerAnnotations.get(0).layer);
		assertEquals(Arrays.asList("MyService"), set.layerAnnotations.get(0).annotations);
		assertEquals("^handle", set.superTypeEndpoints.get(0).methodPattern);
		assertTrue(set.superTypeEndpoints.get(0).methods.isEmpty());
		assertNull(set.superTypeEndpoints.get(0).paramCount);

		RuleSet.CallArgumentRule sql = set.callArguments.get(0);
		assertEquals("SQL", sql.target);
		assertEquals(1, sql.argIndex);
		assertEquals("Order.", sql.prefix);
		assertEquals("", sql.suffix);
		assertEquals(Arrays.asList("com.fw.BaseDao"), sql.receiverTypes);
		// 화면 규칙의 틀을 적지 않으면 {value}.jsp 다.
		RuleSet.CallArgumentRule view = set.callArguments.get(1);
		assertEquals("", view.prefix);
		assertEquals(".jsp", view.suffix);
		assertEquals("^redirect:", view.exclude);

		assertEquals("callTx", set.screenCalls.get(0).getFunction());
		assertEquals(0, set.screenCalls.get(0).getArgIndex());
		assertEquals(1, parse("screens:\n  opens: [{ function: openPage, screen: '{id}.xml', argIndex: 1 }]\n", "x.yml").screenOpens.get(0).getArgIndex());
		assertTrue(set.screenFields.get(0).isField());
		assertEquals("JIMC1000G.xml", set.screenOpens.get(0).fill("JIMC1000G"));
		assertEquals("SRC_PATH", set.screenMenus.get(0).getPathKey());
		assertEquals(12, set.ruleCount());
	}

	@Test
	public void 모르는_키는_오타로_보고_자리를_알려_준다() {
		RuleException e = parseFails("callArguments:\n  - target: SQL\n    method: [execute]\n", "x.yml");
		assertEquals("callArguments[0].method", e.getPath());
		assertTrue(e.getMessage().contains("모르는 키"), e.getMessage());
	}

	@Test
	public void 잘못된_값은_서버를_띄우기_전에_막는다() {
		// 없는 계층
		assertEquals("layers.annotations[0].layer", parseFails("layers:\n  annotations:\n    - { layer: SERVCE, annotations: [S] }\n", "x.yml").getPath());
		// 틀린 정규식
		assertEquals("layers.names[0].pattern", parseFails("layers:\n  names:\n    - { layer: SERVICE, pattern: '(Service' }\n", "x.yml").getPath());
		// {value} 가 없는 틀
		assertEquals("callArguments[0].template", parseFails("callArguments:\n  - { target: SQL, methods: [q], template: 'Order.' }\n", "x.yml").getPath());
		// argIndex 범위
		assertEquals("callArguments[0].argIndex", parseFails("callArguments:\n  - { target: SQL, methods: [q], argIndex: 10 }\n", "x.yml").getPath());
		// 생성자에는 receiverTypes 를 쓰지 않는다
		assertEquals("callArguments[0].receiverTypes", parseFails("callArguments:\n  - { target: SQL, constructors: [Q], receiverTypes: [a.B] }\n", "x.yml").getPath());
		// httpMethod 와 methodAttribute 는 하나만
		parseFails("endpoints:\n  http:\n    - methodAnnotations: [{ annotation: A, httpMethod: GET, methodAttribute: m }]\n      pathAttributes: [value]\n", "x.yml");
		// {id} 가 없는 화면 규칙
		parseFails("screens:\n  calls: [{ function: f, method: perform }]\n", "x.yml");
		// 애노테이션에 @ 를 붙였다
		parseFails("injects:\n  annotations: ['@Autowired']\n", "x.yml");
		// 묶음 이름은 파일 이름과 같아야 한다
		assertEquals("name", parseFails("name: other\n", "x.yml").getPath());
		// YAML 문법 오류
		parseFails("layers: [\n", "x.yml");
	}

	@Test
	public void 두_묶음이_같은_애노테이션으로_진입점을_만들면_합칠_때_막는다() {
		final RuleSet a = parse("endpoints:\n  annotations:\n    - { endpointType: SCHEDULED, annotations: [Scheduled] }\n", "a.yml");
		final RuleSet b = parse("endpoints:\n  annotations:\n    - { endpointType: SCHEDULED, annotations: [Scheduled] }\n", "b.yml");
		assertThrows(RuleException.class, new Executable() {
			@Override
			public void execute() {
				new SemanticRules(Arrays.asList(a, b));
			}
		});
	}

	@Test
	public void 합칠_때_목록_규칙은_겹치는_것을_하나만_남기고_화면_필드_규칙은_호출_규칙_뒤에_둔다() {
		RuleSet a = parse("injects:\n  annotations: [Autowired, Inject]\n"
				+ "screens:\n  fields: [{ field: tranId, method: 'perform{id}' }]\n  calls: [{ function: f1, method: 'perform{id}' }]\n", "a.yml");
		RuleSet b = parse("injects:\n  annotations: [Inject, Resource]\n"
				+ "screens:\n  calls: [{ function: f1, method: 'perform{id}' }, { function: f2, method: 'perform{id}' }]\n", "b.yml");
		SemanticRules rules = new SemanticRules(Arrays.asList(a, b));
		assertEquals(Arrays.asList("Autowired", "Inject", "Resource"), rules.getInjectAnnotations());
		List<String> functions = new ArrayList<String>();
		for (int i = 0; i < rules.getScreenCalls().size(); i++) {
			functions.add(rules.getScreenCalls().get(i).getFunction());
		}
		assertEquals(Arrays.asList("f1", "f2", "tranId"), functions);
	}

	@Test
	public void conf_rules_폴더의_규칙_파일은_모두_올바르고_함께_쓸_수_있다() throws Exception {
		File dir = new File("conf/rules");
		assertTrue(dir.isDirectory(), "conf/rules 가 없습니다: " + dir.getAbsolutePath());
		List<RuleSet> sets = new ArrayList<RuleSet>();
		readAll(dir, "", sets);
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < sets.size(); i++) {
			names.add(sets.get(i).name);
		}
		// 프로젝트에 따른 규칙은 하위 폴더에 있고, 묶음 이름은 폴더를 포함한 경로다.
		assertTrue(names.contains("hi-japan/frontend"), String.valueOf(names));
		assertTrue(names.contains("hi-japan/backend"), String.valueOf(names));
		// 합칠 때 겹치는 진입점 애노테이션이 없어야 한다.
		SemanticRules rules = new SemanticRules(sets);
		// 기본 규칙이 그대로 옮겨졌는지 대표 값만 본다(정확한 전후 비교는 실제 리비전으로 했다).
		assertTrue(rules.getInjectAnnotations().contains("Autowired"));
		assertEquals(1, rules.getHttpMappings().size());
		assertFalse(rules.getSqlArguments().isEmpty());
		assertFalse(rules.getViewArguments().isEmpty());
		assertFalse(rules.getLayerNames().isEmpty());
	}

	/** 폴더 아래의 *.yml 을 규칙 폴더 안의 경로를 이름으로 읽는다(SemanticRuleRegistry 와 같은 방식) */
	private void readAll(File dir, String prefix, List<RuleSet> sets) throws Exception {
		File[] files = dir.listFiles();
		for (int i = 0; i < files.length; i++) {
			if (files[i].isDirectory()) {
				readAll(files[i], prefix + files[i].getName() + "/", sets);
			} else if (files[i].getName().endsWith(".yml")) {
				// 대응표(idMap)를 쓰는 규칙이 있을 수 있어서 규칙 폴더를 아는 파서로 읽는다.
				sets.add(new RuleSetParser(new File("conf/rules")).parse(new String(Files.readAllBytes(files[i].toPath()), StandardCharsets.UTF_8)
						, prefix + files[i].getName()));
			}
		}
	}

	@Test
	public void 하위_폴더의_묶음은_경로가_이름이고_폴더_이름으로_고를_수_있다() {
		// name 을 적지 않으면 경로가 이름이다. 적었으면 경로와 같아야 한다.
		assertEquals("hi-japan/frontend", parse("order: 500\n", "hi-japan/frontend.yml").name);
		assertEquals("hi-japan/frontend", parse("name: hi-japan/frontend\n", "hi-japan/frontend.yml").name);
		assertEquals("name", parseFails("name: frontend\n", "hi-japan/frontend.yml").getPath());
		// ruleSets 에 폴더 이름을 적으면 그 아래 묶음을 모두 고른다. 이름 앞부분만 같은 다른 폴더는 고르지 않는다.
		assertTrue(SemanticRuleRegistry.refersTo("hi-japan", "hi-japan/frontend"));
		assertTrue(SemanticRuleRegistry.refersTo("hi-japan/backend", "hi-japan/backend"));
		assertFalse(SemanticRuleRegistry.refersTo("hi-japan", "hi-japan-batch/backend"));
		assertFalse(SemanticRuleRegistry.refersTo("hi-japan/front", "hi-japan/frontend"));
	}


	@Test
	public void 하위_폴더의_묶음은_기본으로_그_폴더의_프로젝트에만_쓴다() {
		// 바로 아래 묶음은 모든 프로젝트에, 하위 폴더 묶음은 기본으로 쓰지 않는다. 적으면 적은 대로.
		assertTrue(parse("order: 200\n", "spring.yml").autoApply);
		assertFalse(parse("order: 500\n", "order-system/backend.yml").autoApply);
		assertTrue(parse("autoApply: true\n", "order-system/backend.yml").autoApply);
		assertFalse(parse("autoApply: false\n", "spring.yml").autoApply);
		// 첫 마디가 프로젝트 ID 와 같으면(대소문자 무시) 그 프로젝트의 묶음이다.
		assertTrue(SemanticRuleRegistry.ownedBy("hi-japan/frontend", "HI-Japan"));
		assertTrue(SemanticRuleRegistry.ownedBy("order-system/sub/backend", "order-system"));
		assertFalse(SemanticRuleRegistry.ownedBy("hi-japan/frontend", "HI-Japan-batch"));
		assertFalse(SemanticRuleRegistry.ownedBy("spring", "spring"));
		assertFalse(SemanticRuleRegistry.ownedBy("hi-japan/frontend", null));
	}

	@Test
	public void 화면_규칙의_ID_다듬기를_읽고_잘못_적으면_막는다() throws Exception {
		RuleSet set = parse("screens:\n"
				+ "  calls:\n"
				+ "    - { function: callTx, method: '{id}', idPattern: '^TX_([A-Z]+)_([A-Z]+)$', idReplace: '$1$2', idCase: lower_first }\n", "a.yml");
		assertEquals("oRDLIST", set.screenCalls.get(0).methodNameOf("TX_ORD_LIST"));
		assertNull(set.screenCalls.get(0).methodNameOf("ORD_LIST"));
		assertEquals("screens.calls[0].idReplace", parseFails("screens:\n  calls:\n    - { function: f, method: '{id}', idReplace: '$1' }\n", "a.yml").getPath());
		assertEquals("screens.calls[0].idReplace", parseFails("screens:\n  calls:\n    - { function: f, method: '{id}', idPattern: '^(a)$', idReplace: '$2' }\n", "a.yml").getPath());
		assertEquals("screens.calls[0].idCase", parseFails("screens:\n  calls:\n    - { function: f, method: '{id}', idCase: TITLE }\n", "a.yml").getPath());
		// 규칙 폴더를 모르는 파서는 대응표를 읽을 수 없다.
		assertEquals("screens.opens[0].idMap", parseFails("screens:\n  opens:\n    - { function: f, screen: '{id}.xfdl', idMap: m.csv }\n", "a.yml").getPath());

		// 규칙 폴더 안의 대응표를 읽는다. 폴더 밖은 막는다. 대응표 내용도 해시에 들어간다.
		File dir = Files.createTempDirectory("rules").toFile();
		File map = new File(dir, "tx.csv");
		Files.write(map.toPath(), "# 거래 ID,클래스.메소드\nORD001,OrderController.list\nORD002 = save\n".getBytes(StandardCharsets.UTF_8));
		RuleSetParser withDir = new RuleSetParser(dir);
		String text = "screens:\n  calls:\n    - { function: callTx, method: '{id}', idMap: tx.csv }\n";
		RuleSet mapped = withDir.parse(text, "a.yml");
		assertEquals("OrderController.list", mapped.screenCalls.get(0).methodNameOf("ORD001"));
		assertEquals("save", mapped.screenCalls.get(0).methodNameOf("ORD002"));
		assertNull(mapped.screenCalls.get(0).methodNameOf("ORD003"));
		String before = mapped.hash;
		Files.write(map.toPath(), "ORD001,OrderController.find\n".getBytes(StandardCharsets.UTF_8));
		assertFalse(before.equals(withDir.parse(text, "a.yml").hash));
		final RuleSetParser outside = withDir;
		RuleException escape = assertThrows(RuleException.class, new Executable() {
			@Override
			public void execute() {
				outside.parse("screens:\n  calls:\n    - { function: f, method: '{id}', idMap: ../x.csv }\n", "a.yml");
			}
		});
		assertEquals("screens.calls[0].idMap", escape.getPath());
	}

	@Test
	public void 설정_XML_진입점_규칙을_읽는다() {
		RuleSet set = parse("endpoints:\n"
				+ "  xml:\n"
				+ "    - { endpointType: TRANSACTION, files: 'service-config\\.xml$', element: service, pathAttribute: id,"
				+ " classAttribute: class, methodAttribute: method }\n"
				+ "    - { endpointType: HTTP, files: 'routes\\.xml$', element: route, pathAttribute: url, pathTemplate: '{value}.do' }\n", "a.yml");
		assertEquals(2, set.xmlEndpoints.size());
		RuleSet.XmlEndpointRule first = set.xmlEndpoints.get(0);
		assertEquals("service", first.element);
		assertEquals("class", first.classAttribute);
		assertNull(first.httpMethod);
		RuleSet.XmlEndpointRule second = set.xmlEndpoints.get(1);
		assertEquals("ALL", second.httpMethod);
		assertEquals(".do", second.suffix);
		assertNull(second.methodAttribute);
		assertEquals(2, new SemanticRules(Arrays.asList(set)).getXmlEndpoints().size());
		assertEquals("endpoints.xml[0].files", parseFails("endpoints:\n  xml:\n    - { endpointType: TX, files: '(', element: a, pathAttribute: b }\n", "a.yml").getPath());
		assertEquals("endpoints.xml[0].element", parseFails("endpoints:\n  xml:\n    - { endpointType: TX, files: 'a', element: 'x:y', pathAttribute: b }\n", "a.yml").getPath());
		assertEquals("endpoints.xml[0].pathTemplate", parseFails("endpoints:\n  xml:\n    - { endpointType: TX, files: 'a', element: a, pathAttribute: b, pathTemplate: '.do' }\n", "a.yml").getPath());
	}

}

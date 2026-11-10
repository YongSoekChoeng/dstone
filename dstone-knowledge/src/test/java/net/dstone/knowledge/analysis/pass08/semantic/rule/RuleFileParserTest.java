package net.dstone.knowledge.analysis.pass08.semantic.rule;

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

import net.dstone.knowledge.analysis.pass08.semantic.screen.ScreenScript;

/**
 * <pre>
 * 규칙 파일을 읽는 RuleFileParser 와, 파일들을 합쳐 우선순위를 정하는 ProjectRules, 파일을 고르는 RuleFiles 를 확인합니다.
 * DB 나 Spring 없이 돕니다. 실제 conf/rules 폴더의 파일도 모두 읽어 봅니다(잘못 적으면 서버가 뜨지 않으므로).
 * </pre>
 */
public class RuleFileParserTest {

	private final RuleFileParser parser = new RuleFileParser();

	private RuleFile parse(String text, String path) {
		return parser.parse(text, path);
	}

	private RuleException parseFails(final String text, final String path) {
		return assertThrows(RuleException.class, new Executable() {
			@Override
			public void execute() {
				parser.parse(text, path);
			}
		});
	}

	@Test
	public void 코드_모양으로_적은_규칙이_절마다_규칙이_된다() {
		String text = "description: 주문 시스템\n"
				+ "layer:\n"
				+ "  SERVICE: [\"@MyService\", extends com.fw.BaseService, name *Biz]\n"
				+ "entry:\n"
				+ "  HTTP:\n"
				+ "    class: \"@MyMapping(value = URL)\"\n"
				+ "    ANY: \"@MyMapping(value = URL, method = VERB)\"\n"
				+ "    GET: \"@MyGet(value|path = URL)\"\n"
				+ "  BATCH_JOB: \"@MyJob(name = KEY)\"\n"
				+ "  TRANSACTION: com.fw.BaseHandler#handle*(...)\n"
				+ "inject: \"@MyInject\"\n"
				+ "proxy: [\"@MyTx\"]\n"
				+ "sql:\n"
				+ "  - com.fw.BaseDao#execute(_, SQL)\n"
				+ "  - { call: new Query(SQL), name: 'Order.{SQL}' }\n"
				+ "view:\n"
				+ "  - { call: _.forward(VIEW), skip: '^redirect:' }\n"
				+ "screen:\n"
				+ "  - { call: 'lib.callTx(_, ID)', to: 'do{ID}' }\n"
				+ "  - { field: txId, to: 'do{ID}' }\n"
				+ "  - { open: openPage(ID), to: '{ID}.xml' }\n"
				+ "  - { menu: { name: MENU_NM, path: SRC_PATH } }\n";
		RuleFile file = parse(text, "projects/order-system/backend.yml");

		assertEquals("projects/order-system/backend", file.name);
		assertEquals(Tier.PROJECT, file.tier);
		assertEquals("order-system", file.projectFolder);
		assertEquals("주문 시스템", file.description);
		assertEquals(12, file.getHash().length());

		assertEquals(3, file.layers.size());
		assertEquals(Rule.Layer.BY_ANNOTATION, file.layers.get(0).by);
		assertEquals("MyService", file.layers.get(0).value);
		assertEquals("HIGH", file.layers.get(0).confidence());
		assertEquals("com.fw.BaseService", file.layers.get(1).value);
		assertEquals("MEDIUM", file.layers.get(1).confidence());
		assertEquals("^.*Biz$", file.layers.get(2).nameRegex);
		assertEquals("projects/order-system/backend.yml:3", file.layers.get(0).at());

		Rule.HttpEntry http = file.httpEntries.get(0);
		assertEquals(Arrays.asList("value"), http.classAnnotation("MyMapping").pathAttributes);
		assertEquals("method", http.methodAnnotation("MyMapping").verbAttribute);
		assertEquals("GET", http.methodAnnotation("MyGet").httpMethod);
		assertEquals(Arrays.asList("value", "path"), http.methodAnnotation("MyGet").pathAttributes);

		assertEquals("BATCH_JOB", file.annotationEntries.get(0).endpointType);
		assertEquals(Arrays.asList("name"), file.annotationEntries.get(0).pathAttributes);
		Rule.ExtendsEntry handler = file.extendsEntries.get(0);
		assertEquals("com.fw.BaseHandler", handler.superType);
		assertNull(handler.methodName);
		assertEquals("^handle.*$", handler.methodRegex);
		assertNull(handler.paramCount);

		assertEquals("MyInject", file.injects.get(0).annotation);
		assertEquals("MyTx", file.proxies.get(0).annotation);

		Rule.CallArgument execute = file.sqlCalls.get(0);
		assertEquals("execute", execute.method);
		assertEquals("com.fw.BaseDao", execute.receiverType);
		assertEquals(1, execute.argIndex);
		Rule.CallArgument query = file.sqlCalls.get(1);
		assertEquals("Query", query.constructor);
		assertEquals("Order.", query.prefix);
		assertEquals("", query.suffix);
		Rule.CallArgument forward = file.viewCalls.get(0);
		assertEquals(".jsp", forward.suffix);
		assertEquals("^redirect:", forward.skip);

		// 화면 함수는 마지막 이름으로 찾는다. ID 자리가 argIndex 가 된다
		ScreenScript.CallRule call = file.screenCalls.get(0);
		assertEquals("callTx", call.getFunction());
		assertEquals(1, call.getArgIndex());
		assertEquals("do{id}", call.getMethodTemplate());
		assertTrue(call.getAt().startsWith("projects/order-system/backend.yml:"));
		assertTrue(file.screenFields.get(0).isField());
		assertEquals("{id}.xml", file.screenOpens.get(0).getMethodTemplate());
		assertEquals("MENU_NM", file.screenMenus.get(0).getNameKey());
	}

	@Test
	public void 상위_타입_진입점의_괄호는_인자_수다() {
		RuleFile file = parse("entry:\n  THREAD:\n    - java.lang.Runnable#run()\n    - com.x.Worker#work(_, _)\n", "common/java.yml");
		assertEquals(Integer.valueOf(0), file.extendsEntries.get(0).paramCount);
		assertEquals("run", file.extendsEntries.get(0).methodName);
		assertEquals(Integer.valueOf(2), file.extendsEntries.get(1).paramCount);
		// [ ] 안에 쉼표가 든 호출을 적으면 YAML 이 쉼표에서 나눈다. 고치는 법을 알려 준다
		assertTrue(parseFails("sql: [com.x.Dao#run(_, SQL)]\n", "common/x.yml").getMessage().contains("괄호가 닫히지 않았습니다"));
	}

	@Test
	public void 잘못_적으면_파일과_줄_번호와_예를_알려_준다() {
		RuleException e = parseFails("layer:\n  SERVCE: \"@Service\"\n", "common/x.yml");
		assertEquals(2, e.getLine());
		assertTrue(e.getMessage().contains("common/x.yml 2번째 줄"), e.getMessage());
		assertTrue(e.getMessage().contains("SERVCE"), e.getMessage());

		// 모양이 틀린 것
		assertTrue(parseFails("layer:\n  SERVICE: Service\n", "common/x.yml").getMessage().contains("세 가지 모양"));
		assertTrue(parseFails("layer:\n  SERVICE: extends Service\n", "common/x.yml").getMessage().contains("전체 이름"));
		assertTrue(parseFails("sql: [ selectList(SQL) ]\n", "common/x.yml").getMessage().contains("_.이름"));
		assertTrue(parseFails("sql: [ _.selectList(x) ]\n", "common/x.yml").getMessage().contains("자리"));
		assertTrue(parseFails("sql: [ _.selectList(_) ]\n", "common/x.yml").getMessage().contains("SQL 자리를 적어야"));
		assertTrue(parseFails("entry:\n  SCHEDULED: \"@Scheduled(cron = URL)\"\n", "common/x.yml").getMessage().contains("KEY"));
		assertTrue(parseFails("entry:\n  HTTP: { GET: \"@GetMapping\" }\n", "common/x.yml").getMessage().contains("URL 자리"));
		assertTrue(parseFails("entry:\n  JOB: { class: \"@X(value = URL)\" }\n", "common/x.yml").getMessage().contains("HTTP 아래"));
		assertTrue(parseFails("layer:\n  SERVICE: \"@org.fw.Service\"\n", "common/x.yml").getMessage().contains("단순 이름"));
		assertTrue(parseFails("screen:\n  - { call: callTx(ID), to: 'do' }\n", "common/x.yml").getMessage().contains("{ID}"));
		assertTrue(parseFails("view:\n  - { call: _.forward(VIEW), skip: '[' }\n", "common/x.yml").getMessage().contains("정규식"));
		// @ 로 시작하는 값을 따옴표 없이 적으면 YAML 이 틀린다. 고치는 법을 알려 준다
		assertTrue(parseFails("layer:\n  SERVICE: @Service\n", "common/x.yml").getMessage().contains("따옴표"));
		// 모르는 키
		assertTrue(parseFails("lyer:\n  SERVICE: \"@Service\"\n", "common/x.yml").getMessage().contains("모르는 키"));
	}

	@Test
	public void 옛_형식의_키는_새_형식을_알려_준다() {
		assertTrue(parseFails("order: 500\n", "common/x.yml").getMessage().contains("폴더가 정합니다"));
		assertTrue(parseFails("callArguments: []\n", "common/x.yml").getMessage().contains("sql / view"));
		assertTrue(parseFails("layers: {}\n", "common/x.yml").getMessage().contains("layer 로"));
	}

	@Test
	public void 폴더가_우선순위를_정하고_다른_자리의_파일은_막는다() {
		assertEquals(Tier.COMMON, parse("", "common/spring.yml").tier);
		assertEquals(Tier.GUESS, parse("", "guess/default.yml").tier);
		assertEquals(Tier.PROJECT, parse("", "projects/hi-japan/backend.yml").tier);
		assertTrue(parseFails("", "spring.yml").getMessage().contains("common/, guess/, projects/"));
		assertTrue(parseFails("", "projects/backend.yml").getMessage().contains("common/, guess/, projects/"));
		assertTrue(parseFails("", "other/spring.yml").getMessage().contains("common/, guess/, projects/"));
	}

	@Test
	public void 계층은_프로젝트_공통_짐작_순서이고_같은_단계에서는_애노테이션_상속_이름_순서다() {
		RuleFile guess = parse("layer:\n  COMPONENT: \"@Component\"\n  SERVICE: name *Biz\n", "guess/default.yml");
		RuleFile common = parse("layer:\n  CONTROLLER: [name *Act, extends javax.servlet.http.HttpServlet, \"@Controller\"]\n", "common/spring.yml");
		RuleFile project = parse("layer:\n  CONTROLLER: extends com.acme.BaseAction\n", "projects/acme/backend.yml");
		List<RuleFile> files = new ArrayList<RuleFile>(Arrays.asList(guess, common, project));
		List<Rule.Layer> layers = new ProjectRules(files).layers();
		List<String> order = new ArrayList<String>();
		for (int i = 0; i < layers.size(); i++) {
			order.add(layers.get(i).text);
		}
		assertEquals(Arrays.asList("extends com.acme.BaseAction", "@Controller", "extends javax.servlet.http.HttpServlet", "name *Act"
				, "@Component", "name *Biz"), order);
	}

	@Test
	public void 같은_애노테이션으로_진입점을_두_번_만들면_두_자리를_알려_준다() {
		final RuleFile a = parse("entry:\n  SCHEDULED: \"@Scheduled(cron = KEY)\"\n", "common/spring.yml");
		final RuleFile b = parse("entry:\n  SCHEDULED: \"@Scheduled\"\n", "projects/x/backend.yml");
		RuleException e = assertThrows(RuleException.class, new Executable() {
			@Override
			public void execute() {
				new ProjectRules(Arrays.asList(a, b));
			}
		});
		assertTrue(e.getMessage().contains("common/spring.yml"), e.getMessage());
		assertTrue(e.getMessage().contains("projects/x/backend.yml"), e.getMessage());
	}

	@Test
	public void 합칠_때_겹치는_목록은_하나만_남기고_화면_필드_규칙은_호출_규칙_뒤에_둔다() {
		RuleFile a = parse("inject: [\"@Autowired\"]\nscreen:\n  - { field: tranId, to: 'perform{ID}' }\n", "projects/a/frontend.yml");
		RuleFile b = parse("inject: [\"@Autowired\", \"@Inject\"]\nscreen:\n  - { call: doRequest(ID), to: 'perform{ID}' }\n", "common/screen.yml");
		ProjectRules rules = new ProjectRules(Arrays.asList(a, b));
		assertEquals(2, rules.injects().size());
		assertEquals("projects/a/frontend.yml:1", rules.injects().get(0).at());
		List<ScreenScript.CallRule> calls = rules.screenCalls();
		assertFalse(calls.get(0).isField());
		assertTrue(calls.get(1).isField());
	}

	@Test
	public void 프로젝트_규칙은_그_프로젝트와_빌려_쓰는_프로젝트에만_쓴다() {
		RuleFile hiJapan = parse("", "projects/hi-japan/backend.yml");
		RuleFile common = parse("", "common/spring.yml");
		List<String> none = new ArrayList<String>();
		assertTrue(RuleFiles.applies(common, "hen", none));
		assertTrue(RuleFiles.applies(hiJapan, "HI-Japan", none));
		assertFalse(RuleFiles.applies(hiJapan, "hen", none));
		assertTrue(RuleFiles.applies(hiJapan, "HI-Japan-batch", RuleFiles.borrowNames("hi-japan")));
		assertTrue(RuleFiles.applies(hiJapan, "websquare-app", RuleFiles.borrowNames("projects/hi-japan/backend.yml")));
		assertFalse(RuleFiles.applies(hiJapan, "x", RuleFiles.borrowNames("hi-japan/frontend")));
	}

	@Test
	public void JPA_규칙을_읽는다() {
		String text = "jpa:\n"
				+ "  entity: \"@Entity\"\n"
				+ "  table: \"@Table\"\n"
				+ "  naming: as_is\n"
				+ "  query: \"@Query(value = JPQL, nativeQuery = NATIVE)\"\n"
				+ "  namedQuery:\n"
				+ "    - \"@NamedQuery(name = NAME, query = JPQL) in @NamedQueries\"\n"
				+ "    - \"@NamedNativeQuery(name = NAME, query = SQL)\"\n"
				+ "  repository: [JpaRepository]\n"
				+ "  jpql: _.createQuery(JPQL)\n";
		RuleFile file = parse(text, "common/jpa.yml");
		assertEquals("AS_IS", file.jpaNaming);
		assertEquals("nativeQuery", file.jpaQueries.get(0).nativeAttribute);
		assertEquals("value", file.jpaQueries.get(0).attribute);
		assertFalse(file.jpaQueries.get(0).nativeQuery);
		assertEquals("NamedQueries", file.jpaNamedQueries.get(0).container);
		assertEquals("name", file.jpaNamedQueries.get(0).nameAttribute);
		assertTrue(file.jpaNamedQueries.get(1).nativeQuery);
		assertEquals(Arrays.asList("createQuery"), file.jpqlMethods);
		assertTrue(parseFails("jpa:\n  naming: CAMEL\n", "common/jpa.yml").getMessage().contains("SNAKE_CASE"));
		assertTrue(parseFails("jpa:\n  query: \"@Query(value = JPQL, x = SQL)\"\n", "common/jpa.yml").getMessage().contains("하나로"));
	}

	@Test
	public void 설정_XML_진입점과_ID_대응표를_읽는다() throws Exception {
		File dir = Files.createTempDirectory("rules").toFile();
		File folder = new File(dir, "projects/order");
		folder.mkdirs();
		Files.write(new File(folder, "tx-map.csv").toPath(), "ORD001,OrderController.list\n".getBytes(StandardCharsets.UTF_8));
		RuleFileParser withDir = new RuleFileParser(dir);
		String text = "entry:\n"
				+ "  TRANSACTION:\n"
				+ "    - file: 'config/service-.*\\.xml$'\n"
				+ "      xml: '<service id=\"KEY\" class=\"CLASS\" method=\"METHOD\"/>'\n"
				+ "      path: '{KEY}.do'\n"
				+ "screen:\n"
				+ "  - { call: callTx(ID), to: '{ID}', id: { map: tx-map.csv } }\n";
		RuleFile file = withDir.parse(text, "projects/order/backend.yml");
		Rule.XmlEntry xml = file.xmlEntries.get(0);
		assertEquals("service", xml.element);
		assertEquals("id", xml.pathAttribute);
		assertEquals("class", xml.classAttribute);
		assertEquals(".do", xml.suffix);
		assertNull(xml.httpMethod);
		// 대응표는 규칙 파일이 있는 폴더를 기준으로 찾는다
		assertEquals("projects/order/tx-map.csv", file.screenCalls.get(0).getTransform().getMapSource());
		assertEquals("OrderController.list", file.screenCalls.get(0).methodNameOf("ORD001"));
		// 규칙 폴더 밖은 가리킬 수 없다
		String escape = "screen:\n  - { call: callTx(ID), to: '{ID}', id: { map: ../../../x.csv } }\n";
		final RuleFileParser p = withDir;
		final String t = escape;
		assertThrows(RuleException.class, new Executable() {
			@Override
			public void execute() {
				p.parse(t, "projects/order/backend.yml");
			}
		});
	}

	@Test
	public void conf_rules_폴더의_규칙_파일은_모두_올바르고_함께_쓸_수_있다() throws Exception {
		File dir = new File("conf/rules");
		assertTrue(dir.isDirectory(), "conf/rules 가 없습니다: " + dir.getAbsolutePath());
		List<RuleFile> files = new ArrayList<RuleFile>();
		collect(dir, dir, files);
		assertTrue(files.size() >= 7, "규칙 파일이 너무 적습니다: " + files.size());
		ProjectRules rules = new ProjectRules(files);
		assertFalse(rules.layers().isEmpty());
		assertFalse(rules.httpEntries().isEmpty());
		assertFalse(rules.sqlCalls().isEmpty());
	}

	private void collect(File root, File dir, List<RuleFile> files) throws Exception {
		File[] children = dir.listFiles();
		for (int i = 0; children != null && i < children.length; i++) {
			if (children[i].isDirectory()) {
				collect(root, children[i], files);
			} else if (children[i].getName().endsWith(".yml")) {
				String path = root.toPath().relativize(children[i].toPath()).toString().replace('\\', '/');
				files.add(new RuleFileParser(root).parse(new String(Files.readAllBytes(children[i].toPath()), StandardCharsets.UTF_8), path));
			}
		}
	}

}

package net.dstone.knowledge.analysis.pass08.semantic.rule;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import net.dstone.knowledge.analysis.pass08.semantic.screen.IdTransform;
import net.dstone.knowledge.analysis.pass08.semantic.screen.ScreenMenu;
import net.dstone.knowledge.analysis.pass08.semantic.screen.ScreenScript;
import net.dstone.knowledge.common.util.HashText;

/**
 * <pre>
 * 규칙 파일(YAML) 하나를 RuleFile 로 읽습니다. DB 도 Spring 도 쓰지 않아 단위 테스트로 확인합니다.
 *
 * 엄격하게 읽습니다. 모르는 키, 없는 계층 이름, 틀린 모양이 있으면 RuleException 을 던지고
 * 메시지에 "어느 파일의 몇 번째 줄"인지와 올바른 예를 적습니다. 오타가 조용히 무시되면
 * "규칙을 적었는데 왜 안 이어지지?"를 찾느라 시간을 버리기 때문입니다.
 *
 * 줄 번호를 알려고 YAML 을 값(Map/List)이 아니라 노드로 읽습니다(SnakeYAML compose).
 * 파일 모양은 conf/rules/README.md 에 있습니다.
 * </pre>
 */
public class RuleFileParser {

	/** 계층으로 쓸 수 있는 이름 */
	public static final List<String> LAYERS = Collections.unmodifiableList(Arrays.asList(
		"CONTROLLER", "SERVICE", "REPOSITORY"
		, "MODEL", "VIEW"
		, "CONFIG", "WEB_FILTER"
		, "UTIL", "EXCEPTION"
		, "BATCH", "COMPONENT", "ASPECT"
	));

	/** 인자 자리의 한도(0부터). DECLARE 가 남기는 인자 수도 이만큼이다 */
	public static final int MAX_ARG_INDEX = 9;

	/** 진입점 종류: 영문 대문자와 밑줄, 20자 이내(analysis_endpoint.endpoint_type) */
	private static final Pattern ENDPOINT_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{1,19}");

	/** HTTP 메소드 */
	private static final List<String> HTTP_VERBS = Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
	private static final Pattern HTTP_METHOD = Pattern.compile("[A-Z]{3,10}");

	/** Rule Key값들 */
	private static final List<String> TOP_KEYS = Arrays.asList(
		"description"
		, "layer"
		, "entry"
		, "inject"
		, "proxy"
		, "sql"
		, "view"
		, "screen"
		, "jpa"
	);

	/** 대응표 파일(id.map)을 찾는 바탕 폴더(규칙 폴더). null 이면 대응표를 쓸 수 없다(글만 읽는 단위 테스트) */
	private final File rulesDir;

	public RuleFileParser() {
		this(null);
	}

	/**
	 * @param rulesDir 규칙 폴더. 대응표 파일은 이 폴더 안에 있어야 한다
	 */
	public RuleFileParser(File rulesDir) {
		this.rulesDir = rulesDir;
	}

	/**
	 * <pre>
	 * 규칙 파일 하나를 읽습니다.
	 * </pre>
	 *
	 * @param text 파일 내용
	 * @param path 규칙 폴더 안의 경로(/ 로 구분). 예: common/spring.yml, projects/hi-japan/backend.yml
	 */
	public RuleFile parse(String text, String path) {
		Location location = locate(path);
		Node root;
		try {
			root = new Yaml(new SafeConstructor(new LoaderOptions())).compose(new StringReader(text));
		} catch (RuntimeException e) {
			String hint = text.matches("(?s).*(^|\\n)[^#\\n]*[:\\-\\[,]\\s*@.*") ? " (@ 로 시작하는 값은 따옴표로 감쌉니다: \"@Service\")" : "";
			throw new RuleException(path, 0, "YAML 문법이 맞지 않습니다" + hint + ": " + firstLine(e.getMessage()));
		}
		Reader r = new Reader(path, location.tier);
		StringBuilder mapTexts = new StringBuilder();
		String description = null;
		List<NodeTuple> top = root == null ? new ArrayList<NodeTuple>() : r.mapping(root, "파일");
		for (int i = 0; i < top.size(); i++) {
			String key = r.key(top.get(i));
			if ("description".equals(key)) {
				description = r.scalar(top.get(i).getValueNode(), "description");
			}
		}
		RuleFile file = new RuleFile(location.name, path, location.tier, location.projectFolder, description);
		for (int i = 0; i < top.size(); i++) {
			NodeTuple tuple = top.get(i);
			String key = r.key(tuple);
			Node value = tuple.getValueNode();
			if ("layer".equals(key)) {
				readLayers(r, value, file);
			} else if ("entry".equals(key)) {
				readEntries(r, value, file);
			} else if ("inject".equals(key)) {
				readMarks(r, value, file.injects, "inject");
			} else if ("proxy".equals(key)) {
				readMarks(r, value, file.proxies, "proxy");
			} else if ("sql".equals(key)) {
				readCallArguments(r, value, file.sqlCalls, Rule.CallArgument.TARGET_SQL);
			} else if ("view".equals(key)) {
				readCallArguments(r, value, file.viewCalls, Rule.CallArgument.TARGET_VIEW);
			} else if ("screen".equals(key)) {
				readScreens(r, value, file, mapTexts);
			} else if ("jpa".equals(key)) {
				readJpa(r, value, file);
			} else if (!"description".equals(key)) {
				throw r.error(tuple.getKeyNode(), unknownTopKey(key));
			}
		}
		// 대응표를 고쳐도 "규칙이 바뀌었다"를 알 수 있게 해시에 대응표 내용도 넣는다.
		file.hash = HashText.sha256(mapTexts.length() == 0 ? text : text + "\n" + mapTexts).substring(0, 12);
		return file;
	}

	private String unknownTopKey(String key) {
		return "모르는 키입니다: " + key + " (쓸 수 있는 키: " + TOP_KEYS + ")";
	}

	/* ============================== 파일 위치 → 이름과 단계 ============================== */

	/** 규칙 파일의 이름, 단계, 프로젝트 폴더 */
	static class Location {
		String name;
		Tier tier;
		String projectFolder;
	}

	/**
	 * <pre>
	 * 경로로 이름과 단계를 정합니다.
	 *   common/spring.yml               → common/spring, COMMON
	 *   guess/naming.yml                → guess/naming, GUESS
	 *   projects/hi-japan/backend.yml   → projects/hi-japan/backend, PROJECT(폴더 hi-japan)
	 * </pre>
	 */
	static Location locate(String path) {
		String[] parts = path.split("/");
		Tier tier = parts.length < 2 ? null : Tier.ofFolder(parts[0]);
		if (tier == null || (tier == Tier.PROJECT && parts.length < 3)) {
			throw new RuleException(path, 0, "규칙 파일은 common/, guess/, projects/{프로젝트ID}/ 아래에 둡니다."
					+ " common 은 모든 프로젝트의 관례, guess 는 짐작(가장 약함), projects/{프로젝트ID} 는 그 프로젝트만의 관례입니다.");
		}
		for (int i = 0; i < parts.length; i++) {
			if (!parts[i].matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,59}")) {
				throw new RuleException(path, 0, "폴더와 파일 이름은 영문자, 숫자, - _ . 로 60자 이내여야 합니다: " + parts[i]);
			}
		}
		Location location = new Location();
		location.name = path.replaceFirst("\\.ya?ml$", "");
		location.tier = tier;
		location.projectFolder = tier == Tier.PROJECT ? parts[1] : null;
		return location;
	}

	/* ============================== layer: ============================== */

	/**
	 * <pre>
	 * layer:
	 *   CONTROLLER: ["@Controller", extends javax.servlet.http.HttpServlet, name *Controller]
	 *   SERVICE: "@Service"
	 * </pre>
	 */
	private void readLayers(Reader r, Node node, RuleFile file) {
		List<NodeTuple> tuples = r.mapping(node, "layer");
		for (int i = 0; i < tuples.size(); i++) {
			String layer = r.key(tuples.get(i)).toUpperCase(Locale.ROOT);
			if (!LAYERS.contains(layer)) {
				throw r.error(tuples.get(i).getKeyNode(), "쓸 수 있는 계층이 아닙니다: " + layer + " (쓸 수 있는 값: " + LAYERS + ")");
			}
			List<Node> items = r.items(tuples.get(i).getValueNode(), "layer." + layer);
			for (int j = 0; j < items.size(); j++) {
				Node item = items.get(j);
				String text = r.scalar(item, "layer." + layer);
				Rule.Origin origin = r.origin(item);
				try {
					if (text.startsWith("@")) {
						CodePattern.Annotation a = CodePattern.annotation(text, Collections.<String>emptyList());
						file.layers.add(new Rule.Layer(origin, text, layer, Rule.Layer.BY_ANNOTATION, a.name, null));
						continue;
					}
					String superType = CodePattern.extendsType(text);
					if (superType != null) {
						file.layers.add(new Rule.Layer(origin, text, layer, Rule.Layer.BY_EXTENDS, superType, null));
						continue;
					}
					String glob = CodePattern.nameGlob(text);
					if (glob != null) {
						file.layers.add(new Rule.Layer(origin, text, layer, Rule.Layer.BY_NAME, glob, CodePattern.globToRegex(glob)));
						continue;
					}
				} catch (IllegalArgumentException e) {
					throw r.error(item, e.getMessage());
				}
				throw r.error(item, "계층 규칙은 세 가지 모양입니다: \"@Service\"(애노테이션), extends com.fw.BaseService(상위 타입), name *Service(클래스 이름). 적힌 것: " + text);
			}
		}
	}

	/* ============================== entry: ============================== */

	/**
	 * <pre>
	 * entry:
	 *   HTTP:
	 *     class: "@RequestMapping(value|path = URL)"
	 *     GET:   "@GetMapping(value|path = URL)"
	 *   SCHEDULED: "@Scheduled(cron|fixedRate = KEY)"
	 *   THREAD:    [java.lang.Runnable#run(), java.util.concurrent.Callable#call()]
	 *   TRANSACTION:
	 *     - file: 'config/service-.*\.xml$'
	 *       xml: '&lt;service id="KEY" class="CLASS" method="METHOD"/&gt;'
	 * </pre>
	 */
	private void readEntries(Reader r, Node node, RuleFile file) {
		List<NodeTuple> tuples = r.mapping(node, "entry");
		for (int i = 0; i < tuples.size(); i++) {
			String type = r.key(tuples.get(i)).toUpperCase(Locale.ROOT);
			if (!ENDPOINT_TYPE.matcher(type).matches()) {
				throw r.error(tuples.get(i).getKeyNode(), "진입점 종류는 영문 대문자와 _ 로 20자 이내입니다(예: HTTP, SCHEDULED, BATCH_JOB): " + type);
			}
			List<Node> items = r.items(tuples.get(i).getValueNode(), "entry." + type);
			for (int j = 0; j < items.size(); j++) {
				Node item = items.get(j);
				if (item instanceof MappingNode) {
					List<NodeTuple> keys = r.mapping(item, "entry." + type);
					if (r.has(keys, "xml")) {
						file.xmlEntries.add(readXmlEntry(r, item, keys, type));
					} else if ("HTTP".equals(type)) {
						file.httpEntries.add(readHttpEntry(r, item, keys));
					} else {
						throw r.error(item, "여기에는 \"@애노테이션\", 상위타입#메소드(), 또는 { file, xml } 을 적습니다. class / GET 같은 키는 HTTP 아래에서만 씁니다.");
					}
					continue;
				}
				String text = r.scalar(item, "entry." + type);
				Rule.Origin origin = r.origin(item);
				try {
					if (text.startsWith("@")) {
						CodePattern.Annotation a = CodePattern.annotation(text, Arrays.asList("KEY"));
						file.annotationEntries.add(new Rule.AnnotationEntry(origin, text, type, a.name, a.attributes("KEY")));
					} else {
						CodePattern.Call c = CodePattern.call(text, CodePattern.CallKind.TYPE_METHOD, Collections.<String>emptyList());
						Integer paramCount = c.anyArgs ? null : Integer.valueOf(c.args.size());
						file.extendsEntries.add(new Rule.ExtendsEntry(origin, text, type, c.receiverType
								, c.isGlob() ? null : c.name, c.isGlob() ? CodePattern.globToRegex(c.name) : null, paramCount));
					}
				} catch (IllegalArgumentException e) {
					throw r.error(item, e.getMessage());
				}
			}
		}
	}

	private Rule.HttpEntry readHttpEntry(Reader r, Node node, List<NodeTuple> keys) {
		List<Rule.HttpAnnotation> classAnnotations = new ArrayList<Rule.HttpAnnotation>();
		List<Rule.HttpAnnotation> methodAnnotations = new ArrayList<Rule.HttpAnnotation>();
		for (int i = 0; i < keys.size(); i++) {
			String key = r.key(keys.get(i));
			boolean isClass = "class".equals(key);
			boolean any = "ANY".equals(key);
			if (!isClass && !any && !HTTP_VERBS.contains(key)) {
				throw r.error(keys.get(i).getKeyNode(), "모르는 키입니다: " + key + " (쓸 수 있는 키: class, " + HTTP_VERBS + ", ANY)");
			}
			List<Node> items = r.items(keys.get(i).getValueNode(), "entry.HTTP." + key);
			for (int j = 0; j < items.size(); j++) {
				Node item = items.get(j);
				String text = r.scalar(item, "entry.HTTP." + key);
				CodePattern.Annotation a;
				try {
					a = CodePattern.annotation(text, any ? Arrays.asList("URL", "VERB") : Arrays.asList("URL"));
				} catch (IllegalArgumentException e) {
					throw r.error(item, e.getMessage());
				}
				if (a.attributes("URL").isEmpty()) {
					throw r.error(item, "주소가 적힌 속성을 URL 자리로 적어야 합니다. 예: \"@" + a.name + "(value|path = URL)\"");
				}
				Rule.HttpAnnotation annotation = new Rule.HttpAnnotation(r.origin(item), text, a.name, a.attributes("URL")
						, isClass || any ? null : key, any ? a.attribute("VERB") : null);
				(isClass ? classAnnotations : methodAnnotations).add(annotation);
			}
		}
		if (methodAnnotations.isEmpty()) {
			throw r.error(node, "메소드에 붙는 애노테이션을 하나 이상 적어야 합니다. 예: GET: \"@GetMapping(value|path = URL)\"");
		}
		return new Rule.HttpEntry(r.origin(node), "HTTP", classAnnotations, methodAnnotations);
	}

	private Rule.XmlEntry readXmlEntry(Reader r, Node node, List<NodeTuple> keys, String type) {
		r.allowOnly(keys, "file", "xml", "path", "verb");
		String files = r.requiredScalar(keys, node, "file");
		r.regex(files, r.valueNode(keys, "file"));
		String xml = r.requiredScalar(keys, node, "xml");
		CodePattern.XmlElement element;
		try {
			element = CodePattern.xmlElement(xml, Arrays.asList("KEY", "CLASS", "METHOD"));
		} catch (IllegalArgumentException e) {
			throw r.error(r.valueNode(keys, "xml"), e.getMessage());
		}
		if (!element.slots.containsKey("KEY")) {
			throw r.error(r.valueNode(keys, "xml"), "주소(거래 ID)가 적힌 속성을 KEY 자리로 적어야 합니다. 예: <service id=\"KEY\" class=\"CLASS\"/>");
		}
		String template = r.optionalScalar(keys, "path", "{KEY}");
		int at = template.indexOf("{KEY}");
		if (at < 0 || template.indexOf("{KEY}", at + 1) >= 0) {
			throw r.error(r.valueNode(keys, "path"), "{KEY} 를 한 번 적어야 합니다(예: \"{KEY}.do\"): " + template);
		}
		String verb = r.optionalScalar(keys, "verb", null);
		if (verb != null) {
			verb = verb.toUpperCase(Locale.ROOT);
			if (!HTTP_METHOD.matcher(verb).matches()) {
				throw r.error(r.valueNode(keys, "verb"), "GET, POST, ALL 처럼 영문 대문자여야 합니다: " + verb);
			}
		} else if ("HTTP".equals(type)) {
			verb = "ALL";
		}
		return new Rule.XmlEntry(r.origin(node), xml, type, files, element.element, element.slots.get("KEY"), element.slots.get("CLASS")
				, element.slots.get("METHOD"), verb, template.substring(0, at), template.substring(at + "{KEY}".length()));
	}

	/* ============================== inject: / proxy: ============================== */

	private void readMarks(Reader r, Node node, List<Rule.Mark> target, String where) {
		List<Node> items = r.items(node, where);
		for (int i = 0; i < items.size(); i++) {
			target.add(readMark(r, items.get(i), where));
		}
	}

	private Rule.Mark readMark(Reader r, Node item, String where) {
		String text = r.scalar(item, where);
		try {
			CodePattern.Annotation a = CodePattern.annotation(text, Collections.<String>emptyList());
			return new Rule.Mark(r.origin(item), text, a.name);
		} catch (IllegalArgumentException e) {
			throw r.error(item, e.getMessage());
		}
	}

	/* ============================== sql: / view: ============================== */

	/**
	 * <pre>
	 * sql:
	 *   - _.selectList(SQL)
	 *   - com.acme.fw.BaseDao#executeList(_, SQL)
	 *   - { call: _.execute(SQL), name: "Order.{SQL}", skip: '^x' }
	 * view:
	 *   - { call: [new ModelAndView(VIEW), _.setViewName(VIEW)], file: "{VIEW}.jsp", skip: '^(redirect|forward):' }
	 * </pre>
	 */
	private void readCallArguments(Reader r, Node node, List<Rule.CallArgument> target, String what) {
		String where = what.toLowerCase(Locale.ROOT);
		String templateKey = Rule.CallArgument.TARGET_SQL.equals(what) ? "name" : "file";
		String slot = "{" + what + "}";
		String defaultTemplate = Rule.CallArgument.TARGET_SQL.equals(what) ? slot : slot + ".jsp";
		List<Node> items = r.items(node, where);
		for (int i = 0; i < items.size(); i++) {
			Node item = items.get(i);
			List<Node> calls;
			String template = defaultTemplate;
			String skip = null;
			if (item instanceof MappingNode) {
				List<NodeTuple> keys = r.mapping(item, where);
				r.allowOnly(keys, "call", templateKey, "skip");
				if (!r.has(keys, "call")) {
					throw r.error(item, "call 을 적어야 합니다. 예: { call: _.selectList(" + what + ") }");
				}
				calls = r.items(r.valueNode(keys, "call"), where + ".call");
				template = r.optionalScalar(keys, templateKey, defaultTemplate);
				int at = template.indexOf(slot);
				if (at < 0 || template.indexOf(slot, at + 1) >= 0) {
					throw r.error(r.valueNode(keys, templateKey), slot + " 를 한 번 적어야 합니다: " + template);
				}
				skip = r.optionalScalar(keys, "skip", null);
				if (skip != null) {
					r.regex(skip, r.valueNode(keys, "skip"));
				}
			} else {
				calls = Collections.singletonList(item);
			}
			int at = template.indexOf(slot);
			String prefix = template.substring(0, at);
			String suffix = template.substring(at + slot.length());
			for (int c = 0; c < calls.size(); c++) {
				Node callNode = calls.get(c);
				String text = r.scalar(callNode, where);
				CodePattern.Call call;
				try {
					call = CodePattern.call(text, CodePattern.CallKind.JAVA_CALL, Arrays.asList(what));
				} catch (IllegalArgumentException e) {
					throw r.error(callNode, e.getMessage());
				}
				int argIndex = call.indexOf(what);
				if (argIndex < 0) {
					throw r.error(callNode, what + " 자리를 적어야 어느 인자를 보는지 압니다. 예: _.selectList(" + what + "), com.fw.BaseDao#execute(_, " + what + ")");
				}
				if (argIndex > MAX_ARG_INDEX) {
					throw r.error(callNode, "인자 자리는 " + (MAX_ARG_INDEX + 1) + "번째까지 볼 수 있습니다: " + text);
				}
				target.add(new Rule.CallArgument(r.origin(callNode), text, what, call.constructor ? null : call.name, call.constructor ? call.name : null
						, call.receiverType, argIndex, prefix, suffix, skip));
			}
		}
	}

	/* ============================== screen: ============================== */

	/**
	 * <pre>
	 * screen:
	 *   - { call: doRequestAjax(ID), to: "perform{ID}" }       화면 함수 → 서버 메소드
	 *   - { field: tranId, to: "perform{ID}" }                 폼 필드 값 → 서버 메소드
	 *   - { open: fnOpenPage(_, ID), to: "{ID}.xml" }          화면 함수 → 화면 파일
	 *   - { menu: { name: MENU_NM, path: SRC_PATH } }          메뉴 목록 → 화면명
	 *   - { call: callTx(ID), to: "{ID}", id: { match: '^SVC_(.*)$', replace: 'svc_$1', case: LOWER, map: tx-map.csv } }
	 * </pre>
	 */
	private void readScreens(Reader r, Node node, RuleFile file, StringBuilder mapTexts) {
		List<Node> items = r.items(node, "screen");
		for (int i = 0; i < items.size(); i++) {
			Node item = items.get(i);
			List<NodeTuple> keys = r.mapping(item, "screen");
			String at = r.origin(item).at();
			if (r.has(keys, "menu")) {
				r.allowOnly(keys, "menu");
				List<NodeTuple> menu = r.mapping(r.valueNode(keys, "menu"), "screen.menu");
				r.allowOnly(menu, "name", "path");
				String name = r.identifier(menu, item, "name");
				String path = r.identifier(menu, item, "path");
				file.screenMenus.add(new ScreenMenu.Rule(name, path, at));
				continue;
			}
			String kind = r.has(keys, "call") ? "call" : r.has(keys, "field") ? "field" : r.has(keys, "open") ? "open" : null;
			if (kind == null) {
				throw r.error(item, "call, field, open, menu 가운데 하나를 적습니다. 예: { call: doRequestAjax(ID), to: \"perform{ID}\" }");
			}
			r.allowOnly(keys, kind, "to", "id");
			String to = r.requiredScalar(keys, item, "to");
			if (to.indexOf("{ID}") < 0) {
				throw r.error(r.valueNode(keys, "to"), "{ID} 가 들어 있어야 합니다(예: \"perform{ID}\", \"{ID}.xml\"): " + to);
			}
			String template = to.replace("{ID}", "{id}");
			IdTransform transform = readIdTransform(r, keys, file, mapTexts);
			if ("field".equals(kind)) {
				String field = r.identifier(keys, item, "field");
				file.screenFields.add(ScreenScript.CallRule.field(field, template, transform, at));
				continue;
			}
			Node callNode = r.valueNode(keys, kind);
			String text = r.scalar(callNode, "screen." + kind);
			CodePattern.Call call;
			try {
				call = CodePattern.call(text, CodePattern.CallKind.SCRIPT_FUNCTION, Arrays.asList("ID"));
			} catch (IllegalArgumentException e) {
				throw r.error(callNode, e.getMessage());
			}
			int argIndex = call.indexOf("ID");
			if (argIndex < 0) {
				throw r.error(callNode, "ID 자리를 적어야 어느 인자가 ID 인지 압니다. 예: " + call.name + "(ID), " + call.name + "(_, ID)");
			}
			if (argIndex > MAX_ARG_INDEX) {
				throw r.error(callNode, "인자 자리는 " + (MAX_ARG_INDEX + 1) + "번째까지 볼 수 있습니다: " + text);
			}
			ScreenScript.CallRule rule = new ScreenScript.CallRule(call.name, template, argIndex, transform, at);
			("call".equals(kind) ? file.screenCalls : file.screenOpens).add(rule);
		}
	}

	/**
	 * <pre>
	 * id: { match: 정규식, replace: '$1', case: UPPER | LOWER | UPPER_FIRST | LOWER_FIRST, map: 대응표 파일 }
	 * map 은 이 규칙 파일이 있는 폴더를 기준으로 찾습니다. 규칙 폴더 밖은 가리킬 수 없습니다.
	 * </pre>
	 */
	private IdTransform readIdTransform(Reader r, List<NodeTuple> keys, RuleFile file, StringBuilder mapTexts) {
		if (!r.has(keys, "id")) {
			return IdTransform.NONE;
		}
		Node idNode = r.valueNode(keys, "id");
		List<NodeTuple> id = r.mapping(idNode, "id");
		r.allowOnly(id, "match", "replace", "case", "map");
		String pattern = r.optionalScalar(id, "match", null);
		String replace = r.optionalScalar(id, "replace", null);
		String idCase = r.optionalScalar(id, "case", null);
		String mapPath = r.optionalScalar(id, "map", null);
		if (pattern != null) {
			r.regex(pattern, r.valueNode(id, "match"));
		}
		if (replace != null && pattern == null) {
			throw r.error(r.valueNode(id, "replace"), "replace 는 match 와 함께 적습니다($1 처럼 match 의 묶음을 씁니다).");
		}
		if (replace != null) {
			int groups = Pattern.compile(pattern).matcher("").groupCount();
			if (groups < maxGroupRef(replace)) {
				throw r.error(r.valueNode(id, "replace"), "match 의 묶음 수(" + groups + ")보다 큰 번호를 씁니다: " + replace);
			}
		}
		if (idCase != null) {
			idCase = idCase.toUpperCase(Locale.ROOT);
			if (!IdTransform.CASES.contains(idCase)) {
				throw r.error(r.valueNode(id, "case"), "쓸 수 있는 값이 아닙니다: " + idCase + " (쓸 수 있는 값: " + IdTransform.CASES + ")");
			}
		}
		Map<String, String> map = null;
		String mapSource = null;
		if (mapPath != null) {
			Node mapNode = r.valueNode(id, "map");
			if (rulesDir == null) {
				throw r.error(mapNode, "규칙 폴더 밖에서 읽는 규칙에는 map 을 쓸 수 없습니다.");
			}
			String folder = file.path.lastIndexOf('/') < 0 ? "" : file.path.substring(0, file.path.lastIndexOf('/') + 1);
			mapSource = normalize(folder + mapPath);
			String text;
			try {
				File base = rulesDir.getCanonicalFile();
				File canonical = new File(rulesDir, mapSource).getCanonicalFile();
				if (!canonical.getPath().startsWith(base.getPath() + File.separator)) {
					throw r.error(mapNode, "대응표 파일은 규칙 폴더(" + rulesDir.getPath() + ") 안에 있어야 합니다: " + mapPath);
				}
				if (!canonical.isFile()) {
					throw r.error(mapNode, "대응표 파일이 없습니다(이 규칙 파일이 있는 폴더 기준): " + canonical.getPath());
				}
				text = new String(Files.readAllBytes(canonical.toPath()), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw r.error(mapNode, "대응표 파일을 읽지 못했습니다: " + e.getMessage());
			}
			List<Integer> errors = new ArrayList<Integer>();
			map = IdTransform.parseMap(text, errors);
			if (!errors.isEmpty()) {
				throw r.error(mapNode, mapPath + " 의 " + errors + " 번째 줄에 \"ID,값\" 이 없습니다.");
			}
			if (map.isEmpty()) {
				throw r.error(mapNode, "대응표가 비어 있습니다: " + mapPath);
			}
			mapTexts.append(mapSource).append('\n').append(text).append('\n');
		}
		return new IdTransform(pattern, replace, idCase, map, mapSource);
	}

	/** a/b/../c.csv → a/c.csv. 규칙 폴더 밖을 가리키면 ../ 가 남아 위에서 막힌다 */
	private static String normalize(String path) {
		List<String> parts = new ArrayList<String>();
		String[] split = path.split("/");
		for (int i = 0; i < split.length; i++) {
			if ("..".equals(split[i]) && !parts.isEmpty() && !"..".equals(parts.get(parts.size() - 1))) {
				parts.remove(parts.size() - 1);
			} else if (split[i].length() > 0 && !".".equals(split[i])) {
				parts.add(split[i]);
			}
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			sb.append(i == 0 ? "" : "/").append(parts.get(i));
		}
		return sb.toString();
	}

	/** 바꿀 글자 안의 $n 가운데 가장 큰 n */
	private static int maxGroupRef(String replace) {
		int max = 0;
		java.util.regex.Matcher m = Pattern.compile("\\$(\\d)").matcher(replace);
		while (m.find()) {
			max = Math.max(max, Integer.parseInt(m.group(1)));
		}
		return max;
	}

	/* ============================== jpa: ============================== */

	/**
	 * <pre>
	 * jpa:
	 *   entity: "@Entity"                     name 속성 = 엔티티 이름(없으면 클래스 이름)
	 *   table: "@Table"                       name 속성 = 테이블 이름
	 *   naming: SNAKE_CASE                    @Table 이 없을 때(SNAKE_CASE | AS_IS)
	 *   query: "@Query(value = JPQL, nativeQuery = NATIVE)"
	 *   namedQuery:
	 *     - "@NamedQuery(name = NAME, query = JPQL) in @NamedQueries"
	 *     - "@NamedNativeQuery(name = NAME, query = SQL) in @NamedNativeQueries"
	 *   repository: [Repository, CrudRepository, JpaRepository]
	 *   jpql: _.createQuery(JPQL)
	 * </pre>
	 */
	private void readJpa(Reader r, Node node, RuleFile file) {
		List<NodeTuple> keys = r.mapping(node, "jpa");
		r.allowOnly(keys, "entity", "table", "naming", "query", "namedQuery", "repository", "jpql");
		if (r.has(keys, "entity")) {
			readMarks(r, r.valueNode(keys, "entity"), file.jpaEntities, "jpa.entity");
		}
		if (r.has(keys, "table")) {
			readMarks(r, r.valueNode(keys, "table"), file.jpaTables, "jpa.table");
		}
		String naming = r.optionalScalar(keys, "naming", null);
		if (naming != null) {
			naming = naming.toUpperCase(Locale.ROOT);
			if (!"SNAKE_CASE".equals(naming) && !"AS_IS".equals(naming)) {
				throw r.error(r.valueNode(keys, "naming"), "SNAKE_CASE 또는 AS_IS 여야 합니다: " + naming);
			}
			file.jpaNaming = naming;
		}
		if (r.has(keys, "query")) {
			readJpaQueries(r, r.valueNode(keys, "query"), false, file.jpaQueries);
		}
		if (r.has(keys, "namedQuery")) {
			readJpaQueries(r, r.valueNode(keys, "namedQuery"), true, file.jpaNamedQueries);
		}
		if (r.has(keys, "repository")) {
			List<Node> items = r.items(r.valueNode(keys, "repository"), "jpa.repository");
			for (int i = 0; i < items.size(); i++) {
				String name = r.scalar(items.get(i), "jpa.repository");
				if (!CodePattern.isIdentifier(name)) {
					throw r.error(items.get(i), "저장소 상위 타입의 단순 이름을 적습니다(예: JpaRepository): " + name);
				}
				file.jpaRepositories.add(name);
			}
		}
		if (r.has(keys, "jpql")) {
			List<Node> items = r.items(r.valueNode(keys, "jpql"), "jpa.jpql");
			for (int i = 0; i < items.size(); i++) {
				String text = r.scalar(items.get(i), "jpa.jpql");
				CodePattern.Call call;
				try {
					call = CodePattern.call(text, CodePattern.CallKind.JAVA_CALL, Arrays.asList("JPQL"));
				} catch (IllegalArgumentException e) {
					throw r.error(items.get(i), e.getMessage());
				}
				if (!call.anyReceiver || call.indexOf("JPQL") != 0) {
					throw r.error(items.get(i), "jpql 은 _.메소드(JPQL) 모양으로 적습니다(예: _.createQuery(JPQL)): " + text);
				}
				file.jpqlMethods.add(call.name);
			}
		}
	}

	private void readJpaQueries(Reader r, Node node, boolean named, List<Rule.JpaQuery> target) {
		String where = named ? "jpa.namedQuery" : "jpa.query";
		List<Node> items = r.items(node, where);
		for (int i = 0; i < items.size(); i++) {
			Node item = items.get(i);
			String text = r.scalar(item, where);
			String annotationText = text;
			String container = null;
			int in = text.lastIndexOf(" in ");
			if (in > 0 && text.substring(in + 4).trim().startsWith("@")) {
				annotationText = text.substring(0, in);
				container = text.substring(in + 4).trim();
			}
			try {
				CodePattern.Annotation a = CodePattern.annotation(annotationText
						, named ? Arrays.asList("NAME", "JPQL", "SQL") : Arrays.asList("JPQL", "SQL", "NATIVE"));
				String jpql = a.attribute("JPQL");
				String sql = a.attribute("SQL");
				if ((jpql == null) == (sql == null)) {
					throw new IllegalArgumentException("쿼리 글이 든 속성을 JPQL 이나 SQL 자리 하나로 적습니다. 예: \"@Query(value = JPQL, nativeQuery = NATIVE)\"");
				}
				String nameAttribute = named ? a.attribute("NAME") : null;
				if (named && nameAttribute == null) {
					throw new IllegalArgumentException("쿼리 이름이 든 속성을 NAME 자리로 적습니다. 예: \"@NamedQuery(name = NAME, query = JPQL)\"");
				}
				String containerName = container == null ? null : CodePattern.annotation(container, Collections.<String>emptyList()).name;
				target.add(new Rule.JpaQuery(r.origin(item), text, a.name, jpql != null ? jpql : sql, named ? null : a.attribute("NATIVE")
						, sql != null, nameAttribute, containerName));
			} catch (IllegalArgumentException e) {
				throw r.error(item, e.getMessage());
			}
		}
	}

	/* ============================== 도우미 ============================== */

	private static String firstLine(String text) {
		if (text == null) {
			return "";
		}
		int nl = text.indexOf('\n');
		return nl < 0 ? text : text.substring(0, nl);
	}

	/**
	 * <pre>
	 * YAML 노드를 꺼내는 도우미. 틀리면 그 노드의 줄 번호를 붙여 RuleException 을 던집니다.
	 * </pre>
	 */
	private static class Reader {

		private final String fileName;
		private final Tier tier;

		Reader(String fileName, Tier tier) {
			this.fileName = fileName;
			this.tier = tier;
		}

		RuleException error(Node node, String message) {
			return new RuleException(fileName, node == null ? 0 : node.getStartMark().getLine() + 1, message);
		}

		Rule.Origin origin(Node node) {
			return new Rule.Origin(fileName, node.getStartMark().getLine() + 1, tier);
		}

		/** 키: 값 모양의 노드. 같은 키가 두 번 나오면 오류 */
		List<NodeTuple> mapping(Node node, String where) {
			if (!(node instanceof MappingNode)) {
				throw error(node, where + " 은(는) 키: 값 모양이어야 합니다.");
			}
			List<NodeTuple> tuples = ((MappingNode) node).getValue();
			List<String> seen = new ArrayList<String>();
			for (int i = 0; i < tuples.size(); i++) {
				String key = key(tuples.get(i));
				if (seen.contains(key)) {
					throw error(tuples.get(i).getKeyNode(), "같은 키를 두 번 적었습니다: " + key);
				}
				seen.add(key);
			}
			return tuples;
		}

		String key(NodeTuple tuple) {
			Node key = tuple.getKeyNode();
			if (!(key instanceof ScalarNode)) {
				throw error(key, "키는 글자여야 합니다.");
			}
			return ((ScalarNode) key).getValue().trim();
		}

		/** 값 하나 또는 목록. 값 하나면 그 하나짜리 목록으로 본다 */
		List<Node> items(Node node, String where) {
			if (node instanceof SequenceNode) {
				return ((SequenceNode) node).getValue();
			}
			if (node instanceof ScalarNode && ((ScalarNode) node).getValue().trim().length() == 0) {
				throw error(node, where + " 에 값이 없습니다.");
			}
			return Collections.singletonList(node);
		}

		String scalar(Node node, String where) {
			if (!(node instanceof ScalarNode)) {
				throw error(node, where + " 은(는) 글자 하나여야 합니다.");
			}
			String value = ((ScalarNode) node).getValue().trim();
			if (value.length() == 0) {
				throw error(node, where + " 에 값이 없습니다.");
			}
			return value;
		}

		boolean has(List<NodeTuple> tuples, String key) {
			return valueNode(tuples, key) != null;
		}

		Node valueNode(List<NodeTuple> tuples, String key) {
			for (int i = 0; i < tuples.size(); i++) {
				if (key.equals(key(tuples.get(i)))) {
					return tuples.get(i).getValueNode();
				}
			}
			return null;
		}

		void allowOnly(List<NodeTuple> tuples, String... allowed) {
			List<String> known = Arrays.asList(allowed);
			for (int i = 0; i < tuples.size(); i++) {
				String key = key(tuples.get(i));
				if (!known.contains(key)) {
					throw error(tuples.get(i).getKeyNode(), "모르는 키입니다: " + key + " (쓸 수 있는 키: " + known + ")");
				}
			}
		}

		String requiredScalar(List<NodeTuple> tuples, Node parent, String key) {
			Node node = valueNode(tuples, key);
			if (node == null) {
				throw error(parent, key + " 을(를) 적어야 합니다.");
			}
			return scalar(node, key);
		}

		String optionalScalar(List<NodeTuple> tuples, String key, String defaultValue) {
			Node node = valueNode(tuples, key);
			return node == null ? defaultValue : scalar(node, key);
		}

		String identifier(List<NodeTuple> tuples, Node parent, String key) {
			String value = requiredScalar(tuples, parent, key);
			if (!CodePattern.isIdentifier(value)) {
				throw error(valueNode(tuples, key), key + " 은(는) 이름(영문자, 숫자, _, $)이어야 합니다: " + value);
			}
			return value;
		}

		void regex(String pattern, Node node) {
			try {
				Pattern.compile(pattern);
			} catch (PatternSyntaxException e) {
				throw error(node, "정규식이 맞지 않습니다: " + firstLine(e.getMessage()));
			}
		}
	}

}

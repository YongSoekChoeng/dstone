package net.dstone.knowledge.analysis.pass08.semantic.rule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.common.util.HashText;
import net.dstone.knowledge.analysis.pass08.semantic.screen.ScreenMenu;
import net.dstone.knowledge.analysis.pass08.semantic.screen.ScreenScript;

/**
 * <pre>
 * 규칙 파일(conf/rules/*.yml)을 읽어 프로젝트마다 쓸 규칙(SemanticRules)을 만들어 줍니다.
 *
 * - 서버가 뜰 때 폴더 전체를 한 번 읽어 검사합니다. 잘못 적힌 파일이 있으면 서버가 뜨지 않습니다(어느 파일의 어느 자리인지 알려 줌).
 * - 분석할 때마다(SEMANTIC 단계 시작) 파일을 다시 읽습니다. 그래서 규칙 파일을 고친 뒤 서버를 다시 띄우지 않고
 *   rerunFrom=SEMANTIC 으로 다시 돌리면 바로 반영됩니다. 이때 잘못 적혀 있으면 그 분석이 실패합니다.
 *
 * 폴더 모양:
 *   공통 관례(java, spring, mybatis ...)는 규칙 폴더 바로 아래에, 특정 프로젝트의 관례는 프로젝트 이름의 하위 폴더에 둡니다.
 *   묶음 이름은 규칙 폴더 안의 경로(확장자 뺀 것)입니다. 예: conf/rules/hi-japan/frontend.yml → hi-japan/frontend
 *
 * 어느 묶음을 쓰나:
 *   autoApply: true 인 묶음은 모든 프로젝트에 씁니다. 해당하는 코드가 없는 프로젝트에서는 아무 일도 하지 않습니다.
 *     적지 않으면 바로 아래 묶음은 true, 하위 폴더의 묶음은 false 입니다.
 *   프로젝트 폴더: 묶음 이름의 첫 마디가 프로젝트 ID 와 같으면(대소문자 무시) 그 프로젝트에 저절로 씁니다.
 *     예: conf/rules/order-system/backend.yml 은 프로젝트 order-system 에 쓴다. ruleSets 에 적지 않아도 된다.
 *   그 밖의 autoApply: false 묶음은 프로젝트의 ruleSets(쉼표로 이은 묶음 이름)에 적었을 때만 씁니다.
 *   ruleSets 에 폴더 이름(예: hi-japan)을 적으면 그 폴더 아래의 묶음을 모두 고른 것입니다.
 *     다른 프로젝트의 규칙을 빌려 쓸 때(예: 같은 프레임워크를 쓰는 배치 프로젝트) 이렇게 적습니다.
 *
 * 옛 설정 키(application.yml 의 dstone.knowledge.semantic.screen.* / layer.super-types)가 남아 있으면
 * "application-yml" 이라는 묶음으로 바꿔 맨 뒤에 붙입니다. 새로 적을 때는 규칙 파일에 적습니다.
 * </pre>
 */
@Component
public class SemanticRuleRegistry extends BaseObject {

	/** 옛 설정 키를 바꿔 만든 묶음의 이름 */
	public static final String LEGACY_SET_NAME = "application-yml";

	private static final String[] LEGACY_KEYS = {
		"dstone.knowledge.semantic.screen.call-rules", "dstone.knowledge.semantic.screen.field-rules"
		, "dstone.knowledge.semantic.screen.open-rules", "dstone.knowledge.semantic.screen.menu-rules"
		, "dstone.knowledge.semantic.layer.super-types"
	};

	@Autowired
	private ConfigProperty configProperty;

	/** 대응표 파일(idMap)을 규칙 폴더에서 찾으므로 쓸 때마다 그 폴더로 만든다 */
	private RuleSetParser parser() {
		return new RuleSetParser(rulesDir());
	}

	/** 서버가 뜰 때 규칙 파일을 모두 검사합니다. 하나라도 틀리면 서버를 띄우지 않습니다. */
	@PostConstruct
	public void check() {
		try {
			List<RuleSet> sets = loadAll();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < sets.size(); i++) {
				sb.append(i == 0 ? "" : ", ").append(sets.get(i).name).append('(').append(sets.get(i).ruleCount()).append(')');
			}
			info("의미 분석 규칙 " + sets.size() + "묶음을 읽었습니다(" + rulesDir().getPath() + "): " + sb);
			if (legacy() != null) {
				warn("application.yml 에 옛 의미 분석 규칙 키가 남아 있습니다. " + LEGACY_SET_NAME
						+ " 묶음으로 바꿔 씁니다. conf/rules 아래 규칙 파일로 옮기세요: " + Arrays.asList(LEGACY_KEYS));
			}
		} catch (RuleException e) {
			throw new IllegalStateException("의미 분석 규칙 파일이 잘못 적혀 있습니다. " + e.getMessage(), e);
		}
	}

	/** 규칙 파일 폴더. dstone.knowledge.semantic.rules-dir 이 없으면 설정 폴더(APP_CONF_DIR) 아래 rules */
	public File rulesDir() {
		String configured = configProperty.getProperty("dstone.knowledge.semantic.rules-dir");
		if (configured != null && configured.trim().length() > 0) {
			return new File(configured.trim());
		}
		String confDir = System.getProperty("APP_CONF_DIR");
		return new File(confDir == null || confDir.trim().length() == 0 ? "conf" : confDir.trim(), "rules");
	}

	/**
	 * <pre>
	 * 폴더의 규칙 파일을 모두 읽습니다(order, 이름 순서). 옛 설정 키에서 만든 묶음은 넣지 않습니다.
	 * </pre>
	 *
	 * @throws RuleException 잘못 적힌 파일이 있거나 폴더가 없을 때
	 */
	public List<RuleSet> loadAll() {
		File dir = rulesDir();
		if (!dir.isDirectory()) {
			throw new RuleException(dir.getPath(), "", "규칙 파일 폴더가 없습니다. 기본 규칙(java, spring, mybatis ...)도 이 폴더에 있어서, 없으면 진입점과 계층을 하나도 찾지 못합니다.");
		}
		List<String> paths = new ArrayList<String>();
		collect(dir, "", paths, 0);
		Collections.sort(paths);
		List<RuleSet> sets = new ArrayList<RuleSet>();
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < paths.size(); i++) {
			String path = paths.get(i);
			String text;
			try {
				text = new String(Files.readAllBytes(new File(dir, path).toPath()), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new RuleException(path, "", "파일을 읽지 못했습니다: " + e.getMessage());
			}
			RuleSet set = parser().parse(text, path);
			if (names.contains(set.name)) {
				throw new RuleException(path, "", "같은 이름의 규칙 묶음이 또 있습니다(.yml 과 .yaml 이 함께 있는지 보세요): " + set.name);
			}
			names.add(set.name);
			sets.add(set);
		}
		sort(sets);
		return sets;
	}

	/**
	 * <pre>
	 * 폴더 아래의 규칙 파일(*.yml, *.yaml)을 규칙 폴더 안의 경로("hi-japan/frontend.yml")로 모읍니다.
	 * 하위 폴더는 3단계까지 봅니다(묶음 이름 규칙과 같다). README.md 같은 다른 파일은 건너뜁니다.
	 * </pre>
	 */
	private void collect(File dir, String prefix, List<String> paths, int depth) {
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (int i = 0; i < files.length; i++) {
			String name = files[i].getName();
			if (files[i].isDirectory()) {
				if (depth < 3 && !name.startsWith(".")) {
					collect(files[i], prefix + name + "/", paths, depth + 1);
				}
				continue;
			}
			String lower = name.toLowerCase(Locale.ROOT);
			if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
				paths.add(prefix + name);
			}
		}
	}

	/** 글로 받은 규칙 묶음 하나를 읽습니다(시험 적용 API). 이름은 묶음 안의 name 이 있어야 합니다. */
	public RuleSet parse(String text) {
		Object name = null;
		try {
			Object root = new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions())).load(text);
			if (root instanceof Map) {
				name = ((Map<?, ?>) root).get("name");
			}
		} catch (RuntimeException e) {
			// 아래 parser.parse 가 같은 오류를 자리와 함께 다시 알려 준다.
		}
		if (name == null || String.valueOf(name).trim().length() == 0) {
			throw new RuleException("(요청)", "name", "시험할 규칙 묶음에는 name 을 적어야 합니다. 같은 이름의 파일이 있으면 그 파일 대신 씁니다.");
		}
		return parser().parse(text, String.valueOf(name).trim() + ".yml");
	}

	/**
	 * <pre>
	 * 이 프로젝트에 쓸 규칙을 만듭니다.
	 * </pre>
	 *
	 * @param project analysis_project 한 행(ruleSets 키를 본다)
	 * @throws RuleException 규칙 파일이 틀렸거나, 프로젝트의 ruleSets 에 없는 묶음 이름이 있을 때
	 */
	public SemanticRules rulesFor(Map<String, Object> project) {
		return rulesFor(project, null);
	}

	/**
	 * @param trial 시험으로 더해 볼 묶음. 같은 이름의 파일이 있으면 그 대신 쓰고, autoApply 와 상관없이 쓴다. 없으면 null
	 */
	public SemanticRules rulesFor(Map<String, Object> project, RuleSet trial) {
		List<RuleSet> all = loadAll();
		List<String> wanted = ruleSetNames(project == null ? null : project.get("ruleSets"));
		List<String> known = new ArrayList<String>();
		for (int i = 0; i < all.size(); i++) {
			known.add(all.get(i).name);
		}
		if (trial != null) {
			known.add(trial.name);
		}
		for (int i = 0; i < wanted.size(); i++) {
			if (!matchesAny(wanted.get(i), known)) {
				throw new RuleException(String.valueOf(project.get("projectId")), "ruleSets"
						, "규칙 묶음 " + wanted.get(i) + " 이(가) " + rulesDir().getPath() + " 에 없습니다. 있는 묶음: " + known);
			}
		}
		String projectId = project == null || project.get("projectId") == null ? null : String.valueOf(project.get("projectId"));
		List<RuleSet> chosen = new ArrayList<RuleSet>();
		for (int i = 0; i < all.size(); i++) {
			RuleSet set = all.get(i);
			if (trial != null && trial.name.equals(set.name)) {
				continue;
			}
			if (set.autoApply || isWanted(set.name, wanted) || ownedBy(set.name, projectId)) {
				chosen.add(set);
			}
		}
		if (trial != null) {
			chosen.add(trial);
		}
		sort(chosen);
		RuleSet legacy = legacy();
		if (legacy != null) {
			chosen.add(legacy);
		}
		return new SemanticRules(chosen);
	}

	/** 프로젝트의 ruleSets 값이 폴더에 있는 묶음(또는 묶음이 든 하위 폴더)인지 확인합니다(프로젝트를 등록할 때). 틀리면 RuleException */
	public void checkRuleSetNames(Object ruleSets) {
		List<String> wanted = ruleSetNames(ruleSets);
		if (wanted.isEmpty()) {
			return;
		}
		List<RuleSet> all = loadAll();
		List<String> known = new ArrayList<String>();
		for (int i = 0; i < all.size(); i++) {
			known.add(all.get(i).name);
		}
		for (int i = 0; i < wanted.size(); i++) {
			if (!matchesAny(wanted.get(i), known)) {
				throw new RuleException("ruleSets", "", "규칙 묶음 " + wanted.get(i) + " 이(가) 없습니다. 있는 묶음: " + known);
			}
		}
	}

	/**
	 * <pre>
	 * 이 묶음이 이 프로젝트의 폴더에 있는지. 묶음 이름의 첫 마디가 프로젝트 ID 와 같으면(대소문자 무시) 그 프로젝트의 것이다.
	 * 예: hi-japan/frontend 는 프로젝트 HI-Japan 의 것. 바로 아래 묶음(spring)은 어느 프로젝트의 것도 아니다.
	 * </pre>
	 */
	static boolean ownedBy(String setName, String projectId) {
		int slash = setName.indexOf('/');
		return projectId != null && slash > 0 && setName.substring(0, slash).equalsIgnoreCase(projectId.trim());
	}

	/** ruleSets 에 적은 이름 하나가 이 묶음을 가리키는지. 묶음 이름 그대로이거나, 묶음이 들어 있는 폴더 이름이면 맞다 */
	static boolean refersTo(String wanted, String setName) {
		return setName.equals(wanted) || setName.startsWith(wanted + "/");
	}

	private boolean isWanted(String setName, List<String> wanted) {
		for (int i = 0; i < wanted.size(); i++) {
			if (refersTo(wanted.get(i), setName)) {
				return true;
			}
		}
		return false;
	}

	private boolean matchesAny(String wanted, List<String> names) {
		for (int i = 0; i < names.size(); i++) {
			if (refersTo(wanted, names.get(i))) {
				return true;
			}
		}
		return false;
	}

	/** "a, b" → [a, b] */
	public static List<String> ruleSetNames(Object value) {
		List<String> names = new ArrayList<String>();
		if (value == null) {
			return names;
		}
		String[] items = String.valueOf(value).split("[,\\s]+");
		for (int i = 0; i < items.length; i++) {
			String item = items[i].trim();
			if (item.length() > 0 && !names.contains(item)) {
				names.add(item);
			}
		}
		return names;
	}

	private void sort(List<RuleSet> sets) {
		Collections.sort(sets, new Comparator<RuleSet>() {
			@Override
			public int compare(RuleSet a, RuleSet b) {
				int byOrder = Integer.compare(a.order, b.order);
				return byOrder != 0 ? byOrder : a.name.compareTo(b.name);
			}
		});
	}

	/**
	 * <pre>
	 * application.yml 에 남은 옛 규칙 키를 묶음 하나로 바꿉니다. 키가 하나도 없으면 null.
	 * 예전처럼 너그럽게 읽습니다(잘못 적힌 항목은 버림). 새 규칙 파일처럼 엄격하게 읽으면 지금 돌던 설정이 갑자기 서버를 못 띄우게 됩니다.
	 * </pre>
	 */
	RuleSet legacy() {
		StringBuilder all = new StringBuilder();
		String[] values = new String[LEGACY_KEYS.length];
		for (int i = 0; i < LEGACY_KEYS.length; i++) {
			values[i] = configProperty.getProperty(LEGACY_KEYS[i]);
			if (values[i] != null && values[i].trim().length() > 0) {
				all.append(LEGACY_KEYS[i]).append('=').append(values[i]).append('\n');
			}
		}
		if (all.length() == 0) {
			return null;
		}
		RuleSet.Builder b = new RuleSet.Builder();
		b.name = LEGACY_SET_NAME;
		b.description = "application.yml 의 옛 규칙 키";
		b.order = Integer.MAX_VALUE;
		b.source = "application.yml";
		b.hash = HashText.sha256(all.toString()).substring(0, 12);
		b.screenCalls.addAll(ScreenScript.parseRules(values[0]));
		b.screenFields.addAll(ScreenScript.parseFieldRules(values[1]));
		b.screenOpens.addAll(ScreenScript.parseRules(values[2]));
		b.screenMenus.addAll(ScreenMenu.parseRules(values[3]));
		if (values[4] != null) {
			String[] items = values[4].split(",");
			for (int i = 0; i < items.length; i++) {
				String item = items[i].trim();
				int eq = item.indexOf('=');
				String superType = eq <= 0 ? "" : item.substring(0, eq).trim();
				String layer = eq <= 0 ? "" : item.substring(eq + 1).trim().toUpperCase(Locale.ROOT);
				if (superType.length() > 0 && RuleSetParser.LAYERS.contains(layer)) {
					b.layerSuperTypes.add(new RuleSet.LayerSuperTypeRule(layer, Arrays.asList(superType)));
				}
			}
		}
		return b.build();
	}

}

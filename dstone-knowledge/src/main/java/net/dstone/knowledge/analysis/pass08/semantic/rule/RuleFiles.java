package net.dstone.knowledge.analysis.pass08.semantic.rule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 규칙 폴더(conf/rules)를 읽어 프로젝트마다 쓸 규칙(ProjectRules)을 만들어 줍니다.
 *
 * 폴더 모양 = 우선순위:
 *   conf/rules/
 *   ├─ projects/{프로젝트ID}/*.yml   ① 프로젝트 규칙. 그 프로젝트에만 쓴다(가장 먼저 본다)
 *   ├─ common/*.yml                  ② 공통 규칙. 모든 프로젝트에 쓴다
 *   └─ guess/*.yml                   ③ 짐작 규칙. 모든 프로젝트에 쓰지만 가장 약하다
 *
 * - 서버가 뜰 때 폴더 전체를 한 번 읽어 검사합니다. 잘못 적힌 파일이 있으면 서버가 뜨지 않습니다(파일과 줄 번호를 알려 줌).
 * - 분석할 때마다(SEMANTIC 시작) 다시 읽습니다. 규칙을 고친 뒤 서버를 다시 띄우지 않고 rerunFrom=SEMANTIC 으로 돌리면 됩니다.
 * - 다른 프로젝트의 규칙을 빌려 쓰려면 프로젝트의 ruleSets 에 그 폴더 이름(hi-japan)이나 파일(hi-japan/backend)을 적습니다.
 *   같은 프레임워크를 쓰는 배치 프로젝트나 표본 프로젝트가 이렇게 씁니다.
 * - 프로젝트 폴더 이름과 프로젝트 ID 는 대소문자를 가리지 않고 맞춥니다(projects/hi-japan → HI-Japan).
 * </pre>
 */
@Component
public class RuleFiles extends BaseObject {

	@Autowired
	private ConfigProperty configProperty;

	/** 서버가 뜰 때 규칙 파일을 모두 검사합니다. 하나라도 틀리면 서버를 띄우지 않습니다. */
	@PostConstruct
	public void check() {
		try {
			List<RuleFile> files = loadAll();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < files.size(); i++) {
				sb.append(i == 0 ? "" : ", ").append(files.get(i).name).append('(').append(files.get(i).ruleCount()).append(')');
			}
			info("의미 분석 규칙 파일 " + files.size() + "개를 읽었습니다(" + rulesDir().getPath() + "): " + sb);
			if (hasText(configProperty.getProperty("dstone.knowledge.semantic.screen.call-rules"))
					|| hasText(configProperty.getProperty("dstone.knowledge.semantic.layer.super-types"))) {
				warn("application.yml 의 옛 규칙 키(dstone.knowledge.semantic.screen.* / layer.super-types)는 더 이상 읽지 않습니다. "
						+ "conf/rules/projects/{프로젝트ID}/ 아래 규칙 파일로 옮기세요.");
			}
		} catch (RuleException e) {
			throw new IllegalStateException("의미 분석 규칙 파일이 잘못 적혀 있습니다. " + e.getMessage(), e);
		}
	}

	/** 규칙 폴더. dstone.knowledge.semantic.rules-dir 이 없으면 설정 폴더(APP_CONF_DIR) 아래 rules */
	public File rulesDir() {
		String configured = configProperty.getProperty("dstone.knowledge.semantic.rules-dir");
		if (hasText(configured)) {
			return new File(configured.trim());
		}
		String confDir = System.getProperty("APP_CONF_DIR");
		return new File(hasText(confDir) ? confDir.trim() : "conf", "rules");
	}

	/**
	 * <pre>
	 * 폴더의 규칙 파일을 모두 읽습니다(우선순위, 이름 순서).
	 * </pre>
	 *
	 * @throws RuleException 잘못 적힌 파일이 있거나 폴더가 없을 때
	 */
	public List<RuleFile> loadAll() {
		File dir = rulesDir();
		if (!dir.isDirectory()) {
			throw new RuleException(dir.getPath(), 0, "규칙 폴더가 없습니다. 기본 규칙(common/spring.yml ...)도 이 폴더에 있어서, 없으면 진입점과 계층을 하나도 찾지 못합니다.");
		}
		List<String> paths = new ArrayList<String>();
		collect(dir, "", paths, 0);
		Collections.sort(paths);
		RuleFileParser parser = new RuleFileParser(dir);
		List<RuleFile> files = new ArrayList<RuleFile>();
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < paths.size(); i++) {
			String path = paths.get(i);
			String text;
			try {
				text = new String(Files.readAllBytes(new File(dir, path).toPath()), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new RuleException(path, 0, "파일을 읽지 못했습니다: " + e.getMessage());
			}
			RuleFile file = parser.parse(text, path);
			if (names.contains(file.name)) {
				throw new RuleException(path, 0, "같은 이름의 규칙 파일이 또 있습니다(.yml 과 .yaml 이 함께 있는지 보세요): " + file.name);
			}
			names.add(file.name);
			files.add(file);
		}
		return new ProjectRules(files).getFiles();
	}

	/**
	 * <pre>
	 * 폴더 아래의 규칙 파일(*.yml, *.yaml)을 규칙 폴더 안의 경로로 모읍니다. 4단계까지 봅니다.
	 * README.md, 대응표(*.csv) 같은 다른 파일은 건너뜁니다. "." 으로 시작하는 폴더도 건너뜁니다.
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
				if (depth < 4 && !name.startsWith(".")) {
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

	/**
	 * <pre>
	 * 글로 받은 규칙 파일 하나를 읽습니다(시험 적용 API).
	 * </pre>
	 *
	 * @param path 규칙 폴더 안의 경로. 이 경로의 파일이 있으면 시험하는 동안 그 대신 쓴다. 예: projects/hen/backend.yml
	 */
	public RuleFile parse(String text, String path) {
		String normalized = path == null ? "" : path.trim().replace('\\', '/');
		if (!normalized.endsWith(".yml") && !normalized.endsWith(".yaml")) {
			normalized = normalized + ".yml";
		}
		return new RuleFileParser(rulesDir()).parse(text, normalized);
	}

	/**
	 * <pre>
	 * 이 프로젝트에 쓸 규칙을 만듭니다.
	 * </pre>
	 *
	 * @param projectId 프로젝트 ID
	 * @param ruleSets  빌려 쓸 다른 프로젝트의 규칙(쉼표로 이은 폴더 · 파일 이름). 없으면 null
	 * @param trial     시험으로 더해 볼 파일. 같은 이름의 파일이 있으면 그 대신 쓴다. 없으면 null
	 * @throws RuleException 규칙 파일이 틀렸거나, ruleSets 에 없는 이름이 있을 때
	 */
	public ProjectRules rulesFor(String projectId, Object ruleSets, RuleFile trial) {
		List<RuleFile> all = loadAll();
		List<String> borrowed = borrowNames(ruleSets);
		List<RuleFile> known = new ArrayList<RuleFile>(all);
		if (trial != null) {
			known.add(trial);
		}
		for (int i = 0; i < borrowed.size(); i++) {
			if (!refersToAny(borrowed.get(i), known)) {
				throw new RuleException("ruleSets", 0, "빌려 쓸 규칙 " + borrowed.get(i) + " 이(가) " + rulesDir().getPath() + "/projects 에 없습니다. 있는 것: " + projectNames(known));
			}
		}
		List<RuleFile> chosen = new ArrayList<RuleFile>();
		for (int i = 0; i < all.size(); i++) {
			RuleFile file = all.get(i);
			if (trial != null && trial.name.equals(file.name)) {
				continue;
			}
			if (applies(file, projectId, borrowed)) {
				chosen.add(file);
			}
		}
		if (trial != null) {
			// 시험하는 파일은 프로젝트 폴더가 달라도 쓴다(무엇이 바뀌는지 보려는 것이라서).
			chosen.add(trial);
		}
		return new ProjectRules(chosen);
	}

	/** 프로젝트의 ruleSets 값이 있는 프로젝트 폴더(또는 그 안의 파일)인지 확인합니다(프로젝트를 등록할 때). 틀리면 RuleException */
	public void checkBorrowNames(Object ruleSets) {
		List<String> borrowed = borrowNames(ruleSets);
		if (borrowed.isEmpty()) {
			return;
		}
		List<RuleFile> all = loadAll();
		for (int i = 0; i < borrowed.size(); i++) {
			if (!refersToAny(borrowed.get(i), all)) {
				throw new RuleException("ruleSets", 0, "빌려 쓸 규칙 " + borrowed.get(i) + " 이(가) 없습니다. 있는 것: " + projectNames(all));
			}
		}
	}

	/**
	 * <pre>
	 * 이 파일을 이 프로젝트에 쓰는지.
	 *   common / guess  모든 프로젝트에 쓴다
	 *   projects/X      X 가 프로젝트 ID 와 같거나(대소문자 무시), 프로젝트의 ruleSets 에 X(또는 X/파일)를 적었을 때
	 * </pre>
	 */
	static boolean applies(RuleFile file, String projectId, List<String> borrowed) {
		if (file.tier != Tier.PROJECT) {
			return true;
		}
		if (projectId != null && file.projectFolder.equalsIgnoreCase(projectId.trim())) {
			return true;
		}
		for (int i = 0; i < borrowed.size(); i++) {
			if (refersTo(borrowed.get(i), file)) {
				return true;
			}
		}
		return false;
	}

	/** ruleSets 에 적은 이름 하나가 이 파일을 가리키는지. 폴더 이름(hi-japan)이거나 폴더/파일(hi-japan/backend)이면 맞다 */
	static boolean refersTo(String borrowed, RuleFile file) {
		if (file.tier != Tier.PROJECT) {
			return false;
		}
		String relative = file.name.substring((Tier.PROJECT.getFolder() + "/").length());
		return relative.equalsIgnoreCase(borrowed) || relative.toLowerCase(Locale.ROOT).startsWith(borrowed.toLowerCase(Locale.ROOT) + "/");
	}

	/** 빌려 쓰겠다고 적은 이름(borrowed)이 이 파일들 중 하나를 가리키는지 */
	private static boolean refersToAny(String borrowed, List<RuleFile> files) {
		for (int i = 0; i < files.size(); i++) {
			if (refersTo(borrowed, files.get(i))) {
				return true;
			}
		}
		return false;
	}

	/** 읽은 규칙 파일 가운데 projects/ 아래 것들의 프로젝트 폴더 이름(겹치지 않게) */
	private static List<String> projectNames(List<RuleFile> files) {
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < files.size(); i++) {
			if (files.get(i).tier == Tier.PROJECT && !names.contains(files.get(i).projectFolder)) {
				names.add(files.get(i).projectFolder);
			}
		}
		return names;
	}

	/** "hi-japan, projects/other/backend" → [hi-japan, other/backend]. 앞의 projects/ 는 떼어 낸다 */
	public static List<String> borrowNames(Object value) {
		List<String> names = new ArrayList<String>();
		if (value == null) {
			return names;
		}
		String[] items = String.valueOf(value).split("[,\\s]+");
		for (int i = 0; i < items.length; i++) {
			String item = items[i].trim().replace('\\', '/');
			if (item.startsWith(Tier.PROJECT.getFolder() + "/")) {
				item = item.substring(Tier.PROJECT.getFolder().length() + 1);
			}
			item = item.replaceFirst("\\.ya?ml$", "");
			if (item.length() > 0 && !names.contains(item)) {
				names.add(item);
			}
		}
		return names;
	}

	/** null이 아니고 공백이 아닌 글자가 있는지 */
	private static boolean hasText(String value) {
		return value != null && value.trim().length() > 0;
	}

}

package net.dstone.knowledge.semantic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.scanner.EncodingDetector;

/**
 * <pre>
 * 화면 파일에서 화면명을 찾지 못한 화면에, 메뉴 목록에 적힌 메뉴 이름을 화면명으로 채웁니다.
 *
 * 화면 파일의 &lt;title&gt; / titletext 는 JspPlugin(500)과 RichClientPlugin(510)이 먼저 찾습니다. 이 플러그인은 그 뒤에 돌고,
 * 거기서 화면명이 생긴 화면은 건드리지 않습니다(화면 파일에 적힌 것이 먼저다).
 *
 * 메뉴 목록은 프로젝트마다 적는 곳과 키 이름이 달라서 설정으로 받습니다.
 *   dstone.knowledge.semantic.screen.menu-rules: "MENU_NM=SRC_PATH"   (화면명 키=화면 경로 키, 쉼표로 여럿)
 * JSP와 XML 파일 가운데 경로 키가 글자로 들어 있는 것만 읽어 메뉴 항목을 찾습니다(ScreenMenu).
 * 메뉴의 화면 경로는 "그 경로로 끝나는 화면"으로 찾고, 화면이 하나로 정해질 때만 씁니다(RichClientPlugin.findScreen).
 * 한 화면이 메뉴 여러 곳에 있으면 파일 순서로 먼저 나온 이름을 씁니다.
 *
 * 남기는 것: analysis_resource SCREEN_NAME (properties_json = {via: MENU, menuFile, menuLine, menuPath}, line_start 는 없음)
 * 규칙이 비어 있거나 메뉴 목록이 없는 프로젝트에서는 아무 일도 하지 않습니다.
 * 메뉴를 DB에 두는 프로젝트는 소스에 메뉴 목록이 없으므로 이 플러그인으로는 채울 수 없습니다.
 * </pre>
 */
@Component
public class ScreenMenuPlugin implements SemanticPlugin {

	/** 메뉴의 화면 경로가 가리킬 수 있는 화면 종류(analysis_file.file_type) */
	private static final List<String> SCREEN_TYPES = Arrays.asList("JSP", "WEBSQUARE", "NEXACRO");

	/** 메뉴 목록이 들어 있을 수 있는 파일의 언어(analysis_file.language) */
	private static final List<String> MENU_LANGUAGES = Arrays.asList("JSP", "XML");

	private static final int PAGE_SIZE = 200;

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Autowired
	private ConfigProperty configProperty;

	private final ScreenMenu screenMenu = new ScreenMenu();

	@Override
	public String name() {
		return "screenMenu";
	}

	@Override
	public int order() {
		// 화면 파일 안의 화면명(JSP 500, 리치클라이언트 510)이 먼저 남아 있어야 빈 화면만 채울 수 있다.
		return 520;
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		List<ScreenMenu.Rule> rules = ScreenMenu.parseRules(configProperty.getProperty("dstone.knowledge.semantic.screen.menu-rules"));
		if (rules.isEmpty()) {
			return "메뉴 규칙 없음.";
		}
		long revisionId = context.getRevisionId();
		Set<Long> named = new HashSet<Long>(semanticDao.selectScreenNamedFileIds(revisionId));
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();

		int menuFiles = 0, entries = 0, filled = 0, notFound = 0;
		long afterId = 0;
		while (true) {
			context.checkCancelled();
			List<Map<String, Object>> page = semanticDao.selectFilePageByLanguages(revisionId, MENU_LANGUAGES, afterId, PAGE_SIZE);
			if (page.isEmpty()) {
				break;
			}
			for (int i = 0; i < page.size(); i++) {
				Map<String, Object> file = page.get(i);
				afterId = ((Number) file.get("fileId")).longValue();
				String text;
				try {
					byte[] bytes = Files.readAllBytes(root.resolve((String) file.get("path")));
					text = encodingDetector.decode(bytes, (String) file.get("encoding"));
				} catch (Exception e) {
					// 파일 하나를 못 읽었다고 전체를 멈추지 않는다.
					continue;
				}
				List<ScreenMenu.Entry> found = screenMenu.findEntries(text, rules);
				if (found.isEmpty()) {
					continue;
				}
				menuFiles++;
				for (int e = 0; e < found.size(); e++) {
					ScreenMenu.Entry entry = found.get(e);
					entries++;
					Long screen = RichClientPlugin.findScreen(semanticDao, revisionId, entry.getPath(), SCREEN_TYPES);
					if (screen == null) {
						notFound++;
						continue;
					}
					if (!named.add(screen)) {
						continue;
					}
					Map<String, Object> properties = new LinkedHashMap<String, Object>();
					properties.put("via", "MENU");
					properties.put("menuFile", file.get("path"));
					properties.put("menuLine", Integer.valueOf(entry.getLine()));
					properties.put("menuPath", entry.getPath());
					semanticDao.insertScreenName(revisionId, screen.longValue(), entry.getPath(), entry.getName(), properties, null);
					filled++;
				}
			}
		}
		return "메뉴 목록 파일 " + menuFiles + "개, 메뉴 항목 " + entries + "개. 메뉴 이름으로 화면명을 채운 화면 " + filled
				+ ", 화면을 찾지 못한 메뉴 " + notFound + ".";
	}
}

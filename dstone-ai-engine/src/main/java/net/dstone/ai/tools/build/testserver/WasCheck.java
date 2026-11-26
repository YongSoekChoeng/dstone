package net.dstone.ai.tools.build.testserver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 원래 WAS용 앱을 Tomcat으로 띄울 수 있는지 판정하고, 띄울 때 필요한 것(javax/jakarta, 컨텍스트 경로)을 읽습니다.
 * 설계: docs/temp/dstone-ai-테스트관련재설계/01.설계.md 5.2·5.3절.
 *
 * 불가: EJB(javax.ejb·jakarta.ejb import, @Stateless 등, ejb-jar.xml), 벤더 API import(설정 vendor-api-prefixes),
 *       WebLogic startup/shutdown class. 플랫폼 이름이 아니라 코드로 판정합니다.
 * 결과는 리비전마다 한 번만 구해 {리비전}/was-check.properties 에 둡니다(리비전은 빌드 뒤 바뀌지 않음).
 * </pre>
 */
public final class WasCheck {

	/** 판정 결과 */
	public static final class Result {
		/** 띄울 수 없는 이유. 띄울 수 있으면 null */
		public String blocker;
		/** jakarta.* 앱(Tomcat 10 이상)인가 */
		public boolean jakarta;
		/** 벤더 설정에서 읽은 컨텍스트 경로. 없으면 null */
		public String contextPath;
	}

	private static final String CACHE = "was-check.properties";
	private static final Set<String> SKIP = Set.of(".git", ".svn", "node_modules", ".gradle", ".idea", "target", "build");
	private static final Pattern EJB = Pattern.compile("(?m)^\\s*import\\s+(javax|jakarta)\\.ejb\\.|@(Stateless|Stateful|MessageDriven|Singleton)\\b(?!\\s*\\()");
	private static final Pattern WEBLOGIC_STARTUP = Pattern.compile("<(wls:)?(startup|shutdown)\\b");
	private static final Pattern CONTEXT_ROOT = Pattern.compile("<(?:wls:)?context-root>\\s*([^<\\s]+)\\s*</");
	private static final Pattern CONTEXT_PATH = Pattern.compile("<context-path>\\s*([^<\\s]+)\\s*</");
	private static final Pattern IBM_ROOT = Pattern.compile("context-root\\s+uri=\"([^\"]+)\"");
	private static final Pattern JAKARTA_LIB = Pattern.compile("^(jakarta\\.servlet-api|spring-web(mvc)?-(6|7)\\.|tomcat-embed-core-(10|11)\\.).*\\.jar$");

	private WasCheck() {
	}

	/**
	 * @param revDir         리비전 폴더(결과를 둠)
	 * @param src            리비전 소스
	 * @param webRoot        풀린 웹 루트(없으면 null)
	 * @param libDir         lib 폴더(없으면 null)
	 * @param vendorPrefixes 벤더 API 패키지 앞부분들
	 */
	public static Result check(File revDir, File src, File webRoot, File libDir, List<String> vendorPrefixes) throws IOException {
		File cache = new File(revDir, CACHE);
		Result result = new Result();
		if (cache.isFile()) {
			Properties props = new Properties();
			try (java.io.Reader reader = Files.newBufferedReader(cache.toPath(), StandardCharsets.UTF_8)) {
				props.load(reader);
			}
			result.blocker = props.getProperty("blocker");
			result.jakarta = "true".equals(props.getProperty("jakarta"));
			result.contextPath = props.getProperty("contextPath");
			return result;
		}
		final List<String> blockers = new ArrayList<String>();
		final boolean[] jakartaSource = new boolean[1];
		final String[] contextPath = new String[1];
		final List<Pattern> vendors = new ArrayList<Pattern>();
		for (String prefix : vendorPrefixes) {
			if (!prefix.isBlank()) {
				vendors.add(Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?" + Pattern.quote(prefix.trim())));
			}
		}
		final Path base = src.toPath();
		Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
				return SKIP.contains(name) && !dir.equals(base) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String name = file.getFileName().toString();
				String lower = name.toLowerCase(Locale.ROOT);
				String rel = base.relativize(file).toString().replace('\\', '/');
				if (lower.endsWith(".java") && attrs.size() < 2_000_000) {
					String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
					if (blockers.size() < 5 && EJB.matcher(text).find()) {
						blockers.add("EJB: " + rel);
					}
					for (Pattern vendor : vendors) {
						Matcher m = vendor.matcher(text);
						if (blockers.size() < 5 && m.find()) {
							blockers.add("벤더 API: " + rel + " (" + text.substring(m.start(), Math.min(text.length(), m.end() + 40)).trim().split("\\s*;")[0] + ")");
							break;
						}
					}
					if (!jakartaSource[0] && text.contains("import jakarta.servlet.")) {
						jakartaSource[0] = true;
					}
				} else if ("ejb-jar.xml".equals(lower)) {
					blockers.add("EJB: " + rel);
				} else if ("weblogic-application.xml".equals(lower)) {
					if (WEBLOGIC_STARTUP.matcher(Files.readString(file, StandardCharsets.ISO_8859_1)).find()) {
						blockers.add("WebLogic startup/shutdown class: " + rel);
					}
				} else if (contextPath[0] == null && ("weblogic.xml".equals(lower) || "jeus-web-dd.xml".equals(lower) || "ibm-web-ext.xml".equals(lower) || "ibm-web-ext.xmi".equals(lower))) {
					String text = Files.readString(file, StandardCharsets.ISO_8859_1);
					Matcher m = "jeus-web-dd.xml".equals(lower) ? CONTEXT_PATH.matcher(text) : lower.startsWith("ibm") ? IBM_ROOT.matcher(text) : CONTEXT_ROOT.matcher(text);
					if (m.find()) {
						String path = m.group(1).trim();
						contextPath[0] = path.startsWith("/") ? path : "/" + path;
					}
				}
				return FileVisitResult.CONTINUE;
			}
		});
		result.blocker = blockers.isEmpty() ? null : "Tomcat으로 바꿔 띄울 수 없는 앱입니다(서버 기동 테스트 불가): " + String.join(", ", blockers);
		result.contextPath = contextPath[0];
		result.jakarta = jakartaSource[0] || jakartaWebXml(webRoot) || jakartaLib(libDir);
		Properties props = new Properties();
		if (result.blocker != null) {
			props.setProperty("blocker", result.blocker);
		}
		props.setProperty("jakarta", String.valueOf(result.jakarta));
		if (result.contextPath != null) {
			props.setProperty("contextPath", result.contextPath);
		}
		try (java.io.Writer writer = Files.newBufferedWriter(cache.toPath(), StandardCharsets.UTF_8)) {
			props.store(writer, "dstone WasCheck");
		}
		return result;
	}

	private static boolean jakartaWebXml(File webRoot) throws IOException {
		File webXml = webRoot == null ? null : new File(webRoot, "WEB-INF/web.xml");
		return webXml != null && webXml.isFile() && Files.readString(webXml.toPath(), StandardCharsets.ISO_8859_1).contains("jakarta.ee/xml/ns/jakartaee");
	}

	static boolean jakartaLib(File libDir) {
		File[] jars = libDir == null ? null : libDir.listFiles();
		if (jars != null) {
			for (File jar : jars) {
				if (JAKARTA_LIB.matcher(jar.getName()).matches()) {
					return true;
				}
			}
		}
		return false;
	}

}

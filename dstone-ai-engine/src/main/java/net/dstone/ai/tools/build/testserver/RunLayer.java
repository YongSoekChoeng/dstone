package net.dstone.ai.tools.build.testserver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dstone.ai.runtime.tool.ToolOutcome;
import net.dstone.ai.tools.build.JavaCompileTool;
import net.dstone.ai.tools.build.sandbox.SandboxPaths;

/**
 * <pre>
 * 테스트 서버의 실행 층(runs/{실행ID}의 webapp, classes)을 만듭니다. 설계: docs/temp/dstone-ai-테스트관련재설계/01.설계.md 5.1·5.5·6.2절.
 *
 * 1. 합친 변경(overlay): 리비전 소스 가운데 바꿔야 하는 파일 + 개발 결과(dev-resources). 바꾸는 것은 셋입니다.
 *    - 원본 소스 디렉토리의 절대경로 → runs/{실행ID}/data (테스트 중 업로드·삭제가 원본 폴더에서 일어나지 않게)
 *    - 프로젝트 DB의 JDBC URL → jdbc:dstonetest:{원래 URL}, 알려진 드라이버 클래스 → 테스트 드라이버 (개발 DB를 원복하려고)
 *    - JNDI 이름(프로젝트 DB의 JNDI 이름)을 java:comp/env 없이 찾는 리터럴 → java:comp/env/… (Tomcat은 그 아래에 둠)
 *    리비전 안에서 바꿀 후보 파일은 리비전마다 한 번만 찾아 둡니다(scan-*.txt).
 * 2. 합친 변경을 자리에 놓습니다. 웹 쪽 파일 → webapp, 리소스·소스 폴더의 Java 밖 파일 → classes, .java → compileJava로 컴파일해 classes.
 * 3. 삭제 목록 가운데 웹 쪽 파일은 RewriteValve 규칙(WEB-INF/rewrite.config)으로 막습니다(410).
 * 결과는 stage 폴더에 만들고, 테스트 서버가 떠 있는 층과 비교해 바뀐 것만 옮깁니다(sync).
 * </pre>
 */
public final class RunLayer {

	/** 만들 재료 */
	public static final class Spec {
		public File revDir;
		public File revSrc;
		/** 리비전의 웹 루트(Boot면 null) */
		public File webRoot;
		public File classesDir;
		public File libDir;
		/** 원본 소스 디렉토리(프로젝트 관리 값). 이 경로를 data로 바꿉니다 */
		public String originalSource;
		public File dataDir;
		/** 개발 결과 폴더(없으면 null) */
		public File overlayDir;
		/** 삭제 목록 파일(없으면 null) */
		public File deleteList;
		/** 만들 곳(webapp, classes, overlay, compile이 생김) */
		public File stageDir;
		/** 프로젝트 DB의 JDBC URL들 */
		public List<String> dbUrls = new ArrayList<String>();
		/** 프로젝트 DB의 JNDI 이름들(java:comp/env 뺀 것) */
		public List<String> jndiNames = new ArrayList<String>();
		/** 소스 폴더(리비전 소스 기준). 비우면 흔한 이름에서 찾음 */
		public List<String> sourceDirs = new ArrayList<String>();
		public int release;
		public boolean boot;
	}

	/** 만든 결과 */
	public static final class Result {
		/** 개발 결과 탓의 실패(컴파일 오류). 없으면 null */
		public String codeProblem;
		public List<String> notes = new ArrayList<String>();
		/** URL을 바꿔 넣은 프로젝트 DB URL들 */
		public Set<String> urlsFound = new LinkedHashSet<String>();
		/** 경로를 바꾼 파일 수 */
		public int pathFiles;
		/** Spring Boot 설정 파일의 datasource URL을 바꿨는가 */
		public boolean bootDatasource;
	}

	private static final Set<String> SKIP = Set.of(".git", ".svn", "node_modules", ".gradle", ".idea", "target", "build", "bin", "out");

	private static final List<String> DRIVER_CLASSES = Arrays.asList("com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver", "org.mariadb.jdbc.Driver",
		"org.postgresql.Driver", "oracle.jdbc.OracleDriver", "oracle.jdbc.driver.OracleDriver", "net.sf.log4jdbc.sql.jdbcapi.DriverSpy", "net.sf.log4jdbc.DriverSpy");

	/** 웹 쪽 파일이 있는 흔한 폴더(리비전 소스 기준) */
	private static final String[] WEB_SOURCES = { "src/main/webapp", "WebContent", "WebRoot", "web", "webapp" };

	/** Java 밖 파일을 classes로 보내는 흔한 폴더 */
	private static final String[] RESOURCE_SOURCES = { "src/main/resources", "src/main/java", "src", "JavaSource", "resources" };

	private RunLayer() {
	}

	/** 실행 층을 stage 폴더에 만듭니다. */
	public static Result build(Spec spec, JavaCompileTool compiler) throws IOException {
		Result result = new Result();
		File overlay = new File(spec.stageDir, "overlay");
		File webapp = new File(spec.stageDir, "webapp");
		File classes = new File(spec.stageDir, "classes");
		for (File dir : new File[] { overlay, webapp, classes }) {
			deleteTree(dir.toPath());
			Files.createDirectories(dir.toPath());
		}
		Transformer transformer = new Transformer(spec);

		// 1) 리비전에서 바꿔야 하는 파일
		for (String rel : candidates(spec, transformer)) {
			File file = new File(spec.revSrc, rel);
			String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
			String changed = transformer.apply(rel, raw, result);
			if (!changed.equals(raw)) {
				write(new File(overlay, rel), changed);
			}
		}
		// 2) 개발 결과(바꾸기도 함께)
		if (spec.overlayDir != null && spec.overlayDir.isDirectory()) {
			for (String rel : listFiles(spec.overlayDir)) {
				File from = new File(spec.overlayDir, rel);
				File to = new File(overlay, rel);
				if (isText(rel)) {
					String raw = new String(Files.readAllBytes(from.toPath()), StandardCharsets.ISO_8859_1);
					write(to, transformer.apply(rel, raw, result));
				} else {
					Files.createDirectories(to.getParentFile().toPath());
					Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		Set<String> deleted = readDeleteList(spec.deleteList);

		// 3) 자리에 놓기
		String webSource = webSourceOf(spec);
		List<String> resourceSources = resourceSourcesOf(spec);
		boolean hasJava = false;
		List<String> ignored = new ArrayList<String>();
		for (String rel : listFiles(overlay)) {
			if (deleted.contains(rel)) {
				continue;
			}
			if (rel.endsWith(".java")) {
				hasJava = true;
				continue;
			}
			File from = new File(overlay, rel);
			if (webSource != null && rel.startsWith(webSource + "/")) {
				copy(from, new File(webapp, rel.substring(webSource.length() + 1)));
				continue;
			}
			String resourceRoot = prefixOf(rel, resourceSources);
			if (resourceRoot != null) {
				copy(from, new File(classes, rel.substring(resourceRoot.length() + 1)));
				continue;
			}
			ignored.add(rel);
		}
		if (!ignored.isEmpty()) {
			result.notes.add("테스트 서버에 놓을 자리를 모르는 파일은 반영하지 않았습니다(빌드 설정이면 다시 [Build]가 필요): "
				+ String.join(", ", ignored.subList(0, Math.min(10, ignored.size()))) + (ignored.size() > 10 ? " 외 " + (ignored.size() - 10) + "개" : ""));
		}
		if (hasJava) {
			File compileOut = new File(spec.stageDir, "compile");
			List<String> classesDirs = new ArrayList<String>();
			classesDirs.add(spec.classesDir.getAbsolutePath());
			List<String> libs = new ArrayList<String>();
			if (spec.libDir != null && spec.libDir.isDirectory()) {
				libs.add(spec.libDir.getAbsolutePath());
			}
			ToolOutcome outcome = compiler.compileJava(spec.revSrc.getAbsolutePath(), overlay.getAbsolutePath(), compileOut.getAbsolutePath(),
				spec.deleteList == null ? null : spec.deleteList.getAbsolutePath(), spec.sourceDirs.isEmpty() ? null : spec.sourceDirs, classesDirs, libs,
				spec.release > 0 ? Integer.valueOf(spec.release) : null, null);
			if (!Boolean.TRUE.equals(outcome.success())) {
				result.codeProblem = outcome.message();
				return result;
			}
			File compiled = new File(compileOut, "classes");
			if (compiled.isDirectory()) {
				copyTree(compiled.toPath(), classes.toPath());
			}
		}
		// 4) 삭제 목록(웹 쪽 파일은 410으로 막고, 나머지는 알린다)
		List<String> rules = new ArrayList<String>();
		List<String> notHidden = new ArrayList<String>();
		for (String rel : deleted) {
			if (webSource != null && rel.startsWith(webSource + "/") && !rel.startsWith(webSource + "/WEB-INF/")) {
				rules.add("RewriteRule ^/" + rel.substring(webSource.length() + 1).replaceAll("([.\\[\\]{}()*+?^$|\\\\])", "\\\\$1") + "$ - [G]");
			} else if (!rel.endsWith(".java")) {
				notHidden.add(rel);
			}
		}
		if (!rules.isEmpty() && !spec.boot) {
			write(new File(webapp, "WEB-INF/rewrite.config"), new String((String.join("\n", rules) + "\n").getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
		}
		if (!notHidden.isEmpty()) {
			result.notes.add("삭제 목록 가운데 테스트 서버에서 감출 수 없어 리비전의 것이 그대로 남은 파일: " + String.join(", ", notHidden));
		}
		if (!deleted.isEmpty()) {
			result.notes.add("삭제 목록의 Java 클래스는 리비전의 클래스 파일이 남아 있습니다(사용처는 컴파일 확인이 잡음).");
		}
		return result;
	}

	/**
	 * <pre>
	 * stage에 만든 층을 떠 있는 층(live)으로 옮깁니다. 바뀐 파일만 복사하고, 없어진 파일은 지웁니다.
	 * 돌려주는 값: 바뀐 파일의 live 기준 상대경로들
	 * </pre>
	 */
	public static List<String> sync(File stage, File live) throws IOException {
		List<String> changed = new ArrayList<String>();
		Files.createDirectories(live.toPath());
		Set<String> stageFiles = new LinkedHashSet<String>(listFiles(stage));
		for (String rel : stageFiles) {
			File from = new File(stage, rel);
			File to = new File(live, rel);
			if (!to.isFile() || to.length() != from.length() || !Arrays.equals(digest(from), digest(to))) {
				copy(from, to);
				changed.add(rel);
			}
		}
		for (String rel : listFiles(live)) {
			if (!stageFiles.contains(rel)) {
				Files.deleteIfExists(new File(live, rel).toPath());
				changed.add(rel);
			}
		}
		return changed;
	}

	/* ---------------------------------------------------------------- 바꾸기 */

	/** 파일 하나의 글을 바꾸는 규칙들 */
	static final class Transformer {
		private final SandboxPaths paths;
		private final List<Pattern> urlPatterns = new ArrayList<Pattern>();
		private final List<String> urls = new ArrayList<String>();
		private final List<String> jndiNames;
		private final List<String> needles = new ArrayList<String>();

		Transformer(Spec spec) {
			this.paths = spec.originalSource == null || spec.originalSource.isBlank() ? null : new SandboxPaths(spec.originalSource, spec.dataDir.getAbsolutePath());
			if (this.paths != null) {
				this.needles.addAll(this.paths.needles());
			}
			for (String url : spec.dbUrls) {
				String core = coreOf(url);
				if (core == null) {
					continue;
				}
				this.urls.add(url);
				this.urlPatterns.add(Pattern.compile("(?<!dstonetest:)jdbc:((?:log4jdbc:)?" + Pattern.quote(core) + ")(?![A-Za-z0-9_])"));
				this.needles.add(core);
			}
			this.jndiNames = spec.jndiNames;
			this.needles.addAll(spec.jndiNames);
			this.needles.addAll(DRIVER_CLASSES);
		}

		List<String> needles() {
			return this.needles;
		}

		/** raw는 ISO-8859-1로 읽은 바이트 그대로의 글입니다. */
		String apply(String rel, String raw, Result result) {
			String text = raw;
			if (this.paths != null) {
				String shifted = this.paths.shiftRaw(text);
				if (!shifted.equals(text)) {
					result.pathFiles++;
					text = shifted;
				}
			}
			boolean urlChanged = false;
			for (int i = 0; i < this.urlPatterns.size(); i++) {
				Matcher m = this.urlPatterns.get(i).matcher(text);
				if (m.find()) {
					text = m.replaceAll(Matcher.quoteReplacement(TestDriverJar.URL_PREFIX) + "jdbc:$1");
					result.urlsFound.add(this.urls.get(i));
					urlChanged = true;
				}
			}
			String lower = rel.toLowerCase(Locale.ROOT);
			if (urlChanged && (lower.matches(".*/application[^/]*\\.(yml|yaml|properties)$") || lower.matches("application[^/]*\\.(yml|yaml|properties)$"))) {
				result.bootDatasource = true;
			}
			boolean java = lower.endsWith(".java");
			for (String driver : DRIVER_CLASSES) {
				String quoted = Pattern.quote(driver);
				text = java ? text.replaceAll("\"" + quoted + "\"", Matcher.quoteReplacement("\"" + TestDriverJar.DRIVER_CLASS + "\""))
					: text.replaceAll("(?<![A-Za-z0-9_.])" + quoted + "(?![A-Za-z0-9_.])", Matcher.quoteReplacement(TestDriverJar.DRIVER_CLASS));
			}
			boolean descriptor = lower.endsWith("web.xml") || lower.endsWith("context.xml");
			if (!descriptor) {
				for (String name : this.jndiNames) {
					text = text.replaceAll("([\"'>])" + Pattern.quote(name) + "([\"'<])", "$1" + Matcher.quoteReplacement("java:comp/env/" + name) + "$2");
				}
			}
			return text;
		}
	}

	/** JDBC URL에서 "jdbc:"와 뒤의 옵션(?·;)을 뗀 핵심 부분입니다. 예: mysql://localhost:3306/app */
	static String coreOf(String url) {
		if (url == null || !url.startsWith("jdbc:")) {
			return null;
		}
		String core = url.substring(5);
		if (core.startsWith("log4jdbc:")) {
			core = core.substring(9);
		}
		int cut = core.length();
		for (char c : new char[] { '?', ';' }) {
			int at = core.indexOf(c);
			if (at > 0 && at < cut) {
				cut = at;
			}
		}
		return core.substring(0, cut);
	}

	/** 리비전에서 바꿀 후보 파일(리비전 소스 기준 상대경로). 리비전마다 한 번만 찾아 둡니다. */
	private static List<String> candidates(Spec spec, Transformer transformer) throws IOException {
		String key = String.join("\n", transformer.needles());
		File cache = new File(spec.revDir, "scan-" + hex(sha(key.getBytes(StandardCharsets.UTF_8))).substring(0, 12) + ".txt");
		if (cache.isFile()) {
			List<String> lines = Files.readAllLines(cache.toPath(), StandardCharsets.UTF_8);
			List<String> result = new ArrayList<String>();
			for (String line : lines) {
				if (!line.isBlank()) {
					result.add(line);
				}
			}
			return result;
		}
		List<String> raws = new ArrayList<String>();
		for (String needle : transformer.needles()) {
			raws.add(new String(needle.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
		}
		List<String> result = new ArrayList<String>();
		for (String rel : listSource(spec.revSrc)) {
			if (!isText(rel)) {
				continue;
			}
			File file = new File(spec.revSrc, rel);
			if (file.length() > 5L * 1024 * 1024) {
				continue;
			}
			String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
			for (String needle : raws) {
				if (raw.contains(needle) || (needle.length() > 2 && needle.matches("^[A-Za-z]:.*") && raw.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT)))) {
					result.add(rel);
					break;
				}
			}
		}
		Files.writeString(cache.toPath(), String.join("\n", result) + "\n", StandardCharsets.UTF_8);
		return result;
	}

	/** 웹 쪽 파일이 있는 폴더(리비전 소스 기준). 리비전의 웹 루트가 소스 안이면 그것, 아니면 흔한 이름. 없으면 null */
	static String webSourceOf(Spec spec) {
		if (spec.boot) {
			return null;
		}
		if (spec.webRoot != null) {
			String rel = relativeOrNull(spec.revSrc, spec.webRoot);
			if (rel != null && !rel.startsWith("target/") && !rel.startsWith("build/") && !rel.startsWith("dist/")) {
				return rel;
			}
		}
		for (String candidate : WEB_SOURCES) {
			if (new File(spec.revSrc, candidate).isDirectory()) {
				return candidate;
			}
		}
		return null;
	}

	private static List<String> resourceSourcesOf(Spec spec) {
		List<String> result = new ArrayList<String>();
		for (String dir : spec.sourceDirs) {
			result.add(dir.replace('\\', '/').replaceAll("/+$", ""));
		}
		for (String candidate : RESOURCE_SOURCES) {
			if (new File(spec.revSrc, candidate).isDirectory() && !result.contains(candidate)) {
				result.add(candidate);
			}
		}
		// 긴 것부터(src/main/resources가 src보다 먼저)
		result.sort(new java.util.Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				return b.length() - a.length();
			}
		});
		return result;
	}

	private static String prefixOf(String rel, List<String> prefixes) {
		for (String prefix : prefixes) {
			if (rel.startsWith(prefix + "/")) {
				return prefix;
			}
		}
		return null;
	}

	private static Set<String> readDeleteList(File file) throws IOException {
		Set<String> result = new LinkedHashSet<String>();
		if (file != null && file.isFile()) {
			for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
				String rel = line.trim().replace('\\', '/');
				while (rel.startsWith("/")) {
					rel = rel.substring(1);
				}
				if (!rel.isEmpty() && !rel.startsWith("#") && !rel.contains("..")) {
					result.add(rel);
				}
			}
		}
		return result;
	}

	static boolean isText(String rel) {
		int dot = rel.lastIndexOf('.');
		return dot >= 0 && SandboxPaths.TEXT_EXTENSIONS.contains(rel.substring(dot + 1).toLowerCase(Locale.ROOT));
	}

	/* ---------------------------------------------------------------- 파일 도우미 */

	/** 폴더 아래 모든 파일의 상대경로들(형상관리 폴더만 제외) */
	static List<String> listFiles(File root) throws IOException {
		return list(root, false);
	}

	/** 소스 폴더 아래 파일의 상대경로들(루트의 빌드 결과 폴더와 형상관리 폴더 제외) */
	static List<String> listSource(File root) throws IOException {
		return list(root, true);
	}

	private static List<String> list(final File root, final boolean skipBuildOutputs) throws IOException {
		final List<String> result = new ArrayList<String>();
		if (!root.isDirectory()) {
			return result;
		}
		final Path base = root.toPath();
		Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
				return skipBuildOutputs && !dir.equals(base) && SKIP.contains(name) && base.relativize(dir).getNameCount() == 1 ? FileVisitResult.SKIP_SUBTREE
					: (name.equals(".git") || name.equals(".svn")) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				result.add(base.relativize(file).toString().replace('\\', '/'));
				return FileVisitResult.CONTINUE;
			}
		});
		return result;
	}

	private static void write(File file, String raw) throws IOException {
		Files.createDirectories(file.getParentFile().toPath());
		Files.write(file.toPath(), raw.getBytes(StandardCharsets.ISO_8859_1));
	}

	private static void copy(File from, File to) throws IOException {
		Files.createDirectories(to.getParentFile().toPath());
		Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	public static void copyTree(final Path from, final Path to) throws IOException {
		Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(to.resolve(from.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	public static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		if (!Files.isDirectory(root)) {
			Files.delete(root);
			return;
		}
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** 지우되 실패해도 넘어갑니다(파일이든 폴더든). */
	public static void deleteTreeQuietly(File file) {
		try {
			if (file != null) {
				deleteTree(file.toPath());
			}
		} catch (IOException e) {
			// 남아도 다음에 다시 지운다
		}
	}

	private static String relativeOrNull(File base, File file) {
		Path b = base.toPath().toAbsolutePath().normalize();
		Path f = file.toPath().toAbsolutePath().normalize();
		return f.startsWith(b) ? b.relativize(f).toString().replace('\\', '/') : null;
	}

	private static byte[] digest(File file) throws IOException {
		return sha(Files.readAllBytes(file.toPath()));
	}

	private static byte[] sha(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder();
		for (byte b : bytes) {
			out.append(String.format("%02x", Integer.valueOf(b & 0xff)));
		}
		return out.toString();
	}

	/** 결과 맵(테스트용) */
	static Map<String, Object> describe(Result result) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("codeProblem", result.codeProblem);
		map.put("urlsFound", result.urlsFound);
		map.put("pathFiles", Integer.valueOf(result.pathFiles));
		map.put("notes", result.notes);
		return map;
	}

}

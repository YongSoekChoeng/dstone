package net.dstone.ai.tools.build.sandbox;

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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 사본과 복제 데이터에 박혀 있는 원본 폴더 경로를 사본 폴더 경로로 바꿉니다.
 *
 * DB만 복제하면 부족합니다. 앱이 업로드 폴더를 절대경로로 갖고 있거나 테이블에 파일 절대경로가 들어 있으면,
 * 시험 중에 누른 [삭제]가 원본 파일을 지우기 때문입니다.
 *
 * 글자셋을 모르는 파일도 깨지 않게 바이트 그대로(ISO-8859-1로 읽어) 바꿉니다.
 * 원본 폴더 밖을 가리키는 절대경로는 바꾸지 못합니다.
 * </pre>
 */
public final class SandboxPaths {

	private static final Set<String> SKIP_DIRS = Set.of(".git", ".svn", ".idea", ".settings", ".gradle", "node_modules", "target", "build", "out");
	public static final Set<String> TEXT_EXTENSIONS = Set.of("java", "kt", "groovy", "xml", "properties", "yml", "yaml", "json", "jsp", "jspf", "js", "conf", "cfg", "ini", "sql", "txt", "md");
	private static final long MAX_TEXT_BYTES = 5L * 1024 * 1024;

	/** 일반 소켓을 쓰지 않아 SandboxGuard가 잡지 못하는 DB 드라이버의 주소 */
	private static final Pattern UNGUARDED_URL = Pattern.compile("jdbc:(?:log4jdbc:)?(oracle:(?:oci8?|kprb)|sqlserver|db2|tibero|cubrid|sap):", Pattern.CASE_INSENSITIVE);

	private final List<Pattern> from = new ArrayList<Pattern>();
	private final List<String> to = new ArrayList<String>();
	private final List<String> needles = new ArrayList<String>();

	/**
	 * @param sourceRoot 원본 폴더
	 * @param copyRoot   사본 폴더
	 */
	public SandboxPaths(String sourceRoot, String copyRoot) {
		String source = trimSlash(sourceRoot.replace('\\', '/'));
		String copy = trimSlash(copyRoot.replace('\\', '/'));
		boolean drive = source.matches("^[A-Za-z]:.*");
		if (drive) {
			// 긴 모양부터: D:\\App(문자열 안에서 이스케이프된 것) → D:\App
			this.add(source.replace("/", "\\\\"), copy.replace("/", "\\\\"), true);
			this.add(source.replace('/', '\\'), copy.replace('/', '\\'), true);
		}
		this.add(source, copy, drive);
	}

	private void add(String source, String copy, boolean ignoreCase) {
		// 파일은 바이트 그대로 읽으므로 찾을 글자도 UTF-8 바이트 모양으로 맞춘다
		String raw = new String(source.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
		// /app/myApp이 /app/myApp2나 /other/app/myApp 안에서 걸리지 않게 앞뒤를 본다
		this.from.add(Pattern.compile("(?<![A-Za-z0-9_.-])" + Pattern.quote(raw) + "(?![A-Za-z0-9_-]|\\.[A-Za-z0-9])", ignoreCase ? Pattern.CASE_INSENSITIVE : 0));
		this.needles.add(source);
		this.to.add(Matcher.quoteReplacement(new String(copy.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)));
	}

	/** 바꿀 경로의 글자들. DB에서 고칠 행을 먼저 찾을 때 씁니다. */
	public List<String> needles() {
		return this.needles;
	}

	/** 바이트 그대로 읽은 글자(ISO-8859-1)에서 경로를 바꿉니다. */
	public String shiftRaw(String raw) {
		String result = raw;
		for (int i = 0; i < this.from.size(); i++) {
			result = this.from.get(i).matcher(result).replaceAll(this.to.get(i));
		}
		return result;
	}

	/** 보통 글자에서 경로를 바꿉니다. */
	public String shift(String text) {
		String raw = new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
		return new String(this.shiftRaw(raw).getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
	}

	/**
	 * 사본의 텍스트 파일을 고치고, 고친 파일 수를 돌려줍니다.
	 *
	 * @param app 사본 폴더
	 */
	int rewrite(File app) throws IOException {
		int changed = 0;
		for (Path file : textFiles(app)) {
			String raw = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
			String shifted = this.shiftRaw(raw);
			if (!shifted.equals(raw)) {
				Files.write(file, shifted.getBytes(StandardCharsets.ISO_8859_1));
				changed++;
			}
		}
		return changed;
	}

	/**
	 * 잡지 못하는 DB 주소가 주석이 아닌 곳에 있으면 "파일: DB 종류"를, 없으면 null을 돌려줍니다.
	 *
	 * @param app 사본 폴더
	 */
	public static String unguardedDatabase(File app) throws IOException {
		for (Path file : textFiles(app)) {
			String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
			if (name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".sql")) {
				continue;
			}
			String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
			if (!UNGUARDED_URL.matcher(text).find()) {
				continue;
			}
			Matcher m = UNGUARDED_URL.matcher(text.replaceAll("(?s)<!--.*?-->", "").replaceAll("(?m)^[ \\t]*(#|//|\\*).*$", ""));
			if (m.find()) {
				return app.toPath().relativize(file) + ": " + m.group(1);
			}
		}
		return null;
	}

	private static List<Path> textFiles(final File app) throws IOException {
		final List<Path> files = new ArrayList<Path>();
		Files.walkFileTree(app.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				Path name = dir.getFileName();
				return !dir.equals(app.toPath()) && name != null && SKIP_DIRS.contains(name.toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
				if (attrs.isRegularFile() && attrs.size() <= MAX_TEXT_BYTES && TEXT_EXTENSIONS.contains(name.substring(name.lastIndexOf('.') + 1))) {
					files.add(file);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return files;
	}

	private static String trimSlash(String path) {
		String result = path.trim();
		while (result.length() > 1 && result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

}

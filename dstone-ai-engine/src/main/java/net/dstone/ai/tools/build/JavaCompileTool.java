package net.dstone.ai.tools.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.runtime.tool.ToolOutcome;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 바뀐 Java 파일만 원본의 컴파일 결과를 참조해 컴파일해 보는 범용 Tool입니다(compileJava).
 *
 * 원본 전체를 다시 빌드하지 않습니다. 원본 소스는 읽기만 하고(바뀐 클래스를 쓰는 파일을 찾을 때), 원본이 이미 만들어 둔 클래스 폴더와 의존성 jar를
 * 참조해 "변경 폴더"의 .java 파일만 엔진 안의 JDK 컴파일러(javax.tools)로 컴파일합니다. 그래서 걸리는 시간은
 * 시스템 크기가 아니라 바뀐 파일 수에 비례합니다(파일이 수만 개인 시스템에서도 쓰려는 것).
 *
 * 무엇을 참조할지는 인자로 직접 주거나, 주지 않으면 원본 폴더를 보고 찾습니다. 빌드 도구를 가정하지 않습니다.
 *   Maven(pom.xml)          소스 src/main/java, 클래스 target/classes, 의존성은 mvn dependency:build-classpath(한 번 구해 두고 pom.xml이 바뀔 때만 다시)
 *   Gradle(build.gradle)    소스 src/main/java, 클래스 build/classes/java/main, 의존성은 libs로 줘야 한다
 *   Eclipse(.classpath)     src / output / lib 항목
 *   WEB-INF 구조            WEB-INF/classes, WEB-INF/lib
 * 찾은 것과 직접 준 것은 합칩니다(예: Maven 프로젝트에 libs를 더 줄 수 있다).
 *
 * 함께 확인하는 것
 *   - 바뀐 클래스(원본에 이미 있던 파일을 고친 것)를 쓰는 원본 파일도 함께 컴파일한다. 메서드 모양을 바꿔 다른 파일이 깨지는지 보려는 것이다.
 *   - 삭제 목록의 클래스를 아직 쓰는 파일이 있으면 오류로 알린다(원본의 클래스 폴더에 그 클래스가 남아 있어 컴파일로는 잡히지 않으므로 글자로 찾는다).
 *
 * 한계: XML, JSP 같은 Java 밖의 파일은 보지 않는다. 원본의 클래스 폴더가 오래됐으면 결과가 틀릴 수 있다.
 * 엔진이 JDK가 아니라 JRE에서 돌면 컴파일러가 없어 실패로 답한다.
 * </pre>
 */
@AiTool
public class JavaCompileTool extends BaseObject {

	/** 바뀐 클래스를 쓰는 원본 파일을 함께 컴파일하는 최대 개수(너무 넓게 퍼지면 그만큼만 보고 알린다) */
	private static final int MAX_DEPENDENTS = 300;
	/** 결과에 보여 줄 오류 줄 수 */
	private static final int MAX_ERROR_LINES = 60;
	/** mvn으로 의존성 경로를 구할 때 기다리는 시간 */
	private static final long MAVEN_TIMEOUT_MINUTES = 10;
	/** 파일을 찾을 때 들어가지 않는 폴더 */
	private static final Set<String> SKIP_DIRS = Set.of(".git", ".svn", "node_modules", ".gradle", ".idea", ".settings");

	@Tool(description = "바뀐 Java 파일만 원본의 컴파일 결과를 참조해 컴파일해 본다(원본 전체를 빌드하지 않는다). "
		+ "overlayDir에는 원본 루트 기준 같은 경로로 바뀐 파일과 새 파일이 있다. 원본은 읽기만 하고, 결과와 캐시는 outputDir에 둔다. "
		+ "소스 폴더, 클래스 폴더, 의존성 jar를 주지 않으면 원본 폴더(Maven, Gradle, Eclipse .classpath, WEB-INF)를 보고 찾는다. "
		+ "바뀐 클래스를 쓰는 원본 파일도 함께 컴파일하고, 삭제 목록의 클래스를 아직 쓰는 파일이 있으면 알린다. 성공/실패와 오류 줄(파일:줄:칸)을 돌려준다.")
	public ToolOutcome compileJava(
			@ToolParam(description = "원본 소스 루트의 절대경로") String sourceRoot,
			@ToolParam(description = "바뀐 파일·새 파일이 원본 루트 기준 같은 경로로 들어 있는 폴더의 절대경로") String overlayDir,
			@ToolParam(description = "컴파일 결과와 캐시를 둘 폴더의 절대경로(원본 밖)") String outputDir,
			@ToolParam(required = false, description = "삭제 목록 파일의 절대경로. 원본 루트 기준 상대경로가 한 줄에 하나") String deleteList,
			@ToolParam(required = false, description = "소스 폴더들(원본 루트 기준 상대경로 또는 절대경로). 비우면 찾는다") List<String> sourceDirs,
			@ToolParam(required = false, description = "원본의 컴파일 결과(클래스) 폴더들. 비우면 찾는다") List<String> classesDirs,
			@ToolParam(required = false, description = "의존성 jar 파일이나 jar가 든 폴더들. 찾은 것에 더한다") List<String> libs,
			@ToolParam(required = false, description = "Java 버전(예: 8, 17). 비우면 원본 클래스 파일에서 읽는다") Integer release,
			@ToolParam(required = false, description = "소스 인코딩. 비우면 UTF-8") String encoding) {
		try {
			return this.compile(sourceRoot, overlayDir, outputDir, deleteList, sourceDirs, classesDirs, libs, release, encoding);
		} catch (Exception e) {
			return ToolOutcome.fail("컴파일 확인 중 오류가 났습니다(" + e.getClass().getSimpleName() + "): " + e.getMessage());
		}
	}

	private ToolOutcome compile(String sourceRootText, String overlayText, String outputText, String deleteListText,
			List<String> sourceDirsArg, List<String> classesDirsArg, List<String> libsArg, Integer releaseArg, String encodingArg) throws IOException, InterruptedException {
		if (isBlank(sourceRootText) || isBlank(overlayText) || isBlank(outputText)) {
			return ToolOutcome.fail("sourceRoot, overlayDir, outputDir는 꼭 있어야 합니다.");
		}
		File root = new File(sourceRootText.trim());
		File overlay = new File(overlayText.trim());
		File output = new File(outputText.trim());
		if (!root.isDirectory()) {
			return ToolOutcome.fail("원본 폴더가 없습니다: " + root);
		}
		if (!overlay.isDirectory()) {
			return ToolOutcome.fail("변경 폴더가 없습니다: " + overlay);
		}
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			return ToolOutcome.fail("이 엔진은 JRE에서 돌고 있어 Java 컴파일러가 없습니다(JDK에서 띄워야 합니다).");
		}
		Files.createDirectories(output.toPath());

		// 1) 무엇을 참조할지 정한다(직접 준 것 + 찾은 것)
		Layout layout = new Layout();
		this.detect(root, layout);
		List<String> notes = new ArrayList<String>();
		if (sourceDirsArg != null && !sourceDirsArg.isEmpty()) {
			layout.sourceDirs.clear();
			for (String dir : sourceDirsArg) {
				layout.sourceDirs.add(resolve(root, dir));
			}
		}
		if (classesDirsArg != null && !classesDirsArg.isEmpty()) {
			layout.classesDirs.clear();
			for (String dir : classesDirsArg) {
				layout.classesDirs.add(resolve(root, dir));
			}
		}
		if (libsArg != null) {
			for (String lib : libsArg) {
				addJars(resolve(root, lib), layout.jars);
			}
		}
		if (layout.mavenPom != null && (libsArg == null || libsArg.isEmpty())) {
			String problem = this.addMavenClasspath(layout.mavenPom, output, layout.jars);
			if (problem != null) {
				return ToolOutcome.fail(problem);
			}
		}
		if (layout.gradle && (libsArg == null || libsArg.isEmpty())) {
			notes.add("Gradle 프로젝트는 의존성을 찾지 않습니다. 의존성 클래스가 없다는 오류가 나면 libs로 jar 폴더를 주십시오.");
		}
		if (layout.sourceDirs.isEmpty()) {
			return ToolOutcome.fail("소스 폴더를 찾지 못했습니다. sourceDirs로 주십시오: " + root);
		}
		File sampleClass = null;
		for (File dir : layout.classesDirs) {
			sampleClass = firstClassFile(dir);
			if (sampleClass != null) {
				break;
			}
		}
		if (sampleClass == null) {
			return ToolOutcome.fail("원본의 컴파일 결과(클래스 파일)를 찾지 못했습니다" + (layout.classesDirs.isEmpty() ? "" : "(" + layout.classesDirs + ")")
				+ ". 원본을 한 번 빌드해 두거나 classesDirs로 주십시오.");
		}
		int release = releaseArg != null && releaseArg.intValue() > 0 ? releaseArg.intValue() : Math.max(classVersion(sampleClass), 8);
		Charset charset = isBlank(encodingArg) ? StandardCharsets.UTF_8 : Charset.forName(encodingArg.trim());

		// 2) 바뀐 파일, 지울 파일, 함께 볼 원본 파일을 고른다
		Set<String> deleted = readDeleteList(deleteListText);
		Map<String, File> changed = new LinkedHashMap<String, File>(); // 원본 루트 기준 경로 → 변경 폴더의 파일
		for (File file : listJava(overlay)) {
			String rel = relative(overlay, file);
			if (!deleted.contains(rel)) {
				changed.put(rel, file);
			}
		}
		Set<String> touchedNames = new LinkedHashSet<String>(); // 원본에 있던 클래스를 고친 것(이름 → 그 클래스를 쓰는 파일도 본다)
		Map<String, String> deletedClasses = new LinkedHashMap<String, String>(); // 지운 클래스의 FQN → 원본 루트 기준 경로
		for (String rel : changed.keySet()) {
			if (new File(root, rel).isFile()) {
				String fqn = fqnOf(root, layout.sourceDirs, rel);
				if (fqn != null) {
					touchedNames.add(fqn);
				}
			}
		}
		for (String rel : deleted) {
			if (rel.endsWith(".java")) {
				String fqn = fqnOf(root, layout.sourceDirs, rel);
				if (fqn != null) {
					deletedClasses.put(fqn, rel);
				}
			}
		}
		if (changed.isEmpty() && deletedClasses.isEmpty()) {
			return ToolOutcome.pass("컴파일할 Java 파일이 없습니다(변경 폴더에 .java가 없음).");
		}

		List<File> originals = new ArrayList<File>();
		for (File dir : layout.sourceDirs) {
			originals.addAll(listJava(dir));
		}
		Set<String> allNames = new LinkedHashSet<String>(touchedNames);
		allNames.addAll(deletedClasses.keySet());
		List<File> dependents = new ArrayList<File>();
		List<String> deletedUses = new ArrayList<String>();
		boolean capped = false;
		for (File file : originals) {
			String rel = relative(root, file);
			if (changed.containsKey(rel) || deleted.contains(rel)) {
				continue;
			}
			String text = readQuietly(file, charset);
			if (text == null) {
				continue;
			}
			String ownPackage = packageOf(text);
			for (String fqn : allNames) {
				if (!uses(text, ownPackage, fqn)) {
					continue;
				}
				if (deletedClasses.containsKey(fqn)) {
					deletedUses.add(rel + ": 삭제 목록의 클래스 " + fqn + "을(를) 아직 씁니다");
				} else if (dependents.size() < MAX_DEPENDENTS) {
					dependents.add(file);
				} else {
					capped = true;
				}
				break;
			}
		}
		// 변경 폴더의 파일이 지운 클래스를 쓰는지도 본다
		for (Map.Entry<String, File> entry : changed.entrySet()) {
			String text = readQuietly(entry.getValue(), charset);
			if (text == null) {
				continue;
			}
			String ownPackage = packageOf(text);
			for (String fqn : deletedClasses.keySet()) {
				if (uses(text, ownPackage, fqn)) {
					deletedUses.add(entry.getKey() + ": 삭제 목록의 클래스 " + fqn + "을(를) 아직 씁니다");
				}
			}
		}
		if (capped) {
			notes.add("바뀐 클래스를 쓰는 원본 파일이 많아 " + MAX_DEPENDENTS + "개만 함께 컴파일했습니다.");
		}

		// 3) 컴파일한다. 바뀐 클래스는 변경 폴더의 소스를, 나머지는 원본의 클래스를 본다
		File classesOut = new File(output, "classes");
		deleteRecursively(classesOut.toPath());
		Files.createDirectories(classesOut.toPath());
		File generated = new File(output, "generated");
		Files.createDirectories(generated.toPath());
		List<String> sourcePath = new ArrayList<String>();
		for (File dir : layout.sourceDirs) {
			String rel = relativeOrNull(root, dir);
			if (rel != null && new File(overlay, rel).isDirectory()) {
				sourcePath.add(new File(overlay, rel).getAbsolutePath());
			}
		}
		// 원본 소스는 sourcepath에 넣지 않는다. 넣으면 원본 소스가 클래스보다 새것일 때 javac가 그 소스를 다시 읽고 분석해서
		// 큰 시스템에서는 사실상 전체를 컴파일하게 된다. 원본은 클래스 폴더로만 본다.
		if (sourcePath.isEmpty()) {
			sourcePath.add(overlay.getAbsolutePath());
		}
		List<String> classPath = new ArrayList<String>();
		for (File dir : layout.classesDirs) {
			classPath.add(dir.getAbsolutePath());
		}
		// 같은 이름의 jar는 한 번만 넣는다(Maven 의존성과 WEB-INF/lib에 같은 jar가 함께 있는 프로젝트가 많고, jar를 여는 데 시간이 든다)
		Set<String> jarNames = new LinkedHashSet<String>();
		for (File jar : layout.jars) {
			if (jarNames.add(jar.getName())) {
				classPath.add(jar.getAbsolutePath());
			}
		}
		List<String> options = new ArrayList<String>();
		options.add("-d");
		options.add(classesOut.getAbsolutePath());
		options.add("-s");
		options.add(generated.getAbsolutePath());
		options.add("-classpath");
		options.add(String.join(File.pathSeparator, classPath));
		options.add("-sourcepath");
		options.add(String.join(File.pathSeparator, sourcePath));
		options.add("--release");
		options.add(String.valueOf(release));
		options.add("-encoding");
		options.add(charset.name());
		options.add("-implicit:none");
		options.add("-nowarn");
		options.add("-Xlint:none");
		// 클래스패스의 애노테이션 처리기(Lombok 등)를 쓴다. JDK 23부터는 이 옵션이 없으면 처리기를 돌리지 않는다
		options.add("-proc:full");

		List<File> units = new ArrayList<File>(changed.values());
		units.addAll(dependents);
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		boolean ok = true;
		if (!units.isEmpty()) {
			StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), charset);
			try {
				Iterable<? extends JavaFileObject> objects = fileManager.getJavaFileObjectsFromFiles(units);
				ok = compiler.getTask(null, fileManager, diagnostics, options, null, objects).call().booleanValue();
			} finally {
				fileManager.close();
			}
		}

		// 4) 결과를 만든다
		List<String> errors = new ArrayList<String>();
		for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
			if (diagnostic.getKind() != Diagnostic.Kind.ERROR) {
				continue;
			}
			String where = "";
			if (diagnostic.getSource() != null) {
				where = shortPath(diagnostic.getSource().toUri().getPath(), root, overlay) + ":" + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber() + ": ";
			}
			errors.add(where + diagnostic.getMessage(Locale.getDefault()).replace("\r", "").replace("\n", " / "));
		}
		errors.addAll(deletedUses);
		String summary = "바뀐 파일 " + changed.size() + "개, 함께 확인한 원본 파일 " + dependents.size() + "개"
			+ " (기준: " + layout.kind + ", Java " + release + ")";
		if (ok && deletedUses.isEmpty()) {
			return ToolOutcome.pass("컴파일 통과: " + summary + (notes.isEmpty() ? "" : "\n" + String.join("\n", notes)));
		}
		StringBuilder message = new StringBuilder("컴파일 오류 " + errors.size() + "건: " + summary + "\n");
		for (int i = 0; i < errors.size() && i < MAX_ERROR_LINES; i++) {
			message.append(errors.get(i)).append('\n');
		}
		if (errors.size() > MAX_ERROR_LINES) {
			message.append("...(").append(errors.size() - MAX_ERROR_LINES).append("건 더 있음)\n");
		}
		for (String note : notes) {
			message.append(note).append('\n');
		}
		return ToolOutcome.fail(message.toString().strip());
	}

	/* ---------------------------------------------------------------- 구조 찾기 */

	/** 원본 폴더를 보고 소스·클래스·의존성 위치를 찾습니다. */
	private void detect(File root, Layout layout) throws IOException {
		File pom = new File(root, "pom.xml");
		File gradle = new File(root, "build.gradle");
		File gradleKts = new File(root, "build.gradle.kts");
		File eclipse = new File(root, ".classpath");
		if (pom.isFile()) {
			layout.kind = "Maven";
			layout.mavenPom = pom;
			addIfDir(new File(root, "src/main/java"), layout.sourceDirs);
			addIfDir(new File(root, "target/classes"), layout.classesDirs);
		} else if (gradle.isFile() || gradleKts.isFile()) {
			layout.kind = "Gradle";
			layout.gradle = true;
			addIfDir(new File(root, "src/main/java"), layout.sourceDirs);
			addIfDir(new File(root, "build/classes/java/main"), layout.classesDirs);
		}
		if (eclipse.isFile()) {
			String text = Files.readString(eclipse.toPath(), StandardCharsets.UTF_8);
			Matcher matcher = Pattern.compile("<classpathentry\\b([^>]*)/?>").matcher(text);
			while (matcher.find()) {
				String attrs = matcher.group(1);
				String kind = attr(attrs, "kind");
				String path = attr(attrs, "path");
				String outputAttr = attr(attrs, "output");
				if (path == null) {
					continue;
				}
				if ("src".equals(kind) && !path.startsWith("/") && !layout.mavenOrGradle()) {
					addIfDir(new File(root, path), layout.sourceDirs);
					if (outputAttr != null && !layout.mavenOrGradle()) {
						addIfDir(new File(root, outputAttr), layout.classesDirs);
					}
				} else if ("output".equals(kind) && !layout.mavenOrGradle()) {
					addIfDir(new File(root, path), layout.classesDirs);
				} else if ("lib".equals(kind)) {
					addJars(resolve(root, path), layout.jars);
				}
			}
			if (layout.kind == null) {
				layout.kind = "Eclipse";
			}
		}
		// WEB-INF 구조는 어느 경우든 더한다(웹 프로젝트의 WEB-INF/lib에만 있는 jar가 있다)
		for (File webInf : findDirs(root, "WEB-INF", 4)) {
			if (!layout.mavenOrGradle()) {
				addIfDir(new File(webInf, "classes"), layout.classesDirs);
			}
			addJars(new File(webInf, "lib"), layout.jars);
			if (layout.kind == null) {
				layout.kind = "WEB-INF";
			}
		}
		if (layout.sourceDirs.isEmpty()) {
			for (String candidate : new String[] { "src/main/java", "src", "JavaSource" }) {
				if (new File(root, candidate).isDirectory()) {
					layout.sourceDirs.add(new File(root, candidate));
					break;
				}
			}
		}
		if (layout.kind == null) {
			layout.kind = "직접 지정";
		}
	}

	/**
	 * Maven 의존성 jar 경로를 구해 jars에 더합니다. outputDir의 캐시를 쓰고, pom.xml이 바뀌었을 때만 mvn을 부릅니다.
	 * @return 문제가 있으면 그 설명, 없으면 null
	 */
	private String addMavenClasspath(File pom, File output, Set<File> jars) throws IOException, InterruptedException {
		File cacheDir = new File(output, "classpath-cache");
		Files.createDirectories(cacheDir.toPath());
		File cache = new File(cacheDir, "maven-classpath.txt");
		File key = new File(cacheDir, "maven-classpath.key");
		String currentKey = pom.getAbsolutePath() + "|" + pom.lastModified() + "|" + pom.length();
		boolean fresh = cache.isFile() && key.isFile() && currentKey.equals(Files.readString(key.toPath(), StandardCharsets.UTF_8).trim());
		if (!fresh) {
			List<String> command = new ArrayList<String>();
			if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
				command.add("cmd.exe");
				command.add("/c");
			}
			command.add("mvn");
			command.add("-q");
			command.add("-B");
			command.add("dependency:build-classpath");
			command.add("-Dmdep.outputFile=" + cache.getAbsolutePath());
			File log = new File(cacheDir, "mvn.log");
			Process process;
			try {
				process = new ProcessBuilder(command).directory(pom.getParentFile()).redirectErrorStream(true)
					.redirectOutput(log).start();
			} catch (IOException e) {
				return "Maven 의존성 경로를 구하지 못했습니다(mvn을 실행할 수 없음: " + e.getMessage() + "). libs로 의존성 jar 폴더를 주십시오.";
			}
			if (!process.waitFor(MAVEN_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
				process.destroyForcibly();
				return "Maven 의존성 경로를 구하다가 " + MAVEN_TIMEOUT_MINUTES + "분이 지났습니다. libs로 의존성 jar 폴더를 주십시오.";
			}
			if (process.exitValue() != 0 || !cache.isFile()) {
				return "Maven 의존성 경로를 구하지 못했습니다(종료 코드 " + process.exitValue() + ", 로그 " + log + "). libs로 의존성 jar 폴더를 주십시오.";
			}
			Files.writeString(key.toPath(), currentKey, StandardCharsets.UTF_8);
		}
		String text = Files.readString(cache.toPath(), StandardCharsets.UTF_8).trim();
		if (!text.isEmpty()) {
			for (String entry : text.split(Pattern.quote(File.pathSeparator))) {
				if (!entry.isBlank()) {
					jars.add(new File(entry.trim()));
				}
			}
		}
		return null;
	}

	/* ---------------------------------------------------------------- 파일 다루기 */

	private static List<File> listJava(File dir) throws IOException {
		final List<File> result = new ArrayList<File>();
		if (!dir.isDirectory()) {
			return result;
		}
		Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
				return SKIP_DIRS.contains(String.valueOf(path.getFileName())) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
				if (path.toString().endsWith(".java")) {
					result.add(path.toFile());
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return result;
	}

	private static List<File> findDirs(File root, final String name, final int maxDepth) throws IOException {
		final List<File> result = new ArrayList<File>();
		final Path base = root.toPath();
		Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
				String dirName = String.valueOf(path.getFileName());
				if (SKIP_DIRS.contains(dirName) || "target".equals(dirName) || "build".equals(dirName)) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				if (name.equals(dirName)) {
					result.add(path.toFile());
					return FileVisitResult.SKIP_SUBTREE;
				}
				return base.relativize(path).getNameCount() >= maxDepth ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}
		});
		return result;
	}

	private static File firstClassFile(File dir) throws IOException {
		if (!dir.isDirectory()) {
			return null;
		}
		final File[] found = new File[1];
		Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
				if (path.toString().endsWith(".class")) {
					found[0] = path.toFile();
					return FileVisitResult.TERMINATE;
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return found[0];
	}

	/** 클래스 파일의 major 버전으로 Java 버전을 정합니다(52 → 8). 읽지 못하면 8. */
	private static int classVersion(File classFile) {
		try {
			byte[] head = Files.readAllBytes(classFile.toPath());
			if (head.length >= 8) {
				int major = ((head[6] & 0xff) << 8) | (head[7] & 0xff);
				return Math.max(major - 44, 1);
			}
		} catch (IOException e) {
			// 읽지 못하면 기본값
		}
		return 8;
	}

	private static void addJars(File target, Set<File> jars) {
		if (target.isFile() && target.getName().endsWith(".jar")) {
			jars.add(target);
		} else if (target.isDirectory()) {
			File[] files = target.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isFile() && file.getName().endsWith(".jar")) {
						jars.add(file);
					}
				}
			}
		}
	}

	private static void addIfDir(File dir, List<File> list) {
		if (dir.isDirectory() && !list.contains(dir)) {
			list.add(dir);
		}
	}

	private static Set<String> readDeleteList(String path) throws IOException {
		Set<String> result = new LinkedHashSet<String>();
		if (isBlank(path) || !new File(path.trim()).isFile()) {
			return result;
		}
		for (String line : Files.readAllLines(new File(path.trim()).toPath(), StandardCharsets.UTF_8)) {
			String rel = line.trim().replace('\\', '/');
			while (rel.startsWith("/")) {
				rel = rel.substring(1);
			}
			if (!rel.isEmpty() && !rel.startsWith("#") && !rel.contains("..")) {
				result.add(rel);
			}
		}
		return result;
	}

	/** 원본 루트 기준 경로(rel)의 Java 파일이 어느 소스 폴더 아래인지 보고 FQN을 만듭니다. 소스 폴더 밖이면 null. */
	private static String fqnOf(File root, List<File> sourceDirs, String rel) {
		for (File dir : sourceDirs) {
			String dirRel = relativeOrNull(root, dir);
			if (dirRel == null) {
				continue;
			}
			String prefix = dirRel.isEmpty() ? "" : dirRel + "/";
			if (rel.startsWith(prefix) && rel.endsWith(".java")) {
				return rel.substring(prefix.length(), rel.length() - ".java".length()).replace('/', '.');
			}
		}
		return null;
	}

	private static String packageOf(String text) {
		Matcher matcher = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE).matcher(text);
		return matcher.find() ? matcher.group(1) : "";
	}

	/** 이 소스가 fqn 클래스를 쓰는지 글자로 봅니다(같은 패키지이거나 그 클래스·패키지를 import하고, 이름이 나온다). */
	private static boolean uses(String text, String ownPackage, String fqn) {
		int dot = fqn.lastIndexOf('.');
		String simple = dot < 0 ? fqn : fqn.substring(dot + 1);
		String pkg = dot < 0 ? "" : fqn.substring(0, dot);
		if (!Pattern.compile("\\b" + Pattern.quote(simple) + "\\b").matcher(text).find()) {
			return false;
		}
		if (pkg.equals(ownPackage)) {
			return true;
		}
		return text.contains("import " + fqn + ";") || text.contains("import " + pkg + ".*;") || text.contains(fqn);
	}

	private static String readQuietly(File file, Charset charset) {
		try {
			return new String(Files.readAllBytes(file.toPath()), charset);
		} catch (IOException e) {
			return null;
		}
	}

	private static File resolve(File root, String path) {
		File file = new File(path.trim());
		return file.isAbsolute() ? file : new File(root, path.trim());
	}

	private static String relative(File base, File file) {
		return base.toPath().toAbsolutePath().normalize().relativize(file.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
	}

	private static String relativeOrNull(File base, File file) {
		Path b = base.toPath().toAbsolutePath().normalize();
		Path f = file.toPath().toAbsolutePath().normalize();
		return f.startsWith(b) ? b.relativize(f).toString().replace('\\', '/') : null;
	}

	/** 오류 위치를 원본 루트나 변경 폴더 기준 경로로 줄입니다. */
	private static String shortPath(String uriPath, File root, File overlay) {
		File file = new File(uriPath);
		String rel = relativeOrNull(overlay, file);
		if (rel != null) {
			return rel;
		}
		rel = relativeOrNull(root, file);
		return rel != null ? rel + " (원본)" : uriPath;
	}

	private static String attr(String attrs, String name) {
		Matcher matcher = Pattern.compile("\\b" + name + "\\s*=\\s*\"([^\"]*)\"").matcher(attrs);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
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

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/** 찾은 구조. */
	private static final class Layout {
		String kind;
		File mavenPom;
		boolean gradle;
		final List<File> sourceDirs = new ArrayList<File>();
		final List<File> classesDirs = new ArrayList<File>();
		final Set<File> jars = new LinkedHashSet<File>();

		boolean mavenOrGradle() {
			return this.mavenPom != null || this.gradle;
		}
	}

}

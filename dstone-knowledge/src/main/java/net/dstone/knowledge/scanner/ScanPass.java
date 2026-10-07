package net.dstone.knowledge.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.AnalysisFileDao;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.job.AnalysisJobReporter;
import net.dstone.knowledge.job.AnalysisPass;

/**
 * <pre>
 * SCAN 단계: 프로젝트 폴더를 훑어서 분석할 파일의 목록을 analysis_file에 만듭니다.
 *
 * 파일마다 알아내는 것: 종류(언어/용도), 인코딩, 내용 해시(SHA-256), 크기, 줄 수,
 * 그리고 Java 파일이면 패키지와 소스 루트, 멀티모듈이면 어느 모듈인지.
 *
 * 메모리에는 파일 목록 전체를 올리지 않습니다. 폴더를 걸어가면서 파일을 하나씩 읽고,
 * 일정 개수(commit-size)가 모이면 DB에 쓰고 비웁니다. 그래서 프로젝트가 커져도 쓰는 메모리는 그대로입니다.
 *
 * 중간에 죽으면 다음에는 처음부터 다시 훑습니다. 이미 들어간 경로는 덮어쓰기 때문에 중복되지 않습니다.
 * (훑는 일은 금방 끝나서 "어디까지 했는지"를 따로 기억하는 것보다 다시 하는 편이 단순합니다.)
 * </pre>
 */
@Component
public class ScanPass extends BaseObject implements AnalysisPass {

	public static final String NAME = "SCAN";

	/** 패키지 선언. 예: package com.legacy.order.dao; */
	private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\s*\\.\\s*[A-Za-z_$][\\w$]*)*)\\s*;");

	/** 주석. 주석 안에 적힌 "package ..." 문장에 속지 않으려고 패키지를 찾기 전에 지웁니다. */
	private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

	@Autowired
	private ConfigProperty configProperty;

	@Autowired
	private EncodingDetector encodingDetector;

	@Autowired
	private FileClassifier fileClassifier;

	@Autowired
	private AnalysisFileDao analysisFileDao;

	@Autowired
	private AnalysisJobReporter reporter;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public void run(final AnalysisJobContext context) throws Exception {
		String localPath = context.getProjectValue("localPath");
		if (localPath == null) {
			throw new IllegalStateException("프로젝트에 소스 경로(localPath)가 없습니다.");
		}
		final Path root = Paths.get(localPath).toRealPath();
		if (!Files.isDirectory(root)) {
			throw new IllegalStateException("소스 경로가 폴더가 아닙니다: " + root);
		}

		final Set<String> excludeDirs = excludeDirs();
		final long maxFileBytes = longProperty("dstone.knowledge.scan.max-file-bytes", 5L * 1024 * 1024);
		final int commitSize = (int) longProperty("dstone.knowledge.scan.commit-size", 200);
		final String projectEncoding = context.getProjectValue("sourceEncoding");

		// 아직 DB에 쓰지 않고 모아 둔 파일들. commitSize개가 되면 쓰고 비운다.
		final List<ScannedFile> buffer = new ArrayList<ScannedFile>();
		// 지금까지 저장한 파일 수. 익명 클래스 안에서 값을 바꿔야 해서 배열에 담았다.
		final int[] savedCount = new int[] { 0 };
		// 지금 들어와 있는 폴더가 속한 모듈. 폴더에 들어갈 때 쌓고 나올 때 뺀다(모듈이 없으면 "").
		final Deque<String> moduleStack = new ArrayDeque<String>();

		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				boolean isRoot = dir.equals(root);
				if (!isRoot && excludeDirs.contains(dir.getFileName().toString())) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				String parentModule = moduleStack.isEmpty() ? "" : moduleStack.peek();
				// 프로젝트 루트는 모듈로 치지 않는다. 그 아래에서 빌드 파일을 가진 폴더가 모듈이다.
				if (!isRoot && hasBuildFile(dir)) {
					moduleStack.push(relativePath(root, dir));
				} else {
					moduleStack.push(parentModule);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				moduleStack.pop();
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (!attrs.isRegularFile()) {
					return FileVisitResult.CONTINUE;
				}
				String fileName = file.getFileName().toString();
				String language = fileClassifier.languageOf(fileName);
				if (language == null) {
					// 분석 대상이 아닌 파일(이미지, js, class, jar ...)
					return FileVisitResult.CONTINUE;
				}
				String path = relativePath(root, file);
				try {
					String module = moduleStack.peek();
					ScannedFile scanned = scanFile(context, file, path, fileName, language, attrs.size(), maxFileBytes, projectEncoding);
					scanned.setModule(module == null || module.length() == 0 ? null : module);
					buffer.add(scanned);
				} catch (IOException e) {
					// 파일 하나를 못 읽었다고 전체를 멈추지 않는다. 기록만 남기고 다음 파일로 간다.
					reporter.error(context, NAME, null, "IO", "파일을 읽지 못했습니다: " + path, ErrorText.summaryOf(e));
				}
				if (buffer.size() >= commitSize) {
					flush(context, buffer, savedCount);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				// 권한이 없는 폴더/파일 등
				reporter.error(context, NAME, null, "IO", "접근하지 못했습니다: " + relativePath(root, file), ErrorText.summaryOf(exc));
				return FileVisitResult.CONTINUE;
			}
		});

		flush(context, buffer, savedCount);
		info("SCAN: 파일 " + savedCount[0] + "개, root=" + root);
	}

	/** 파일 하나를 읽어 analysis_file 한 행에 들어갈 내용을 만듭니다. */
	private ScannedFile scanFile(AnalysisJobContext context, Path file, String path, String fileName, String language
			, long size, long maxFileBytes, String projectEncoding) throws IOException {
		ScannedFile scanned = new ScannedFile();
		scanned.setRevisionId(context.getRevisionId());
		scanned.setPath(path);
		scanned.setLanguage(language);
		scanned.setSizeBytes(size);
		scanned.setParseStatus("PENDING");

		if (size > maxFileBytes) {
			// 너무 큰 파일은 메모리에 올리지 않는다. 해시만 흘려 읽으면서 계산하고, 분석에서는 뺀다.
			scanned.setChecksum(sha256Of(file));
			scanned.setFileType(fileClassifier.fileTypeOf(language, fileName, null));
			scanned.setParseStatus("SKIPPED");
			scanned.setParseError("파일이 너무 커서 분석하지 않습니다(" + size + " bytes > " + maxFileBytes + " bytes).");
			return scanned;
		}

		byte[] bytes = Files.readAllBytes(file);
		scanned.setSizeBytes(bytes.length);
		scanned.setChecksum(sha256Of(bytes));

		EncodingDetector.Result encoding = encodingDetector.detect(bytes, language, projectEncoding);
		scanned.setEncoding(encoding.encoding);
		if (!encoding.confident) {
			reporter.error(context, NAME, null, "ENCODING", "인코딩을 알아내지 못했습니다: " + path
					, "UTF-8, EUC-KR, MS949 어느 것으로도 깨끗하게 읽히지 않아 ISO-8859-1로 읽었습니다. 한글이 깨져 있을 수 있습니다.");
		}

		String text = encodingDetector.decode(bytes, encoding);
		scanned.setLineCount(Integer.valueOf(lineCountOf(text)));
		scanned.setFileType(fileClassifier.fileTypeOf(language, fileName, text));

		if ("JAVA".equals(language)) {
			// 여기서는 정규식으로 가볍게 찾는다. 정확한 값은 DECLARE 단계가 파싱하면서 다시 확인한다.
			String packageName = packageOf(text);
			scanned.setPackageName(packageName);
			scanned.setSourceRoot(sourceRootOf(path, packageName));
		}
		return scanned;
	}

	/**
	 * <pre>
	 * 모아 둔 파일을 DB에 쓰고 비웁니다.
	 * 저장은 대량 저장용 세션으로 한 트랜잭션에 묶고, 진행 기록과 취소 확인은 그 트랜잭션이 끝난 뒤에 합니다.
	 * </pre>
	 */
	private void flush(AnalysisJobContext context, final List<ScannedFile> buffer, int[] savedCount) {
		if (!buffer.isEmpty()) {
			txTemplateCommon.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					analysisFileDao.upsertFiles(buffer);
				}
			});
			savedCount[0] += buffer.size();
			buffer.clear();
		}
		// 전체 파일 수는 다 훑어 봐야 알 수 있어서, 전체와 완료를 같은 값으로 올려 간다.
		reporter.progress(context, NAME, savedCount[0], savedCount[0]);
		context.checkCancelled();
	}

	/** 폴더에 빌드 파일이 있으면 모듈로 봅니다. */
	private boolean hasBuildFile(Path dir) {
		return Files.isRegularFile(dir.resolve("pom.xml"))
				|| Files.isRegularFile(dir.resolve("build.gradle"))
				|| Files.isRegularFile(dir.resolve("build.gradle.kts"));
	}

	/** 프로젝트 루트 기준 상대 경로. 윈도우에서도 구분자를 '/'로 맞춥니다. */
	private String relativePath(Path root, Path path) {
		return root.relativize(path).toString().replace('\\', '/');
	}

	/** 패키지 선언을 찾습니다. 없으면(기본 패키지) 빈 문자열입니다. */
	String packageOf(String text) {
		Matcher m = PACKAGE.matcher(COMMENT.matcher(text).replaceAll(" "));
		if (m.find()) {
			return m.group(1).replaceAll("\\s+", "");
		}
		return "";
	}

	/**
	 * <pre>
	 * 소스 루트를 찾습니다. 파일이 놓인 폴더 경로의 끝이 패키지 경로와 같으면, 그 앞부분이 소스 루트입니다.
	 *
	 * 예: WEB-INF/src/com/legacy/order/dao/OrderDAO.java + 패키지 com.legacy.order.dao → WEB-INF/src
	 *
	 * 빌드 파일이 없는 구버전 프로젝트는 소스가 src/main/java가 아닌 곳(WEB-INF/src, WEB-INF/classes ...)에
	 * 있어서, 폴더 이름을 미리 정해 두지 않고 패키지 선언으로 거꾸로 찾습니다.
	 * </pre>
	 *
	 * @return 소스 루트(프로젝트 루트 자체면 "."). 폴더 구조가 패키지와 맞지 않으면 null
	 */
	public static String sourceRootOf(String path, String packageName) {
		int slash = path.lastIndexOf('/');
		String dir = slash < 0 ? "" : path.substring(0, slash);
		if (packageName == null || packageName.length() == 0) {
			return dir.length() == 0 ? "." : dir;
		}
		String packagePath = packageName.replace('.', '/');
		if (dir.equals(packagePath)) {
			return ".";
		}
		if (dir.endsWith("/" + packagePath)) {
			return dir.substring(0, dir.length() - packagePath.length() - 1);
		}
		return null;
	}

	private int lineCountOf(String text) {
		if (text.length() == 0) {
			return 0;
		}
		int lines = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				lines++;
			}
		}
		// 마지막 줄이 줄바꿈 없이 끝나면 그 줄도 센다.
		if (text.charAt(text.length() - 1) != '\n') {
			lines++;
		}
		return lines;
	}

	private String sha256Of(byte[] bytes) throws IOException {
		MessageDigest digest = newSha256();
		digest.update(bytes);
		return hex(digest.digest());
	}

	private String sha256Of(Path file) throws IOException {
		MessageDigest digest = newSha256();
		byte[] chunk = new byte[64 * 1024];
		try (InputStream in = Files.newInputStream(file)) {
			int read;
			while ((read = in.read(chunk)) > 0) {
				digest.update(chunk, 0, read);
			}
		}
		return hex(digest.digest());
	}

	private MessageDigest newSha256() throws IOException {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException("SHA-256을 쓸 수 없습니다.", e);
		}
	}

	private String hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (int i = 0; i < bytes.length; i++) {
			sb.append(Character.forDigit((bytes[i] >> 4) & 0xF, 16));
			sb.append(Character.forDigit(bytes[i] & 0xF, 16));
		}
		return sb.toString();
	}

	/** 훑지 않을 폴더 이름들(dstone.knowledge.scan.exclude-dirs, 쉼표 구분) */
	private Set<String> excludeDirs() {
		Set<String> dirs = new HashSet<String>();
		String configured = configProperty.getProperty("dstone.knowledge.scan.exclude-dirs");
		if (configured == null || configured.trim().length() == 0) {
			configured = ".git,.svn,.hg,node_modules,target,build,.gradle,.idea,.settings,.metadata";
		}
		String[] names = configured.split(",");
		for (int i = 0; i < names.length; i++) {
			String name = names[i].trim();
			if (name.length() > 0) {
				dirs.add(name);
			}
		}
		return dirs;
	}

	private long longProperty(String key, long defaultValue) {
		String configured = configProperty.getProperty(key);
		if (configured == null || configured.trim().length() == 0) {
			return defaultValue;
		}
		return Long.parseLong(configured.trim());
	}

}

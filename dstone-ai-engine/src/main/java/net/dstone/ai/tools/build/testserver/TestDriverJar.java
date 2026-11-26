package net.dstone.ai.tools.build.testserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * <pre>
 * 테스트 드라이버(resources/testdriver 아래 소스, 앱 JVM에서 도는 JDBC 드라이버) jar를 만듭니다.
 * 테스트 서버는 Java 8 앱도 띄우므로 엔진의 JDK로 --release 8 컴파일합니다. 결과는 소스 내용의 해시별로
 * {작업 루트}/testdriver/{해시}/dstone-testdriver.jar 에 두고 다시 씁니다.
 * jar에는 META-INF/services/java.sql.Driver 가 들어 있어 DriverManager로도 찾힙니다.
 * </pre>
 */
public final class TestDriverJar {

	/** 드라이버 클래스 이름 */
	public static final String DRIVER_CLASS = "net.dstone.ai.testdriver.TestDriver";

	/** 드라이버가 받는 URL 앞부분 */
	public static final String URL_PREFIX = "jdbc:dstonetest:";

	private static final String BASE = "testdriver/net/dstone/ai/testdriver/";
	private static final String[] SOURCES = { "TestDriver", "Holder", "Control" };

	private TestDriverJar() {
	}

	/** jar를 만들어(이미 있으면 그대로) 돌려줍니다. */
	public static synchronized File ensure(File workRoot) throws IOException {
		List<byte[]> sources = new ArrayList<byte[]>();
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
		for (String name : SOURCES) {
			InputStream in = TestDriverJar.class.getClassLoader().getResourceAsStream(BASE + name + ".javasrc");
			if (in == null) {
				throw new IOException("테스트 드라이버 소스가 없습니다: " + BASE + name + ".javasrc");
			}
			try {
				byte[] bytes = in.readAllBytes();
				sources.add(bytes);
				digest.update(bytes);
			} finally {
				in.close();
			}
		}
		StringBuilder hash = new StringBuilder();
		byte[] sum = digest.digest();
		for (int i = 0; i < 8; i++) {
			hash.append(String.format("%02x", Integer.valueOf(sum[i] & 0xff)));
		}
		File dir = new File(new File(workRoot, "testdriver"), hash.toString());
		File jar = new File(dir, "dstone-testdriver.jar");
		if (jar.isFile()) {
			return jar;
		}
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IOException("엔진이 JRE에서 돌고 있어 테스트 드라이버를 컴파일할 수 없습니다(JDK에서 띄워야 합니다).");
		}
		File srcDir = new File(dir, "src/net/dstone/ai/testdriver");
		File classes = new File(dir, "classes");
		Files.createDirectories(srcDir.toPath());
		Files.createDirectories(classes.toPath());
		List<File> files = new ArrayList<File>();
		for (int i = 0; i < SOURCES.length; i++) {
			File file = new File(srcDir, SOURCES[i] + ".java");
			Files.write(file.toPath(), sources.get(i));
			files.add(file);
		}
		List<String> options = new ArrayList<String>();
		options.add("-d");
		options.add(classes.getAbsolutePath());
		options.add("--release");
		options.add("8");
		options.add("-encoding");
		options.add("UTF-8");
		options.add("-nowarn");
		options.add("-Xlint:-options");
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), StandardCharsets.UTF_8);
		boolean ok;
		try {
			ok = compiler.getTask(null, fileManager, diagnostics, options, null, fileManager.getJavaFileObjectsFromFiles(files)).call().booleanValue();
		} finally {
			fileManager.close();
		}
		if (!ok) {
			StringBuilder message = new StringBuilder("테스트 드라이버를 컴파일하지 못했습니다:");
			for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
				if (d.getKind() == Diagnostic.Kind.ERROR) {
					message.append('\n').append(d.getSource() == null ? "" : d.getSource().getName() + ":" + d.getLineNumber() + " ").append(d.getMessage(Locale.getDefault()));
				}
			}
			throw new IOException(message.toString());
		}
		File temp = new File(dir, "dstone-testdriver.jar.tmp");
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		JarOutputStream out = new JarOutputStream(new FileOutputStream(temp), manifest);
		try {
			out.putNextEntry(new JarEntry("META-INF/services/java.sql.Driver"));
			out.write((DRIVER_CLASS + "\n").getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
			File[] compiled = new File(classes, "net/dstone/ai/testdriver").listFiles();
			if (compiled != null) {
				for (File file : compiled) {
					out.putNextEntry(new JarEntry("net/dstone/ai/testdriver/" + file.getName()));
					out.write(Files.readAllBytes(file.toPath()));
					out.closeEntry();
				}
			}
		} finally {
			out.close();
		}
		Files.move(temp.toPath(), jar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		return jar;
	}

}

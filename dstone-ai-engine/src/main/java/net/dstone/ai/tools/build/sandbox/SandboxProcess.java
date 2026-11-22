package net.dstone.ai.tools.build.sandbox;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <pre>
 * 일회용 DB 구현들이 같이 쓰는 프로세스·폴더 도구입니다. DB 종류를 모릅니다.
 * </pre>
 */
final class SandboxProcess {

	private static final int COMMAND_TIMEOUT_SECONDS = 900;

	private SandboxProcess() {
	}

	/**
	 * 명령을 실행합니다. 성공이면 null, 실패면 이유.
	 *
	 * @param password 환경변수 passwordEnv로 넘길 비밀번호(없으면 null)
	 * @param input    표준 입력으로 줄 파일(없으면 null)
	 * @param output   표준 출력을 받을 파일(없으면 null)
	 */
	static String execute(File dir, List<String> command, String passwordEnv, String password, File input, File output) throws IOException {
		File out = new File(dir, "command.out");
		File error = new File(dir, "command.err");
		ProcessBuilder builder = new ProcessBuilder(command).directory(dir)
			.redirectOutput(output == null ? out : output).redirectError(error);
		if (input != null) {
			builder.redirectInput(input);
		}
		builder.environment().remove(passwordEnv);
		if (password != null) {
			builder.environment().put(passwordEnv, password);
		}
		Process started = builder.start();
		if (input == null) {
			started.getOutputStream().close();
		}
		try {
			if (!started.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				killTree(started.toHandle());
				return COMMAND_TIMEOUT_SECONDS + "초 안에 끝나지 않음";
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			killTree(started.toHandle());
			return "중단됨";
		}
		return started.exitValue() == 0 ? null : "종료 코드 " + started.exitValue() + ", " + tail(error);
	}

	static File executable(String home, String name, String kind) throws SandboxException {
		if (home == null || home.isBlank()) {
			throw new SandboxException("격리 시험 DB를 만들 " + kind + " 설치 폴더가 설정되지 않았습니다(설정 dstone.ai.tool.test-server.sandbox-" + kind + "-home).", false);
		}
		String file = isWindows() ? name + ".exe" : name;
		for (String folder : new String[] { "bin", "sbin" }) {
			File candidate = new File(new File(home.trim(), folder), file);
			if (candidate.isFile()) {
				return candidate.getAbsoluteFile();
			}
		}
		throw new SandboxException(kind + " 설치 폴더에 " + file + "이(가) 없습니다: " + home, false);
	}

	static void killTree(ProcessHandle root) {
		List<ProcessHandle> all = new ArrayList<ProcessHandle>();
		Iterator<ProcessHandle> children = root.descendants().iterator();
		while (children.hasNext()) {
			all.add(children.next());
		}
		all.add(root);
		for (ProcessHandle handle : all) {
			handle.destroyForcibly(); // 버릴 DB라 정상 종료를 기다릴 까닭이 없다
		}
		long deadline = System.currentTimeMillis() + 10000L;
		while (root.isAlive() && System.currentTimeMillis() < deadline) {
			pause(100L);
		}
	}

	/** 폴더를 지웁니다. Windows는 프로세스가 끝난 뒤에도 잠깐 파일이 잠겨 있어 몇 번 해 봅니다. */
	static boolean deleteTree(File root) {
		for (int i = 0; i < 20 && root.exists(); i++) {
			try {
				Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						Files.delete(file);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult postVisitDirectory(Path folder, IOException e) throws IOException {
						Files.delete(folder);
						return FileVisitResult.CONTINUE;
					}
				});
			} catch (IOException e) {
				pause(500L);
			}
		}
		return !root.exists();
	}

	static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
			return socket.getLocalPort();
		}
	}

	static void pause(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	static String tail(File file) {
		try {
			String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
			return text.substring(Math.max(0, text.length() - 1200));
		} catch (IOException e) {
			return "";
		}
	}

	static String brief(String sql) {
		String line = sql.replaceAll("(?m)^\\s*(--|#).*$", "").replaceAll("\\s+", " ").trim();
		return line.length() > 160 ? line.substring(0, 160) + "…" : line;
	}

	static String word(String value) {
		return value != null && value.matches("[A-Za-z0-9_]+") ? value : null;
	}

	static String secret() {
		return "Sx9!" + UUID.randomUUID().toString().replace("-", "");
	}

	static String slash(File file) {
		return file.getAbsolutePath().replace('\\', '/');
	}

	static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

}

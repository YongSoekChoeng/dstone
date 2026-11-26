package net.dstone.ai.tools.build.sandbox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * <pre>
 * 빌드한 WAR·jar에 SandboxGuard가 잡지 못하는 접속 수단이 들어 있는지 봅니다.
 *
 * SandboxGuard는 일반 소켓만 잡습니다. NIO로 여는 Redis(Lettuce), Kafka, MongoDB, SQL Server 드라이버 등은
 * 그냥 원래 서버로 나갑니다. 그래서 들어 있으면 띄우지 않습니다. 들어 있기만 하고 쓰지 않는지는 기계가 알 수 없으므로,
 * 사람이 확인한 것만 설정 sandbox-trusted-libs에 프로젝트별로(프로젝트ID=jar 앞부분) 적어 넘어갑니다.
 *
 * Oracle 드라이버는 JVM 옵션으로 잡히므로(SandboxGuard) 들어 있어도 띄우되, 앱이 그 옵션(javaNetNio)을 만지면 띄우지 않습니다.
 *
 * 다 잡는다는 뜻은 아닙니다. 이름을 바꾼 jar, 라이브러리 안의 네이티브 코드는 알아보지 못합니다.
 * </pre>
 */
public final class SandboxArtifact {

	private static final Pattern NIO_LIBRARY = Pattern.compile("^(mssql-jdbc|sqljdbc|db2jcc|jcc-|tibero|lettuce|netty-transport|netty-all|mongodb-driver|mongo-java-driver"
		+ "|kafka-clients|grpc-netty|httpasyncclient|reactor-netty|r2dbc-|hazelcast|aerospike|cassandra-driver|java-driver-core|spymemcached|xmemcached|zookeeper).*\\.jar$");

	/** 앱이 되돌리면 Oracle 드라이버가 가드를 우회하는 설정 이름 */
	private static final String ORACLE_NIO_SWITCH = "javaNetNio";
	private static final long MAX_SCAN_BYTES = 5L * 1024 * 1024;

	private static final String[] NIO_CLASSES = { "java/nio/channels/SocketChannel", "java/nio/channels/AsynchronousSocketChannel", "java/net/http/HttpClient" };

	private SandboxArtifact() {
	}

	/**
	 * @param archive WAR나 Spring Boot jar
	 * @param trusted 사람이 확인한 jar 이름의 앞부분들
	 * @return 잡지 못하는 것들. 없으면 빈 목록
	 */
	static List<String> unguarded(File archive, List<String> trusted) throws IOException {
		List<String> found = new ArrayList<String>();
		try (ZipFile zip = new ZipFile(archive)) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String path = entry.getName();
				String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
				if (entry.isDirectory()) {
					continue;
				}
				if (path.endsWith(".jar")) {
					if (NIO_LIBRARY.matcher(name).matches() && !startsWithAny(name, trusted)) {
						found.add(name);
					}
					continue;
				}
				if (entry.getSize() <= MAX_SCAN_BYTES && !startsWithAny(name, trusted)) {
					try (InputStream in = zip.getInputStream(entry)) {
						if (new String(in.readAllBytes(), StandardCharsets.ISO_8859_1).contains(ORACLE_NIO_SWITCH)) {
							found.add(path + " → " + ORACLE_NIO_SWITCH);
							continue;
						}
					}
				}
				if (path.endsWith(".class") && (path.startsWith("WEB-INF/classes/") || path.startsWith("BOOT-INF/classes/")) && !startsWithAny(name, trusted)) {
					try (InputStream in = zip.getInputStream(entry)) {
						String bytes = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
						for (String used : NIO_CLASSES) {
							if (bytes.contains(used)) {
								found.add(path + " → " + used);
								break;
							}
						}
					}
				}
			}
		}
		return found;
	}

	/**
	 * lib 폴더에서 가드가 잡지 못하는 접속 라이브러리(NIO) jar를 찾습니다. 테스트 서버는 풀린 WAR를 쓰므로 jar 이름만 봅니다.
	 *
	 * @param libDir  WEB-INF/lib 같은 jar 폴더
	 * @param trusted 사람이 확인한 jar 이름의 앞부분들
	 */
	public static List<String> unguardedJars(File libDir, List<String> trusted) {
		List<String> found = new ArrayList<String>();
		File[] jars = libDir == null ? null : libDir.listFiles();
		if (jars == null) {
			return found;
		}
		for (File jar : jars) {
			String name = jar.getName().toLowerCase(Locale.ROOT);
			if (NIO_LIBRARY.matcher(name).matches() && !startsWithAny(name, trusted)) {
				found.add(jar.getName());
			}
		}
		return found;
	}

	private static boolean startsWithAny(String name, List<String> prefixes) {
		for (String prefix : prefixes) {
			if (!prefix.isBlank() && name.startsWith(prefix.trim().toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

}

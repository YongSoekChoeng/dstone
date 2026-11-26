package net.dstone.ai.tools.build.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.runtime.tool.StepRef;
import net.dstone.ai.tools.build.testserver.TestServerRegistry;

/**
 * <pre>
 * 격리 시험 서버의 규칙을 DB 없이 확인합니다.
 * - DDL 꺼내기, 원본 경로 바꾸기, 잡지 못하는 접속 수단 알아보기
 * - 원래 DB로 가는 연결은 복제본으로 돌고, 그 밖은 끊김
 * - 격리 시험 중인 step은 복제본과 시험 서버만 봄
 * </pre>
 */
class SandboxRulesTest {

	@TempDir
	Path dir;

	@Test
	void 마크다운은_sql_블록만_실행하고_롤백은_뺀다() {
		String text = "# DDL\n```sql\nCREATE TABLE a(x varchar(9) default 'p;q');\n-- 주석;\nINSERT INTO a VALUES('1');\n```\n## 롤백\n```sql\nDROP TABLE a;\n```\n";
		SandboxDdl.Script script = SandboxDdl.parse("ddl.md", text, SandboxDdl.Dialect.MYSQL);

		assertThat(script.problem()).isNull();
		assertThat(script.statements()).hasSize(2);
		assertThat(script.skipped()).isEqualTo(1);
	}

	@Test
	void 블록_밖의_DDL은_조용히_빼먹지_않는다() {
		assertThat(SandboxDdl.parse("ddl.md", "# DDL\nCREATE TABLE a(x int);\n", SandboxDdl.Dialect.MYSQL).problem()).contains("블록 밖");
	}

	@Test
	void DB마다_다른_따옴표_규칙으로_나눈다() {
		assertThat(SandboxDdl.split("INSERT INTO a VALUES('x\\';y'); SELECT 1", SandboxDdl.Dialect.MYSQL)).hasSize(2);
		assertThat(SandboxDdl.split("INSERT INTO a VALUES('x\\'); SELECT 1", SandboxDdl.Dialect.POSTGRESQL)).hasSize(2);
		assertThat(SandboxDdl.split("DO $$ BEGIN PERFORM 1; END $$; SELECT 1", SandboxDdl.Dialect.POSTGRESQL)).hasSize(2);
	}

	@Test
	void Oracle은_PLSQL을_슬래시_줄까지_한_문장으로_본다() {
		String text = "SET DEFINE OFF\n-- 주석;\nCREATE TABLE a(x VARCHAR2(9) DEFAULT 'p;q');\n"
			+ "CREATE OR REPLACE TRIGGER a_t BEFORE INSERT ON a FOR EACH ROW\nBEGIN\n  :new.x := q'[it's;]';\nEND;\n/\n"
			+ "UPDATE a\nSET x = '1';\nCREATE TYPE t AS OBJECT(n NUMBER);\n/\n";

		assertThat(SandboxDdl.split(text, SandboxDdl.Dialect.ORACLE)).containsExactly(
			"CREATE TABLE a(x VARCHAR2(9) DEFAULT 'p;q')",
			"CREATE OR REPLACE TRIGGER a_t BEFORE INSERT ON a FOR EACH ROW\nBEGIN\n  :new.x := q'[it's;]';\nEND;",
			"UPDATE a\nSET x = '1'",
			"CREATE TYPE t AS OBJECT(n NUMBER)");
	}

	@Test
	void 원본_폴더_경로만_사본으로_바꾼다() {
		SandboxPaths linux = new SandboxPaths("/app/shop", "/work/copy");
		assertThat(linux.shift("/app/shop/up /app/shop2/up /other/app/shop.bak")).isEqualTo("/work/copy/up /app/shop2/up /other/app/shop.bak");

		SandboxPaths windows = new SandboxPaths("D:/App/shop", "C:/work/copy");
		assertThat(windows.shift("d:/app/SHOP/up D:\\App\\shop\\up \"D:\\\\App\\\\shop\\\\up\""))
			.isEqualTo("C:/work/copy/up C:\\work\\copy\\up \"C:\\\\work\\\\copy\\\\up\"");
	}

	@Test
	void 잡지_못하는_라이브러리는_알아본다() throws IOException {
		Path war = this.dir.resolve("app.war");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(war))) {
			for (String name : List.of("WEB-INF/lib/lettuce-core-6.8.jar", "WEB-INF/lib/jedis-5.0.jar")) {
				out.putNextEntry(new ZipEntry(name));
				out.closeEntry();
			}
		}

		assertThat(SandboxArtifact.unguarded(war.toFile(), List.of())).containsExactly("lettuce-core-6.8.jar");
		assertThat(SandboxArtifact.unguarded(war.toFile(), List.of("lettuce-core"))).isEmpty();
	}

	@Test
	void 원래_DB_연결은_돌리고_그_밖은_끊는다() throws Exception {
		InetAddress loopback = InetAddress.getByName("127.0.0.1");
		try (ServerSocket clone = new ServerSocket(0, 5, loopback);
			SandboxGuard guard = new SandboxGuard(List.of(new SandboxGuard.Endpoint("localhost", 39999)), clone.getLocalPort(), false)) {
			int proxyPort = Integer.parseInt(guard.jvmOptions().replaceAll(".*socksProxyPort=([0-9]+).*", "$1"));
			Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(loopback, proxyPort));
			try (Socket original = new Socket(proxy)) {
				original.connect(new InetSocketAddress(loopback, 39999), 5000);
				try (Socket received = clone.accept()) {
					assertThat(received.isConnected()).isTrue();
				}
			}
			boolean blocked = false;
			try (Socket other = new Socket(proxy)) {
				other.connect(new InetSocketAddress(loopback, 39998), 5000);
			} catch (IOException e) {
				blocked = true;
			}
			assertThat(blocked).isTrue();
			assertThat(guard.denied()).hasSize(1);
		}
	}

	@Test
	void 테스트_서버는_개발_DB로_그대로_잇되_연결_수를_센다() throws Exception {
		InetAddress loopback = InetAddress.getByName("127.0.0.1");
		try (ServerSocket db = new ServerSocket(0, 5, loopback);
			SandboxGuard guard = new SandboxGuard(List.of(), 0, false)) {
			guard.watch(new SandboxGuard.Endpoint("localhost", db.getLocalPort()));
			int proxyPort = Integer.parseInt(guard.jvmOptions().replaceAll(".*socksProxyPort=([0-9]+).*", "$1"));
			Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(loopback, proxyPort));
			try (Socket app = new Socket(proxy)) {
				app.connect(new InetSocketAddress(loopback, db.getLocalPort()), 5000);
				try (Socket received = db.accept()) {
					assertThat(received.isConnected()).isTrue();
				}
			}
			assertThat(guard.watchedCount()).isEqualTo(1);
		}
	}

	@Test
	void 테스트_중인_step은_테스트_DB와_테스트_서버만_본다() {
		TestServerRegistry registry = new TestServerRegistry();
		StepRef step = new StepRef("run", "step34");
		registry.opened(step, "http://localhost:40001/", Map.of("test-main", Map.of("url", "jdbc:mysql://127.0.0.1/shop")));

		assertThat(registry.check("dbQuery", "{\"alias\":\"test-main\"}", step)).isNull();
		assertThat(registry.check("dbQuery", "{\"alias\":\"shop.main\"}", step)).isNotNull();
		assertThat(registry.check("browser_click", "{}", step)).as("앞 step이 열어 둔 화면").isNotNull();
		assertThat(registry.check("browser_navigate", "{\"url\":\"http://localhost:40001/list\"}", step)).isNull();
		assertThat(registry.check("browser_click", "{}", step)).isNull();
		assertThat(registry.check("browser_navigate", "{\"url\":\"http://localhost:8080/list\"}", step)).isNotNull();
		assertThat(registry.check("dbQuery", "{\"alias\":\"shop.main\"}", new StepRef("run", "step07"))).isNull();

		registry.stepEnded(step);
		assertThat(registry.check("dbQuery", "{\"alias\":\"shop.main\"}", step)).isNull();
	}

}

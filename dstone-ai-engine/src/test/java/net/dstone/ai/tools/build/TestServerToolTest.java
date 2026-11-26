package net.dstone.ai.tools.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.tools.build.sandbox.SandboxGuard;
import net.dstone.ai.tools.build.testserver.RunLayer;
import net.dstone.ai.tools.build.testserver.TestDriverJar;
import net.dstone.ai.tools.build.testserver.TomcatBase;

/**
 * <pre>
 * 테스트 서버의 부품을 서버 없이 봅니다: server.xml·Context 파일, DB 주소 읽기, 실행 층(URL·경로 바꾸기, 바뀐 것만 옮기기).
 * server.xml 모양은 Windows 개발 PC의 Tomcat 9처럼, Host 안에 사람이 등록한 다른 앱 Context가 있는 경우를 본떴습니다.
 * </pre>
 */
public class TestServerToolTest {

	private static final String SERVER_XML = "<Server port=\"8005\" shutdown=\"SHUTDOWN\">\n"
		+ "  <Service name=\"Catalina\">\n"
		+ "    <Connector port=\"8080\" protocol=\"HTTP/1.1\" connectionTimeout=\"20000\" redirectPort=\"8443\" />\n"
		+ "    <!-- <Connector port=\"8443\" protocol=\"HTTP/1.1\" SSLEnabled=\"true\" /> -->\n"
		+ "    <!-- <Context path=\"/old\" docBase=\"C:/old\" /> -->\n"
		+ "    <Connector protocol=\"AJP/1.3\" port=\"8009\" redirectPort=\"8443\" />\n"
		+ "    <Engine name=\"Catalina\" defaultHost=\"localhost\">\n"
		+ "      <Host name=\"localhost\" appBase=\"webapps\" unpackWARs=\"true\" autoDeploy=\"true\">\n"
		+ "        <Context path=\"/webapp\" docBase=\"C:/somewhere/webapp\" reloadable=\"true\" />\n"
		+ "        <Context path=\"/other\" docBase=\"C:/other\"><Resource name=\"jdbc/x\" /></Context>\n"
		+ "      </Host>\n"
		+ "    </Engine>\n"
		+ "  </Service>\n"
		+ "</Server>\n";

	@Test
	void server_xml은_포트를_바꾸고_AJP와_다른_앱_Context를_뺀다() {
		String patched = TomcatBase.patchServerXml(SERVER_XML, 51234);
		assertThat(patched).contains("<Server port=\"-1\"").contains("<Connector port=\"51234\" protocol=\"HTTP/1.1\"");
		assertThat(patched).doesNotContain("port=\"8009\"").doesNotContain("docBase=\"C:/somewhere/webapp\"").doesNotContain("jdbc/x");
		assertThat(patched).contains("<!-- <Context path=\"/old\" docBase=\"C:/old\" /> -->");
		assertThat(patched).contains("<Host name=\"localhost\" appBase=\"webapps\"");
	}

	@Test
	void 콘솔_인코딩을_UTF8로_바꾸거나_덧붙인다() {
		assertThat(TomcatBase.patchConsoleEncoding("a=1\njava.util.logging.ConsoleHandler.encoding = MS949\nb=2\n"))
			.contains("java.util.logging.ConsoleHandler.encoding = UTF-8").doesNotContain("MS949").contains("b=2");
		assertThat(TomcatBase.patchConsoleEncoding("a=1")).endsWith("java.util.logging.ConsoleHandler.encoding = UTF-8\n");
	}

	@TempDir
	Path dir;

	@Test
	void Context_파일은_실행_층을_먼저_리비전을_나중에_겹친다() {
		File run = this.dir.resolve("run").toFile();
		File rev = this.dir.resolve("rev/webapp").toFile();
		TomcatBase.JndiResource jndi = new TomcatBase.JndiResource();
		jndi.name = "jdbc/main";
		jndi.url = "jdbc:mysql://db:3306/app";
		jndi.username = "app";
		jndi.password = "p&w";
		String xml = TomcatBase.contextXml(new File(run, "webapp"), new File(run, "classes"), rev, new File(rev, "WEB-INF/classes"), new File(rev, "WEB-INF/lib"), List.of(jndi));
		assertThat(xml).contains("PreResources").contains("webAppMount=\"/WEB-INF/classes\"").contains("PostResources");
		assertThat(xml.indexOf("PreResources")).isLessThan(xml.indexOf("PostResources"));
		assertThat(xml).contains("url=\"jdbc:dstonetest:jdbc:mysql://db:3306/app\"").contains("password=\"p&amp;w\"").contains("RewriteValve");
		assertThat(TomcatBase.contextName("/")).isEqualTo("ROOT");
		assertThat(TomcatBase.contextName("/a/b")).isEqualTo("a#b");
	}

	@Test
	void JDBC_URL에서_DB_주소를_읽는다() {
		assertThat(TestServerTool.endpointsOf("jdbc:mysql://localhost:3306/app?x=1")).containsExactly(new SandboxGuard.Endpoint("localhost", 3306));
		assertThat(TestServerTool.endpointsOf("jdbc:log4jdbc:postgresql://db/app")).containsExactly(new SandboxGuard.Endpoint("db", 5432));
		assertThat(TestServerTool.endpointsOf("jdbc:oracle:thin:@db:1521:ORCL")).containsExactly(new SandboxGuard.Endpoint("db", 1521));
		assertThat(TestServerTool.endpointsOf("jdbc:oracle:thin:@//db2/svc")).containsExactly(new SandboxGuard.Endpoint("db2", 1521));
		assertThat(TestServerTool.endpointsOf("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a)(PORT=1530))(CONNECT_DATA=(SERVICE_NAME=S)))"))
			.containsExactly(new SandboxGuard.Endpoint("a", 1530));
		assertThat(TestServerTool.endpointsOf("jdbc:mysql://h1:3306,h2:3307/app")).hasSize(2);
	}

	@Test
	void 예외_설정은_적은_프로젝트에만_걸린다() {
		String setting = "shop=ojdbc8, shop=lettuce, stock=kafka-clients";
		assertThat(TestServerTool.forProject(setting, "shop")).isEqualTo("ojdbc8,lettuce");
		assertThat(TestServerTool.forProject(setting, "sho")).isEmpty();
	}

	@Test
	void 실행_층은_URL과_원본_경로를_바꾸고_웹_파일과_리소스를_제자리에_둔다() throws IOException {
		File rev = this.dir.resolve("rev").toFile();
		File src = new File(rev, "src");
		write(new File(src, "src/main/resources/globals.xml"), "<e key=\"Url\">jdbc:mysql://localhost:3306/testApp?useSSL=false&amp;a=b</e>\n"
			+ "<e key=\"Driver\">com.mysql.cj.jdbc.Driver</e>\n<e key=\"Up\">/app/testApp/uploadData</e>");
		write(new File(src, "src/main/resources/context-ds.xml"), "<jee:jndi-lookup jndi-name=\"jdbc/main\" />");
		write(new File(src, "src/main/webapp/WEB-INF/web.xml"), "<res-ref-name>jdbc/main</res-ref-name>");
		write(new File(src, "src/main/java/a/Keep.java"), "class Keep {}");
		File overlay = this.dir.resolve("dev").toFile();
		write(new File(overlay, "src/main/webapp/list.jsp"), "<p>new</p>");
		File deleteList = this.dir.resolve("delete.txt").toFile();
		write(deleteList, "src/main/webapp/old.jsp\n");
		RunLayer.Spec spec = new RunLayer.Spec();
		spec.revDir = rev;
		spec.revSrc = src;
		spec.webRoot = new File(src, "target/app");
		spec.classesDir = new File(src, "target/classes");
		spec.originalSource = "/app/testApp";
		spec.dataDir = this.dir.resolve("run/data").toFile();
		spec.overlayDir = overlay;
		spec.deleteList = deleteList;
		spec.stageDir = this.dir.resolve("run/stage").toFile();
		spec.dbUrls.add("jdbc:mysql://localhost:3306/testApp?useSSL=false");
		spec.jndiNames.add("jdbc/main");

		RunLayer.Result result = RunLayer.build(spec, null);

		assertThat(result.codeProblem).isNull();
		assertThat(result.urlsFound).containsExactly("jdbc:mysql://localhost:3306/testApp?useSSL=false");
		String globals = Files.readString(new File(spec.stageDir, "classes/globals.xml").toPath(), StandardCharsets.UTF_8);
		assertThat(globals).contains("jdbc:dstonetest:jdbc:mysql://localhost:3306/testApp?useSSL=false&amp;a=b")
			.contains(TestDriverJar.DRIVER_CLASS).contains(spec.dataDir.getAbsolutePath().replace('\\', '/') + "/uploadData");
		assertThat(result.driversFound).containsExactly("com.mysql.cj.jdbc.Driver");
		assertThat(Files.readString(new File(spec.stageDir, "classes/context-ds.xml").toPath(), StandardCharsets.UTF_8)).contains("java:comp/env/jdbc/main");
		assertThat(new File(spec.stageDir, "webapp/WEB-INF/web.xml")).as("web.xml의 res-ref-name은 바꾸지 않아 바뀐 파일이 아님").doesNotExist();
		assertThat(new File(spec.stageDir, "webapp/list.jsp")).isFile();
		assertThat(Files.readString(new File(spec.stageDir, "webapp/WEB-INF/rewrite.config").toPath(), StandardCharsets.UTF_8)).contains("^/old\\.jsp$ - [G]");

		File live = this.dir.resolve("run/webapp").toFile();
		assertThat(RunLayer.sync(new File(spec.stageDir, "webapp"), live)).containsExactlyInAnyOrder("list.jsp", "WEB-INF/rewrite.config");
		assertThat(RunLayer.sync(new File(spec.stageDir, "webapp"), live)).isEmpty();
		write(new File(spec.stageDir, "webapp/list.jsp"), "<p>changed</p>");
		assertThat(RunLayer.sync(new File(spec.stageDir, "webapp"), live)).containsExactly("list.jsp");
	}

	private static void write(File file, String text) throws IOException {
		Files.createDirectories(file.getParentFile().toPath());
		Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
	}

}

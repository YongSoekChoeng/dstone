package net.dstone.ai.tools.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <pre>
 * 임시 시험 서버의 설정 고치기(포트, 다른 앱 Context, 콘솔 인코딩)와 pom 읽기를 봅니다.
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
		String patched = TestServerTool.patchServerXml(SERVER_XML, 51234);
		assertThat(patched).contains("<Server port=\"-1\"").contains("<Connector port=\"51234\" protocol=\"HTTP/1.1\"");
		assertThat(patched).doesNotContain("port=\"8009\"").doesNotContain("docBase=\"C:/somewhere/webapp\"").doesNotContain("jdbc/x");
		// 주석은 그대로 둔다
		assertThat(patched).contains("<!-- <Context path=\"/old\" docBase=\"C:/old\" /> -->");
		assertThat(patched).contains("<Host name=\"localhost\" appBase=\"webapps\"");
	}

	@Test
	void 콘솔_인코딩을_UTF8로_바꾸거나_덧붙인다() {
		assertThat(TestServerTool.patchConsoleEncoding("a=1\njava.util.logging.ConsoleHandler.encoding = MS949\nb=2\n"))
			.contains("java.util.logging.ConsoleHandler.encoding = UTF-8").doesNotContain("MS949").contains("b=2");
		assertThat(TestServerTool.patchConsoleEncoding("a=1")).endsWith("java.util.logging.ConsoleHandler.encoding = UTF-8\n");
	}

	@Test
	void pom에서_war와_tomcat_경로와_Java_버전을_읽는다() {
		String pom = "<project><artifactId>demo</artifactId><packaging>war</packaging>"
			+ "<properties><maven.compiler.source>1.8</maven.compiler.source></properties>"
			+ "<build><plugins><plugin><groupId>org.apache.tomcat.maven</groupId><artifactId>tomcat7-maven-plugin</artifactId>"
			+ "<configuration><port>7080</port><path>/</path></configuration></plugin></plugins></build></project>";
		assertThat(TestServerTool.isWar(pom)).isTrue();
		assertThat(TestServerTool.tomcatPathOf(pom)).isEqualTo("/");
		assertThat(TestServerTool.javaVersionOfPom(pom)).isEqualTo("8");
		assertThat(TestServerTool.patchTomcatPort(pom, 51000)).contains("<port>51000</port>").doesNotContain("7080");
		assertThat(TestServerTool.keyOf(null, "D:/AppHome/demo/")).isEqualTo("demo");
		assertThat(TestServerTool.keyOf("my app", "x")).isEqualTo("my_app");
	}

	@TempDir
	Path dir;

	@Test
	void 정적_경로는_Context_파일로_열고_격리_때는_사본_폴더로_바꾼다() throws IOException {
		File source = Files.createDirectories(this.dir.resolve("src/myApp")).toFile();
		File app = Files.createDirectories(this.dir.resolve("work/app")).toFile();
		File base = Files.createDirectories(this.dir.resolve("work/tomcat")).toFile();
		File outside = Files.createDirectories(this.dir.resolve("shared/img")).toFile();
		List<String[]> paths = new ArrayList<String[]>();
		paths.add(new String[] { "/Upload", source.getPath().replace('\\', '/') + "/uploadData/Upload" });
		paths.add(new String[] { "/files/img", outside.getPath().replace('\\', '/') });
		paths.add(new String[] { "/none", this.dir.resolve("nowhere").toString().replace('\\', '/') });

		String line = TestServerTool.addStaticContexts(base, paths, source, app, true);

		File upload = new File(base, "conf/Catalina/localhost/Upload.xml");
		assertThat(Files.readString(upload.toPath(), StandardCharsets.UTF_8)).contains(new File(app, "uploadData/Upload").getAbsolutePath());
		assertThat(new File(app, "uploadData/Upload")).isDirectory();
		assertThat(Files.readString(new File(base, "conf/Catalina/localhost/files#img.xml").toPath(), StandardCharsets.UTF_8)).contains(outside.getAbsolutePath());
		assertThat(new File(base, "conf/Catalina/localhost/none.xml")).doesNotExist();
		assertThat(line).contains("/Upload →").contains("폴더가 없어 열지 않음");
		assertThat(TestServerTool.addStaticContexts(base, new ArrayList<String[]>(), source, app, true)).contains("없음");
	}

}

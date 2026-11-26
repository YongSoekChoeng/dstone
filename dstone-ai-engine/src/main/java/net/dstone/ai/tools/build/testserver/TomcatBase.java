package net.dstone.ai.tools.build.testserver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 실행 전용 CATALINA_BASE(runs/{실행ID}/tomcat)를 만듭니다. 설계: docs/temp/dstone-ai-테스트관련재설계/01.설계.md 5.1·5.2·5.6절.
 *
 * 설치된 Tomcat(CATALINA_HOME)의 conf를 복사하고, HTTP 포트만 빈 포트로 바꾸고, shutdown 포트와 AJP는 끄고, 사람이 등록한 다른 앱의 Context는 뺍니다.
 * 앱은 Context 파일(conf/Catalina/localhost/{이름}.xml) 하나로 올립니다.
 *   docBase       = runs/{실행ID}/webapp (실행 층, 쓰기 가능)
 *   PreResources  = runs/{실행ID}/classes → /WEB-INF/classes (컴파일·바꾼 설정이 먼저 읽힘)
 *   PostResources = 리비전의 웹 루트 → / (그리고 따로 입력한 클래스·lib 폴더)
 * Resources는 Tomcat 8.5 이상에서만 됩니다. Context 파일의 수정 시각을 바꾸면 Tomcat이 그 앱을 다시 올립니다(context reload).
 * </pre>
 */
public final class TomcatBase {

	/** Context 파일에 넣을 JNDI DataSource 하나 */
	public static final class JndiResource {
		public String name;
		public String url;
		public String username;
		public String password;
	}

	private TomcatBase() {
	}

	/**
	 * CATALINA_BASE를 새로 만들고 Context 파일 경로를 돌려줍니다.
	 *
	 * @param staticPaths [주소, 폴더] 목록(테스트서버 정적 경로)
	 */
	public static File prepare(File tomcatHome, File base, int port, String contextPath, File runWebapp, File runClasses, File revWebRoot,
		File revClasses, File revLib, List<JndiResource> resources, File driverJar, List<String[]> staticPaths, List<String> notes) throws IOException {
		for (String name : new String[] { "conf", "work", "temp", "lib", "webapps" }) {
			RunLayer.deleteTree(new File(base, name).toPath());
			Files.createDirectories(new File(base, name).toPath());
		}
		Files.createDirectories(new File(base, "logs").toPath());
		RunLayer.copyTree(new File(tomcatHome, "conf").toPath(), new File(base, "conf").toPath());
		RunLayer.deleteTree(new File(base, "conf/Catalina").toPath());
		File logging = new File(base, "conf/logging.properties");
		if (logging.isFile()) {
			String props = new String(Files.readAllBytes(logging.toPath()), StandardCharsets.ISO_8859_1);
			Files.write(logging.toPath(), patchConsoleEncoding(props).getBytes(StandardCharsets.ISO_8859_1));
		}
		// JSP를 바꾸면 바로 보이게(기본은 4초마다 확인)
		File webXml = new File(base, "conf/web.xml");
		if (webXml.isFile()) {
			Files.writeString(webXml.toPath(), patchJspServlet(Files.readString(webXml.toPath(), StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
		}
		File serverXml = new File(base, "conf/server.xml");
		Files.writeString(serverXml.toPath(), patchServerXml(Files.readString(serverXml.toPath(), StandardCharsets.UTF_8), port), StandardCharsets.UTF_8);
		Files.copy(driverJar.toPath(), new File(base, "lib/" + driverJar.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);

		File contexts = new File(base, "conf/Catalina/localhost");
		Files.createDirectories(contexts.toPath());
		File descriptor = new File(contexts, contextName(contextPath) + ".xml");
		Files.writeString(descriptor.toPath(), contextXml(runWebapp, runClasses, revWebRoot, revClasses, revLib, resources), StandardCharsets.UTF_8);
		// 비밀번호가 들어 있으므로 주인만 읽게 한다(Windows는 무시됨)
		descriptor.setReadable(false, false);
		descriptor.setReadable(true, true);
		for (String[] path : staticPaths) {
			File folder = new File(path[1]);
			if (!folder.isDirectory()) {
				notes.add("테스트서버 정적 경로 " + path[0] + "의 폴더가 없어 열지 않았습니다: " + path[1]);
				continue;
			}
			Files.writeString(new File(contexts, contextName(path[0]) + ".xml").toPath(), "<Context docBase=\"" + xml(folder.getAbsolutePath()) + "\" />\n", StandardCharsets.UTF_8);
		}
		return descriptor;
	}

	/** 앱의 Context 파일 내용입니다. */
	public static String contextXml(File runWebapp, File runClasses, File revWebRoot, File revClasses, File revLib, List<JndiResource> resources) {
		StringBuilder out = new StringBuilder();
		out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		out.append("<!-- dstone-ai-engine 테스트 서버. 실행 층이 먼저, 리비전이 나중에 읽힙니다 -->\n");
		out.append("<Context docBase=\"").append(xml(runWebapp.getAbsolutePath())).append("\" reloadable=\"false\">\n");
		out.append("  <Resources cachingAllowed=\"false\">\n");
		out.append("    <PreResources className=\"org.apache.catalina.webresources.DirResourceSet\" base=\"").append(xml(runClasses.getAbsolutePath()))
			.append("\" webAppMount=\"/WEB-INF/classes\" />\n");
		if (revWebRoot != null) {
			out.append("    <PostResources className=\"org.apache.catalina.webresources.DirResourceSet\" base=\"").append(xml(revWebRoot.getAbsolutePath()))
				.append("\" webAppMount=\"/\" />\n");
		}
		if (revClasses != null && (revWebRoot == null || !isUnder(revClasses, new File(revWebRoot, "WEB-INF/classes")))) {
			out.append("    <PostResources className=\"org.apache.catalina.webresources.DirResourceSet\" base=\"").append(xml(revClasses.getAbsolutePath()))
				.append("\" webAppMount=\"/WEB-INF/classes\" />\n");
		}
		if (revLib != null && revLib.isDirectory() && (revWebRoot == null || !isUnder(revLib, new File(revWebRoot, "WEB-INF/lib")))) {
			out.append("    <PostResources className=\"org.apache.catalina.webresources.DirResourceSet\" base=\"").append(xml(revLib.getAbsolutePath()))
				.append("\" webAppMount=\"/WEB-INF/lib\" />\n");
		}
		out.append("  </Resources>\n");
		out.append("  <Valve className=\"org.apache.catalina.valves.rewrite.RewriteValve\" />\n");
		for (JndiResource resource : resources) {
			out.append("  <Resource name=\"").append(xml(resource.name)).append("\" auth=\"Container\" type=\"javax.sql.DataSource\"")
				.append(" driverClassName=\"").append(TestDriverJar.DRIVER_CLASS).append("\" url=\"").append(xml(TestDriverJar.URL_PREFIX + resource.url)).append('"')
				.append(" username=\"").append(xml(resource.username == null ? "" : resource.username)).append('"')
				.append(" password=\"").append(xml(resource.password == null ? "" : resource.password)).append('"')
				.append(" maxTotal=\"20\" maxIdle=\"5\" maxWaitMillis=\"60000\" />\n");
		}
		out.append("</Context>\n");
		return out.toString();
	}

	/** Context 파일 이름(/ → ROOT, /a/b → a#b) */
	public static String contextName(String contextPath) {
		return "/".equals(contextPath) || contextPath.isEmpty() ? "ROOT" : contextPath.substring(1).replace('/', '#');
	}

	/** server.xml의 포트를 바꾸고 다른 앱의 Context를 뺍니다. shutdown 포트는 끄고, 첫 HTTP Connector만 남깁니다. */
	public static String patchServerXml(String xml, int port) {
		String text = xml.replaceFirst("<Server\\s+port=\"[^\"]*\"", Matcher.quoteReplacement("<Server port=\"-1\""));
		text = removeContexts(text);
		text = text.replaceAll("autoDeploy=\"false\"", "autoDeploy=\"true\"");
		Matcher m = Pattern.compile("<!--.*?-->|<Connector\\b[^>]*?(?:/>|>.*?</Connector>)", Pattern.DOTALL).matcher(text);
		StringBuilder out = new StringBuilder();
		int last = 0;
		boolean httpDone = false;
		while (m.find()) {
			String part = m.group();
			if (part.startsWith("<!--")) {
				continue;
			}
			String replaced;
			boolean http = !part.contains("AJP") && !part.contains("SSLEnabled=\"true\"");
			if (http && !httpDone) {
				replaced = part.replaceFirst("port=\"[^\"]*\"", Matcher.quoteReplacement("port=\"" + port + "\""));
				httpDone = true;
			} else {
				replaced = "<!-- dstone 테스트 서버에서 끔 -->";
			}
			out.append(text, last, m.start()).append(replaced);
			last = m.end();
		}
		out.append(text.substring(last));
		return out.toString();
	}

	/** 주석이 아닌 Context를 주석 한 줄로 바꿉니다(사람이 등록한 다른 앱). */
	public static String removeContexts(String xml) {
		Matcher m = Pattern.compile("<!--.*?-->|<Context\\b[^>]*?(?:/>|>.*?</Context>)", Pattern.DOTALL).matcher(xml);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (m.find()) {
			if (m.group().startsWith("<!--")) {
				continue;
			}
			out.append(xml, last, m.start()).append("<!-- dstone 테스트 서버에서 뺌(사람이 등록한 다른 앱) -->");
			last = m.end();
		}
		out.append(xml.substring(last));
		return out.toString();
	}

	/** conf/web.xml의 JspServlet이 JSP 변경을 매번 확인하게 합니다(modificationTestInterval 0). */
	public static String patchJspServlet(String xml) {
		String marker = "<servlet-class>org.apache.jasper.servlet.JspServlet</servlet-class>";
		if (!xml.contains(marker) || xml.contains("<param-name>modificationTestInterval</param-name>")) {
			return xml;
		}
		return xml.replace(marker, marker + "\n        <init-param><param-name>modificationTestInterval</param-name><param-value>0</param-value></init-param>"
			+ "\n        <init-param><param-name>development</param-name><param-value>true</param-value></init-param>");
	}

	/** logging.properties의 콘솔 인코딩을 UTF-8로 바꾸거나 덧붙입니다. */
	public static String patchConsoleEncoding(String props) {
		String key = "java.util.logging.ConsoleHandler.encoding";
		Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=.*$").matcher(props);
		if (m.find()) {
			return m.replaceAll(Matcher.quoteReplacement(key + " = UTF-8"));
		}
		return props + (props.endsWith("\n") ? "" : "\n") + key + " = UTF-8\n";
	}

	/** 설치된 Tomcat의 메이저 버전(lib/catalina.jar의 ServerInfo). 모르면 0 */
	public static int majorVersion(File tomcatHome) {
		File release = new File(tomcatHome, "RELEASE-NOTES");
		try {
			if (release.isFile()) {
				Matcher m = Pattern.compile("Apache Tomcat Version (\\d+)\\.").matcher(Files.readString(release.toPath(), StandardCharsets.ISO_8859_1));
				if (m.find()) {
					return Integer.parseInt(m.group(1));
				}
			}
		} catch (IOException e) {
			// 모름
		}
		return 0;
	}

	/** 띄울 명령(catalina run)입니다. */
	public static List<String> command(File tomcatHome) {
		List<String> command = new ArrayList<String>();
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			command.add("cmd.exe");
			command.add("/c");
			command.add(new File(tomcatHome, "bin/catalina.bat").getPath());
		} else {
			command.add("sh");
			command.add(new File(tomcatHome, "bin/catalina.sh").getPath());
		}
		command.add("run");
		return command;
	}

	private static boolean isUnder(File file, File dir) {
		Path f = file.toPath().toAbsolutePath().normalize();
		Path d = dir.toPath().toAbsolutePath().normalize();
		return f.equals(d) || f.startsWith(d);
	}

	static String xml(String value) {
		return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
	}

}

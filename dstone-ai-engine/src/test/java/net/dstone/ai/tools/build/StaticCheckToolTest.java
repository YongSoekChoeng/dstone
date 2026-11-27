package net.dstone.ai.tools.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <pre>
 * 서버를 띄우지 않는 형식 검사(StaticCheckTool)를 봅니다.
 * 틀렸다고 확신할 수 있는 것만 problem이어야 해서, 맞는 파일이 걸리지 않는지도 함께 봅니다.
 * JavaScript 검사는 node가 있을 때만 돕니다(없으면 skipped).
 * </pre>
 */
public class StaticCheckToolTest {

	@TempDir
	Path dir;

	private File write(String relative, String text) throws IOException {
		File file = this.dir.resolve(relative).toFile();
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	@SuppressWarnings("unchecked")
	private static List<String> problems(Map<String, Object> result) {
		return (List<String>) result.get("problems");
	}

	/** prefix로 시작하고 text가 든 문제가 있는지 */
	private static boolean has(List<String> problems, String prefix, String text) {
		for (int i = 0; i < problems.size(); i++) {
			if (problems.get(i).startsWith(prefix) && problems.get(i).contains(text)) {
				return true;
			}
		}
		return false;
	}

	@Test
	void 닫히지_않은_코드_블록과_칸이_어긋난_표를_찾는다() throws IOException {
		File doc = this.write("03-1.design.md", "# 설계\n\n| 파일 | 변경 전 | 변경 후 |\n|---|---|---|\n| A.java | `a | b` | c |\n| B.java | x | y | z |\n\n```java\nclass A {}\n```\n\n```\n");
		Map<String, Object> result = new StaticCheckTool().checkDocuments(Arrays.asList(doc.getAbsolutePath()), null, null);
		assertThat(result.get("route")).isEqualTo("problem");
		assertThat(problems(result)).hasSize(2);
		assertThat(problems(result).get(0)).startsWith("03-1.design.md:6 ").contains("칸 수");
		assertThat(problems(result).get(1)).startsWith("03-1.design.md:12 ").contains("닫히지 않음");
	}

	@Test
	void 맞는_문서와_DDL은_통과한다() throws IOException {
		File doc = this.write("03-1.design.md", "# 설계\n\n| a | b |\n| :-- | --: |\n| `x|y` | z \\| w |\n\n````md\n```sql\nselect 1;\n```\n````\n");
		File ddl = this.write("03-3.ddl.md", "# DDL\n\n```sql\nCREATE TABLE t (id INT);\n```\n");
		Map<String, Object> result = new StaticCheckTool().checkDocuments(Arrays.asList(doc.getAbsolutePath(), this.dir.resolve("none.md").toString()), ddl.getAbsolutePath(), null);
		assertThat(result.get("route")).isEqualTo("ok");
		assertThat(result.get("checked")).isEqualTo(Integer.valueOf(1));
		assertThat((List<?>) result.get("skipped")).hasSize(1);
	}

	@Test
	void DDL은_격리_서버와_같은_규칙으로_읽는다() throws IOException {
		File ddl = this.write("03-3.ddl.md", "# DDL\n\n```sql\nCREATE TABLE t (id INT);\n```\n- 설명\n```\n");
		Map<String, Object> result = new StaticCheckTool().checkDocuments(null, ddl.getAbsolutePath(), null);
		assertThat(result.get("route")).isEqualTo("problem");
		assertThat(problems(result).get(0)).contains("DDL을 읽을 수 없음").contains("닫히지 않은 코드 블록");
	}

	@Test
	void XML_형식_오류와_statement_id_중복을_찾는다() throws IOException {
		this.write("dev/src/main/resources/sqlmap/a-mapper.xml", "<?xml version=\"1.0\" encoding=\"EUC-KR\"?>\n"
			+ "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
			+ "<mapper namespace=\"A\">\n  <select id=\"list\">SELECT 1 WHERE a &lt; 2</select>\n  <insert id=\"list\">INSERT</insert>\n</mapper>\n");
		this.write("dev/src/main/resources/sqlmap/b-mapper.xml", "<mapper namespace=\"B\">\n  <select id=\"x\">SELECT 1 WHERE a < 2</select>\n</mapper>\n");
		Map<String, Object> result = new StaticCheckTool().checkSources(this.dir.resolve("dev").toString());
		assertThat(result.get("route")).isEqualTo("problem");
		assertThat(has(problems(result), "src/main/resources/sqlmap/a-mapper.xml", "두 번")).isTrue();
		assertThat(has(problems(result), "src/main/resources/sqlmap/b-mapper.xml:2", "XML 형식 오류")).isTrue();
	}

	@Test
	void JSP의_스크립틀릿과_태그_짝을_본다() throws IOException {
		this.write("dev/ok.jsp", "<%@ taglib prefix=\"c\" uri=\"http://java.sun.com/jsp/jstl/core\" %>\n<%-- <c:if> 주석 --%>\n"
			+ "<c:if test=\"${a > b}\"><c:out value=\"x\"/></c:if>\n<% if (a < b) { %><div><% } %>\n");
		this.write("dev/bad.jsp", "<%@ taglib prefix=\"c\" uri=\"http://java.sun.com/jsp/jstl/core\" %>\n<c:forEach items=\"${list}\" var=\"i\">\n<c:if test=\"${i}\">\n</c:forEach>\n");
		this.write("dev/open.jsp", "<div>\n<% String a = \"x\";\n</div>\n");
		Map<String, Object> result = new StaticCheckTool().checkSources(this.dir.resolve("dev").toString());
		assertThat(problems(result)).hasSize(2);
		assertThat(has(problems(result), "bad.jsp:4 ", "</c:forEach>")).isTrue();
		assertThat(has(problems(result), "open.jsp:2 ", "%>")).isTrue();
	}

	@Test
	void JavaScript는_JSP_코드가_섞이지_않은_블록만_검사한다() throws IOException {
		this.write("dev/a.jsp", "<html>\n<script type=\"text/x-kendo-template\">#= name #</script>\n"
			+ "<script>\nvar a = '${x}'; if (\n</script>\n"
			+ "<script type=\"text/javascript\">\nfunction ok() { return 1; }\nfunction bad() { return (1; }\n</script>\n</html>\n");
		this.write("dev/b.js", "export function f() { return 1 }\n");
		Map<String, Object> result = new StaticCheckTool().checkSources(this.dir.resolve("dev").toString());
		List<?> skipped = (List<?>) result.get("skipped");
		if (skipped.toString().contains("node를 실행할 수 없음")) {
			return;
		}
		assertThat(problems(result)).hasSize(1);
		assertThat(problems(result).get(0)).startsWith("a.jsp:8 ").contains("JavaScript 구문 오류");
		assertThat(skipped.toString()).contains("JSP 코드가 섞인");
	}

	@Test
	void 글자_수_상한을_넘은_문서를_찾는다() throws IOException {
		File doc = this.write("03-1.design.md", "# 설계\n\n0123456789\n");
		List<Map<String, Object>> limits = Arrays.asList(Map.<String, Object>of("path", doc.getAbsolutePath(), "maxChars", Integer.valueOf(10)));
		Map<String, Object> result = new StaticCheckTool().checkDocuments(Arrays.asList(doc.getAbsolutePath()), null, limits);
		assertThat(result.get("route")).isEqualTo("problem");
		assertThat(problems(result).get(0)).startsWith("03-1.design.md ").contains("크기 초과");
		limits = Arrays.asList(Map.<String, Object>of("path", doc.getAbsolutePath(), "maxChars", Integer.valueOf(1000)));
		assertThat(new StaticCheckTool().checkDocuments(null, null, limits).get("route")).isEqualTo("ok");
	}

}

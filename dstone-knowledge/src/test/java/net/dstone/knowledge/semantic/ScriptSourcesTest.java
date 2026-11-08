package net.dstone.knowledge.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 화면(JSP)이 &lt;script src&gt; 로 불러오는 스크립트 경로를 찾는 로직을 확인합니다. DB나 Spring 없이 도는 테스트입니다.
 * </pre>
 */
public class ScriptSourcesTest {

	private final ScriptSources scriptSources = new ScriptSources();

	@Test
	public void 실행할_때_붙는_앞부분을_떼고_경로를_찾는다() {
		// dstone-boot 의 knowledge/impact/impact.jsp 모양
		String jsp = "<body>\n"
				+ "<script src=\"<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js\"></script>\n"
				+ "<script src=\"<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/impact.js\"></script>\n"
				+ "<script>\n  DstoneKnowledgeImpact.init();\n</script>\n"
				+ "<SCRIPT type='text/javascript' src='${ctx}/js/common.js?v=3'></SCRIPT>\n"
				+ "<script src=\"<c:url value='/js/util.js'/>\"></script>\n"
				+ "<script src=\"/knowledge/assets/js/impact.js\"></script>\n"
				+ "</body>";
		List<ScriptSources.Source> sources = scriptSources.find(jsp);
		List<String> paths = new ArrayList<String>();
		for (int i = 0; i < sources.size(); i++) {
			paths.add(sources.get(i).getPath());
		}
		// 같은 경로는 한 번만, src 가 없는 <script> 는 빠진다.
		assertEquals("[knowledge/assets/js/knowledge-common.js, knowledge/assets/js/impact.js, js/common.js, js/util.js]", paths.toString());
		assertEquals(jsp.indexOf("<script src=\"<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/impact.js"), sources.get(1).getIndex());
	}

	@Test
	public void 다른_서버의_파일과_실행할_때_정해지는_경로는_뺀다() {
		assertNull(ScriptSources.pathOf("https://cdn.example.com/jquery.min.js"));
		assertNull(ScriptSources.pathOf("//cdn.example.com/jquery.min.js"));
		assertNull(ScriptSources.pathOf("${ctx}/js/${name}.js"));
		assertNull(ScriptSources.pathOf("/js/app.jsp"));
		assertNull(ScriptSources.pathOf("<%=ctx%>"));
		assertEquals("js/a.js", ScriptSources.pathOf("../js/a.js"));
		assertEquals("js/a.js", ScriptSources.pathOf("./js/a.js"));
	}

}

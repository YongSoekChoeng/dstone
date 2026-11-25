package net.dstone.ai.tools.knowledge;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <pre>
 * 본뜰 기능의 코드 문서(knowledgeFeatureCode)를 만드는 부분을 dstone-knowledge 없이 봅니다.
 * 서버 응답은 GET /api/revisions/{리비전}/feature 모양을 줄여 손으로 적었습니다.
 * </pre>
 */
public class KnowledgeFeatureToolTest {

	@TempDir
	Path dir;

	private static final String FEATURE = "{"
		+ "\"entries\":[{\"entry\":\"views/board/list.jsp\",\"kind\":\"SCREEN\",\"matched\":\"web/views/board/list.jsp\"},"
		+ "{\"entry\":\"/none.do\",\"kind\":\"NOT_FOUND\",\"matched\":null}],"
		+ "\"screens\":[{\"path\":\"web/views/board/list.jsp\",\"hop\":0,\"reason\":\"시작 화면\",\"scripts\":[]},"
		+ "{\"path\":\"web/views/board/pop.jsp\",\"hop\":1,\"reason\":\"메소드가 돌려주는 화면\",\"scripts\":[]}],"
		+ "\"endpoints\":[{\"paths\":[\"/board/list.ajax\"],\"handler\":\"a.b.BoardController#list(Vo)\",\"requestedBy\":[\"web/views/board/list.jsp\"]}],"
		+ "\"methods\":[{\"owner\":\"a.b.BoardController\",\"layer\":\"CONTROLLER\",\"signature\":\"list(Vo)\",\"path\":\"src/BoardController.java\",\"lineStart\":2,\"lineEnd\":4,\"depth\":1},"
		+ "{\"owner\":\"a.b.BoardDao\",\"layer\":\"REPOSITORY\",\"signature\":\"select(Vo)\",\"path\":\"src/BoardDao.java\",\"lineStart\":1,\"lineEnd\":1,\"depth\":4}],"
		+ "\"statements\":[{\"statement\":\"Board.select\",\"path\":\"src/board-mapper.xml\",\"lineStart\":2,\"lineEnd\":2,\"tables\":\"TB_BOARD(R)\"}],"
		+ "\"shared\":[],\"models\":[\"a.b.Vo\"],\"truncated\":false,\"notes\":[]}";

	private void write(String relative, String text) throws IOException {
		File file = this.dir.resolve(relative).toFile();
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void 시작점은_한_줄에_하나_목록_기호와_설명을_뗀다() {
		List<String> entries = KnowledgeFeatureTool.splitEntries("## 참고 기능\n- `/system/member/member.go` — 회원 관리\n* WEB-INF/views/a.jsp\n\n/x.do, /y.do\n- `/x.do`\n- board.jsp\n\n(목록·등록팝업·파일업로드를 한 기능에서 모두 하는 회원 관리. 썸네일 팝업은 확인 필요.)\n");
		assertThat(entries).containsExactly("/system/member/member.go", "WEB-INF/views/a.jsp", "/x.do", "/y.do", "board.jsp");
	}

	@Test
	void 원본에서_줄_범위대로_옮기고_한도를_넘으면_위치만_적는다() throws Exception {
		this.write("web/views/board/list.jsp", "<html>\n" + "<div>list</div>\n".repeat(300) + "</html>\n");
		this.write("src/BoardController.java", "class C {\n  Object list(Vo vo) {\n    return dao.select(vo);\n  }\n}\n");
		this.write("src/BoardDao.java", "Object select(Vo vo) { return null; }\n");
		this.write("src/board-mapper.xml", "<mapper>\n<select id=\"select\">SELECT * FROM TB_BOARD</select>\n</mapper>\n");
		JsonNode feature = new ObjectMapper().readTree(FEATURE);
		List<String> notFound = new ArrayList<String>();
		notFound.add("/none.do");

		String markdown = KnowledgeFeatureTool.render(feature, KnowledgeFeatureTool.piecesOf(feature), this.dir.toFile(), 50000, notFound);
		assertThat(markdown).contains("| /board/list.ajax | `BoardController#list(Vo)` | web/views/board/list.jsp |");
		assertThat(markdown).contains("```java\n  Object list(Vo vo) {\n    return dao.select(vo);\n  }\n```");
		assertThat(markdown).contains("```xml\n<select id=\"select\">SELECT * FROM TB_BOARD</select>\n```");
		assertThat(markdown).contains("```jsp\n<html>\n<div>list</div>\n");
		// 팝업 화면은 원본에 없어서 위치만
		assertThat(markdown).contains("- web/views/board/pop.jsp (메소드가 돌려주는 화면) (원본에 파일이 없음)");
		assertThat(markdown).contains("찾지 못한 시작점: /none.do");
		assertThat(markdown).contains("쓰는 VO 같은 타입 `a.b.Vo`");

		// 한도를 조금씩 줄이면 우선순위가 가장 낮은 것(깊은 DAO 메소드)부터 위치만 남고, 화면은 남는다
		String small = null;
		for (int limit = markdown.length() + 3000; limit > 1000; limit -= 20) {
			String candidate = KnowledgeFeatureTool.render(feature, KnowledgeFeatureTool.piecesOf(feature), this.dir.toFile(), limit, notFound);
			if (candidate.contains("글자 한도로 위치만 적음")) {
				small = candidate;
				break;
			}
		}
		assertThat(small).contains("```jsp\n<html>");
		assertThat(small).contains("- BoardDao#select(Vo)").doesNotContain("Object select(Vo vo) { return null; }");
		assertThat(small).contains("글자 한도로 위치만 적음");
	}

}

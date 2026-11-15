package net.dstone.knowledge.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <pre>
 * 영향도 정답표: samples/struts-app 을 분석한 결과로 영향도 API 가 사람이 센 값과 같은지 봅니다.
 * 정답은 소스를 읽고 손으로 셌습니다(samples/struts-app/README.md 의 "영향도 정답표").
 *
 * 떠 있는 dstone-knowledge 를 부르는 시험이라 주소를 줄 때만 돕니다. 주지 않으면 건너뜁니다.
 *   1) struts-app 프로젝트를 등록하고 분석한다(localPath = 이 저장소의 samples/struts-app)
 *   2) mvn test -DskipTests=false -Dtest=StrutsAppImpactGoldenTest -Ddstone.knowledge.test.api=http://localhost:4081
 * 가장 최근의 READY 리비전으로 봅니다.
 * </pre>
 */
public class StrutsAppImpactGoldenTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient HTTP = HttpClient.newHttpClient();

	private static String api;
	private static long revisionId;

	@BeforeAll
	static void findRevision() throws Exception {
		api = System.getProperty("dstone.knowledge.test.api");
		Assumptions.assumeTrue(api != null && api.length() > 0, "dstone.knowledge.test.api 를 주지 않아 건너뜁니다");
		JsonNode revisions = get("/api/projects/struts-app/revisions");
		revisionId = -1;
		for (int i = 0; i < revisions.size(); i++) {
			JsonNode revision = revisions.get(i);
			if ("READY".equals(revision.path("status").asText())) {
				revisionId = Math.max(revisionId, revision.path("revisionId").asLong());
			}
		}
		Assumptions.assumeTrue(revisionId > 0, "struts-app 의 분석이 끝난 리비전이 없어 건너뜁니다");
	}

	@Test
	void tableAll() throws Exception {
		JsonNode result = impact("table=TB_BOARD");
		assertEquals(map("countBoard", "HIGH", "deleteBoard", "HIGH", "insertBoard", "HIGH", "selectBoardList", "HIGH"), starts(result));
		// admin.do 는 DispatchAction 이라 타입까지만 이어진다 → 짐작(LOW)
		assertEquals(map("/board/admin.do", "LOW", "/board/list.do", "HIGH", "/board/save.do", "HIGH"), rows(result, "endpoints"));
		// header.jsp 는 화면 안의 주소 글자로 이어진다 → MEDIUM. list.jsp · edit.jsp 는 액션이 끝나고 여는 화면
		assertEquals(map("board/edit.jsp", "HIGH", "board/list.jsp", "HIGH", "common/header.jsp", "MEDIUM"), rows(result, "screens"));
		assertEquals(2, result.path("touches").get(0).path("writes").size());
		assertEquals(2, result.path("touches").get(0).path("reads").size());
	}

	@Test
	void tableWriteOnly() throws Exception {
		JsonNode result = impact("table=TB_BOARD&access=WRITE");
		assertEquals(map("deleteBoard", "HIGH", "insertBoard", "HIGH"), starts(result));
		assertEquals(map("/board/admin.do", "LOW", "/board/save.do", "HIGH"), rows(result, "endpoints"));
		assertEquals(map("board/edit.jsp", "HIGH"), rows(result, "screens"));
	}

	@Test
	void column() throws Exception {
		// TITLE 을 쓰는 SQL 은 selectBoardList · insertBoard. countBoard(COUNT(*))는 컬럼을 읽지 못한 SQL 이라 LOW 로 함께 나온다
		JsonNode result = impact("column=TB_BOARD.TITLE");
		assertEquals(map("countBoard", "LOW", "insertBoard", "HIGH", "selectBoardList", "HIGH"), starts(result));
		assertEquals(map("/board/list.do", "HIGH", "/board/save.do", "HIGH"), rows(result, "endpoints"));
		assertEquals("Board.countBoard", result.path("touches").get(0).path("unsure").get(0).asText());
	}

	@Test
	void statementWithChain() throws Exception {
		JsonNode result = impact("statement=Board.deleteBoard");
		assertEquals(map("/board/admin.do", "LOW"), rows(result, "endpoints"));
		assertEquals(0, result.path("screens").size());
		// 근거 길: BoardAdminAction#delete → BoardDAO#deleteBoard(Board.deleteBoard 실행)
		JsonNode chain = result.path("endpoints").get(0).path("chain");
		assertEquals(2, chain.size());
		assertTrue(name(result, chain.get(0)).contains("BoardAdminAction#delete("));
		assertEquals("CALLS", chain.get(0).path("relation").asText());
		assertTrue(name(result, chain.get(1)).contains("BoardDAO#deleteBoard("));
		assertTrue(chain.get(1).path("via").asText().startsWith("Board.deleteBoard"));
	}

	@Test
	void type() throws Exception {
		JsonNode result = impact("type=com.sample.board.dao.BoardDAO");
		assertEquals(map("/board/admin.do", "LOW", "/board/list.do", "HIGH", "/board/save.do", "HIGH"), rows(result, "endpoints"));
		// 따라 내려가면 TB_USER 도 읽는다(selectBoardList 의 JOIN)
		assertEquals(2, result.path("touches").size());
	}

	/* ---------------- 도우미 ---------------- */

	private static JsonNode impact(String query) throws Exception {
		return get("/api/revisions/" + revisionId + "/impact?" + query);
	}

	private static JsonNode get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(api + path.replace(" ", URLEncoder.encode(" ", StandardCharsets.UTF_8)))).GET().build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		assertEquals(200, response.statusCode(), path + " → " + response.body());
		return JSON.readTree(response.body());
	}

	/** 출발점(거리 0) 메소드 이름 → 신뢰도 */
	private static Map<String, String> starts(JsonNode result) {
		Map<String, String> found = new TreeMap<String, String>();
		JsonNode methods = result.path("methods");
		for (int i = 0; i < methods.size(); i++) {
			JsonNode method = methods.get(i);
			if (method.path("depth").asInt() == 0) {
				String full = method.path("method").asText();
				String name = full.substring(full.indexOf('#') + 1, full.indexOf('('));
				found.put(name, method.path("confidence").asText());
			}
		}
		return found;
	}

	/** 진입점 · 화면 경로 → 신뢰도 */
	private static Map<String, String> rows(JsonNode result, String key) {
		Map<String, String> found = new TreeMap<String, String>();
		Iterator<JsonNode> it = result.path(key).iterator();
		while (it.hasNext()) {
			JsonNode row = it.next();
			found.put(row.path("path").asText(), row.path("confidence").asText());
		}
		return found;
	}

	private static String name(JsonNode result, JsonNode step) {
		return result.path("nodes").path(step.path("id").asText()).path("name").asText();
	}

	private static Map<String, String> map(String... pairs) {
		Map<String, String> map = new TreeMap<String, String>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put(pairs[i], pairs[i + 1]);
		}
		return map;
	}
}

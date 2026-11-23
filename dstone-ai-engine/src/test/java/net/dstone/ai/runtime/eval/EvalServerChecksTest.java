package net.dstone.ai.runtime.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * <pre>
 * http·dbQuery 채점과 JSON 경로(JsonPick)를 Spring 없이 확인합니다.
 * 격리 시험 서버 대신 이 테스트가 띄운 작은 HTTP 서버와 가짜 DB(Probe)를 씁니다.
 * </pre>
 */
public class EvalServerChecksTest {

	@TempDir
	Path dir;

	private HttpServer server;

	/** 서버가 받은 요청: "메소드 경로 본문" */
	private final List<String> received = new ArrayList<String>();

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				InputStream in = exchange.getRequestBody();
				String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				String path = exchange.getRequestURI().toString();
				EvalServerChecksTest.this.received.add(exchange.getRequestMethod() + " " + path + " " + body);
				String answer;
				int status = 200;
				if ("POST".equals(exchange.getRequestMethod())) {
					answer = body.contains("\"title\":\"\"") ? "{\"error\":\"title\"}" : "{\"id\":\"m-7\",\"title\":\"회의\"}";
					status = body.contains("\"title\":\"\"") ? 400 : 200;
				} else if (path.startsWith("/api/memos/m-7")) {
					answer = "{\"id\":\"m-7\"}";
				} else {
					answer = "{\"total\":12,\"items\":[{\"name\":\"강도윤\",\"no\":1},{\"name\":\"김민수\",\"no\":2.0}]}";
				}
				byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(status, bytes.length);
				OutputStream out = exchange.getResponseBody();
				out.write(bytes);
				out.close();
			}
		});
		this.server.start();
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	@Test
	void JSON_경로로_값을_꺼낸다() throws Exception {
		Object json = new ObjectMapper().readValue("{\"total\":12,\"items\":[{\"name\":\"a\",\"n\":2.0},{\"name\":\"b\"}],\"list\":[]}", Object.class);
		assertThat(JsonPick.pick(json, "total")).isEqualTo("12");
		assertThat(JsonPick.pick(json, "items.0.name")).isEqualTo("a");
		assertThat(JsonPick.pick(json, "items[1].name")).isEqualTo("b");
		assertThat(JsonPick.pick(json, "items[*].name")).isEqualTo("a,b");
		assertThat(JsonPick.pick(json, "items.$size")).isEqualTo("2");
		assertThat(JsonPick.pick(json, "list.$size")).isEqualTo("0");
		assertThat(JsonPick.pick(json, "items.0.n")).isEqualTo("2");
		assertThat(JsonPick.pick(json, "items.5.name")).isNull();
		assertThat(JsonPick.pick(json, "nothing.deep")).isNull();
		Object top = new ObjectMapper().readValue("[{\"title\":\"x\"}]", Object.class);
		assertThat(JsonPick.pick(top, "[0].title")).isEqualTo("x");
		assertThat(JsonPick.pick(top, "$size")).isEqualTo("1");
	}

	@Test
	@SuppressWarnings("unchecked")
	void http와_dbQuery로_채점하고_앞_응답의_값을_다음_요청에_쓴다() throws Exception {
		List<EvalCase.Check> checks = new ArrayList<EvalCase.Check>();
		checks.add(new EvalCase.Check("등록", "http", map("method", "POST", "path", "/api/memos", "body", map("title", "회의", "body", "${param.memo}"),
			"status", 200, "save", map("memoId", "id"))));
		checks.add(new EvalCase.Check("저장한 id로 조회", "http", map("path", "/api/memos/${saved.memoId}", "json", map("id", "m-7"))));
		checks.add(new EvalCase.Check("빈 제목 거절", "http", map("method", "POST", "path", "/api/memos", "body", map("title", ""), "status", 400)));
		checks.add(new EvalCase.Check("목록 순서", "http", map("path", "/api/employees?page=1", "json", map("total", 12, "items[*].name", "강도윤,김민수", "items.1.no", 2))));
		checks.add(new EvalCase.Check("틀린 기대", "http", map("path", "/api/employees", "json", map("total", 13), "contains", List.of("강도윤", "없는글"))));
		checks.add(new EvalCase.Check("DB 값", "dbQuery", map("sql", "SELECT COUNT(*) AS n FROM memo", "equals", "1")));
		checks.add(new EvalCase.Check("DB 행 수", "dbQuery", map("sql", "SELECT * FROM memo", "rows", 2)));
		Map<String, String> server = new LinkedHashMap<String, String>();
		server.put("projectId", "p");
		server.put("sourceRoot", "/src");
		server.put("overlayDir", "${requestDir}/${state.now}/dev");
		EvalCase evalCase = new EvalCase("c", null, null, "wf", new LinkedHashMap<String, String>(), null, server, null, null,
			Map.of("memo", "내용"), null, null, "${requestDir}", checks, "c.eval.yml");
		EvalWorkspace workspace = new EvalWorkspace(evalCase, null, this.dir.resolve("req"), Map.of("memo", "내용"));
		workspace.setState(Map.of("now", (Object) "20261123-1200"));
		final String base = "http://127.0.0.1:" + this.server.getAddress().getPort();
		final List<String> queries = new ArrayList<String>();
		EvalWorkspace.Probe probe = new EvalWorkspace.Probe() {
			@Override
			public String baseUrl() {
				return base;
			}

			@Override
			public List<Map<String, Object>> rows(String sql, int maxRows) {
				queries.add(sql);
				List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
				Map<String, Object> row = new LinkedHashMap<String, Object>();
				row.put("n", Long.valueOf(1));
				rows.add(row);
				return rows;
			}
		};

		assertThat(workspace.serverArgs().get("overlayDir")).isEqualTo(this.dir.resolve("req").toString().replace('\\', '/') + "/20261123-1200/dev");
		List<Map<String, Object>> results = workspace.check(null, Map.of(), probe);

		assertThat(okOf(results)).containsExactly(true, true, true, true, false, true, false);
		assertThat(String.valueOf(results.get(4).get("detail"))).contains("total=12(기대 13)").contains("없는글").doesNotContain("강도윤' 없음");
		assertThat(String.valueOf(results.get(6).get("detail"))).isEqualTo("1행");
		assertThat(this.received.get(0)).contains("POST /api/memos").contains("\"body\":\"내용\"");
		assertThat(this.received.get(1)).startsWith("GET /api/memos/m-7");
		assertThat(queries).containsExactly("SELECT COUNT(*) AS n FROM memo", "SELECT * FROM memo");

		// 서버가 없으면 http·dbQuery 채점은 실패로 남는다
		List<Map<String, Object>> noServer = new EvalWorkspace(evalCase, null, this.dir.resolve("req2"), Map.of("memo", "x")).check(null, Map.of(), null);
		assertThat(okOf(noServer)).containsOnly(false);
		assertThat(String.valueOf(noServer.get(0).get("detail"))).contains("격리 시험 서버");
	}

	private static List<Boolean> okOf(List<Map<String, Object>> results) {
		List<Boolean> ok = new ArrayList<Boolean>();
		for (Map<String, Object> result : results) {
			ok.add((Boolean) result.get("ok"));
		}
		return ok;
	}

	private static Map<String, Object> map(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put(String.valueOf(pairs[i]), pairs[i + 1]);
		}
		return map;
	}

}

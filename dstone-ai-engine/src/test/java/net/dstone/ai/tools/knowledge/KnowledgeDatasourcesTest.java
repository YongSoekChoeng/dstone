package net.dstone.ai.tools.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.dstone.ai.common.exception.KnowledgeCallException;
import net.dstone.ai.common.knowledge.KnowledgeClient;

/**
 * <pre>
 * knowledgeDatasources가 dstone-knowledge의 설정 / 빈 목록에서 데이터소스만 골라 묶는지 서버 없이 확인합니다.
 * 가짜 KnowledgeClient는 dstone-knowledge처럼 key(포함) / value(대소문자 무시 포함) 조건으로 거릅니다.
 * </pre>
 */
class KnowledgeDatasourcesTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final KnowledgeTool tool = new KnowledgeTool();

	private final ArrayNode configs = MAPPER.createArrayNode();

	private final ArrayNode resources = MAPPER.createArrayNode();

	/** dstone-knowledge 대신 응답하는 가짜 */
	private class FakeClient extends KnowledgeClient {
		@Override
		public JsonNode get(String path, Map<String, String> params) throws KnowledgeCallException {
			if (path.endsWith("/revisions")) {
				ArrayNode revisions = MAPPER.createArrayNode();
				revisions.addObject().put("revisionId", 7).put("status", "READY");
				return revisions;
			}
			String key = params == null ? null : params.get("key");
			String value = params == null ? null : params.get("value");
			ObjectNode result = MAPPER.createObjectNode();
			if (path.endsWith("/configs")) {
				ArrayNode list = result.putArray("configs");
				for (int i = 0; i < configs.size(); i++) {
					JsonNode row = configs.get(i);
					if (matches(row.path("key").asText(), key, false) && matches(row.path("value").asText(), value, true)) {
						list.add(row);
					}
				}
			} else if (path.endsWith("/resources")) {
				ArrayNode list = result.putArray("resources");
				for (int i = 0; i < resources.size(); i++) {
					JsonNode row = resources.get(i);
					if (matches(row.path("value").asText(), value, true)) {
						list.add(row);
					}
				}
			}
			return result;
		}
	}

	private static boolean matches(String text, String condition, boolean ignoreCase) {
		if (condition == null) {
			return true;
		}
		return ignoreCase ? text.toLowerCase(Locale.ROOT).contains(condition.toLowerCase(Locale.ROOT)) : text.contains(condition);
	}

	private void config(String file, String key, String value, String placeholder) {
		ObjectNode row = this.configs.addObject();
		row.put("key", key).put("value", value).put("placeholder", placeholder).put("file", file);
		row.putNull("lineStart");
	}

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(this.tool, "knowledgeClient", new FakeClient());
		ReflectionTestUtils.setField(this.tool, "environment", new MockEnvironment());
	}

	@Test
	void 설정_파일의_데이터소스를_묶고_변수를_푼다() {
		config("src/main/resources/application.yml", "spring.datasource.hikari.common.jdbc-url", "jdbc:mysql://${DB_HOST}:3306/sampleDB", "DB_HOST");
		config("src/main/resources/application.yml", "spring.datasource.hikari.common.username", "dstone", null);
		config("src/main/resources/application.yml", "spring.datasource.hikari.common.password", "****", null);
		config("src/main/resources/application.yml", "spring.datasource.hikari.common.maximum-pool-size", "10", null);
		config("conf/env.properties", "DB_HOST", "localhost", null);
		// 키에는 datasource / jdbc가 없고 값으로만 찾히는 것
		config("src/main/resources/db.properties", "db.url", "jdbc:oracle:thin:@ora:1521/ORCL", null);
		config("src/main/resources/db.properties", "db.username", "scott", null);
		config("src/main/resources/db.properties", "db.timeout", "30", null);
		// 상관없는 설정
		config("src/main/resources/application.yml", "server.port", "7081", null);

		String result = this.tool.knowledgeDatasources("p1");

		assertThat(result).contains("[설정 파일]");
		assertThat(result).contains("spring.datasource.hikari.common.jdbc-url = jdbc:mysql://${DB_HOST}:3306/sampleDB  → ${DB_HOST} = localhost (conf/env.properties)");
		assertThat(result).contains("spring.datasource.hikari.common.username = dstone");
		assertThat(result).contains("spring.datasource.hikari.common.password = ****");
		assertThat(result).doesNotContain("maximum-pool-size").doesNotContain("server.port").doesNotContain("db.timeout");
		assertThat(result).contains("- src/main/resources/db.properties  db").contains("db.url = jdbc:oracle:thin:@ora:1521/ORCL").contains("db.username = scott");
	}

	@Test
	void Spring_XML의_DataSource_빈을_보여_준다() throws Exception {
		ObjectNode bean = this.resources.addObject();
		bean.put("resourceType", "SPRING_BEAN").put("name", "dataSource").put("value", "org.apache.commons.dbcp.BasicDataSource").put("file", "WEB-INF/spring/context-datasource.xml");
		bean.putNull("lineStart");
		bean.put("properties", "{\"properties\":{\"driverClassName\":\"oracle.jdbc.OracleDriver\",\"url\":\"${jdbc.url}\",\"username\":\"scott\",\"password\":\"****\",\"maxActive\":\"20\"}}");
		ObjectNode other = this.resources.addObject();
		other.put("resourceType", "SPRING_BEAN").put("name", "txManager").put("value", "org.springframework.jdbc.datasource.DataSourceTransactionManager").put("file", "x.xml");
		other.put("properties", "{\"properties\":{\"dataSource\":\"ref:dataSource\"}}");
		config("WEB-INF/config/globals.properties", "jdbc.url", "jdbc:oracle:thin:@db:1521/APP", null);

		String result = this.tool.knowledgeDatasources("p1");

		assertThat(result).contains("[Spring XML 빈]");
		assertThat(result).contains("- dataSource = org.apache.commons.dbcp.BasicDataSource  (WEB-INF/spring/context-datasource.xml)");
		assertThat(result).contains("url = ${jdbc.url}  → ${jdbc.url} = jdbc:oracle:thin:@db:1521/APP (WEB-INF/config/globals.properties)");
		assertThat(result).contains("driverClassName = oracle.jdbc.OracleDriver").contains("password = ****");
		assertThat(result).doesNotContain("maxActive");
		// 트랜잭션 매니저는 클래스 이름에 DataSource가 들어 있어도 데이터소스가 아니다
		assertThat(result).doesNotContain("txManager");
	}

	@Test
	void 찾지_못하면_까닭을_알린다() {
		config("src/main/resources/application.yml", "server.port", "7081", null);
		assertThat(this.tool.knowledgeDatasources("p1")).startsWith("데이터소스 설정을 찾지 못했습니다");
	}

}

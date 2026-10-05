package net.dstone.knowledge.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 설정 파일, 빌드 파일, Spring XML을 읽는 규칙을 확인합니다.
 * </pre>
 */
public class ResourceReadersTest {

	private final ResourceReaders readers = new ResourceReaders();

	@Test
	public void properties의_키와_값을_읽고_비밀_값은_가린다() throws Exception {
		String text = "# DB\n"
				+ "db.url=jdbc:mysql://${DB_HOST}:3306/app\n"
				+ "db.password = s3cret!\n"
				+ "api-key: abcd-1234\n"
				+ "page.size=20\n";
		List<Map<String, Object>> configs = readers.readProperties(text);

		assertEquals(4, configs.size());
		Map<String, Object> url = find(configs, "keyPath", "db.url");
		assertEquals("jdbc:mysql://${DB_HOST}:3306/app", url.get("value"));
		assertEquals("DB_HOST", url.get("placeholder"));
		assertEquals(Integer.valueOf(2), url.get("lineStart"));

		// 분석 대상의 비밀 값이 분석 결과(검색되는 데이터)에 들어가면 안 된다.
		Map<String, Object> password = find(configs, "keyPath", "db.password");
		assertEquals("****", password.get("value"));
		assertEquals("MASKED", password.get("valueType"));
		assertEquals("****", find(configs, "keyPath", "api-key").get("value"));
		assertEquals("20", find(configs, "keyPath", "page.size").get("value"));
	}

	@Test
	public void YAML의_중첩된_키는_점으로_잇고_목록은_번호를_붙인다() {
		String text = "spring:\n"
				+ "  datasource:\n"
				+ "    url: jdbc:postgresql://localhost/app\n"
				+ "    password: ENC(abc)\n"
				+ "server:\n"
				+ "  port: 8080\n"
				+ "hosts:\n"
				+ "  - a.example.com\n"
				+ "  - b.example.com\n"
				+ "---\n"
				+ "feature:\n"
				+ "  enabled: true\n";
		List<Map<String, Object>> configs = readers.readYaml(text);

		assertEquals("jdbc:postgresql://localhost/app", find(configs, "keyPath", "spring.datasource.url").get("value"));
		assertEquals("MASKED", find(configs, "keyPath", "spring.datasource.password").get("valueType"));
		assertEquals("NUMBER", find(configs, "keyPath", "server.port").get("valueType"));
		assertEquals("b.example.com", find(configs, "keyPath", "hosts[1]").get("value"));
		// --- 로 나뉜 두 번째 문서도 읽는다.
		assertEquals("BOOLEAN", find(configs, "keyPath", "feature.enabled").get("valueType"));
	}

	@Test
	public void pom_xml의_의존성을_읽는다() throws Exception {
		String text = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
				+ "  <dependencyManagement><dependencies>\n"
				+ "    <dependency><groupId>org.x</groupId><artifactId>x-bom</artifactId><version>1.0</version></dependency>\n"
				+ "  </dependencies></dependencyManagement>\n"
				+ "  <dependencies>\n"
				+ "    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis</artifactId><version>3.2.8</version><scope>compile</scope></dependency>\n"
				+ "    <dependency><groupId>junit</groupId><artifactId>junit</artifactId></dependency>\n"
				+ "  </dependencies>\n"
				+ "</project>\n";
		List<Map<String, Object>> resources = readers.readPom(text);

		assertEquals(3, resources.size());
		Map<String, Object> mybatis = find(resources, "name", "org.mybatis:mybatis");
		assertEquals("DEPENDENCY", mybatis.get("resourceType"));
		assertEquals("3.2.8", mybatis.get("value"));
		assertTrue(((String) mybatis.get("propertiesJson")).contains("\"managed\":false"));
		assertTrue(((String) find(resources, "name", "org.x:x-bom").get("propertiesJson")).contains("\"managed\":true"));
		// 버전을 적지 않은 의존성은 버전이 비어 있다.
		assertNull(find(resources, "name", "junit:junit").get("value"));
	}

	@Test
	public void build_gradle의_흔한_모양의_의존성을_읽는다() {
		String text = "dependencies {\n"
				+ "    implementation 'org.springframework:spring-core:5.3.9'\n"
				+ "    testImplementation(\"junit:junit:4.13\")\n"
				+ "    compileOnly 'org.projectlombok:lombok'\n"
				+ "}\n";
		List<Map<String, Object>> resources = readers.readGradle(text);

		assertEquals(3, resources.size());
		assertEquals("5.3.9", find(resources, "name", "org.springframework:spring-core").get("value"));
		assertEquals("4.13", find(resources, "name", "junit:junit").get("value"));
		assertNull(find(resources, "name", "org.projectlombok:lombok").get("value"));
	}

	@Test
	public void Spring_XML의_빈과_컴포넌트_스캔_범위를_읽는다() throws Exception {
		String text = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<beans xmlns=\"http://www.springframework.org/schema/beans\" xmlns:context=\"http://www.springframework.org/schema/context\">\n"
				+ "  <context:component-scan base-package=\"com.x.order\"/>\n"
				+ "  <import resource=\"classpath:spring/context-datasource.xml\"/>\n"
				+ "  <bean id=\"dataSource\" class=\"org.apache.commons.dbcp.BasicDataSource\">\n"
				+ "    <property name=\"url\" value=\"jdbc:mysql://localhost/app\"/>\n"
				+ "    <property name=\"password\" value=\"s3cret!\"/>\n"
				+ "  </bean>\n"
				+ "  <bean id=\"sqlSession\" class=\"org.mybatis.spring.SqlSessionFactoryBean\">\n"
				+ "    <property name=\"dataSource\" ref=\"dataSource\"/>\n"
				+ "  </bean>\n"
				+ "</beans>\n";
		List<Map<String, Object>> resources = readers.readSpringXml(text);

		assertEquals("com.x.order", find(resources, "resourceType", "COMPONENT_SCAN").get("value"));
		assertEquals("classpath:spring/context-datasource.xml", find(resources, "resourceType", "SPRING_IMPORT").get("value"));

		Map<String, Object> dataSource = find(resources, "name", "dataSource");
		assertEquals("SPRING_BEAN", dataSource.get("resourceType"));
		assertEquals("org.apache.commons.dbcp.BasicDataSource", dataSource.get("value"));
		String json = (String) dataSource.get("propertiesJson");
		assertTrue(json.contains("jdbc:mysql://localhost/app"));
		// 빈의 속성에 직접 적힌 비밀번호도 가린다.
		assertTrue(!json.contains("s3cret!"));
		// 다른 빈을 가리키는 속성은 ref: 를 붙여 적는다.
		assertTrue(((String) find(resources, "name", "sqlSession").get("propertiesJson")).contains("ref:dataSource"));
	}

	private Map<String, Object> find(List<Map<String, Object>> rows, String key, String value) {
		for (int i = 0; i < rows.size(); i++) {
			if (value.equals(rows.get(i).get(key))) {
				return rows.get(i);
			}
		}
		assertNotNull(null, key + "=" + value + " 인 항목이 없다.");
		return null;
	}

}

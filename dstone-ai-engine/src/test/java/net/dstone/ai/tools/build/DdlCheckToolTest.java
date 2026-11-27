package net.dstone.ai.tools.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import net.dstone.ai.common.project.ProjectStore;

/**
 * <pre>
 * DDL 반영 확인 Tool을 메모리 DB(H2)로 서버 없이 확인합니다.
 * 테이블·컬럼뿐 아니라 DDL 문서의 INSERT(초기 데이터)가 개발 DB에 있는지도 보는지 확인합니다.
 * </pre>
 */
class DdlCheckToolTest {

	private static final String URL = "jdbc:h2:mem:ddlchecktest;DB_CLOSE_DELAY=-1";

	private final DdlCheckTool tool = new DdlCheckTool();

	/** 테스트 동안 메모리 DB가 사라지지 않게 잡아 두는 연결 */
	private Connection keeper;

	@TempDir
	Path dir;

	@BeforeEach
	void setUp() throws Exception {
		this.keeper = DriverManager.getConnection(URL, "sa", "");
		this.sql("DROP TABLE IF EXISTS TB_ROOM");
		this.sql("DROP TABLE IF EXISTS TB_CODE");
		this.sql("DROP TABLE IF EXISTS TB_RESOURCE");
		this.sql("DROP TABLE IF EXISTS TB_LOG");
		this.sql("CREATE TABLE TB_ROOM (ROOM_NO VARCHAR(10) PRIMARY KEY, ROOM_NM VARCHAR(50), SEAT INT)");
		this.sql("CREATE TABLE TB_CODE (CODE_ID INT PRIMARY KEY, CODE_NM VARCHAR(50))");
		this.sql("CREATE TABLE TB_RESOURCE (MB_ID VARCHAR(10), RESOURCE_ID VARCHAR(10), RESOURCE_NAME VARCHAR(50), IN_DTM TIMESTAMP, PRIMARY KEY (MB_ID, RESOURCE_ID))");
		this.sql("CREATE TABLE TB_LOG (LOG_TYPE VARCHAR(10), LOG_MSG VARCHAR(50))");

		Map<String, String> db = new LinkedHashMap<String, String>();
		db.put("url", URL);
		db.put("username", "sa");
		db.put("password", "");
		Map<String, Map<String, String>> connections = new LinkedHashMap<String, Map<String, String>>();
		connections.put("testApp.devdb", db);
		ProjectStore projectStore = Mockito.mock(ProjectStore.class);
		Mockito.when(projectStore.connections()).thenReturn(connections);
		ReflectionTestUtils.setField(this.tool, "projectStore", projectStore);
	}

	@AfterEach
	void tearDown() throws Exception {
		this.keeper.close();
	}

	private void sql(String sql) throws Exception {
		try (Statement st = this.keeper.createStatement()) {
			st.execute(sql);
		}
	}

	private String ddl(String body) throws Exception {
		Path file = this.dir.resolve("03-3.ddl.md");
		Files.writeString(file, "# DDL\n\n```sql\n" + body + "\n```\n", StandardCharsets.UTF_8);
		return file.toString();
	}

	@Test
	@SuppressWarnings("unchecked")
	void 테이블은_있지만_초기_데이터가_없으면_missing이다() throws Exception {
		String file = this.ddl("CREATE TABLE tb_room (room_no VARCHAR(10) PRIMARY KEY, room_nm VARCHAR(50), seat INT);\n"
			+ "INSERT INTO tb_room (room_no, room_nm, seat) VALUES ('RM_001', '1회의실(본관, 2층)', 6), ('RM_002', 'It''s 2', 8);\n"
			+ "INSERT INTO tb_code (code_id, code_nm) VALUES (10, '대기');");

		Map<String, Object> result = this.tool.checkDdlApplied("testApp", file);

		assertThat(result.get("route")).isEqualTo("missing");
		assertThat((List<String>) result.get("missing")).isEmpty();
		assertThat((List<String>) result.get("missingRows")).containsExactly("tb_room(room_no=RM_001)", "tb_room(room_no=RM_002)", "tb_code(code_id=10)");
		assertThat(String.valueOf(result.get("message"))).contains("초기 데이터").contains("RM_001");
	}

	@Test
	void 초기_데이터까지_있으면_ok다() throws Exception {
		this.sql("INSERT INTO TB_ROOM VALUES ('RM_001', '1회의실(본관, 2층)', 6)");
		this.sql("INSERT INTO TB_ROOM VALUES ('RM_002', 'It''s 2', 8)");
		this.sql("INSERT INTO TB_CODE VALUES (10, '대기')");
		String file = this.ddl("CREATE TABLE tb_room (room_no VARCHAR(10) PRIMARY KEY, room_nm VARCHAR(50), seat INT);\n"
			+ "INSERT INTO tb_room (room_no, room_nm, seat) VALUES ('RM_001', '1회의실(본관, 2층)', 6), ('RM_002', 'It''s 2', 8);\n"
			+ "INSERT INTO tb_code (code_id, code_nm) VALUES (10, '대기');");

		Map<String, Object> result = this.tool.checkDdlApplied("testApp", file);

		assertThat(result.get("route")).isEqualTo("ok");
		assertThat(result.get("checkedRows")).isEqualTo(Integer.valueOf(3));
	}

	@Test
	@SuppressWarnings("unchecked")
	void 컬럼_목록이_없는_INSERT는_테이블에_행이_있는지만_본다() throws Exception {
		String file = this.ddl("INSERT INTO tb_code VALUES (10, '대기');");

		Map<String, Object> result = this.tool.checkDdlApplied("testApp", file);
		assertThat(result.get("route")).isEqualTo("missing");
		assertThat((List<String>) result.get("missingRows")).containsExactly("tb_code(행 없음)");

		this.sql("INSERT INTO TB_CODE VALUES (99, '다른 값')");
		assertThat(this.tool.checkDdlApplied("testApp", file).get("route")).isEqualTo("ok");
	}

	@Test
	@SuppressWarnings("unchecked")
	void 첫_컬럼이_키가_아니면_기본키_전체로_행을_찾는다() throws Exception {
		// 같은 mb_id의 다른 메뉴는 이미 있다. 첫 컬럼(mb_id)만 보면 있다고 잘못 판단한다
		this.sql("INSERT INTO TB_RESOURCE VALUES ('PRD', 'SY_000', '시스템', NOW())");
		String file = this.ddl("INSERT INTO tbsy_x_dummy_never (a) VALUES (1);\n"
			+ "INSERT INTO tb_resource (mb_id, resource_id, resource_name, in_dtm) VALUES ('PRD', 'SP_000', '업무지원', NOW());");

		Map<String, Object> result = this.tool.checkDdlApplied("testApp", file);
		assertThat((List<String>) result.get("missingRows")).contains("tb_resource(mb_id=PRD, resource_id=SP_000)");

		this.sql("INSERT INTO TB_RESOURCE VALUES ('PRD', 'SP_000', '업무지원(이름은 바꿈)', NOW())");
		assertThat((List<String>) this.tool.checkDdlApplied("testApp", file).get("missingRows")).doesNotContain("tb_resource(mb_id=PRD, resource_id=SP_000)");
	}

	@Test
	@SuppressWarnings("unchecked")
	void 기본키가_없으면_리터럴_값이_있는_컬럼_전부로_찾는다() throws Exception {
		this.sql("INSERT INTO TB_LOG VALUES ('INFO', '다른 글')");
		String file = this.ddl("INSERT INTO tb_log (log_type, log_msg) VALUES ('INFO', '시작');");

		assertThat((List<String>) this.tool.checkDdlApplied("testApp", file).get("missingRows")).containsExactly("tb_log(log_type=INFO, log_msg=시작)");
		this.sql("INSERT INTO TB_LOG VALUES ('INFO', '시작')");
		assertThat(this.tool.checkDdlApplied("testApp", file).get("route")).isEqualTo("ok");
	}

	@Test
	@SuppressWarnings("unchecked")
	void 테이블이_없으면_그_테이블의_행은_따로_알리지_않는다() throws Exception {
		String file = this.ddl("CREATE TABLE tb_new (id INT);\nINSERT INTO tb_new (id) VALUES (1);");

		Map<String, Object> result = this.tool.checkDdlApplied("testApp", file);

		assertThat(result.get("route")).isEqualTo("missing");
		assertThat((List<String>) result.get("missing")).containsExactly("tb_new");
		assertThat((List<String>) result.get("missingRows")).isEmpty();
	}

}

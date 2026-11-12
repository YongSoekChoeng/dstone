package net.dstone.ai.tools.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * <pre>
 * DB 조회 Tool을 메모리 DB(H2)로 서버 없이 확인합니다.
 * 조회만 되는지, 쓰기가 막히는지, 결과가 줄어들고 가려지는지를 봅니다.
 * </pre>
 */
class DbQueryToolTest {

	private static final String URL = "jdbc:h2:mem:dbtooltest;DB_CLOSE_DELAY=-1";

	private final DbQueryTool tool = new DbQueryTool();

	/** 테스트 동안 메모리 DB가 사라지지 않게 잡아 두는 연결 */
	private Connection keeper;

	@BeforeEach
	void setUp() throws Exception {
		this.keeper = DriverManager.getConnection(URL, "sa", "");
		Statement st = this.keeper.createStatement();
		st.execute("DROP TABLE IF EXISTS TB_USER");
		st.execute("CREATE TABLE TB_USER (USER_ID INT PRIMARY KEY, USER_NM VARCHAR(50) NOT NULL, PASSWORD VARCHAR(100), MEMO CLOB)");
		st.execute("COMMENT ON TABLE TB_USER IS '사용자'");
		st.execute("COMMENT ON COLUMN TB_USER.USER_NM IS '사용자명'");
		for (int i = 1; i <= 5; i++) {
			st.execute("INSERT INTO TB_USER VALUES (" + i + ", 'user" + i + "', 'secret" + i + "', 'line1\nline2')");
		}
		st.close();

		MockEnvironment env = new MockEnvironment();
		env.setProperty("dstone.ai.tool.db.max-rows", "3");
		env.setProperty("dstone.ai.tool.db.connections.test-db.url", URL);
		env.setProperty("dstone.ai.tool.db.connections.test-db.username", "sa");
		env.setProperty("dstone.ai.tool.db.connections.test-db.password", "");
		env.setProperty("dstone.ai.tool.db.connections.test-db.description", "테스트 DB");
		ReflectionTestUtils.setField(this.tool, "environment", env);
	}

	@AfterEach
	void tearDown() throws Exception {
		this.tool.destroy();
		this.keeper.close();
	}

	@Test
	void 등록된_별칭을_보여_준다() {
		assertThat(this.tool.dbListConnections()).contains("test-db").contains(URL).contains("테스트 DB");
	}

	@Test
	void 등록되지_않은_별칭은_실패한다() {
		assertThat(this.tool.dbQuery("none", "SELECT 1", null)).startsWith("실패: 등록되지 않은 DB 별칭").contains("test-db");
	}

	@Test
	void 테이블_목록과_구조를_읽는다() {
		assertThat(this.tool.dbListTables("test-db", "user", null)).contains("TB_USER").contains("사용자");
		String described = this.tool.dbDescribeTable("test-db", "tb_user", null);
		assertThat(described).contains("PK: USER_ID").contains("USER_NM").contains("NOT NULL").contains("사용자명");
	}

	@Test
	void 조회는_상한까지만_주고_비밀번호_컬럼은_가린다() {
		String result = this.tool.dbQuery("test-db", "SELECT USER_ID, USER_NM, PASSWORD, MEMO FROM TB_USER ORDER BY USER_ID;", null);
		assertThat(result).contains("열: USER_ID | USER_NM | PASSWORD | MEMO");
		assertThat(result).contains("1) 1 | user1 | **** | line1 line2");
		assertThat(result).doesNotContain("secret1").doesNotContain("4) ");
		assertThat(result).contains("3행까지만 보였습니다");
	}

	@Test
	void 쓰기_문장은_실행하지_않는다() throws Exception {
		assertThat(this.tool.dbQuery("test-db", "DELETE FROM TB_USER", null)).startsWith("실패: 조회(SELECT) 문장만");
		assertThat(this.tool.dbQuery("test-db", "SELECT 1; DELETE FROM TB_USER", null)).startsWith("실패: 한 번에 SQL 문장 하나만");
		assertThat(this.tool.dbQuery("test-db", "SELECT * FROM TB_USER FOR UPDATE", null)).startsWith("실패: SELECT ... FOR UPDATE");

		Statement st = this.keeper.createStatement();
		java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM TB_USER");
		rs.next();
		assertThat(rs.getInt(1)).isEqualTo(5);
		rs.close();
		st.close();
	}

	@Test
	void SQL_오류는_실패_문구로_돌려준다() {
		assertThat(this.tool.dbQuery("test-db", "SELECT NO_SUCH_COLUMN FROM TB_USER", null)).startsWith("실패: 쿼리 실행 오류");
	}

}

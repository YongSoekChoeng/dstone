package net.dstone.ai.tools.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 조회 문장 하나만 통과시키는지 확인합니다.
 * </pre>
 */
class DbSqlGuardTest {

	@Test
	void 조회_문장은_통과한다() {
		assertThat(DbSqlGuard.check("SELECT * FROM TB_USER WHERE USER_ID = 1")).isNull();
		assertThat(DbSqlGuard.check("WITH A AS (SELECT 1 AS X) SELECT X FROM A")).isNull();
		assertThat(DbSqlGuard.check("SELECT A FROM T1 UNION ALL SELECT B FROM T2")).isNull();
		assertThat(DbSqlGuard.check("SELECT * FROM (SELECT * FROM T1) X")).isNull();
	}

	@Test
	void 쓰기와_DDL은_막는다() {
		assertThat(DbSqlGuard.check("UPDATE TB_USER SET USER_NM = 'x'")).startsWith("조회(SELECT) 문장만");
		assertThat(DbSqlGuard.check("INSERT INTO T1 SELECT * FROM T2")).startsWith("조회(SELECT) 문장만");
		assertThat(DbSqlGuard.check("DROP TABLE TB_USER")).startsWith("조회(SELECT) 문장만");
		assertThat(DbSqlGuard.check("TRUNCATE TABLE TB_USER")).startsWith("조회(SELECT) 문장만");
	}

	@Test
	void 여러_문장과_빈_문장은_막는다() {
		assertThat(DbSqlGuard.check("SELECT 1; SELECT 2")).startsWith("한 번에 SQL 문장 하나만");
		assertThat(DbSqlGuard.check("  ")).isEqualTo("SQL이 비어 있습니다.");
	}

	@Test
	void INTO와_잠금은_막는다() {
		assertThat(DbSqlGuard.check("SELECT * INTO NEW_T FROM T1")).startsWith("SELECT ... INTO");
		assertThat(DbSqlGuard.check("SELECT * FROM T1 INTO OUTFILE '/tmp/x'")).startsWith("INTO OUTFILE");
		assertThat(DbSqlGuard.check("SELECT * FROM T1 FOR UPDATE")).startsWith("SELECT ... FOR UPDATE");
		assertThat(DbSqlGuard.check("SELECT A FROM T1 UNION SELECT A FROM T2 FOR UPDATE")).isNotNull();
	}

	@Test
	void 끝의_세미콜론은_뗀다() {
		assertThat(DbQueryTool.trimSql("SELECT 1 ; ")).isEqualTo("SELECT 1");
	}

}

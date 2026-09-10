package net.dstone.ai.tools.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.common.core.BaseObject;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * SqlSyntaxTools(JSQLParser, 순수 그래머 검사)보다 더 정확한 검증이 필요해서 만든 Tool이다.
 * JSQLParser는 PostgreSQL 전용 그래머가 아니라 여러 방언을 함께 지원하는 범용 파서라, PostgreSQL
 * 고유 문법의 세부를 완벽히 커버하지 못할 수 있다. 진짜 PostgreSQL 그래머로 검증하는 라이브러리
 * (libpg_query 등)는 JVM용 Maven 배포판이 없어(JNI로 직접 빌드해야 함, 이 프로젝트의 순수 JVM
 * Docker/K8s 배포 방식과 안 맞음) 대신 이 엔진이 RAG(pgvector)용으로 이미 물고 있는 실제
 * PostgreSQL 서버에 EXPLAIN(플랜만 만들고 절대 실행하지 않음 - ANALYZE 아님)을 던져서 진짜
 * PostgreSQL 파서로 검증한다.
 *
 * 다만 EXPLAIN은 대상 테이블/컬럼이 실제로 존재해야 계획을 세울 수 있는데, 이 도구가 붙어있는
 * dstone_ai(pgvector) DB에는 변환 대상 쿼리가 참조하는 진짜 테이블이 없는 게 보통이다. 그래서
 * SQLSTATE가 "문법은 맞는데 이 서버에 없는 객체를 참조한다"는 부류(undefined_table 등)면 실패로
 * 보지 않고 "문법은 통과, 다만 이 서버 기준 객체 존재 여부는 확인 못 함"으로 보고한다. 진짜
 * syntax_error 등 그 외 오류만 실패로 취급한다.
 *
 * 안전장치: EXPLAIN(ANALYZE 없이)은 문서상 대상 SQL을 절대 실행하지 않는다(SELECT/INSERT/UPDATE/DELETE
 * 전부 동일). 그래도 방어적으로 트랜잭션을 열고 끝나면 무조건 rollback한다. 또한 세미콜론으로 여러
 * 문장을 이어붙여 EXPLAIN 뒤에 실제로 실행되는 두 번째 문장을 몰래 끼워넣는 것을 막기 위해, JDBC로
 * 보내기 전에 JSQLParser로 "정확히 하나의 SELECT/INSERT/UPDATE/DELETE 문"인지부터 검증한다.
 */
@AiTool
@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true")
public class PostgresExplainValidationTools extends BaseObject {

	// syntax_error_or_access_rule_violation(42) 클래스 중, "문법은 맞는데 이 서버에 그 객체가 없다"는
	// 성격의 SQLSTATE만 통과로 봐준다. 그 외(42601 syntax_error 등)는 전부 실패로 취급한다.
	private static final Set<String> UNKNOWN_SCHEMA_OBJECT_SQLSTATES = Set.of(
			"42P01", // undefined_table
			"42703", // undefined_column
			"42883", // undefined_function
			"42P02", // undefined_parameter
			"3F000"  // invalid_schema_name
	);

	private final DataSource dataSource;

	public PostgresExplainValidationTools(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Tool(description = "SQL 문 하나를 실제 PostgreSQL 서버의 EXPLAIN(실행하지 않고 계획만 세움)으로 검증한다. "
			+ "SqlSyntaxTools보다 더 정확한 PostgreSQL 문법 검증이 필요할 때 쓴다. "
			+ "이 서버에 없는 테이블/컬럼을 참조해서 나는 오류는 실패로 보지 않고 알려주기만 하며, 그 외 문법 오류만 실패로 본다.")
	public String validatePostgresSyntax(@ToolParam(description = "검증할 단일 SQL 문(SELECT/INSERT/UPDATE/DELETE)") String sql) {

		String guardResult = this.guardSingleDmlStatement(sql);
		if (guardResult != null) {
			return guardResult;
		}

		try (Connection connection = this.dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (java.sql.Statement jdbcStatement = connection.createStatement()) {
				jdbcStatement.setQueryTimeout(5);
				jdbcStatement.execute("EXPLAIN (COSTS FALSE) " + sql);
				return "통과: 실제 PostgreSQL 서버 기준으로 문법과 참조 객체 모두 문제가 없습니다.";
			} finally {
				connection.rollback();
			}
		} catch (SQLException e) {
			if (UNKNOWN_SCHEMA_OBJECT_SQLSTATES.contains(e.getSQLState())) {
				return "통과(참고): 문법은 정상입니다. 다만 이 서버에는 없는 테이블/컬럼을 참조하고 있어 "
						+ "실제 대상 스키마 기준으로는 확인하지 못했습니다 - " + e.getMessage();
			}
			return "실패: PostgreSQL 서버가 거부했습니다(SQLSTATE=" + e.getSQLState() + ") - " + e.getMessage();
		}
	}

	/** 정확히 하나의 SELECT/INSERT/UPDATE/DELETE 문일 때만 null을 반환하고, 그 외에는 실패 메시지를 반환한다. */
	private String guardSingleDmlStatement(String sql) {
		Statements statements;
		try {
			statements = CCJSqlParserUtil.parseStatements(sql);
		} catch (JSQLParserException e) {
			return "실패: 문법 오류 - " + e.getMessage();
		}
		if (statements.size() != 1) {
			return "실패: 한 번에 하나의 SQL 문만 검증할 수 있습니다(세미콜론으로 여러 문장을 이어붙이지 마십시오). 문장 수="
					+ statements.size();
		}
		Statement statement = statements.get(0);
		if (!(statement instanceof Select || statement instanceof Insert || statement instanceof Update || statement instanceof Delete)) {
			return "실패: SELECT/INSERT/UPDATE/DELETE만 검증할 수 있습니다(DDL 등은 EXPLAIN으로 검증할 수 없습니다).";
		}
		return null;
	}

}

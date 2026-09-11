package net.dstone.ai.tools.sql;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.common.core.BaseObject;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statements;

/**
 * 오라클→PostgreSQL 변환처럼 LLM이 SQL을 생성하는 프롬프트(prompts/oracle-to-postgresql 등)에서,
 * 최종 답변을 내놓기 전에 스스로 문법을 검증하고 틀렸으면 고치는 자기수정 루프에 쓰라고 만든 Tool이다.
 * JSQLParser는 특정 DB 실서버 없이 순수 그래머 파싱만 하므로(대상 테이블이 실제로 존재하는지는 모름)
 * 어떤 대상 스키마의 쿼리든 항상 쓸 수 있다는 게 장점이고, 대신 PostgreSQL 고유 문법의 세부까지
 * 완벽히 검증하지는 못한다 - 더 정확한 검증이 필요하면 dstone.ai.rag.enabled=true일 때만 쓸 수 있는
 * PostgresExplainValidationTools(실제 PostgreSQL 파서로 검증)를 같이 쓴다.
 */
@AiTool
public class SqlSyntaxTools extends BaseObject {

	@Tool(description = "SQL 문 하나의 문법이 구조적으로 올바른지 파싱해서 검사한다(특정 DB 서버 없이 순수 문법 검사). "
			+ "세미콜론으로 여러 문장을 이어붙인 입력은 거부한다. SQL을 최종 답변으로 반환하기 전에 반드시 이 도구로 먼저 검증하고, "
			+ "실패하면 보고된 오류를 근거로 SQL을 고친 뒤 다시 검증하라.")
	public String validateSqlSyntax(@ToolParam(description = "검증할 단일 SQL 문(SELECT/INSERT/UPDATE/DELETE)") String sql) {
		Statements statements;
		try {
			this.sysout("net.dstone.ai.tools.sql.SqlSyntaxTools.validateSqlSyntax INPUT("+sql+")");
			statements = CCJSqlParserUtil.parseStatements(sql);
			
			this.sysout("net.dstone.ai.tools.sql.SqlSyntaxTools.validateSqlSyntax OUTPUT("+statements+")");
		} catch (JSQLParserException e) {
			return "실패: 문법 오류 - " + e.getMessage();
		}
		if (statements.size() != 1) {
			return "실패: 한 번에 하나의 SQL 문만 검증할 수 있습니다(세미콜론으로 여러 문장을 이어붙이지 마십시오). 문장 수="
					+ statements.size();
		}
		return "통과: 문법 구조상 문제가 없습니다.";
	}

}

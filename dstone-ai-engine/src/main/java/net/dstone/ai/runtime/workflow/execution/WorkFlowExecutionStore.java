package net.dstone.ai.runtime.workflow.execution;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.definition.StepType;
import net.dstone.ai.runtime.status.WorkFlowExecutionStatus;

/**
 * AI_WORKFLOW_EXECUTION / AI_WORKFLOW_EXECUTION_STEP_HISTORY 두 테이블(schema/01-create-table-postgresql-
 * ai-workflow-execution.sql)을 JdbcTemplate로 직접 다룬다. 이 모듈은 MyBatis를 쓰지 않으므로 별도 sqlmap 없이
 * 이 클래스 하나가 영속화를 전담한다.
 *
 * variables는 Map이라 JSONB 컬럼에 문자열로 넣고 빼야 한다 - Jackson으로 직렬화/역직렬화하고, INSERT/UPDATE에서는
 * PostgreSQL이 문자열을 jsonb로 자동 변환하지 않으므로 "?::jsonb" 캐스트를 명시한다.
 */
@Repository
public class WorkFlowExecutionStore {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** @param execution 새로 저장할 실행 상태 */
	public void insert(WorkFlowExecution execution) {
		this.jdbcTemplate.update(
			"INSERT INTO AI_WORKFLOW_EXECUTION (EXECUTION_ID, WORKFLOW_ID, CALLER, SESSION_ID, STATUS, CURRENT_STEP_INDEX, VARIABLES_JSON, RESULT_TEXT, ERROR_MESSAGE, CREATED_AT, UPDATED_AT) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)",
			execution.executionId(), execution.workflowId(), execution.caller(), execution.sessionId(), execution.status().name(), execution.currentStepIndex(), this.toJson(execution.variables()),
			execution.resultText(), execution.errorMessage(), Timestamp.from(execution.createdAt()), Timestamp.from(execution.updatedAt()));
	}

	/** @param execution 최신 상태로 덮어쓸 실행 상태 */
	public void update(WorkFlowExecution execution) {
		this.jdbcTemplate.update(
			"UPDATE AI_WORKFLOW_EXECUTION SET STATUS = ?, CURRENT_STEP_INDEX = ?, VARIABLES_JSON = ?::jsonb, RESULT_TEXT = ?, ERROR_MESSAGE = ?, UPDATED_AT = ? WHERE EXECUTION_ID = ?",
			execution.status().name(), execution.currentStepIndex(), this.toJson(execution.variables()), execution.resultText(), execution.errorMessage(), Timestamp.from(execution.updatedAt()),
			execution.executionId());
	}

	/** @param executionId 조회할 실행 id */
	public WorkFlowExecution find(String executionId) {
		List<WorkFlowExecution> found = this.jdbcTemplate.query("SELECT * FROM AI_WORKFLOW_EXECUTION WHERE EXECUTION_ID = ?", this::mapExecution, executionId);
		if (found.isEmpty()) {
			throw new IllegalArgumentException("존재하지 않는 실행입니다: " + executionId);
		}
		return found.get(0);
	}

	/**
	 * <pre>
	 * 실행 목록을 최신순으로 조회한다. status/workflowId/caller는 값이 있을 때만 필터로 걸리고, 셋 다 없으면 전체를 돌려준다.
	 * </pre>
	 *
	 * @param status     WAITING_APPROVAL 등으로 좁히고 싶을 때(없으면 전체)
	 * @param workflowId 특정 workflow의 실행만 보고 싶을 때(없으면 전체)
	 * @param caller     특정 호출 주체의 실행만 보고 싶을 때(없으면 전체)
	 * @param page       0부터 시작하는 페이지 번호
	 * @param size       페이지당 개수
	 */
	public List<WorkFlowExecution> list(String status, String workflowId, String caller, int page, int size) {
		StringBuilder sql = new StringBuilder("SELECT * FROM AI_WORKFLOW_EXECUTION WHERE 1=1");
		List<Object> args = new java.util.ArrayList<>();
		if (StringUtils.hasText(status)) {
			sql.append(" AND STATUS = ?");
			args.add(status);
		}
		if (StringUtils.hasText(workflowId)) {
			sql.append(" AND WORKFLOW_ID = ?");
			args.add(workflowId);
		}
		if (StringUtils.hasText(caller)) {
			sql.append(" AND CALLER = ?");
			args.add(caller);
		}
		sql.append(" ORDER BY CREATED_AT DESC LIMIT ? OFFSET ?");
		args.add(size);
		args.add(page * size);
		return this.jdbcTemplate.query(sql.toString(), this::mapExecution, args.toArray());
	}

	/**
	 * @param executionId 이력을 남길 실행 id
	 * @param entry       스텝 실행 결과 1건
	 */
	public void appendHistory(String executionId, StepHistoryEntry entry) {
		this.jdbcTemplate.update(
			"INSERT INTO AI_WORKFLOW_EXECUTION_STEP_HISTORY (EXECUTION_ID, STEP_ID, STEP_TYPE, STEP_REF, SUCCESS, DURATION_MS, OUTPUT_SUMMARY, FAILURE_REASON, EXECUTED_AT) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
			executionId, entry.stepId(), entry.stepType().name(), entry.ref(), entry.success(), entry.durationMs(), entry.outputSummary(), entry.failureReason(), Timestamp.from(entry.executedAt()));
	}

	/** @param executionId 이력을 조회할 실행 id */
	public List<StepHistoryEntry> findHistory(String executionId) {
		return this.jdbcTemplate.query("SELECT * FROM AI_WORKFLOW_EXECUTION_STEP_HISTORY WHERE EXECUTION_ID = ? ORDER BY EXECUTED_AT ASC, ID ASC", this::mapHistoryEntry, executionId);
	}

	private WorkFlowExecution mapExecution(ResultSet rs, int rowNum) throws SQLException {
		return new WorkFlowExecution(
			rs.getString("EXECUTION_ID"),
			rs.getString("WORKFLOW_ID"),
			rs.getString("CALLER"),
			rs.getString("SESSION_ID"),
			WorkFlowExecutionStatus.valueOf(rs.getString("STATUS")),
			rs.getInt("CURRENT_STEP_INDEX"),
			this.fromJson(rs.getString("VARIABLES_JSON")),
			rs.getString("RESULT_TEXT"),
			rs.getString("ERROR_MESSAGE"),
			this.toInstant(rs.getTimestamp("CREATED_AT")),
			this.toInstant(rs.getTimestamp("UPDATED_AT")));
	}

	private StepHistoryEntry mapHistoryEntry(ResultSet rs, int rowNum) throws SQLException {
		return new StepHistoryEntry(
			rs.getString("STEP_ID"),
			StepType.valueOf(rs.getString("STEP_TYPE")),
			rs.getString("STEP_REF"),
			rs.getBoolean("SUCCESS"),
			rs.getLong("DURATION_MS"),
			rs.getString("OUTPUT_SUMMARY"),
			rs.getString("FAILURE_REASON"),
			this.toInstant(rs.getTimestamp("EXECUTED_AT")));
	}

	private Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}

	/** @param variables JSON 문자열로 바꿀 변수 맵 */
	private String toJson(Map<String, Object> variables) {
		try {
			return this.objectMapper.writeValueAsString(variables == null ? Map.of() : variables);
		} catch (Exception e) {
			throw new IllegalStateException("Workflow 변수를 JSON으로 직렬화하지 못했습니다.", e);
		}
	}

	/** @param json 변수 맵으로 되돌릴 JSON 문자열 */
	private Map<String, Object> fromJson(String json) {
		if (!StringUtils.hasText(json)) {
			return new LinkedHashMap<>();
		}
		try {
			return this.objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
			});
		} catch (Exception e) {
			throw new IllegalStateException("저장된 Workflow 변수를 파싱하지 못했습니다: " + json, e);
		}
	}

}

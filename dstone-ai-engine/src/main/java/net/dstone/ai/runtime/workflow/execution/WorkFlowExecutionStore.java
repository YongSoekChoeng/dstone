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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.consts.StepType;

/**
 * AI_WORKFLOW_EXECUTION과 AI_WORKFLOW_EXECUTION_STEP_HISTORY, 이 두 테이블(schema/01-create-table-postgresql-
 * ai-workflow-execution.sql에 정의되어 있습니다)을 JdbcTemplate로 직접 다루는 클래스입니다. 이 모듈은
 * MyBatis를 쓰지 않기 때문에, 별도의 sqlmap 파일 없이 이 클래스 하나가 저장과 조회를 전담합니다.
 *
 * variables는 자바에서는 Map이지만, DB에는 JSONB 컬럼에 문자열로 저장해야 합니다. 그래서 저장할 때는
 * Jackson으로 JSON 문자열로 바꾸고, 읽어올 때는 다시 Map으로 되돌립니다. INSERT/UPDATE 쿼리에서는
 * PostgreSQL이 일반 문자열을 jsonb 타입으로 자동으로 바꿔주지 않으므로, "?::jsonb"라고 캐스트를
 * 직접 명시해 줍니다.
 */
@Repository
public class WorkFlowExecutionStore {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** 새 실행 상태를 한 행으로 저장합니다. @param execution 새로 저장할 실행 상태입니다. */
	public void insert(WorkFlowExecution execution) {
		this.jdbcTemplate.update(
			"INSERT INTO AI_WORKFLOW_EXECUTION ("
			+ "  EXECUTION_ID, WORKFLOW_ID, CALLER, SESSION_ID, STATUS, CURRENT_STEP_INDEX, VARIABLES_JSON, RESULT_TEXT, ERROR_MESSAGE, CREATED_AT, UPDATED_AT "
			+ ") VALUES ( "
			+ "  ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ? "
			+ ")",
			execution.executionId(), execution.workflowId(), execution.caller(), execution.sessionId(), execution.status().name(), execution.currentStepIndex(), this.toJson(execution.variables()),
			execution.resultText(), execution.errorMessage(), Timestamp.from(execution.createdAt()), Timestamp.from(execution.updatedAt()));
	}

	/** 기존에 저장된 실행 상태를 최신 상태로 덮어씁니다. @param execution 덮어쓸 최신 실행 상태입니다. */
	public void update(WorkFlowExecution execution) {
		this.jdbcTemplate.update(
			"UPDATE AI_WORKFLOW_EXECUTION SET STATUS = ?, CURRENT_STEP_INDEX = ?, VARIABLES_JSON = ?::jsonb, RESULT_TEXT = ?, ERROR_MESSAGE = ?, UPDATED_AT = ? WHERE EXECUTION_ID = ?",
			execution.status().name(), execution.currentStepIndex(), this.toJson(execution.variables()), execution.resultText(), execution.errorMessage(), Timestamp.from(execution.updatedAt()),
			execution.executionId());
	}

	/** id로 실행 상태 한 건을 조회합니다. @param executionId 조회할 실행의 id입니다. */
	public WorkFlowExecution find(String executionId) {
		List<WorkFlowExecution> found = this.jdbcTemplate.query("SELECT * FROM AI_WORKFLOW_EXECUTION WHERE EXECUTION_ID = ?", this.executionRowMapper(), executionId);
		if (found.isEmpty()) {
			throw new IllegalArgumentException("존재하지 않는 실행입니다: " + executionId);
		}
		return found.get(0);
	}

	/**
	 * 실행 목록을 최신순으로 조회합니다. status, workflowId, caller는 값이 있을 때만 필터로 적용되고,
	 * 셋 다 비어 있으면 전체를 돌려줍니다.
	 *
	 * @param status     WAITING_APPROVAL처럼 특정 상태로만 좁혀서 보고 싶을 때 씁니다(비우면 전체를 봅니다).
	 * @param workflowId 특정 Workflow의 실행만 보고 싶을 때 씁니다(비우면 전체를 봅니다).
	 * @param caller     특정 호출 주체의 실행만 보고 싶을 때 씁니다(비우면 전체를 봅니다).
	 * @param page       0부터 시작하는 페이지 번호입니다.
	 * @param size       한 페이지에 담을 개수입니다.
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
		return this.jdbcTemplate.query(sql.toString(), this.executionRowMapper(), args.toArray());
	}

	/**
	 * 스텝 하나가 실행된 이력을 한 줄 추가합니다.
	 *
	 * @param executionId 이 이력이 속한 실행의 id입니다.
	 * @param entry       기록할 스텝 실행 결과 한 건입니다.
	 */
	public void appendHistory(String executionId, StepHistoryEntry entry) {
		this.jdbcTemplate.update(
			"INSERT INTO AI_WORKFLOW_EXECUTION_STEP_HISTORY (EXECUTION_ID, STEP_ID, STEP_TYPE, STEP_REF, SUCCESS, DURATION_MS, OUTPUT_SUMMARY, FAILURE_REASON, EXECUTED_AT) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
			executionId, entry.stepId(), entry.stepType().name(), entry.ref(), entry.success(), entry.durationMs(), entry.outputSummary(), entry.failureReason(), Timestamp.from(entry.executedAt()));
	}

	/** 실행 한 건의 스텝 이력을 전부 조회합니다. @param executionId 이력을 조회할 실행의 id입니다. */
	public List<StepHistoryEntry> findHistory(String executionId) {
		return this.jdbcTemplate.query("SELECT * FROM AI_WORKFLOW_EXECUTION_STEP_HISTORY WHERE EXECUTION_ID = ? ORDER BY EXECUTED_AT ASC, ID ASC", this.historyRowMapper(), executionId);
	}

	/** find()와 list()가 함께 쓰는, AI_WORKFLOW_EXECUTION의 한 행을 WorkFlowExecution으로 바꿔주는 매핑기입니다. */
	private RowMapper<WorkFlowExecution> executionRowMapper() {
		return new RowMapper<WorkFlowExecution>() {
			@Override
			public WorkFlowExecution mapRow(ResultSet rs, int rowNum) throws SQLException {
				return WorkFlowExecutionStore.this.mapExecution(rs, rowNum);
			}
		};
	}

	/** findHistory()가 쓰는, AI_WORKFLOW_EXECUTION_STEP_HISTORY의 한 행을 StepHistoryEntry로 바꿔주는 매핑기입니다. */
	private RowMapper<StepHistoryEntry> historyRowMapper() {
		return new RowMapper<StepHistoryEntry>() {
			@Override
			public StepHistoryEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
				return WorkFlowExecutionStore.this.mapHistoryEntry(rs, rowNum);
			}
		};
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
		StepType stepType = StepType.valueOf(rs.getString("STEP_TYPE"));
		return new StepHistoryEntry(
			rs.getString("STEP_ID"),
			stepType,
			stepType.kind(),
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

	/** 변수 맵을 DB에 저장할 수 있도록 JSON 문자열로 바꿉니다. @param variables JSON 문자열로 바꿀 변수 맵입니다. */
	private String toJson(Map<String, Object> variables) {
		try {
			return this.objectMapper.writeValueAsString(variables == null ? Map.of() : variables);
		} catch (Exception e) {
			throw new IllegalStateException("Workflow 변수를 JSON으로 직렬화하지 못했습니다.", e);
		}
	}

	/** DB에서 읽어온 JSON 문자열을 다시 변수 맵으로 되돌립니다. @param json 변수 맵으로 되돌릴 JSON 문자열입니다. */
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

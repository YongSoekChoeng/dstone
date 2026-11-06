package net.dstone.ai.runtime.workflow.execution;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.biz.BaseDao;
import net.dstone.ai.common.consts.StepType;
import net.dstone.ai.common.consts.WorkFlowExecutionStatus;

/**
 * <pre>
 * AI_WORKFLOW_EXECUTION과 AI_WORKFLOW_EXECUTION_STEP_HISTORY,
 * 이 두 테이블(schema/02-create-table-postgresql-dstone-ai.sql에 정의되어 있습니다)의 저장과 조회를 맡는 Dao입니다.
 * SQL은 MyBatis 매퍼(resources/sqlmap/workflow/WorkFlowExecutionDao.xml)에 있고, 이 클래스는 값을 바꿔서 넘기고 받습니다.
 *
 * context(실행 컨텍스트 트리)는 자바에서는 Map이지만, DB에는 CONTEXT_JSON(JSONB) 컬럼에 문자열로 저장해야 합니다. 
 * 그래서 저장할 때는 Jackson으로 JSON 문자열로 바꾸고, 읽어올 때는 다시 Map으로 되돌립니다.
 * (jsonb로 바꾸는 CAST는 매퍼 XML에 적혀 있습니다.)
 *
 * 최종 결과(WorkFlowExecution.output)는 글자일 수도, 객체나 리스트일 수도 있어서 RESULT_TEXT 컬럼에 항상 JSON 글자로 저장하고, 읽을 때 다시 원래 값으로 되돌립니다
 * (글자 결과는 "..."처럼 따옴표가 붙은 JSON 글자로 저장됩니다).
 * 
 * ## 테이블정의
 *  <b>(AI_WORKFLOW_EXECUTION - WORKFLOW 실행)</b>
 *     EXECUTION_ID        실행아이디(KEY)
 *     WORKFLOW_ID         WORKFLOW아이디
 *     CALLER              호출클라이언트
 *     SESSION_ID          세션아이디
 *     STATUS              상태(QUEUED / RUNNING / WAITING_APPROVAL / PAUSED / DONE / FAILED / CANCELLED)
 *     CURRENT_STEP_INDEX  현재 STEP 인덱스
 *     CONTEXT_JSON        컨텍스트
 *     RESULT_TEXT         결과텍스트
 *     ERROR_MESSAGE       에러텍스트
 *     PAUSE_REQUESTED     일시정지 요청 여부
 *     REQUESTED_BY        실행을 시작한 사용자(dstone-boot 로그인 id)
 *     OWNER_INSTANCE      실행을 맡은 엔진 인스턴스(EngineInstance)
 *     HEARTBEAT_AT        맡은 인스턴스가 살아 있다고 마지막으로 알린 시각
 * 
 * <b>(AI_WORKFLOW_EXECUTION_STEP_HISTORY - WORKFLOW STEP 실행 이력)</b>
 *     ID                실행이력아이디(KEY)
 *     EXECUTION_ID      실행아이디
 *     STEP_ID           STEP 아이디
 *     STEP_TYPE         STEP 종류
 *     STEP_REF          참조 STEP 아이디
 *     SUCCESS           성공여부
 *     DURATION_MS       수행시간
 *     OUTPUT_SUMMARY    STEP 결과텍스트
 *     FAILURE_REASON    STEP 실패텍스트
 * 
 * </pre>
 */
@Repository
public class WorkFlowExecutionStore extends BaseDao {

	private static final String NS = "net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore.";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private EngineInstance engineInstance;

	/** 새 실행 상태를 한 행으로 저장합니다. 이 인스턴스가 맡은 실행으로 적습니다. @param execution 새로 저장할 실행 상태입니다. */
	public void insert(WorkFlowExecution execution) {
		Map<String, Object> param = this.executionParam(execution);
		param.put("workflowId", execution.workflowId());
		param.put("caller", execution.caller());
		param.put("sessionId", execution.sessionId());
		param.put("createdAt", Timestamp.from(execution.createdAt()));
		param.put("requestedBy", execution.requestedBy());
		param.put("ownerInstance", this.engineInstance.id());
		this.sqlSessionCommon.insert(NS + "insertExecution", param);
	}

	/** 기존에 저장된 실행 상태를 최신 상태로 덮어씁니다. @param execution 덮어쓸 최신 실행 상태입니다. */
	public void update(WorkFlowExecution execution) {
		this.sqlSessionCommon.update(NS + "updateExecution", this.executionParam(execution));
	}

	/** insert와 update가 같이 쓰는 값: 실행이 진행되면서 바뀌는 것들입니다. */
	private Map<String, Object> executionParam(WorkFlowExecution execution) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("executionId", execution.executionId());
		param.put("status", execution.status().name());
		param.put("currentStepIndex", Integer.valueOf(execution.currentStepIndex()));
		param.put("contextJson", this.toJson(execution.context()));
		param.put("resultText", this.toResultJson(execution.output()));
		param.put("errorMessage", execution.errorMessage());
		param.put("updatedAt", Timestamp.from(execution.updatedAt()));
		return param;
	}

	/** id로 실행 상태 한 건을 조회합니다. @param executionId 조회할 실행의 id입니다. */
	public WorkFlowExecution find(String executionId) {
		Map<String, Object> row = this.sqlSessionCommon.selectOne(NS + "selectExecution", executionId);
		if (row == null) {
			throw new IllegalArgumentException("존재하지 않는 실행입니다: " + executionId);
		}
		return this.mapExecution(row);
	}

	/**
	 * 실행 목록을 최신순으로 조회합니다. status, workflowId, caller는 값이 있을 때만 필터로 적용되고,
	 * 셋 다 비어 있으면 전체를 돌려줍니다. 건수를 세거나 목록을 보여 줄 때만 쓰므로 컨텍스트와 결과는 읽지 않습니다.
	 *
	 * @param status     WAITING_APPROVAL처럼 특정 상태로만 좁혀서 보고 싶을 때 씁니다(비우면 전체를 봅니다).
	 * @param workflowId 특정 Workflow의 실행만 보고 싶을 때 씁니다(비우면 전체를 봅니다).
	 * @param caller     특정 호출 주체의 실행만 보고 싶을 때 씁니다(비우면 전체를 봅니다).
	 * @param page       0부터 시작하는 페이지 번호입니다.
	 * @param size       한 페이지에 담을 개수입니다.
	 */
	public List<WorkFlowExecution> list(String status, String workflowId, String caller, int page, int size) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("status", status);
		param.put("workflowId", workflowId);
		param.put("caller", caller);
		param.put("size", Integer.valueOf(size));
		param.put("offset", Integer.valueOf(page * size));
		List<Map<String, Object>> rows = this.sqlSessionCommon.selectList(NS + "selectExecutionList", param);
		List<WorkFlowExecution> executions = new ArrayList<WorkFlowExecution>(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			executions.add(this.mapExecution(rows.get(i)));
		}
		return executions;
	}

	/**
	 * 관리 화면의 검색입니다. list()와 달리 workflowId와 executionId는 일부만 적어도 찾습니다.
	 *
	 * @param status      이 상태만 봅니다(비우면 전체).
	 * @param workflowId  id에 이 글자가 들어 있는 Workflow만 봅니다(비우면 전체).
	 * @param executionId id에 이 글자가 들어 있는 실행만 봅니다(비우면 전체).
	 * @param caller      이 호출 주체만 봅니다(비우면 전체).
	 * @param requestedBy 이 사용자가 시작한 실행만 봅니다(비우면 전체).
	 * @param page        0부터 시작하는 페이지 번호입니다.
	 * @param size        한 페이지에 담을 개수입니다.
	 */
	public List<WorkFlowExecution> search(String status, String workflowId, String executionId, String caller, String requestedBy, int page, int size) {
		Map<String, Object> param = this.searchParam(status, workflowId, executionId, caller, requestedBy);
		param.put("size", Integer.valueOf(size));
		param.put("offset", Integer.valueOf(page * size));
		List<Map<String, Object>> rows = this.sqlSessionCommon.selectList(NS + "searchExecutionList", param);
		List<WorkFlowExecution> executions = new ArrayList<WorkFlowExecution>(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			executions.add(this.mapExecution(rows.get(i)));
		}
		return executions;
	}

	/** search()와 같은 조건에 맞는 실행이 모두 몇 건인지 돌려줍니다. */
	public long count(String status, String workflowId, String executionId, String caller, String requestedBy) {
		Long total = this.sqlSessionCommon.selectOne(NS + "countExecutionList", this.searchParam(status, workflowId, executionId, caller, requestedBy));
		return total == null ? 0L : total.longValue();
	}

	private Map<String, Object> searchParam(String status, String workflowId, String executionId, String caller, String requestedBy) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("status", status);
		param.put("workflowId", workflowId);
		param.put("executionId", executionId);
		param.put("caller", caller);
		param.put("requestedBy", requestedBy);
		return param;
	}

	/**
	 * 돌고 있는 실행에 일시정지를 요청합니다. WorkFlowExecutor가 다음 step을 시작하기 전에 이 요청을 보고 멈춥니다.
	 *
	 * @param executionId 멈출 실행의 id입니다.
	 * @return 요청을 넣었으면 true, 실행이 RUNNING이 아니면 false입니다.
	 */
	public boolean requestPause(String executionId) {
		return this.sqlSessionCommon.update(NS + "requestPause", executionId) > 0;
	}

	/** 이 실행에 일시정지 요청이 들어와 있는지 돌려줍니다. @param executionId 확인할 실행의 id입니다. */
	public boolean isPauseRequested(String executionId) {
		Boolean requested = this.sqlSessionCommon.selectOne(NS + "selectPauseRequested", executionId);
		return Boolean.TRUE.equals(requested);
	}

	/**
	 * <pre>
	 * 멈춰 있던 실행(승인 대기, 일시정지, 실패, 완료)을 다시 돌리려고 QUEUED로 바꾸고, 이 인스턴스가 맡은 실행으로 적습니다.
	 * 상태가 읽어 온 그대로일 때만 바꾸므로, 재개나 승인을 동시에 두 번 눌러도 한 번만 성공합니다.
	 * </pre>
	 *
	 * @param execution  다시 돌릴 실행입니다(읽어 온 그대로의 상태).
	 * @param stepIndex  다시 실행할 스텝이 몇 번째인지입니다.
	 * @return 바꿨으면 true, 그사이 상태가 달라졌으면 false입니다.
	 */
	public boolean markQueued(WorkFlowExecution execution, int stepIndex) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("executionId", execution.executionId());
		param.put("fromStatus", execution.status().name());
		param.put("currentStepIndex", Integer.valueOf(stepIndex));
		param.put("ownerInstance", this.engineInstance.id());
		param.put("updatedAt", Timestamp.from(Instant.now()));
		return this.sqlSessionCommon.update(NS + "markQueued", param) > 0;
	}

	/**
	 * 대기열에서 기다리던 실행이 슬롯을 받았을 때 RUNNING으로 바꿉니다. QUEUED 그대로일 때만 바꿉니다.
	 *
	 * @param executionId 돌기 시작할 실행의 id입니다.
	 * @return 바꿨으면 true, 그사이 일시정지 등으로 상태가 달라졌으면 false입니다(이때는 돌리지 않습니다).
	 */
	public boolean markStarted(String executionId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("executionId", executionId);
		param.put("ownerInstance", this.engineInstance.id());
		param.put("updatedAt", Timestamp.from(Instant.now()));
		return this.sqlSessionCommon.update(NS + "markStarted", param) > 0;
	}

	/**
	 * 대기열이 꽉 차서 받지 못한 새 실행을 지웁니다. QUEUED일 때만 지웁니다.
	 *
	 * @param executionId 지울 실행의 id입니다.
	 */
	public void deleteQueued(String executionId) {
		this.sqlSessionCommon.delete(NS + "deleteQueued", executionId);
	}

	/**
	 * 아직 대기열에 있는 실행을 바로 PAUSED로 바꿉니다.
	 *
	 * @param executionId 멈출 실행의 id입니다.
	 * @return 바꿨으면 true, QUEUED가 아니었으면 false입니다.
	 */
	public boolean pauseQueued(String executionId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("executionId", executionId);
		param.put("updatedAt", Timestamp.from(Instant.now()));
		return this.sqlSessionCommon.update(NS + "pauseQueued", param) > 0;
	}

	/**
	 * 이 인스턴스가 맡은 실행(도는 중이거나 대기열에 있는 것)에 "아직 살아 있다"고 적습니다. WorkFlowExecutionRecovery가 주기적으로 부릅니다.
	 *
	 * @return 갱신한 건수입니다.
	 */
	public int heartbeat() {
		return this.sqlSessionCommon.update(NS + "heartbeat", this.engineInstance.id());
	}

	/**
	 * <pre>
	 * 맡은 인스턴스가 사라진 실행(heartbeat가 staleSeconds초 넘게 끊긴 RUNNING/QUEUED)을 FAILED로 바꿉니다.
	 * 서버가 뜰 때와 그 뒤 주기적으로 WorkFlowExecutionRecovery가 부릅니다.
	 * </pre>
	 *
	 * @param errorMessage 남길 실패 사유입니다.
	 * @param staleSeconds heartbeat가 이 초만큼 끊기면 맡은 인스턴스가 사라졌다고 봅니다.
	 * @return 바꾼 건수입니다.
	 */
	public int failOrphans(String errorMessage, int staleSeconds) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("errorMessage", errorMessage);
		param.put("staleSeconds", Integer.valueOf(staleSeconds));
		return this.sqlSessionCommon.update(NS + "failOrphans", param);
	}

	/**
	 * 스텝 하나가 실행된 이력을 한 줄 추가합니다.
	 *
	 * @param executionId 이 이력이 속한 실행의 id입니다.
	 * @param entry       기록할 스텝 실행 결과 한 건입니다.
	 */
	public void appendHistory(String executionId, StepHistoryEntry entry) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("executionId", executionId);
		param.put("stepId", entry.stepId());
		param.put("stepType", entry.stepType().name());
		param.put("stepRef", entry.ref());
		param.put("success", Boolean.valueOf(entry.success()));
		param.put("durationMs", Long.valueOf(entry.durationMs()));
		param.put("outputSummary", entry.outputSummary());
		param.put("failureReason", entry.failureReason());
		param.put("executedAt", Timestamp.from(entry.executedAt()));
		this.sqlSessionCommon.insert(NS + "insertHistory", param);
	}

	/** 실행 한 건의 스텝 이력을 전부 조회합니다. @param executionId 이력을 조회할 실행의 id입니다. */
	public List<StepHistoryEntry> findHistory(String executionId) {
		List<Map<String, Object>> rows = this.sqlSessionCommon.selectList(NS + "selectHistoryList", executionId);
		List<StepHistoryEntry> history = new ArrayList<StepHistoryEntry>(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			history.add(this.mapHistoryEntry(rows.get(i)));
		}
		return history;
	}

	/**
	 * AI_WORKFLOW_EXECUTION 한 행을 WorkFlowExecution으로 바꿉니다. CONTEXT_JSON 컬럼은 다시 컨텍스트 맵으로 되돌립니다.
	 * 목록 조회는 CONTEXT_JSON과 RESULT_TEXT를 읽지 않으므로, 그때는 빈 컨텍스트와 null 결과가 들어갑니다.
	 */
	private WorkFlowExecution mapExecution(Map<String, Object> row) {
		return new WorkFlowExecution(
			this.textOf(row.get("executionId")),
			this.textOf(row.get("workflowId")),
			this.textOf(row.get("caller")),
			this.textOf(row.get("sessionId")),
			WorkFlowExecutionStatus.valueOf(this.textOf(row.get("status"))),
			row.get("currentStepIndex") == null ? 0 : ((Number) row.get("currentStepIndex")).intValue(),
			this.fromJson(this.textOf(row.get("contextJson"))),
			this.fromResultJson(this.textOf(row.get("resultText"))),
			this.textOf(row.get("errorMessage")),
			this.toInstant(row.get("createdAt")),
			this.toInstant(row.get("updatedAt")),
			this.textOf(row.get("requestedBy")));
	}

	/** AI_WORKFLOW_EXECUTION_STEP_HISTORY 한 행을 StepHistoryEntry로 바꿉니다. */
	private StepHistoryEntry mapHistoryEntry(Map<String, Object> row) {
		return new StepHistoryEntry(
			this.textOf(row.get("stepId")),
			StepType.valueOf(this.textOf(row.get("stepType"))),
			this.textOf(row.get("stepRef")),
			Boolean.TRUE.equals(row.get("success")),
			row.get("durationMs") == null ? 0L : ((Number) row.get("durationMs")).longValue(),
			this.textOf(row.get("outputSummary")),
			this.textOf(row.get("failureReason")),
			this.toInstant(row.get("executedAt")));
	}

	private String textOf(Object value) {
		return value == null ? null : value.toString();
	}

	/**
	 * DB에서 읽은 시각을 Instant로 바꿉니다. 값이 없으면(null) 그대로 null입니다.
	 * Map으로 받으면 드라이버가 주는 타입 그대로 오는데, TIMESTAMPTZ는 java.sql.Timestamp(java.util.Date의 하위 타입)로 옵니다.
	 */
	private Instant toInstant(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Date) {
			return ((Date) value).toInstant();
		}
		if (value instanceof java.time.OffsetDateTime) {
			return ((java.time.OffsetDateTime) value).toInstant();
		}
		throw new IllegalStateException("시각으로 읽을 수 없는 값입니다: " + value.getClass().getName());
	}

	/** 컨텍스트 맵을 DB에 저장할 수 있도록 JSON 문자열로 바꿉니다. @param context JSON 문자열로 바꿀 컨텍스트 맵입니다. */
	private String toJson(Map<String, Object> context) {
		try {
			return this.objectMapper.writeValueAsString(context == null ? Map.of() : context);
		} catch (Exception e) {
			throw new IllegalStateException("Workflow 컨텍스트를 JSON으로 직렬화하지 못했습니다.", e);
		}
	}

	/**
	 * 최종 결과를 RESULT_TEXT 컬럼에 저장할 JSON 글자로 바꿉니다. 아직 결과가 없으면(null) null입니다.
	 *
	 * @param output 최종 결과입니다.
	 */
	private String toResultJson(Object output) {
		if (output == null) {
			return null;
		}
		try {
			return this.objectMapper.writeValueAsString(output);
		} catch (Exception e) {
			throw new IllegalStateException("Workflow 결과를 JSON으로 직렬화하지 못했습니다.", e);
		}
	}

	/**
	 * RESULT_TEXT 컬럼의 JSON 글자를 원래 결과 값으로 되돌립니다. JSON이 아니면 글자 그대로 씁니다.
	 *
	 * @param json RESULT_TEXT 컬럼 값입니다.
	 */
	private Object fromResultJson(String json) {
		if (json == null) {
			return null;
		}
		try {
			return this.objectMapper.readValue(json, Object.class);
		} catch (Exception e) {
			return json;
		}
	}

	/** DB에서 읽어온 JSON 문자열을 다시 컨텍스트 맵으로 되돌립니다. @param json 컨텍스트 맵으로 되돌릴 JSON 문자열입니다. */
	private Map<String, Object> fromJson(String json) {
		if (!StringUtils.hasText(json)) {
			return new LinkedHashMap<>();
		}
		try {
			return this.objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
			});
		} catch (Exception e) {
			throw new IllegalStateException("저장된 Workflow 컨텍스트를 파싱하지 못했습니다: " + json, e);
		}
	}

}

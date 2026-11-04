package net.dstone.ai.runtime.workflow.file;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.ai.common.biz.BaseDao;

/**
 * <pre>
 * AI_WORKFLOW_EXECUTION_FILE(step이 읽고 쓴 파일 기록)의 저장과 조회를 맡는 Dao입니다.
 * SQL은 resources/sqlmap/workflow/WorkFlowFileDao.xml에 있습니다.
 *
 * ## 테이블정의
 *  <b>(AI_WORKFLOW_EXECUTION_FILE - step이 읽고 쓴 파일)</b>
 *     EXECUTION_ID   실행아이디(KEY)
 *     STEP_ID        STEP 아이디(KEY)
 *     FILE_PATH      파일 경로(KEY)
 *     ACTION         READ / WRITE(KEY)
 *     ACCESS_COUNT   건드린 횟수
 *     FIRST_AT       처음 건드린 시각
 *     LAST_AT        마지막으로 건드린 시각
 *     CREATED        WRITE일 때 이 step이 새로 만든 파일인지(이 열이 생기기 전 기록은 NULL)
 * </pre>
 */
@Repository
public class WorkFlowFileStore extends BaseDao {

	private static final String NS = "net.dstone.ai.runtime.workflow.file.WorkFlowFileStore.";

	/**
	 * 파일 하나를 건드렸다고 기록합니다. 같은 (실행, step, 경로, 동작)이 있으면 횟수만 늘립니다.
	 *
	 * @param executionId 실행 id
	 * @param stepId      step id
	 * @param path        파일 경로
	 * @param action      READ 또는 WRITE
	 * @param created     WRITE일 때 이 호출 전에는 없던 파일이면 true
	 */
	public void record(String executionId, String stepId, String path, String action, boolean created) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("executionId", executionId);
		param.put("stepId", stepId);
		param.put("filePath", path);
		param.put("action", action);
		param.put("accessedAt", Timestamp.from(Instant.now()));
		param.put("created", Boolean.valueOf(created));
		this.sqlSessionCommon.insert(NS + "upsertFile", param);
	}

	/** 실행 한 건이 건드린 파일을 처음 건드린 순서대로 돌려줍니다. @param executionId 실행 id */
	public List<WorkFlowFileEntry> list(String executionId) {
		List<Map<String, Object>> rows = this.sqlSessionCommon.selectList(NS + "selectFileList", executionId);
		List<WorkFlowFileEntry> entries = new ArrayList<WorkFlowFileEntry>(rows.size());
		for (Map<String, Object> row : rows) {
			entries.add(new WorkFlowFileEntry(
				(String) row.get("stepId"),
				(String) row.get("filePath"),
				(String) row.get("action"),
				row.get("accessCount") == null ? 0 : ((Number) row.get("accessCount")).intValue(),
				this.toInstant(row.get("firstAt")),
				this.toInstant(row.get("lastAt")),
				(Boolean) row.get("created")));
		}
		return entries;
	}

	/**
	 * 이 실행이 이 경로를 건드린 적이 있는지 봅니다. 화면이 기록에 없는 파일을 열지 못하게 할 때 씁니다.
	 *
	 * @param executionId 실행 id
	 * @param path        파일 경로
	 */
	public boolean contains(String executionId, String path) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("executionId", executionId);
		param.put("filePath", path);
		Long count = this.sqlSessionCommon.selectOne(NS + "countFile", param);
		return count != null && count.longValue() > 0;
	}

	/**
	 * 재실행할 때 되돌리는 구간의 step 기록을 지웁니다. 그 step이 다시 돌면서 새로 남깁니다.
	 *
	 * @param executionId 실행 id
	 * @param stepIds     기록을 지울 step id들(비어 있으면 아무것도 하지 않습니다)
	 */
	public void deleteSteps(String executionId, List<String> stepIds) {
		if (stepIds == null || stepIds.isEmpty()) {
			return;
		}
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("executionId", executionId);
		param.put("stepIds", stepIds);
		this.sqlSessionCommon.delete(NS + "deleteStepFiles", param);
	}

	/** DB에서 읽은 시각(TIMESTAMPTZ)을 Instant로 바꿉니다. */
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

}

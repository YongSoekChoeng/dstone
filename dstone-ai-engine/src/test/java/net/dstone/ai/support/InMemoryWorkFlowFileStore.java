package net.dstone.ai.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.dstone.ai.runtime.workflow.file.WorkFlowFileEntry;
import net.dstone.ai.runtime.workflow.file.WorkFlowFileStore;

/**
 * <pre>
 * 테스트에서 DB 대신 쓰는 "step이 읽고 쓴 파일" 기록 저장소입니다. 기록을 메모리 목록에 둡니다.
 * 운영 저장소의 SQL(ON CONFLICT)은 PostgreSQL 전용이라, 같은 동작을 메모리로 다시 구현했습니다.
 * </pre>
 */
public class InMemoryWorkFlowFileStore extends WorkFlowFileStore {

	/** 기록 한 줄입니다. 운영 테이블의 한 행과 같습니다. */
	private static final class Row {
		private final String executionId;
		private final String stepId;
		private final String path;
		private final String action;
		private int accessCount;
		private final Instant firstAt;
		private Instant lastAt;
		private boolean created;

		private Row(String executionId, String stepId, String path, String action, Instant at, boolean created) {
			this.executionId = executionId;
			this.stepId = stepId;
			this.path = path;
			this.action = action;
			this.accessCount = 1;
			this.firstAt = at;
			this.lastAt = at;
			this.created = created;
		}
	}

	private final List<Row> rows = new ArrayList<Row>();

	@Override
	public synchronized void record(String executionId, String stepId, String path, String action, boolean created) {
		Instant now = Instant.now();
		for (Row row : this.rows) {
			if (row.executionId.equals(executionId) && row.stepId.equals(stepId) && row.path.equals(path) && row.action.equals(action)) {
				row.accessCount++;
				row.lastAt = now;
				row.created = row.created || created;
				return;
			}
		}
		this.rows.add(new Row(executionId, stepId, path, action, now, created));
	}

	@Override
	public synchronized List<WorkFlowFileEntry> list(String executionId) {
		List<WorkFlowFileEntry> entries = new ArrayList<WorkFlowFileEntry>();
		for (Row row : this.rows) {
			if (row.executionId.equals(executionId)) {
				entries.add(new WorkFlowFileEntry(row.stepId, row.path, row.action, row.accessCount, row.firstAt, row.lastAt, Boolean.valueOf(row.created)));
			}
		}
		return entries;
	}

	@Override
	public synchronized void deleteSteps(String executionId, List<String> stepIds) {
		for (int i = this.rows.size() - 1; i >= 0; i--) {
			Row row = this.rows.get(i);
			if (row.executionId.equals(executionId) && stepIds.contains(row.stepId)) {
				this.rows.remove(i);
			}
		}
	}

	@Override
	public synchronized boolean contains(String executionId, String path) {
		for (Row row : this.rows) {
			if (row.executionId.equals(executionId) && row.path.equals(path)) {
				return true;
			}
		}
		return false;
	}

}

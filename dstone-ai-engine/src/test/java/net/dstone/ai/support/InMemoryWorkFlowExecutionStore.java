package net.dstone.ai.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;

/**
 * <pre>
 * 테스트에서 DB 대신 쓰는 실행 기록 저장소입니다. 실행 상태와 step 이력을 메모리(Map)에 둡니다.
 *
 * 운영의 WorkFlowExecutionStore는 PostgreSQL 전용 SQL(jsonb CAST)을 써서 다른 DB로 대신 돌리기 어렵습니다.
 * 그래서 같은 메서드를 메모리로 다시 구현한 하위 클래스를 TestEngineConfig가 @Primary 빈으로 넣습니다.
 * WorkFlowExecutor, WorkFlowExecutionService는 이 저장소인지 모르고 운영과 같은 방식으로 씁니다.
 *
 * 운영 저장소는 context 맵을 JSON 글자로 저장했다가 다시 읽지만, 이 저장소는 객체를 그대로 둡니다.
 * 그래서 "JSON으로 바꿀 수 없는 값을 state에 넣은 경우" 같은 저장 문제는 이 테스트로는 드러나지 않습니다.
 * </pre>
 */
public class InMemoryWorkFlowExecutionStore extends WorkFlowExecutionStore {

	/** executionId → 마지막으로 저장한 실행 상태입니다. */
	private final Map<String, WorkFlowExecution> executions = new ConcurrentHashMap<String, WorkFlowExecution>();

	/** executionId → step 실행 이력(쌓인 순서)입니다. */
	private final Map<String, List<StepHistoryEntry>> histories = new ConcurrentHashMap<String, List<StepHistoryEntry>>();

	@Override
	public void insert(WorkFlowExecution execution) {
		this.executions.put(execution.executionId(), execution);
	}

	@Override
	public void update(WorkFlowExecution execution) {
		this.executions.put(execution.executionId(), execution);
	}

	@Override
	public WorkFlowExecution find(String executionId) {
		WorkFlowExecution execution = this.executions.get(executionId);
		if (execution == null) {
			throw new IllegalArgumentException("존재하지 않는 실행입니다: " + executionId);
		}
		return execution;
	}

	@Override
	public List<WorkFlowExecution> list(String status, String workflowId, String caller, int page, int size) {
		List<WorkFlowExecution> matched = new ArrayList<WorkFlowExecution>();
		for (WorkFlowExecution execution : this.executions.values()) {
			if (status != null && !status.isEmpty() && !status.equals(String.valueOf(execution.status()))) {
				continue;
			}
			if (workflowId != null && !workflowId.isEmpty() && !workflowId.equals(execution.workflowId())) {
				continue;
			}
			if (caller != null && !caller.isEmpty() && !caller.equals(execution.caller())) {
				continue;
			}
			matched.add(execution);
		}
		// 운영과 같이 최근에 시작된 것부터 보여 줍니다.
		Collections.sort(matched, new Comparator<WorkFlowExecution>() {
			@Override
			public int compare(WorkFlowExecution a, WorkFlowExecution b) {
				return b.createdAt().compareTo(a.createdAt());
			}
		});
		int from = Math.min(page * size, matched.size());
		int to = Math.min(from + size, matched.size());
		return new ArrayList<WorkFlowExecution>(matched.subList(from, to));
	}

	@Override
	public void appendHistory(String executionId, StepHistoryEntry entry) {
		List<StepHistoryEntry> history = this.histories.get(executionId);
		if (history == null) {
			history = Collections.synchronizedList(new ArrayList<StepHistoryEntry>());
			List<StepHistoryEntry> existing = this.histories.putIfAbsent(executionId, history);
			if (existing != null) {
				history = existing;
			}
		}
		history.add(entry);
	}

	@Override
	public List<StepHistoryEntry> findHistory(String executionId) {
		List<StepHistoryEntry> history = this.histories.get(executionId);
		if (history == null) {
			return new ArrayList<StepHistoryEntry>();
		}
		synchronized (history) {
			return new ArrayList<StepHistoryEntry>(history);
		}
	}

}

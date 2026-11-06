package net.dstone.ai.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
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

	/** 일시정지를 요청받은 실행의 id입니다. */
	private final Set<String> pauseRequests = ConcurrentHashMap.newKeySet();

	/** executionId → 맡은 인스턴스가 마지막으로 살아 있다고 알린 시각(운영의 HEARTBEAT_AT)입니다. */
	private final Map<String, Instant> heartbeats = new ConcurrentHashMap<String, Instant>();

	@Override
	public synchronized boolean requestPause(String executionId) {
		if (this.find(executionId).status() != WorkFlowExecutionStatus.RUNNING) {
			return false;
		}
		this.pauseRequests.add(executionId);
		return true;
	}

	@Override
	public boolean isPauseRequested(String executionId) {
		return this.pauseRequests.contains(executionId);
	}

	@Override
	public synchronized boolean markQueued(WorkFlowExecution execution, int stepIndex) {
		if (this.find(execution.executionId()).status() != execution.status()) {
			return false;
		}
		this.pauseRequests.remove(execution.executionId());
		this.executions.put(execution.executionId(), execution.queued(stepIndex));
		this.heartbeats.put(execution.executionId(), Instant.now());
		return true;
	}

	@Override
	public synchronized boolean markStarted(String executionId) {
		WorkFlowExecution execution = this.find(executionId);
		if (execution.status() != WorkFlowExecutionStatus.QUEUED) {
			return false;
		}
		this.executions.put(executionId, execution.started());
		this.heartbeats.put(executionId, Instant.now());
		return true;
	}

	@Override
	public synchronized void deleteQueued(String executionId) {
		if (this.executions.containsKey(executionId) && this.find(executionId).status() == WorkFlowExecutionStatus.QUEUED) {
			this.executions.remove(executionId);
			this.heartbeats.remove(executionId);
		}
	}

	@Override
	public synchronized boolean pauseQueued(String executionId) {
		WorkFlowExecution execution = this.find(executionId);
		if (execution.status() != WorkFlowExecutionStatus.QUEUED) {
			return false;
		}
		this.executions.put(executionId, execution.paused(execution.currentStepIndex()));
		this.pauseRequests.remove(executionId);
		return true;
	}

	@Override
	public synchronized int heartbeat() {
		int count = 0;
		for (WorkFlowExecution execution : this.executions.values()) {
			if (this.active(execution)) {
				this.heartbeats.put(execution.executionId(), Instant.now());
				count++;
			}
		}
		return count;
	}

	@Override
	public synchronized int failOrphans(String errorMessage, int staleSeconds) {
		Instant limit = Instant.now().minusSeconds(staleSeconds);
		int count = 0;
		for (WorkFlowExecution execution : new ArrayList<WorkFlowExecution>(this.executions.values())) {
			Instant beat = this.heartbeats.get(execution.executionId());
			if (this.active(execution) && (beat == null || beat.isBefore(limit))) {
				this.executions.put(execution.executionId(), execution.failed(errorMessage));
				this.pauseRequests.remove(execution.executionId());
				count++;
			}
		}
		return count;
	}

	/**
	 * 테스트에서 "맡은 엔진이 사라진" 상황을 만듭니다. 그 실행의 heartbeat를 seconds초 전으로 돌립니다.
	 *
	 * @param executionId 실행 id
	 * @param seconds     몇 초 전으로 돌릴지
	 */
	public void ageHeartbeat(String executionId, long seconds) {
		this.heartbeats.put(executionId, Instant.now().minusSeconds(seconds));
	}

	private boolean active(WorkFlowExecution execution) {
		return execution.status() == WorkFlowExecutionStatus.RUNNING || execution.status() == WorkFlowExecutionStatus.QUEUED;
	}

	@Override
	public List<WorkFlowExecution> search(String status, String workflowId, String executionId, String caller, String requestedBy, int page, int size) {
		List<WorkFlowExecution> matched = this.searchAll(status, workflowId, executionId, caller, requestedBy);
		int from = Math.min(page * size, matched.size());
		int to = Math.min(from + size, matched.size());
		return new ArrayList<WorkFlowExecution>(matched.subList(from, to));
	}

	@Override
	public long count(String status, String workflowId, String executionId, String caller, String requestedBy) {
		return this.searchAll(status, workflowId, executionId, caller, requestedBy).size();
	}

	private List<WorkFlowExecution> searchAll(String status, String workflowId, String executionId, String caller, String requestedBy) {
		List<WorkFlowExecution> matched = new ArrayList<WorkFlowExecution>();
		for (WorkFlowExecution execution : this.list(status, null, caller, 0, Integer.MAX_VALUE)) {
			boolean workflowMatches = workflowId == null || execution.workflowId().toLowerCase().contains(workflowId.toLowerCase());
			boolean executionMatches = executionId == null || execution.executionId().toLowerCase().contains(executionId.toLowerCase());
			boolean requesterMatches = requestedBy == null || requestedBy.isEmpty() || requestedBy.equals(execution.requestedBy());
			if (workflowMatches && executionMatches && requesterMatches) {
				matched.add(execution);
			}
		}
		return matched;
	}

	@Override
	public void insert(WorkFlowExecution execution) {
		this.executions.put(execution.executionId(), execution);
		this.heartbeats.put(execution.executionId(), Instant.now());
	}

	@Override
	public synchronized void update(WorkFlowExecution execution) {
		this.executions.put(execution.executionId(), execution);
		// 운영 SQL과 같이, 실행이 멈추거나 끝나면 요청을 지웁니다.
		if (execution.status() != WorkFlowExecutionStatus.RUNNING) {
			this.pauseRequests.remove(execution.executionId());
		}
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

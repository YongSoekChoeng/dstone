package net.dstone.ai.api.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.WorkFlowDefinition;
import net.dstone.ai.common.registry.WorkFlowRegistry;
import net.dstone.ai.runtime.status.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.WorkFlowExecutor;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.common.biz.BaseService;

/**
 * Workflow 실행(동기/비동기/승인 재개) 전부가 거치는 단일 진입점이다. 세 진입 경로 모두 "실행 상태를 만들거나 불러와서
 * WorkFlowExecutor에 넘긴다"는 같은 흐름을 타고, 그 실행 상태는 WorkFlowExecutionStore(PostgreSQL)에 영속화된다 -
 * 그래서 동기 호출이 APPROVAL에서 멈추든, 비동기 job이 처리 중이든, 같은 방식으로 상태를 조회/재개할 수 있다.
 */
@Service
public class WorkFlowExecutionService extends BaseService {

	@Autowired
	private WorkFlowExecutor workFlowExecutor;
	@Autowired
	private WorkFlowExecutionStore executionStore;
	@Autowired
	private WorkFlowRegistry workFlowRegistry;

	/**
	 * <pre>
	 * 새 실행을 만들어 끝까지(또는 승인 대기까지) 동기로 돌리고 최종 상태를 돌려준다.
	 * </pre>
	 *
	 * @param workflow     실행할 Workflow 정의
	 * @param sessionId    대화 세션 식별자
	 * @param caller       호출 주체 식별자(tenant)
	 * @param variables    Workflow 호출 시 넘겨받은 변수 맵
	 * @param initialInput Workflow 최초 입력값
	 */
	public WorkFlowExecution executeSync(WorkFlowDefinition workflow, String sessionId, String caller, Map<String, Object> variables, String initialInput) {
		WorkFlowExecution execution = this.newExecution(workflow.id(), sessionId, caller, variables, initialInput);
		this.executionStore.insert(execution);
		return this.workFlowExecutor.run(workflow, execution);
	}

	/**
	 * <pre>
	 * 새 실행을 만들어 즉시 executionId를 돌려주고, 실제 실행은 백그라운드에서 진행한다. 진행 상태는
	 * api.controller.WorkFlowExecutionController의 조회 API로 확인한다.
	 * </pre>
	 *
	 * @param workflow     실행할 Workflow 정의
	 * @param sessionId    대화 세션 식별자
	 * @param caller       호출 주체 식별자(tenant)
	 * @param variables    Workflow 호출 시 넘겨받은 변수 맵
	 * @param initialInput Workflow 최초 입력값
	 */
	public String submitAsync(WorkFlowDefinition workflow, String sessionId, String caller, Map<String, Object> variables, String initialInput) {
		WorkFlowExecution execution = this.newExecution(workflow.id(), sessionId, caller, variables, initialInput);
		this.executionStore.insert(execution);
		// 기본 ForkJoinPool.commonPool()을 그대로 쓴다. 전용 스레드풀/큐잉/동시 실행 수 제한은 실제 운영에서 동시 submit이 많아지면 그때 도입한다.
		CompletableFuture.runAsync(() -> this.workFlowExecutor.run(workflow, execution));
		return execution.executionId();
	}

	/**
	 * <pre>
	 * WAITING_APPROVAL 상태의 실행에 사람의 결정을 기록하고, 같은 스텝부터 끝까지(또는 다음 승인 대기까지) 동기로 재개한다.
	 * </pre>
	 *
	 * @param executionId 결정을 내릴 실행 id
	 * @param approved    승인 여부
	 * @param approver    결정한 사람/역할
	 * @param comment     결정 사유/메모
	 */
	public WorkFlowExecution decide(String executionId, boolean approved, String approver, String comment) {
		WorkFlowExecution execution = this.executionStore.find(executionId);
		if (execution.status() != WorkFlowExecutionStatus.WAITING_APPROVAL) {
			throw new IllegalStateException("실행[" + executionId + "]은 지금 승인 대기 상태가 아닙니다(현재 상태: " + execution.status() + ").");
		}
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(execution.workflowId(), execution.caller());
		String pendingStepId = workflow.steps().get(execution.currentStepIndex()).id();
		this.recordDecision(execution, pendingStepId, approved, approver, comment);
		return this.workFlowExecutor.run(workflow, execution);
	}

	/**
	 * executionId 에 해당하는 WorkFlowExecution 조회
	 * @param executionId executionId 조회할 실행 id
	 * @return
	 */
	public WorkFlowExecution find(String executionId) {
		return this.executionStore.find(executionId);
	}

	/**
	 * WorkFlowExecution 목록조회
	 * @param status     상태로 좁히고 싶을 때(없으면 전체)
	 * @param workflowId 특정 workflow로 좁히고 싶을 때(없으면 전체)
	 * @param caller     특정 호출 주체로 좁히고 싶을 때(없으면 전체)
	 * @param page       0부터 시작하는 페이지 번호
	 * @param size       페이지당 개수
	 */
	public List<WorkFlowExecution> list(String status, String workflowId, String caller, int page, int size) {
		return this.executionStore.list(status, workflowId, caller, page, size);
	}

	/**
	 * executionId 에 해당하는 이력을 조회
	 * @param executionId 이력을 조회할 실행 id
	 * @return
	 */
	public List<StepHistoryEntry> history(String executionId) {
		return this.executionStore.findHistory(executionId);
	}

	/**
	 * @param workflowId   새 실행이 속할 Workflow id
	 * @param sessionId    대화 세션 식별자
	 * @param caller       호출 주체 식별자(tenant)
	 * @param variables    Workflow 호출 시 넘겨받은 변수 맵(null이면 빈 Map으로 시작)
	 * @param initialInput Workflow 최초 입력값(첫 스텝의 {previous}가 된다)
	 */
	private WorkFlowExecution newExecution(String workflowId, String sessionId, String caller, Map<String, Object> variables, String initialInput) {
		Map<String, Object> mutableVariables = variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
		mutableVariables.put(Constants.WorkFlow.PREVIOUS_TEXT_VARIABLE_KEY, initialInput);
		return WorkFlowExecution.start(UUID.randomUUID().toString(), workflowId, caller, sessionId, mutableVariables);
	}

	/**
	 * @param execution 결정을 기록할 실행(variables가 그대로 변경됨)
	 * @param stepId    결정을 기록할 APPROVAL 스텝 id
	 * @param approved  승인 여부
	 * @param approver  결정한 사람/역할
	 * @param comment   결정 사유/메모
	 */
	@SuppressWarnings("unchecked")
	private void recordDecision(WorkFlowExecution execution, String stepId, boolean approved, String approver, String comment) {
		Map<String, Object> approvals = (Map<String, Object>) execution.variables().computeIfAbsent(Constants.WorkFlow.APPROVALS_VARIABLE_KEY, key -> new LinkedHashMap<String, Object>());
		Map<String, Object> decision = new LinkedHashMap<>();
		decision.put("approved", approved);
		decision.put("approver", approver);
		decision.put("comment", comment);
		approvals.put(stepId, decision);
	}

}

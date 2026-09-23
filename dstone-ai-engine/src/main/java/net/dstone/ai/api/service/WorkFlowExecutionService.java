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
import net.dstone.ai.runtime.workflow.WorkFlowExecutor;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.common.biz.BaseService;

/**
 * Workflow를 실행하는 세 가지 방법(동기 실행, 비동기 실행, 승인 대기에서 재개)이 모두 거쳐 가는
 * 단일 진입점입니다.
 *
 * 세 경로 모두 결국은 "실행 상태를 새로 만들거나 기존 것을 불러와서 WorkFlowExecutor에 넘긴다"는
 * 같은 흐름을 탑니다. 그 실행 상태는 WorkFlowExecutionStore(PostgreSQL)에 영구히 저장되기 때문에,
 * 동기 호출이 APPROVAL(승인 대기)에서 멈추든 비동기 job이 아직 처리 중이든 상관없이, 똑같은 방식으로
 * 그 상태를 조회하거나 이어서 재개할 수 있습니다.
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
	 * 새 실행을 만들고, 끝날 때까지(또는 승인 대기 상태가 될 때까지) 동기로 쭉 돌린 뒤 최종 상태를
	 * 돌려줍니다.
	 *
	 * @param workflow     실행할 Workflow의 정의입니다.
	 * @param sessionId    대화를 구분하는 세션 식별자입니다.
	 * @param caller       이 Workflow를 호출한 주체를 가리키는 식별자(tenant)입니다.
	 * @param variables    Workflow를 호출할 때 함께 넘겨받은 변수 맵입니다.
	 * @param initialInput Workflow에 처음 넣어줄 입력값입니다.
	 */
	public WorkFlowExecution executeSync(WorkFlowDefinition workflow, String sessionId, String caller, Map<String, Object> variables, String initialInput) {
		WorkFlowExecution execution = this.newExecution(workflow.id(), sessionId, caller, variables, initialInput);
		this.executionStore.insert(execution);
		return this.workFlowExecutor.run(workflow, execution);
	}

	/**
	 * 새 실행을 만들고 executionId를 곧바로 돌려준 뒤, 실제 실행은 백그라운드에서 계속 진행합니다.
	 * 진행 상황은 api.controller.WorkFlowExecutionController가 제공하는 조회 API로 확인하면 됩니다.
	 *
	 * @param workflow     실행할 Workflow의 정의입니다.
	 * @param sessionId    대화를 구분하는 세션 식별자입니다.
	 * @param caller       이 Workflow를 호출한 주체를 가리키는 식별자(tenant)입니다.
	 * @param variables    Workflow를 호출할 때 함께 넘겨받은 변수 맵입니다.
	 * @param initialInput Workflow에 처음 넣어줄 입력값입니다.
	 */
	public String submitAsync(WorkFlowDefinition workflow, String sessionId, String caller, Map<String, Object> variables, String initialInput) {
		WorkFlowExecution execution = this.newExecution(workflow.id(), sessionId, caller, variables, initialInput);
		this.executionStore.insert(execution);
		// 지금은 기본 ForkJoinPool.commonPool()을 그대로 쓰고 있습니다. 전용 스레드풀이나 큐잉,
		// 동시 실행 개수 제한 같은 건 실제 운영 환경에서 동시에 들어오는 submit이 많아지면 그때 도입할 계획입니다.
		CompletableFuture.runAsync(new Runnable() {
			@Override
			public void run() {
				WorkFlowExecutionService.this.workFlowExecutor.run(workflow, execution);
			}
		});
		return execution.executionId();
	}

	/**
	 * WAITING_APPROVAL(승인 대기) 상태로 멈춰 있는 실행에 사람의 결정을 기록하고, 멈췄던 그
	 * 스텝부터 다시 이어서 끝까지(또는 다음 승인 대기가 나올 때까지) 동기로 재개합니다.
	 *
	 * @param executionId 결정을 내릴 실행의 id입니다.
	 * @param approved    승인이면 true, 반려면 false입니다.
	 * @param approver    이 결정을 내린 사람이나 역할입니다.
	 * @param comment     결정한 이유나 메모입니다.
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
	 * executionId로 실행 상태 하나를 조회합니다.
	 *
	 * @param executionId 조회할 실행의 id입니다.
	 */
	public WorkFlowExecution find(String executionId) {
		return this.executionStore.find(executionId);
	}

	/**
	 * 조건에 맞는 실행 목록을 페이지 단위로 조회합니다.
	 *
	 * @param status     이 상태인 것만 보고 싶을 때 지정합니다(비우면 전체를 봅니다).
	 * @param workflowId 특정 Workflow의 실행만 보고 싶을 때 지정합니다(비우면 전체를 봅니다).
	 * @param caller     특정 호출 주체가 실행한 것만 보고 싶을 때 지정합니다(비우면 전체를 봅니다).
	 * @param page       0부터 시작하는 페이지 번호입니다.
	 * @param size       한 페이지에 몇 개씩 담을지 정합니다.
	 */
	public List<WorkFlowExecution> list(String status, String workflowId, String caller, int page, int size) {
		return this.executionStore.list(status, workflowId, caller, page, size);
	}

	/**
	 * executionId에 해당하는 스텝별 실행 이력을 조회합니다.
	 *
	 * @param executionId 이력을 조회할 실행의 id입니다.
	 */
	public List<StepHistoryEntry> history(String executionId) {
		return this.executionStore.findHistory(executionId);
	}

	/**
	 * 새 실행 상태를 만듭니다. initialInput은 첫 스텝이 {previous} 토큰으로 참조할 수 있도록
	 * variables 안에 함께 넣어둡니다.
	 *
	 * @param workflowId   이 새 실행이 속할 Workflow의 id입니다.
	 * @param sessionId    대화를 구분하는 세션 식별자입니다.
	 * @param caller       이 Workflow를 호출한 주체를 가리키는 식별자(tenant)입니다.
	 * @param variables    Workflow를 호출할 때 함께 넘겨받은 변수 맵입니다. null이면 빈 Map으로 시작합니다.
	 * @param initialInput Workflow에 처음 넣어줄 입력값입니다. 첫 스텝의 {previous} 자리에 채워집니다.
	 */
	private WorkFlowExecution newExecution(String workflowId, String sessionId, String caller, Map<String, Object> variables, String initialInput) {
		Map<String, Object> mutableVariables = variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
		mutableVariables.put(Constants.WorkFlow.PREVIOUS_TEXT_VARIABLE_KEY, initialInput);
		return WorkFlowExecution.start(UUID.randomUUID().toString(), workflowId, caller, sessionId, mutableVariables);
	}

	/**
	 * APPROVAL 스텝의 결정 내용을 실행의 variables 안에 기록합니다. execution의 variables를
	 * 직접 수정하는 방식으로 동작합니다.
	 *
	 * @param execution 결정을 기록할 실행입니다(variables가 그대로 바뀝니다).
	 * @param stepId    결정을 기록할 대상 APPROVAL 스텝의 id입니다.
	 * @param approved  승인이면 true, 반려면 false입니다.
	 * @param approver  이 결정을 내린 사람이나 역할입니다.
	 * @param comment   결정한 이유나 메모입니다.
	 */
	@SuppressWarnings("unchecked")
	private void recordDecision(WorkFlowExecution execution, String stepId, boolean approved, String approver, String comment) {
		Map<String, Object> variables = execution.variables();
		Object existingApprovals = variables.get(Constants.WorkFlow.APPROVALS_VARIABLE_KEY);
		Map<String, Object> approvals;
		if (existingApprovals instanceof Map) {
			approvals = (Map<String, Object>) existingApprovals;
		} else {
			approvals = new LinkedHashMap<String, Object>();
			variables.put(Constants.WorkFlow.APPROVALS_VARIABLE_KEY, approvals);
		}
		Map<String, Object> decision = new LinkedHashMap<>();
		decision.put("approved", approved);
		decision.put("approver", approver);
		decision.put("comment", comment);
		approvals.put(stepId, decision);
	}

}

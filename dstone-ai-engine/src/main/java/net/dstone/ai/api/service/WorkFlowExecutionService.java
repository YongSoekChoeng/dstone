package net.dstone.ai.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.FieldDefinition;
import net.dstone.ai.common.definition.WorkFlowDefinition;
import net.dstone.ai.common.registry.WorkFlowRegistry;
import net.dstone.ai.common.schema.FieldTypes;
import net.dstone.ai.runtime.workflow.WorkFlowExecutor;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
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
	 * @param variables    Workflow를 호출할 때 함께 넘겨받은 변수 맵입니다(컨텍스트의 inputs 아래에 들어갑니다).
	 * @param message      Workflow를 호출할 때 넘겨받은 메시지입니다(컨텍스트의 inputs.message에 들어갑니다).
	 */
	public WorkFlowExecution executeSync(WorkFlowDefinition workflow, String sessionId, String caller, Map<String, Object> variables, String message) {
		WorkFlowExecution execution = this.newExecution(workflow.id(), sessionId, caller, variables, message);
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
	 * @param variables    Workflow를 호출할 때 함께 넘겨받은 변수 맵입니다(컨텍스트의 inputs 아래에 들어갑니다).
	 * @param message      Workflow를 호출할 때 넘겨받은 메시지입니다(컨텍스트의 inputs.message에 들어갑니다).
	 */
	public String submitAsync(WorkFlowDefinition workflow, String sessionId, String caller, Map<String, Object> variables, String message) {
		WorkFlowExecution execution = this.newExecution(workflow.id(), sessionId, caller, variables, message);
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
		WorkFlowContext.recordApproval(execution.context(), pendingStepId, approved, approver, comment);
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
	 * Workflow에 inputs(입력 계약)가 선언되어 있으면, 요청에 그 값들이 빠짐없이 올바른 타입으로 들어 있는지
	 * 확인합니다. message는 항상 inputs.message로 들어가므로 inputs에 선언되어 있다면 message 값으로 검사합니다.
	 * 계약에 없는 값이 더 들어 있는 것은 허용합니다.
	 *
	 * @param workflow  실행할 Workflow의 정의입니다.
	 * @param variables 요청의 variables입니다.
	 * @param message   요청의 message입니다.
	 * @throws IllegalArgumentException 빠진 값이나 타입이 다른 값이 있을 때(어떤 값이 왜 문제인지 메시지에 담깁니다)
	 */
	public void checkInputs(WorkFlowDefinition workflow, Map<String, Object> variables, String message) {
		if (workflow.inputs() == null || workflow.inputs().isEmpty()) {
			return;
		}
		List<String> problems = new ArrayList<>();
		for (Map.Entry<String, FieldDefinition> entry : workflow.inputs().entrySet()) {
			String name = entry.getKey();
			Object value = Constants.WorkFlow.Context.MESSAGE.equals(name) ? message : (variables == null ? null : variables.get(name));
			if (value == null) {
				problems.add(name + "(없음)");
			} else if (!FieldTypes.matches(entry.getValue().type(), value)) {
				problems.add(name + "(" + entry.getValue().type() + " 타입이어야 함)");
			}
		}
		if (!problems.isEmpty()) {
			throw new IllegalArgumentException("workflow[" + workflow.id() + "]의 inputs 계약을 지키지 않았습니다: " + problems);
		}
	}

	/**
	 * 새 실행 상태를 만듭니다. 요청의 message와 variables로 새 컨텍스트를 만들어 담습니다(WorkFlowContext.create() 참고).
	 *
	 * @param workflowId 이 새 실행이 속할 Workflow의 id입니다.
	 * @param sessionId  대화를 구분하는 세션 식별자입니다.
	 * @param caller     이 Workflow를 호출한 주체를 가리키는 식별자(tenant)입니다.
	 * @param variables  Workflow를 호출할 때 함께 넘겨받은 변수 맵입니다(없으면 null).
	 * @param message    Workflow를 호출할 때 넘겨받은 메시지입니다.
	 */
	private WorkFlowExecution newExecution(String workflowId, String sessionId, String caller, Map<String, Object> variables, String message) {
		return WorkFlowExecution.start(UUID.randomUUID().toString(), workflowId, caller, sessionId, WorkFlowContext.create(message, variables));
	}

}

package net.dstone.ai.api.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.ai.api.dto.PendingApproval;
import net.dstone.ai.api.dto.ResumePoint;
import net.dstone.ai.api.dto.RewindPoint;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.step.ApprovalStepDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.common.definition.workflow.step.ToolStepDefinition;
import net.dstone.ai.common.registry.WorkFlowRegistry;
import net.dstone.ai.common.schema.JsonSchemaUtil;
import net.dstone.ai.runtime.workflow.WorkFlowExecutor;
import net.dstone.ai.runtime.workflow.WorkFlowThreadPools;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.ai.runtime.workflow.file.FileAccessRule;
import net.dstone.ai.runtime.workflow.file.WorkFlowFileEntry;
import net.dstone.ai.runtime.workflow.file.WorkFlowFileStore;
import net.dstone.common.biz.BaseService;
import net.dstone.common.utils.StringUtil;

/**
 * Workflow를 실행하는 네 가지 방법(동기 실행, 비동기 실행, 승인 대기에서 재개, 일시정지나 실패에서 재개)이 모두 거쳐 가는
 * 단일 진입점입니다.
 *
 * 네 경로 모두 결국은 "실행 상태를 새로 만들거나 기존 것을 불러와서 WorkFlowExecutor에 넘긴다"는
 * 같은 흐름을 탑니다. 그 실행 상태는 WorkFlowExecutionStore(PostgreSQL)에 영구히 저장되기 때문에,
 * 동기 호출이 APPROVAL(승인 대기)에서 멈추든 비동기 job이 아직 처리 중이든 상관없이, 똑같은 방식으로
 * 그 상태를 조회하거나 이어서 재개할 수 있습니다.
 *
 * 모든 경로는 실행을 바로 돌리지 않고 QUEUED로 저장한 뒤 WorkFlowThreadPools의 실행 풀에 맡깁니다.
 * 동시에 도는 실행 수가 정해져 있어서, 여러 사람이 한꺼번에 돌려도 서버가 감당할 만큼만 돌고 나머지는 차례를 기다립니다.
 * 대기열까지 꽉 차면 새 실행은 503(SERVICE_UNAVAILABLE)으로 거절합니다.
 *
 * 승인/재개/되감기는 "상태가 읽어 온 그대로일 때만 QUEUED로 바꾸는" UPDATE(markQueued)로 시작합니다.
 * 같은 실행에 버튼을 두 번 누르거나 두 사람이 동시에 눌러도 한 번만 성공하고, 나머지는 409로 거절됩니다.
 */
@Service
public class WorkFlowExecutionService extends BaseService {

	@Autowired
	private WorkFlowExecutor workFlowExecutor;
	@Autowired
	private WorkFlowExecutionStore executionStore;
	@Autowired
	private WorkFlowRegistry workFlowRegistry;
	@Autowired
	private WorkFlowFileStore fileStore;
	@Autowired
	private WorkFlowThreadPools threadPools;

	/**
	 * 새 실행을 만들고, 끝날 때까지(또는 승인 대기 상태가 될 때까지) 기다린 뒤 최종 상태를 돌려줍니다.
	 * 실행 자체는 실행 풀에서 돌기 때문에, 슬롯이 없으면 차례가 올 때까지 기다립니다.
	 *
	 * @param workflow     실행할 Workflow의 정의입니다.
	 * @param sessionId    대화를 구분하는 세션 식별자입니다.
	 * @param caller       이 Workflow를 호출한 주체를 가리키는 식별자(tenant)입니다.
	 * @param input        Workflow를 호출할 때 넘겨받은 값입니다(컨텍스트의 input에 들어갑니다).
	 */
	public WorkFlowExecution executeSync(WorkFlowDefinition workflow, String sessionId, String caller, Object input) {
		return this.executeSync(workflow, sessionId, caller, input, null);
	}

	/**
	 * executeSync()와 같고, 실행을 시작한 사용자를 함께 남깁니다.
	 *
	 * @param requestedBy 실행을 시작한 사용자입니다(없으면 null).
	 */
	public WorkFlowExecution executeSync(WorkFlowDefinition workflow, String sessionId, String caller, Object input, String requestedBy) {
		this.checkRoom();
		WorkFlowExecution execution = this.newExecution(workflow, sessionId, caller, input, requestedBy);
		this.executionStore.insert(execution);
		Future<WorkFlowExecution> future = this.enqueue(workflow, execution, true);
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("실행[" + execution.executionId() + "]을 기다리다 중단되었습니다.", e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new IllegalStateException(cause.getMessage(), cause);
		}
	}

	/**
	 * 새 실행을 만들고 executionId를 곧바로 돌려준 뒤, 실제 실행은 백그라운드에서 계속 진행합니다.
	 * 진행 상황은 api.controller.WorkFlowExecutionController가 제공하는 조회 API로 확인하면 됩니다.
	 *
	 * @param workflow     실행할 Workflow의 정의입니다.
	 * @param sessionId    대화를 구분하는 세션 식별자입니다.
	 * @param caller       이 Workflow를 호출한 주체를 가리키는 식별자(tenant)입니다.
	 * @param input        Workflow를 호출할 때 넘겨받은 값입니다(컨텍스트의 input에 들어갑니다).
	 */
	public String submitAsync(WorkFlowDefinition workflow, String sessionId, String caller, Object input) {
		return this.submitAsync(workflow, sessionId, caller, input, null);
	}

	/**
	 * submitAsync()와 같고, 실행을 시작한 사용자를 함께 남깁니다.
	 *
	 * @param requestedBy 실행을 시작한 사용자입니다(없으면 null).
	 */
	public String submitAsync(WorkFlowDefinition workflow, String sessionId, String caller, Object input, String requestedBy) {
		this.checkRoom();
		WorkFlowExecution execution = this.newExecution(workflow, sessionId, caller, input, requestedBy);
		this.executionStore.insert(execution);
		this.enqueue(workflow, execution, true);
		return execution.executionId();
	}

	/** 대기열이 꽉 찼으면 실행을 DB에 넣기 전에 503으로 거절합니다. */
	private void checkRoom() {
		if (!this.threadPools.hasRoom()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, this.fullMessage());
		}
	}

	private String fullMessage() {
		return "실행 대기열이 꽉 찼습니다(동시 실행 " + this.threadPools.maxRunning() + "개, 대기 " + this.threadPools.maxQueued() + "개). 잠시 뒤 다시 시도하십시오.";
	}

	/**
	 * <pre>
	 * QUEUED로 저장해 둔 실행을 실행 풀에 맡깁니다.
	 * 슬롯을 받으면 DB의 상태가 아직 QUEUED인지 확인하고(markStarted) RUNNING으로 바꾼 뒤 돌립니다.
	 * 기다리는 사이 일시정지했으면 돌리지 않고 그 상태를 돌려줍니다.
	 * 실행 도중 예상하지 못한 예외가 나면 FAILED로 남깁니다(그냥 두면 heartbeat가 계속 살아 있어 RUNNING으로 남습니다).
	 * 대기열이 그사이 꽉 찼으면 503을 던집니다. 새 실행이면 저장한 행을 지우고(아무 일도 하지 않았으므로),
	 * 멈춰 있던 실행을 다시 돌리려던 것이면 FAILED로 남겨 실행 관리 화면에서 다시 시도할 수 있게 합니다.
	 * </pre>
	 *
	 * @param workflow 돌릴 Workflow의 정의
	 * @param queued   QUEUED로 저장한 실행
	 * @param isNew    방금 만든 새 실행이면 true
	 */
	private Future<WorkFlowExecution> enqueue(final WorkFlowDefinition workflow, final WorkFlowExecution queued, boolean isNew) {
		try {
			return this.threadPools.submit(new Callable<WorkFlowExecution>() {
				@Override
				public WorkFlowExecution call() {
					return WorkFlowExecutionService.this.runQueued(workflow, queued);
				}
			});
		} catch (RejectedExecutionException e) {
			String message = this.fullMessage();
			if (isNew) {
				this.executionStore.deleteQueued(queued.executionId());
			} else {
				this.executionStore.update(queued.failed(message));
			}
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
		}
	}

	/** 실행 풀의 스레드에서 도는 부분입니다(enqueue() 설명 참고). */
	private WorkFlowExecution runQueued(WorkFlowDefinition workflow, WorkFlowExecution queued) {
		if (!this.executionStore.markStarted(queued.executionId())) {
			this.info("실행[" + queued.executionId() + "]은 대기하는 사이 상태가 바뀌어(일시정지 등) 돌리지 않습니다.");
			return this.executionStore.find(queued.executionId());
		}
		WorkFlowExecution started = queued.started();
		try {
			return this.workFlowExecutor.run(workflow, started);
		} catch (RuntimeException e) {
			this.error("실행[" + queued.executionId() + "] 도중 예상하지 못한 오류가 났습니다: " + e.getMessage());
			WorkFlowExecution failed = started.failed("실행 도중 예상하지 못한 오류가 났습니다 - " + e.getMessage());
			this.executionStore.update(failed);
			return failed;
		}
	}

	/**
	 * <pre>
	 * WAITING_APPROVAL(승인 대기) 상태로 멈춰 있는 실행에 사람의 결정을 기록하고, 멈췄던 그
	 * 스텝부터 다시 이어 가도록 대기열에 넣습니다. 끝날 때까지 기다리지 않고 QUEUED 상태를 바로 돌려줍니다
	 * (예전에는 다음 승인 대기나 끝까지 요청 스레드에서 돌려서 몇 분씩 응답이 없었습니다). 진행 상황은 상세 조회로 확인합니다.
	 *
	 * 상태가 WAITING_APPROVAL 그대로일 때만 결정을 받습니다(markQueued). 두 사람이 동시에 결정하거나 버튼을 두 번 눌러도
	 * 실행은 한 번만 이어지고, 늦은 쪽은 409로 거절됩니다.
	 *
	 * 결정(decision)은 멈춰 있는 APPROVAL step의 routes에 있는 이름 중 하나여야 합니다. 아니면 400으로 거절합니다.
	 * 틀린 이름을 기록해 두면 다음 step을 찾지 못해 실행 전체가 실패로 끝나 버리기 때문에, 기록하기 전에 막습니다.
	 * decision을 보내지 않고 approved만 보내면 true는 APPROVED, false는 REJECTED로 봅니다.
	 * step에 approval.requireCommentOnReject: true를 적었으면 APPROVED가 아닌 결정에는 comment가 있어야 합니다.
	 * </pre>
	 *
	 * @param executionId 결정을 내릴 실행의 id입니다.
	 * @param decision    고른 결정 이름입니다(없으면 null).
	 * @param approved    decision이 없을 때만 봅니다. true면 APPROVED, false면 REJECTED입니다(없으면 null).
	 * @param approver    이 결정을 내린 사람이나 역할입니다.
	 * @param comment     결정한 이유나 메모입니다.
	 */
	public WorkFlowExecution decide(String executionId, String decision, Boolean approved, String approver, String comment) {
		WorkFlowExecution execution = this.executionStore.find(executionId);
		if (execution.status() != WorkFlowExecutionStatus.WAITING_APPROVAL) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "실행[" + executionId + "]은 지금 승인 대기 상태가 아닙니다(현재 상태: " + execution.status() + "). 다른 사람이 먼저 결정했을 수 있습니다.");
		}
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(execution.workflowId(), execution.caller());
		StepDefinition pendingStep = workflow.steps().get(execution.currentStepIndex());
		if (!(pendingStep instanceof ApprovalStepDefinition approval)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "실행[" + executionId + "]이 멈춰 있는 step[" + pendingStep.id() + "]은 APPROVAL step이 아닙니다(실행을 시작한 뒤 Workflow 정의가 바뀌었을 수 있습니다).");
		}
		String chosen = decision;
		if (StringUtil.isEmpty(chosen) && approved != null) {
			chosen = approved.booleanValue() ? ApprovalStepDefinition.DECISION_APPROVED : ApprovalStepDefinition.DECISION_REJECTED;
		}
		Map<String, String> routes = approval.decisionRoutes();
		if (StringUtil.isEmpty(chosen) || !routes.containsKey(chosen)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "step[" + pendingStep.id() + "]은 다음 중 하나를 골라야 합니다. decision에 보내십시오: " + routes.keySet()
				+ (StringUtil.isEmpty(chosen) ? "" : " (보낸 값: " + chosen + ")"));
		}
		if (approval.requiresCommentOnReject() && !ApprovalStepDefinition.DECISION_APPROVED.equals(chosen) && StringUtil.isEmpty(comment)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "step[" + pendingStep.id() + "]은 " + ApprovalStepDefinition.DECISION_APPROVED + "가 아닌 결정에 의견(comment)이 꼭 있어야 합니다.");
		}
		this.checkRoom();
		if (!this.executionStore.markQueued(execution, execution.currentStepIndex())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "실행[" + executionId + "]의 상태가 그사이 바뀌었습니다. 다른 사람이 먼저 결정했을 수 있습니다. 다시 조회하십시오.");
		}
		WorkFlowContext.recordApproval(execution.context(), pendingStep.id(), chosen, approver, comment);
		WorkFlowExecution queued = execution.queued(execution.currentStepIndex());
		// 결정을 컨텍스트에 남긴 채로 저장해 둡니다. 차례를 기다리는 사이 서버가 내려가도 결정은 남습니다.
		this.executionStore.update(queued);
		this.enqueue(workflow, queued, false);
		return queued;
	}

	/**
	 * <pre>
	 * 돌고 있는 실행에 일시정지를 요청합니다. 지금 돌고 있는 step은 끝까지 기다리고, 다음 step을 시작하기 전에 PAUSED로 멈춥니다.
	 * 그래서 돌려주는 상태는 대개 아직 RUNNING입니다. 멈췄는지는 상세 조회로 확인합니다.
	 * 아직 대기열에서 차례를 기다리는 실행(QUEUED)은 바로 PAUSED가 되고, 차례가 와도 돌지 않습니다.
	 * </pre>
	 *
	 * @param executionId 멈출 실행의 id입니다.
	 * @throws ResponseStatusException 실행이 RUNNING이나 QUEUED가 아닐 때(409)
	 */
	public WorkFlowExecution pause(String executionId) {
		if (!this.executionStore.pauseQueued(executionId) && !this.executionStore.requestPause(executionId)) {
			WorkFlowExecution execution = this.executionStore.find(executionId);
			throw new ResponseStatusException(HttpStatus.CONFLICT, "실행 중이거나 대기 중일 때만 일시정지할 수 있습니다(현재 상태: " + execution.status() + ").");
		}
		return this.executionStore.find(executionId);
	}

	/**
	 * <pre>
	 * 일시정지했거나 실패한 실행을 멈춘 step부터 이어 갑니다. 현재 등록된 정의로 돕니다.
	 * 남은 step이 오래 걸릴 수 있어 끝날 때까지 기다리지 않습니다. 진행 상황은 상세 조회로 확인합니다.
	 * </pre>
	 *
	 * @param executionId 이어 갈 실행의 id입니다.
	 * @return QUEUED(대기열에 들어감)로 바뀐 실행 상태입니다.
	 * @throws ResponseStatusException 이어 갈 수 없는 상태이거나 재개할 step을 찾지 못했을 때(409)
	 */
	public WorkFlowExecution resume(String executionId) {
		WorkFlowExecution execution = this.executionStore.find(executionId);
		ResumePoint point = this.resumePoint(execution);
		if (point == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "일시정지했거나 실패한 실행만 이어 갈 수 있습니다(현재 상태: " + execution.status() + ").");
		}
		if (!point.resumable()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, point.problem());
		}
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(execution.workflowId(), execution.caller());
		this.checkRoom();
		if (!this.executionStore.markQueued(execution, point.stepIndex())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "실행[" + executionId + "]의 상태가 그사이 바뀌었습니다. 다시 조회하십시오.");
		}
		WorkFlowExecution queued = execution.queued(point.stepIndex());
		this.enqueue(workflow, queued, false);
		return queued;
	}

	/**
	 * <pre>
	 * 실행을 이어 가면 어느 step부터 도는지 알려 줍니다. 일시정지나 실패 상태가 아니면 null입니다.
	 * step은 순번이 아니라 id로 찾습니다. 멈춘 사이 step을 끼워 넣어도 엉뚱한 step이 돌지 않게 하기 위해서입니다.
	 * 아래 두 경우는 막지 않고 warnings로 알립니다.
	 * - 정의 버전이 시작할 때와 다를 때: step id를 다시 매겼다면 같은 id가 다른 일을 할 수 있습니다.
	 * - 실패한 TOOL이나 forEach step을 다시 돌릴 때: 앞선 시도에서 일부가 이미 끝났을 수 있습니다.
	 * </pre>
	 *
	 * @param execution 확인할 실행입니다.
	 */
	public ResumePoint resumePoint(WorkFlowExecution execution) {
		if (execution.status() != WorkFlowExecutionStatus.PAUSED && execution.status() != WorkFlowExecutionStatus.FAILED) {
			return null;
		}
		WorkFlowDefinition workflow;
		try {
			workflow = this.workFlowRegistry.resolve(execution.workflowId(), execution.caller());
		} catch (RuntimeException e) {
			return ResumePoint.blocked("Workflow[" + execution.workflowId() + "] 정의를 찾지 못해 이어 갈 수 없습니다: " + e.getMessage());
		}
		List<StepDefinition> steps = workflow.steps();
		List<String> warnings = new ArrayList<>();
		String savedStepId = WorkFlowContext.currentStep(execution.context());
		int index = execution.currentStepIndex();
		if (savedStepId != null) {
			index = -1;
			for (int i = 0; i < steps.size(); i++) {
				if (steps.get(i).id().equals(savedStepId)) {
					index = i;
				}
			}
			if (index < 0) {
				return ResumePoint.blocked("멈췄던 step[" + savedStepId + "]이 지금 정의에 없어 이어 갈 수 없습니다.");
			}
		} else if (index < 0 || index >= steps.size()) {
			return ResumePoint.blocked("멈췄던 " + (index + 1) + "번째 step이 지금 정의에 없어 이어 갈 수 없습니다.");
		} else {
			warnings.add("step id 기록이 없는 예전 실행이라 순번으로 찾았습니다. 정의가 바뀌었다면 다른 step일 수 있습니다.");
		}
		StepDefinition step = steps.get(index);
		String startedVersion = WorkFlowContext.definitionVersion(execution.context());
		if (startedVersion != null && !startedVersion.equals(workflow.version())) {
			warnings.add("정의 버전이 시작할 때(" + startedVersion + ")와 다릅니다(지금 " + workflow.version() + "). 지금 정의로 이어 가므로, step id를 다시 매겼다면 다른 일을 하는 step일 수 있습니다.");
		}
		if (execution.status() == WorkFlowExecutionStatus.FAILED && (step instanceof ToolStepDefinition || StepDefinition.forEachOf(step) != null)) {
			warnings.add("이 step은 앞선 시도에서 일부가 이미 끝났을 수 있습니다. 파일을 고치는 일이면 다시 실패하거나 같은 일을 두 번 할 수 있습니다.");
		}
		return new ResumePoint(step.id(), step.type().name(), index, true, null, warnings);
	}

	/**
	 * <pre>
	 * 실행을 지난 step으로 되돌려 그 step부터 다시 돌립니다. 현재 등록된 정의로 돕니다.
	 * 실행 기록에 있는 step만 고를 수 있고, 돌고 있는 실행은 되돌릴 수 없습니다.
	 *
	 * clearState가 true면 그 step이 마지막으로 돈 자리부터 뒤에서 저장한 state 이름을 지우고 시작합니다.
	 * false면 그대로 두는데, 다시 돌 때 경로가 달라지면 예전 값을 읽을 수 있습니다(rewindPoint()의 warnings).
	 * 어느 쪽이든 그 구간의 아직 쓰지 않은 승인 결정은 지웁니다.
	 * 그 구간의 step이 새로 만든 파일(산출물)은 실제로 지우고, 비게 된 폴더도 지웁니다(rewindPoint()의 deleteFiles).
	 * 원래 있던 파일을 고친 것은 지우지도 되돌리지도 않습니다(keptFiles). 그 구간의 step 파일 기록도 지웁니다.
	 * 실행 기록(step history)은 지우지 않고, 다시 돈 step이 그 뒤에 이어 붙습니다.
	 * resume()처럼 끝날 때까지 기다리지 않습니다.
	 * </pre>
	 *
	 * @param executionId 되돌릴 실행의 id입니다.
	 * @param fromStep    다시 시작할 step의 id입니다.
	 * @param clearState  true면 그 구간에서 저장한 state 값을 지웁니다.
	 * @return QUEUED(대기열에 들어감)로 바뀐 실행 상태입니다.
	 * @throws ResponseStatusException 되돌릴 수 없을 때(409), fromStep이 비었을 때(400)
	 */
	public WorkFlowExecution rewind(String executionId, String fromStep, boolean clearState) {
		if (StringUtil.isEmpty(fromStep)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "다시 시작할 step(fromStep)을 보내십시오.");
		}
		WorkFlowExecution execution = this.executionStore.find(executionId);
		RewindPoint point = this.rewindPoint(execution, fromStep);
		if (!point.rewindable()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, point.problem());
		}
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(execution.workflowId(), execution.caller());
		this.checkRoom();
		if (!this.executionStore.markQueued(execution, point.stepIndex())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "실행[" + executionId + "]의 상태가 그사이 바뀌었습니다. 다시 조회하십시오.");
		}
		Map<String, Object> context = execution.context();
		if (clearState) {
			WorkFlowContext.removeState(context, point.savedNames());
		}
		for (String stepId : point.steps()) {
			WorkFlowContext.clearApproval(context, stepId);
		}
		WorkFlowContext.setCurrentStep(context, point.fromStep());
		List<String> deleted = this.deleteFiles(executionId, point.deleteFiles());
		this.fileStore.deleteSteps(executionId, point.steps());
		WorkFlowExecution rewound = execution.rewound(point.stepIndex());
		this.executionStore.update(rewound);
		this.info("workflow[" + workflow.id() + "] 실행[" + executionId + "]을 step[" + point.fromStep() + "]으로 되돌려 다시 돌립니다"
			+ (clearState ? "(지운 state: " + point.savedNames() + ")" : "(state는 그대로 둡니다)")
			+ " - 지운 파일 " + deleted.size() + "개: " + deleted);
		this.enqueue(workflow, rewound, false);
		return rewound;
	}

	/**
	 * <pre>
	 * 실행을 fromStep으로 되돌리면 무엇이 어떻게 되는지 알려 줍니다. 실제로 바꾸지는 않습니다.
	 *
	 * 되돌리는 구간은 fromStep이 실행 기록에서 마지막으로 돈 자리부터 기록 끝까지입니다.
	 * (forEach step은 "step17[0]", "step17[1]"처럼 여러 줄이라, 마지막으로 이어진 줄 묶음의 첫 줄부터입니다.)
	 * 막는 경우: 실행 중이거나 대기 중, 기록에 없는 step, 지금 정의에 없는 step.
	 * 알리는 경우: 그 구간에서 저장한 state가 남아 있음, 그 구간의 step이 쓴 파일, 정의 버전이 다름,
	 *             구간의 step 중 지금 정의에 없는 것(그 step이 저장한 이름은 알 수 없음).
	 * </pre>
	 *
	 * @param execution 확인할 실행입니다.
	 * @param fromStep  다시 시작할 step의 id입니다.
	 */
	public RewindPoint rewindPoint(WorkFlowExecution execution, String fromStep) {
		if (execution.status() == WorkFlowExecutionStatus.RUNNING || execution.status() == WorkFlowExecutionStatus.QUEUED) {
			return RewindPoint.blocked(fromStep, "돌고 있는 실행이나 차례를 기다리는 실행은 되돌릴 수 없습니다. 먼저 일시정지하십시오.");
		}
		WorkFlowDefinition workflow;
		try {
			workflow = this.workFlowRegistry.resolve(execution.workflowId(), execution.caller());
		} catch (RuntimeException e) {
			return RewindPoint.blocked(fromStep, "Workflow[" + execution.workflowId() + "] 정의를 찾지 못해 되돌릴 수 없습니다: " + e.getMessage());
		}

		// 1) 실행 기록에서 fromStep이 마지막으로 돈 자리를 찾습니다.
		List<StepHistoryEntry> history = this.executionStore.findHistory(execution.executionId());
		int start = -1;
		for (int i = history.size() - 1; i >= 0; i--) {
			if (fromStep.equals(baseStepId(history.get(i).stepId()))) {
				start = i;
				break;
			}
		}
		if (start < 0) {
			return RewindPoint.blocked(fromStep, "step[" + fromStep + "]은 이 실행의 기록에 없습니다. 이미 돈 step만 고를 수 있습니다.");
		}
		while (start > 0 && fromStep.equals(baseStepId(history.get(start - 1).stepId()))) {
			start--;
		}

		// 2) 지금 정의에서 그 step을 찾습니다.
		List<StepDefinition> definitionSteps = workflow.steps();
		int index = -1;
		for (int i = 0; i < definitionSteps.size(); i++) {
			if (definitionSteps.get(i).id().equals(fromStep)) {
				index = i;
			}
		}
		if (index < 0) {
			return RewindPoint.blocked(fromStep, "step[" + fromStep + "]이 지금 정의에 없어 되돌릴 수 없습니다.");
		}

		// 3) 그 자리부터 기록 끝까지 돈 step들과, 그 step들이 저장해 지금 state에 남아 있는 이름을 모읍니다.
		List<String> warnings = new ArrayList<>();
		List<String> rangeSteps = new ArrayList<>();
		for (int i = start; i < history.size(); i++) {
			String stepId = baseStepId(history.get(i).stepId());
			if (!rangeSteps.contains(stepId)) {
				rangeSteps.add(stepId);
			}
		}
		// WAITING_APPROVAL이면 기다리는 APPROVAL step은 아직 기록이 없지만, 구간에 들어갑니다.
		String currentStepId = WorkFlowContext.currentStep(execution.context());
		if (currentStepId != null && execution.status() == WorkFlowExecutionStatus.WAITING_APPROVAL && !rangeSteps.contains(currentStepId)) {
			rangeSteps.add(currentStepId);
		}
		Map<String, Object> state = WorkFlowContext.state(execution.context());
		List<String> savedNames = new ArrayList<>();
		List<String> unknownSteps = new ArrayList<>();
		for (String stepId : rangeSteps) {
			StepDefinition step = this.stepOf(definitionSteps, stepId);
			if (step == null) {
				unknownSteps.add(stepId);
				continue;
			}
			for (String name : step.output().values()) {
				if (state.containsKey(name) && !savedNames.contains(name)) {
					savedNames.add(name);
				}
			}
		}
		if (!savedNames.isEmpty()) {
			warnings.add("step[" + fromStep + "]부터 뒤에서 저장한 값이 state에 남아 있습니다: " + String.join(", ", savedNames)
				+ ". 지우지 않으면 다시 돌 때 경로가 달라질 경우 예전 값을 읽을 수 있습니다.");
		}
		if (!unknownSteps.isEmpty()) {
			warnings.add("step " + unknownSteps + "은 지금 정의에 없어, 그 step이 저장한 state 이름은 알 수 없습니다(지우지 못합니다).");
		}

		// 4) 그 구간의 step이 쓴 파일. 새로 만든 파일은 지우고, 원래 있던 파일을 고친 것은 그대로 둡니다.
		List<String> deleteFiles = new ArrayList<>();
		List<String> keptFiles = new ArrayList<>();
		List<WorkFlowFileEntry> fileEntries = this.fileStore.list(execution.executionId());
		for (WorkFlowFileEntry entry : fileEntries) {
			if (!FileAccessRule.WRITE.equals(entry.action()) || !rangeSteps.contains(entry.stepId())) {
				continue;
			}
			if (createdByRun(entry, fileEntries)) {
				if (!deleteFiles.contains(entry.path())) {
					deleteFiles.add(entry.path());
				}
				keptFiles.remove(entry.path());
			} else if (!deleteFiles.contains(entry.path()) && !keptFiles.contains(entry.path())) {
				keptFiles.add(entry.path());
			}
		}
		if (!deleteFiles.isEmpty()) {
			warnings.add("이 구간의 step이 새로 만든 파일 " + deleteFiles.size() + "개를 지웁니다(파일이 지워진 뒤 빈 폴더도 지웁니다).");
		}
		if (!keptFiles.isEmpty()) {
			warnings.add("이 구간의 step이 쓴 파일 " + keptFiles.size() + "개는 지우지 않습니다. 원래 있던 파일을 고친 것이거나, "
				+ "기록이 오래되어 새로 만든 것인지 알 수 없는 파일입니다. 원래대로 돌아가지도 않으므로, 고친 파일을 다시 고치는 step은 원문을 찾지 못해 실패할 수 있습니다.");
		}

		String startedVersion = WorkFlowContext.definitionVersion(execution.context());
		if (startedVersion != null && !startedVersion.equals(workflow.version())) {
			warnings.add("정의 버전이 시작할 때(" + startedVersion + ")와 다릅니다(지금 " + workflow.version() + "). 지금 정의로 다시 돕니다.");
		}
		StepDefinition step = definitionSteps.get(index);
		return new RewindPoint(fromStep, step.type().name(), index, true, null, rangeSteps, savedNames, deleteFiles, keptFiles, warnings);
	}

	/**
	 * <pre>
	 * 이 실행의 step이 새로 만든 파일의 기록인지 봅니다.
	 * CREATED 열이 생기기 전의 기록(null)은 만들었는지 알 수 없어서, md 문서이고 이 실행에서 그 파일을 처음 건드린 것이
	 * 이 쓰기 기록일 때만 만든 것으로 봅니다. 소스 파일이나 먼저 읽은 문서는 원래 있던 파일일 수 있어 지우지 않습니다.
	 * </pre>
	 *
	 * @param entry   확인할 WRITE 기록
	 * @param entries 이 실행의 파일 기록 전부
	 */
	private static boolean createdByRun(WorkFlowFileEntry entry, List<WorkFlowFileEntry> entries) {
		if (entry.created() != null) {
			return entry.created().booleanValue();
		}
		if (!entry.path().toLowerCase().endsWith(".md")) {
			return false;
		}
		for (WorkFlowFileEntry other : entries) {
			if (other != entry && other.path().equals(entry.path()) && other.firstAt() != null && entry.firstAt() != null
				&& other.firstAt().isBefore(entry.firstAt())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * <pre>
	 * 재실행할 때 구간의 step이 새로 만든 파일을 지웁니다. 파일을 지운 뒤 비게 된 폴더도 위로 올라가며 지웁니다
	 * (재실행 때마다 새 폴더를 만드는 Workflow가 빈 폴더를 남기지 않게). 비어 있지 않은 폴더를 만나면 멈춥니다.
	 * 하나를 못 지워도 나머지는 계속 지우고, 경고 로그만 남깁니다. 지운 파일 목록을 돌려줍니다.
	 * </pre>
	 */
	private List<String> deleteFiles(String executionId, List<String> paths) {
		List<String> deleted = new ArrayList<>();
		for (String path : paths) {
			File file = new File(path);
			if (!file.isFile()) {
				continue;
			}
			if (!file.delete()) {
				this.warn("실행[" + executionId + "] 재실행: 파일을 지우지 못했습니다 - " + path);
				continue;
			}
			deleted.add(path);
			File dir = file.getParentFile();
			while (dir != null && dir.isDirectory()) {
				String[] children = dir.list();
				if (children == null || children.length > 0 || !dir.delete()) {
					break;
				}
				dir = dir.getParentFile();
			}
		}
		return deleted;
	}

	/** id로 step 정의를 찾습니다. 없으면 null입니다. */
	private StepDefinition stepOf(List<StepDefinition> steps, String stepId) {
		for (StepDefinition step : steps) {
			if (step.id().equals(stepId)) {
				return step;
			}
		}
		return null;
	}

	/** forEach step의 기록은 "step17[0]"처럼 순번이 붙습니다. 순번을 떼어 step id만 돌려줍니다. */
	private static String baseStepId(String historyStepId) {
		if (historyStepId == null) {
			return "";
		}
		int bracket = historyStepId.lastIndexOf('[');
		return bracket > 0 && historyStepId.endsWith("]") ? historyStepId.substring(0, bracket) : historyStepId;
	}

	/** 일시정지 요청이 들어와 있는지 돌려줍니다. @param executionId 확인할 실행의 id입니다. */
	public boolean isPauseRequested(String executionId) {
		return this.executionStore.isPauseRequested(executionId);
	}

	/**
	 * 실행이 승인 대기(WAITING_APPROVAL)로 멈춰 있으면, 지금 어떤 결정을 기다리는지 돌려줍니다. 승인 대기가 아니면 null입니다.
	 * 승인 화면이 이 값을 보고 고를 수 있는 결정마다 버튼을 하나씩 보여 줍니다.
	 *
	 * @param execution 확인할 실행입니다.
	 */
	public PendingApproval pendingApproval(WorkFlowExecution execution) {
		if (execution.status() != WorkFlowExecutionStatus.WAITING_APPROVAL) {
			return null;
		}
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(execution.workflowId(), execution.caller());
		StepDefinition pendingStep = workflow.steps().get(execution.currentStepIndex());
		if (!(pendingStep instanceof ApprovalStepDefinition approval)) {
			return null;
		}
		return new PendingApproval(approval.id(), approval.approverRole(), new ArrayList<>(approval.decisionRoutes().keySet()), approval.artifact());
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
	 * 관리 화면의 검색입니다. workflowId와 executionId는 일부만 적어도 찾습니다.
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
		return this.executionStore.search(status, workflowId, executionId, caller, requestedBy, page, size);
	}

	/** search()와 같은 조건에 맞는 실행이 모두 몇 건인지 돌려줍니다. */
	public long count(String status, String workflowId, String executionId, String caller, String requestedBy) {
		return this.executionStore.count(status, workflowId, executionId, caller, requestedBy);
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
	 * 요청의 input이 Workflow의 input 계약(workflow.input, 비워두면 string) 모양인지 확인합니다.
	 *
	 * @param workflow 실행할 Workflow의 정의입니다.
	 * @param input    요청의 input입니다.
	 * @throws IllegalArgumentException input이 없거나 모양이 다를 때(어디가 왜 문제인지 메시지에 담깁니다)
	 */
	public void checkInput(WorkFlowDefinition workflow, Object input) {
		if (input == null || (input instanceof String text && StringUtil.isEmpty(text))) {
			throw new IllegalArgumentException("input은 필수입니다(workflow[" + workflow.id() + "]의 input 모양 = " + workflow.inputSchema() + ").");
		}
		List<String> problems = JsonSchemaUtil.validate(workflow.inputSchema(), input);
		if (!problems.isEmpty()) {
			throw new IllegalArgumentException("workflow[" + workflow.id() + "]의 input 모양이 맞지 않습니다: " + problems);
		}
	}

	/**
	 * 대기열에 넣을 새 실행 상태(QUEUED)를 만듭니다. 요청의 input으로 새 컨텍스트를 만들어 담습니다(WorkFlowContext.create() 참고).
	 * 첫 step id도 미리 적어 둡니다. 차례를 기다리다 일시정지한 실행을 재개할 때 어디서 시작할지 id로 찾게 하려는 것입니다.
	 *
	 * @param workflow    이 새 실행이 속할 Workflow의 정의입니다.
	 * @param sessionId   대화를 구분하는 세션 식별자입니다.
	 * @param caller      이 Workflow를 호출한 주체를 가리키는 식별자(tenant)입니다.
	 * @param input       Workflow를 호출할 때 넘겨받은 값입니다.
	 * @param requestedBy 실행을 시작한 사용자입니다(없으면 null).
	 */
	private WorkFlowExecution newExecution(WorkFlowDefinition workflow, String sessionId, String caller, Object input, String requestedBy) {
		Map<String, Object> context = WorkFlowContext.create(input, workflow.id(), workflow.version());
		if (!workflow.steps().isEmpty()) {
			WorkFlowContext.setCurrentStep(context, workflow.steps().get(0).id());
		}
		return WorkFlowExecution.queue(UUID.randomUUID().toString(), workflow.id(), caller, sessionId, context, requestedBy);
	}

}

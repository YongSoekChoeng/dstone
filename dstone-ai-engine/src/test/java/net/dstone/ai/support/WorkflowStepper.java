package net.dstone.ai.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.runtime.workflow.WorkFlowExecutor;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;

/**
 * <pre>
 * Workflow를 step 하나씩 끊어서 실행하는 테스트 도구입니다. WorkflowTestBase.stepper(id, input)로 만듭니다.
 *
 *   WorkflowStepper flow = this.stepper("sm-workflow", 요청서폴더);
 *   flow.step("step00_01_readReq");          // 이 step 하나만 실행하고 멈춤
 *   assertThat(flow.state("request"))...      // 그 step이 save한 값 확인
 *   flow.step("step00_02_decideJobKind");
 *   // flow.step("step01_...");               // 여기를 주석으로 막으면 앞 step까지만 실행
 *
 * - step(id)는 엔진의 WorkFlowExecutor.run(workflow, execution, 1)을 부릅니다. 표현식 계산, state 저장, 다음 step 정하기가
 *   한 번에 끝까지 돌 때와 똑같습니다. 다른 점은 step 하나를 마치면 다음 step을 가리킨 채 멈춘다는 것뿐입니다.
 * - step(id)에 적은 id가 "지금 실행할 차례인 step"과 다르면 실행하지 않고 AssertionError를 던집니다.
 *   흐름이 예상과 다르게 갈라졌을 때(onFailure, routes) 바로 드러납니다.
 * - 중간 step부터 시작하고 싶으면 put(이름, 값)으로 앞 step이 저장했을 state를 넣고 jumpTo(id)로 옮긴 뒤 step(id)를 부릅니다.
 * - APPROVAL step은 step(id)를 부르면 WAITING_APPROVAL로 멈춥니다. decide(결정, 의견)로 결정을 넣으면 그 step을 이어서 실행합니다.
 *
 * step 하나만 따로 떼어 확인하기(앞 step 없이)
 *   WorkflowStepper step1 = this.stepper("sm-workflow", "step00_01_readReq", 그 step에 넣을 값);
 *   step1.step("step00_01_readReq");          // with를 계산하지 않고 받은 값으로 실행
 * 넣을 값은 그 step의 with를 계산한 결과와 같은 모양입니다(TOOL은 인자 맵, AGENT류는 Agent input 모양의 값).
 * step(id, 값)으로 실행 중간의 step 하나에만 값을 넣을 수도 있습니다.
 * </pre>
 */
public class WorkflowStepper {

	private final WorkFlowDefinition workflow;
	private final WorkFlowExecutor executor;
	private final WorkFlowExecutionStore store;

	/** 지금 실행 상태입니다. step을 실행할 때마다 새 값으로 바뀝니다(context 맵은 같은 것을 계속 씁니다). */
	private WorkFlowExecution execution;

	/** 다음 step(...)에 with 대신 넣을 값이 있는지, 그 값이 무엇인지입니다(stepper(id, stepId, 값)으로 만든 경우). */
	private boolean hasNextInput;
	private Object nextInput;

	/**
	 * 새 실행을 만듭니다. 아직 아무 step도 실행하지 않은 상태(첫 step을 가리킴)입니다.
	 *
	 * @param workflow 실행할 Workflow 정의
	 * @param executor 엔진의 Workflow 실행기
	 * @param store    실행 상태 저장소(테스트에서는 메모리 저장소)
	 * @param input    요청 input
	 */
	WorkflowStepper(WorkFlowDefinition workflow, WorkFlowExecutor executor, WorkFlowExecutionStore store, Object input) {
		this.workflow = workflow;
		this.executor = executor;
		this.store = store;
		this.execution = WorkFlowExecution.start(UUID.randomUUID().toString(), workflow.id(), null, "test-" + workflow.id(),
			WorkFlowContext.create(input, workflow.id(), workflow.version()));
		this.store.insert(this.execution);
	}

	/**
	 * <pre>
	 * 지금 차례인 step 하나를 실행하고 멈춥니다. 차례인 step이 stepId가 아니면 실행하지 않고 AssertionError를 던집니다.
	 * 실행 뒤의 상태는 status()로 봅니다. 다음 step이 있으면 RUNNING, Workflow가 끝났으면 DONE / FAILED,
	 * APPROVAL이면 WAITING_APPROVAL입니다.
	 * </pre>
	 *
	 * @param stepId 실행할 step의 id
	 */
	public WorkflowStepper step(String stepId) {
		if (this.hasNextInput) {
			Object input = this.nextInput;
			this.hasNextInput = false;
			this.nextInput = null;
			return this.step(stepId, input);
		}
		this.checkTurn(stepId);
		this.execution = this.executor.run(this.workflow, this.execution, 1);
		return this;
	}

	/**
	 * <pre>
	 * 지금 차례인 step 하나를, with를 계산하지 않고 input으로 실행하고 멈춥니다.
	 * input은 그 step의 with를 계산한 결과와 같은 모양이어야 합니다(TOOL은 인자 맵, AGENT류는 Agent input 모양의 값).
	 * forEach step에는 쓸 수 없습니다.
	 * </pre>
	 *
	 * @param stepId 실행할 step의 id
	 * @param input  그 step에 넣을 값
	 */
	public WorkflowStepper step(String stepId, Object input) {
		this.checkTurn(stepId);
		this.execution = this.executor.runStepWith(this.workflow, this.execution, input);
		return this;
	}

	/**
	 * 다음 step(id) 한 번에 with 대신 넣을 값을 정해 둡니다. WorkflowTestBase.stepper(id, stepId, 값)이 씁니다.
	 *
	 * @param input 그 step에 넣을 값
	 */
	WorkflowStepper nextInput(Object input) {
		this.hasNextInput = true;
		this.nextInput = input;
		return this;
	}

	/**
	 * <pre>
	 * 승인 대기(WAITING_APPROVAL)인 APPROVAL step에 사람의 결정을 넣고, 그 step을 이어서 실행합니다.
	 * 결정 이름이 그 step의 routes에 있는지는 보지 않습니다(API의 400 검사는 WorkflowTestBase.decide()로 확인합니다).
	 * </pre>
	 *
	 * @param decision 고른 결정 이름(그 step의 routes 키)
	 * @param comment  의견(없으면 null)
	 */
	public WorkflowStepper decide(String decision, String comment) {
		if (this.execution.status() != WorkFlowExecutionStatus.WAITING_APPROVAL) {
			throw new AssertionError("승인 대기 상태가 아닙니다(지금 상태: " + this.execution.status() + ", 다음 step: " + this.currentStepId() + ")");
		}
		WorkFlowContext.recordApproval(this.execution.context(), this.currentStepId(), decision, "tester", comment);
		this.execution = this.executor.run(this.workflow, this.execution, 1);
		return this;
	}

	/**
	 * <pre>
	 * 다음에 실행할 step을 stepId로 옮깁니다. 앞 step을 건너뛰고 중간부터 확인할 때 씁니다.
	 * 건너뛴 step이 저장했을 값은 put(이름, 값)으로 먼저 넣어 둡니다.
	 * </pre>
	 *
	 * @param stepId 다음에 실행할 step의 id
	 */
	public WorkflowStepper jumpTo(String stepId) {
		int index = this.indexOf(stepId);
		if (index < 0) {
			throw new IllegalArgumentException("workflow[" + this.workflow.id() + "]에 step[" + stepId + "]이 없습니다.");
		}
		this.execution = this.execution.advanceTo(index);
		this.store.update(this.execution);
		return this;
	}

	/**
	 * state에 값을 넣습니다. 건너뛴 step이 save했을 값을 미리 넣어 둘 때 씁니다.
	 *
	 * @param name  state 이름(Workflow의 state:에 선언한 이름)
	 * @param value 넣을 값
	 */
	public WorkflowStepper put(String name, Object value) {
		WorkFlowContext.state(this.execution.context()).put(name, value);
		return this;
	}

	/** 지금 실행 상태입니다. */
	public WorkFlowExecution execution() {
		return this.execution;
	}

	/** 지금 상태입니다(RUNNING, DONE, FAILED, WAITING_APPROVAL). */
	public WorkFlowExecutionStatus status() {
		return this.execution.status();
	}

	/** 다음에 실행할 차례인 step의 id입니다. 끝난 실행이면 마지막으로 실행한 step의 id입니다. */
	public String currentStepId() {
		return this.workflow.steps().get(this.execution.currentStepIndex()).id();
	}

	/**
	 * state의 값 하나를 돌려줍니다. 없으면 null입니다.
	 *
	 * @param name state 이름
	 */
	public Object state(String name) {
		return WorkFlowContext.state(this.execution.context()).get(name);
	}

	/**
	 * state의 값 하나를 맵으로 돌려줍니다. Agent output이나 ROUTER 결과({route, reason})처럼 객체인 값에 씁니다.
	 *
	 * @param name state 이름
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> stateMap(String name) {
		return (Map<String, Object>) this.state(name);
	}

	/** Workflow가 끝났을 때의 최종 결과(return)입니다. 아직 끝나지 않았으면 null입니다. */
	public Object output() {
		return this.execution.output();
	}

	/** 실패했을 때의 사유입니다. */
	public String errorMessage() {
		return this.execution.errorMessage();
	}

	/** 지금까지 실행한 step의 id를 차례로 돌려줍니다(forEach step은 "id[0]"처럼 순번이 붙습니다). */
	public List<String> stepIds() {
		List<String> ids = new ArrayList<String>();
		for (StepHistoryEntry entry : this.store.findHistory(this.execution.executionId())) {
			ids.add(entry.stepId());
		}
		return ids;
	}

	/** step을 실행해도 되는 상태인지, 차례인 step이 stepId인지 봅니다. */
	private void checkTurn(String stepId) {
		WorkFlowExecutionStatus status = this.execution.status();
		if (status == WorkFlowExecutionStatus.DONE || status == WorkFlowExecutionStatus.FAILED) {
			throw new AssertionError("Workflow가 이미 " + status + "로 끝나서 step[" + stepId + "]을 실행할 수 없습니다"
				+ (this.execution.errorMessage() == null ? "" : " - 사유: " + this.execution.errorMessage()));
		}
		String current = this.currentStepId();
		if (!current.equals(stepId)) {
			throw new AssertionError("지금 실행할 차례는 step[" + current + "]입니다. step[" + stepId + "]이 아닙니다(실행한 step: " + this.stepIds() + ").");
		}
	}

	/** step id가 목록의 몇 번째인지 돌려줍니다. 없으면 -1입니다. */
	private int indexOf(String stepId) {
		List<StepDefinition> steps = this.workflow.steps();
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).id().equals(stepId)) {
				return i;
			}
		}
		return -1;
	}

}

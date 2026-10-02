package net.dstone.ai.runtime.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.step.AgentStepDefinition;
import net.dstone.ai.common.definition.workflow.step.ApprovalStepDefinition;
import net.dstone.ai.common.definition.workflow.step.RouterStepDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.common.definition.workflow.step.SupervisorStepDefinition;
import net.dstone.ai.common.definition.workflow.step.ToolStepDefinition;
import net.dstone.ai.common.exception.ExpressionException;
import net.dstone.ai.common.exception.ProviderErrorMessage;
import net.dstone.ai.common.exec.ExecContext;
import net.dstone.ai.common.schema.JqExpEvalUtil;
import net.dstone.ai.common.schema.JsonSchemaUtil;
import net.dstone.ai.runtime.step.AgentStepExecutor;
import net.dstone.ai.runtime.step.ApprovalStepExecutor;
import net.dstone.ai.runtime.step.RouterStepExecutor;
import net.dstone.ai.runtime.step.StepOutcome;
import net.dstone.ai.runtime.step.SupervisorStepExecutor;
import net.dstone.ai.runtime.step.ToolStepExecutor;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.common.core.BaseObject;

/**
 * Workflow를 실제로 진행시키는 핵심 클래스입니다. 
 * WorkFlowDefinition에 정의된 내용을 
 *  1. 순차 실행,
 *  2. 분기(둘 중 하나를 고르는 onSuccess/onFailure, 여러 개 중 하나를 고르는 ROUTER의 routes), 
 *  3. 병렬 실행 (forEach - 같은 step 하나를 리스트 항목 개수만큼 동시에 실행), 
 *  4. 루프(재시도) 
 * 패턴으로 실행합니다.
 * "어느 step 다음에 어느 step으로 갈지, 언제 멈출지" 같은 Workflow의 큰 흐름은 이 클래스가 직접 통제하고,
 * step 하나하나에서 필요한 똑똑한 판단은 각 StepExecutor를 거쳐 결국 LLM에게 맡깁니다. 
 * "지금 몇 번째 step인가 → 다음엔 몇 번째 step으로 가는가"를 계속 따라가는 단순한 상태 기계로 구현했습니다.
 *
 * ## step 사이에 데이터가 오가는 방법
 * 모든 데이터는 실행 컨텍스트(WorkFlowContext) 트리 하나를 거쳐서 오갑니다.
 * 1) step을 실행하기 직전에, 그 step의 input에 적힌 "${ ... }" 표현식을 컨텍스트로 계산합니다. (resolveInput())
 *    이 일은 step 종류와 상관없이 항상 이 클래스가 하므로, 모든 step이 같은 표현식 규칙을 씁니다. (common.schema.JqExpEvalUtil)
 * 2) 계산된 입력을 step 종류에 맞는 StepExecutor에게 넘기고(runStep()), 결과(StepOutcome)를 돌려받습니다.
 * 3) 그 결과를 컨텍스트의 steps.{stepId}에 {input, output, error}로 남깁니다.
 * 그래서 다음 step들은 "${ .steps.id.output.키 }"처럼 누구의 어떤 값인지 이름으로 콕 집어서 가져다 씁니다(숨은 "직전 결과"는 없습니다).
 * 표현식을 계산하지 못하면(jq 오류) 그 step은 실패로 처리되고, 사유가 error에 남습니다.
 *
 * ## 병렬 실행
 * 병렬 실행은 forEach 한 가지 방식으로만 표현합니다. "같은 step을 데이터만 바꿔가며 동시에 반복한다"는 한 가지 모델만 있으므로, 
 * YAML을 읽는 사람은 병렬에 대해 forEach 하나만 알면 됩니다. 서로 다른 step을 동시에 실행하는 것은 이 모델로 표현할 수 없고, 순차 실행으로 풀어서 써야 합니다.
 *
 * ## 상태 저장과 다음 step
 * step 하나를 처리할 때마다 WorkFlowExecutionStore로 상태를 바로 저장해 둡니다. 그
 * 래서 APPROVAL step에서 실행이 멈추더라도, 혹은 서버가 중간에 재시작되더라도 마지막으로 멈춘 step부터 이어서 진행할 수 있습니다.
 * 다음 step은 nextStepId()가 onSuccess/onFailure(또는 ROUTER의 routes)를 보고 정합니다. 
 * 앞쪽 step을 가리키면 되돌아가는 재시도 루프가 되고, 무한 루프는 maxIterations가 막습니다.
 * "SUCCESS"나 "FAIL" 예약어를 만나면 그 자리에서 Workflow 전체를 끝냅니다.
 */
@Component
public class WorkFlowExecutor extends BaseObject {

	@Autowired
	private AgentStepExecutor agentStepExecutor;
	@Autowired
	private SupervisorStepExecutor supervisorStepExecutor;
	@Autowired
	private RouterStepExecutor routerStepExecutor;
	@Autowired
	private ToolStepExecutor toolStepExecutor;
	@Autowired
	private ApprovalStepExecutor approvalStepExecutor;
	@Autowired
	private WorkFlowExecutionStore executionStore;
	@Autowired
	private JqExpEvalUtil jqExpEvalUtil;

	/**
	 * <pre>
	 * Workflow 하나를 실제로 실행합니다.
	 *
	 * execution.currentStepIndex()가 가리키는 스텝부터 이어서 실행합니다.
	 * 처음 시작하는 실행이면 0번(첫 스텝)부터, 승인 대기 상태에서 다시 이어가는 실행이면 멈췄던 바로 그 스텝부터 다시 실행됩니다.
	 * 실행 도중 SUCCESS, FAIL, WAITING_APPROVAL 중 하나에 도달하면 그 상태로 저장하고 결과를 돌려줍니다.
	 *
	 * 이 메서드가 호출되는 경우는 두 가지입니다.
	 * - 새로 실행할 때
	 *     사용자가 POST /api/ai/workflow/{id}/execute(동기 방식) 또는 /submit(비동기 방식)을 호출하면,
	 * 	   currentStepIndex가 0이고 status가 RUNNING인 새 실행이 만들어지고,
	 * - 승인(APPROVAL)이 끝나서 이어갈 때
	 *     사람이 승인 또는 반려 결정을 내리면, 그 결정이 먼저 컨텍스트의 approvals.{stepId}에 기록되고,
	 *     같은 실행(같은 executionId, 같은 currentStepIndex)을 가지고 run()이 다시 호출됩니다.
	 * </pre>
	 *
	 * @param workflow  실행할 Workflow의 정의입니다(steps 목록, maxIterations, output 등).
	 * @param execution 지금 진행 중인 실행 1건입니다. WorkFlowExecution은 상태가 바뀔 때마다 새 인스턴스를
	 *                  만드는 불변 객체지만, context 맵만은 같은 Map 인스턴스를 계속 공유해서 여러 스텝의
	 *                  결과가 한곳에 누적됩니다.
	 */
	public WorkFlowExecution run(WorkFlowDefinition workflow, WorkFlowExecution execution) {
		int maxIterations = workflow.maxIterations() == null ? Constants.WorkFlow.DEFAULT_MAX_ITERATIONS : workflow.maxIterations();
		int currentIndex = execution.currentStepIndex();
		WorkFlowExecution currentExecution = execution;
		int executed = 0;

		try {
			
			ExecContext.getInstance().put("WorkFlowExecution", currentExecution);

			while (true) {

				/****************************************************************************************
				1) 실행 횟수를 확인합니다. maxIterations를 넘어서면 무한 루프로 보고 FAILED로 끝냅니다.
				****************************************************************************************/
				if (++executed > maxIterations) {
					return this.persistFailed(currentExecution, "최대 실행 횟수(" + maxIterations + ")를 초과했습니다(루프 정지) - onFailure로 되돌아가는 step 구성을 다시 확인하십시오.");
				}

				/****************************************************************************************
				2) 지금 몇 번째 step인지(currentIndex)로 그 StepDefinition을 꺼내옵니다.
				****************************************************************************************/
				StepDefinition step = workflow.steps().get(currentIndex);

				/****************************************************************************************
				3) 실제로 이 step을 실행합니다. forEach가 없으면 한 번만 실행하는 runOne()을,
				   있으면 여러 번 동시에 실행하는 runForEach()를 씁니다.
				****************************************************************************************/
				StepOutcome outcome;
				try {
					if (StepDefinition.forEachOf(step) != null) {
						outcome = this.runForEach(step, currentExecution);
					} else {
						outcome = this.runOne(step, currentExecution);
					}
				} catch (Exception e) {
					// StepExecutor가 던진 예외(시스템 오류: 외부 연결 실패 등)는 onFailure로 보내지 않고 그 자리에서
					// 바로 FAILED로 끝냅니다. 재작성 루프 같은 onFailure 흐름은 "값이 틀렸다"는 비즈니스 실패를
					// 고치려는 것이지, 시스템 오류를 되풀이하려는 것이 아니기 때문입니다.
					return this.persistFailed(currentExecution, "step[" + step.id() + "] 실행 중 예외가 발생했습니다 - " + ProviderErrorMessage.of(e));
				}

				/****************************************************************************************
				4) 결과가 대기(APPROVAL이 사람의 결정을 기다리는 중)면, 실행을 여기서 멈춥니다.
				****************************************************************************************/
				if (outcome.pending()) {
					currentExecution = currentExecution.waitingApproval(currentIndex);
					this.executionStore.update(currentExecution);
					return currentExecution;
				}

				/****************************************************************************************
				5) 이번 step의 결과를 컨텍스트의 steps.{stepId}에 남겨서,
				   다음 step들이 "${ .steps.id... }"로 가져다 쓸 수 있게 합니다.
				****************************************************************************************/
				WorkFlowContext.recordStep(currentExecution.context(), step.id(), outcome.toRecord());

				/****************************************************************************************
				6) nextStepId()로 다음에 갈 곳을 정합니다(step id, 또는 SUCCESS/FAIL 예약어).
				   ROUTER가 routes에 없는 경로를 고르면 예외를 던지는데, 여기서 잡아서 FAILED로 남깁니다.
				****************************************************************************************/
				String nextId;
				try {
					nextId = this.nextStepId(workflow, step, outcome);
				} catch (Exception e) {
					return this.persistFailed(currentExecution, "step[" + step.id() + "]의 다음 step을 정하는 중 예외가 발생했습니다 - " + e.getMessage());
				}

				/****************************************************************************************
				7) 다음 곳으로 갑니다.
				   - SUCCESS: Workflow 전체를 성공으로 끝냅니다. workflow.output.value를 계산한 값을 최종 결과로 남깁니다
				              (output.schema가 있으면 그 모양인지 검사하고, 아니면 FAILED로 끝냅니다).
				   - FAIL   : Workflow 전체를 실패로 끝냅니다.
				   - step id: 그 step으로 이동해서 while 루프를 계속 돕니다(앞쪽 step이면 재시도 루프).
				****************************************************************************************/
				if (Constants.WorkFlow.SUCCESS_SENTINEL.equals(nextId)) {
					Object result;
					try {
						result = this.jqExpEvalUtil.resolve(workflow.output().value(), currentExecution.context(), null);
					} catch (ExpressionException e) {
						return this.persistFailed(currentExecution, "Workflow output을 만들지 못했습니다 - " + e.getMessage());
					}
					if (workflow.output().schema() != null) {
						List<String> problems = JsonSchemaUtil.validate(workflow.output().schema(), result);
						if (!problems.isEmpty()) {
							return this.persistFailed(currentExecution, "Workflow output이 output.schema 모양이 아닙니다: " + problems);
						}
					}
					currentExecution = currentExecution.done(result);
					this.executionStore.update(currentExecution);
					return currentExecution;
				}
				if (Constants.WorkFlow.FAIL_SENTINEL.equals(nextId)) {
					return this.persistFailed(currentExecution, this.failMessage(step, outcome));
				}
				int nextIndex = this.indexOf(workflow.steps(), nextId);
				if (nextIndex < 0) {
					return this.persistFailed(currentExecution, "workflow[" + workflow.id() + "]에 없는 step id로 이동하려 했습니다: " + nextId);
				}
				currentIndex = nextIndex;
				currentExecution = currentExecution.advanceTo(currentIndex);
				this.executionStore.update(currentExecution);
			}
			
		} catch (Exception e) {
			throw e;
		} finally {
			ExecContext.getInstance().removeCurrentContext();
		}
		
	}

	/**
	 * 실행을 FAILED 상태로 저장하고 그 상태를 돌려줍니다.
	 *
	 * @param execution    실패로 끝낼 실행입니다.
	 * @param errorMessage 실패 사유입니다.
	 */
	private WorkFlowExecution persistFailed(WorkFlowExecution execution, String errorMessage) {
		WorkFlowExecution failed = execution.failed(errorMessage);
		this.executionStore.update(failed);
		return failed;
	}

	/**
	 * forEach가 없는 step을 한 번 실행합니다. 대기 중이 아니면 실행 이력을 한 줄 남깁니다.
	 *
	 * @param step      실행할 step의 정의입니다.
	 * @param execution 지금 진행 중인 실행입니다.
	 */
	private StepOutcome runOne(StepDefinition step, WorkFlowExecution execution) {
		StepOutcome outcome = this.call(step, execution, null);
		if (!outcome.pending()) {
			this.appendHistory(execution, step, step.id(), outcome);
		}
		return outcome;
	}

	/**
	 * <pre>
	 * forEach가 있는 step을 리스트 항목 개수만큼 동시에 실행합니다.
	 *
	 * forEach 표현식을 계산한 리스트의 항목마다, 그 항목을 jq 변수($item 또는 $itemVariable)로 넣고 input을 계산해 실행합니다.
	 * 모든 반복이 끝나면 반복 순서대로 실행 이력을 남기고, 반복별 input과 output을 각각 순서대로 리스트로 모읍니다
	 * (steps.id.input / steps.id.output이 리스트가 됩니다).
	 * 반복이 하나라도 실패하면 이 step 전체가 실패입니다. 반복할 항목이 하나도 없으면 빈 결과로 성공 처리합니다.
	 *
	 * forEach 표현식을 계산하지 못하거나 그 값이 리스트가 아니면, 이 step은 실패로 처리됩니다(onFailure를 따릅니다).
	 * 반복 하나가 StepExecutor 예외(시스템 오류)를 던지면, 그 반복의 실행 이력을 남긴 뒤 예외를 그대로 올려보내서 실행 전체를 FAILED로 끝냅니다(run()의 3번 설명 참고).
	 * </pre>
	 *
	 * @param step      실행할 step의 정의입니다(forEach가 설정되어 있습니다).
	 * @param execution 지금 진행 중인 실행입니다.
	 */
	private StepOutcome runForEach(StepDefinition step, WorkFlowExecution execution) {
		long start = System.nanoTime();
		String forEach = StepDefinition.forEachOf(step);
		Object rawList;
		try {
			// 현재까지는 yaml 에서 forEach 의 실행값들만 가져올 목적이므로 는 변수맵핑기능을 제공하지 않음. 그래서 variables 는 null 로 세팅.
			rawList = this.jqExpEvalUtil.resolve(forEach, execution.context(), null);
		} catch (ExpressionException e) {
			return this.failedBeforeRun(execution, step, "forEach - " + e.getMessage());
		}
		if (!(rawList instanceof List<?> items)) {
			return this.failedBeforeRun(execution, step, "forEach[" + forEach + "]의 값이 리스트가 아닙니다(현재 값=" + rawList + ").");
		}

		String itemKey = StepDefinition.itemKeyOf(step);
		List<CompletableFuture<StepOutcome>> futures = new ArrayList<>(items.size());
		for (Object item : items) {
			final Map<String, Object> variables = new HashMap<>();
			variables.put(itemKey, item);
			futures.add(CompletableFuture.supplyAsync(new Supplier<StepOutcome>() {
				@Override
				public StepOutcome get() {
					// forEach 의 개별 실행값을 실행시킬 때 비로소 변수값들을 셋팅.
					return WorkFlowExecutor.this.call(step, execution, variables);
				}
			}));
		}

		boolean allSuccess = true;
		List<Object> inputs = new ArrayList<>();
		List<Object> outputs = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (int i = 0; i < futures.size(); i++) {
			String historyId = step.id() + "[" + i + "]";
			StepOutcome outcome;
			try {
				outcome = futures.get(i).join();
			} catch (CompletionException e) {
				Throwable cause = e.getCause() == null ? e : e.getCause();
				this.appendHistory(execution, step, historyId, StepOutcome.failure(null, ProviderErrorMessage.of(cause)));
				throw new IllegalStateException(historyId + " - " + cause.getMessage(), cause);
			}
			this.appendHistory(execution, step, historyId, outcome);
			if (!outcome.success()) {
				allSuccess = false;
				errors.add(historyId + ": " + outcome.error());
			}
			inputs.add(outcome.input());
			outputs.add(outcome.output());
		}
		String error = errors.isEmpty() ? null : String.join("\n", errors);
		return StepOutcome.forEach(allSuccess, inputs, outputs, error, (System.nanoTime() - start) / 1_000_000);
	}

	/**
	 * StepExecutor를 부르기 전에 이미 실패한 경우(forEach 리스트를 찾지 못한 경우 등)의 결과를 만듭니다.
	 * 실행 이력도 한 줄 남깁니다.
	 *
	 * @param execution 지금 진행 중인 실행입니다.
	 * @param step      실패한 step의 정의입니다.
	 * @param reason    실패 사유입니다.
	 */
	private StepOutcome failedBeforeRun(WorkFlowExecution execution, StepDefinition step, String reason) {
		StepOutcome outcome = StepOutcome.failure(null, reason);
		this.appendHistory(execution, step, step.id(), outcome);
		return outcome;
	}

	/**
	 * <pre>
	 * step을 실제로 한 번 실행합니다.
	 * - input 표현식을 계산하고
	 * - step 종류에 맞는 StepExecutor를 부르고
	 * - 실제로 넘긴 입력과 걸린 시간을 결과에 채웁니다.
	 * 표현식을 계산하지 못하면 StepExecutor를 부르지 않고 실패 결과를 돌려줍니다(onFailure를 따릅니다).
	 * StepExecutor가 던진 예외(시스템 오류)는 잡지 않고 그대로 올려보내서, run()이 onFailure를 거치지 않고 실행 전체를 FAILED로 끝내게 합니다.
	 * forEach의 반복들이 동시에 부를 수 있도록, 이 메서드는 실행 이력을 직접 남기지 않습니다.
	 * </pre>
	 *
	 * @param step      실행할 step의 정의입니다.
	 * @param execution 지금 진행 중인 실행입니다.
	 * @param variables input 표현식에 넣을 jq 변수입니다(forEach 반복이면 {item: 이번 항목}, 아니면 null).
	 */
	private StepOutcome call(StepDefinition step, WorkFlowExecution execution, Map<String, Object> variables) {
		long start = System.nanoTime();
		Object resolvedInput = null;
		StepOutcome outcome;
		try {
			resolvedInput = this.resolveInput(step, execution.context(), variables);
			outcome = this.runStep(execution, step, resolvedInput);
		} catch (ExpressionException e) {
			outcome = StepOutcome.failure(null, "input을 계산하지 못했습니다 - " + e.getMessage());
		}
		return outcome.withCall(resolvedInput, (System.nanoTime() - start) / 1_000_000);
	}

	/**
	 * <pre>
	 * step의 input을 컨텍스트로 계산합니다(표현식은 계산하고, 리터럴은 그대로 둡니다).
	 * - AGENT/SUPERVISOR/ROUTER: input(Agent input 모양에 따라 값 하나 또는 맵)을 계산해서 돌려줍니다.
	 *   input은 필수라서 엔진이 켜질 때 이미 검사되어 있습니다.
	 * - TOOL: input(맵)을 계산해서 맵으로 돌려줍니다. input이 없으면 빈 맵입니다.
	 * - APPROVAL: input이 없는 step이라 null입니다.
	 * </pre>
	 *
	 * @param step      input을 계산할 step의 정의입니다.
	 * @param context   실행 컨텍스트입니다.
	 * @param variables 표현식에 넣을 jq 변수입니다(forEach 반복이 아니면 null).
	 */
	private Object resolveInput(StepDefinition step, Map<String, Object> context, Map<String, Object> variables) {
		switch (step) {
			case AgentStepDefinition agent:
				return this.jqExpEvalUtil.resolve(agent.input(), context, variables);
			case SupervisorStepDefinition supervisor:
				return this.jqExpEvalUtil.resolve(supervisor.input(), context, variables);
			case RouterStepDefinition router:
				return this.jqExpEvalUtil.resolve(router.input(), context, variables);
			case ToolStepDefinition tool:
				return tool.input() == null ? Map.of() : this.jqExpEvalUtil.resolve(tool.input(), context, variables);
			case ApprovalStepDefinition approval:
				return null;
		}
	}

	/**
	 * <pre>
	 * step 종류에 맞는 StepExecutor에게 채워진 입력을 넘겨 실행합니다. step 정의와 StepExecutor는 1:1입니다.
	 * StepDefinition이 sealed interface라서, 새 step 종류를 추가하고 여기를 빠뜨리면 컴파일 오류로 알려줍니다.
	 * </pre>
	 *
	 * @param execution     지금 진행 중인 실행입니다.
	 * @param step          실행할 step의 정의입니다.
	 * @param resolvedInput resolveInput()이 계산한 입력입니다(TOOL은 맵, AGENT류는 Agent input 모양, APPROVAL은 null).
	 */
	@SuppressWarnings("unchecked")
	private StepOutcome runStep(WorkFlowExecution execution, StepDefinition step, Object resolvedInput) {

		ExecContext.getInstance().put("StepDefinition", step);

		switch (step) {
			case AgentStepDefinition agent:
				return this.agentStepExecutor.run(execution, agent, resolvedInput);
			case SupervisorStepDefinition supervisor:
				return this.supervisorStepExecutor.run(execution, supervisor, resolvedInput);
			case RouterStepDefinition router:
				return this.routerStepExecutor.run(execution, router, resolvedInput);
			case ToolStepDefinition tool:
				return this.toolStepExecutor.run(execution, tool, (Map<String, Object>) resolvedInput);
			case ApprovalStepDefinition approval:
				return this.approvalStepExecutor.run(execution, approval);
		}
	}

	/**
	 * step 실행 이력을 한 줄 남깁니다.
	 *
	 * @param execution 지금 진행 중인 실행입니다.
	 * @param step      실행한 step의 정의입니다.
	 * @param historyId 이력에 남길 step id입니다(forEach 반복이면 "id[0]"처럼 순번이 붙습니다).
	 * @param outcome   실행 결과입니다.
	 */
	private void appendHistory(WorkFlowExecution execution, StepDefinition step, String historyId, StepOutcome outcome) {
		StepHistoryEntry stepHistoryEntry = new StepHistoryEntry(
			historyId, step.type()
			, StepDefinition.refOf(step)
			, outcome.success()
			, outcome.durationMs()
			, (outcome.success() ? JsonSchemaUtil.toText(outcome.output()) : null)
			, outcome.error()
			, Instant.now()
		);
		this.executionStore.appendHistory(
			execution.executionId(),
			stepHistoryEntry
		);
	}

	/**
	 * <pre>
	 * step 하나가 끝난 뒤 다음에 갈 곳을 정합니다. step id를 돌려주거나, Workflow를 끝낼 때는 "SUCCESS"/"FAIL" 예약어를 돌려줍니다.
	 * - 성공한 ROUTER, routes를 적은 APPROVAL: 고른 route(ROUTER는 LLM이, APPROVAL은 사람이 고름)를 routes에서 찾습니다.
	 *   routes에 없는 이름이면 예외를 던집니다.
	 * - 그 밖의 성공: onSuccess에 적은 곳. 비어 있으면 목록상 다음 step, 마지막 step이면 SUCCESS입니다.
	 * - 실패: onFailure에 적은 곳. 비어 있으면 FAIL입니다.
	 * </pre>
	 *
	 * @param workflow 실행 중인 Workflow의 정의입니다.
	 * @param step     방금 끝난 step의 정의입니다.
	 * @param outcome  그 step의 결과입니다.
	 */
	private String nextStepId(WorkFlowDefinition workflow, StepDefinition step, StepOutcome outcome) {
		if (!outcome.success()) {
			return step.onFailure() == null ? Constants.WorkFlow.FAIL_SENTINEL : step.onFailure();
		}
		Map<String, String> routes = StepDefinition.routesOf(step);
		if (routes != null) {
			String target = routes.get(outcome.route());
			if (target == null) {
				throw new IllegalStateException("route['" + outcome.route() + "']가 routes에 정의되어 있지 않습니다(정의된 route=" + routes.keySet() + ").");
			}
			return target;
		}
		String onSuccess = StepDefinition.onSuccessOf(step);
		if (onSuccess != null) {
			return onSuccess;
		}
		String sequentialNextId = this.nextSequentialId(workflow.steps(), step.id());
		return sequentialNextId == null ? Constants.WorkFlow.SUCCESS_SENTINEL : sequentialNextId;
	}

	/**
	 * Workflow를 FAIL로 끝낼 때 남길 메시지를 만듭니다. 고른 route(ROUTER, routes를 적은 APPROVAL)가 FAIL이거나
	 * 성공했는데 onSuccess가 FAIL이면 그 step의 output을 글자로 바꾼 값을,
	 * 실패했는데 onFailure가 없으면 그 사실을 덧붙인 실패 사유를, 그 밖에는 실패 사유를 씁니다.
	 *
	 * @param step    방금 끝난 step의 정의입니다.
	 * @param outcome 그 step의 결과입니다.
	 */
	private String failMessage(StepDefinition step, StepOutcome outcome) {
		if (outcome.success() && outcome.route() != null) {
			return "step[" + step.id() + "]에서 고른 route '" + outcome.route() + "'가 FAIL이라 Workflow를 끝냈습니다: " + JsonSchemaUtil.toText(outcome.output());
		}
		if (outcome.success()) {
			return "step[" + step.id() + "]가 onSuccess: FAIL로 Workflow를 끝냈습니다: " + JsonSchemaUtil.toText(outcome.output());
		}
		if (step.onFailure() == null) {
			return "step[" + step.id() + "]가 실패했고 onFailure가 지정되지 않았습니다: " + outcome.error();
		}
		return outcome.error();
	}

	/**
	 * 목록에서 지금 step 바로 다음 step의 id를 돌려줍니다. 마지막 step이면 null입니다.
	 *
	 * @param steps     Workflow의 step 목록입니다.
	 * @param currentId 지금 step의 id입니다.
	 */
	private String nextSequentialId(List<StepDefinition> steps, String currentId) {
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).id().equals(currentId) && i + 1 < steps.size()) {
				return steps.get(i + 1).id();
			}
		}
		return null;
	}

	/**
	 * step id가 목록의 몇 번째인지 돌려줍니다. 없으면 -1입니다.
	 *
	 * @param steps Workflow의 step 목록입니다.
	 * @param id    찾을 step의 id입니다.
	 */
	private int indexOf(List<StepDefinition> steps, String id) {
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

}

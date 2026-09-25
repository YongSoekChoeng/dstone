package net.dstone.ai.runtime.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.consts.Constants.WorkFlow.Context;
import net.dstone.ai.common.consts.StepType;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.common.definition.WorkFlowDefinition;
import net.dstone.ai.common.template.Template;
import net.dstone.ai.common.template.TemplateException;
import net.dstone.ai.runtime.step.AgentStepRunner;
import net.dstone.ai.runtime.step.ApprovalStepRunner;
import net.dstone.ai.runtime.step.StepInput;
import net.dstone.ai.runtime.step.StepOutcome;
import net.dstone.ai.runtime.step.StepRunner;
import net.dstone.ai.runtime.step.ToolStepRunner;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * Workflow를 실제로 진행시키는 핵심 클래스입니다. WorkFlowDefinition에 정의된 내용을 순차 실행,
 * 분기(둘 중 하나를 고르는 onSuccess/onFailure, 여러 개 중 하나를 고르는 ROUTER의 routes), 병렬 실행
 * (forEach - 같은 step 하나를 리스트 항목 개수만큼 동시에 실행), 루프(재시도) 패턴으로 실행합니다.
 * "어느 step 다음에 어느 step으로 갈지, 언제 멈출지" 같은 Workflow의 큰 흐름은 이 클래스가 직접 통제하고,
 * step 하나하나에서 필요한 똑똑한 판단은 각 StepRunner를 거쳐 결국 LLM에게 맡깁니다. "지금 몇 번째
 * step인가 → 다음엔 몇 번째 step으로 가는가"를 계속 따라가는 단순한 상태 기계로 구현했습니다.
 *
 * ## step 사이에 데이터가 오가는 방법
 * 모든 데이터는 실행 컨텍스트(WorkFlowContext) 트리 하나를 거쳐서 오갑니다.
 * 1) step을 실행하기 직전에, 그 step의 input 템플릿({{ ... }})을 컨텍스트로 채웁니다(renderInput()).
 *    이 일은 step 종류와 상관없이 항상 이 클래스가 하므로, 모든 step이 같은 템플릿 규칙을 씁니다.
 * 2) 채워진 입력을 StepRunner에게 넘기고, StepRunner는 결과(StepOutcome)를 돌려줍니다.
 * 3) 그 결과를 컨텍스트의 steps.{stepId}에 {input, output, text, error}로 남기고, previous도 이 결과로 바꿉니다.
 * 그래서 다음 step들은 {{steps.id.output.키}}처럼 누구의 어떤 값인지 이름으로 콕 집어서 가져다 씁니다.
 * input 템플릿이 가리키는 값을 찾지 못하면 그 step은 실패로 처리되고, 사유가 error에 남습니다.
 *
 * ## 병렬 실행
 * 병렬 실행은 forEach 한 가지 방식으로만 표현합니다. "같은 step을 데이터만 바꿔가며 동시에 반복한다"는
 * 한 가지 모델만 있으므로, YAML을 읽는 사람은 병렬에 대해 forEach 하나만 알면 됩니다. 서로 다른 step을
 * 동시에 실행하는 것은 이 모델로 표현할 수 없고, 순차 실행으로 풀어서 써야 합니다.
 *
 * ## 상태 저장과 전이
 * step 하나를 처리할 때마다 WorkFlowExecutionStore로 상태를 바로 저장해 둡니다. 그래서 APPROVAL step에서
 * 실행이 멈추더라도, 혹은 서버가 중간에 재시작되더라도 마지막으로 멈춘 step부터 이어서 진행할 수 있습니다.
 * onSuccess/onFailure(또는 ROUTER의 routes)에 적어둔 다음 step이 지금 step보다 앞쪽에 있으면
 * WorkflowTransition.Loop로, 뒤쪽에 있으면 WorkflowTransition.NextStep으로 판단합니다(둘의 실제 동작은
 * 같고, 로그를 읽을 때 구분하기 좋게 나눠둔 것입니다. 무한 루프는 maxIterations가 막습니다).
 * "SUCCESS"나 "FAIL" 예약어를 만나면 그 자리에서 Workflow 전체를 끝냅니다.
 */
@Component
public class WorkFlowExecutor extends BaseObject {

	@Autowired
	private AgentStepRunner agentStepRunner;
	@Autowired
	private ToolStepRunner toolStepRunner;
	@Autowired
	private ApprovalStepRunner approvalStepRunner;
	@Autowired
	private WorkFlowExecutionStore executionStore;

	/**
	 * <pre>
	 * Workflow 하나를 실제로 실행합니다.
	 *
	 * execution.currentStepIndex()가 가리키는 스텝부터 이어서 실행합니다. 
	 * 처음 시작하는 실행이면 0번(첫 스텝)부터, 승인 대기 상태에서 다시 이어가는 실행이면 멈췄던 바로 그 스텝부터 다시 실행됩니다. 
	 * 실행 도중 SUCCESS, FAIL, WAITING_APPROVAL 중 하나에 도달하면 그 상태로 저장하고 결과를 돌려줍니다.
	 *
	 * 이 메서드가 호출되는 경우는 두 가지입니다.
	 * - 새로 실행할 때: 
	 * 		사용자가 POST /api/ai/workflow/{id}/execute(동기 방식) 또는 /submit(비동기 방식)을 호출하면, 
	 * 		currentStepIndex가 0이고 status가 RUNNING인 새 실행이 만들어지고, 
	 * - 승인(APPROVAL)이 끝나서 이어갈 때: 
	 *   사람이 승인 또는 반려 결정을 내리면, 그 결정이 먼저 컨텍스트의 approvals.{stepId}에 기록되고, 
	 *   같은 실행(같은 executionId, 같은 currentStepIndex)을 가지고 run()이 다시 호출됩니다.
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
			StepRunResult stepResult;
			try {
				if( !StringUtil.isEmpty(step.forEach()) ) {
					stepResult = this.runForEach(step, currentExecution);
				}else {
					stepResult = this.runOne(step, currentExecution);
				}
			} catch (Exception e) {
				// StepRunner가 던진 예외(시스템 오류: 외부 연결 실패 등)는 onFailure로 보내지 않고 그 자리에서
				// 바로 FAILED로 끝냅니다. 재작성 루프 같은 onFailure 흐름은 "값이 틀렸다"는 비즈니스 실패를
				// 고치려는 것이지, 시스템 오류를 되풀이하려는 것이 아니기 때문입니다.
				return this.persistFailed(currentExecution, "step[" + step.id() + "] 실행 중 예외가 발생했습니다 - " + e.getMessage());
			}

			/****************************************************************************************
			4) 결과가 PENDING(APPROVAL 대기 중)이면, 실행을 여기서 멈춥니다.
			****************************************************************************************/
			if (stepResult.pending()) {
				// WAITING_APPROVAL 상태로 저장하고 곧바로 리턴합니다(루프를 빠져나갑니다).
				currentExecution = currentExecution.waitingApproval(currentIndex);
				this.executionStore.update(currentExecution);
				return currentExecution;
			}

			/****************************************************************************************
			5) 이번 step의 결과를 컨텍스트의 steps.{stepId}와 previous에 남겨서,
			   다음 step들이 {{steps.id...}}나 {{previous...}}로 가져다 쓸 수 있게 합니다.
			****************************************************************************************/
			WorkFlowContext.recordStep(currentExecution.context(), step.id(), stepResult.record());

			/****************************************************************************************
			6) decideTransition()으로 다음에 무엇을 할지 정합니다.
			  - 성공이면 결과 텍스트를, 실패면 실패 사유를 전이 메시지로 씁니다.
			  - onSuccess/onFailure에 존재하지 않는 step id를 적어두면 decideTransition()이 예외를
			    던지는데, 여기서 잡아서 FAILED로 남깁니다. 그래야 실행 상태가 어중간하게 남지 않습니다.
			****************************************************************************************/
			WorkflowTransition transition;
			try {
				String message = stepResult.success() ? stepResult.text() : stepResult.error();
				transition = this.decideTransition(workflow, step, stepResult.success(), message, stepResult.route());
			} catch (Exception e) {
				return this.persistFailed(currentExecution, "step[" + step.id() + "]의 다음 전이(transition)를 계산하는 중 예외가 발생했습니다 - " + e.getMessage());
			}

			/****************************************************************************************
			WorkflowTransition은 sealed interface라 4가지 경우(Done/Failed/NextStep/Loop)를 컴파일러가 빠짐없이 다뤘는지 검사해 줍니다.
			****************************************************************************************/
			switch (transition) {
				/****************************************************************************************
				Done: Workflow 전체가 성공적으로 끝났다는 뜻입니다.
					- Workflow에 output 템플릿이 있으면 그 값을, 없으면 마지막 결과 텍스트를 최종 결과로 저장합니다.
					- output 템플릿이 가리키는 값을 찾지 못하면 FAILED로 끝냅니다.
				****************************************************************************************/
				case WorkflowTransition.Done done -> {
					String result;
					try {
						result = this.renderOutput(workflow, currentExecution.context(), done.message());
					} catch (TemplateException e) {
						return this.persistFailed(currentExecution, "Workflow output을 만들지 못했습니다 - " + e.getMessage());
					}
					currentExecution = currentExecution.done(result);
					this.executionStore.update(currentExecution);
					return currentExecution;
				}
				/****************************************************************************************
				Failed: Workflow 전체가 실패로 끝났다는 뜻입니다.
					- status를 FAILED로, errorMessage에 실패 사유를 저장한 뒤 리턴합니다(루프를 빠져나갑니다).
				****************************************************************************************/
				case WorkflowTransition.Failed failed -> {
					return this.persistFailed(currentExecution, failed.message());
				}
				/****************************************************************************************
				NextStep: 아직 끝나지 않고 다른 step으로 계속 진행한다는 뜻입니다.
					- currentIndex를 갱신하고 status를 RUNNING으로 저장한 뒤 while 루프를 계속 돕니다.
				****************************************************************************************/
				case WorkflowTransition.NextStep next -> {
					currentIndex = this.indexOf(workflow.steps(), next.stepId());
					currentExecution = currentExecution.advanceTo(currentIndex);
					this.executionStore.update(currentExecution);
				}
				/****************************************************************************************
				Loop: 실패해서 앞쪽의 다른 step으로 되돌아가 다시 시도한다는 뜻입니다.
					- NextStep과 실제 동작은 완전히 같습니다. 로그를 읽을 때 구분하기 좋게 나눠둔 것입니다.
				****************************************************************************************/
				case WorkflowTransition.Loop loop -> {
					currentIndex = this.indexOf(workflow.steps(), loop.stepId());
					currentExecution = currentExecution.advanceTo(currentIndex);
					this.executionStore.update(currentExecution);
				}
			}
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
	 * forEach가 없는 step을 한 번 실행합니다. 실행 이력을 한 줄 남기고, 컨텍스트에 남길 결과를 만들어 돌려줍니다.
	 *
	 * @param step      실행할 step의 정의입니다.
	 * @param execution 지금 진행 중인 실행입니다.
	 */
	private StepRunResult runOne(StepDefinition step, WorkFlowExecution execution) {
		StepCall call = this.call(step, execution, execution.context());
		if (call.outcome() instanceof StepOutcome.Pending) {
			return StepRunResult.pendingResult();
		}
		this.appendHistory(execution, step, step.id(), call);
		StepOutcome outcome = call.outcome();
		boolean success = !(outcome instanceof StepOutcome.Failure);
		return new StepRunResult(false, success, WorkFlowContext.stepRecord(call.renderedInput(), outcome.output(), outcome.text(), outcome.failureReason()), outcome.route());
	}

	/**
	 * <pre>
	 * forEach가 있는 step을 리스트 항목 개수만큼 동시에 실행합니다.
	 *
	 * forEach 경로가 가리키는 리스트를 찾아서, 항목마다 {{item}}(또는 itemVariable) 하나만 더한 컨텍스트 복사본으로 input을 채워 실행합니다. 
	 * 모든 반복이 끝나면 반복 순서대로 실행 이력을 남기고, 결과를 items에 모읍니다. 
	 * 반복이 하나라도 실패하면 이 step 전체가 실패입니다. 반복할 항목이 하나도 없으면 빈 결과로 성공 처리합니다.
	 *
	 * forEach 경로의 값을 찾지 못하거나 그 값이 리스트가 아니면, 이 step은 실패로 처리됩니다(onFailure를 따릅니다).
	 * 반복 하나가 StepRunner 예외(시스템 오류)를 던지면, 그 반복의 실행 이력을 남긴 뒤 예외를 그대로 올려보내서 실행 전체를 FAILED로 끝냅니다(run()의 3번 설명 참고).
	 * </pre>
	 *
	 * @param step      실행할 step의 정의입니다(forEach가 설정되어 있습니다).
	 * @param execution 지금 진행 중인 실행입니다.
	 */
	private StepRunResult runForEach(StepDefinition step, WorkFlowExecution execution) {
		Map<String, Object> context = execution.context();
		Object rawList;
		try {
			rawList = Template.evaluate(step.forEach(), context);
		} catch (TemplateException e) {
			return this.failedBeforeRun(execution, step, "forEach[" + step.forEach() + "] - " + e.getMessage());
		}
		if (!(rawList instanceof List<?> items)) {
			return this.failedBeforeRun(execution, step, "forEach[" + step.forEach() + "]의 값이 리스트가 아닙니다(현재 값=" + rawList + ").");
		}

		String itemKey = StringUtil.isEmpty(step.itemVariable()) ? Constants.WorkFlow.DEFAULT_ITEM_VARIABLE_KEY : step.itemVariable();
		List<CompletableFuture<StepCall>> futures = new ArrayList<>(items.size());
		for (Object item : items) {
			Map<String, Object> iterationContext = WorkFlowContext.withItem(context, itemKey, item);
			futures.add(CompletableFuture.supplyAsync(() -> this.call(step, execution, iterationContext)));
		}

		boolean allSuccess = true;
		List<Map<String, Object>> itemRecords = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (int i = 0; i < futures.size(); i++) {
			String historyId = step.id() + "[" + i + "]";
			StepCall call;
			try {
				call = futures.get(i).join();
			} catch (CompletionException e) {
				Throwable cause = e.getCause() == null ? e : e.getCause();
				this.appendHistory(execution, step, historyId, new StepCall(null, StepOutcome.failure(null, cause.getMessage()), 0L));
				throw new IllegalStateException(historyId + " - " + cause.getMessage(), cause);
			}
			this.appendHistory(execution, step, historyId, call);
			StepOutcome outcome = call.outcome();
			if (outcome instanceof StepOutcome.Failure) {
				allSuccess = false;
				errors.add(historyId + ": " + outcome.failureReason());
			}
			if (outcome.text() != null) {
				texts.add(outcome.text());
			}
			itemRecords.add(WorkFlowContext.stepRecord(call.renderedInput(), outcome.output(), outcome.text(), outcome.failureReason()));
		}
		String error = errors.isEmpty() ? null : String.join("\n", errors);
		return new StepRunResult(false, allSuccess, WorkFlowContext.forEachRecord(String.join("\n", texts), error, itemRecords), null);
	}

	/**
	 * StepRunner를 부르기 전에 이미 실패한 경우(forEach 리스트를 찾지 못한 경우 등)의 결과를 만듭니다.
	 * 실행 이력도 한 줄 남깁니다.
	 *
	 * @param execution 지금 진행 중인 실행입니다.
	 * @param step      실패한 step의 정의입니다.
	 * @param reason    실패 사유입니다.
	 */
	private StepRunResult failedBeforeRun(WorkFlowExecution execution, StepDefinition step, String reason) {
		StepCall call = new StepCall(null, StepOutcome.failure(null, reason), 0L);
		this.appendHistory(execution, step, step.id(), call);
		return new StepRunResult(false, false, WorkFlowContext.stepRecord(null, null, null, reason), null);
	}

	/**
	 * <pre>
	 * step을 실제로 한 번 실행합니다.
	 * - input 템플릿을 채우고 
	 * - StepRunner를 부르고 
	 * - 걸린 시간을 잽니다.
	 * 템플릿이 가리키는 값을 찾지 못하면 StepRunner를 부르지 않고 실패 결과를 돌려줍니다(YAML을 잘못 조립한 비즈니스 실패이므로 onFailure를 따릅니다). 
	 * StepRunner가 던진 예외(시스템 오류)는 잡지 않고 그대로 올려보내서, run()이 onFailure를 거치지 않고 실행 전체를 FAILED로 끝내게 합니다.
	 * forEach의 반복들이 동시에 부를 수 있도록, 이 메서드는 실행 이력을 직접 남기지 않습니다.
	 * </pre>
	 *
	 * @param step      실행할 step의 정의입니다.
	 * @param execution 지금 진행 중인 실행입니다.
	 * @param context   input 템플릿을 채울 때 쓸 컨텍스트입니다(forEach 반복이면 item이 더해진 복사본).
	 */
	private StepCall call(StepDefinition step, WorkFlowExecution execution, Map<String, Object> context) {
		long start = System.nanoTime();
		Object renderedInput = null;
		StepOutcome outcome;
		try {
			renderedInput = this.renderInput(step, context);
			outcome = this.runnerFor(step.type()).run(execution, step, this.toStepInput(step, renderedInput, context));
		} catch (TemplateException e) {
			outcome = StepOutcome.failure(null, "input을 채우지 못했습니다 - " + e.getMessage());
		}
		return new StepCall(renderedInput, outcome, (System.nanoTime() - start) / 1_000_000);
	}

	/**
	 * <pre>
	 * step의 input 템플릿을 컨텍스트로 채웁니다.
	 * - TOOL: input(맵)을 채워서 맵으로 돌려줍니다. input이 없으면 빈 맵입니다.
	 * - 그 밖의 step: input(문자열)을 채워서 문자열로 돌려줍니다. input이 없으면 {{previous.text}}를 씁니다.
	 *   APPROVAL은 input을 적을 수 없으므로 항상 {{previous.text}}입니다.
	 * </pre>
	 *
	 * @param step    input을 채울 step의 정의입니다.
	 * @param context 값을 찾아볼 컨텍스트입니다.
	 */
	private Object renderInput(StepDefinition step, Map<String, Object> context) {
		if (step.type() == StepType.TOOL) {
			return step.input() == null ? Map.of() : Template.render(step.input(), context);
		}
		String template = step.input() == null ? "{{" + Context.PREVIOUS + "." + Context.FIELD_TEXT + "}}" : step.input().toString();
		return Template.renderText(template, context);
	}

	/**
	 * 채워진 입력을 StepRunner에게 넘길 StepInput으로 감쌉니다. TOOL이면 arguments에, 그 밖의 step이면 text에 담습니다.
	 *
	 * @param step          실행할 step의 정의입니다.
	 * @param renderedInput renderInput()이 채운 입력입니다.
	 * @param context       실행 컨텍스트입니다(system prompt 변수로 쓸 inputs를 꺼냅니다).
	 */
	@SuppressWarnings("unchecked")
	private StepInput toStepInput(StepDefinition step, Object renderedInput, Map<String, Object> context) {
		if (step.type() == StepType.TOOL) {
			return new StepInput(null, (Map<String, Object>) renderedInput, WorkFlowContext.inputs(context));
		}
		return new StepInput((String) renderedInput, null, WorkFlowContext.inputs(context));
	}

	/**
	 * Workflow가 성공으로 끝났을 때 돌려줄 최종 결과를 만듭니다. Workflow에 output 템플릿이 있으면 그것을
	 * 채운 값을, 없으면 마지막 결과 텍스트를 그대로 씁니다.
	 *
	 * @param workflow 끝난 Workflow의 정의입니다.
	 * @param context  실행 컨텍스트입니다.
	 * @param lastText 마지막으로 실행된 step의 결과 텍스트입니다.
	 */
	private String renderOutput(WorkFlowDefinition workflow, Map<String, Object> context, String lastText) {
		return StringUtil.isEmpty(workflow.output()) ? lastText : Template.renderText(workflow.output(), context);
	}

	/**
	 * step 실행 이력을 한 줄 남깁니다.
	 *
	 * @param execution 지금 진행 중인 실행입니다.
	 * @param step      실행한 step의 정의입니다.
	 * @param historyId 이력에 남길 step id입니다(forEach 반복이면 "id[0]"처럼 순번이 붙습니다).
	 * @param call      실행 결과입니다.
	 */
	private void appendHistory(WorkFlowExecution execution, StepDefinition step, String historyId, StepCall call) {
		StepOutcome outcome = call.outcome();
		boolean success = !(outcome instanceof StepOutcome.Failure);
		this.executionStore.appendHistory(execution.executionId(),
			new StepHistoryEntry(historyId, step.type(), step.type().kind(), step.ref(), success, call.durationMs(), success ? outcome.text() : null, outcome.failureReason(), Instant.now()));
	}

	/**
	 * StepType에 맞는 StepRunner를 고릅니다. Agent를 부르는 세 종류(AGENT/SUPERVISOR/ROUTER)는 AgentStepRunner가 함께 맡습니다.
	 *
	 * @param type 실행할 step의 종류입니다.
	 */
	private StepRunner runnerFor(StepType type) {
		return switch (type) {
			case AGENT, SUPERVISOR, ROUTER -> this.agentStepRunner;
			case TOOL -> this.toolStepRunner;
			case APPROVAL -> this.approvalStepRunner;
		};
	}

	/**
	 * step 하나가 끝난 뒤 다음에 무엇을 할지 정합니다.
	 *
	 * @param workflow 실행 중인 Workflow의 정의입니다.
	 * @param step     방금 끝난 step의 정의입니다.
	 * @param success  그 step이 성공했는지 여부입니다.
	 * @param message  성공이면 결과 텍스트, 실패면 실패 사유입니다(Workflow가 끝날 때 결과나 에러 메시지로 쓰입니다).
	 * @param route    ROUTER step이 고른 경로 이름입니다(ROUTER가 아니면 null).
	 */
	private WorkflowTransition decideTransition(WorkFlowDefinition workflow, StepDefinition step, boolean success, String message, String route) {
		if (step.type() == StepType.ROUTER && success) {
			return this.decideRouterTransition(workflow, step, route, message);
		}

		// nextId: 성공이면 onSuccess에, 실패면 onFailure에 적어둔 값입니다. YAML에 적어두지 않았으면 null입니다.
		String nextId = success ? step.onSuccess() : step.onFailure();

		if (nextId == null) {
			if (!success) {
				// 실패했는데 onFailure가 없습니다 → 이 실패를 이어받을 곳이 없으므로 Workflow 전체를 실패로 끝냅니다.
				return WorkflowTransition.failed("step[" + step.id() + "]가 실패했고 onFailure가 지정되지 않았습니다: " + message);
			}
			// 성공했는데 onSuccess가 없습니다 → 목록상 그냥 다음 스텝으로 넘어가는, 가장 흔한 순차 실행입니다.
			String sequentialNextId = this.nextSequentialId(workflow.steps(), step.id());
			return sequentialNextId == null ? WorkflowTransition.done(message) : WorkflowTransition.next(sequentialNextId);
		}
		return this.resolveNextIdToFlow(workflow, step, nextId, message);
	}

	/**
	 * <pre>
	 * ROUTER step이 고른 route를 routes에서 찾아 다음 step을 정합니다. routes에 없는 이름이면 예외를 던집니다.
	 * </pre>
	 *
	 * @param workflow 실행 중인 Workflow의 정의입니다.
	 * @param step     방금 끝난 ROUTER step의 정의입니다.
	 * @param route    LLM이 고른 경로 이름입니다.
	 * @param message  결과 텍스트입니다.
	 */
	private WorkflowTransition decideRouterTransition(WorkFlowDefinition workflow, StepDefinition step, String route, String message) {
		Map<String, String> routes = step.routes();
		String nextId = routes == null ? null : routes.get(route);
		if (nextId == null) {
			Set<String> allowed = routes == null ? Set.of() : routes.keySet();
			throw new IllegalStateException("step[" + step.id() + "]: route['" + route + "']가 routes에 정의되어 있지 않습니다(정의된 route=" + allowed + ").");
		}
		return this.resolveNextIdToFlow(workflow, step, nextId, message);
	}

	/**
	 * 다음 step id(또는 SUCCESS/FAIL 예약어)를 실제 전이로 바꿉니다.
	 *
	 * @param workflow 실행 중인 Workflow의 정의입니다.
	 * @param step     방금 끝난 step의 정의입니다.
	 * @param nextId   다음 step id 또는 예약어입니다.
	 * @param message  Workflow가 여기서 끝날 때 쓸 결과 텍스트 또는 실패 사유입니다.
	 */
	private WorkflowTransition resolveNextIdToFlow(WorkFlowDefinition workflow, StepDefinition step, String nextId, String message) {
		if (Constants.WorkFlow.SUCCESS_SENTINEL.equals(nextId)) {
			// 예약어 "SUCCESS"가 적혀 있습니다 → 그 자리에서 바로 Workflow를 성공으로 끝냅니다.
			return WorkflowTransition.done(message);
		}
		if (Constants.WorkFlow.FAIL_SENTINEL.equals(nextId)) {
			// 예약어 "FAIL"이 적혀 있습니다 → 그 자리에서 바로 Workflow를 실패로 끝냅니다.
			return WorkflowTransition.failed(message);
		}
		// 예약어가 아니라 진짜 다른 step의 id가 적혀 있습니다(명시적인 분기이거나 재시도 루프입니다) → 그 step으로 이동해야 합니다.
		int currentIndex = this.indexOf(workflow.steps(), step.id());
		int nextIndex = this.indexOf(workflow.steps(), nextId);
		if (nextIndex < 0) {
			// 그런 id를 가진 step이 이 Workflow 안에 없습니다 → 더 진행할 수 없으므로 예외로 알립니다(run()이 잡아서 FAILED로 정리합니다).
			throw new IllegalStateException("workflow[" + workflow.id() + "]에 없는 step id로 이동하려 했습니다: " + nextId);
		}
		if (nextIndex <= currentIndex) {
			// 목표 step이 지금 step과 같거나 목록상 더 앞에 있습니다 → 뒤로 되돌아가는 것이므로 Loop(재시도)로 봅니다.
			return WorkflowTransition.loop(nextId);
		}
		// 목표 step이 지금 step보다 목록상 더 뒤에 있습니다 → 앞으로 나아가는 것이므로 NextStep으로 봅니다.
		return WorkflowTransition.next(nextId);
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

	/**
	 * step을 한 번 실행한 결과입니다(call()이 돌려줍니다).
	 *
	 * @param renderedInput 템플릿을 채운 뒤 실제로 넘긴 입력입니다(채우기 전에 실패했으면 null).
	 * @param outcome       StepRunner가 돌려준 결과입니다.
	 * @param durationMs    걸린 시간(밀리초)입니다.
	 */
	private record StepCall(Object renderedInput, StepOutcome outcome, long durationMs) {
	}

	/**
	 * step 하나(forEach면 모든 반복)를 처리한 결과입니다. run()이 이 값으로 컨텍스트 기록과 다음 전이를 정합니다.
	 *
	 * @param pending 사람의 승인을 기다리는 중인지 여부입니다.
	 * @param success 성공했는지 여부입니다.
	 * @param record  컨텍스트의 steps.{stepId}에 남길 결과입니다(pending이면 null).
	 * @param route   ROUTER step이 고른 경로 이름입니다(ROUTER가 아니면 null).
	 */
	private record StepRunResult(boolean pending, boolean success, Map<String, Object> record, String route) {

		/** APPROVAL step이 사람의 결정을 기다리는 중이라는 결과입니다. 컨텍스트에 남길 값이 아직 없어서 record는 null입니다. */
		private static StepRunResult pendingResult() {
			return new StepRunResult(true, false, null, null);
		}

		/** 결과 텍스트입니다. */
		private String text() {
			return (String) this.record.get(Context.FIELD_TEXT);
		}

		/** 실패 사유입니다. */
		private String error() {
			return (String) this.record.get(Context.FIELD_ERROR);
		}
	}

}

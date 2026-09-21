package net.dstone.ai.runtime.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.common.definition.StepType;
import net.dstone.ai.common.definition.WorkFlowDefinition;
import net.dstone.ai.runtime.status.StepFlow;
import net.dstone.ai.runtime.status.StepInput;
import net.dstone.ai.runtime.status.StepOutput;
import net.dstone.ai.runtime.status.StepResult;
import net.dstone.ai.runtime.step.AgentStepRunner;
import net.dstone.ai.runtime.step.ApprovalStepRunner;
import net.dstone.ai.runtime.step.StepRunner;
import net.dstone.ai.runtime.step.ToolStepRunner;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * Workflow를 실제로 진행시키는 핵심 클래스입니다. WorkFlowDefinition에 정의된 내용을 순차 실행,
 * 분기(둘 중 하나를 고르는 onSuccess/onFailure, 여러 개 중 하나를 고르는 ROUTER의 routes), 병렬 실행
 * (forEachVariable - 같은 step 하나를 실행 시점에 정해지는 개수만큼 동시에 실행), 루프(재시도) 패턴으로
 * 실행합니다. "어느 step 다음에 어느 step으로 갈지, 언제 멈출지" 같은 Workflow의 큰 흐름은 이 클래스가
 * 직접 통제하고, step 하나하나에서 필요한 똑똑한 판단은 각 StepRunner를 거쳐 결국 LLM에게 맡깁니다.
 * 별도의 복잡한 워크플로우 그래프 엔진을 새로 만들지 않고, "지금 몇 번째 step인가 → 다음엔 몇 번째
 * step으로 가는가"를 계속 따라가는 단순한 상태 기계로 구현했습니다.
 *
 * 병렬 실행은 "YAML을 작성할 때 미리 정해둔 서로 다른 step들을 묶는" 정적인 그룹 개념 없이,
 * forEachVariable 한 가지 방식으로만 표현합니다. 만약 개념이 두 가지였다면 YAML을 읽는 사람이 매번
 * "이건 어느 쪽 방식이지"를 따져봐야 하므로, "같은 step을 데이터만 바꿔가며 반복한다"는 이 한 가지
 * 모델로 통일했습니다. 그래서 서로 다른 step을 동시에 실행하고 싶은 경우(예: 서로 다른 Tool 두 개를
 * 한꺼번에 호출하고 싶은 경우)는 이 모델로 표현할 수 없고, 순차 실행으로 풀어서 써야 합니다. 그 대신
 * "병렬"이라는 개념 자체는 항상 forEachVariable 하나만 알면 되는 장점이 있습니다.
 *
 * AGENT/TOOL/SUPERVISOR/APPROVAL/ROUTER, 이 모든 StepType을 StepRunner라는 인터페이스 하나로 똑같이
 * 호출하고(자세한 내용은 runtime.step.StepRunner 참고), step 하나(또는 forEach의 반복 하나)를 처리할
 * 때마다 WorkFlowExecutionStore로 그 상태를 바로바로 저장해 둡니다. 그래서 APPROVAL step에서 실행이
 * 멈추더라도, 혹은 서버가 중간에 재시작되더라도 마지막으로 끝낸 step부터 이어서 계속 진행할 수 있습니다.
 *
 * StepDefinition의 onSuccess/onFailure(또는 ROUTER의 routes)에 적어둔 다음 step의 id가 지금 step보다
 * 앞쪽에 있으면 LOOP로, 뒤쪽에 있으면 NEXT_STEP으로 판단합니다(둘 다 실제 동작은 똑같습니다 -
 * StepStatus는 로그를 읽을 때 구분하기 좋으라고 나눠둔 것뿐이고, 진짜 무한 루프를 막는 역할은
 * maxIterations 하나가 맡습니다). onSuccess/onFailure/routes에 "SUCCESS"나 "FAIL"이라는 예약어를
 * 적어두면 그 자리에서 바로 Workflow 전체를 끝냅니다.
 *
 * step 하나(또는 forEach의 반복 하나)가 돌려준 StepOutput.data()는 그 step의 id를 앞에 붙인 형태로
 * ({stepId.키}) variables에 합쳐집니다(자세한 내용은 namespaced() 참고) - 이렇게 해두면 forEach로
 * 동시에 실행되는 반복들이 우연히 같은 데이터 키를 써도 서로 덮어쓰지 않습니다. 다만 이렇게 '.'이 섞인
 * 키는 TOOL step의 inputTemplate처럼 단순 문자열 치환을 쓰는 곳에서만 참조할 수 있습니다 - Agent의
 * prompt:는 StringTemplate 기반이라 변수 이름에 '.'을 쓸 수 없기 때문에, runtime.agent.AgentExecutor가
 * prompt를 렌더링하기 직전에 그런 키들을 미리 걸러냅니다(그렇게 하지 않으면, 그 prompt가 그 토큰을
 * 실제로 쓰지 않더라도 variables 안에 '.'이 섞인 키가 하나라도 있으면 렌더링 자체가 예외로 실패합니다).
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
	 * Workflow 하나를 실제로 실행합니다.
	 *
	 * execution.currentStepIndex()가 가리키는 스텝부터 이어서 실행합니다. 처음 시작하는 실행이면
	 * 0번(첫 스텝)부터, 승인 대기 상태에서 다시 이어가는 실행이면 멈췄던 바로 그 스텝부터 다시
	 * 실행됩니다. 실행 도중 SUCCESS, FAIL, WAITING_APPROVAL 중 하나에 도달하면 그 상태로 저장하고
	 * 결과를 돌려줍니다.
	 *
	 * 동작 방식은 간단합니다: YAML로 정의된 Workflow의 steps 목록을 처음부터 끝까지(또는 멈춰야 할
	 * 때까지) 한 스텝씩 실행하는데, "지금 몇 번째 step인가?" → "그 step을 실행한다" → "성공/실패에
	 * 따라 다음 step 번호를 계산한다"라는 단순한 반복만으로 순차 실행, 분기(둘 중 하나를 고르는
	 * 분기와 ROUTER의 여러 갈래 분기 둘 다), 병렬 실행(forEachVariable), 루프(재시도)까지 모든
	 * 패턴을 처리합니다.
	 *
	 * 이 메서드가 호출되는 경우는 두 가지입니다.
	 * - 새로 실행할 때: 사용자가 POST /api/ai/workflow/{id}/execute(동기 방식) 또는 /submit(비동기
	 *   방식)을 호출하면, WorkFlowExecution.start(...)로 currentStepIndex가 0이고 status가
	 *   RUNNING인 새 실행이 만들어지고, 곧바로 이 run()이 호출됩니다.
	 * - 승인(APPROVAL)이 끝나서 이어갈 때: 이전에 run()이 WAITING_APPROVAL 상태로 멈춰서 돌려준
	 *   실행 건에 대해, 나중에 사람이 승인 또는 반려 결정을 내리면, 그 결정이 먼저
	 *   variables.approvals.{stepId} 자리에 기록되고, 그다음 같은 실행(같은 executionId, 같은
	 *   currentStepIndex)을 가지고 run()이 다시 호출됩니다.
	 *
	 * @param workflow  실행할 Workflow의 정의입니다. YAML에서 읽어온 일종의 설계도로, steps 목록과 maxIterations(최대 반복 횟수) 등을 담고 있습니다.
	 *                  WorkFlowExecution은 불변 객체(record)라서, 상태가 바뀔 때마다(advanceTo/done/failed/waitingApproval) 새 인스턴스를 만들어서
	 *                  돌려주는 방식으로 동작합니다. 덕분에 "이 실행이 어느 시점에 어떤 상태였는지"를 언제 들여다봐도 안전하게 추적할 수 있습니다.
	 *                  다만 variables 맵만은 예외적으로 같은 Map 인스턴스를 계속 공유하는데, 그래야 여러 스텝이 값을 계속 누적해서 넣을 수 있기 때문입니다.
	 * @param execution 지금 진행 중인 실행 1건입니다(새 실행이면 currentStepIndex가 0이고, 이어서 재개하는 실행이면 멈췄던 스텝을 가리킵니다).
	 */
	public WorkFlowExecution run(WorkFlowDefinition workflow, WorkFlowExecution execution) {
		int maxIterations = workflow.maxIterations() == null ? Constants.WorkFlow.DEFAULT_MAX_ITERATIONS : workflow.maxIterations();
		int currentIndex = execution.currentStepIndex();
		WorkFlowExecution current = execution;
		int executed = 0;

		while (true) {

			/****************************************************************************************
			1) 실행 횟수를 확인합니다. maxIterations를 넘어서면 무한 루프로 보고 FAILED로 끝냅니다.
			****************************************************************************************/
			if (++executed > maxIterations) {
				return this.persistFailed(current, "최대 실행 횟수(" + maxIterations + ")를 초과했습니다(루프 정지) - onFailure로 되돌아가는 step 구성을 다시 확인하십시오.");
			}

			/****************************************************************************************
			2) 지금 몇 번째 step인지(currentIndex)로 그 StepDefinition을 꺼내옵니다.
			****************************************************************************************/
			StepDefinition step = workflow.steps().get(currentIndex);

			/****************************************************************************************
			3) 실제로 이 step을 실행합니다. forEachVariable이 없으면 한 번만 실행하는 runOne()을,
			   있으면 여러 번 동시에 실행하는 runForEach()를 씁니다.
			****************************************************************************************/
			StepRunResult stepResult;
			try {
				stepResult = StringUtil.isEmpty(step.forEachVariable()) ? this.runOne(step, current) : this.runForEach(step, current);
			} catch (Exception e) {
				// 실행 중 예외가 나면 그 자리에서 바로 FAILED로 끝냅니다.
				return this.persistFailed(current, "step[" + step.id() + "] 실행 중 예외가 발생했습니다 - " + e.getMessage());
			}

			/****************************************************************************************
			4) 결과가 PENDING(APPROVAL 대기 중)이면, 실행을 여기서 멈춥니다.
			****************************************************************************************/
			if (stepResult.pending()) {
				// WAITING_APPROVAL 상태로 저장하고 곧바로 리턴합니다(루프를 빠져나갑니다).
				current = current.waitingApproval(currentIndex);
				this.executionStore.update(current);
				return current;
			}

			/****************************************************************************************
			5) 이번 step이 만들어낸 데이터를 variables에 합치고, 결과 텍스트는 "__previous" 자리에 저장해서
			   다음 step이 {previous} 토큰으로 가져다 쓸 수 있게 해둡니다.
			****************************************************************************************/
			this.mergeVariables(current.variables(), stepResult.mergedData());
			current.variables().put(Constants.WorkFlow.PREVIOUS_TEXT_VARIABLE_KEY, stepResult.combinedText());

			/****************************************************************************************
			6) decideTransition()으로 다음에 무엇을 할지 정합니다.
			  - onSuccess/onFailure에 오타로 존재하지 않는 step id를 적어두면 decideTransition()이 예외를
			    던지는데, 3)번의 실행 예외와 똑같이 여기서도 잡아서 FAILED로 곱게 남깁니다. 이렇게 해두면
			    이 예외가 run() 밖으로 그냥 새나가서 WorkFlowExecution의 상태가 갱신되지 않은 채로 어중간하게
			    남는 일을 막을 수 있습니다.
			****************************************************************************************/
			StepFlow transition;
			try {
				transition = this.decideTransition(workflow, step, stepResult.success(), stepResult.combinedText(), stepResult.route());
			} catch (Exception e) {
				return this.persistFailed(current, "step[" + step.id() + "]의 다음 전이(transition)를 계산하는 중 예외가 발생했습니다 - " + e.getMessage());
			}

			switch (transition.status()) {
				/*************************************
				SUCCESS: Workflow 전체가 성공적으로 끝났다는 뜻입니다.
					- status를 DONE으로, resultText에 마지막 결과 텍스트를 저장한 뒤 리턴합니다(루프를 빠져나갑니다).
				*************************************/
				case SUCCESS:
					current = current.done(transition.message());
					this.executionStore.update(current);
					return current;
				/*************************************
				FAIL: Workflow 전체가 실패로 끝났다는 뜻입니다.
					- status를 FAILED로, errorMessage에 실패 사유를 저장한 뒤 리턴합니다(루프를 빠져나갑니다).
				*************************************/
				case FAIL:
					return this.persistFailed(current, transition.message());
				/*************************************
				NEXT_STEP, LOOP: 아직 끝나지 않고 다른 step으로 계속 진행한다는 뜻입니다.
					- currentIndex를 갱신하고 status를 RUNNING으로 저장한 뒤 while 루프를 계속 돕니다.
					- NEXT_STEP과 LOOP는 실제 동작이 완전히 같습니다 - 로그를 읽을 때 구분하기 좋으라고 나눠둔 것뿐입니다.
				*************************************/
				case NEXT_STEP, LOOP:
					currentIndex = this.indexOf(workflow.steps(), transition.nextStepId());
					current = current.advanceTo(currentIndex);
					this.executionStore.update(current);
					break;
				/*************************************
				나머지(ERROR/WAITING_APPROVAL)는 여기로 오지 않습니다.
					- ERROR와 WAITING_APPROVAL은 decideTransition()이 만들어내는 값이 아니라, 각각 위쪽의
					  catch 블록과 stepResult.pending() 분기에서 이미 먼저 처리되기 때문입니다.
					- 이 default는 컴파일러가 switch문이 모든 경우를 다뤘는지 확인하는 규칙을 만족시키기
					  위한 방어적인 코드일 뿐, 실제로 실행될 일은 없습니다.
				*************************************/
				default:
					return this.persistFailed(current, "알 수 없는 스텝 전이 상태입니다: " + transition.status());
			}
		}
	}

	/**
	 * 실행 상태를 FAILED로 바꾸고 저장합니다.
	 *
	 * @param execution    FAILED로 남길 실행 상태입니다.
	 * @param errorMessage 실패하거나 에러가 난 이유입니다.
	 */
	private WorkFlowExecution persistFailed(WorkFlowExecution execution, String errorMessage) {
		WorkFlowExecution failed = execution.failed(errorMessage);
		this.executionStore.update(failed);
		return failed;
	}

	/**
	 * forEachVariable이 설정되지 않은, 평범한 스텝 하나를 딱 한 번 실행합니다. 만약 APPROVAL
	 * 스텝인데 아직 사람의 결정이 나지 않았다면, 그대로 PENDING을 돌려줍니다(StepRunResult.pendingResult()).
	 *
	 * @param step      실행할 스텝의 정의입니다.
	 * @param execution 지금 진행 중인 실행 상태입니다.
	 */
	private StepRunResult runOne(StepDefinition step, WorkFlowExecution execution) {
		StepInput input = new StepInput(this.previousText(execution), execution.variables());
		long start = System.nanoTime();
		StepOutput output = this.runnerFor(step.type()).run(execution, step, input);
		long durationMs = (System.nanoTime() - start) / 1_000_000;

		if (output.result() == StepResult.PENDING) {
			return StepRunResult.pendingResult();
		}

		boolean success = output.result() == StepResult.SUCCESS;
		this.executionStore.appendHistory(execution.executionId(), new StepHistoryEntry(step.id(), step.type(), step.ref(), success, durationMs, success ? output.primaryText() : null, output.failureReason(), Instant.now()));
		return new StepRunResult(false, success, output.primaryText(), this.namespaced(step.id(), output.data()), output.route());
	}

	/**
	 * step.forEachVariable()이 가리키는 Workflow 변수(List 타입)에 담긴 항목 개수만큼, 이 step 하나를
	 * CompletableFuture로 한꺼번에 동시에 실행합니다. 이렇게 여러 번 동시에 도는 반복들은 서로 다른
	 * step id를 가질 수 없으므로, 대신 결과를 "<stepId>.<반복 순번>.<키>" 형태로 구분해서 담아둡니다.
	 * 그리고 각 반복이 낸 primaryText들을 모은 목록이 "<stepId>.results"라는 JSON 배열로 남습니다
	 * (실제로 <stepId>를 앞에 붙이는 건 이 메서드가 아니라 namespaced()가 마지막에 해줍니다 - 이 메서드
	 * 안에서는 "<반복 순번>.<키>"까지만 만듭니다). 반복 중 하나라도 실패하면 전체를 실패로 간주하고,
	 * 결과 텍스트는 반복된 순서대로 줄바꿈으로 이어붙입니다.
	 *
	 * 각 반복은 자기 자신만의 변수 맵 복사본을 받습니다. 그 복사본 안에서 itemVariable로 지정한 이름
	 * (기본값은 "item")에 그 반복이 맡은 항목 값을 채워 넣습니다 - 이렇게 복사본을 따로 만드는 이유는,
	 * CompletableFuture로 동시에 도는 반복들이 서로의 {item} 값을 덮어쓰지 않게 하기 위해서입니다.
	 * execution.variables() 자체는 이 메서드 안에서 건드리지 않고 그대로 둡니다(실제 병합은 항상
	 * run()의 mergeVariables 한 곳에서만 일어납니다).
	 *
	 * @param step      forEachVariable이 설정된 step의 정의입니다.
	 * @param execution 지금 진행 중인 실행 상태입니다.
	 */
	private StepRunResult runForEach(StepDefinition step, WorkFlowExecution execution) {
		Object rawList = execution.variables().get(step.forEachVariable());
		if (!(rawList instanceof List<?> items)) {
			throw new IllegalStateException(
				"step[" + step.id() + "]: forEachVariable['" + step.forEachVariable() + "']가 List가 아닙니다(현재 값=" + rawList + "). Workflow 호출 시 variables에 배열을 넘겨주십시오.");
		}
		if (items.isEmpty()) {
			// 반복할 항목이 하나도 없습니다. 굳이 실패로 볼 이유는 없으니, 직전 결과를 그대로 이어서 성공으로 취급합니다.
			return new StepRunResult(false, true, this.previousText(execution), Map.of(), null);
		}

		String itemKey = StringUtil.isEmpty(step.itemVariable()) ? Constants.WorkFlow.DEFAULT_ITEM_VARIABLE_KEY : step.itemVariable();
		String baseText = this.previousText(execution);
		List<CompletableFuture<StepOutput>> futures = new ArrayList<>(items.size());
		for (Object item : items) {
			Map<String, Object> iterationVariables = new LinkedHashMap<>(execution.variables());
			iterationVariables.put(itemKey, item);
			StepInput iterationInput = new StepInput(baseText, iterationVariables);
			futures.add(CompletableFuture.supplyAsync(() -> this.runnerFor(step.type()).run(execution, step, iterationInput)));
		}

		boolean allSuccess = true;
		StringBuilder combinedText = new StringBuilder();
		Map<String, Object> mergedData = new LinkedHashMap<>();
		List<String> resultTexts = new ArrayList<>();
		for (int i = 0; i < futures.size(); i++) {
			long start = System.nanoTime();
			StepOutput output;
			try {
				output = futures.get(i).join();
			} catch (Exception e) {
				this.executionStore.appendHistory(execution.executionId(),
					new StepHistoryEntry(step.id() + "[" + i + "]", step.type(), step.ref(), false, (System.nanoTime() - start) / 1_000_000, null, e.getMessage(), Instant.now()));
				allSuccess = false;
				continue;
			}
			long durationMs = (System.nanoTime() - start) / 1_000_000;
			boolean success = output.result() == StepResult.SUCCESS;
			allSuccess &= success;
			this.executionStore.appendHistory(execution.executionId(),
				new StepHistoryEntry(step.id() + "[" + i + "]", step.type(), step.ref(), success, durationMs, success ? output.primaryText() : null, output.failureReason(), Instant.now()));
			if (combinedText.length() > 0) {
				combinedText.append("\n");
			}
			combinedText.append(output.primaryText());
			resultTexts.add(output.primaryText());
			for (Map.Entry<String, Object> entry : output.data().entrySet()) {
				mergedData.put(i + "." + entry.getKey(), entry.getValue());
			}
		}
		mergedData.put("results", resultTexts);
		return new StepRunResult(false, allSuccess, combinedText.toString(), this.namespaced(step.id(), mergedData), null);
	}

	/**
	 * data에 담긴 키들 앞에 stepId를 붙여서, 그 값이 어느 step에서 나온 것인지 구분할 수 있게
	 * 만들어 줍니다.
	 *
	 * @param stepId 이 데이터를 만들어낸 step의 id입니다.
	 * @param data   그 step이 돌려준 구조화 결과입니다(StepOutput.data()).
	 */
	private Map<String, Object> namespaced(String stepId, Map<String, Object> data) {
		if (data == null || data.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : data.entrySet()) {
			result.put(stepId + "." + entry.getKey(), entry.getValue());
		}
		return result;
	}

	/** 직전 스텝이 남긴 결과 텍스트를 꺼내옵니다. @param execution 그 텍스트를 꺼낼 대상 실행 상태입니다. */
	private String previousText(WorkFlowExecution execution) {
		Object value = execution.variables().get(Constants.WorkFlow.PREVIOUS_TEXT_VARIABLE_KEY);
		return value == null ? null : value.toString();
	}

	/**
	 * 구조화 결과를 Workflow 전역 변수 맵에 합쳐 넣습니다.
	 *
	 * @param variables 값을 합쳐 넣을 대상 Workflow 전역 변수 맵입니다(이 맵을 직접 바꿉니다).
	 * @param data      합쳐 넣을 구조화 결과입니다.
	 */
	private void mergeVariables(Map<String, Object> variables, Map<String, Object> data) {
		if (data != null) {
			variables.putAll(data);
		}
	}

	/** 스텝 종류에 맞는 StepRunner를 찾아 돌려줍니다. @param type 러너를 찾을 스텝 종류입니다. */
	private StepRunner runnerFor(StepType type) {
		return switch (type) {
			case AGENT, SUPERVISOR, ROUTER -> this.agentStepRunner;
			case TOOL -> this.toolStepRunner;
			case APPROVAL -> this.approvalStepRunner;
		};
	}

	/**
	 * 방금 실행한 스텝의 결과를 보고, 다음에 어디로 가야 할지를 정합니다.
	 *
	 * 성공했는지 실패했는지를 판정하는 방식은 스텝 종류(StepType)마다 다릅니다.
	 *   - AGENT: structuredOutput이 false(기본값)면 항상 성공으로 봅니다. true면 StepPayload로
	 *     제대로 파싱됐는지 여부로 판정합니다.
	 *   - TOOL: ToolOutput.success() 값이 있으면 그걸 우선으로 쓰고, 없으면 응답 텍스트가 "실패:"로
	 *     시작하는지로 판단합니다(예전 방식과의 호환을 위해 남겨둔 규칙입니다).
	 *   - SUPERVISOR: 구조화된 Verdict의 pass 값(true/false)으로 판정합니다.
	 *   - APPROVAL: 사람이 승인했는지 반려했는지로 정해집니다.
	 *   - ROUTER: 성공/실패가 아니라, LLM이 고른 route(이름표)로 다음 step을 정합니다 - onSuccess/onFailure는 아예 보지 않습니다.
	 *
	 * @param workflow 지금 실행 중인 Workflow의 정의입니다.
	 * @param step     방금 실행한 스텝의 정의입니다(forEachVariable로 여러 번 돌았다면, 그 step 자체입니다).
	 * @param success  방금 실행 결과가 성공이었는지 여부입니다(ROUTER인 경우에는 이 값을 쓰지 않습니다).
	 * @param text     방금 실행한 결과 텍스트입니다.
	 * @param route    ROUTER가 고른 route입니다(ROUTER가 아니면 항상 null입니다).
	 */
	private StepFlow decideTransition(WorkFlowDefinition workflow, StepDefinition step, boolean success, String text, String route) {
		if (step.type() == StepType.ROUTER) {
			return this.decideRouterTransition(workflow, step, route, text);
		}

		// nextId: 성공이면 onSuccess에, 실패면 onFailure에 적어둔 값입니다. YAML에 적어두지 않았으면 null입니다.
		String nextId = success ? step.onSuccess() : step.onFailure();

		// nextId가 없는 경우만 여기서 바로 처리합니다("실패했는데 onFailure가 없는 경우"와 "성공했는데
		// onSuccess가 없는 경우" 둘 다, SUCCESS/FAIL 예약어나 다른 step id를 적어둔 경우와 달리
		// 성공/실패 여부 자체를 더 따져봐야 하기 때문입니다). 그 외의 경우(SUCCESS/FAIL 예약어, 또는
		// 진짜 다른 step id)는 resolveNextIdToFlow가 ROUTER와 똑같은 방식으로 처리해 줍니다.
		if (nextId == null) {
			if (!success) {
				// 실패했는데 onFailure가 없습니다 → 이 실패를 이어받을 곳이 없으므로 Workflow 전체를 실패로 끝냅니다.
				return StepFlow.fail("step[" + step.id() + "]가 실패했고 onFailure가 지정되지 않았습니다: " + text);
			}
			// 성공했는데 onSuccess가 없습니다 → 목록상 그냥 다음 스텝으로 넘어가는, 가장 흔한 순차 실행입니다.
			String sequentialNextId = this.nextSequentialId(workflow.steps(), step.id());
			return sequentialNextId == null ? StepFlow.success(text) : StepFlow.next(sequentialNextId);
		}
		return this.resolveNextIdToFlow(workflow, step, nextId, text);
	}

	/**
	 * ROUTER step 전용 로직입니다. onSuccess/onFailure 대신, routes(route 이름 → 다음 step id
	 * 매핑)에서 LLM이 고른 route를 찾습니다. 만약 그 route가 routes 안에 없다면(LLM이 오타를 냈거나
	 * 없는 이름을 지어낸 경우), 더 이상 진행할 수 없으므로 예외를 던져서 알립니다(decideTransition의
	 * 다른 IllegalStateException들과 마찬가지로, 이 예외도 run()이 잡아서 FAILED로 곱게 정리합니다).
	 *
	 * @param workflow 지금 실행 중인 Workflow의 정의입니다.
	 * @param step     방금 실행한 ROUTER step의 정의입니다.
	 * @param route    LLM이 고른 route입니다.
	 * @param text     방금 실행한 결과 텍스트입니다.
	 */
	private StepFlow decideRouterTransition(WorkFlowDefinition workflow, StepDefinition step, String route, String text) {
		Map<String, String> routes = step.routes();
		String nextId = routes == null ? null : routes.get(route);
		if (nextId == null) {
			Set<String> allowed = routes == null ? Set.of() : routes.keySet();
			throw new IllegalStateException("step[" + step.id() + "]: route['" + route + "']가 routes에 정의되어 있지 않습니다(정의된 route=" + allowed + ").");
		}
		return this.resolveNextIdToFlow(workflow, step, nextId, text);
	}

	/**
	 * "이 step 다음엔 nextId로 가라"는 값(SUCCESS/FAIL 예약어, 또는 진짜 다른 step의 id)을 실제
	 * StepFlow 값으로 바꿔줍니다. decideTransition(onSuccess/onFailure에 적힌 값을 처리)과
	 * decideRouterTransition(routes에서 찾은 값을 처리) 둘 다 결국 이 메서드로 모입니다 - "다음
	 * step id 문자열 하나를 어떻게 StepFlow로 바꾸는가"라는 로직 자체는 두 경로에서 완전히 똑같기
	 * 때문입니다.
	 *
	 * @param workflow 지금 실행 중인 Workflow의 정의입니다.
	 * @param step     기준이 되는, 지금 막 끝난 step의 정의입니다.
	 * @param nextId   SUCCESS/FAIL 예약어이거나, 다른 step의 id입니다.
	 * @param text     성공했거나 실패했을 때 남길 텍스트입니다.
	 */
	private StepFlow resolveNextIdToFlow(WorkFlowDefinition workflow, StepDefinition step, String nextId, String text) {
		if (Constants.WorkFlow.SUCCESS_SENTINEL.equals(nextId)) {
			// 예약어 "SUCCESS"가 적혀 있습니다 → 그 자리에서 바로 Workflow를 성공으로 끝냅니다.
			return StepFlow.success(text);
		}
		if (Constants.WorkFlow.FAIL_SENTINEL.equals(nextId)) {
			// 예약어 "FAIL"이 적혀 있습니다 → 그 자리에서 바로 Workflow를 실패로 끝냅니다.
			return StepFlow.fail(text);
		}
		// 예약어가 아니라 진짜 다른 step의 id가 적혀 있습니다(명시적인 분기이거나 재시도 루프입니다) → 그 step으로 이동해야 합니다.
		int currentIndex = this.indexOf(workflow.steps(), step.id());
		int nextIndex = this.indexOf(workflow.steps(), nextId);
		if (nextIndex < 0) {
			// 그런 id를 가진 step이 이 Workflow 안에 없습니다(YAML에 오타가 있는 경우 등) → 더 진행할 수 없으므로 예외로 알립니다.
			// (이 예외 역시 run()이 잡아서 FAILED로 곱게 정리합니다 - 위쪽 run()의 주석 참고)
			throw new IllegalStateException("workflow[" + workflow.id() + "]에 없는 step id로 이동하려 했습니다: " + nextId);
		}
		if (nextIndex <= currentIndex) {
			// 목표 step이 지금 step과 같거나 목록상 더 앞에 있습니다 → 뒤로 되돌아가는 것이므로 LOOP(재시도)로 봅니다.
			return StepFlow.loop(nextId);
		}
		// 목표 step이 지금 step보다 목록상 더 뒤에 있습니다 → 앞으로 나아가는 것이므로 NEXT_STEP으로 봅니다.
		return StepFlow.next(nextId);
	}

	/**
	 * 목록에서 currentId 바로 다음에 있는 step의 id를 돌려줍니다. currentId가 마지막 step이면
	 * null을 돌려주는데, 이는 Workflow가 여기서 끝난다는 뜻입니다.
	 *
	 * @param steps     전체 step 목록입니다.
	 * @param currentId 기준이 되는, 지금 step의 id입니다.
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
	 * 목록에서 주어진 id를 가진 step이 몇 번째에 있는지 찾습니다.
	 *
	 * @param steps 전체 step 목록입니다.
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
	 * 스텝(또는 forEach의 반복) 하나를 실행한 뒤의 결과를, run() 루프에 돌려주기 위해 담아두는
	 * 내부 값입니다. pending이 true면 APPROVAL 대기 중이라는 뜻이고, 이때는 나머지 필드 값은
	 * 의미가 없습니다.
	 *
	 * @param pending      승인 대기로 멈춰야 하는 상황인지 여부입니다.
	 * @param success      성공했는지 여부입니다(pending이면 이 값은 무시되고, ROUTER면 항상 true입니다).
	 * @param combinedText 결합된 결과 텍스트입니다(pending이면 무시됩니다).
	 * @param mergedData   Workflow 변수에 합쳐 넣을 구조화 결과입니다(pending이면 무시됩니다).
	 * @param route        ROUTER가 고른 route입니다(ROUTER가 아니면 항상 null입니다).
	 */
	private record StepRunResult(boolean pending, boolean success, String combinedText, Map<String, Object> mergedData, String route) {

		private static StepRunResult pendingResult() {
			return new StepRunResult(true, false, null, Map.of(), null);
		}
	}

}

package net.dstone.ai.runtime.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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
 * Workflow를 진행하는 핵심 클래스. WorkFlowDefinition을 순차/분기/병렬/루프 4패턴으로 실행한다. Workflow의 큰 흐름(어떤 step 다음에 어떤 step, 언제 멈출지)은 이
 * 클래스가 통제하고, step 하나하나의 지능적인 판단은 각 StepRunner를 거쳐 결국 LLM에게 맡긴다. 별도 워크플로우 그래프 엔진을 새로 설계하지 않고, "지금 step의 순번 → 다음에
 * 실행할 step의 순번"을 계속 따라가는 단순한 상태 기계로 구현했다.
 *
 * 모든 StepType(AGENT/TOOL/SUPERVISOR/APPROVAL)을 StepRunner 인터페이스 하나로 균일하게 호출하고(runtime.step.StepRunner 참고),
 * 스텝(또는 병렬 그룹) 하나를 처리할 때마다 WorkFlowExecutionStore로 상태를 즉시 영속화한다 - 그래서 APPROVAL 스텝에서 멈추더라도,
 * 또는 서버가 중간에 재기동되더라도 마지막으로 끝낸 스텝부터 다시 이어갈 수 있다.
 *
 * StepDefinition.onSuccess/onFailure로 지정한 다음 step id가 지금 step보다 앞에 있으면 LOOP로, 뒤에 있으면 NEXT_STEP으로 본다(둘 다 동작은 같다 -
 * StepStatus는 로그/가독성을 위한 구분일 뿐이고, 실제 무한루프 방지는 maxIterations 하나가 담당한다). onSuccess/onFailure에 예약어 "SUCCESS"/"FAIL"을 쓰면 그
 * 자리에서 Workflow를 즉시 종료한다.
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
	 * 1. 기능 정의
	 * - execution.currentStepIndex()부터 이어서 Workflow를 실행한다. 새 실행이면 0부터, 승인 대기에서 재개하는 실행이면 멈췄던 그 스텝부터 다시 실행된다. 
	 *   SUCCESS/FAIL/WAITING_APPROVAL 중 하나에 도달하면 그 상태로 저장하고 돌려준다.
	 * 
	 * - YAML로 정의된 Workflow(steps 목록)를 처음부터 끝까지(또는 멈춰야 할 때까지) 한 스텝씩 실행하는 엔진으로 
	 *   LOOP["지금 몇 번째 step인가?" → "그 step을 실행한다" → "성공/실패에 따라 다음 step 번호를 계산한다"]
	 *   이 단순한 루프만으로 순차 실행 / 분기 / 병렬 실행 / 루프(재시도) 4가지 패턴을 전부 처리한다.
	 *   
	 * 2. 호출 시점
	 * - 신규실행: 
	 *          사용자가 POST /api/ai/workflow/{id}/execute(동기) 또는 /submit(비동기)을 호출 
	 *          → WorkFlowExecution.start(...)로 currentStepIndex=0, status=RUNNING인 새 실행이 만들어지고 
	 *          → run() 호출 
	 * - 승인(APPROVAL)재개: 
	 *          이전에 run()이 WAITING_APPROVAL 상태로 멈춰서 리턴한 실행 건에 대해, 나중에 사람이 승인/반려 결정을 내리면 
	 *          → 그 결정이 variables.approvals.{stepId}에 기록된 뒤 
	 *          → 같은 실행(같은 executionId, 같은 currentStepIndex) 을 가지고 run()이 다시 호출됨 
	 * </pre>
	 *
	 * @param workflow  실행할 Workflow 정의. YAML에서 읽어온 설계도. steps 목록, maxIterations(반복 상한) 등을 담음.
	 *                  WorkFlowExecution은 불변 객체(record). 
	 *                  상태가 바뀔 때마다(advanceTo/done/failed/waitingApproval) 새 인스턴스를 만들어서 돌려주는 방식이라, 
	 *                  "지금 이 실행이 어떤 상태였는지"를 어느 시점에 봐도 안전하게 추적할 수 있다. 
	 *                  다만 variables 맵만은 예외적으로 같은 Map 인스턴스를 계속 공유해서, 스텝들이 값을 계속 누적해 넣을 수 있음.
	 * @param execution 지금 진행 중인 실행 1건(신규면 currentStepIndex=0, 재개면 멈췄던 스텝)
	 */
	public WorkFlowExecution run(WorkFlowDefinition workflow, WorkFlowExecution execution) {
		int maxIterations = workflow.maxIterations() == null ? Constants.WorkFlow.DEFAULT_MAX_ITERATIONS : workflow.maxIterations();
		int currentIndex = execution.currentStepIndex(); 
		WorkFlowExecution current = execution;
		int executed = 0;

		while (true) {
			
			/****************************************************************************************
			1) 실행 횟수 체크 → maxIterations 초과 시 FAILED로 종료
			****************************************************************************************/
			if (++executed > maxIterations) {
				return this.persistFailed(current, "최대 실행 횟수(" + maxIterations + ")를 초과했습니다(루프 정지) - onFailure로 되돌아가는 step 구성을 다시 확인하십시오.");
			}

			/****************************************************************************************
			2) 지금 step 번호(currentIndex)로 StepDefinition 조회
			****************************************************************************************/
			StepDefinition step = workflow.steps().get(currentIndex);

			/****************************************************************************************
			3) 같은 parallelGroup을 가진 인접 step이 있으면 묶어서 "그룹"으로 취급
			****************************************************************************************/
			List<StepDefinition> group = this.parallelGroupOf(workflow.steps(), step);
			
			/****************************************************************************************
			4) 핵심로직(Run)수행. 그룹 크기가 1이면 runOne(), 2개 이상이면 runGroup() 실행 
			****************************************************************************************/
			GroupResult groupResult;
			try {
				groupResult = group.size() > 1 ? this.runGroup(group, current) : this.runOne(step, current);
			} catch (Exception e) {
				// 실행 중 예외 발생 → 그 자리에서 FAILED로 종료
				return this.persistFailed(current, "step[" + step.id() + "] 실행 중 예외가 발생했습니다 - " + e.getMessage());
			}

			/****************************************************************************************
			5) 결과가 PENDING(APPROVAL 대기)이면
			****************************************************************************************/
			if (groupResult.pending()) {
				// WAITING_APPROVAL 상태로 저장하고 즉시 리턴 (루프 탈출)
				current = current.waitingApproval(currentIndex);
				this.executionStore.update(current);
				return current;
			}

			/****************************************************************************************
			6) 결과 데이터를 variables에 병합, 결과 텍스트를 "__previous"에 저장
			****************************************************************************************/
			this.mergeVariables(current.variables(), groupResult.mergedData());
			current.variables().put(Constants.WorkFlow.PREVIOUS_TEXT_VARIABLE_KEY, groupResult.combinedText());

			/****************************************************************************************
			7) decideTransition()으로 다음 행동 결정
			  - 병렬 그룹이었다면 그룹의 마지막 스텝(선언 순서 기준)의 onSuccess/onFailure만 보고 다음 행동을 결정함.
			    그룹 안의 다른 스텝들의 onSuccess/onFailure는 무시되므로 /workflow/*.yml 에서 병렬 그룹을 만들 때 이 점을 염두에 둬야 함.
			  - onSuccess/onFailure에 오타로 존재하지 않는 step id를 적어두면 decideTransition()이 예외를 던지는데,
			    4)번 스텝 실행 예외와 똑같이 여기서도 잡아서 FAILED로 곱게 남긴다 - 이 예외가 run() 밖으로
			    그냥 새나가서 WorkFlowExecution 상태가 갱신 안 된 채로 남는 일이 없도록 하기 위함이다.
			****************************************************************************************/
			StepDefinition lastOfGroup = group.get(group.size() - 1);
			StepFlow transition;
			try {
				transition = this.decideTransition(workflow, lastOfGroup, groupResult.success(), groupResult.combinedText());
			} catch (Exception e) {
				return this.persistFailed(current, "step[" + lastOfGroup.id() + "]의 다음 전이(transition)를 계산하는 중 예외가 발생했습니다 - " + e.getMessage());
			}

			switch (transition.status()) {
				/*************************************
				SUCCESS : DONE 으로 저장하고 리턴 (루프 탈출)	
					- Workflow 전체가 성공적으로 끝남
					- status=DONE, resultText에 마지막 결과 텍스트 저장 후 리턴
				*************************************/
				case SUCCESS: 
					current = current.done(transition.message());
					this.executionStore.update(current);
					return current;
				/*************************************
				FAIL : FAILED로 저장하고 리턴 (루프 탈출)	
					- Workflow 전체가 실패로 끝남
					- status=FAILED, errorMessage에 실패 사유 저장 후 리턴
				*************************************/
				case FAIL:
					return this.persistFailed(current, transition.message());
				/*************************************
				NEXT_STEP, LOOP : RUNNING 저장 후 이동(루프 지속)	
					- 앞으로 있는 다른 스텝으로 이동
					- currentIndex 갱신, status=RUNNING 저장 후 while 루프 계속
					- NEXT_STEP 과 LOOP 의 동작은 완전히 동일 — 로그/가독성 구분용일 뿐
				*************************************/
				case NEXT_STEP, LOOP:
					currentIndex = this.indexOf(workflow.steps(), transition.nextStepId());
					current = current.advanceTo(currentIndex);
					this.executionStore.update(current);
					break;
				/*************************************
				나머지 ERROR/WAITING_APPROVAL 
					- ERROR/WAITING_APPROVAL은 decideTransition이 만들어내지 않는다.
					  각각 위의 catch, groupResult.pending()에서 먼저 처리됨.
					- 컴파일러의 switch 완결성 요구를 맞추기 위한 방어적 분기일 뿐 실제로 타지 않는다.
				*************************************/
				default:
					return this.persistFailed(current, "알 수 없는 스텝 전이 상태입니다: " + transition.status());
			}
		}
	}

	/**
	 * @param execution    FAILED로 남길 실행 상태
	 * @param errorMessage 실패/에러 사유
	 */
	private WorkFlowExecution persistFailed(WorkFlowExecution execution, String errorMessage) {
		WorkFlowExecution failed = execution.failed(errorMessage);
		this.executionStore.update(failed);
		return failed;
	}

	/**
	 * <pre>
	 * 병렬 그룹이 아닌 스텝 하나를 실행한다. APPROVAL 스텝이 아직 결정을 못 받았으면 PENDING을 그대로 돌려준다(GroupResult.pending()).
	 * </pre>
	 *
	 * @param step      실행할 스텝 정의
	 * @param execution 지금 실행 상태
	 */
	private GroupResult runOne(StepDefinition step, WorkFlowExecution execution) {
		StepInput input = new StepInput(this.previousText(execution), execution.variables());
		long start = System.nanoTime();
		StepOutput output = this.runnerFor(step.type()).run(execution, step, input);
		long durationMs = (System.nanoTime() - start) / 1_000_000;

		if (output.result() == StepResult.PENDING) {
			return GroupResult.pendingResult();
		}

		boolean success = output.result() == StepResult.SUCCESS;
		this.executionStore.appendHistory(execution.executionId(), new StepHistoryEntry(step.id(), step.type(), step.ref(), success, durationMs, success ? output.primaryText() : null, output.failureReason(), Instant.now()));
		return new GroupResult(false, success, output.primaryText(), output.data() == null ? Map.of() : output.data());
	}

	/**
	 * <pre>
	 * 같은 parallelGroup 값을 가진 인접 스텝들을 CompletableFuture로 동시에 실행한다. 하나라도 실패하면 그룹 전체가 실패로 간주되고,
	 * 결과 텍스트는 선언 순서대로 줄바꿈으로 이어붙인다. 실행 자체는 동시에 일어나지만 결과를 모으는 이 루프는 항상 그룹의 선언
	 * 순서대로 돌기 때문에, data 병합 순서와 결합 텍스트 순서는 매번 결정적이다.
	 * </pre>
	 *
	 * @param group     동시 실행할 병렬 스텝 그룹(선언 순서 그대로)
	 * @param execution 지금 실행 상태
	 */
	private GroupResult runGroup(List<StepDefinition> group, WorkFlowExecution execution) {
		StepInput input = new StepInput(this.previousText(execution), execution.variables());
		Map<StepDefinition, CompletableFuture<StepOutput>> futures = new LinkedHashMap<>();
		for (StepDefinition step : group) {
			final StepDefinition groupStep = step;
			futures.put(step, CompletableFuture.supplyAsync(new Supplier<StepOutput>() {
				@Override
				public StepOutput get() {
					return WorkFlowExecutor.this.runnerFor(groupStep.type()).run(execution, groupStep, input);
				}
			}));
		}

		boolean allSuccess = true;
		boolean anyFailed = false;
		StringBuilder combinedText = new StringBuilder();
		Map<String, Object> mergedData = new LinkedHashMap<>();

		for (Map.Entry<StepDefinition, CompletableFuture<StepOutput>> entry : futures.entrySet()) {
			StepDefinition step = entry.getKey();
			long start = System.nanoTime();
			StepOutput output;
			try {
				output = entry.getValue().join();
			} catch (Exception e) {
				this.executionStore.appendHistory(execution.executionId(), new StepHistoryEntry(step.id(), step.type(), step.ref(), false, (System.nanoTime() - start) / 1_000_000, null, e.getMessage(), Instant.now()));
				anyFailed = true;
				allSuccess = false;
				continue;
			}
			long durationMs = (System.nanoTime() - start) / 1_000_000;
			boolean success = output.result() == StepResult.SUCCESS;
			allSuccess &= success;
			this.executionStore.appendHistory(execution.executionId(), new StepHistoryEntry(step.id(), step.type(), step.ref(), success, durationMs, success ? output.primaryText() : null, output.failureReason(), Instant.now()));
			if (combinedText.length() > 0) {
				combinedText.append("\n");
			}
			combinedText.append(output.primaryText());
			if (output.data() != null) {
				mergedData.putAll(output.data());
			}
		}

		if (anyFailed) {
			// 형제 중 예외를 던진 게 있으면 이 그룹 전체를 실패로 취급한다는 사실을 상위 run()의 catch가 아니라
			// 여기서 이미 반영한다(allSuccess=false) - 예외를 다시 던지지 않고 결과로 흡수해서, 이미 끝난 다른
			// 형제 스텝들의 기록(appendHistory)이 함께 남도록 한다.
			return new GroupResult(false, false, combinedText.toString(), mergedData);
		}
		return new GroupResult(false, allSuccess, combinedText.toString(), mergedData);
	}

	/** @param execution 직전 스텝 결과 텍스트를 꺼낼 실행 상태 */
	private String previousText(WorkFlowExecution execution) {
		Object value = execution.variables().get(Constants.WorkFlow.PREVIOUS_TEXT_VARIABLE_KEY);
		return value == null ? null : value.toString();
	}

	/**
	 * @param variables 병합 대상 Workflow 전역 변수 맵(직접 변경됨)
	 * @param data      병합할 구조화 결과
	 */
	private void mergeVariables(Map<String, Object> variables, Map<String, Object> data) {
		if (data != null) {
			variables.putAll(data);
		}
	}

	/** @param type 러너를 찾을 스텝 종류 */
	private StepRunner runnerFor(StepType type) {
		return switch (type) {
			case AGENT, SUPERVISOR -> this.agentStepRunner;
			case TOOL -> this.toolStepRunner;
			case APPROVAL -> this.approvalStepRunner;
		};
	}

	/**
	 * <pre>
	 * 방금 실행한 스텝의 결과에 따라 다음 전환에 대한 전환 방향을 결정하는 메소드.
	 * 성공/실패는 스텝 종류(StepType)마다 판정 방식이 다름. 
	 *   - AGENT는 항상 성공
	 *   - TOOL은 응답 텍스트가 "실패:"로 시작하면 실패. 아니면 성공.
	 *   - SUPERVISOR는 구조화된 Verdict.pass(true/false)로 판단.
	 *   - APPROVAL은 사람의 승인/반려로 정해짐.
	 * </pre>
	 * 
	 * @param workflow 실행 중인 Workflow 정의
	 * @param step     방금 실행한 스텝(그룹이었다면 그룹의 마지막 스텝) 정의
	 * @param success  방금 실행 결과의 성공 여부
	 * @param text     방금 실행 결과 텍스트
	 */
	private StepFlow decideTransition(WorkFlowDefinition workflow, StepDefinition step, boolean success, String text) {

		// nextId : 성공이면 onSuccess에, 실패면 onFailure에 적어둔 값. YAML에 안 적었으면 null.
		String nextId = success ? step.onSuccess() : step.onFailure();

		// 아래는 "nextId가 없다 / SUCCESS 예약어다 / FAIL 예약어다 / 그 외(진짜 다른 step id)다" 이 4가지 경우를
		// if~else if~else 하나로 쭉 이어서, 어떤 경우든 반드시 이 사슬 중 하나에는 걸리도록(빠져나가는 틈이 없도록) 정리했다.
		// 각 가지의 안쪽도 전부 if~else 쌍으로만 구성해서, "이 if는 참일 때만 처리하고 거짓일 때는 그냥 넘어간다" 같은
		// 애매한 지점이 하나도 없게 했다(거짓일 때 뭘 해야 하는지가 항상 else 쪽에 명시적으로 적혀 있다).
		if (nextId == null) {
			// [경우 1] 다음 step id를 아예 안 적어둔 경우 - 실패/성공 여부로 다시 나뉜다.
			if (!success) {
				// [경우 1-1] 실패했는데 onFailure가 없다 → 이 실패를 이어받을 곳이 없으니 그대로 Workflow 실패.
				return StepFlow.fail("step[" + step.id() + "]가 실패했고 onFailure가 지정되지 않았습니다: " + text);
			} else {
				// [경우 1-2] 성공했는데 onSuccess가 없다 → "그냥 목록상 다음 스텝으로" 넘어가는 가장 흔한 순차 실행.
				String sequentialNextId = this.nextSequentialId(workflow.steps(), step.id());
				if (sequentialNextId == null) {
					// 목록상 다음 스텝이 없다(=지금 스텝이 마지막) → Workflow 전체 성공으로 종료.
					return StepFlow.success(text);
				} else {
					// 목록상 다음 스텝이 있다 → 그 스텝으로 진행.
					return StepFlow.next(sequentialNextId);
				}
			}
		} else if (Constants.WorkFlow.SUCCESS_SENTINEL.equals(nextId)) {
			// [경우 2] 예약어 "SUCCESS"를 적어둔 경우 → 그 자리에서 바로 Workflow 성공 종료.
			return StepFlow.success(text);
		} else if (Constants.WorkFlow.FAIL_SENTINEL.equals(nextId)) {
			// [경우 3] 예약어 "FAIL"을 적어둔 경우 → 그 자리에서 바로 Workflow 실패 종료.
			return StepFlow.fail(text);
		} else {
			// [경우 4] 예약어가 아닌, 진짜 다른 step의 id를 적어둔 경우(명시적 분기/재시도 루프) → 그 step으로 이동해야 한다.
			int currentIndex = this.indexOf(workflow.steps(), step.id());
			int nextIndex = this.indexOf(workflow.steps(), nextId);
			if (nextIndex < 0) {
				// 그런 id를 가진 step이 workflow 안에 없다(YAML 오타 등) → 더는 진행할 수 없으니 예외로 알린다.
				// (이 예외는 run()이 catch해서 FAILED로 곱게 남긴다 - 아래 run() 쪽 주석 참고)
				throw new IllegalStateException("workflow[" + workflow.id() + "]에 없는 step id로 이동하려 했습니다: " + nextId);
			} else {
				if (nextIndex <= currentIndex) {
					// 목표 step이 지금 step과 같거나 목록상 더 앞에 있다 → 뒤로 되돌아가는 것이니 LOOP(재시도).
					return StepFlow.loop(nextId);
				} else {
					// 목표 step이 지금 step보다 목록상 더 뒤에 있다 → 앞으로 나아가는 것이니 NEXT_STEP.
					return StepFlow.next(nextId);
				}
			}
		}
	}

	/**
	 * <pre>
	 * step이 parallelGroup을 갖고 있으면 같은 그룹의 인접 step 전체를, 아니면 자기 자신만 담은 목록을 돌려준다.
	 * </pre>
	 *
	 * @param steps 전체 step 목록
	 * @param step  그룹을 찾을 기준 step
	 */
	private List<StepDefinition> parallelGroupOf(List<StepDefinition> steps, StepDefinition step) {
		if (StringUtil.isEmpty(step.parallelGroup())) {
			return List.of(step);
		}
		List<StepDefinition> group = new ArrayList<>();
		for (StepDefinition candidate : steps) {
			if (step.parallelGroup().equals(candidate.parallelGroup())) {
				group.add(candidate);
			}
		}
		return group;
	}

	/**
	 * <pre>
	 * 목록상 currentId 바로 다음 step의 id를 돌려준다 - currentId가 마지막이면 null(=Workflow 종료).
	 * </pre>
	 *
	 * @param steps     전체 step 목록
	 * @param currentId 기준이 되는 현재 step id
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
	 * @param steps 전체 step 목록
	 * @param id    찾을 step id
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
	 * 스텝(또는 병렬 그룹) 하나를 실행한 뒤의 결과를 run() 루프에 돌려주기 위한 내부 값. pending=true면 나머지 필드는 의미가 없다(APPROVAL
	 * 대기 중이라는 뜻).
	 *
	 * @param pending    승인 대기로 멈춰야 하는지 여부
	 * @param success    성공 여부(pending이면 무시)
	 * @param combinedText 결합된 결과 텍스트(pending이면 무시)
	 * @param mergedData 병합할 구조화 결과(pending이면 무시)
	 */
	private record GroupResult(boolean pending, boolean success, String combinedText, Map<String, Object> mergedData) {

		private static GroupResult pendingResult() {
			return new GroupResult(true, false, null, Map.of());
		}
	}

}

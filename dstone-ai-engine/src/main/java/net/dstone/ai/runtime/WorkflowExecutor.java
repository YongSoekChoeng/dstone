package net.dstone.ai.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.common.definition.WorkflowDefinition;
import net.dstone.ai.runtime.step.AgentStepRunner;
import net.dstone.ai.runtime.step.RagStepRunner;
import net.dstone.ai.runtime.step.ToolStepRunner;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * WorkflowDefinition을 순차/분기/병렬/루프 4패턴으로 실행한다 - Workflow의 큰 흐름(어떤 step 다음에
 * 어떤 step, 언제 멈출지)은 이 클래스가 통제하고, step 하나하나의 지능적인 판단은 각 StepType 러너를
 * 거쳐 결국 LLM에게 맡긴다. 별도 워크플로우 그래프 엔진을 새로 설계하지 않고, "지금 step의 id → 다음에
 * 실행할 step의 id"를 계속 따라가는 단순한 상태 기계로 구현했다.
 *
 * StepDefinition.onSuccess/onFailure로 지정한 다음 step id가 지금 step보다 앞에 있으면 LOOP로,
 * 뒤에 있으면 NEXT_STEP으로 본다(둘 다 동작은 같다 - StepStatus는 로그/가독성을 위한 구분일 뿐이고,
 * 실제 무한루프 방지는 maxIterations 하나가 담당한다). onSuccess/onFailure에 예약어 "SUCCESS"/"FAIL"을
 * 쓰면 그 자리에서 Workflow를 즉시 종료한다.
 */
@Component
public class WorkflowExecutor extends BaseObject {

	private static final int DEFAULT_MAX_ITERATIONS = 5;
	private static final String SUCCESS_SENTINEL = "SUCCESS";
	private static final String FAIL_SENTINEL = "FAIL";

	@Autowired
	private AgentStepRunner agentStepRunner;
	@Autowired
	private ToolStepRunner toolStepRunner;
	@Autowired
	private RagStepRunner ragStepRunner;

	/**
	 * name으로 등록된 Workflow를 실행하고, 마지막 step 결과가 담긴 WorkflowContext를 돌려준다.
	 * @param workflow 실행할 Workflow 정의
	 * @param sessionId 대화 세션 식별자
	 * @param caller 호출 주체(caller) 식별자
	 * @param variables Workflow 호출 시 넘겨받은 변수 맵
	 * @param initialInput Workflow 최초 입력값
	 */
	public WorkflowContext run(WorkflowDefinition workflow, String sessionId, String caller, Map<String, Object> variables,
			String initialInput) {
		WorkflowContext context = new WorkflowContext();
		context.put("input", initialInput);
		context.put("result", initialInput);
		context.put("variables", variables);

		Map<String, StepDefinition> stepsById = new LinkedHashMap<>();
		for (StepDefinition step : workflow.steps()) {
			stepsById.put(step.id(), step);
		}
		int maxIterations = workflow.maxIterations() == null ? DEFAULT_MAX_ITERATIONS : workflow.maxIterations();

		String currentId = workflow.steps().get(0).id();
		int executed = 0;

		while (currentId != null) {
			if (++executed > maxIterations) {
				throw new IllegalStateException("workflow[" + workflow.id() + "]가 최대 실행 횟수(" + maxIterations + ")를 초과했습니다(루프 정지) - onFailure로 되돌아가는 step 구성을 다시 확인하십시오.");
			}
			StepDefinition step = stepsById.get(currentId);
			if (step == null) {
				throw new IllegalStateException("workflow[" + workflow.id() + "]에 없는 step id로 이동하려 했습니다: " + currentId);
			}

			List<StepDefinition> group = this.parallelGroupOf(workflow.steps(), step);
			StepOutcome outcome;
			if (group.size() > 1) {
				outcome = this.runParallel(group, sessionId, caller, context);
				step = group.get(group.size() - 1); // 다음 step 결정은 그룹의 마지막 step 기준
			}
			else {
				outcome = this.runStep(step, sessionId, caller, context);
			}
			context.put("result", outcome.text());

			StepResult transition = this.decideTransition(workflow, step, outcome);
			switch (transition.status()) {
				case SUCCESS -> {
					return context;
				}
				case FAIL -> throw new IllegalStateException("workflow[" + workflow.id() + "] 실패: " + transition.message());
				case NEXT_STEP, LOOP -> currentId = transition.nextStepId();
			}
		}
		return context;
	}

	/**
	 * @param step 실행할 step 정의
	 * @param sessionId 대화 세션 식별자
	 * @param caller 호출 주체(caller) 식별자
	 * @param context step들이 공유하는 실행 컨텍스트
	 */
	private StepOutcome runStep(StepDefinition step, String sessionId, String caller, WorkflowContext context) {
		String input = context.<String>get("result");
		Map<String, Object> variables = context.get("variables");
		return switch (step.type()) {
			case AGENT -> this.agentStepRunner.runAgent(step.ref(), sessionId, caller, variables, input);
			case SUPERVISOR -> this.agentStepRunner.runSupervisor(step.ref(), sessionId, caller, variables, input);
			case RAG -> new StepOutcome(true, this.ragStepRunner.run(caller, input));
			case TOOL -> this.toolStepRunner.run(step, caller, variables, input);
		};
	}

	/**
	 * @param workflow 실행 중인 Workflow 정의
	 * @param step 방금 실행한 step 정의
	 * @param outcome 방금 실행한 step의 결과
	 */
	private StepResult decideTransition(WorkflowDefinition workflow, StepDefinition step, StepOutcome outcome) {
		String nextId = outcome.success() ? step.onSuccess() : step.onFailure();
		if (nextId == null) {
			if (!outcome.success()) {
				return StepResult.fail("step[" + step.id() + "]가 실패했고 onFailure가 지정되지 않았습니다: " + outcome.text());
			}
			String sequentialNextId = this.nextSequentialId(workflow.steps(), step.id());
			return sequentialNextId == null ? StepResult.success(outcome.text()) : StepResult.next(sequentialNextId);
		}
		if (SUCCESS_SENTINEL.equals(nextId)) {
			return StepResult.success(outcome.text());
		}
		if (FAIL_SENTINEL.equals(nextId)) {
			return StepResult.fail(outcome.text());
		}
		int currentIndex = this.indexOf(workflow.steps(), step.id());
		int nextIndex = this.indexOf(workflow.steps(), nextId);
		if (nextIndex < 0) {
			throw new IllegalStateException("workflow[" + workflow.id() + "]에 없는 step id로 이동하려 했습니다: " + nextId);
		}
		return nextIndex <= currentIndex ? StepResult.loop(nextId) : StepResult.next(nextId);
	}

	/**
	 * step이 parallelGroup을 갖고 있으면 같은 그룹의 인접 step 전체를, 아니면 자기 자신만 담은 목록을 돌려준다.
	 * @param steps 전체 step 목록
	 * @param step 그룹을 찾을 기준 step
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
	 * @param group 동시 실행할 병렬 step 그룹
	 * @param sessionId 대화 세션 식별자
	 * @param caller 호출 주체(caller) 식별자
	 * @param context step들이 공유하는 실행 컨텍스트
	 */
	private StepOutcome runParallel(List<StepDefinition> group, String sessionId, String caller, WorkflowContext context) {
		Map<String, CompletableFuture<StepOutcome>> futures = new LinkedHashMap<>();
		for (StepDefinition step : group) {
			final StepDefinition currentStep = step;
			futures.put(step.id(), CompletableFuture.supplyAsync(new Supplier<StepOutcome>() {
				@Override
				public StepOutcome get() {
					return WorkflowExecutor.this.runStep(currentStep, sessionId, caller, context);
				}
			}));
		}
		CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();

		StringBuilder combinedText = new StringBuilder();
		boolean allSuccess = true;
		boolean first = true;
		for (CompletableFuture<StepOutcome> future : futures.values()) {
			StepOutcome outcome = future.join();
			if (!first) {
				combinedText.append("\n");
			}
			combinedText.append(outcome.text());
			first = false;
			if (!outcome.success()) {
				allSuccess = false;
			}
		}
		return new StepOutcome(allSuccess, combinedText.toString());
	}

	/**
	 * 목록상 currentId 바로 다음 step의 id를 돌려준다 - currentId가 마지막이면 null(=Workflow 종료).
	 * @param steps 전체 step 목록
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
	 * @param id 찾을 step id
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

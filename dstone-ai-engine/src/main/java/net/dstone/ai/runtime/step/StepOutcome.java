package net.dstone.ai.runtime.step;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dstone.ai.common.consts.Constants.WorkFlow.Context;

/**
 * <pre>
 * step 하나를 실행한 결과입니다. 각 StepExecutor가 만들어 돌려주고, runtime.workflow.WorkFlowExecutor가
 * 컨텍스트의 steps.{stepId}에 그대로 남깁니다(toRecord()). 그래서 이 record의 input/output/error가 곧
 * 다음 step들이 {{steps.id.input}}, {{steps.id.output}}, {{steps.id.error}}로 꺼내 쓰는 값입니다. 그 밖의 숨은 값은 없습니다.
 *
 * StepExecutor는 success()/failure()/routed()/waitingApproval()로 결과만 만들고, 실제로 넘긴 입력(input)과 걸린 시간(durationMs)은
 * WorkFlowExecutor가 withCall()로 채웁니다. forEach step의 결과는 WorkFlowExecutor가 반복별 결과를 모아 forEach()로 만듭니다.
 * </pre>
 *
 * @param success    성공했는지 여부입니다. ROUTER가 경로를 고른 것도 성공입니다.
 * @param pending    APPROVAL step이 사람의 결정을 기다리는 중인지 여부입니다. true면 나머지 값은 모두 비어 있습니다.
 * @param input      이 step이 실제로 받은 입력입니다(템플릿을 채운 뒤의 값. 글자, 맵 등).
 * @param output     이 step이 돌려준 값입니다(Agent output 모양, Tool 응답, 엔진이 정한 모양 등). 없으면 null입니다.
 * @param error      실패 사유입니다. 성공이면 null입니다.
 * @param route      ROUTER step이 고른 경로 이름입니다. ROUTER가 아니면 null입니다(다음 step을 정할 때만 쓰고 컨텍스트에는 output.route로 남습니다).
 * @param durationMs 이 step을 처리하는 데 걸린 시간(밀리초)입니다. 실행 이력에 남깁니다.
 */
public record StepOutcome(
	boolean success
	, boolean pending
	, Object input
	, Object output
	, String error
	, String route
	, long durationMs
	) {

	/**
	 * 성공했을 때 씁니다.
	 *
	 * @param output 이 step이 돌려준 값입니다.
	 */
	public static StepOutcome success(Object output) {
		return new StepOutcome(true, false, null, output, null, null, 0L);
	}

	/**
	 * ROUTER step이 경로를 골랐을 때 씁니다. 그 route를 실제로 어느 step으로 이어줄지는
	 * runtime.workflow.WorkFlowExecutor가 RouterStepDefinition.routes를 보고 정합니다.
	 *
	 * @param output 이 step이 돌려준 값입니다({route, reason}).
	 * @param route  고른 경로 이름입니다.
	 */
	public static StepOutcome routed(Object output, String route) {
		return new StepOutcome(true, false, null, output, null, route, 0L);
	}

	/**
	 * 실패했을 때 씁니다.
	 *
	 * @param output 실패하기 전까지 받은 값입니다(예: Tool이 돌려준 실패 응답, SUPERVISOR의 {pass: false, reason}). 없으면 null.
	 * @param error  왜 실패했는지에 대한 설명입니다.
	 */
	public static StepOutcome failure(Object output, String error) {
		return new StepOutcome(false, false, null, output, error, null, 0L);
	}

	/** 사람의 결정을 기다리는 중일 때 씁니다(ApprovalStepExecutor 전용). */
	public static StepOutcome waitingApproval() {
		return new StepOutcome(false, true, null, null, null, null, 0L);
	}

	/**
	 * forEach step의 결과를 만듭니다. input과 output은 반복 순서대로 모은 리스트입니다. 반복이 하나라도 실패하면
	 * 실패이고, error는 실패한 반복들의 사유를 줄바꿈으로 이은 값입니다.
	 *
	 * @param success    모든 반복이 성공했는지 여부입니다.
	 * @param inputs     반복별로 실제로 받은 입력 목록입니다.
	 * @param outputs    반복별로 돌려준 값 목록입니다(실패한 반복은 그 반복이 받은 값 또는 null).
	 * @param error      실패한 반복들의 사유를 이은 값입니다(모두 성공이면 null).
	 * @param durationMs 모든 반복이 끝날 때까지 걸린 시간(밀리초)입니다.
	 */
	public static StepOutcome forEach(boolean success, List<Object> inputs, List<Object> outputs, String error, long durationMs) {
		return new StepOutcome(success, false, inputs, outputs, error, null, durationMs);
	}

	/**
	 * 이 결과에 실제로 넘긴 입력과 걸린 시간을 채운 복사본을 돌려줍니다(WorkFlowExecutor가 씁니다).
	 *
	 * @param input      이 step이 실제로 받은 입력입니다.
	 * @param durationMs 걸린 시간(밀리초)입니다.
	 */
	public StepOutcome withCall(Object input, long durationMs) {
		return new StepOutcome(this.success, this.pending, input, this.output, this.error, this.route, durationMs);
	}

	/**
	 * 컨텍스트의 steps.{stepId}에 남길 모양({input, output, error})으로 바꿉니다.
	 */
	public Map<String, Object> toRecord() {
		Map<String, Object> record = new LinkedHashMap<>();
		record.put(Context.FIELD_INPUT, this.input);
		record.put(Context.FIELD_OUTPUT, this.output);
		record.put(Context.FIELD_ERROR, this.error);
		return record;
	}

}

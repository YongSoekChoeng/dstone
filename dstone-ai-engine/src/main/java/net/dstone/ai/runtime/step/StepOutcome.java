package net.dstone.ai.runtime.step;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dstone.ai.common.consts.Constants.WorkFlow.Context;

/**
 * <pre>
 * step 하나를 실행한 결과입니다. 각 StepExecutor가 만들어 돌려주고, runtime.workflow.WorkFlowExecutor가
 * 컨텍스트의 steps.{stepId}에 그대로 남깁니다(toRecord()). 그래서 이 record의 input/output/text/error/items가 곧
 * 다음 step들이 {{steps.id.input}}, {{steps.id.output.키}}, {{steps.id.text}}, {{steps.id.error}}, {{steps.id.items}}로
 * 꺼내 쓰는 값입니다.
 *
 * StepExecutor는 success()/failure()/routed()/waitingApproval()로 결과만 만들고, 실제로 넘긴 입력(input)과 걸린 시간(durationMs)은
 * WorkFlowExecutor가 withCall()로 채웁니다. forEach step의 결과는 WorkFlowExecutor가 반복별 결과를 모아 forEach()로 만듭니다.
 * </pre>
 *
 * @param success    성공했는지 여부입니다. ROUTER가 경로를 고른 것도 성공입니다.
 * @param pending    APPROVAL step이 사람의 결정을 기다리는 중인지 여부입니다. true면 나머지 값은 모두 비어 있습니다.
 * @param input      이 step이 실제로 받은 입력입니다(템플릿을 채운 뒤의 값. 문자열 또는 Tool 인자 맵).
 * @param output     구조화된 결과입니다. 없으면 빈 맵입니다.
 * @param text       결과 텍스트입니다.
 * @param error      실패 사유입니다. 성공이면 null입니다.
 * @param route      ROUTER step이 고른 경로 이름입니다. ROUTER가 아니면 null입니다.
 * @param items      forEach step의 반복별 결과(각각 toRecord() 모양)입니다. forEach가 아니면 null입니다.
 * @param durationMs 이 step을 처리하는 데 걸린 시간(밀리초)입니다. 실행 이력에 남깁니다.
 */
public record StepOutcome(
	boolean success
	, boolean pending
	, Object input
	, Map<String, Object> output
	, String text
	, String error
	, String route
	, List<Map<String, Object>> items
	, long durationMs
	) {

	/** 성공했고 구조화된 결과는 없을 때 씁니다. @param text 결과 텍스트입니다. */
	public static StepOutcome success(String text) {
		return success(text, Map.of());
	}

	/**
	 * 성공했고 구조화된 결과도 있을 때 씁니다.
	 *
	 * @param text   결과 텍스트입니다.
	 * @param output 구조화된 결과입니다.
	 */
	public static StepOutcome success(String text, Map<String, Object> output) {
		return new StepOutcome(true, false, null, output == null ? Map.of() : output, text, null, null, null, 0L);
	}

	/**
	 * ROUTER step이 경로를 골랐을 때 씁니다. 그 route를 실제로 어느 step으로 이어줄지는
	 * runtime.workflow.WorkFlowExecutor가 RouterStepDefinition.routes를 보고 정합니다.
	 *
	 * @param text   결과 텍스트입니다(ROUTER는 받은 입력을 그대로 넘깁니다).
	 * @param output 구조화된 결과입니다({route, reason}).
	 * @param route  고른 경로 이름입니다.
	 */
	public static StepOutcome routed(String text, Map<String, Object> output, String route) {
		return new StepOutcome(true, false, null, output == null ? Map.of() : output, text, null, route, null, 0L);
	}

	/**
	 * 실패했을 때 씁니다.
	 *
	 * @param text  실패하기 전까지 만들어진 결과 텍스트입니다(없으면 null).
	 * @param error 왜 실패했는지에 대한 설명입니다.
	 */
	public static StepOutcome failure(String text, String error) {
		return new StepOutcome(false, false, null, Map.of(), text, error, null, null, 0L);
	}

	/** 사람의 결정을 기다리는 중일 때 씁니다(ApprovalStepExecutor 전용). */
	public static StepOutcome waitingApproval() {
		return new StepOutcome(false, true, null, Map.of(), null, null, null, null, 0L);
	}

	/**
	 * forEach step의 결과를 만듭니다. 반복이 하나라도 실패하면 실패이고, text는 반복별 text를, error는 실패한
	 * 반복들의 사유를 줄바꿈으로 이은 값입니다. output은 비어 있고, 반복별 결과는 items에 순서대로 담깁니다.
	 *
	 * @param success    모든 반복이 성공했는지 여부입니다.
	 * @param text       반복별 text를 이은 값입니다.
	 * @param error      실패한 반복들의 사유를 이은 값입니다(모두 성공이면 null).
	 * @param items      반복별 결과 목록입니다(각각 toRecord() 모양).
	 * @param durationMs 모든 반복이 끝날 때까지 걸린 시간(밀리초)입니다.
	 */
	public static StepOutcome forEach(boolean success, String text, String error, List<Map<String, Object>> items, long durationMs) {
		return new StepOutcome(success, false, null, Map.of(), text, error, null, items, durationMs);
	}

	/**
	 * 이 결과에 실제로 넘긴 입력과 걸린 시간을 채운 복사본을 돌려줍니다(WorkFlowExecutor가 씁니다).
	 *
	 * @param input      이 step이 실제로 받은 입력입니다.
	 * @param durationMs 걸린 시간(밀리초)입니다.
	 */
	public StepOutcome withCall(Object input, long durationMs) {
		return new StepOutcome(this.success, this.pending, input, this.output, this.text, this.error, this.route, this.items, durationMs);
	}

	/**
	 * 컨텍스트의 steps.{stepId}에 남길 모양({input, output, text, error}, forEach면 items까지)으로 바꿉니다.
	 */
	public Map<String, Object> toRecord() {
		Map<String, Object> record = new LinkedHashMap<>();
		record.put(Context.FIELD_INPUT, this.input);
		record.put(Context.FIELD_OUTPUT, this.output == null ? Map.of() : this.output);
		record.put(Context.FIELD_TEXT, this.text);
		record.put(Context.FIELD_ERROR, this.error);
		if (this.items != null) {
			record.put(Context.FIELD_ITEMS, this.items);
		}
		return record;
	}

}

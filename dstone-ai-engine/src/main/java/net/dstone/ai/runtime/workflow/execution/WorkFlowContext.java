package net.dstone.ai.runtime.workflow.execution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dstone.ai.common.consts.Constants.WorkFlow.Context;

/**
 * <pre>
 * Workflow 실행 1건의 컨텍스트(실행 중 모든 상태를 담은 트리)를 만들고 고치는 도구 모음입니다.
 * 컨텍스트 자체는 평범한 Map이라서 그대로 JSON으로 DB에 저장되고(WorkFlowExecutionStore), 실행 상세
 * 조회 API로도 그대로 보입니다. YAML 템플릿의 {{ ... }} 참조는 이 트리의 경로를 그대로 따라갑니다.
 * 이름은 YAML에 적는 이름과 맞춰 두었습니다(workflow.inputs → inputs, step의 input/output → steps.id.input/output).
 *
 * inputs:                   ← Workflow를 시작할 때 한 번 채워지고 바뀌지 않음(YAML workflow.inputs에 선언한 값들)
 *   message: "사용자 메시지"
 *   sqlList: [...]          ← 요청의 variables가 여기에 펼쳐짐
 * steps:                    ← step이 끝날 때마다 자기 id 아래에 결과를 남김(같은 step이 다시 돌면 덮어씀)
 *   analyze:
 *     input:  "채워진 입력"   ← YAML step의 input을 채운 값
 *     output: { ... }        ← YAML step의 output(schema/parse)에 선언한 모양의 값
 *     text:   "결과 텍스트"
 *     error:  null
 *   validate-each:          ← forEach step은 반복별 결과를 items에 담음
 *     text: "..."
 *     items: [ { input, output, text, error }, ... ]
 * previous:                 ← 바로 직전에 실행된 step의 결과(첫 step에서는 { text: message })
 * approvals:                ← APPROVAL step별 사람의 결정(엔진 내부용)
 *   design-review: { approved, approver, comment }
 * </pre>
 */
public final class WorkFlowContext {

	private WorkFlowContext() {
	}

	/**
	 * 새 실행의 컨텍스트를 만듭니다. 요청의 variables와 message는 inputs 아래에 들어가고, 첫 step이
	 * {{previous.text}}로 사용자 메시지를 받을 수 있도록 previous.text에도 message를 넣어 둡니다.
	 *
	 * @param message   실행 요청의 message입니다.
	 * @param variables 실행 요청의 variables입니다(없으면 null).
	 */
	public static Map<String, Object> create(String message, Map<String, Object> variables) {
		Map<String, Object> inputs = variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
		inputs.put(Context.MESSAGE, message);

		Map<String, Object> previous = new LinkedHashMap<>();
		previous.put(Context.FIELD_TEXT, message);

		Map<String, Object> context = new LinkedHashMap<>();
		context.put(Context.INPUTS, inputs);
		context.put(Context.STEPS, new LinkedHashMap<String, Object>());
		context.put(Context.PREVIOUS, previous);
		return context;
	}

	/**
	 * 컨텍스트의 inputs(사용자가 넘긴 값) 맵을 돌려줍니다. Agent의 system prompt에 있는 {변수명}을
	 * 채울 때 이 맵을 씁니다.
	 *
	 * @param context 실행 컨텍스트입니다.
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> inputs(Map<String, Object> context) {
		Object inputs = context.get(Context.INPUTS);
		return inputs instanceof Map ? (Map<String, Object>) inputs : Map.of();
	}

	/**
	 * step 하나의 결과를 steps.{stepId}에 남기고, previous도 이 결과로 바꿉니다.
	 *
	 * @param context 실행 컨텍스트입니다(이 맵을 직접 고칩니다).
	 * @param stepId  결과를 남길 step의 id입니다.
	 * @param record  남길 결과입니다(stepRecord()나 forEachRecord()로 만든 값).
	 */
	@SuppressWarnings("unchecked")
	public static void recordStep(Map<String, Object> context, String stepId, Map<String, Object> record) {
		Object steps = context.get(Context.STEPS);
		if (!(steps instanceof Map)) {
			steps = new LinkedHashMap<String, Object>();
			context.put(Context.STEPS, steps);
		}
		((Map<String, Object>) steps).put(stepId, record);
		context.put(Context.PREVIOUS, record);
	}

	/**
	 * step 한 번의 결과를 컨텍스트에 남길 모양({input, output, text, error})으로 만듭니다.
	 *
	 * @param input  이 step이 실제로 받은 입력입니다(템플릿을 채운 뒤의 값).
	 * @param output 구조화된 결과입니다(없으면 빈 맵).
	 * @param text   결과 텍스트입니다.
	 * @param error  실패 사유입니다(성공이면 null).
	 */
	public static Map<String, Object> stepRecord(Object input, Map<String, Object> output, String text, String error) {
		Map<String, Object> record = new LinkedHashMap<>();
		record.put(Context.FIELD_INPUT, input);
		record.put(Context.FIELD_OUTPUT, output == null ? Map.of() : output);
		record.put(Context.FIELD_TEXT, text);
		record.put(Context.FIELD_ERROR, error);
		return record;
	}

	/**
	 * forEach step의 결과를 남길 모양으로 만듭니다. text는 반복별 text를 줄바꿈으로 이은 값이고,
	 * error는 실패한 반복들의 사유를 이은 값입니다(모두 성공이면 null). 반복별 결과는 items에 순서대로 담깁니다.
	 *
	 * @param text  반복별 text를 이은 값입니다.
	 * @param error 실패한 반복들의 사유를 이은 값입니다(모두 성공이면 null).
	 * @param items 반복별 결과 목록입니다(각각 stepRecord() 모양).
	 */
	public static Map<String, Object> forEachRecord(String text, String error, List<Map<String, Object>> items) {
		Map<String, Object> record = stepRecord(null, Map.of(), text, error);
		record.put(Context.FIELD_ITEMS, items);
		return record;
	}

	/**
	 * forEach의 반복 하나를 위해, 원래 컨텍스트에 {{item}} 변수 하나만 더한 복사본을 만듭니다.
	 * 원래 컨텍스트는 건드리지 않으므로, 여러 반복이 동시에 돌아도 서로의 item이 섞이지 않습니다.
	 *
	 * @param context 원래 실행 컨텍스트입니다.
	 * @param itemKey 항목을 담을 변수 이름입니다(기본값 "item").
	 * @param item    이번 반복이 맡은 항목입니다.
	 */
	public static Map<String, Object> withItem(Map<String, Object> context, String itemKey, Object item) {
		Map<String, Object> copy = new LinkedHashMap<>(context);
		copy.put(itemKey, item);
		return copy;
	}

	/**
	 * APPROVAL step에 대해 사람이 내린 결정을 approvals.{stepId}에 기록합니다.
	 *
	 * @param context  실행 컨텍스트입니다(이 맵을 직접 고칩니다).
	 * @param stepId   결정을 기록할 APPROVAL step의 id입니다.
	 * @param approved 승인이면 true, 반려면 false입니다.
	 * @param approver 결정한 사람이나 역할입니다.
	 * @param comment  결정한 이유나 메모입니다.
	 */
	@SuppressWarnings("unchecked")
	public static void recordApproval(Map<String, Object> context, String stepId, boolean approved, String approver, String comment) {
		Object approvals = context.get(Context.APPROVALS);
		if (!(approvals instanceof Map)) {
			approvals = new LinkedHashMap<String, Object>();
			context.put(Context.APPROVALS, approvals);
		}
		Map<String, Object> decision = new LinkedHashMap<>();
		decision.put("approved", approved);
		decision.put("approver", approver);
		decision.put("comment", comment);
		((Map<String, Object>) approvals).put(stepId, decision);
	}

	/**
	 * APPROVAL step에 대해 기록된 결정을 돌려줍니다. 아직 결정이 없으면 null입니다.
	 *
	 * @param context 실행 컨텍스트입니다.
	 * @param stepId  결정을 찾을 APPROVAL step의 id입니다.
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> approval(Map<String, Object> context, String stepId) {
		Object approvals = context.get(Context.APPROVALS);
		if (!(approvals instanceof Map)) {
			return null;
		}
		Object decision = ((Map<String, Object>) approvals).get(stepId);
		return decision instanceof Map ? (Map<String, Object>) decision : null;
	}

}

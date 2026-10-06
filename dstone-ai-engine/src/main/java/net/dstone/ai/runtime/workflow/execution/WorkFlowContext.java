package net.dstone.ai.runtime.workflow.execution;

import java.util.LinkedHashMap;
import java.util.Map;

import net.dstone.ai.common.consts.Constants.WorkFlow.Context;

/**
 * <pre>
 * Workflow 실행 1건의 컨텍스트(실행 중 모든 상태를 담은 트리)를 만들고 고치는 도구 모음입니다.
 * 컨텍스트 자체는 평범한 Map이라서 그대로 JSON으로 DB에 저장되고(WorkFlowExecutionStore), 실행 상세
 * 조회 API로도 그대로 보입니다. YAML의 "${ ... }" 표현식은 이 트리를 jq로 읽습니다(common.schema.JqExpEvalUtil).
 * 이름은 YAML에 적는 이름과 맞춰 두었고(workflow.input → .input, step의 input/output → .steps.id.input/output),
 * 엔진이 몰래 채워 넣는 숨은 이름은 없습니다. approvals는 엔진 내부용이라 표현식에는 보이지 않습니다.
 *
 * input:                    ← 요청의 input 그대로(workflow.input 모양). 시작할 때 한 번 채워지고 바뀌지 않음
 *   requirement: "..."
 * steps:                    ← step이 끝날 때마다 자기 id 아래에 결과를 남김(같은 step이 다시 돌면 덮어씀)
 *   analyze:
 *     input:  "계산된 입력"   ← YAML step의 input 표현식을 계산한 값
 *     output: { ... }        ← 부른 Agent/Tool이 돌려준 값(Agent output 모양)
 *     error:  null
 *   validateEach:           ← forEach step은 input/output이 반복별 값의 리스트
 *     input:  [ {...}, {...} ]
 *     output: [ ..., ... ]
 * approvals:                ← APPROVAL step별 사람의 결정(엔진 내부용)
 *   designReview: { approved, approver, comment }
 * </pre>
 */
public final class WorkFlowContext {

	private WorkFlowContext() {
	}

	/**
	 * <pre>
	 * 새 실행의 컨텍스트를 만듭니다. 요청의 input은 그대로 input 아래에 들어갑니다.
	 * </pre>
	 *
	 * @param input 실행 요청의 input입니다(workflow.input 모양으로 이미 검사된 값).
	 */
	public static Map<String, Object> create(Object input) {
		Map<String, Object> context = new LinkedHashMap<>();
		context.put(Context.INPUT, input);
		context.put(Context.STEPS, new LinkedHashMap<String, Object>());
		return context;
	}

	/**
	 * <pre>
	 * step 하나의 결과를 steps.{stepId}에 남깁니다.
	 * </pre>
	 *
	 * @param context 실행 컨텍스트입니다(이 맵을 직접 고칩니다).
	 * @param stepId  결과를 남길 step의 id입니다.
	 * @param record  남길 결과입니다(runtime.step.StepOutcome.toRecord()로 만든 값).
	 */
	@SuppressWarnings("unchecked")
	public static void recordStep(Map<String, Object> context, String stepId, Map<String, Object> record) {
		Object steps = context.get(Context.STEPS);
		if (!(steps instanceof Map)) {
			steps = new LinkedHashMap<String, Object>();
			context.put(Context.STEPS, steps);
		}
		((Map<String, Object>) steps).put(stepId, record);
	}

	/**
	 * <pre>
	 * APPROVAL step에 대해 사람이 내린 결정을 approvals.{stepId}에 기록합니다.
	 * </pre>
	 *
	 * @param context  실행 컨텍스트입니다(이 맵을 직접 고칩니다).
	 * @param stepId   결정을 기록할 APPROVAL step의 id입니다.
	 * @param approved 승인이면 true, 반려면 false입니다(선택지 방식이면 쓰지 않습니다).
	 * @param route    선택지 방식(routes)일 때 사람이 고른 이름입니다. 승인/반려 방식이면 null입니다.
	 * @param approver 결정한 사람이나 역할입니다.
	 * @param comment  결정한 이유나 메모입니다.
	 */
	@SuppressWarnings("unchecked")
	public static void recordApproval(Map<String, Object> context, String stepId, boolean approved, String route, String approver, String comment) {
		Object approvals = context.get(Context.APPROVALS);
		if (!(approvals instanceof Map)) {
			approvals = new LinkedHashMap<String, Object>();
			context.put(Context.APPROVALS, approvals);
		}
		Map<String, Object> decision = new LinkedHashMap<>();
		decision.put("approved", approved);
		decision.put("route", route);
		decision.put("approver", approver);
		decision.put("comment", comment);
		((Map<String, Object>) approvals).put(stepId, decision);
	}

	/**
	 * <pre>
	 * APPROVAL step에 대해 기록된 결정을 돌려줍니다. 아직 결정이 없으면 null입니다.
	 * </pre>
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

	/**
	 * <pre>
	 * APPROVAL step에 기록된 결정을 지웁니다. 결정을 읽어서 쓴 직후에 부릅니다.
	 *
	 * 지우는 이유: 결정을 남겨 두면, 흐름이 되돌아와 같은 APPROVAL step에 다시 왔을 때 사람에게 묻지 않고
	 * 지난번 결정을 그대로 또 씁니다(반려 → 되돌아감 → 또 반려 → ... 로 끝없이 돕니다).
	 * 지워 두면 다시 올 때마다 새로 묻습니다. 결정 내용은 steps.{stepId}.output에 남으므로 잃는 것은 없습니다.
	 * </pre>
	 *
	 * @param context 실행 컨텍스트입니다(이 맵을 직접 고칩니다).
	 * @param stepId  결정을 지울 APPROVAL step의 id입니다.
	 */
	@SuppressWarnings("unchecked")
	public static void clearApproval(Map<String, Object> context, String stepId) {
		Object approvals = context.get(Context.APPROVALS);
		if (approvals instanceof Map) {
			((Map<String, Object>) approvals).remove(stepId);
		}
	}

}

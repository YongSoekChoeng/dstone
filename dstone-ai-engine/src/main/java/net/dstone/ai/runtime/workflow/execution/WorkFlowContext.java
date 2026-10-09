package net.dstone.ai.runtime.workflow.execution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dstone.ai.common.consts.Constants.WorkFlow.Context;
import net.dstone.ai.common.consts.Constants.WorkFlow.Output;
import net.dstone.ai.common.expression.ContextResolver;

/**
 * <pre>
 * Workflow 실행 컨텍스트(WorkFlowExecution.context 맵)를 다루는 도우미입니다. 컨텍스트는 아래 모양의 트리 하나입니다.
 *
 *   input:      요청의 input 값 그대로
 *   state:      step이 output으로 저장한 값들({ 이름: 값 })
 *   approvals:  { stepId: { decision, approver, comment } }   아직 쓰지 않은 승인 결정(엔진 내부용)
 *   definition: { id, version }                               이 실행을 시작한 Workflow 정의
 *
 * step 사이에 값이 오가는 길은 state 하나뿐입니다. step이 끝나면 그 step의 output에 적힌 대로 state에 저장하고(saveOutput()),
 * 뒤의 step은 "${state.이름}"으로 읽습니다(common.expression.ContextResolver).
 * 이 맵은 실행 상태와 함께 DB에 JSON으로 저장되므로, 승인 대기로 멈췄다가 이어서 실행해도 그대로 남아 있습니다.
 * </pre>
 */
public final class WorkFlowContext {

	private WorkFlowContext() {
	}

	/**
	 * 새 실행의 컨텍스트를 만듭니다. state는 비어 있습니다.
	 *
	 * @param input           실행 요청의 input 값
	 * @param workflowId      실행할 Workflow의 id
	 * @param workflowVersion 실행할 Workflow 정의의 버전
	 */
	public static Map<String, Object> create(Object input, String workflowId, String workflowVersion) {
		Map<String, Object> definition = new LinkedHashMap<>();
		definition.put("id", workflowId);
		definition.put("version", workflowVersion);
		Map<String, Object> context = new LinkedHashMap<>();
		context.put(Context.INPUT, input);
		context.put(Context.STATE, new LinkedHashMap<String, Object>());
		context.put(Context.DEFINITION, definition);
		return context;
	}

	/**
	 * 이 실행을 시작한 Workflow 정의의 버전을 돌려줍니다. 기록이 없으면 null입니다.
	 *
	 * @param context 실행 컨텍스트
	 */
	@SuppressWarnings("unchecked")
	public static String definitionVersion(Map<String, Object> context) {
		Object definition = context.get(Context.DEFINITION);
		Object version = definition instanceof Map ? ((Map<String, Object>) definition).get("version") : null;
		return version == null ? null : version.toString();
	}

	/**
	 * 컨텍스트의 state 맵을 돌려줍니다. 없으면 만들어 넣습니다.
	 *
	 * @param context 실행 컨텍스트
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> state(Map<String, Object> context) {
		Object state = context.get(Context.STATE);
		if (!(state instanceof Map)) {
			state = new LinkedHashMap<String, Object>();
			context.put(Context.STATE, state);
		}
		return (Map<String, Object>) state;
	}

	/**
	 * <pre>
	 * step 하나가 끝난 뒤, 그 step의 output에 적힌 대로 결과를 state에 저장합니다.
	 *
	 *   output:
	 *     result: state.analysis         돌려준 값 전체
	 *     result.sql: state.sql          돌려준 값 안의 필드 하나
	 *     input: state.sentInput         실제로 받은 입력
	 *     error: state.analysisError     실패 사유(성공이면 null)
	 *
	 * 성공이든 실패든 적힌 자리를 모두 새 값으로 덮어씁니다. 그래서 다시 실행된 step의 옛 결과가 남아 있지 않습니다.
	 * (forEach로 여러 반복이 동시에 도는 동안에는 부르지 않습니다. 모든 반복이 끝난 뒤 한 번 부릅니다.)
	 * </pre>
	 *
	 * @param context 실행 컨텍스트
	 * @param mapping step의 output(무엇 → state.이름). 없으면 아무것도 하지 않습니다.
	 * @param input   이 step이 실제로 받은 입력
	 * @param result  이 step이 돌려준 값
	 * @param error   실패 사유(성공이면 null)
	 */
	public static void saveOutput(Map<String, Object> context, Map<String, String> mapping, Object input, Object result, String error) {
		if (mapping == null || mapping.isEmpty()) {
			return;
		}
		Map<String, Object> record = new LinkedHashMap<>();
		record.put(Output.INPUT, input);
		record.put(Output.RESULT, result);
		record.put(Output.ERROR, error);
		Map<String, Object> state = state(context);
		for (Map.Entry<String, String> entry : mapping.entrySet()) {
			List<String> source = sourcePath(entry.getKey());
			ContextResolver.write(state, ContextResolver.statePath(entry.getValue()), ContextResolver.read(record, source));
		}
	}

	/**
	 * output의 왼쪽(무엇을 저장할지)을 이름 목록으로 나눕니다. 예: "result.sql" → [result, sql]
	 *
	 * @param source output에 적은 이름(result, result.필드, input, input.필드, error)
	 */
	public static List<String> sourcePath(String source) {
		return List.of(source.strip().split("\\."));
	}

	/**
	 * 사람이 내린 결정을 승인 수신함에 넣어 둡니다. 이어서 실행되는 APPROVAL step이 꺼내 씁니다.
	 *
	 * @param context  실행 컨텍스트
	 * @param stepId   결정을 기다리던 APPROVAL step의 id
	 * @param decision 사람이 고른 결정 이름(그 step의 routes에 있는 이름)
	 * @param approver 결정한 사람
	 * @param comment  의견
	 */
	@SuppressWarnings("unchecked")
	public static void recordApproval(Map<String, Object> context, String stepId, String decision, String approver, String comment) {
		Object approvals = context.get(Context.APPROVALS);
		if (!(approvals instanceof Map)) {
			approvals = new LinkedHashMap<String, Object>();
			context.put(Context.APPROVALS, approvals);
		}
		Map<String, Object> record = new LinkedHashMap<>();
		record.put("decision", decision);
		record.put("approver", approver);
		record.put("comment", comment);
		((Map<String, Object>) approvals).put(stepId, record);
	}

	/**
	 * 승인 수신함에서 이 step의 결정을 꺼내 봅니다. 아직 없으면 null입니다.
	 *
	 * @param context 실행 컨텍스트
	 * @param stepId  APPROVAL step의 id
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
	 * 다 쓴 결정을 승인 수신함에서 지웁니다. 지워 두어야 같은 APPROVAL step에 다시 왔을 때 사람에게 다시 묻습니다.
	 *
	 * @param context 실행 컨텍스트
	 * @param stepId  APPROVAL step의 id
	 */
	@SuppressWarnings("unchecked")
	public static void clearApproval(Map<String, Object> context, String stepId) {
		Object approvals = context.get(Context.APPROVALS);
		if (approvals instanceof Map) {
			((Map<String, Object>) approvals).remove(stepId);
		}
	}

}

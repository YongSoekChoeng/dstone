package net.dstone.ai.runtime.workflow.execution;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dstone.ai.common.consts.Constants.WorkFlow.Context;
import net.dstone.ai.common.consts.Constants.WorkFlow.Output;

/**
 * <pre>
 * Workflow 실행 컨텍스트(WorkFlowExecution.context 맵)를 다루는 도우미입니다. 컨텍스트는 아래 모양의 트리 하나입니다.
 *
 *   input:      요청의 input 값 그대로
 *   state:      step이 save로 저장한 값들({ 이름: 값 })
 *   approvals:  { stepId: { decision, approver, comment } }   아직 쓰지 않은 승인 결정(엔진 내부용)
 *   definition: { id, version }                               이 실행을 시작한 Workflow 정의
 *   currentStep: 지금 차례인 step의 id(엔진 내부용). 정의가 바뀌어 순번이 밀려도 재개할 자리를 찾으려고 둡니다.
 *   decisions:  [ { step, name, kind, decision, approver, comment, to, at } ]  내려진 결정(승인, 판정) 기록. ${decisions}로 읽습니다
 *   arrival:    지금 step으로 오게 만든 마지막 결정(위 기록 하나). ${arrival}로 읽습니다
 *   arrivedFrom: { step, via }  지금 step에 어느 step에서 어떤 길로 왔는지(엔진 내부용). 실행 이력에 함께 남깁니다
 *   limits:     { stepId: { count, scope, ... } }  step의 횟수 한도(limit)를 센 값(엔진 내부용)
 *
 * step 사이에 값이 오가는 길은 state 하나뿐입니다. step이 끝나면 그 step의 save에 적힌 이름으로 state에 저장하고(saveOutput()),
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
	 * 지금 차례인 step의 id를 적어 둡니다. 실행 상태를 저장할 때 함께 저장됩니다.
	 *
	 * @param context 실행 컨텍스트
	 * @param stepId  지금 차례인 step의 id
	 */
	public static void setCurrentStep(Map<String, Object> context, String stepId) {
		context.put(Context.CURRENT_STEP, stepId);
	}

	/**
	 * 지금 차례인 step의 id를 돌려줍니다. 이 기록이 생기기 전에 시작한 실행이면 null입니다.
	 *
	 * @param context 실행 컨텍스트
	 */
	public static String currentStep(Map<String, Object> context) {
		Object stepId = context.get(Context.CURRENT_STEP);
		return stepId == null ? null : stepId.toString();
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
	 * step 하나가 끝난 뒤, 그 step의 save / saveError / saveInput / saveEach에 적힌 이름으로 결과를 state에 저장합니다.
	 *
	 *   save: analysis            돌려준 값 전체 → state.analysis
	 *   saveError: analysisError  실패 사유(성공이면 null)
	 *   saveInput: sentInput      실제로 받은 입력
	 *   saveEach: copied          forEach step만: 반복마다 {item, input, result, error, success} 한 건씩
	 *
	 * 성공이든 실패든 적힌 이름을 모두 새 값으로 덮어씁니다. 그래서 다시 실행된 step의 옛 결과가 남아 있지 않습니다.
	 * (forEach로 여러 반복이 동시에 도는 동안에는 부르지 않습니다. 모든 반복이 끝난 뒤 한 번 부릅니다.)
	 * </pre>
	 *
	 * @param context 실행 컨텍스트
	 * @param mapping 무엇(result, error, input, items) → state 이름(StepDefinition.output()). 비었으면 아무것도 하지 않습니다.
	 * @param input   이 step이 실제로 받은 입력
	 * @param result  이 step이 돌려준 값
	 * @param error   실패 사유(성공이면 null)
	 * @param items   forEach step의 반복별 묶음 목록(forEach가 아니면 null)
	 */
	public static void saveOutput(Map<String, Object> context, Map<String, String> mapping, Object input, Object result, String error, List<Object> items) {
		if (mapping == null || mapping.isEmpty()) {
			return;
		}
		Map<String, Object> record = new LinkedHashMap<>();
		record.put(Output.INPUT, input);
		record.put(Output.RESULT, result);
		record.put(Output.ERROR, error);
		record.put(Output.ITEMS, items);
		Map<String, Object> state = state(context);
		for (Map.Entry<String, String> entry : mapping.entrySet()) {
			state.put(entry.getValue(), record.get(entry.getKey()));
		}
	}

	/**
	 * state에서 이름들을 지웁니다. 지난 step으로 되돌릴 때, 그 뒤에서 저장한 값을 없애려고 씁니다.
	 *
	 * @param context 실행 컨텍스트
	 * @param names   지울 state 이름들
	 */
	public static void removeState(Map<String, Object> context, List<String> names) {
		Map<String, Object> state = state(context);
		for (String name : names) {
			state.remove(name);
		}
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
	 * <pre>
	 * 실행 중에 내려진 결정(APPROVAL의 승인, SUPERVISOR의 판정)을 기록하고, 다음에 LLM을 부를 step이 받도록 arrival로 둡니다.
	 * 기록은 지우지 않고 쌓습니다(${decisions}). arrival은 결정 하나뿐이고(${arrival}), LLM을 부르는 step이 성공하면 비웁니다(clearArrival()).
	 * 그래서 TOOL step을 몇 개 거쳐도 다음 Agent step이 "왜 여기로 왔는지"를 받습니다.
	 * </pre>
	 *
	 * @param context  실행 컨텍스트
	 * @param stepId   결정을 내린 step의 id
	 * @param stepName 그 step의 name(없으면 null)
	 * @param kind     APPROVAL 또는 SUPERVISOR
	 * @param decision 고른 route 이름(APPROVAL) 또는 통과/불통과(SUPERVISOR)
	 * @param approver 결정한 사람(SUPERVISOR는 null)
	 * @param comment  의견 또는 판정 사유
	 * @param to       이 결정으로 가게 된 곳(step id, END, FAIL)
	 */
	@SuppressWarnings("unchecked")
	public static void recordDecision(Map<String, Object> context, String stepId, String stepName, String kind, String decision, String approver, String comment, String to) {
		Map<String, Object> record = new LinkedHashMap<>();
		record.put("step", stepId);
		record.put("name", stepName);
		record.put("kind", kind);
		record.put("decision", decision);
		record.put("approver", approver);
		record.put("comment", comment);
		record.put("to", to);
		record.put("at", OffsetDateTime.now().toString());
		Object decisions = context.get(Context.DECISIONS);
		if (!(decisions instanceof List)) {
			decisions = new ArrayList<Object>();
			context.put(Context.DECISIONS, decisions);
		}
		((List<Object>) decisions).add(record);
		context.put(Context.ARRIVAL, record);
	}

	/**
	 * 지금 step으로 오게 만든 결정을 비웁니다. LLM을 부르는 step이 성공한 뒤에 부릅니다(그 결정은 그 step이 받아 썼습니다).
	 *
	 * @param context 실행 컨텍스트
	 */
	public static void clearArrival(Map<String, Object> context) {
		context.remove(Context.ARRIVAL);
	}

	/**
	 * 지금까지 기록된 결정 목록입니다(오래된 것부터). 없으면 빈 목록입니다.
	 *
	 * @param context 실행 컨텍스트
	 */
	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> decisions(Map<String, Object> context) {
		Object decisions = context.get(Context.DECISIONS);
		return decisions instanceof List ? (List<Map<String, Object>>) decisions : List.of();
	}

	/**
	 * 결정 하나를 다시 arrival로 둡니다. 지난 step으로 되돌려 다시 돌릴 때, 그 step으로 오게 했던 결정을 되살리려고 씁니다.
	 * null이면 arrival을 비웁니다.
	 *
	 * @param context  실행 컨텍스트
	 * @param decision 되살릴 결정(decisions() 가운데 하나) 또는 null
	 */
	public static void restoreArrival(Map<String, Object> context, Map<String, Object> decision) {
		if (decision == null) {
			context.remove(Context.ARRIVAL);
		} else {
			context.put(Context.ARRIVAL, decision);
		}
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

	/**
	 * <pre>
	 * 다음 step에 어디서 어떤 길로 가는지 적어 둡니다. 다음 step의 실행 이력에 함께 남아, 실행 경로를 거꾸로 따라갈 수 있습니다.
	 * via는 고른 route 이름(APPROVAL 결정, ROUTER 경로, Tool이 돌려준 값), 실패로 갔으면 "onFailure", next로 갔으면 null입니다.
	 * </pre>
	 *
	 * @param context 실행 컨텍스트
	 * @param stepId  방금 끝난 step의 id(되감기면 null)
	 * @param via     어떤 길로 가는지
	 */
	public static void setArrivedFrom(Map<String, Object> context, String stepId, String via) {
		Map<String, Object> from = new LinkedHashMap<>();
		from.put("step", stepId);
		from.put("via", via);
		context.put(Context.ARRIVED_FROM, from);
	}

	/**
	 * step의 횟수 한도를 지난번에 센 값입니다. 처음이면 null입니다.
	 *
	 * @param context 실행 컨텍스트
	 * @param stepId  limit을 적은 step의 id
	 */
	@SuppressWarnings("unchecked")
	public static Object limitCount(Map<String, Object> context, String stepId) {
		Object limits = context.get(Context.LIMITS);
		return limits instanceof Map ? ((Map<String, Object>) limits).get(stepId) : null;
	}

	/**
	 * step의 횟수 한도를 센 값을 적어 둡니다. 실행 상태와 함께 저장되어 승인 대기·재시작 뒤에도 이어집니다.
	 *
	 * @param context 실행 컨텍스트
	 * @param stepId  limit을 적은 step의 id
	 * @param count   센 값(DataLogic.countUp의 결과)
	 */
	@SuppressWarnings("unchecked")
	public static void setLimitCount(Map<String, Object> context, String stepId, Object count) {
		Object limits = context.get(Context.LIMITS);
		if (!(limits instanceof Map)) {
			limits = new LinkedHashMap<String, Object>();
			context.put(Context.LIMITS, limits);
		}
		((Map<String, Object>) limits).put(stepId, count);
	}

	/**
	 * 여러 step의 횟수 한도를 처음부터 다시 세게 지웁니다. 되감기에서 그 구간의 state를 지울 때 함께 씁니다.
	 *
	 * @param context 실행 컨텍스트
	 * @param stepIds 지울 step id들
	 */
	@SuppressWarnings("unchecked")
	public static void clearLimitCounts(Map<String, Object> context, List<String> stepIds) {
		Object limits = context.get(Context.LIMITS);
		if (limits instanceof Map) {
			for (String stepId : stepIds) {
				((Map<String, Object>) limits).remove(stepId);
			}
		}
	}

	/**
	 * 지금 step에 온 곳을 돌려줍니다({step, via}). 첫 step이거나 이 기록이 생기기 전의 실행이면 null입니다.
	 *
	 * @param context 실행 컨텍스트
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> arrivedFrom(Map<String, Object> context) {
		Object from = context.get(Context.ARRIVED_FROM);
		return from instanceof Map ? (Map<String, Object>) from : null;
	}

}

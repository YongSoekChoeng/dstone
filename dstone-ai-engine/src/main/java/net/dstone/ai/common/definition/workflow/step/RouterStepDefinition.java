package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * type: ROUTER - routes에 적어둔 여러 경로 이름 중 하나를 LLM이 직접 고르게 하는 step입니다.
 * 답은 정해진 모양(runtime.agent.RouteDecision - route/reason)으로 받고, 받은 input은 결과 텍스트로 그대로 넘깁니다.
 *
 * 세 갈래 이상으로 나뉘어야 하는 경우(예: 문의 내용에 따라 담당 부서를 나누는 경우)에 씁니다.
 * onSuccess/onFailure 두 갈래만 있는 다른 step으로 이걸 표현하려면 SUPERVISOR를 여러 겹 쌓아야 하는데,
 * ROUTER는 step 하나로 간단하게 표현합니다.
 *
 * - 성공(route를 골랐음): routes에서 그 이름을 찾아 다음 step으로 갑니다. 그래서 onSuccess가 없습니다.
 *   고른 이름이 routes에 없으면(오타를 냈거나 없는 경로를 지어낸 경우) Workflow가 그 자리에서 FAILED로 끝납니다.
 * - 실패(route를 고르지 못했거나 답의 모양이 깨짐): onFailure를 따릅니다.
 *
 * output 모양이 {route, reason}으로 정해져 있어서 output 키는 쓰지 않습니다.
 * forEach도 쓸 수 없습니다. 여러 번 동시에 실행하면 "그중 어느 실행이 고른 경로를 따라야 하는지"가 애매해지기 때문입니다.
 *
 *   - id: classify
 *     type: ROUTER
 *     ref: inquiry-router
 *     routes:
 *       billing: handle-billing
 *       tech: handle-tech
 *       other: SUCCESS
 * </pre>
 *
 * @param id        (필수)이 step의 이름입니다.
 * @param ref       (필수)부를 Agent의 id입니다.
 * @param input     (옵셔널)LLM에게 보낼 사용자 메시지의 템플릿입니다. 비워두면 {{previous.text}}입니다.
 * @param routes    (필수)LLM이 고른 경로 이름 → 이동할 step id(또는 "SUCCESS"/"FAIL")입니다. 최소 1개 있어야 합니다.
 * @param onFailure (옵셔널)route를 고르지 못했을 때 갈 step의 id(또는 "FAIL")입니다.
 */
public record RouterStep(
	String id
	, String ref
	, String input
	, Map<String, String> routes
	, String onFailure
	) implements AgentCallStep {

	@Override
	public StepType type() {
		return StepType.ROUTER;
	}

}

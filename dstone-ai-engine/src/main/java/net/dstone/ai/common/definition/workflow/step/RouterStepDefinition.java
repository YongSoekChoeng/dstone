package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/*.yml 의 type: ROUTER step입니다. routes에 적어둔 경로 이름 중 하나를 LLM이 직접 고르게 합니다
 * (runtime.step.RouterStepExecutor). 세 갈래 이상으로 나뉘어야 하는 경우(예: 문의 내용에 따라 담당 부서를 나누는 경우)에 씁니다.
 *
 * - 성공(route를 골랐음): output에 {route, reason}을 남기고, routes에서 그 이름을 찾아 다음 step으로 갑니다. 그래서 onSuccess가 없습니다.
 *   LLM에게는 routes 이름만 고를 수 있다고 알려주고 답도 그 이름들로 검사합니다(common.schema.StepOutputSchemas).
 * - 실패(route를 고르지 못했거나 답의 모양이 깨짐): onFailure를 따릅니다.
 *
 * output 모양은 엔진이 정하므로 ref의 Agent는 output을 선언하지 않습니다. input은 AGENT step과 같은 규칙입니다.
 * forEach는 없습니다. 여러 번 동시에 실행하면 "그중 어느 실행이 고른 경로를 따라야 하는지"가 애매해지기 때문입니다.
 *
 *   - id: classify
 *     type: ROUTER
 *     ref: sample-router-classifier-agent
 *     input: "${ .input.message }"
 *     routes:
 *       billing: billingStep
 *       technical: technicalStep
 *       other: SUCCESS
 * </pre>
 *
 * @param id        (필수)이 step의 이름입니다.
 * @param ref       (필수)부를 Agent의 id입니다.
 * @param input     (필수)Agent에게 넣을 값(분류할 대상)입니다.
 * @param routes    (필수)경로 이름 → 이동할 step id(또는 "SUCCESS"/"FAIL")입니다. 최소 1개 있어야 합니다.
 * @param onFailure (옵셔널)route를 고르지 못했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param memory    (옵셔널)true면 이 step이 이전에 자기가 나눈 대화를 기억합니다(대화방 = sessionId:stepId). 비워두면 false입니다.
 *                  재작성 루프처럼 같은 step이 다시 불릴 때 이전 시도를 기억하게 할 때 씁니다. 다른 step의 대화는 섞이지 않습니다.
 */
public record RouterStepDefinition(
	String id
	, String ref
	, Object input
	, Map<String, String> routes
	, String onFailure
	, Boolean memory
	) implements StepDefinition {

	@Override
	public StepType type() {
		return StepType.ROUTER;
	}

}

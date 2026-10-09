package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/*.yml 의 type: ROUTER step입니다. LLM이 routes에 적힌 이름 중 하나를 고르고, 그 이름에 적힌 step으로 갑니다
 * (runtime.step.RouterStepExecutor).
 *
 * 답의 모양은 엔진이 {route, reason}으로 정해 두었습니다. 그래서 부르는 Agent는 output을 선언하지 않습니다.
 * 갈 곳은 routes가 정하므로 next는 적지 않습니다.
 *
 *   - id: classify
 *     type: ROUTER
 *     agent: sample-router-classifier-agent
 *     input: "${input.message}"
 *     output:
 *       result: "state.classify"          # ${state.classify.route}, ${state.classify.reason}
 *     routes:
 *       BILLING: billing
 *       TECH: tech
 *       ETC: END
 * </pre>
 *
 * @param id        (필수)이 step의 이름입니다.
 * @param agent     (필수)분류를 맡길 Agent의 id입니다.
 * @param input     (필수)Agent에게 넣을 값입니다(Agent input 모양).
 * @param output    (옵셔널)이 step의 결과를 state 어디에 저장할지입니다.
 * @param routes    (필수)고를 수 있는 이름 → 이동할 step id(또는 "END"/"FAIL")입니다.
 * @param onFailure (옵셔널)분류하지 못했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param memory    (옵셔널)true면 이 step이 자기 대화방에서 이전에 나눈 대화를 기억합니다.
 */
public record RouterStepDefinition(
	String id
	, String agent
	, Object input
	, Map<String, String> output
	, Map<String, String> routes
	, String onFailure
	, Boolean memory
	) implements StepDefinition {

	@Override
	public StepType type() {
		return StepType.ROUTER;
	}

}

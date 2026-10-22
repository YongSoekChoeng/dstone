package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/**.yml 의 type: ROUTER step입니다. LLM이 routes에 적힌 이름 중 하나를 고르고, 그 이름에 적힌 step으로 갑니다
 * (runtime.step.RouterStepExecutor).
 *
 * 답의 모양은 엔진이 {route, reason}으로 정해 두었습니다. 그래서 부르는 Agent는 output을 선언하지 않습니다.
 * 갈 곳은 routes가 정하므로 next는 적지 않습니다.
 *
 *   - id: classify
 *     type: ROUTER
 *     agent: sample-router-classifier-agent
 *     with: ${input.message}
 *     save: classify                     # ${state.classify.route}, ${state.classify.reason}
 *     routes:
 *       BILLING: billing
 *       TECH: tech
 *       ETC: END
 * </pre>
 *
 * @param id        (필수)이 step의 이름입니다.
 * @param name      (옵셔널)사람이 읽는 step 이름입니다(예: 요구사항 분석). 디자이너 화면에 id와 함께 보이고, 실행에는 쓰이지 않습니다.
 * @param agent     (필수)분류를 맡길 Agent의 id입니다.
 * @param input     (필수)Agent에게 넣을 값입니다. YAML 키는 with입니다(Agent input 모양).
 * @param save      (옵셔널)분류 결과({route, reason})를 저장할 state 이름입니다.
 * @param saveError (옵셔널)실패 사유를 저장할 state 이름입니다.
 * @param saveInput (옵셔널)이 step이 실제로 받은 입력을 저장할 state 이름입니다.
 * @param routes    (필수)고를 수 있는 이름 → 이동할 step id(또는 "END"/"FAIL")입니다.
 * @param onFailure (옵셔널)분류하지 못했을 때 갈 step의 id(또는 "FAIL")입니다.
 * @param memory    (옵셔널)true면 이 step이 자기 대화방에서 이전에 나눈 대화를 기억합니다.
 */
public record RouterStepDefinition(
	String id
	, String name
	, String agent
	, @JsonProperty("with") Object input
	, String save
	, String saveError
	, String saveInput
	, Map<String, String> routes
	, String onFailure
	, Boolean memory
	) implements StepDefinition {

	@Override
	public StepType type() {
		return StepType.ROUTER;
	}

}

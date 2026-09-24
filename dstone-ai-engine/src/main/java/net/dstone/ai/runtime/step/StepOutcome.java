package net.dstone.ai.runtime.step;

import java.util.Map;

/**
 * StepRunner가 step 하나를 실행하고 나서 돌려주는 결과입니다. 경우마다 필요한 값만 갖도록 네 가지로 나뉩니다.
 * - Success: 성공. text와 data를 갖습니다.
 * - Routed : ROUTER step의 성공. text와 data에 더해, LLM이 고른 route를 갖습니다.
 * - Failure: 실패. text와 실패 사유(failureReason)를 갖습니다.
 * - Pending: APPROVAL step이 사람의 결정을 기다리는 중. 아무 값도 없습니다.
 *
 * 호출하는 쪽은 경우를 따지지 않고 outcome.text()/outcome.data()처럼 같은 방식으로 값을 꺼낼 수 있습니다.
 * 자기에게 없는 값은 이 interface의 default 메서드(null 또는 빈 맵)가 대신 돌려줍니다.
 * 성공/실패/대기는 outcome instanceof StepOutcome.Failure / StepOutcome.Pending처럼 타입으로 구분합니다.
 * Routed도 Workflow 입장에서는 성공이므로, Failure/Pending이 아니면 모두 성공입니다.
 *
 * runtime.workflow.WorkFlowExecutor는 이 결과를 컨텍스트의 steps.{stepId}에 {input, text, data, error}로
 * 남기고, 다음 step들은 {{steps.id.text}}, {{steps.id.data.키}}, {{steps.id.error}}로 가져다 씁니다.
 */
public sealed interface StepOutcome {

	/** 결과 텍스트입니다. 대기 중(Pending)이면 null입니다. */
	default String text() {
		return null;
	}

	/** 구조화된 결과입니다. 없으면 빈 맵입니다. */
	default Map<String, Object> data() {
		return Map.of();
	}

	/** 실패 사유입니다. 실패(Failure)일 때만 값이 있습니다. */
	default String failureReason() {
		return null;
	}

	/** LLM이 고른 경로 이름입니다. ROUTER step의 결과(Routed)일 때만 값이 있습니다. */
	default String route() {
		return null;
	}

	/**
	 * 성공했을 때의 결과입니다.
	 *
	 * @param text 결과 텍스트입니다.
	 * @param data 구조화된 결과입니다(없으면 빈 맵).
	 */
	record Success(String text, Map<String, Object> data) implements StepOutcome {
	}

	/**
	 * ROUTER step이 경로를 고르는 데 성공했을 때의 결과입니다. 그 route를 실제로 어느 step으로 이어줄지는
	 * runtime.workflow.WorkFlowExecutor가 StepDefinition.routes를 보고 정합니다.
	 *
	 * @param text  결과 텍스트입니다(ROUTER는 받은 입력을 그대로 넘깁니다).
	 * @param data  구조화된 결과입니다({route, reason}).
	 * @param route StepDefinition.routes에 정의된 키 중 하나입니다.
	 */
	record Routed(String text, Map<String, Object> data, String route) implements StepOutcome {
	}

	/**
	 * 실패했을 때의 결과입니다.
	 *
	 * @param text          실패하기 전까지 만들어진 결과 텍스트입니다(없으면 null).
	 * @param failureReason 왜 실패했는지에 대한 설명입니다.
	 */
	record Failure(String text, String failureReason) implements StepOutcome {
	}

	/** 사람의 승인/반려 결정이 아직 나지 않아서 기다리는 중입니다(ApprovalStepRunner 전용). */
	record Pending() implements StepOutcome {
	}

	/** 성공했고 구조화된 결과는 없을 때 씁니다. @param text 결과 텍스트입니다. */
	static StepOutcome success(String text) {
		return new Success(text, Map.of());
	}

	/**
	 * 성공했고 구조화된 결과도 있을 때 씁니다.
	 *
	 * @param text 결과 텍스트입니다.
	 * @param data 구조화된 결과입니다.
	 */
	static StepOutcome success(String text, Map<String, Object> data) {
		return new Success(text, data == null ? Map.of() : data);
	}

	/**
	 * 실패했을 때 씁니다.
	 *
	 * @param text   실패하기 전까지 만들어진 결과 텍스트입니다(없으면 null).
	 * @param reason 왜 실패했는지에 대한 설명입니다.
	 */
	static StepOutcome failure(String text, String reason) {
		return new Failure(text, reason);
	}

	/** 사람의 결정을 기다리는 중일 때 씁니다(ApprovalStepRunner 전용). */
	static StepOutcome pending() {
		return new Pending();
	}

	/**
	 * ROUTER step이 경로를 골랐을 때 씁니다.
	 *
	 * @param text  결과 텍스트입니다(ROUTER는 받은 입력을 그대로 넘깁니다).
	 * @param data  구조화된 결과입니다({route, reason}).
	 * @param route StepDefinition.routes에 정의된 키 중 하나입니다.
	 */
	static StepOutcome routed(String text, Map<String, Object> data, String route) {
		return new Routed(text, data == null ? Map.of() : data, route);
	}

}

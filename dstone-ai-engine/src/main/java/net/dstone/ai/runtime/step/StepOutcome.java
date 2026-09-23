package net.dstone.ai.runtime.step;

import java.util.Map;

/**
 * StepRunner가 스텝 하나를 실행하고 나서 돌려주는 결과입니다. 예전에는 result(SUCCESS/FAILURE/PENDING)
 * 하나로 성공/실패/대기를 구분하고, primaryText/data/failureReason/route를 한 record에 전부 담아서
 * 타입마다 안 쓰는 필드는 null로 남기는 방식이었습니다. 지금은 sealed interface로 경우를 나눠서, 각
 * 경우가 실제로 필요한 필드만 갖도록 정리했습니다 - 예를 들어 Failure는 route를 가질 수 없고, Pending은
 * 아예 아무 값도 갖지 않습니다.
 *
 * 그래도 호출하는 쪽 입장에서는 여전히 output.primaryText()/output.data()처럼 하나의 통일된 방식으로
 * 값을 꺼낼 수 있습니다 - 각 경우(Success/Routed/Failure/Pending)가 자신이 갖고 있지 않은 값은 이
 * interface의 default 메서드(값 없음 = null 또는 빈 Map)를 그대로 물려받기 때문입니다.
 *
 * "성공/실패/대기"를 구분해야 할 때는 result 필드 비교 대신, output instanceof StepOutcome.Failure /
 * StepOutcome.Pending처럼 타입으로 구분합니다(runtime.workflow.WorkFlowExecutor 참고). ROUTER step의
 * Routed도 Workflow 입장에서는 "성공"과 똑같이 취급되므로(다음에 어디로 갈지만 route로 더 정해질 뿐),
 * Failure/Pending이 아니면 전부 성공으로 봅니다.
 */
public sealed interface StepOutcome {

	/** 다음 step의 {previous} 토큰 자리에 그대로 들어갈 주된 결과 텍스트입니다. 이 값이 없는 경우(Pending)는 null입니다. */
	default String primaryText() {
		return null;
	}

	/** Workflow 전역 변수에 {stepId.키} 형태로 합쳐 넣을 구조화된 결과입니다(없으면 빈 Map). */
	default Map<String, Object> data() {
		return Map.of();
	}

	/** 실패했을 때(Failure)만 값이 있고, 그 외에는 항상 null입니다. */
	default String failureReason() {
		return null;
	}

	/** StepType이 ROUTER인 스텝(Routed)에서만 값이 있고, 그 외에는 항상 null입니다. */
	default String route() {
		return null;
	}

	/**
	 * 성공했을 때 씁니다. structuredOutput=true로 설정된 AGENT step이 LLM으로부터 이 형태(primaryText,
	 * data)로 직접 구조화된 응답을 받을 때도(runtime.step.AgentStepRunner.runStructuredAgent 참고) 이
	 * record를 그대로 재사용합니다 - LLM에게 강제하는 JSON 스키마와, 성공한 step이 돌려주는 값의 모양이
	 * 원래도 완전히 같았기 때문에 별도의 타입(예전의 StepPayload)을 더 두지 않습니다.
	 *
	 * @param primaryText 다음 step으로 이어질 성공 결과 텍스트입니다.
	 * @param data        Workflow 전역 변수에 {stepId.키} 형태로 합쳐 넣을 구조화 결과입니다(없으면 빈 Map).
	 */
	record Success(String primaryText, Map<String, Object> data) implements StepOutcome {
	}

	/**
	 * StepType이 ROUTER인 스텝 전용입니다. LLM이 고른 route 이름을 실어 나릅니다. 이 결과는 항상 성공으로
	 * 취급되고, 그 route를 실제로 어느 스텝으로 이어줄지는 runtime.workflow.WorkFlowExecutor가
	 * StepDefinition.routes를 보고 정합니다.
	 *
	 * @param primaryText 다음 스텝으로 이어질 텍스트입니다(보통 이 스텝에 들어온 입력을 그대로 전달합니다).
	 * @param data        Workflow 전역 변수에 {stepId.키} 형태로 합쳐 넣을 구조화 결과입니다(없으면 빈 Map).
	 * @param route       StepDefinition.routes에 정의된 키 중 하나입니다.
	 */
	record Routed(String primaryText, Map<String, Object> data, String route) implements StepOutcome {
	}

	/**
	 * 스텝이 실패했을 때 씁니다.
	 *
	 * @param primaryText   다음 스텝(보통은 onFailure로 지정된, 문제를 고치는 스텝)으로 이어질 텍스트입니다.
	 * @param failureReason 왜 실패했는지에 대한 설명입니다.
	 */
	record Failure(String primaryText, String failureReason) implements StepOutcome {
	}

	/** 사람의 승인/반려 결정이 아직 나지 않아서 대기 중일 때 씁니다(ApprovalStepRunner 전용). */
	record Pending() implements StepOutcome {
	}

	/** 성공했고 별도의 구조화 데이터는 없을 때 씁니다. @param text 다음 스텝으로 이어질 성공 결과 텍스트입니다. */
	static StepOutcome success(String text) {
		return new Success(text, Map.of());
	}

	/**
	 * 성공했고 다음 스텝들이 참조할 구조화 데이터도 함께 있을 때 씁니다.
	 *
	 * @param text 다음 스텝으로 이어질 성공 결과 텍스트입니다.
	 * @param data Workflow 전역 변수에 {stepId.키} 형태로 합쳐 넣을 구조화 결과입니다.
	 */
	static StepOutcome successWithData(String text, Map<String, Object> data) {
		return new Success(text, data == null ? Map.of() : data);
	}

	/**
	 * 스텝이 실패했을 때 씁니다.
	 *
	 * @param text   다음 스텝(보통은 onFailure로 지정된, 문제를 고치는 스텝)으로 이어질 텍스트입니다.
	 * @param reason 왜 실패했는지에 대한 설명입니다.
	 */
	static StepOutcome failure(String text, String reason) {
		return new Failure(text, reason);
	}

	/** 사람의 승인/반려 결정이 아직 나지 않아서 대기 중일 때 씁니다(ApprovalStepRunner 전용). */
	static StepOutcome pending() {
		return new Pending();
	}

	/**
	 * StepType이 ROUTER인 스텝 전용입니다. LLM이 고른 route 이름을 실어 나릅니다.
	 *
	 * @param text  다음 스텝으로 이어질 텍스트입니다(보통 이 스텝에 들어온 입력을 그대로 전달합니다).
	 * @param data  Workflow 전역 변수에 {stepId.키} 형태로 합쳐 넣을 구조화 결과입니다(없으면 빈 Map).
	 * @param route StepDefinition.routes에 정의된 키 중 하나입니다.
	 */
	static StepOutcome routed(String text, Map<String, Object> data, String route) {
		return new Routed(text, data == null ? Map.of() : data, route);
	}

}

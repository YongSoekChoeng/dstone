package net.dstone.ai.common.definition.workflow.step;

import java.util.Map;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/*.yml 의 type: APPROVAL step입니다. 사람이 결정할 때까지 기다립니다(runtime.step.ApprovalStepExecutor).
 *
 * 처음 실행될 때는 아직 결정이 없으므로 Workflow 전체를 WAITING_APPROVAL(승인 대기) 상태로 멈춰 둡니다.
 * 나중에 누군가 decision API를 호출하면 같은 step을 다시 실행하고, 이번에는 그 결정대로 다음 step으로 갑니다.
 *
 * 결정을 받는 방식은 두 가지입니다. 둘 중 하나만 씁니다.
 *
 * 1) 승인/반려 (routes를 적지 않음)
 *    승인이면 성공(onSuccess), 반려면 실패(onFailure, error에 반려 사유)입니다.
 *    output: {approved, approver, comment}
 *
 *   - id: designReview
 *     type: APPROVAL
 *     approverRole: "PL"
 *     onFailure: FAIL
 *
 * 2) 선택지 (routes를 적음)
 *    사람이 routes 이름 중 하나를 고르고, 그 이름에 적힌 step으로 갑니다. 갈 곳이 셋 이상이거나
 *    "승인/반려"로는 뜻이 드러나지 않을 때 씁니다(예: 진행 / 다시 분석 / 리뷰만 다시).
 *    이때는 onSuccess/onFailure를 적지 않습니다. 고르는 것 자체가 결정이라 실패가 없습니다.
 *    output: {route, approver, comment}
 *
 *   - id: analysisReview
 *     type: APPROVAL
 *     approverRole: "PL"
 *     routes:
 *       진행: design
 *       재분석: analyze
 *       재리뷰: review
 *
 * 되돌아가는 흐름을 만들어도 됩니다. 한 번 쓴 결정은 지우기 때문에, 같은 APPROVAL step에 다시 오면
 * 사람에게 다시 묻습니다. 직전 결정은 steps.id.output에 남아 있어서 다음 step이
 * "${ .steps.id.output.comment }"처럼 꺼내 쓸 수 있습니다(예: 재분석 Agent에게 반려 의견을 넘김).
 *
 * 사람이 결정만 하는 관문이라 부르는 대상(ref)도, 넣어줄 값(input)도 없습니다. 결정할 내용은 사람이 실행 상세
 * 화면에서 앞 step들의 결과(context.steps)를 보고 판단합니다.
 * forEach도 없습니다. 결정은 step id 하나로만 구분되는데, 같은 step을 여러 번 동시에
 * 돌리면 "그중 어느 실행에 대한 결정인지"를 구분할 방법이 없기 때문입니다.
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다. decision API가 이 id로 결정을 기록합니다.
 * @param approverRole (옵셔널)누가 결정해야 하는지 기록해 두는 값입니다. 서버가 실제로 역할을 검사하지는 않습니다.
 * @param onSuccess    (옵셔널)승인됐을 때 갈 step의 id(또는 "SUCCESS")입니다. routes와 함께 쓸 수 없습니다.
 * @param onFailure    (옵셔널)반려됐을 때 갈 step의 id(또는 "FAIL")입니다. routes와 함께 쓸 수 없습니다.
 * @param routes       (옵셔널)선택지 이름 → 이동할 step id(또는 "SUCCESS"/"FAIL")입니다. 적으면 선택지 방식이 됩니다.
 */
public record ApprovalStepDefinition(
	String id
	, String approverRole
	, String onSuccess
	, String onFailure
	, Map<String, String> routes
	) implements StepDefinition {

	@Override
	public StepType type() {
		return StepType.APPROVAL;
	}

	/** routes를 적어서 선택지 방식으로 결정을 받는 step인지 봅니다. */
	public boolean hasRoutes() {
		return this.routes != null && !this.routes.isEmpty();
	}

}

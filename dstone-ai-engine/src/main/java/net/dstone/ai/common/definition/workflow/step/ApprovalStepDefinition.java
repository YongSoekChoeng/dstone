package net.dstone.ai.common.definition.workflow.step;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/*.yml 의 type: APPROVAL step입니다. 사람이 승인하거나 반려할 때까지 기다립니다(runtime.step.ApprovalStepExecutor).
 *
 * 처음 실행될 때는 아직 결정이 없으므로 Workflow 전체를 WAITING_APPROVAL(승인 대기) 상태로 멈춰 둡니다.
 * 나중에 누군가 승인 또는 반려 API를 호출하면 같은 step을 다시 실행하고, 이번에는 그 결정을 output에
 * {approved, approver, comment}로 남기면서 승인이면 성공, 반려면 실패(error에 반려 사유)로 진행합니다.
 *
 * 사람이 결정만 하는 관문이라 부르는 대상(ref)도, 넣어줄 값(input)도 없습니다. 승인할 내용은 사람이 실행 상세
 * 화면에서 앞 step들의 결과(context.steps)를 보고 판단하고, 다음 step은 앞 step의 결과를 "${ .steps.id.output }"으로
 * 직접 가져다 씁니다.
 * forEach도 없습니다. 승인/반려 결정은 step id 하나로만 구분되는데, 같은 step을 여러 번 동시에
 * 돌리면 "그중 어느 실행에 대한 결정인지"를 구분할 방법이 없기 때문입니다.
 *
 *   - id: designReview
 *     type: APPROVAL
 *     approverRole: "PL"
 *     onFailure: FAIL
 * </pre>
 *
 * @param id           (필수)이 step의 이름입니다. 승인/반려 API가 이 id로 결정을 기록합니다.
 * @param approverRole (옵셔널)누가 승인해야 하는지 기록해 두는 값입니다. 서버가 실제로 역할을 검사하지는 않습니다.
 * @param onSuccess    (옵셔널)승인됐을 때 갈 step의 id(또는 "SUCCESS")입니다.
 * @param onFailure    (옵셔널)반려됐을 때 갈 step의 id(또는 "FAIL")입니다.
 */
public record ApprovalStepDefinition(
	String id
	, String approverRole
	, String onSuccess
	, String onFailure
	) implements StepDefinition {

	@Override
	public StepType type() {
		return StepType.APPROVAL;
	}

}

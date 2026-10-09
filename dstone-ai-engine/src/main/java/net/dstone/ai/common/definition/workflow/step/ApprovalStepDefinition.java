package net.dstone.ai.common.definition.workflow.step;

import java.util.LinkedHashMap;
import java.util.Map;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/*.yml 의 type: APPROVAL step입니다. 사람이 결정할 때까지 기다립니다(runtime.step.ApprovalStepExecutor).
 *
 * AI의 리뷰(SUPERVISOR)와 사람의 승인은 별개입니다. 이 step은 권한이 있는 사람이 직접 고르는 관문입니다.
 *
 * 처음 실행될 때는 아직 결정이 없으므로 Workflow 전체를 WAITING_APPROVAL(승인 대기) 상태로 멈춰 둡니다.
 * 나중에 누군가 decision API를 호출하면 같은 step을 다시 실행하고, 이번에는 그 결정대로 다음 step으로 갑니다.
 *
 * 사람은 routes에 적힌 이름 중 하나를 고르고(decision), 그 이름에 적힌 step으로 갑니다.
 * 이름은 자유롭게 정합니다. 승인/반려만 필요하면 APPROVED / REJECTED 두 개를 적습니다.
 *
 *   - id: step04
 *     type: APPROVAL
 *     approval:
 *       artifact: analysis-document          # 무엇을 승인하는지(화면에 보여 줄 이름)
 *       approverRole: ANALYSIS_MANAGER
 *       requireCommentOnReject: true
 *     output:
 *       result: "state.analysisApproval"     # {decision, approver, comment}
 *     routes:
 *       APPROVED: step05
 *       CHANGES_REQUESTED: step02
 *       REJECTED: FAIL
 *
 * - approval.rejectTo는 routes의 REJECTED를 짧게 적는 방법입니다(둘 다 적었으면 같은 곳이어야 합니다).
 * - requireCommentOnReject: true면 APPROVED가 아닌 결정에는 의견(comment)이 꼭 있어야 합니다.
 * - 되돌아가는 흐름을 만들어도 됩니다. 한 번 쓴 결정은 지우기 때문에, 같은 APPROVAL step에 다시 오면 사람에게 다시 묻습니다.
 *   직전 결정은 output으로 저장한 state에 남아 있어서 다음 step이 "${state.analysisApproval.comment:}"처럼 꺼내 쓸 수 있습니다.
 * - 고르는 것 자체가 결정이라 이 step에는 실패가 없습니다(onFailure, next를 적지 않습니다).
 * - forEach도 없습니다. 결정은 step id 하나로만 구분되는데, 같은 step을 여러 번 동시에 돌리면
 *   "그중 어느 실행에 대한 결정인지"를 구분할 방법이 없기 때문입니다.
 * </pre>
 *
 * @param id       (필수)이 step의 이름입니다. decision API가 이 id로 결정을 기록합니다.
 * @param approval (옵셔널)무엇을 누가 승인하는지입니다.
 * @param output   (옵셔널)결정을 state 어디에 저장할지입니다.
 * @param routes   (필수)고를 수 있는 결정 이름 → 이동할 step id(또는 "END"/"FAIL")입니다(approval.rejectTo만 적어도 됩니다).
 */
public record ApprovalStepDefinition(
	String id
	, Approval approval
	, Map<String, String> output
	, Map<String, String> routes
	) implements StepDefinition {

	/** 승인을 뜻하는 결정 이름입니다. requireCommentOnReject는 이 이름이 아닌 결정에 의견을 요구합니다. */
	public static final String DECISION_APPROVED = "APPROVED";

	/** 반려를 뜻하는 결정 이름입니다. approval.rejectTo가 이 이름의 갈 곳이 됩니다. */
	public static final String DECISION_REJECTED = "REJECTED";

	/**
	 * step의 approval 입니다.
	 *
	 * @param artifact               무엇을 승인하는지 가리키는 이름입니다(화면에 보여 주고 기록에 남깁니다).
	 * @param approverRole           누가 결정해야 하는지 기록해 두는 값입니다. 서버가 실제로 역할을 검사하지는 않습니다.
	 * @param rejectTo               반려(REJECTED)했을 때 갈 step의 id입니다. routes의 REJECTED와 같은 뜻입니다.
	 * @param requireCommentOnReject true면 APPROVED가 아닌 결정에 의견(comment)이 꼭 있어야 합니다.
	 */
	public record Approval(String artifact, String approverRole, String rejectTo, Boolean requireCommentOnReject) {
	}

	@Override
	public StepType type() {
		return StepType.APPROVAL;
	}

	/** APPROVAL step은 실패가 없어서 항상 null입니다. */
	@Override
	public String onFailure() {
		return null;
	}

	/** 누가 결정해야 하는지입니다. 적지 않았으면 null입니다. */
	public String approverRole() {
		return this.approval == null ? null : this.approval.approverRole();
	}

	/** 무엇을 승인하는지입니다. 적지 않았으면 null입니다. */
	public String artifact() {
		return this.approval == null ? null : this.approval.artifact();
	}

	/** APPROVED가 아닌 결정에 의견이 꼭 있어야 하는지 봅니다. */
	public boolean requiresCommentOnReject() {
		return this.approval != null && Boolean.TRUE.equals(this.approval.requireCommentOnReject());
	}

	/**
	 * 고를 수 있는 결정 이름 → 갈 곳입니다. routes에 approval.rejectTo(REJECTED)를 더한 것입니다.
	 * 둘 다 없으면 빈 맵입니다.
	 */
	public Map<String, String> decisionRoutes() {
		Map<String, String> all = new LinkedHashMap<>();
		if (this.routes != null) {
			all.putAll(this.routes);
		}
		if (this.approval != null && this.approval.rejectTo() != null && !all.containsKey(DECISION_REJECTED)) {
			all.put(DECISION_REJECTED, this.approval.rejectTo());
		}
		return all;
	}

}

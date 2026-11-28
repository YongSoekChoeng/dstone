package net.dstone.ai.common.definition.workflow.step;

import java.util.LinkedHashMap;
import java.util.Map;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * workflows/**.yml 의 type: APPROVAL step입니다. 사람이 결정할 때까지 기다립니다(runtime.step.ApprovalStepExecutor).
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
 *       links:                             # 승인 화면에 보일 링크
 *         테스트 서버: ${state.serverCheck.url:}
 *       deliverable:                       # 실행 관리 화면에서 내려받거나 로컬 폴더에 반영할 파일
 *         dir: ${input}/output             # 새로 만들거나 고친 파일(원본 루트 기준 상대경로 그대로)
 *         deleteList: ${input}/output/_delete-files.txt
 *         baseDir: ${state.sourceRoot:}    # 원본. 로컬에서 따로 고친 파일을 가려낼 때 비교한다
 *         documentDir: ${input}            # 이 폴더 바로 아래의 md 파일은 ZIP에 함께 담는다
 *         key: ${state.projectId:}         # 같은 값이면 화면이 로컬 폴더 맵핑을 다시 쓴다
 *     save: analysisApproval               # {decision, approver, comment}
 *     routes:
 *       APPROVED: step05
 *       CHANGES_REQUESTED: step02
 *       REJECTED: FAIL
 *
 * - approval.rejectTo는 routes의 REJECTED를 짧게 적는 방법입니다(둘 다 적었으면 같은 곳이어야 합니다).
 * - requireCommentOnReject: true면 APPROVED가 아닌 결정에는 의견(comment)이 꼭 있어야 합니다.
 * - 되돌아가는 흐름을 만들어도 됩니다. 한 번 쓴 결정은 지우기 때문에, 같은 APPROVAL step에 다시 오면 사람에게 다시 묻습니다.
 *   직전 결정은 save로 저장한 state에 남아 있어서 다음 step이 ${state.analysisApproval.comment:}처럼 꺼내 쓸 수 있습니다.
 * - 고르는 것 자체가 결정이라 이 step에는 실패가 없습니다(onFailure, next를 적지 않습니다).
 * - forEach도 없습니다. 결정은 step id 하나로만 구분되는데, 같은 step을 여러 번 동시에 돌리면
 *   "그중 어느 실행에 대한 결정인지"를 구분할 방법이 없기 때문입니다.
 * </pre>
 *
 * @param id       (필수)이 step의 이름입니다. decision API가 이 id로 결정을 기록합니다.
 * @param name     (옵셔널)사람이 읽는 step 이름입니다(예: 요구사항 분석). 디자이너 화면에 id와 함께 보이고, 실행에는 쓰이지 않습니다.
 * @param approval (옵셔널)무엇을 누가 승인하는지입니다.
 * @param save     (옵셔널)결정({decision, approver, comment})을 저장할 state 이름입니다.
 * @param routes   (필수)고를 수 있는 결정 이름 → 이동할 step id(또는 "END"/"FAIL")입니다(approval.rejectTo만 적어도 됩니다).
 */
public record ApprovalStepDefinition(
	String id
	, String name
	, Approval approval
	, String save
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
	 * @param hints                  결정 이름 → 그 결정이 무슨 뜻인지 적은 안내문입니다. 승인 화면에서 그 결정에 마우스를 올리면 풍선도움말로 보입니다.
	 *                               실행에는 쓰이지 않으며, 키는 고를 수 있는 결정 이름이어야 합니다.
	 * @param links                  표시 이름 → 주소입니다. 승인 화면에 눌러서 여는 링크로 보입니다(예: 앞 step이 띄운 서버 주소).
	 *                               값은 ${state.…}처럼 읽는 표현식이나 주소 글자이고, http·https 주소가 아니면(값이 없으면) 보이지 않습니다.
	 * @param deliverable            승인자가 내려받아 확인할 결과 파일이 어디 있는지입니다. 없으면 화면에 내려받기가 보이지 않습니다.
	 */
	public record Approval(String artifact, String approverRole, String rejectTo, Boolean requireCommentOnReject, Map<String, String> hints, Map<String, String> links, Deliverable deliverable) {
	}

	/**
	 * <pre>
	 * approval.deliverable 입니다. 값은 모두 ${input}, ${state.…}처럼 읽는 표현식이나 글자입니다.
	 * </pre>
	 *
	 * @param dir         (필수)새로 만들거나 고친 파일이 있는 폴더입니다. 안의 상대경로가 원본 루트 기준 경로입니다.
	 * @param deleteList  지울 파일 목록(원본 루트 기준 상대경로가 한 줄에 하나)입니다. 파일이 없으면 지울 것이 없는 것입니다.
	 * @param baseDir     원본 폴더입니다. 로컬 파일이 원본과 같은지 비교할 때 씁니다.
	 * @param documentDir 이 폴더 바로 아래의 md 파일을 ZIP에 함께 담습니다.
	 * @param key         화면이 로컬 폴더 맵핑을 저장할 때 쓰는 이름입니다(예: 프로젝트 id). 비면 Workflow id를 씁니다.
	 */
	public record Deliverable(String dir, String deleteList, String baseDir, String documentDir, String key) {

		/** 표현식을 한꺼번에 읽거나 검사할 수 있게 적은 값만 이름 → 표현식으로 돌려줍니다. */
		public Map<String, String> templates() {
			Map<String, String> all = new LinkedHashMap<String, String>();
			putIfPresent(all, "dir", this.dir);
			putIfPresent(all, "deleteList", this.deleteList);
			putIfPresent(all, "baseDir", this.baseDir);
			putIfPresent(all, "documentDir", this.documentDir);
			putIfPresent(all, "key", this.key);
			return all;
		}

		private static void putIfPresent(Map<String, String> map, String name, String value) {
			if (value != null) {
				map.put(name, value);
			}
		}
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

	/** 결정 이름 → 안내문입니다. 적지 않았으면 빈 맵입니다. */
	public Map<String, String> hints() {
		if (this.approval == null || this.approval.hints() == null) {
			return new LinkedHashMap<>();
		}
		return this.approval.hints();
	}

	/** 표시 이름 → 주소(표현식)입니다. 적지 않았으면 빈 맵입니다. */
	public Map<String, String> links() {
		if (this.approval == null || this.approval.links() == null) {
			return new LinkedHashMap<>();
		}
		return this.approval.links();
	}

	/** 내려받을 결과 파일의 위치입니다. 적지 않았으면 null입니다. */
	public Deliverable deliverable() {
		return this.approval == null ? null : this.approval.deliverable();
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

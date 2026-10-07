/**
 * "리비전 비교" 화면.
 */
var DstoneKnowledgeDiff = (function () {

	var K = DstoneKnowledge;
	var el = {};

	/** 종류의 이름과, 목록의 두 칸(name, detail)이 뜻하는 것 */
	var KINDS = {
		FILE: ["파일", "경로", "종류"],
		TYPE: ["타입", "타입", "종류"],
		METHOD: ["메소드", "메소드", "반환 타입 (바뀌었으면 앞 → 뒤)"],
		ENDPOINT: ["진입점", "주소", "처리하는 메소드"],
		STATEMENT: ["SQL statement", "이름", "종류"],
		TABLE_USE: ["테이블 사용", "테이블", "읽기·쓰기 ← statement"],
		CALL: ["호출", "부르는 메소드", "불리는 메소드"]
	};

	var CHANGE_LABELS = { ADDED: "생김", REMOVED: "없어짐", CHANGED: "바뀜" };

	function init() {
		["kn-diff-project", "kn-diff-revision", "kn-diff-base", "kn-diff-limit", "kn-diff-btn", "kn-diff-message", "kn-diff-summary", "kn-diff-result"]
			.forEach(function (id) {
				el[id] = document.getElementById(id);
			});
		el["kn-diff-project"].addEventListener("change", loadRevisions);
		el["kn-diff-btn"].addEventListener("click", runDiff);
		K.loadProjects(el["kn-diff-project"]).then(loadRevisions).catch(function (err) {
			K.message(el["kn-diff-message"], err.message, true);
		});
	}

	function loadRevisions() {
		var projectId = el["kn-diff-project"].value;
		return Promise.all([
			K.loadRevisions(projectId, el["kn-diff-revision"]),
			K.loadRevisions(projectId, el["kn-diff-base"], "(자동: 증분 분석의 기준, 없으면 바로 앞 리비전)")
		]);
	}

	function runDiff() {
		var revisionId = el["kn-diff-revision"].value;
		if (!revisionId) {
			K.message(el["kn-diff-message"], "리비전을 고르세요.", true);
			return;
		}
		el["kn-diff-summary"].innerHTML = "";
		el["kn-diff-result"].innerHTML = "";
		K.message(el["kn-diff-message"], "비교 중...");
		K.call("GET", "/api/revisions/" + revisionId + "/diff", { base: el["kn-diff-base"].value, limit: el["kn-diff-limit"].value }).then(function (result) {
			K.message(el["kn-diff-message"], "#" + result.base.revisionId + " " + result.base.revisionLabel + "  →  #" + result.revision.revisionId + " "
				+ result.revision.revisionLabel + (result.truncated ? " · 목록은 종류마다 일부만 보입니다(건수는 전체)" : ""));
			renderSummary(result.summary);
			Object.keys(result.changes).forEach(function (kind) {
				renderKind(kind, result.summary[kind], result.changes[kind]);
			});
		}).catch(function (err) {
			K.message(el["kn-diff-message"], err.message, true);
		});
	}

	function renderSummary(summary) {
		var rows = Object.keys(summary).map(function (kind) {
			return { kind: (KINDS[kind] || [kind])[0], ADDED: summary[kind].ADDED, REMOVED: summary[kind].REMOVED, CHANGED: summary[kind].CHANGED };
		});
		el["kn-diff-summary"].innerHTML = "<div class=\"kn-section-title\">요약</div><div class=\"kn-scroll\"></div>";
		K.table(el["kn-diff-summary"].querySelector(".kn-scroll"), [
			{ key: "kind", label: "종류" },
			{ key: "ADDED", label: "생김", num: true },
			{ key: "REMOVED", label: "없어짐", num: true },
			{ key: "CHANGED", label: "바뀜", num: true }
		], rows);
	}

	function renderKind(kind, counts, rows) {
		var total = counts.ADDED + counts.REMOVED + counts.CHANGED;
		if (total === 0) {
			return;
		}
		var labels = KINDS[kind] || [kind, "이름", "내용"];
		var wrap = document.createElement("div");
		wrap.innerHTML = "<div class=\"kn-section-title\">" + K.escapeHtml(labels[0]) + " · " + total + "건"
			+ (rows.length < total ? " (앞쪽 " + rows.length + "건만 보임)" : "") + "</div><div class=\"kn-scroll\"></div>";
		K.table(wrap.querySelector(".kn-scroll"), [
			{ key: "change", label: "", html: function (row) {
				return "<span class=\"kn-change-" + K.escapeHtml(row.change) + "\">" + K.escapeHtml(CHANGE_LABELS[row.change] || row.change) + "</span>";
			} },
			{ key: "name", label: labels[1], mono: true },
			{ key: "detail", label: labels[2], mono: true },
			{ key: "path", label: "파일", mono: true }
		], rows);
		el["kn-diff-result"].appendChild(wrap);
	}

	return { init: init };
})();

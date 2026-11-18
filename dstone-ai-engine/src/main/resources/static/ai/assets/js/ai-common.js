/*
 * AI 화면들이 같이 쓰는 작은 도구 모음.
 * - 실행 상태 코드(WAITING_APPROVAL 등)를 사람이 읽는 이름으로 바꾼다. 원래 코드는 title(마우스를 올리면 보임)에 남긴다.
 * - step 종류(AGENT, TOOL …)의 이름. 색은 main.css 의 --ai-step-* 변수에 있다.
 * common/head.jsp 가 모든 AI 화면에 읽어 둔다.
 */
var DstoneAi = (function () {

	var STATUS_LABELS = {
		QUEUED: "대기 중",
		RUNNING: "실행 중",
		WAITING_APPROVAL: "승인 대기",
		PAUSED: "일시정지",
		DONE: "완료",
		FAILED: "실패",
		CANCELLED: "취소됨",
		ERROR: "오류"
	};

	var STEP_TYPES = {
		AGENT: "Agent가 판단",
		SUPERVISOR: "결과 검토",
		ROUTER: "갈림길",
		TOOL: "도구 실행",
		APPROVAL: "사람이 승인"
	};

	/** 상태 코드 → 한글 이름. 모르는 코드는 그대로 */
	function statusLabel(status) {
		if (!status) {
			return "-";
		}
		return STATUS_LABELS[status] || status;
	}

	/**
	 * 상태 딱지(span.workflow-badge)를 고친다. 글자는 한글 이름, data-status 와 title 에는 원래 코드.
	 * 다른 코드가 상태를 비교할 때는 글자 말고 data-status 를 본다.
	 */
	function setBadge(el, status) {
		el.textContent = statusLabel(status);
		el.className = "workflow-badge workflow-badge-" + String(status || "unknown").toLowerCase();
		el.setAttribute("data-status", status || "");
		el.title = status || "";
	}

	/** 목록 표에 넣을 상태 딱지 HTML */
	function badgeHtml(status) {
		var code = escapeHtml(status || "");
		return "<span class=\"workflow-badge workflow-badge-" + escapeHtml(String(status || "unknown").toLowerCase()) + "\" data-status=\"" + code + "\" title=\"" + code + "\">"
			+ escapeHtml(statusLabel(status)) + "</span>";
	}

	function stepTypeLabel(type) {
		return STEP_TYPES[type] || type || "";
	}

	function escapeHtml(value) {
		var div = document.createElement("div");
		div.textContent = value == null ? "" : value;
		return div.innerHTML.replace(/"/g, "&quot;");
	}

	return {
		STEP_TYPES: STEP_TYPES,
		statusLabel: statusLabel,
		setBadge: setBadge,
		badgeHtml: badgeHtml,
		stepTypeLabel: stepTypeLabel,
		escapeHtml: escapeHtml
	};
})();

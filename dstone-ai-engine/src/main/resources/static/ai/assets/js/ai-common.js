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

	/**
	 * 엔진 API(/api/ai/**)를 부른다. 성공하면 응답 JSON(본문이 없으면 null)을, 실패하면 서버가 준 사유를 담은 Error 를 돌려준다.
	 * 화면을 연 사람은 세션 쿠키로 엔진에 전해진다(엔진의 RequesterContext 가 세션 사용자를 읽는다).
	 * body 가 있으면 JSON 으로 보낸다.
	 */
	function api(method, url, body) {
		var init = { method: method, headers: { "Accept": "application/json" } };
		if (body !== undefined && body !== null) {
			init.headers["Content-Type"] = "application/json";
			init.body = JSON.stringify(body);
		}
		return fetch(url, init).then(function (response) {
			return response.text().then(function (raw) {
				var parsed = null;
				try {
					parsed = raw ? JSON.parse(raw) : null;
				} catch (e) {
					parsed = null;
				}
				if (!response.ok) {
					var reason = parsed && (parsed.message || parsed.error);
					var error = new Error(reason || ("서버 오류(" + response.status + ")"));
					error.status = response.status;
					throw error;
				}
				return parsed;
			});
		});
	}

	/** 실행 한 건의 API 주소. suffix 는 "/pause" 처럼 뒤에 붙일 경로(없으면 상세) */
	function executionUrl(executionId, suffix) {
		return "/api/ai/workflow/executions/" + encodeURIComponent(executionId) + (suffix || "");
	}

	return {
		api: api,
		executionUrl: executionUrl,
		STEP_TYPES: STEP_TYPES,
		statusLabel: statusLabel,
		setBadge: setBadge,
		badgeHtml: badgeHtml,
		stepTypeLabel: stepTypeLabel,
		escapeHtml: escapeHtml
	};
})();

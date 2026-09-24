var DstoneAiWorkflowAdmin = (function () {

	var urls = {};
	var statusFilterEl, refreshBtn, listBodyEl;
	var detailPanelEl, detailIdEl, detailBadgeEl, detailResultEl, detailContextEl, historyBodyEl;
	var decisionFormEl, approverEl, commentEl, approveBtn, rejectBtn, decisionStatusEl;

	var activeExecutionId = null;

	function init(options) {
		urls = options;

		statusFilterEl = document.getElementById("workflow-admin-status-filter");
		refreshBtn = document.getElementById("workflow-admin-refresh-btn");
		listBodyEl = document.getElementById("workflow-admin-list-body");

		detailPanelEl = document.getElementById("workflow-admin-detail-panel");
		detailIdEl = document.getElementById("workflow-admin-detail-id");
		detailBadgeEl = document.getElementById("workflow-admin-detail-badge");
		detailResultEl = document.getElementById("workflow-admin-detail-result");
		detailContextEl = document.getElementById("workflow-admin-detail-context");
		historyBodyEl = document.getElementById("workflow-admin-history-body");

		decisionFormEl = document.getElementById("workflow-admin-decision-form");
		approverEl = document.getElementById("workflow-admin-approver");
		commentEl = document.getElementById("workflow-admin-comment");
		approveBtn = document.getElementById("workflow-admin-approve-btn");
		rejectBtn = document.getElementById("workflow-admin-reject-btn");
		decisionStatusEl = document.getElementById("workflow-admin-decision-status");

		refreshBtn.addEventListener("click", loadList);
		statusFilterEl.addEventListener("change", loadList);
		approveBtn.addEventListener("click", function () { decide(true); });
		rejectBtn.addEventListener("click", function () { decide(false); });

		loadList();
	}

	function loadList() {
		var status = statusFilterEl.value;
		var url = urls.listUrl + (status ? "?status=" + encodeURIComponent(status) : "");
		fetch(url, { method: "POST" })
			.then(function (response) { return response.json(); })
			.then(renderList)
			.catch(function (err) { alert("목록 조회 실패: " + err.message); });
	}

	function renderList(executions) {
		listBodyEl.innerHTML = "";
		(executions || []).forEach(function (row) {
			var tr = document.createElement("tr");
			tr.innerHTML =
				"<td><a href=\"javascript:void(0)\" class=\"workflow-history-jobid\">" + escapeHtml(row.executionId) + "</a></td>" +
				"<td>" + escapeHtml(row.workflowId) + "</td>" +
				"<td><span class=\"workflow-badge workflow-badge-" + escapeHtml((row.status || "unknown").toLowerCase()) + "\">" + escapeHtml(row.status) + "</span></td>" +
				"<td>" + row.currentStepIndex + "</td>" +
				"<td>" + escapeHtml(row.updatedAt) + "</td>";
			tr.querySelector(".workflow-history-jobid").addEventListener("click", function () {
				loadDetail(row.executionId);
			});
			listBodyEl.appendChild(tr);
		});
	}

	function loadDetail(executionId) {
		fetch(urls.detailUrl + "?executionId=" + encodeURIComponent(executionId), { method: "POST" })
			.then(function (response) { return response.json(); })
			.then(renderDetail)
			.catch(function (err) { alert("상세 조회 실패: " + err.message); });
	}

	function renderDetail(detail) {
		activeExecutionId = detail.executionId;
		detailPanelEl.style.display = "";

		detailIdEl.textContent = detail.executionId;
		detailBadgeEl.textContent = detail.status;
		detailBadgeEl.className = "workflow-badge workflow-badge-" + (detail.status || "unknown").toLowerCase();
		detailResultEl.value = detail.resultText || detail.errorMessage || "";
		detailContextEl.value = detail.context ? JSON.stringify(detail.context, null, 2) : "";

		historyBodyEl.innerHTML = "";
		(detail.history || []).forEach(function (h) {
			var tr = document.createElement("tr");
			tr.innerHTML =
				"<td>" + escapeHtml(h.stepId) + "</td>" +
				"<td>" + escapeHtml(h.stepType) + "</td>" +
				"<td>" + escapeHtml(h.ref) + "</td>" +
				"<td>" + (h.success ? "성공" : "실패") + "</td>" +
				"<td>" + (h.durationMs == null ? "-" : h.durationMs) + "</td>" +
				"<td>" + escapeHtml(h.success ? h.outputSummary : h.failureReason) + "</td>" +
				"<td>" + escapeHtml(h.executedAt) + "</td>";
			historyBodyEl.appendChild(tr);
		});

		var waitingApproval = detail.status === "WAITING_APPROVAL";
		decisionFormEl.style.display = waitingApproval ? "" : "none";
		if (waitingApproval) {
			decisionStatusEl.textContent = "";
		}
	}

	function decide(approved) {
		if (!activeExecutionId) {
			return;
		}
		if (!approverEl.value.trim()) {
			alert("approver를 입력하세요.");
			return;
		}
		decisionStatusEl.textContent = "처리 중입니다...";
		fetch(urls.decisionUrl, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				executionId: activeExecutionId,
				approved: approved,
				approver: approverEl.value.trim(),
				comment: commentEl.value.trim() || null
			})
		})
			.then(function (response) {
				if (!response.ok) {
					throw new Error("서버 오류(" + response.status + ")");
				}
				return response.json();
			})
			.then(function (detail) {
				decisionStatusEl.textContent = "처리 완료 (" + detail.status + ")";
				renderDetail(detail);
				loadList();
			})
			.catch(function (err) {
				decisionStatusEl.textContent = "처리 실패: " + err.message;
			});
	}

	function escapeHtml(value) {
		var div = document.createElement("div");
		div.textContent = value == null ? "" : value;
		return div.innerHTML;
	}

	return { init: init };
})();

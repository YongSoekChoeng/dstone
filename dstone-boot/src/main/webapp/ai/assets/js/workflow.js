var DstoneAiWorkflow = (function () {

	var urls = {};
	var POLL_INTERVAL_MS = 2000;

	var workflowIdEl, workflowIdDescEl, sessionIdEl, messageEl, variablesEl;
	var submitBtn, submitStatusEl;
	var executionIdEl, statusBadgeEl, resultEl, pollStopBtn, pollRefreshBtn;
	var historyBodyEl;

	var pollTimer = null;
	var activeExecutionId = null;
	var historyRows = {}; // executionId -> <tr> element, 화면에서 제출한 작업만 기억(서버 저장 아님)
	var workflowsById = {}; // id -> description, 목록 조회 결과를 드롭다운 change 시 다시 쓰기 위해 기억

	function init(options) {
		urls = options;

		workflowIdEl = document.getElementById("workflow-id");
		workflowIdDescEl = document.getElementById("workflow-id-desc");
		sessionIdEl = document.getElementById("workflow-session-id");
		messageEl = document.getElementById("workflow-message");
		variablesEl = document.getElementById("workflow-variables");

		submitBtn = document.getElementById("workflow-submit-btn");
		submitStatusEl = document.getElementById("workflow-submit-status");

		executionIdEl = document.getElementById("workflow-job-id");
		statusBadgeEl = document.getElementById("workflow-status-badge");
		resultEl = document.getElementById("workflow-result");
		pollStopBtn = document.getElementById("workflow-poll-stop-btn");
		pollRefreshBtn = document.getElementById("workflow-poll-refresh-btn");

		historyBodyEl = document.getElementById("workflow-history-body");

		submitBtn.addEventListener("click", submit);
		pollStopBtn.addEventListener("click", stopPolling);
		pollRefreshBtn.addEventListener("click", function () {
			if (activeExecutionId) {
				fetchStatus(activeExecutionId);
			}
		});
		workflowIdEl.addEventListener("change", updateWorkflowDescription);

		loadWorkflowList();
	}

	function loadWorkflowList() {
		fetch(urls.listUrl)
			.then(function (response) {
				if (!response.ok) {
					throw new Error("서버 오류(" + response.status + ")");
				}
				return response.json();
			})
			.then(function (workflows) {
				workflowIdEl.innerHTML = "";
				workflowsById = {};
				(workflows || []).forEach(function (workflow) {
					workflowsById[workflow.id] = workflow.description;
					var option = document.createElement("option");
					option.value = workflow.id;
					option.textContent = workflow.id;
					workflowIdEl.appendChild(option);
				});
				updateWorkflowDescription();
			})
			.catch(function (err) {
				workflowIdDescEl.textContent = "workflow 목록을 불러오지 못했습니다: " + err.message;
			});
	}

	function updateWorkflowDescription() {
		var description = workflowsById[workflowIdEl.value];
		workflowIdDescEl.textContent = description || "";
	}

	function submit() {
		var workflowId = workflowIdEl.value.trim();
		var message = messageEl.value.trim();
		if (!workflowId) {
			alert("workflowId를 선택하세요.");
			return;
		}
		if (!message) {
			alert("message를 입력하세요.");
			return;
		}

		var variables = null;
		var variablesText = variablesEl.value.trim();
		if (variablesText) {
			try {
				variables = JSON.parse(variablesText);
			} catch (e) {
				alert("variables는 올바른 JSON 객체여야 합니다: " + e.message);
				return;
			}
		}

		setSubmitLoading(true);

		fetch(urls.submitUrl, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				workflowId: workflowId,
				message: message,
				sessionId: sessionIdEl.value.trim() || null,
				variables: variables
			})
		})
			.then(function (response) {
				if (!response.ok) {
					throw new Error("서버 오류(" + response.status + ")");
				}
				return response.json();
			})
			.then(function (result) {
				if (result.error) {
					submitStatusEl.textContent = "제출 실패: " + result.error;
					return;
				}
				submitStatusEl.textContent = "제출 완료";
				startTracking(workflowId, result.executionId);
			})
			.catch(function (err) {
				submitStatusEl.textContent = "요청 실패: " + err.message;
			})
			.finally(function () {
				setSubmitLoading(false);
			});
	}

	function setSubmitLoading(loading) {
		submitBtn.disabled = loading;
		submitStatusEl.textContent = loading ? "제출 중입니다..." : submitStatusEl.textContent;
	}

	function startTracking(workflowId, executionId) {
		stopPolling();

		activeExecutionId = executionId;
		executionIdEl.textContent = executionId;
		setBadge("RUNNING");
		resultEl.value = "";
		pollStopBtn.disabled = false;
		pollRefreshBtn.disabled = false;

		addHistoryRow(workflowId, executionId);

		fetchStatus(executionId);
		pollTimer = setInterval(function () {
			fetchStatus(executionId);
		}, POLL_INTERVAL_MS);
	}

	function fetchStatus(executionId) {
		fetch(urls.statusUrl, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ executionId: executionId })
		})
			.then(function (response) { return response.json(); })
			.then(function (result) {
				if (executionId !== activeExecutionId) {
					// 그 사이 다른 executionId를 선택했으면(이력 클릭) 이 응답은 화면에 반영하지 않는다.
					updateHistoryRow(executionId, result.status);
					return;
				}
				setBadge(result.status);
				resultEl.value = result.status === "FAILED" || result.status === "ERROR"
					? (result.error || "")
					: (result.result || "");
				updateHistoryRow(executionId, result.status);

				if (result.status === "DONE" || result.status === "FAILED" || result.status === "ERROR") {
					stopPolling();
				}
			})
			.catch(function (err) {
				if (executionId === activeExecutionId) {
					setBadge("ERROR");
					resultEl.value = "상태 조회 실패: " + err.message;
					stopPolling();
				}
			});
	}

	function stopPolling() {
		if (pollTimer) {
			clearInterval(pollTimer);
			pollTimer = null;
		}
		pollStopBtn.disabled = true;
	}

	function setBadge(status) {
		statusBadgeEl.textContent = status || "-";
		statusBadgeEl.className = "workflow-badge workflow-badge-" + (status || "unknown").toLowerCase();
	}

	function addHistoryRow(workflowId, executionId) {
		var tr = document.createElement("tr");
		tr.innerHTML =
			"<td>" + escapeHtml(new Date().toLocaleTimeString()) + "</td>" +
			"<td>" + escapeHtml(workflowId) + "</td>" +
			"<td><a href=\"javascript:void(0)\" class=\"workflow-history-jobid\">" + escapeHtml(executionId) + "</a></td>" +
			"<td class=\"workflow-history-status\">RUNNING</td>";
		tr.querySelector(".workflow-history-jobid").addEventListener("click", function () {
			trackExisting(executionId);
		});
		historyBodyEl.insertBefore(tr, historyBodyEl.firstChild);
		historyRows[executionId] = tr;
	}

	function updateHistoryRow(executionId, status) {
		var tr = historyRows[executionId];
		if (tr) {
			tr.querySelector(".workflow-history-status").textContent = status || "-";
		}
	}

	/** 이력 목록에서 이전에 제출한 executionId를 다시 클릭했을 때, 그 작업의 폴링을 다시 시작한다. */
	function trackExisting(executionId) {
		stopPolling();
		activeExecutionId = executionId;
		executionIdEl.textContent = executionId;
		resultEl.value = "";
		pollStopBtn.disabled = false;
		pollRefreshBtn.disabled = false;

		fetchStatus(executionId);
		pollTimer = setInterval(function () {
			fetchStatus(executionId);
		}, POLL_INTERVAL_MS);
	}

	function escapeHtml(value) {
		var div = document.createElement("div");
		div.textContent = value == null ? "" : value;
		return div.innerHTML;
	}

	return { init: init };
})();

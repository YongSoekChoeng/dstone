/*
 * Workflow 실행 관리 화면.
 * 실행 상세는 위에서부터 [머리: 무슨 실행이고 어디까지 왔나] → [지금 할 일] → [진행 기록 + 고른 step] → [결과] → [산출 문서] 순서다.
 * - 진행 기록 표는 모든 줄을 한 줄로 그린다(줄 높이가 고르게). 줄을 고르면 오른쪽 구역에 그 step의 전체 내용, 문서, [다시 실행]이 나온다.
 * - 실행 중이면 3초마다 상세를 다시 읽는다. 사람이 줄을 고르지 않았으면 새로 생긴 마지막 줄을 따라간다.
 */
var DstoneAiWorkflowAdmin = (function () {

	var urls = {};
	var statusFilterEl, workflowFilterEl, executionFilterEl, mineFilterEl, refreshBtn, resetBtn, listBodyEl, totalEl, pagerEl;
	var detailPanelEl, detailIdEl, detailBadgeEl, detailRequesterEl, detailResultEl, detailContextEl, historyBodyEl;
	var decisionFormEl, approverEl, commentEl, approveBtn, rejectBtn, decisionStatusEl, decisionStepEl, routeButtonsEl;
	var decideStepEl, decideRoleEl, decideRoleItemEl, decideChoicesBoxEl, decideButtonsEl;
	// 결정을 보내는 중이면 true. 그동안 단추를 막아 두 번 눌리지 않게 한다
	var deciding = false;

	var controlEl, controlMessageEl, controlWarningsEl, controlStatusEl, pauseBtn, resumeBtn;
	var docsBodyEl, docsAllEl, docsCountEl;
	var rewindEl, rewindMessageEl, rewindWarningsEl, rewindFilesEl, rewindKeptEl, rewindClearLabelEl, rewindClearEl, rewindStatusEl, rewindRunBtn;
	// 머리, 지금 할 일, 고른 step 구역
	var headWorkflowEl, headCreatedEl, headElapsedEl, stripEl, stripTextEl;
	var todoEl, todoTitleEl, todoReasonEl, historyCountEl;
	var historyPagerEl;
	var paneEmptyEl, paneBodyEl, paneDotEl, paneIdEl, paneNameEl, paneBadgeEl, paneMetaEl, paneTextEl, paneDocsEl, paneRewindBtn, paneCopyStatusEl;
	// [다시 실행]으로 고른 step의 미리보기(엔진 GET .../rewind 응답). 패널을 닫으면 비운다.
	var currentRewind = null;

	var PAGE_SIZE = 10;
	var POLL_MILLIS = 3000;

	var activeExecutionId = null;
	var currentPage = 0;
	var currentDetail = null;
	var pollTimer = null;
	// 지금 보고 있는 실행의 step 문서 목록(엔진 GET .../files 응답). 다른 실행을 고르면 비운다.
	var currentFiles = null;
	// 진행 기록에서 고른 줄의 순번(-1이면 없음). 사람이 직접 골랐으면 pickedByUser가 true다.
	var selectedIndex = -1;
	var pickedByUser = false;
	/** 진행 기록 페이지(0 = 가장 최근 10줄). 사람이 페이지 단추를 눌렀으면 자동 갱신 때도 그 페이지에 머문다 */
	var historyPage = 0;
	var historyPageByUser = false;
	var HISTORY_PAGE_SIZE = 10;
	// 고른 step 구역에 마지막으로 그린 내용. 같으면 다시 그리지 않는다(읽던 스크롤을 지킨다).
	var paneKey = null;
	// Workflow별 step 정의(흐름 띠용). 값이 "loading"이면 읽는 중이다.
	var stepDefs = {};

	function init(options) {
		urls = options;

		statusFilterEl = document.getElementById("workflow-admin-status-filter");
		workflowFilterEl = document.getElementById("workflow-admin-workflow-filter");
		executionFilterEl = document.getElementById("workflow-admin-execution-filter");
		mineFilterEl = document.getElementById("workflow-admin-mine-filter");
		refreshBtn = document.getElementById("workflow-admin-refresh-btn");
		resetBtn = document.getElementById("workflow-admin-reset-btn");
		totalEl = document.getElementById("workflow-admin-total");
		pagerEl = document.getElementById("workflow-admin-pager");
		controlEl = document.getElementById("workflow-admin-control");
		controlMessageEl = document.getElementById("workflow-admin-control-message");
		controlWarningsEl = document.getElementById("workflow-admin-control-warnings");
		controlStatusEl = document.getElementById("workflow-admin-control-status");
		pauseBtn = document.getElementById("workflow-admin-pause-btn");
		resumeBtn = document.getElementById("workflow-admin-resume-btn");
		listBodyEl = document.getElementById("workflow-admin-list-body");

		detailPanelEl = document.getElementById("workflow-admin-detail-panel");
		detailIdEl = document.getElementById("workflow-admin-detail-id");
		detailBadgeEl = document.getElementById("workflow-admin-detail-badge");
		detailRequesterEl = document.getElementById("workflow-admin-detail-requester");
		detailResultEl = document.getElementById("workflow-admin-detail-result");
		detailContextEl = document.getElementById("workflow-admin-detail-context");
		historyBodyEl = document.getElementById("workflow-admin-history-body");
		historyCountEl = document.getElementById("wa-history-count");
		historyPagerEl = document.getElementById("wa-history-pager");

		headWorkflowEl = document.getElementById("wa-head-workflow");
		headCreatedEl = document.getElementById("wa-head-created");
		headElapsedEl = document.getElementById("wa-head-elapsed");
		stripEl = document.getElementById("wa-strip");
		stripTextEl = document.getElementById("wa-strip-text");

		todoEl = document.getElementById("wa-todo");
		todoTitleEl = document.getElementById("wa-todo-title");
		// 실패했을 때 사유 첫머리. 전체는 아래 '결과'와 진행 기록에 있다.
		todoReasonEl = document.createElement("div");
		todoReasonEl.className = "wa-todo-reason";
		todoTitleEl.parentNode.insertBefore(todoReasonEl, todoTitleEl.nextSibling);

		paneEmptyEl = document.getElementById("wa-step-empty");
		paneBodyEl = document.getElementById("wa-step-body");
		paneDotEl = document.getElementById("wa-step-dot");
		paneIdEl = document.getElementById("wa-step-id");
		paneNameEl = document.getElementById("wa-step-title");
		paneBadgeEl = document.getElementById("wa-step-badge");
		paneMetaEl = document.getElementById("wa-step-meta");
		paneTextEl = document.getElementById("wa-step-text");
		paneDocsEl = document.getElementById("wa-step-docs");
		paneRewindBtn = document.getElementById("wa-step-rewind");
		paneCopyStatusEl = document.getElementById("wa-step-copy-status");
		paneRewindBtn.addEventListener("click", function () {
			var h = selectedHistory();
			if (h) {
				openRewind(baseStepId(h.stepId));
			}
		});
		document.getElementById("wa-step-copy").addEventListener("click", copyPaneText);

		decisionFormEl = document.getElementById("workflow-admin-decision-form");
		approverEl = document.getElementById("workflow-admin-approver");
		commentEl = document.getElementById("workflow-admin-comment");
		approveBtn = document.getElementById("workflow-admin-approve-btn");
		rejectBtn = document.getElementById("workflow-admin-reject-btn");
		decisionStatusEl = document.getElementById("workflow-admin-decision-status");
		decisionStepEl = document.getElementById("workflow-admin-decision-step");
		routeButtonsEl = document.getElementById("workflow-admin-route-buttons");
		decideStepEl = document.getElementById("wa-decide-step");
		decideRoleEl = document.getElementById("wa-decide-role");
		decideRoleItemEl = document.getElementById("wa-decide-role-item");
		decideChoicesBoxEl = document.getElementById("wa-decide-choices-box");
		decideButtonsEl = document.getElementById("wa-decide-buttons");
		docsBodyEl = document.getElementById("workflow-admin-docs-body");
		docsAllEl = document.getElementById("workflow-admin-docs-all");
		docsCountEl = document.getElementById("workflow-admin-docs-count");
		docsAllEl.addEventListener("change", renderDocs);

		rewindEl = document.getElementById("workflow-admin-rewind");
		rewindMessageEl = document.getElementById("workflow-admin-rewind-message");
		rewindWarningsEl = document.getElementById("workflow-admin-rewind-warnings");
		rewindFilesEl = document.getElementById("workflow-admin-rewind-files");
		rewindKeptEl = document.getElementById("workflow-admin-rewind-kept");
		rewindClearLabelEl = document.getElementById("workflow-admin-rewind-clear-label");
		rewindClearEl = document.getElementById("workflow-admin-rewind-clear");
		rewindStatusEl = document.getElementById("workflow-admin-rewind-status");
		rewindRunBtn = document.getElementById("workflow-admin-rewind-run-btn");
		rewindRunBtn.addEventListener("click", runRewind);
		document.getElementById("workflow-admin-rewind-cancel-btn").addEventListener("click", closeRewind);

		// 진행 기록: 줄을 누르거나, 줄에 초점을 두고 ↑/↓/Enter
		historyBodyEl.addEventListener("click", function (event) {
			var tr = event.target.closest("tr[data-index]");
			if (tr) {
				selectRow(Number(tr.getAttribute("data-index")), true);
			}
		});
		historyBodyEl.addEventListener("keydown", onHistoryKey);

		// step 문서 팝업. 저장하면 목록(크기, 수정 시각)을 다시 읽는다.
		DstoneAiDocViewer.init({
			onSaved: function (executionId) {
				if (executionId === activeExecutionId) {
					loadFiles(executionId);
				}
			}
		});

		refreshBtn.addEventListener("click", function () { loadList(0); });
		resetBtn.addEventListener("click", function () {
			statusFilterEl.value = "";
			workflowFilterEl.value = "";
			executionFilterEl.value = "";
			mineFilterEl.checked = true;
			loadList(0);
		});
		statusFilterEl.addEventListener("change", function () { loadList(0); });
		mineFilterEl.addEventListener("change", function () { loadList(0); });
		[workflowFilterEl, executionFilterEl].forEach(function (input) {
			input.addEventListener("keydown", function (event) {
				if (event.key === "Enter") {
					loadList(0);
				}
			});
		});
		pauseBtn.addEventListener("click", pause);
		resumeBtn.addEventListener("click", resume);
		approveBtn.addEventListener("click", function () { decide(true, null); });
		rejectBtn.addEventListener("click", function () { decide(false, null); });

		loadList(0);
	}

	function loadList(page) {
		var params = new URLSearchParams();
		params.set("status", statusFilterEl.value);
		params.set("workflowId", workflowFilterEl.value.trim());
		params.set("executionId", executionFilterEl.value.trim());
		params.set("page", page);
		params.set("size", PAGE_SIZE);
		// 여러 사람이 함께 쓰므로 기본은 내가 시작한 실행만 본다(세션 사용자 id 로 거른다).
		DstoneConsole.session()
			.then(function (info) {
				params.set("requestedBy", mineFilterEl.checked && info.userId ? info.userId : "");
				return DstoneAi.api("GET", urls.listUrl + "?" + params.toString());
			})
			.then(function (result) {
				var total = result.total || 0;
				var lastPage = Math.max(Math.ceil(total / PAGE_SIZE) - 1, 0);
				// 조건이 바뀌어 지금 페이지가 사라졌으면 마지막 페이지를 다시 읽는다.
				if (page > lastPage) {
					loadList(lastPage);
					return;
				}
				currentPage = page;
				renderList(result.items);
				renderPager(total, lastPage);
			})
			.catch(function (err) { alert("목록 조회 실패: " + err.message); });
	}

	function renderList(executions) {
		listBodyEl.innerHTML = "";
		(executions || []).forEach(function (row) {
			var tr = document.createElement("tr");
			tr.setAttribute("data-execution-id", row.executionId);
			if (row.executionId === activeExecutionId) {
				tr.className = "wa-selected";
			}
			tr.innerHTML =
				"<td><a href=\"javascript:void(0)\" class=\"workflow-history-jobid\">" + escapeHtml(row.executionId) + "</a></td>" +
				"<td>" + escapeHtml(row.workflowId) + "</td>" +
				"<td>" + escapeHtml(row.requestedBy || "-") + "</td>" +
				"<td>" + DstoneAi.badgeHtml(row.status) + "</td>" +
				currentStepCell(row) +
				"<td>" + escapeHtml(formatTime(row.updatedAt)) + "</td>";
			tr.querySelector(".workflow-history-jobid").addEventListener("click", function () {
				loadDetail(row.executionId);
			});
			listBodyEl.appendChild(tr);
		});
		if (!executions || executions.length === 0) {
			listBodyEl.innerHTML = "<tr><td colspan=\"6\">조건에 맞는 실행이 없습니다.</td></tr>";
		}
	}

	// 현재 스텝 칸. step id를 보여 주고, 길면 말줄임 + 풍선 도움말로 전체를 보여 준다.
	// id 기록이 생기기 전에 시작한 옛 실행이면 순번(1부터)만 보여 준다.
	function currentStepCell(row) {
		var text = row.currentStepId ? row.currentStepId : "#" + (row.currentStepIndex + 1);
		return "<td><span class=\"wa-cell-ellipsis\" title=\"" + escapeHtml(text) + "\">" + escapeHtml(text) + "</span></td>";
	}

	// [이전] 1 2 3 [다음]. 지금 페이지 앞뒤로 두 개씩만 보여 준다.
	function renderPager(total, lastPage) {
		totalEl.textContent = "총 " + total + "건";
		pagerEl.innerHTML = "";
		if (total === 0) {
			return;
		}
		addPageButton("이전", currentPage - 1, currentPage === 0);
		var from = Math.max(currentPage - 2, 0);
		var to = Math.min(currentPage + 2, lastPage);
		for (var page = from; page <= to; page++) {
			addPageButton(String(page + 1), page, page === currentPage);
		}
		addPageButton("다음", currentPage + 1, currentPage === lastPage);
	}

	function addPageButton(label, page, disabled) {
		var button = document.createElement("button");
		button.type = "button";
		button.textContent = label;
		button.disabled = disabled;
		if (page !== currentPage) {
			button.className = "kn-plain";
		}
		button.addEventListener("click", function () { loadList(page); });
		pagerEl.appendChild(button);
	}

	function loadDetail(executionId) {
		DstoneAi.api("GET", DstoneAi.executionUrl(executionId))
			.then(renderDetail)
			.catch(function (err) { alert("상세 조회 실패: " + err.message); });
	}

	function renderDetail(detail) {
		var newExecution = detail.executionId !== activeExecutionId;
		if (newExecution) {
			controlStatusEl.textContent = "";
			currentFiles = null;
			selectedIndex = -1;
			pickedByUser = false;
			historyPage = 0;
			historyPageByUser = false;
			paneKey = null;
			closeRewind();
		}
		if (isActive(detail.status)) {
			closeRewind();
		}
		activeExecutionId = detail.executionId;
		currentDetail = detail;
		detailPanelEl.style.display = "";
		markSelectedRow();
		schedulePoll(detail);

		renderHead(detail);
		renderTodo(detail);
		renderHistory(detail);
		renderResult(detail);
		renderDocs();
		loadFiles(detail.executionId);
		if (newExecution) {
			detailPanelEl.scrollIntoView({ behavior: "smooth", block: "start" });
		}
	}

	/* ---------- 머리: Workflow, 상태, 실행 정보, step 흐름 띠 ---------- */

	function renderHead(detail) {
		headWorkflowEl.textContent = detail.workflowId || "-";
		DstoneAi.setBadge(detailBadgeEl, detail.status);
		detailIdEl.textContent = detail.executionId;
		detailRequesterEl.textContent = detail.requestedBy || "-";
		headCreatedEl.textContent = formatTime(detail.createdAt) || "-";
		var start = Date.parse(detail.createdAt);
		// 아직 돌고 있으면 지금까지, 멈췄거나 끝났으면 마지막 변경 시각까지
		var end = isActive(detail.status) ? Date.now() : Date.parse(detail.updatedAt);
		headElapsedEl.textContent = isNaN(start) || isNaN(end) ? "-" : durationText(Math.max(end - start, 0));
		renderStrip(detail);
	}

	/*
	 * step 흐름 카드. 카드마다 step id, 이름, 종류(대상), 상태를 보여 준다.
	 * 완료 / 실패(빨간 테두리) / 지금(진한 테두리, 실행 중이면 깜박임) / 건너뜀(점선, 옅게) / 아직(옅게).
	 * 글자가 길면 말줄임으로 자르고, 카드에 마우스를 올리면 풍선 도움말로 전체를 보여 준다.
	 * 정의는 GET /api/ai/workflow/{id}/steps 로 한 번만 읽는다. 못 읽으면 진행 기록에 나온 step만으로 그린다.
	 */
	function renderStrip(detail) {
		var workflowId = detail.workflowId;
		var defs = stepDefs[workflowId];
		if (defs === undefined && workflowId) {
			stepDefs[workflowId] = "loading";
			DstoneAi.api("GET", "/api/ai/workflow/" + encodeURIComponent(workflowId) + "/steps")
				.then(function (result) {
					stepDefs[workflowId] = (result && !result.error && result.steps && result.steps.length > 0) ? result.steps : null;
				})
				.catch(function () {
					stepDefs[workflowId] = null;
				})
				.then(function () {
					if (currentDetail && currentDetail.workflowId === workflowId) {
						renderStrip(currentDetail);
						// 이름은 정의에만 있으므로, 정의가 늦게 오면 진행 기록과 고른 step 구역도 다시 그린다.
						paneKey = null;
						renderHistory(currentDetail);
					}
				});
		}
		var steps = (defs && defs !== "loading") ? defs : stepsFromHistory(detail.history || []);
		var runs = stepRuns(detail.history || []);
		var done = 0;
		var html = "";
		var currentText = "";
		var failedText = "";
		for (var i = 0; i < steps.length; i++) {
			var step = steps[i];
			var state = segmentState(detail, step, i, runs);
			if (state === "done") {
				done++;
			}
			if (state === "current") {
				currentText = stepLabel(step.id) + " (" + DstoneAi.stepTypeLabel(step.type) + ")";
			}
			if (state === "failed") {
				failedText = stepLabel(step.id);
			}
			html += stepCardHtml(step, i, state);
		}
		stripEl.innerHTML = html;
		// 실행 중이면 '지금' 카드만 천천히 깜박인다
		stripEl.className = "wa-cards" + (detail.status === "RUNNING" ? " wa-cards-running" : "");
		scrollToCurrentCard(detail);
		Array.prototype.forEach.call(stripEl.children, function (li) {
			li.addEventListener("click", function () {
				var row = runs.lastRow[li.getAttribute("data-step-id")];
				if (row != null) {
					selectRow(row, true);
				}
			});
		});

		var text = steps.length === 0 ? "아직 기록된 step이 없습니다." : "step " + steps.length + "개 중 " + done + "개 완료";
		// 실패한 실행이면 실패한 곳을, 아니면 지금 있는 곳을 말한다(앞 회차의 실패보다 지금이 중요하다)
		if (failedText && detail.status === "FAILED") {
			text += ". " + failedText + "에서 실패했습니다";
		} else if (currentText) {
			text += ". 지금 " + currentText;
		} else if (detail.status === "DONE" && done < steps.length) {
			text += ". 갈림길 때문에 돌지 않은 step이 있습니다";
		}
		if (stepDefs[workflowId] === null) {
			text += " (정의를 읽지 못해 기록된 step만 보입니다)";
		}
		stripTextEl.textContent = text;
	}

	var SEGMENT_LABELS = { done: "완료", failed: "실패", current: "지금 여기", skipped: "건너뜀", todo: "아직" };

	// 카드 한 장. 맨 위 줄은 순번과 상태, 그 아래 step id, 이름, 종류·대상 순서다.
	function stepCardHtml(step, index, state) {
		var typeText = DstoneAi.stepTypeLabel(step.type) + (step.target ? " · " + step.target : "") + (step.forEach ? " · 항목마다 반복" : "");
		var tip = step.id + (step.name ? "\n" + step.name : "") + "\n" + typeText + "\n상태: " + SEGMENT_LABELS[state];
		return "<li class=\"wa-card wa-card-" + escapeHtml(step.type) + " wa-seg-" + state + "\""
			+ " data-step-id=\"" + escapeHtml(step.id) + "\" title=\"" + escapeHtml(tip) + "\">"
			+ "<div class=\"wa-card-top\"><span class=\"wa-card-no\">" + (index + 1) + "</span>"
			+ "<span class=\"wa-card-state\">" + escapeHtml(SEGMENT_LABELS[state]) + "</span></div>"
			+ "<div class=\"wa-card-id\">" + escapeHtml(step.id) + "</div>"
			+ "<div class=\"wa-card-name\">" + escapeHtml(step.name || "-") + "</div>"
			+ "<div class=\"wa-card-type\">" + escapeHtml(typeText) + "</div>"
			+ "</li>";
	}

	// 카드가 많아 옆으로 넘칠 때 '지금'(없으면 실패) 카드가 보이게 가로로만 옮긴다.
	// 새로 고칠 때마다 옮기면 사용자가 둘러보던 자리를 빼앗으므로, 실행이나 지금 step이 바뀔 때만 옮긴다.
	var lastScrollKey = null;
	function scrollToCurrentCard(detail) {
		var key = detail.executionId + "|" + detail.currentStepIndex + "|" + detail.status;
		if (key === lastScrollKey) {
			return;
		}
		lastScrollKey = key;
		var card = stripEl.querySelector(".wa-seg-current") || stripEl.querySelector(".wa-seg-failed");
		if (!card) {
			stripEl.scrollLeft = 0;
			return;
		}
		stripEl.scrollLeft = Math.max(card.offsetLeft - stripEl.offsetLeft - 24, 0);
	}

	function segmentState(detail, step, index, runs) {
		var row = runs.lastRow[step.id];
		var history = detail.history || [];
		var here = index === detail.currentStepIndex && detail.status !== "DONE" && detail.status !== "CANCELLED";
		if (here && detail.status !== "FAILED") {
			return "current";
		}
		if (row != null) {
			return history[row].success ? "done" : "failed";
		}
		if (index < detail.currentStepIndex || detail.status === "DONE") {
			return "skipped";
		}
		return "todo";
	}

	// 정의를 못 읽었을 때: 진행 기록에 나온 step을 처음 나온 순서대로
	function stepsFromHistory(history) {
		var steps = [];
		var seen = {};
		history.forEach(function (h) {
			var id = baseStepId(h.stepId);
			if (id && !seen[id]) {
				seen[id] = true;
				steps.push({ id: id, type: h.stepType, target: h.ref, forEach: /\[\d+\]$/.test(h.stepId || "") });
			}
		});
		return steps;
	}

	/*
	 * step별 마지막 기록 줄과, 그 step이 마지막으로 돈 회차의 시작 시각.
	 * 기록 시각(executedAt)은 step이 끝난 시각이라 바로 앞 줄의 시각을 그 회차의 시작으로 본다(맨 첫 줄이면 제한 없음).
	 * forEach 반복 줄(step[0], step[1] ...)은 이어서 나오므로 한 회차로 묶는다.
	 */
	function stepRuns(history) {
		var lastRow = {};
		var since = {};
		var previousStepId = null;
		var previousTime = null;
		history.forEach(function (h, index) {
			var stepId = baseStepId(h.stepId);
			lastRow[stepId] = index;
			if (stepId !== previousStepId) {
				since[stepId] = previousTime;
			}
			previousStepId = stepId;
			var time = Date.parse(h.executedAt || "");
			previousTime = isNaN(time) ? previousTime : time;
		});
		return { lastRow: lastRow, since: since };
	}

	/* ---------- 지금 할 일 ---------- */

	var TODO_TITLES = {
		WAITING_APPROVAL: "승인을 기다립니다",
		RUNNING: "실행 중입니다",
		QUEUED: "실행 차례를 기다립니다",
		PAUSED: "일시정지했습니다",
		FAILED: "실패했습니다",
		CANCELLED: "취소된 실행입니다"
	};

	function renderTodo(detail) {
		renderControl(detail);
		var waitingApproval = detail.status === "WAITING_APPROVAL";
		decisionFormEl.style.display = waitingApproval ? "" : "none";
		if (waitingApproval) {
			// 새로 승인 대기에 들어왔을 때(다른 실행을 골랐거나, 결정 뒤 다음 승인 step에 닿았을 때)만 입력칸을 비운다.
			// 자동 갱신 중에는 같은 대기가 이어지므로 쓰던 의견을 지우지 않는다.
			if (decisionFormEl.getAttribute("data-execution") !== detail.executionId) {
				decisionStatusEl.textContent = "";
				commentEl.value = "";
				decisionFormEl.setAttribute("data-execution", detail.executionId);
			}
			renderDecisionButtons(detail.pendingApproval);
		} else {
			// 대기가 끝났으니 다음에 다시 승인 대기가 되면 새 대기로 본다.
			decisionFormEl.removeAttribute("data-execution");
		}
		var show = waitingApproval || controlEl.style.display !== "none" || detail.status === "FAILED";
		todoEl.style.display = show ? "" : "none";
		todoEl.className = "wa-todo wa-todo-" + String(detail.status || "").toLowerCase();
		todoTitleEl.textContent = TODO_TITLES[detail.status] || "할 일";
		var reason = detail.status === "FAILED" ? firstLine(detail.errorMessage) : "";
		todoReasonEl.textContent = reason;
		todoReasonEl.style.display = reason ? "" : "none";
	}

	function firstLine(text) {
		var value = text == null ? "" : String(text).trim();
		var cut = value.indexOf("\n");
		value = cut >= 0 ? value.substring(0, cut) : value;
		return value.length > 240 ? value.substring(0, 240) + "…" : value;
	}

	/* ---------- 진행 기록: 한 줄씩 ---------- */

	/*
	 * 진행 기록. 가장 최근 줄이 맨 위이고(시간 역순), 10줄씩 페이지로 나눈다.
	 * 줄의 data-index는 기록 배열(시간순)의 위치 그대로라서, 고르기·문서 개수·되감기는 순서와 상관없이 같은 값을 쓴다.
	 */
	function renderHistory(detail) {
		var history = detail.history || [];
		historyCountEl.textContent = history.length > 0 ? history.length + "줄" : "";

		// 고를 줄: 사람이 고른 줄이 있으면 그대로, 아니면 실패한 마지막 줄, 그것도 없으면 마지막 줄(실행 중이면 새 줄을 따라간다)
		if (!pickedByUser || selectedIndex >= history.length) {
			selectedIndex = defaultRow(detail);
			pickedByUser = false;
		}
		// 페이지: 사람이 페이지를 넘겼거나 줄을 직접 골랐으면 그대로, 아니면 자동으로 고른 줄(보통 최근 줄)이 있는 페이지
		if (!historyPageByUser && !pickedByUser && selectedIndex >= 0) {
			historyPage = pageOfRow(selectedIndex);
		}
		drawHistoryRows();
	}

	function historyPageCount() {
		var history = (currentDetail && currentDetail.history) || [];
		return Math.max(Math.ceil(history.length / HISTORY_PAGE_SIZE), 1);
	}

	/** 기록 배열의 index가 몇 번째 페이지에 나오는지(최근 줄이 0페이지) */
	function pageOfRow(index) {
		var history = (currentDetail && currentDetail.history) || [];
		return Math.floor((history.length - 1 - index) / HISTORY_PAGE_SIZE);
	}

	/** 지금 페이지의 줄을 최근 것부터 그리고, 페이지 단추와 문서 칸, 고른 step 구역을 맞춘다 */
	function drawHistoryRows() {
		var history = (currentDetail && currentDetail.history) || [];
		historyPage = Math.min(Math.max(historyPage, 0), historyPageCount() - 1);
		historyBodyEl.innerHTML = "";
		if (history.length === 0) {
			historyBodyEl.innerHTML = "<tr><td colspan=\"6\" class=\"wa-history-empty\">아직 끝난 step이 없습니다.</td></tr>";
		}
		var first = history.length - 1 - historyPage * HISTORY_PAGE_SIZE;
		var last = Math.max(first - HISTORY_PAGE_SIZE + 1, 0);
		for (var index = first; index >= last; index--) {
			historyBodyEl.appendChild(historyRow(history[index], index));
		}
		renderHistoryPager();
		renderHistoryDocs();
		highlightRow();
		renderPane();
	}

	function historyRow(h, index) {
		var tr = document.createElement("tr");
		tr.setAttribute("data-index", index);
		tr.setAttribute("tabindex", "0");
		var text = historyText(h);
		var name = stepNameOf(h);
		tr.innerHTML =
			"<td><span class=\"wa-type-dot wa-type-" + escapeHtml(h.stepType) + "\"></span><span class=\"wa-step-cell\">" + escapeHtml(h.stepId) + "</span>"
				+ (name ? "<span class=\"wa-step-cell-name\">" + escapeHtml(name) + "</span>" : "") + "</td>" +
			"<td><span class=\"wa-outcome wa-outcome-" + (h.success ? "ok" : "fail") + "\">" + (h.success ? "성공" : "실패") + "</span></td>" +
			"<td class=\"wa-num\">" + (h.durationMs == null ? "-" : escapeHtml(durationText(h.durationMs))) + "</td>" +
			"<td class=\"wa-line" + (h.success ? "" : " wa-text-failed") + "\">" + escapeHtml(text ? text.replace(/\s+/g, " ").substring(0, 300) : "-") + "</td>" +
			"<td class=\"wa-num wa-doc-count\"></td>" +
			"<td title=\"" + escapeHtml(formatTime(h.executedAt)) + "\">" + escapeHtml(clockText(h.executedAt)) + "</td>";
		tr.title = (index + 1) + "번째 줄, " + h.stepId + (name ? " " + name : "") + ", " + DstoneAi.stepTypeLabel(h.stepType) + (h.ref ? ", " + h.ref : "");
		return tr;
	}

	// [이전] 1 2 3 [다음]. 목록 표와 같은 모양. 한 페이지에 다 들어가면 단추를 숨긴다
	function renderHistoryPager() {
		var history = (currentDetail && currentDetail.history) || [];
		var pages = historyPageCount();
		historyPagerEl.innerHTML = "";
		historyPagerEl.style.display = history.length > HISTORY_PAGE_SIZE ? "" : "none";
		if (history.length <= HISTORY_PAGE_SIZE) {
			return;
		}
		addHistoryPageButton("최근", 0, historyPage === 0);
		addHistoryPageButton("이전", historyPage - 1, historyPage === 0);
		var from = Math.max(historyPage - 2, 0);
		var to = Math.min(historyPage + 2, pages - 1);
		for (var page = from; page <= to; page++) {
			addHistoryPageButton(String(page + 1), page, page === historyPage);
		}
		addHistoryPageButton("다음", historyPage + 1, historyPage === pages - 1);
	}

	function addHistoryPageButton(label, page, disabled) {
		var button = document.createElement("button");
		button.type = "button";
		button.textContent = label;
		button.disabled = disabled;
		if (page !== historyPage || label === "최근" || label === "이전" || label === "다음") {
			button.className = "kn-plain";
		}
		button.addEventListener("click", function () {
			historyPage = page;
			// 맨 앞(최근) 페이지로 돌아오면 다시 새 줄을 따라간다
			historyPageByUser = page !== 0;
			drawHistoryRows();
		});
		historyPagerEl.appendChild(button);
	}

	function defaultRow(detail) {
		var history = detail.history || [];
		if (detail.status === "FAILED") {
			for (var i = history.length - 1; i >= 0; i--) {
				if (!history[i].success) {
					return i;
				}
			}
		}
		return history.length - 1;
	}

	function historyText(h) {
		var value = h.success ? h.outputSummary : h.failureReason;
		return value == null ? "" : String(value);
	}

	function selectedHistory() {
		var history = (currentDetail && currentDetail.history) || [];
		return selectedIndex >= 0 ? history[selectedIndex] : null;
	}

	function selectRow(index, byUser) {
		var history = (currentDetail && currentDetail.history) || [];
		if (index < 0 || index >= history.length) {
			return;
		}
		if (byUser) {
			pickedByUser = true;
		}
		if (selectedIndex !== index) {
			var before = selectedHistory();
			if (!before || baseStepId(before.stepId) !== baseStepId(history[index].stepId)) {
				closeRewind();
			}
		}
		selectedIndex = index;
		if (pageOfRow(index) !== historyPage) {
			historyPage = pageOfRow(index);
			historyPageByUser = historyPage !== 0;
			drawHistoryRows();
			return;
		}
		highlightRow();
		renderPane();
	}

	function highlightRow() {
		Array.prototype.forEach.call(historyBodyEl.querySelectorAll("tr[data-index]"), function (tr) {
			var on = Number(tr.getAttribute("data-index")) === selectedIndex;
			tr.classList.toggle("wa-selected", on);
			tr.setAttribute("aria-selected", on ? "true" : "false");
		});
	}

	// 줄에 초점을 두고 ↑/↓로 옮기고, Enter나 Space로 고른다
	function onHistoryKey(event) {
		var tr = event.target.closest("tr[data-index]");
		if (!tr) {
			return;
		}
		var index = Number(tr.getAttribute("data-index"));
		var next = null;
		// 최근 줄이 위에 있으므로 ↓는 더 이전 기록(index - 1), ↑는 더 최근 기록(index + 1)이다
		if (event.key === "ArrowDown") {
			next = index - 1;
		} else if (event.key === "ArrowUp") {
			next = index + 1;
		} else if (event.key === "Enter" || event.key === " ") {
			event.preventDefault();
			selectRow(index, true);
			return;
		} else {
			return;
		}
		var history = (currentDetail && currentDetail.history) || [];
		if (next < 0 || next >= history.length) {
			return;
		}
		event.preventDefault();
		// 다른 페이지의 줄이면 selectRow가 페이지를 넘기고 다시 그린다
		selectRow(next, true);
		var target = historyBodyEl.querySelector("tr[data-index=\"" + next + "\"]");
		if (target) {
			target.focus();
		}
	}

	/*
	 * 진행 기록의 '문서' 칸(개수)과 고른 step 구역의 문서 단추를 채운다.
	 * 문서는 step의 마지막 회차에 건드린 것만 센다. 재실행 때마다 새 폴더에 문서를 만드는 Workflow는
	 * 같은 이름의 문서가 회차 수만큼 쌓이기 때문이다(지난 회차 문서는 아래 '산출 문서' 표에 그대로 있다).
	 */
	function renderHistoryDocs() {
		var history = (currentDetail && currentDetail.history) || [];
		var runs = stepRuns(history);
		Array.prototype.forEach.call(historyBodyEl.querySelectorAll("tr[data-index]"), function (tr) {
			var index = Number(tr.getAttribute("data-index"));
			var stepId = baseStepId(history[index].stepId);
			var cell = tr.querySelector(".wa-doc-count");
			cell.innerHTML = "";
			cell.removeAttribute("title");
			if (runs.lastRow[stepId] !== index) {
				return;
			}
			var files = filesOfStep(stepId, false, runs.since[stepId]);
			var earlier = docsWrittenOnlyBefore(stepId, runs.since[stepId]);
			if (earlier.length > 0) {
				cell.innerHTML = "<span class=\"wa-doc-alert\">!</span>";
				cell.title = "이번 회차에 작성한 문서가 없습니다";
			} else if (files.length > 0) {
				cell.textContent = String(files.length);
				cell.title = "문서 " + files.length + "개";
			}
		});
		paneKey = paneKey ? paneKey.replace(/\|docs:.*$/, "") : null;
		renderPane();
	}

	/* ---------- 고른 step 구역 ---------- */

	function renderPane() {
		var h = selectedHistory();
		paneEmptyEl.style.display = h ? "none" : "";
		paneBodyEl.style.display = h ? "" : "none";
		if (!h) {
			paneKey = null;
			return;
		}
		var history = currentDetail.history || [];
		var stepId = baseStepId(h.stepId);
		var runs = stepRuns(history);
		var key = currentDetail.executionId + "|" + selectedIndex + "|" + h.executedAt + "|" + h.success + "|" + currentDetail.status
			+ "|docs:" + (currentFiles ? "y" + (currentFiles.files || []).length : "n");
		if (key === paneKey) {
			return;
		}
		var sameRow = paneKey && paneKey.split("|").slice(0, 3).join("|") === key.split("|").slice(0, 3).join("|");
		paneKey = key;

		paneDotEl.className = "wa-type-dot wa-type-" + (h.stepType || "");
		var paneName = stepNameOf(h);
		paneIdEl.textContent = h.stepId;
		paneNameEl.textContent = paneName || "";
		paneNameEl.style.display = paneName ? "" : "none";
		paneBadgeEl.className = "workflow-badge workflow-badge-" + (h.success ? "done" : "failed");
		paneBadgeEl.textContent = h.success ? "성공" : "실패";
		paneMetaEl.innerHTML = "";
		addMeta(DstoneAi.stepTypeLabel(h.stepType) + (h.ref ? " " + h.ref : ""));
		if (h.durationMs != null) {
			addMeta("소요 " + durationText(h.durationMs));
		}
		addMeta(formatTime(h.executedAt));
		addMeta((selectedIndex + 1) + "번째 / 전체 " + history.length + "줄");

		var text = historyText(h);
		if (!sameRow) {
			paneTextEl.textContent = text ? prettyText(text) : "(남긴 내용이 없습니다)";
			paneTextEl.className = "wa-step-text" + (h.success ? "" : " wa-text-failed");
			paneTextEl.scrollTop = 0;
			paneCopyStatusEl.textContent = "";
		}

		// 문서: 이 step의 마지막 회차 기준
		paneDocsEl.innerHTML = "";
		var since = runs.since[stepId];
		var files = filesOfStep(stepId, false, since);
		var earlier = docsWrittenOnlyBefore(stepId, since);
		if (files.length > 0 || earlier.length > 0) {
			var label = document.createElement("div");
			label.className = "wa-step-docs-label";
			label.textContent = runs.lastRow[stepId] === selectedIndex ? "이 step의 문서" : "이 step의 문서 (마지막 회차 " + (runs.lastRow[stepId] + 1) + "번째 줄 기준)";
			paneDocsEl.appendChild(label);
			files.forEach(function (file) {
				paneDocsEl.appendChild(docChip(file, actionOf(file, stepId, since)));
			});
			if (earlier.length > 0) {
				paneDocsEl.appendChild(noNewDocWarning(earlier));
			}
		}

		paneRewindBtn.textContent = stepId + "부터 다시 실행";
		paneRewindBtn.disabled = isActive(currentDetail.status);
		paneRewindBtn.title = paneRewindBtn.disabled ? "실행 중에는 되돌릴 수 없습니다. 먼저 일시정지하세요." : stepLabel(stepId) + " step부터 다시 실행합니다";
	}

	function addMeta(text) {
		var span = document.createElement("span");
		span.textContent = text;
		paneMetaEl.appendChild(span);
	}

	// 클립보드 API는 https나 localhost에서만 된다. 안 되면 글자를 골라 복사 명령을 쓴다.
	function copyPaneText() {
		var text = paneTextEl.textContent;
		function done(ok) {
			paneCopyStatusEl.textContent = ok ? "복사했습니다." : "복사하지 못했습니다. 글자를 골라 Ctrl+C로 복사하세요.";
		}
		if (navigator.clipboard && window.isSecureContext) {
			navigator.clipboard.writeText(text).then(function () { done(true); }, function () { done(false); });
			return;
		}
		var range = document.createRange();
		range.selectNodeContents(paneTextEl);
		var selection = window.getSelection();
		selection.removeAllRanges();
		selection.addRange(range);
		var ok = false;
		try {
			ok = document.execCommand("copy");
		} catch (e) {
			ok = false;
		}
		done(ok);
	}

	/* ---------- 결과 ---------- */

	function renderResult(detail) {
		// output은 Workflow에 따라 글자일 수도, 객체일 수도 있다 - 객체면 보기 좋게 JSON으로 펼친다.
		var output = detail.output;
		if (output != null && typeof output !== "string") {
			output = JSON.stringify(output, null, 2);
		}
		var text = output || detail.errorMessage || "";
		var value = text ? prettyText(String(text)) : (isActive(detail.status) || detail.status === "WAITING_APPROVAL" || detail.status === "PAUSED" ? "아직 끝나지 않아 결과가 없습니다." : "남긴 결과가 없습니다.");
		if (detailResultEl.textContent !== value) {
			detailResultEl.textContent = value;
		}
		detailResultEl.className = "wa-result" + (text ? "" : " wa-result-empty") + (!output && detail.errorMessage ? " wa-text-failed" : "");
		var context = detail.context ? JSON.stringify(detail.context, null, 2) : "(없음)";
		if (detailContextEl.textContent !== context) {
			detailContextEl.textContent = context;
		}
	}

	// 1234 → 1.2초, 75000 → 1분 15초, 1초 안쪽은 ms
	function durationText(ms) {
		if (ms < 1000) {
			return ms + "ms";
		}
		var seconds = ms / 1000;
		if (seconds < 60) {
			return (Math.round(seconds * 10) / 10) + "초";
		}
		var minutes = Math.floor(seconds / 60);
		if (minutes < 60) {
			return minutes + "분 " + Math.round(seconds - minutes * 60) + "초";
		}
		return Math.floor(minutes / 60) + "시간 " + (minutes % 60) + "분";
	}

	// 진행 기록의 시각 칸은 시:분:초만. 날짜까지는 마우스를 올리면 보인다.
	function clockText(value) {
		var full = formatTime(value);
		return full.length > 11 ? full.substring(11) : full;
	}

	// step들이 읽고 쓴 파일 목록을 읽는다. 엔진이 예전 버전이라 이 API가 없으면 문서 칸만 비워 둔다.
	function loadFiles(executionId) {
		DstoneAi.api("GET", DstoneAi.executionUrl(executionId, "/files"))
			.then(function (result) {
				if (executionId !== activeExecutionId) {
					return;
				}
				currentFiles = result;
				renderHistoryDocs();
				renderDocs();
			})
			.catch(function (err) {
				if (executionId !== activeExecutionId) {
					return;
				}
				currentFiles = null;
				docsCountEl.textContent = "문서 목록을 읽지 못했습니다: " + err.message;
			});
	}

	// md처럼 화면에서 고칠 수 있는 종류의 파일인지(엔진 설정 editable-extensions 기준)
	function isDocument(file) {
		return !!file.editableType;
	}

	// 이 step이 건드린 파일들. allFiles가 false면 문서(md)만.
	// since(밀리초)를 주면 그 뒤에 건드린 파일만 고른다. 재실행 전에 다룬 파일은 빠진다.
	function filesOfStep(stepId, allFiles, since) {
		var result = [];
		if (!currentFiles) {
			return result;
		}
		(currentFiles.files || []).forEach(function (file) {
			if (!allFiles && !isDocument(file)) {
				return;
			}
			(file.steps || []).forEach(function (step) {
				if (step.stepId === stepId && touchedSince(step, since) && result.indexOf(file) < 0) {
					result.push(file);
				}
			});
		});
		return result;
	}

	function actionOf(file, stepId, since) {
		var action = "READ";
		(file.steps || []).forEach(function (step) {
			if ((stepId == null || step.stepId === stepId) && touchedSince(step, since) && step.action === "WRITE") {
				action = "WRITE";
			}
		});
		return action;
	}

	// 파일 기록의 마지막 접근 시각(lastAt)이 since 이후인지. since가 없으면 모두 통과.
	function touchedSince(step, since) {
		if (since == null) {
			return true;
		}
		var lastAt = Date.parse(step.lastAt);
		return isNaN(lastAt) || lastAt >= since;
	}

	// forEach step의 이력은 "apply[0]"처럼 순번이 붙는다. 파일 기록은 순번 없이 step id로 남는다.
	function baseStepId(stepId) {
		return String(stepId || "").replace(/\[\d+\]$/, "");
	}

	/*
	 * step의 사람용 이름(정의의 name). 정의는 지금 등록된 것이라, 실행한 뒤에 step 번호를 다시 매겼으면
	 * 같은 id가 다른 step일 수 있다. 그래서 기록 줄은 종류와 부른 대상(Agent/Tool)이 정의와 같을 때만 이름을 붙인다.
	 */
	function stepDefOf(stepId) {
		var defs = currentDetail ? stepDefs[currentDetail.workflowId] : null;
		if (!defs || defs === "loading") {
			return null;
		}
		var id = baseStepId(stepId);
		for (var i = 0; i < defs.length; i++) {
			if (defs[i].id === id) {
				return defs[i];
			}
		}
		return null;
	}

	function stepNameOf(h) {
		var def = stepDefOf(h.stepId);
		if (!def || !def.name) {
			return null;
		}
		if ((h.stepType && def.type && h.stepType !== def.type) || (h.ref && def.target && h.ref !== def.target)) {
			return null;
		}
		return def.name;
	}

	/** "step06(영향도 분석)"처럼 id 뒤에 이름을 붙인다. 이름이 없으면 id만 */
	function stepLabel(stepId) {
		var def = stepDefOf(stepId);
		return def && def.name ? stepId + "(" + def.name + ")" : String(stepId);
	}

	// 이 step이 앞 회차에서는 문서를 썼는데, 마지막 회차(since 이후)에는 한 문서도 쓰지 않았으면 앞 회차 문서들을 돌려준다.
	// Agent가 "이제 작성합니다"라고 말만 하고 끝나면 화면에는 지난 회차 문서가 그대로 남아 있어서,
	// 승인하는 사람이 새 문서로 착각하기 쉽다. 그것을 알리려는 것이다.
	function docsWrittenOnlyBefore(stepId, since) {
		if (!currentFiles || since == null) {
			return [];
		}
		var before = [];
		var wroteNow = false;
		(currentFiles.files || []).forEach(function (file) {
			if (!isDocument(file)) {
				return;
			}
			(file.steps || []).forEach(function (step) {
				if (step.stepId !== stepId || step.action !== "WRITE") {
					return;
				}
				if (touchedSince(step, since)) {
					wroteNow = true;
				} else if (before.indexOf(file) < 0) {
					before.push(file);
				}
			});
		});
		return wroteNow ? [] : before;
	}

	function noNewDocWarning(earlier) {
		var box = document.createElement("div");
		box.className = "wa-doc-warning";
		box.textContent = "⚠ 이번 회차에 작성한 문서가 없습니다. 지난 회차 문서 " + earlier.length + "개가 그대로 남아 있습니다.";
		box.title = "지난 회차에 작성한 문서(이번 회차에는 바뀌지 않음):\n" + earlier.map(function (file) {
			return file.path;
		}).join("\n");
		return box;
	}

	function docChip(file, action) {
		var chip = document.createElement("button");
		chip.type = "button";
		chip.className = "wa-doc-chip" + (action === "WRITE" ? " wa-doc-chip-write" : "") + (file.exists ? "" : " wa-doc-chip-missing");
		chip.textContent = file.name;
		chip.title = file.path + (action === "WRITE" ? " (작성)" : " (읽음)") + (file.exists ? "" : " - 지금은 파일이 없습니다");
		chip.disabled = !file.exists;
		chip.addEventListener("click", function () { openDoc(file); });
		return chip;
	}

	function openDoc(file) {
		DstoneAiDocViewer.open(activeExecutionId, file.path, file, currentDetail ? currentDetail.status : null);
	}

	// 'step 문서' 표. 기본은 md만, [모든 파일 보기]를 켜면 step이 읽은 소스 파일까지 보여 준다.
	function renderDocs() {
		docsBodyEl.innerHTML = "";
		if (!currentFiles) {
			docsCountEl.textContent = "";
			docsBodyEl.innerHTML = "<tr><td colspan=\"5\">불러오는 중입니다...</td></tr>";
			return;
		}
		var all = docsAllEl.checked;
		var picked = (currentFiles.files || []).filter(function (file) { return all || isDocument(file); });
		var files = latestOnly(picked);
		var total = (currentFiles.files || []).length;
		var hidden = picked.length - files.length;
		docsCountEl.textContent = files.length + "개" + (all ? "" : " (전체 파일 " + total + "개)")
			+ (hidden > 0 ? " · 지난 회차 문서 " + hidden + "개 숨김" : "")
			+ (currentFiles.readOnlyReason ? " · " + currentFiles.readOnlyReason : "");
		if (files.length === 0) {
			docsBodyEl.innerHTML = "<tr><td colspan=\"5\">" + (total === 0
				? "기록된 파일이 없습니다. 이 기능이 생기기 전에 돈 실행이거나, 파일 Tool을 쓰지 않은 Workflow입니다."
				: "md 문서가 없습니다. [모든 파일 보기]를 켜면 step이 읽은 파일도 보입니다.") + "</td></tr>";
			return;
		}
		files.forEach(function (file) {
			var tr = document.createElement("tr");
			var steps = [];
			(file.steps || []).forEach(function (step) {
				steps.push(step.stepId + (step.action === "WRITE" ? "(작성)" : "(읽음)"));
			});
			tr.innerHTML =
				"<td class=\"wa-doc-name-cell\"></td>" +
				"<td>" + escapeHtml(steps.join(", ")) + "</td>" +
				"<td class=\"wa-num\">" + (file.exists ? escapeHtml(DstoneAiDocViewer.sizeText(file.size)) : "-") + "</td>" +
				"<td>" + (file.exists ? escapeHtml(formatTime(file.lastModified)) : "<span class=\"wa-text-failed\">파일 없음</span>") + "</td>" +
				"<td class=\"wa-doc-open-cell\"></td>";
			var nameCell = tr.querySelector(".wa-doc-name-cell");
			var name = document.createElement("div");
			name.className = "wa-doc-name";
			name.textContent = file.name;
			var filePath = document.createElement("div");
			filePath.className = "wa-doc-path-text";
			filePath.textContent = file.path;
			nameCell.appendChild(name);
			nameCell.appendChild(filePath);
			var openBtn = document.createElement("button");
			openBtn.type = "button";
			openBtn.className = "kn-plain";
			openBtn.textContent = isDocument(file) && !currentFiles.readOnlyReason ? "열기/수정" : "열기";
			openBtn.disabled = !file.exists;
			openBtn.addEventListener("click", function () { openDoc(file); });
			tr.querySelector(".wa-doc-open-cell").appendChild(openBtn);
			docsBodyEl.appendChild(tr);
		});
	}

	/*
	 * 같은 문서(md)가 여러 번 나오면 가장 최근 것 하나만 남긴다.
	 * 재실행 때마다 새 폴더에 문서를 만드는 Workflow는 '01.요구사항.md'가 회차 수만큼 쌓이기 때문이다.
	 * 같은 문서 = 파일 이름이 같은 md 문서(폴더는 달라도 된다).
	 * md가 아닌 파일(소스 등)은 이름이 같아도 다른 파일인 경우가 많아 지우지 않고 모두 보여 준다.
	 * 가장 최근 = step이 마지막으로 건드린 시각(lastAt)이 가장 늦은 것. 자리는 처음 나온 자리를 지킨다.
	 */
	function latestOnly(files) {
		var result = [];
		var indexOfKey = {};
		files.forEach(function (file) {
			if (!isDocument(file)) {
				result.push(file);
				return;
			}
			var key = file.name;
			if (!Object.prototype.hasOwnProperty.call(indexOfKey, key)) {
				indexOfKey[key] = result.length;
				result.push(file);
			} else if (lastTouched(file) > lastTouched(result[indexOfKey[key]])) {
				result[indexOfKey[key]] = file;
			}
		});
		return result;
	}

	function lastTouched(file) {
		var latest = 0;
		(file.steps || []).forEach(function (step) {
			var time = Date.parse(step.lastAt);
			if (!isNaN(time) && time > latest) {
				latest = time;
			}
		});
		return latest;
	}

	function markSelectedRow() {
		Array.prototype.forEach.call(listBodyEl.children, function (tr) {
			tr.className = tr.getAttribute("data-execution-id") === activeExecutionId ? "wa-selected" : "";
		});
	}

	// JSON이면 보기 좋게 펼치고, 아니면 그대로 둔다.
	function prettyText(value) {
		var first = value.charAt(0);
		if (first !== "{" && first !== "[") {
			return value;
		}
		try {
			return JSON.stringify(JSON.parse(value), null, 2);
		} catch (e) {
			return value;
		}
	}

	// 실행 상태에 따라 일시정지/재개 버튼과 안내를 보여 준다.
	function renderControl(detail) {
		var point = detail.resumePoint;
		var running = isActive(detail.status);
		controlEl.style.display = running || point ? "" : "none";
		pauseBtn.style.display = running ? "" : "none";
		pauseBtn.disabled = !!detail.pauseRequested;
		resumeBtn.style.display = point ? "" : "none";
		resumeBtn.disabled = !(point && point.resumable);
		resumeBtn.textContent = detail.status === "FAILED" ? "실패 지점부터 다시 실행" : "재개";
		controlWarningsEl.innerHTML = "";

		if (detail.status === "QUEUED") {
			controlMessageEl.textContent = "실행 차례를 기다리는 중입니다(엔진의 동시 실행 수가 다 찼습니다). 일시정지하면 차례가 와도 돌지 않습니다.";
			return;
		}
		if (running) {
			controlMessageEl.textContent = detail.pauseRequested
				? "일시정지를 요청했습니다. 지금 돌고 있는 step이 끝나면 멈춥니다."
				: "실행 중입니다. 일시정지하면 지금 돌고 있는 step이 끝난 뒤에 멈춥니다.";
			return;
		}
		if (!point) {
			return;
		}
		if (!point.resumable) {
			controlMessageEl.textContent = point.problem;
			return;
		}
		controlMessageEl.textContent = (detail.status === "FAILED" ? "다시 실행할 step: " : "재개할 step: ") + stepLabel(point.stepId) + " (" + point.stepType + ")";
		(point.warnings || []).forEach(function (warning) {
			var li = document.createElement("li");
			li.textContent = warning;
			controlWarningsEl.appendChild(li);
		});
	}

	// 고른 step으로 되돌리면 무엇이 남고 무엇을 지울 수 있는지 엔진에 물어 패널에 보여 준다.
	function openRewind(stepId) {
		var executionId = activeExecutionId;
		rewindEl.style.display = "";
		rewindMessageEl.textContent = stepLabel(stepId) + " step을 확인하는 중입니다...";
		rewindWarningsEl.innerHTML = "";
		rewindFilesEl.style.display = "none";
		rewindKeptEl.style.display = "none";
		rewindClearLabelEl.style.display = "none";
		rewindStatusEl.textContent = "";
		rewindRunBtn.disabled = true;
		currentRewind = null;
		DstoneAi.api("GET", DstoneAi.executionUrl(executionId, "/rewind?fromStep=" + encodeURIComponent(stepId)))
			.then(function (point) {
				if (executionId !== activeExecutionId) {
					return;
				}
				renderRewind(point);
			})
			.catch(function (err) {
				rewindMessageEl.textContent = "확인하지 못했습니다: " + err.message;
			});
		rewindEl.scrollIntoView({ block: "nearest" });
	}

	function renderRewind(point) {
		currentRewind = point;
		rewindWarningsEl.innerHTML = "";
		if (!point.rewindable) {
			rewindMessageEl.textContent = point.problem;
			rewindRunBtn.disabled = true;
			return;
		}
		rewindMessageEl.textContent = point.fromStep + " (" + point.stepType + ") step부터 다시 실행합니다. 되돌리는 구간: " + (point.steps || []).join(" → ");
		(point.warnings || []).forEach(function (warning) {
			var li = document.createElement("li");
			li.textContent = warning;
			rewindWarningsEl.appendChild(li);
		});

		fillFileList(rewindFilesEl, point.deleteFiles, "다시 실행하면 지우는 파일(이 구간에서 새로 만든 산출물) ");
		fillFileList(rewindKeptEl, point.keptFiles, "지우지 않는 파일(원래 있던 파일을 고쳤거나 새로 만들었는지 알 수 없는 것) ");

		var names = point.savedNames || [];
		rewindClearLabelEl.style.display = names.length > 0 ? "" : "none";
		rewindClearEl.checked = false;
		rewindClearLabelEl.querySelector("span").textContent = "남아 있는 state 값을 지우고 시작 (" + names.join(", ") + ")";
		rewindRunBtn.disabled = false;
	}

	// 파일 목록 접기 칸을 채운다. 목록이 비면 칸을 숨긴다.
	function fillFileList(detailsEl, paths, title) {
		var files = paths || [];
		detailsEl.style.display = files.length > 0 ? "" : "none";
		detailsEl.open = false;
		detailsEl.querySelector("summary").textContent = title + files.length + "개 보기";
		var fileList = detailsEl.querySelector("ul");
		fileList.innerHTML = "";
		files.forEach(function (path) {
			var li = document.createElement("li");
			li.textContent = path;
			fileList.appendChild(li);
		});
	}

	function runRewind() {
		var point = currentRewind;
		if (!point || !point.rewindable) {
			return;
		}
		var clear = rewindClearEl.checked && (point.savedNames || []).length > 0;
		var message = point.fromStep + " step부터 다시 실행할까요?\n\n"
			+ (clear ? "지울 state 값: " + point.savedNames.join(", ") : "state 값은 지우지 않습니다.");
		if ((point.deleteFiles || []).length > 0) {
			message += "\n이 구간에서 새로 만든 파일 " + point.deleteFiles.length + "개를 실제로 지웁니다(되살릴 수 없습니다).";
		}
		if ((point.keptFiles || []).length > 0) {
			message += "\n원래 있던 파일을 고친 " + point.keptFiles.length + "개는 지우지 않고, 원래대로 돌아가지도 않습니다.";
		}
		if (!window.confirm(message)) {
			return;
		}
		rewindStatusEl.textContent = "요청 중입니다...";
		rewindRunBtn.disabled = true;
		DstoneAi.api("POST", DstoneAi.executionUrl(activeExecutionId, "/rewind"), { fromStep: point.fromStep, clearState: clear })
			.then(function (detail) {
				closeRewind();
				renderDetail(detail);
				loadList(currentPage);
			})
			.catch(function (err) {
				rewindStatusEl.textContent = "처리 실패: " + err.message;
				rewindRunBtn.disabled = false;
			});
	}

	function closeRewind() {
		currentRewind = null;
		if (rewindEl) {
			rewindEl.style.display = "none";
			rewindStatusEl.textContent = "";
		}
	}

	// POST로 부르고, 실패 응답이면 본문의 message로 오류를 낸다.
	function pause() {
		control("/pause", "일시정지 요청 중입니다...");
	}

	function resume() {
		var point = currentDetail && currentDetail.resumePoint;
		if (!point) {
			return;
		}
		var message = stepLabel(point.stepId) + " step부터 이어 갈까요?";
		if (point.warnings && point.warnings.length > 0) {
			message += "\n\n" + point.warnings.join("\n");
		}
		if (window.confirm(message)) {
			control("/resume", "요청 중입니다...");
		}
	}

	function control(action, progress) {
		controlStatusEl.textContent = progress;
		DstoneAi.api("POST", DstoneAi.executionUrl(activeExecutionId, action))
			.then(function (detail) {
				controlStatusEl.textContent = "";
				renderDetail(detail);
				loadList(currentPage);
			})
			.catch(function (err) {
				controlStatusEl.textContent = "처리 실패: " + err.message;
				loadDetail(activeExecutionId);
			});
	}

	// 실행 중인 동안에는 상세를 주기적으로 다시 읽는다. 끝나거나 멈추면 목록도 새로 읽고 그만둔다.
	function schedulePoll(detail) {
		if (pollTimer) {
			clearTimeout(pollTimer);
			pollTimer = null;
		}
		if (!isActive(detail.status)) {
			return;
		}
		pollTimer = setTimeout(function () {
			pollTimer = null;
			var executionId = detail.executionId;
			DstoneAi.api("GET", DstoneAi.executionUrl(executionId))
				.then(function (latest) {
					if (executionId !== activeExecutionId) {
						return;
					}
					if (latest.updatedAt === currentDetail.updatedAt && latest.status === currentDetail.status && latest.pauseRequested === currentDetail.pauseRequested) {
						// 바뀐 것이 없으면 다시 그리지 않는다(읽던 자리와 스크롤을 지킨다).
						schedulePoll(latest);
						return;
					}
					renderDetail(latest);
					if (!isActive(latest.status) || latest.status !== currentListStatus(executionId)) {
						loadList(currentPage);
					}
				})
				.catch(function () { schedulePoll(detail); });
		}, POLL_MILLIS);
	}

	// 아직 끝나지 않고 엔진이 돌리고 있거나(RUNNING) 차례를 기다리는(QUEUED) 상태인지.
	function isActive(status) {
		return status === "RUNNING" || status === "QUEUED";
	}

	// 목록에 지금 보이는 그 실행의 상태(QUEUED → RUNNING처럼 바뀌면 목록도 새로 읽으려고 본다).
	function currentListStatus(executionId) {
		var tr = listBodyEl.querySelector("tr[data-execution-id=\"" + executionId + "\"] .workflow-badge");
		// 글자는 한글 이름이라 원래 상태 코드(data-status)를 본다
		return tr ? tr.getAttribute("data-status") : null;
	}

	// 2026-11-02T04:55:03.123Z → 이 PC 시각의 2026-11-02 13:55:03
	function formatTime(value) {
		var date = value ? new Date(value) : null;
		if (!date || isNaN(date.getTime())) {
			return value || "";
		}
		function two(number) {
			return (number < 10 ? "0" : "") + number;
		}
		return date.getFullYear() + "-" + two(date.getMonth() + 1) + "-" + two(date.getDate()) + " " + two(date.getHours()) + ":" + two(date.getMinutes()) + ":" + two(date.getSeconds());
	}

	// 기다리는 APPROVAL step이 어떤 방식인지에 따라 결정 단추를 바꿔 보여 준다.
	// - routes가 비어 있으면: 아래 오른쪽의 승인/반려 단추
	// - routes에 이름이 있으면: 이름마다 카드 하나씩(누르면 그 이름을 route로 보낸다). 카드에는 의견을 받는 곳도 적는다
	function renderDecisionButtons(pendingApproval) {
		var routes = (pendingApproval && pendingApproval.routes) || [];
		var hasRoutes = routes.length > 0;

		decideButtonsEl.style.display = hasRoutes ? "none" : "";
		decideChoicesBoxEl.style.display = hasRoutes ? "" : "none";
		approveBtn.disabled = deciding;
		rejectBtn.disabled = deciding;

		routeButtonsEl.innerHTML = "";
		routes.forEach(function (route) {
			var receiver = pendingApproval.receivers && pendingApproval.receivers[route];
			var button = document.createElement("button");
			button.type = "button";
			button.className = "wa-choice";
			button.disabled = deciding;
			button.title = receiver ? route + " · 의견은 " + receiver + "이(가) 받습니다" : route;

			var name = document.createElement("span");
			name.className = "wa-choice-name";
			name.textContent = route;
			button.appendChild(name);

			var to = document.createElement("span");
			to.className = "wa-choice-to";
			to.textContent = receiver ? "의견 받는 곳 · " + receiver : "받는 곳 지정 없음";
			button.appendChild(to);

			button.addEventListener("click", function () { decide(false, route); });
			routeButtonsEl.appendChild(button);
		});

		var role = pendingApproval && pendingApproval.approverRole;
		decideStepEl.textContent = pendingApproval ? stepLabel(pendingApproval.stepId) : "-";
		decideRoleEl.textContent = role || "-";
		decideRoleItemEl.style.display = role ? "" : "none";
		decisionStepEl.textContent = hasRoutes
			? "진행 기록에서 앞 step의 결과와 문서를 확인한 뒤, 아래 결정 중 하나를 고르세요."
			: "진행 기록에서 앞 step의 결과와 문서를 확인한 뒤 승인하거나 반려하세요.";
	}

	// 결정을 보내는 동안 모든 결정 단추를 막고, 끝나면 푼다
	function setDeciding(value) {
		deciding = value;
		approveBtn.disabled = value;
		rejectBtn.disabled = value;
		var buttons = routeButtonsEl.querySelectorAll("button");
		for (var i = 0; i < buttons.length; i++) {
			buttons[i].disabled = value;
		}
	}

	// approved: 승인/반려 방식일 때의 값. route: 선택지 방식일 때 고른 이름(승인/반려 방식이면 null).
	function decide(approved, route) {
		if (!activeExecutionId) {
			return;
		}
		if (!approverEl.value.trim()) {
			alert("approver를 입력하세요.");
			return;
		}
		if (deciding) {
			return;
		}
		setDeciding(true);
		decisionStatusEl.textContent = "처리 중입니다...";
		// 결정한 사람은 엔진이 세션 사용자로 남긴다(approver 값보다 우선).
		// 409: 다른 사람이 먼저 결정함, 503: 실행 대기열이 꽉 참 등. 엔진이 준 사유를 보여 준다.
		DstoneAi.api("POST", DstoneAi.executionUrl(activeExecutionId, "/decision"), {
			approved: approved,
			route: route,
			approver: approverEl.value.trim(),
			comment: commentEl.value.trim() || null
		})
			.then(function (detail) {
				// 엔진은 결정을 받으면 이어서 돌릴 실행을 대기열에 넣고 바로 돌려준다(QUEUED). 진행은 자동 갱신으로 본다.
				// 보낸 의견은 비운다. 남겨 두면 다음 step의 결과처럼 보일 수 있다.
				commentEl.value = "";
				setDeciding(false);
				decisionStatusEl.textContent = "결정을 전달했습니다. 이어서 실행합니다 (" + detail.status + ")";
				renderDetail(detail);
				loadList(currentPage);
			})
			.catch(function (err) {
				setDeciding(false);
				decisionStatusEl.textContent = "처리 실패: " + err.message;
				loadDetail(activeExecutionId);
			});
	}

	// 속성(title="...")에도 넣으므로 따옴표까지 바꾸는 공통 함수를 쓴다
	function escapeHtml(value) {
		return DstoneAi.escapeHtml(value);
	}

	return { init: init };
})();

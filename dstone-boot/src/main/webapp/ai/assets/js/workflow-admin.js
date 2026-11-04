var DstoneAiWorkflowAdmin = (function () {

	var urls = {};
	var statusFilterEl, workflowFilterEl, executionFilterEl, refreshBtn, resetBtn, listBodyEl, totalEl, pagerEl;
	var detailPanelEl, detailIdEl, detailBadgeEl, detailResultEl, detailContextEl, historyBodyEl;
	var decisionFormEl, approverEl, commentEl, approveBtn, rejectBtn, decisionStatusEl, decisionStepEl, routeButtonsEl;

	var controlEl, controlMessageEl, controlWarningsEl, controlStatusEl, pauseBtn, resumeBtn;
	var docsBodyEl, docsAllEl, docsCountEl;

	var PAGE_SIZE = 10;
	var POLL_MILLIS = 3000;
	// 두 줄(약 100자)을 넘는 내용에만 [펼치기]를 붙인다.
	var PREVIEW_CHARS = 100;

	var activeExecutionId = null;
	var currentPage = 0;
	var currentDetail = null;
	var pollTimer = null;
	// 펼쳐 둔 이력 줄의 순번. 자동 갱신으로 다시 그려도 펼친 채로 둔다.
	var openHistory = {};
	// 지금 보고 있는 실행의 step 문서 목록(엔진 GET .../files 응답). 다른 실행을 고르면 비운다.
	var currentFiles = null;

	function init(options) {
		urls = options;

		statusFilterEl = document.getElementById("workflow-admin-status-filter");
		workflowFilterEl = document.getElementById("workflow-admin-workflow-filter");
		executionFilterEl = document.getElementById("workflow-admin-execution-filter");
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
		detailResultEl = document.getElementById("workflow-admin-detail-result");
		detailContextEl = document.getElementById("workflow-admin-detail-context");
		historyBodyEl = document.getElementById("workflow-admin-history-body");

		decisionFormEl = document.getElementById("workflow-admin-decision-form");
		approverEl = document.getElementById("workflow-admin-approver");
		commentEl = document.getElementById("workflow-admin-comment");
		approveBtn = document.getElementById("workflow-admin-approve-btn");
		rejectBtn = document.getElementById("workflow-admin-reject-btn");
		decisionStatusEl = document.getElementById("workflow-admin-decision-status");
		decisionStepEl = document.getElementById("workflow-admin-decision-step");
		routeButtonsEl = document.getElementById("workflow-admin-route-buttons");
		docsBodyEl = document.getElementById("workflow-admin-docs-body");
		docsAllEl = document.getElementById("workflow-admin-docs-all");
		docsCountEl = document.getElementById("workflow-admin-docs-count");
		docsAllEl.addEventListener("change", renderDocs);

		// step 문서 팝업. 저장하면 목록(크기, 수정 시각)을 다시 읽는다.
		DstoneAiDocViewer.init({
			contentUrl: urls.fileContentUrl,
			saveUrl: urls.saveFileUrl,
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
			loadList(0);
		});
		statusFilterEl.addEventListener("change", function () { loadList(0); });
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
		fetch(urls.listUrl + "?" + params.toString(), { method: "POST" })
			.then(function (response) { return response.json(); })
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
				"<td><span class=\"workflow-badge workflow-badge-" + escapeHtml((row.status || "unknown").toLowerCase()) + "\">" + escapeHtml(row.status) + "</span></td>" +
				"<td class=\"wa-num\">" + row.currentStepIndex + "</td>" +
				"<td>" + escapeHtml(formatTime(row.updatedAt)) + "</td>";
			tr.querySelector(".workflow-history-jobid").addEventListener("click", function () {
				loadDetail(row.executionId);
			});
			listBodyEl.appendChild(tr);
		});
		if (!executions || executions.length === 0) {
			listBodyEl.innerHTML = "<tr><td colspan=\"5\">조건에 맞는 실행이 없습니다.</td></tr>";
		}
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
		fetch(urls.detailUrl + "?executionId=" + encodeURIComponent(executionId), { method: "POST" })
			.then(function (response) { return response.json(); })
			.then(renderDetail)
			.catch(function (err) { alert("상세 조회 실패: " + err.message); });
	}

	function renderDetail(detail) {
		if (detail.executionId !== activeExecutionId) {
			openHistory = {};
			controlStatusEl.textContent = "";
			currentFiles = null;
		}
		activeExecutionId = detail.executionId;
		currentDetail = detail;
		detailPanelEl.style.display = "";
		markSelectedRow();
		renderControl(detail);
		schedulePoll(detail);

		detailIdEl.textContent = detail.executionId;
		detailBadgeEl.textContent = detail.status;
		detailBadgeEl.className = "workflow-badge workflow-badge-" + (detail.status || "unknown").toLowerCase();
		// output은 Workflow에 따라 글자일 수도, 객체일 수도 있다 - 객체면 보기 좋게 JSON으로 펼친다.
		var output = detail.output;
		if (output != null && typeof output !== "string") {
			output = JSON.stringify(output, null, 2);
		}
		detailResultEl.value = output || detail.errorMessage || "";
		detailContextEl.value = detail.context ? JSON.stringify(detail.context, null, 2) : "";

		historyBodyEl.innerHTML = "";
		(detail.history || []).forEach(function (h, index) {
			var tr = document.createElement("tr");
			tr.innerHTML =
				"<td>" + escapeHtml(h.stepId) + "</td>" +
				"<td>" + escapeHtml(h.stepType) + "</td>" +
				"<td>" + escapeHtml(h.ref) + "</td>" +
				"<td><span class=\"workflow-badge workflow-badge-" + (h.success ? "done" : "failed") + "\">" + (h.success ? "성공" : "실패") + "</span></td>" +
				"<td class=\"wa-num\">" + (h.durationMs == null ? "-" : h.durationMs) + "</td>" +
				"<td class=\"wa-text-cell\"></td>" +
				"<td class=\"wa-doc-cell\"></td>" +
				"<td>" + escapeHtml(formatTime(h.executedAt)) + "</td>";
			fillHistoryText(tr.querySelector(".wa-text-cell"), h.success ? h.outputSummary : h.failureReason, !h.success, index);
			tr.setAttribute("data-step-id", h.stepId || "");
			historyBodyEl.appendChild(tr);
		});
		renderHistoryDocs();
		renderDocs();
		loadFiles(detail.executionId);

		var waitingApproval = detail.status === "WAITING_APPROVAL";
		decisionFormEl.style.display = waitingApproval ? "" : "none";
		if (waitingApproval) {
			decisionStatusEl.textContent = "";
			renderDecisionButtons(detail.pendingApproval);
		}
	}

	// step들이 읽고 쓴 파일 목록을 읽는다. 엔진이 예전 버전이라 이 API가 없으면 문서 칸만 비워 둔다.
	function loadFiles(executionId) {
		fetch(urls.filesUrl + "?executionId=" + encodeURIComponent(executionId), { method: "POST" })
			.then(function (response) {
				return response.json().then(function (body) {
					if (!response.ok) {
						throw new Error(body.message || ("서버 오류(" + response.status + ")"));
					}
					return body;
				});
			})
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
	function filesOfStep(stepId, allFiles) {
		var result = [];
		if (!currentFiles) {
			return result;
		}
		(currentFiles.files || []).forEach(function (file) {
			if (!allFiles && !isDocument(file)) {
				return;
			}
			(file.steps || []).forEach(function (step) {
				if (step.stepId === stepId && result.indexOf(file) < 0) {
					result.push(file);
				}
			});
		});
		return result;
	}

	function actionOf(file, stepId) {
		var action = "READ";
		(file.steps || []).forEach(function (step) {
			if ((stepId == null || step.stepId === stepId) && step.action === "WRITE") {
				action = "WRITE";
			}
		});
		return action;
	}

	// forEach step의 이력은 "apply[0]"처럼 순번이 붙는다. 파일 기록은 순번 없이 step id로 남는다.
	function baseStepId(stepId) {
		return String(stepId || "").replace(/\[\d+\]$/, "");
	}

	// 스텝 이력의 '문서' 칸. 같은 step이 여러 번 돌았으면(forEach 반복 포함) 마지막 줄에만 단다(기록은 step별로 합쳐져 있다).
	function renderHistoryDocs() {
		var rows = historyBodyEl.querySelectorAll("tr[data-step-id]");
		var lastRowOfStep = {};
		Array.prototype.forEach.call(rows, function (tr) {
			lastRowOfStep[baseStepId(tr.getAttribute("data-step-id"))] = tr;
		});
		Array.prototype.forEach.call(rows, function (tr) {
			var cell = tr.querySelector(".wa-doc-cell");
			cell.innerHTML = "";
			var stepId = baseStepId(tr.getAttribute("data-step-id"));
			if (lastRowOfStep[stepId] !== tr) {
				return;
			}
			filesOfStep(stepId, false).forEach(function (file) {
				cell.appendChild(docChip(file, actionOf(file, stepId)));
			});
		});
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
		var files = (currentFiles.files || []).filter(function (file) { return all || isDocument(file); });
		var total = (currentFiles.files || []).length;
		docsCountEl.textContent = files.length + "개" + (all ? "" : " (전체 파일 " + total + "개)")
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

	function markSelectedRow() {
		Array.prototype.forEach.call(listBodyEl.children, function (tr) {
			tr.className = tr.getAttribute("data-execution-id") === activeExecutionId ? "wa-selected" : "";
		});
	}

	// 내용/사유 칸: 평소에는 두 줄, [펼치기]를 누르면 칸 안에서 줄을 바꿔 전부 보여 준다.
	function fillHistoryText(cell, text, failed, index) {
		var value = text == null ? "" : String(text);
		var preview = document.createElement("div");
		preview.className = "wa-text-preview" + (failed ? " wa-text-failed" : "");
		preview.textContent = value;
		cell.appendChild(preview);
		if (value.length <= PREVIEW_CHARS && value.indexOf("\n") < 0) {
			return;
		}
		var full = document.createElement("pre");
		full.className = "wa-text-full" + (failed ? " wa-text-failed" : "");
		var toggle = document.createElement("button");
		toggle.type = "button";
		toggle.className = "wa-text-toggle";
		function show(open) {
			if (open && !full.textContent) {
				full.textContent = prettyText(value);
			}
			preview.style.display = open ? "none" : "";
			full.style.display = open ? "" : "none";
			toggle.textContent = open ? "접기" : "펼치기";
		}
		toggle.addEventListener("click", function () {
			openHistory[index] = !openHistory[index];
			show(openHistory[index]);
		});
		show(!!openHistory[index]);
		cell.appendChild(full);
		cell.appendChild(toggle);
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
		var running = detail.status === "RUNNING";
		controlEl.style.display = running || point ? "" : "none";
		pauseBtn.style.display = running ? "" : "none";
		pauseBtn.disabled = !!detail.pauseRequested;
		resumeBtn.style.display = point ? "" : "none";
		resumeBtn.disabled = !(point && point.resumable);
		resumeBtn.textContent = detail.status === "FAILED" ? "실패 지점부터 다시 실행" : "재개";
		controlWarningsEl.innerHTML = "";

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
		controlMessageEl.textContent = (detail.status === "FAILED" ? "다시 실행할 step: " : "재개할 step: ") + point.stepId + " (" + point.stepType + ")";
		(point.warnings || []).forEach(function (warning) {
			var li = document.createElement("li");
			li.textContent = warning;
			controlWarningsEl.appendChild(li);
		});
	}

	function pause() {
		control(urls.pauseUrl, "일시정지 요청 중입니다...");
	}

	function resume() {
		var point = currentDetail && currentDetail.resumePoint;
		if (!point) {
			return;
		}
		var message = point.stepId + " step부터 이어 갈까요?";
		if (point.warnings && point.warnings.length > 0) {
			message += "\n\n" + point.warnings.join("\n");
		}
		if (window.confirm(message)) {
			control(urls.resumeUrl, "요청 중입니다...");
		}
	}

	function control(url, progress) {
		controlStatusEl.textContent = progress;
		fetch(url + "?executionId=" + encodeURIComponent(activeExecutionId), { method: "POST" })
			.then(function (response) {
				return response.json().then(function (body) {
					if (!response.ok) {
						throw new Error(body.message || ("서버 오류(" + response.status + ")"));
					}
					return body;
				});
			})
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
		if (detail.status !== "RUNNING") {
			return;
		}
		pollTimer = setTimeout(function () {
			pollTimer = null;
			var executionId = detail.executionId;
			fetch(urls.detailUrl + "?executionId=" + encodeURIComponent(executionId), { method: "POST" })
				.then(function (response) { return response.json(); })
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
					if (latest.status !== "RUNNING") {
						loadList(currentPage);
					}
				})
				.catch(function () { schedulePoll(detail); });
		}, POLL_MILLIS);
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

	// 기다리는 APPROVAL step이 어떤 방식인지에 따라 버튼을 바꿔 보여 준다.
	// - routes가 비어 있으면: 승인/반려 버튼
	// - routes에 이름이 있으면: 그 이름마다 버튼 하나씩(누르면 그 이름을 route로 보낸다)
	function renderDecisionButtons(pendingApproval) {
		var routes = (pendingApproval && pendingApproval.routes) || [];
		var hasRoutes = routes.length > 0;

		approveBtn.style.display = hasRoutes ? "none" : "";
		rejectBtn.style.display = hasRoutes ? "none" : "";

		routeButtonsEl.innerHTML = "";
		routes.forEach(function (route) {
			var button = document.createElement("button");
			button.type = "button";
			button.textContent = route;
			button.addEventListener("click", function () { decide(false, route); });
			routeButtonsEl.appendChild(button);
			routeButtonsEl.appendChild(document.createTextNode(" "));
		});

		var label = "";
		if (pendingApproval) {
			label = "step: " + pendingApproval.stepId + (pendingApproval.approverRole ? " / 결정할 사람: " + pendingApproval.approverRole : "");
			if (hasRoutes) {
				label += " / 아래 선택지 중 하나를 고르세요";
			}
		}
		decisionStepEl.textContent = label;
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
		decisionStatusEl.textContent = "처리 중입니다...";
		fetch(urls.decisionUrl, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				executionId: activeExecutionId,
				approved: approved,
				route: route,
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
				loadList(currentPage);
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

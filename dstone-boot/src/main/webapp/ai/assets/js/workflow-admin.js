var DstoneAiWorkflowAdmin = (function () {

	var urls = {};
	var statusFilterEl, workflowFilterEl, executionFilterEl, mineFilterEl, refreshBtn, resetBtn, listBodyEl, totalEl, pagerEl;
	var detailPanelEl, detailIdEl, detailBadgeEl, detailRequesterEl, detailResultEl, detailContextEl, historyBodyEl;
	var decisionFormEl, approverEl, commentEl, approveBtn, rejectBtn, decisionStatusEl, decisionStepEl, routeButtonsEl;

	var controlEl, controlMessageEl, controlWarningsEl, controlStatusEl, pauseBtn, resumeBtn;
	var docsBodyEl, docsAllEl, docsCountEl;
	var rewindEl, rewindMessageEl, rewindWarningsEl, rewindFilesEl, rewindKeptEl, rewindClearLabelEl, rewindClearEl, rewindStatusEl, rewindRunBtn;
	// [여기서 다시]로 고른 step의 미리보기(엔진 GET .../rewind 응답). 패널을 닫으면 비운다.
	var currentRewind = null;

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
		// 여러 사람이 함께 쓰므로 기본은 내가 시작한 실행만 본다.
		params.set("mine", mineFilterEl.checked ? "true" : "false");
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
				"<td>" + escapeHtml(row.requestedBy || "-") + "</td>" +
				"<td><span class=\"workflow-badge workflow-badge-" + escapeHtml((row.status || "unknown").toLowerCase()) + "\">" + escapeHtml(row.status) + "</span></td>" +
				"<td class=\"wa-num\">" + row.currentStepIndex + "</td>" +
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
			closeRewind();
		}
		if (isActive(detail.status)) {
			closeRewind();
		}
		activeExecutionId = detail.executionId;
		currentDetail = detail;
		detailPanelEl.style.display = "";
		markSelectedRow();
		renderControl(detail);
		schedulePoll(detail);

		detailIdEl.textContent = detail.executionId;
		detailRequesterEl.textContent = detail.requestedBy || "-";
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
				"<td class=\"wa-fit\">" + escapeHtml(h.stepId) + "</td>" +
				"<td class=\"wa-fit\">" + escapeHtml(h.stepType) + "</td>" +
				"<td class=\"wa-fit\">" + escapeHtml(h.ref) + "</td>" +
				"<td class=\"wa-fit\"><span class=\"workflow-badge workflow-badge-" + (h.success ? "done" : "failed") + "\">" + (h.success ? "성공" : "실패") + "</span></td>" +
				"<td class=\"wa-fit wa-num\">" + (h.durationMs == null ? "-" : h.durationMs) + "</td>" +
				"<td class=\"wa-text-cell\"></td>" +
				"<td class=\"wa-doc-cell\"></td>" +
				"<td class=\"wa-fit\">" + escapeHtml(formatTime(h.executedAt)) + "</td>" +
				"<td class=\"wa-fit wa-rewind-cell\"></td>";
			fillHistoryText(tr.querySelector(".wa-text-cell"), h.success ? h.outputSummary : h.failureReason, !h.success, index);
			tr.setAttribute("data-step-id", h.stepId || "");
			tr.setAttribute("data-executed-at", h.executedAt || "");
			historyBodyEl.appendChild(tr);
		});
		renderRewindButtons(detail);
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

	// 스텝 이력의 '문서' 칸. 같은 step이 여러 번 돌았으면(forEach 반복 포함) 마지막 줄에만 단다(기록은 step별로 합쳐져 있다).
	// 단, 그 step이 마지막으로 돈 회차에 건드린 파일만 단다. 재실행 때마다 새 폴더에 문서를 만드는 Workflow는
	// 같은 이름의 문서가 회차 수만큼 쌓이기 때문이다(지난 회차 문서는 아래 'step 문서' 표에 그대로 있다).
	function renderHistoryDocs() {
		var rows = historyBodyEl.querySelectorAll("tr[data-step-id]");
		var lastRowOfStep = {};
		// step별 마지막 회차의 시작 시각. 이력 시각(executedAt)은 step이 끝난 시각이라,
		// 바로 앞 줄의 시각을 그 회차의 시작으로 본다(맨 첫 줄이면 제한 없음).
		var lastRunSince = {};
		var previousStepId = null;
		var previousTime = null;
		Array.prototype.forEach.call(rows, function (tr) {
			var stepId = baseStepId(tr.getAttribute("data-step-id"));
			lastRowOfStep[stepId] = tr;
			// forEach 반복 줄(step[0], step[1] ...)은 이어서 나오므로 한 회차로 묶는다.
			if (stepId !== previousStepId) {
				lastRunSince[stepId] = previousTime;
			}
			previousStepId = stepId;
			var time = Date.parse(tr.getAttribute("data-executed-at") || "");
			previousTime = isNaN(time) ? previousTime : time;
		});
		Array.prototype.forEach.call(rows, function (tr) {
			var cell = tr.querySelector(".wa-doc-cell");
			cell.innerHTML = "";
			var stepId = baseStepId(tr.getAttribute("data-step-id"));
			if (lastRowOfStep[stepId] !== tr) {
				return;
			}
			var since = lastRunSince[stepId];
			filesOfStep(stepId, false, since).forEach(function (file) {
				cell.appendChild(docChip(file, actionOf(file, stepId, since)));
			});
			var earlier = docsWrittenOnlyBefore(stepId, since);
			if (earlier.length > 0) {
				cell.appendChild(noNewDocWarning(earlier));
			}
		});
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
		controlMessageEl.textContent = (detail.status === "FAILED" ? "다시 실행할 step: " : "재개할 step: ") + point.stepId + " (" + point.stepType + ")";
		(point.warnings || []).forEach(function (warning) {
			var li = document.createElement("li");
			li.textContent = warning;
			controlWarningsEl.appendChild(li);
		});
	}

	// 스텝 이력의 [여기서 다시] 버튼. step마다 마지막으로 돈 줄에 하나만 단다(forEach 반복 줄은 묶어서 하나).
	// 돌고 있는 실행은 되돌릴 수 없어 버튼을 잠근다.
	function renderRewindButtons(detail) {
		var rows = historyBodyEl.querySelectorAll("tr[data-step-id]");
		var lastRowOfStep = {};
		Array.prototype.forEach.call(rows, function (tr) {
			lastRowOfStep[baseStepId(tr.getAttribute("data-step-id"))] = tr;
		});
		Object.keys(lastRowOfStep).forEach(function (stepId) {
			if (!stepId) {
				return;
			}
			var button = document.createElement("button");
			button.type = "button";
			button.className = "kn-plain wa-rewind-btn";
			button.textContent = "여기서 다시";
			button.title = stepId + " step부터 다시 실행합니다";
			button.disabled = isActive(detail.status);
			button.addEventListener("click", function () { openRewind(stepId); });
			lastRowOfStep[stepId].querySelector(".wa-rewind-cell").appendChild(button);
		});
	}

	// 고른 step으로 되돌리면 무엇이 남고 무엇을 지울 수 있는지 엔진에 물어 패널에 보여 준다.
	function openRewind(stepId) {
		var executionId = activeExecutionId;
		var params = new URLSearchParams();
		params.set("executionId", executionId);
		params.set("fromStep", stepId);
		rewindEl.style.display = "";
		rewindMessageEl.textContent = stepId + " step을 확인하는 중입니다...";
		rewindWarningsEl.innerHTML = "";
		rewindFilesEl.style.display = "none";
		rewindKeptEl.style.display = "none";
		rewindClearLabelEl.style.display = "none";
		rewindStatusEl.textContent = "";
		rewindRunBtn.disabled = true;
		currentRewind = null;
		fetchJson(urls.rewindPointUrl + "?" + params.toString())
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
		var params = new URLSearchParams();
		params.set("executionId", activeExecutionId);
		params.set("fromStep", point.fromStep);
		params.set("clearState", clear ? "true" : "false");
		rewindStatusEl.textContent = "요청 중입니다...";
		rewindRunBtn.disabled = true;
		fetchJson(urls.rewindUrl + "?" + params.toString())
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
	function fetchJson(url) {
		return fetch(url, { method: "POST" }).then(function (response) {
			return response.json().then(function (body) {
				if (!response.ok) {
					throw new Error(body.message || ("서버 오류(" + response.status + ")"));
				}
				return body;
			});
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
		if (!isActive(detail.status)) {
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
		return tr ? tr.textContent : null;
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
				return response.json().then(function (body) {
					if (!response.ok) {
						// 409: 다른 사람이 먼저 결정함, 503: 실행 대기열이 꽉 참 등. 엔진이 준 사유를 보여 준다.
						throw new Error(body.message || ("서버 오류(" + response.status + ")"));
					}
					return body;
				});
			})
			.then(function (detail) {
				// 엔진은 결정을 받으면 이어서 돌릴 실행을 대기열에 넣고 바로 돌려준다(QUEUED). 진행은 자동 갱신으로 본다.
				decisionStatusEl.textContent = "결정을 전달했습니다. 이어서 실행합니다 (" + detail.status + ")";
				renderDetail(detail);
				loadList(currentPage);
			})
			.catch(function (err) {
				decisionStatusEl.textContent = "처리 실패: " + err.message;
				loadDetail(activeExecutionId);
			});
	}

	function escapeHtml(value) {
		var div = document.createElement("div");
		div.textContent = value == null ? "" : value;
		return div.innerHTML;
	}

	return { init: init };
})();

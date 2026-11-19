/*
 * AI 개요 화면(views/ai/index.jsp).
 * - 맨 위 한 줄: 엔진 연결, Workflow 수, 실행 현황(실행 중, 대기 중, 승인 대기). 승인 대기가 있거나 연결이 안 될 때만 색이 붙는다.
 * - Workflow 목록: Workflow마다 step을 정의 순서대로 색 띠로 그린다(GET /api/ai/workflow/overview).
 * - 최근 실행: 실행 관리 화면과 같은 목록 API(GET /api/ai/workflow/executions)의 첫 몇 건.
 */
var DstoneAiOverview = (function () {

	var RECENT_SIZE = 6;
	// 실제 업무용 폴더를 먼저 보이고, 예제(sample)와 시험용(test)은 뒤로 미룬다
	var LATE_FOLDERS = { sample: 1, test: 2 };

	var urls = {};
	var workflows = [];
	var folder = "";
	var counts = null;
	var workflowError = null;

	var statusEl, countEl, foldersEl, listEl, runsEl;
	var esc = DstoneAi.escapeHtml;

	function init(options) {
		urls = options;
		statusEl = document.getElementById("ov-status");
		countEl = document.getElementById("ov-flow-count");
		foldersEl = document.getElementById("ov-folders");
		listEl = document.getElementById("ov-flow-list");
		runsEl = document.getElementById("ov-runs");

		foldersEl.addEventListener("click", function (e) {
			var btn = e.target.closest("button[data-folder]");
			if (btn) {
				folder = btn.getAttribute("data-folder");
				renderFolders();
				renderList();
			}
		});

		loadWorkflows();
		loadRuns();
	}

	/* ---------- Workflow 목록 ---------- */

	function loadWorkflows() {
		fetch(urls.workflowsUrl)
			.then(function (response) {
				if (!response.ok) {
					throw new Error("응답 " + response.status);
				}
				return response.json();
			})
			.then(function (list) {
				workflows = sortByFolder(list || []);
				renderFolders();
				renderList();
				renderStatus();
			})
			.catch(function (err) {
				workflowError = err.message;
				listEl.innerHTML = "<p class=\"ov-empty\">dstone-ai-engine에서 Workflow 목록을 받지 못했습니다(" + esc(err.message) + "). 엔진이 떠 있는지 확인하세요.</p>";
				renderStatus();
			});
	}

	function folderOf(workflow) {
		var path = workflow.path || "";
		var parts = path.split("/");
		// workflows/sample/a.yml → sample
		return parts.length >= 3 ? parts[1] : "기타";
	}

	function sortByFolder(list) {
		return list.slice().sort(function (a, b) {
			var fa = folderOf(a);
			var fb = folderOf(b);
			var ra = LATE_FOLDERS[fa] || 0;
			var rb = LATE_FOLDERS[fb] || 0;
			if (ra !== rb) {
				return ra - rb;
			}
			if (fa !== fb) {
				return fa < fb ? -1 : 1;
			}
			return a.id < b.id ? -1 : (a.id > b.id ? 1 : 0);
		});
	}

	function renderFolders() {
		var names = [];
		var size = {};
		for (var i = 0; i < workflows.length; i++) {
			var name = folderOf(workflows[i]);
			if (!size[name]) {
				size[name] = 0;
				names.push(name);
			}
			size[name]++;
		}
		// 폴더가 하나뿐이면 고를 것이 없으니 숨긴다
		if (names.length < 2) {
			foldersEl.innerHTML = "";
			return;
		}
		var html = folderButton("", "전체", workflows.length);
		for (var j = 0; j < names.length; j++) {
			html += folderButton(names[j], names[j], size[names[j]]);
		}
		foldersEl.innerHTML = html;
	}

	function folderButton(value, label, size) {
		var on = value === folder;
		return "<button type=\"button\" data-folder=\"" + esc(value) + "\" aria-pressed=\"" + on + "\">" + esc(label) + " <span>" + size + "</span></button>";
	}

	function renderList() {
		var shown = [];
		for (var i = 0; i < workflows.length; i++) {
			if (!folder || folderOf(workflows[i]) === folder) {
				shown.push(workflows[i]);
			}
		}
		countEl.textContent = shown.length + "개";
		if (shown.length === 0) {
			listEl.innerHTML = "<p class=\"ov-empty\">등록된 Workflow가 없습니다. <a href=\"" + urls.linkBase + "ai/designer/designer.html\">디자이너</a>에서 새로 만들 수 있습니다.</p>";
			return;
		}
		var html = "";
		for (var j = 0; j < shown.length; j++) {
			html += flowRow(shown[j]);
		}
		listEl.innerHTML = html;
	}

	/** Workflow 한 줄: 이름과 설명, step 띠, 바로 가기 */
	function flowRow(workflow) {
		var id = workflow.id;
		var steps = workflow.steps || [];
		var html = "<article class=\"ov-flow\">"
			+ "<div class=\"ov-flow-text\">"
			+ "<h4>" + esc(id) + "</h4>"
			+ "<p>" + esc(firstLine(workflow.description)) + "</p>"
			+ "</div>"
			+ "<div class=\"ov-flow-run\">"
			+ strip(steps, workflow.error)
			+ "<p class=\"ov-flow-meta\">" + esc(summary(steps)) + "</p>"
			+ "</div>"
			+ "<div class=\"ov-flow-links\">"
			+ "<a href=\"" + urls.linkBase + "ai/workflow/workflow.html#" + encodeURIComponent(id) + "\">실행해 보기</a>"
			+ "<a href=\"" + urls.linkBase + "ai/designer/designer.html#" + encodeURIComponent(id) + "\">흐름 열기</a>"
			+ "</div>"
			+ "</article>";
		return html;
	}

	/** step 띠. 한 칸이 step 하나, 색은 종류, 줄무늬는 항목마다 반복하는 step */
	function strip(steps, error) {
		if (error) {
			return "<p class=\"ov-strip-error\">정의를 읽지 못했습니다: " + esc(error) + "</p>";
		}
		if (steps.length === 0) {
			return "<p class=\"ov-strip-error\">step이 없습니다.</p>";
		}
		var html = "<ol class=\"ov-strip\" aria-label=\"step " + steps.length + "개\">";
		for (var i = 0; i < steps.length; i++) {
			var step = steps[i];
			var tip = step.id + (step.name ? " " + step.name : "") + " · " + DstoneAi.stepTypeLabel(step.type) + (step.target ? " · " + step.target : "") + (step.forEach ? " · 항목마다 반복" : "");
			html += "<li class=\"ov-seg ov-seg-" + esc(step.type) + (step.forEach ? " ov-seg-each" : "") + "\" title=\"" + esc(tip) + "\"><span>" + esc(tip) + "</span></li>";
		}
		return html + "</ol>";
	}

	/** "step 7개 · 승인 2번 · 반복 1" 처럼 띠를 말로 한 번 더 */
	function summary(steps) {
		if (steps.length === 0) {
			return "";
		}
		var approvals = 0;
		var agents = 0;
		var loops = 0;
		for (var i = 0; i < steps.length; i++) {
			if (steps[i].type === "APPROVAL") {
				approvals++;
			}
			if (steps[i].type === "AGENT" || steps[i].type === "SUPERVISOR" || steps[i].type === "ROUTER") {
				agents++;
			}
			if (steps[i].forEach) {
				loops++;
			}
		}
		var parts = ["step " + steps.length + "개"];
		if (agents > 0) {
			parts.push("AI 판단 " + agents + "번");
		}
		if (approvals > 0) {
			parts.push("승인 " + approvals + "번");
		}
		if (loops > 0) {
			parts.push("반복 " + loops + "곳");
		}
		return parts.join(", ");
	}

	function firstLine(text) {
		var value = String(text || "").trim();
		var cut = value.indexOf("\n");
		return cut < 0 ? value : value.substring(0, cut);
	}

	/* ---------- 실행 현황 ---------- */

	/** 실행 중, 대기 중, 승인 대기 건수는 상태별로 1건만 달라고 해서 total 만 읽는다 */
	function loadRuns() {
		var statuses = ["RUNNING", "QUEUED", "WAITING_APPROVAL"];
		var calls = [];
		for (var i = 0; i < statuses.length; i++) {
			calls.push(fetchRuns(statuses[i], 1));
		}
		calls.push(fetchRuns("", RECENT_SIZE));
		Promise.all(calls)
			.then(function (pages) {
				counts = { RUNNING: pages[0].total || 0, QUEUED: pages[1].total || 0, WAITING_APPROVAL: pages[2].total || 0 };
				renderRuns(pages[3].items || []);
				renderStatus();
			})
			.catch(function () {
				counts = { failed: true };
				runsEl.innerHTML = "<li class=\"ov-empty\">실행 기록을 받지 못했습니다. 엔진의 실행 기록 DB를 확인하세요.</li>";
				renderStatus();
			});
	}

	function fetchRuns(status, size) {
		var params = new URLSearchParams();
		params.set("status", status);
		params.set("page", "0");
		params.set("size", String(size));
		return fetch(urls.runsUrl + "?" + params.toString()).then(function (response) {
			var type = response.headers.get("Content-Type") || "";
			if (!response.ok || type.indexOf("json") < 0) {
				throw new Error("응답 " + response.status);
			}
			return response.json();
		});
	}

	function renderRuns(items) {
		if (items.length === 0) {
			runsEl.innerHTML = "<li class=\"ov-empty\">아직 실행한 Workflow가 없습니다. 왼쪽 목록에서 [실행해 보기]로 시작해 보세요.</li>";
			return;
		}
		var html = "";
		for (var i = 0; i < items.length; i++) {
			var run = items[i];
			html += "<li>"
				+ "<span class=\"ov-run-name\">" + esc(run.workflowId) + "</span>"
				+ DstoneAi.badgeHtml(run.status)
				+ "<span class=\"ov-run-time\" title=\"" + esc(run.updatedAt || "") + "\">" + esc(ago(run.updatedAt)) + "</span>"
				+ "</li>";
		}
		runsEl.innerHTML = html;
	}

	/** 2026-11-18T04:55:03Z → "3분 전" */
	function ago(value) {
		var time = value ? new Date(value).getTime() : NaN;
		if (isNaN(time)) {
			return "";
		}
		var minutes = Math.floor((Date.now() - time) / 60000);
		if (minutes < 1) {
			return "방금";
		}
		if (minutes < 60) {
			return minutes + "분 전";
		}
		var hours = Math.floor(minutes / 60);
		if (hours < 24) {
			return hours + "시간 전";
		}
		return Math.floor(hours / 24) + "일 전";
	}

	/* ---------- 맨 위 한 줄 ---------- */

	function renderStatus() {
		var parts = [];
		if (workflowError) {
			parts.push(item("엔진에 연결되지 않음", "bad"));
		} else if (workflows.length > 0 || counts) {
			parts.push(item("엔진 연결됨", "ok") + " <span>Workflow " + workflows.length + "개</span>");
		}
		if (counts && counts.failed) {
			parts.push(item("실행 기록을 읽지 못함", "warn"));
		} else if (counts) {
			parts.push("<span>실행 중 " + counts.RUNNING + "</span>");
			parts.push("<span>대기 중 " + counts.QUEUED + "</span>");
			if (counts.WAITING_APPROVAL > 0) {
				parts.push("<a class=\"ov-status-item ov-status-warn\" href=\"" + urls.linkBase + "ai/admin/workflow/workflow.html\">승인 대기 " + counts.WAITING_APPROVAL + "건, 확인하러 가기</a>");
			} else {
				parts.push("<span>승인 대기 0</span>");
			}
		}
		if (parts.length > 0) {
			statusEl.innerHTML = parts.join("<i class=\"ov-status-sep\" aria-hidden=\"true\"></i>");
		}
	}

	function item(text, level) {
		return "<span class=\"ov-status-item ov-status-" + level + "\">" + esc(text) + "</span>";
	}

	return { init: init };
})();

/**
 * Agent 평가 화면(/ai/admin/eval/eval.html).
 *   사례 목록(GET /api/ai/eval/cases) → 사례를 고르면 params·모델·반복 수를 넣고 시작(POST /api/ai/eval/runs)
 *   결과 목록(GET /api/ai/eval/runs) → 줄을 누르면 상세(GET /api/ai/eval/runs/{id})
 *   비교 표는 결과 목록을 (사례, 대상 버전, 모델)로 묶어 화면에서 평균을 낸다.
 *   Workflow 사례는 모델을 바꿀 수 없고, snapshotAfter가 있으면 떠 둔 결과(첫 결과물)의 채점도 같이 보인다.
 * 끝나지 않은 평가가 있으면 5초마다 목록을 다시 읽는다.
 */
(function () {
	var api = DstoneAi.api;
	var esc = DstoneAi.escapeHtml;

	var cases = [];
	var selectedCase = null;
	var selectedRunId = null;
	var timer = null;

	var caseBody = document.getElementById("ev-case-body");
	var startPanel = document.getElementById("ev-start-panel");
	var paramBody = document.getElementById("ev-param-body");
	var startStatus = document.getElementById("ev-start-status");
	var startBtn = document.getElementById("ev-start-btn");
	var runBody = document.getElementById("ev-run-body");
	var compareBody = document.getElementById("ev-compare-body");
	var onlyCase = document.getElementById("ev-only-case");

	/** 밀리초를 "1분 5초"처럼 */
	function duration(ms) {
		if (ms === null || ms === undefined) {
			return "-";
		}
		var seconds = Math.round(Number(ms) / 1000);
		if (seconds < 60) {
			return seconds + "초";
		}
		return Math.floor(seconds / 60) + "분 " + (seconds % 60) + "초";
	}

	/** 시각 글자를 "11-22 11:35"처럼 */
	function shortTime(value) {
		if (!value) {
			return "-";
		}
		var date = new Date(value);
		if (isNaN(date.getTime())) {
			return String(value);
		}
		function two(n) { return (n < 10 ? "0" : "") + n; }
		return two(date.getMonth() + 1) + "-" + two(date.getDate()) + " " + two(date.getHours()) + ":" + two(date.getMinutes()) + ":" + two(date.getSeconds());
	}

	function score(run) {
		if (run.total === null || run.total === undefined) {
			return "-";
		}
		return run.passed + "/" + run.total;
	}

	/** 떠 둔 결과(snapshotAfter)의 채점이 있으면 "첫 → 마지막", 없으면 마지막만 */
	function scoreWithSnapshot(run) {
		if (run.snapshotTotal === null || run.snapshotTotal === undefined) {
			return score(run);
		}
		return run.snapshotPassed + "/" + run.snapshotTotal + " → " + score(run);
	}

	/** 사례가 부르는 대상: Agent id 또는 "Workflow id" */
	function targetOf(item) {
		return item.workflow ? "Workflow " + item.workflow : item.agent;
	}

	/* ---------- 사례 ---------- */

	function loadCases() {
		api("GET", "/api/ai/eval/cases").then(function (list) {
			cases = list || [];
			renderCases();
		}).catch(function (e) {
			caseBody.innerHTML = "<tr><td colspan=\"4\">사례를 읽지 못했습니다: " + esc(e.message) + "</td></tr>";
		});
	}

	function renderCases() {
		if (cases.length === 0) {
			caseBody.innerHTML = "<tr><td colspan=\"4\">사례가 없습니다. 엔진의 conf/eval 아래에 *.eval.yml을 두세요.</td></tr>";
			return;
		}
		var html = "";
		cases.forEach(function (item, index) {
			if (item.problem) {
				html += "<tr class=\"ev-case-bad\"><td>" + esc(item.path) + "</td><td>-</td><td colspan=\"2\">사례 파일이 틀렸습니다: " + esc(item.problem) + "</td></tr>";
				return;
			}
			var on = selectedCase && selectedCase.id === item.id;
			html += "<tr class=\"ev-row" + (on ? " ev-row-on" : "") + "\" data-index=\"" + index + "\" tabindex=\"0\">"
				+ "<td><span class=\"workflow-job-id\">" + esc(item.id) + "</span></td>"
				+ "<td>" + esc(targetOf(item)) + "</td>"
				+ "<td>" + esc(item.description || "") + "</td>"
				+ "<td>" + (item.checks ? item.checks.length : 0) + "개</td></tr>";
		});
		caseBody.innerHTML = html;
	}

	function chooseCase(item) {
		selectedCase = item;
		renderCases();
		document.getElementById("ev-start-case").textContent = item.id;
		var desc = "채점 항목: " + (item.checks || []).join(", ");
		if (item.workflow) {
			var approvals = item.approvals || {};
			var rules = Object.keys(approvals).map(function (step) { return step + "=" + approvals[step]; });
			desc += " | 승인: " + (rules.length === 0 ? "없음(첫 승인에서 멈추고 채점)" : rules.join(", ") + " (이 밖의 승인에서 멈추고 채점)");
			if (item.snapshotAfter) {
				desc += " | " + item.snapshotAfter + " 직후 결과도 따로 채점";
			}
		}
		if (item.needsServer) {
			desc += " | 격리 시험 서버를 띄워 채점";
		}
		document.getElementById("ev-start-desc").textContent = desc;
		var html = "";
		var params = item.params || {};
		Object.keys(params).forEach(function (name) {
			html += "<tr><th><label for=\"ev-param-" + esc(name) + "\">" + esc(name) + "</label></th>"
				+ "<td><input type=\"text\" class=\"ev-param\" id=\"ev-param-" + esc(name) + "\" data-name=\"" + esc(name) + "\" value=\"" + esc(params[name]) + "\" /></td></tr>";
		});
		html += (item.workflow
				? "<tr><th>모델</th><td><input type=\"hidden\" id=\"ev-model\" value=\"\" /><span class=\"ai-hint\">Workflow 사례는 각 Agent 정의의 모델로 돕니다.</span></td></tr>"
				: "<tr><th><label for=\"ev-model\">모델</label></th><td><input type=\"text\" id=\"ev-model\" placeholder=\"비우면 Agent·provider 기본 모델 (예: qwen/qwen3.8-27b)\" /></td></tr>")
			+ "<tr><th><label for=\"ev-repeat\">반복</label></th><td><select id=\"ev-repeat\">"
			+ "<option value=\"1\">1번</option><option value=\"2\">2번</option><option value=\"3\" selected>3번</option><option value=\"4\">4번</option><option value=\"5\">5번</option>"
			+ "</select> <span class=\"ai-hint\">한 번만 돌린 결과는 우연일 수 있어서 3번 이상을 권합니다.</span></td></tr>";
		paramBody.innerHTML = html;
		startStatus.textContent = "";
		startPanel.style.display = "";
		if (onlyCase.checked) {
			loadRuns();
		}
	}

	caseBody.addEventListener("click", function (event) {
		var row = event.target.closest("tr.ev-row");
		if (row) {
			chooseCase(cases[Number(row.getAttribute("data-index"))]);
		}
	});
	caseBody.addEventListener("keydown", function (event) {
		var row = event.target.closest("tr.ev-row");
		if (row && (event.key === "Enter" || event.key === " ")) {
			event.preventDefault();
			chooseCase(cases[Number(row.getAttribute("data-index"))]);
		}
	});

	startBtn.addEventListener("click", function () {
		if (!selectedCase) {
			return;
		}
		var params = {};
		paramBody.querySelectorAll("input.ev-param").forEach(function (input) {
			params[input.getAttribute("data-name")] = input.value;
		});
		var body = {
			caseId: selectedCase.id,
			params: params,
			model: document.getElementById("ev-model").value.trim(),
			repeat: Number(document.getElementById("ev-repeat").value)
		};
		startBtn.disabled = true;
		startStatus.textContent = "평가를 대기열에 넣는 중...";
		api("POST", "/api/ai/eval/runs", body).then(function (result) {
			startStatus.textContent = result.runIds.length + "건을 대기열에 넣었습니다. 끝나면 아래 결과에 채점이 나옵니다.";
			loadRuns();
		}).catch(function (e) {
			startStatus.textContent = "시작하지 못했습니다: " + e.message;
		}).then(function () {
			startBtn.disabled = false;
		});
	});

	/* ---------- 결과 ---------- */

	function loadRuns() {
		var url = "/api/ai/eval/runs?limit=200";
		if (onlyCase.checked && selectedCase) {
			url += "&caseId=" + encodeURIComponent(selectedCase.id);
		}
		api("GET", url).then(function (runs) {
			runs = runs || [];
			renderRuns(runs);
			renderCompare(runs);
			var pending = runs.some(function (run) { return run.status === "QUEUED" || run.status === "RUNNING"; });
			clearTimeout(timer);
			if (pending) {
				timer = setTimeout(loadRuns, 5000);
			}
			if (selectedRunId) {
				loadDetail(selectedRunId);
			}
		}).catch(function (e) {
			runBody.innerHTML = "<tr><td colspan=\"8\">결과를 읽지 못했습니다: " + esc(e.message) + "</td></tr>";
		});
	}

	function renderRuns(runs) {
		document.getElementById("ev-run-count").textContent = runs.length + "건";
		if (runs.length === 0) {
			runBody.innerHTML = "<tr><td colspan=\"8\">아직 평가가 없습니다.</td></tr>";
			return;
		}
		var html = "";
		runs.forEach(function (run) {
			var on = run.runId === selectedRunId;
			var full = run.total && run.passed === run.total;
			var stopped = run.stoppedAt ? " <span class=\"ai-hint\">" + esc(run.stoppedAt) + "</span>" : "";
			html += "<tr class=\"ev-row" + (on ? " ev-row-on" : "") + "\" data-run=\"" + esc(run.runId) + "\" tabindex=\"0\">"
				+ "<td>" + esc(shortTime(run.createdAt)) + "</td>"
				+ "<td>" + esc(run.caseId) + "</td>"
				+ "<td>" + esc(run.agentVersion || "-") + "</td>"
				+ "<td>" + esc(run.model || "-") + "</td>"
				+ "<td>" + DstoneAi.badgeHtml(run.status) + stopped + "</td>"
				+ "<td class=\"" + (full ? "ev-score-full" : "") + "\">" + esc(scoreWithSnapshot(run)) + "</td>"
				+ "<td>" + esc(duration(run.durationMs)) + "</td>"
				+ "<td>" + esc(run.toolCalls === null || run.toolCalls === undefined ? "-" : String(run.toolCalls)) + "</td></tr>";
		});
		runBody.innerHTML = html;
	}

	/** (사례, 대상 버전, 모델)로 묶어 끝난 평가의 평균을 낸다. 떠 둔 결과가 있으면 그 평균도 */
	function renderCompare(runs) {
		var groups = {};
		var order = [];
		runs.forEach(function (run) {
			if (run.status !== "DONE" && run.status !== "FAILED") {
				return;
			}
			if (run.total === null || run.total === undefined) {
				return;
			}
			var key = run.caseId + "\u0000" + (run.agentVersion || "") + "\u0000" + (run.model || "");
			if (!groups[key]) {
				groups[key] = { caseId: run.caseId, version: run.agentVersion, model: run.model, n: 0, rate: 0, full: 0, sn: 0, srate: 0, ms: 0, tools: 0, tokens: 0 };
				order.push(key);
			}
			var g = groups[key];
			g.n++;
			g.rate += run.total ? run.passed / run.total : 0;
			g.full += run.total && run.passed === run.total ? 1 : 0;
			if (run.snapshotTotal) {
				g.sn++;
				g.srate += run.snapshotPassed / run.snapshotTotal;
			}
			g.ms += Number(run.durationMs || 0);
			g.tools += Number(run.toolCalls || 0);
			g.tokens += Number(run.promptTokens || 0);
		});
		if (order.length === 0) {
			compareBody.innerHTML = "<tr><td colspan=\"10\">끝난 평가가 없습니다.</td></tr>";
			return;
		}
		order.sort();
		var html = "";
		order.forEach(function (key) {
			var g = groups[key];
			html += "<tr><td>" + esc(g.caseId) + "</td><td>" + esc(g.version || "-") + "</td><td>" + esc(g.model || "-") + "</td>"
				+ "<td>" + g.n + "</td>"
				+ "<td>" + Math.round(g.rate / g.n * 100) + "%</td>"
				+ "<td>" + g.full + "/" + g.n + "</td>"
				+ "<td>" + (g.sn ? Math.round(g.srate / g.sn * 100) + "%" : "-") + "</td>"
				+ "<td>" + esc(duration(g.ms / g.n)) + "</td>"
				+ "<td>" + Math.round(g.tools / g.n) + "</td>"
				+ "<td>" + Math.round(g.tokens / g.n).toLocaleString() + "</td></tr>";
		});
		compareBody.innerHTML = html;
	}

	function loadDetail(runId) {
		api("GET", "/api/ai/eval/runs/" + encodeURIComponent(runId)).then(function (run) {
			renderDetail(run);
		}).catch(function (e) {
			document.getElementById("ev-detail-meta").textContent = "상세를 읽지 못했습니다: " + e.message;
		});
	}

	function renderDetail(run) {
		document.getElementById("ev-detail-panel").style.display = "";
		document.getElementById("ev-detail-id").textContent = run.runId;
		var meta = [
			["사례", run.caseId],
			["대상", run.agentId + " " + (run.agentVersion || "")],
			["모델", run.model || "-"],
			["상태", DstoneAi.statusLabel(run.status)],
			["채점", score(run)],
			["시간", duration(run.durationMs)],
			["LLM 회차", run.rounds === null || run.rounds === undefined ? "-" : run.rounds],
			["입력/출력 토큰", (run.promptTokens || 0).toLocaleString() + " / " + (run.completionTokens || 0).toLocaleString()],
			["시작한 사람", run.requestedBy || "-"],
			["작업 폴더", run.workDir || "-"]
		];
		var flow = run.answer && typeof run.answer === "object" ? run.answer.workflowRun : null;
		if (flow) {
			meta.push(["Workflow 실행 ID", (flow.executionId || "-") + " (실행 관리 화면에서 찾을 수 있습니다)"]);
			meta.push(["끝난 모양", (flow.end || "-") + (flow.stoppedAt ? " @ " + flow.stoppedAt : "") + ", " + (flow.steps || 0) + " step"]);
			meta.push(["고른 승인", (flow.decisions || []).map(function (d) { return d.step + "=" + d.decision; }).join(", ") || "없음"]);
		}
		var params = run.params || {};
		Object.keys(params).forEach(function (name) {
			meta.push(["params." + name, params[name]]);
		});
		document.getElementById("ev-detail-meta").innerHTML = meta.map(function (pair) {
			return "<div><dt>" + esc(pair[0]) + "</dt><dd>" + esc(String(pair[1])) + "</dd></div>";
		}).join("");
		document.getElementById("ev-detail-checks").innerHTML = checksHtml(run.checks || []);
		var snapshot = flow ? flow.snapshot : null;
		var snapshotTitle = document.getElementById("ev-detail-snapshot-title");
		snapshotTitle.style.display = snapshot ? "" : "none";
		snapshotTitle.textContent = snapshot ? "떠 둔 결과의 채점(" + snapshot.after + " 직후, " + snapshot.passed + "/" + snapshot.total + ")" : "";
		document.getElementById("ev-detail-snapshot").innerHTML = snapshot ? checksHtml(snapshot.checks || []) : "";
		var failure = document.getElementById("ev-detail-failure");
		failure.style.display = run.failure ? "" : "none";
		failure.textContent = run.failure ? "실패 사유: " + run.failure : "";
		var tools = run.tools || {};
		var names = Object.keys(tools);
		document.getElementById("ev-detail-tools").textContent = names.length === 0 ? "없음" : names.map(function (name) {
			return name + " " + tools[name];
		}).join(", ");
		document.getElementById("ev-detail-answer").textContent = run.answer === null || run.answer === undefined ? "(답 없음)"
			: (typeof run.answer === "string" ? run.answer : JSON.stringify(run.answer, null, 2));
	}

	function checksHtml(checks) {
		return checks.length === 0 ? "<li>아직 채점하지 않았습니다.</li>" : checks.map(function (check) {
			return "<li class=\"" + (check.ok ? "ev-ok" : "ev-ng") + "\"><span class=\"ev-mark\">" + (check.ok ? "통과" : "실패") + "</span>"
				+ esc(check.name) + (check.detail ? " <span class=\"ai-hint\">" + esc(check.detail) + "</span>" : "") + "</li>";
		}).join("");
	}

	runBody.addEventListener("click", function (event) {
		var row = event.target.closest("tr.ev-row");
		if (row) {
			selectedRunId = row.getAttribute("data-run");
			runBody.querySelectorAll("tr.ev-row-on").forEach(function (tr) { tr.classList.remove("ev-row-on"); });
			row.classList.add("ev-row-on");
			loadDetail(selectedRunId);
		}
	});
	runBody.addEventListener("keydown", function (event) {
		var row = event.target.closest("tr.ev-row");
		if (row && (event.key === "Enter" || event.key === " ")) {
			event.preventDefault();
			row.click();
		}
	});
	onlyCase.addEventListener("change", loadRuns);
	document.getElementById("ev-refresh-btn").addEventListener("click", function () {
		loadCases();
		loadRuns();
	});

	loadCases();
	loadRuns();
})();

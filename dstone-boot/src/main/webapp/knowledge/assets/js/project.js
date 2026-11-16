/**
 * "프로젝트 · 분석" 화면.
 *   왼쪽: 프로젝트 목록과 등록, 분석 시작과 진행 상황, 리비전 목록
 *   오른쪽: 고른 리비전의 결과를 탭 다섯 개로 나눠 보여 준다(개요 · 연결 품질 · 분석 내용 · 미사용 · 실행 기록)
 * 표의 제목과 설명, 어느 탭에 둘지는 knowledge-terms.js 의 SECTIONS 에 있다.
 */
var DstoneKnowledgeProject = (function () {

	var K = DstoneKnowledge;
	var T = DstoneKnowledgeTerms;
	var el = {};
	var projectId = null;
	var pollTimer = null;
	// 지금 오른쪽에 보여 주는 리비전(닫혀 있으면 null)
	var current = null;
	// 지금 고른 탭. 다른 리비전을 열어도 같은 탭을 그대로 보여 준다
	var activeTab = "overview";
	// 탭마다 "살펴볼 것이 있다(⚠)" 표시. 요약과 미사용 결과가 따로 도착하므로 모아 두었다가 그린다
	var tabWarnings = {};
	// 요청 번호. 다른 프로젝트 · 리비전 · Job 을 고른 뒤 늦게 도착한 이전 응답은 버린다
	var summaryRequestNo = 0;
	var revisionsRequestNo = 0;
	var jobRequestNo = 0;

	/** 탭 이름 */
	var TAB_LABELS = { overview: "개요", connect: "연결 품질", content: "분석 내용", unused: "미사용", history: "실행 기록" };

	/** 적용 진단 항목 → 그 내용을 자세히 볼 수 있는 탭. warn / bad 가 나오면 그 탭에 ⚠ 를 붙인다 */
	var CHECK_TAB = {
		calls: "connect", unresolvedRequests: "connect", unresolvedSql: "connect", unresolvedPaths: "connect",
		endpoints: "content", endpointsWithoutMethod: "content", screens: "content", layers: "content", unknownXml: "content", javaSql: "content",
		parse: "history", unsupportedFiles: "history"
	};

	function init() {
		["kn-health", "kn-project-list", "kn-project-message", "kn-project-form", "kn-new-project-id", "kn-new-project-name", "kn-new-project-path",
			"kn-new-project-classpath", "kn-new-project-btn", "kn-project-save-btn",
			"kn-analysis-panel", "kn-selected-project", "kn-revision-label", "kn-incremental", "kn-rerun-from", "kn-analysis-start-btn", "kn-analysis-message",
			"kn-job-box", "kn-job-id", "kn-job-badge", "kn-job-progress", "kn-job-cancel-btn", "kn-job-passes", "kn-job-errors",
			"kn-revision-list", "kn-retention-keep", "kn-retention-btn", "kn-revision-message",
			"kn-detail-empty", "kn-summary-panel", "kn-summary-title", "kn-summary-badge", "kn-summary-delete-btn", "kn-tabs",
			"kn-summary-cards", "kn-overview", "kn-connect", "kn-content", "kn-history"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		el["kn-project-save-btn"].addEventListener("click", saveProject);
		el["kn-new-project-btn"].addEventListener("click", clearProjectForm);
		el["kn-analysis-start-btn"].addEventListener("click", startAnalysis);
		el["kn-job-cancel-btn"].addEventListener("click", cancelJob);
		el["kn-retention-btn"].addEventListener("click", applyRetention);
		el["kn-summary-delete-btn"].addEventListener("click", function () {
			if (current) {
				deleteRevision(current);
			}
		});
		Array.prototype.forEach.call(el["kn-tabs"].querySelectorAll(".kn-tab"), function (button) {
			button.addEventListener("click", function () {
				showTab(button.getAttribute("data-tab"));
			});
		});

		K.call("GET", "/api/system/health").then(function (health) {
			el["kn-health"].innerHTML = K.badge(health.status);
		}).catch(function (err) {
			el["kn-health"].innerHTML = K.badge("DOWN");
			K.message(el["kn-project-message"], err.message, true);
		});
		loadProjects().then(openFromHash);
	}

	/**
	 * 대시보드에서 프로젝트 줄을 누르면 주소에 #p=프로젝트&r=리비전&l=리비전 라벨 이 붙어 옵니다.
	 * 그 프로젝트를 고르고, 리비전이 있으면 그 결과까지 바로 엽니다. 한 번 열고 나면 주소를 비웁니다.
	 */
	function openFromHash(projects) {
		var wanted = {};
		location.hash.replace(/^#/, "").split("&").forEach(function (pair) {
			var at = pair.indexOf("=");
			if (at > 0) {
				wanted[pair.substring(0, at)] = decodeURIComponent(pair.substring(at + 1));
			}
		});
		if (!wanted.p) {
			return;
		}
		history.replaceState(null, "", location.pathname + location.search);
		var project = null;
		(projects || []).forEach(function (row) {
			if (row.projectId === wanted.p) {
				project = row;
			}
		});
		if (!project) {
			K.message(el["kn-project-message"], "등록되지 않은 프로젝트입니다: " + wanted.p, true);
			return;
		}
		selectProject(project);
		if (/^[0-9]+$/.test(wanted.r || "")) {
			loadSummary({ revisionId: wanted.r, revisionLabel: wanted.l || "" });
		}
	}

	/* ---------------- 프로젝트 ---------------- */

	/** 프로젝트 목록을 다시 그립니다. 다 그리면 프로젝트 목록을 넘겨줍니다(실패하면 빈 목록) */
	function loadProjects() {
		return K.call("GET", "/api/projects").then(function (projects) {
			if (!projects || projects.length === 0) {
				el["kn-project-list"].innerHTML = "<p class=\"ai-hint\">등록된 프로젝트가 없습니다. 아래 \"프로젝트 등록 / 수정\"을 펴서 등록하세요.</p>";
				el["kn-project-form"].open = true;
				return [];
			}
			el["kn-project-list"].innerHTML = projects.map(function (project, index) {
				return "<button type=\"button\" class=\"kn-pick" + (project.projectId === projectId ? " kn-pick-on" : "") + "\" data-index=\"" + index + "\">"
					+ "<span class=\"kn-pick-main\">" + K.escapeHtml(project.projectId) + "</span>"
					+ (project.projectName && project.projectName !== project.projectId ? "<span class=\"kn-pick-sub\">" + K.escapeHtml(project.projectName) + "</span>" : "")
					+ "</button>";
			}).join("");
			Array.prototype.forEach.call(el["kn-project-list"].querySelectorAll(".kn-pick"), function (button) {
				button.addEventListener("click", function () {
					selectProject(projects[parseInt(button.getAttribute("data-index"), 10)]);
				});
			});
			return projects;
		}).catch(function (err) {
			K.message(el["kn-project-message"], err.message, true);
			return [];
		});
	}

	function saveProject() {
		var body = {
			projectId: el["kn-new-project-id"].value.trim(),
			projectName: el["kn-new-project-name"].value.trim(),
			localPath: el["kn-new-project-path"].value.trim()
		};
		if (el["kn-new-project-classpath"].value.trim()) {
			body.classpath = el["kn-new-project-classpath"].value.trim();
		}
		K.call("POST", "/api/projects", null, body).then(function (project) {
			K.message(el["kn-project-message"], "등록했습니다: " + project.projectId);
			selectProject(project);
			loadProjects();
		}).catch(function (err) {
			K.message(el["kn-project-message"], err.message, true);
		});
	}

	/** 등록 칸을 비워 새 프로젝트를 적게 한다 */
	function clearProjectForm() {
		["kn-new-project-id", "kn-new-project-name", "kn-new-project-path", "kn-new-project-classpath"].forEach(function (id) {
			el[id].value = "";
		});
		el["kn-new-project-id"].focus();
	}

	function selectProject(project) {
		projectId = project.projectId;
		stopPolling();
		markPicked(el["kn-project-list"], function (button) {
			return button.querySelector(".kn-pick-main").textContent === projectId;
		});
		el["kn-analysis-panel"].style.display = "";
		closeSummary();
		el["kn-job-box"].style.display = "none";
		el["kn-selected-project"].textContent = projectId;
		// 분석 칸은 프로젝트마다 새로 시작한다. 앞 프로젝트의 라벨이 남아 있으면 엉뚱한 리비전을 이어서 분석하게 된다.
		el["kn-revision-label"].value = "";
		el["kn-incremental"].checked = false;
		el["kn-rerun-from"].value = "";
		el["kn-revision-list"].innerHTML = "<p class=\"ai-hint\">읽는 중...</p>";
		// 등록 칸에도 채워 둔다. 경로나 이름을 고쳐서 다시 등록하면 수정이 된다.
		el["kn-new-project-id"].value = project.projectId;
		el["kn-new-project-name"].value = project.projectName || "";
		el["kn-new-project-path"].value = project.localPath || "";
		el["kn-new-project-classpath"].value = project.classpath || "";
		K.message(el["kn-analysis-message"], "");
		K.message(el["kn-revision-message"], "");
		loadRevisions();
	}

	/* ---------------- 분석 ---------------- */

	function startAnalysis() {
		var body = {};
		if (el["kn-revision-label"].value.trim()) {
			body.revisionLabel = el["kn-revision-label"].value.trim();
		}
		if (el["kn-incremental"].checked) {
			body.incremental = true;
		}
		if (el["kn-rerun-from"].value) {
			body.rerunFrom = el["kn-rerun-from"].value;
		}
		K.call("POST", "/api/projects/" + encodeURIComponent(projectId) + "/analyses", null, body).then(function (started) {
			var text = "분석을 시작했습니다: 리비전 #" + started.revisionId + " (" + started.revisionLabel + ")";
			if (started.incremental) {
				text += ", 증분 분석(기준 #" + started.baseRevisionId + ")";
			}
			if (started.note) {
				text += " - " + started.note;
			}
			K.message(el["kn-analysis-message"], text);
			watchJob(started.analysisId);
			loadRevisions();
		}).catch(function (err) {
			K.message(el["kn-analysis-message"], err.message, true);
		});
	}

	/** Job 이 끝날 때까지 3초마다 상태를 다시 읽습니다 */
	function watchJob(analysisId) {
		stopPolling();
		el["kn-job-box"].style.display = "";
		el["kn-job-id"].textContent = analysisId;
		var requestNo = jobRequestNo;
		var load = function () {
			K.call("GET", "/api/analyses/" + encodeURIComponent(analysisId)).then(function (job) {
				if (requestNo !== jobRequestNo) {
					return;
				}
				renderJob(job);
				if (job.status !== "READY" && job.status !== "RUNNING") {
					stopPolling();
					loadRevisions();
				}
			}).catch(function (err) {
				if (requestNo !== jobRequestNo) {
					return;
				}
				stopPolling();
				K.message(el["kn-analysis-message"], err.message, true);
			});
		};
		load();
		pollTimer = setInterval(load, 3000);
	}

	/** 3초마다 읽기를 멈춘다. 이미 보낸 요청의 응답도 그리지 않는다 */
	function stopPolling() {
		jobRequestNo++;
		if (pollTimer) {
			clearInterval(pollTimer);
			pollTimer = null;
		}
	}

	function renderJob(job) {
		var running = job.status === "READY" || job.status === "RUNNING";
		el["kn-job-badge"].innerHTML = K.badge(job.status);
		el["kn-job-progress"].textContent = (job.currentPass ? "단계 " + job.currentPass : "") + (job.errorCount ? " · 오류 " + job.errorCount + "건" : "")
			+ (job.errorMessage ? " · " + job.errorMessage : "");
		el["kn-job-cancel-btn"].disabled = !running;
		K.table(el["kn-job-passes"], [
			{ key: "pass", label: "단계", html: function (row) { return "<span class=\"kn-code\" title=\"" + K.escapeHtml(T.PASS[row.pass] || "") + "\">" + K.escapeHtml(row.pass) + "</span>"; } },
			{ key: "status", label: "상태", html: function (row) { return K.badge(row.status); } },
			{ key: "progress", label: "처리", num: true, html: function (row) {
				return row.totalCount ? K.escapeHtml(T.fmt(row.doneCount) + " / " + T.fmt(row.totalCount)) : "";
			} }
		], job.passes);
		if (job.errors && job.errors.length > 0) {
			el["kn-job-errors"].innerHTML = "<div class=\"kn-section-title\">오류 (앞쪽 " + job.errors.length + "건)</div><div class=\"kn-scroll\"></div>";
			K.autoTable(el["kn-job-errors"].querySelector(".kn-scroll"), job.errors);
		} else {
			el["kn-job-errors"].innerHTML = "";
		}
	}

	function cancelJob() {
		K.call("POST", "/api/analyses/" + encodeURIComponent(el["kn-job-id"].textContent) + "/cancel").then(function () {
			K.message(el["kn-analysis-message"], "취소를 요청했습니다. 돌고 있는 단계가 다음 확인 지점에서 멈춥니다.");
		}).catch(function (err) {
			K.message(el["kn-analysis-message"], err.message, true);
		});
	}

	/* ---------------- 리비전 ---------------- */

	function loadRevisions() {
		var requestNo = ++revisionsRequestNo;
		K.call("GET", "/api/projects/" + encodeURIComponent(projectId) + "/revisions").then(function (revisions) {
			if (requestNo !== revisionsRequestNo) {
				return;
			}
			if (!revisions || revisions.length === 0) {
				el["kn-revision-list"].innerHTML = "<p class=\"ai-hint\">아직 분석한 적이 없습니다. 위의 \"분석 시작\"을 누르세요.</p>";
				return;
			}
			el["kn-revision-list"].innerHTML = revisions.map(function (revision, index) {
				var on = current && String(current.revisionId) === String(revision.revisionId);
				return "<button type=\"button\" class=\"kn-pick" + (on ? " kn-pick-on" : "") + "\" data-index=\"" + index + "\" data-id=\"" + revision.revisionId + "\">"
					+ "<span class=\"kn-pick-main\">#" + revision.revisionId + " " + K.escapeHtml(revision.revisionLabel) + " " + K.badge(revision.status) + "</span>"
					+ "<span class=\"kn-pick-sub\">" + K.escapeHtml(revision.createdAt || "") + (revision.parentRevisionId ? " · 증분(기준 #" + revision.parentRevisionId + ")" : "") + "</span>"
					+ "</button>";
			}).join("");
			Array.prototype.forEach.call(el["kn-revision-list"].querySelectorAll(".kn-pick"), function (button) {
				button.addEventListener("click", function () {
					loadSummary(revisions[parseInt(button.getAttribute("data-index"), 10)]);
				});
			});
		}).catch(function (err) {
			if (requestNo === revisionsRequestNo) {
				K.message(el["kn-revision-message"], err.message, true);
			}
		});
	}

	function deleteRevision(revision) {
		if (!confirm("리비전 #" + revision.revisionId + " (" + revision.revisionLabel + ")과 그 분석 결과를 모두 지웁니다. 되돌릴 수 없습니다.")) {
			return;
		}
		K.call("DELETE", "/api/revisions/" + revision.revisionId).then(function () {
			K.message(el["kn-revision-message"], "지웠습니다: #" + revision.revisionId);
			if (current && String(current.revisionId) === String(revision.revisionId)) {
				closeSummary();
			}
			loadRevisions();
		}).catch(function (err) {
			K.message(el["kn-revision-message"], err.message, true);
		});
	}

	function applyRetention() {
		var keep = parseInt(el["kn-retention-keep"].value, 10);
		if (!(keep >= 1)) {
			K.message(el["kn-revision-message"], "남길 개수는 1 이상이어야 합니다.", true);
			return;
		}
		if (!confirm("분석이 끝난 리비전 가운데 최근 " + keep + "개만 남기고 나머지를 지웁니다. 되돌릴 수 없습니다.")) {
			return;
		}
		K.call("POST", "/api/projects/" + encodeURIComponent(projectId) + "/retention", { keep: keep }).then(function (result) {
			K.message(el["kn-revision-message"], "지운 리비전 " + result.deletedRevisions.length + "개, 건너뛴 리비전 " + result.skippedRevisions.length
				+ "개, 지운 임베딩 " + result.deletedEmbeddings + "건");
			// 보고 있던 리비전이 지워졌으면 오른쪽을 닫는다
			(result.deletedRevisions || []).forEach(function (deleted) {
				var deletedId = String(deleted && deleted.revisionId !== undefined ? deleted.revisionId : deleted);
				if (current && String(current.revisionId) === deletedId) {
					closeSummary();
				}
			});
			loadRevisions();
		}).catch(function (err) {
			K.message(el["kn-revision-message"], err.message, true);
		});
	}

	/* ---------------- 리비전 상세 (오른쪽) ---------------- */

	/** 오른쪽을 비우고 안내 글을 보인다. 읽고 있던 응답도 버린다 */
	function closeSummary() {
		summaryRequestNo++;
		current = null;
		el["kn-summary-panel"].style.display = "none";
		el["kn-detail-empty"].style.display = "";
		parkUnresolvedPanel();
		["kn-summary-cards", "kn-overview", "kn-connect", "kn-content", "kn-history"].forEach(function (id) {
			el[id].innerHTML = "";
		});
		markPicked(el["kn-revision-list"], function () { return false; });
		DstoneKnowledgeUnresolved.close();
		DstoneKnowledgeUnused.close();
	}

	function loadSummary(revision) {
		var requestNo = ++summaryRequestNo;
		current = revision;
		// 풀지 못한 참조 · 미사용 목록은 리비전에 딸린 것이다. 다른 리비전을 열면 앞 리비전의 것은 닫는다.
		DstoneKnowledgeUnresolved.close();
		DstoneKnowledgeUnused.close();
		markPicked(el["kn-revision-list"], function (button) {
			return button.getAttribute("data-id") === String(revision.revisionId);
		});
		el["kn-detail-empty"].style.display = "none";
		el["kn-summary-panel"].style.display = "";
		el["kn-summary-title"].textContent = "#" + revision.revisionId + " " + (revision.revisionLabel || "");
		el["kn-summary-badge"].innerHTML = revision.status ? K.badge(revision.status) : "";
		el["kn-summary-cards"].innerHTML = "";
		parkUnresolvedPanel();
		["kn-overview", "kn-connect", "kn-content", "kn-history"].forEach(function (id) {
			el[id].innerHTML = "<p class=\"ai-hint\">읽는 중...</p>";
		});
		tabWarnings = {};
		renderTabWarnings();
		showTab(activeTab);
		Promise.all([
			K.call("GET", "/api/revisions/" + revision.revisionId),
			// RAG 상태는 따로 조회한다. 실패해도 나머지는 보여 준다.
			K.call("GET", "/api/revisions/" + revision.revisionId + "/rag").catch(function () { return null; }),
			// 적용 진단도 따로. 실패하면 진단 상자만 빠진다.
			K.call("GET", "/api/revisions/" + revision.revisionId + "/diagnostics").catch(function () { return null; })
		]).then(function (results) {
			if (requestNo !== summaryRequestNo) {
				return;
			}
			var summary = results[0];
			summary.rag = results[1];
			summary.diagnostics = results[2];
			current.revisionLabel = summary.revisionLabel;
			el["kn-summary-title"].textContent = "#" + summary.revisionId + " " + summary.revisionLabel;
			el["kn-summary-badge"].innerHTML = K.badge(summary.status);
			renderSummary(summary);
			if (window.innerWidth < 861) {
				el["kn-summary-panel"].scrollIntoView({ behavior: "smooth" });
			}
		}).catch(function (err) {
			if (requestNo === summaryRequestNo) {
				el["kn-overview"].innerHTML = "<div class=\"kn-message kn-error\">" + K.escapeHtml(err.message) + "</div>";
			}
		});
		// 미사용은 계산이 오래 걸릴 수 있어 따로 읽는다. 다 되면 개요의 미사용 상자와 탭 표시를 채운다.
		DstoneKnowledgeUnused.load({ projectId: projectId, revisionId: revision.revisionId, revisionLabel: revision.revisionLabel }, function (unused) {
			if (requestNo !== summaryRequestNo) {
				return;
			}
			var box = el["kn-overview"].querySelector(".kn-unused-overview");
			if (box) {
				DstoneKnowledgeUnused.renderOverview(box, unused, function () { showTab("unused"); });
			}
			setTabWarning("unused", unused && unused.warning ? "warn" : null);
		});
	}

	function renderSummary(summary) {
		var metrics = metricsOf(summary);
		var values = [
			T.valueAt(summary, "files.total"),
			sumOf(T.valueAt(summary, "declarations.types"), "count"),
			sumOf(T.valueAt(summary, "relations.byType"), "count"),
			sumOf(T.valueAt(summary, "semantic.endpoints"), "count"),
			metrics.callResolutionRate === undefined ? "-" : metrics.callResolutionRate + "%",
			sumOf(T.valueAt(summary, "rag.documents"), "documents")
		];
		// 카드마다 숫자, 이름, 그 숫자가 무엇을 센 것인지
		el["kn-summary-cards"].innerHTML = T.CARDS.map(function (card, index) {
			return "<div class=\"kn-card\" title=\"" + K.escapeHtml(card[1]) + "\"><div class=\"kn-card-value\">" + K.escapeHtml(T.fmt(values[index]))
				+ "</div><div class=\"kn-card-label\">" + K.escapeHtml(card[0]) + "</div><div class=\"kn-card-help\">" + K.escapeHtml(card[1]) + "</div></div>";
		}).join("");

		// 개요: 한눈에 보기 → 미사용 → 적용 진단
		el["kn-overview"].innerHTML = "";
		var verdict = verdictLines(summary, metrics);
		el["kn-overview"].appendChild(verdictBox(verdict));
		var unusedBox = document.createElement("div");
		unusedBox.className = "kn-verdict kn-unused-overview";
		unusedBox.innerHTML = "<div class=\"kn-section-title\" style=\"margin-top:0;\">미사용</div><p class=\"ai-hint\">계산하는 중...</p>";
		el["kn-overview"].appendChild(unusedBox);
		var unused = DstoneKnowledgeUnused.summary();
		if (unused && String(unused.revisionId) === String(summary.revisionId)) {
			DstoneKnowledgeUnused.renderOverview(unusedBox, unused, function () { showTab("unused"); });
		}
		if (summary.diagnostics) {
			el["kn-overview"].appendChild(diagnosticsBox(summary.diagnostics));
		}

		// 나머지 탭: SECTIONS 를 탭별로 나눠 그린다
		var targets = { connect: el["kn-connect"], content: el["kn-content"], history: el["kn-history"] };
		parkUnresolvedPanel();
		Object.keys(targets).forEach(function (tab) {
			targets[tab].innerHTML = "";
		});
		T.SECTIONS.forEach(function (section) {
			var rows = section.build ? section.build(summary) : T.valueAt(summary, section.path);
			if (rows === null || rows === undefined || (Array.isArray(rows) && rows.length === 0)) {
				return;
			}
			if (section.showIf && !section.showIf(rows)) {
				return;
			}
			var box = sectionBox(section, rows, summary);
			targets[section.tab].appendChild(box);
			if (section.path === "relations.unresolvedReasons") {
				attachUnresolved(box, rows, summary);
			}
		});
		Object.keys(targets).forEach(function (tab) {
			if (!targets[tab].firstChild) {
				targets[tab].innerHTML = "<p class=\"ai-hint\">보여 줄 결과가 없습니다.</p>";
			}
		});

		// 탭의 ⚠: 한눈에 보기의 경고, 적용 진단의 warn / bad, 끝나지 않은 리비전
		verdict.forEach(function (line) {
			if (line.tab && (line.level === "warn" || line.level === "bad")) {
				setTabWarning(line.tab, line.level);
			}
		});
		((summary.diagnostics && summary.diagnostics.checks) || []).forEach(function (check) {
			if (CHECK_TAB[check.key] && (check.level === "warn" || check.level === "bad")) {
				setTabWarning(CHECK_TAB[check.key], check.level);
			}
		});
		if (summary.status !== "READY") {
			setTabWarning("history", "bad");
		}
	}

	/* ---------------- 탭 ---------------- */

	function showTab(tab) {
		activeTab = tab;
		Array.prototype.forEach.call(el["kn-tabs"].querySelectorAll(".kn-tab"), function (button) {
			var on = button.getAttribute("data-tab") === tab;
			button.classList.toggle("kn-tab-on", on);
			button.setAttribute("aria-selected", on ? "true" : "false");
		});
		Array.prototype.forEach.call(el["kn-summary-panel"].querySelectorAll(".kn-tab-panel"), function (panel) {
			panel.style.display = panel.getAttribute("data-panel") === tab ? "" : "none";
		});
	}

	/** 탭에 ⚠ 를 붙이거나(level = warn / bad) 뗀다(null). bad 가 warn 보다 앞선다 */
	function setTabWarning(tab, level) {
		if (level === null) {
			delete tabWarnings[tab];
		} else if (tabWarnings[tab] !== "bad") {
			tabWarnings[tab] = level;
		}
		renderTabWarnings();
	}

	function renderTabWarnings() {
		Array.prototype.forEach.call(el["kn-tabs"].querySelectorAll(".kn-tab"), function (button) {
			var tab = button.getAttribute("data-tab");
			var level = tabWarnings[tab];
			button.innerHTML = K.escapeHtml(TAB_LABELS[tab]) + (level ? " <span class=\"kn-tab-warn kn-tab-warn-" + level + "\" title=\"이 탭에 살펴볼 것이 있습니다\">⚠</span>" : "");
		});
	}

	/* ---------------- 개요 ---------------- */

	/**
	 * "한눈에 보기"의 줄들. 숫자를 읽지 않아도 이 리비전을 믿고 써도 되는지 알 수 있게 말로 풀어 준다.
	 * 기준(95% / 80%)은 대략의 눈금이다. 줄마다 그 내용을 자세히 볼 수 있는 탭을 적어 두어 탭의 ⚠ 로 쓴다.
	 * @return [{level: good / warn / bad / info, text, tab}]
	 */
	function verdictLines(summary, metrics) {
		var lines = [];
		if (metrics.callResolutionRate !== undefined && metrics.callResolutionRate !== null) {
			var rate = Number(metrics.callResolutionRate);
			lines.push({ level: rate >= 95 ? "good" : rate >= 80 ? "warn" : "bad", tab: "connect",
				text: "메소드 호출 " + T.fmt(metrics.callCount) + "건 가운데 **" + rate + "%** 가 누구를 부르는지 풀렸습니다. "
					+ (rate >= 95 ? "호출 관계와 영향도 결과를 믿고 써도 됩니다." : rate >= 80 ? "쓸 수는 있지만 빠진 호출이 있을 수 있습니다. 라이브러리(jar) 위치를 확인하세요."
						: "빠진 호출이 많습니다. 프로젝트 등록 칸에 라이브러리(jar) 위치를 적고 다시 분석하세요.") });
		}
		if (metrics.parseSuccessRate !== undefined && metrics.parseSuccessRate !== null) {
			var parse = Number(metrics.parseSuccessRate);
			lines.push({ level: parse >= 100 ? "good" : "warn", tab: "history",
				text: parse >= 100 ? "Java 파일을 모두 읽었습니다." : "Java 파일의 " + parse + "% 만 읽었습니다. 읽지 못한 파일의 메소드는 결과에 없습니다(실행 기록 탭의 Job 오류)." });
		}
		var endpoints = sumOf(T.valueAt(summary, "semantic.endpoints"), "count");
		if (typeof endpoints === "number") {
			lines.push({ level: endpoints > 0 ? "good" : "warn", tab: "content",
				text: endpoints > 0 ? "진입점(주소 등) " + T.fmt(endpoints) + "개를 찾았습니다." : "진입점을 하나도 찾지 못했습니다. 영향도 분석의 '진입점' 표가 비어 나옵니다." });
		}
		var sql = 0;
		(T.valueAt(summary, "relations.byType") || []).forEach(function (row) {
			if (row.relationType === "EXECUTES_SQL" && row.confidence !== "UNRESOLVED") {
				sql += row.count;
			}
		});
		lines.push({ level: sql > 0 ? "good" : "info", tab: "connect",
			text: sql > 0 ? "메소드와 SQL 이 " + T.fmt(sql) + "건 이어졌습니다. 테이블에서 출발하는 영향도 분석을 할 수 있습니다."
				: "메소드와 이어진 SQL 이 없습니다(매퍼 XML 을 쓰지 않는 프로그램이면 정상). 테이블에서 출발하는 영향도 분석은 할 수 없습니다." });
		var done = 0;
		var all = 0;
		(T.valueAt(summary, "rag.embedding") || []).forEach(function (row) {
			all += row.chunks;
			if (row.status === "DONE") {
				done += row.chunks;
			}
		});
		if (all > 0) {
			var percent = Math.floor(100 * done / all);
			lines.push({ level: percent >= 100 ? "good" : "info", tab: "history",
				text: percent >= 100 ? "임베딩이 끝났습니다. 뜻으로 찾는 검색을 온전히 쓸 수 있습니다."
					: "임베딩이 " + percent + "% 진행됐습니다(" + T.fmt(done) + " / " + T.fmt(all) + "). 끝날 때까지 뜻으로 찾는 검색은 일부만 됩니다. 이름으로 찾기 · 호출 관계 · 영향도는 지금도 됩니다." });
		}
		if (summary.status !== "READY") {
			lines.unshift({ level: "bad", tab: "history", text: "이 리비전은 분석이 끝나지 않았습니다(상태 " + summary.status + "). 아래 숫자는 도중까지의 결과입니다." });
		}
		return lines;
	}

	function verdictBox(lines) {
		var box = document.createElement("div");
		box.className = "kn-verdict";
		box.innerHTML = "<div class=\"kn-section-title\" style=\"margin-top:0;\">한눈에 보기</div>" + lines.map(function (line) {
			// 글은 먼저 이스케이프하고, **로 감싼 부분만 굵게 바꾼다.
			var text = K.escapeHtml(line.text).replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
			return "<div class=\"kn-verdict-line kn-verdict-" + line.level + "\"><span class=\"kn-verdict-mark\">" + markOf(line.level) + "</span>" + text + "</div>";
		}).join("");
		return box;
	}

	/** 진단 항목의 해결 방법 → 화면에 보일 말 */
	var SOLUTION = {
		RULE: "규칙 파일로 해결",
		CLASSPATH: "jar 위치 등록",
		SETTING: "프로젝트 설정",
		SOURCE: "소스 보충",
		DEVELOP: "분석기 개발 필요",
		NONE: ""
	};

	/**
	 * "적용 진단" 상자. 어디가 안 이어졌는지와, 그것을 무엇으로 풀지(규칙 / jar / 설정 / 소스 / 개발)를 항목마다 보여 준다.
	 * 새 프로젝트를 처음 분석한 뒤 "규칙만으로 되는지, 개발이 필요한지"를 바로 가리려는 것이다.
	 */
	function diagnosticsBox(diagnostics) {
		var box = document.createElement("div");
		box.className = "kn-verdict kn-diag";
		var summary = diagnostics.summary || {};
		var head = Object.keys(summary).map(function (key) {
			return "<span class=\"kn-diag-solution kn-diag-" + K.escapeHtml(key) + "\">" + K.escapeHtml(SOLUTION[key] || key) + " " + summary[key] + "</span>";
		}).join(" ");
		var html = "<div class=\"kn-section-title\" style=\"margin-top:0;\">적용 진단</div>"
			+ "<p class=\"ai-hint\">살펴볼 항목을 해결 방법별로 나눴습니다. <b>규칙 파일로 해결</b>은 conf/rules/projects/" + K.escapeHtml(String(diagnostics.projectId || "").toLowerCase())
			+ "/ 에 규칙을 적고 SEMANTIC 부터 다시 분석하면 되고, <b>분석기 개발 필요</b>는 지금 지원하는 범위 밖입니다.</p>"
			+ (head ? "<div class=\"kn-row\">" + head + "</div>" : "<p class=\"ai-hint\">살펴볼 항목이 없습니다.</p>");
		(diagnostics.checks || []).forEach(function (check) {
			var count = check.count === null || check.count === undefined ? "" : " (" + T.fmt(check.count)
				+ (check.total === null || check.total === undefined ? "" : " / " + T.fmt(check.total)) + ")";
			var solution = SOLUTION[check.solution] ? " <span class=\"kn-diag-solution kn-diag-" + K.escapeHtml(check.solution) + "\">" + K.escapeHtml(SOLUTION[check.solution]) + "</span>" : "";
			html += "<div class=\"kn-verdict-line kn-verdict-" + K.escapeHtml(check.level) + "\"><span class=\"kn-verdict-mark\">" + markOf(check.level) + "</span><b>"
				+ K.escapeHtml(check.title) + "</b>" + K.escapeHtml(count) + solution + " — " + K.escapeHtml(check.message) + "</div>";
			if (check.samples && check.samples.length > 0) {
				html += "<details class=\"kn-howto kn-diag-samples\"><summary>보기 " + check.samples.length + "건</summary><div class=\"kn-scroll\" data-key=\""
					+ K.escapeHtml(check.key) + "\"></div></details>";
			}
		});
		box.innerHTML = html;
		(diagnostics.checks || []).forEach(function (check) {
			var target = box.querySelector(".kn-scroll[data-key=\"" + check.key + "\"]");
			if (target) {
				K.autoTable(target, check.samples);
			}
		});
		return box;
	}

	function markOf(level) {
		return level === "good" ? "✔" : level === "bad" ? "✖" : level === "warn" ? "!" : "·";
	}

	/* ---------------- 연결 품질 ---------------- */

	/**
	 * "풀지 못한 참조의 이유" 표 바로 아래에 "살펴보기"(unresolved.js)를 붙인다. 표는 펴 두고, 줄을 누르면
	 * 그 줄의 참조의 종류 · 상태 · 이유가 아래 조건으로 걸려 바로 조회된다. 짐작으로 이은 참조(HEURISTIC)는 상태에서 고른다.
	 */
	function attachUnresolved(box, rows, summary) {
		box.open = true;
		var body = box.querySelector(".kn-fold-body");
		var panel = document.getElementById("kn-unresolved-panel");
		body.insertBefore(panel, body.querySelector(".kn-howto"));
		DstoneKnowledgeUnresolved.attach({ revisionId: summary.revisionId, revisionLabel: summary.revisionLabel });
		var trs = box.querySelectorAll(".kn-scroll tbody tr[data-index]");
		Array.prototype.forEach.call(trs, function (tr) {
			tr.classList.add("kn-click-row");
			tr.title = "누르면 아래에서 이 조건으로 조회합니다";
			tr.addEventListener("click", function () {
				Array.prototype.forEach.call(trs, function (other) {
					other.classList.toggle("kn-row-on", other === tr);
				});
				DstoneKnowledgeUnresolved.openReason(rows[parseInt(tr.getAttribute("data-index"), 10)]);
			});
		});
	}

	/** 살펴보기 패널을 원래 자리(숨긴 칸)로 돌려놓는다. 탭 내용을 지우기 전에 불러야 패널이 같이 지워지지 않는다 */
	function parkUnresolvedPanel() {
		var panel = document.getElementById("kn-unresolved-panel");
		var home = document.getElementById("kn-unresolved-home");
		if (panel.parentNode !== home) {
			home.appendChild(panel);
		}
	}

	/* ---------------- 표 하나 ---------------- */

	/** 접힌 표 하나: 제목 — 한 줄 요약. 펴면 무엇을 보여 주는지, 표(영문 값 옆에 뜻), 읽는 법 */
	function sectionBox(section, rows, summary) {
		var box = document.createElement("details");
		box.className = "kn-fold";
		var brief = section.brief ? section.brief(rows, summary) : "";
		var html = "<summary><span class=\"kn-fold-title\">" + K.escapeHtml(section.title) + "</span>"
			+ (brief ? "<span class=\"kn-fold-brief\">" + K.escapeHtml(brief) + "</span>" : "") + "</summary>"
			+ "<div class=\"kn-fold-body\"><p class=\"ai-hint\">" + K.escapeHtml(section.what) + "</p><div class=\"kn-scroll\"></div>";
		var help = "<ul>" + section.columns.filter(function (column) { return column[2]; }).map(function (column) {
			return "<li><b>" + K.escapeHtml(column[1]) + "</b> <code>" + K.escapeHtml(column[0]) + "</code> — " + K.escapeHtml(column[2]) + "</li>";
		}).join("") + "</ul>";
		if (section.read) {
			help += "<p>" + K.escapeHtml(section.read) + "</p>";
		}
		html += "<details class=\"kn-howto\"><summary>이 표 읽는 법</summary>" + help + "</details></div>";
		box.innerHTML = html;
		K.table(box.querySelector(".kn-scroll"), section.columns.map(function (column) {
			var numeric = rows.length > 0 && rows.some(function (row) { return typeof row[column[0]] === "number"; });
			return {
				key: column[0],
				label: column[1],
				num: numeric,
				html: column[3] ? function (row) { return withMeaning(row[column[0]], T.meaningOf(column[3], row[column[0]])); } : null
			};
		}), rows);
		return box;
	}

	/** 영문 값 뒤에 한글 뜻을 흐린 글자로 붙인다 */
	function withMeaning(code, meaning) {
		return "<span class=\"kn-code\">" + K.escapeHtml(code) + "</span>" + (meaning ? "<span class=\"kn-term\">" + K.escapeHtml(meaning) + "</span>" : "");
	}

	/* ---------------- 도우미 ---------------- */

	/** 품질 지표 표를 {이름: 값} 으로 */
	function metricsOf(summary) {
		var metrics = {};
		(T.valueAt(summary, "relations.metrics") || []).forEach(function (row) {
			metrics[row.name] = row.value;
		});
		return metrics;
	}

	/** 행들의 key 값을 더한다. 행 목록이 아니면 "-" */
	function sumOf(rows, key) {
		if (!Array.isArray(rows)) {
			return "-";
		}
		var sum = 0;
		rows.forEach(function (row) {
			sum += Number(row[key]) || 0;
		});
		return sum;
	}

	/** 고르는 목록에서 test 가 참인 단추 하나만 켠다 */
	function markPicked(container, test) {
		Array.prototype.forEach.call(container.querySelectorAll(".kn-pick"), function (button) {
			button.classList.toggle("kn-pick-on", test(button));
		});
	}

	return { init: init };
})();

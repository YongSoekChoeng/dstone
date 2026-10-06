/**
 * "프로젝트 · 분석" 화면: 프로젝트 등록, 분석 시작과 진행 상황, 리비전 목록, 리비전 요약(품질).
 */
var DstoneKnowledgeProject = (function () {

	var K = DstoneKnowledge;
	var el = {};
	var projectId = null;
	var pollTimer = null;

	var T = DstoneKnowledgeTerms;

	function init() {
		["kn-health", "kn-project-table", "kn-new-project-id", "kn-new-project-name", "kn-new-project-path", "kn-new-project-classpath", "kn-project-save-btn", "kn-project-message",
			"kn-analysis-panel", "kn-selected-project", "kn-revision-label", "kn-incremental", "kn-rerun-from", "kn-analysis-start-btn", "kn-analysis-message",
			"kn-job-box", "kn-job-id", "kn-job-badge", "kn-job-progress", "kn-job-cancel-btn", "kn-job-passes", "kn-job-errors",
			"kn-revision-table", "kn-retention-keep", "kn-retention-btn", "kn-revision-message",
			"kn-summary-panel", "kn-summary-title", "kn-summary-cards", "kn-summary-sections"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		el["kn-project-save-btn"].addEventListener("click", saveProject);
		el["kn-analysis-start-btn"].addEventListener("click", startAnalysis);
		el["kn-job-cancel-btn"].addEventListener("click", cancelJob);
		el["kn-retention-btn"].addEventListener("click", applyRetention);

		K.call("GET", "/api/system/health").then(function (health) {
			el["kn-health"].innerHTML = K.badge(health.status);
		}).catch(function (err) {
			el["kn-health"].innerHTML = K.badge("DOWN");
			K.message(el["kn-project-message"], err.message, true);
		});
		loadProjects();
	}

	/* ---------------- 프로젝트 ---------------- */

	function loadProjects() {
		K.call("GET", "/api/projects").then(function (projects) {
			K.table(el["kn-project-table"], [
				{ key: "projectId", label: "projectId", html: function (row) {
					return "<a href=\"javascript:void(0)\" class=\"workflow-history-jobid\">" + K.escapeHtml(row.projectId) + "</a>";
				} },
				{ key: "projectName", label: "이름" },
				{ key: "localPath", label: "소스 폴더", mono: true },
				{ key: "updatedAt", label: "수정 시각" }
			], projects, "등록된 프로젝트가 없습니다.");
			eachRow(el["kn-project-table"], function (tr, index) {
				tr.querySelector("a").addEventListener("click", function () {
					selectProject(projects[index]);
				});
			});
		}).catch(function (err) {
			K.message(el["kn-project-message"], err.message, true);
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
			loadProjects();
			selectProject(project);
		}).catch(function (err) {
			K.message(el["kn-project-message"], err.message, true);
		});
	}

	function selectProject(project) {
		projectId = project.projectId;
		stopPolling();
		el["kn-analysis-panel"].style.display = "";
		el["kn-summary-panel"].style.display = "none";
		el["kn-job-box"].style.display = "none";
		el["kn-selected-project"].textContent = projectId;
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
		var load = function () {
			K.call("GET", "/api/analyses/" + encodeURIComponent(analysisId)).then(function (job) {
				renderJob(job);
				if (job.status !== "READY" && job.status !== "RUNNING") {
					stopPolling();
					loadRevisions();
				}
			}).catch(function (err) {
				stopPolling();
				K.message(el["kn-analysis-message"], err.message, true);
			});
		};
		load();
		pollTimer = setInterval(load, 3000);
	}

	function stopPolling() {
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
			{ key: "pass", label: "단계", html: function (row) { return withMeaning(row.pass, T.PASS[row.pass]); } },
			{ key: "status", label: "상태", html: function (row) { return K.badge(row.status); } },
			{ key: "doneCount", label: "처리", num: true },
			{ key: "totalCount", label: "전체", num: true },
			{ key: "startedAt", label: "시작" },
			{ key: "endedAt", label: "끝" }
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
		K.call("GET", "/api/projects/" + encodeURIComponent(projectId) + "/revisions").then(function (revisions) {
			K.table(el["kn-revision-table"], [
				{ key: "revisionId", label: "#", num: true },
				{ key: "revisionLabel", label: "라벨" },
				{ key: "status", label: "상태", html: function (row) { return K.badge(row.status); } },
				{ key: "parentRevisionId", label: "증분 기준", html: function (row) { return row.parentRevisionId ? "#" + row.parentRevisionId : ""; } },
				{ key: "createdAt", label: "만든 시각" },
				{ key: "action", label: "", html: function () {
					return "<button type=\"button\" class=\"kn-summary-btn\">요약</button> <button type=\"button\" class=\"kn-delete-btn\">삭제</button>";
				} }
			], revisions, "아직 분석한 적이 없습니다.");
			eachRow(el["kn-revision-table"], function (tr, index) {
				var revision = revisions[index];
				tr.querySelector(".kn-summary-btn").addEventListener("click", function () { loadSummary(revision); });
				tr.querySelector(".kn-delete-btn").addEventListener("click", function () { deleteRevision(revision); });
			});
		}).catch(function (err) {
			K.message(el["kn-revision-message"], err.message, true);
		});
	}

	function deleteRevision(revision) {
		if (!confirm("리비전 #" + revision.revisionId + " (" + revision.revisionLabel + ")과 그 분석 결과를 모두 지웁니다. 되돌릴 수 없습니다.")) {
			return;
		}
		K.call("DELETE", "/api/revisions/" + revision.revisionId).then(function () {
			K.message(el["kn-revision-message"], "지웠습니다: #" + revision.revisionId);
			el["kn-summary-panel"].style.display = "none";
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
			loadRevisions();
		}).catch(function (err) {
			K.message(el["kn-revision-message"], err.message, true);
		});
	}

	/* ---------------- 리비전 요약 ---------------- */

	function loadSummary(revision) {
		el["kn-summary-panel"].style.display = "";
		el["kn-summary-title"].textContent = "#" + revision.revisionId + " " + revision.revisionLabel;
		el["kn-summary-cards"].innerHTML = "";
		el["kn-summary-sections"].innerHTML = "<p class=\"ai-hint\">읽는 중...</p>";
		Promise.all([
			K.call("GET", "/api/revisions/" + revision.revisionId),
			// RAG 상태는 따로 조회한다. 실패해도 나머지 요약은 보여 준다.
			K.call("GET", "/api/revisions/" + revision.revisionId + "/rag").catch(function () { return null; })
		]).then(function (results) {
			var summary = results[0];
			summary.rag = results[1];
			renderSummary(summary);
			el["kn-summary-panel"].scrollIntoView({ behavior: "smooth" });
		}).catch(function (err) {
			el["kn-summary-sections"].innerHTML = "<div class=\"kn-message kn-error\">" + K.escapeHtml(err.message) + "</div>";
		});
	}

	function renderSummary(summary) {
		var values = [
			valueAt(summary, "files.total"),
			sumOf(valueAt(summary, "declarations.types")),
			sumOf(valueAt(summary, "relations.byType")),
			sumOf(valueAt(summary, "semantic.endpoints")),
			sumOf(valueAt(summary, "rag.documents"), "documents")
		];
		// 카드마다 숫자, 이름, 그 숫자가 무엇을 센 것인지
		el["kn-summary-cards"].innerHTML = T.CARDS.map(function (card, index) {
			return "<div class=\"kn-card\" title=\"" + K.escapeHtml(card[1]) + "\"><div class=\"kn-card-value\">" + K.escapeHtml(formatNumber(values[index]))
				+ "</div><div class=\"kn-card-label\">" + K.escapeHtml(card[0]) + "</div><div class=\"kn-card-help\">" + K.escapeHtml(card[1]) + "</div></div>";
		}).join("");

		el["kn-summary-sections"].innerHTML = "";
		el["kn-summary-sections"].appendChild(verdictBox(summary));
		T.SECTIONS.forEach(function (section) {
			var value = valueAt(summary, section.path);
			if (value === null || value === undefined || (Array.isArray(value) && value.length === 0)) {
				return;
			}
			el["kn-summary-sections"].appendChild(sectionBox(section, value));
		});
	}

	/**
	 * 요약 맨 위의 "한눈에 보기". 숫자를 읽지 않아도 이 리비전을 믿고 써도 되는지 알 수 있게 말로 풀어 준다.
	 * 기준(95% / 80%)은 대략의 눈금이다. knowledge-terms.js 의 '품질 지표' 설명과 같은 기준을 쓴다.
	 */
	function verdictBox(summary) {
		var lines = [];
		var metrics = {};
		(valueAt(summary, "relations.metrics") || []).forEach(function (row) {
			metrics[row.name] = row.value;
		});
		if (metrics.callResolutionRate !== undefined && metrics.callResolutionRate !== null) {
			var rate = Number(metrics.callResolutionRate);
			lines.push([rate >= 95 ? "good" : rate >= 80 ? "warn" : "bad",
				"메소드 호출 " + formatNumber(metrics.callCount) + "건 가운데 " + rate + "% 가 누구를 부르는지 풀렸습니다. "
				+ (rate >= 95 ? "호출 관계와 영향도 결과를 믿고 써도 됩니다." : rate >= 80 ? "쓸 수는 있지만 빠진 호출이 있을 수 있습니다. 라이브러리(jar) 위치를 확인하세요."
					: "빠진 호출이 많습니다. 프로젝트 등록 칸에 라이브러리(jar) 위치를 적고 다시 분석하세요.")]);
		}
		if (metrics.parseSuccessRate !== undefined && metrics.parseSuccessRate !== null) {
			var parse = Number(metrics.parseSuccessRate);
			lines.push([parse >= 100 ? "good" : "warn", parse >= 100 ? "Java 파일을 모두 읽었습니다." : "Java 파일의 " + parse + "% 만 읽었습니다. 읽지 못한 파일의 메소드는 결과에 없습니다."]);
		}
		var endpoints = sumOf(valueAt(summary, "semantic.endpoints"));
		if (typeof endpoints === "number") {
			lines.push([endpoints > 0 ? "good" : "warn", endpoints > 0 ? "진입점(주소 등) " + formatNumber(endpoints) + "개를 찾았습니다."
				: "진입점을 하나도 찾지 못했습니다. 영향도 분석의 '진입점' 표가 비어 나옵니다."]);
		}
		var sql = 0;
		(valueAt(summary, "relations.byType") || []).forEach(function (row) {
			if (row.relationType === "EXECUTES_SQL" && row.confidence !== "UNRESOLVED") {
				sql += row.count;
			}
		});
		lines.push([sql > 0 ? "good" : "info", sql > 0 ? "메소드와 SQL 이 " + formatNumber(sql) + "건 이어졌습니다. 테이블에서 출발하는 영향도 분석을 할 수 있습니다."
			: "메소드와 이어진 SQL 이 없습니다(매퍼 XML 을 쓰지 않는 프로그램이면 정상). 테이블에서 출발하는 영향도 분석은 할 수 없습니다."]);
		var done = 0;
		var total = 0;
		(valueAt(summary, "rag.embedding") || []).forEach(function (row) {
			total += row.chunks;
			if (row.status === "DONE") {
				done += row.chunks;
			}
		});
		if (total > 0) {
			var percent = Math.floor(100 * done / total);
			lines.push([percent >= 100 ? "good" : "info", percent >= 100 ? "임베딩이 끝났습니다. 뜻으로 찾는 검색을 온전히 쓸 수 있습니다."
				: "임베딩이 " + percent + "% 진행됐습니다(" + formatNumber(done) + " / " + formatNumber(total) + "). 끝날 때까지 뜻으로 찾는 검색은 일부만 됩니다. 이름으로 찾기 · 호출 관계 · 영향도는 지금도 됩니다."]);
		}
		if (summary.status !== "READY") {
			lines.unshift(["bad", "이 리비전은 분석이 끝나지 않았습니다(상태 " + summary.status + "). 아래 숫자는 도중까지의 결과입니다."]);
		}
		var box = document.createElement("div");
		box.className = "kn-verdict";
		box.innerHTML = "<div class=\"kn-section-title\" style=\"margin-top:0;\">한눈에 보기</div>" + lines.map(function (line) {
			var mark = line[0] === "good" ? "✔" : line[0] === "bad" ? "✖" : line[0] === "warn" ? "!" : "·";
			return "<div class=\"kn-verdict-line kn-verdict-" + line[0] + "\"><span class=\"kn-verdict-mark\">" + mark + "</span>" + K.escapeHtml(line[1]) + "</div>";
		}).join("");
		return box;
	}

	/** 표 하나: 제목, 무엇을 보여 주는지, 표(영문 값 옆에 뜻), 읽는 법 */
	function sectionBox(section, value) {
		var wrap = document.createElement("div");
		var html = "<div class=\"kn-section-title\">" + K.escapeHtml(section.title) + "</div><p class=\"ai-hint\">" + K.escapeHtml(section.what) + "</p><div class=\"kn-scroll\"></div>";
		var help = "";
		if (section.columns) {
			help += "<ul>" + section.columns.filter(function (column) { return column[2]; }).map(function (column) {
				return "<li><b>" + K.escapeHtml(column[1]) + "</b> <code>" + K.escapeHtml(column[0]) + "</code> — " + K.escapeHtml(column[2]) + "</li>";
			}).join("") + "</ul>";
		}
		if (section.read) {
			help += "<p>" + K.escapeHtml(section.read) + "</p>";
		}
		if (help) {
			html += "<details class=\"kn-howto\"><summary>이 표 읽는 법</summary>" + help + "</details>";
		}
		wrap.innerHTML = html;
		var tableEl = wrap.querySelector(".kn-scroll");
		if (section.pairs) {
			var rows = Object.keys(value).map(function (key) {
				return { name: key, value: value[key] };
			});
			K.table(tableEl, [
				{ key: "name", label: "항목", html: function (row) { return withMeaning(row.name, section.rows[row.name]); } },
				{ key: "value", label: "값", num: true }
			], rows);
			return wrap;
		}
		K.table(tableEl, section.columns.map(function (column) {
			var numeric = value.length > 0 && typeof value[0][column[0]] === "number";
			return {
				key: column[0],
				label: column[1],
				num: numeric,
				html: column[3] ? function (row) { return withMeaning(row[column[0]], T.meaningOf(column[3], row[column[0]])); } : null
			};
		}), value);
		return wrap;
	}

	/** 영문 값 뒤에 한글 뜻을 흐린 글자로 붙인다 */
	function withMeaning(code, meaning) {
		return "<span class=\"kn-code\">" + K.escapeHtml(code) + "</span>" + (meaning ? "<span class=\"kn-term\">" + K.escapeHtml(meaning) + "</span>" : "");
	}

	function formatNumber(value) {
		return typeof value === "number" ? value.toLocaleString() : (value === null || value === undefined ? "-" : String(value));
	}

	/** "a.b.c" 경로로 값을 꺼냅니다. 중간에 없으면 null */
	function valueAt(object, path) {
		var value = object;
		path.split(".").forEach(function (key) {
			value = value === null || value === undefined ? null : value[key];
		});
		return value === undefined ? null : value;
	}

	/** 행들의 건수를 더합니다. 건수가 든 키(count 등)는 행에서 처음 나오는 숫자 값으로 봅니다 */
	function sumOf(rows, key) {
		if (!Array.isArray(rows)) {
			return "-";
		}
		var total = 0;
		rows.forEach(function (row) {
			var numberKey = key;
			if (!numberKey) {
				Object.keys(row).forEach(function (name) {
					if (!numberKey && typeof row[name] === "number") {
						numberKey = name;
					}
				});
			}
			total += Number(row[numberKey]) || 0;
		});
		return total;
	}

	function eachRow(container, callback) {
		Array.prototype.forEach.call(container.querySelectorAll("tbody tr[data-index]"), function (tr) {
			callback(tr, parseInt(tr.getAttribute("data-index"), 10));
		});
	}

	return { init: init };
})();

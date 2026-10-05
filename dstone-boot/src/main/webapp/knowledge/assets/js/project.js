/**
 * "프로젝트 · 분석" 화면: 프로젝트 등록, 분석 시작과 진행 상황, 리비전 목록, 리비전 요약(품질).
 */
var DstoneKnowledgeProject = (function () {

	var K = DstoneKnowledge;
	var el = {};
	var projectId = null;
	var pollTimer = null;

	/** 리비전 요약에서 보여 줄 항목. [응답에서 값을 꺼내는 경로, 제목, 설명] */
	var SUMMARY_SECTIONS = [
		["incremental.files", "증분 분석: 파일", "기준 리비전과 견주어 같은 / 바뀐 / 새로 생긴 / 없어진 파일 수"],
		["incremental.passes", "증분 분석: 단계별", "옮겨 온 파일(carriedFiles)과 실제로 처리한 파일(analyzedFiles)"],
		["passes", "단계별 진행", null],
		["relations.metrics", "품질 지표", "호출이 얼마나 풀렸는지 등. LINK 단계가 계산합니다"],
		["relations.byType", "관계 (종류 · 신뢰도별)", "HIGH는 확실, MEDIUM은 덜 확실, LOW는 이름만 보고 짐작한 것"],
		["relations.unresolvedReasons", "풀지 못한 참조의 이유", null],
		["semantic.endpoints", "진입점", null],
		["semantic.layers", "계층", null],
		["semantic.resources", "Java 밖의 자원 (SQL, 설정, 빈 ...)", null],
		["files.byType", "파일 (종류별)", null],
		["declarations.types", "타입", null],
		["declarations.members", "멤버", null],
		["declarations.references", "참조", null],
		["declarations.filePasses", "파일별 단계 결과", null],
		["rag.documents", "검색 문서", null],
		["rag.embedding", "임베딩 진행", "DONE이 돼야 뜻으로 찾는 검색에 나옵니다. PENDING은 대기 중"],
		["jobs", "Job 이력", null]
	];

	function init() {
		["kn-health", "kn-project-table", "kn-new-project-id", "kn-new-project-name", "kn-new-project-path", "kn-project-save-btn", "kn-project-message",
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
			{ key: "pass", label: "단계" },
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
		K.cards(el["kn-summary-cards"], [
			{ label: "파일", value: valueAt(summary, "files.total") },
			{ label: "타입", value: sumOf(valueAt(summary, "declarations.types")) },
			{ label: "관계", value: sumOf(valueAt(summary, "relations.byType")) },
			{ label: "진입점", value: sumOf(valueAt(summary, "semantic.endpoints")) },
			{ label: "검색 문서", value: sumOf(valueAt(summary, "rag.documents"), "documents") }
		]);
		el["kn-summary-sections"].innerHTML = "";
		SUMMARY_SECTIONS.forEach(function (section) {
			var value = valueAt(summary, section[0]);
			if (value === null || value === undefined || (Array.isArray(value) && value.length === 0)) {
				return;
			}
			var wrap = document.createElement("div");
			wrap.innerHTML = "<div class=\"kn-section-title\">" + K.escapeHtml(section[1]) + "</div>"
				+ (section[2] ? "<p class=\"ai-hint\">" + K.escapeHtml(section[2]) + "</p>" : "") + "<div class=\"kn-scroll\"></div>";
			K.autoTable(wrap.querySelector(".kn-scroll"), value);
			el["kn-summary-sections"].appendChild(wrap);
		});
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
